package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;

import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;

public class LogFtp extends LogTarget {

    private FTPClient client;
    private String address, user, password;

    /**
     * Creates a new FTP uploader, that is a wrapper around Apache Commons FTPClient
     *
     * @param prefs the application preferences
     * @see Util#PREF_FTP_ADDRESS, Util#PREF_FTP_USER, Util#PREF_FTP_PW
     */
    public LogFtp(SharedPreferences prefs) {
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
                Util.Log.d(getTag(), "Connecting to "+address);
                client.connect(address);
                client.login(user, password);
                client.enterLocalPassiveMode();
                client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
                client.makeDirectory(folder);
                client.changeWorkingDirectory(folder);
                out = client.storeFileStream(filename);
                if (out!=null)
                    Util.Log.d(getTag(), "File opened: "+filename);
                else {
                    Util.Log.w(getTag(), "Cannot create or access file "+filename);
                    close();
                }
            } catch (Exception e) {
                Util.Log.e(getTag(), "Can't connect to " + address + ", user: " + user, e);
            }
    }

    /**
     * Closes current connection
     */
    @Override
    public void close() throws IOException {
        super.close();
        Util.Log.d(getTag(), "Disconnecting from "+address);
        if (client != null && client.isConnected()) {
            try {
                client.completePendingCommand();
                client.logout();
                client.disconnect();
                Util.Log.d(getTag(), "Client disconnected");
            } catch (Exception e) {
                Util.Log.e(getTag(), "Error finalizing FTP connection", e);
            }
        }
    }

}
