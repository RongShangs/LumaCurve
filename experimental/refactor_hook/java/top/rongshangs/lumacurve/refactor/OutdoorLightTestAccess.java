package top.rongshangs.lumacurve.refactor;

import android.os.Handler;
import java.lang.reflect.*;

/** Verified calculation seam; sensor samples, thresholds and learning inputs stay real. */
final class OutdoorLightTestAccess {
    final HookRuntime s;final Object impl,abc;final long physical;
    final Method ambient,update,recompute,reset,maybeReset,user,userLux,dual,sdr;final OutdoorSdrScope scope;
    final Field lux,rawBrightness,valid,enabled;final Handler handler;
    final Object range;final Method rangeLux,implLux;
    static Method method(Class<?> c,String name,Class<?> returns,Class<?>... types)throws Exception{
        for(Class<?> at=c;at!=null;at=at.getSuperclass())try{Method m=at.getDeclaredMethod(name,types);if(m.getReturnType()!=returns||Modifier.isStatic(m.getModifiers()))throw new IllegalArgumentException("Outdoor test ABI: "+name);m.setAccessible(true);return m;}catch(NoSuchMethodException absent){}
        Method inherited=c.getMethod(name,types);if(inherited.getReturnType()!=returns||Modifier.isStatic(inherited.getModifiers()))throw new IllegalArgumentException("Outdoor test ABI: "+name);inherited.setAccessible(true);return inherited;
    }
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{Field f=HookEntry.field(c,name);if(f.getType()!=type||(Modifier.isStatic(f.getModifiers())||Modifier.isFinal(f.getModifiers())))throw new IllegalArgumentException("Outdoor test field: "+name);f.setAccessible(true);return f;}
    OutdoorLightTestAccess(HookRuntime state)throws Exception{
        s=state;BrightnessSensorAccess.Selection source=BrightnessSensorAccess.selection(s.owner);impl=source.impl;abc=source.controller;physical=OutdoorOutputAccess.physical(s);if(physical<=0)throw new IllegalStateException("Primary physical display pending");
        Class<?> c=abc.getClass();lux=field(c,"mAmbientLux",float.class);rawBrightness=field(c,"mRawScreenAutoBrightness",float.class);valid=field(c,"mAmbientLuxValid",boolean.class);enabled=field(c,"mLightSensorEnabled",boolean.class);
        Object h=HookEntry.get(abc,"mHandler");if(!(h instanceof Handler)||((Handler)h).getLooper()!=s.handler.getLooper())throw new IllegalArgumentException("ABC display handler mismatch");handler=(Handler)h;
        ambient=method(c,"setAmbientLux",void.class,float.class);update=method(c,"updateAutoBrightness",void.class,boolean.class,boolean.class);recompute=method(c,"updateAmbientLux",void.class);
        reset=method(c,"resetShortTermModel",void.class);maybeReset=method(c,"resetShortTermModelIfNeeded",void.class);user=method(c,"setScreenBrightnessByUser",boolean.class,float.class);userLux=method(c,"setScreenBrightnessByUser",boolean.class,float.class,float.class);
        Class<?> reason=Class.forName("com.android.server.display.brightness.BrightnessReason",false,s.owner.getClass().getClassLoader());sdr=HbmAccess.sdrMethod(s.owner.getClass(),reason);scope=OutdoorSdrScope.resolve(s.owner.getClass());
        Method alternate=null;try{alternate=method(c,"setAmbientLux",void.class,int.class,float.class,boolean.class,boolean.class);}catch(NoSuchMethodException missing){}
        Object flag=BrightnessSensorAccess.optional(abc,"mSupportDualSensorPolicy");if(flag!=null&&!(flag instanceof Boolean)||Boolean.TRUE.equals(flag)&&alternate==null)throw new IllegalArgumentException("Partial dual ambient ABI");dual=alternate;
        range=HookEntry.get(abc,"mBrightnessRangeController");rangeLux=method(range.getClass(),"onAmbientLuxChange",void.class,float.class);implLux=method(impl.getClass(),"onAmbientLuxChange",void.class,float.class);
    }
    boolean current(){try{return !s.closed&&HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl")==impl&&HookEntry.get(impl,"mAutomaticBrightnessController")==abc&&OutdoorOutputAccess.physical(s)==physical;}catch(Exception changed){return false;}}
    float read()throws Exception{return lux.getFloat(abc);}
    float raw()throws Exception{return rawBrightness.getFloat(abc);}
    void notifyLux(float value)throws Exception{Exception failure=null;try{rangeLux.invoke(range,value);}catch(Exception damaged){failure=damaged;}try{implLux.invoke(impl,value);}catch(Exception damaged){if(failure==null)failure=damaged;}if(failure!=null)throw failure;}
    boolean ready()throws Exception{return valid.getBoolean(abc)&&enabled.getBoolean(abc)&&Float.isFinite(read())&&read()>=0;}
    static boolean checked(HookRuntime s)throws Exception{new HbmAccess(HookEntry.get(s.owner,"mHbmController"));return new OutdoorLightTestAccess(s).current();}
    static boolean supported(HookRuntime s){try{return checked(s);}catch(Throwable unavailable){return false;}}
}
