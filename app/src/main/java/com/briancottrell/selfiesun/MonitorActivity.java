package com.briancottrell.selfiesun;

import com.briancottrell.selfiesun.util.SystemUiHider;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.flir.flironesdk.Device;
import com.flir.flironesdk.Frame;
import com.flir.flironesdk.FrameProcessor;
import com.flir.flironesdk.RenderedImage;
import com.flir.flironesdk.SimulatedDevice;

import java.util.EnumSet;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MonitorActivity extends AppCompatActivity implements Device.Delegate, FrameProcessor.Delegate, Device.StreamDelegate, Device.PowerUpdateDelegate {
    ImageView thermalImageView;
    private boolean chargeCableIsConnected = true;
    private int deviceRotation= 0;
    private OrientationEventListener orientationEventListener;
    private volatile Device flirOneDevice;
    private FrameProcessor frameProcessor;
    private String lastSavedPath;
    private Device.TuningState currentTuningState = Device.TuningState.Unknown;
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    ///////////////////////////////////////////////////////////////////////////////////////
    // ADJUST THESE VALUES TO AFTER TESTING APP WITH AN ACTUAL PERSON AND FLIRONE CAMERA //
    ///////////////////////////////////////////////////////////////////////////////////////
    private int targetOffset = 100;
    private int warningOffset = 150;
    ///////////////////////////////////////////////////////////////////////////////////////

    private View mContentView;
    private View mControlsView;
    private boolean mVisible;

    private TextView temperatureReading;
    private int heatLevel = 0;
    private int suntanLevel = 0;
    private int sunblockLevel = 0;
    private int skinTone = 0;
    private int targetHeat = 0;
    private int warningHeat = 0;

    private boolean displayTargetMessage = false;
    private boolean displayWarningMessage = false;
    private boolean dismissMessage = true;

    private ColorFilter originalChargingIndicatorColor = null;
    private Bitmap thermalBitmap = null;
    ScaleGestureDetector mScaleDetector;
    final ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
    private long storedTime = System.currentTimeMillis()/1000;
    private long delayTime = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_monitor);

        String[] imageTypeNames = new String[RenderedImage.ImageType.values().length];
        // Massage the type names for display purposes
        for (RenderedImage.ImageType t : RenderedImage.ImageType.values()){
            String name = t.name().replaceAll("(RGBA)|(YCbCr)|(8)","").replaceAll("([a-z])([A-Z])", "$1 $2");

            if (name.contains("YCbCr888")){
                name = name.replace("YCbCr888", "Aligned");
            }
            imageTypeNames[t.ordinal()] = name;
        }
        RenderedImage.ImageType defaultImageType = RenderedImage.ImageType.BlendedMSXRGBA8888Image;
        frameProcessor = new FrameProcessor(this, this, EnumSet.of(defaultImageType));

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                deviceRotation = orientation;
            }
        };
        mScaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                Log.d("ZOOM", "zoom ongoing, scale: " + detector.getScaleFactor());
                frameProcessor.setMSXDistance(detector.getScaleFactor());
                return false;
            }
        });

        findViewById(R.id.fullscreen_content).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                return true;
            }
        });

        Intent intent = getIntent();
        if(intent.getBooleanExtra(SetupActivity.EXTRA_SIMULATE, false)) {
            if (flirOneDevice == null) {
                try {
                    flirOneDevice = new SimulatedDevice(this, getResources().openRawResource(R.raw.sampleframes), 10);
                    flirOneDevice.setPowerUpdateDelegate(this);
                    chargeCableIsConnected = true;
                } catch (Exception ex) {
                    flirOneDevice = null;
                    ex.printStackTrace();
                }
            } else if (flirOneDevice instanceof SimulatedDevice) {
                flirOneDevice.close();
                flirOneDevice = null;
            }
        }

        suntanLevel = intent.getIntExtra(SetupActivity.EXTRA_SUNTAN, 0);
        sunblockLevel = intent.getIntExtra(SetupActivity.EXTRA_SUNBLOCK, 0);
        skinTone = intent.getIntExtra(SetupActivity.EXTRA_COLOR, 0);
        targetHeat = suntanLevel+sunblockLevel+skinTone+targetOffset;
        warningHeat = suntanLevel+sunblockLevel+skinTone+warningOffset;

        temperatureReading = (TextView) findViewById(R.id.temperatureReading);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    protected void onStart(){
        super.onStart();
        thermalImageView = (ImageView) findViewById(R.id.imageView);
        try {
            Device.startDiscovery(this, this);
        }catch(IllegalStateException e){
            // it's okay if we've already started discovery
        }
    }

    @Override
    public void onRestart(){
        try {
            Device.startDiscovery(this, this);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        super.onRestart();
    }

    @Override
    public void onStop() {
        // We must unregister our usb receiver, otherwise we will steal events from other apps
        Device.stopDiscovery();
        super.onStop();
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {

        }
    };

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };

    private final Handler mHideHandler = new Handler();
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void onTuningStateChanged(Device.TuningState tuningState) {

    }

    @Override
    public void onAutomaticTuningChanged(boolean b) {

    }

    @Override
    public void onDeviceConnected(Device device) {
        flirOneDevice = device;
        flirOneDevice.setPowerUpdateDelegate(this);
        flirOneDevice.startFrameStream(this);
        orientationEventListener.enable();
    }

    @Override
    public void onDeviceDisconnected(Device device) {
        final TextView levelTextView = (TextView)findViewById(R.id.batteryLevelTextView);
        final ImageView chargingIndicator = (ImageView)findViewById(R.id.batteryChargeIndicator);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
                levelTextView.setText("--");
                chargingIndicator.setVisibility(View.GONE);
                thermalImageView.clearColorFilter();
            }
        });
        flirOneDevice = null;
        orientationEventListener.disable();
    }

    private void updateThermalImageView(final Bitmap frame){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                thermalImageView.setImageBitmap(frame);
                temperatureReading.setText(String.valueOf(heatLevel));

                if (displayWarningMessage && dismissMessage) {
                    dismissMessage = false;
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK);
                    AlertDialog alertDialog = new AlertDialog.Builder(MonitorActivity.this).create();
                    alertDialog.setTitle(MonitorActivity.this.getString(R.string.warning_reached_title));
                    alertDialog.setMessage(MonitorActivity.this.getString(R.string.warning_reached_message));
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, MonitorActivity.this.getString(R.string.warning_reached_dismiss),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    displayWarningMessage = false;
                                    dismissMessage = true;
                                    storedTime = System.currentTimeMillis()/1000;
                                }
                            });
                    alertDialog.show();
                } else if (displayTargetMessage && dismissMessage) {
                    dismissMessage = false;
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2);
                    AlertDialog alertDialog = new AlertDialog.Builder(MonitorActivity.this).create();
                    alertDialog.setTitle(MonitorActivity.this.getString(R.string.suntan_reached_title));
                    alertDialog.setMessage(MonitorActivity.this.getString(R.string.suntan_reached_message));
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, MonitorActivity.this.getString(R.string.suntan_reached_dismiss),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    displayTargetMessage = false;
                                    dismissMessage = true;
                                    storedTime = System.currentTimeMillis()/1000;
                                }
                            });
                    alertDialog.show();
                }
            }
        });
    }

    @Override
    public void onFrameProcessed(RenderedImage renderedImage) {
        thermalBitmap = renderedImage.getBitmap();
        heatLevel = Color.red(thermalBitmap.getPixel(thermalBitmap.getWidth()/2, thermalBitmap.getHeight()/2));
        heatLevel += Color.blue(thermalBitmap.getPixel(thermalBitmap.getWidth()/2, thermalBitmap.getHeight() / 2));
        heatLevel += Color.green(thermalBitmap.getPixel(thermalBitmap.getWidth()/2, thermalBitmap.getHeight()/2));
        updateThermalImageView(thermalBitmap);
        if (!displayTargetMessage && !displayWarningMessage) {
            if (System.currentTimeMillis()/1000 > storedTime + delayTime) {
                if (heatLevel > warningHeat) {
                    displayWarningMessage = true;
                } else if (heatLevel > targetHeat) {
                    displayTargetMessage = true;
                }
            }
        }
    }

    @Override
    public void onFrameReceived(Frame frame) {
        if (currentTuningState != Device.TuningState.InProgress){
            frameProcessor.processFrame(frame);
        }
    }

    @Override
    public void onBatteryChargingStateReceived(final Device.BatteryChargingState batteryChargingState) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ImageView chargingIndicator = (ImageView)findViewById(R.id.batteryChargeIndicator);
                if (originalChargingIndicatorColor == null){
                    originalChargingIndicatorColor = chargingIndicator.getColorFilter();
                }
                switch (batteryChargingState) {
                    case FAULT:
                    case FAULT_HEAT:
                        chargingIndicator.setColorFilter(Color.RED);
                        chargingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case FAULT_BAD_CHARGER:
                        chargingIndicator.setColorFilter(Color.DKGRAY);
                        chargingIndicator.setVisibility(View.VISIBLE);
                    case MANAGED_CHARGING:
                        chargingIndicator.setColorFilter(originalChargingIndicatorColor);
                        chargingIndicator.setVisibility(View.VISIBLE);
                        break;
                    case NO_CHARGING:
                    default:
                        chargingIndicator.setVisibility(View.GONE);
                        break;
                }
            }
        });
    }

    @Override
    public void onBatteryPercentageReceived(final byte percentage) {
        final TextView levelTextView = (TextView)findViewById(R.id.batteryLevelTextView);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                levelTextView.setText(String.valueOf((int) percentage) + "%");
                if ((int) percentage < 5) {
                    toneGenerator.startTone(ToneGenerator.TONE_CDMA_KEYPAD_VOLUME_KEY_LITE);
                    AlertDialog alertDialog = new AlertDialog.Builder(MonitorActivity.this).create();
                    alertDialog.setTitle(MonitorActivity.this.getString(R.string.battery_low_title));
                    alertDialog.setMessage(MonitorActivity.this.getString(R.string.battery_low_message));
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, MonitorActivity.this.getString(R.string.battery_low_dismiss),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }
        });
    }
}
