package top.rongshangs.lumacurve.refactor.hooking;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;

/** Module-owned callback shape; deliberately independent of the legacy Xposed API. */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam p)throws Throwable {}
    protected void afterHookedMethod(MethodHookParam p)throws Throwable {}
    public final void before(MethodHookParam p)throws Throwable {beforeHookedMethod(p);}
    public final void after(MethodHookParam p)throws Throwable {afterHookedMethod(p);}
    public static final class Unhook {
        private Runnable remove;
        public Unhook(Runnable remove){this.remove=remove;}
        public synchronized void unhook(){if(remove!=null){Runnable r=remove;r.run();remove=null;}}
    }
    public static final class MethodHookParam {
        public Object thisObject;public Object[] args;public Member method;
        private Object result;private Throwable throwable;private boolean early;
        private final Map<String,Object> extras=new HashMap<>();
        public boolean hasThrowable(){return throwable!=null;}
        public Throwable getThrowable(){return throwable;}
        public Object getResult(){return result;}
        public void setResult(Object value){result=value;throwable=null;early=true;}
        public void setThrowable(Throwable value){throwable=value;result=null;early=true;}
        public boolean returnEarly(){return early;}
        public void outcome(Object value,Throwable failure){result=value;throwable=failure;early=false;}
        public void setObjectExtra(String key,Object value){extras.put(key,value);}
        public Object getObjectExtra(String key){return extras.get(key);}
    }
}
