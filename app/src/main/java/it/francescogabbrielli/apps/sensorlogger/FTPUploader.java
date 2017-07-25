package it.francescogabbrielli.apps.sensorlogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.net.ftp.FTPClient;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FTPUploader {

    private final static String TAG = FTPUploader.class.getSimpleName();

    private ExecutorService exec;

    private FTPClient client;

    private String address, user, password;

    public FTPUploader(Context context) {
        client = new FTPClient();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        address = prefs.getString(Util.PREF_FTP_ADDRESS, "");
        user = prefs.getString(Util.PREF_FTP_USER, "");
        password = prefs.getString(Util.PREF_FTP_PW, "");
        exec = Executors.newSingleThreadExecutor();
        execute(null);
    }

    public void execute(final Runnable command) {

        if (client.isConnected()) {

            if(command!=null)
                exec.execute(command);

        } else {

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
                    if (command!=null)
                        command.run();
                }
            });
        }
    }

    public void close() {
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
                exec.shutdown();
            }
        });
    }

    /**
     * Send data asynchronously via FTP
     *
     * @param data
     *          bytes to send
     * @param filename
     *          the filename to write to
     */
    public void send(final byte[] data, final String filename) {
        if (client.isConnected())
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    OutputStream out = null;
                    try {
                        out = client.storeFileStream(filename);
                        out.write(data);
                        Log.d(TAG, "Data written to "+filename);
                    } catch (Exception e) {
                        Log.e(FTPUploader.class.getSimpleName(), "FTP Error", e);
                    } finally {
                        try {
                            out.close();
                            client.completePendingCommand();
                        }
                        catch (Exception e) {}
                    }
                }
            });
        else {
            Log.d(TAG, "Retry");
            execute(new Runnable() {
                @Override
                public void run() {
                    send(data, filename);
                }
            });
        }
    }

}
