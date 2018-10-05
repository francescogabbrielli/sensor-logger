package it.francescogabbrielli.apps.sensorlogger;

import android.content.SharedPreferences;
import android.os.SystemClock;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

public class LogStreaming extends LogTarget {

    private ServerSocket server;
    private Socket socket;
    private String boundary, contentBegin;
    private byte[] end;

    public LogStreaming(SharedPreferences prefs) {
        super(prefs);
        boundary = "";
        for (int i = 0; i < 16; i++)
            boundary += Integer.toHexString((int) (Math.random() * 255));
        contentBegin = "--" + boundary + "\r\n" +
                "Content-type: image/jpeg\r\n" +
                "Content-Length: %d\r\n" +
                "X-Timestamp: %d\r\n" +
                "\r\n";
        end = "\r\n\r\n".getBytes();

        post(new Runnable() {
            @Override
            public void run() {
                try {
                    init();
                } catch(Exception e) {
                    Util.Log.e(getTag(), "Open streaming error", e);
                    dispose();
                }
            }
        });

    }

    public void init() throws Exception {
        server = new ServerSocket(8080);
        socket = server.accept();
        //server.close();
        out = socket.getOutputStream();
        out.write(("HTTP/1.0 200 OK\r\n" +
                "Server: SensorLogger\r\n" +
                "Connection: close\r\n" +
                "Max-Age: 0\r\n" +
                "Expires: 0\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: multipart/x-mixed-replace; " +
                "boundary=" + boundary + "\r\n" +
                "\r\n" +
                "--"+ boundary + "\r\n").getBytes());
        Util.Log.i(getTag(), "Start Streaming");
    }

    @Override
    public void open(String folder, String filename) throws IOException {
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
    }

    @Override
    public void dispose() {
        Util.Log.i(getTag(), "Stop Streaming");
        try {
            super.close();
            socket.close();
            server.close();
        } catch(Exception e) {
            Util.Log.e(getTag(), "Close streaming error", e);
        }
        super.dispose();
    }
}
