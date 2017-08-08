package it.francescogabbrielli.apps.sensorlogger;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/**
 * XXX: all callbacks moved to activty...
 * TODO: refactor
 */
public class CameraPreview extends SurfaceView {

    public CameraPreview(MainActivity activity) {
        super(activity);
        SurfaceHolder mHolder = getHolder();
        mHolder.addCallback(activity);
    }

}