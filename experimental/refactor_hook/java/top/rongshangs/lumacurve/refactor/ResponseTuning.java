package top.rongshangs.lumacurve.refactor;

import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Optional post-hook of verified OEM deadlines. Ring buffers, lux thresholds and animations stay OEM. */
final class ResponseTuning {
    static final Set<Class<?>> installed=new HashSet<>();
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
                            if(!((Boolean)state.kernel.get("mUseAutoBrightness")))return;
                            if((Boolean)idle.invoke(p.thisObject))return;
                            Method driving=impl.getClass().getMethod("getDrivingStatus");
                            if(driving.getReturnType()!=boolean.class||(Boolean)driving.invoke(impl))return;
                            boolean small=brighten&&p.args.length==2&&(Float)p.args[1]<((Number)HookEntry.get(p.thisObject,"mAmbientBrighteningThreshold")).floatValue();
                            float candidate=p.args.length==2?(Float)p.args[1]:state.mainCandidate(p.thisObject,impl);
                            boolean guard=state.lowLightApplies(p.thisObject,impl,candidate),custom=small?state.smallResponseEnabled:state.responseEnabled;
                            if(!guard&&!custom)return;
                            long base=((Number)HookEntry.get(p.thisObject,small?"mSmallBrighteningLightDebounceConfig":brighten?"mBrighteningLightDebounceConfig":"mDarkeningLightDebounceConfig")).longValue();
                            long chosen=custom?(small?state.smallBrightenDelay:brighten?state.brightenDelay:state.darkenDelay):base;
                            if(guard){long existing=Math.max(base,chosen);
                                long horizon=((Number)HookEntry.get(p.thisObject,"mAmbientLightHorizonLong")).longValue();
                                long additional=brighten?0:((Number)HookEntry.get(p.thisObject,"mStepModeDarkenDebounceConfig")).longValue();
                                chosen=LowLightPolicy.withinWindow(LowLightPolicy.chosen(existing,brighten,false,state.lowLightBrighten,state.lowLightDarken),existing,horizon,additional);
                            }
                            if(chosen==base)return;
                            p.setResult(DelayPolicy.deadline((Long)p.getResult(),(Long)p.args[0],base,chosen));state.responseAdjustments++;if(small)state.smallResponseAdjustments++;if(guard)state.lowLightMainAdjustments++;return;
                        }catch(Throwable incompatible){return;}
                    }}
                }}));
            }
            installed.add(abc);
        }catch(Throwable incompatible){for(XC_MethodHook.Unhook hook:hooks)hook.unhook();XposedBridge.log("LumaCurve response tuning unavailable: "+incompatible);}
    }
    static boolean supported(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");return abc!=null&&installed.contains(abc.getClass())&&impl.getClass().getMethod("getDrivingStatus").getReturnType()==boolean.class;}catch(Throwable absent){return false;}}
    static boolean smallSupported(Object owner){try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");return supported(owner)&&HookEntry.field(abc.getClass(),"mSmallBrighteningLightDebounceConfig").getType()==long.class;}catch(Throwable absent){return false;}}
}
