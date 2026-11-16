package top.rongshangs.lumacurve.refactor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Optional window eligibility filter; does not write brightness, attributes or preferences. */
final class AppWindowBrightness {
    static final Map<ClassLoader,Binding> bindings=new ConcurrentHashMap<>();
    static final Map<ClassLoader,String> errors=new ConcurrentHashMap<>();
    static final class Binding {
        final WindowBrightnessAccess access;volatile Set<HookRuntime> owners=Collections.emptySet();volatile Object service;volatile String error="";
        final AtomicBoolean queued=new AtomicBoolean();final AtomicLong blocked=new AtomicLong();
        Binding(WindowBrightnessAccess access){this.access=access;}
        synchronized void configure(HookRuntime s,boolean on){Set<HookRuntime> next=Collections.newSetFromMap(new IdentityHashMap<HookRuntime,Boolean>());next.addAll(owners);if(on)next.add(s);else next.remove(s);boolean changed=!next.equals(owners);owners=Collections.unmodifiableSet(next);if(changed)request(service);}
        void request(Object value){
            if(value==null||!queued.compareAndSet(false,true))return;
            try{if(!access.post(value,()->{queued.set(false);if(service!=value)return;try{access.reevaluate(value);}catch(Throwable failed){error=failed.toString();}})){queued.set(false);error="窗口重评估调度未接受";}}
            catch(Throwable failed){queued.set(false);error=failed.toString();}
        }
    }
    static synchronized void install(ClassLoader loader){
        if(bindings.containsKey(loader))return;
        try{WindowBrightnessAccess access=new WindowBrightnessAccess(loader);Binding binding=new Binding(access);
            try(HookTransaction tx=new HookTransaction()){
                tx.add(XposedBridge.hookMethod(access.gate,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam m){
                    if(m.hasThrowable())return;
                    try{Object service=access.service(m.thisObject);if(service!=null)binding.service=service;
                        if(!Boolean.TRUE.equals(m.getResult())||binding.owners.isEmpty()||HookEntry.currentUser.serial()!=0)return;
                        boolean suspended=access.suspended(m.thisObject,m.args[0]);binding.error="";if(suspended){m.setResult(false);binding.blocked.incrementAndGet();}
                    }catch(Throwable unavailable){binding.error=unavailable.toString();}
                }}));
                tx.add(XposedBridge.hookMethod(access.modeChanged,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam m){if(binding.owners.isEmpty())return;try{m.setObjectExtra("hyperlux.window.previous_mode",access.mode.invoke(m.thisObject));}catch(Throwable ignored){}}
                    protected void afterHookedMethod(MethodHookParam m){if(m.hasThrowable()||binding.owners.isEmpty())return;try{Object before=m.getObjectExtra("hyperlux.window.previous_mode");if(before instanceof Integer&&!before.equals(access.mode.invoke(m.thisObject))){Object service=access.serviceForStack(m.thisObject);if(service!=null){binding.service=service;binding.request(service);}}}catch(Throwable unavailable){binding.error=unavailable.toString();}}
                }));
                bindings.put(loader,binding);errors.remove(loader);tx.commit();
            }
        }catch(Throwable unsupported){errors.put(loader,unsupported.toString());XposedBridge.log("HyperLux minimized-window brightness unavailable: "+unsupported);}
    }
    static Binding binding(HookRuntime s){return bindings.get(s.owner.getClass().getClassLoader());}
    static boolean supported(HookRuntime s)throws Exception{Binding b=binding(s);if(b==null)throw new NoSuchMethodException(errors.getOrDefault(s.owner.getClass().getClassLoader(),"挂起小窗亮度接口尚未匹配"));return true;}
    static void configure(HookRuntime s,boolean on){Binding b=binding(s);if(b!=null)b.configure(s,on&&!s.closed&&s.appliesToUser()&&s.brightnessControl.mainDisplay());}
    static JSONObject status(HookRuntime s)throws JSONException{Binding b=binding(s);return new JSONObject().put("profile",b==null?"":WindowBrightnessAccess.ROUTE).put("enabled",b!=null&&b.owners.contains(s)).put("blocked_requests",b==null?0:b.blocked.get()).put("reevaluation_queued",b!=null&&b.queued.get()).put("detail",b==null?errors.getOrDefault(s.owner.getClass().getClassLoader(),""):b.error);}
}
