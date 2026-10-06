package top.rongshangs.lumacurve.refactor;
import java.util.*;

public final class RefactorTest {
    static int cases;
    static void check(boolean b){if(!b)throw new AssertionError();cases++;}
    static void bad(Runnable task){try{task.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException expected){cases++;}}
    public static class OEM {
        int mDisplayId=0,mAnchorCount=4,mGoodAnchorCount=4,mOrderCounter;
        float mLuxSeg0=30,mLuxSeg1=600,mLuxMax=5000,mDefNit0=2,mDefNit30=135,mDefNitMidHigh=220,mUserLux=-1,mUserLogicalNit=-1;
        boolean mIsHaveGoodCurve,fail;
        float[] mAnchorLux=new float[7],mAnchorNit=new float[7],mGoodAnchorLux=new float[7],mGoodAnchorNit=new float[7];
        int[] mAnchorOrder=new int[7],mGoodAnchorOrder=new int[7];
        boolean[] mAnchorIsUserDrag=new boolean[7],mGoodAnchorIsUserDrag=new boolean[7];
        void resetDefaultSpline(){
            for(float[] a:new float[][]{mAnchorLux,mAnchorNit,mGoodAnchorLux,mGoodAnchorNit})Arrays.fill(a,0);
            check(SensorState.resolve(false,false,false).equals("manual"));
        check(!SensorState.usable(false,true,true)); // Old cached lux cannot become live in manual mode.
        check(SensorState.resolve(true,false,true).equals("paused"));
        check(SensorState.resolve(true,true,false).equals("warming"));
        check(!SensorState.usable(true,true,false)); // Zero initialization is not zero ambient light.
        check(SensorState.resolve(null,true,true).equals("unknown"));
        check(SensorState.resolve(true,null,null).equals("unknown"));
        check(SensorState.usable(true,true,true));
        check(SensorState.usable(true,null,true)); // Some firmware lacks an optional sampling flag.
        check(UiText.translate("有未保存的设置",true).equals("Unsaved settings"));
        float[] x={0,30,600,5000},y={mDefNit0,mDefNit30,mDefNitMidHigh,1060};
            System.arraycopy(x,0,mAnchorLux,0,4);System.arraycopy(x,0,mGoodAnchorLux,0,4);
            System.arraycopy(y,0,mAnchorNit,0,4);System.arraycopy(y,0,mGoodAnchorNit,0,4);
            mAnchorCount=mGoodAnchorCount=4;mUserLux=mUserLogicalNit=-1;
            Arrays.fill(mAnchorIsUserDrag,false);Arrays.fill(mAnchorOrder,0);mOrderCounter=0;mIsHaveGoodCurve=false;
            if(fail)throw new IllegalStateException("OEM reset failed");
        }
        float defaultAtLux(float lux){
            if(lux<=0)return mDefNit0;
            if(lux<=30)return mDefNit0+(mDefNit30-mDefNit0)*lux/30;
            if(lux<=600)return mDefNit30+(mDefNitMidHigh-mDefNit30)*(lux-30)/570;
            if(lux<=5000)return mDefNitMidHigh+(1060-mDefNitMidHigh)*(lux-600)/4400;
            return 1060;
        }
    }
    public static void main(String[] args)throws Exception {
        float[] x={0,30,600,5000},y={2,135,220,1060},one={1,1,1,1};
        CurvePlan p=new CurvePlan(x,y,.2f,1060,one);
        check(p.at(0)==2);check(p.at(30)==135);check(p.at(600)==220);check(p.at(50000)==1060);
        check(Math.abs(p.at(70)-(135+85*40/570f))<.001);
        x[1]=999; y[1]=999;check(p.at(30)==135);float[] copy=p.nits();copy[0]=999;check(p.at(0)==2);
        bad(()->CurvePlan.factors("NaN,1,1,1"));bad(()->CurvePlan.factors("1,1,1"));bad(()->CurvePlan.factors("Infinity,1,1,1"));bad(()->CurvePlan.factors("0,1,1,1"));bad(()->CurvePlan.factors("3,1,1,1"));
        bad(()->p.at(Float.NaN));bad(()->p.at(-1));
        bad(()->new CurvePlan(new float[]{0,30,30,5000},new float[]{2,135,220,1060},.2f,1060,one));
        bad(()->new CurvePlan(new float[]{0,30,600,5000},new float[]{2,135,220,1060},.2f,1060,new float[]{1,2,.1f,1}));
        CurvePlan clipped=new CurvePlan(new float[]{0,30,600,5000},new float[]{2,135,220,1060},2,1060,new float[]{.1f,1,1,2});check(clipped.at(0)==2 && clipped.at(5000)==1060);
        float[] zeroLux={0,30,600,5000},zeroNit={0,20,100,1000};
        CurvePlan raised=new CurvePlan(zeroLux,zeroNit,0,1000,one,10);check(raised.at(0)==10);check(raised.at(15)==15);check(raised.at(30)==20);check(raised.at(5000)==1000);
        check(new CurvePlan(zeroLux,zeroNit,0,1000,one).at(0)==0);check(zeroNit[0]==0);
        bad(()->new CurvePlan(zeroLux,zeroNit,0,1000,one,21));bad(()->new CurvePlan(zeroLux,zeroNit,0,1000,one,Float.NaN));bad(()->new CurvePlan(zeroLux,zeroNit,0,1000,one,-1));
        check(CurvePlan.floor(null)==0);check(CurvePlan.floor(10)==10);bad(()->CurvePlan.floor("10"));bad(()->CurvePlan.floor(Float.POSITIVE_INFINITY));
        check(Arrays.equals(CurveEditor.floorBounds(zeroNit,0,1000,one),new float[]{0,20}));check(CurveEditor.preset(one,zeroNit,0,1000)[0]==1);
        float[] zeroDraft=one.clone();zeroDraft[1]=CurveEditor.clamp(1,.1f,zeroNit,0,1000,one,10);check(zeroDraft[1]>=.5f);check(new CurvePlan(zeroLux,zeroNit,0,1000,zeroDraft,10).at(30)>=10);
        OEM o=new OEM();o.resetDefaultSpline();RefactorAdapter a=new RefactorAdapter(o,.2f,1060);
        a.configure(new float[]{.7f,.8f,.95f,1});check(Math.abs(o.mAnchorNit[1]-108)<.001);check(o.mGoodAnchorNit[1]==o.mAnchorNit[1]);check(o.mDefNit30==108);
        check(Math.abs(o.defaultAtLux(70)-a.plan().at(70))<.001);
        check(o.defaultAtLux(70)<135+85*40/570f);
        check(o.defaultAtLux(5000)==1060);
        try{a.configure(new float[]{1,1,1,.5f});throw new AssertionError();}catch(IllegalArgumentException expected){check(o.mDefNit30==108);}
        // Manual anchors survive normal reads; module rewrites only explicit profile/reset events.
        o.mAnchorNit[1]=180;o.mAnchorIsUserDrag[1]=true;check(a.plan().at(30)==108 && o.mAnchorNit[1]==180 && o.mAnchorIsUserDrag[1]);
        o.resetDefaultSpline();check(o.mAnchorNit[1]==108 && o.mGoodAnchorNit[1]==108);
        a.configure(null);check(a.plan()==null && o.mAnchorNit[1]==135 && o.mGoodAnchorNit[1]==135 && o.mDefNit30==135);
        a.configure(new float[]{1,1,1,1});check(Arrays.equals(Arrays.copyOf(o.mAnchorNit,4),new float[]{2,135,220,1060}));
        o.mAnchorNit[1]=177;o.mUserLux=30;o.mUserLogicalNit=177;o.mAnchorIsUserDrag[1]=true;o.fail=true;
        try{a.configure(new float[]{.7f,.8f,.95f,1});throw new AssertionError();}catch(IllegalStateException expected){check(o.mAnchorNit[1]==177 && o.mUserLux==30 && o.mAnchorIsUserDrag[1] && o.mDefNit30==135);check(a.plan().at(30)==135);}
        o.fail=false;OEM rear=new OEM();rear.mDisplayId=1;
        a.configure(one,50);check(o.mDefNit0==50&&o.mAnchorNit[0]==50);a.clearMemory();check(o.mDefNit0==50&&a.plan().at(0)==50);o.fail=true;try{a.configure(one,100);throw new AssertionError();}catch(IllegalStateException expected){check(o.mDefNit0==50&&a.plan().at(0)==50);}o.fail=false;a.configure(null);check(o.mDefNit0==2&&a.plan()==null);a.configure(one);o.mAnchorNit[1]=177;
        try{new RefactorAdapter(rear,.2f,1060);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
        ThermalPolicy gate=new ThermalPolicy();
        check(!gate.evaluate(false,true,0,35,43));check(!gate.evaluate(true,false,0,35,43));
        check(!gate.evaluate(true,true,-1,35,43));check(!gate.evaluate(true,true,0,Float.NaN,43));
        check(gate.evaluate(true,true,0,40,43));check(gate.evaluate(true,true,2,42.8f,43));
        check(!gate.evaluate(true,true,3,40,43));check(!gate.evaluate(true,true,0,42.8f,43));
        check(gate.evaluate(true,true,0,42,43));check(!gate.evaluate(true,true,0,43,43));
        check(!gate.evaluate(true,true,0,42.5f,43));check(gate.evaluate(true,true,0,41.9f,43));
        bad(()->ThermalPolicy.validate(50.1f));bad(()->ThermalPolicy.validate(37));bad(()->ThermalPolicy.validate(Float.NaN));
        ThermalPolicy.validate(50);check(gate.evaluate(true,true,0,48,50));check(gate.evaluate(true,true,2,49.9f,50));
        check(!gate.evaluate(true,true,0,50,50));check(!gate.evaluate(true,true,0,49.5f,50));check(gate.evaluate(true,true,0,49,50));check(!gate.evaluate(true,true,3,48,50));
        MemoryPolicy memory=new MemoryPolicy();
        check(memory.remember(30,200,100,.25f,1000)==125);
        // Many callbacks in one drag must not compound into an unintended full-strength update.
        for(int i=1;i<=20;i++)check(memory.remember(30,200,125,.25f,1000+i*40)==125);
        check(memory.remember(30,200,125,.25f,4000)==143.75f);
        check(memory.remember(300,400,200,.25f,4100)==250);
        check(memory.remember(300,400,250,1,4200)==400);
        check(memory.remember(300,400,250,0,4300)==200);
        memory.reset();check(memory.remember(300,400,250,.25f,4400)==287.5f);
        bad(()->MemoryPolicy.validate(1.1f));bad(()->MemoryPolicy.validate(-.1f));bad(()->MemoryPolicy.validate(Float.NaN));
        bad(()->memory.remember(Float.NaN,200,100,.25f,4500));
        check(a.currentAt(30)==177);
        // Point editing must clamp visibly at a reachable system/neighbor boundary,
        // and every accepted drag must still pass the backend's real CurvePlan checks.
        float[] base={2,135,220,1060},draft={.7f,.8f,.95f,1};
        float[] range=CurveEditor.bounds(1,base,.2f,1060,draft);
        check(Math.abs(range[1]-209f/135)<.00001f);
        check(CurveEditor.clamp(1,9,base,.2f,1060,draft)==range[1]);
        check(CurveEditor.clamp(1,-9,base,.2f,1060,draft)==range[0]);
        float[] saturation={10,700,900,1060},full={1,1,1,1};
        check(CurveEditor.bounds(2,saturation,.2f,1060,full)[1]<2);
        float effective=CurveEditor.clamp(2,2,saturation,.2f,1060,full);
        check(saturation[2]*effective<=1060 && saturation[2]*effective>1059.9f);
        bad(()->CurveEditor.bounds(3,base,.2f,1060,draft));
        bad(()->CurveEditor.clamp(1,Float.NaN,base,.2f,1060,draft));
        Random random=new Random(928);
        for(int i=0;i<400;i++){
            int point=random.nextInt(3);float wanted=random.nextFloat()*6-2;
            draft[point]=CurveEditor.clamp(point,wanted,base,.2f,1060,draft);
            float[] actual=new CurvePlan(new float[]{0,30,600,5000},base,.2f,1060,draft).nits();
            check(actual[0]<=actual[1]&&actual[1]<=actual[2]&&actual[2]<=actual[3]);
        }
        check(UiText.translate("当前亮度曲线",true).equals("Current brightness curve"));
        check(UiText.translate("当前亮度曲线",false).equals("当前亮度曲线"));
        check(UiText.translate("记忆强度 25% · 缓慢记忆",true).equals("Memory strength 25% · Gentler memory"));
        check(UiText.translate("电池温度阈值：43℃（建议）",true).equals("Battery threshold: 43℃ (recommended)"));
        check(UiText.translate("手动记忆节点：2 个",true).equals("Remembered points: 2 points"));
        check(UiText.translate("/sdcard/LumaCurve-20261001.zip",true).equals("/sdcard/LumaCurve-20261001.zip"));
        float[] dimPreset=CurveEditor.preset(new float[]{.1f,.1f,.1f,1},base,.2f,1060);
        check(Arrays.equals(dimPreset,new float[]{.1f,.1f,.1f,1}));
        float[] constrained=CurveEditor.preset(new float[]{1,2,.1f,1},base,.2f,1060);
        check(new CurvePlan(new float[]{0,30,600,5000},base,.2f,1060,constrained).nits()[1]<=base[2]*constrained[2]);
        // A configurable gesture window groups callbacks without compounding memory.
        memory.configure(3000,.5f);check(memory.remember(100,200,100,.25f,1000)==125);
        check(memory.remember(149,200,125,.25f,3900)==125);
        check(memory.remember(151,200,125,.25f,3950)==143.75f);
        memory.configure(500,.1f);check(memory.remember(100,200,100,.25f,5000)==125);
        check(memory.remember(100,200,125,.25f,5501)==143.75f);
        memory.configure(3000,.5f);check(memory.remember(0,200,100,.25f,6000)==125);
        check(memory.remember(5,200,125,.25f,6010)==125);
        check(memory.remember(5.01f,200,125,.25f,6020)==143.75f);
        bad(()->MemoryPolicy.validateGrouping(499,.3f));bad(()->MemoryPolicy.validateGrouping(3001,.3f));bad(()->MemoryPolicy.validateGrouping(1000,Float.NaN));bad(()->MemoryPolicy.validateGrouping(1000,.51f));
        gate.configure(3);check(!gate.evaluate(true,true,0,41,43));check(gate.evaluate(true,true,0,40,43));check(gate.evaluate(true,true,0,42,43));check(!gate.evaluate(true,true,0,43,43));check(!gate.evaluate(true,true,0,40.1f,43));
        gate.configure(1);check(gate.evaluate(true,true,0,41.9f,43));
        bad(()->ThermalPolicy.validateCooling(.9f));bad(()->ThermalPolicy.validateCooling(3.1f));bad(()->ThermalPolicy.validateCooling(Float.NaN));
        DelayPolicy.validate(500,1000);DelayPolicy.validate(10000,15000);
        check(DelayPolicy.deadline(6500,4000,1500,1500)==6500);
        check(DelayPolicy.deadline(6500,4000,1500,3000)==8000);
        check(DelayPolicy.deadline(6500,6000,1500,500)==6000);
        // Extra OEM step delay is retained: original=5000 evidence + 1000 step + 5000 base.
        check(DelayPolicy.deadline(11000,7000,5000,15000)==21000);
        bad(()->DelayPolicy.validate(499,5000));bad(()->DelayPolicy.validate(10001,5000));bad(()->DelayPolicy.validate(1500,999));bad(()->DelayPolicy.validate(1500,15001));bad(()->DelayPolicy.deadline(100,-1,1500,500));
        try{DelayPolicy.deadline(Long.MAX_VALUE,0,500,15000);throw new AssertionError();}catch(ArithmeticException expected){cases++;}
        check(ReleasePolicy.compare("v2.0.0","2.0.0")==0);check(ReleasePolicy.compare("2.0.1","2.0.0")>0);check(ReleasePolicy.compare("2.10.0","2.9.0")>0);check(ReleasePolicy.compare("1.9.9","2.0.0")<0);
        bad(()->ReleasePolicy.compare("3.0.bad","2.0.0"));bad(()->ReleasePolicy.compare("2.0.0-beta","2.0.0"));bad(()->ReleasePolicy.compare("2.0.0","-1.0.0"));
        String asset="https://github.com/RongShangs/LumaCurve/releases/download/v2.0.1/LumaCurve-2.0.1.apk";
        check(ReleasePolicy.trustedApk("v2.0.1","LumaCurve-2.0.1.apk",asset));
        String renamed=asset.replace("LumaCurve-2.0.1.apk","HyperLux-2.0.1.apk");
        check(ReleasePolicy.trustedApk("v2.0.1","HyperLux-2.0.1.apk",renamed));
        check(ReleasePolicy.trustedApk("2.0.1","HyperLux-2.0.1.apk",renamed.replace("/v2.0.1/","/2.0.1/")));
        check(!ReleasePolicy.trustedApk("2.0.1","HyperLux-2.0.1.apk",asset));
        check(!ReleasePolicy.trustedApk("2.0.2","HyperLux-2.0.1.apk",renamed));
        check(!ReleasePolicy.trustedApk("2.0.1","HyperLux-2.0.1.zip",renamed));
        check(!ReleasePolicy.trustedApk("2.0.1","HyperLux-2.0.1.apk",renamed.replace("RongShangs","Other")));
        check(!ReleasePolicy.trustedApk("2.0.1","LumaCurve-2.0.1.zip",asset));check(!ReleasePolicy.trustedApk("2.0.2","LumaCurve-2.0.1.apk",asset));check(!ReleasePolicy.trustedApk("2.0.1","LumaCurve-2.0.1.apk",asset.replace("https:","http:")));
        check(!ReleasePolicy.trustedApk("2.0.1","LumaCurve-2.0.1.apk",asset.replace("github.com","github.com.evil.org")));check(!ReleasePolicy.trustedApk("2.0.1","LumaCurve-2.0.1.apk",asset.replace("RongShangs","Other")));check(!ReleasePolicy.trustedApk("2.0.1","LumaCurve-2.0.1.apk",asset+"?redirect=1"));check(!ReleasePolicy.trustedApk("2.0.1","LumaCurve-2.0.1.apk",asset.replace("github.com","user@github.com")));
        DelayPolicy.validateSmall(500);DelayPolicy.validateSmall(15000);bad(()->DelayPolicy.validateSmall(499));bad(()->DelayPolicy.validateSmall(15001));
        check(DelayPolicy.deadline(12000,6000,7000,3000)==8000);check(DelayPolicy.deadline(12000,11000,7000,500)==11000);
        java.nio.file.Path fixtures=java.nio.file.Paths.get("build/refactor-hook").toAbsolutePath().normalize();java.nio.file.Files.createDirectories(fixtures);
        java.nio.file.Path temp=java.nio.file.Files.createTempDirectory(fixtures,"module-inventory-");
        try{
            java.nio.file.Path installed=temp.resolve("installed"),pending=temp.resolve("pending");java.nio.file.Files.createDirectories(installed);java.nio.file.Files.createDirectories(pending);
            check(LegacyModules.scan(installed.toFile(),pending.toFile()).isEmpty());
            java.nio.file.Path module=installed.resolve("luma_curve");java.nio.file.Files.createDirectory(module);byte[] prop="id=luma_curve\nname=流光亮度Ω\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);java.nio.file.Files.write(module.resolve("module.prop"),prop);
            List<String[]> list=LegacyModules.scan(installed.toFile(),pending.toFile());check(list.size()==1&&list.get(0)[0].equals("luma_curve")&&list.get(0)[1].equals("流光亮度Ω")&&list.get(0)[2].equals("installed"));
            java.nio.file.Files.createFile(module.resolve("disable"));check(LegacyModules.scan(installed.toFile(),pending.toFile()).get(0)[2].equals("disabled"));
            java.nio.file.Path update=pending.resolve("luma_curve");java.nio.file.Files.createDirectory(update);list=LegacyModules.scan(installed.toFile(),pending.toFile());check(list.size()==1&&list.get(0)[2].equals("pending"));
            java.nio.file.Files.createDirectory(installed.resolve("unrelated_brightness"));check(LegacyModules.scan(installed.toFile(),pending.toFile()).size()==1);
            java.nio.file.Files.createFile(module.resolve("remove"));java.nio.file.Files.createFile(update.resolve("remove"));check(LegacyModules.scan(installed.toFile(),pending.toFile()).isEmpty());
            java.nio.file.Files.createDirectory(installed.resolve("ios_auto_brightness"));java.nio.file.Path malformed=pending.resolve("luma_curve_official_test");java.nio.file.Files.createDirectory(malformed);java.nio.file.Files.write(malformed.resolve("module.prop"),"name=\\uZZZZ\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            list=LegacyModules.scan(installed.toFile(),pending.toFile());check(list.size()==2&&list.get(0)[0].equals("luma_curve_official_test")&&list.get(1)[0].equals("ios_auto_brightness"));
            check(Arrays.equals(java.nio.file.Files.readAllBytes(module.resolve("module.prop")),prop));check(java.nio.file.Files.isRegularFile(module.resolve("disable"))&&java.nio.file.Files.isDirectory(installed.resolve("unrelated_brightness")));
        }finally{
            // Only this generated fixture below the build directory may be removed.
            if(!temp.toAbsolutePath().normalize().startsWith(fixtures))throw new AssertionError("invalid fixture path");
            try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(temp)){for(java.nio.file.Path file:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator){if(!file.toAbsolutePath().normalize().startsWith(temp))throw new AssertionError("fixture escape");java.nio.file.Files.delete(file);}}
        }
        // Confirming a real change must finish from the original evidence timestamp,
        // rather than moving the deadline forward at every sample.
        check(LowLightPolicy.applies(true,true,true,false,false,false,1.14f,100));
        check(LowLightPolicy.applies(true,true,true,false,false,false,100,1.14f));
        check(!LowLightPolicy.applies(true,true,true,false,false,false,100,101));
        check(LowLightPolicy.applies(true,true,true,false,false,false,50,Float.NaN));
        check(!LowLightPolicy.applies(true,true,true,false,false,false,Float.NaN,1));
        check(!LowLightPolicy.applies(true,true,true,false,false,false,-1,1));
        check(!LowLightPolicy.applies(true,true,true,false,false,false,100,-1));
        for(int mode=0;mode<6;mode++)check(!LowLightPolicy.applies(mode!=0,mode!=1,mode!=2,mode==3,mode==4,mode==5,1,2));
        check(LowLightPolicy.chosen(1000,true,false)==3000);
        check(LowLightPolicy.chosen(1000,false,false)==4000);
        check(LowLightPolicy.chosen(8000,false,false)==8000);
        check(LowLightPolicy.chosen(1000,false,true)==4000);
        check(LowLightPolicy.chosen(5000,true,true)==5000); // Never shorten the OEM small-change delay.
        check(LowLightPolicy.withinWindow(4000,1000,10000,0)==4000);
        check(LowLightPolicy.withinWindow(4000,1000,5000,2000)==2750); // Use the remaining evidence window instead of discarding the whole extension.
        check(LowLightPolicy.withinWindow(4000,1000,5000,5000)==1000);
        check(LowLightPolicy.withinWindow(8000,8000,5000,0)==8000); // Preserve an already-existing policy, don't lengthen it.
        check(LowLightPolicy.withinWindow(4000,1000,0,0)==1000);
        bad(()->LowLightPolicy.chosen(-1,false,true));bad(()->LowLightPolicy.chosen(60001,false,true));
        long onset=20000,baseDelay=1000,original=onset+baseDelay;
        for(long now=20000;now<=24000;now+=250){
            long corrected=DelayPolicy.deadline(original,now,baseDelay,LowLightPolicy.chosen(baseDelay,false,true));
            check(corrected==24000); // Reaches now exactly after 4 s, before 5 s assist pruning.
        }
        check(DelayPolicy.deadline(23000,23000,1000,4000)==26000); // New evidence starts later after an interruption.
        check(DelayPolicy.deadline(22000,20000,1000,4000)==25000); // Preserves an OEM additional 1 s delay.
        check(DelayPolicy.deadline(21000,25000,1000,3000)==25000); // Already-established change can proceed.
        PipelineHistory history=new PipelineHistory();
        PipelineHistory.Frame first=history.begin(1,1000);first.route="refactor";first.curve=.1f;first.sceneIn=.1f;first.sceneOut=.2f;history.finish(first);
        PipelineHistory.Frame next=history.begin(2,2000);next.route="mapping";history.finish(next);
        check(next.sequence==2&&history.last()==next);check(Float.isNaN(next.sceneOut)&&next.shortTermMemory==null&&next.nightWake==null);
        check(first.sceneOut==.2f&&PipelineHistory.Frame.changed(first.sceneIn,first.sceneOut));
        check(!PipelineHistory.Frame.changed(Float.NaN,0)&&!PipelineHistory.Frame.changed(.1f,.1f));
        for(int i=0;i<100;i++)history.finish(history.begin(i+3,3000+i));
        check(history.snapshot().size()==24&&history.last().sequence==102&&history.snapshot().get(0).sequence==79);
        history.snapshot().clear();check(history.snapshot().size()==24);history.finish(null);check(history.last().sequence==102);
        check(UiText.translate("暗光稳定",true).equals("Low-light stability"));
        check(UiText.translate("当前路径未取得可绘制曲线",true).equals("No drawable curve observed for this path"));
        // Tunable low-light limits cover both confirmed and candidate light without altering lux.
        LowLightPolicy.validate(5,1000,1000);LowLightPolicy.validate(100,4000,4000);
        bad(()->LowLightPolicy.validate(Float.NaN,3000,4000));bad(()->LowLightPolicy.validate(4,3000,4000));
        bad(()->LowLightPolicy.validate(101,3000,4000));bad(()->LowLightPolicy.validate(50,999,4000));bad(()->LowLightPolicy.validate(50,3000,4001));
        check(!LowLightPolicy.applies(true,true,true,false,false,false,51,51,50));
        check(LowLightPolicy.applies(true,true,true,false,false,false,51,51,60));
        check(LowLightPolicy.applies(true,true,true,false,false,false,200,5,5));
        check(!LowLightPolicy.applies(true,true,true,false,false,true,1,1,100));
        check(LowLightPolicy.chosen(1000,true,true,2000,3500)==2000);
        check(LowLightPolicy.chosen(1000,false,true,2000,3500)==3500);
        check(LowLightPolicy.chosen(6000,false,true,2000,3500)==6000);
        check(LowLightPolicy.withinWindow(4000,1000,5000,1000)==3750);
        check(LowLightPolicy.withinWindow(3500,1000,5000,1000)==3500);
        // Property checks: threshold tuning preserves direction over dark and bright ranges.
        for(float lux:new float[]{0,.01f,.95f,5,50,200,2000})for(float scale:new float[]{.5f,1,2,3})for(float floor:new float[]{0,.5f,5,10}){
            float bright=AdvancedPolicy.threshold(lux,lux+Math.max(1,lux*.2f),true,scale,floor);
            float dark=AdvancedPolicy.threshold(lux,lux*.8f,false,scale,floor);
            check(Float.isFinite(bright)&&bright>=lux&&bright-lux+.001f>=floor);
            check(Float.isFinite(dark)&&dark>=0&&dark<=lux);
        }
        check(Float.isNaN(AdvancedPolicy.threshold(1,Float.NaN,true,1,0)));
        check(AdvancedPolicy.threshold(10,5,true,3,10)==5); // Unexpected system branch is untouched.
        check(AdvancedPolicy.duration(0,3)==0);check(AdvancedPolicy.duration(2,3)==6);
        check(AdvancedPolicy.duration(50,3)==60);check(Double.isNaN(AdvancedPolicy.duration(Double.NaN,2)));
        check(AdvancedPolicy.retainedDelay(15000,5000,10000,0)==9750);
        check(AdvancedPolicy.retainedDelay(15000,5000,10000,3000)==6750);
        check(AdvancedPolicy.retainedDelay(1000,5000,10000,0)==1000);
        check(AdvancedPolicy.retainedDelay(15000,5000,200,0)==5000);
        check(AdvancedPolicy.threshold(.95f,.5f,false,3,5)>0); // Genuine darkness can still cross the strict threshold.
        check(LowLightPolicy.withinWindow(3000,1000,3000,0)==2750);
        check(LowLightPolicy.withinWindow(5000,5000,3000,0)==5000); // Do not reduce an existing OEM safety wait.
        bad(()->AdvancedPolicy.range(Double.NaN,.5,3));bad(()->AdvancedPolicy.range(Double.POSITIVE_INFINITY,.5,3));
        bad(()->AdvancedPolicy.range(.49,.5,3));bad(()->AdvancedPolicy.range(3.01,.5,3));
        check(UiText.translate("微小变亮阈值倍率：1×",true).startsWith("Small-brightening threshold multiplier"));
        // Continuous evidence with pruning: legal delay settles rather than chasing a moving deadline.
        boolean confirmed=false;long selected=AdvancedPolicy.retainedDelay(15000,5000,10000,0);
        for(long now=0;now<=20000;now+=250){long oldest=Math.max(0,now-10000);if(oldest+selected<=now){confirmed=true;break;}}
        check(confirmed);
        System.out.println("Refactor curve/adapter/thermal/memory/response/update/modules/stability/trace/advanced: "+cases+" cases PASS; OEM calls modeled, device not verified");
    }
}
