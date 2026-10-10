package top.rongshangs.lumacurve.refactor;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Per-primary-display admission, evaluated on its Looper; no new sensor registrations. */
final class SceneController {
    final HookRuntime s;final ScenePolicy policy=new ScenePolicy();SceneOptions options=new SceneOptions();
    volatile boolean[] denied=new boolean[SceneOptions.IDS.length];volatile boolean enabled;
    boolean evaluating,reconciling;long stepOriginal=-1;boolean[] wakeOriginal;String error="";long next=-1;
    final Runnable timer;
    SceneController(HookRuntime s){this.s=s;timer=()->{next=-1;if(enabled&&!s.closed){refresh();reconcile();}};}
    void configure(SceneOptions nextOptions){boolean wasEnabled=enabled;s.handler.removeCallbacks(timer);next=-1;options=nextOptions;policy.reset();enabled=options.custom();denied=new boolean[SceneOptions.IDS.length];if(enabled||wasEnabled){try{Object abc=SceneTuning.object(s,"abc");if(stepOriginal<0){float value=HookRuntime.optionalNumber(abc,"mStepModeDarkenDebounceConfig");if(Float.isFinite(value)&&value>=0)stepOriginal=(long)value;}if(wakeOriginal==null){Boolean mode=HookRuntime.optionalBoolean(abc,"mIsNightWakeMode"),target=HookRuntime.optionalBoolean(abc,"mSetNightWakeBrightness");if(mode!=null&&target!=null)wakeOriginal=new boolean[]{mode,target};}}catch(Throwable absent){}refresh();reconcile();}}
    void stop(){boolean wasEnabled=enabled;s.handler.removeCallbacks(timer);next=-1;enabled=false;options=new SceneOptions();policy.reset();denied=new boolean[SceneOptions.IDS.length];if(wasEnabled)reconcile();}
    boolean blocked(int i){if(s.onDisplayThread())refresh();return enabled&&denied[i];}
    JSONObject facts(){JSONObject f=new JSONObject();try{
        Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController"),dual=HookEntry.get(impl,"mDualSensorPolicy"),scene=HookEntry.get(impl,"mSceneDetector");
        if(s.screenOn()&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(abc,"mLightSensorEnabled"))&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(abc,"mAmbientLuxValid"))){putNumber(f,"effective",abc,"mAmbientLux");putNumber(f,"main",dual,"mMainFastAmbientLux");if(Boolean.TRUE.equals(HookRuntime.optionalBoolean(dual,"mAssistLightSensorEnable"))&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(dual,"mAssistAmbientLuxValid")))putNumber(f,"assist",dual,"mAssistFastAmbientLux");}
        if(s.screenOn()&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(impl,"mProximitySensorEnabled")))HookRuntime.readBoolean(f,"proximity_near",impl,"mProximityPositive");
        // Stream proximity is a latched classifier; require its live acquisition state.
        if(s.screenOn()&&HookRuntime.optionalNumber(scene,"mProxState")==8)HookRuntime.readBoolean(f,"stream_near",scene,"mProximityStatus");
        Object touch=HookEntry.get(impl,"mTouchAreaHelper");if(!s.screenOn()||!Boolean.TRUE.equals(HookRuntime.optionalBoolean(touch,"mTouchTrackingEnabled")))return f;Method m=touch.getClass().getDeclaredMethod("isTouchCoverProtectionActive");m.setAccessible(true);Object active=de.robv.android.xposed.XposedBridge.invokeOriginalMethod(m,touch,new Object[0]);if(active instanceof Boolean)f.put("touch",active);
    }catch(Throwable unavailable){}return f;}
    static void putNumber(JSONObject f,String key,Object object,String field){HookRuntime.readNumber(f,key,object,field);}
    void refresh(){if(!enabled||evaluating||!s.onDisplayThread())return;evaluating=true;try{
        if(!s.appliesToUser()){denied=new boolean[SceneOptions.IDS.length];policy.reset();schedule(SystemClock.uptimeMillis()+60000);return;}
        Calendar clock=Calendar.getInstance();long now=SystemClock.uptimeMillis();boolean[] before=denied;denied=policy.evaluate(options,facts(),clock.get(Calendar.HOUR_OF_DAY)*60+clock.get(Calendar.MINUTE),now);
        // Minute boundaries and in-flight confirmations get one bounded callback, no polling loop.
        long at=now+60000-clock.get(Calendar.SECOND)*1000-clock.get(Calendar.MILLISECOND);if(policy.next>=0)at=Math.min(at,policy.next);schedule(Math.max(now+1,at));
        if(!Arrays.equals(before,denied)&&!reconciling)s.handler.post(()->{if(!s.closed)reconcile();});
    }finally{evaluating=false;}}
    void schedule(long at){if(next==at)return;s.handler.removeCallbacks(timer);next=at;s.handler.postDelayed(timer,Math.max(1,at-SystemClock.uptimeMillis()));}
    void reconcile(){if(reconciling||!s.onDisplayThread())return;reconciling=true;try{
        Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController"),scene=HookEntry.get(impl,"mSceneDetector");
        if(SceneTuning.supported(s,0))call(scene,"applyNightDrivingBrightness",new Class<?>[0]);
        if(stepOriginal>=0&&SceneTuning.supported(s,1))call(abc,"updateStepModeDarkenDebounceTime",new Class<?>[]{long.class},enabled&&denied[1]?0L:stepOriginal);
        if(wakeOriginal!=null&&SceneTuning.supported(s,6))call(abc,"updateNightWakeMode",new Class<?>[]{boolean.class,boolean.class},enabled&&denied[6]?false:wakeOriginal[0],enabled&&denied[6]?false:wakeOriginal[1]);
        if(SceneTuning.supported(s,7)){Object sun=HookEntry.get(s.owner,"mSunlightController");if(enabled&&denied[7])call(sun,"setSunLightModeActive",new Class<?>[]{boolean.class},false);else if(Boolean.TRUE.equals(HookRuntime.optionalBoolean(sun,"mSunlightSensorEnabled"))&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(sun,"mScreenOn"))&&Boolean.FALSE.equals(HookRuntime.optionalBoolean(sun,"mAutoBrightnessSettingsEnable")))call(sun,"updateAmbientLux",new Class<?>[0]);}
        error="";s.requestRecalculation();
    }catch(Throwable unavailable){error="场景重新确认未完成，沿用系统下一次事件";}finally{reconciling=false;}}
    static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception{Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    void put(JSONObject status)throws JSONException{for(int i=0;i<SceneOptions.IDS.length;i++)status.put("scene_"+SceneOptions.IDS[i]+"_supported",SceneTuning.supported(s,i));JSONObject current=new JSONObject(),policies=new JSONObject();for(int i=0;i<denied.length;i++){current.put(SceneOptions.IDS[i],enabled&&denied[i]);policies.put(SceneOptions.IDS[i],options.entries[i].mode);}status.put("scene_control",new JSONObject().put("denied",current).put("policies",policies).put("error",error));}
}
