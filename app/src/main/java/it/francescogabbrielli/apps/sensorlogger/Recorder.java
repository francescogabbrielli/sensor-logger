package it.francescogabbrielli.apps.sensorlogger;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.IBinder;
import android.os.SystemClock;
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

//    private ScheduledExecutorService executor;
//    private ScheduledFuture schedule;
    private SensorReader reader;
    private ICamera camera;

    private long start, rate;
    private boolean flagTime, flagTimestamp, flagNetwork, flagHeaders;
    private String filenameData, filenameFrame, folder;
    private int counter;

    private final SparseIntArray dataLengths;

    Recorder(SensorReader reader) {
//        executor = Executors.newScheduledThreadPool(10);
//        pictureExec = Executors.newScheduledThreadPool(4);
        this.reader = reader;
        dataLengths = new SparseIntArray();
    }

    private String getFilename(String s, int param) {
        return flagTimestamp
                ? s.replaceFirst("\\.([a-z]+)", String.format("%08d.$1", param<0 ? 0 : param))
                : s;
    }

    public void onData(byte[] data, long timestamp) {

        if (counter==0)
            start = timestamp;

        int t = (int) ((timestamp-start)/1000000l);
        if (t > MAX_RECORDING_TIME) {
            context.stopRecording(R.string.toast_recording_offlimits);
            return;
        }
        byte[] sensorsData = readSensors(t).getBytes();

        //log precise frames and fill in missing frames
        for (long time=start+counter*rate; time < timestamp+rate/2; time+=rate) {
            logSensors(sensorsData);
            if (data != null)
                logImage(data, counter);
            //        else
            //            Log.d(TAG, "No image!");
        }

        counter++;
    }

    private void logImage(byte[] data, int param) {
        service.log(folder,
                getFilename(filenameFrame, param),
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
                if (flagHeaders && counter == 0)
                    headers.append(Util.getSensorName(e.sensor))
                            .append(" ")
                            .append(Util.DATA_HEADERS[i])
                            .append(",");
                buffer.append(String.format("%2.5f", e.values[i]));
                buffer.append(',');
            }
        }
        if (buffer.length() > 0)
            buffer.deleteCharAt(buffer.length() - 1);
        if (headers != null && headers.length() > 0) {
            headers.replace(headers.length() - 1, headers.length(), "\n");
            buffer.insert(0, headers.toString());
        }

//        Log.v(TAG, "Sensor reading: "+buffer);

        buffer.append('\n');

        return buffer.toString();
    }

    private void logSensors(byte[] data) {
        service.log(folder,
                filenameData,//getFilename(filenameData, timestamp),
                counter==0 ? ILogTarget.OPEN : ILogTarget.WRITE,
                data);
    }

    public void start(MainActivity context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        filenameData = prefs.getString(Util.PREF_FILENAME_DATA, "sensors.csv");
        filenameFrame = prefs.getString(Util.PREF_FILENAME_FRAME, "image.jpg");
        flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);
        flagTimestamp = prefs.getBoolean(Util.PREF_LOGGING_TIMESTAMP, false);
        flagHeaders = prefs.getBoolean(Util.PREF_LOGGING_HEADERS, false);
        flagNetwork = prefs.getBoolean(Util.PREF_FTP,false);
        folder = dateFormat.format(new Date());
        rate = Util.getLongPref(prefs, Util.PREF_LOGGING_RATE);
        this.context = context;
        counter = 0;

        //binding
        context.bindService(new Intent(context, LoggingService.class), this, Context.BIND_AUTO_CREATE);

        reader.start();
//        schedule = executor.scheduleAtFixedRate(new Runnable() {
//            @Override
//            public void run() {
//                camera.takePicture(Recorder.this);
//            }
//        }, 3000, Util.getLongPref(prefs, Util.PREF_LOGGING_RATE), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (context==null)
            return;
//        schedule.cancel(true);
//        schedule = null;
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
