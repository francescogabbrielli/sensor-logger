package it.francescogabbrielli.apps.sensorlogger;

import android.util.Log;

import java.util.Locale;

public class LogOperation {

    protected int type;
    protected String folder, filename;
    protected byte[] data;

    LogOperation(int type, byte[]data, String folder, String filename) {
        this.type = type;
        this.data = data;
        this.folder = folder;
        this.filename = filename;
    }

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
     * @param e
     * @param formattedMsg
     * @param params
     */
    private void report(Exception e, String formattedMsg, Object... params) {
        //TODO: report to foreground (callback?)
        Log.e(LogOperation.class.getSimpleName(), String.format(Locale.US, formattedMsg, params), e);
    }
}
