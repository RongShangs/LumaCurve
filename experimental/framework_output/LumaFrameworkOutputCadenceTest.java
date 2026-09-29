public final class LumaFrameworkOutputCadenceTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(LumaFrameworkOutputCadence.nextDelay(false,false,0,0,0)==500);
        check(LumaFrameworkOutputCadence.nextDelay(true,false,.1f,.1f,.1f)==100);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,.1f,.1f,.1f)==500);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,.1f,.2f,.1f)==100);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,.1f,.1f,.2f)==100);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,Float.NaN,.1f,.1f)==100);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,.1f,Float.POSITIVE_INFINITY,.1f)==100);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,.1f,.1f,Float.NaN)==100);
        check(LumaFrameworkOutputCadence.nextDelay(true,true,.1f,.10000001f,.10000001f)==500);
        System.out.println("framework cadence: 9 cases PASS; device pending");
    }
}
