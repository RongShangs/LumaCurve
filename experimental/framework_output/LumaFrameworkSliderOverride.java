/** Give an automatic-mode slider gesture to the system, then retain its local bias. */
public final class LumaFrameworkSliderOverride {
    private long releaseUntil;
    private float referenceGoal;
    private float factor=1f;
    private boolean pending;

    public boolean observe(long now, int mode, float previous, float current, float goal) {
        if (mode!=1 || !Float.isFinite(previous) || !Float.isFinite(current) ||
            Math.abs(previous-current)<.0001f) return false;
        if (Float.isFinite(goal) && goal>0) referenceGoal=goal;
        pending=referenceGoal>0;
        releaseUntil=now+2500;
        return true;
    }
    public boolean waiting(long now){return pending && now<releaseUntil;}
    public boolean pending(){return pending;}
    public float settle(long now,float systemAdjusted) {
        if (!pending || now<releaseUntil || !Float.isFinite(systemAdjusted) || systemAdjusted<=0)
            throw new IllegalStateException("slider brightness not settled");
        factor=Math.max(.25f,Math.min(4f,systemAdjusted/referenceGoal));
        pending=false;
        return factor;
    }
    public float apply(float unadjusted){return unadjusted*factor;}
    public float factor(){return factor;}
}
