"""Exercise additive native layouts with production hooks; no firmware is distributed.

The older layout mirrors interfaces observed in the supplied Android 16 / OS3
collection. OS names never participate in selection. Existing OS4 fixtures run
separately in the full build.
"""
from pathlib import Path
import ast, re, subprocess, os

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
J=R/'build/refactor-diagnostics/json-20240303.jar'
O=R/'build/os3-compatibility-tests';O.mkdir(exist_ok=True)

def fixtures(name):
    tree=ast.parse((R/'tests'/name).read_text(encoding='utf-8'))
    return ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))

def method(signature):
    text=(S/'HookRuntime.java').read_text(encoding='utf-8');start=text.index(signature);at=text.index('{',start);end=at+1;depth=1
    while depth:depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]

def run(group,scenario,sources,production,main):
    out=O/group/scenario;out.mkdir(parents=True,exist_ok=True)
    paths=[]
    for name,code in sources.items():
        p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
    classes=out/'classes';classes.mkdir(exist_ok=True)
    compilation=subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(classes),*map(str,paths),str(S/'hooking/HookTransaction.java'),*[str(S/(name+'.java')) for name in production]],capture_output=True,text=True,encoding='utf-8',errors='replace')
    if compilation.returncode:print(compilation.stderr)
    compilation.check_returncode()
    result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),main,scenario],capture_output=True,text=True,encoding='utf-8',errors='replace')
    print(result.stdout,end='')
    if result.returncode:print(result.stderr)
    result.check_returncode()
    return int(re.search(r'(\d+) cases PASS',result.stdout).group(1))

total=0
advanced=fixtures('refactor_advanced_hooks.py')
for scenario in ['native_old','partial_driving','wrong_flag','unknown_detector']:
    sources=dict(advanced)
    detector='package com.android.server.display;public class SceneDetector{public boolean mIsStepMode,mIsReflectiveScene,mNeedCheckProximitySensor,mNeedCheckAonFlare;}'
    sources['com/android/server/display/SceneDetector.java']=detector.replace('mNeedCheckAonFlare','unknown' if scenario=='unknown_detector' else 'mNeedCheckAonFlare')
    impl='public AutomaticBrightnessController mAutomaticBrightnessController;public HysteresisLevelsImpl mHysteresisLevelsImpl;public DualSensorPolicy mDualSensorPolicy=new DualSensorPolicy();public TouchCoverProtectionHelper mTouchAreaHelper=new TouchCoverProtectionHelper();public SceneDetector mSceneDetector=new SceneDetector();public boolean mUseAonFlareEnabled,mUseProximityEnabled,mTransientProximityEnabled;'
    if scenario=='partial_driving':impl+='public boolean mUseDrivingModeForBrt;'
    if scenario=='wrong_flag':impl=impl.replace('mTransientProximityEnabled;','spare;public int mTransientProximityEnabled;')
    # This test uses actual eligibility methods, not a mocked "OS3 supported" flag.
    double=sources['top/rongshangs/lumacurve/refactor/HookRuntime.java']
    double=re.sub(r'boolean normalTuningAllowed\(Object a,Object i\)\{[^\n]+?\}boolean animationTuningAllowed',method('    boolean normalTuningAllowed(')+' boolean screenOn(){return true;}boolean animationTuningAllowed',double)
    double=double.replace('__PRODUCTION_LOW_LIGHT_ELIGIBILITY__',method('    boolean lowLightApplies(')+method('    static boolean nightDrivingConfirmed('))
    sources['top/rongshangs/lumacurve/refactor/HookRuntime.java']=double
    sources['com/android/server/display/AutomaticBrightnessController.java']=sources['com/android/server/display/AutomaticBrightnessController.java'].replace('return impl!=null&&impl.driving?impl.nightDelay:0;','return 0;')
    sources['top/rongshangs/lumacurve/refactor/AdvancedHookTest.java']='''package top.rongshangs.lumacurve.refactor;
import org.json.*;import com.android.server.display.*;import top.rongshangs.lumacurve.refactor.hooking.XposedBridge;
public class AdvancedHookTest{
 public static class Impl {IMPL}
 public static class Display{public int getScreenState(){return 2;}}
 public static class Dpc{public Display getDisplayPowerState(){return new Display();}}
 public static class Owner{public Impl mAutomaticBrightnessControllerImpl=new Impl();public Dpc mDisplayPowerController=new Dpc();public RefactorAutoBrightnessAnimator mBrightnessAnimator=new RefactorAutoBrightnessAnimator(),mSdrBrightnessAnimator=new RefactorAutoBrightnessAnimator();public SunlightController mSunlightController=new SunlightController();}
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("scene case "+cases);cases++;}
 public static void main(String[] args)throws Exception{
  boolean supported=args[0].equals("native_old");Owner o=new Owner();Impl impl=o.mAutomaticBrightnessControllerImpl;AutomaticBrightnessController abc=new AutomaticBrightnessController();impl.mAutomaticBrightnessController=abc;abc.impl=impl;
  HysteresisLevelsImpl h=new HysteresisLevelsImpl();impl.mHysteresisLevelsImpl=h;abc.h=h;impl.mDualSensorPolicy.h=h;
  HookRuntime s=new HookRuntime();s.owner=o;HookEntry.states.put(o,s);ClassLoader loader=AdvancedHookTest.class.getClassLoader();ResponseTuning.install(loader);LowLightTuning.install(loader);AdvancedTuning.install(loader);
  check(ResponseTuning.supported(o)==supported);check(ResponseTuning.smallSupported(o)==supported);check(s.normalTuningAllowed(abc,impl)==supported);
  for(int group=0;group<5;group++)check(AdvancedTuning.supported(group,s)==supported);
  if(!supported){check(ResponseTuning.sceneProfile(o).equals("unsupported"));System.out.println("OS3 scene "+args[0]+": "+cases+" cases PASS");return;}
  check(ResponseTuning.sceneProfile(o).equals("native_without_driving"));check(!ResponseTuning.driving(impl)&&ResponseTuning.nightDelay(impl)==0);
  s.responseEnabled=true;s.brightenDelay=2000;s.darkenDelay=1800;abc.mStepModeDarkenDebounceConfig=1300;
  check((Long)XposedBridge.call(abc,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)==10000);
  // The OEM 1300 ms walking delay remains; retained-history clamping may shorten only our override.
  check((Long)XposedBridge.call(abc,"nextAmbientLightDarkeningTransition",new Class<?>[]{long.class},8000L)==10750);
  s.lowLightEnabled=true;check(LowLightTuning.supported(s));check(s.lowLightApplies(abc,impl,20));
  s.hdr=true;check(!s.normalTuningAllowed(abc,impl)&&!s.lowLightApplies(abc,impl,20));s.hdr=false;
  abc.idle=true;check(!s.normalTuningAllowed(abc,impl));abc.idle=false;s.kernel.auto=false;check(!s.normalTuningAllowed(abc,impl));s.kernel.auto=true;
  s.allowed=false;check(!s.normalTuningAllowed(abc,impl));s.allowed=true;s.closed=true;check(!s.normalTuningAllowed(abc,impl));
  System.out.println("OS3 scene "+args[0]+": "+cases+" cases PASS");
 }
}'''.replace('IMPL',impl)
    total+=run('scene',scenario,sources,['AdvancedPolicy','AdvancedOptions','AdvancedTuning','FieldOverride','TraditionalAnimation','DelayPolicy','LowLightPolicy','LowLightThresholds','LowLightAssistGate','LowLightAssistEvidence','MemoryOptions','ResponseTuning','LowLightTuning'],'top.rongshangs.lumacurve.refactor.AdvancedHookTest')

