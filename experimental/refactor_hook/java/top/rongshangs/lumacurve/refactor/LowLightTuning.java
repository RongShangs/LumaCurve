package top.rongshangs.lumacurve.refactor;

import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** The assist sensor has its own ring buffer, independently of the ABC main deadlines. */
final class LowLightTuning {
    static final Set<Class<?>> installed=new HashSet<>();
    static void install(ClassLoader loader){
        List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try{
            Class<?> ring=Class.forName("com.android.server.display.AmbientLightRingBuffer",false,loader);
            if(installed.contains(ring))return;
            for(String field:new String[]{"mBrighteningLightDebounceConfig","mDarkeningLightDebounceConfig","mSmallBrighteningLightDebounceConfig"})
                if(HookEntry.field(ring,field).getType()!=long.class)throw new IllegalStateException("assist debounce ABI");
            Method[] methods={ring.getDeclaredMethod("nextAmbientLightBrighteningTransition",long.class,float.class),
                ring.getDeclaredMethod("nextAmbientLightBrighteningTransition",long.class,float.class,float.class,float.class),
                ring.getDeclaredMethod("nextAmbientLightDarkeningTransition",long.class,float.class)};
            for(Method m:methods){if(m.getReturnType()!=long.class)throw new IllegalStateException("assist deadline ABI");
                final boolean bright=m.getName().contains("Brightening");
                hooks.add(XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
                        if(s.closed||!s.lowLightEnabled||Looper.myLooper()!=s.handler.getLooper())continue;
                        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),dual=HookEntry.get(impl,"mDualSensorPolicy");
                            if(HookEntry.get(dual,"mAssistLightSensorRingBuffer")!=p.thisObject)continue;
                            Object abc=HookEntry.get(impl,"mAutomaticBrightnessController");if(!s.lowLightApplies(abc,impl,HookRuntime.optionalNumber(dual,"mAssistFastAmbientLux")))return;
                            boolean small=bright&&p.args.length==4&&(Float)p.args[1]<(Float)p.args[2];
                            long base=((Number)HookEntry.get(p.thisObject,small?"mSmallBrighteningLightDebounceConfig":bright?"mBrighteningLightDebounceConfig":"mDarkeningLightDebounceConfig")).longValue();
                            long chosen=LowLightPolicy.chosen(base,bright,true);if(chosen==base)return;
                            p.setResult(DelayPolicy.deadline((Long)p.getResult(),(Long)p.args[0],base,chosen));s.lowLightAssistAdjustments++;return;
                        }catch(Throwable incompatible){return;}
                    }}
                }}));
            }installed.add(ring);
        }catch(Throwable unavailable){for(XC_MethodHook.Unhook h:hooks)h.unhook();XposedBridge.log("HyperLux assist stability unavailable: "+unavailable);}
    }
    static boolean supported(HookRuntime s){
        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),dual=HookEntry.get(impl,"mDualSensorPolicy"),ring=HookEntry.get(dual,"mAssistLightSensorRingBuffer");
            Object abc=HookEntry.get(impl,"mAutomaticBrightnessController");
            return ResponseTuning.supported(s.owner)&&s.hdrProbe!=null&&ring!=null&&installed.contains(ring.getClass())&&
                HookEntry.field(abc.getClass(),"mAmbientLightHorizonLong").getType()==int.class&&
                HookEntry.field(abc.getClass(),"mStepModeDarkenDebounceConfig").getType()==long.class&&
                HookEntry.field(abc.getClass(),"mAmbientLuxValid").getType()==boolean.class;
        }catch(Throwable absent){return false;}
    }
}
