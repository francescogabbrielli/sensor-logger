package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Generic destination where to log (transfer, save) recording data
 */
public abstract class LogTarget {

    /** Write operation: write data to the underlying stream */
    static final int WRITE = 0;
    /** Open operation: will generally open an OutputStream */
    static final int OPEN = 1;
    /** Close operation: will generally close the current OutputStream */
    static final int CLOSE = 2;
    /** Send operation: OPEN, WRITE, CLOSE */
    static final int SEND = 3;

    /** Operation names (for debugging) */
    final static String[] OP_NAMES = {"write", "open", "close", "send"};

    /** The stream to log to */
    protected OutputStream out;

    /** Execute operations in own thread */
    private HandlerThread thread;
    /** Own hander */
    private Handler handler;

    LogTarget(LoggingService service, SharedPreferences prefs) {
        thread = new HandlerThread(getTag()+" Thread");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /**
     * Post a generic task in the thread managed by this class.
     * @see LogOperation for a more specific task
     */
    public void post(Runnable runnable) {
        if (handler!=null)
            handler.post(runnable);
    }

    /**
     * Check if this thread is running any operation/task
     * @return if is running anything
     */
    public boolean isRunning() {
        return handler!=null && handler.hasMessages(0);
    }

    /**
     * Implement to create the output stream
     *
     * @param folder the recording folder
     * @param filename the filename to log to
     * @return
     * @throws IOException
     */
    protected abstract OutputStream openOutputStream(String folder, String filename) throws IOException;

    /**
     * Connect. Override to connect/initialize logger
     * @throws IOException
     */
    public abstract void connect() throws IOException;


    /**
     * Log to a file
     *
     * @param folder the recording folder
     * @param filename the filename to log to
     * @param timestamp
     * @throws IOException
     */
    public void open(String folder, String filename, long timestamp) throws IOException {
        out = openOutputStream(folder, filename);
        if (out==null) {
            Util.Log.w(getTag(), "Cannot create or access file "+filename);
            close();
        }
    }

    /**
     * Log data to current file
     *
     * @param data bytes to write
     * @throws IOException
     */
    public void write(byte[] data) throws IOException {
        out.write(data);
    }

    /**
     * Close the current file
     * @throws IOException
     */
    public void close() throws IOException {
        if (out!=null)
            out.close();
        out = null;
    }

    /**
     * Disconnect. Override to finalize logger
     * @throws IOException
     */
    public abstract void disconnect() throws IOException;

    public void dispose() {
        thread.quitSafely();
        handler = null;
    }

    /** Get string TAG for debugging */
    protected String getTag() {
        return getClass().getSimpleName();
    }


    public boolean skip() {
        return false;
    }
}
