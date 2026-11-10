package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Optional independent-brightness ABI; no model/OS whitelist or window value learning. */
final class AppIndependentAccess {
    static final Map<Class<?>,AppIndependentAccess> profiles=new java.util.concurrent.ConcurrentHashMap<>();
    static final Map<Class<?>,AppIndependentAccess> probes=new java.util.concurrent.ConcurrentHashMap<>();
    static final Map<Class<?>,String> errors=new java.util.concurrent.ConcurrentHashMap<>();
    final Field controller,display,physical,hbm,configuration,brightnessController,automatic;final Method normal,maximum,override,temporary,settingsChanged,consumePending,clearTemporary,userBrightness;
    AppIndependentAccess(Class<?> owner)throws Exception{
        ClassLoader cl=owner.getClassLoader();Class<?> dpc=Class.forName("com.android.server.display.DisplayPowerController",false,cl),dbc=Class.forName("com.android.server.display.brightness.DisplayBrightnessController",false,cl);
        controller=AppBrightnessIsolation.field(owner,"mDisplayPowerController",dpc);display=AppBrightnessIsolation.field(dpc,"mDisplayId",int.class);physical=AppBrightnessIsolation.field(dpc,"mUniqueDisplayId",String.class);
        hbm=AppBrightnessIsolation.field(owner,"mHbmController",Class.forName("com.android.server.display.HighBrightnessModeController",false,cl));configuration=AppBrightnessIsolation.field(owner,"mDisplayDeviceConfig",Class.forName("com.android.server.display.DisplayDeviceConfig",false,cl));
        normal=AppBrightnessIsolation.method(hbm.getType(),"getNormalBrightnessMax",float.class);maximum=AppBrightnessIsolation.method(hbm.getType(),"getCurrentBrightnessMax",float.class);
        brightnessController=AppBrightnessIsolation.field(dpc,"mDisplayBrightnessController",dbc);override=AppBrightnessIsolation.method(dbc,"isAppliedOverrideStrategy",boolean.class);temporary=AppBrightnessIsolation.method(dpc,"setTemporaryBrightness",void.class,float.class);
        Class<?> abc=Class.forName("com.android.server.display.AutomaticBrightnessController",false,cl);
        automatic=AppBrightnessIsolation.field(dpc,"mAutomaticBrightnessController",abc);
        userBrightness=AppBrightnessIsolation.method(abc,"setScreenBrightnessByUser",boolean.class,float.class,float.class);
        settingsChanged=AppBrightnessIsolation.method(dbc,"handleSettingsChange",void.class);
        consumePending=AppBrightnessIsolation.method(dbc,"updateUserSetScreenBrightness",boolean.class);
        clearTemporary=AppBrightnessIsolation.method(dbc,"setTemporaryBrightness",void.class,Float.class);
    }
    static synchronized void install(Class<?> owner){if(profiles.containsKey(owner)||errors.containsKey(owner))return;try{
        AppIndependentAccess p=inspect(owner);try(HookTransaction tx=new HookTransaction()){
            tx.add(XposedBridge.hookMethod(p.consumePending,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam call){
                if(call.hasThrowable())return;
                HookRuntime[] states;synchronized(HookEntry.states){states=HookEntry.states.values().toArray(new HookRuntime[0]);}
                for(HookRuntime s:states)try{
                    if(!s.closed&&s.owner.getClass()==owner&&s.onDisplayThread()&&p.brightnessController.get(p.dpc(s))==call.thisObject)s.brightnessControl.independent.nativeFrame();
                }catch(Throwable ignored){}
            }}));
            tx.add(XposedBridge.hookMethod(p.userBrightness,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam call){
                synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values())try{
                    if(!s.closed&&s.owner.getClass()==owner&&s.onDisplayThread()&&s.brightnessControl.independent.blocksLearning()&&p.automatic.get(p.dpc(s))==call.thisObject){call.setResult(false);return;}
                }catch(Throwable ignored){}}
            }}));
            tx.add(XposedBridge.hookMethod(p.temporary,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam call){
                if(AppBrightnessIsolation.current.get()!=null||call.hasThrowable()||call.args.length!=1||!(call.args[0] instanceof Float))return;float value=(Float)call.args[0];if(!Float.isFinite(value)||value<=0||value>=1)return;
                synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values())try{if(!s.closed&&s.owner.getClass()==owner&&p.controller.get(s.owner)==call.thisObject)s.brightnessControl.independent.enqueueUser(value);}catch(Throwable ignored){}}
            }}));profiles.put(owner,p);tx.commit();}
        }catch(Throwable unavailable){errors.put(owner,unavailable.toString());XposedBridge.log("HyperLux independent brightness unavailable: "+unavailable);}}
    Object dpc(HookRuntime s)throws Exception{Object d=controller.get(s.owner);if(d==null||display.getInt(d)!=0)throw new IllegalStateException("等待当前主屏");return d;}
    String identity(HookRuntime s)throws Exception{String id=(String)physical.get(dpc(s));if(id==null||!id.startsWith("local:")||id.length()>256||hbm.get(s.owner)==null||configuration.get(s.owner)==null)throw new IllegalStateException("等待物理主屏标定");return id;}
    float ceiling(HookRuntime s)throws Exception{Object range=hbm.get(s.owner);if(range==null)throw new IllegalStateException("普通亮度范围尚未就绪");float a=(Float)normal.invoke(range),b=(Float)maximum.invoke(range);if(!Float.isFinite(a)||!Float.isFinite(b)||a<=0||a>b||b>1)throw new IllegalStateException("普通亮度范围无效");return Math.min(a,b);}
    boolean ordinaryScene(HookRuntime s)throws Exception{return Boolean.FALSE.equals(s.hdrActive())&&s.thermalSeverity>=0&&s.thermalSeverity<3;}
    void settle(HookRuntime s)throws Exception{
        // Consume the restored setting while still manual. A late settings notification
        // reads that same baseline, so the next automatic frame is not a user gesture.
        Object dbc=brightnessController.get(dpc(s));settingsChanged.invoke(dbc);
        consumePending.invoke(dbc);consumePending.invoke(dbc);
        clearTemporary.invoke(dbc,Float.valueOf(Float.NaN));
    }
    static synchronized AppIndependentAccess inspect(Class<?> owner)throws Exception{
        AppIndependentAccess p=probes.get(owner);if(p==null){p=new AppIndependentAccess(owner);probes.put(owner,p);}return p;
    }
    static boolean supported(HookRuntime s)throws Exception{if(errors.containsKey(s.owner.getClass()))throw new NoSuchMethodException(errors.get(s.owner.getClass()));AppIndependentAccess p=inspect(s.owner.getClass());p.identity(s);p.ceiling(s);return true;}
}
