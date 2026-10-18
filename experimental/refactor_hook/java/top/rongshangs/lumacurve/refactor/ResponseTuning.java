package top.rongshangs.lumacurve.refactor;

import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Optional post-hook of verified OEM deadlines. Ring buffers, lux thresholds and animations stay OEM. */
final class ResponseTuning {
    static final Set<Class<?>> installed=new HashSet<>();
    /** Profiles belong to actual classes/loaders. A broken modern interface never falls back. */
    static final Map<Class<?>,SceneAccess> sceneProfiles=new HashMap<>();
    static final class SceneAccess {
        final String name;final Method driving,nightDelay;
        SceneAccess(Class<?> impl)throws Exception{
            Method present=null;try{present=impl.getMethod("getDrivingStatus");}catch(NoSuchMethodException absent){}
            if(present!=null){
                if(present.getReturnType()!=boolean.class||Modifier.isStatic(present.getModifiers()))throw new IllegalStateException("driving status ABI");
                driving=present;nightDelay=impl.getMethod("getNightDrivingDebounceConfig");
                if(nightDelay.getReturnType()!=long.class||Modifier.isStatic(nightDelay.getModifiers()))throw new IllegalStateException("driving delay ABI");
                name="native_driving";
            }else{
                // The older native strategy has no driving feature at all. Verify its complete
                // scene layout and reject partially ported/newer driving implementations.
                rejectDriving(impl);Class<?> detector=HookEntry.field(impl,"mSceneDetector").getType();
                if(!detector.getName().equals("com.android.server.display.SceneDetector"))throw new IllegalStateException("legacy scene owner ABI");
                rejectDriving(detector);
                for(String field:new String[]{"mUseAonFlareEnabled","mUseProximityEnabled","mTransientProximityEnabled"})
                    if(HookEntry.field(impl,field).getType()!=boolean.class)throw new IllegalStateException("legacy scene flags ABI");
                for(String field:new String[]{"mIsStepMode","mIsReflectiveScene","mNeedCheckProximitySensor","mNeedCheckAonFlare"})
                    if(HookEntry.field(detector,field).getType()!=boolean.class)throw new IllegalStateException("legacy scene detector ABI");
                driving=nightDelay=null;name="native_without_driving";
            }
        }
    }
    static void rejectDriving(Class<?> type){for(Class<?> c=type;c!=null&&c!=Object.class;c=c.getSuperclass()){
        for(Field f:c.getDeclaredFields())if(f.getName().toLowerCase(Locale.ROOT).contains("driv"))throw new IllegalStateException("Incomplete driving scene ABI");
        for(Method m:c.getDeclaredMethods())if(m.getName().toLowerCase(Locale.ROOT).contains("driv"))throw new IllegalStateException("Incomplete driving scene ABI");
    }}
    static synchronized SceneAccess sceneAccess(Object impl)throws Exception{Class<?> type=impl.getClass();SceneAccess found=sceneProfiles.get(type);if(found==null){found=new SceneAccess(type);sceneProfiles.put(type,found);}return found;}
    static boolean driving(Object impl)throws Exception{SceneAccess access=sceneAccess(impl);return access.driving!=null&&(Boolean)access.driving.invoke(impl);}
    static long nightDelay(Object impl)throws Exception{SceneAccess access=sceneAccess(impl);return access.nightDelay==null?0:((Number)access.nightDelay.invoke(impl)).longValue();}
    static String sceneProfile(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl");return supported(owner)?sceneAccess(impl).name:"unsupported";}catch(Throwable absent){return "unsupported";}}
    static void install(ClassLoader loader){
        List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try{
            Class<?> abc=Class.forName("com.android.server.display.AutomaticBrightnessController",false,loader);
            if(installed.contains(abc))return;
            for(String field:new String[]{"mBrighteningLightDebounceConfig","mDarkeningLightDebounceConfig"})
                if(HookEntry.field(abc,field).getType()!=long.class)throw new IllegalStateException("debounce field ABI");
            if(HookEntry.field(abc,"mAmbientBrighteningThreshold").getType()!=float.class)throw new IllegalStateException("threshold ABI");
            Method idle=abc.getDeclaredMethod("isInIdleMode");idle.setAccessible(true);
            if(idle.getReturnType()!=boolean.class)throw new IllegalStateException("idle ABI");
            Method[] methods={abc.getDeclaredMethod("nextAmbientLightBrighteningTransition",long.class),abc.getDeclaredMethod("nextAmbientLightBrighteningTransition",long.class,float.class),abc.getDeclaredMethod("nextAmbientLightDarkeningTransition",long.class)};
            for(Method method:methods){
                if(method.getReturnType()!=long.class)throw new IllegalStateException("deadline ABI");
                final boolean brighten=method.getName().contains("Brightening");
                hooks.add(XposedBridge.hookMethod(method,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    synchronized(HookEntry.states){for(HookRuntime state:HookEntry.states.values()){
                        if(state.closed||(!state.responseEnabled&&!state.smallResponseEnabled&&!state.lowLightEnabled)||!state.phase.equals("active")||!state.appliesToUser()||Looper.myLooper()!=state.handler.getLooper())continue;
                        try{
                            Object impl=HookEntry.get(state.owner,"mAutomaticBrightnessControllerImpl");
                            if(HookEntry.get(impl,"mAutomaticBrightnessController")!=p.thisObject)continue;
                            if(!((Boolean)state.kernel.get("mUseAutoBrightness"))){state.assistGate.release("inactive");return;}
                            if((Boolean)idle.invoke(p.thisObject)){state.assistGate.release("inactive");return;}
                            boolean small=brighten&&p.args.length==2&&(Float)p.args[1]<((Number)HookEntry.get(p.thisObject,"mAmbientBrighteningThreshold")).floatValue();
                            float candidate=p.args.length==2?(Float)p.args[1]:state.mainCandidate(p.thisObject,impl);
                            boolean guard=state.lowLightApplies(p.thisObject,impl,candidate),custom=(small?state.smallResponseEnabled:state.responseEnabled)&&state.normalTuningAllowed(p.thisObject,impl);
                            if(!guard&&!custom){state.assistGate.release("inactive");return;}
                            long base=((Number)HookEntry.get(p.thisObject,small?"mSmallBrighteningLightDebounceConfig":brighten?"mBrighteningLightDebounceConfig":"mDarkeningLightDebounceConfig")).longValue();
                            long chosen=custom?(small?state.smallBrightenDelay:brighten?state.brightenDelay:state.darkenDelay):base;
                            long horizon=((Number)HookEntry.get(p.thisObject,"mAmbientLightHorizonLong")).longValue();
                            long additional=brighten?0:((Number)HookEntry.get(p.thisObject,"mStepModeDarkenDebounceConfig")).longValue();
                            // OEM night-driving time is added to every main deadline. Keep it and
                            // include it in the retained-history budget; never shorten a native wait.
                            if(driving(impl)){
                                long night=nightDelay(impl);
                                if(night<0||night>60000||additional<0||additional>60000)return;
                                additional=Math.addExact(additional,night);
                            }
                            if(custom){long safe=AdvancedPolicy.retainedDelay(chosen,base,horizon,additional);if(safe!=chosen)state.delayWindowClamps++;chosen=safe;}
                            if(guard){long existing=Math.max(base,chosen);
                                long wanted=LowLightPolicy.chosen(existing,brighten,false,state.lowLightBrighten,state.lowLightDarken);
                                chosen=LowLightPolicy.withinWindow(wanted,existing,horizon,additional);if(chosen!=wanted)state.delayWindowClamps++;
                            }
                            if(small)state.lastMainSmall=chosen;else if(brighten)state.lastMainBrighten=chosen;else {state.lastMainDarken=chosen;state.lastMainExtra=additional;}
                            long deadline=chosen==base?(Long)p.getResult():DelayPolicy.deadline((Long)p.getResult(),(Long)p.args[0],base,chosen);
                            if(brighten&&state.assistGate.enabled)deadline=LowLightAssistEvidence.deadline(state,p.thisObject,impl,(Long)p.args[0],deadline,p.args.length==2?(Float)p.args[1]:HookRuntime.optionalNumber(p.thisObject,"mAmbientBrighteningThreshold"));
                            if(deadline!=(Long)p.getResult()){p.setResult(deadline);state.responseAdjustments++;if(small)state.smallResponseAdjustments++;if(guard)state.lowLightMainAdjustments++;}return;
                        }catch(Throwable incompatible){return;}
                    }}
                }}));
            }
            installed.add(abc);
        }catch(Throwable incompatible){for(XC_MethodHook.Unhook hook:hooks)hook.unhook();XposedBridge.log("LumaCurve response tuning unavailable: "+incompatible);}
    }
    static boolean supported(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");return abc!=null&&installed.contains(abc.getClass())&&sceneAccess(impl)!=null&&HookEntry.field(abc.getClass(),"mAmbientLightHorizonLong").getType()==int.class&&HookEntry.field(abc.getClass(),"mStepModeDarkenDebounceConfig").getType()==long.class;}catch(Throwable absent){return false;}}
    static boolean smallSupported(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");return supported(owner)&&HookEntry.field(abc.getClass(),"mSmallBrighteningLightDebounceConfig").getType()==long.class;}catch(Throwable absent){return false;}}
}
