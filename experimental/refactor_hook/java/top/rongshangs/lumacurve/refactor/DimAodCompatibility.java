package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import android.os.SystemClock;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Optional DIM/AOD coordination. Never changes AOD state, sensors or node ownership.
 * Only the native dimming substage is capped; subsequent OEM protections still run.
 */
final class DimAodCompatibility {
    static final String ROUTE="automatic_dim_aod_cap",FRAME="hyperlux.dim.frame",INPUT="hyperlux.dim.input";
    static final Map<Class<?>,Profile> installed=new HashMap<>();
    static final Map<Class<?>,String> errors=new HashMap<>();
    static final ThreadLocal<Frame> current=new ThreadLocal<>();
    static final class Counters {
        long corrections,lastElapsed=-1;float input=Float.NaN,nativeResult=Float.NaN;
        String error="";
    }
    static final class Frame {
        final Frame previous;final Object owner,reason;final boolean automatic,dimming;
        Frame(XC_MethodHook.MethodHookParam v){previous=current.get();owner=v.thisObject;reason=v.args[2];automatic=Boolean.TRUE.equals(v.args[1]);dimming=Boolean.TRUE.equals(v.args[4]);}
    }
    static Field field(Class<?> c,String name,Class<?> type)throws Exception {
        Field f=HookEntry.field(c,name);
        if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new IllegalStateException("Unexpected DIM field: "+name);
        return f;
    }
    static Method method(Class<?> c,String name,Class<?> result,Class<?>...args)throws Exception {
        Method m=c.getDeclaredMethod(name,args);
        if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new IllegalStateException("Unexpected DIM method: "+name);
        m.setAccessible(true);return m;
    }
    static int constant(Class<?> c,String name)throws Exception {
        Field f=HookEntry.field(c,name);
        if(f.getType()!=int.class||!Modifier.isStatic(f.getModifiers()))throw new IllegalStateException("Unexpected DIM constant: "+name);
        return f.getInt(null);
    }
    static final class Profile {
        final Method outer,inner,fullAod,hdr,powerState,screenState,reason,modifier,strategyFullAod;
        final Field display,automatic,controller,request,policy,strategy,controllerDisplay;
        final int dimPolicy,automaticReason,dimModifier;
        Profile(Class<?> owner)throws Exception {
            ClassLoader loader=owner.getClassLoader();
            Class<?> dpc=Class.forName("com.android.server.display.DisplayPowerController",false,loader);
            Class<?> dps=Class.forName("com.android.server.display.DisplayPowerState",false,loader);
            Class<?> req=Class.forName("android.hardware.display.DisplayManagerInternal$DisplayPowerRequest",false,loader);
            Class<?> why=Class.forName("com.android.server.display.brightness.BrightnessReason",false,loader);
            Class<?> doze=Class.forName("com.android.server.display.brightness.strategy.DozeBrightnessStrategyImpl",false,loader);
            outer=method(owner,"adjustSdrBrightness",float.class,float.class,boolean.class,why,boolean.class,boolean.class);
            inner=method(owner,"adjustBrightnessWhenDimming",float.class,float.class,boolean.class);
            fullAod=method(owner,"isFullAodStateForBrightness",boolean.class);hdr=method(owner,"isHdrScene",boolean.class);
            display=field(owner,"mDisplayId",int.class);automatic=field(owner,"mAutoBrightnessEnable",boolean.class);
            controller=field(owner,"mDisplayPowerController",dpc);strategy=field(owner,"mDozeBrightnessStrategy",doze);
            controllerDisplay=field(dpc,"mDisplayId",int.class);request=field(dpc,"mPowerRequest",req);policy=field(req,"policy",int.class);
            powerState=method(dpc,"getDisplayPowerState",dps);screenState=method(dps,"getScreenState",int.class);
            reason=method(why,"getReason",int.class);modifier=method(why,"getModifier",int.class);
            strategyFullAod=method(doze,"getFullAodStateForBrightness",boolean.class);
            method(doze,"getAodScreenBrightness",float.class);
            dimPolicy=constant(req,"POLICY_DIM");automaticReason=constant(why,"REASON_AUTOMATIC");dimModifier=constant(why,"MODIFIER_DIMMED");
            if(dimPolicy<0||automaticReason<=0||dimModifier<=0||(dimModifier&(dimModifier-1))!=0)throw new IllegalStateException("Invalid DIM constants");
            for(String name:new String[]{"POLICY_OFF","POLICY_DOZE","POLICY_BRIGHT"})if(dimPolicy==constant(req,name))throw new IllegalStateException("Aliased DIM policy");
        }
        boolean ready(Object owner)throws Exception {
            Object dpc=controller.get(owner);
            return dpc!=null&&request.get(dpc)!=null&&powerState.invoke(dpc)!=null&&strategy.get(owner)!=null;
        }
        boolean allows(Frame f,HookRuntime s)throws Exception {
            if(s==null||s.closed||s.changing||!s.onDisplayThread()||!s.phase.equals("active")||s.kernel.plan()==null||!s.appliesToUser())return false;
            if(!f.automatic||!f.dimming||f.reason==null||!Boolean.TRUE.equals(s.kernel.get("mUseAutoBrightness")))return false;
            Object owner=f.owner,dpc=controller.get(owner),doze=strategy.get(owner);
            if(display.getInt(owner)!=0||!automatic.getBoolean(owner)||dpc==null||controllerDisplay.getInt(dpc)!=0||doze==null)return false;
            Object req=request.get(dpc),state=powerState.invoke(dpc);
            return req!=null&&policy.getInt(req)==dimPolicy&&state!=null&&((Integer)screenState.invoke(state))==2
                &&((Integer)reason.invoke(f.reason))==automaticReason&&(((Integer)modifier.invoke(f.reason))&dimModifier)!=0
                &&Boolean.TRUE.equals(fullAod.invoke(owner))&&Boolean.TRUE.equals(strategyFullAod.invoke(doze))&&Boolean.FALSE.equals(hdr.invoke(owner));
        }
    }
    static synchronized boolean install(Class<?> owner){
        if(installed.containsKey(owner))return true;
        if(errors.containsKey(owner))return false;
        try{
            final Profile p=new Profile(owner);
            try(HookTransaction tx=new HookTransaction()){
                tx.add(XposedBridge.hookMethod(p.outer,new XC_MethodHook(){
                    protected void beforeHookedMethod(MethodHookParam call){Frame frame=new Frame(call);call.setObjectExtra(FRAME,frame);current.set(frame);}
                    protected void afterHookedMethod(MethodHookParam call){Frame frame=(Frame)call.getObjectExtra(FRAME);if(frame!=null){if(frame.previous==null)current.remove();else current.set(frame.previous);}}
                }));
                tx.add(XposedBridge.hookMethod(p.inner,new XC_MethodHook(){
                    protected void beforeHookedMethod(MethodHookParam call){call.setObjectExtra(INPUT,call.args[0]);}
                    protected void afterHookedMethod(MethodHookParam call){
                        Frame frame=current.get();
                        if(frame==null||frame.owner!=call.thisObject||call.hasThrowable()||!Boolean.TRUE.equals(call.args[1]))return;
                        Object input=call.getObjectExtra(INPUT),result=call.getResult();
                        if(!(input instanceof Float)||!(result instanceof Float))return;
                        float before=(Float)input,after=(Float)result;
                        if(!Float.isFinite(before)||!Float.isFinite(after)||before<=0||before>1||after<=before||after>1)return;
                        HookRuntime s=HookEntry.ownerState(frame.owner);
                        try{
                            if(!p.allows(frame,s))return;
                            call.setResult(before);Counters c=s.dimAod;c.corrections++;c.lastElapsed=SystemClock.elapsedRealtime();c.input=before;c.nativeResult=after;c.error="";
                            if(c.corrections==1)s.log("已协调熄屏前降亮，避免息屏目标重新抬高亮度");
                            s.queuePublish();
                        }catch(Throwable unavailable){if(s!=null)s.dimAod.error=unavailable.getClass().getSimpleName();}
                    }
                }));
                tx.commit();
            }
            installed.put(owner,p);errors.remove(owner);return true;
        }catch(Throwable unavailable){errors.put(owner,unavailable.toString());return false;}
    }
    static synchronized boolean matched(Class<?> owner){return installed.containsKey(owner);}
    static synchronized boolean supported(Object owner)throws Exception {Profile p=installed.get(owner.getClass());return p!=null&&p.ready(owner);}
    static JSONObject status(HookRuntime s)throws JSONException {
        Class<?> cls=s.owner.getClass();String profile,error;
        synchronized(DimAodCompatibility.class){profile=installed.containsKey(cls)?ROUTE:"";error=errors.containsKey(cls)?errors.get(cls):"";}
        Counters c=s.dimAod;
        JSONObject value=new JSONObject().put("profile",profile).put("detail",error).put("runtime_error",c.error).put("corrections",c.corrections).put("last_elapsed_ms",c.lastElapsed);
        if(Float.isFinite(c.input))value.put("last_dim_input",c.input).put("last_native_aod_result",c.nativeResult).put("last_output",c.input);
        return value;
    }
}
