package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Save data (images / sensor data) to local file-system, in the app folder
 */
public class LogFile extends LogTarget {

    /** The app folder, not the recording folder */
    protected File folder;

    LogFile(LoggingService service, SharedPreferences prefs) {
        super(service, prefs);
        folder = new File(Environment.getExternalStorageDirectory(),
                prefs.getString(Util.PREF_APP_FOLDER, "SensorLogger"));

    }

    @Override
    protected OutputStream openOutputStream(String folder, String filename) throws IOException {
        File subfolder = new File(this.folder, folder);
        subfolder.mkdir();
        return new FileOutputStream(new File(subfolder, filename));
    }

    @Override
    public void connect() { }

    @Override
    public void disconnect() { }
}
