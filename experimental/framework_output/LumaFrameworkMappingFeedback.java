/** Sustained coordinate mismatch after request and framework feedback have settled. */
public final class LumaFrameworkMappingFeedback {
    private long since=-1;
    public void reset(){since=-1;}
    public boolean failed(long now,float request,float limited,float adjusted,boolean mappingOk) {
        if(!Float.isFinite(request)||!Float.isFinite(limited)||!Float.isFinite(adjusted)||request<0||limited<0||
           Math.abs(request-limited)>LumaFrameworkOutputCadence.SETTLED_TOLERANCE||
           Math.abs(request-adjusted)>LumaFrameworkOutputCadence.SETTLED_TOLERANCE||mappingOk){reset();return false;}
        if(since<0||now<since){since=now;return false;}
        return now-since>=3000;
    }
}
