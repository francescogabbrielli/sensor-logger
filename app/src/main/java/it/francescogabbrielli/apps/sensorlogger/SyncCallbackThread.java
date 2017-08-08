package it.francescogabbrielli.apps.sensorlogger;

import android.util.Log;

public class SyncCallbackThread  extends Thread {

    private final static long SYNC_TIMEOUT = 15000;

    private int locks;
    private Runnable finalCallback;

    public SyncCallbackThread(Runnable callback) {
        this.finalCallback = callback;
    }

    public synchronized void addLock() {
        locks++;
    }

    public synchronized void releaseLock() {
        locks--;
        notify();
    }

    @Override
    public void run() {
        try {
            synchronized (this) {
                long initial = System.currentTimeMillis();
                long elapsed = 0;
                while (elapsed<SYNC_TIMEOUT && locks>0) {
                    wait(SYNC_TIMEOUT-elapsed);
                    elapsed = System.currentTimeMillis()-initial;
                }
            }
        } catch (InterruptedException ie) {
            //Log.e(TAG, "Log sync interrupted", ie);
        }
        finalCallback.run();
    }
}
