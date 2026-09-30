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
        yes(!s.scene(3000,130));yes(!s.scene(6000,245));
        yes(!s.scene(7000,330));yes(!s.scene(8500,330));
        yes(!s.scene(10000,330));yes(s.scene(11000,330));yes(!s.holding());near(s.apply(.08f),.08f);
        yes(s.observe(12000,1,.5f,.7f));near(s.settle(14500,.28f,.5f),.28f);
        yes(!s.scene(15000,2f));yes(!s.scene(16000,4f));yes(!s.scene(17100,7f));
        yes(!s.scene(18000,9f));yes(!s.scene(20000,9f));yes(!s.scene(21100,9f));yes(s.scene(22000,9f));
        yes(s.observe(23000,1,.7f,.8f));near(s.settle(25500,.30f,Float.NaN),.30f);
        yes(!s.scene(26000,20f));s.clear();yes(!s.holding()&&!s.pending());
        System.out.println("automatic slider hold: settle, stable scene, confirmed change, repeated gesture and lock reset PASS");
    }
}
