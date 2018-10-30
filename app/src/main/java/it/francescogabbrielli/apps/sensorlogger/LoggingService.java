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

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A {@link android.app.Service} subclass for handling asynchronous task requests in
 * a service on a separate handler threads.
 *
 * This is a bound service the binds to {@link MainActivity} through the {@link Recorder},
 * during a recording session
 */
public class LoggingService extends Service {

    private final static String TAG = LoggingService.class.getSimpleName();

    public static final String ACTION_START = "it.francescogabbrielli.apps.sensorlogger.action.START_SERVICE";
    public static final String ACTION_STOP  = "it.francescogabbrielli.apps.sensorlogger.action.STOP_SERVICE";

    private IBinder binder = new Binder();

    /** Loggers */
    private final List<LogTarget> dataLoggers, imageLoggers;

    /** App preferences */
    private SharedPreferences prefs;

    /** Own thread (not really used, because all loggers have their own thread) */
    private HandlerThread thread;

    /** Own thread handler */
    private Handler handler;

    /** A simple streaming server */
    private StreamingServer streamingServer;


    public class Binder extends android.os.Binder {
        LoggingService getService() {
            return LoggingService.this;
        }
    }

    public LoggingService() {
        imageLoggers = new LinkedList<>();
        dataLoggers = new LinkedList<>();
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
        connect();
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Util.Log.v(TAG, "in onRebind");
        connect();
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Util.Log.v(TAG, "in onUnbind");
        disconnect();
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
        Util.Log.d(TAG, "onDestroy");
        for(LogTarget t : imageLoggers)
            t.dispose();
        imageLoggers.clear();
        for(LogTarget t: dataLoggers)
            t.dispose();
        dataLoggers.clear();
        thread.quit();
        if (streamingServer!=null)
            streamingServer.dispose();
        super.onDestroy();
    }

    /**
     * Instantiate new loggers based on preferences
     *
     * @param mask bitmask (1=images, 2=sensors data)
     * @return the list of available targets
     * @see Util#LOG_IMAGE
     * @see Util#LOG_DATA
     */
    private List<LogTarget> newLoggers(int mask) {

        // create targets based on preferences
        List<LogTarget> ret = new LinkedList<>();
        if ((Util.getIntPref(prefs, Util.PREF_FILE) & mask)==mask)
            ret.add(new LogFile(this, prefs));
        if ((Util.getIntPref(prefs, Util.PREF_FTP) & mask)==mask)
            ret.add(new LogFtp(this, prefs));
        if ((Util.getIntPref(prefs, Util.PREF_STREAMING) & mask)==mask)
            ret.add(new LogStreaming(this, prefs));

        // connect targets as soon are they are created
        for (final Iterator<LogTarget> it = ret.iterator(); it.hasNext() ; ) {
            final LogTarget t = it.next();
            t.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        t.connect();
                    } catch (IOException e) {
                        it.remove();
                        Util.Log.e(t.getTag(), "Cannot connect", e);
                    }
                }
            });
        }

        return ret;
    }

    StreamingServer getStreamingServer() {
        if (streamingServer==null)
            streamingServer = new StreamingServer();
        return streamingServer;
    }

    private void connect() {
        imageLoggers.clear();
        imageLoggers.addAll(newLoggers(Util.LOG_IMAGE));
        dataLoggers.clear();
        dataLoggers.addAll(newLoggers(Util.LOG_DATA));
    }

    private void disconnect() {
        for (final LogTarget t : imageLoggers)
            t.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        t.disconnect();
                    } catch(Exception e) {}
                }
            });
        for (final LogTarget t : dataLoggers)
            t.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        t.disconnect();
                    } catch(Exception e) {}
                }
            });
    }

    /**
     * Send data on available {@link LogTarget}s
     *
     * @param folder the folder
     * @param filename the filename to log to
     * @param type the operation type
     *     (one of {@link LogTarget#OPEN}, {@link LogTarget#WRITE}, {@link LogTarget#CLOSE}, {@link LogTarget#SEND})
     * @param data the data to send
     */
    public synchronized void log(final String folder, final String filename, final int type, final byte[] data, long timestamp) {
        LogOperation operate = new LogOperation(type, data, folder, filename, timestamp);
        if (type==LogTarget.SEND) {
            for (LogTarget t : imageLoggers)
                operate.on(t);
        } else {
            for (LogTarget t : dataLoggers)
                operate.on(t);
        }
    }


    /**
     * Handle action START in the provided background thread with the provided
     */
    private void handleStart(final Bundle extras) {
        int type = extras.getInt(Util.EXTRA_TYPE);
        byte[] data = extras.getByteArray(Util.EXTRA_DATA);
        String filename = extras.getString(Util.EXTRA_FILENAME);
        String folder = extras.getString(Util.EXTRA_FOLDER);
        log(folder, filename, type, data, extras.getInt(Util.EXTRA_TIMESTAMP));
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

    private synchronized boolean isRunning() {
        for (LogTarget t : dataLoggers)
            if(t.isRunning())
                return true;
        for (LogTarget t : imageLoggers)
            if(t.isRunning())
                return true;
        return false;
    }

}
