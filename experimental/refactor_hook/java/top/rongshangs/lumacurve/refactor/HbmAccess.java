package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;

/** Primary-controller data ownership; never mutates the shared DisplayDeviceConfig object. */
final class HbmAccess {
    final Object controller;final Field dataField;final Method maxMethod,normalMethod,allowedMethod,recalculate,luxMethod;
    final Field managedField;final Method managedMethod;final String controllerProfile;
    Object original,applied;float appliedLux;long appliedBudget;final boolean copyable;
    static final String[] DATA_NAMES={"minimumLux","transitionPoint","timeWindowMillis","timeMaxMillis","timeMinMillis","allowInLowPowerMode","minimumHdrPercentOfScreen","sdrToHdrRatioSpline","refreshRateLimit","isHighBrightnessModeEnabled"};
    HbmAccess(Object c)throws Exception{
        controller=c;dataField=HookEntry.field(c.getClass(),"mHbmData");
        for(String f:new String[]{"mBrightnessMax","mBrightnessMin","mAmbientLux","mBrightness"})typed(c.getClass(),f,float.class);
        for(String f:new String[]{"mIsAutoBrightnessEnabled","mIsAutoBrightnessOffByState","mIsTimeAvailable","mIsInAllowedAmbientRange","mIsBlockedByLowPowerMode","mIsHdrLayerPresent","mDolbyEnable"})typed(c.getClass(),f,boolean.class);
        Field enabled;try{enabled=HookEntry.field(c.getClass(),"mHbmControllerIsEnabled");}catch(NoSuchFieldException absent){enabled=null;}
        if(enabled!=null){typed(c.getClass(),"mHbmControllerIsEnabled",boolean.class);managedField=enabled;managedMethod=null;controllerProfile="enabled_field";}
        else{
            // The older controller derives support from live HBM data and metadata. Invoke
            // that native predicate; never invent a true enable flag or bypass its guards.
            Method supports=method(c.getClass(),"deviceSupportsHbm",boolean.class);
            if(Modifier.isStatic(supports.getModifiers())||!dataField.getType().getName().equals("com.android.server.display.config.HighBrightnessModeData")||
                !HookEntry.field(c.getClass(),"mHighBrightnessModeMetadata").getType().getName().equals("com.android.server.display.HighBrightnessModeMetadata"))throw new IllegalStateException("legacy HBM ownership ABI");
            managedField=null;managedMethod=supports;controllerProfile="native_support_method";
        }
        maxMethod=method(c.getClass(),"getCurrentBrightnessMax",float.class);normalMethod=method(c.getClass(),"getNormalBrightnessMax",float.class);
        allowedMethod=method(c.getClass(),"isHbmCurrentlyAllowed",boolean.class);recalculate=method(c.getClass(),"recalculateTimeAllowance",void.class);
        luxMethod=method(c.getClass(),"onAmbientLuxChange",void.class,float.class);original=dataField.get(c);
        validateData(original);boolean cloneable;try{copyData(original);cloneable=true;}catch(Throwable unknown){cloneable=false;}copyable=cloneable;
        if(!OutdoorPolicy.valid(max())||!OutdoorPolicy.valid(normal())||normal()>max())throw new IllegalStateException("HBM brightness coordinates unavailable");
    }
    static void typed(Class<?> c,String name,Class<?> type)throws Exception{Field field=HookEntry.field(c,name);if(field.getType()!=type||Modifier.isStatic(field.getModifiers()))throw new IllegalStateException("HBM field type: "+name);}
    static Method method(Class<?> c,String name,Class<?> result,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(name,args);if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new IllegalStateException("HBM method type: "+name);m.setAccessible(true);return m;}
    static void validateData(Object data)throws Exception{if(data==null||!data.getClass().getName().equals("com.android.server.display.config.HighBrightnessModeData"))throw new IllegalStateException("HBM data unavailable");
        for(String name:new String[]{"minimumLux","transitionPoint"})typed(data.getClass(),name,float.class);
        for(String name:new String[]{"timeWindowMillis","timeMaxMillis","timeMinMillis"})typed(data.getClass(),name,long.class);
        for(Field f:data.getClass().getDeclaredFields())if(!Modifier.isStatic(f.getModifiers())&&!Arrays.asList(DATA_NAMES).contains(f.getName()))throw new IllegalStateException("HBM data layout changed");
    }
    static Object copyData(Object source)throws Exception{
        validateData(source);Class<?>[] types=new Class<?>[DATA_NAMES.length];Object[] args=new Object[DATA_NAMES.length];
        for(int i=0;i<DATA_NAMES.length;i++){Field f=HookEntry.field(source.getClass(),DATA_NAMES[i]);types[i]=f.getType();args[i]=f.get(source);}
        Constructor<?> ctor=source.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(args);
    }
    void observeReplacement()throws Exception{Object now=dataField.get(controller);if(now!=applied&&now!=original){applied=null;original=now;validateData(original);}}
    float max()throws Exception{return ((Number)HookEntry.get(controller,"mBrightnessMax")).floatValue();}
    float normal()throws Exception{return ((Number)HookEntry.get(original,"transitionPoint")).floatValue();}
    float allowedMax()throws Exception{return ((Number)maxMethod.invoke(controller)).floatValue();}
    boolean managed()throws Exception{return managedField!=null?managedField.getBoolean(controller):Boolean.TRUE.equals(managedMethod.invoke(controller));}
    boolean allowed()throws Exception{return Boolean.TRUE.equals(allowedMethod.invoke(controller));}
    long originalLong(String f)throws Exception{return ((Number)HookEntry.get(original,f)).longValue();}
    float originalLux()throws Exception{return ((Number)HookEntry.get(original,"minimumLux")).floatValue();}
    boolean budgetTunable(){try{if(!managed()||!copyable)return false;OutdoorPolicy.budget(originalLong("timeWindowMillis"),originalLong("timeMaxMillis"),originalLong("timeMinMillis"),1);return true;}catch(Throwable unknown){return false;}}
    /** Some OEM profiles use the ambient gate with all three timers disabled. */
    boolean zeroBudget(){try{return originalLong("timeWindowMillis")==0&&originalLong("timeMaxMillis")==0&&originalLong("timeMinMillis")==0;}catch(Throwable unknown){return false;}}
    boolean tunable(){try{return managed()&&copyable&&Float.isFinite(originalLux())&&originalLux()>0&&(budgetTunable()||zeroBudget());}catch(Throwable unknown){return false;}}
    String tuningProfile(){return !tunable()?"unsupported":budgetTunable()?"timed_budget":"ambient_only";}
    boolean configure(OutdoorOptions options,boolean permitted)throws Exception{
        observeReplacement();
        if(!permitted||!options.flags[0]||!options.flags[1])return restore();
        if(!tunable())throw new IllegalStateException("HBM budget interface changed");
        float lux=(float)Math.max(2000,originalLux()*options.values[8]);
        // Preserve the original zero timers: do not manufacture or bypass a budget.
        long budget=budgetTunable()?OutdoorPolicy.budget(originalLong("timeWindowMillis"),originalLong("timeMaxMillis"),originalLong("timeMinMillis"),options.values[9]):originalLong("timeMaxMillis");
        if(applied!=null&&Float.compare(lux,appliedLux)==0&&budget==appliedBudget)return false;
        Object clone=copyData(original);HookEntry.field(clone.getClass(),"minimumLux").setFloat(clone,lux);HookEntry.field(clone.getClass(),"timeMaxMillis").setLong(clone,budget);
        dataField.set(controller,clone);applied=clone;appliedLux=lux;appliedBudget=budget;return true;
    }
    boolean restore()throws Exception{if(applied==null)return false;boolean ours=dataField.get(controller)==applied;if(ours)dataField.set(controller,original);applied=null;return ours;}
    void reevaluate(float lux)throws Exception{if(Float.isFinite(lux)&&lux>=0)luxMethod.invoke(controller,lux);recalculate.invoke(controller);}
    /** Read-only budget estimate, unlike calculateRemainingTime which prunes OEM history. */
    long remaining(long now)throws Exception{
        Object data=dataField.get(controller),metadata=HookEntry.get(controller,"mHighBrightnessModeMetadata");
        long window=((Number)HookEntry.get(data,"timeWindowMillis")).longValue(),limit=((Number)HookEntry.get(data,"timeMaxMillis")).longValue();
        if(!managed()||window<=0||limit<=0)return -1;
        Method running=metadata.getClass().getDeclaredMethod("getRunningStartTimeMillis"),events=metadata.getClass().getDeclaredMethod("getHbmEventQueue");running.setAccessible(true);events.setAccessible(true);
        long start=((Number)running.invoke(metadata)).longValue();if(start>now)return -1;long used=start>=0?now-start:0;
        int count=0;for(Object e:(Iterable<?>)events.invoke(metadata)){if(++count>256)return -1;Method a=e.getClass().getDeclaredMethod("getStartTimeMillis"),b=e.getClass().getDeclaredMethod("getEndTimeMillis");a.setAccessible(true);b.setAccessible(true);
            long begin=((Number)a.invoke(e)).longValue(),end=((Number)b.invoke(e)).longValue();if(begin<0||end<begin||end>now)return -1;if(end>now-window){long add=end-Math.max(begin,now-window);used=add>Long.MAX_VALUE-used?Long.MAX_VALUE:used+add;}}
        return Math.max(0,limit-used);
    }
}
