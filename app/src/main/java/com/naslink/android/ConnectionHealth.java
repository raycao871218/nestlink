package com.naslink.android;

/** Rejects health results belonging to an earlier tunnel or physical network. Engine-thread confined. */
public final class ConnectionHealth {
    private long epoch;
    private boolean connected;
    public long epoch() { return epoch; }
    public void invalidate() { epoch++; connected=false; }
    public boolean accept(long generation, boolean lan, boolean internet) {
        if(generation!=epoch) return false;
        connected=lan && internet;
        return true;
    }
    public boolean connected() { return connected; }
}
