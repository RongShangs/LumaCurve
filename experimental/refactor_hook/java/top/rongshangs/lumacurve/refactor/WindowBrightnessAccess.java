package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import android.os.Handler;

/** Exact WM/mini-window boundary. Never infers a window state from focus or size. */
final class WindowBrightnessAccess {
    static final String ROUTE="wm_mini_pin_gate";
    final Class<?> implementation,task,stack,manager;
    final Method gate,windowTask,windowDisplay,rootId,mini,pin,modeChanged,mode;
    final Method traversal;
    final Field wm,atm,managerField,globalLock,handler,taskUser,taskWm,stackTask,attrs,windowType;
    WindowBrightnessAccess(ClassLoader loader)throws Exception{
        implementation=Class.forName("com.android.server.wm.WindowManagerServiceImpl",false,loader);
        Class<?> window=Class.forName("com.android.server.wm.WindowState",false,loader),service=Class.forName("com.android.server.wm.WindowManagerService",false,loader);
        Class<?> activity=Class.forName("com.android.server.wm.ActivityTaskManagerService",false,loader),lock=Class.forName("com.android.server.wm.WindowManagerGlobalLock",false,loader);
        task=Class.forName("com.android.server.wm.Task",false,loader);stack=Class.forName("com.android.server.wm.MiuiFreeFormActivityStack",false,loader);
        manager=Class.forName("com.android.server.wm.MiuiFreeFormManagerService",false,loader);
        Class<?> managerBase=Class.forName("com.android.server.wm.MiuiFreeFormManagerServiceStub",false,loader),layout=Class.forName("android.view.WindowManager$LayoutParams",false,loader);
        if(!managerBase.isAssignableFrom(manager))throw new IllegalStateException("小窗服务类型不匹配");
        gate=method(implementation,"shouldApplyOverrideBrightness",boolean.class,window);
        windowTask=method(window,"getTask",task);windowDisplay=method(window,"getDisplayId",int.class);rootId=method(task,"getRootTaskId",int.class);
        attrs=field(window,"mAttrs",layout);windowType=field(layout,"type",int.class);
        taskUser=field(task,"mUserId",int.class);taskWm=field(task,"mWmService",service);stackTask=field(stack,"mTask",task);
        wm=field(implementation,"mWmService",service);atm=field(service,"mAtmService",activity);managerField=field(activity,"mMiuiFreeFormManagerService",managerBase);
        globalLock=field(service,"mGlobalLock",lock);handler=HookEntry.field(service,"mH");if(Modifier.isStatic(handler.getModifiers())||!Handler.class.isAssignableFrom(handler.getType()))throw new IllegalStateException("窗口调度接口不匹配");
        mini=method(manager,"isInMiniFreeFormMode",boolean.class,int.class);pin=method(manager,"inPinMode",boolean.class,int.class);
        mode=method(stack,"getMiuiFreeFromWindowMode",int.class);modeChanged=method(stack,"setStackFreeFormMode",void.class,int.class);traversal=method(service,"requestTraversal",void.class);
    }
    static Method method(Class<?> type,String name,Class<?> result,Class<?>...args)throws Exception{
        for(Class<?> at=type;at!=null;at=at.getSuperclass())try{Method m=at.getDeclaredMethod(name,args);if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new IllegalStateException("窗口接口类型不匹配："+name);m.setAccessible(true);return m;}catch(NoSuchMethodException absent){}
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    static Field field(Class<?> type,String name,Class<?> result)throws Exception{Field f=HookEntry.field(type,name);if(f.getType()!=result||Modifier.isStatic(f.getModifiers()))throw new IllegalStateException("窗口字段类型不匹配："+name);return f;}
    Object service(Object implementation)throws Exception{return wm.get(implementation);}
    boolean suspended(Object implementation,Object window)throws Exception{
        Object service=service(implementation);if(service==null||!Thread.holdsLock(globalLock.get(service)))return false;
        if(((Integer)windowDisplay.invoke(window))!=0)return false;
        Object layout=attrs.get(window);if(layout==null)return false;int type=windowType.getInt(layout);if(!(type>=1&&type<=99||type>=1000&&type<=1999))return false;
        Object current=windowTask.invoke(window);if(current==null||taskUser.getInt(current)!=0)return false;
        int id=(Integer)rootId.invoke(current);if(id<0)return false;
        Object owner=atm.get(service),value=owner==null?null:managerField.get(owner);if(!manager.isInstance(value))throw new IllegalStateException("等待实际小窗服务");
        return Boolean.TRUE.equals(mini.invoke(value,id))||Boolean.TRUE.equals(pin.invoke(value,id));
    }
    Object serviceForStack(Object value)throws Exception{Object current=stackTask.get(value);return current==null||taskUser.getInt(current)!=0?null:taskWm.get(current);}
    boolean post(Object service,Runnable action)throws Exception{Object value=handler.get(service);return value instanceof Handler&&((Handler)value).post(action);}
    void reevaluate(Object service)throws Exception{Object lock=globalLock.get(service);if(lock==null)throw new IllegalStateException("等待窗口锁");synchronized(lock){traversal.invoke(service);}}
}
