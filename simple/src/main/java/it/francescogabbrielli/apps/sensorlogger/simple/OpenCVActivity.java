package it.francescogabbrielli.apps.sensorlogger.simple;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

public abstract class OpenCVActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener {

    private final static String TAG = OpenCVActivity.class.getSimpleName();

    private final static int REQUEST_PERMISSIONS = 3;

    protected JavaCameraView camera;

    /**
     * Call this to load OpenCV
     */
    protected void loadOpenCVLibrary() {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, this);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            verifyPermissions();
            //onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    /**
     * Setup the camera; can be called anytime
     */
    private void onSetupCamera() {
        camera = findViewById(R.id.camera_view);
        camera.setVisibility(SurfaceView.VISIBLE);
        camera.setCvCameraViewListener(this);
        camera.enableView();
    }

    //<editor-fold desc="Permissions">
    // ----------------------------------- PERMISSIONS MANAGEMENT ----------------------------------
    //
    private boolean requestingPermissions = false;

    protected abstract List<String> onVerifyPermissions();


    /**
     * Checks if the app has the required permissions, as per current setttings.
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions.
     */
    private void verifyPermissions() {

        List<String> requests = onVerifyPermissions();

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
        if (!requestingPermissions && !requests.isEmpty()) {
            Log.d(TAG, "Request permissions: "+new LinkedList<>(requests));
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
                for (int i = 0; i < grantResults.length; i++) {
                    Log.v(TAG, "Request permission: " + permissions[i]);
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        onPermissionGranted(permissions[i]);
                        onPermissionOk(permissions[i]);
                    } else {
                        Toast.makeText(this, R.string.alert_permission_denied, Toast.LENGTH_LONG).show();
                        onPermissionDenied(permissions[i]);
                    }
                }
                break;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onPermissionOk(String permission) {
        Log.d(TAG, "Permission ok: " + permission);
        if (Manifest.permission.CAMERA.equals(permission)) {
            onSetupCamera();
        }
    }

    protected abstract void onPermissionGranted(String permission);

    protected abstract void onPermissionDenied(String permission);

    //
    // ----------------------------------- ---------------------- ----------------------------------
    //</editor-fold>

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, String.format(Locale.US, "OpenCV camera started: %dx%d", width, height));
    }

    @Override
    public Mat onCameraFrame(Mat inputFrame) {
        return inputFrame;
    }

    @Override
    public void onCameraViewStopped() {
        Log.v(TAG, "OpenCV camera stopped");
    }

}
