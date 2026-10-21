package com.naslink.android;

/** Path liveness from actual engine receive timestamps, not discovery or cached latency. */
public final class MoonConnectivity {
    public static final long FRESH_MS=120_000;
    private MoonConnectivity() {}
    public static String state(boolean found,boolean moon,long lastReceive,long now,long observedSince) {
        if(!found) return "discovering";
        if(!moon) return "unconfirmed";
        if(lastReceive<=0) return "no_path";
        if(lastReceive<observedSince) return "waiting";
        long age=now-lastReceive;
        return age>=0 && age<=FRESH_MS?"online":"no_path";
    }
}
