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
    static final Map<Object,AttachFailure> failedOwners=Collections.synchronizedMap(new WeakHashMap<>());
    static final class AttachFailure{final int attempts;final long retryAt;AttachFailure(int count){attempts=count;retryAt=SystemClock.uptimeMillis()+count*1000L;}}
    static final List<XC_MethodHook.Unhook> discovery=new ArrayList<>();
    static boolean discovering;
    static final ForegroundUser currentUser=new ForegroundUser();
    static void refreshCurrentUser(android.content.Context context){
        long identity=Binder.clearCallingIdentity();
        try{currentUser.refresh(()->{
            try{
                int id=((Number)Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null)).intValue();
                if(id<0)return -1;
                // The display callback carries a serial, not a user ID. They can differ.
                UserManager users=(UserManager)context.getSystemService(android.content.Context.USER_SERVICE);
                UserHandle handle=(UserHandle)UserHandle.class.getMethod("of",int.class).invoke(null,id);
                long serial=users.getSerialNumberForUser(handle);
                return serial>=0&&serial<=Integer.MAX_VALUE?(int)serial:-1;
            }catch(Exception unavailable){return -1;}
        });}finally{Binder.restoreCallingIdentity(identity);}
    }
    static HookRuntime ownerState(Object owner){synchronized(states){for(HookRuntime s:states.values())if(s.owner==owner&&!s.closed)return s;}return null;}
    static void reattach(Object owner,Object mapper){HookRuntime old=states.get(mapper);if(old==null||old.owner!=owner)return;states.remove(mapper);old.close();failedOwners.remove(owner);attach(owner);}
    static final Set<Class<?>> thermalSupported=new HashSet<>();
    static final java.util.concurrent.ConcurrentMap<Class<?>,java.util.concurrent.ConcurrentMap<String,Field>> fieldCache=new java.util.concurrent.ConcurrentHashMap<>();
    static Field field(Class<?> cls,String name)throws Exception{
        java.util.concurrent.ConcurrentMap<String,Field> fields=fieldCache.computeIfAbsent(cls,k->new java.util.concurrent.ConcurrentHashMap<>());
        Field f=fields.get(name);if(f!=null)return f;f=TraditionalAdapter.find(cls,name);Field existing=fields.putIfAbsent(name,f);return existing==null?f:existing;
    }
    static Object get(Object obj,String name)throws Exception{return field(obj.getClass(),name).get(obj);}
    static String os()throws Exception{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.mi.os.version.name");}
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p)throws Throwable {
        if(android.os.Process.myUid()!=1000 || !(p.packageName.equals("android")||p.packageName.equals("system")) ||
                !(p.processName.equals("android")||p.processName.equals("system")||p.processName.equals("system_server")))return;
        if(!os().startsWith("OS4"))return;
        InjectionStatus.write(null,"injected","LSPosed 已加载，正在识别设备曲线");
        XposedBridge.log("HyperLux "+AppBuild.BUILD+": system_server entry, package="+p.packageName+", process="+p.processName);
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
            if(installed.isEmpty())InjectionStatus.write(null,"curve_incompatible","LSPosed 已加载，但尚未找到兼容的系统显示接口");
            else if(InjectionStatus.lastStage.isEmpty())InjectionStatus.write(null,"hooks_ready","LSPosed 已加载，等待读取设备曲线");
        },45000);
    }
    static synchronized boolean discover(ClassLoader loader){
        if(discovering)return false;
        discovering=true;
        try {
            Class<?> owner=Class.forName(OWNER,false,loader);
            if(installed.contains(owner))return true;
            for(String name:new String[]{"mContext","mHandler","mDisplayId"})field(owner,name);
            List<XC_MethodHook.Unhook> added=new ArrayList<>();
            try {
                installThermal(owner);
                ResponseTuning.install(owner.getClassLoader());AdvancedTuning.install(owner.getClassLoader());
                LowLightTuning.install(owner.getClassLoader());
                PipelineHooks.install(owner);OutdoorTuning.install(owner);
                try {
                Class<?> ref=Class.forName(REFACTOR,false,owner.getClassLoader());
                Method output=ref.getDeclaredMethod("getCurrentNit",float.class,float.class,boolean.class);
                if(output.getReturnType()!=float.class)throw new IllegalStateException("Refactor output ABI");
                Method memory=ref.getDeclaredMethod("updateLogicalCurve",float.class,float.class);
                if(memory.getReturnType()!=void.class)throw new IllegalStateException("manual memory ABI");
                added.add(XposedBridge.hookMethod(memory,new XC_MethodHook(){
                    protected void beforeHookedMethod(MethodHookParam p){
                        HookRuntime state=states.get(p.thisObject);
                        if(state==null||state.closed||!state.phase.equals("active")||!state.appliesToUser()||Looper.myLooper()!=state.handler.getLooper())return;
                        try{
                            if(!((Boolean)state.kernel.get("mUseAutoBrightness")))return;
                            state.lastManualAdjustment=SystemClock.uptimeMillis();
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
                }catch(Throwable optional){XposedBridge.log("HyperLux Refactor hooks unavailable; will inspect physical mapper: "+optional);}
                XC_MethodHook attachHook=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(!p.hasThrowable())attach(p.thisObject);}};
                added.addAll(XposedBridge.hookAllMethods(owner,"init",attachHook));
                added.addAll(XposedBridge.hookAllMethods(owner,"setUpAutoBrightness",attachHook));
                // updateAutoBrightness occurs after the ABC has been assigned by its constructor.
                added.addAll(XposedBridge.hookAllMethods(owner,"updateAutoBrightness",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){attach(p.thisObject);}}));
                added.addAll(XposedBridge.hookAllMethods(owner,"getRefactorBrightness",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){attach(p.thisObject);HookRuntime s=ownerState(p.thisObject);if(s!=null&&s.kernel.plan()!=null&&!s.appliesToUser())s.userChanged();}}));
                added.addAll(XposedBridge.hookAllMethods(owner,"handleOnSwitchUser",new XC_MethodHook(){
                    protected void beforeHookedMethod(MethodHookParam p){HookRuntime s=ownerState(p.thisObject);if(s!=null&&s.kernel.plan()!=null)s.userChanged();currentUser.switched(((Number)p.args[0]).intValue());}
                    protected void afterHookedMethod(MethodHookParam p){HookRuntime s=ownerState(p.thisObject);if(s!=null)s.queuePublish();}
                }));
                InjectionStatus.write(null,"hooks_ready","系统接口已连接，等待读取设备曲线");
                installed.add(owner);
            }catch(Throwable error){for(XC_MethodHook.Unhook u:added)u.unhook();throw error;}
            for(XC_MethodHook.Unhook u:discovery)u.unhook();discovery.clear();
            XposedBridge.log("HyperLux "+AppBuild.BUILD+": verified hooks installed");return true;
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
                        HookRuntime state=ownerState(p.thisObject);
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
            AttachFailure failure=failedOwners.get(owner);if(failure!=null&&(failure.attempts>=3||SystemClock.uptimeMillis()<failure.retryAt))return;
            if(((Number)get(owner,"mDisplayId")).intValue()!=0)return;
            Object ref=null;try{ref=get(owner,"mRefactorNitController");}catch(NoSuchFieldException absent){}
            Object mapper=null,abc=null;Boolean usesRefactor=null;
            try{mapper=get(owner,"mBrightnessMapper");Object impl=get(owner,"mAutomaticBrightnessControllerImpl");if(impl!=null)abc=get(impl,"mAutomaticBrightnessController");if(abc!=null)usesRefactor=(Boolean)get(abc,"mUseRefactorBrightnessPolicy");}catch(NoSuchFieldException absent){}
            String selected=BackendSelection.choose(usesRefactor,ref!=null,mapper!=null&&abc!=null);
            if(selected.equals("waiting")){InjectionStatus.write((android.content.Context)get(owner,"mContext"),"waiting_curve","LSPosed 已加载，设备曲线尚未就绪");return;}
            if(selected.equals("physical_mapping"))ref=mapper;
            if(states.containsKey(ref))return;
            Handler handler=(Handler)get(owner,"mHandler");if(handler==null)return;
            if(Looper.myLooper()!=handler.getLooper()){handler.post(()->attach(owner));return;}
            // Validate the replacement before releasing an existing observer.
            HookRuntime state=new HookRuntime(owner,ref,handler);
            if(state.kernel instanceof TraditionalAdapter)TraditionalHooks.install(ref.getClass());
            synchronized(states){for(HookRuntime old:states.values())old.close();states.clear();}
            failedOwners.remove(owner);
            states.put(ref,state);state.start();
        }catch(Throwable error){AttachFailure before=failedOwners.get(owner),failure=new AttachFailure(before==null?1:before.attempts+1);failedOwners.put(owner,failure);
            XposedBridge.log("HyperLux attach attempt "+failure.attempts+" failed, OEM left in control: "+error);
            try{InjectionStatus.write((android.content.Context)get(owner,"mContext"),"curve_incompatible","LSPosed 已加载，但当前设备曲线接口未兼容："+error);}catch(Throwable ignored){}
            if(failure.attempts<3){try{Handler handler=(Handler)get(owner,"mHandler");if(handler!=null)handler.postDelayed(()->attach(owner),failure.attempts*1000L);}catch(Throwable absent){}}
            else HookRuntime.reportAttachFailure(owner,error);
        }
    }
}
