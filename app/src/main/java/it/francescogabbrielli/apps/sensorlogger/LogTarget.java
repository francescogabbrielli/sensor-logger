package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Generic target where to log data
 */
public abstract class LogTarget {

    static final int WRITE = 0;
    static final int OPEN = 1;
    static final int CLOSE = 2;
    static final int SEND = 3;

    final static String[] OP_NAMES = {"write", "open", "close", "send"};

    protected OutputStream out;

    HandlerThread thread;
    Handler handler;//;Looper

    LogTarget(SharedPreferences prefs) {
        thread = new HandlerThread("Service Thread");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void post(Runnable runnable) {
        handler.post(runnable);
    }

    public boolean isRunning() {
        return handler.hasMessages(0);
    }

    /**
     * Log to a file
     * @param filename the filename to log to
     */
    public abstract void open(String folder, String filename) throws IOException;

    /**
     * Log data to current file
     * @param data
     */
    public void write(byte[] data) throws IOException {
        out.write(data);
    }

    /**
     * Close file
     */
    public void close() throws IOException {
        if (out!=null)
            out.close();
        out = null;
    }

    public void dispose() {
        thread.quitSafely();
    }

    protected String getTag() {
        return getClass().getSimpleName();
    }

}
