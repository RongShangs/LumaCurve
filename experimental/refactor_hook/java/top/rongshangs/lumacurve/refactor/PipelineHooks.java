package top.rongshangs.lumacurve.refactor;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Read-only probes; an optional ABI failure never disables the curve hook. */
final class PipelineHooks {
    static final Set<Class<?>> installed=new HashSet<>();
    static HookRuntime state(Object object,int role){
        synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
            if(s.closed||Looper.myLooper()!=s.handler.getLooper())continue;
            try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");
                Object match=role==0?HookEntry.get(impl,"mAutomaticBrightnessController"):role==1?s.owner:impl;
                if(match==object)return s;
            }catch(Throwable ignored){}
        }}return null;
    }
    static void install(Class<?> owner){
        if(!installed.add(owner))return;
        ClassLoader loader=owner.getClassLoader();
        try{
            Class<?> abc=Class.forName("com.android.server.display.AutomaticBrightnessController",false,loader);
            Method method=abc.getDeclaredMethod("updateAutoBrightness",boolean.class,boolean.class);
            if(method.getReturnType()!=void.class)throw new IllegalStateException("calculation ABI");
            XposedBridge.hookMethod(method,new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){
                    HookRuntime s=state(p.thisObject,0);if(s==null)return;
                    p.setObjectExtra("luma.trace.previous",s.traceFrame);
                    PipelineHistory.Frame f=s.beginTrace(p.thisObject);s.traceFrame=f;p.setObjectExtra("luma.trace.frame",f);
                }
                protected void afterHookedMethod(MethodHookParam p){
                    HookRuntime s=state(p.thisObject,0);Object f=p.getObjectExtra("luma.trace.frame");if(s==null||f==null)return;
                    try{s.finishTrace((PipelineHistory.Frame)f,p.thisObject,p.hasThrowable());}
                    catch(Throwable ignored){}finally{s.traceFrame=(PipelineHistory.Frame)p.getObjectExtra("luma.trace.previous");}
                }
            });
        }catch(Throwable absent){XposedBridge.log("HyperLux trace calculation unavailable: "+absent);}
        probe(owner,"getCustomBrightness",1,"mapped",4,float.class,String.class,int.class,float.class,float.class,boolean.class);
        probe(owner,"getRefactorBrightness",1,"refactor",-1,float.class,float.class,boolean.class,boolean.class);
        try{Class<?> impl=Class.forName("com.android.server.display.AutomaticBrightnessControllerImpl",false,loader);
            probe(impl,"getCustomBrightnessForRefactorPolicy",2,"scene",1,float.class,float.class,boolean.class);
            probe(impl,"getOverrideLimitBrightness",2,"override",0,float.class,boolean.class,float.class);
        }catch(Throwable ignored){}
        try{Class<?> reason=Class.forName("com.android.server.display.brightness.BrightnessReason",false,loader);
            output(owner,"adjustBrightness",0,reason);
            output(owner,"adjustSdrBrightness",2,reason);
        }catch(Throwable ignored){}
    }
    static void probe(Class<?> cls,String name,int role,String stage,int input,Class<?>...types){
        try{Method m=cls.getDeclaredMethod(name,types);if(m.getReturnType()!=float.class)return;
            XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;HookRuntime s=state(p.thisObject,role);if(s==null||s.traceFrame==null)return;
                PipelineHistory.Frame f=s.traceFrame;float out=(Float)p.getResult();
                if(stage.equals("mapped")){f.mapped=(Float)p.args[input];f.curve=out;f.route="mapping";}
                if(stage.equals("refactor")){f.curve=out;f.route="refactor";}
                if(stage.equals("scene")){f.sceneIn=(Float)p.args[input];f.sceneOut=out;}
                if(stage.equals("override")){f.overrideIn=(Float)p.args[input];f.overrideOut=out;}
            }});
        }catch(Throwable absent){XposedBridge.log("HyperLux trace stage unavailable: "+name);}
    }
    static void output(Class<?> owner,String name,int reasonIndex,Class<?> reason){
        try{Method m=reasonIndex==0?owner.getDeclaredMethod(name,float.class,reason):owner.getDeclaredMethod(name,float.class,boolean.class,reason,boolean.class,boolean.class);
            if(m.getReturnType()!=float.class)return;
            XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;HookRuntime s=state(p.thisObject,1);if(s==null)return;
                s.outputTrace(name,(Float)p.args[0],(Float)p.getResult(),String.valueOf(p.args[reasonIndex==0?1:reasonIndex]));
            }});
        }catch(Throwable absent){XposedBridge.log("HyperLux trace output unavailable: "+name);}
    }
}
