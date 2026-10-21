import com.naslink.android.MoonConnectivity;
public final class MoonConnectivityTest {
    static void expect(String state,boolean found,boolean moon,long last,long now,long started) {
        if(!state.equals(MoonConnectivity.state(found,moon,last,now,started))) throw new AssertionError(state);
    }
    public static void main(String[] args) {
        long now=1_000_000,start=900_000;
        expect("discovering",false,false,0,now,start);
        expect("unconfirmed",true,false,now,now,start);
        expect("no_path",true,true,0,now,start);
        expect("online",true,true,now-20,now,start);
        expect("online",true,true,now-120_000,now,800_000);
        expect("no_path",true,true,now-120_001,now,800_000);
        expect("waiting",true,true,start-1,now,start);
        expect("no_path",true,true,now+1,now,start);
        expect("online",true,true,start,now,start);
        System.out.println("PASS Moon discovery versus liveness, stale/absent replies, network-change invalidation and clock boundaries");
    }
}
