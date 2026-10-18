package top.rongshangs.lumacurve.refactor;

import android.hardware.*;
import android.os.*;
import java.lang.reflect.*;
import org.json.*;

/** Fill a missing OEM auxiliary ALS only while this user's module configuration is active. */
final class AssistSensorCompat {
    final HookRuntime s;Object dual,controller;Sensor selected;Field slot,enabled,supportCache;Method toggle;boolean changing,restorePending,restoreEnabled,originalSupport;
    String state="inactive",detail="";long bindings;
    AssistSensorCompat(HookRuntime runtime){s=runtime;}
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{Field f=HookEntry.field(c,name);if(f.getType()!=type)throw new NoSuchFieldException(name);return f;}
    String restriction(Object current,Object impl)throws Exception{
        if(((Number)HookEntry.get(s.owner,"mDisplayId")).intValue()!=0||((Number)HookEntry.get(current,"mDisplayId")).intValue()!=0||((Number)HookEntry.get(impl,"mDisplayId")).intValue()!=0)return "other_display";
        if(!Boolean.TRUE.equals(HookEntry.get(impl,"mUseAssistSensorEnabled")))return "policy_disabled";
        float scale=((Number)HookEntry.get(current,"mAssistFovAmplifyFactor")).floatValue();return !Float.isFinite(scale)||scale!=1f?"incompatible_units":"";
    }
    void refresh(){
        if(changing||s.closed||!s.onDisplayThread())return;
        if(!"active".equals(s.phase)||!s.appliesToUser()){restore();return;}
        changing=true;
        try{
            Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),current=HookEntry.get(impl,"mDualSensorPolicy"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
            if(current==null){restoreBinding();state="no_policy";return;}
            if(dual!=null){if(!restorePending&&current==dual&&controller==abc&&slot.get(dual)==selected){String reason=restriction(current,impl);if(reason.isEmpty()){state="bound";return;}restoreBinding();state=reason;return;}restoreBinding();}
            Class<?> c=current.getClass();Field source=field(c,"mAssistLightSensor",Sensor.class),on=field(c,"mAssistLightSensorEnable",boolean.class);
            if(source.get(current)!=null){state="native";return;}
            String reason=restriction(current,impl);if(!reason.isEmpty()){state=reason;return;}
            if(abc==null){state="main_not_ready";return;}Sensor main=(Sensor)HookEntry.get(abc,"mLightSensor");if(main==null||main.getType()!=Sensor.TYPE_LIGHT){state="main_not_ready";return;}
            SensorManager manager=(SensorManager)HookEntry.get(current,"mSensorManager");if(manager==null){state="manager_not_ready";return;}
            // Original registration, filtering, timeout, screen and scene gates stay with the OEM policy.
            Method set=c.getDeclaredMethod("setSensorEnabled",boolean.class);if(set.getReturnType()!=void.class)throw new NoSuchMethodException("setSensorEnabled");set.setAccessible(true);
            Object h=HookEntry.get(current,"mPolicyHandler");if(!(h instanceof Handler)||((Handler)h).getLooper()!=s.handler.getLooper()){state="wrong_thread";return;}
            Sensor candidate=AuxiliarySensorIdentity.unique(manager.getSensorList(Sensor.TYPE_ALL));if(candidate==null){state="no_verified_candidate";return;}
            // ABC caches this answer during construction, before our missing-slot fallback.
            Field cached=field(abc.getClass(),"mSupportDualSensorPolicy",boolean.class);
            Method supports=impl.getClass().getMethod("supportDualSensorPolicy");supports.setAccessible(true);if(supports.getReturnType()!=boolean.class)throw new NoSuchMethodException("supportDualSensorPolicy");
            Method initialize=c.getDeclaredMethod("setAmbientLuxWhenInvalid",int.class,float.class);initialize.setAccessible(true);if(initialize.getReturnType()!=void.class)throw new NoSuchMethodException("setAmbientLuxWhenInvalid");
            Field reference=field(c,"mUseLightSensorFlag",int.class),valid=field(abc.getClass(),"mAmbientLuxValid",boolean.class),mainEnabled=field(abc.getClass(),"mLightSensorEnabled",boolean.class),ambient=field(abc.getClass(),"mAmbientLux",float.class);
            Class<?> constants=Class.forName("com.android.server.display.AutomaticBrightnessControllerStub",false,c.getClassLoader());
            int invalid=field(constants,"DUAL_SENSOR_LUX_INVALID",int.class).getInt(null),mainEvent=field(constants,"HANDLE_MAIN_LUX_EVENT",int.class).getInt(null);
            boolean wasOn=on.getBoolean(current);
            dual=current;controller=abc;supportCache=cached;originalSupport=cached.getBoolean(abc);slot=source;enabled=on;toggle=set;selected=candidate;
            restorePending=true;restoreEnabled=wasOn;
            try{if(wasOn)set.invoke(current,false);source.set(current,candidate);
                if(!Boolean.TRUE.equals(supports.invoke(impl)))throw new IllegalStateException("OEM dual policy rejected auxiliary source");
                cached.setBoolean(abc,true);if(wasOn)set.invoke(current,true);
                // An already-running ABC has passed its initial-lux branch. Re-enter the
                // OEM initialization once using its accepted lux, not a synthetic sample.
                float lux=ambient.getFloat(abc);if(wasOn&&mainEnabled.getBoolean(abc)&&valid.getBoolean(abc)&&reference.getInt(current)==invalid&&Float.isFinite(lux)&&lux>=0)initialize.invoke(current,mainEvent,lux);
                restorePending=false;}
            catch(Throwable failure){try{restoreBinding();}catch(Throwable rollback){failure.addSuppressed(rollback);}throw failure;}
            state="bound";detail="";bindings++;s.log("辅助光感兼容已接入："+candidate.getName()+" / "+candidate.getStringType());
            s.handler.post(()->{if(!s.closed&&"active".equals(s.phase)){s.checkFeatures("assist-sensor-compat");s.queuePublish();}});
        }catch(Throwable unavailable){try{restoreBinding();}catch(Throwable rollback){unavailable.addSuppressed(rollback);}state="unavailable";detail=unavailable.toString();}
        finally{changing=false;}
    }
    void restoreBinding()throws Exception{
        if(dual==null)return;
        Object old=dual;Sensor sensor=selected;Field source=slot,on=enabled;Method set=toggle;
        // Another owner replaced the source; do not overwrite its selection.
        Object present=source.get(old);
        if(present!=sensor&&!(restorePending&&present==null)){if(present==null&&!originalSupport&&supportCache.getBoolean(controller))supportCache.setBoolean(controller,false);dual=null;controller=null;selected=null;restorePending=false;return;}
        if(!restorePending){restoreEnabled=on.getBoolean(old);restorePending=true;}
        if(present==sensor){if(on.getBoolean(old))set.invoke(old,false);source.set(old,null);}
        if(!originalSupport&&supportCache.getBoolean(controller))supportCache.setBoolean(controller,false);
        if(on.getBoolean(old)!=restoreEnabled)set.invoke(old,restoreEnabled);
        dual=null;controller=null;selected=null;restorePending=false;
    }
    void restore(){if(changing)return;if(dual!=null&&Looper.myLooper()!=s.handler.getLooper()){s.handler.post(this::restore);return;}changing=true;try{restoreBinding();state="inactive";detail="";}catch(Throwable failure){state="restore_failed";detail=failure.toString();}finally{changing=false;}}
    void put(JSONObject out)throws JSONException{
        JSONObject value=new JSONObject().put("state",state).put("detail",detail).put("bindings",bindings).put("source","oem_auxiliary_slot").put("extra_listener",false);
        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),current=HookEntry.get(impl,"mDualSensorPolicy"),sensor=HookEntry.get(current,"mAssistLightSensor");if(sensor instanceof Sensor){Sensor actual=(Sensor)sensor;value.put("sensor_name",actual.getName()).put("sensor_type",actual.getType()).put("sensor_string_type",actual.getStringType()).put("binding_kind",current==dual&&actual==selected?"ported_auxiliary":"native_auxiliary");}}catch(Throwable unavailable){/* Evidence absence does not change binding or capability. */}
        if(controller!=null)try{value.put("dual_policy_cached",supportCache.getBoolean(controller));}catch(Exception unavailable){}
        out.put("assist_compatibility",value);
    }
}
