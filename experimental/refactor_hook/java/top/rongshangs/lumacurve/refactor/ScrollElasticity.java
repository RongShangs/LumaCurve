package top.rongshangs.lumacurve.refactor;

/** Pixel-only edge resistance; never changes the logical scroll position. */
final class ScrollElasticity {
    float pull,limit=1;
    void reset(){pull=0;}
    void setPull(float value){pull=Float.isFinite(value)?Math.max(-4*limit,Math.min(4*limit,value)):0;}
    float offset(){return Math.signum(pull)*limit*(float)(1-Math.exp(-Math.abs(pull)*.45f/limit));}
    int scroll(int delta,int position,int range,int height,float density,boolean touch){
        if(range<=0||height<=0){reset();return delta;}
        limit=Math.max(1,Math.min(height*.18f,56*Math.max(1,density)));
        int remaining=delta;
        // Reverse dragging first takes up the existing stretch, before moving the page.
        if(touch&&pull!=0&&Math.signum(delta)==Math.signum(pull)){
            int consumed=(int)Math.min(Math.abs((long)delta),Math.ceil(Math.abs(pull)));
            remaining-=Integer.signum(delta)*consumed;
            setPull(Math.signum(pull)*Math.max(0,Math.abs(pull)-consumed));
        }
        long target=(long)position+remaining;long excess=target-Math.max(0,Math.min((long)range,target));
        if(excess!=0){float cap=touch?4*limit:.85f*limit;setPull(Math.max(-cap,Math.min(cap,pull-excess)));}
        return remaining; // Native ScrollView still clamps the original edge crossing / fling.
    }
    static float returnFraction(float time){float t=Math.max(0,Math.min(1,time));return (float)((1-(1+8*t)*Math.exp(-8*t))/(1-9*Math.exp(-8)));}
}
