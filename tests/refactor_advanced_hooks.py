"""Exercise production optional hooks with a small Xposed callback model, not an Android emulator."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-advanced-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
sources={
'android/os/Looper.java':'''package android.os;public class Looper {public static Looper myLooper(){return null;}}''',
'android/os/Handler.java':'''package android.os;public class Handler {public Looper getLooper(){return null;}}''',
'de/robv/android/xposed/XC_MethodHook.java':'''package de.robv.android.xposed;import java.util.*;
public class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
 public class Unhook {Runnable remove;Unhook(Runnable r){remove=r;}public void unhook(){remove.run();}}
 public static class MethodHookParam {public Object thisObject;public Object[] args;Object result;Throwable throwable;Map<String,Object> extra=new HashMap<>();
 public boolean hasThrowable(){return throwable!=null;}public Object getResult(){return result;}public void setResult(Object r){result=r;}public void setObjectExtra(String k,Object v){extra.put(k,v);}public Object getObjectExtra(String k){return extra.get(k);}}
}''',
'de/robv/android/xposed/XposedBridge.java':'''package de.robv.android.xposed;import java.lang.reflect.*;import java.util.*;
public final class XposedBridge {static Map<Method,List<XC_MethodHook>> hooks=new HashMap<>();public static Object invokeOriginalMethod(Method m,Object target,Object[] args)throws Exception{m.setAccessible(true);return m.invoke(target,args);}
 public static XC_MethodHook.Unhook hookMethod(Method m,XC_MethodHook h){hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);return h.new Unhook(()->hooks.get(m).remove(h));}public static void log(String s){System.out.println(s);}
 public static Object call(Object o,String name,Class<?>[] types,Object...args){try{
 Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=o;p.args=args;
 List<XC_MethodHook> list=hooks.getOrDefault(m,Collections.emptyList());for(XC_MethodHook h:list)h.beforeHookedMethod(p);
 try{p.result=m.invoke(o,args);}catch(InvocationTargetException ex){p.throwable=ex.getCause();}
 for(int i=list.size()-1;i>=0;i--)list.get(i).afterHookedMethod(p);if(p.throwable!=null)throw p.throwable;return p.result;
 }catch(Throwable ex){throw new RuntimeException(ex);}}
}''',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;import java.util.*;import java.lang.reflect.*;
class HookEntry {static Map<Object,HookRuntime> states=new IdentityHashMap<>();static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}}
''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;class HookRuntime {
 LowLightAssistGate assistGate=new LowLightAssistGate();LowLightThresholds lowThresholds=new LowLightThresholds();float lowLightLimit=50;long lowLightThresholdAdjustments;Object owner;AdvancedOptions advanced=new AdvancedOptions();String phase="active";boolean closed,allowed=true,probingScenes;Boolean hdr=false;Object hdrProbe=new Object();long thresholdAdjustments,animationAdjustments,sunlightAdjustments,touchAdjustments;double lastAnimationSeconds;
 android.os.Handler handler=new android.os.Handler();Kernel kernel=new Kernel();static class Kernel{boolean auto=true;Object get(String n){return auto;}}
 boolean responseEnabled,smallResponseEnabled,lowLightEnabled;long brightenDelay=1500,darkenDelay=5000,smallBrightenDelay=5000,lowLightBrighten=3000,lowLightDarken=4000,responseAdjustments,smallResponseAdjustments,lowLightMainAdjustments,lowLightAssistAdjustments,assistAdjustments,delayWindowClamps,lastMainSmall,lastMainBrighten,lastMainDarken,lastMainExtra;
 boolean onDisplayThread(){return !closed;}boolean normalTuningAllowed(Object a,Object i){return allowed&&!closed&&phase.equals("active")&&kernel.auto&&!((com.android.server.display.AutomaticBrightnessController)a).idle&&!((AdvancedHookTest.Impl)i).driving&&Boolean.FALSE.equals(hdr);}boolean animationTuningAllowed(Object a,Object i){return normalTuningAllowed(a,i);}boolean appliesToUser(){return true;}Boolean hdrActive(){return hdr;}void log(String s){}
 String sensorName(int flag){return flag==0?"main":"assist";}float mainCandidate(Object a,Object i){float v=optionalNumber(((AdvancedHookTest.Impl)i).mDualSensorPolicy,"mMainFastAmbientLux");return Float.isFinite(v)?v:optionalNumber(a,"mAmbientLux");}boolean lowLightApplies(Object a,Object i,float candidate){return lowLightEnabled&&normalTuningAllowed(a,i)&&candidate<=50;}
 static float optionalNumber(Object o,String f){try{return ((Number)HookEntry.get(o,f)).floatValue();}catch(Exception e){return Float.NaN;}}
 static Boolean optionalBoolean(Object o,String f){try{return (Boolean)HookEntry.get(o,f);}catch(Exception e){return null;}}
}''',
'com/android/server/display/HysteresisLevelsImpl.java':'''package com.android.server.display;public class HysteresisLevelsImpl{
 public Object mHbmController;public float getBrighteningThreshold(float lux){return lux+10;}public float getDarkeningThreshold(float lux){return Math.max(0,lux-10);}public float getBrighteningSmallThreshold(float lux){return lux+2;}}
''',
'com/android/server/display/AutomaticBrightnessController.java':'''package com.android.server.display;import de.robv.android.xposed.XposedBridge;public class AutomaticBrightnessController{
 public HysteresisLevelsImpl h;public float mAmbientLux=20,mAmbientBrighteningThreshold,mAmbientDarkeningThreshold,mAmbientBrighteningSmallThreshold;
 public boolean idle,mAmbientLuxValid=true;public int mAmbientLightHorizonLong=3000;public long mBrighteningLightDebounceConfig=1000,mDarkeningLightDebounceConfig=1000,mSmallBrighteningLightDebounceConfig=5000,mStepModeDarkenDebounceConfig=0;
 public boolean isInIdleMode(){return idle;}public long nextAmbientLightBrighteningTransition(long now){return now+mBrighteningLightDebounceConfig;}public long nextAmbientLightBrighteningTransition(long now,float lux){return now+(lux<mAmbientBrighteningThreshold?mSmallBrighteningLightDebounceConfig:mBrighteningLightDebounceConfig);}public long nextAmbientLightDarkeningTransition(long now){return now+mDarkeningLightDebounceConfig+mStepModeDarkenDebounceConfig;}
 public void setAmbientLux(float v){mAmbientLux=v;mAmbientBrighteningThreshold=(Float)XposedBridge.call(h,"getBrighteningThreshold",new Class<?>[]{float.class},v);mAmbientDarkeningThreshold=(Float)XposedBridge.call(h,"getDarkeningThreshold",new Class<?>[]{float.class},v);mAmbientBrighteningSmallThreshold=(Float)XposedBridge.call(h,"getBrighteningSmallThreshold",new Class<?>[]{float.class},v);}
}''',
'com/android/server/display/DualSensorPolicy.java':'''package com.android.server.display;import de.robv.android.xposed.XposedBridge;public class DualSensorPolicy {public float mMainFastAmbientLux=Float.NaN;public int mUseLightSensorFlag=0,mAssistLightSensorWarmUpTime=1000;public long mAssistLightSensorEnableTime=0;public boolean mAssistLightSensorEnable=true,mIsPendingResetAssistValue;public HysteresisLevelsImpl h;public boolean mAssistAmbientLuxValid=false;public float mAssistFastAmbientLux=20,mAssistBrighteningThreshold,mAssistDarkeningThreshold,mAssistBrighteningSmallThreshold;public AmbientLightRingBuffer mAssistLightSensorRingBuffer=new AmbientLightRingBuffer();public void updateDualSensorPolicy(long t,int v){mAssistBrighteningThreshold=(Float)XposedBridge.call(h,"getBrighteningThreshold",new Class<?>[]{float.class},mAssistFastAmbientLux);mAssistDarkeningThreshold=(Float)XposedBridge.call(h,"getDarkeningThreshold",new Class<?>[]{float.class},mAssistFastAmbientLux);mAssistBrighteningSmallThreshold=(Float)XposedBridge.call(h,"getBrighteningSmallThreshold",new Class<?>[]{float.class},mAssistFastAmbientLux);}}''',
'com/android/server/display/AmbientLightRingBuffer.java':'''package com.android.server.display;public class AmbientLightRingBuffer {public long[] time={6000,7000};public float[] lux={5,5};public int size(){return time.length;}public long getTime(int i){return time[i];}public float getLux(int i){return lux[i];}public long mBrighteningLightDebounceConfig=1000,mDarkeningLightDebounceConfig=1000,mSmallBrighteningLightDebounceConfig=5000;public long nextAmbientLightBrighteningTransition(long now,float lux){return now+mBrighteningLightDebounceConfig;}public long nextAmbientLightBrighteningTransition(long now,float lux,float threshold,float unused){return now+(lux<threshold?mSmallBrighteningLightDebounceConfig:mBrighteningLightDebounceConfig);}public long nextAmbientLightDarkeningTransition(long now,float lux){return now+mDarkeningLightDebounceConfig;}}''',
'com/android/server/display/TouchCoverProtectionHelper.java':'''package com.android.server.display;public class TouchCoverProtectionHelper {public int mTouchEventDebounce=1,ageSeconds=2;public boolean touching,throwing;protected boolean isTouchCoverProtectionActive(){if(throwing)throw new IllegalStateException("test original failure");return touching||ageSeconds<mTouchEventDebounce;}}''',
'com/android/server/display/RefactorAutoBrightnessAnimator.java':'''package com.android.server.display;public class RefactorAutoBrightnessAnimator{
 public double mDuration=1,mDurationTime=1,mLogicalStart=.1,mLogicalTarget=.5;public void updatePerceptualDuration(){mDuration=1;mDurationTime=1;}}
''',
'com/android/server/display/SunlightController.java':'''package com.android.server.display;public class SunlightController{
 public static final int THRESHOLD_ENTER_SUNLIGHT_DURATION=5000,THRESHOLD_EXIT_SUNLIGHT_DURATION=2000,SUNLIGHT_AMBIENT_LIGHT_HORIZON=10000;
 public boolean mSunlightSensorEnabled=true,mScreenOn=true,mAutoBrightnessSettingsEnable=false;
 public long nextEnterSunlightModeTransition(long now){return now+5000;}public long nextExitSunlightModeTransition(long now){return now+2000;}}
''',
'top/rongshangs/lumacurve/refactor/AdvancedHookTest.java':'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import com.android.server.display.*;import de.robv.android.xposed.XposedBridge;
public final class AdvancedHookTest {
 public static class Impl {public AutomaticBrightnessController mAutomaticBrightnessController;public HysteresisLevelsImpl mHysteresisLevelsImpl;public DualSensorPolicy mDualSensorPolicy=new DualSensorPolicy();public TouchCoverProtectionHelper mTouchAreaHelper=new TouchCoverProtectionHelper();public boolean driving;public boolean getDrivingStatus(){return driving;}}
 public static class Owner {public Impl mAutomaticBrightnessControllerImpl=new Impl();public RefactorAutoBrightnessAnimator mBrightnessAnimator=new RefactorAutoBrightnessAnimator(),mSdrBrightnessAnimator=new RefactorAutoBrightnessAnimator();public SunlightController mSunlightController=new SunlightController();}
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void invalid(JSONObject c){try{AdvancedOptions.parse(c);throw new AssertionError("bad options accepted");}catch(IllegalArgumentException expected){cases++;}}
 static void lux(AutomaticBrightnessController c,float lux){XposedBridge.call(c,"setAmbientLux",new Class<?>[]{float.class},lux);}
 public static void main(String[] args)throws Exception{
  AdvancedOptions defaults=AdvancedOptions.parse(new JSONObject());for(boolean enabled:defaults.enabled)check(!enabled);
  JSONObject round=new JSONObject();defaults.put(round);check(AdvancedOptions.parse(round).values[12]==2000);
  for(int i=0;i<AdvancedOptions.KEYS.length;i++){JSONObject c=new JSONObject().put(AdvancedOptions.KEYS[i],AdvancedOptions.MIN[i]);check(AdvancedOptions.parse(c).values[i]==AdvancedOptions.MIN[i]);c.put(AdvancedOptions.KEYS[i],AdvancedOptions.MAX[i]);check(AdvancedOptions.parse(c).values[i]==AdvancedOptions.MAX[i]);invalid(c.put(AdvancedOptions.KEYS[i],AdvancedOptions.MAX[i]+1));invalid(new JSONObject().put(AdvancedOptions.KEYS[i],"bad"));}
  invalid(new JSONObject().put("touch_release_seconds",1.5));
  invalid(new JSONObject().put("animation_override",1));invalid(new JSONObject().put("sunlight_override",JSONObject.NULL));
  AdvancedOptions enabled=AdvancedOptions.parse(new JSONObject().put("assist_override",true));try{enabled.verify(new JSONObject());throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  HysteresisLevelsImpl shared=new HysteresisLevelsImpl();Owner o=new Owner();o.mAutomaticBrightnessControllerImpl.mHysteresisLevelsImpl=shared;AutomaticBrightnessController main=new AutomaticBrightnessController();main.h=shared;o.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController=main;
  HookRuntime s=new HookRuntime();s.owner=o;HookEntry.states.put(o,s);ClassLoader loader=AdvancedHookTest.class.getClassLoader();ResponseTuning.install(loader);LowLightTuning.install(loader);AdvancedTuning.install(loader);for(int i=0;i<AdvancedOptions.GROUPS.length;i++)check(AdvancedTuning.supported(i,s));check(!AdvancedTuning.supported(99,s));
  lux(main,20);check(main.mAmbientBrighteningThreshold==30);
  s.advanced.enabled[0]=true;s.advanced.values[0]=2;s.advanced.values[1]=2;lux(main,20);check(main.mAmbientBrighteningThreshold==40&&main.mAmbientDarkeningThreshold>0&&main.mAmbientDarkeningThreshold<.01);
  AutomaticBrightnessController other=new AutomaticBrightnessController();other.h=shared;lux(other,20);check(other.mAmbientBrighteningThreshold==30);check(AdvancedTuning.thresholdScope.get()==null);
  s.hdr=true;lux(main,20);check(main.mAmbientBrighteningThreshold==30);s.hdr=false;
  s.hdr=null;lux(main,20);check(main.mAmbientBrighteningThreshold==30);s.hdr=false;
  lux(main,500);check(main.mAmbientBrighteningThreshold==510);
  s.advanced.enabled[0]=false;AdvancedTuning.refreshThresholds(s);check(main.mAmbientBrighteningThreshold==510);
  s.advanced.enabled[2]=true;s.advanced.values[9]=2;s.advanced.values[10]=3;
  XposedBridge.call(o.mBrightnessAnimator,"updatePerceptualDuration",new Class<?>[]{});check(o.mBrightnessAnimator.mDuration==2&&o.mBrightnessAnimator.mDurationTime==2);AdvancedTuning.restoreAnimation(s);check(o.mBrightnessAnimator.mDuration==1);
  o.mBrightnessAnimator.mLogicalTarget=.01;XposedBridge.call(o.mBrightnessAnimator,"updatePerceptualDuration",new Class<?>[]{});check(o.mBrightnessAnimator.mDuration==3);
  s.hdr=true;XposedBridge.call(o.mBrightnessAnimator,"updatePerceptualDuration",new Class<?>[]{});check(o.mBrightnessAnimator.mDuration==1);s.hdr=false;
  XposedBridge.call(o.mBrightnessAnimator,"updatePerceptualDuration",new Class<?>[]{});o.mBrightnessAnimator.mDuration=7;o.mBrightnessAnimator.mDurationTime=7;AdvancedTuning.restoreAnimation(s);check(o.mBrightnessAnimator.mDuration==7); // Do not overwrite a later OEM update.
  s.advanced.enabled[3]=true;s.advanced.values[11]=8000;s.advanced.values[12]=500;
  check((Long)XposedBridge.call(o.mSunlightController,"nextEnterSunlightModeTransition",new Class<?>[]{long.class},1000L)==9000);
  check((Long)XposedBridge.call(o.mSunlightController,"nextExitSunlightModeTransition",new Class<?>[]{long.class},1000L)==1500);
  o.mSunlightController.mAutoBrightnessSettingsEnable=true;check((Long)XposedBridge.call(o.mSunlightController,"nextEnterSunlightModeTransition",new Class<?>[]{long.class},1000L)==6000);
  o.mSunlightController.mAutoBrightnessSettingsEnable=false;s.phase="attached";check((Long)XposedBridge.call(o.mSunlightController,"nextEnterSunlightModeTransition",new Class<?>[]{long.class},1000L)==6000);
  s.phase="active";TouchCoverProtectionHelper touch=o.mAutomaticBrightnessControllerImpl.mTouchAreaHelper;s.advanced.enabled[4]=true;s.advanced.values[13]=3;
  check((Boolean)XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{}));check(touch.mTouchEventDebounce==1&&s.touchAdjustments==1);
  s.probingScenes=true;check((Boolean)XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{}));check(s.touchAdjustments==1);s.probingScenes=false;
  touch.throwing=true;try{XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{});throw new AssertionError();}catch(RuntimeException expected){check(touch.mTouchEventDebounce==1);}touch.throwing=false;
  s.advanced.values[13]=0;touch.touching=true;check((Boolean)XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{}));touch.touching=false;check(!(Boolean)XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{}));check(touch.mTouchEventDebounce==1);
  s.advanced.values[13]=3;s.hdr=true;check(!(Boolean)XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{}));s.hdr=false;
  check(!(Boolean)XposedBridge.call(new TouchCoverProtectionHelper(),"isTouchCoverProtectionActive",new Class<?>[]{}));
  s.responseEnabled=true;s.brightenDelay=5000;s.darkenDelay=6000;main.mAmbientLux=20;
  check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},1000L)==3750);check(s.lastMainBrighten==2750&&s.delayWindowClamps>0);
  main.mStepModeDarkenDebounceConfig=1000;check((Long)XposedBridge.call(main,"nextAmbientLightDarkeningTransition",new Class<?>[]{long.class},1000L)==3750);check(s.lastMainDarken==1750&&s.lastMainExtra==1000);
  main.mStepModeDarkenDebounceConfig=4000;check((Long)XposedBridge.call(main,"nextAmbientLightDarkeningTransition",new Class<?>[]{long.class},1000L)==6000); // Preserve longer OEM additions when no safe extension fits.
  main.mStepModeDarkenDebounceConfig=0;s.hdr=true;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},1000L)==2000);s.hdr=false;
  o.mAutomaticBrightnessControllerImpl.driving=true;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},1000L)==2000);o.mAutomaticBrightnessControllerImpl.driving=false;
  main.idle=true;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},1000L)==2000);main.idle=false;
  s.kernel.auto=false;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},1000L)==2000);s.kernel.auto=true;
  s.responseEnabled=false;s.lowLightEnabled=true;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},1000L)==3750);check(s.lowLightMainAdjustments>0);
  AmbientLightRingBuffer ring=o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy.mAssistLightSensorRingBuffer;s.advanced.enabled[1]=true;s.advanced.values[6]=4000;s.advanced.values[7]=3000;s.advanced.values[8]=3500;
  check((Long)XposedBridge.call(ring,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class,float.class},1000L,20f)==5000);
  check((Long)XposedBridge.call(ring,"nextAmbientLightDarkeningTransition",new Class<?>[]{long.class,float.class},1000L,20f)==5000); // Low-light minimum wins over shorter custom assist delay.
  s.lowLightEnabled=false;check((Long)XposedBridge.call(ring,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class,float.class,float.class,float.class},1000L,20f,30f,40f)==4500);
  check((Long)XposedBridge.call(new AmbientLightRingBuffer(),"nextAmbientLightDarkeningTransition",new Class<?>[]{long.class,float.class},1000L,20f)==2000);
  s.hdr=true;check((Long)XposedBridge.call(ring,"nextAmbientLightDarkeningTransition",new Class<?>[]{long.class,float.class},1000L,20f)==2000);s.hdr=false;
  // Low-light thresholds cover normal, small-change and independently scoped assist consumers.
  s.advanced.enabled[0]=false;s.lowLightEnabled=true;lux(main,20);check(main.mAmbientBrighteningThreshold==32&&main.mAmbientBrighteningSmallThreshold==32);check(main.mAmbientDarkeningThreshold==10);check(s.lowLightThresholdAdjustments>0);
  lux(main,.9f);check(Math.abs(main.mAmbientBrighteningThreshold-10.9f)<.0001);check(main.mAmbientBrighteningSmallThreshold==main.mAmbientBrighteningThreshold);check(5<main.mAmbientBrighteningSmallThreshold&&13>main.mAmbientBrighteningThreshold);check(main.mAmbientDarkeningThreshold>0&&main.mAmbientDarkeningThreshold<=.0011);
  lux(main,45);check(main.mAmbientBrighteningThreshold==72&&main.mAmbientBrighteningSmallThreshold==72&&main.mAmbientDarkeningThreshold==27);lux(main,51);check(main.mAmbientBrighteningThreshold==61&&main.mAmbientBrighteningSmallThreshold==53);
  lux(main,20);DualSensorPolicy dual=o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy;dual.h=shared;dual.mAssistAmbientLuxValid=true;dual.mAssistFastAmbientLux=20;XposedBridge.call(dual,"updateDualSensorPolicy",new Class<?>[]{long.class,int.class},0L,0);check(dual.mAssistBrighteningThreshold==32&&dual.mAssistBrighteningSmallThreshold==32);check(AdvancedTuning.thresholdScope.get()==null);
  s.lowLightEnabled=false;AdvancedTuning.refreshThresholds(s);check(main.mAmbientBrighteningThreshold==30&&main.mAmbientBrighteningSmallThreshold==22);check(dual.mAssistBrighteningThreshold==30&&dual.mAssistBrighteningSmallThreshold==22);s.lowLightEnabled=true;
  for(int mode=0;mode<6;mode++){s.hdr=mode==0?Boolean.TRUE:mode==1?null:Boolean.FALSE;s.allowed=mode!=2;s.kernel.auto=mode!=3;main.idle=mode==4;o.mAutomaticBrightnessControllerImpl.driving=mode==5;lux(main,20);check(main.mAmbientBrighteningSmallThreshold==22);}s.hdr=false;s.allowed=s.kernel.auto=true;main.idle=o.mAutomaticBrightnessControllerImpl.driving=false;
  other.mAmbientLux=20;lux(other,20);check(other.mAmbientBrighteningSmallThreshold==22);
  s.lowThresholds=new LowLightThresholds(false,LowLightThresholds.DEFAULT);lux(main,20);check(main.mAmbientBrighteningSmallThreshold==22);s.lowThresholds=new LowLightThresholds();s.advanced.enabled[0]=true;s.advanced.values[0]=.5;s.advanced.values[1]=.5;s.advanced.values[2]=.5;lux(main,20);check(main.mAmbientBrighteningThreshold>=30&&main.mAmbientDarkeningThreshold<=10);
  LowLightThresholds lt=LowLightThresholds.parse(new JSONObject());JSONObject stored=new JSONObject();lt.put(stored);check(!LowLightThresholds.parse(stored).enabled);check(lt.threshold(Float.NaN,4,true)==4);check(lt.threshold(1,Float.POSITIVE_INFINITY,true)==Float.POSITIVE_INFINITY);check(lt.threshold(10,9,true)==9);
  for(int i=0;i<=100;i++){float x=i*.5f;check(lt.threshold(x,x+20,true)>=x+20);check(lt.threshold(x,Math.max(0,x-20),false)<=Math.max(.001f,x-20));float threshold=lt.threshold(x,Math.max(0,x-1),false);check(x==0?threshold==0:threshold>=0&&threshold<x);}
  for(Object invalid:new Object[]{0,4,".6",JSONObject.NULL})try{LowLightThresholds.parse(new JSONObject().put("low_light_brighten_ratio",invalid));throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  try{new LowLightThresholds().verify(true,false);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}lt.verify(false,false);cases++;
  // Bounded main-only gate: uncertainty never turns into a brightness lock.
  LowLightAssistGate gate=new LowLightAssistGate();gate.configure(new JSONObject().put("low_light_assist_gate",true));check(gate.deadline(8000,8500,true,1,15,11,true,5,5,5,6000,7000)==14000);check(gate.deadline(9000,9500,true,1,15,11,true,5,5,5,7000,8000)==14000);check(gate.deadline(14000,14500,true,1,15,11,true,5,5,5,12000,13000)==14500);check(gate.reason.equals("wait_complete"));check(gate.deadline(15000,15500,true,1,15,11,true,5,5,5,13000,14000)==15500);
  gate.reset();check(gate.deadline(8000,8500,true,1,50,11,true,5,5,5,6000,7000)==8500);check(gate.reason.equals("large_main_rise"));gate.reset();check(gate.deadline(8000,8500,true,1,15,11,false,5,5,5,6000,7000)==8500);check(gate.deadline(8000,8500,true,1,15,11,true,0,0,0,6000,7000)==8500);check(gate.deadline(8000,8500,true,1,15,11,true,5,5,5,0,1000)==8500);check(gate.deadline(8000,8500,true,1,15,11,true,5,5,5,7000,7000)==8500);check(gate.deadline(8000,8500,true,1,15,11,true,5,5,5,9000,10000)==8500);
  gate.reset();check(gate.deadline(8000,8500,true,1,15,11,true,5,5,5,6000,7000)==14000);check(gate.deadline(9000,9500,true,1,15,11,true,8,8,8,7000,8000)==9500);check(gate.reason.equals("assist_changed"));gate.reset();check(gate.deadline(8000,8500,true,1,15,11,true,8,5,8,6000,7000)==8500);check(gate.reason.equals("assist_changed"));check(gate.deadline(8000,8500,false,1,15,11,true,5,5,5,6000,7000)==8500);check(gate.deadline(8000,8500,true,1,5,11,true,5,5,5,6000,7000)==8500);gate.enabled=false;check(gate.deadline(8000,8500,true,1,15,11,true,5,5,5,6000,7000)==8500);
  for(Object invalid:new Object[]{500,11000,1500.5,"6000"})try{gate.configure(new JSONObject().put("low_light_assist_wait",invalid));throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  s.advanced.enabled[0]=false;main.mAmbientLightHorizonLong=3000;main.mStepModeDarkenDebounceConfig=0;s.lowLightEnabled=true;s.assistGate.configure(new JSONObject().put("low_light_assist_gate",true));dual.mAssistFastAmbientLux=5;dual.mMainFastAmbientLux=15;lux(main,1);check(LowLightAssistEvidence.supported(s));check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)==14000);dual.mAssistLightSensorRingBuffer.lux=new float[]{0,0};check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},9000L)<14000);dual.mAssistLightSensorRingBuffer.lux=new float[]{5,5};s.assistGate.reset();dual.mUseLightSensorFlag=1;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)<14000);dual.mUseLightSensorFlag=0;
  s.assistGate.reset();dual.mIsPendingResetAssistValue=true;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)<14000);dual.mIsPendingResetAssistValue=false;dual.mAssistLightSensorEnableTime=7800;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)<14000);dual.mAssistLightSensorEnableTime=0;s.hdr=true;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)==9000);s.hdr=false;
  s.assistGate.reset();check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class,float.class},8000L,11f)==14000);s.assistGate.enabled=false;check((Long)XposedBridge.call(main,"nextAmbientLightBrighteningTransition",new Class<?>[]{long.class},8000L)<14000);
  System.out.println("Advanced options/hooks: "+cases+" cases PASS; callback model only, no Android/ART execution");
 }
}'''
}
files=[]
for name,text in sources.items():
 f=O/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,encoding='utf-8');files.append(f)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(f) for f in files],*[str(S/n) for n in ['AdvancedPolicy.java','AdvancedOptions.java','AdvancedTuning.java','DelayPolicy.java','LowLightPolicy.java','LowLightThresholds.java','LowLightAssistGate.java','LowLightAssistEvidence.java','MemoryOptions.java','ResponseTuning.java','LowLightTuning.java']]],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.AdvancedHookTest'],check=True)
