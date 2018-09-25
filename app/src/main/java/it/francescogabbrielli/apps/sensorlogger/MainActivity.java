package it.francescogabbrielli.apps.sensorlogger;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements
        SurfaceHolder.Callback,
        ICamera {

    private final static String TAG = MainActivity.class.getSimpleName();

    private final static int REQUEST_PERMISSIONS = 3;

    private CameraHandlerThread cameraHandlerThread;
    private CameraPreview cameraPreview;
    private Camera.ShutterCallback shutterCallback;

    private SharedPreferences prefs;

    private Recorder recorder;

    private boolean safeToTakePicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        recorder = new Recorder(
                new SensorReader((SensorManager) getSystemService(SENSOR_SERVICE), prefs),
                this);

        //start the service
        startService(new Intent(this, LoggingService.class));

    }

    /**
     * Take a picture
     * @param pictureCallback the callback
     */
    @Override
    public void takePicture(Camera.PictureCallback pictureCallback) {
        if (safeToTakePicture)
            try {
                Camera camera = cameraHandlerThread.getCamera();
                camera.startPreview();
                safeToTakePicture = false;
                //TODO: settings -> image raw data
                camera.takePicture(shutterCallback, null, pictureCallback);
            } catch (Exception e) {
                Log.e(TAG, "Picture not taken", e);
                pictureCallback.onPictureTaken(null, null);
            }
    }

    @Override
    public void pictureTaken() {
        safeToTakePicture = true;
    }

    /**
     * Setup the camera; can be called anytime
     */
    private void setupCamera() {

        // Start a handler thread for the camera operation if not already started
        if (cameraHandlerThread == null) {
            cameraHandlerThread = new CameraHandlerThread(this);
            cameraPreview = new CameraPreview(this);
            FrameLayout preview = findViewById(R.id.recording_preview);
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

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        verifyPermissions();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        recorder.stop();
        hidePrepareAnimation();
        hideBlinkingAnimation();
        if (animExec!=null)
            animExec.shutdown();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean recording;
    private long lastPressed;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (SystemClock.elapsedRealtime()-lastPressed>1000)
            if (keyCode==KeyEvent.KEYCODE_VOLUME_DOWN || keyCode==KeyEvent.KEYCODE_VOLUME_UP ) {
                lastPressed = SystemClock.elapsedRealtime();
                AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (!recording) {
                    recording = true;
                    recorder.start(this);
                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                    showPrepareAnimation();
                } else {
                    recording = false;
                    recorder.stop();
                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                    hidePrepareAnimation();
                    hideBlinkingAnimation();
                    // restart preview ???
                    if (cameraHandlerThread != null)
                        cameraHandlerThread.restart();
                }
                return true;
            }
        return super.onKeyDown(keyCode,event);
    }

    private ScheduledExecutorService animExec;
    private ScheduledFuture prepareFuture, recordingFuture;

    private void showPrepareAnimation() {
        if (animExec==null)
            animExec = Executors.newSingleThreadScheduledExecutor();
        prepareFuture = animExec.schedule(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView t = findViewById(R.id.anim_prepare);
                        int val = 4;
                        try { val = Integer.parseInt(t.getText().toString()); } catch(Exception e) {}
                        if (--val>0) {
                            t.setText(String.valueOf(val));
                            showPrepareAnimation();
                        } else {
                            hidePrepareAnimation();
                            showBlinkingAnimation();
                        }
                    }
                });
            }
        }, 1, TimeUnit.SECONDS);
    }
    private void hidePrepareAnimation() {
        if (prepareFuture !=null)
            prepareFuture.cancel(true);
        TextView t = findViewById(R.id.anim_prepare);
        t.setText("");
    }

    private void showBlinkingAnimation() {
        if (animExec==null)
            animExec = Executors.newSingleThreadScheduledExecutor();
        recordingFuture = animExec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView i = findViewById(R.id.img_record);
                        i.setVisibility(i.getVisibility()==View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                    }
                });
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

    }
    private void hideBlinkingAnimation() {
        if (recordingFuture!=null)
            recordingFuture.cancel(true);
        ImageView i = findViewById(R.id.img_record);
        i.setVisibility(View.INVISIBLE);
    }


    //<editor-fold desc="Permissions">
    // ----------------------------------- PERMISSIONS MANAGEMENT ----------------------------------
    //
    /**
     * Checks if the app has the required permissions, as per current setttings.
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions.
     */
    private void verifyPermissions() {

        // List all permissions that are involved in activated features
        List<String> requests = new LinkedList<>();
        if (prefs.getBoolean(Util.PREF_FILE, false))
            requests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (prefs.getBoolean(Util.PREF_CAPTURE_CAMERA, false))
            requests.add(Manifest.permission.CAMERA);
        if (prefs.getBoolean(Util.PREF_FTP, false))
            requests.add(Manifest.permission.INTERNET);

        // Check if we have requested permission
        for (ListIterator<String> it = requests.listIterator(); it.hasNext(); ) {
            String permission = it.next();
            int check = ActivityCompat.checkSelfPermission(this, permission);
            if (check != PackageManager.PERMISSION_GRANTED) {
                onPermissionDenied(permission);
            } else {
                it.remove();
                onPermissionOk(permission);
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
            case REQUEST_PERMISSIONS:
                for (int i = 0; i < grantResults.length; i++)
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        onPermissionGranted(permissions[i]);
                    } else {
                        Toast.makeText(this, R.string.alert_permission_denied, Toast.LENGTH_LONG).show();
                        onPermissionDenied(permissions[i]);
                    }
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onPermissionOk(String permission) {
        Log.d(TAG, "Permission ok: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            setupCamera();
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
        } else if (Manifest.permission.INTERNET.equals(permission)) {
        }
    }

    protected void onPermissionGranted(String permission) {
        Log.d(TAG, "Permission granted: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_CAPTURE_CAMERA, true).apply();
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    getString(R.string.app_folder));
            Log.i(TAG, "Creating app folder " + dir.getAbsolutePath() + "..." + dir.mkdirs());
            prefs.edit().putBoolean(Util.PREF_FILE, true).apply();
        } else if (Manifest.permission.INTERNET.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_FTP, true).apply();
        }
    }

    protected void onPermissionDenied(String permission) {
        Log.d(TAG, "Permission denied: " + permission);
        if (Manifest.permission.CAMERA.equals(permission))
            prefs.edit().putBoolean(Util.PREF_CAPTURE_CAMERA, false).apply();
        else if (Manifest.permission.INTERNET.equals(permission))
            prefs.edit().putBoolean(Util.PREF_FTP, false).apply();
        else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission))
            prefs.edit().putBoolean(Util.PREF_FILE, false).apply();
    }
    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>


    //<editor-fold desc="Camera Surface">
    // ---------------------------------- CAMERA SURFACE CALLBACKS ---------------------------------
    //
    public void surfaceCreated(SurfaceHolder holder) {
        cameraHandlerThread.openCamera(holder, null);
        safeToTakePicture = true;
        Log.d(TAG, "Start");
//        new Runnable() {
//            @Override
//            public void run() {
//                cameraHandlerThread.restart();
//            }
//        });
    }

    public void surfaceChanged(final SurfaceHolder holder, final int format, final int w, final int h) {

        Camera camera = cameraHandlerThread.getCamera();
        if (camera == null) {
//            cameraHandlerThread.openCamera(holder, new Runnable() {
//                @Override
//                public void run() {
//                    surfaceChanged(holder, format, w, h);
//                }
//            });
            return;
        }

        Log.d(TAG, "Camera change: " + camera);

        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
//        if (holder.getSurface() == null)
//            return;

//        // stop preview before making changes
//        try {
//            camera.stopPreview();
//        } catch (Exception e) {
//            // ignore: tried to stop a non-existent preview
//        }

//        mCamera.setPreviewCallback(null);
        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            safeToTakePicture = true;
            Log.d(TAG, "Restart");
        } catch (Exception e) {
            Log.e(TAG, "Error starting camera preview", e);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        cameraHandlerThread.closeCamera();
    }
    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>

}
