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
    /** The gate asks "is the user driving", which does not depend on the legacy scene layout the
     *  confirmation hooks need. Resolve it separately so a provably absent driving capability
     *  reads as false, while any partial/replaced driving ABI stays rejected. */
    static final Map<Class<?>,DrivingAccess> drivingProfiles=new HashMap<>();
    static final class DrivingAccess {
        final String name;final Method status;
        DrivingAccess(Class<?> impl)throws Exception{
            Method present=null;try{present=impl.getMethod("getDrivingStatus");}catch(NoSuchMethodException absent){}
            if(present!=null){
                if(present.getReturnType()!=boolean.class||Modifier.isStatic(present.getModifiers()))throw new IllegalStateException("driving status ABI");
                Method delay=impl.getMethod("getNightDrivingDebounceConfig");
                if(delay.getReturnType()!=long.class||Modifier.isStatic(delay.getModifiers()))throw new IllegalStateException("driving delay ABI");
                status=present;name="native_driving";
            }else{
                // No status getter. Only a complete scan of the controller and its real detector
                // proves the feature is absent; a missing detector or any leftover driving member
                // stays ambiguous and keeps the gate closed.
                rejectDriving(impl);rejectDriving(HookEntry.field(impl,"mSceneDetector").getType());
                status=null;name="native_without_driving";
            }
        }
    }
    static void rejectDriving(Class<?> type){for(Class<?> c=type;c!=null&&c!=Object.class;c=c.getSuperclass()){
        for(Field f:c.getDeclaredFields())if(f.getName().toLowerCase(Locale.ROOT).contains("driv"))throw new IllegalStateException("Incomplete driving scene ABI");
        for(Method m:c.getDeclaredMethods())if(m.getName().toLowerCase(Locale.ROOT).contains("driv"))throw new IllegalStateException("Incomplete driving scene ABI");
    }}
    /** Newer HyperOS builds dropped the legacy transient-proximity/reflection flags and moved the
     *  always-on flare decision into a lux window inside SceneDetector.updateAmbientLux. Accept
     *  only that complete layout; a leftover legacy marker means a partially ported build and
     *  stays rejected instead of being guessed. */
    static final Map<Class<?>,Boolean> aonProfiles=new HashMap<>();
    static boolean aonScene(Class<?> impl)throws Exception{
        Boolean found=aonProfiles.get(impl);if(found!=null)return found;
        boolean accepted=false;
        try{
            for(String legacy:new String[]{"mTransientProximityEnabled","mNeedCheckProximitySensor","mNeedCheckAonFlare","mIsReflectiveScene"})
                if(hasMember(impl,legacy))throw new IllegalStateException("legacy scene marker "+legacy);
            for(String flag:new String[]{"mUseProxAndTouchEnabled","mUseAonFlareEnabled","mUseProximityEnabled","mIsGameSceneEnable"})
                if(HookEntry.field(impl,flag).getType()!=boolean.class)throw new IllegalStateException("scene flag ABI");
            Class<?> detector=HookEntry.field(impl,"mSceneDetector").getType();
            if(!detector.getName().equals("com.android.server.display.SceneDetector"))throw new IllegalStateException("scene owner ABI");
            if(HookEntry.field(detector,"mIsStepMode").getType()!=boolean.class||HookEntry.field(detector,"mUseAonFlareEnabled").getType()!=boolean.class)throw new IllegalStateException("scene detector flags ABI");
            for(String field:new String[]{"mMinAonFlareEnableLux","mMaxAonFlareEnableLux"})
                if(HookEntry.field(detector,field).getType()!=float.class)throw new IllegalStateException("AON window ABI");
            Method ambient=detector.getDeclaredMethod("updateAmbientLux",int.class,float.class,boolean.class);
            if(ambient.getReturnType()!=void.class||Modifier.isStatic(ambient.getModifiers()))throw new IllegalStateException("ambient stream ABI");
            Method flare=detector.getDeclaredMethod("checkAonFlareStatus",float.class);
            if(flare.getReturnType()!=void.class||Modifier.isStatic(flare.getModifiers()))throw new IllegalStateException("AON status ABI");
            accepted=true;
        }catch(Throwable incomplete){accepted=false;}
        aonProfiles.put(impl,accepted);return accepted;
    }
    static boolean hasMember(Class<?> type,String name){for(Class<?> c=type;c!=null&&c!=Object.class;c=c.getSuperclass()){
        for(Field f:c.getDeclaredFields())if(f.getName().equals(name))return true;
        for(Method m:c.getDeclaredMethods())if(m.getName().equals(name))return true;
    }return false;}
    /** Recognised scene layouts. Each capability keeps its own shape check so a feature only
     *  opens when the members it actually reads are present. */
    static String sceneShape(Object impl){
        try{return sceneAccess(impl).name;}catch(Throwable legacyAbsent){}
        try{if(aonScene(impl.getClass()))return "native_scene_aon";}catch(Throwable newerAbsent){}
        return null;
    }
    static synchronized SceneAccess sceneAccess(Object impl)throws Exception{Class<?> type=impl.getClass();SceneAccess found=sceneProfiles.get(type);if(found==null){found=new SceneAccess(type);sceneProfiles.put(type,found);}return found;}
    static synchronized DrivingAccess drivingAccess(Object impl)throws Exception{Class<?> type=impl.getClass();DrivingAccess found=drivingProfiles.get(type);if(found==null){found=new DrivingAccess(type);drivingProfiles.put(type,found);}return found;}
    static boolean driving(Object impl)throws Exception{DrivingAccess access=drivingAccess(impl);return access.status!=null&&(Boolean)access.status.invoke(impl);}
    static long nightDelay(Object impl)throws Exception{SceneAccess access=sceneAccess(impl);return access.nightDelay==null?0:((Number)access.nightDelay.invoke(impl)).longValue();}
    static String sceneProfile(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl");if(!supported(owner))return "unsupported";String shape=sceneShape(impl);return shape==null?"unsupported":shape;}catch(Throwable absent){return "unsupported";}}
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
        }catch(Throwable incompatible){HookTransaction.rollbackAll(hooks);XposedBridge.log("LumaCurve response tuning unavailable: "+incompatible);}
    }
    /** Core timing capability only: the deadline hooks need the installed ABC plus the retained
     *  history fields. Scene confirmation is resolved separately by sceneShape, so a build with
     *  a different scene layout no longer closes response/small/low-light/override gates. */
    static boolean supported(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
        if(abc==null||!installed.contains(abc.getClass()))return false;
        if(HookEntry.field(abc.getClass(),"mAmbientLightHorizonLong").getType()!=int.class)return false;
        if(HookEntry.field(abc.getClass(),"mStepModeDarkenDebounceConfig").getType()!=long.class)return false;
        drivingAccess(impl); // Complete driving ABI, or provably none; partial/replaced shapes reject.
        return sceneShape(impl)!=null;
    }catch(Throwable absent){return false;}}
    static boolean smallSupported(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");return supported(owner)&&HookEntry.field(abc.getClass(),"mSmallBrighteningLightDebounceConfig").getType()==long.class;}catch(Throwable absent){return false;}}
}
