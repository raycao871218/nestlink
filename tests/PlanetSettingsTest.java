import com.naslink.android.PlanetSettings;
import com.naslink.android.BootstrapPolicy;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public final class PlanetSettingsTest {
    private static void rejected(byte[] data) throws Exception {
        try { PlanetSettings.read(new ByteArrayInputStream(data)); }
        catch(IOException expected) { return; }
        throw new AssertionError("accepted invalid Planet");
    }
    public static void main(String[] args) throws Exception {
        byte[] bundled=Files.readAllBytes(Paths.get(args[0]));
        PlanetSettings.validate(bundled);
        for(int size=0;size<bundled.length;size++) rejected(Arrays.copyOf(bundled,size));
        rejected(new byte[PlanetSettings.MAX_SIZE+1]);
        byte[] bad=bundled.clone();bad[0]=127;rejected(bad);
        bad=bundled.clone();bad[0]=2;rejected(bad);
        bad=bundled.clone();bad[177]=5;rejected(bad);
        bad=bundled.clone();bad[183]=1;rejected(bad);
        bad=bundled.clone();bad[248]=64;rejected(bad);
        bad=bundled.clone();bad[249]=33;rejected(bad);
        rejected(Arrays.copyOf(bundled,bundled.length+1));
        String selected=BootstrapPolicy.hash(bundled);
        if(!PlanetSettings.shouldReplace(null,selected+":reset",false,bundled,bundled)) throw new AssertionError("explicit reset must replace legacy seed");
        if(!PlanetSettings.shouldReplace(selected,selected+":reset",false,bundled,bundled)) throw new AssertionError("explicit reset must replace updated seed");
        if(!PlanetSettings.shouldReplace(null,"custom",true,bundled,bundled)) throw new AssertionError("import must replace seed");
        if(PlanetSettings.shouldReplace("custom","custom",true,bundled,bundled)) throw new AssertionError("reconnect must preserve updates");
        if(!PlanetSettings.shouldReplace("custom",selected,false,bundled,bundled)) throw new AssertionError("reset must replace custom seed");
        if(!PlanetSettings.shouldReplace(selected,selected,false,null,bundled)) throw new AssertionError("missing seed must be reinstalled");
        if(PlanetSettings.shouldReplace(null,selected,false,bundled,bundled)) throw new AssertionError("legacy working seed must survive");
        System.out.println("PASS Planet format, all truncated prefixes, size limit, import/reset and update preservation");
    }
}
