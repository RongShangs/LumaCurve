package top.rongshangs.lumacurve.refactor;

/** A slider gesture contributes once relative to its starting curve, not once per callback. */
public final class MemoryPolicy {
    private long last=-1;private float sourceLux,base;
    private long window=1500;private float luxRange=.3f;
    public static void validateGrouping(long window,float range){if(window<500||window>3000||!Float.isFinite(range)||range<.1f||range>.5f)throw new IllegalArgumentException("记忆分组参数超出范围");}
    public void configure(long window,float range){validateGrouping(window,range);this.window=window;luxRange=range;reset();}
    public static void validate(float strength){if(!Float.isFinite(strength)||strength<0||strength>1)throw new IllegalArgumentException("锚点记忆强度应在 0%～100%");}
    public void reset(){last=-1;}
    public float remember(float lux,float desired,float current,float strength,long now){
        validate(strength);
        if(!Float.isFinite(lux)||lux<0||!Float.isFinite(desired)||!Float.isFinite(current)||desired<0||current<0)
            throw new IllegalArgumentException("手动锚点输入无效");
        if(last<0||now<last||now-last>window||Math.abs(lux-sourceLux)>Math.max(5,sourceLux*luxRange)){
            sourceLux=lux;base=current;
        }
        last=now;return base+(desired-base)*strength;
    }
}
