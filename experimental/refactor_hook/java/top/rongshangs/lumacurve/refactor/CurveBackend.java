package top.rongshangs.lumacurve.refactor;

/** Both backends keep OEM sensor processing and output; only the baseline changes. */
public abstract class CurveBackend {
    public float[] factoryLux,factoryNit;
    public final float min,max;
    protected CurveBackend(float min,float max){this.min=min;this.max=max;}
    public abstract Object get(String name)throws Exception;
    public float number(String name)throws Exception{return ((Number)get(name)).floatValue();}
    public int integer(String name)throws Exception{return ((Number)get(name)).intValue();}
    public abstract CurvePlan plan();
    public abstract void configure(float[] factors)throws Exception;
    public abstract void clearMemory()throws Exception;
    public abstract float currentAt(float lux)throws Exception;
    public float memoryAt(float lux)throws Exception{return currentAt(lux);}
    public abstract String name();
    public float[] fullLux(){return factoryLux.clone();}
    public float[] fullNit(){return factoryNit.clone();}
    public float[] currentLux()throws Exception{return java.util.Arrays.copyOf((float[])get("mAnchorLux"),integer("mAnchorCount"));}
    public float[] currentNit()throws Exception{return java.util.Arrays.copyOf((float[])get("mAnchorNit"),integer("mAnchorCount"));}
}
