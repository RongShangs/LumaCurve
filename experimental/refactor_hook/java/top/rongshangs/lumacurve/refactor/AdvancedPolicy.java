package top.rongshangs.lumacurve.refactor;

/** Pure parameter rules; all brightness coordinates remain owned by the OEM. */
public final class AdvancedPolicy {
    public static void range(double value,double min,double max){
        if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("高级参数超出范围");
    }
    public static long retainedDelay(long desired,long existing,long horizon,long extra){
        // Pruning advances the oldest evidence timestamp. Reserve one scheduling margin.
        if(horizon<=250||extra<0||extra>=horizon)return existing;
        long ceiling=horizon-extra-250;
        if(ceiling<500)return existing;
        return Math.min(desired,ceiling);
    }
    public static float threshold(float lux,float original,boolean bright,float scale,float floor){
        if(!Float.isFinite(lux)||lux<0||!Float.isFinite(original)||original<0||
                (bright?original<lux:original>lux))return original;
        double gap=Math.max(Math.abs((double)original-lux)*scale,floor);
        // OEM evidence scans use strict '< threshold'. A zero dark threshold at
        // positive lux can never accept even a genuine 0-lux sample.
        double result=bright?lux+gap:Math.max(lux>0?Math.min(lux,.001f):0,lux-gap);
        return result<=Float.MAX_VALUE?(float)result:original;
    }
    public static double duration(double original,double scale){
        // A zero duration is the system's immediate transition, not a tuning opportunity.
        if(!Double.isFinite(original)||original<=0||original>60)return original;
        return Math.max(.05,Math.min(60,original*scale));
    }
}
