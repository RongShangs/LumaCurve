package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;

/** Outdoor-only output provenance. A missing preferred method permits the older ABI. */
final class OutdoorOutputAccess {
    static Field field(Object o,String name,Class<?> type)throws Exception{
        Field f=HookEntry.field(o.getClass(),name);
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
    static final class Binding {
        String profile;Object dpc,strategy,reason,pending;Method selected,enabled,use,applied,getReason,getModifier;int automatic,cosmetic;
        boolean automatic()throws Exception{
            if(selected!=null){Object value=selected.invoke(dpc);return value!=null&&value.getClass().getSimpleName().equals("AutomaticBrightnessStrategy");}
            // Both the current calculation and committed frame must be ordinary automatic.
            // Only explicitly verified cosmetic modifiers may remain; protection wins.
            return (Boolean)enabled.invoke(strategy)&&(Boolean)use.invoke(strategy)&&(Boolean)applied.invoke(strategy)
                &&(Integer)getReason.invoke(reason)==automatic&&(Integer)getReason.invoke(pending)==automatic
                &&((Integer)getModifier.invoke(reason)&~cosmetic)==0&&((Integer)getModifier.invoke(pending)&~cosmetic)==0;
        }
    }
    static Binding read(Object owner)throws Exception{
        Binding b=new Binding();b.dpc=HookEntry.get(owner,"mDisplayPowerController");if(b.dpc==null)throw new IllegalStateException("Display controller not ready");
        Method selected=null;
        for(Class<?> c=b.dpc.getClass();c!=null&&selected==null;c=c.getSuperclass())try{selected=c.getDeclaredMethod("getLastSelectedStrategy");}catch(NoSuchMethodException absent){}
        if(selected!=null){
            if(selected.getReturnType().isPrimitive()||Modifier.isStatic(selected.getModifiers()))throw new NoSuchMethodException("getLastSelectedStrategy ABI");
            selected.setAccessible(true);b.selected=selected;b.profile="selected_strategy";return b;
        }
        b.profile="automatic_strategy2_reason";b.strategy=HookEntry.get(b.dpc,"mAutomaticBrightnessStrategy");
        if(b.strategy==null)throw new IllegalStateException("Automatic strategy not ready");
        if(!b.strategy.getClass().getName().equals("com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2"))throw new NoSuchFieldException("Verified AutomaticBrightnessStrategy2 missing");
        if(field(b.strategy,"mDisplayId",int.class).getInt(b.strategy)!=0)throw new IllegalArgumentException("Not the logical primary display");
        Object abc=HookEntry.get(b.dpc,"mAutomaticBrightnessController"),impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl");
        if(abc==null||HookEntry.get(b.strategy,"mAutomaticBrightnessController")!=abc||HookEntry.get(impl,"mAutomaticBrightnessController")!=abc)throw new IllegalArgumentException("Automatic controller ownership mismatch");
        b.enabled=method(b.strategy.getClass(),"isAutoBrightnessEnabled",boolean.class);
        b.use=method(b.strategy.getClass(),"shouldUseAutoBrightness",boolean.class);
        b.applied=method(b.strategy.getClass(),"hasAppliedAutoBrightness",boolean.class);
        b.reason=HookEntry.get(b.dpc,"mBrightnessReason");b.pending=HookEntry.get(b.dpc,"mBrightnessReasonTemp");
        if(b.reason==null||b.pending==null)throw new IllegalStateException("Output reason not ready");
        Class<?> reason=b.reason.getClass();if(reason!=b.pending.getClass()||!reason.getName().equals("com.android.server.display.brightness.BrightnessReason"))throw new NoSuchFieldException("Output reason ABI");
        b.getReason=method(reason,"getReason",int.class);b.getModifier=method(reason,"getModifier",int.class);b.automatic=constant(reason,"REASON_AUTOMATIC");
        if(b.automatic<=0)throw new NoSuchFieldException("Automatic reason identity");
        for(String name:new String[]{"REASON_MANUAL","REASON_OVERRIDE","REASON_DOZE","REASON_BOOST"})if(constant(reason,name)==b.automatic)throw new NoSuchFieldException("Aliased "+name);
        for(String name:new String[]{"MODIFIER_BCBC","MODIFIER_OPR","MODIFIER_PEAK"}){int bit=constant(reason,name);if(bit<=0||(bit&(bit-1))!=0||(b.cosmetic&bit)!=0)throw new NoSuchFieldException("Aliased "+name);b.cosmetic|=bit;}
        for(String name:new String[]{"MODIFIER_THERMAL","MODIFIER_HDR","MODIFIER_DIMMED","MODIFIER_LOW_POWER","MODIFIER_THROTTLED"})if((constant(reason,name)&b.cosmetic)!=0)throw new NoSuchFieldException("Protected "+name);
        return b;
    }
    static String name(HookRuntime s){try{return read(s.owner).automatic()?"AutomaticBrightnessStrategy":"OtherBrightnessOutput";}catch(Throwable unavailable){return null;}}
    static String profile(HookRuntime s)throws Exception{return read(s.owner).profile;}
    static long physical(HookRuntime s){try{
        Object dpc=HookEntry.get(s.owner,"mDisplayPowerController");String id=(String)HookEntry.get(dpc,"mUniqueDisplayId");
        if(id==null||!id.matches("local:[0-9]{1,19}"))return 0;long value=Long.parseLong(id.substring(6));return value>0?value:0;
    }catch(Throwable unavailable){return 0;}}
}
