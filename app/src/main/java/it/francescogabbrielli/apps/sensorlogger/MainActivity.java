package it.francescogabbrielli.apps.sensorlogger;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
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
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

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

import it.francescogabbrielli.streaming.server.StreamingServer;


public class MainActivity extends AppCompatActivity implements
        CameraBridgeViewBase.CvCameraViewListener,
        LoaderCallbackInterface {

    private final static String TAG = MainActivity.class.getSimpleName();
    private final static double ONE_BILLION = 1000000000d;

    private final static int REQUEST_PERMISSIONS = 3;

    private JavaCameraView camera;//OpenCV Camera View

    private SharedPreferences prefs;

    private Recorder recorder;
    private long timestamp, frameDuration;
    private String imgFormat;

    @Override
    public void onManagerConnected(int status) {
        Util.Log.d(TAG, "OpenCV status:" +status);
        switch (status) {
            case LoaderCallbackInterface.SUCCESS:
                verifyPermissions();
                break;
        }
    }

    @Override
    public void onPackageInstall(int operation, InstallCallbackInterface callback) {
        Util.Log.d(TAG, "OpenCV install:" +operation);
        switch (operation) {
            case InstallCallbackInterface.NEW_INSTALLATION:
                callback.install();
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        //start the service
        startService(new Intent(this, LoggingService.class));

        recorder = new Recorder(this,
                new SensorReader((SensorManager) getSystemService(SENSOR_SERVICE), prefs),
                new StreamingServer());
    }

    /**
     * Setup the camera; can be called anytime
     */
    private void onSetupCamera() {
        camera = findViewById(R.id.camera_view);
        camera.setVisibility(SurfaceView.VISIBLE);
        camera.setCvCameraViewListener(this);
        camera.enableView();
        frameDuration = Util.getLongPref(prefs, Util.PREF_LOGGING_RATE);
        imgFormat = prefs.getString(Util.PREF_CAPTURE_IMGFORMAT, ".png");
    }

    private boolean goodPause = true, goodResume = false, paused = true;

    @Override
    protected void onStart() {

        super.onStart();

        if (!OpenCVLoader.initDebug()) {
            Util.Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, this);
        } else {
            Util.Log.d("OpenCV", "OpenCV library found inside package. Using it!");
            onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        goodPause = true;
    }

    @Override
    protected void onResume() {

        super.onResume();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        goodResume = pm!=null && pm.isInteractive();
        Util.Log.v(TAG, "onResume: "+goodResume);

        if (!goodResume || !goodPause)
            return;

        paused = false;

        if (Util.getIntPref(prefs, Util.PREF_STREAMING)>0 && prefs.getBoolean(Util.PREF_STREAMING_RECORD, false))
            recorder.startStreaming();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

    }

    @Override
    protected void onPause() {

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        goodPause = pm==null || !pm.isInteractive();
        paused = true;
        Util.Log.v(TAG, "onPause: "+goodPause);

        super.onPause();

        if (!goodResume || !goodPause)
            return;

        //stop the streaming server
        if (Util.getIntPref(prefs, Util.PREF_STREAMING) > 0 && prefs.getBoolean(Util.PREF_STREAMING_RECORD, false))
            recorder.stopStreaming();

        stopRecording(R.string.toast_recording_interrupted);

        if (camera != null)
            camera.disableView();
        if (toneGenerator != null)
            toneGenerator.release();
        toneGenerator = null;

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recorder.dispose();
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

    //<editor-fold desc="Start/Stop management">
    // --------------------------------- START / STOP MANAGEMENT  ----------------------------------
    //

    private boolean recPressed, recording;
    private long lastPressed;

//    @TargetApi(Build.VERSION_CODES.M)
//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        if(event.getAction() == MotionEvent.ACTION_DOWN) {
//            event.setAction(MotionEvent.ACTION_BUTTON_RELEASE);
//            return onTrackballEvent(event);
//        }
//        return super.onTouchEvent(event);
//    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_BUTTON_RELEASE) {
            onRecPressed();
            //return true;
        }
        return super.onTrackballEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_VOLUME_DOWN || keyCode==KeyEvent.KEYCODE_VOLUME_UP) {
            onRecPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void onRecPressed() {
        if (SystemClock.elapsedRealtime()-lastPressed>750) {
            lastPressed = SystemClock.elapsedRealtime();
            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (!recPressed && !recording) {
                recPressed = true;
                if (mgr!=null)
                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                showPrepareAnimation();
            } else {
                if (mgr!=null)
                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
                stopRecording(R.string.toast_recording_stop);
            }
        }
    }

    private ScheduledExecutorService animExec;
    private ScheduledFuture prepareFuture, recordingFuture;
    private ToneGenerator toneGenerator;

    private void startTone(int toneType, int duration) {
        if (toneGenerator==null && prefs.getBoolean(Util.PREF_CAPTURE_SOUND, false))
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        if (toneGenerator!=null)
            toneGenerator.startTone(toneType, duration);
    }

    private void showPrepareAnimation() {
        if (animExec==null)
            animExec = Executors.newSingleThreadScheduledExecutor();
        try {
            prepareFuture = animExec.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView t = findViewById(R.id.anim_prepare);
                            int val = 4;
                            try { val = Integer.parseInt(t.getText().toString()); } catch(Exception e) {}
                            if (--val>0) {
                                startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                                t.setText(String.valueOf(val));
                            } else {
                                startRecording();
                            }
                        }
                    });
                }
            },0,1, TimeUnit.SECONDS);
        } catch(Exception e) {
            Util.Log.e(TAG, "Cannot prepare animation", e);
        }
    }
    private void hidePrepareAnimation() {
        if (prepareFuture !=null)
            prepareFuture.cancel(false);
        TextView t = findViewById(R.id.anim_prepare);
        t.setText("");
    }

    private void showBlinkingAnimation() {
        timestamp = SystemClock.elapsedRealtimeNanos()- frameDuration;
        if (animExec==null)
            animExec = Executors.newSingleThreadScheduledExecutor();
        try {
            recordingFuture = animExec.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView i = findViewById(R.id.img_record);
                            i.setVisibility(i.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                        }
                    });
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            Util.Log.e(TAG, "Cannot start rec animation", e);
        }
    }

    private void hideBlinkingAnimation() {
        if (animExec==null)
            animExec = Executors.newSingleThreadScheduledExecutor();
        if (recordingFuture!=null)
            recordingFuture.cancel(false);
        try {
            animExec.schedule(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ImageView i = findViewById(R.id.img_record);
                            i.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            }, 0, TimeUnit.SECONDS);
        } catch(Exception e) {
            Util.Log.e(TAG, String.format(Locale.US,
                    "Cannot stop rec animation [SHUTDOWN: %s, TERMINATED: %s]",
                    animExec.isShutdown(), animExec.isTerminated()), e);
        }
    }

    /** Start recording: must be called on UI thread */
    void startRecording() {
        if (recording || paused)
            return;
        startTone(ToneGenerator.TONE_CDMA_PIP, 300);
        recPressed = true;
        recording = true;
        recorder.start();
        hidePrepareAnimation();
        showBlinkingAnimation();
    }

    /** Stop recording: must be called on UI thread */
    void stopRecording(int msg) {
        if (!recording && !recPressed)
            return;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        startTone(ToneGenerator.TONE_CDMA_CONFIRM, 300);
        recPressed = false;
        recording = false;
        recorder.stop();
        hidePrepareAnimation();
        hideBlinkingAnimation();
    }
    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>

    //<editor-fold desc="Permissions">
    // ----------------------------------- PERMISSIONS MANAGEMENT ----------------------------------
    //
    private boolean requestingPermissions = false;

    /**
     * Checks if the app has the required permissions, as per current setttings.
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions.
     */
    private void verifyPermissions() {

        // List all permissions that are involved in activated features
        List<String> requests = new LinkedList<>();
        if (prefs.getBoolean(Util.PREF_CAPTURE_CAMERA, false))
            requests.add(Manifest.permission.CAMERA);
        if (Util.getIntPref(prefs, Util.PREF_FILE)>0)
            requests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Util.getIntPref(prefs, Util.PREF_FTP)>0)
            requests.add(Manifest.permission.INTERNET);

        // Check if we have requested permission
        for (ListIterator<String> it = requests.listIterator(); it.hasNext(); ) {
            String permission = it.next();
            int check = ActivityCompat.checkSelfPermission(this, permission);
            if (check != PackageManager.PERMISSION_GRANTED) {
                //do nothing
            } else {
                onPermissionOk(permission);
                it.remove();
            }
        }

        // Request missing permissions
        if (!requestingPermissions &&  !requests.isEmpty()) {
            requestingPermissions = true;
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this,
                    requests.toArray(new String[0]),
                    REQUEST_PERMISSIONS
            );
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        requestingPermissions = false;
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
            onSetupCamera();
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
        //XXX: don't really need to set these...
        if (Manifest.permission.CAMERA.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_CAPTURE_CAMERA, true).apply();
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            //prefs.edit().putBoolean(Util.PREF_FILE, true).apply();
        } else if (Manifest.permission.INTERNET.equals(permission)) {
            //prefs.edit().putBoolean(Util.PREF_FTP, true).apply();
        }
    }

    protected void onPermissionDenied(String permission) {
        Util.Log.d(TAG, "Permission denied: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            prefs.edit()
                    .putBoolean(Util.PREF_CAPTURE_CAMERA, false)
                    .putString(Util.PREF_STREAMING, "0").apply();
        } else if (Manifest.permission.INTERNET.equals(permission)) {
            prefs.edit().putString(Util.PREF_FTP, "0").apply();
        } else if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
            prefs.edit().putString(Util.PREF_FILE, "0").apply();
        }
    }
    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>

    //<editor-fold desc="OpenCV Callbacks">
    // ---------------------------------- OPENCV CAMERA CALLBACKS ----------------------------------
    //    @Override
    public void onCameraViewStarted(int width, int height) {
        Util.Log.d(TAG, String.format(Locale.US, "openCV camera started: %dx%d", width, height));
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

    /**
     * Receive a frame from OpenCV whenever is available
     *
     * @param inputFrame the frame
     * @return the (eventually modified) frame
     */
    @Override
    public Mat onCameraFrame(final Mat inputFrame) {
        final long t = SystemClock.elapsedRealtimeNanos();
        fps(t);//compute fps and show em on screen
        if (recording && t-timestamp >= frameDuration) {// check if enough time is passed to record the next frame (during recording)
            frameExec.execute(new Runnable() {
                @Override
                public void run() {
                    MatOfByte buf = new MatOfByte();//buffer: is the way OpenCV encodes bitmaps
                    Mat converted = new Mat(inputFrame.size(), inputFrame.type());
                    Imgproc.cvtColor(inputFrame, converted, Imgproc.COLOR_RGB2BGRA);//convert colors
                    Imgcodecs.imencode(imgFormat, converted, buf);//encode the frame into the buffer buf in the format specified by imgFormat
                    if (recording)
                        recorder.record(buf.toArray(), t);//record the frame
                }
            });
            timestamp = t;
        }
        return inputFrame;
    }

    @Override
    public void onCameraViewStopped() {
        Util.Log.d(TAG, String.format(Locale.US, "openCV camera stopped ~ %2.1f (%d) [target=%2.1f])",
                ONE_BILLION/frameDurationAvg,
                (int) (frameDurationAvg/1000000),
                ONE_BILLION/frameDuration));
    }
    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>

}
