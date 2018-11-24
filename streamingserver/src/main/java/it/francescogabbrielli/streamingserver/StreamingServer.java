package it.francescogabbrielli.streamingserver;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private static final long DELAY_LIMIT = 100000000L;

    /** A new random boundary */
    private static final String BOUNDARY = makeBoundary(32);
    /** Boundary line to separate parts in the stream */
    private static final String BOUNDARY_LINE = "\r\n--" + BOUNDARY + "\r\n";
    /** HTTP header */
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

    private static final String PART_FORMAT =
            "Content-type: %s\r\n"
            + "Content-Length: %d\r\n"
            + "X-Timestamp: %d\r\n"
            + "\r\n";

    /** Create a random boundary */
    private static String makeBoundary(int len) {
        String boundary = "";
        for (int i = 0; i < len / 2; i++)
            boundary += Integer.toHexString((int) (Math.random() * 255));
        return boundary;
    }

    private int port;

    private boolean running, stopped;
    private ExecutorService threadPool;
    private List<Future> futures;
    private final static int N_THREADS = 4;

    /** The server socket */
    private ServerSocket serverSocket;
    private Socket socket;

    /** The streaming buffers */
    private Buffer[] buffers;
    /** The streaming data buffers */
    private Buffer[] dataBuffers;
    /** Number of buffers */
    private final static int N_BUFFERS = 3;
    /** Size of each buffer */
    private final static int BUFFER_SIZE = 512 * 1024;
    /** Size beyond which the outputstream is flushed */
    private final static int CHUNK_SIZE = 32 * 1024;
    /** Current buffer index */
    private int currentBuffer, currentDataBuffer;
    /** New data available */
    private boolean newImage, newData;
    /** Delay beyond which to logcat a delay in the streaming */
    private long delayLimit;

    private String textHeaders;

    /** Recording control callback */
    private Callback callback;

    /**
     * Buffer where to write the data to be streamed
     */
    class Buffer {
        /** Buffer data. Of fixed length BUFFER_SIZE */
        byte[] data;
        /** Actual length of current data */
        int length;
        /** Timestamp of current data */
        long timestamp;
        /** Content-Type of current data */
        String contentType;

        Buffer() {
            this.data = new byte[BUFFER_SIZE];
        }

        /**
         * Write data into this buffer
         * @param data the actual data to be streamed
         * @param timestamp the timestamp of the data
         * @param contentType the content type of the data
         */
        void setData(byte[] data, long timestamp, String contentType) {
            length = data.length;
            System.arraycopy(data, 0, this.data, 0, length);
            this.timestamp = timestamp;
            this.contentType = contentType;
        }

        void appendData(byte[] data, long timestamp) {
            int curr = length;
            length += data.length;
            System.arraycopy(data, 0, this.data, curr, data.length);
            this.timestamp = timestamp;
            contentType = "text/csv";
        }

        @Override
        public String toString() {
            return String.format(Locale.US, PART_FORMAT, contentType, length, timestamp);
        }
    }

    public StreamingServer() {
        buffers = new Buffer[N_BUFFERS];
        dataBuffers = new Buffer[N_BUFFERS];
        for (int i = 0; i < N_BUFFERS; i++) {
            buffers[i] = new Buffer();
            dataBuffers[i] = new Buffer();
        }
        currentBuffer = 0;
        currentDataBuffer = 0;
        stopped = true;
        threadPool = Executors.newFixedThreadPool(N_THREADS);
        futures = new LinkedList<>();
    }

    public synchronized void setCallback(Callback callback) {
        this.callback = callback;
    }

    public synchronized void setTextHeaders(String headers) {
        this.textHeaders = headers;
    }

    boolean isRunning() {
        return running;
    }

    synchronized void startCallback() {
        if (callback!=null)
            callback.start();
    }

    synchronized void stopCallback() {
        if (callback!=null)
            callback.stop();
    }

    synchronized void streamTextHeaders(DataOutputStream stream) throws IOException {
        if (textHeaders!=null) {
            stream.writeBytes(textHeaders);
            textHeaders = null;
        }
    }

    /**
     * Start the server
     *
     * @param port
     * @return true if it actually starts
     * @throws Exception
     */
    public synchronized boolean start(int port) {
        if (running || !stopped)
            return false;

        Log.i(TAG, "Start it.francescogabbrielli.streamingserver.Streaming");

        this.port = port;

        running = true;
        futures.add(threadPool.submit(this));
        return true;
    }

    /**
     * Stream data
     *
     * @param data
     * @param timestamp
     * @throws IOException
     */
    public synchronized void streamImage(byte[] data, long timestamp, String contentType) {
        buffers[currentBuffer].setData(data, timestamp, contentType);
        newImage = true;
        notify();
    }

    public synchronized void streamData(byte[] data, long timestamp) {
        dataBuffers[currentDataBuffer].appendData(data, timestamp);
        newData = true;
        notify();
    }



    /**
     * Stop the server
     */
    public synchronized void stop() {
        if (running) {
            running = false;
            Log.i(TAG, "Stop it.francescogabbrielli.streamingserver.Streaming");
            try { serverSocket.close(); }//in case is accepting
            catch(Exception e) {Log.e(TAG, "Can't stop?", e);}
            newData = true;
            notify();
            futures.clear();
        }
    }

    public synchronized void restart() {
        Log.i(TAG, "Restart it.francescogabbrielli.streamingserver.Streaming");
        for (Future f : futures)
            f.cancel(true);
        futures.clear();
    }

    /**
     * Main server loop
     */
    @Override
    public void run() {
        stopped = false;
        while (running)
            try {
                Log.d(TAG, "Listen for incoming connections...");
                acceptAndSStream();
            } catch( SocketException e ) {
                //e.printStackTrace();
            } catch ( Exception e ) {
                Log.e(TAG, "Error while streaming", e);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { }
            } finally {
                if (callback != null)
                    callback.stop();
            }
        stopped = true;
    }

    /**
     * Listen for incoming connections and stream after receiving one
     *
     * @throws IOException
     */
    private void acceptAndSStream() throws IOException {

        DataOutputStream stream = null;

        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(0);

            // accept connection
            do try {
                socket = serverSocket.accept();
                Log.d(TAG, "Connected to " + socket);
                delayLimit = DELAY_LIMIT;
            } catch (final SocketTimeoutException e) {
                if (!running)
                    return;
            }
            while (socket == null);

            serverSocket.close();
            stream = new DataOutputStream(socket.getOutputStream());
            stream.writeBytes(HTTP_HEADER);
            Log.v(TAG, HTTP_HEADER);
            int toFlush = HTTP_HEADER.length();

            // start recording automatically if set
            if (callback !=null)
                callback.start();

            // stream current data
            out:
            while (running) {
                Buffer buffer, dataBuffer;
                synchronized (this) {

                    while (!newData && !newImage)
                        try {
                            //Util.Log.d(TAG, "Wait for data...");
                            wait();
                        } catch (final InterruptedException stopMayHaveBeenCalled) {
                            break out;
                        }


                    dataBuffer = newData ? dataBuffers[currentDataBuffer++] : null;
                    buffer = newImage ? buffers[currentBuffer++] : null;
                    //Util.Log.v(TAG, "SWAP!");
                    currentBuffer %= N_BUFFERS;
                    currentDataBuffer %= N_BUFFERS;
                    if (newData)
                        dataBuffers[currentDataBuffer].length = 0;
                    newData = false;
                    newImage = false;
                }


                if (dataBuffer!=null) {
                    stream.writeBytes(BOUNDARY_LINE);
                    stream.writeBytes(dataBuffer.toString());
                    if (textHeaders!=null) {
                        stream.writeBytes(textHeaders);
                        textHeaders = null;
                    }
                    stream.write(dataBuffer.data, 0, dataBuffer.length);
                    stream.writeBytes("\r\n");
                    toFlush += dataBuffer.length;
                }

                if (buffer!=null) {
                    stream.writeBytes(BOUNDARY_LINE);
                    stream.writeBytes(buffer.toString());
                    stream.write(buffer.data, 0, buffer.length);
                    stream.writeBytes("\r\n");
                    toFlush += buffer.length;
                }

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
            Log.e(TAG, "Unexpected error", t);
        } finally {
            Log.d(TAG, "Closing down");
            if (serverSocket!=null)
                serverSocket.close();
            try {
                if (stream!=null)
                    stream.close();
            } catch (final Exception e) {
                Log.e(TAG, "Error closing streaming", e);
            }
            try { if (socket!=null) socket.close(); }
            catch (final Exception e) { Log.e(TAG, "Error closing streaming client", e); }

        }

    }

    public void dispose() {
        stop();
        threadPool.shutdown();
    }

}
