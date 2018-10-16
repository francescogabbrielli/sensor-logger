package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Streaming {@link LogTarget}. Stream data to an HTTP client
 */
public class LogStreaming extends LogTarget {

    private static final Map<String, String> CONTENT_TYPES = new HashMap<String, String>()
    {{
        put(".jpg", "image/jpeg");
        put(".png", "image/png");
    }};

    /** Server port */
    private int port;
    /** Content imageType of streaming */
    private String imageType;
    /** The server */
    private StreamingServer server;
    /** Current timestamp */
    private long timestamp;

    public LogStreaming(SharedPreferences prefs) {
        super(prefs);
        server = StreamingServer.getInstance();
        port = Util.getIntPref(prefs, Util.PREF_STREAMING_PORT);
        imageType = CONTENT_TYPES.get(prefs.getString(Util.PREF_CAPTURE_IMGFORMAT, ""));
        if (imageType ==null)
            imageType = "image/*";
    }

    @Override
    protected OutputStream openOutputStream(String folder, String filename) throws IOException {
        return null;
    }

    @Override
    public void connect() throws IOException {
        server.start(port);
        Util.Log.i(getTag(), "Start Streaming");
    }

    @Override
    public void open(String folder, String filename, long timestamp) throws IOException {
        //overriding default stream to manage everything in the streaming server
        this.timestamp = timestamp;
    }

    @Override
    public void write(byte[] data) throws IOException {
        server.stream(data, timestamp, data.length>1000 ? imageType : "text/csv");
    }

    @Override
    public void close() throws IOException {
        //overriding default stream to manage everything in the streaming server
    }

    @Override
    public void disconnect() throws IOException {
        Util.Log.i(getTag(), "Stop Streaming");
        server.stop();
    }

}