import com.naslink.android.RouteStability;

public final class RouteStabilityTest {
    private static void check(boolean ok) { if(!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        RouteStability gate=new RouteStability();
        check(!gate.ready("wifi-home","wifi-away",0,true));
        check(!gate.ready("wifi-home","wifi-away",4999,true));
        check(!gate.ready("wifi-home","wifi-home",5000,true));
        check(!gate.ready("wifi-home","wifi-away",6000,true));
        check(gate.ready("wifi-home","wifi-away",11000,true));
        check(gate.ready("wifi-home","cell-away",11001,false));
        check(gate.ready("cell-away","cell-away",11002,false));
        check(RouteStability.retainHome(true,true,true,true,"Home",""));
        check(!RouteStability.retainHome(true,true,true,false,"Home",""));
        check(!RouteStability.retainHome(true,true,false,true,"Home",""));
        check(!RouteStability.retainHome(true,false,true,true,"Home",""));
        check(!RouteStability.retainHome(true,true,true,true,"Home","Other"));
        check(!RouteStability.retainHome(false,true,true,true,"Home","Home"));
        check(!RouteStability.retainHome(true,true,true,true,"Home","home"));
        check(RouteStability.retainHome(true,true,false,false,"Home","Home"));
        System.out.println("PASS transient route changes suppressed; sustained changes, loss/recovery and SSID redaction tied to the same live network");
    }
}
