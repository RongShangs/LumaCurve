package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Verified OEM consumers only. Hooks cannot enable a scene or alter final safety clamps. */
final class SceneTuning {
    static final Map<Class<?>,Set<Integer>> capabilities=new HashMap<>();
    static Object object(HookRuntime s,String kind)throws Exception{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");return kind.equals("impl")?impl:kind.equals("abc")?HookEntry.get(impl,"mAutomaticBrightnessController"):kind.equals("touch")?HookEntry.get(impl,"mTouchAreaHelper"):kind.equals("sun")?HookEntry.get(s.owner,"mSunlightController"):HookEntry.get(impl,"mSceneDetector");}
    static HookRuntime state(Object object,String kind,boolean background){synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values())try{if(!s.closed&&(s.onDisplayThread()||background)&&object(s,kind)==object)return s;}catch(Throwable absent){}}return null;}
    static boolean supported(HookRuntime s,int i){try{String kind=i==1||i==6?"abc":i==2?"touch":i==3||i==8?"impl":i==7?"sun":"scene";Object object=object(s,kind);Set<Integer> cap=capabilities.get(object.getClass());if(cap==null||!cap.contains(i))return false;
        if(i==0)return Boolean.TRUE.equals(HookRuntime.optionalBoolean(object,"mUseDrivingModeForBrt"));
        if(i==3)return Boolean.TRUE.equals(HookRuntime.optionalBoolean(object,"mUseProximityEnabled"));
        if(i==4)return Boolean.TRUE.equals(HookRuntime.optionalBoolean(object,"mTransientProximityEnabled"));
        if(i==5)return Boolean.TRUE.equals(HookRuntime.optionalBoolean(object,"mUseAonFlareEnabled"));
        return true;
    }catch(Throwable missing){return false;}}
    static void install(ClassLoader loader){
        installGroup(loader,0,"SceneDetector",new String[]{"isNightTime","applyNightDrivingBrightness"},new Class<?>[][]{{},{}},new Class<?>[]{boolean.class,void.class},new String[]{"mUseDrivingModeForBrt"});
        installGroup(loader,1,"AutomaticBrightnessController",new String[]{"updateStepModeDarkenDebounceTime"},new Class<?>[][]{{long.class}},new Class<?>[]{void.class},new String[]{"mStepModeDarkenDebounceConfig"});
        installGroup(loader,2,"TouchCoverProtectionHelper",new String[]{"isTouchCoverProtectionActive"},new Class<?>[][]{{}},new Class<?>[]{boolean.class},new String[]{});
        installGroup(loader,3,"AutomaticBrightnessControllerImpl",new String[]{"dropAmbientLuxIfNeeded"},new Class<?>[][]{{}},new Class<?>[]{boolean.class},new String[]{"mUseProximityEnabled"});
        installGroup(loader,4,"SceneDetector",new String[]{"updateAmbientLux","isNeedSkipBrightnessChange"},new Class<?>[][]{{int.class,float.class,boolean.class},{}},new Class<?>[]{void.class,boolean.class},new String[]{"mTransientProximityEnabled","mNeedCheckProximitySensor","mNeedCheckAonFlare","mMinAonFlareEnableLux","mMaxAonFlareEnableLux"});
        installGroup(loader,5,"SceneDetector",new String[]{"isReflectiveScene"},new Class<?>[][]{{}},new Class<?>[]{boolean.class},new String[]{"mUseAonFlareEnabled"});
        installGroup(loader,6,"AutomaticBrightnessController",new String[]{"updateNightWakeMode"},new Class<?>[][]{{boolean.class,boolean.class}},new Class<?>[]{void.class},new String[]{});
        installGroup(loader,7,"SunlightController",new String[]{"setSunLightModeActive","updateAmbientLux"},new Class<?>[][]{{boolean.class},{}},new Class<?>[]{void.class,void.class},new String[]{});
        installGroup(loader,8,"AutomaticBrightnessControllerImpl",new String[]{"dropDecreaseLuxIfNeeded"},new Class<?>[][]{{}},new Class<?>[]{boolean.class},new String[]{"mIsGameSceneEnable"});
    }
    static void installGroup(ClassLoader loader,int group,String className,String[] names,Class<?>[][] args,Class<?>[] results,String[] fields){List<XC_MethodHook.Unhook> added=new ArrayList<>();try{
        Class<?> cls=Class.forName("com.android.server.display."+className,false,loader);Set<Integer> cap=capabilities.get(cls);if(cap!=null&&cap.contains(group))return;
        List<Method> methods=new ArrayList<>();for(int i=0;i<names.length;i++){Method m=cls.getDeclaredMethod(names[i],args[i]);if(m.getReturnType()!=results[i])throw new IllegalStateException("scene method ABI");methods.add(m);}for(String f:fields){Class<?> expected=f.equals("mMinAonFlareEnableLux")||f.equals("mMaxAonFlareEnableLux")?float.class:f.equals("mStepModeDarkenDebounceConfig")?long.class:boolean.class;if(HookEntry.field(cls,f).getType()!=expected)throw new IllegalStateException("scene field ABI");}
        for(Method m:methods){String name=m.getName();if(name.equals("applyNightDrivingBrightness")||name.equals("updateAmbientLux")&&group==7)continue;
            // Shared consumers are installed once; their callback handles both independent gates.
            if(group==5&&!hasSharedSceneConsumer(cls))throw new IllegalStateException("scene admission consumer unavailable");
            added.add(XposedBridge.hookMethod(m,hook(group,name)));}
        if(cap==null){cap=new HashSet<>();capabilities.put(cls,cap);}cap.add(group);
    }catch(Throwable missing){for(XC_MethodHook.Unhook h:added)h.unhook();XposedBridge.log("HyperLux scene option "+group+" unavailable: "+missing);}}
    static boolean hasSharedSceneConsumer(Class<?> cls){Set<Integer> cap=capabilities.get(cls);return cap!=null&&cap.contains(4);}
    static XC_MethodHook hook(int group,String name){return new XC_MethodHook(){
        protected void beforeHookedMethod(MethodHookParam p){String kind=group==1||group==6?"abc":group==2?"touch":group==3||group==8?"impl":group==7?"sun":"scene";HookRuntime s=state(p.thisObject,kind,false);if(s==null||s.probingScenes)return;
            SceneController c=s.scenes;boolean denied=c.blocked(group);try{
                if(name.equals("updateStepModeDarkenDebounceTime")){if(!c.reconciling)c.stepOriginal=(Long)p.args[0];if(denied)p.args[0]=0L;}
                else if(name.equals("updateNightWakeMode")){if(!c.reconciling)c.wakeOriginal=new boolean[]{(Boolean)p.args[0],(Boolean)p.args[1]};if(denied){p.args[0]=false;p.args[1]=false;}}
                else if(name.equals("setSunLightModeActive")){if(denied)p.args[0]=false;}
                else if(name.equals("dropAmbientLuxIfNeeded")&&denied)save(p,"mUseProximityEnabled",false);
                else if(name.equals("dropDecreaseLuxIfNeeded")&&denied)save(p,"mIsGameSceneEnable",false);
                else if(name.equals("updateAmbientLux")){if(denied)save(p,"mTransientProximityEnabled",false);if(c.blocked(5)){save(p,"mMinAonFlareEnableLux",Float.MAX_VALUE);save(p,"mMaxAonFlareEnableLux",-1f);}}
            }catch(Throwable failure){restore(p);c.stop();c.error="场景接口异常，已停止自定义场景";}
        }
        protected void afterHookedMethod(MethodHookParam p){restore(p);if(p.hasThrowable())return;String kind=group==2?"touch":"scene";HookRuntime s=state(p.thisObject,kind,name.equals("isNightTime"));if(s==null||s.probingScenes)return;SceneController c=s.scenes;
            if(name.equals("isNightTime")&&c.enabled&&c.denied[0])p.setResult(false);
            else if(name.equals("isTouchCoverProtectionActive")&&c.blocked(2))p.setResult(false);
            else if(name.equals("isReflectiveScene")&&c.blocked(5))p.setResult(false);
            else if(name.equals("isNeedSkipBrightnessChange")&&c.enabled){Object rear=HookRuntime.optionalBoolean(p.thisObject,"mNeedCheckProximitySensor"),aon=HookRuntime.optionalBoolean(p.thisObject,"mNeedCheckAonFlare");if(rear instanceof Boolean&&aon instanceof Boolean)p.setResult((Boolean)rear&&!c.blocked(4)||(Boolean)aon&&!c.blocked(5));}
        }
    };}
    static void save(XC_MethodHook.MethodHookParam p,String name,Object value)throws Exception{@SuppressWarnings("unchecked") Map<Field,Object> saved=(Map<Field,Object>)p.getObjectExtra("hyperlux.scene.saved");if(saved==null){saved=new LinkedHashMap<>();p.setObjectExtra("hyperlux.scene.saved",saved);}Field f=HookEntry.field(p.thisObject.getClass(),name);if(!saved.containsKey(f))saved.put(f,f.get(p.thisObject));f.set(p.thisObject,value);}
    static void restore(XC_MethodHook.MethodHookParam p){@SuppressWarnings("unchecked") Map<Field,Object> saved=(Map<Field,Object>)p.getObjectExtra("hyperlux.scene.saved");if(saved==null)return;for(Map.Entry<Field,Object> e:saved.entrySet())try{e.getKey().set(p.thisObject,e.getValue());}catch(Throwable failed){XposedBridge.log("HyperLux scene field restoration failed: "+failed);}saved.clear();}
}
