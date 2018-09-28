package it.francescogabbrielli.apps.sensorlogger;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements
        CameraBridgeViewBase.CvCameraViewListener {

    private final static String TAG = MainActivity.class.getSimpleName();
    private final static double ONE_BILLION = 1000000000d;

    private final static int REQUEST_PERMISSIONS = 3;

    static {
        if (!OpenCVLoader.initDebug())
            Util.Log.w(TAG, "Cannot initialize OpenCV!");
    }

    private JavaCameraView camera;

    private SharedPreferences prefs;

    private Recorder recorder;
    private long timestamp, frameDuration;
    private String imgFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        recorder = new Recorder(
                new SensorReader((SensorManager) getSystemService(SENSOR_SERVICE), prefs));

        //start the service
        startService(new Intent(this, LoggingService.class));

    }

    /**
     * Setup the camera; can be called anytime
     */
    private void setupCamera() {
        camera = findViewById(R.id.camera_view);
        camera.setVisibility(SurfaceView.VISIBLE);
        camera.setCvCameraViewListener(this);
        camera.enableView();
        frameDuration = Util.getLongPref(prefs, Util.PREF_LOGGING_RATE);
        imgFormat = prefs.getString(Util.PREF_CAPTURE_IMGFORMAT, ".png");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Util.Log.d(TAG, "onResume");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        verifyPermissions();
    }

    @Override
    protected void onPause() {
        Util.Log.d(TAG, "onPause");
        stopRecording(R.string.toast_recording_interrupted);
        if (camera != null)
            camera.disableView();
        if (toneGenerator!=null)
            toneGenerator.release();
        toneGenerator = null;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (animExec!=null)
            animExec.shutdown();
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

    private boolean recPressed, recording;
    private long lastPressed;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (SystemClock.elapsedRealtime()-lastPressed>750)
            if (keyCode==KeyEvent.KEYCODE_VOLUME_DOWN || keyCode==KeyEvent.KEYCODE_VOLUME_UP ) {
                lastPressed = SystemClock.elapsedRealtime();
                AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (!recPressed) {
                    recPressed = true;
                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                    showPrepareAnimation();
                } else {
                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                    stopRecording(R.string.toast_recording_stop);
//                    // restart preview ???
//                    if (cameraHandlerThread != null)
//                        cameraHandlerThread.restart();
                }
                return true;
            }
        return super.onKeyDown(keyCode,event);
    }

    private ScheduledExecutorService animExec;
    private ScheduledFuture prepareFuture, recordingFuture;
    private ToneGenerator toneGenerator;

    private void showPrepareAnimation() {
        if (toneGenerator==null && prefs.getBoolean(Util.PREF_CAPTURE_SOUND, false))
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
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
                            if (toneGenerator!=null)
                                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                            t.setText(String.valueOf(val));
                            showPrepareAnimation();
                        } else {
                            if (toneGenerator!=null)
                                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 300);
                            recording = true;
                            recorder.start(MainActivity.this);
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
        timestamp = SystemClock.elapsedRealtimeNanos()- frameDuration;
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
        if (animExec==null)
            animExec = Executors.newSingleThreadScheduledExecutor();
        if (recordingFuture!=null)
            recordingFuture.cancel(true);
        animExec.schedule(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView i = findViewById(R.id.img_record);
                        i.setVisibility(View.INVISIBLE);
                        if (toneGenerator!=null)
                            toneGenerator.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 300);
                    }
                });
            }
        }, 0, TimeUnit.SECONDS);
    }

    void stopRecording(int msg) {
        if (recPressed)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        recPressed = false;
        recording = false;
        recorder.stop();
        hidePrepareAnimation();
        hideBlinkingAnimation();
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
                    requests.toArray(new String[0]),
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
        Util.Log.d(TAG, "Permission ok: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            setupCamera();
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            File dir = new File(Environment.getExternalStorageDirectory(),
                    getString(R.string.app_folder));
            if (dir.mkdirs())
                Util.Log.i(TAG, "Creating app folder " + dir.getAbsolutePath());
        } else if (Manifest.permission.INTERNET.equals(permission)) {
        }
    }

    protected void onPermissionGranted(String permission) {
        Util.Log.d(TAG, "Permission granted: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_CAPTURE_CAMERA, true).apply();
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_FILE, true).apply();
        } else if (Manifest.permission.INTERNET.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_FTP, true).apply();
        }
    }

    protected void onPermissionDenied(String permission) {
        Util.Log.d(TAG, "Permission denied: " + permission);
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

    //<editor-fold desc="OpenCV Callbacks">
    // ---------------------------------- OPENCV CAMERA CALLBACKS ----------------------------------
    //    @Override
    public void onCameraViewStarted(int width, int height) {
        Util.Log.d(TAG, String.format("openCV camera started: %dx%d", width, height));
        frameExec = Executors.newSingleThreadExecutor();
        frameDurationAvg = frameDuration;
        frameNumber = 0;
        lastTime = 0;
    }

    double frameDurationAvg;//milliseconds
    long frameNumber, lastTime;
    int lastFps;
    private Executor frameExec;

    private void fps(long t) {
        if (lastTime>0) {
            frameDurationAvg = (frameDurationAvg * frameNumber + t - lastTime) / ++frameNumber;
            if (frameNumber>40)
                frameNumber=1;
            double duration = recording ? Math.max(frameDuration, frameDurationAvg) : frameDurationAvg;
            final int fps = (int) (ONE_BILLION/duration + 0.5d);
            if (fps!=lastFps)
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView tv = findViewById(R.id.text_fps);
                        tv.setText(String.format(Locale.US, "%d FPS", fps));
                        lastFps = fps;
                    }
                });
        }
        lastTime = t;
    }

    @Override
    public Mat onCameraFrame(final Mat inputFrame) {
        final long t = SystemClock.elapsedRealtimeNanos();
        fps(t);
        if (recording && t-timestamp >= frameDuration) {
            frameExec.execute(new Runnable() {
                @Override
                public void run() {
                    MatOfByte buf = new MatOfByte();
                    Imgcodecs.imencode(imgFormat, inputFrame, buf);
                    recorder.record(buf.toArray(), t);
                }
            });
            timestamp = t;
        }
        return inputFrame;
    }

    @Override
    public void onCameraViewStopped() {
        Util.Log.d(TAG, String.format("openCV camera stopped ~ %2.1f (%d) [target=%2.1f])",
                ONE_BILLION/frameDurationAvg,
                (int) (frameDurationAvg/1000000),
                ONE_BILLION/frameDuration));
    }
    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>
}
