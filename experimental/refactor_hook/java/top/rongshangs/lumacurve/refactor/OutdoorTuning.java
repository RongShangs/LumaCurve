package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Targets enter BEFORE downstream caps and animation. Driver/thermal/power limits remain; optional OPR relaxation is scoped to strong auto SDR. */
final class OutdoorTuning {
    static final Set<Class<?>> owners=new HashSet<>(),ranges=new HashSet<>(),peaks=new HashSet<>(),dynamicRanges=new HashSet<>(),nitCaps=new HashSet<>();
    static final Map<Class<?>,OutdoorSdrScope> nitGuards=new HashMap<>();
    static final Map<Class<?>,String> targetProfiles=new HashMap<>();
    static String targetProfile(Class<?> owner){String profile=targetProfiles.get(owner);return profile==null?"unsupported":profile;}
    static boolean nitScope(Object owner){try{OutdoorSdrScope scope=nitGuards.get(owner.getClass());return scope!=null&&scope.allows(owner);}catch(Throwable unavailable){return false;}}
    static HookRuntime state(Object hbm){synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values())if(s.onDisplayThread())try{if(HookEntry.get(s.owner,"mHbmController")==hbm)return s;}catch(Throwable ignored){}}return null;}
    static synchronized void install(Class<?> owner){if(owners.contains(owner))return;
        try(HookTransaction hooks=new HookTransaction()){
            ClassLoader loader=owner.getClassLoader();Class<?> impl=Class.forName("com.android.server.display.AutomaticBrightnessControllerImpl",false,loader);
            Method target;boolean legacyTarget;
            try{target=HbmAccess.method(impl,"getOverrideLimitBrightness",float.class,float.class,boolean.class,float.class);legacyTarget=false;}
            catch(NoSuchMethodException absent){
                for(Method method:impl.getDeclaredMethods())if(method.getName().equals("getOverrideLimitBrightness"))throw new IllegalStateException("Unknown override target ABI");
                // Older ABC calls this once after physical mapping, then keeps its own scene
                // veto, brightness clamp and all downstream SDR/thermal/power stages.
                Class<?>[] signature={float.class,String.class,int.class,float.class,float.class,boolean.class};
                target=HbmAccess.method(impl,"getCustomBrightness",float.class,signature);
                HbmAccess.method(owner,"getCustomBrightness",float.class,signature);
                if(HookEntry.field(impl,"mDisplayPowerControllerImpl").getType()!=owner||
                    !HookEntry.field(impl,"mAutomaticBrightnessController").getType().getName().equals("com.android.server.display.AutomaticBrightnessController"))throw new IllegalStateException("legacy custom target ownership ABI");
                legacyTarget=true;
            }
            final boolean customTarget=legacyTarget;
            hooks.add(XposedBridge.hookMethod(target,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=PipelineHooks.state(p.thisObject,2);if(s==null||!s.outdoor.options.flags[0])return;
                if(Boolean.TRUE.equals(p.args[customTarget?5:1])){s.outdoor.manual(((Number)p.args[customTarget?0:2]).floatValue());return;}
                // Preserve the OEM drag policy and all earlier scene decisions.
                float base=(Float)p.getResult(),next=s.outdoor.target(base);if(next!=base){p.setObjectExtra("hyperlux.outdoor.original",base);p.setResult(next);s.outdoor.stage("outdoor_target",base,next);}
            }}));
            Class<?> hbm=Class.forName("com.android.server.display.HighBrightnessModeController",false,loader);
            if(!ranges.contains(hbm)){
            for(String name:new String[]{"onAmbientLuxChange","setAutoBrightnessEnabled","resetHbmData","updateHbmMode"})for(Method m:hbm.getDeclaredMethods())if(m.getName().equals(name))hooks.add(XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()){HookRuntime s=state(p.thisObject);if(s!=null)s.outdoor.tick();}}}));
            Method maximum=HbmAccess.method(hbm,"getCurrentBrightnessMax",float.class);
            hooks.add(XposedBridge.hookMethod(maximum,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=state(p.thisObject);if(s!=null)p.setResult(s.outdoor.range((Float)p.getResult()));}}));
            }
            ranges.add(hbm);owners.add(owner);targetProfiles.put(owner,legacyTarget?"legacy_custom_target":"override_target");
            hooks.commit();
        }catch(Throwable unavailable){XposedBridge.log("HyperLux outdoor interface unavailable: "+unavailable);return;}
        installDynamicRange(owner);installPeakRange(owner);installStageProbes(owner);
        OutdoorOpr.install(owner);installNitCap(owner);
    }
    static void installDynamicRange(Class<?> owner){ClassLoader loader=owner.getClassLoader();
        try{Class<?> hbm=Class.forName("com.android.server.display.HighBrightnessModeController",false,loader);
            // Xiaomi's lux-based dynamic range can bypass the HBM getter entirely.
            // Intercept only its known range getter, preserving downstream clampers.
            Class<?> dynamic=Class.forName("com.android.server.display.BrightnessRangeControllerImpl",false,loader);
                if(dynamicRanges.contains(dynamic))return;
                HbmAccess.typed(dynamic,"mSupportDynamicBrightnessRange",boolean.class);HbmAccess.typed(dynamic,"mAutoBrightnessEnabled",boolean.class);HbmAccess.typed(dynamic,"mMaxBrightness",float.class);
                Method maximumDynamic=HbmAccess.method(dynamic,"getCurrentBrightnessMax",float.class,hbm);
                XposedBridge.hookMethod(maximumDynamic,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=state(p.args[0]);if(s==null)return;
                    try{if(!Boolean.TRUE.equals(HookEntry.get(p.thisObject,"mSupportDynamicBrightnessRange"))||!Boolean.TRUE.equals(HookEntry.get(p.thisObject,"mAutoBrightnessEnabled")))return;
                        Object dpc=HookEntry.get(s.owner,"mDisplayPowerController"),range=HookEntry.get(dpc,"mBrightnessRangeController");if(HookEntry.get(range,"mBrightnessRangeControllerImpl")!=p.thisObject)return;
                        float before=(Float)p.getResult(),next=s.outdoor.dynamicRange(before);if(next!=before)p.setResult(next);
                    }catch(Throwable unknown){}
                }});dynamicRanges.add(dynamic);
            }catch(Throwable optional){XposedBridge.log("HyperLux outdoor dynamic range unavailable: "+optional);}
    }
    static void installPeakRange(Class<?> owner){
            try{Method peak=HbmAccess.method(owner,"getMaxHbmBrightnessForPeak",float.class);XposedBridge.hookMethod(peak,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=HookEntry.ownerState(p.thisObject);if(s==null||!s.onDisplayThread()||!s.outdoor.rangeAllowed())return;
                try{float before=(Float)p.getResult(),max=s.outdoor.access.max();if(OutdoorPolicy.valid(before)&&OutdoorPolicy.valid(max)&&max>before){p.setResult(max);s.outdoor.stage("outdoor_peak_range",before,max);}}catch(Throwable unknown){}
            }});peaks.add(owner);}catch(Throwable optional){XposedBridge.log("HyperLux outdoor peak range unavailable: "+optional);}
    }
    static void installStageProbes(Class<?> owner){
        try{
            // Read-only per-stage causes; optional OPR relaxation reports its native cap separately.
            for(String name:new String[]{"adjustBrightnessByOpr","adjustBrightnessByThermal","adjustBrightnessByBattery","adjustBrightnessByPowerSaveMode","adjustBrightnessToPeak","adjustBrightnessByBcbc","adjustSdrBrightness"})
                for(Method m:owner.getDeclaredMethods())if(m.getName().equals(name)&&m.getReturnType()==float.class&&m.getParameterTypes().length>0&&m.getParameterTypes()[0]==float.class)try{
                    final String stage=name;XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(stage.equals("adjustSdrBrightness")){HookRuntime s=HookEntry.ownerState(p.thisObject);if(s!=null)s.outdoor.tick();}}
                        protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=HookEntry.ownerState(p.thisObject);if(s!=null&&s.onDisplayThread())s.outdoor.stage(stage,(Float)p.args[0],(Float)p.getResult());}});
                }catch(Throwable optional){XposedBridge.log("HyperLux outdoor trace unavailable: "+name+" / "+optional);}
        }catch(Throwable optional){XposedBridge.log("HyperLux outdoor trace discovery unavailable: "+optional);}
    }
    static synchronized void installNitCap(Class<?> owner){
        if(nitCaps.contains(owner))return;
        try{
            Class<?> reason=Class.forName("com.android.server.display.brightness.BrightnessReason",false,owner.getClassLoader());
            Method sdr=HbmAccess.method(owner,"adjustSdrBrightness",float.class,float.class,boolean.class,reason,boolean.class,boolean.class);
            OutdoorSdrScope scope=OutdoorSdrScope.resolve(owner);
            HbmAccess.typed(owner,"mAutoBrightnessEnable",boolean.class);
            XposedBridge.hookMethod(sdr,new XC_MethodHook(){
                boolean eligible(MethodHookParam p,HookRuntime s)throws Exception{
                    return s!=null&&s.onDisplayThread()&&s.outdoor.options.values[OutdoorOptions.MAX_NIT]>0
                        &&Boolean.TRUE.equals(p.args[1])&&nitScope(p.thisObject);
                }
                protected void beforeHookedMethod(MethodHookParam p){
                    try{HookRuntime s=HookEntry.ownerState(p.thisObject);if(!eligible(p,s))return;
                        float before=(Float)p.args[0],next=s.outdoor.limit(before);if(next<before){p.args[0]=next;s.outdoor.stage("outdoor_nit_limit",before,next);}
                    }catch(Throwable unavailable){/* Unknown mapping retains system control. */}
                }
                protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    try{HookRuntime s=HookEntry.ownerState(p.thisObject);if(!eligible(p,s))return;
                        // Clamp again after OPR/content adjustments; never raise a thermal/power cap.
                        float before=(Float)p.getResult(),next=s.outdoor.limit(before);if(next<before){p.setResult(next);s.outdoor.stage("outdoor_nit_limit",before,next);}
                    }catch(Throwable unavailable){/* Unknown mapping retains system control. */}
                }
            });
            nitGuards.put(owner,scope);nitCaps.add(owner);
        }catch(Throwable unavailable){XposedBridge.log("HyperLux outdoor nit limit unavailable: "+unavailable);}
    }
}
