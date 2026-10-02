package top.rongshangs.lumacurve.refactor;

/** Extra evidence time only. Never substitutes lux or brightness values. */
public final class LowLightPolicy {
    public static final float LIMIT_LUX=50;
    public static final long BRIGHTEN_MS=3000,DARKEN_MS=4000;
    public static boolean applies(boolean enabled,boolean automatic,boolean screenOn,
            boolean idle,boolean driving,boolean hdr,float confirmedLux,float candidateLux){
        return enabled&&automatic&&screenOn&&!idle&&!driving&&!hdr&&
            Float.isFinite(confirmedLux)&&confirmedLux>=0&&(confirmedLux<=LIMIT_LUX||
                (Float.isFinite(candidateLux)&&candidateLux>=0&&candidateLux<=LIMIT_LUX));
    }
    public static long chosen(long existing,boolean brighten,boolean assist){
        if(existing<0||existing>60000)throw new IllegalArgumentException("invalid OEM delay");
        // The assist buffer retains only 5 s. Never create a deadline beyond its evidence window.
        long wanted=Math.max(existing,brighten?BRIGHTEN_MS:DARKEN_MS);
        return assist?Math.max(existing,Math.min(4000,wanted)):wanted;
    }
    public static long withinWindow(long wanted,long existing,long horizon,long additional){
        // Do not increase a wait beyond the retained evidence, including OEM step-mode time.
        if(horizon<=250||additional<0||additional>=horizon)return existing;
        return wanted<=horizon-additional-250?wanted:existing;
    }
}
