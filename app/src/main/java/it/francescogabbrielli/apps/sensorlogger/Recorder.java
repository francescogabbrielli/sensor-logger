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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Recorder implements Camera.PictureCallback, ServiceConnection {

    private final static String TAG = Recorder.class.getSimpleName();

    private final static int MAX_RECORDING_TIME = 600000;//10min
    private final static DateFormat dateFormat = new SimpleDateFormat("Y_MM_d__HH_mm", Locale.US);

    private Context context;
    private LoggingService service;
    private boolean bound;

    private ScheduledExecutorService executor;
    private ScheduledFuture schedule;
    private SensorReader reader;
    private ICamera camera;

    private long start;
    private boolean flagTime, flagTimestamp, flagNetwork, flagHeaders;
    private String filenameData, filenameFrame, folder;
    private boolean started;

    private final SparseIntArray dataLengths;

    Recorder(SensorReader reader, ICamera camera) {
        executor = Executors.newScheduledThreadPool(10);
//        pictureExec = Executors.newScheduledThreadPool(4);
        this.reader = reader;
        this.camera = camera;
        dataLengths = new SparseIntArray();
    }

    private String getFilename(String s, int timestamp) {
        return flagTimestamp ? String.format("%08d_"+s, timestamp<0 ? 0 : timestamp) : s;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        if (started)
            start = SystemClock.elapsedRealtime();
        int t = (int) (SystemClock.elapsedRealtime()-start);
        if (t > MAX_RECORDING_TIME) {
            this.camera.done(false);
            return;
        }
        logSensors(t);
        if (data!=null)
            logImage(data, t);
        else
            Log.d(TAG, "No image!");
        this.camera.done(true);
    }

    private void logImage(byte[] data, int timestamp) {
        service.log(folder,
                getFilename(filenameFrame, timestamp),
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

    private void logSensors(int timestamp) {
        StringBuilder buffer = new StringBuilder();
        StringBuilder headers = null;
        if (flagHeaders && started) {
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
            for (int i=0; i<l; i++) {
                if (flagHeaders && started) {
                    headers.append(Util.getSensorName(e.sensor));
                    headers.append(",");
                }
                buffer.append(String.format("%2.5f", e.values[i]));
                buffer.append(',');
            }
        }
        if (buffer.length()>0)
            buffer.deleteCharAt(buffer.length()-1);
        if (headers!=null && headers.length()>0) {
            headers.replace(headers.length()-1, headers.length(), "\n");
            buffer.insert(0, headers.toString());
        }

//        Log.v(TAG, "Sensor reading: "+buffer);

        buffer.append('\n');

        service.log(folder,
                getFilename(filenameData, timestamp),
                started ? ILogTarget.OPEN : ILogTarget.WRITE,
                buffer.toString().getBytes());

        started = false;
    }

    public void start(Context context) {
        if (schedule!=null)
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        filenameData = prefs.getString(Util.PREF_FILENAME_DATA, "sensors.csv");
        filenameFrame = prefs.getString(Util.PREF_FILENAME_FRAME, "image.jpg");
        flagTime = prefs.getBoolean(Util.PREF_LOGGING_TIME, false);
        flagTimestamp = prefs.getBoolean(Util.PREF_LOGGING_TIMESTAMP, false);
        flagHeaders = prefs.getBoolean(Util.PREF_LOGGING_HEADERS, false);
        flagNetwork = prefs.getBoolean(Util.PREF_FTP,false);
        folder = dateFormat.format(new Date());
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
        if (started)
            service.log(folder, null, ILogTarget.CLOSE, null);

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
