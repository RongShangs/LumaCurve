/** One monotonic perceptual ramp; retarget from the current request. */
public final class LumaFrameworkOutputRamp {
    public interface Coordinate { float encode(float linear)throws Exception; float decode(float perceptual)throws Exception; }
    final Coordinate coordinate;
    float request;
    long stamp;
    public LumaFrameworkOutputRamp(Coordinate c,float initial,long now){coordinate=c;reset(initial,now);}
    public void reset(float initial,long now) {
        if(!LumaFrameworkOutputSession.finite(initial)||initial<0||initial>1||now<stamp)throw new IllegalArgumentException("invalid reanchor");
        request=initial;stamp=now;
    }
    public float next(float goal,float min,float max,long now)throws Exception {
        if(now<stamp||!LumaFrameworkOutputSession.finite(goal)||min<0||max>1||min>=max)throw new IllegalArgumentException("invalid ramp input");
        float dt=Math.min(200,now-stamp)/1000f;stamp=now;
        float from=coordinate.encode(request),to=coordinate.encode(Math.max(min,Math.min(max,goal)));
        float distance=.04f*dt;
        float step=from+Math.max(-distance,Math.min(distance,to-from));
        request=Math.max(min,Math.min(max,coordinate.decode(step)));
        if(!LumaFrameworkOutputSession.finite(request))throw new IllegalStateException("invalid coordinate conversion");
        return request;
    }
}
