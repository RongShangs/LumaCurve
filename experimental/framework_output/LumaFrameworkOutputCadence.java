/** Bounded control polling. Relax only when the request and framework feedback agree. */
public final class LumaFrameworkOutputCadence {
    private LumaFrameworkOutputCadence(){}
    // About two codes on the pinned 17848-code panel. Framework float
    // readback need not be bit-identical to the last request at rest.
    static final float SETTLED_TOLERANCE=.00012f;
    public static int nextDelay(boolean acquired,boolean hasGoal,float request,float limited,float adjusted) {
        if(!acquired)return 500; // Still observe mode and crash recovery at most 500 ms later.
        if(!hasGoal)return 100;
        if(!LumaFrameworkOutputSession.finite(request)||!LumaFrameworkOutputSession.finite(limited)||
           !LumaFrameworkOutputSession.finite(adjusted))return 100;
        return Math.abs(request-limited)<=SETTLED_TOLERANCE&&Math.abs(request-adjusted)<=SETTLED_TOLERANCE?500:100;
    }
}
