package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Event source only. Never calls Settings/providers/services while WM holds its lock. */
final class ForegroundApps {
    static final Set<BrightnessControl> targets=Collections.newSetFromMap(new IdentityHashMap<BrightnessControl,Boolean>());
    static boolean installed,attempted;
    static String error="";
    static final class Snapshot {
        final String pkg;final int user,display;
        Snapshot(String p,int u,int d){pkg=p==null?"":p;user=u;display=d;}
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
        }catch(Throwable unavailable){error=unavailable.toString();XposedBridge.log("HyperLux foreground events unavailable: "+unavailable);}
        return installed;
    }
    static synchronized void attach(BrightnessControl target){
        if(target.options.manualApps.isEmpty()||!target.enabled()||!target.awake()){detach(target);return;}
        if(install(target.s.owner.getClass().getClassLoader())&&targets.add(target))refresh(target);
    }
    static synchronized void detach(BrightnessControl target){targets.remove(target);target.cancelForeground();}
    static void refresh(BrightnessControl target){
        // One query when enabling/resuming. Runs later on the existing display handler.
        target.s.handler.removeCallbacks(target.foregroundQuery);target.s.handler.post(target.foregroundQuery);
    }
    static Snapshot query()throws Exception {
        Class<?> atm=Class.forName("android.app.ActivityTaskManager");Method get=atm.getDeclaredMethod("getService");get.setAccessible(true);Object service=get.invoke(null);
        Method focused=service.getClass().getMethod("getFocusedRootTaskInfo");focused.setAccessible(true);Object info=focused.invoke(service);if(info==null)return Snapshot.unknown();
        Object component=HookEntry.field(info.getClass(),"topActivity").get(info);if(component==null)return Snapshot.unknown();
        Method name=component.getClass().getMethod("getPackageName");
        return new Snapshot((String)name.invoke(component),HookEntry.field(info.getClass(),"userId").getInt(info),HookEntry.field(info.getClass(),"displayId").getInt(info));
    }
}
