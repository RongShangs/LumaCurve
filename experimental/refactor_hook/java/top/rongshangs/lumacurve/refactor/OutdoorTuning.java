package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Targets enter BEFORE downstream caps and animation. Driver/thermal/power limits remain; optional OPR relaxation is scoped to strong auto SDR. */
final class OutdoorTuning {
    static final Set<Class<?>> owners=new HashSet<>(),ranges=new HashSet<>(),peaks=new HashSet<>(),dynamicRanges=new HashSet<>();
    static HookRuntime state(Object hbm){synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values())if(s.onDisplayThread())try{if(HookEntry.get(s.owner,"mHbmController")==hbm)return s;}catch(Throwable ignored){}}return null;}
    static void install(Class<?> owner){if(owners.contains(owner))return;List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try{
            ClassLoader loader=owner.getClassLoader();Class<?> impl=Class.forName("com.android.server.display.AutomaticBrightnessControllerImpl",false,loader);
            Method target=HbmAccess.method(impl,"getOverrideLimitBrightness",float.class,float.class,boolean.class,float.class);
            hooks.add(XposedBridge.hookMethod(target,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=PipelineHooks.state(p.thisObject,2);if(s==null||!s.outdoor.options.flags[0])return;
                if(Boolean.TRUE.equals(p.args[1])){s.outdoor.manual(((Number)p.args[2]).floatValue());return;}
                // Preserve the OEM drag policy and all earlier scene decisions.
                float base=(Float)p.getResult(),next=s.outdoor.target(base);if(next!=base){p.setObjectExtra("hyperlux.outdoor.original",base);p.setResult(next);s.outdoor.stage("outdoor_target",base,next);}
            }}));
            Class<?> hbm=Class.forName("com.android.server.display.HighBrightnessModeController",false,loader);
            for(String name:new String[]{"onAmbientLuxChange","setAutoBrightnessEnabled","resetHbmData","updateHbmMode"})for(Method m:hbm.getDeclaredMethods())if(m.getName().equals(name))hooks.add(XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable()){HookRuntime s=state(p.thisObject);if(s!=null)s.outdoor.tick();}}}));
            Method maximum=HbmAccess.method(hbm,"getCurrentBrightnessMax",float.class);
            hooks.add(XposedBridge.hookMethod(maximum,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=state(p.thisObject);if(s!=null)p.setResult(s.outdoor.range((Float)p.getResult()));}}));ranges.add(hbm);
            owners.add(owner);
            // Xiaomi's lux-based dynamic range can bypass the HBM getter entirely.
            // Intercept only its known range getter, preserving downstream clampers.
            try{Class<?> dynamic=Class.forName("com.android.server.display.BrightnessRangeControllerImpl",false,loader);
                HbmAccess.typed(dynamic,"mSupportDynamicBrightnessRange",boolean.class);HbmAccess.typed(dynamic,"mAutoBrightnessEnabled",boolean.class);HbmAccess.typed(dynamic,"mMaxBrightness",float.class);
                Method maximumDynamic=HbmAccess.method(dynamic,"getCurrentBrightnessMax",float.class,hbm);
                XposedBridge.hookMethod(maximumDynamic,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=state(p.args[0]);if(s==null)return;
                    try{if(!Boolean.TRUE.equals(HookEntry.get(p.thisObject,"mSupportDynamicBrightnessRange"))||!Boolean.TRUE.equals(HookEntry.get(p.thisObject,"mAutoBrightnessEnabled")))return;
                        Object dpc=HookEntry.get(s.owner,"mDisplayPowerController"),range=HookEntry.get(dpc,"mBrightnessRangeController");if(HookEntry.get(range,"mBrightnessRangeControllerImpl")!=p.thisObject)return;
                        float before=(Float)p.getResult(),next=s.outdoor.dynamicRange(before);if(next!=before)p.setResult(next);
                    }catch(Throwable unknown){}
                }});dynamicRanges.add(dynamic);
            }catch(Throwable optional){XposedBridge.log("HyperLux outdoor dynamic range unavailable: "+optional);}
            try{Method peak=HbmAccess.method(owner,"getMaxHbmBrightnessForPeak",float.class);XposedBridge.hookMethod(peak,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=HookEntry.ownerState(p.thisObject);if(s==null||!s.onDisplayThread()||!s.outdoor.rangeAllowed())return;
                try{float before=(Float)p.getResult(),max=s.outdoor.access.max();if(OutdoorPolicy.valid(before)&&OutdoorPolicy.valid(max)&&max>before){p.setResult(max);s.outdoor.stage("outdoor_peak_range",before,max);}}catch(Throwable unknown){}
            }});peaks.add(owner);}catch(Throwable optional){XposedBridge.log("HyperLux outdoor peak range unavailable: "+optional);}
            // Read-only per-stage causes; optional OPR relaxation reports its native cap separately.
            for(String name:new String[]{"adjustBrightnessByOpr","adjustBrightnessByThermal","adjustBrightnessByBattery","adjustBrightnessByPowerSaveMode","adjustBrightnessToPeak","adjustBrightnessByBcbc","adjustSdrBrightness"})
                for(Method m:owner.getDeclaredMethods())if(m.getName().equals(name)&&m.getReturnType()==float.class&&m.getParameterTypes().length>0&&m.getParameterTypes()[0]==float.class){
                    final String stage=name;XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(stage.equals("adjustSdrBrightness")){HookRuntime s=HookEntry.ownerState(p.thisObject);if(s!=null)s.outdoor.tick();}}
                        protected void afterHookedMethod(MethodHookParam p){if(p.hasThrowable())return;HookRuntime s=HookEntry.ownerState(p.thisObject);if(s!=null&&s.onDisplayThread())s.outdoor.stage(stage,(Float)p.args[0],(Float)p.getResult());}});
                }
            OutdoorOpr.install(owner);
        }catch(Throwable unavailable){for(XC_MethodHook.Unhook h:hooks)h.unhook();XposedBridge.log("HyperLux outdoor interface unavailable: "+unavailable);}
    }
}
