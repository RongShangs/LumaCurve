/** Bounded control polling. Relax only when the request and framework feedback agree. */
public final class LumaFrameworkOutputCadence {
    private LumaFrameworkOutputCadence(){}
    public static int nextDelay(boolean acquired,boolean hasGoal,float request,float limited,float adjusted) {
        if(!acquired)return 500; // Still observe mode and crash recovery at most 500 ms later.
        if(!hasGoal)return 100;
        if(!LumaFrameworkOutputSession.finite(request)||!LumaFrameworkOutputSession.finite(limited)||
           !LumaFrameworkOutputSession.finite(adjusted))return 100;
        return Math.abs(request-limited)<=.000001f&&Math.abs(request-adjusted)<=.000001f?500:100;
    }
}
