package it.francescogabbrielli.apps.sensorlogger;


import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Util {

    public final static String PREF_RECORDING       = "pref_recording";

    public final static String PREF_FTP             = "pref_ftp";
    public final static String PREF_FTP_ADDRESS     = "pref_ftp_address";
    public final static String PREF_FTP_USER        = "pref_ftp_user";
    public final static String PREF_FTP_PW          = "pref_ftp_pw";
    public final static String PREF_LOGGING_RATE    = "pref_logging_rate";
    public final static String PREF_LOGGING_UPDATE  = "pref_logging_update";
    public final static String PREF_LOGGING_LENGTH  = "pref_logging_length";
    public final static String PREF_LOGGING_HEADERS = "pref_logging_headers";
    public final static String PREF_LOGGING_TIME    = "pref_logging_time";
    public final static String PREF_LOGGING_TIMESTAMP = "pref_logging_timestamp";
    public final static String PREF_LOGGING_SAVE    = "pref_logging_save";

    public final static String PREF_CAPTURE_CAMERA  = "pref_capture_camera";
    public final static String PREF_CAPTURE_SOUND   = "pref_capture_sound";

    /**
     * Retrieve all available sensors sorted alphabetically
     *
     * @param context
     *          the context
     * @return
     *      the list of sensors
     */
    public final static List<Sensor> getSensors(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = new ArrayList<>(sensorManager.getSensorList(Sensor.TYPE_ALL));
        Collections.sort(sensors, new Comparator<Sensor>() {
            @Override
            public int compare(Sensor s1, Sensor s2) {
                return Util.getSensorName(s1).compareTo(Util.getSensorName(s2));
            }
        });
        return sensors;
    }

    public final static String getSensorName(Sensor sensor) {
        String name = "";
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH)
            name = sensor.getStringType();
        if (name.startsWith("android.sensor."))
            name = Character.toUpperCase(name.charAt(15))
                    + name.substring(16).replaceAll("_"," ");
        if("".equals(name))
            name = sensor.getName();
        return name;
    }

    public final static int getSensorMaxLength(Sensor sensor) {
        Method[] methodList = Sensor.class.getDeclaredMethods();
        int m_count = methodList.length;
        for (int j = 0; j < m_count; j++) {
            Method method = methodList[j];
            if (!method.getName().equals("getMaxLengthValuesArray"))
                continue;
            method.setAccessible(true);
            try {
                int values_length = (Integer) method.invoke(sensor, sensor, Build.VERSION.SDK_INT);
//                Log.e(TAG,"value length is "+values_length);
                return values_length;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return -1;
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }
}
