package top.rongshangs.lumacurve.refactor;

/** Display-layer relaxation with a fail-closed temperature gate and hysteresis. */
public final class ThermalPolicy {
    private boolean permitted;
    private float cooling=1;
    public static void validateCooling(float value){if(!Float.isFinite(value)||value<1||value>3)throw new IllegalArgumentException("温控冷却幅度应在 1～3℃");}
    public void configure(float value){validateCooling(value);if(cooling!=value)permitted=false;cooling=value;}
    public static void validate(float ceiling) {
        if(!Float.isFinite(ceiling)||ceiling<38||ceiling>45)
            throw new IllegalArgumentException("温控恢复阈值应在 38～45℃");
    }
    public boolean evaluate(boolean enabled,boolean supported,int severity,float battery,float ceiling) {
        validate(ceiling);
        if(!enabled||!supported||severity<0||severity>=3||!Float.isFinite(battery)||battery<0||battery>=ceiling)
            permitted=false;
        else if(battery<=ceiling-cooling)permitted=true;
        return permitted;
    }
}
