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
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener,
        SurfaceHolder.Callback {

    private final static String TAG = MainActivity.class.getSimpleName();

    private final static int REQUEST_EXTERNAL_STORAGE = 1;
    private final static int REQUEST_CAMERA = 2;
    private final static int REQUEST_PERMISSIONS = 3;

    private BroadcastReceiver receiver;
    private IntentFilter filter;

    private CameraHandlerThread cameraHandlerThread;
    private CameraPreview cameraPreview;
    private Camera.ShutterCallback shutterCallback;

    private FTPUploader uploader;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (RecordingService.BROADCAST_SEND_DATA.equals(intent.getAction())) {
                    final byte[] sensorsData =
                            intent.getStringExtra(RecordingService.EXTRA_SENSORS_DATA).getBytes();
                    try {
                        Camera camera = cameraHandlerThread.getCamera();
                        camera.startPreview();
                        camera.takePicture(shutterCallback, null, new Camera.PictureCallback() {
                            @Override
                            public void onPictureTaken(final byte[] imageData, final Camera camera) {
                                send(imageData, sensorsData, new Runnable() {
                                    @Override
                                    public void run() {
                                        camera.startPreview();
                                    }
                                });
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

        final ToggleButton b = (ToggleButton) findViewById(R.id.btn_rec);
        b.setChecked(prefs.getBoolean(Util.PREF_RECORDING, false));
        b.setOnCheckedChangeListener(this);

    }

    /** Setup the camera; can be called anytime */
    private void setupCamera() {

        // Start a handler thread for the camera operation if not already started
        if (cameraHandlerThread == null) {
            cameraHandlerThread = new CameraHandlerThread(this);
            cameraPreview = new CameraPreview(this);
            FrameLayout preview = (FrameLayout) findViewById(R.id.recording_preview);
            preview.addView(cameraPreview);
        }

        // Click sound
        shutterCallback = prefs.getBoolean(Util.PREF_CAPTURE_SOUND, false) ?
                new Camera.ShutterCallback() {
                    @Override
                    public void onShutter() {
                        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
                    }
                } : null;
    }

    protected void onPause() {
        super.onPause();
//        if (camera!=null)
//            camera.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyPermissions();
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
            uploader.connect();
            RecordingService.startRecording(this);
        } else {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
            if (uploader!=null)
                uploader.close();
            RecordingService.stopRecording(this);

            // restart preview ???
            cameraHandlerThread.restart();
        }
    }

    /**
     * Checks if the app has the required permissions, as per current setttings.
     *
     * If the app does not has permission then the user will be prompted to grant permissions.
     */
    private void verifyPermissions() {

        // List all permissions that are involved in activated features
        List<String> requests = new LinkedList<>();
        if(prefs.getBoolean(Util.PREF_CAPTURE_CAMERA, false))
            requests.add(Manifest.permission.CAMERA);
        if(prefs.getBoolean(Util.PREF_FTP, false))
            requests.add(Manifest.permission.INTERNET);
        if(prefs.getBoolean(Util.PREF_LOGGING_SAVE, false))
            requests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        // Check if we have requested permission
        for (ListIterator<String> it = requests.listIterator();it.hasNext();) {
            String permission = it.next();
            int check = ActivityCompat.checkSelfPermission(this, permission);
            if (check != PackageManager.PERMISSION_GRANTED) {
                onPermissionDenied(permission);
            } else {
                it.remove();
                onPermissionGranted(permission);
            }
        }

        // Request missing permissions
        if (!requests.isEmpty()) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this,
                    requests.toArray(new String[requests.size()]),
                    REQUEST_PERMISSIONS
            );
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
//            case REQUEST_EXTERNAL_STORAGE:
//                if (grantResults.length > 0
//                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    new File(Environment.getExternalStorageDirectory(), getString(R.string.app_folder)).mkdirs();
//                }
//                break;
//            case REQUEST_CAMERA:
//                if (grantResults.length > 0
//                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    setupCamera();
//                }
//                break;
            case REQUEST_PERMISSIONS:
                for (int i=0;i<grantResults.length;i++)
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                        onPermissionGranted(permissions[i]);
                    else
                        onPermissionDenied(permissions[i]);
                break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onPermissionGranted(String permission) {
        if (Manifest.permission.CAMERA.equals(permission))
            setupCamera();
//        else if(Manifest.permission.INTERNET.equals(permission))

    }

    protected void onPermissionDenied(String permission) {
        Toast.makeText(this, R.string.alert_permission_denied, Toast.LENGTH_LONG).show();
        if (Manifest.permission.CAMERA.equals(permission))
            prefs.edit().putBoolean(Util.PREF_CAPTURE_CAMERA, false);
        else if (Manifest.permission.INTERNET.equals(permission))
            prefs.edit().putBoolean(Util.PREF_FTP, false);
        else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission))
            prefs.edit().putBoolean(Util.PREF_LOGGING_SAVE, false);
    }

    private void send(byte[] imageData, byte[] sensorsData, Runnable callback) {
        if (uploader != null)
            try {
                uploader.send(sensorsData, "sensors.csv", null);
                uploader.send(imageData, "frame.jpg", callback);
            } catch (Exception e) {
                Log.e(TAG, "Error sending data: " + e.getMessage(), e);
            }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        cameraHandlerThread.openCamera(holder, null);
//        new Runnable() {
//            @Override
//            public void run() {
//                cameraHandlerThread.restart();
//            }
//        });
    }

    public void surfaceChanged(final SurfaceHolder holder, final int format, final int w, final int h) {

        Camera camera = cameraHandlerThread.getCamera();
        if (camera==null) {
            cameraHandlerThread.openCamera(holder, new Runnable() {
                @Override
                public void run() {
                    surfaceChanged(holder, format, w, h);
                }
            });
            return;
        }

        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (holder.getSurface()==null)
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
        cameraHandlerThread.closeCamera();
    }

}
