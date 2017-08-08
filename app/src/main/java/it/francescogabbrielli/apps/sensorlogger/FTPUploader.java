package it.francescogabbrielli.apps.sensorlogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.net.ftp.FTPClient;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FTPUploader {

    private final static String TAG = FTPUploader.class.getSimpleName();

    private ExecutorService exec;

    private FTPClient client;

    private String address, user, password;

    /**
     * Creates a new FTP uploader, that is a wrapper around Apache Commons FTPClient with threading
     * support and configuration linked to application settings
     *
     * TODO: add a kind of listener interface support instead of single callbacks
     *
     * @param context
     *              the application context
     */
    public FTPUploader(Context context) {
        client = new FTPClient();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        address = prefs.getString(Util.PREF_FTP_ADDRESS, "");
        user = prefs.getString(Util.PREF_FTP_USER, "");
        password = prefs.getString(Util.PREF_FTP_PW, "");
        exec = Executors.newSingleThreadExecutor();
    }

    /**
     * Connects to the server
     */
    public void connect() {
        if (!client.isConnected())
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        client.connect(address);
                        client.login(user, password);
                        client.enterLocalPassiveMode();
                        client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
                        Log.d(TAG, "Client connected");
                    } catch (Exception e) {
                        Log.e(TAG, "Can't connect to " + address + ", user: " + user);
                    }
            }
        });
    }

    public void execute(final Runnable command) {
        if (!client.isConnected())
            connect();
        if (command!=null)
            exec.execute(command);
    }

    /**
     * Closes current connection
     */
    public void close() {
        if (client!=null && client.isConnected()) {
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        client.logout();
                        client.disconnect();
                        Log.d(TAG, "Client disconnected");
                    } catch (Exception e) {
                        Log.e(TAG, "Error finalizing FTP connection", e);
                    }
                }
            });
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
    }

    /**
     * Send data asynchronously via FTP
     *
     * @param data
     *          bytes to send
     * @param filename
     *          the filename to write to
     */
    public void send(final byte[] data, final String filename, final Runnable callback) {
        if (client.isConnected())
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    OutputStream out = null;
                    try {
                        out = client.storeFileStream(filename);
                        out.write(data);
                        if (callback!=null)
                            callback.run();
                        Log.d(TAG, "Data written to "+filename);
                    } catch (Exception e) {
                        Log.e(FTPUploader.class.getSimpleName(), "Transfer Error", e);
                    } finally {
                        try {
                            out.close();
                            client.completePendingCommand();
                        } catch (Exception e) {
                            Log.e(FTPUploader.class.getSimpleName(), "Unexpected error", e);
                        }
                    }
                }
            });
//        else {
//            Log.d(TAG, "Retry");
//            execute(new Runnable() {
//                @Override
//                public void run() {
//                    send(data, filename);
//                }
//            });
//        }
    }

}
