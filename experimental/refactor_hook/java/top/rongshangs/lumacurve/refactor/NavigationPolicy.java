package top.rongshangs.lumacurve.refactor;

/** View-only scroll hysteresis and geometry. Never reads or changes brightness options. */
final class NavigationPolicy {
    boolean hidden;int direction,travel;
    void reset(){hidden=false;direction=travel=0;}
    boolean scroll(int y,int old,int hideDistance,int showDistance){
        int delta=y-old,next=Integer.signum(delta);if(next==0)return hidden;
        if(next!=direction){direction=next;travel=0;}
        travel=(int)Math.min(Integer.MAX_VALUE,(long)travel+Math.abs((long)delta));
        if(next>0&&y>hideDistance&&travel>=hideDistance)hidden=true;
        else if(next<0&&travel>=showDistance)hidden=false;
        return hidden;
    }
    static int width(int available,int side,int maximum){return Math.max(1,Math.min(maximum,available-2*Math.max(0,side)));}
    static int reserve(int height,int gap,int systemBottom){return Math.max(0,height)+Math.max(0,gap)+Math.max(0,systemBottom);}
}
