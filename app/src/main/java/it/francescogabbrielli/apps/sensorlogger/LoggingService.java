package it.francescogabbrielli.apps.sensorlogger;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;

/**
 * An {@link android.app.Service} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * helper methods.
 */
public class LoggingService extends Service {

    private final static String TAG = LoggingService.class.getSimpleName();

    public static final String ACTION_START = "it.francescogabbrielli.apps.sensorlogger.action.START";
    public static final String ACTION_STOP = "it.francescogabbrielli.apps.sensorlogger.action.STOP";

    private IBinder binder = new Binder();

    private final List<ILogTarget> openLoggers, atomicLoggers;
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
        atomicLoggers.addAll(newLoggers());
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
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "in onBind");
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "in onRebind");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "in onUnbind");
        return true;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.d(TAG, "onLowMemory");
    }

    @Override
    public void onDestroy() {
        atomicLoggers.clear();
        openLoggers.clear();
        thread.quit();
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    private List<ILogTarget> newLoggers() {
        List<ILogTarget> ret = new LinkedList<>();
        if (prefs.getBoolean(Util.PREF_FILE, false))
            ret.add(new LogFileWriter(prefs));
        if (prefs.getBoolean(Util.PREF_FTP, false))
            ret.add(new LogFTPUploader(prefs));
        return ret;
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleStart(final Bundle extras) {
        int type = extras.getInt(Util.EXTRA_TYPE);
        byte[] data = extras.getByteArray(Util.EXTRA_DATA);
        String filename = extras.getString(Util.EXTRA_FILENAME);
        log(filename, type, data);
    }

    public void log(final String filename, final int type, final byte[] data) {
//        Log.v(TAG, "Logging to "+filename+" type "+type);
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    switch(type) {
                        case ILogTarget.OPEN:
                            if (openLoggers.isEmpty())
                                openLoggers.addAll(newLoggers());
                            for (ILogTarget t : openLoggers)
                                t.open(filename);
                        case ILogTarget.WRITE:
                            for (ILogTarget t : openLoggers)
                                t.write(data);
                            break;
                        case ILogTarget.CLOSE:
                            for (ILogTarget t : openLoggers)
                                t.close();
                            openLoggers.clear();
                            break;
                        case ILogTarget.SEND:
                            for (ILogTarget t : atomicLoggers) {
                                t.open(filename);
                                t.write(data);
                                t.close();
                            }
                            break;
                    }
//                    Log.d(TAG, "Logged to "+filename+", type "+type);
                } catch(Exception exc) {
                    Log.e(TAG, "Logging error", exc);
                }
            }
        });

    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private void handleStop(Bundle extras) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                stopSelf();
            }
        });
    }


}
