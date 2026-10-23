package com.naslink.android;

import android.net.Network;
import android.net.VpnService;

/** One native core at a time. The service serializes start/stop on its engine. */
final class HomeProxyEngine {
    static { System.loadLibrary("NestLinkHome"); }
    private static final java.util.concurrent.atomic.AtomicBoolean claimed=new java.util.concurrent.atomic.AtomicBoolean();
    private Thread worker;
    private static native int run(String configuration,int fd,long network,VpnService service);
    private static native void quit();
    void start(int fd,Network network,VpnService service,String nas,int port,Runnable failure) {
        if(!claimed.compareAndSet(false,true)) throw new IllegalStateException("Previous home proxy is still stopping");
        String config="tunnel:\n  mtu: 1500\n  ipv4: 198.18.0.1\nsocks5:\n  address: '"+nas+"'\n  port: "+port+"\n  udp: udp\nmisc:\n  task-stack-size: 24576\n  tcp-buffer-size: 32768\n  max-session-count: 192\n  connect-timeout: 10000\n  log-level: error\n";
        worker=new Thread(() -> {
            try { run(config,fd,network.getNetworkHandle(),service); }
            finally { failure.run(); }
        },"nestlink-home");
        try { worker.start(); } catch(RuntimeException|Error e) { claimed.set(false); worker=null; throw e; }
    }
    void stop() throws InterruptedException {
        if(worker==null) return;
        if(worker.isAlive()) quit();
        worker.join(12000);
        if(worker.isAlive()) throw new IllegalStateException("Home proxy did not stop");
        worker=null; claimed.set(false);
    }
}
