package it.francescogabbrielli.apps.sensorlogger;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
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

    public CameraHandlerThread(Context context) {
        super("CameraHandlerThread");
        this.context = context;
        start();
        handler = new Handler(getLooper());
    }

    public void openCamera(final SurfaceHolder holder) {
        handler.post(new Runnable() {
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
                } catch (IOException e) {
                    Log.e("Camera", "Error opening camera", e);
                }
            }
        });
    }

    public Camera getCamera() {
        return camera;
    }
}
