"""Actual scene callbacks and lifecycle with explicit OEM/Looper doubles, never a device test."""
from pathlib import Path
import ast,subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-scene-hook-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
tree=ast.parse((R/'tests/refactor_advanced_hooks.py').read_text(encoding='utf-8'))
common=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
sources={k:common[k] for k in ['de/robv/android/xposed/XC_MethodHook.java','de/robv/android/xposed/XposedBridge.java','top/rongshangs/lumacurve/refactor/HookEntry.java']}
sources.update({
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now=1000;public static long uptimeMillis(){return now;}}',
'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler{public final Map<Runnable,Long> pending=new IdentityHashMap<>();public boolean post(Runnable r){pending.put(r,0L);return true;}public boolean postDelayed(Runnable r,long delay){pending.put(r,delay);return true;}public void removeCallbacks(Runnable r){pending.remove(r);}}''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;import org.json.*;import android.os.*;class HookRuntime{
 final Object owner;final Handler handler=new Handler();final SceneController scenes;boolean closed,probingScenes,display=true,user=true,on=true;int recalculations;
 HookRuntime(Object owner){this.owner=owner;scenes=new SceneController(this);}boolean onDisplayThread(){return !closed&&display;}boolean appliesToUser(){return user;}boolean screenOn(){return on;}void requestRecalculation(){recalculations++;}
 static float optionalNumber(Object o,String f){try{return ((Number)HookEntry.get(o,f)).floatValue();}catch(Exception e){return Float.NaN;}}static Boolean optionalBoolean(Object o,String f){try{return (Boolean)HookEntry.get(o,f);}catch(Exception e){return null;}}
 static void readBoolean(JSONObject j,String key,Object o,String f){try{Boolean b=optionalBoolean(o,f);if(b!=null)j.put(key,b);}catch(Exception e){}}static void readNumber(JSONObject j,String key,Object o,String f){try{float v=optionalNumber(o,f);if(Float.isFinite(v)&&v>=0)j.put(key,v);}catch(Exception e){}}
}''',
'com/android/server/display/SceneDetector.java':'''package com.android.server.display;import de.robv.android.xposed.*;public class SceneDetector{
 public boolean mUseDrivingModeForBrt=true,mTransientProximityEnabled=true,mUseAonFlareEnabled=true,mNeedCheckProximitySensor=true,mNeedCheckAonFlare=true,mIsNightDrivingMode=true,mProximityStatus=true,throwing;
 public int mProxState=8;public float mMinAonFlareEnableLux=17,mMaxAonFlareEnableLux=200;public boolean sawRear,sawAon;public int applies;
 private boolean isNightTime(){return true;}private void applyNightDrivingBrightness(){applies++;mIsNightDrivingMode=(Boolean)XposedBridge.call(this,"isNightTime",new Class<?>[0]);}
 public void updateAmbientLux(int event,float lux,boolean dark){sawRear=mTransientProximityEnabled;sawAon=lux>=mMinAonFlareEnableLux&&lux<=mMaxAonFlareEnableLux;if(throwing)throw new IllegalStateException("OEM failure");}public boolean isNeedSkipBrightnessChange(){return mNeedCheckProximitySensor||mNeedCheckAonFlare;}public boolean isReflectiveScene(){return true;}
}''',
'com/android/server/display/AutomaticBrightnessController.java':'''package com.android.server.display;public class AutomaticBrightnessController{public long mStepModeDarkenDebounceConfig=3000;public boolean mIsNightWakeMode=true,mSetNightWakeBrightness=true,mLightSensorEnabled=true,mAmbientLuxValid=true;public float mAmbientLux=20;public void updateStepModeDarkenDebounceTime(long ms){mStepModeDarkenDebounceConfig=ms;}public void updateNightWakeMode(boolean active,boolean target){mIsNightWakeMode=active;mSetNightWakeBrightness=target;}}''',
'com/android/server/display/AutomaticBrightnessControllerImpl.java':'''package com.android.server.display;public class AutomaticBrightnessControllerImpl{public boolean mUseProximityEnabled=true,mIsGameSceneEnable=true,mProximitySensorEnabled=true,mProximityPositive=true,throwing;public Object mDualSensorPolicy=new Dual();public AutomaticBrightnessController mAutomaticBrightnessController=new AutomaticBrightnessController();public SceneDetector mSceneDetector=new SceneDetector();public TouchCoverProtectionHelper mTouchAreaHelper=new TouchCoverProtectionHelper();public boolean dropAmbientLuxIfNeeded(){if(throwing)throw new IllegalStateException("OEM failure");return mUseProximityEnabled;}public boolean dropDecreaseLuxIfNeeded(){if(throwing)throw new IllegalStateException("OEM failure");return mIsGameSceneEnable;}public static class Dual{public float mMainFastAmbientLux=10,mAssistFastAmbientLux=30;public boolean mAssistLightSensorEnable=true,mAssistAmbientLuxValid=true;}}''',
'com/android/server/display/TouchCoverProtectionHelper.java':'''package com.android.server.display;public class TouchCoverProtectionHelper{public boolean active=true,mTouchTrackingEnabled=true;protected boolean isTouchCoverProtectionActive(){return active;}}''',
'com/android/server/display/SunlightController.java':'''package com.android.server.display;public class SunlightController{public boolean mSunlightModeActive=true,mSunlightSensorEnabled=true,mScreenOn=true,mAutoBrightnessSettingsEnable=false;public int updates;private void setSunLightModeActive(boolean active){mSunlightModeActive=active;}private void updateAmbientLux(){updates++;}}'''
})
files=[]
for name,code in sources.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');files.append(p)
p=O/'SceneHookTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import com.android.server.display.*;import de.robv.android.xposed.*;import android.os.*;
class Calendar extends java.util.GregorianCalendar{static int seconds,millis;public static Calendar getInstance(){Calendar c=new Calendar();c.set(SECOND,seconds);c.set(MILLISECOND,millis);return c;}}
public class SceneHookTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static class Owner{AutomaticBrightnessControllerImpl mAutomaticBrightnessControllerImpl=new AutomaticBrightnessControllerImpl();SunlightController mSunlightController=new SunlightController();}
 static Object call(Object o,String name,Class<?>[] args,Object...values){return XposedBridge.call(o,name,args,values);}
 static boolean scheduled(HookRuntime s,long deadline){Long delay=s.handler.pending.get(s.scenes.timer);return s.scenes.policy.next==deadline&&delay!=null&&delay>0&&delay<=deadline-SystemClock.now;}
 static void block(HookRuntime s,int...ids)throws Exception{JSONObject policies=new JSONObject();for(int i:ids)policies.put(SceneOptions.IDS[i],new JSONObject().put("mode","block"));s.scenes.configure(SceneOptions.parse(new JSONObject().put("scene_options",new JSONObject().put("policies",policies))));}
 public static void main(String[] args)throws Exception{
  SceneTuning.install(SceneHookTest.class.getClassLoader());Owner o=new Owner();HookRuntime s=new HookRuntime(o);HookEntry.states.put(o,s);for(int i=0;i<9;i++)check(SceneTuning.supported(s,i));SceneTuning.install(SceneHookTest.class.getClassLoader());
  AutomaticBrightnessControllerImpl impl=o.mAutomaticBrightnessControllerImpl;SceneDetector scene=impl.mSceneDetector;AutomaticBrightnessController abc=impl.mAutomaticBrightnessController;SunlightController sun=o.mSunlightController;
  int initial=scene.applies;s.scenes.configure(new SceneOptions());check(scene.applies==initial&&s.handler.pending.isEmpty());
  block(s,0);check(!scene.mIsNightDrivingMode);check(!(Boolean)call(scene,"isNightTime",new Class<?>[0]));s.display=false;check(!(Boolean)call(scene,"isNightTime",new Class<?>[0]));s.display=true;s.scenes.stop();check(scene.mIsNightDrivingMode);check((Boolean)call(scene,"isNightTime",new Class<?>[0]));check(!s.scenes.enabled&&s.handler.pending.get(s.scenes.timer)==null);
  block(s,1,6);check(abc.mStepModeDarkenDebounceConfig==0&&!abc.mIsNightWakeMode&&!abc.mSetNightWakeBrightness);call(abc,"updateStepModeDarkenDebounceTime",new Class<?>[]{long.class},6000L);check(abc.mStepModeDarkenDebounceConfig==0&&s.scenes.stepOriginal==6000);call(abc,"updateNightWakeMode",new Class<?>[]{boolean.class,boolean.class},true,false);check(!abc.mIsNightWakeMode&&!abc.mSetNightWakeBrightness);s.scenes.stop();check(abc.mStepModeDarkenDebounceConfig==6000&&abc.mIsNightWakeMode&&!abc.mSetNightWakeBrightness);
  block(s,2);check(!(Boolean)call(impl.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));s.probingScenes=true;check((Boolean)call(impl.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));s.probingScenes=false;check(impl.mTouchAreaHelper.active);s.scenes.stop();check((Boolean)call(impl.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));
  for(int id:new int[]{3,8}){block(s,id);String method=id==3?"dropAmbientLuxIfNeeded":"dropDecreaseLuxIfNeeded";check(!(Boolean)call(impl,method,new Class<?>[0]));check(impl.mUseProximityEnabled&&impl.mIsGameSceneEnable);impl.throwing=true;try{call(impl,method,new Class<?>[0]);throw new AssertionError("exception lost");}catch(RuntimeException expected){cases++;}check(impl.mUseProximityEnabled&&impl.mIsGameSceneEnable);impl.throwing=false;s.display=false;check((Boolean)call(impl,method,new Class<?>[0]));s.display=true;s.scenes.stop();}
  for(int[] ids:new int[][]{{4},{5},{4,5}}){block(s,ids);call(scene,"updateAmbientLux",new Class<?>[]{int.class,float.class,boolean.class},0,50f,true);boolean rear=ids.length==1&&ids[0]==5,aon=ids.length==1&&ids[0]==4;check(scene.sawRear==rear&&scene.sawAon==aon);check(scene.mTransientProximityEnabled&&scene.mMinAonFlareEnableLux==17&&scene.mMaxAonFlareEnableLux==200);check((Boolean)call(scene,"isNeedSkipBrightnessChange",new Class<?>[0])==(ids.length==1));scene.throwing=true;try{call(scene,"updateAmbientLux",new Class<?>[]{int.class,float.class,boolean.class},0,50f,true);throw new AssertionError("exception lost");}catch(RuntimeException expected){cases++;}scene.throwing=false;check(scene.mTransientProximityEnabled&&scene.mMinAonFlareEnableLux==17&&scene.mMaxAonFlareEnableLux==200);s.scenes.stop();}
  block(s,5);check(!(Boolean)call(scene,"isReflectiveScene",new Class<?>[0]));scene.mNeedCheckProximitySensor=false;check(!(Boolean)call(scene,"isNeedSkipBrightnessChange",new Class<?>[0]));scene.mNeedCheckProximitySensor=true;s.scenes.stop();
  block(s,7);check(!sun.mSunlightModeActive);call(sun,"setSunLightModeActive",new Class<?>[]{boolean.class},true);check(!sun.mSunlightModeActive);int updates=sun.updates;s.scenes.stop();check(sun.updates==updates+1);sun.mSunlightSensorEnabled=false;block(s,7);updates=sun.updates;s.scenes.stop();check(sun.updates==updates);
  // Separate instance and non-display threads cannot change primary input-consumer results.
  block(s,2,3);AutomaticBrightnessControllerImpl other=new AutomaticBrightnessControllerImpl();check((Boolean)call(other,"dropAmbientLuxIfNeeded",new Class<?>[0]));check((Boolean)call(other.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));s.display=false;check((Boolean)call(impl,"dropAmbientLuxIfNeeded",new Class<?>[0]));s.display=true;s.user=false;s.scenes.refresh();check((Boolean)call(impl,"dropAmbientLuxIfNeeded",new Class<?>[0]));s.user=true;s.scenes.stop();
  JSONObject facts=s.scenes.facts();check(facts.getDouble("effective")==20&&facts.getDouble("main")==10&&facts.getDouble("assist")==30);check(facts.getBoolean("proximity_near")&&facts.getBoolean("stream_near"));s.on=false;check(!s.scenes.facts().has("effective"));s.on=true;
  block(s,0);JSONObject status=new JSONObject();s.scenes.put(status);check(status.getJSONObject("scene_control").getJSONObject("denied").getBoolean("night_driving"));check(status.getBoolean("scene_night_driving_supported"));check(s.handler.pending.containsKey(s.scenes.timer));s.scenes.stop();check(!s.handler.pending.containsKey(s.scenes.timer));

  // Actual display-thread scheduling uses separate delays in both directions.
  JSONObject conditional=new JSONObject().put("scene_options",new JSONObject().put("policies",new JSONObject().put("touch",new JSONObject().put("mode","condition").put("condition",new JSONObject().put("lux",true).put("max",50).put("confirm_enter",300).put("confirm_exit",900)))));
  for(int[] wall:new int[][]{{0,0},{59,950}}){Calendar.seconds=wall[0];Calendar.millis=wall[1];
  SystemClock.now=1000;s.scenes.configure(SceneOptions.parse(conditional));check(s.scenes.denied[2]&&scheduled(s,1300));check(!(Boolean)call(impl.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));
  SystemClock.now=1300;s.scenes.timer.run();check(!s.scenes.denied[2]);check((Boolean)call(impl.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));
  abc.mAmbientLux=100;s.scenes.refresh();check(!s.scenes.denied[2]&&scheduled(s,2200));SystemClock.now=2199;s.scenes.timer.run();check(!s.scenes.denied[2]);SystemClock.now=2200;s.scenes.timer.run();check(s.scenes.denied[2]);
  abc.mAmbientLuxValid=false;s.scenes.refresh();check(!s.scenes.denied[2]);abc.mAmbientLuxValid=true;abc.mAmbientLux=20;SystemClock.now=2300;s.scenes.refresh();check(s.scenes.denied[2]&&scheduled(s,2600));
  SystemClock.now=2600;s.scenes.timer.run();impl.mTouchAreaHelper.active=false;check(!(Boolean)call(impl.mTouchAreaHelper,"isTouchCoverProtectionActive",new Class<?>[0]));impl.mTouchAreaHelper.active=true;s.scenes.stop();SystemClock.now=1000;check(!s.handler.pending.containsKey(s.scenes.timer));
  }
  System.out.println("Scene hooks/lifecycle: "+cases+" cases PASS; actual callbacks with OEM and Looper doubles, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in ['SceneOptions','ScenePolicy','SceneController','SceneTuning']],*[str(f) for f in files],str(p)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.SceneHookTest'],check=True)
