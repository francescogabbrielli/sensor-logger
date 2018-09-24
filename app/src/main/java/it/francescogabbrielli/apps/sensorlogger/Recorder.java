package it.francescogabbrielli.apps.sensorlogger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Recorder implements Camera.PictureCallback, ServiceConnection {

    private final static String TAG = Recorder.class.getSimpleName();

    private Context context;
    private LoggingService service;
    private boolean bound;

    private ScheduledExecutorService executor;
    private ScheduledFuture schedule;
    private SensorReader reader;
    private ICamera camera;

    private long start;
    private boolean flagTime, flagTimestamp, flagNetwork;
    private String filenameData, filenameFrame;
    private boolean started;

    private final SparseIntArray dataLengths;

    Recorder(SensorReader reader, ICamera camera) {
        executor = Executors.newScheduledThreadPool(10);
//        pictureExec = Executors.newScheduledThreadPool(4);
        this.reader = reader;
        this.camera = camera;
        dataLengths = new SparseIntArray();
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        if (started)
            start = SystemClock.elapsedRealtimeNanos()+3000000;
        int t = (int) ((SystemClock.elapsedRealtimeNanos()-start)/1000000);
        logSensors(t);
        if (data!=null)
            logImage(data, t);
        else
            Log.d(TAG, "No image!");
        this.camera.pictureTaken();
    }

    private String getFilename(String s, int timestamp) {
        return flagTimestamp ? String.format("%8d_"+s, timestamp) : s;
    }

    private void logImage(byte[] data, int timestamp) {
        scheduleJob(
                getFilename(filenameFrame, timestamp),
                data,
                ILogTarget.SEND);
    }

    private int getSensorDataLength(Sensor sensor) {
        int ret = dataLengths.get(sensor.getType());
        if (ret==0) {
            ret = Util.getSensorMaxLength(sensor);
            dataLengths.put(sensor.getType(), ret);
        }
        return ret;
    }

    private void logSensors(int timestamp) {
        StringBuilder buffer = new StringBuilder();
        if (flagTime) {
            buffer.append(String.valueOf(timestamp));
            buffer.append(",");
        }
        for (SensorEvent e : reader) {
            int l = Math.min(e.values.length, getSensorDataLength(e.sensor));
            for (int i=0; i<l; i++) {
                buffer.append(String.format("%2.5f", e.values[i]));
                buffer.append(',');
            }
        }
        if (buffer.length()>0)
            buffer.deleteCharAt(buffer.length()-1);

        Log.v(TAG, "Sensor reading: "+buffer);

        buffer.append('\n');

        scheduleJob(
                getFilename(filenameData, timestamp),
                buffer.toString().getBytes(),
                started ? ILogTarget.OPEN : ILogTarget.WRITE);

        started = false;
    }

    private void scheduleJob(String filename, byte[] data, int type) {
        Log.v(TAG, "Scheduling "+filename+", type"+type);
//        Bundle extras = new Bundle();
//        extras.putString(Util.EXTRA_FILENAME, filename);
//        extras.putInt(Util.EXTRA_TYPE, type);
//        if (data!=null)
//            extras.putByteArray(Util.EXTRA_DATA, data);
//        Intent intent = new Intent(LoggingService.ACTION_START, null, context, LoggingService.class);
//        intent.putExtras(extras);
//        context.startService(intent);
        service.log(filename, type, data);
    }

    public void start(Context context) {
        if (schedule!=null)
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        filenameData = prefs.getString(Util.PREF_FILENAME_DATA, "sensors.csv");
        filenameFrame = prefs.getString(Util.PREF_FILENAME_FRAME, "image.jpg");
        flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);
        flagTimestamp = prefs.getBoolean(Util.PREF_LOGGING_TIMESTAMP, false);
        flagNetwork = prefs.getBoolean(Util.PREF_FTP,false);
        this.context = context;
        started = true;

        //binding
        context.bindService(new Intent(context, LoggingService.class), this, Context.BIND_AUTO_CREATE);

        reader.start();
        schedule = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                camera.takePicture(Recorder.this);
            }
        }, 3000, Util.getIntPref(prefs, Util.PREF_LOGGING_RATE), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (schedule==null)
            return;
        schedule.cancel(true);
        schedule = null;
        reader.stop();
        scheduleJob(null,null, ILogTarget.CLOSE);

        // unbinding
        context.unbindService(this);
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
