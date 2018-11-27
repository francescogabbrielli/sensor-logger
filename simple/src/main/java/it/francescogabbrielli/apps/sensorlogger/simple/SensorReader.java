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
public class SensorReader implements SensorEventListener, Iterable<SensorEvent> {

    private final static String TAG = SensorReader.class.getSimpleName();

    /** The system {@link SensorManager} */
    private final SensorManager sensorManager;

    /** Sensors managed by this class */
    private final TreeSet<Sensor> sensors;

    /** Latest sensor readings ({@link SensorEvent}) for each sensor */
    private final SparseArray<SensorEvent> readings;

    /** Own thread, where to register sensor listeners */
    private HandlerThread ht;


    /**
     * Setup the sensor reader with the sensor specified in the preferences
     *
     * @param sensorManager the Android {@link SensorManager}
     * @param prefs the app preferences
     */
    SensorReader(SensorManager sensorManager, SharedPreferences prefs) {
        this.sensorManager = sensorManager;
        readings = new SparseArray<>();
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
                sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, handler);
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
                sensorManager.unregisterListener(this, s);
        }
    }

    /**
     * Iterate through all the specified sensors
     *
     * @return an iterator of {@link SensorEvent}s
     */
    @NonNull
    @Override
    public Iterator<SensorEvent> iterator() {
        return new Iterator<SensorEvent>() {
            private Iterator<Sensor> it = sensors.iterator();
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }
            @Override
            public SensorEvent next() {
                return readSensor(it.next());
            }
        };
    }

    /**
     * Read the latest available data for a sensor.
     *
     * @param sensor the sensor
     * @return the last {@link SensorEvent} for that sensor
     */
    public SensorEvent readSensor(Sensor sensor) {
        return readings.get(sensor.getType());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        SensorEvent e = readSensor(sensor);
        Log.w(TAG, "Accuracy changed for " + Util.getSensorName(sensor)+": "
                +(e!=null ? e.accuracy : -1)+"->"+accuracy);
    }

    /**
     * Store the last {@link SensorEvent} for each sensor
     * @param event the event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        readings.put(event.sensor.getType(), event);
    }

    public void dispose() {
        stop();
    }

}
