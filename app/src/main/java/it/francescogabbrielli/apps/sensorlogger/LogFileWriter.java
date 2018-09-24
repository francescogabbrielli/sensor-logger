package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class LogFileWriter extends ILogTarget {

    protected File folder;

    LogFileWriter(SharedPreferences prefs) {
        super(prefs);
        folder = new File(Environment.getExternalStorageDirectory(),
                prefs.getString(Util.PREF_APP_FOLDER, "SensorLogger"));
    }

    @Override
    public void open(String filename) throws IOException {
        out = new FileOutputStream(new File(folder, filename));
    }

}
