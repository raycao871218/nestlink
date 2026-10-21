import com.naslink.android.ConnectionSettings;
import com.naslink.android.EthernetBridge;
import java.nio.ByteBuffer;
import java.util.*;

public final class NetworkCoreTest {
    static final long CLIENT=0x021122334455L, NAS=0x0266778899aaL;
    static final int LOCAL=0x0a0c0011, GATEWAY=0x0a0c0042;
    static class Frame { long mac; int type; byte[] data; Frame(long m,int t,byte[] d){mac=m;type=t;data=d;} }
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static byte[] packet(int dest) {
        byte[] p=new byte[20]; p[0]=0x45;p[3]=20;p[8]=64;p[9]=6;
        ByteBuffer.wrap(p).putInt(12,LOCAL).putInt(16,dest);return p;
    }
    public static void main(String[] args) throws Exception {
        ArrayList<Frame> frames=new ArrayList<>();
        EthernetBridge bridge=new EthernetBridge(CLIENT,LOCAL,24,GATEWAY,(m,t,d)->frames.add(new Frame(m,t,d)));
        bridge.outbound(packet(0x08080808),1000);
        check(frames.size()==1 && frames.get(0).type==0x0806,"must resolve gateway before public traffic");
        check(EthernetBridge.ip(frames.get(0).data,24)==GATEWAY,"public traffic next hop must be NAS");
        bridge.inboundArp(NAS,EthernetBridge.arp(2,NAS,GATEWAY,CLIENT,LOCAL));
        check(frames.size()==2 && frames.get(1).mac==NAS && frames.get(1).type==0x0800,"ARP response must flush queued IPv4");
        check(EthernetBridge.ip(frames.get(1).data,16)==0x08080808,"do not rewrite original IP destination");
        frames.clear();bridge.outbound(packet(0x0a0c0022),2000);
        check(EthernetBridge.ip(frames.get(0).data,24)==0x0a0c0022,"same-subnet ZeroTier peer must resolve directly");
        frames.clear();bridge.inboundArp(NAS,EthernetBridge.arp(1,NAS,GATEWAY,0,LOCAL));
        check(frames.size()==1 && frames.get(0).type==0x0806 && frames.get(0).mac==NAS,"answer requests for our IPv4 address");
        frames.clear();bridge.inboundArp(NAS,new byte[2]);bridge.outbound(new byte[19],0);
        byte[] bad=packet(0x08080808);bad[3]=40;bridge.outbound(bad,0);
        check(frames.isEmpty(),"ignore malformed/truncated frames");
        bridge.expire();bridge.outbound(packet(0x08080808),4000);
        check(frames.size()==1 && frames.get(0).type==0x0806,"network switch must invalidate neighbor cache");
        check(ConnectionSettings.ipv4("192.0.2.66").getHostAddress().equals("192.0.2.66"),"valid gateway");
        for(String value:new String[]{"127.1","10.0.0.256","example.com","01.2.3.4","10.0.0.-1","10.0.0.1/24"}) {
            try { ConnectionSettings.ipv4(value); throw new AssertionError("accepted "+value); }
            catch(java.net.UnknownHostException expected) {}
        }
        System.out.println("PASS gateway selection, peer routing, ARP queue/reply, malformed packets, cache invalidation, strict IPv4 validation");
    }
}

