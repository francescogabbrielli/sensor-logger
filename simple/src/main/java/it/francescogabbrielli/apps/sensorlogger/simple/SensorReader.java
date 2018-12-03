package it.francescogabbrielli.apps.sensorlogger.simple;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;

import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * An utility class to manage sensor readings in a separate thread
 */
public class SensorReader {

    private final static String TAG = SensorReader.class.getSimpleName();

    private SensorEventListener sensorListener;

    /** The system {@link SensorManager} */
    private final SensorManager sensorManager;

    /** Sensors managed by this class */
    private final TreeSet<Sensor> sensors;

    /** Own thread, where to register sensor listeners */
    private HandlerThread ht;

    private int delay;


    /**
     * Setup the sensor reader with the sensor specified in the preferences
     *
     * @param sensorManager the Android {@link SensorManager}
     * @param prefs the app preferences
     */
    SensorReader(SensorEventListener listener, SensorManager sensorManager, SharedPreferences prefs) {
        this.sensorListener = listener;
        this.sensorManager = sensorManager;
        sensors = new TreeSet<>(new Comparator<Sensor>() {
            @Override
            public int compare(Sensor s1, Sensor s2) {
                return Util.getSensorName(s1).compareTo(Util.getSensorName(s2));
            }
        });
        for (String prefKey : prefs.getAll().keySet())
            if (prefKey.startsWith("pref_sensor_") && !prefKey.endsWith("_length") && prefs.getBoolean(prefKey, false)) {
                Sensor s = sensorManager.getDefaultSensor(Integer.parseInt(prefKey.substring(12)));
                if (s!=null)
                    sensors.add(s);
            }
        delay = Util.getIntPref(prefs, App.SENSORS_SAMPLING_DELAY);

    }

    /**
     * Start reading sensor data in its own as fast as possible
     */
    public void start() {
        if (ht==null) {
            ht = new HandlerThread(getClass().getSimpleName());
            ht.start();
            Handler handler = new Handler(ht.getLooper());
            for (Sensor s : sensors)
                sensorManager.registerListener(sensorListener, s, delay, handler);
        }
    }

    /**
     * Stop reading sensor data
     */
    public void stop() {
        if (ht!=null) {
            ht.quitSafely();
            ht = null;
            for (Sensor s : sensors)
                sensorManager.unregisterListener(sensorListener, s);
        }
    }


}
