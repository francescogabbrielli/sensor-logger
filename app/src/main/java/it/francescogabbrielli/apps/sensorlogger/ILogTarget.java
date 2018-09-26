package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Generic target where to log data
 */
public abstract class ILogTarget {

    static final int WRITE = 0;
    static final int OPEN = 1;
    static final int CLOSE = 2;
    static final int SEND = 3;

    protected OutputStream out;

    ILogTarget(SharedPreferences prefs) { }

    /**
     * Initailize logger
     * @param filename the filename to log to
     */
    public abstract void open(String folder, String filename) throws IOException;

    /**
     * Write data to logger
     * @param data
     */
    public void write(byte[] data) throws IOException {
        out.write(data);
   }

    /**
     * Implement final cleanup
     */
    public void close() throws IOException {
        out.close();
        out = null;
    }

    protected String getTag() {
        return getClass().getSimpleName();
    }

}
