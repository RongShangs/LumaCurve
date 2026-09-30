/** Detect a settled request that never reaches framework feedback. */
public final class LumaFrameworkConvergence {
    private long since=-1;
    public void reset(){since=-1;}
    public boolean failed(long now,float request,float goal,float adjusted){
        if(!Float.isFinite(request)||!Float.isFinite(goal)||!Float.isFinite(adjusted)||
           goal<0||Math.abs(request-goal)>LumaFrameworkOutputCadence.SETTLED_TOLERANCE||
           Math.abs(request-adjusted)<=LumaFrameworkOutputCadence.SETTLED_TOLERANCE){reset();return false;}
        if(since<0||now<since){since=now;return false;}
        return now-since>=15000;
    }
}
