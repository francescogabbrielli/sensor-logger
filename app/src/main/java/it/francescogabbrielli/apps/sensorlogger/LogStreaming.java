package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
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

    private int n;

    public LogStreaming(LoggingService service, SharedPreferences prefs) {
        super(service, prefs);
        server = service.getStreamingServer();
        port = Util.getIntPref(prefs, Util.PREF_STREAMING_PORT);
        imageType = CONTENT_TYPES.get(prefs.getString(Util.PREF_CAPTURE_IMGFORMAT, ""));
        if (imageType == null)
            imageType = "image/*";
    }

    @Override
    protected OutputStream openOutputStream(String folder, String filename) throws IOException {
        return null;
    }

    @Override
    public void connect() throws IOException {
        server.start(port);
    }

    @Override
    public void open(String folder, String filename, long timestamp) throws IOException {
        //overriding default stream to manage everything in the streaming server
        this.timestamp = timestamp;
    }

    @Override
    public void write(byte[] data) throws IOException {
        String type = imageType;
        long offset = 0;
        if (data.length < 2000) {
            type = "text/csv";
            //offset = getTimeOffset(data);
        }
        server.stream(data, timestamp+offset, type);
    }

    /**
     * Retrieve the time offset from sensor data
     *
     * @param data sensor data line
     * @return the time offset
     */
    private long getTimeOffset(byte[] data) {
        try {
            StringBuilder buffer = new StringBuilder();
            for (byte b : data) {
                char c = (char) (b & 0xFF);
                switch (c) {
                    case '.':
                        break;
                    case ',':
                        return Long.valueOf(buffer.toString())*1000000L;
                    default:
                        buffer.append(c);
                }
            }
        } catch (Exception e) {
            Util.Log.d(getTag(),
                    String.format(Locale.US, "Cannot parse time from:", new String (data)));
        }
        return 0;
    }

    @Override
    public void close() throws IOException {
        //overriding default stream to manage everything in the streaming server
    }

    @Override
    public void disconnect() throws IOException {
        server.stop();
    }

}
