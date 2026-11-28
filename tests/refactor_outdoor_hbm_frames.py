"""Replay the verified temporary-reason reset with production HBM and output gates."""
from pathlib import Path
import ast
import os
import subprocess

root = Path(__file__).resolve().parents[1]
fixtures = ast.parse((root/'tests/refactor_outdoor.py').read_text('utf-8'))
sources = ast.literal_eval(next(node.value for node in fixtures.body
    if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id=='sources' for t in node.targets)))
core = 'top/rongshangs/lumacurve/refactor/'
oem = 'com/android/server/display/'
sources.pop(core+'OutdoorOutputAccess.java')
sources.pop(core+'OutdoorTest.java')
entry=sources[core+'HookEntry.java']
sources[core+'HookEntry.java']=entry.replace(
    'static Field field(Class<?> c,String n)throws Exception{',
    'static int reasonLookups;static Field field(Class<?> c,String n)throws Exception{if(n.equals("REASON_AUTOMATIC"))reasonLookups++;',
)
dpc = sources[oem+'DisplayPowerController.java']
sources[oem+'DisplayPowerController.java'] = dpc[:-1]+'''
public AutomaticBrightnessController mAutomaticBrightnessController;
public String mUniqueDisplayId="local:12345";
public com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2 mAutomaticBrightnessStrategy=
    new com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2();
public com.android.server.display.brightness.BrightnessReason mBrightnessReason=
    new com.android.server.display.brightness.BrightnessReason();
public com.android.server.display.brightness.BrightnessReason mBrightnessReasonTemp=
    new com.android.server.display.brightness.BrightnessReason();
}'''
sources[oem+'brightness/strategy/AutomaticBrightnessStrategy2.java'] = '''
package com.android.server.display.brightness.strategy;
public class AutomaticBrightnessStrategy2 {
    public int mDisplayId;
    public Object mAutomaticBrightnessController;
    public boolean isAutoBrightnessEnabled(){return true;}
    public boolean shouldUseAutoBrightness(){return true;}
    public boolean hasAppliedAutoBrightness(){return true;}
}'''
reason = sources[oem+'brightness/BrightnessReason.java']
sources[oem+'brightness/BrightnessReason.java'] = reason[:-1]+'''
public static final int REASON_UNKNOWN=0,REASON_AUTOMATIC=4,REASON_MANUAL=1,REASON_OVERRIDE=6,REASON_DOZE=2,REASON_BOOST=8;
public static final int MODIFIER_BCBC=2048,MODIFIER_OPR=8192,MODIFIER_PEAK=64;
public static final int MODIFIER_THERMAL=8,MODIFIER_HDR=16,MODIFIER_DIMMED=32,MODIFIER_LOW_POWER=128,MODIFIER_THROTTLED=256;
public int reason=REASON_AUTOMATIC;
public int getReason(){return reason;}
}'''
sources[core+'Replay.java'] = '''
package top.rongshangs.lumacurve.refactor;
import android.os.SystemClock;
import org.json.JSONObject;
import com.android.server.display.*;
import com.android.server.display.config.*;
import com.android.server.display.brightness.BrightnessReason;
public final class Replay {
    static int cases;
    static void check(boolean condition){if(!condition)throw new AssertionError("case "+cases);cases++;}
    public static void main(String[] ignored)throws Exception{
        OutdoorTuning.install(DisplayPowerControllerImpl.class);
        for(double factor:new double[]{1.0,1.1})for(boolean custom:new boolean[]{false,true}){
            DisplayPowerControllerImpl owner=new DisplayPowerControllerImpl();
            HookRuntime runtime=new HookRuntime();
            HookEntry.states.put(owner,runtime);runtime.owner=owner;
            DisplayPowerController dpc=owner.mDisplayPowerController;
            AutomaticBrightnessController abc=owner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController;
            dpc.mAutomaticBrightnessController=abc;dpc.mAutomaticBrightnessStrategy.mAutomaticBrightnessController=abc;
            abc.mAmbientLux=33000f;
            HighBrightnessModeController hbm=owner.mHbmController;
            HighBrightnessModeData original=new HighBrightnessModeData(4475,.24996346f,0,0,0,true,0,null,null,true);
            hbm.mHbmData=original;
            OutdoorOptions options=OutdoorOptions.parse(new JSONObject().put("outdoor_enabled",true)
                .put("outdoor_range_unlock",true).put("outdoor_opr_relax",true)
                .put("outdoor_enter_lux",18000).put("outdoor_enter_ms",2000)
                .put("outdoor_hbm_tuning",custom).put("outdoor_hbm_lux_factor",factor));
            runtime.outdoor.configure(options);runtime.handler.advance(SystemClock.now+2000);
            check(runtime.outdoor.policy.active&&OutdoorOutputAccess.profile(runtime).equals("automatic_strategy2_reason"));
            HighBrightnessModeData active=hbm.mHbmData;
            check((active!=original)==(custom&&factor!=1));
            int work=runtime.recalculations,nativeWork=hbm.recalc;
            for(int frame=0;frame<500;frame++){
                ++SystemClock.now;
                dpc.mBrightnessReasonTemp.reason=0;
                runtime.outdoor.tick();
                check(hbm.mHbmData==active&&!runtime.outdoor.oprAllowed());
                dpc.mBrightnessReasonTemp.reason=BrightnessReason.REASON_AUTOMATIC;
                owner.adjustSdrBrightness(.8f,true,dpc.mBrightnessReasonTemp,false,false);
                check(hbm.mHbmData==active&&runtime.outdoor.policy.active&&runtime.outdoor.oprAllowed());
            }
            int requests=runtime.recalculations-work, nativeRequests=hbm.recalc-nativeWork;
            check(requests==0&&nativeRequests==0);
            System.out.printf("custom=%s factor=%.1f frames=500 moduleRecalculations=%d nativeReevaluations=%d%n",
                custom,factor,requests,nativeRequests);
            if(active!=original){
                dpc.mBrightnessReasonTemp.reason=0;runtime.outdoor.tick();
                check(hbm.mHbmData==active&&runtime.outdoor.policy.active);
                SystemClock.now+=50;runtime.outdoor.tick();
                check(hbm.mHbmData==original&&!runtime.outdoor.policy.active);
                dpc.mBrightnessReasonTemp.reason=4;
                for(int which=0;which<6;which++){
                    runtime.outdoor.stop();runtime.outdoor.configure(options);runtime.handler.advance(SystemClock.now+2000);
                    check(hbm.mHbmData!=original);
                    dpc.mBrightnessReasonTemp.reason=0;
                    switch(which){
                        case 0:dpc.mBrightnessReasonTemp.reason=BrightnessReason.REASON_OVERRIDE;break;
                        case 1:runtime.hdr=true;break;
                        case 2:runtime.batteryTemperature=50;break;
                        case 3:runtime.kernel.auto=false;break;
                        case 4:dpc.mUniqueDisplayId="local:67890";break;
                        case 5:dpc.mBrightnessReasonTemp.setModifier(BrightnessReason.MODIFIER_THERMAL);break;
                    }
                    runtime.outdoor.tick();
                    check(hbm.mHbmData==original&&!runtime.outdoor.oprAllowed());
                    runtime.hdr=false;runtime.batteryTemperature=30;runtime.kernel.auto=true;
                    dpc.mUniqueDisplayId="local:12345";
                    dpc.mBrightnessReasonTemp.reason=4;dpc.mBrightnessReasonTemp.setModifier(0);
                }
            }
            runtime.outdoor.stop();HookEntry.states.remove(owner);
            check(hbm.mHbmData==original);
        }
        check(HookEntry.reasonLookups==1);
        System.out.println("HBM frame stability: "+cases+" cases PASS; production Java with bounded callback replay, device not tested");
    }
}'''
out = root/'build/outdoor-hbm-frame-tests'
classes = out/'classes'
classes.mkdir(parents=True,exist_ok=True)
java = root/'experimental/refactor_hook/java'/core
jars = root/'build/refactor-diagnostics/json-20240303.jar'
production = [java/'hooking/HookTransaction.java'] + [java/(name+'.java') for name in (
    'OutdoorNodeTarget','OutdoorTargetCurve','OutdoorOptions','OutdoorPolicy','HbmAccess',
    'OutdoorController','OutdoorTuning','OutdoorOpr','OutdoorOprEnvelope','OutdoorSdrScope',
    'ThermalPolicy','AdvancedPolicy','LiveLimitEvidence','NitBrightnessLimit','OutdoorCurvePreview','OutdoorOutputAccess',
)]
files = []
for name,body in sources.items():
    p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,'utf-8');files.append(p)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(jars),'-d',str(classes),*map(str,production+files)],check=True)
subprocess.run(['java','-cp',os.pathsep.join(map(str,(classes,jars))),'top.rongshangs.lumacurve.refactor.Replay'],check=True)
