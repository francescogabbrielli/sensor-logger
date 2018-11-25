package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;

import java.io.IOException;
import java.io.OutputStream;

import it.francescogabbrielli.streaming.server.StreamingServer;

/**
 * Streaming {@link LogTarget}. Stream data to an HTTP client
 */
public class LogStreaming extends LogTarget {

    /** StreamingServer port */
    private int port;
    /** Image Content-Type of the streaming */
    private String imageExt;
    /** The server */
    private StreamingServer server;
    /** If recording can be controlled remotely */
    private boolean remoteControl;
    /** If to try to find sensors headers when opening */
    private boolean sendHeaders;
    /** Streaming type: 0=None, 1=Image, 2=Sensors, 3=Both */
    private int type;

    public LogStreaming(LoggingService service, SharedPreferences prefs) {
        super(service, prefs);
        server = service.getStreamingServer();
        port = Util.getIntPref(prefs, Util.PREF_STREAMING_PORT);
        imageExt = prefs.getString(Util.PREF_CAPTURE_IMGFORMAT, "");
        if (!prefs.getBoolean(Util.PREF_CAPTURE_CAMERA, false))
            imageExt = null;
        remoteControl = prefs.getBoolean(Util.PREF_STREAMING_RECORD, false);
        type = Util.getIntPref(prefs, Util.PREF_STREAMING);

    }

    @Override
    protected OutputStream openOutputStream(String folder, String filename) throws IOException {
        return null;
    }

    @Override
    public void connect() throws IOException {
        if (!remoteControl)
            server.start(port,
                    (type & Util.LOG_IMAGE)==Util.LOG_IMAGE ? imageExt : null,
                    (type & Util.LOG_SENSORS) == Util.LOG_SENSORS);
    }

    @Override
    public void open(String folder, String filename) throws IOException {
        //overriding default stream to manage everything in the streaming server
        sendHeaders = true;
    }

    @Override
    public void write(byte[] data, long timestamp) throws IOException {
        if (data.length<2000) {//heuristic guess: TODO pass content-type?
            if (sendHeaders && data[0]>64) {
                sendHeaders = false;
                String headers = new String(data);
                int endLine = headers.indexOf('\n');
                if (endLine>0 && endLine<data.length) {
                    data = headers.substring(endLine + 1).getBytes();
                    server.setDataHeaders(headers.substring(0, endLine + 1));
                }
            }
            server.streamData(data, timestamp);
        } else
            server.streamImage(data, timestamp);
    }

    @Override
    public void close() throws IOException {
        //overriding default stream to manage everything in the streaming server
    }

    @Override
    public void disconnect() throws IOException {
        if (!remoteControl)
            server.stop();
        else
            server.reset();
    }

}
