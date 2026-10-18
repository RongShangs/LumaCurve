package top.rongshangs.lumacurve.refactor.hooking;

import java.lang.reflect.Method;
import java.util.*;

/** Owns hook handles for future generation retirement; never calls a framework implicitly. */
public final class XposedBridge {
    public interface Backend {
        XC_MethodHook.Unhook hook(Method method,XC_MethodHook callback);
        Object original(Method method,Object receiver,Object[] args)throws Throwable;
        void log(String text);
    }
    private static volatile Backend backend;
    private static final Set<XC_MethodHook.Unhook> handles=Collections.newSetFromMap(new IdentityHashMap<>());
    public static synchronized boolean initialize(Backend value){if(backend!=null)return false;backend=value;return true;}
    public static synchronized int hookCount(){return handles.size();}
    public static XC_MethodHook.Unhook hookMethod(Method method,XC_MethodHook callback){
        Backend current; synchronized(XposedBridge.class){current=backend;}
        if(current==null)throw new IllegalStateException("Hook backend not initialized");
        XC_MethodHook.Unhook real=current.hook(method,callback);
        final XC_MethodHook.Unhook[] tracked=new XC_MethodHook.Unhook[1];
        tracked[0]=new XC_MethodHook.Unhook(()->{real.unhook();synchronized(XposedBridge.class){handles.remove(tracked[0]);}});
        synchronized(XposedBridge.class){handles.add(tracked[0]);}return tracked[0];
    }
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> owner,String name,XC_MethodHook callback){
        Set<XC_MethodHook.Unhook> result=new LinkedHashSet<>();
        try{for(Method m:owner.getDeclaredMethods())if(m.getName().equals(name))result.add(hookMethod(m,callback));return result;}
        catch(Throwable failure){for(XC_MethodHook.Unhook h:result)h.unhook();throw failure;}
    }
    public static Object invokeOriginalMethod(Method method,Object receiver,Object[] args)throws Throwable {
        if(backend==null)throw new IllegalStateException("Hook backend not initialized");return backend.original(method,receiver,args);
    }
    public static void log(String text){Backend current=backend;if(current!=null)current.log(text);}
    public static void unhookAll(){XC_MethodHook.Unhook[] all;synchronized(XposedBridge.class){all=handles.toArray(new XC_MethodHook.Unhook[0]);}for(XC_MethodHook.Unhook hook:all)hook.unhook();}
}
