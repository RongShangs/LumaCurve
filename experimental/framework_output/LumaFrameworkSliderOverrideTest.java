public final class LumaFrameworkSliderOverrideTest {
    static void yes(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] ignored){
        LumaFrameworkSliderOverride s=new LumaFrameworkSliderOverride();
        yes(!s.observe(100,1,0,0,.10f));
        yes(!s.observe(100,0,0,.5f,.10f));
        yes(s.observe(100,1,0,.5f,.10f));
        yes(s.waiting(2599));
        yes(!s.waiting(2600));
        try{s.settle(2599,.20f);throw new AssertionError();}catch(IllegalStateException expected){}
        yes(Math.abs(s.settle(2600,.20f)-2f)<.00001f);
        yes(Math.abs(s.apply(.08f)-.16f)<.00001f);
        yes(s.observe(3000,1,.5f,-.5f,.08f));
        yes(Math.abs(s.settle(5500,.02f)-.25f)<.00001f);
        yes(s.observe(6000,1,-.5f,.9f,.08f));
        yes(Math.abs(s.settle(8500,.9f)-4f)<.00001f);
        System.out.println("automatic slider handoff: 11 cases PASS");
    }
}
