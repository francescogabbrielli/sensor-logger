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

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Range;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

public class Recorder implements ServiceConnection {

    private final static String TAG = Recorder.class.getSimpleName();

    private final static int MAX_RECORDING_TIME = 3600000;//1h in ms

    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.US);

    private MainActivity context;
    private LoggingService service;
    private boolean bound;

    private SensorReader reader;

    private long start, duration;
    private boolean flagTime, flagTimestamp, flagNetwork, flagHeaders, stopped;
    private String filenameData, filenameFrame, folder, ext, formatTimestamp;
    private int counter;

    /** 3d sensors axes rotation */
    private Mat rotation;

    private final SparseIntArray dataLengths;

    Recorder(SensorReader reader) {
        this.reader = reader;
        dataLengths = new SparseIntArray();
    }

    /**
     * Record image data (called from MainActivity at every desired frame)
     *
     * @param data the image data
     * @param timestamp the timestamp of the frame capture
     */
    public void record(byte[] data, long timestamp) {

        if (stopped)
            return;

        if (counter==0)
            start = timestamp;

        long max = timestamp-start+duration/2;
        if (max > MAX_RECORDING_TIME * 1000000L) {
            context.stopRecording(R.string.toast_recording_offlimits);
            return;
        }

        //log precise frames and fill in missing frames, if any
        for (long time = counter*duration; time < max; time+=duration) {
            byte[] sensorsData = readSensors((int)(time/1000000L)).getBytes();
            logSensors(sensorsData, timestamp);
            if (data != null)
                logImage(data, timestamp, counter);
            else
                Util.Log.d(TAG, "No image!");
            counter++;
        }

    }

    private void logImage(byte[] data, long timestamp, int n) {
        if (!bound)
            return;
        service.log(
                folder,
                flagTimestamp
                        ? String.format(Locale.US, formatTimestamp, filenameFrame, n, ext)
                        : filenameFrame,
                LogTarget.SEND,
                data, timestamp);
    }

    private void logSensors(byte[] data, long timestamp) {
        if (!bound)
            return;
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
        for (SensorEvent e : reader) {//iterate through accelerometer and gyroscope (and magnetometer, etc)
            int l = Math.min(e.values.length, getSensorDataLength(e.sensor));
            if (l==3 && rotation!=null)
                applyRotation(e.values);
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

        //axes rotation
        double theta = Util.getDoublePref(prefs, Util.PREF_SENSORS_THETA);
        double phi = Util.getDoublePref(prefs, Util.PREF_SENSORS_PHI);
        rotation = null;
        if (theta!=0 || phi!=0)
            createRotation(theta, phi);

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

        reader.start();
        stopped = false;
    }

    public void stop() {
        if (stopped)
            return;
        stopped = true;

        reader.stop();
        if (bound && counter>0)
            service.log(folder, null, LogTarget.CLOSE, null, 0);

        // unbinding
        if (context!=null)
            context.unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bound = false;
        service = null;
        context = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        LoggingService.Binder myBinder = (LoggingService.Binder) service;
        this.service = myBinder.getService();
        this.bound = true;
    }

    private void createRotation(double theta, double phi) {
        rotation = new Mat(3,3, CvType.CV_32F);
        rotation.put(0,0,0);
        rotation.put(0,1,0);
        rotation.put(0,2,0);
        rotation.put(1,0,0);
        rotation.put(1,1,0);
        rotation.put(1,2,0);
        rotation.put(2,0,0);
        rotation.put(2,1,0);
        rotation.put(2,2,0);
    }

    private void applyRotation(float[] values) {
        Mat v = new Mat(1,3, CvType.CV_32F);
        v.put(0,0, values[0]);
        v.put(1,0, values[1]);
        v.put(2,0, values[2]);
        Mat res = rotation.mul(v);
        values[0] = (float) res.get(0,0)[0];
        values[1] = (float) res.get(0,0)[1];
        values[2] = (float) res.get(0,0)[2];
    }

}
