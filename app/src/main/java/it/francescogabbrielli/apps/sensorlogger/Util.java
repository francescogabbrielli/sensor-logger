package it.francescogabbrielli.apps.sensorlogger;


import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Util {

    private final static String TAG = Util.class.getSimpleName();

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

    private final static Map<Sensor, Integer> DATA_LENGTHS = new LinkedHashMap<>();
    public final static String[] DATA_HEADERS = new String[] {
            "X", "Y", "Z", "Param1", "Param2", "Param3"
    };


    /**
     * Load dedaults from config file (asynchronously)
     *
     * @param context
     *              application context
     * @return
     *          the default properties passed (still not populated!) (TODO: implement callback)
     */
    public static Properties loadDefaults(final Context context) {
        final Properties defaults = new Properties();
        new Thread() {
            @Override
            public void run() {
                try{
                    defaults.load(context.getAssets().open("defaults.properties"));
                    Log.d(TAG, "Defaults loaded: "+defaults.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Error loading defaults", e);
                }
            }
        }.start();
        return defaults;
    }

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
        Integer ret = DATA_LENGTHS.get(sensor);
        if (ret==null) {
            Method[] methodList = Sensor.class.getDeclaredMethods();
            int m_count = methodList.length;
            for (int j = 0; j < m_count; j++) {
                Method method = methodList[j];
                if (!method.getName().equals("getMaxLengthValuesArray"))
                    continue;
                method.setAccessible(true);
                try {
                    ret = Math.min(
                            DATA_HEADERS.length,
                            (Integer) method.invoke(sensor, sensor, Build.VERSION.SDK_INT));
                    Log.v(TAG, getSensorName(sensor)+" value length is "+ret);
                    DATA_LENGTHS.put(sensor, ret);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return ret!=null ? ret : -1;
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
