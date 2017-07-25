package it.francescogabbrielli.apps.sensorlogger;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ToggleButton;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener,
        SurfaceHolder.Callback {

    private final static String TAG = MainActivity.class.getSimpleName();

    private final static int REQUEST_EXTERNAL_STORAGE = 1;
    private final static int REQUEST_CAMERA = 2;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private CameraHandlerThread cameraHandlerThread;
    private CameraPreview cameraPreview;

    private byte[] sensorsData;

    private FTPUploader uploader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (RecordingService.BROADCAST_SEND_DATA.equals(intent.getAction())) {
                    sensorsData = intent.getStringExtra(RecordingService.EXTRA_SENSORS_DATA).getBytes();
                    try {
                        Camera camera = cameraHandlerThread.getCamera();
                        camera.startPreview();
                        camera.takePicture(
//                        new Camera.ShutterCallback() {
//                            @Override
//                            public void onShutter() {
//                                AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//                                mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
//                            }
//                        },
                                null, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(byte[] imageData, Camera camera) {
                                send(imageData, sensorsData);
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error taking picture", e);
                    }
                } else if(RecordingService.BROADCAST_FTP_ERROR.equals(intent.getAction())) {
                    ToggleButton b = (ToggleButton) findViewById(R.id.btn_rec);
                    b.setChecked(false);
                }
            }
        };

        filter = new IntentFilter();
        filter.addAction(RecordingService.BROADCAST_SEND_DATA);
        filter.addAction(RecordingService.BROADCAST_FTP_ERROR);

        verifyStoragePermissions();
        verifyCameraPermissions();

        final ToggleButton b = (ToggleButton) findViewById(R.id.btn_rec);
        b.setChecked(prefs.getBoolean(Util.PREF_RECORDING, false));
        b.setOnCheckedChangeListener(this);

        cameraHandlerThread = new CameraHandlerThread(this);
        cameraPreview = new CameraPreview(this);
        FrameLayout preview = (FrameLayout) findViewById(R.id.recording_preview);
        preview.addView(cameraPreview);

    }

    protected void onPause() {
        super.onPause();
//        if (camera!=null)
//            camera.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        try {
//        } catch(Exception e) {
//            Log.e(TAG, "Camera recoonection error", e);
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(isChecked) {
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
            uploader = new FTPUploader(getApplicationContext());
            RecordingService.startRecording(this);
        } else {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            if (uploader!=null)
                uploader.close();
            RecordingService.stopRecording(this);

            // restart preview???
            Camera camera = cameraHandlerThread.getCamera();
            if (camera!=null)
                camera.startPreview();
        }
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permissions
     */
    private void verifyStoragePermissions() {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    private void verifyCameraPermissions() {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA
            );
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    new File(Environment.getExternalStorageDirectory(), getString(R.string.app_folder)).mkdirs();
                }
                break;
            case REQUEST_CAMERA:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    createCamera();
                }
                break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void send(byte[] imageData, byte[] sensorsData) {
        if (uploader != null)
            try {
                uploader.send(sensorsData, "sensors.csv");
                uploader.send(imageData, "frame.jpg");
            } catch (Exception e) {
                Log.e(TAG, "Error sending data: " + e.getMessage(), e);
            }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        cameraHandlerThread.openCamera(holder);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

        Camera camera = cameraHandlerThread.getCamera();
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (holder.getSurface()==null || camera==null)
            return;

        // stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

//        mCamera.setPreviewCallback(null);
        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();

        } catch (Exception e){
            Log.e(TAG, "Error starting camera preview", e);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Camera camera = cameraHandlerThread.getCamera();
        if (camera!=null)
            camera.release();
//        onCheckedChanged((ToggleButton) findViewById(R.id.btn_rec), false);
    }



}
