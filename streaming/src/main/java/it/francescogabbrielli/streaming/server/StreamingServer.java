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

    private static final String TAG = StreamingServer.class.getSimpleName();

    private static final long DELAY_LIMIT = 100000000L;

    private int port;

    private boolean running, stopped;
    private Thread thread;
    private BufferThread imageBufferThread, dataBufferThread;
    private ExecutorService threadPool;
    private List<StreamingThread> threads;
    private final static int N_THREADS = 4;
    private String contentType;

    private int counter;

    /** The server socket */
    private ServerSocket serverSocket;

    class StreamingThread {
        int number;
        Streaming streaming;
        Future future;
        StreamingThread(int number, Streaming streaming) {
            this.number = number;
            this.streaming = streaming;
            this.future = threadPool.submit(streaming);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Streaming
                    ? ((Streaming) obj).getNumber() == number : super.equals(obj);
        }

        @Override
        public int hashCode() {
            return streaming.hashCode();
        }
    }


    /** Recording control callback */
    private StreamingCallback callback;

    public StreamingServer() {
        this(null);//multipart
    }


    public StreamingServer(String contentType) {
        stopped = true;
        threadPool = Executors.newFixedThreadPool(N_THREADS);
        threads = Collections.synchronizedList(new LinkedList<StreamingThread>());
        this.contentType = contentType;
        counter = 0;
    }

    public synchronized void setCallback(StreamingCallback callback) {
        this.callback = callback;
    }

    public boolean isRunning() {
        return running;
    }

    synchronized void startCallback(Streaming s) {
        if (callback!=null)
            callback.onStartStreaming(s);
    }

    synchronized void stopCallback() {
        if (callback!=null)
            callback.onStopStreaming();
    }

    public synchronized boolean start(int port) {
        return start(port, contentType,false);
    }

    public synchronized boolean start(int port, String contentType) {
        return start(port, contentType,false);
    }

    /**
     * Start the server
     *
     * @param port server port
     * @param contentType frame content-type
     * @param streamSensors if streaming extra data for sensors together with the main data
     * @return true if it actually starts
     * @throws Exception
     */
    public synchronized boolean start(int port, String contentType, boolean streamSensors) {
        if (running || !stopped)
            return false;

        Log.i(TAG, "Start Streaming");

        this.port = port;

        if (contentType!=null)
            imageBufferThread = new BufferThread(this, contentType);
        if (streamSensors)
            dataBufferThread = new BufferThread(this, "text/csv");
        thread = new Thread(this);
        //thread.setDaemon(true);
        thread.start();
        running = true;
        return true;
    }

    /**
     * Stream frame data
     *
     * @param data the frame data to stream
     * @param timestamp the timestamp of the image
     * @return the {@link Runnable} performing the streaming
     */
    public synchronized void streamFrame(byte[] data, long timestamp) {
        imageBufferThread.stream(data, timestamp);
    }

    /**
     * Stream extra sensors data
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
            catch(Exception e) {Log.e(TAG, "Can't stop?", e);}
            if (imageBufferThread!=null)
                imageBufferThread.terminate();
            if (dataBufferThread !=null)
                dataBufferThread.terminate();
            for (StreamingThread s : threads) {
                s.streaming.stream(null);
                s.future.cancel(false);
            }
            threads.clear();
        }
    }

    /**
     * Reset client connections without stopping the server
     */
    public synchronized void reset() {
        Log.i(TAG, "Restart Streaming");
        for (StreamingThread st : threads)
            st.future.cancel(true);
        threads.clear();
    }

    /**
     * Main server loop
     */
    @Override
    public void run() {
        stopped = false;
        while (running)
            try {
                Log.d(TAG, "Listen for incoming connections on port "+port+"...");
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
            int n = counter++;
            Streaming s = new Streaming(this, n, socket, contentType);
            threads.add(new StreamingThread(n, s));
            serverSocket.close();
        } catch (final SocketTimeoutException e) {
            if (!running)
                return;
        }
        while (true);

    }

    void end(Streaming s) {
        threads.remove(s);
    }

    void stream(Buffer buffer) {
        for (StreamingThread st : threads)
            st.streaming.stream(buffer);
    }

    public void dispose() {
        stop();
        threadPool.shutdown();
    }

}
