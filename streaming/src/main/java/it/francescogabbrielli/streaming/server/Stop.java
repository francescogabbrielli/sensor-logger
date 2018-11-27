package it.francescogabbrielli.streaming.server;

public class Stop implements Runnable {

    private StreamingServer server;

    public Stop(StreamingServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        server.stop();
    }

}
