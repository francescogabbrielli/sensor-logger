package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.util.Log;

import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;

public class LogFTPUploader extends ILogTarget {

    private FTPClient client;
    private String address, user, password;

    /**
     * Creates a new FTP uploader, that is a wrapper around Apache Commons FTPClient with threading
     * support and configuration linked to application settings
     *
     * @param prefs the application preferences
     * @see Util#PREF_FTP_ADDRESS, Util#PREF_FTP_USER, Util#PREF_FTP_PW
     */
    public LogFTPUploader(SharedPreferences prefs) {
        super(prefs);
        client = new FTPClient();
        address = prefs.getString(Util.PREF_FTP_ADDRESS, "");
        user = prefs.getString(Util.PREF_FTP_USER, "");
        password = prefs.getString(Util.PREF_FTP_PW, "");
    }

    /**
     * Connects to the FTP server
     */
    @Override
    public void open(String folder, String filename) throws IOException {
        if (!client.isConnected())
            try {
                client.connect(address);
                client.login(user, password);
                client.enterLocalPassiveMode();
                client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
                out = client.storeFileStream(folder+"/"+filename);
                Log.d(getTag(), "Client connected");
            } catch (Exception e) {
                Log.e(getTag(), "Can't connect to " + address + ", user: " + user);
            }
    }

    /**
     * Closes current connection
     */
    @Override
    public void close() throws IOException {
        super.close();
        if (client != null && client.isConnected()) {
            try {
                client.completePendingCommand();
                client.logout();
                client.disconnect();
                Log.d(getTag(), "Client disconnected");
            } catch (Exception e) {
                Log.e(getTag(), "Error finalizing FTP connection", e);
            }
        }
    }


}
