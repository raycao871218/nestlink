package com.naslink.android;

import java.nio.ByteBuffer;
import java.util.*;

/** IPv4/ARP adapter between Android's layer-3 TUN and ZeroTier's Ethernet port. */
public final class EthernetBridge {
    public interface Sender { void send(long destination, int type, byte[] data); }
    private final Sender sender;
    private final long mac;
    private final int local, mask, gateway;
    private final Map<Integer, Long> neighbors = new HashMap<>();
    private final Map<Integer, Long> requested = new HashMap<>();
    private final Map<Integer, ArrayDeque<byte[]>> pending = new HashMap<>();
    private int queued;
    public EthernetBridge(long mac, int local, int prefix, int gateway, Sender sender) {
        this.mac=mac; this.local=local; this.gateway=gateway; this.sender=sender;
        mask=prefix == 0 ? 0 : -1 << (32-prefix);
    }
    public static int ip(byte[] data, int offset) { return ByteBuffer.wrap(data,offset,4).getInt(); }
    public static long mac(byte[] data,int offset) {
        long result=0; for(int n=0;n<6;n++) result=(result<<8)|(data[offset+n]&255L); return result;
    }
    private static void putMac(ByteBuffer b,long m) { for(int n=5;n>=0;n--) b.put((byte)(m >>> (n*8))); }
    public static byte[] arp(int operation,long fromMac,int fromIp,long toMac,int toIp) {
        ByteBuffer b=ByteBuffer.allocate(28);
        b.putShort((short)1).putShort((short)0x0800).put((byte)6).put((byte)4).putShort((short)operation);
        putMac(b,fromMac); b.putInt(fromIp); putMac(b,toMac); b.putInt(toIp); return b.array();
    }
    public void outbound(byte[] packet,long now) {
        if(packet.length<20 || (packet[0]>>>4 & 15)!=4) return;
        int header=(packet[0]&15)*4, length=((packet[2]&255)<<8)|(packet[3]&255);
        if(header<20 || length<header || length>packet.length) return;
        byte[] payload=Arrays.copyOf(packet,length);
        int destination=ip(payload,16);
        int next=(destination & mask)==(local & mask) ? destination : gateway;
        Long target=neighbors.get(next);
        if(target!=null) { sender.send(target,0x0800,payload); return; }
        if(queued<64) { pending.computeIfAbsent(next,k->new ArrayDeque<>()).add(payload); queued++; }
        Long last=requested.get(next);
        if(last==null || now-last>=1000) {
            requested.put(next,now);
            sender.send(0xffffffffffffL,0x0806,arp(1,mac,local,0,next));
        }
    }
    public void inboundArp(long source,byte[] data) {
        if(data.length<28 || data[0]!=0 || data[1]!=1 || data[2]!=8 || data[3]!=0 || data[4]!=6 || data[5]!=4) return;
        int op=((data[6]&255)<<8)|(data[7]&255), from=ip(data,14), to=ip(data,24);
        if(op!=1 && op!=2 || mac(data,8)!=source || source==0 || (source&0x010000000000L)!=0) return;
        if((from & mask)!=(local & mask) || from==local) return;
        if(to!=local) return;
        neighbors.put(from,source);
        ArrayDeque<byte[]> queue=pending.remove(from);
        if(queue!=null) while(!queue.isEmpty()) { queued--; sender.send(source,0x0800,queue.remove()); }
        requested.remove(from);
        if(op==1) sender.send(source,0x0806,arp(2,mac,local,source,from));
    }
    public void expire() { neighbors.clear(); pending.clear(); requested.clear(); queued=0; }
}
