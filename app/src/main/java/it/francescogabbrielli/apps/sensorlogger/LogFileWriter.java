package it.francescogabbrielli.apps.sensorlogger;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LogFileWriter extends LogTarget {

    private final static String TAG = LogFTPUploader.class.getSimpleName();

    private File folder;
    private ExecutorService exec;

    public LogFileWriter(File folder) {
        this.folder = folder;
        exec = Executors.newSingleThreadExecutor();
    }

    @Override
    public void send(final byte[] data, final String filename, final SyncCallbackThread scThread) {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(new File(folder, filename));
                    out.write(data);
                    if (scThread!=null)
                        scThread.releaseLock();
                    Log.d(TAG, "Data written to " + filename);
                } catch (Exception e) {
                    Log.e(TAG, "Error writing file", e);
                } finally {
                    try {
                        out.close();
                    } catch (Exception e) {

                    }
                }
            }
        });
    }

    @Override
    public void close() {
        exec.execute(new Runnable() {
            @Override
            public void run() {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            exec.awaitTermination(5, TimeUnit.SECONDS);
                            exec.shutdown();
                        } catch(InterruptedException e) {
                            Log.e(TAG, "Unexpected interruption", e);
                        }
                    }
                }.start();
            }
        });
    }

}
