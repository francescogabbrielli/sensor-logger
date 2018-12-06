package it.francescogabbrielli.apps.sensorlogger.simple;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import it.francescogabbrielli.streaming.server.Streaming;
import it.francescogabbrielli.streaming.server.StreamingCallback;
import it.francescogabbrielli.streaming.server.StreamingServer;


public class StreamingService extends Service {


    private static final Map<String, String> CONTENT_TYPES = new HashMap<String, String>()
    {{
        put(".jpg", "image/jpeg");
        put(".png", "image/png");
    }};

    private final static String TAG = StreamingService.class.getSimpleName();

    private IBinder binder = new Binder();

    public class Binder extends android.os.Binder {
        StreamingService getService() {
            return StreamingService.this;
        }
    }

    private SharedPreferences prefs;
    private StreamingServer imageServer, sensorServer;
    private SensorReader sensorReader;
    private int port;


    public StreamingService() { }

    @Override
    public void onCreate() {
        super.onCreate();
        imageServer = new StreamingServer();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (Util.getIntPref(prefs, App.STREAMING_SENSORS_PORT)>0) {
            sensorReader = new SensorReader((SensorManager) getSystemService(SENSOR_SERVICE), prefs);
            sensorServer = new StreamingServer("text/event-stream");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {//Bound Service
        Log.v(TAG, "in onBind");
        return binder;
    }
    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "in onUnbind");
        stop();
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "in onRebind");
        //onStartStreaming();
        super.onRebind(intent);
    }

    public void start(StreamingCallback callback) {
        port = Util.getIntPref(prefs, App.STREAMING_IMAGE_PORT);
        String imageExt = prefs.getString(App.STREAMING_IMAGE_EXT, ".jpg");
        imageServer.setCallback(callback);
        imageServer.start(port, CONTENT_TYPES.get(imageExt)!=null ? CONTENT_TYPES.get(imageExt) : "image/*");
        int port2 = Util.getIntPref(prefs, App.STREAMING_SENSORS_PORT);
        if (sensorServer!=null && port2>0) {
            sensorServer.setCallback(callback);
            sensorServer.start(port2);
            sensorReader.start();
        }
    }

    public void stop() {
        imageServer.stop();
        if (sensorServer!=null) {
            sensorServer.stop();
            sensorReader.stop();
        }
    }

    public void streamImage(byte[] data, long timestamp) {
        imageServer.streamFrame(data, timestamp);
        if (sensorServer!=null)
            sensorServer.streamFrame(sensorReader.readSensors(timestamp).getBytes(), timestamp);
    }

    public void onStartStreaming(Streaming s) {
        if (s.getPort()!=port)
            s.setDataHeaders(sensorReader.readHeaders());
    }



}
