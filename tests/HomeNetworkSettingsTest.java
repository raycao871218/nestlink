import com.naslink.android.HomeNetworkSettings;

public final class HomeNetworkSettingsTest {
    private static void check(boolean value) { if(!value) throw new AssertionError(); }
    private static void invalid(String ssid,String nas,int port) {
        try { HomeNetworkSettings.validate(true,ssid,nas,port); throw new AssertionError(); }
        catch(IllegalArgumentException expected) {}
    }
    public static void main(String[] args) {
        HomeNetworkSettings.validate(false,"","",0);
        HomeNetworkSettings.validate(true," Home Wi-Fi ","192.168.1.66",7890);
        HomeNetworkSettings.validate(true,"家".repeat(10),"172.31.1.1",65535);
        invalid("家".repeat(11),"192.168.1.66",7890);
        invalid(" ","192.168.1.66",7890); invalid("home\n","192.168.1.66",7890);
        invalid("home","8.8.8.8",7890); invalid("home","127.0.0.1",7890);
        invalid("home","172.32.0.1",7890); invalid("home","192.168.01.1",7890);
        invalid("home","10.1.1.1",0); invalid("home","10.1.1.1",65536);
        check(HomeNetworkSettings.sameSubnet("192.168.1.66","192.168.1.2",24));
        check(!HomeNetworkSettings.sameSubnet("192.168.1.66","192.168.2.2",24));
        check(!HomeNetworkSettings.sameSubnet("192.168.1.66","192.168.1.66",24));
        check(!HomeNetworkSettings.sameSubnet("192.168.1.66","192.168.1.2",0));
        check(HomeNetworkSettings.unquote("\" Home \"").equals(" Home "));
        check(HomeNetworkSettings.unquote("<unknown ssid>").isEmpty());
        check(HomeNetworkSettings.unquote(null).isEmpty());
        System.out.println("PASS home private IPv4, subnet boundaries, UTF-8 SSID, exact names, ports and redaction");
    }
}
