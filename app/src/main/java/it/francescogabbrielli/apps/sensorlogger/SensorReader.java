package it.francescogabbrielli.apps.sensorlogger;

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

public class SensorReader implements SensorEventListener, Iterable<SensorEvent> {

    private final static String TAG = SensorReader.class.getSimpleName();

    private final SensorManager sensorManager;
    private final TreeSet<Sensor> sensors;

    private final SparseArray<SensorEvent> readings;
    private HandlerThread ht;
    private Handler handler;

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
            if (prefKey.startsWith("pref_sensor_") && prefs.getBoolean(prefKey, false))
                sensors.add(sensorManager.getDefaultSensor(Integer.parseInt(prefKey.substring(12))));
    }

    public void start() {
        if (ht==null) {
            ht = new HandlerThread(getClass().getSimpleName());
            ht.start();
            Handler handler = new Handler(ht.getLooper());
            for (Sensor s : sensors)
                sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, handler);
        }
    }

    public void stop() {
        if (ht!=null) {
            ht.quitSafely();
            ht = null;
            for (Sensor s : sensors)
                sensorManager.unregisterListener(this, s);
        }
    }

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

    public  SensorEvent readSensor(Sensor sensor) {
        return readings.get(sensor.getType());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        SensorEvent e = readSensor(sensor);
        Log.w(TAG, "Accuracy changed for " + Util.getSensorName(sensor)+": "
                +(e!=null ? e.accuracy : -1)+"->"+accuracy);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        readings.put(event.sensor.getType(), event);
    }

}
