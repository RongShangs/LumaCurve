public final class LumaFrameworkOutputLeaseTest {
    public static void main(String[] args){
        LumaFrameworkOutputLease lease=new LumaFrameworkOutputLease();
        if(lease.alive(0))throw new AssertionError("unclaimed lease");
        lease.renew(100);if(!lease.alive(3099)||lease.alive(3100))throw new AssertionError("expiry boundary");
        // Repeated broker activity must not renew the independent client clock.
        for(int i=0;i<1000;i++)lease.alive(2000);
        if(lease.alive(4000))throw new AssertionError("broker frames kept dead core alive");
        lease.renew(4100);if(!lease.alive(4100)||lease.alive(4099))throw new AssertionError("clock guard");
        boolean refused=false;try{lease.renew(100);}catch(IllegalArgumentException expected){refused=true;}
        if(!refused)throw new AssertionError("backwards clock accepted");
        System.out.println("client lease: expiry, independent frames, renewal, clock guards PASS");
    }
}
