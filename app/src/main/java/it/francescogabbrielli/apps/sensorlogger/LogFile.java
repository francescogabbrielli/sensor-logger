package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class LogFile extends LogTarget {

    protected File folder;

    LogFile(SharedPreferences prefs) {
        super(prefs);
        folder = new File(Environment.getExternalStorageDirectory(),
                prefs.getString(Util.PREF_APP_FOLDER, "SensorLogger"));

    }

    @Override
    public void open(String folder, String filename) throws IOException {
        File subfolder = new File(this.folder, folder);
        subfolder.mkdir();
        out = new FileOutputStream(new File(subfolder, filename));
    }

}
