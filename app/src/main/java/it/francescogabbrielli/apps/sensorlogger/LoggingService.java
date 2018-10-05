package it.francescogabbrielli.apps.sensorlogger;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * A {@link android.app.Service} subclass for handling asynchronous task requests in
 * a service on a separate handler threads.
 */
public class LoggingService extends Service {

    private final static String TAG = LoggingService.class.getSimpleName();

    public static final String ACTION_START = "it.francescogabbrielli.apps.sensorlogger.action.START_SERVICE";
    public static final String ACTION_STOP  = "it.francescogabbrielli.apps.sensorlogger.action.STOP_SERVICE";

    private IBinder binder = new Binder();

    private final List<LogTarget> openLoggers, atomicLoggers;
    private SharedPreferences prefs;

    HandlerThread thread;
    Handler handler;


    public class Binder extends android.os.Binder {
        LoggingService getService() {
            return LoggingService.this;
        }
    }

    public LoggingService() {
        atomicLoggers = new LinkedList<>();
        openLoggers = new LinkedList<>();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        thread = new HandlerThread("Service Thread");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                handleStart(intent.getExtras());
            } else if (ACTION_STOP.equals(action)) {
                handleStop(intent.getExtras());
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {//Bound Service
        Util.Log.v(TAG, "in onBind");
        atomicLoggers.clear();
        atomicLoggers.addAll(newLoggers(Util.LOG_IMAGE));
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Util.Log.v(TAG, "in onRebind");
        atomicLoggers.clear();
        atomicLoggers.addAll(newLoggers(Util.LOG_IMAGE));
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Util.Log.v(TAG, "in onUnbind");
        return true;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Util.Log.d(TAG, "onTrimMemory: "+level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Util.Log.d(TAG, "onLowMemory");
    }

    @Override
    public void onDestroy() {
        synchronized (this) {
            for (LogTarget logTarget : atomicLoggers)
                logTarget.dispose();
            atomicLoggers.clear();
            for (LogTarget logTarget : openLoggers)
                logTarget.dispose();
            openLoggers.clear();
            thread.quit();
        }
        Util.Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    private List<LogTarget> newLoggers(int mask) {
        List<LogTarget> ret = new LinkedList<>();
        if ((Util.getIntPref(prefs, Util.PREF_FILE) & mask)==mask)
            ret.add(new LogFile(prefs));
        if ((Util.getIntPref(prefs, Util.PREF_FTP) & mask)==mask)
            ret.add(new LogFtp(prefs));
        if ((Util.getIntPref(prefs, Util.PREF_STREAMING) & mask)==mask)
            ret.add(new LogStreaming(prefs));
        return ret;
    }

    /**
     * Handle action START in the provided background thread with the provided
     */
    private void handleStart(final Bundle extras) {
        int type = extras.getInt(Util.EXTRA_TYPE);
        byte[] data = extras.getByteArray(Util.EXTRA_DATA);
        String filename = extras.getString(Util.EXTRA_FILENAME);
        String folder = extras.getString(Util.EXTRA_FOLDER);
        log(folder, filename, type, data);
    }

    /**
     * Log on available {@link LogTarget}s
     * @param folder
     * @param filename
     * @param type
     * @param data
     */
    public synchronized void log(final String folder, final String filename, final int type, final byte[] data) {
        LogOperation operate = new LogOperation(type, data, folder, filename);
        if (type==LogTarget.SEND) {
            for (LogTarget t : atomicLoggers)
                operate.on(t);
        } else {
            if (type==LogTarget.OPEN && openLoggers.isEmpty())
                openLoggers.addAll(newLoggers(Util.LOG_DATA));
            for (LogTarget t : openLoggers)
                operate.on(t);
            if (type==LogTarget.CLOSE)
                openLoggers.clear();
        }
    }

    private synchronized boolean isRunning() {
        for (LogTarget t : openLoggers)
            if(t.isRunning())
                return true;
        for (LogTarget t : atomicLoggers)
            if(t.isRunning())
                return true;
        return false;
    }

    /**
     * Handle action STOP in the provided background thread with the provided
     * parameters.
     */
    private void handleStop(Bundle extras) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                boolean done;
                do {
                    done = !isRunning();
                    try {Thread.sleep(100);}
                    catch(Exception e) {}
                } while(!done);
                stopSelf();
            }
        });
    }

}
