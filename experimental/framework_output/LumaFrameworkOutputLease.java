/** Client liveness is separate from broker-generated ramp frames. */
public final class LumaFrameworkOutputLease {
    private long heartbeat=-1;
    public void renew(long now){if(now<0||now<heartbeat)throw new IllegalArgumentException("monotonic heartbeat required");heartbeat=now;}
    public boolean alive(long now){return heartbeat>=0&&now>=heartbeat&&now-heartbeat<3000;}
}
