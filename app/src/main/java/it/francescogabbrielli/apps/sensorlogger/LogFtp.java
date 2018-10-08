package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;

import org.apache.commons.net.ftp.FTPClient;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Transfer data (images / sensor readings) to an FTP server
 */
public class LogFtp extends LogTarget {

    private FTPClient client;
    private String address, user, password;
    private boolean folderCreated;

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
    public void connect() throws IOException {
        if (!client.isConnected()) {
            Util.Log.d(getTag(), "Connecting to " + address);
            client.connect(address);
            client.login(user, password);
            client.enterLocalPassiveMode();
            client.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE);
        }
    }

    @Override
    protected OutputStream openOutputStream(String folder, String filename) throws IOException {
        if (!folderCreated) {
            client.makeDirectory(folder);
            folderCreated = client.changeWorkingDirectory(folder);
        }
        return client.storeFileStream(filename);
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (client != null && client.isConnected())
            client.completePendingCommand();
    }

    @Override
    public void disconnect() throws IOException {
        if (client != null && client.isConnected()) {
            Util.Log.d(getTag(), "Disconnecting from "+address);
            client.logout();
            client.disconnect();
            Util.Log.i(getTag(), "Client disconnected: "+address);
        }
    }
}
