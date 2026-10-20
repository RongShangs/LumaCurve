package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Optional traditional ramp route. Changes a call argument, never targets or persistent fields. */
final class TraditionalAnimation {
    static final Set<Class<?>> installed=new HashSet<>();
    static final Map<Object,Saved> rates=Collections.synchronizedMap(new WeakHashMap<>());
    static final class Saved {final Object owner;final float original,applied;Saved(Object o,float a,float b){owner=o;original=a;applied=b;}}
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{
        Field f=HookEntry.field(c,name);if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException(name);return f;
    }
    static Method method(Class<?> c,String name,Class<?> result,Class<?>... args)throws Exception{
        Method m=c.getDeclaredMethod(name,args);if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new NoSuchMethodException(name);m.setAccessible(true);return m;
    }
    static int autoReason(Object controller)throws Exception{
        Field f=HookEntry.field(controller.getClass(),"REASON_AUTO_BRIGHTNESS_RATE");
        if(f.getType()!=int.class||!Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException("auto rate reason");
        int auto=f.getInt(null);
        // Read the native identities; do not assume reason numbers are stable between ROMs.
        for(String name:new String[]{"REASON_THERMAL_BRIGHTNESS_RATE","REASON_HDR_BRIGHTNESS_RATE","REASON_MANUAL_BRIGHTNESS_RATE","REASON_ZERO_RATE"}){
            Field other=HookEntry.field(controller.getClass(),name);
            if(other.getType()!=int.class||!Modifier.isStatic(other.getModifiers())||other.getInt(null)==auto)throw new NoSuchFieldException(name);
        }
        return auto;
    }
    static final class Binding {
        Object first,second,controller,record,display;Method reason;String firstName,secondName;int auto;
    }
    static Binding read(HookRuntime s)throws Exception{
        Object owner=s.owner;
        if(((Number)HookEntry.get(owner,"mDisplayId")).intValue()!=0)throw new IllegalArgumentException("Not the primary display");
        // A disabled native Refactor policy and two empty Refactor slots are required.
        // An incomplete or temporarily unavailable active Refactor implementation must not fall back.
        if(!Boolean.FALSE.equals(method(owner.getClass(),"supportRefactorBrightnessPolicy",boolean.class).invoke(owner))
                ||HookEntry.get(owner,"mBrightnessAnimator")!=null||HookEntry.get(owner,"mSdrBrightnessAnimator")!=null)throw new IllegalStateException("Refactor policy owns animation");
        Object dpc=HookEntry.get(owner,"mDisplayPowerController"),dual=HookEntry.get(dpc,"mScreenBrightnessRampAnimator");
        Binding b=new Binding();b.first=HookEntry.get(dual,"mFirst");b.second=HookEntry.get(dual,"mSecond");
        if(b.first==null||b.second==null||b.first==b.second||b.first.getClass()!=b.second.getClass()||!installed.contains(b.first.getClass()))throw new IllegalStateException("Traditional dual ramp not ready");
        b.display=dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc);
        for(Object ramp:new Object[]{b.first,b.second}){
            if(HookEntry.get(ramp,"mDisplayPowerControllerImpl")!=owner||HookEntry.get(ramp,"mObject")!=b.display)throw new IllegalArgumentException("Ramp belongs to another output");
        }
        Object firstProperty=HookEntry.get(b.first,"mProperty"),secondProperty=HookEntry.get(b.second,"mProperty");
        b.firstName=(String)firstProperty.getClass().getMethod("getName").invoke(firstProperty);
        b.secondName=(String)secondProperty.getClass().getMethod("getName").invoke(secondProperty);
        Class<?> state=b.display.getClass();
        if(firstProperty!=HookEntry.field(state,"SCREEN_BRIGHTNESS_FLOAT").get(null)||secondProperty!=HookEntry.field(state,"SCREEN_SDR_BRIGHTNESS_FLOAT").get(null)
                ||b.firstName==null||b.secondName==null||b.firstName.equals(b.secondName))throw new IllegalArgumentException("Unknown brightness properties");
        b.controller=HookEntry.get(owner,"mRampRateController");b.auto=autoReason(b.controller);
        if(HookEntry.get(b.controller,"mDisplayPowerControllerImpl")!=owner)throw new IllegalArgumentException("Rate controller belongs to another display");
        field(b.controller.getClass(),"mSlowChange",boolean.class);
        b.record=HookEntry.get(b.controller,"mRateRecord");field(b.record.getClass(),"mRateModifier",int.class);
        b.reason=method(b.record.getClass(),"getCurrentRateReason",int.class,String.class);
        return b;
    }
    static boolean supported(HookRuntime s){try{read(s);return true;}catch(Throwable unavailable){return false;}}
    static void install(ClassLoader loader){
        try{
            Class<?> c=Class.forName("com.android.server.display.RampAnimator",false,loader);if(installed.contains(c))return;
            for(String name:new String[]{"mCurrentValue","mRate","mTargetLinearValue"})field(c,name,float.class);
            field(c,"mFirstTime",boolean.class);
            // Both the ordinary target entry and its downstream time-limit consumer must match.
            Method target=method(c,"setAnimationTarget",boolean.class,float.class,float.class,boolean.class,float.class);
            method(c,"setAnimationTarget",boolean.class,float.class,float.class,float.class,float.class);
            method(c,"updateRampRate",float.class,float.class,float.class);
            try(HookTransaction tx=new HookTransaction()){
                tx.add(XposedBridge.hookMethod(target,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                    synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
                        if(!s.advanced.enabled[2]||!s.onDisplayThread())continue;
                        try{
                            Binding b=read(s);if(p.thisObject!=b.first&&p.thisObject!=b.second)continue;
                            rates.remove(p.thisObject);
                            Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
                            if(!s.animationTuningAllowed(abc,impl)||!Boolean.TRUE.equals(HookEntry.get(b.controller,"mSlowChange"))
                                    ||Boolean.TRUE.equals(HookEntry.get(p.thisObject,"mFirstTime"))||((Number)HookEntry.get(b.record,"mRateModifier")).intValue()!=0)return;
                            String property=p.thisObject==b.first?b.firstName:b.secondName;
                            if(((Number)b.reason.invoke(b.record,property)).intValue()!=b.auto)return;
                            float current=((Number)HookEntry.get(p.thisObject,"mCurrentValue")).floatValue(),next=(Float)p.args[0],rate=(Float)p.args[1];
                            float chosen=rate(current,next,rate,s.advanced.values[next>current?9:10]);
                            if(Float.compare(chosen,rate)!=0){p.setObjectExtra("hyperlux.ramp.saved",new Saved(s.owner,rate,chosen));p.args[1]=chosen;}
                            return;
                        }catch(Throwable unavailable){continue;}
                    }}
                }
                protected void afterHookedMethod(MethodHookParam p){
                    Saved saved=(Saved)p.getObjectExtra("hyperlux.ramp.saved");if(saved==null||p.hasThrowable())return;
                    try{if(((Number)HookEntry.get(p.thisObject,"mRate")).floatValue()!=saved.applied)return;
                        rates.put(p.thisObject,saved);synchronized(HookEntry.states){HookRuntime s=HookEntry.states.get(saved.owner);if(s!=null)s.animationAdjustments++;}
                    }catch(Throwable unavailable){/* Native limits and later owners remain authoritative. */}
                }}));
                installed.add(c);tx.commit();
            }
        }catch(Throwable unavailable){XposedBridge.log("HyperLux traditional animation unavailable: "+unavailable);}
    }
    static void restore(HookRuntime s){
        synchronized(rates){for(Iterator<Map.Entry<Object,Saved>> i=rates.entrySet().iterator();i.hasNext();){Map.Entry<Object,Saved> e=i.next();Saved saved=e.getValue();if(saved.owner!=s.owner)continue;
            try{Field f=field(e.getKey().getClass(),"mRate",float.class);if(f.getFloat(e.getKey())==saved.applied)f.setFloat(e.getKey(),saved.original);i.remove();}
            catch(Throwable unavailable){s.log("传统过渡速率等待原生动画更新");}
        }}
    }
    static float rate(float current,float target,float original,double durationScale){
        if(!Float.isFinite(current)||!Float.isFinite(target)||current<0||current>1||target<0||target>1||current==target
                ||!Float.isFinite(original)||original<=0||!Double.isFinite(durationScale)||durationScale<.5||durationScale>3)return original;
        double value=original/durationScale;float result=(float)value;
        return Float.isFinite(result)&&result>0?result:original;
    }
}
