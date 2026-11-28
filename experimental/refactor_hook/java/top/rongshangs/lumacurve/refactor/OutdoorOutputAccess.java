package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.concurrent.ConcurrentHashMap;

/** Outdoor-only output provenance. A missing preferred method permits the older ABI. */
final class OutdoorOutputAccess {
    private static final ConcurrentHashMap<Class<?>,Lookup<Method>> selectors=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>,Lookup<StrategyAccess>> strategies=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>,Lookup<ReasonAccess>> reasons=new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>,Lookup<Method>> powerReaders=new ConcurrentHashMap<>(),brightnessReaders=new ConcurrentHashMap<>();
    private interface Resolver<T>{T resolve(Class<?> type)throws Exception;}
    private static final class Lookup<T>{
        final T value;final Exception failure;
        Lookup(T value,Exception failure){this.value=value;this.failure=failure;}
    }
    private static <T> T resolve(ConcurrentHashMap<Class<?>,Lookup<T>> cache,Class<?> type,Resolver<T> resolver)throws Exception{
        Lookup<T> lookup=cache.computeIfAbsent(type,key->{
            try{return new Lookup<>(resolver.resolve(key),null);}
            catch(Exception unavailable){return new Lookup<>(null,unavailable);}
        });
        if(lookup.failure!=null)throw lookup.failure;
        return lookup.value;
    }
    private static Method selector(Class<?> type)throws Exception{
        for(Class<?> c=type;c!=null;c=c.getSuperclass()){
            Method method;
            try{method=c.getDeclaredMethod("getLastSelectedStrategy");}catch(NoSuchMethodException absent){continue;}
            if(method.getReturnType().isPrimitive()||Modifier.isStatic(method.getModifiers()))throw new NoSuchMethodException("getLastSelectedStrategy ABI");
            method.setAccessible(true);return method;
        }
        return null;
    }
    static Field field(Class<?> owner,String name,Class<?> type)throws Exception{
        Field f=HookEntry.field(owner,name);
        if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException(name+" ABI");
        return f;
    }
    static Method method(Class<?> c,String name,Class<?> result)throws Exception{
        Method m=c.getDeclaredMethod(name);
        if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new NoSuchMethodException(name+" ABI");
        m.setAccessible(true);return m;
    }
    static int constant(Class<?> c,String name)throws Exception{
        Field f=HookEntry.field(c,name);if(f.getType()!=int.class||!Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException(name+" ABI");return f.getInt(null);
    }
    private static final class StrategyAccess{
        final Field display,abc;
        final Method enabled,use,applied;
        StrategyAccess(Class<?> type)throws Exception{
            display=field(type,"mDisplayId",int.class);abc=HookEntry.field(type,"mAutomaticBrightnessController");
            enabled=method(type,"isAutoBrightnessEnabled",boolean.class);
            use=method(type,"shouldUseAutoBrightness",boolean.class);
            applied=method(type,"hasAppliedAutoBrightness",boolean.class);
        }
    }
    private static final class ReasonAccess{
        final Method reason,modifier;
        final int automatic,cosmetic;
        final boolean zeroIsReset;
        ReasonAccess(Class<?> type)throws Exception{
            reason=method(type,"getReason",int.class);modifier=method(type,"getModifier",int.class);
            automatic=constant(type,"REASON_AUTOMATIC");
            if(automatic<=0)throw new NoSuchFieldException("Automatic reason identity");
            boolean zeroDistinct=true;
            for(String name:new String[]{"REASON_MANUAL","REASON_OVERRIDE","REASON_DOZE","REASON_BOOST"}){
                int value=constant(type,name);
                if(value==automatic)throw new NoSuchFieldException("Aliased "+name);
                zeroDistinct&=value!=0;
            }
            boolean reset=false;
            try{reset=constant(type,"REASON_UNKNOWN")==0;}catch(NoSuchFieldException unavailable){}
            zeroIsReset=zeroDistinct&&reset;
            int mask=0;
            for(String name:new String[]{"MODIFIER_BCBC","MODIFIER_OPR","MODIFIER_PEAK"}){
                int bit=constant(type,name);
                if(bit<=0||(bit&(bit-1))!=0||(mask&bit)!=0)throw new NoSuchFieldException("Aliased "+name);
                mask|=bit;
            }
            for(String name:new String[]{"MODIFIER_THERMAL","MODIFIER_HDR","MODIFIER_DIMMED","MODIFIER_LOW_POWER","MODIFIER_THROTTLED"})if((constant(type,name)&mask)!=0)throw new NoSuchFieldException("Protected "+name);
            cosmetic=mask;
        }
    }
    static final class Binding {
        String profile;Object dpc,strategy,reason,pending;Method selected;StrategyAccess strategyAccess;ReasonAccess reasonAccess;
        boolean automatic()throws Exception{
            return "AutomaticBrightnessStrategy".equals(decision().name);
        }
        Decision decision()throws Exception{
            Decision d=new Decision();d.profile=profile;
            if(selected!=null){
                Object selectedStrategy=selected.invoke(dpc);
                d.selector=selectedStrategy==null?null:selectedStrategy.getClass().getSimpleName();
                d.name="AutomaticBrightnessStrategy".equals(d.selector)?"AutomaticBrightnessStrategy":"OtherBrightnessOutput";
            }else{
                ReasonAccess r=reasonAccess;
                d.enabled=(Boolean)strategyAccess.enabled.invoke(strategy);d.use=(Boolean)strategyAccess.use.invoke(strategy);
                d.applied=(Boolean)strategyAccess.applied.invoke(strategy);
                d.reason=(Integer)r.reason.invoke(reason);d.temp=(Integer)r.reason.invoke(pending);
                d.reasonMod=(Integer)r.modifier.invoke(reason);d.tempMod=(Integer)r.modifier.invoke(pending);d.cosmetic=r.cosmetic;
                boolean currentAuto=d.enabled&&d.use&&d.applied&&d.reason==r.automatic
                    &&((d.reasonMod|d.tempMod)&~r.cosmetic)==0;
                d.name=currentAuto&&d.temp==r.automatic?"AutomaticBrightnessStrategy":"OtherBrightnessOutput";
                d.pendingReset=currentAuto&&r.zeroIsReset&&d.temp==0&&d.tempMod==0;
            }
            return d;
        }
    }
    static Binding read(Object owner)throws Exception{
        Binding b=new Binding();b.dpc=HookEntry.get(owner,"mDisplayPowerController");if(b.dpc==null)throw new IllegalStateException("Display controller not ready");
        Method selected=resolve(selectors,b.dpc.getClass(),OutdoorOutputAccess::selector);
        if(selected!=null){
            b.selected=selected;b.profile="selected_strategy";return b;
        }
        b.profile="automatic_strategy2_reason";b.strategy=HookEntry.get(b.dpc,"mAutomaticBrightnessStrategy");
        if(b.strategy==null)throw new IllegalStateException("Automatic strategy not ready");
        if(!b.strategy.getClass().getName().equals("com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2"))throw new NoSuchFieldException("Verified AutomaticBrightnessStrategy2 missing");
        StrategyAccess strategy=resolve(strategies,b.strategy.getClass(),StrategyAccess::new);
        if(strategy.display.getInt(b.strategy)!=0)throw new IllegalArgumentException("Not the logical primary display");
        Object abc=HookEntry.get(b.dpc,"mAutomaticBrightnessController"),impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl");
        if(abc==null||impl==null||strategy.abc.get(b.strategy)!=abc||HookEntry.get(impl,"mAutomaticBrightnessController")!=abc)throw new IllegalArgumentException("Automatic controller ownership mismatch");
        b.strategyAccess=strategy;
        b.reason=HookEntry.get(b.dpc,"mBrightnessReason");b.pending=HookEntry.get(b.dpc,"mBrightnessReasonTemp");
        if(b.reason==null||b.pending==null)throw new IllegalStateException("Output reason not ready");
        Class<?> reason=b.reason.getClass();if(reason!=b.pending.getClass()||!reason.getName().equals("com.android.server.display.brightness.BrightnessReason"))throw new NoSuchFieldException("Output reason ABI");
        b.reasonAccess=resolve(reasons,reason,ReasonAccess::new);
        return b;
    }
    static final class Decision {
        String profile,name,selector,error="";Boolean enabled,use,applied;Integer reason,temp,reasonMod,tempMod,cosmetic;boolean pendingReset;
    }
    /** Same predicate as {@link Binding#automatic()}, but keeps every input for diagnosis. */
    static Decision evaluate(HookRuntime s){
        try{return read(s.owner).decision();}
        catch(Throwable unavailable){Decision d=new Decision();d.error=unavailable.toString();return d;}
    }
    static String name(HookRuntime s){try{return evaluate(s).name;}catch(Throwable unavailable){return null;}}

    static String profile(HookRuntime s)throws Exception{return read(s.owner).profile;}
    static float diagnosticBrightness(HookRuntime s){
        try{
            Object dpc=HookEntry.get(s.owner,"mDisplayPowerController");
            if(dpc==null)return Float.NaN;
            Object display=resolve(powerReaders,dpc.getClass(),type->type.getMethod("getDisplayPowerState")).invoke(dpc);
            if(display==null)return Float.NaN;
            return ((Number)resolve(brightnessReaders,display.getClass(),type->type.getMethod("getScreenBrightness")).invoke(display)).floatValue();
        }catch(Throwable unavailable){return Float.NaN;}
    }
    static long physical(HookRuntime s){try{
        Object dpc=HookEntry.get(s.owner,"mDisplayPowerController");String id=(String)HookEntry.get(dpc,"mUniqueDisplayId");
        if(id==null||!id.matches("local:[0-9]{1,19}"))return 0;long value=Long.parseLong(id.substring(6));return value>0?value:0;
    }catch(Throwable unavailable){return 0;}}
}
