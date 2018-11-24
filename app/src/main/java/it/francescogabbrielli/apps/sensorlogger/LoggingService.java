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
import android.widget.Toast;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import it.francescogabbrielli.streamingserver.StreamingServer;

/**
 * A {@link android.app.Service} subclass for handling asynchronous task requests in
 * a service on a separate handler threads.
 *
 * This is a bound service the binds to {@link MainActivity} through the {@link Recorder},
 * during a recording session
 */
public class LoggingService extends Service {

    private final static String TAG = LoggingService.class.getSimpleName();

    public static final String ACTION_START = "it.francescogabbrielli.apps.sensorlogger.action.START";
    public static final String ACTION_STOP  = "it.francescogabbrielli.apps.sensorlogger.action.STOP";

    private IBinder binder = new Binder();

    /** Loggers */
    private final List<LogTarget> loggers, dataLoggers, imageLoggers;

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
        loggers = new LinkedList<>();
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
        return binder;
    }

    @Override
    public void onRebind(Intent intent) {
        Util.Log.v(TAG, "in onRebind");
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
        Util.Log.d(TAG, "onDestroy");
        for(LogTarget t : imageLoggers)
            t.dispose();
        imageLoggers.clear();
        for(LogTarget t: dataLoggers)
            t.dispose();
        dataLoggers.clear();
        thread.quit();
        super.onDestroy();
    }

    public StreamingServer getStreamingServer() {
        return streamingServer;
    }

    private LogTarget getLogger(Class<? extends LogTarget> loggerClass) throws Exception {
        int found = -1;
        for (int i=0;found<0 && i<loggers.size();i++)
            if (loggers.get(i).getClass().equals(loggerClass))
               found = i;
        if (!loggerClass.equals(LogStreaming.class) || found<0) {
            LogTarget t = LogTarget.newInstance(loggerClass, this, prefs);
            loggers.add(t);
            Util.Log.i(TAG, "New Logger: " + loggerClass.getSimpleName());
            return t;
        } else
            return loggers.get(found);
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

        // create targets based on preferences TODO: generalize
        List<LogTarget> ret = new LinkedList<>();
        if ((Util.getIntPref(prefs, Util.PREF_FILE) & mask)==mask)
            try {ret.add(getLogger(LogFile.class));}
            catch (Exception e) {Util.Log.e(TAG, "Wrong file logger class", e);}
        if ((Util.getIntPref(prefs, Util.PREF_FTP) & mask)==mask)
            try {ret.add(getLogger(LogFtp.class));}
            catch (Exception e) {Util.Log.e(TAG, "Wrong ftp logger class", e);}
        if ((Util.getIntPref(prefs, Util.PREF_STREAMING) & mask)==mask)
            try {ret.add(getLogger(LogStreaming.class));}
            catch (Exception e) {Util.Log.e(TAG, "Wrong streamin logger class", e);}

        return ret;
    }

    void connect(StreamingServer server) {
        this.streamingServer = server;
        loggers.clear();
        imageLoggers.clear();
        imageLoggers.addAll(newLoggers(Util.LOG_IMAGE));
        dataLoggers.clear();
        dataLoggers.addAll(newLoggers(Util.LOG_DATA));

        // connect targets as soon are they are created
        final List<LogTarget> toRemove = new LinkedList<>();
        for (final Iterator<LogTarget> it = loggers.iterator(); it.hasNext(); ) {
            final LogTarget t = it.next();
            t.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        t.connect();
                    } catch (IOException e) {
                        toRemove.add(t);
                        Toast.makeText(LoggingService.this, "Cannot connect: "+e.getMessage(), Toast.LENGTH_LONG).show();
                        Util.Log.e(t.getTag(), "Cannot connect", e);
                    }
                }
            });
        }
        if (!toRemove.isEmpty()) {
            loggers.removeAll(toRemove);
            dataLoggers.removeAll(toRemove);
            imageLoggers.removeAll(toRemove);
        }
    }

    void disconnect() {
        for (final LogTarget t : loggers)
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
     * Handle action START in the provided background thread with the provided data
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
     * data.
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
