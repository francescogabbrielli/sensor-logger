package it.francescogabbrielli.apps.sensorlogger;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Util.loadDefaults(this);
    }

}
