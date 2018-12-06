package it.francescogabbrielli.streaming.server;

import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

public class Streaming implements Runnable {

    /** A new random boundary */
    private static final String BOUNDARY = makeBoundary(32);
    /** Boundary line to separate parts in the stream */
    private static final String BOUNDARY_LINE = "\r\n--" + BOUNDARY + "\r\n";

    private static final int BOUNDARY_LENGTH = BOUNDARY_LINE.length();
    /** HTTP header */
    private static final String HTTP_HEADER =
            "HTTP/1.0 200 OK\r\n" +
                    "StreamingServer: SensorLogger\r\n" +
                    "Connection: close\r\n" +
                    "Max-Age: 0\r\n" +
                    "Expires: 0\r\n" +
                    "Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Content-Type: %s \r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "\r\n";


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
    private String contentType;
    private String address;
    private String dataHeaders;
    private int number;

    /** Size beyond which the outputstream is flushed */
    private final static int CHUNK_SIZE = 32 * 1024;

    Streaming(StreamingServer server, int number, Socket socket, String contentType) {
        this.number = number;
        this.server = server;
        this.socket = socket;
        this.withBoundary = contentType==null;
        this.contentType = contentType;
        address = socket.getLocalAddress()+":"+socket.getLocalPort();
    }

    public int getNumber() {
        return number;
    }

    public synchronized void setDataHeaders(String dataHeaders) {
        this.dataHeaders = dataHeaders;
    }

    synchronized void stream(Buffer buffer) {
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
        boolean brokenPipe = false;

        try {

            stream = new DataOutputStream(socket.getOutputStream());
            String header = String.format(HTTP_HEADER, withBoundary
                    ? "multipart/x-mixed-replace; boundary=" + BOUNDARY
                    : contentType);
            stream.writeBytes(header);
            Log.v(getTag(), header);
            int toFlush = header.length();

            // start recording automatically if set
            server.startCallback(this);

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

                    if (buffer==null)
                        return;

                    if (withBoundary) {
                        stream.writeBytes(BOUNDARY_LINE);
                        toFlush += BOUNDARY_LENGTH;
                        String partHeaders = buffer.getPartHeaders();
                        stream.writeBytes(partHeaders);
                        toFlush += partHeaders.length();
                    }

                    if (dataHeaders != null) {
                        stream.writeBytes(dataHeaders);
                        toFlush += dataHeaders.length();
                        dataHeaders = null;
                    }

                    stream.write(buffer.data, 0, buffer.length);
                    toFlush += buffer.length;

                    if (withBoundary) {
                        stream.writeBytes("\r\n");
                        toFlush += 2;
                    }

                    if (toFlush > CHUNK_SIZE) {
                        stream.flush();
                        toFlush = 0;
                    }

                    buffer = null;
                }

            }
        } catch (SocketException e) {
            Log.e(getTag(), "Broken stream", e);
            brokenPipe = true;
        } catch (IOException e) {
            Log.e(getTag(), "Error while streaming", e);
        } catch(Throwable t) {
            Log.e(getTag(), "Unexpected error", t);
        } finally {
            Log.d(getTag(), "Closing down");
            try {
                if (stream!=null && !brokenPipe) {
                    if (withBoundary && socket!=null) {
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
            server.end(this);
        }

    }

    private String getTag() {
        return "Streaming "+address;
    }

}
