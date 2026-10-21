import com.naslink.android.MoonSettings;
import java.util.*;

public final class MoonSettingsTest {
    static void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
    public static void main(String[] args) {
        check(MoonSettings.parse(" \n\r\n").isEmpty(),"clear all");
        List<MoonSettings.Moon> one=MoonSettings.parse("deadbeef00");
        check(one.get(0).seed==one.get(0).world,"conventional default seed");
        check(MoonSettings.format(one).equals("000000deadbeef00 deadbeef00"),"normalized output");
        check(MoonSettings.parse("0xdeadbeef00\n000000DEADBEEF00 deadbeef00").size()==1,"deduplicate same world/seed");
        List<MoonSettings.Moon> high=MoonSettings.parse("ffffffffffffffff deadbeef00");
        check(high.get(0).world==-1L,"unsigned 64-bit world IDs");
        check(MoonSettings.format(MoonSettings.parse(MoonSettings.format(high))).equals(MoonSettings.format(high)),"round trip");
        for(String bad:new String[]{"0","abc 0","10.10.0.1","abc:9993","ffffffffffffffff","deadbeef00 deadbeef0011","a b c","deadbeef00 a\ndeadbeef00 b","1\n2\n3\n4\n5\n6\n7\n8\n9"}) {
            try { MoonSettings.parse(bad); throw new AssertionError("accepted "+bad); }
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("PASS Moon normalization, default/explicit seeds, unsigned worlds, duplicates, clearing, invalid IDs and count limit");
    }
}
