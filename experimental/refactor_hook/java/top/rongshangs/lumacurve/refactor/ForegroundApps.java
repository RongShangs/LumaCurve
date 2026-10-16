package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import de.robv.android.xposed.*;

/** Event source only. Never calls Settings/providers/services while WM holds its lock. */
final class ForegroundApps {
    static final Set<BrightnessControl> targets=Collections.newSetFromMap(new IdentityHashMap<BrightnessControl,Boolean>());
    static boolean installed,attempted,multiWindowSupported;
    static String error="";
    // No WM service query on the display handler. One lazy, bounded worker;
    // it exits after ten idle seconds and never polls or changes brightness.
    static Executor queryExecutor=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8),r->{Thread t=new Thread(r,"HyperLux-visible-apps");t.setDaemon(true);return t;});
    static void query(BrightnessControl target,long generation){
        queryExecutor.execute(()->{
            synchronized(target){if(!target.foregroundQuerying||target.foregroundQueryGeneration!=generation||target.s.closed)return;}
            Snapshot value;String detail="";
            try{value=query();}catch(Throwable unavailable){value=Snapshot.unknown();detail="前台应用读取失败："+unavailable;}
            final Snapshot result=value;final String message=detail;
            target.s.handler.post(()->target.finishForegroundQuery(generation,result,message));
        });
    }
    static final class Snapshot {
        final String pkg;final int user,display;final Set<String> visible;final boolean visibilityKnown;
        Snapshot(String p,int u,int d){this(p,u,d,null);}
        Snapshot(String p,int u,int d,Set<String> packages){pkg=p==null?"":p;user=u;display=d;visibilityKnown=packages!=null;visible=packages==null?Collections.emptySet():Collections.unmodifiableSet(new LinkedHashSet<>(packages));}
        boolean contains(String p){return visibilityKnown?visible.contains(p):user==0&&display==0&&pkg.equals(p);}
        String selected(Set<String> apps){if(contains(pkg)&&apps.contains(pkg))return pkg;for(String p:visible)if(apps.contains(p))return p;return "";}
        static Snapshot unknown(){return new Snapshot("",-1,-1);}
    }
    static synchronized boolean install(ClassLoader loader){
        if(attempted)return installed;attempted=true;
        try{
            Class<?> service=Class.forName("com.android.server.wm.ActivityTaskManagerService",false,loader),record=Class.forName("com.android.server.wm.ActivityRecord",false,loader);
            Method changed=service.getDeclaredMethod("setLastResumedActivityUncheckLocked",record,String.class);
            if(changed.getReturnType()!=void.class||Modifier.isStatic(changed.getModifiers()))throw new IllegalStateException("Unknown foreground callback");changed.setAccessible(true);
            Field last=HookEntry.field(service,"mLastResumedActivity"),pkg=HookEntry.field(record,"packageName");
            Field user;try{user=HookEntry.field(record,"mUserId");}catch(NoSuchFieldException old){user=HookEntry.field(record,"userId");}
            final Field userId=user;Method display=record.getDeclaredMethod("getDisplayId");display.setAccessible(true);
            if(last.getType()!=record||pkg.getType()!=String.class||userId.getType()!=int.class||display.getReturnType()!=int.class||Modifier.isStatic(last.getModifiers())||Modifier.isStatic(pkg.getModifiers())||Modifier.isStatic(userId.getModifiers())||Modifier.isStatic(display.getModifiers()))throw new IllegalStateException("Unknown foreground fields");
            XposedBridge.hookMethod(changed,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;
                synchronized(ForegroundApps.class){if(targets.isEmpty())return;}
                Snapshot value;
                try{Object top=last.get(p.thisObject);value=top==null?Snapshot.unknown():new Snapshot((String)pkg.get(top),userId.getInt(top),(Integer)display.invoke(top));}
                catch(Throwable unavailable){value=Snapshot.unknown();}
                // Only read immutable identity and enqueue. No binder/provider I/O here.
                synchronized(ForegroundApps.class){for(BrightnessControl c:targets)c.enqueueForeground(value);}
            }});installed=true;
            // Visibility changes also matter when focus stays in a floating window.
            // Probe the complete optional path before subscribing to it.
            try{
                Method tasks=service.getMethod("getTasks",int.class,boolean.class,boolean.class,int.class);
                Class<?> info=Class.forName("android.app.TaskInfo",false,loader);
                Field visible=visibilityField(info);
                Method changedVisibility=record.getDeclaredMethod("setVisibleRequested",boolean.class);
                if(!List.class.isAssignableFrom(tasks.getReturnType())||Modifier.isStatic(tasks.getModifiers())||visible.getType()!=boolean.class||Modifier.isStatic(visible.getModifiers())||changedVisibility.getReturnType()!=boolean.class||Modifier.isStatic(changedVisibility.getModifiers()))throw new IllegalStateException("Unknown visible-task interface");
                for(String name:new String[]{"userId","displayId"}){Field identity=HookEntry.field(info,name);if(identity.getType()!=int.class||Modifier.isStatic(identity.getModifiers()))throw new IllegalStateException("Unknown task identity");}
                Field top=HookEntry.field(info,"topActivity");if(!top.getType().getName().equals("android.content.ComponentName")||Modifier.isStatic(top.getModifiers()))throw new IllegalStateException("Unknown task component");
                changedVisibility.setAccessible(true);
                XposedBridge.hookMethod(changedVisibility,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable()||!Boolean.TRUE.equals(p.getResult()))return;
                    synchronized(ForegroundApps.class){for(BrightnessControl c:targets)c.invalidateForeground();}
                }});multiWindowSupported=true;
            }catch(Throwable unavailable){error="多窗口可见性接口不可用，沿用焦点判断："+unavailable;}
        }catch(Throwable unavailable){error=unavailable.toString();XposedBridge.log("HyperLux foreground events unavailable: "+unavailable);}
        return installed;
    }
    static void attach(BrightnessControl target){
        // Power/service reads must not hold the monitor used by WM callbacks.
        if(target.options.manualApps.isEmpty()||!target.enabled()||!target.mainDisplay()||!target.awake()){detach(target);return;}
        if(!install(target.s.owner.getClass().getClassLoader()))return;
        boolean added;synchronized(ForegroundApps.class){added=targets.add(target);}
        if(added)refresh(target);
    }
    static void detach(BrightnessControl target){synchronized(ForegroundApps.class){targets.remove(target);}target.cancelForeground();}
    static void refresh(BrightnessControl target){
        // Schedule one initial refresh; the service read itself runs off the display handler.
        target.s.handler.removeCallbacks(target.foregroundQuery);target.s.handler.post(target.foregroundQuery);
    }
    static Snapshot query()throws Exception {
        Class<?> atm=Class.forName("android.app.ActivityTaskManager");Method get=atm.getDeclaredMethod("getService");get.setAccessible(true);Object service=get.invoke(null);
        Method focused=service.getClass().getMethod("getFocusedRootTaskInfo");focused.setAccessible(true);Object info=focused.invoke(service);
        Object component=info==null?null:HookEntry.field(info.getClass(),"topActivity").get(info);
        Set<String> visible=null;
        if(multiWindowSupported){
            Method tasks=service.getClass().getMethod("getTasks",int.class,boolean.class,boolean.class,int.class);tasks.setAccessible(true);
            Object result=tasks.invoke(service,128,false,false,0);if(!(result instanceof List)||((List<?>)result).size()>128)throw new IllegalStateException("Invalid visible-task response");
            visible=new LinkedHashSet<>();
            for(Object task:(List<?>)result){
                if(task==null||HookEntry.field(task.getClass(),"userId").getInt(task)!=0||HookEntry.field(task.getClass(),"displayId").getInt(task)!=0||!visibilityField(task.getClass()).getBoolean(task))continue;
                Object top=HookEntry.field(task.getClass(),"topActivity").get(task);if(top==null)continue;
                visible.add((String)top.getClass().getMethod("getPackageName").invoke(top));
            }
        }
        if(component==null)return new Snapshot("",-1,-1,visible);
        Method name=component.getClass().getMethod("getPackageName");
        return new Snapshot((String)name.invoke(component),HookEntry.field(info.getClass(),"userId").getInt(info),HookEntry.field(info.getClass(),"displayId").getInt(info),visible);
    }
    static Field visibilityField(Class<?> info)throws Exception{try{return HookEntry.field(info,"isVisibleRequested");}catch(NoSuchFieldException old){return HookEntry.field(info,"isVisible");}}
}
