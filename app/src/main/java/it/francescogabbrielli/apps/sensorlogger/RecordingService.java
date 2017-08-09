package it.francescogabbrielli.apps.sensorlogger;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */
public class RecordingService extends IntentService {

    private final static String TAG = RecordingService.class.getSimpleName();

    public static final String ACTION_START     = "it.francescogabbrielli.apps.sensorlogger.action.START";
    public static final String ACTION_STOP      = "it.francescogabbrielli.apps.sensorlogger.action.STOP";

    public static final String BROADCAST_SENSORS_DATA       = "it.francescogabbrielli.apps.sensorlogger.action.BROADCAST_SENSORS_DATA";
    public static final String EXTRA_SENSORS_DATA           = "it.francescogabbrielli.apps.sensorlogger.extra.SENSORS_DATA";
    public static final String BROADCAST_RECORDING_ERROR    = "it.francescogabbrielli.apps.sensorlogger.action.BROADCAST_RECORDING_ERROR";
    public static final String EXTRA_RECORDING_ERROR        = "it.francescogabbrielli.apps.sensorlogger.extra.RECORDING_ERROR";


    private Properties defaults;

    public RecordingService() {
        super("RecordingService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        defaults = new Properties();
        try{
            defaults.load(getAssets().open("defaults.properties"));
            Log.d(TAG, "Defaults loaded: "+defaults.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error loading defaults", e);
        }
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startRecording(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    /**
     * Starts this service to perform action Baz with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void stopRecording(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START.equals(action)) {
//                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
//                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleStart();
            } else if (ACTION_STOP.equals(action)) {
//                final String param1 = intent.getStringExtra(EXTRA_PARAM1);
//                final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleStop();
            }
        }
    }

    class BufferLine {
        long time;
        String line;
        BufferLine(long time, StringBuilder b) {
            this.time = time;
            this.line = b.toString();
        }
        @Override
        public String toString() {
            return line;
        }
        public byte[] toByteArray() { return line.getBytes();}
    }

    private void handleStart() {

        final SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Set<Sensor> sensors = new TreeSet<>(new Comparator<Sensor>() {
            @Override
            public int compare(Sensor s1, Sensor s2) {
                return Util.getSensorName(s1).compareTo(Util.getSensorName(s2));
            }
        });

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        for (String prefKey : prefs.getAll().keySet())
            if (prefKey.startsWith("pref_sensor_") && prefs.getBoolean(prefKey, false))
                sensors.add(sensorManager.getDefaultSensor(Integer.parseInt(prefKey.substring(12))));

        if (!prefs.getBoolean(Util.PREF_RECORDING, false)) {

            final Map<Integer, SensorEvent> readings = new HashMap<>();
            final SensorEventListener listener = new SensorEventListener() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    readings.put(event.sensor.getType(), event);
                }
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {

                }
            };

            for (Sensor s : sensors)
                if (s!=null)
                    sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_FASTEST);

            prefs.edit().putBoolean(Util.PREF_RECORDING, true).commit();

            new Thread() {

                public void run() {

                    LinkedList<BufferLine> buffer = new LinkedList<>();

                    long start = SystemClock.elapsedRealtime();
                    long lastWrite = start;
                    long delay = Long.parseLong(
                            prefs.getString(Util.PREF_LOGGING_RATE,
                                    defaults.getProperty(Util.PREF_LOGGING_RATE)));
                    long interval = Long.parseLong(
                            prefs.getString(Util.PREF_LOGGING_UPDATE,
                                    defaults.getProperty(Util.PREF_LOGGING_UPDATE)));
                    long length = Long.parseLong(
                            prefs.getString(Util.PREF_LOGGING_LENGTH,
                                    defaults.getProperty(Util.PREF_LOGGING_LENGTH)));

                    boolean flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);
                    StringBuilder bb = new StringBuilder();
                    if (prefs.getBoolean(Util.PREF_LOGGING_HEADERS, false)) {
                        if (flagTime)
                            bb.append("Time");
                        for (Sensor s : sensors) {
                            String n = Util.getSensorName(s);
                            int l = Util.getSensorMaxLength(s);
                            for (int i=0;i<l;i++)
                                bb.append(",").append(n).append(" ").append(Util.DATA_HEADERS[i]);
                        }
                        bb.append('\n');
                        if (!flagTime)
                            bb.deleteCharAt(0);
                    }


                    try {

                        while (prefs.getBoolean(Util.PREF_RECORDING, false)) {

                            long t = SystemClock.elapsedRealtime();

                            if (t - lastWrite > interval) {

                                for (ListIterator<BufferLine> it = buffer.listIterator(); it.hasNext(); ) {
                                    BufferLine bl = it.next();
                                    if (bl.time < t - length)
                                        it.remove();
                                    else
                                        bb.append(bl.line).append('\n');
                                }

                                lastWrite += interval;
                                Intent intent = new Intent(BROADCAST_SENSORS_DATA);
                                intent.putExtra(EXTRA_SENSORS_DATA, bb.toString());
                                LocalBroadcastManager.getInstance(RecordingService.this).sendBroadcast(intent);
                                Log.d(TAG, "Buffer size: " + bb.length());
                            }


                            StringBuilder sb = new StringBuilder();
                            if (flagTime)
                                sb.append(String.valueOf((t - start) / 1000f));
                            for (Sensor s : sensors) {
                                SensorEvent event = readings.get(s.getType());
                                int l = Util.getSensorMaxLength(s);
                                for (int i=0; i<l; i++) {
                                    sb.append(',');
                                    if (event!=null && i<event.values.length)
                                        sb.append(String.format("%2.5f", event.values[i]));
                                }
                            }
                            if (!flagTime)
                                sb.deleteCharAt(0);
                            buffer.add(new BufferLine(t, sb));

                            long overhead = SystemClock.elapsedRealtime() - t;
                            Thread.sleep(Math.max(0, delay - overhead));

                        }

                    } catch (Exception e) {

                        Log.e(RecordingService.class.getSimpleName(), "Sensor recording error", e);
                        Intent intent = new Intent(BROADCAST_RECORDING_ERROR);
                        intent.putExtra(EXTRA_RECORDING_ERROR, e.getMessage());
                        LocalBroadcastManager.getInstance(RecordingService.this).sendBroadcast(intent);
                        handleStop();

                    } finally {

                        for (Sensor s : sensors)
                            if (s != null)
                                sensorManager.unregisterListener(listener, s);

                    }

                }

            }.start();

        }

        Log.d(TAG, "Recording: "+prefs.getBoolean(Util.PREF_RECORDING, false));

    }

    private void handleStop() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(Util.PREF_RECORDING, false).commit();
        Log.d(TAG, "Recording: "+prefs.getBoolean(Util.PREF_RECORDING, false));
    }


}
