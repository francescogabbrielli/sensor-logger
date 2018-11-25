package it.francescogabbrielli.streaming.server;

public class BufferThread extends Thread {

    private final static int N_BUFFERS = 4;

    /** Size of each buffer */
    private final static int BUFFER_SIZE = 512 * 1024;

    private final StreamingServer server;

    /** The streaming buffers */
    private Buffer[] buffers;
    /** Flag for new data available */
    private boolean newData;
    private int currentBuffer;

    BufferThread(StreamingServer server, String contentType) {
        super("BufferThread-"+contentType);
        this.server = server;
        buffers = new Buffer[N_BUFFERS];
        for (int i = 0; i < N_BUFFERS; i++)
            buffers[i] = new Buffer(BUFFER_SIZE, contentType);
        setDaemon(true);
        start();
    }

    synchronized void stream(byte[] data, long timestamp) {
        buffers[currentBuffer].setData(data, timestamp);
        newData = true;
        notify();
    }

    synchronized void streamAppend(byte[] data, long timestamp) {
        buffers[currentBuffer].appendData(data, timestamp);
        newData = true;
        notify();
    }

    synchronized void setHeaders(String headers) {
        buffers[currentBuffer].headers = headers;
    }

    @Override
    public void run() {

        // stream current data
        while (server.isRunning()) {

            Buffer buffer;

            synchronized (this) {

                while (!newData)
                    try {
                        //Util.Log.d(getTag(), "Wait for data...");
                        wait();
                    } catch (final InterruptedException stopMayHaveBeenCalled) {
                        return;
                    }

                buffer = buffers[currentBuffer++];
                currentBuffer %= N_BUFFERS;
                buffers[currentBuffer].reset();
                newData = false;
            }

            server.stream(buffer);

        }

    }

    void terminate() {
        newData = true;
        notify();
    }

}
