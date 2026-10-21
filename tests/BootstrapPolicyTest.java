import com.naslink.android.BootstrapPolicy;
import java.nio.file.*;

public final class BootstrapPolicyTest {
    public static void main(String[] args) throws Exception {
        byte[] verified=Files.readAllBytes(Paths.get(args[0]));
        if(!BootstrapPolicy.shouldInstall(null,verified)) throw new AssertionError("fresh install needs working bootstrap");
        if(BootstrapPolicy.shouldInstall(verified,verified)) throw new AssertionError("must preserve existing working definition");
        if(BootstrapPolicy.shouldInstall(new byte[]{1,2,3},verified)) throw new AssertionError("must not overwrite unrelated custom definition");
        try { BootstrapPolicy.shouldInstall(null,new byte[]{1,2,3}); throw new AssertionError("accepted corrupted candidate"); }
        catch(IllegalArgumentException expected) {}
        if(args.length>1 && !BootstrapPolicy.shouldInstall(Files.readAllBytes(Paths.get(args[1])),verified)) throw new AssertionError("known unreachable seed definition must migrate");
        System.out.println("PASS verified bootstrap, fresh installation, custom/working definition preservation, candidate integrity");
    }
}
