package top.rongshangs.lumacurve.refactor;

/** Extra evidence time only. Never substitutes lux or brightness values. */
public final class LowLightPolicy {
    public static final float LIMIT_LUX=50;
    public static final long BRIGHTEN_MS=3000,DARKEN_MS=4000;
    public static void validate(float limit,long bright,long dark){
        if(!Float.isFinite(limit)||limit<5||limit>100||bright<1000||bright>4000||dark<1000||dark>4000)
            throw new IllegalArgumentException("暗光参数超出可调范围");
    }
    public static boolean applies(boolean enabled,boolean automatic,boolean screenOn,
            boolean idle,boolean driving,boolean hdr,float confirmedLux,float candidateLux){
        return applies(enabled,automatic,screenOn,idle,driving,hdr,confirmedLux,candidateLux,LIMIT_LUX);
    }
    public static boolean applies(boolean enabled,boolean automatic,boolean screenOn,
            boolean idle,boolean driving,boolean hdr,float confirmedLux,float candidateLux,float limit){
        return enabled&&automatic&&screenOn&&!idle&&!driving&&!hdr&&
            Float.isFinite(confirmedLux)&&confirmedLux>=0&&(confirmedLux<=limit||
                (Float.isFinite(candidateLux)&&candidateLux>=0&&candidateLux<=limit));
    }
    public static long chosen(long existing,boolean brighten,boolean assist){
        return chosen(existing,brighten,assist,BRIGHTEN_MS,DARKEN_MS);
    }
    public static long chosen(long existing,boolean brighten,boolean assist,long bright,long dark){
        if(existing<0||existing>60000)throw new IllegalArgumentException("invalid OEM delay");
        // The assist buffer retains only 5 s. Never create a deadline beyond its evidence window.
        long wanted=Math.max(existing,brighten?bright:dark);
        return assist?Math.max(existing,Math.min(4000,wanted)):wanted;
    }
    public static long withinWindow(long wanted,long existing,long horizon,long additional){
        // Do not increase a wait beyond the retained evidence, including OEM step-mode time.
        if(horizon<=250||additional<0||additional>=horizon)return existing;
        return wanted<=horizon-additional-250?wanted:existing;
    }
}
