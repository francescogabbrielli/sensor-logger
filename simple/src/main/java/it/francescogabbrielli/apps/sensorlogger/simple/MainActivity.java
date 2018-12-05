package it.francescogabbrielli.apps.sensorlogger.simple;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.francescogabbrielli.streaming.server.StreamingCallback;

public class MainActivity extends OpenCVActivity implements ServiceConnection, StreamingCallback {

    private final static String TAG = OpenCVActivity.class.getSimpleName();

    private final static double ONE_BILLION = 1000000000d;

    private SharedPreferences prefs;
    private String imageExt;
    private double frameRate;

    private StreamingService streamingService;
    private boolean bound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        imageExt = prefs.getString(App.STREAMING_IMAGE_EXT, ".jpg");
        frameRate = Util.getIntPref(prefs, App.FRAME_RATE);
        startService(new Intent(this, StreamingService.class));
//        sensorReader = new SensorReader(t(SensorManager) getSystemService(SENSOR_SERVICE), prefs);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_record:
                onRecPressed();
                return true;
//            case R.id.action_settings:
//                startActivity(new Intent(this, SettingsActivity.class));
//                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
        int orientation = getResources().getConfiguration().orientation;
        if (orientation==2)
            loadOpenCVLibrary();
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        super.onResume();

//        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
//        if (pm==null) {
//            Toast.makeText(this, "No power manager?", Toast.LENGTH_LONG).show();
//            finish();
//            return;
//        }
//        if (pm.isInteractive())
//            Log.v(TAG, "onGoodResume");

    }


    @Override
    protected void onPause() {
        Log.v(TAG, "onPause");
        //unbindService(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        super.onStop();
    }


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
//        if (SystemClock.elapsedRealtime()-lastPressed>750) {
//            lastPressed = SystemClock.elapsedRealtime();
//            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//            if (!recPressed && !recording) {
//                recPressed = true;
//                if (mgr!=null)
//                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
//                showPrepareAnimation();
//            } else {
//                if (mgr!=null)
//                    mgr.playSoundEffect(AudioManager.FX_KEY_CLICK);
//                stopRecording(R.string.toast_recording_stop);
//            }
//        }
    }

    @Override
    protected List<String> onVerifyPermissions() {
        List<String> requests = new LinkedList<>();
        if (prefs.getBoolean(Util.PREF_CAPTURE_CAMERA, false))
            requests.add(Manifest.permission.CAMERA);
        if (Util.getIntPref(prefs, Util.PREF_FILE)>0)
            requests.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (Util.getIntPref(prefs, Util.PREF_FTP)>0)
            requests.add(Manifest.permission.INTERNET);
        return requests;
    }

    @Override
    protected void onPermissionGranted(String permission) {
        Log.d(TAG, "Permission granted: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            prefs.edit().putBoolean(Util.PREF_CAPTURE_CAMERA, true).apply();
        }
    }

    @Override
    protected void onPermissionDenied(String permission) {
        Log.d(TAG, "Permission denied: " + permission);
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

    boolean streaming;
    double frameRateAvg;//milliseconds
    long frameNumber, lastTime, frameDuration, timestamp;
    int lastFps;
    private ExecutorService frameExec;

    @Override
    public void onCameraViewStarted(int width, int height) {
        super.onCameraViewStarted(width, height);
        bindService(new Intent(this, StreamingService.class), this, Context.BIND_AUTO_CREATE);
        frameExec = Executors.newSingleThreadExecutor();
        frameRateAvg = frameRate;
        frameDuration = (long) (0.5d + ONE_BILLION / frameRate);
        frameNumber = 1;
        lastTime = 0;
    }

    private void fps(long t) {
        if (lastTime>0) {
            frameRateAvg = frameNumber * frameRateAvg /
                    ((frameNumber-1) + frameRateAvg * ((t-lastTime) / ONE_BILLION));
            if (frameNumber++>40)
                frameNumber=2;
            final int fps = (int) (0.5d + Math.min(frameRateAvg, frameRate));
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
        fps(t);//compute fps and show em on screen
        if (streaming && t-timestamp >= frameDuration) {// check if enough time is passed to record the next frame (during recording)
            frameExec.execute(new Runnable() {
                @Override
                public void run() {
                    MatOfByte buf = new MatOfByte();//buffer: is the way OpenCV encodes bitmaps
                    Mat converted = new Mat(inputFrame.size(), inputFrame.type());
                    Imgproc.cvtColor(inputFrame, converted, Imgproc.COLOR_RGB2BGRA);//convert colors
                    Imgcodecs.imencode(imageExt, converted, buf);//encode the frame into the buffer buf in the format specified by imgFormat
                    if (bound)
                        streamingService.streamImage(buf.toArray(), t);
                }
            });
            timestamp = t;
        }
        return inputFrame;
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, String.format("Dumped %s frames", frameExec.shutdownNow().size()));
        unbindService(this);
        super.onCameraViewStopped();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "Service disconnected");
        if (bound && streamingService!=null)
            streamingService.stop();
        streamingService = null;
        bound = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "Service connected");
        StreamingService.Binder binder = (StreamingService.Binder) service;
        streamingService = binder.getService();
        streamingService.start(this);
        bound = true;
    }

    @Override
    public void onStartStreaming() {
        streaming = true;
    }

    @Override
    public void onStopStreaming() {
        streaming = false;
    }

}
