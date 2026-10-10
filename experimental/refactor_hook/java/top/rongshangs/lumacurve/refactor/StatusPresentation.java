package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Read-only presentation of current OEM modes. Missing and stale data are not false or zero. */
final class StatusPresentation {
    static final class Reading {
        final String text;final double lux;
        Reading(String text){this.text=text;lux=Double.NaN;}
        Reading(double lux){text="";this.lux=lux;}
    }
    static boolean fresh(JSONObject s,long elapsed){long at=s==null?-1:s.optLong("elapsed_ms",-1);return at>=0&&elapsed>=at&&elapsed-at<=15000;}
    static JSONObject scenes(JSONObject s){JSONObject o=s==null?null:s.optJSONObject("system_scene");return o==null?new JSONObject():o;}
    static boolean night(JSONObject s){return scenes(s).optBoolean("night_driving");}
    static List<String> modes(JSONObject s,long elapsed){
        List<String> names=new ArrayList<>();
        if(s==null){names.add("等待读取");return names;}
        if(!fresh(s,elapsed)){names.add("状态待刷新");return names;}
        JSONObject c=s.optJSONObject("brightness_control"),o=scenes(s);String owner=c==null?"":c.optString("owner");
        if("dark".equals(owner))names.add("暗光锁定保持中");
        else if("panel".equals(owner)||"raw_panel".equals(owner))names.add("主屏手动面板");
        else if(s.has("auto_mode")&&!s.optBoolean("auto_mode"))names.add("手动亮度模式");
        if(o.optBoolean("night_driving"))names.add("夜间驾驶（系统判定）");
        if(s.optBoolean("hdr_active")||o.optBoolean("hdr_layer_present"))names.add("HDR 场景");
        if(o.optBoolean("dolby_enabled"))names.add("杜比显示");
        if(o.optBoolean("touch_protection_active"))names.add("触摸遮挡保护");
        if(o.optBoolean("proximity_near"))names.add("距离遮挡");
        if(o.optBoolean("reflective"))names.add("反射判定");
        if(o.optBoolean("step_mode"))names.add("运动延迟");
        if(o.optBoolean("night_wake"))names.add("夜间唤醒");
        if(s.optBoolean("sunlight_active")||o.optBoolean("manual_sunlight_active"))names.add("阳光增强");
        if(names.isEmpty())names.add(s.optBoolean("auto_mode")?o.length()==0?"自动亮度（场景未取得）":"常规自动亮度":"模式未取得");
        return names;
    }
    static String modeSummary(JSONObject s,long elapsed){return "当前模式："+String.join(" · ",modes(s,elapsed));}
    static String modeDetail(JSONObject s,long elapsed){
        if(!fresh(s,elapsed)||!night(s))return "";
        return "这是系统的夜间驾驶判定，不代表你正在开车。该场景使用主光感时，系统会重置并忽略辅助读数；仍保留系统亮度下限和额外确认时间。";
    }
    static Reading sensor(JSONObject s,boolean assist,long elapsed){
        if(s==null)return new Reading("等待读取");
        if(!fresh(s,elapsed))return new Reading("状态待刷新");
        JSONObject c=s.optJSONObject("brightness_control");
        if(c!=null&&"dark".equals(c.optString("owner"))&&c.optBoolean("listening")){
            double v=c.optDouble(assist?"watch_assist_lux":"watch_main_lux",Double.NaN);
            return valid(v)?new Reading(v):new Reading("等待监听");
        }
        if(s.has("auto_mode")&&!s.optBoolean("auto_mode"))return new Reading("采样暂停");
        String sampling=s.optString("sensor_status");
        if("paused".equals(sampling))return new Reading("采样暂停");
        if(assist&&s.has("assist_sampling_enabled")&&!s.optBoolean("assist_sampling_enabled"))return new Reading("采样暂停");
        if(assist&&scenes(s).optBoolean("assist_reset_pending"))return new Reading("重置等待");
        // The OEM listener discards assist events only for night-driving + main reference.
        if(assist&&night(s)&&"main".equals(s.optString("sensor_reference_name"))&&"active".equals(sampling)&&s.optBoolean("assist_sampling_enabled"))return new Reading("场景停用");
        if("warming".equals(sampling))return new Reading("等待采样");
        if(!"active".equals(sampling))return new Reading("未取得");
        if(assist&&s.has("assist_valid")&&!s.optBoolean("assist_valid"))return new Reading("等待采样");
        double v=s.optDouble(assist?"assist_fast_lux":"main_fast_lux",Double.NaN);
        return valid(v)?new Reading(v):new Reading(assist&&"reset".equals(s.optString("assist_reading_state"))?"读数已重置":"未取得有效读数");
    }
    static boolean valid(double v){return Double.isFinite(v)&&v>=0;}
}
