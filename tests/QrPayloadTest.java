import com.naslink.android.QrPayload;

public final class QrPayloadTest {
    static final String ID="8056c2e21c000001";
    static void check(boolean ok) {if(!ok) throw new AssertionError();}
    static void reject(String value) {try {QrPayload.parse(value);throw new AssertionError("Accepted invalid payload");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args) {
        check(QrPayload.parse("  "+ID.toUpperCase()+"  ").network.equals(ID));
        check(QrPayload.parse("zerotier://"+ID).network.equals(ID));
        check(QrPayload.parse("zerotier://network/"+ID).network.equals(ID));
        check(QrPayload.parse("https://my.zerotier.com/network/"+ID).network.equals(ID));
        check(QrPayload.parse("https://central.zerotier.com/networks/"+ID+"/").network.equals(ID));
        QrPayload only=QrPayload.parse(ID);check(only.gateway==null&&only.port==null&&only.moons==null);
        String uri=QrPayload.encode(ID,"192.0.2.2",7890,"000000abcdef0123 abcdef0123");
        QrPayload full=QrPayload.parse(uri);check(full.network.equals(ID)&&full.gateway.equals("192.0.2.2")&&full.port==7890&&full.moons.contains("abcdef0123"));
        check(QrPayload.parse(QrPayload.encode(ID,"192.0.2.2",7890,"")).moons.equals(""));
        for(String bad:new String[]{"",null,"0000000000000000","8056c2e21c00000","8056c2e21c0000011","javascript:alert(1)","https://evil.example/network/"+ID,"zerotier://network/"+ID+"?token=secret","zerotier://user@"+ID,"harborlink://join?network="+ID+"&network="+ID,"harborlink://join?network="+ID+"&gateway=example.com","harborlink://join?network="+ID+"&port=65536","harborlink://join?network="+ID+"&moons=invalid","harborlink://join?network="+ID+"&token=secret","harborlink://join?network="+ID+"#fragment","x".repeat(4097)}) reject(bad);
        System.out.println("PASS network QR formats, complete config roundtrip, optional fields preserved, invalid/duplicate/foreign input rejected");
    }
}
