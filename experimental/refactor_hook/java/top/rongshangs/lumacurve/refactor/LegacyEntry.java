package top.rongshangs.lumacurve.refactor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import top.rongshangs.lumacurve.refactor.hooking.XC_MethodHook;
import top.rongshangs.lumacurve.refactor.hooking.XposedBridge;

/** Isolated fallback for existing API 82 frameworks. Modern entry never loads this class. */
public final class LegacyEntry implements IXposedHookLoadPackage {
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p)throws Throwable {
        XposedBridge.initialize(new LegacyBackend());
        XposedBridge.log("HyperLux "+AppBuild.BUILD+" legacy API 82 entry; process="+p.processName);
        if("com.android.systemui".equals(p.packageName)){ControlCenterHooks.install(p.classLoader);return;}
        HookEntry.handleLoadPackage(new HookEntry.LoadParam(p.packageName,p.processName,p.classLoader));
    }
    static final class LegacyBackend implements XposedBridge.Backend {
        public XC_MethodHook.Unhook hook(Method method,XC_MethodHook callback){
            final String slot="hyperlux.callback."+java.util.UUID.randomUUID();
            de.robv.android.xposed.XC_MethodHook.Unhook handle=de.robv.android.xposed.XposedBridge.hookMethod(method,new de.robv.android.xposed.XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam old)throws Throwable {
                    XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.method=old.method;p.thisObject=old.thisObject;p.args=old.args;
                    old.setObjectExtra(slot,p);callback.before(p);old.args=p.args;
                    if(p.returnEarly()){if(p.hasThrowable())old.setThrowable(p.getThrowable());else old.setResult(p.getResult());}
                }
                protected void afterHookedMethod(MethodHookParam old)throws Throwable {
                    XC_MethodHook.MethodHookParam p=(XC_MethodHook.MethodHookParam)old.getObjectExtra(slot);if(p==null)return;
                    p.args=old.args;p.outcome(old.getResult(),old.getThrowable());callback.after(p);
                    if(p.hasThrowable())old.setThrowable(p.getThrowable());else old.setResult(p.getResult());
                }
            });return new XC_MethodHook.Unhook(handle::unhook);
        }
        public Object original(Method method,Object receiver,Object[] args)throws Throwable{return de.robv.android.xposed.XposedBridge.invokeOriginalMethod(method,receiver,args);}
        public void log(String text){de.robv.android.xposed.XposedBridge.log(text);}
    }
}
