public final class LumaFrameworkOutputRampTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        LumaFrameworkOutputRamp.Coordinate identity=new LumaFrameworkOutputRamp.Coordinate(){public float encode(float v){return v;}public float decode(float v){return v;}};
        LumaFrameworkOutputRamp r=new LumaFrameworkOutputRamp(identity,.2f,0);
        float a=r.next(.8f,0,1,100);check(Math.abs(a-.204f)<.000001);
        float b=r.next(.1f,0,1,200);check(Math.abs(b-.2f)<.000001);
        float c=r.next(.8f,0,1,10000);check(Math.abs(c-.208f)<.000001);
        check(r.next(.8f,0,.15f,10100)==.15f);
        boolean rejected=false;try{r.next(Float.NaN,0,1,10200);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
        rejected=false;try{r.next(.1f,0,1,10000);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
        System.out.println("perceptual ramp: 6 cases PASS");
    }
}
