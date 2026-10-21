import com.naslink.android.ConnectionHealth;
public final class ConnectionHealthTest {
    static void check(boolean ok) { if(!ok) throw new AssertionError(); }
    public static void main(String[] args) {
        ConnectionHealth h=new ConnectionHealth(); long first=h.epoch();
        check(!h.connected()); check(h.accept(first,true,false)); check(!h.connected());
        check(h.accept(first,false,true)); check(!h.connected());
        check(h.accept(first,true,true)); check(h.connected());
        h.invalidate(); check(!h.connected()); check(!h.accept(first,true,true)); check(!h.connected());
        long current=h.epoch(); check(h.accept(current,true,true)); check(h.connected());
        check(!h.accept(first,false,false)); check(h.connected());
        check(h.accept(current,true,false)); check(!h.connected());
        h.invalidate(); check(!h.accept(current,true,true)); check(!h.connected());
        System.out.println("PASS both checks required, stale success/failure rejected, reconnection resets health");
    }
}
