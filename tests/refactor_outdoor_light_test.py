"""Native-input test lease with actual adapter/session/hooks and deterministic service doubles."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/outdoor-light-test';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'com/android/server/display/brightness/BrightnessReason.java':'package com.android.server.display.brightness;public class BrightnessReason{}',
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now;public static long elapsedRealtime(){return now;}public static long uptimeMillis(){return now;}}',
'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler{public static final Object LOOP=new Object();public Object loop=LOOP;public boolean reject;public Map<Runnable,Long> jobs=new IdentityHashMap<>();public Object getLooper(){return loop;}public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long delay){if(reject)return false;jobs.put(r,SystemClock.now+delay);return true;}public void removeCallbacks(Runnable r){jobs.remove(r);}public void advance(long time){SystemClock.now=time;for(int i=0;i<100;i++){Runnable due=null;for(Map.Entry<Runnable,Long> e:jobs.entrySet())if(e.getValue()<=time){due=e.getKey();break;}if(due==null)return;jobs.remove(due);due.run();}throw new AssertionError("timer loop");}}''',
'top/rongshangs/lumacurve/refactor/hooking/XposedBridge.java':'''package top.rongshangs.lumacurve.refactor.hooking;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.Callable;
public class XposedBridge{static final Map<Method,List<XC_MethodHook>> hooks=new HashMap<>();public static int installed,failAt,removeFailures;public static int count(){int n=0;for(List<?> h:hooks.values())n+=h.size();return n;}public static void clear(){hooks.clear();installed=failAt=removeFailures=0;}public static void log(String s){}
public static XC_MethodHook.Unhook hookMethod(Method m,XC_MethodHook h){if(++installed==failAt)throw new IllegalStateException("install failed");hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);return new XC_MethodHook.Unhook(()->{if(removeFailures>0){removeFailures--;throw new IllegalStateException("remove failed");}hooks.get(m).remove(h);});}
public static Object call(Object owner,String name,Class<?>[] types,Object[] args,Callable<Object> nativeBody){try{Method m=owner.getClass().getDeclaredMethod(name,types);List<XC_MethodHook> list=new ArrayList<>(hooks.getOrDefault(m,Collections.emptyList()));XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=owner;p.method=m;p.args=args;for(XC_MethodHook h:list){h.before(p);if(p.returnEarly())break;}if(!p.returnEarly())try{p.outcome(nativeBody.call(),null);}catch(Throwable failure){p.outcome(null,failure);}for(int i=list.size()-1;i>=0;i--)list.get(i).after(p);if(p.hasThrowable())throw p.getThrowable();return p.getResult();}catch(Throwable failure){throw new IllegalStateException(failure);}}
}''',
'top/rongshangs/lumacurve/refactor/SessionTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import android.os.*;import org.json.*;import top.rongshangs.lumacurve.refactor.hooking.*;
class HookEntry{static Field field(Class<?> c,String name)throws Exception{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}static Object get(Object o,String name)throws Exception{return field(o.getClass(),name).get(o);}}
class BrightnessSensorAccess{static class Selection{Object impl,controller;}static Selection selection(Object o)throws Exception{HookRuntime.Owner owner=(HookRuntime.Owner)o;Selection s=new Selection();s.impl=owner.mAutomaticBrightnessControllerImpl;s.controller=owner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController;return s;}static Object optional(Object o,String name)throws Exception{try{return HookEntry.get(o,name);}catch(NoSuchFieldException absent){return null;}}static Method method(Object o,String name,Class<?> returns,Class<?>... types)throws Exception{Method m=o.getClass().getMethod(name,types);if(m.getReturnType()!=returns||Modifier.isStatic(m.getModifiers()))throw new IllegalArgumentException();m.setAccessible(true);return m;}}
class HbmAccess{HbmAccess(Object o){if(o==null)throw new IllegalArgumentException();}static Method sdrMethod(Class<?> c,Class<?> reason)throws Exception{return c.getDeclaredMethod("sdr",float.class,boolean.class);}}
class OutdoorSdrScope{static OutdoorSdrScope resolve(Class<?> c){return new OutdoorSdrScope();}boolean allows(Object o){return !((HookRuntime.Owner)o).privacy;}}
class OutdoorOutputAccess{static long physical(HookRuntime s){return s.physical;}}
class OutdoorOptions{boolean nodeBoost;}
class Outdoor{OutdoorOptions options=new OutdoorOptions();String block;Policy policy=new Policy();String block(){return block;}String testBlock(){return "disabled".equals(block)?null:block;}void tick(){}}
class Policy{boolean active;long enterAt=-1,exitAt=-1,started=-1,cooldownUntil,manualAt=-1,next=-1;float manualLux=Float.NaN;String reason="disabled";int resets;void reset(){resets++;active=false;}}
class BrightnessControl{boolean independent,unlocked=true;boolean independentActive(){return independent;}boolean unlocked(){return unlocked;}}
class ScreenBrightnessReading{static void capture(Object owner,JSONObject j){HookRuntime.Owner o=(HookRuntime.Owner)owner;j.put("actual_nit",o.actualNit).put("actual_nit_source","device_backlight_mapping");}}
class HookRuntime{
 final Handler handler=new Handler();final Owner owner=new Owner();final Outdoor outdoor=new Outdoor();final BrightnessControl brightnessControl=new BrightnessControl();final OutdoorLightTest test=new OutdoorLightTest(this);boolean closed,thread=true,user=true;String phase="active";long physical=1;int recalcs,publishes;
 boolean onDisplayThread(){return thread;}boolean appliesToUser(){return user;}void requestRecalculation(){recalcs++;}void queuePublish(){publishes++;}
 public static class Range{public float value=123;void onAmbientLuxChange(float v){value=v;}}
 public static class Impl{public Object mAutomaticBrightnessController=new ABC();public float value=123;public void onAmbientLuxChange(float v){value=v;}}
 public static class Owner{public Object mHbmController=new Object();public Impl mAutomaticBrightnessControllerImpl=new Impl();double actualNit=500;boolean privacy;public float sdr(float value,boolean automatic){Object[] args={value,automatic};return (Float)XposedBridge.call(this,"sdr",new Class[]{float.class,boolean.class},args,()->(Float)args[0]);}}
}
class ABC {
 public Handler mHandler=new Handler();public float mAmbientLux=123,mAmbientBrighteningThreshold=150,mAmbientDarkeningThreshold=100,mAmbientBrighteningSmallThreshold=140,mPreThresholdLux=100,rawLux=123,anchor=-1;
 public boolean mAmbientLuxValid=true,mLightSensorEnabled=true,mSupportDualSensorPolicy=true,failSetter,failUpdate;public HookRuntime.Range mBrightnessRangeController=new HookRuntime.Range();public int learned=8,resets;public float calculated,mRawScreenAutoBrightness=.3f;
 private void setAmbientLux(float value){Object[] a={value};XposedBridge.call(this,"setAmbientLux",new Class[]{float.class},a,()->{if(failSetter)throw new IllegalStateException("setter damaged");mAmbientLux=(Float)a[0];mAmbientBrighteningThreshold=mAmbientLux*1.2f;mAmbientDarkeningThreshold=mAmbientLux*.8f;mAmbientBrighteningSmallThreshold=mAmbientLux*1.1f;mBrightnessRangeController.onAmbientLuxChange(mAmbientLux);resetShortTermModelIfNeeded();return null;});}
 void setAmbientLux(int source,float value,boolean changed,boolean update){Object[] a={source,value,changed,update};XposedBridge.call(this,"setAmbientLux",new Class[]{int.class,float.class,boolean.class,boolean.class},a,()->{mPreThresholdLux=mAmbientLux;mAmbientLux=(Float)a[1];if((Boolean)a[3])updateAutoBrightness(true,false);return null;});}
 private void updateAutoBrightness(boolean send,boolean manual){Object[] a={send,manual};XposedBridge.call(this,"updateAutoBrightness",new Class[]{boolean.class,boolean.class},a,()->{if(failUpdate)throw new IllegalStateException("update damaged");calculated=mAmbientLux;mRawScreenAutoBrightness=mAmbientLux/100000;return null;});}
 private void updateAmbientLux(){XposedBridge.call(this,"updateAmbientLux",new Class[]{},new Object[]{},()->{setAmbientLux(rawLux);return null;});}
 public void resetShortTermModel(){XposedBridge.call(this,"resetShortTermModel",new Class[]{},new Object[]{},()->{resets++;learned=0;return null;});}
 private void resetShortTermModelIfNeeded(){XposedBridge.call(this,"resetShortTermModelIfNeeded",new Class[]{},new Object[]{},()->{if(mAmbientLux>50000)resetShortTermModel();return null;});}
 private boolean setScreenBrightnessByUser(float value){Object[] a={value};return (Boolean)XposedBridge.call(this,"setScreenBrightnessByUser",new Class[]{float.class},a,()->setScreenBrightnessByUser(mAmbientLux,(Float)a[0]));}
 private boolean setScreenBrightnessByUser(float lux,float value){Object[] a={lux,value};return (Boolean)XposedBridge.call(this,"setScreenBrightnessByUser",new Class[]{float.class,float.class},a,()->{anchor=(Float)a[0];learned++;return true;});}
 void real(float value){rawLux=value;setAmbientLux(1,value,true,true);}void manual(boolean direct){if(direct)setScreenBrightnessByUser(mAmbientLux,.7f);else setScreenBrightnessByUser(.7f);}
}
class PublicRange extends HookRuntime.Range{public void onAmbientLuxChange(float v){super.onAmbientLuxChange(v);}}
class InheritedRange extends HookRuntime.Range{}
interface DefaultNotifier{default float nativeNotice(float value){return value;}}
class InterfaceNotifier implements DefaultNotifier{}
class Wrong extends ABC{public String mAmbientLux="wrong";}
public class SessionTest {
 static int cases;static void check(boolean value){if(!value)throw new AssertionError("outdoor test case "+cases);cases++;}
 static HookRuntime host(){XposedBridge.clear();SystemClock.now=0;return new HookRuntime();}static ABC abc(HookRuntime s){return (ABC)s.owner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController;}
 static void failStart(HookRuntime s)throws Exception{try{s.test.start("test");throw new AssertionError("unexpected start");}catch(IllegalStateException expected){cases++;}check(!s.test.active&&XposedBridge.count()==0);}
 public static void main(String[] args)throws Exception{
  HookRuntime s=host();check(OutdoorLightTestAccess.supported(s));check(!s.test.active&&XposedBridge.count()==0&&s.handler.jobs.isEmpty());int learned=abc(s).learned;s.test.start("test");check(s.test.active&&abc(s).mAmbientLux==123&&abc(s).rawLux==123&&abc(s).calculated==100000);check(abc(s).learned==learned&&abc(s).resets==0);check(s.test.status().getLong("remaining_ms")==20000);
  abc(s).real(250);check(abc(s).mAmbientLux==250&&abc(s).rawLux==250&&s.test.realLux==250);check(abc(s).calculated==100000&&abc(s).learned==learned);
  s.test.observe(new JSONObject().put("actual_nit",1800).put("actual_nit_source","device_backlight_mapping"));s.test.observe(new JSONObject().put("actual_nit",9000).put("actual_nit_source","node_readback_device_calibration"));check(s.test.peak==1800);s.owner.actualNit=1900;s.handler.advance(19999);check(s.test.active);s.handler.advance(20000);check(!s.test.active&&s.test.reason.equals("timeout"));check(abc(s).mAmbientLux==250&&abc(s).mBrightnessRangeController.value==250&&abc(s).calculated==250);check(abc(s).learned==learned&&XposedBridge.count()==0&&s.handler.jobs.isEmpty());check(s.test.peak==1900);check(abc(s).mPreThresholdLux!=100000);
  for(boolean direct:new boolean[]{false,true}){s=host();s.test.start("manual");abc(s).real(400);abc(s).manual(direct);check(!s.test.active&&s.test.reason.equals("manual"));check(abc(s).anchor==400&&abc(s).learned==9&&XposedBridge.count()==0);}
  for(String reason:new String[]{"temperature","hdr","manual_mode","screen_off","output_override","power_save"}){s=host();s.test.start("guard");s.outdoor.block=reason;s.test.check();check(!s.test.active&&s.test.reason.equals(reason));check(abc(s).mAmbientLux==123&&XposedBridge.count()==0&&abc(s).learned==8);}
  s=host();s.outdoor.options.nodeBoost=true;failStart(s);s=host();s.brightnessControl.unlocked=false;failStart(s);s=host();s.brightnessControl.independent=true;failStart(s);s=host();s.user=false;failStart(s);s=host();s.thread=false;failStart(s);s=host();abc(s).mAmbientLuxValid=false;failStart(s);s=host();abc(s).mLightSensorEnabled=false;failStart(s);
  s=host();abc(s).mHandler.loop=new Object();check(!OutdoorLightTestAccess.supported(s));s=host();s.physical=0;check(!OutdoorLightTestAccess.supported(s));s=host();s.owner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController=new Wrong();check(!OutdoorLightTestAccess.supported(s));
  for(int fail=1;fail<=8;fail++){s=host();XposedBridge.failAt=fail;failStart(s);check(abc(s).mAmbientLux==123&&abc(s).learned==8);check(s.test.status().getString("profile").equals("unconfirmed"));}
  s=host();s.handler.reject=true;failStart(s);check(abc(s).mAmbientLux==123);s=host();abc(s).failUpdate=true;failStart(s);check(abc(s).mAmbientLux==123&&abc(s).learned==8);
  s=host();s.test.start("damaged");abc(s).failSetter=true;s.test.stop("user_stop");check(!s.test.active&&abc(s).mAmbientLux==123&&s.test.error.isEmpty());check(abc(s).mBrightnessRangeController.value==123&&XposedBridge.count()==0);
  s=host();s.test.start("remove");XposedBridge.removeFailures=2;s.test.stop("user_stop");check(!s.test.active&&abc(s).mAmbientLux==123&&!s.test.hooks.isEmpty());s.handler.advance(250);check(s.test.hooks.isEmpty()&&XposedBridge.count()==0);
  s=host();ABC old=abc(s);s.test.start("display");s.physical=2;s.owner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController=new ABC();s.test.check();check(!s.test.active&&old.mAmbientLux==123&&abc(s).mAmbientLux==123&&XposedBridge.count()==0);
  s=host();s.test.start("id");try{s.test.stopFor("old");throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}check(s.test.active);s.test.stopFor("id");check(!s.test.active);long until=s.test.until;s.handler.advance(50000);check(s.test.until==until&&XposedBridge.count()==0);
  s=host();s.test.start("thread");s.thread=false;s.test.check();check(s.test.active&&s.test.stopQueued);s.thread=true;s.handler.advance(0);check(!s.test.active&&s.test.reason.equals("thread_changed")&&abc(s).mAmbientLux==123);
  s=host();s.outdoor.policy.active=true;s.outdoor.policy.started=-5000;s.outdoor.policy.cooldownUntil=50000;s.test.start("budget");s.outdoor.policy.started=100;s.outdoor.policy.cooldownUntil=70000;s.test.stop("user_stop");check(s.outdoor.policy.active&&s.outdoor.policy.started==-5000&&s.outdoor.policy.cooldownUntil==50000);
  for(boolean automatic:new boolean[]{false,true}){s=host();s.test.start("frame");s.owner.privacy=automatic;s.owner.sdr(1,automatic);check(!s.test.active&&s.test.reason.equals("protected_frame")&&abc(s).mAmbientLux==123);}
  s=host();s.test.start("ownership");abc(s).mAmbientLux=777;abc(s).mPreThresholdLux=700;s.test.stop("user_stop");check(abc(s).mAmbientLux==777&&abc(s).calculated==777&&abc(s).mPreThresholdLux==700);
  s=host();s.test.start("ratio");s.test.observe(new JSONObject().put("actual_brightness",.8).put("actual_brightness_source","display_power_state"));check(s.test.status().getDouble("recorded_peak_brightness")==.8);s.test.stop("user_stop");
  s=host();s.test.start("bypass");abc(s).mRawScreenAutoBrightness=.2f;s.owner.sdr(1,true);check(!s.test.active&&s.test.reason.equals("input_route_changed")&&abc(s).mAmbientLux==123);
  s=host();abc(s).failSetter=true;s.test.start("no_fake_setter");check(s.test.active&&abc(s).mAmbientLux==123&&abc(s).calculated==100000&&abc(s).learned==8);s.test.stop("user_stop");check(abc(s).mAmbientLux==123&&abc(s).calculated==123&&s.test.error.isEmpty());
  s=host();s.outdoor.block="disabled";s.test.start("native_baseline");check(s.test.active&&abc(s).calculated==100000&&abc(s).mAmbientLux==123);abc(s).anchor=abc(s).mAmbientLux;check(abc(s).anchor==123);s.test.stop("user_stop");
  s=host();s.test.start("threshold");abc(s).real(300);check(abc(s).mPreThresholdLux==123&&abc(s).mAmbientBrighteningThreshold==150&&abc(s).mAmbientLux==300);abc(s).resetShortTermModel();check(abc(s).learned==0);s.test.stop("user_stop");
  check(((Float)OutdoorLightTestAccess.method(InterfaceNotifier.class,"nativeNotice",float.class,float.class).invoke(new InterfaceNotifier(),7f))==7f);
  s=host();abc(s).mBrightnessRangeController=new PublicRange();check(OutdoorLightTestAccess.checked(s));s.test.start("public-notify");check(s.test.active&&abc(s).mBrightnessRangeController.value==100000);s.test.stop("user_stop");check(abc(s).mBrightnessRangeController.value==123);
  s=host();abc(s).mBrightnessRangeController=new InheritedRange();check(OutdoorLightTestAccess.checked(s));s.test.start("inherited-notify");check(s.test.active&&abc(s).mBrightnessRangeController.value==100000);s.test.stop("user_stop");check(abc(s).mBrightnessRangeController.value==123);
  System.out.println("Outdoor light test: "+cases+" cases PASS; native input lease model, no device execution");
 }
}'''
}
paths=[]
for name,body in files.items():p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,'utf-8');paths.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
production=[S/'OutdoorLightTestAccess.java',S/'OutdoorLightTest.java',S/'FieldOverride.java',S/'hooking/XC_MethodHook.java',S/'hooking/HookTransaction.java']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(p) for p in production+paths]],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(C),str(J)]),'top.rongshangs.lumacurve.refactor.SessionTest'],check=True)
