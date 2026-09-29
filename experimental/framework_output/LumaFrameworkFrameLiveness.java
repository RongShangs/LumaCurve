/** An acquired broker has no frame lease until its first successful submission. */
public final class LumaFrameworkFrameLiveness {
    private boolean submitted;
    public void acquire(){submitted=false;}
    public void submit(){submitted=true;}
    public void release(){submitted=false;}
    public boolean expired(LumaFrameworkOutputSession.State state){
        return submitted && state==LumaFrameworkOutputSession.State.IDLE;
    }
}
