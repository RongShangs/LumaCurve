/** One monotonic perceptual ramp; retarget from the current request. */
public final class LumaFrameworkOutputRamp {
    public interface Coordinate { float encode(float linear)throws Exception; float decode(float perceptual)throws Exception; }
    final Coordinate coordinate;
    float request;
    long stamp;
    float brightenSpeed=1, darkenSpeed=1;
    public void speeds(float brighten,float darken) {
        if(!Float.isFinite(brighten)||!Float.isFinite(darken)||brighten<.5f||brighten>2.5f||darken<.5f||darken>2.5f)
            throw new IllegalArgumentException("invalid ramp speed");
        brightenSpeed=brighten;darkenSpeed=darken;
    }
    public LumaFrameworkOutputRamp(Coordinate c,float initial,long now){coordinate=c;reset(initial,now);}
    public void reset(float initial,long now) {
        if(!LumaFrameworkOutputSession.finite(initial)||initial<0||initial>1||now<stamp)throw new IllegalArgumentException("invalid reanchor");
        request=initial;stamp=now;
    }
    public float next(float goal,float min,float max,long now)throws Exception {
        if(now<stamp||!LumaFrameworkOutputSession.finite(goal)||min<0||max>1||min>=max)throw new IllegalArgumentException("invalid ramp input");
        float dt=Math.min(200,now-stamp)/1000f;stamp=now;
        float target=Math.max(min,Math.min(max,goal));
        if(request==target)return request;
        float from=coordinate.encode(request),to=coordinate.encode(target);
        float distance=.04f*dt*(to>from?brightenSpeed:darkenSpeed);
        // Exact arrival prevents round-trip noise from creating endless tiny submissions.
        if(Math.abs(to-from)<=distance){request=target;return request;}
        float step=from+Math.max(-distance,Math.min(distance,to-from));
        request=Math.max(min,Math.min(max,coordinate.decode(step)));
        if(!LumaFrameworkOutputSession.finite(request))throw new IllegalStateException("invalid coordinate conversion");
        return request;
    }
}
