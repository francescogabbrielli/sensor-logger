package it.francescogabbrielli.apps.sensorlogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 * Manage camera's "heavy" operations in a handler thread
 */
public class CameraHandlerThread extends HandlerThread {

    private Context context;

    private Handler handler;

    private Camera camera;

    private boolean on;

    public CameraHandlerThread(Context context) {
        super("CameraHandlerThread");
        this.context = context;
        start();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        on = prefs.getBoolean(Util.PREF_CAPTURE_CAMERA, false);
        handler = new Handler(getLooper());
    }

    public void openCamera(final SurfaceHolder holder, final Runnable callback) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    camera = Camera.open();
                    Camera.Parameters params = camera.getParameters();
                    params.setPreviewSize(1280,720);
                    params.setPictureSize(1280,720);
                    camera.setParameters(params);
//                    Util.setCameraDisplayOrientation(MainActivity.this, 0, camera);XXX: rotation?
                    camera.setPreviewDisplay(holder);
                    camera.startPreview();
                    if (callback!=null)
                        callback.run();
                } catch (IOException e) {
                    Log.e("Camera", "Error opening camera", e);
                }
            }
        }, 100);
    }

    public void closeCamera(){
        if (camera!=null)
            camera.release();
        camera = null;
    }

    public Camera getCamera() {
        return camera;
    }

    //XXX
    public void restart() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    camera.startPreview();
                } catch (Throwable t) {
                    Log.e("Camera", "Restarting preview?", t);
                }
            }
        },100);
    }
}
