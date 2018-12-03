package it.francescogabbrielli.streaming.server;

import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Multithreaded HTTP streaming server, adapted to stream both images and data.
 *
 * REFERENCES
 * ----------
 * https://github.com/foxdog-studios/peepers/blob/master/src/com/foxdogstudios/peepers/MJpegHttpStreamer.java
 */
public class StreamingServer implements Runnable {

    private static final Map<String, String> CONTENT_TYPES = new HashMap<String, String>()
    {{
        put(".jpg", "image/jpeg");
        put(".png", "image/png");
    }};

    private static final String TAG = StreamingServer.class.getSimpleName();

    private static final long DELAY_LIMIT = 100000000L;

    private int port;

    private boolean running, stopped;
    private Thread thread;
    private BufferThread imageBufferThread, dataBufferThread;
    private ExecutorService threadPool;
    private List<Streaming> threads;
    private List<Future> futures;
    private final static int N_THREADS = 4;

    /** The server socket */
    private ServerSocket serverSocket;


    /** Recording control callback */
    private StreamingCallback callback;


    public StreamingServer() {
        stopped = true;
        threadPool = Executors.newFixedThreadPool(N_THREADS);
        threads = Collections.synchronizedList(new LinkedList<Streaming>());
        futures = Collections.synchronizedList(new LinkedList<Future>());
    }

    public synchronized void setCallback(StreamingCallback callback) {
        this.callback = callback;
    }

    public synchronized void setDataHeaders(String headers) {
        if (dataBufferThread !=null)
            dataBufferThread.setHeaders(headers);
    }

    public boolean isRunning() {
        return running;
    }

    synchronized void startCallback() {
        if (callback!=null)
            callback.onStartStreaming();
    }

    synchronized void stopCallback() {
        if (callback!=null)
            callback.onStopStreaming();
    }

    public synchronized boolean start(int port, String imageExt) {
        return start(port, imageExt,false);
    }

    /**
     * Start the server
     *
     * @param port
     * @return true if it actually starts
     * @throws Exception
     */
    public synchronized boolean start(int port, String imageExt, boolean streamSensors) {
        if (running || !stopped)
            return false;

        Log.i(TAG, "Start Streaming");

        this.port = port;

        if (imageExt!=null)
            imageBufferThread = new BufferThread(this, CONTENT_TYPES.get(imageExt)!=null ? CONTENT_TYPES.get(imageExt) : "image/*");
        if (streamSensors)
            dataBufferThread = new BufferThread(this, "text/csv");
        thread = new Thread(this);
        //thread.setDaemon(true);
        thread.start();
        running = true;
        return true;
    }

    /**
     * Stream image data
     *
     * @param data the image data to stream
     * @param timestamp the timestamp of the image
     * @return the {@link Runnable} performing the streaming
     */
    public synchronized void streamImage(byte[] data, long timestamp) {
        imageBufferThread.stream(data, timestamp);
    }

    /**
     * Stream sensors data
     *
     * @param data the sensors data to stream
     * @param timestamp the timestamp of the sensors data
     * @return the {@link Runnable} performing the streaming
     */
    public synchronized void streamData(byte[] data, long timestamp) {
        dataBufferThread.streamAppend(data, timestamp);
    }



    /**
     * Stop the server
     */
    public synchronized void stop() {
        if (running) {
            running = false;
            Log.i(TAG, "Stop Streaming");
            try { serverSocket.close(); }//in case is accepting
            catch(Exception e) {Log.e(TAG, "Can't onStopStreaming?", e);}
            if (imageBufferThread!=null)
                imageBufferThread.terminate();
            if (dataBufferThread !=null)
                dataBufferThread.terminate();
            for (Streaming s : threads)
                s.stream(null);
            for (Future f : futures)
                f.cancel(false);
            futures.clear();
        }
    }

    /**
     * Reset client connections without stopping the server
     */
    public synchronized void reset() {
        Log.i(TAG, "Restart Streaming");
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
                stopCallback();
            }
        stopped = true;
    }

    /**
     * Listen for incoming connections and stream in a new thread
     *
     * @throws IOException
     */
    private void acceptAndSStream() throws IOException {

        Socket socket = null;

        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(0);

        // accept connection
        do try {
            socket = serverSocket.accept();
            Log.d(TAG, "Connected to " + socket);
            Streaming s = new Streaming(this, socket);
            threads.add(s);
            futures.add(threadPool.submit(s));
            serverSocket.close();
        } catch (final SocketTimeoutException e) {
            if (!running)
                return;
        }
        while (true);

    }

    void stream(Buffer buffer) {
        for (Streaming s : threads)
            s.stream(buffer);
    }

    public void dispose() {
        stop();
        threadPool.shutdown();
    }

}
