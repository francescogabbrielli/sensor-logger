package it.francescogabbrielli.streaming.server;

import android.os.Handler;
import android.os.HandlerThread;

public class BufferThread implements Runnable {

    private final static int N_BUFFERS = 4;

    /** Size of each buffer */
    private final static int BUFFER_SIZE = 512 * 1024;

    private final StreamingServer server;

    /** The streaming buffers */
    private Buffer[] buffers;
    /** Index of current buffer */
    private int current;

    private HandlerThread thread;
    private Handler handler;

    BufferThread(StreamingServer server, String contentType) {
        //super("BufferThread-"+contentType);
        this.server = server;
        buffers = new Buffer[N_BUFFERS];
        for (int i = 0; i < N_BUFFERS; i++)
            buffers[i] = new Buffer(BUFFER_SIZE, contentType);
        thread = new HandlerThread("BufferThread-"+contentType);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    synchronized void stream(byte[] data, long timestamp) {
        //Log.d("BUFFER","Streaming data: "+data.length);
        buffers[current].setData(data, timestamp);
//        return this;
        handler.post(this);
    }

    synchronized void streamAppend(byte[] data, long timestamp) {
        buffers[current].appendData(data, timestamp);
//        return this;
        handler.post(this);
    }

    @Override
    public void run() {

        Buffer buffer;

        synchronized (this) {
            buffer = buffers[current++];
            current %= N_BUFFERS;
            buffers[current].reset();
        }

        server.stream(buffer);

    }

    synchronized void terminate() {
        thread.quitSafely();
    }

}
