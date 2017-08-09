package it.francescogabbrielli.apps.sensorlogger;

/**
 * Generic target where to log data
 *
 * TODO: move executor framework here
 */
public abstract class LogTarget {

    /**
     * Send data to the target
     *
     * TODO: add a kind of listener interface support instead of single callbacks
     */
    public abstract void send(final byte[] data, final String filename, final SyncCallbackThread scThread);

    /**
     * Implement final cleanup
     */
    public abstract void close();

}
