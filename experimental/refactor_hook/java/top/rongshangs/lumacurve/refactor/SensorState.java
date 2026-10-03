package top.rongshangs.lumacurve.refactor;

/** Missing sensor samples must not be presented as zero lux. No extra sensor subscription. */
public final class SensorState {
    public static String resolve(Boolean auto,Boolean sampling,Boolean valid){
        if(Boolean.FALSE.equals(auto))return "manual";
        if(!Boolean.TRUE.equals(auto))return "unknown";
        if(Boolean.FALSE.equals(sampling))return "paused";
        if(Boolean.FALSE.equals(valid))return "warming";
        if(Boolean.TRUE.equals(valid))return "active";
        return "unknown";
    }
    public static boolean usable(Boolean auto,Boolean sampling,Boolean valid){return resolve(auto,sampling,valid).equals("active");}
}
