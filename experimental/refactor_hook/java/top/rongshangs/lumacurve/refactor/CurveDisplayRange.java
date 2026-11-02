package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import org.json.*;

/** Display scale only. Never replaces the backend's full device/edit/storage domain. */
final class CurveDisplayRange {
    static JSONObject capture(Object owner,String backend,float minimum,float maximum)throws Exception{
        if("physical_mapping".equals(backend))return capture(owner,minimum,maximum).put("backend",backend).put("coordinate","physical_nit");
        if(!"refactor".equals(backend))throw new IllegalArgumentException("未知曲线坐标");
        Class<?> util=Class.forName("com.android.server.display.RefactorBrightnessUtil",false,owner.getClass().getClassLoader());Method convert=ControlCenterCalibration.method(util,"sliderToLogicalBrightness",float.class,float.class);
        if(!Modifier.isStatic(convert.getModifiers()))throw new IllegalArgumentException("未知逻辑手动亮度接口");float n=ControlCenterCalibration.value(convert,null,1);
        if(!Float.isFinite(n)||n<=minimum||n>maximum)throw new IllegalArgumentException("逻辑手动亮度范围无效");
        return new JSONObject().put("state","available").put("profile","logical_slider_endpoint").put("maximum_nit",n).put("backend",backend).put("coordinate","logical_nit");
    }
    static JSONObject capture(Object owner,float minimum,float maximum)throws Exception{
        Object config=ControlCenterCalibration.configuration(owner);JSONObject boundary=ControlCenterPartition.boundary(owner);
        Method backlight=ControlCenterCalibration.method(config.getClass(),"getBacklightFromBrightness",float.class,float.class),nits=ControlCenterCalibration.method(config.getClass(),"getNitsFromBacklight",float.class,float.class);
        if(Modifier.isStatic(backlight.getModifiers())||Modifier.isStatic(nits.getModifiers()))throw new IllegalArgumentException("曲线手动范围换算接口无效");
        float b=ControlCenterCalibration.value(backlight,config,(float)boundary.getDouble("brightness")),n=ControlCenterCalibration.value(nits,config,b);
        if(!Float.isFinite(b)||b<0||b>1||!Float.isFinite(n)||n<=minimum||n>maximum+Math.ulp(maximum)*16||ControlCenterCalibration.configuration(owner)!=config)throw new IllegalArgumentException("曲线手动范围尚未确认");
        return new JSONObject().put("state","available").put("profile",boundary.getString("profile")).put("maximum_nit",Math.min(n,maximum)).put("ordinary_brightness",boundary.getDouble("brightness"));
    }
    static float maximum(JSONObject runtime,float full){
        if(runtime==null)return full;
        JSONObject range=runtime.optJSONObject("curve_display_range");double n=range==null?Double.NaN:range.optDouble("maximum_nit",Double.NaN);
        return range!=null&&range.optString("backend").equals(runtime.optString("curve_backend"))&&range.optString("coordinate").equals(runtime.optString("curve_coordinate"))&&"available".equals(range.optString("state"))&&Double.isFinite(n)&&n>runtime.optDouble("min_logical_nit",0)&&n<=full?(float)n:full;
    }
    static void validate(float full,float scale){if(!Float.isFinite(full)||!Float.isFinite(scale)||full<=0||scale<=0||scale>full)throw new IllegalArgumentException("曲线坐标不兼容");}
    static float percent(float value,float full,float scale){validate(full,scale);if(!Float.isFinite(value)||value<0||value>full)throw new IllegalArgumentException("曲线坐标不兼容");return (float)((double)value/scale*100);}
    static float fromPercent(float percent,float full,float scale){validate(full,scale);if(!Float.isFinite(percent)||percent<0||percent>percent(full,full,scale))throw new IllegalArgumentException("请输入范围内的数值");return Math.min(full,(float)(percent/100d*scale));}
}
