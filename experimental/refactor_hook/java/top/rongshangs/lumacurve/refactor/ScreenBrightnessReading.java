package top.rongshangs.lumacurve.refactor;

import java.io.File;
import java.lang.reflect.*;
import org.json.*;

/** Read-only display telemetry. A requested node is never presented as measured output. */
final class ScreenBrightnessReading {
    static Field field(Class<?> type,String name)throws NoSuchFieldException{for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    static JSONObject calibration(Object owner)throws Exception{
        Object config=field(owner.getClass(),"mDisplayDeviceConfig").get(owner);
        if(config==null)throw new IllegalStateException("display_config_pending");
        Field axis=field(config.getClass(),"mBacklight");if(axis.getType()!=float[].class||Modifier.isStatic(axis.getModifiers()))throw new IllegalArgumentException("backlight_axis_shape");
        float[] live=(float[])axis.get(config);if(live==null)throw new IllegalStateException("backlight_axis_pending");float[] x=live.clone();
        if(x.length<2||x.length>4096)throw new IllegalArgumentException("backlight_axis_length");
        for(int i=0;i<x.length;i++)if(!Float.isFinite(x[i])||x[i]<0||x[i]>1||i>0&&x[i]<=x[i-1])throw new IllegalArgumentException("backlight_axis_order");
        Method convert=config.getClass().getMethod("getNitsFromBacklight",float.class);convert.setAccessible(true);
        if(convert.getReturnType()!=float.class||Modifier.isStatic(convert.getModifiers()))throw new IllegalArgumentException("nit_conversion_shape");
        JSONArray backlight=new JSONArray(),nits=new JSONArray();float previous=-1;
        for(int i=0;i<=128;i++){float b=x[0]+(x[x.length-1]-x[0])*i/128f;float n=((Number)convert.invoke(config,b)).floatValue();if(!Float.isFinite(n)||n<0||n<previous)throw new IllegalArgumentException("nit_conversion_invalid");backlight.put(b);nits.put(n);previous=n;}
        if(nits.getDouble(128)<=nits.getDouble(0)||axis.get(config)!=live||!java.util.Arrays.equals(live,x))throw new IllegalStateException("nit_calibration_changed");
        return new JSONObject().put("backlight",backlight).put("nit",nits).put("route","display_config_backlight_to_nit");
    }
    static double nit(JSONObject table,double fraction)throws JSONException{
        if(table==null||!Double.isFinite(fraction)||fraction<0||fraction>1)return Double.NaN;
        if(fraction==0)return 0;
        JSONArray x=table.getJSONArray("backlight"),y=table.getJSONArray("nit");
        if(x.length()!=y.length()||x.length()<2||fraction<x.getDouble(0)||fraction>x.getDouble(x.length()-1))return Double.NaN;
        int i=1;while(i<x.length()-1&&fraction>x.getDouble(i))i++;
        double a=x.getDouble(i-1),b=x.getDouble(i);if(b<=a)return Double.NaN;
        return y.getDouble(i-1)+(fraction-a)/(b-a)*(y.getDouble(i)-y.getDouble(i-1));
    }
    static boolean direct(JSONObject state){JSONObject c=state.optJSONObject("brightness_control"),o=state.optJSONObject("outdoor_node_guard");return c!=null&&"raw_panel".equals(c.optString("owner"))||o!=null&&o.optBoolean("running")&&o.optBoolean("held");}
    static boolean frameworkValue(JSONObject state){double n=state.optDouble("actual_nit",Double.NaN);return Double.isFinite(n)&&n>=0&&!state.optString("actual_nit_source").startsWith("node_");}
    static boolean litOutput(JSONObject state){
        if(frameworkValue(state)&&state.optDouble("actual_nit")>0)return true;
        JSONObject control=state.optJSONObject("brightness_control"),range=control==null?null:control.optJSONObject("range");
        double value=range==null?Double.NaN:range.optDouble("actual",Double.NaN);return Double.isFinite(value)&&value>0&&value<=1;
    }
    static void apply(JSONObject state,JSONObject node,String physical)throws Exception{
        boolean raw=direct(state),framework=frameworkValue(state);double frameworkNit=state.optDouble("actual_nit",Double.NaN);boolean lit=litOutput(state);
        if(framework)state.put("framework_actual_nit",frameworkNit);
        for(String key:new String[]{"actual_node","actual_node_maximum","actual_node_fraction","actual_node_driver","actual_node_register","actual_node_fallback_reason"})state.remove(key);
        if(raw){state.remove("actual_nit");state.put("actual_nit_source","node_read_pending");}
        if(!node.optBoolean("supported"))return;
        String bound=node.optString("physical_display_id");if(!bound.isEmpty()&&!bound.equals(physical))throw new IllegalStateException("brightness_display_changed");
        int register=node.getInt("actual"),max=node.getInt("maximum"),value=node.has("driver_actual")?node.getInt("driver_actual"):register;
        if(max<=10||max>65535||register<0||register>max||value<0||value>max)throw new IllegalArgumentException("brightness_readback_range");
        boolean zeroConflict=node.has("driver_actual")&&value==0&&register>0&&state.optInt("screen_state",-1)==2&&node.optInt("bl_power",0)==0&&lit;
        if(node.has("driver_actual"))state.put("actual_node_driver",value);state.put("actual_node_register",register);
        if(zeroConflict){value=register;state.put("actual_node_fallback_reason","driver_zero_conflicts_with_lit_output");}
        double fraction=(double)value/max;
        state.put("actual_node",value).put("actual_node_maximum",max).put("actual_node_fraction",fraction).put("actual_node_source",node.has("driver_actual")&&!zeroConflict?"driver_actual_brightness":"sysfs_brightness_readback");
        // Framework output remains the established route outside direct node ownership.
        // A real powered-off zero wins over a framework value retained from the last frame.
        boolean poweredOff=state.optInt("screen_state",-1)==1||node.has("bl_power")&&node.optInt("bl_power")!=0;
        if(!raw&&framework&&!(poweredOff&&value==0)&&(frameworkNit>0||value==0||state.optInt("screen_state",-1)!=2))return;
        JSONObject table=state.optJSONObject("screen_nit_calibration");double mapped=nit(table,fraction);
        if(Double.isFinite(mapped)&&mapped>=0)state.put("actual_nit",mapped).put("actual_nit_source","node_readback_device_calibration");
        else if(raw)state.put("actual_nit_source","node_readback_uncalibrated");
    }
    static void annotate(JSONObject state){if(state==null)return;try{
        String physical="";try{physical=ActivePanelNode.display().optString("unique_id");}catch(Exception unavailable){}JSONObject node=ActivePanelNode.discover();
        if(node.optBoolean("supported")){File actual=new File(new File(node.getString("path")).getParentFile(),"actual_brightness");if(actual.isFile())try{node.put("driver_actual",PanelNodeDiscovery.number(actual));}catch(Exception unavailable){}}
        if(node.optBoolean("supported")){File power=new File(new File(node.getString("path")).getParentFile(),"bl_power");if(power.isFile())try{node.put("bl_power",PanelNodeDiscovery.number(power));}catch(Exception unavailable){}}
        Object owner=state.optJSONObject("brightness_control");String bound=owner instanceof JSONObject?((JSONObject)owner).optString("raw_physical_display_id"):"";
        String calibrated=state.optString("screen_nit_physical");if(direct(state)&&!bound.isEmpty()&&!bound.equals(physical))throw new IllegalStateException("brightness_calibration_display_changed");
        if(calibrated.isEmpty()||!calibrated.equals(physical)){state.remove("screen_nit_calibration");if(direct(state))state.remove("actual_nit");}
        apply(state,node,physical);state.put("brightness_read_elapsed_ms",android.os.SystemClock.elapsedRealtime());
    }catch(Exception unavailable){try{if(direct(state)){state.remove("actual_nit");state.put("actual_nit_source","node_read_pending");}state.put("brightness_read_error",unavailable.toString());}catch(JSONException ignored){}}}
}
