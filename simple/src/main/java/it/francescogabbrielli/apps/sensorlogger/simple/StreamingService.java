package it.francescogabbrielli.apps.sensorlogger.simple;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import it.francescogabbrielli.streaming.server.StreamingCallback;
import it.francescogabbrielli.streaming.server.StreamingServer;


public class StreamingService extends Service {

    private final static String TAG = StreamingService.class.getSimpleName();

    private IBinder binder = new Binder();

    public class Binder extends android.os.Binder {
        StreamingService getService() {
            return StreamingService.this;
        }
    }

    private SharedPreferences prefs;
    private StreamingServer streamingServer;

    public StreamingService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        streamingServer = new StreamingServer();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "in onRebind");
        //onStartStreaming();
        super.onRebind(intent);
    }

    public void start(StreamingCallback callback) {
        int port = Util.getIntPref(prefs, App.STREAMING_IMAGE_PORT);
        String imageExt = prefs.getString(App.STREAMING_IMAGE_EXT, ".jpg");
        streamingServer.setCallback(callback);
        streamingServer.start(port, imageExt);
    }

    public void stop() {
        streamingServer.stop();
    }

    public void streamImage(byte[] data, long timestamp) {
        streamingServer.streamImage(data, timestamp);
    }

}
