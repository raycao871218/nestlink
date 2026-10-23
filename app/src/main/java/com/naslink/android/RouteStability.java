package com.naslink.android;

/** Debounce only while a usable route exists. Loss/recovery is immediate. */
public final class RouteStability {
    private String pending="";
    private long since;
    public boolean ready(String current,String desired,long now,boolean usable) {
        if(current.equals(desired) && usable) { pending=""; return false; }
        if(!usable) { pending=""; return true; }
        if(!desired.equals(pending)) { pending=desired; since=now; return false; }
        if(now-since<5000) return false;
        pending=""; return true;
    }
    public static boolean retainHome(boolean enabled,boolean wifi,boolean sameNetwork,boolean home,
                                     String configured,String observed) {
        if(!enabled || !wifi || configured.isEmpty()) return false;
        if(configured.equals(observed)) return true;
        // Android may redact SSID when the activity loses foreground access.
        // Trust only the already-matched live Network, never a new handle.
        return observed.isEmpty() && sameNetwork && home;
    }
}
