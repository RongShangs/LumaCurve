public final class LumaFrameworkAuditRepairTest {
    static void yes(boolean value){if(!value)throw new AssertionError();}
    static void near(float a,float b){yes(Math.abs(a-b)<.000001f);}
    public static void main(String[] args)throws Exception {
        LumaFrameworkSliderOverride hold=new LumaFrameworkSliderOverride();
        yes(hold.observe(1000,1,0,.5f));hold.settle(3500,.6f,100);
        near(hold.apply(.1f,1),.6f);near(hold.apply(.1f,.15f),.15f);
        near(hold.apply(.1f,1),.6f); // protection exits without losing preference
        LumaFrameworkConvergence guard=new LumaFrameworkConvergence();
        yes(!guard.failed(1000,.5f,.5f,.1f));yes(!guard.failed(15999,.5f,.5f,.1f));
        yes(guard.failed(16000,.5f,.5f,.1f));
        yes(!guard.failed(17000,.4f,.5f,.1f));yes(!guard.failed(40000,.5f,.5f,.1f));
        yes(!guard.failed(50000,.5f,.5f,.5f));yes(!guard.failed(60000,.5f,.5f,.1f));
        guard.reset();yes(!guard.failed(90000,.5f,.5f,.1f));
        LumaFrameworkOutputRamp.Coordinate identity=new LumaFrameworkOutputRamp.Coordinate(){
            public float encode(float f){return f;}public float decode(float f){return f;}};
        LumaFrameworkOutputRamp ramp=new LumaFrameworkOutputRamp(identity,.2f,1000);
        ramp.speeds(2,.5f);near(ramp.next(.8f,0,1,1200),.216f);
        near(ramp.next(.1f,0,1,1400),.212f);
        try{ramp.speeds(Float.NaN,1);throw new AssertionError();}catch(IllegalArgumentException expected){}
        String old="local:4630946949513469331", fw="1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd", svc="ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20";
        yes(LumaFrameworkDeviceProfile.measuredEvidence(old,16383,fw,svc));
        yes(!LumaFrameworkDeviceProfile.measuredEvidence(old,16383,"OTA",svc));
        yes(!LumaFrameworkDeviceProfile.measuredEvidence(old,16383,fw,"OTA"));
        near(LumaFrameworkDeviceProfile.scale(old,16383),16383);
        yes(LumaFrameworkDeviceProfile.temporaryClear(new java.io.BufferedReader(new java.io.StringReader("Display Power Controller:\n  mDisplayId=0\n  mTemporaryScreenBrightness:NaN\n"))));
        yes(!LumaFrameworkDeviceProfile.temporaryClear(new java.io.BufferedReader(new java.io.StringReader("Display Power Controller:\n  mDisplayId=0\n  mTemporaryScreenBrightness:0.5\nDisplay Power Controller:\n  mDisplayId=1\n  mTemporaryScreenBrightness:NaN\n"))));
        System.out.println("Audit repairs: thermal hold, restored preference, convergence deadlines/reset, configured ramps and OTA evidence PASS");
    }
}
