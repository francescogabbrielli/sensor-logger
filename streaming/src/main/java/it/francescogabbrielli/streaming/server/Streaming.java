package it.francescogabbrielli.streaming.server;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Streaming implements Runnable {

    /** A new random boundary */
    private static final String BOUNDARY = makeBoundary(32);
    /** Boundary line to separate parts in the stream */
    private static final String BOUNDARY_LINE = "\r\n--" + BOUNDARY + "\r\n";

    private static final int BOUNDARY_LENGTH = BOUNDARY_LINE.length();
    /** HTTP header */
    private static final String HTTP_HEADER = (
            "HTTP/1.0 200 OK\r\n" +
                    "StreamingServer: SensorLogger\r\n" +
                    "Connection: close\r\n" +
                    "Max-Age: 0\r\n" +
                    "Expires: 0\r\n" +
                    "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=" + BOUNDARY + "\r\n" +
                    "\r\n");


    /** Create a random boundary */
    private static String makeBoundary(int len) {
        StringBuilder boundary = new StringBuilder();
        for (int i = 0; i < len / 2; i++)
            boundary.append(Integer.toHexString((int) (Math.random() * 255)));
        return boundary.toString();
    }


    private StreamingServer server;

    private Buffer buffer;

    private Socket socket;

    private boolean withBoundary;

    /** Size beyond which the outputstream is flushed */
    private final static int CHUNK_SIZE = 32 * 1024;

    Streaming(StreamingServer server, Socket socket) {
        this(server, socket, true);
    }

    Streaming(StreamingServer server, Socket socket, boolean withBoundary) {
        this.server = server;
        this.socket = socket;
        this.withBoundary = withBoundary;
    }

    public synchronized void stream(Buffer buffer) {
        this.buffer = buffer;
        notify();
    }

    /**
     * Write data to the currently connected client (socket)
     *
     * @throws IOException
     */
    @Override
    public void run() {

        DataOutputStream stream = null;

        try {

            stream = new DataOutputStream(socket.getOutputStream());
            stream.writeBytes(HTTP_HEADER);
            Log.v(getTag(), HTTP_HEADER);
            int toFlush = HTTP_HEADER.length();

            // start recording automatically if set
            server.startCallback();

            // stream current data
            while (server.isRunning()) {

                synchronized (this) {

                    //while (buffer==null)
                        try {
                            //Util.Log.d(getTag(), "Wait for data...");
                            wait();
                        } catch (final InterruptedException stopMayHaveBeenCalled) {
                            return;
                        }

                    if (withBoundary) {
                        stream.writeBytes(BOUNDARY_LINE);
                        toFlush += BOUNDARY_LENGTH;
                        String partHeaders = buffer.getPartHeaders();
                        stream.writeBytes(partHeaders);
                        toFlush += partHeaders.length();
                    }

                    if (buffer.headers!=null) {
                        stream.writeBytes(buffer.headers);
                        toFlush += buffer.headers.length();
                        buffer.headers = null;
                    }

                    stream.write(buffer.data, 0, buffer.length);
                    toFlush += buffer.length;

                    if (withBoundary) {
                        stream.writeBytes("\r\n");
                        toFlush += 2;
                    }

                    if (toFlush>CHUNK_SIZE) {
                        stream.flush();
                        toFlush = 0;
                    }

                    buffer = null;
                }

            }

        } catch (IOException e) {
            Log.e(getTag(), "Error while streaming", e);
        } catch(Throwable t) {
            Log.e(getTag(), "Unexpected error", t);
        } finally {
            Log.d(getTag(), "Closing down");
            try {
                if (stream!=null) {
                    if (withBoundary && socket!=null && !socket.isOutputShutdown()) {
                        stream.writeBytes(BOUNDARY_LINE + "--\r\n\r\n");
                        stream.flush();
                    }
                    stream.close();
                }
            } catch (final Exception e) {
                Log.e(getTag(), "Error closing streaming", e);
            }
            try { if (socket!=null) socket.close(); }
            catch (final Exception e) { Log.e(getTag(), "Error closing streaming client", e); }

        }

    }

    private String getTag() {
        return "Streaming";
    }

}