outdoor=fixtures('refactor_outdoor.py')
for scenario in ['native_old','modern_priority','wrong_enable','wrong_support','partial_target','wrong_owner']:
    sources=dict(outdoor)
    sources.pop('top/rongshangs/lumacurve/refactor/OutdoorTest.java')
    key='com/android/server/display/HighBrightnessModeController.java';hbm=sources[key]
    if scenario!='modern_priority':
        hbm=hbm.replace(',mHbmControllerIsEnabled=true','').replace('mHbmControllerIsEnabled','deviceSupportsHbm()')
    hbm=hbm.replace('public float peak=', 'public boolean support=true;public boolean deviceSupportsHbm(){return support&&mHbmData!=null&&mHighBrightnessModeMetadata!=null;} public float peak=')
    if scenario=='wrong_enable':hbm=hbm.replace('public boolean support=', 'public int mHbmControllerIsEnabled=1;public boolean support=')
    if scenario=='wrong_support':hbm=hbm.replace('public boolean deviceSupportsHbm(){return support&&mHbmData!=null&&mHighBrightnessModeMetadata!=null;}','public int deviceSupportsHbm(){return 1;}').replace('!deviceSupportsHbm()','!support').replace('if(deviceSupportsHbm())','if(support)')
    sources[key]=hbm
    key='com/android/server/display/AutomaticBrightnessControllerImpl.java';impl=sources[key]
    if scenario!='modern_priority':impl=re.sub(r'public float getOverrideLimitBrightness\(.*?\)\{return .*?;\}', '',impl)
    extra='''public DisplayPowerControllerImpl mDisplayPowerControllerImpl;
 public float getCustomBrightness(float lux,String pkg,int cat,float old,float value,boolean user){return (Float)XposedBridge.invoke(this,"getCustomBrightness",new Class<?>[]{float.class,String.class,int.class,float.class,float.class,boolean.class},new Object[]{lux,pkg,cat,old,value,user},()->value);}
'''
    if scenario=='wrong_owner':extra=extra.replace('DisplayPowerControllerImpl mDisplayPowerControllerImpl','Object mDisplayPowerControllerImpl')
    if scenario=='partial_target':extra+='public float getOverrideLimitBrightness(float value){return value;}'
    impl=impl[:-1]+extra+'}'
    sources[key]=impl
    key='com/android/server/display/DisplayPowerControllerImpl.java';owner=sources[key]
    owner=owner[:-1]+'''public float getCustomBrightness(float lux,String pkg,int cat,float old,float value,boolean user){return value;}
}'''
    sources[key]=owner
    sources['top/rongshangs/lumacurve/refactor/Os3OutdoorTest.java']='''package top.rongshangs.lumacurve.refactor;
import com.android.server.display.*;import com.android.server.display.config.*;import com.android.server.display.brightness.*;import org.json.*;import android.os.*;
public class Os3OutdoorTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("outdoor case "+cases);cases++;}static void eq(float a,float b){check(Math.abs(a-b)<.00001f);}
 static float target(DisplayPowerControllerImpl o,String which)throws Exception{Object impl=o.mAutomaticBrightnessControllerImpl;
  return which.equals("modern_priority")?(Float)impl.getClass().getMethod("getOverrideLimitBrightness",float.class,boolean.class,float.class).invoke(impl,.4f,false,30000f):o.mAutomaticBrightnessControllerImpl.getCustomBrightness(30000,"pkg",0,.3f,.4f,false);
 }

 public static void main(String[] args)throws Exception{
  String which=args[0];DisplayPowerControllerImpl o=new DisplayPowerControllerImpl();o.mAutomaticBrightnessControllerImpl.mDisplayPowerControllerImpl=o;
  HookRuntime s=new HookRuntime();s.owner=o;HookEntry.states.put(o,s);OutdoorTuning.install(o.getClass());
  if(which.equals("wrong_enable")||which.equals("wrong_support")){try{new HbmAccess(o.mHbmController);throw new AssertionError("invalid HBM accepted");}catch(Exception expected){cases++;}System.out.println("OS3 outdoor "+which+": "+cases+" cases PASS");return;}
  if(which.equals("partial_target")||which.equals("wrong_owner")){check(!s.outdoor.supported());check(!OutdoorTuning.owners.contains(o.getClass()));eq(o.mAutomaticBrightnessControllerImpl.getCustomBrightness(30000,"pkg",0,.3f,.4f,false),.4f);System.out.println("OS3 outdoor "+which+": "+cases+" cases PASS");return;}
  HbmAccess h=new HbmAccess(o.mHbmController);check(h.managed());check(h.controllerProfile.equals(which.equals("modern_priority")?"enabled_field":"native_support_method"));
  if(which.equals("modern_priority")){o.mHbmController.support=false;check(h.managed());check(OutdoorTuning.targetProfile(o.getClass()).equals("override_target"));}
  else{o.mHbmController.support=false;check(!h.managed());o.mHbmController.support=true;check(OutdoorTuning.targetProfile(o.getClass()).equals("legacy_custom_target"));}
  HighBrightnessModeData original=new HighBrightnessModeData(6001,.5f,0,0,0,false,.5f,null,null,true);o.mHbmController.mHbmData=original;h.observeReplacement();
  check(h.tunable()&&!h.budgetTunable());OutdoorOptions opt=OutdoorOptions.parse(new JSONObject().put("outdoor_enabled",true).put("outdoor_range_unlock",true).put("outdoor_opr_relax",true).put("outdoor_hbm_tuning",true).put("outdoor_hbm_lux_factor",.5).put("outdoor_hbm_budget_factor",2));
  check(h.configure(opt,true));eq(o.mHbmController.mHbmData.minimumLux,3000.5f);check(o.mHbmController.mHbmData.timeMaxMillis==0&&o.mHbmController.mHbmData.timeWindowMillis==0&&o.mHbmController.mHbmData.timeMinMillis==0);check(h.restore()&&o.mHbmController.mHbmData==original);
  s.outdoor.configure(opt);s.handler.advance(SystemClock.now+3000);o.thermal=1;o.opr=.4f;
  float target=target(o,which);
  check(target>.4f);check(s.outdoor.boosts==1);eq(o.adjustSdrBrightness(target,true,new BrightnessReason(),false,false),target);
  o.thermal=.2f;eq(o.adjustSdrBrightness(target,true,new BrightnessReason(),false,false),.2f);o.thermal=1;o.battery=.15f;eq(o.adjustSdrBrightness(target,true,new BrightnessReason(),false,false),.15f);o.battery=1;
  for(int guard=0;guard<8;guard++){
   if(guard==0)s.hdr=true;if(guard==1)s.power.low=true;if(guard==2)s.kernel.auto=false;if(guard==3)s.scene=false;if(guard==4)s.screen=false;if(guard==5)s.user=false;if(guard==6)s.thread=false;if(guard==7)s.strategy="OverrideBrightnessStrategy";
   float result=target(o,which);eq(result,.4f);
   s.hdr=false;s.power.low=false;s.kernel.auto=true;s.scene=true;s.screen=true;s.user=true;s.thread=true;s.strategy="AutomaticBrightnessStrategy";s.outdoor.stop();s.outdoor.configure(opt);s.handler.advance(SystemClock.now+3000);
  }
  s.outdoor.stop();check(o.mHbmController.mHbmData==original);
  System.out.println("OS3 outdoor "+which+": "+cases+" cases PASS");
 }
}'''
    total+=run('outdoor',scenario,sources,['OutdoorNodeTarget','OutdoorOptions','OutdoorPolicy','HbmAccess','OutdoorController','OutdoorTuning','OutdoorOpr','OutdoorOprEnvelope','OutdoorSdrScope','ThermalPolicy','AdvancedPolicy','LiveLimitEvidence','NitBrightnessLimit'],'top.rongshangs.lumacurve.refactor.Os3OutdoorTest')

