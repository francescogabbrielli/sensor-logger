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
import android.util.SparseIntArray;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
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

    private final int delay;

    /** Latest sensor readings ({@link SensorEvent}) for each sensor */
    private final SparseArray<SensorEvent> readings;

    /** Own thread, where to register sensor listeners */
    private HandlerThread ht;

    private Rotation rotation;

    private boolean flagTime;

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
        delay = Util.getIntPref(prefs, App.SENSORS_SAMPLING_DELAY);
        for (String prefKey : prefs.getAll().keySet())
            if (prefKey.startsWith("pref_sensor_") && !prefKey.endsWith("_length") && prefs.getBoolean(prefKey, false)) {
                Sensor s = sensorManager.getDefaultSensor(Integer.parseInt(prefKey.substring(12)));
                if (s!=null)
                    sensors.add(s);
            } else if (prefKey.startsWith("pref_sensor_") && prefKey.endsWith("_length")) {
                dataLengths.put(
                        Integer.parseInt(prefKey.substring(12, prefKey.indexOf("_", 12))),
                        Util.getIntPref(prefs, prefKey));
            }
        rotation = Rotation.getRotation(
                Util.getIntPref(prefs, Util.PREF_ROTATION_X),
                Util.getIntPref(prefs, Util.PREF_ROTATION_Y),
                Util.getIntPref(prefs, Util.PREF_ROTATION_Z)
        );
        flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);

    }

    /** Lengths of each sensor data */
    private final SparseIntArray dataLengths = new SparseIntArray();

    /**
     * Try to read a sensor data length
     *
     * @param sensor the sensor
     * @return the dimensionality of its data
     */
    int getSensorDataLength(Sensor sensor) {
        int ret = dataLengths.get(sensor.getType());
        if (ret == 0) {
            ret = Util.getSensorMaxLength(sensor);
            dataLengths.put(sensor.getType(), ret);
        }
        return ret;
    }

    String readHeaders() {
        StringBuilder builder = new StringBuilder();
        if (flagTime)
            builder.append("Timestamp,");
        for (Sensor s : sensors) {
            int l = getSensorDataLength(s);
            for (int i = 0; i<l; i++) {
                builder.append(Util.getSensorName(s));
                if (l>1)
                    builder.append(" ").append(Util.DATA_HEADERS[i]);
                builder.append(",");
            }
        }
        return builder.length()>0
                ? builder.deleteCharAt(builder.length()-1).append('\n').toString() : "";
    }

    /**
     * Start reading sensor data in its own thread
     */
    public void start() {
        if (ht==null) {
            ht = new HandlerThread(getClass().getSimpleName());
            ht.start();
            Handler handler = new Handler(ht.getLooper());
            for (Sensor s : sensors)
                sensorManager.registerListener(this, s, delay, handler);
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

    /**
     * Read the current sensor data (the latest available for each sensor)
     *
     * @param timestamp the timestamp of the reading request
     * @return a line already formatted to write in the .csv file
     */
    String readSensors(long timestamp) {
        StringBuilder buffer = new StringBuilder();
//        StringBuilder headers = null;
//        if (flagHeaders && counter == 0) {
//            headers = new StringBuilder();
//            if (flagTime)
//                headers.append("Frame Time,");
//        }
        if (flagTime) {
            buffer.append(String.valueOf(timestamp));
            buffer.append(",");
        }
        for (SensorEvent e : this) {//iterate through accelerometer and gyroscope (and magnetometer, etc)
            int l = Math.min(e.values.length, getSensorDataLength(e.sensor));
            float[] values = l>=3 ? rotation.multiply(e.values) : e.values;
            for (int i = 0; i < l; i++) {//iterate through x, y, z (and what else... if a sensor has more than 3 values)
//                if (flagHeaders && counter==0) {
//                    headers.append(Util.getSensorName(e.sensor));
//                    if (l>1)
//                        headers.append(" ").append(Util.DATA_HEADERS[i]);
//                    headers.append(",");
//                }
                buffer.append(String.format(Locale.US, "%2.5f,", values[i]));
            }
        }
        if (buffer.length() > 0)
            buffer.deleteCharAt(buffer.length() - 1);
//        if (headers!=null && headers.length() > 0) {
//            headers.replace(headers.length() - 1, headers.length(), "\n");
//            buffer.insert(0, headers.toString());
//        }

        //Util.Log.v(TAG, "Sensor reading: "+buffer);

        return buffer.append('\n').toString();
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
