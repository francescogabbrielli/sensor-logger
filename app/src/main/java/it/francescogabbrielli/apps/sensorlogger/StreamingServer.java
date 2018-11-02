package it.francescogabbrielli.apps.sensorlogger;

import android.preference.PreferenceManager;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Simple HTTP streaming server, adapted to stream both images and sensors data.
 *
 * Can handle only one client connection.
 *
 * REFERENCES
 * ----------
 * https://github.com/foxdog-studios/peepers/blob/master/src/com/foxdogstudios/peepers/MJpegHttpStreamer.java
 */
public class StreamingServer implements Runnable {

    private static final String TAG = StreamingServer.class.getSimpleName();

    private static final String BOUNDARY = makeBoundary(32);
    private static final String BOUNDARY_LINE = "\r\n--" + BOUNDARY + "\r\n";
    private static final String HTTP_HEADER = (
            "HTTP/1.0 200 OK\r\n" +
                "Server: SensorLogger\r\n" +
                "Connection: close\r\n" +
                "Max-Age: 0\r\n" +
                "Expires: 0\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=" + BOUNDARY + "\r\n" +
                "\r\n");

    private static String makeBoundary(int len) {
        String boundary = "";
        for (int i = 0; i < len/2; i++)
            boundary += Integer.toHexString((int) (Math.random() * 255));
        return boundary;
    }

    private MainActivity main;

    private int port;
    private String boundaryFormat;
    private boolean running;
    private Thread thread;

    private Buffer[] buffers;
    private final static int N_BUFFERS = 3;
    private final static int BUFFER_SIZE = 512 * 1024;
    private final static int CHUNK_SIZE = 32 * 1024;
    private int currentBuffer;
    private boolean newData;
    private long delayLimit;

    class Buffer {
        byte[] data;
        int length;
        long timestamp;
        String contentType;
        Buffer() {
            this.data = new byte[BUFFER_SIZE];
        }
        void setData(byte[] data, long timestamp, String contentType) {
            length = data.length;
            System.arraycopy(data,0, this.data, 0, length);
            this.timestamp = timestamp;
            this.contentType = contentType;
        }
    }

    /**
     * If the main activity is specified the recording can be started on a streaming request
     *
     * @param main the {@link MainActivity}
     */
    public StreamingServer(MainActivity main) {
        buffers = new Buffer[N_BUFFERS];
        for(int i=0;i<N_BUFFERS;i++)
            buffers[i] = new Buffer();
        currentBuffer = 0;
        this.main = main;
        if (main!=null)
            start(Util.getIntPref(PreferenceManager.getDefaultSharedPreferences(main), Util.PREF_STREAMING_PORT));
    }

    /**
     * Start the server
     *
     * @param port
     * @return true if it actually starts
     * @throws Exception
     */
    public boolean start(int port) {
        if (running)
            return false;
        else
            running = true;

        Util.Log.i(TAG, "Start Streaming");

        this.port = port;
        boundaryFormat =
                "Content-type: %s\r\n"
                + "Content-Length: %d\r\n"
                + "X-Timestamp: %d\r\n"
                + "\r\n";

        thread = new Thread(this);
        //thread.setDaemon(true);
        thread.start();
        return true;
    }

    /**
     * Stream data
     *
     * @param data
     * @param timestamp
     * @throws IOException
     */
    public synchronized void stream(byte[] data, long timestamp, String contentType) throws IOException {
        buffers[currentBuffer].setData(data, timestamp, contentType);
        newData = true;
        notify();
    }

    /**
     * Stop the server
     */
    public void stop() {
        if (running) {
            running = false;
            Util.Log.i(TAG, "Stop Streaming");
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running)
            try {
                Util.Log.d(TAG, "Listen for incoming connections...");
                acceptAndSStream();
                if (main!=null)
                    main.stopRecording(R.string.toast_recording_endofstream);
            } catch (Exception e) {
                Util.Log.e(TAG, "Error while streaming", e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { }
            }
    }

    /**
     *
     * @throws IOException
     */
    private void acceptAndSStream() throws IOException {

        Socket socket = null;
        DataOutputStream stream = null;

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(0);

            // accept connection
            do try {
                socket = serverSocket.accept();
                if (main!=null)
                    main.startRecording();
                Util.Log.d(TAG, "Connected to " + socket);
                delayLimit = 100000000;
            } catch (final SocketTimeoutException e) {
                if (!running)
                    return;
            }

            while (socket == null);

            serverSocket.close();
            stream = new DataOutputStream(socket.getOutputStream());
            stream.writeBytes(HTTP_HEADER);

            int toFlush = HTTP_HEADER.length();

            // stream current data
            out:
            while (running) {

                Buffer buffer = null;

                synchronized (this) {

                    while (!newData)
                        try {
                            //Util.Log.d(TAG, "Wait for data...");
                            wait();
                        } catch (final InterruptedException stopMayHaveBeenCalled) {
                            break out;
                        }

                    buffer = buffers[currentBuffer++];
                    currentBuffer %= N_BUFFERS;
                    newData = false;
                }

//                if (SystemClock.elapsedRealtime() - buffer.timestamp > delayLimit) {
//                    Util.Log.i(TAG, "Streaming is delayed > " + delayLimit + "ns");
//                    delayLimit *= 2;
//                }

                stream.writeBytes(BOUNDARY_LINE);
                stream.writeBytes(String.format(Locale.US, boundaryFormat, buffer.contentType, buffer.length, buffer.timestamp));
                stream.write(buffer.data, 0, buffer.length);
                stream.writeBytes("\r\n");
                toFlush += buffer.length;
//                if (buffer.contentType.equals("text/csv"))
//                    Util.Log.i(TAG, buffer.contentType);
                if (toFlush>CHUNK_SIZE) {
                    stream.flush();
                    toFlush = 0;
                }
            }

            stream.writeBytes(BOUNDARY_LINE+"--\r\n\r\n");
            stream.flush();

        } catch (IOException e) {
            throw e;
        } catch(Throwable t) {
            Util.Log.e(TAG, "Unexpected error", t);
        } finally {
            if (serverSocket!=null)
                serverSocket.close();
            try {
                if (stream!=null)
                    stream.close();
            } catch (final Exception e) {
                Util.Log.e(TAG, "Error closing streaming", e);
            }
            try { if (socket!=null) socket.close(); }
            catch (final Exception e) { Util.Log.e(TAG, "Error closing streaming client", e); }
        }

    }

    public void dispose() {
        stop();
    }

}
