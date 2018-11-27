package it.francescogabbrielli.apps.sensorlogger.simple;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class Util {

    private final static String TAG = Util.class.getSimpleName();

    public final static int LOG_IMAGE = 1;
    public final static int LOG_SENSORS = 2;

    public final static String PREF_APP_FOLDER      = "pref_app_folder";
    public final static String PREF_FILENAME_DATA   = "pref_filename_data";
    public final static String PREF_FILENAME_FRAME  = "pref_filename_frame";

    public final static String PREF_FILE            = "pref_file";
    public final static String PREF_FTP             = "pref_ftp";
    public final static String PREF_FTP_ADDRESS     = "pref_ftp_address";
    public final static String PREF_FTP_USER        = "pref_ftp_user";
    public final static String PREF_FTP_PW          = "pref_ftp_pw";
    public final static String PREF_FTP_SKIP        = "pref_ftp_skip";
    public final static String PREF_STREAMING           = "pref_streaming";
    public final static String PREF_STREAMING_PORT      = "pref_streaming_port";
    public final static String PREF_STREAMING_RECORD    = "pref_streaming_record";
    public final static String PREF_LOGGING_RATE        = "pref_logging_rate";
    public final static String PREF_LOGGING_HEADERS     = "pref_logging_headers";
    public final static String PREF_LOGGING_TIME        = "pref_logging_time";
    public final static String PREF_LOGGING_TIMESTAMP   = "pref_logging_timestamp";
    public final static String PREF_LOGGING_TIMESTAMP_FORMAT = "pref_logging_timestamp_format";
    public final static String PREF_LOGGING_CHUNK       = "pref_logging_chunk";

    public final static String PREF_CAPTURE_CAMERA      = "pref_capture_camera";
    public final static String PREF_CAPTURE_IMGFORMAT   = "pref_capture_imgformat";
    public final static String PREF_CAPTURE_SOUND       = "pref_capture_sound";

    public final static String PREF_HELP_RESET          = "pref_help_reset";

    public final static String PREF_ROTATION_X          = "pref_rotation_x";
    public final static String PREF_ROTATION_Y          = "pref_rotation_y";
    public final static String PREF_ROTATION_Z          = "pref_rotation_z";

    public final static String EXTRA_TYPE       = "extra_type";
    public final static String EXTRA_DATA       = "extra_data";
    public final static String EXTRA_FILENAME   = "extra_filename";
    public final static String EXTRA_FOLDER     = "extra_folder";
    public final static String EXTRA_TIMESTAMP  = "extra_timestamp";

    public final static String[] DATA_HEADERS = new String[] {
            "X", "Y", "Z", "Param1", "Param2", "Param3"
    };


    /**
     * Load dedaults from config file (asynchronously)
     *
     * @param context
     *              application context
     */
    public static void loadDefaults(final Context context, final boolean force) {
        new Thread() {
            @Override
            public void run() {
                Properties defaults = new Properties();
                try {
                    defaults.load(context.getAssets().open("defaults.properties"));
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    SharedPreferences.Editor editor = prefs.edit();
                    for (Enumeration e=defaults.propertyNames(); e.hasMoreElements();) {
                        String key = String.valueOf(e.nextElement());
                        if (force || !prefs.contains(key)) {
                            String val = defaults.getProperty(key);
                            //TODO: manage different types?
                            if ("TRUE".equalsIgnoreCase(val) || "FALSE".equalsIgnoreCase(val)) {
                                editor.putBoolean(key, Boolean.parseBoolean(val.toLowerCase()));
                                Log.d("Defaults", key+"="+val);
                            } else
                                editor.putString(key, val);
                        }
                    }
                    editor.apply();
                    Log.d(TAG, "Defaults loaded: "+defaults.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Error loading defaults", e);
                }
            }
        }.start();
    }

    /**
     * Retrieve all available sensors sorted alphabetically
     *
     * @param sensorManager
     *          the sensor manager
     * @return
     *      the list of sensors
     */
    public static List<Sensor> getSensors(SensorManager sensorManager) {
        List<Sensor> sensors = new ArrayList<>();
        if (sensorManager!=null) {
            sensors.addAll(sensorManager.getSensorList(Sensor.TYPE_ALL));
            Collections.sort(sensors, new Comparator<Sensor>() {
                @Override
                public int compare(Sensor s1, Sensor s2) {
                    return Util.getSensorName(s1).compareTo(Util.getSensorName(s2));
                }
            });
        }
        return sensors;
    }

    public static String getSensorName(Sensor sensor) {
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

    public static int getSensorMaxLength(Sensor sensor) {
        int ret = -1;
        for (Method method : Sensor.class.getDeclaredMethods()) {
            if (!method.getName().equals("getMaxLengthValuesArray"))
                continue;
            method.setAccessible(true);
            try {
                ret = Math.min(
                        DATA_HEADERS.length,
                        (Integer) method.invoke(sensor, sensor, Build.VERSION.SDK_INT));
                Log.v(TAG, getSensorName(sensor)+" value length is "+ret);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public static int getIntPref(SharedPreferences prefs, String prefKey) {
        Object val = prefs.getAll().get(prefKey);
        if (val instanceof Boolean)
            return prefs.getBoolean(prefKey, false) ? 1 : 0;
        else
            return val!=null ? Integer.valueOf(val.toString()) : 0;
    }

    public static long getLongPref(SharedPreferences prefs, String prefKey) {
        long ret = 0;
        try {
            ret = Long.parseLong(prefs.getString(prefKey, "0"));
        } catch(Exception e) {}
        return ret;
    }

    public static double getDoublePref(SharedPreferences prefs, String key) {
        return Double.valueOf(prefs.getString(key, "0"));
    }
}
