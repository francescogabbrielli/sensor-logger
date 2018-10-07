package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Basic streaming server. Stream data (images) to an HTTP client
 * EXPERIMENTAL
 */
public class LogStreaming extends LogTarget {

    private static final Map<String, String> CONTENT_TYPES = new HashMap<String, String>()
    {{
        put(".jpg", "image/jpeg");
        put(".png", "image/png");
    }};

    private int port;
    private ServerSocket server;
    private Socket socket;
    private String boundary, contentBegin;
    private byte[] end;

    public LogStreaming(SharedPreferences prefs) {
        super(prefs);
        port = Util.getIntPref(prefs, Util.PREF_STREAMING_PORT);
        String imgFormat = prefs.getString(Util.PREF_CAPTURE_IMGFORMAT, ".png");
        String contentType = CONTENT_TYPES.get(imgFormat);
        if (contentType==null)
            contentType = "image/*";

        //boundary data
        boundary = "";
        for (int i = 0; i < 16; i++)
            boundary += Integer.toHexString((int) (Math.random() * 255));
        contentBegin = "--" + boundary + "\r\n" +
                "Content-type: " + contentType +"\r\n" +
                "Content-Length: %d\r\n" +
                "X-Timestamp: %d\r\n" +
                "\r\n";
        end = "\r\n\r\n".getBytes();

        //        post(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    init();
//                } catch(Exception e) {
//                    Util.Log.e(getTag(), "Open streaming error", e);
//                    disconnect();
//                }
//            }
//        });

    }

    @Override
    protected OutputStream openOutputStream(String folder, String filename) throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public void connect() throws IOException {
        server = new ServerSocket(port);
        socket = server.accept();
        server.close();
        socket.getOutputStream().write(("HTTP/1.0 200 OK\r\n" +
                "Server: SensorLogger\r\n" +
                "Connection: close\r\n" +
                "Max-Age: 0\r\n" +
                "Expires: 0\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: multipart/x-mixed-replace; " +
                "boundary=" + boundary + "\r\n" +
                "\r\n").getBytes());
        Util.Log.i(getTag(), "Start Streaming");
    }

    @Override
    public void write(byte[] data) throws IOException {
        out.write(String.format(Locale.US, contentBegin, data.length, SystemClock.elapsedRealtime()).getBytes());
        super.write(data);
        out.write(end);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        //overriding to leave stream open
    }

    @Override
    public void disconnect() throws IOException {
        Util.Log.i(getTag(), "Stop Streaming");
        try {
            super.close();
            socket.close();
            server.close();
        } catch(Exception e) {
            Util.Log.e(getTag(), "Close streaming error", e);
        }
    }

}
