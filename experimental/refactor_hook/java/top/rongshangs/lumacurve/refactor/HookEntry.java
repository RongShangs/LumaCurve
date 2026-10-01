package top.rongshangs.lumacurve.refactor;

import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    static final String OWNER="com.android.server.display.DisplayPowerControllerImpl";
    static final String REFACTOR="com.android.server.display.RefactorNitController";
    static final Map<Object,HookRuntime> states=Collections.synchronizedMap(new IdentityHashMap<>());
    static final Set<Class<?>> installed=new HashSet<>();
    static final Map<Object,Boolean> failedOwners=Collections.synchronizedMap(new WeakHashMap<>());
    static final List<XC_MethodHook.Unhook> discovery=new ArrayList<>();
    static boolean discovering;
    static final Set<Class<?>> thermalSupported=new HashSet<>();
    static Field field(Class<?> cls,String name)throws Exception{Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;}
    static Object get(Object obj,String name)throws Exception{return field(obj.getClass(),name).get(obj);}
    static String os()throws Exception{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.mi.os.version.name");}
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p)throws Throwable {
        if(android.os.Process.myUid()!=1000 || !(p.packageName.equals("android")||p.packageName.equals("system")) ||
                !(p.processName.equals("android")||p.processName.equals("system")||p.processName.equals("system_server")))return;
        if(!os().startsWith("OS4"))return;
        XposedBridge.log("LumaCurve release-2.0.0-r2: system_server entry, package="+p.packageName+", process="+p.processName);
        if(discover(p.classLoader))return;
        // Some ROMs create a separate MIUI services loader later. Remove this startup
        // discovery hook as soon as the owner class is found; never poll brightness.
        XC_MethodHook watch=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam param){
            if(param.hasThrowable() || !(param.getResult() instanceof Class))return;
            Class<?> c=(Class<?>)param.getResult();
            if(c.getName().equals(OWNER)||c.getName().equals(REFACTOR))discover(c.getClassLoader());
        }};
        discovery.addAll(XposedBridge.hookAllMethods(ClassLoader.class,"loadClass",watch));
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            synchronized(HookEntry.class){for(XC_MethodHook.Unhook u:discovery)u.unhook();discovery.clear();}
        },45000);
    }
    static synchronized boolean discover(ClassLoader loader){
        if(discovering)return false;
        discovering=true;
        try {
            Class<?> owner=Class.forName(OWNER,false,loader);
            if(installed.contains(owner))return true;
            Class<?> ref=Class.forName(REFACTOR,false,owner.getClassLoader());
            Method defaults=ref.getDeclaredMethod("defaultAtLux",float.class);
            Method reset=ref.getDeclaredMethod("resetDefaultSpline");
            Method output=ref.getDeclaredMethod("getCurrentNit",float.class,float.class,boolean.class);
            Method actual=owner.getDeclaredMethod("getRefactorBrightness",float.class,float.class,boolean.class,boolean.class);
            if(defaults.getReturnType()!=float.class||reset.getReturnType()!=void.class||output.getReturnType()!=float.class||actual.getReturnType()!=float.class)
                throw new IllegalStateException("Refactor signature changed");
            for(String name:new String[]{"mContext","mHandler","mRefactorNitController","mDisplayId"})field(owner,name);
            for(String name:new String[]{"mDisplayId","mUserSerial","mDefaultLogicalCurve"})field(ref,name);
            List<XC_MethodHook.Unhook> added=new ArrayList<>();
            try {
                installThermal(owner);
                ResponseTuning.install(owner.getClassLoader());
                Method memory=ref.getDeclaredMethod("updateLogicalCurve",float.class,float.class);
                if(memory.getReturnType()!=void.class)throw new IllegalStateException("manual memory ABI");
                added.add(XposedBridge.hookMethod(memory,new XC_MethodHook(){
                    protected void beforeHookedMethod(MethodHookParam p){
                        HookRuntime state=states.get(p.thisObject);
                        if(state==null||state.closed||!state.phase.equals("active")||!state.appliesToUser()||Looper.myLooper()!=state.handler.getLooper())return;
                        try{
                            if(!((Boolean)state.kernel.get("mUseAutoBrightness")))return;
                            float lux=(Float)p.args[0],desired=(Float)p.args[1];
                            if(state.memoryStrength==0){p.setResult(null);state.memoryEvent(lux,desired,Float.NaN);return;}
                            float applied=desired;
                            if(state.memoryStrength<1)applied=state.memoryPolicy.remember(lux,desired,state.kernel.currentAt(lux),state.memoryStrength,SystemClock.elapsedRealtime());
                            p.args[1]=applied;state.memoryEvent(lux,desired,applied);
                        }catch(Throwable error){XposedBridge.log("LumaCurve memory left official: "+error);}
                    }
                    protected void afterHookedMethod(MethodHookParam p){HookRuntime state=states.get(p.thisObject);if(state!=null&&!p.hasThrowable())state.queuePublish();}
                }));
                // The adapter changes baseline fields, not private return values.
                // OEM reset/interpolation code continues to run even when inlined.
                added.add(XposedBridge.hookMethod(output,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam param){
                    HookRuntime state=states.get(param.thisObject);if(state==null||param.hasThrowable())return;
                    if(Float.isNaN((Float)param.args[1]) && !((Boolean)param.args[2]))
                        state.sample((Float)param.args[0],(Float)param.getResult());
                }}));
                added.add(XposedBridge.hookMethod(actual,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam param){
                    attach(param.thisObject);
                    try {HookRuntime state=states.get(get(param.thisObject,"mRefactorNitController"));
                        if(state!=null && state.kernel.plan()!=null && !state.appliesToUser())state.userChanged();
                    }catch(Throwable error){XposedBridge.log(error);}
                }}));
                added.addAll(XposedBridge.hookAllMethods(owner,"init",new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam param){if(!param.hasThrowable())attach(param.thisObject);}}));
                installed.add(owner);
            }catch(Throwable error){for(XC_MethodHook.Unhook u:added)u.unhook();throw error;}
            for(XC_MethodHook.Unhook u:discovery)u.unhook();discovery.clear();
            XposedBridge.log("LumaCurve release-2.0.0-r2: verified hooks installed");return true;
        }catch(ClassNotFoundException absent){return false;}
        catch(Throwable error){XposedBridge.log("LumaCurve incompatible Refactor interface: "+error);return false;}
        finally{discovering=false;}
    }
    static void installThermal(Class<?> owner){
        if(thermalSupported.contains(owner))return;
        List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try{
            if(field(owner,"mThermalMaxBrightness").getType()!=float.class)throw new IllegalStateException("thermal cap type");
            Class<?> reason=Class.forName("com.android.server.display.brightness.BrightnessReason",false,owner.getClassLoader());
            Method hdr=owner.getDeclaredMethod("adjustBrightness",float.class,reason);
            Method sdr=owner.getDeclaredMethod("adjustSdrBrightness",float.class,boolean.class,reason,boolean.class,boolean.class);
            if(hdr.getReturnType()!=float.class||sdr.getReturnType()!=float.class)throw new IllegalStateException("thermal ABI");
            XC_MethodHook hook=new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){
                    try{
                        HookRuntime state=states.get(get(p.thisObject,"mRefactorNitController"));
                        if(state==null||state.closed||Looper.myLooper()!=state.handler.getLooper()||!state.relaxThermal())return;
                        float cap=((Number)get(p.thisObject,"mThermalMaxBrightness")).floatValue();
                        if(!Float.isFinite(cap)||cap<0||cap>1)return;
                        p.setObjectExtra("luma.thermal.saved",Float.valueOf(cap));
                        field(owner,"mThermalMaxBrightness").setFloat(p.thisObject,Float.NaN);
                        state.thermalCall(cap,((Number)p.args[0]).floatValue());
                    }catch(Throwable error){XposedBridge.log("LumaCurve thermal left official: "+error);}
                }
                protected void afterHookedMethod(MethodHookParam p){
                    Object saved=p.getObjectExtra("luma.thermal.saved");if(saved==null)return;
                    try{field(owner,"mThermalMaxBrightness").setFloat(p.thisObject,(Float)saved);}
                    catch(Throwable error){XposedBridge.log("LumaCurve thermal restore: "+error);}
                }
            };
            hooks.add(XposedBridge.hookMethod(hdr,hook));hooks.add(XposedBridge.hookMethod(sdr,hook));
            thermalSupported.add(owner);
        }catch(Throwable unavailable){for(XC_MethodHook.Unhook h:hooks)h.unhook();XposedBridge.log("LumaCurve thermal option unavailable: "+unavailable);}
    }
    static void attach(Object owner){
        try {
            if(failedOwners.containsKey(owner))return;
            if(((Number)get(owner,"mDisplayId")).intValue()!=0)return;
            Object ref=get(owner,"mRefactorNitController");if(ref==null||states.containsKey(ref))return;
            Handler handler=(Handler)get(owner,"mHandler");if(handler==null)return;
            if(Looper.myLooper()!=handler.getLooper()){handler.post(()->attach(owner));return;}
            // A replacement controller owns the primary display. Release the old observer.
            synchronized(states){for(HookRuntime old:states.values())old.close();states.clear();}
            HookRuntime state=new HookRuntime(owner,ref,handler);
            states.put(ref,state);state.start();
        }catch(Throwable error){failedOwners.put(owner,true);XposedBridge.log("LumaCurve attach failed, OEM left in control: "+error);HookRuntime.reportAttachFailure(owner,error);}
    }
}
