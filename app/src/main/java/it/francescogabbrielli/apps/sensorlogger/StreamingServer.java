package it.francescogabbrielli.apps.sensorlogger;

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
    private static StreamingServer instance;

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

    /**
     * Get the streaming server singleton instance

     * @return the server instance
     */
    public static StreamingServer getInstance() {
        if (instance==null)
            instance = new StreamingServer();
        return instance;
    }

    private int port;
    private String boundaryFormat;
    private boolean running;
    private Thread thread;

    private Buffer[] buffers;
    private final static int N_BUFFERS = 4;
    private final static int BUFFER_SIZE = 2000000;
    private int currentBuffer;
    private boolean newData;

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

    private StreamingServer() {
        buffers = new Buffer[N_BUFFERS];
        for(int i=0;i<N_BUFFERS;i++)
            buffers[i] = new Buffer();
        currentBuffer = 0;
    }

    /**
     * Start the server
     *
     * @param port
     * @throws Exception
     */
    public void start(int port) {
        if (running)
            return;

        this.port = port;
        boundaryFormat = "Content-type: %s\r\n"
                + "Content-Length: %d\r\n"
                + "X-Timestamp:%d\r\n"
                + "\r\n";

        running = true;
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
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
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        while (running)
            try {
                Util.Log.d(TAG, "Listen for incoming connections...");
                acceptAndSStream();
            } catch (Exception e) {
                Util.Log.e(TAG, "Error while streaming", e);
            }
    }

    /**
     *
     * @throws IOException
     */
    private void acceptAndSStream() throws IOException {

        Socket socket = null;
        DataOutputStream stream = null;

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            serverSocket.setSoTimeout(1000 );

            // accept connection
            do try {
                    socket = serverSocket.accept();
                    Util.Log.d(TAG, "Connected to "+socket);
                } catch (final SocketTimeoutException e) {
                    if (!running)
                        return;
                }
            while (socket == null);

            serverSocket.close();
            stream = new DataOutputStream(socket.getOutputStream());
            stream.writeBytes(HTTP_HEADER);
            stream.flush();

            // stream current data
            while (running) {

                Buffer buffer = null;

                synchronized (this) {

                    while (!newData)
                        try {
//                            Util.Log.d(TAG, "Wait for image...");
                            wait();
                        } catch (final InterruptedException stopMayHaveBeenCalled) {
                            return;
                        }

                    buffer = buffers[currentBuffer++];
                    currentBuffer %= N_BUFFERS;
                    newData = false;
                }

                stream.writeBytes(BOUNDARY_LINE);
                stream.writeBytes(String.format(Locale.US, boundaryFormat, buffer.contentType, buffer.length, buffer.timestamp));
                stream.write(buffer.data, 0, buffer.length);
                stream.writeBytes("\r\n\r\n");
                stream.flush();
            }

        } finally {
            try { if (stream!=null) stream.close(); }
            catch (final Exception e) { Util.Log.e(TAG, "Error closing streaming", e); }
            try { if (socket!=null) socket.close(); }
            catch (final Exception e) { Util.Log.e(TAG, "Error closing streaming client", e); }
        }

    }

}
