package it.francescogabbrielli.streaming.server;

public interface StreamingCallback {

    void onStartStreaming(Streaming s);

    void onStopStreaming();

}