print(f'OS3 compatibility: {total} cases PASS; actual hooks with isolated native ABI fixtures, not a device test')

# Optional local provenance check. Proprietary dumps stay in ignored build/ and
# never enter the source archive. The portable fixtures above always run.
private=R/'build/os3-discussion-20261018-75bb25'
if (private/'disassembly').is_dir():
    def read(name,extra=False):return (private/('extra' if extra else 'disassembly')/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
    def body(text,name):
        blocks=[b for b in text.split('\nMETHOD ')[1:] if b.startswith(name+' ')]
        assert len(blocks)==1,(name,len(blocks))
        return blocks[0]
    checks=0
    def check(value):
        global checks
        assert value,checks
        checks+=1
    impl=read('AutomaticBrightnessControllerImpl');abc=read('AutomaticBrightnessController');scene=read('SceneDetector');hbm=read('HighBrightnessModeController');owner=read('DisplayPowerControllerImpl')
    for text in [impl,scene]:check(not re.search(r'^(?:FIELD|METHOD) [^\n]*[Dd]riv',text,re.M))
    for name in ['mUseAonFlareEnabled','mUseProximityEnabled','mTransientProximityEnabled']:check('FIELD '+name+' Z' in impl)
    for name in ['mIsStepMode','mIsReflectiveScene','mNeedCheckProximitySensor','mNeedCheckAonFlare']:check('FIELD '+name+' Z' in scene)
    check('METHOD getOverrideLimitBrightness ' not in impl);check('METHOD getCustomBrightness (F Ljava/lang/String; I F F Z)F' in impl)
    auto=body(abc,'updateAutoBrightness')
    check(auto.index('->getCustomBrightness(')<auto.index('->clampScreenBrightness(')<auto.rindex('->isNeedSkipBrightnessChange()Z'))
    for name in ['mBrighteningLightDebounceConfig','mDarkeningLightDebounceConfig','mStepModeDarkenDebounceConfig']:check('FIELD '+name+' J' in abc)
    check('FIELD mHbmControllerIsEnabled ' not in hbm)
    supports=body(hbm,'deviceSupportsHbm');check('->mHbmData ' in supports and '->mHighBrightnessModeMetadata ' in supports)
    allowed=body(hbm,'isHbmCurrentlyAllowed')
    for name in ['mIsAutoBrightnessEnabled','mIsTimeAvailable','mIsInAllowedAmbientRange','mIsBlockedByLowPowerMode']:check('->'+name+' Z' in allowed)
    sdr=body(owner,'adjustSdrBrightness');check(sdr.index('->adjustBrightnessByOpr(')<sdr.index('->adjustBrightnessByThermal(')<sdr.index('->adjustBrightnessByBattery('))
    data=read('config.HighBrightnessModeData',True);check('METHOD <init> (F F J J J Z F Landroid/util/Spline; Landroid/view/SurfaceControl$RefreshRateRange; Z)V' in data)
    print(f'OS3 private firmware: {checks} checks PASS; actual call order, guards and native field layout, not a device test')
