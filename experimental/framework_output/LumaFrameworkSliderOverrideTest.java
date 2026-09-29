public final class LumaFrameworkSliderOverrideTest {
    static void yes(boolean b){if(!b)throw new AssertionError();}
    static void near(float a,float b){yes(Math.abs(a-b)<.00001f);}
    public static void main(String[] ignored){
        LumaFrameworkSliderOverride s=new LumaFrameworkSliderOverride();
        yes(!s.observe(100,1,0,0));
        yes(!s.observe(100,0,0,.5f));
        yes(s.observe(100,1,0,.5f));
        yes(s.waiting(2599));yes(!s.waiting(2600));
        try{s.settle(2599,.20f,100);throw new AssertionError();}catch(IllegalStateException expected){}
        near(s.settle(2600,.20f,100),.20f);yes(s.holding());
        near(s.apply(.08f),.20f);
        yes(!s.scene(3000,130));yes(!s.scene(6000,145));
        yes(!s.scene(7000,230));yes(!s.scene(7500,230));
        yes(s.scene(8100,230));yes(!s.holding());near(s.apply(.08f),.08f);
        yes(s.observe(9000,1,.5f,.7f));near(s.settle(11500,.28f,.5f),.28f);
        yes(!s.scene(12000,2f));yes(!s.scene(13000,4f));yes(s.scene(14100,4f));
        yes(s.observe(15000,1,.7f,.8f));near(s.settle(17500,.30f,Float.NaN),.30f);
        yes(!s.scene(18000,20f));s.clear();yes(!s.holding()&&!s.pending());
        System.out.println("automatic slider hold: settle, stable scene, confirmed change, repeated gesture and lock reset PASS");
    }
}
