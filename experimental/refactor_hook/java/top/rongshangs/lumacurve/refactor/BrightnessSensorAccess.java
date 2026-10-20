package top.rongshangs.lumacurve.refactor;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import java.lang.reflect.*;
import org.json.*;

/** Read the current display controller's sources, never enumerate or select another panel's ALS. */
final class BrightnessSensorAccess {
    static final java.lang.reflect.Method HANDLE=handle();
    static java.lang.reflect.Method handle(){try{java.lang.reflect.Method m=Sensor.class.getDeclaredMethod("getHandle");m.setAccessible(true);return m;}catch(Throwable unavailable){return null;}}
    static boolean same(Sensor a,Sensor b){
        if(a==b)return true;if(a==null||b==null||a.getType()!=b.getType())return false;
        try{return HANDLE!=null&&((Number)HANDLE.invoke(a)).intValue()==((Number)HANDLE.invoke(b)).intValue();}catch(Throwable unavailable){return false;}
    }
    static String profile(Sensor sensor){
        if(sensor==null)return "";
        // Preserve the existing standard ALS route, including older metadata implementations.
        if(sensor.getType()==Sensor.TYPE_LIGHT)return "oem_main";
        if(sensor.getType()>=Sensor.TYPE_DEVICE_PRIVATE_BASE&&"xiaomi.sensor.light_secondary".equals(sensor.getStringType())
            &&!sensor.isWakeUpSensor()&&sensor.getReportingMode()==Sensor.REPORTING_MODE_ON_CHANGE
            &&AuxiliarySensorIdentity.permissionFree(sensor)
            &&Float.isFinite(sensor.getMaximumRange())&&sensor.getMaximumRange()>0
            &&Float.isFinite(sensor.getResolution())&&sensor.getResolution()>=0)return "oem_selected_secondary";
        return "";
    }
    static Object impl(Object owner)throws Exception{return HookEntry.get(owner,"mAutomaticBrightnessControllerImpl");}
    static boolean scalarMetadata(Sensor sensor){return sensor!=null&&!sensor.isWakeUpSensor()&&sensor.getReportingMode()==Sensor.REPORTING_MODE_ON_CHANGE
        &&AuxiliarySensorIdentity.permissionFree(sensor)&&Float.isFinite(sensor.getMaximumRange())&&sensor.getMaximumRange()>0
        &&Float.isFinite(sensor.getResolution())&&sensor.getResolution()>=0;}
    static Method method(Object object,String name,Class<?> returns,Class<?>... parameters)throws Exception{
        Method m=object.getClass().getMethod(name,parameters);
        if(m.getReturnType()!=returns||Modifier.isStatic(m.getModifiers()))throw new NoSuchMethodException(name);m.setAccessible(true);return m;
    }
    static final class Selection {Sensor configured,main;Object impl,controller;String profile;boolean fov;}
    static Selection selection(Object owner)throws Exception{
        if(((Number)HookEntry.get(owner,"mDisplayId")).intValue()!=0)throw new IllegalArgumentException("Not the default display");
        Selection s=new Selection();s.impl=impl(owner);s.controller=HookEntry.get(s.impl,"mAutomaticBrightnessController");
        s.configured=(Sensor)HookEntry.get(s.controller,"mLightSensor");s.main=s.configured;
        if(s.configured==null)throw new IllegalStateException("Configured main light sensor not ready");
        // Standard devices keep their existing route even if additional vendor slots exist.
        if(s.configured.getType()==Sensor.TYPE_LIGHT){s.profile="oem_main";return s;}
        Object enabled=optional(s.impl,"mSupportFovSensor");
        if(enabled!=null&&!(enabled instanceof Boolean))throw new IllegalArgumentException("Invalid FOV support flag");
        if(Boolean.TRUE.equals(enabled)){
            // ABC registers the OEM selector's return value, which need not equal mLightSensor.
            if(((Number)HookEntry.get(s.impl,"mDisplayId")).intValue()!=0)throw new IllegalArgumentException("FOV source belongs to another display");
            Sensor fov=(Sensor)HookEntry.get(s.impl,"mLightFovSensor");
            Sensor actual=(Sensor)method(s.impl,"switchLightSensor",Sensor.class,Sensor.class).invoke(s.impl,s.configured);
            if(fov!=null){
                if(!same(actual,fov)||actual.getType()<Sensor.TYPE_DEVICE_PRIVATE_BASE||!"".equals(actual.getStringType())||!scalarMetadata(actual))throw new IllegalArgumentException("Unverified OEM FOV selection");
                s.main=actual;s.fov=true;s.profile="oem_selected_fov";return s;
            }
            if(!same(actual,s.configured))throw new IllegalArgumentException("Unverified OEM main selection");
        }
        s.profile=profile(s.main);if(s.profile.isEmpty())throw new IllegalArgumentException("Unverified selected main light sensor");return s;
    }
    static Object optional(Object object,String field)throws Exception{try{return HookEntry.get(object,field);}catch(NoSuchFieldException absent){return null;}}
    static float scale(Object dual,String field)throws Exception{
        float value=((Number)HookEntry.get(dual,field)).floatValue();
        if(!Float.isFinite(value)||value<=0||value>100)throw new IllegalArgumentException("Invalid sensor lux scale: "+field);return value;
    }
    static final class Binding {
        Object impl,controller,policy,panelConfig;String physicalId="",profile="";SensorManager manager;Sensor configured,main,assist;float mainScale=1,assistScale=1;boolean fov;Method driving;
        boolean same(Binding other){return other!=null&&impl==other.impl&&profile.equals(other.profile)&&controller==other.controller&&policy==other.policy&&panelConfig==other.panelConfig
            &&physicalId.equals(other.physicalId)&&manager==other.manager&&BrightnessSensorAccess.same(main,other.main)
            &&BrightnessSensorAccess.same(configured,other.configured)&&BrightnessSensorAccess.same(assist,other.assist)&&mainScale==other.mainScale&&assistScale==other.assistScale;}
    }
    static Binding read(Object owner)throws Exception{
        Binding b=new Binding();Selection selected=selection(owner);b.main=selected.main;b.configured=selected.configured;b.profile=selected.profile;b.impl=selected.impl;b.controller=selected.controller;b.fov=selected.fov;
        b.manager=(SensorManager)HookEntry.get(b.controller,"mSensorManager");if(b.manager==null)throw new IllegalStateException("Main sensor manager not ready");
        b.policy=optional(b.impl,"mDualSensorPolicy");
        if(b.policy!=null){b.assist=(Sensor)HookEntry.get(b.policy,"mAssistLightSensor");b.mainScale=scale(b.policy,"mFovAmplifyFactor");b.assistScale=scale(b.policy,"mAssistFovAmplifyFactor");}
        if(b.fov){
            b.driving=method(b.impl,"getDrivingStatus",boolean.class);
            b.mainScale=((Number)method(b.impl,"getFovAmplifyFactor",float.class).invoke(b.impl)).floatValue();
            if(!Float.isFinite(b.mainScale)||b.mainScale<=0||b.mainScale>100)throw new IllegalArgumentException("Invalid FOV lux scale");
        }else if(b.profile.equals("oem_selected_secondary")&&b.mainScale!=1f)throw new IllegalArgumentException("Unverified secondary sensor conversion");
        if(b.assist!=null&&(!AuxiliarySensorIdentity.accepted(b.assist)||same(b.main,b.assist)))throw new IllegalArgumentException("Invalid or duplicate auxiliary light sensor");
        b.panelConfig=optional(owner,"mDisplayDeviceConfig");Object dpc=optional(owner,"mDisplayPowerController");
        if(dpc!=null){Object id=optional(dpc,"mUniqueDisplayId");if(id!=null){if(!(id instanceof String))throw new IllegalArgumentException("Invalid physical display identity");b.physicalId=(String)id;}}
        return b;
    }
    static float mainValue(Binding b,SensorEvent event)throws Exception{
        if(b==null)throw new IllegalStateException("No current sensor binding");
        if(!b.fov)return event.values[0]*b.mainScale;
        // Match the verified ABC consumer: normal FOV values[0] is already lux;
        // confirmed driving packets may carry an alternative raw source in values[2].
        if(event.values.length>2&&event.values[1]==1f&&Boolean.TRUE.equals(b.driving.invoke(b.impl)))return event.values[2]*b.mainScale;
        return event.values[0];
    }
    static JSONObject evidence(Object owner)throws JSONException{
        JSONObject j=new JSONObject().put("source","automatic_brightness_controller").put("extra_listener",false);
        try{Selection selected=selection(owner);Sensor sensor=selected.main;j.put("profile",selected.profile).put("binding_source",selected.fov?"oem_fov_override":"abc_main_slot").put("configured_sensor_name",selected.configured==null?"":selected.configured.getName()).put("sensor_name",sensor.getName()).put("sensor_type",sensor.getType()).put("sensor_string_type",sensor.getStringType()).put("display_id",0);}
        catch(Throwable unknown){j.put("profile","").put("detail",unknown.toString());}
        try{Binding b=read(owner);j.put("input_mode",b.assist==null?"single_main":"dual_input").put("assist_source_present",b.assist!=null);}
        catch(Throwable unknown){j.put("input_detail",unknown.toString());}
        return j;
    }
}
