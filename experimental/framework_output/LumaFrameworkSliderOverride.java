/** Keep an automatic-mode slider choice until a confirmed scene change or screen lock. */
public final class LumaFrameworkSliderOverride {
    private long releaseUntil, sceneSince;
    private float heldBrightness, anchorLux=Float.NaN;
    private boolean pending, holding;
    private int sceneSamples;

    public boolean observe(long now, int mode, float previous, float current) {
        if (mode!=1 || !Float.isFinite(previous) || !Float.isFinite(current) ||
            Math.abs(previous-current)<.0001f) return false;
        pending=true;holding=false;releaseUntil=now+2500;
        sceneSamples=0;sceneSince=0;
        return true;
    }
    public boolean waiting(long now){return pending && now<releaseUntil;}
    public boolean pending(){return pending;}
    public boolean holding(){return holding;}
    public float heldBrightness(){return heldBrightness;}
    public float settle(long now,float systemAdjusted,float lux) {
        if (!pending || now<releaseUntil || !Float.isFinite(systemAdjusted) || systemAdjusted<=0 || systemAdjusted>1)
            throw new IllegalStateException("slider brightness not settled");
        heldBrightness=systemAdjusted;anchorLux=validLux(lux)?lux:Float.NaN;
        pending=false;holding=true;sceneSamples=0;sceneSince=0;
        return heldBrightness;
    }
    private static boolean validLux(float lux){return Float.isFinite(lux)&&lux>=0;}
    public boolean scene(long now,float lux) {
        if (!holding || !validLux(lux)) return false;
        if (!validLux(anchorLux)) {anchorLux=lux;return false;}
        // A short local fluctuation should not cancel an explicit slider choice.
        boolean large=Math.abs(Math.log1p(lux)-Math.log1p(anchorLux))>=Math.log(3) && Math.abs(lux-anchorLux)>=8f;
        if (!large) {sceneSamples=0;sceneSince=0;return false;}
        if (sceneSamples==0) {sceneSamples=1;sceneSince=now;return false;}
        if (now<sceneSince || now-sceneSince<3000) return false;
        if (++sceneSamples<3) return false;
        clear();return true;
    }
    public float apply(float unadjusted){return holding?heldBrightness:unadjusted;}
    public float apply(float unadjusted,float safetyMaximum){
        if(!Float.isFinite(safetyMaximum)||safetyMaximum<0||safetyMaximum>1)
            throw new IllegalArgumentException("invalid safety maximum");
        return Math.min(safetyMaximum,apply(unadjusted));
    }
    public void clear(){pending=false;holding=false;sceneSamples=0;sceneSince=0;anchorLux=Float.NaN;}
}
