package it.francescogabbrielli.apps.sensorlogger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.SparseIntArray;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Recorder implements ServiceConnection {

    private final static String TAG = Recorder.class.getSimpleName();

    private final static int MAX_RECORDING_TIME = 3600000;//1h in ms
    /** Format for timestamping files */
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.US);
    /** MainActivity context */
    private MainActivity context;
    /** The data-logging service */
    private LoggingService service;
    /** Bound to service */
    private boolean bound;

    /** Streaming Server */
    private StreamingServer streamingServer;
    /** Sensor sensorReader */
    private SensorReader sensorReader;

    /** Beginning of recording */
    private long start;
    /** Duration of each frame */
    private long duration;
    //flags
    private boolean flagTime, flagTimestamp, flagNetwork, flagHeaders, stopped;
    //filename structure
    private String filenameData, filenameFrame, folder, ext, formatTimestamp;
    /** Internal counter */
    private int counter;
    /** Lengths of each sensor data */
    private final SparseIntArray dataLengths;

    Recorder(SensorReader reader, StreamingServer server) {
        this.sensorReader = reader;
        this.streamingServer = server;
        dataLengths = new SparseIntArray();
    }

    /**
     * Record image data (called from MainActivity at every desired frame)
     *
     * @param data the image data
     * @param timestamp the timestamp of the frame capture
     */
    public void record(byte[] data, long timestamp) {

        if (stopped || !bound)
            return;

        if (counter==0)
            start = timestamp;

        long max = timestamp-start+duration/2;
//        if (max > MAX_RECORDING_TIME * 1000000L) {
//            context.stopRecording(R.string.toast_recording_offlimits);
//            return;
//        }

        //log precise frames and fill in missing frames, if any
        for (long time = counter*duration; time<max; time+=duration) {
            byte[] sensorsData = readSensors((int)(time/1000000L)).getBytes();
            logSensors(sensorsData, time);
            if (data != null)
                logImage(data, time, counter);
            else
                Util.Log.w(TAG, "No image!");
            counter++;
        }

    }

    private void logImage(byte[] data, long timestamp, int n) {
        service.log(
                folder,
                flagTimestamp
                        ? String.format(Locale.US, formatTimestamp, filenameFrame, n, ext)
                        : filenameFrame,
                LogTarget.SEND,
                data, timestamp);
    }

    private void logSensors(byte[] data, long timestamp) {
        service.log(
                folder,
                filenameData,
                counter==0 ? LogTarget.OPEN : LogTarget.WRITE,
                data, timestamp);
    }

    private int getSensorDataLength(Sensor sensor) {
        int ret = dataLengths.get(sensor.getType());
        if (ret==0) {
            ret = Util.getSensorMaxLength(sensor);
            dataLengths.put(sensor.getType(), ret);
        }
        return ret;
    }

    private String readSensors(int timestamp) {
        StringBuilder buffer = new StringBuilder();
        StringBuilder headers = null;
        if (flagHeaders && counter == 0) {
            headers = new StringBuilder();
            if (flagTime)
                headers.append("Frame Time,");
        }
        if (flagTime) {
            buffer.append(String.valueOf(timestamp));
            buffer.append(",");
        }
        for (SensorEvent e : sensorReader) {//iterate through accelerometer and gyroscope (and magnetometer, etc)
            int l = Math.min(e.values.length, getSensorDataLength(e.sensor));
            for (int i = 0; i < l; i++) {//iterate through x, y, z (and what else... if a sensor has more than 3 values)
                if (flagHeaders && counter==0) {
                    headers.append(Util.getSensorName(e.sensor));
                    if (l>1)
                        headers.append(" ").append(Util.DATA_HEADERS[i]);
                    headers.append(",");
                }
                buffer.append(String.format(Locale.US, "%2.5f,", e.values[i]));
            }
        }
        if (buffer.length() > 0)
            buffer.deleteCharAt(buffer.length() - 1);
        if (headers!=null && headers.length() > 0) {
            headers.replace(headers.length() - 1, headers.length(), "\n");
            buffer.insert(0, headers.toString());
        }

        //Util.Log.v(TAG, "Sensor reading: "+buffer);

        return buffer.append('\n').toString();
    }

    public void start(MainActivity context) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        //basic preferences
        filenameData = prefs.getString(Util.PREF_FILENAME_DATA, "sensors.csv");
        filenameFrame = prefs.getString(Util.PREF_FILENAME_FRAME, "frame");
        flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);
        flagTimestamp = prefs.getBoolean(Util.PREF_LOGGING_TIMESTAMP, false);
        flagHeaders = prefs.getBoolean(Util.PREF_LOGGING_HEADERS, false);
        flagNetwork = Util.getIntPref(prefs, Util.PREF_FTP)>0;
        folder = dateFormat.format(new Date());
        duration = Util.getLongPref(prefs, Util.PREF_LOGGING_RATE);
        ext = prefs.getString(Util.PREF_CAPTURE_IMGFORMAT,".png");
        formatTimestamp = prefs.getString(Util.PREF_LOGGING_TIMESTAMP_FORMAT, "%s%07d%s");


        //configurable sensor dimension
        for (String k : prefs.getAll().keySet())
            if (k.startsWith("pref_sensor_") && k.endsWith("_length"))
                try{
                    int sensor = Integer.parseInt(k.substring(12, k.length() - 7));
                    dataLengths.put(sensor, Integer.parseInt(prefs.getString(k, "0")));
                } catch(Exception e) {
                    Util.Log.e(TAG, "Wrong configuration: "+k, e);
                }

        this.context = context;
        counter = 0;

        //binding
        context.bindService(new Intent(context, LoggingService.class), this, Context.BIND_AUTO_CREATE);


    }

    public void stop() {
        if (stopped)
            return;
        stopped = true;

        sensorReader.stop();
        if (bound && counter>0)
            service.log(folder, null, LogTarget.CLOSE, null, 0);

        // unbinding
        if (context!=null)
            context.unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Util.Log.d(TAG, "Service disconnected");
        bound = false;
        service = null;
        context = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Util.Log.d(TAG, "Service connected");
        LoggingService.Binder myBinder = (LoggingService.Binder) service;
        this.service = myBinder.getService();

        this.service.connect(streamingServer);
        sensorReader.start();
        stopped = false;
        this.bound = true;
    }

    public void dispose() {
        sensorReader.dispose();
        streamingServer.dispose();
    }
}
