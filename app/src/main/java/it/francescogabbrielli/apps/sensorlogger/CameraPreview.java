package it.francescogabbrielli.apps.sensorlogger;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * XXX: all callbacks moved to activty...
 * TODO: refactor?
 */
public class CameraPreview extends SurfaceView {

    public CameraPreview(Context context) {
        super(context);
        SurfaceHolder mHolder = getHolder();
        mHolder.addCallback((SurfaceHolder.Callback) context);
    }

}