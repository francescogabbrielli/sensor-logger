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
import android.util.Log;
import android.util.SparseIntArray;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Recorder implements ServiceConnection {

    private final static String TAG = Recorder.class.getSimpleName();

    private final static int MAX_RECORDING_TIME = 600000;//10min in ms
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_d__HH_mm", Locale.US);

    private MainActivity context;
    private LoggingService service;
    private boolean bound;

    private SensorReader reader;

    private long start, duration;
    private boolean flagTime, flagTimestamp, flagNetwork, flagHeaders, stopped;
    private String filenameData, filenameFrame, folder, ext;
    private int counter;

    private final SparseIntArray dataLengths;

    Recorder(SensorReader reader) {
        this.reader = reader;
        dataLengths = new SparseIntArray();
    }

    private String getImageFilename(String s, int param) {
        return flagTimestamp
                ? String.format(Locale.US, "%s%07d%s", s, param<0 ? 0 : param, ext) : s;
    }

    /**
     * Record image data
     *
     * @param data the image data
     * @param timestamp the timestamp of the frame capture
     */
    public void record(Object data, long timestamp) {

        if (stopped)
            return;

        if (counter==0)
            start = timestamp;

        int t = (int) ((timestamp-start)/1000000L);
        if (t > MAX_RECORDING_TIME) {
            context.stopRecording(R.string.toast_recording_offlimits);
            return;
        }

        //log precise frames and fill in missing frames
        long max = timestamp-start+duration/2;
        for (long time = counter*duration; time < max; time+=duration) {
            byte[] sensorsData = readSensors((int)(time/1000000L)).getBytes();
            logSensors(sensorsData);
            if (data != null)
                logImage(data, counter);
            else
                Util.Log.d(TAG, "No image!");
            counter++;
        }

    }

    private void logImage(Object data, int param) {
        service.log(folder,
                getImageFilename(filenameFrame, param),
                ILogTarget.SEND, data);
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
                headers.append("Time,");
        }
        if (flagTime) {
            buffer.append(String.valueOf(timestamp));
            buffer.append(",");
        }
        for (SensorEvent e : reader) {
            int l = Math.min(e.values.length, getSensorDataLength(e.sensor));
            for (int i = 0; i < l; i++) {
                if (flagHeaders && counter==0) {
                    headers.append(Util.getSensorName(e.sensor));
                    if (l>1)
                        headers.append(" ").append(Util.DATA_HEADERS[i]).append(",");
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

        Util.Log.v(TAG, "Sensor reading: "+buffer);

        return buffer.append('\n').toString();
    }

    private void logSensors(byte[] data) {
        service.log(folder,
                filenameData,//getImageFilename(filenameData, timestamp),
                counter==0 ? ILogTarget.OPEN : ILogTarget.WRITE,
                data);
    }

    public void start(MainActivity context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        filenameData = prefs.getString(Util.PREF_FILENAME_DATA, "sensors.csv");
        filenameFrame = prefs.getString(Util.PREF_FILENAME_FRAME, "frame");
        flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);
        flagTimestamp = prefs.getBoolean(Util.PREF_LOGGING_TIMESTAMP, false);
        flagHeaders = prefs.getBoolean(Util.PREF_LOGGING_HEADERS, false);
        flagNetwork = prefs.getBoolean(Util.PREF_FTP,false);
        folder = dateFormat.format(new Date());
        duration = Util.getLongPref(prefs, Util.PREF_LOGGING_RATE);
        ext = prefs.getString(Util.PREF_CAPTURE_IMGFORMAT,".png");
        this.context = context;
        counter = 0;

        //binding
        context.bindService(new Intent(context, LoggingService.class), this, Context.BIND_AUTO_CREATE);

        reader.start();
        stopped = false;
    }

    public void stop() {
        stopped = true;
        if (context==null)
            return;
        reader.stop();
        if (counter>0)
            service.log(folder, null, ILogTarget.CLOSE, null);

        // unbinding
        context.unbindService(this);
        context = null;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bound = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        LoggingService.Binder myBinder = (LoggingService.Binder) service;
        this.service = myBinder.getService();
        this.bound = true;
    }

}
