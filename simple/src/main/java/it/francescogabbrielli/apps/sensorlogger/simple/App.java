package it.francescogabbrielli.apps.sensorlogger.simple;

import android.app.Application;

public class App extends Application {

    public final static String FRAME_RATE                  = "frame_rate";
    public final static String SENSORS_SAMPLING_DELAY      = "sensors_sampling_delay";

    public final static String STREAMING_IMAGE_PORT        = "streaming_image_port";
    public final static String STREAMING_IMAGE_EXT         = "streaming_image_ext";

    @Override
    public void onCreate() {
        super.onCreate();
        Util.loadDefaults(this, true);
    }

}
