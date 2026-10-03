package top.rongshangs.lumacurve.refactor;

/** Event driven confirmation/hysteresis/session policy. No synthetic lux or output writer. */
final class OutdoorPolicy {
    boolean active;long enterAt=-1,exitAt=-1,started=-1,cooldownUntil,manualAt=-1;float manualLux=Float.NaN;
    String reason="disabled";long next=-1;
    void manual(float lux,long now){manualAt=now;manualLux=lux;active=false;enterAt=exitAt=started=-1;reason="manual";next=-1;}
    void reset(){active=false;enterAt=exitAt=started=-1;next=-1;}
    boolean step(OutdoorOptions o,float lux,long now,String block){
        next=-1;
        if(!o.flags[0]){reset();manualAt=-1;manualLux=Float.NaN;cooldownUntil=0;reason="disabled";return false;}
        if(block!=null){if(active){cooldownUntil=Math.max(cooldownUntil,now+(long)o.values[7]);}reset();if(block.equals("screen_off")){manualAt=-1;manualLux=Float.NaN;}reason=block;return false;}
        if(!Float.isFinite(lux)||lux<0){reset();reason="lux_unknown";return false;}
        if(manualAt>=0){if(!Float.isFinite(manualLux)||Math.abs(lux-manualLux)<Math.max(5000,Math.max(lux,manualLux)*.5f)){reset();reason="manual";return false;}manualAt=-1;manualLux=Float.NaN;}
        if(now<cooldownUntil){reset();reason="cooldown";next=cooldownUntil;return false;}
        if(active&&now-started>=(long)o.values[6]){reset();cooldownUntil=now+(long)o.values[7];reason="cooldown";next=cooldownUntil;return false;}
        if(active){
            if(lux<o.values[0]*o.values[4]){if(exitAt<0)exitAt=now;if(now-exitAt>=(long)o.values[3]){reset();reason="low_lux";return false;}next=exitAt+(long)o.values[3];reason="exit_confirm";}else{exitAt=-1;reason="active";}
            long deadline=started+(long)o.values[6];next=next<0?deadline:Math.min(next,deadline);return true;
        }
        if(lux<o.values[0]){enterAt=-1;reason="low_lux";return false;}
        if(enterAt<0)enterAt=now;
        if(now-enterAt<(long)o.values[2]){reason="enter_confirm";next=enterAt+(long)o.values[2];return false;}
        active=true;started=now;enterAt=exitAt=-1;reason="active";next=started+(long)o.values[6];return true;
    }
    static float target(OutdoorOptions o,float lux,float base,float normalMax,float allowedMax){
        if(!Float.isFinite(lux)||!valid(base)||!valid(normalMax)||!valid(allowedMax)||allowedMax<normalMax)return base;
        double ramp=Math.max(0,Math.min(1,(lux-o.values[0]*o.values[4])/(o.values[1]-o.values[0]*o.values[4])));
        float floor=(float)(normalMax+(allowedMax-normalMax)*o.values[5]*ramp);
        // Raise only; bounds here are framework brightness coordinates, never nit/raw panel codes.
        return Math.max(base,Math.min(allowedMax,floor));
    }
    static boolean valid(float value){return Float.isFinite(value)&&value>=0&&value<=1;}
    static long budget(long window,long max,long minimum,double factor){
        if(window<=0||max<=0||minimum<0||minimum>max||max>window||!Double.isFinite(factor)||factor<1||factor>2)throw new IllegalArgumentException("无效的 HBM 时间预算");
        return Math.min(window,(long)Math.min(Long.MAX_VALUE,max*factor));
    }
}
