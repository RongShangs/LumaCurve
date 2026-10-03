package top.rongshangs.lumacurve.refactor;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Actual default mapper only; no sysfs writes or second output controller. */
final class TraditionalHooks {
    static final Set<Class<?>> installed=new HashSet<>();
    static HookRuntime state(Object mapper){HookRuntime s=HookEntry.states.get(mapper);return s!=null&&!s.closed&&s.onDisplayThread()&&s.kernel instanceof TraditionalAdapter?s:null;}
    static void install(Class<?> type)throws Exception {
        if(installed.contains(type))return;
        Method config=TraditionalAdapter.method(type,"setBrightnessConfiguration",Class.forName("android.hardware.display.BrightnessConfiguration",false,type.getClassLoader()));
        Method memory;try{memory=TraditionalAdapter.method(type,"addUserDataPoint",float.class,float.class,String.class);}catch(NoSuchMethodException two){memory=TraditionalAdapter.method(type,"addUserDataPoint",float.class,float.class);}
        Method output=TraditionalAdapter.method(type,"getBrightness",float.class,String.class,int.class);
        if(config.getReturnType()!=boolean.class||memory.getReturnType()!=void.class||output.getReturnType()!=float.class)throw new IllegalArgumentException("传统映射 Hook 签名不兼容");
        List<XC_MethodHook.Unhook> added=new ArrayList<>();
        try {
            added.add(XposedBridge.hookMethod(config,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                HookRuntime s=state(p.thisObject);if(s==null||s.changing||s.faultPending||!s.phase.equals("active")||!s.appliesToUser())return;
                TraditionalAdapter a=(TraditionalAdapter)s.kernel;Object requested=p.args[0];
                if(a.updating)return;
                if(requested==a.applied)return;
                if(requested==null||requested.equals(a.baseline))p.args[0]=a.applied;
                else s.fault(new IllegalStateException("系统更新了本地曲线，已交回系统，请重新读取并应用"));
            }
                protected void afterHookedMethod(MethodHookParam p){
                    HookRuntime s=state(p.thisObject);if(s==null||s.changing||p.hasThrowable())return;
                    try{TraditionalAdapter a=(TraditionalAdapter)s.kernel;Object current=TraditionalAdapter.read(a.mapper,"mConfig");
                        if(a.updating)return;
                        if(current!=a.baseline&&current!=a.applied)s.handler.post(()->HookEntry.reattach(s.owner,p.thisObject));
                    }catch(Throwable error){s.fault(error);}
                }
            }));
            added.add(XposedBridge.hookMethod(memory,new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){
                    HookRuntime s=state(p.thisObject);if(s==null||s.changing||!s.phase.equals("active")||!s.appliesToUser())return;
                    try{TraditionalAdapter a=(TraditionalAdapter)s.kernel;
                        if(!Boolean.TRUE.equals(s.kernel.get("mUseAutoBrightness"))||HookEntry.get(a.abc,"mCurrentBrightnessMapper")!=p.thisObject)return;
                        float lux=(Float)p.args[0],desired=(Float)p.args[1];if(!Float.isFinite(lux)||lux<0||!Float.isFinite(desired)||desired<0||desired>1)return;
                        s.lastManualAdjustment=SystemClock.uptimeMillis();
                        if(s.memoryStrength==0){p.setResult(null);s.memoryEvent(lux,desired,Float.NaN);return;}
                        float value=s.memoryStrength<1?s.memoryPolicy.remember(lux,desired,a.memoryAt(lux),s.memoryStrength,SystemClock.elapsedRealtime()):desired;
                        p.args[1]=value;s.memoryEvent(lux,desired,value);
                    }catch(Throwable error){XposedBridge.log("HyperLux traditional memory left OEM: "+error);}
                }
                protected void afterHookedMethod(MethodHookParam p){HookRuntime s=state(p.thisObject);if(s!=null&&!p.hasThrowable())s.queuePublish();}
            }));
            added.add(XposedBridge.hookMethod(output,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;HookRuntime s=state(p.thisObject);if(s==null)return;
                try{TraditionalAdapter a=(TraditionalAdapter)s.kernel;if(HookEntry.get(a.abc,"mCurrentBrightnessMapper")!=p.thisObject)return;
                    float br=(Float)p.getResult();if(!Float.isFinite(br)||br<0||br>1)return;
                    float nit=((Number)TraditionalAdapter.method(type,"convertToNits",float.class).invoke(p.thisObject,br)).floatValue();s.sample((Float)p.args[0],nit);
                }catch(Throwable optional){}
            }}));
            installed.add(type);
        }catch(Throwable failure){for(XC_MethodHook.Unhook h:added)h.unhook();throw new IllegalStateException("传统曲线 Hook 未建立",failure);}
    }
}
