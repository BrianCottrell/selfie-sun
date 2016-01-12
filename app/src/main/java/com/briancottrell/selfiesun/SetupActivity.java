package com.briancottrell.selfiesun;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.ToggleButton;

public class SetupActivity extends AppCompatActivity {
    public final static String EXTRA_SIMULATE = "com.briancottrell.selfiesun.simulate";
    public final static String EXTRA_COLOR = "com.briancottrell.selfiesun.color";
    public final static String EXTRA_SUNTAN = "com.briancottrell.selfiesun.suntan";
    public final static String EXTRA_SUNBLOCK = "com.briancottrell.selfiesun.sunblock";
    static final int REQUEST_IMAGE_CAPTURE = 1;

    private boolean simulate = false;
    private int tanLevel = 0;
    private int sunblock = 0;

    private Switch simulateSwitch;
    private ToggleButton tanButtonMild;
    private ToggleButton tanButtonMedium;
    private ToggleButton tanButtonWell;
    private ToggleButton sunblockButtonMild;
    private ToggleButton sunblockButtonMedium;
    private ToggleButton sunblockButtonWell;
    private ToggleButton sunblockButtonHigh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        simulateSwitch = (Switch) findViewById(R.id.simulateSwitch);
        simulateSwitch.setChecked(false);
        simulateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    simulate = true;
                } else {
                    simulate = false;
                }
            }
        });

        tanButtonMild = (ToggleButton) findViewById(R.id.tanLevelMild);
        tanButtonMedium = (ToggleButton) findViewById(R.id.tanLevelMedium);
        tanButtonWell = (ToggleButton) findViewById(R.id.tanLevelWell);
        tanButtonMild.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tanButtonMild.setChecked(true);
                tanButtonMedium.setChecked(false);
                tanButtonWell.setChecked(false);
                tanLevel = 0;
            }
        });
        tanButtonMedium.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tanButtonMild.setChecked(false);
                tanButtonMedium.setChecked(true);
                tanButtonWell.setChecked(false);
                tanLevel = 20;
            }
        });
        tanButtonWell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tanButtonMild.setChecked(false);
                tanButtonMedium.setChecked(false);
                tanButtonWell.setChecked(true);
                tanLevel = 40;
            }
        });

        sunblockButtonMild = (ToggleButton) findViewById(R.id.sunblockLevelMild);
        sunblockButtonMedium = (ToggleButton) findViewById(R.id.sunblockLevelMedium);
        sunblockButtonWell = (ToggleButton) findViewById(R.id.sunblockLevelWell);
        sunblockButtonHigh = (ToggleButton) findViewById(R.id.sunblockLevelHigh);
        sunblockButtonMild.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sunblockButtonMild.setChecked(true);
                sunblockButtonMedium.setChecked(false);
                sunblockButtonWell.setChecked(false);
                sunblockButtonHigh.setChecked(false);
                sunblock = 0;
            }
        });
        sunblockButtonMedium.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sunblockButtonMild.setChecked(false);
                sunblockButtonMedium.setChecked(true);
                sunblockButtonWell.setChecked(false);
                sunblockButtonHigh.setChecked(false);
                sunblock = 20;
            }
        });
        sunblockButtonWell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sunblockButtonMild.setChecked(false);
                sunblockButtonMedium.setChecked(false);
                sunblockButtonWell.setChecked(true);
                sunblockButtonHigh.setChecked(false);
                sunblock = 40;
            }
        });
        sunblockButtonHigh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sunblockButtonMild.setChecked(false);
                sunblockButtonMedium.setChecked(false);
                sunblockButtonWell.setChecked(false);
                sunblockButtonHigh.setChecked(true);
                sunblock = 60;
            }
        });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    public void startMonitor(View view) {
        dispatchTakePictureIntent();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            int centerPixelColor = Color.red(imageBitmap.getPixel(imageBitmap.getWidth() / 2, imageBitmap.getHeight() / 2)) / 5;
            Intent intent = new Intent(this, MonitorActivity.class);
            intent.putExtra(EXTRA_SIMULATE, simulate);
            intent.putExtra(EXTRA_COLOR, centerPixelColor);
            intent.putExtra(EXTRA_SUNTAN, tanLevel);
            intent.putExtra(EXTRA_SUNBLOCK, sunblock);
            startActivity(intent);
        }
    }
}