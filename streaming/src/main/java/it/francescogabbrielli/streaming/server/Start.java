package it.francescogabbrielli.streaming.server;

public class Start implements Runnable {

    private StreamingServer server;
    private int port;
    private String ext;
    private boolean sensors;

    public Start(StreamingServer server, int port, String ext) {
        this(server, port, ext, false);
    }

    public Start(StreamingServer server, int port, String ext, boolean sensors) {
        this.server = server;
        this.port = port;
        this.ext = ext;
        this.sensors = sensors;
    }

    @Override
    public void run() {
        server.start(port, ext, sensors);
    }

}
