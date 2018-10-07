package it.francescogabbrielli.apps.sensorlogger;

import android.util.Log;

import java.util.Locale;

/**
 * An utility class to post speicific operation to a {@link LogTarget}
 */
public class LogOperation {

    private int type;
    private String folder, filename;
    private byte[] data;

    /**
     * Create a new {@link LogOperation}
     *
     * @param type one of {@link LogTarget#OPEN}, {@link LogTarget#WRITE}, {@link LogTarget#CLOSE}, {@link LogTarget#SEND}
     * @param data the actual data to log
     * @param folder the folder
     * @param filename the filename to log to
     */
    LogOperation(int type, byte[]data, String folder, String filename) {
        this.type = type;
        this.data = data;
        this.folder = folder;
        this.filename = filename;
    }

    /**
     * Operate on a {@link LogTarget}, i.e.: post the task identified by {@code this} operation
     * on the LogTarget own thread
     *
     * @param target the destination
     */
    public void on(final LogTarget target) {
        Util.Log.v(target.getTag(), LogTarget.OP_NAMES[type] + " " + filename + "; bytes: " + (data != null ? data.length : -1));
        target.post(new Runnable() {
            @Override
            public void run() {
                try {
                    switch (type) {
                        case LogTarget.OPEN:
                            target.open(folder, filename);
                        case LogTarget.WRITE:
                            target.write(data);
                            break;
                        case LogTarget.CLOSE:
                            target.close();
                            break;
                        case LogTarget.SEND:
                            target.open(folder, filename);
                            target.write(data);
                            target.close();
                    }
                } catch(Exception e) {
                    report(e,"Cannot %s %s (%s)",
                            LogTarget.OP_NAMES[type], filename, target);
                }
            }
        });
    }

    /**
     * Report log exception
     *
     * @param e the exception
     * @param formattedMsg the message format
     * @param params the parameters of the format
     */
    private void report(Exception e, String formattedMsg, Object... params) {
        //TODO: report to foreground (callback?)
        Log.e(LogOperation.class.getSimpleName(), String.format(Locale.US, formattedMsg, params), e);
    }

}
