package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Structured, read-only report. Live state, last calculation and history have separate sections. */
final class BranchReport {
    static final class Row {final String title,value,detail;Row(String t,String v,String d){title=t;value=v;detail=d;}}
    static final class Section {
        final String title;final List<Row> rows=new ArrayList<>();Section(String t){title=t;}
        Section row(String t,String v,String d){rows.add(new Row(t,v,d));return this;}
    }
    static String flag(JSONObject o,String key,String yes,String no){return o==null||!o.has(key)||!(o.opt(key) instanceof Boolean)?"未取得":o.optBoolean(key)?yes:no;}
    static String number(JSONObject o,String key,String unit){double n=o==null?Double.NaN:o.optDouble(key,Double.NaN);return StatusPresentation.valid(n)?String.format(Locale.ROOT,"%.1f",n)+unit:"未取得";}
    static String percent(JSONObject o,String key){double n=o==null?Double.NaN:o.optDouble(key,Double.NaN);return StatusPresentation.valid(n)&&n<=1?String.format(Locale.ROOT,"%.1f%%",100*n):"未取得";}
    static String clock(JSONObject o,String key){int n=o==null?-1:o.optInt(key,-1);return n>=0&&n<1440?String.format(Locale.ROOT,"%02d:%02d",n/60,n%60):"未取得";}
    static String reading(JSONObject s,boolean assist,long elapsed){StatusPresentation.Reading r=StatusPresentation.sensor(s,assist,elapsed);return Double.isFinite(r.lux)?String.format(Locale.ROOT,"%.1f lux",r.lux):r.text;}
    static String route(JSONObject f){String r=f==null?"":f.optString("route");return r.equals("refactor")?"Refactor 曲线":r.equals("mapping")?"标准映射曲线":"未取得";}
    static String strategy(JSONObject s){String r=s==null?"":s.optString("output_strategy");switch(r){case "AutomaticBrightnessStrategy":return "自动亮度";case "FallbackBrightnessStrategy":return "系统回退";case "ScreenOffBrightnessStrategy":return "熄屏";case "DozeBrightnessStrategy":return "息屏显示";case "TemporaryBrightnessStrategy":return "临时亮度";case "OverrideBrightnessStrategy":return "应用指定亮度";default:return r.isEmpty()?"未取得":r;}}
    static String stage(String s){switch(s){case "outdoor_opr_native":case "adjustBrightnessByOpr":return "画面灰阶限亮";case "adjustBrightnessByThermal":return "显示温控";case "adjustBrightnessByBattery":return "电池限亮";case "adjustBrightnessByPowerSaveMode":return "省电限亮";case "adjustBrightnessToPeak":return "峰值范围";case "adjustBrightnessByBcbc":return "画面亮度修正";case "adjustSdrBrightness":return "SDR 输出";default:return s;}}
    static List<Section> read(JSONObject snapshot,long uptime,long elapsed){
        List<Section> result=new ArrayList<>();JSONObject s=StatusPresentation.fresh(snapshot,elapsed)?snapshot:null;
        JSONObject scenes=StatusPresentation.scenes(s),outdoor=s==null?null:s.optJSONObject("outdoor"),control=s==null?null:s.optJSONObject("brightness_control");
        Section mode=new Section("当前模式与判定来源");result.add(mode);
        mode.row("当前模式",String.join(" · ",StatusPresentation.modes(snapshot,elapsed)),"场景标记由系统提供；开启某功能不等于当前已进入该场景。");
        mode.row("夜间驾驶判定",flag(scenes,"night_driving","已进入","未进入"),StatusPresentation.modeDetail(s,elapsed));
        mode.row("系统驾驶识别",flag(scenes,"driving","检测到","未检测到"),"来自系统识别，不证明用户实际正在驾驶。");
        mode.row("夜间驾驶亮度策略",flag(scenes,"night_driving_allowed","允许","未允许"),"系统是否允许驾驶识别参与亮度处理。");
        mode.row("系统日出与日落",clock(scenes,"sunrise_minutes")+" / "+clock(scenes,"sunset_minutes"),"顺序为日出、日落；系统结合识别结果和昼夜时间判定。");
        Section sensors=new Section("光感与融合");result.add(sensors);
        sensors.row("主光感",reading(snapshot,false,elapsed),"");sensors.row("辅助光感",reading(snapshot,true,elapsed),"场景停用、重置等待和采样暂停均不等于 0 lux，也不代表硬件故障。");
        String reference=s==null?"":s.optString("sensor_reference_name");sensors.row("当前光感参考",reference.equals("main")?"主光感":reference.equals("assist")?"辅助光感":reference.equals("other")?"其他系统策略":"未取得","");
        sensors.row("系统有效照度",number(s,"official_effective_lux"," lux"),"融合结果用于曲线计算；不等于任一侧的原始读数。");
        JSONObject f=snapshot==null?null:snapshot.optJSONObject("last_pipeline");long at=f==null?-1:f.optLong("uptime_ms",-1);
        Section curve=new Section("最近一次曲线计算");result.add(curve);
        curve.row("计算记录",at>=0&&uptime>=at?String.format(Locale.ROOT,"%.1f",(uptime-at)/1000d)+" 秒前":"未取得","以下属于同一次历史计算，不能当作当前每一帧的状态。");
        if(at<0||at>uptime)f=null;
        curve.row("曲线路径",route(f),"");
        curve.row("场景修正",flag(f,"scene_changed","已改写目标","未改写目标"),"");
        curve.row("手动保持修正",flag(f,"override_changed","已改写目标","未改写目标"),"");
        curve.row("户外目标修正",flag(f,"outdoor_changed","已改写目标","未改写目标"),"");
        curve.row("短期记忆模型",flag(f,"short_term_memory","模型有效","模型未生效"),"模型状态不等于当前曲线一定由记忆改变。");
        Section output=new Section("实时输出与限制");result.add(output);StatusAdvice advice=StatusAdvice.read(snapshot,uptime,elapsed);
        output.row("当前主要状态",advice.title,advice.detail);output.row("输出路径",strategy(s),"系统回退不代表用户关闭了自动亮度。");
        String owner=control==null?"":control.optString("owner");output.row("亮度控制方式",owner.equals("dark")?"暗光锁定":owner.equals("panel")||owner.equals("raw_panel")?"主屏手动面板":s!=null&&s.has("auto_mode")?s.optBoolean("auto_mode")?"系统自动调节":"系统手动亮度":"未取得","");
        output.row("当前屏幕亮度",number(s,"actual_nit"," nit"),"");
        long requestAge=outdoor==null?-1:outdoor.optLong("request_age_ms",-1);
        output.row("最近请求目标",requestAge>=0&&requestAge<=5000?percent(outdoor,"requested_brightness"):"未取得有效请求","以下百分比是系统亮度坐标，不是实际 nit。");
        output.row("可用范围上限",percent(outdoor,"display_range_max"),"");output.row("设备映射上限",percent(outdoor,"device_mapping_max"),"");
        Section hbm=new Section("系统高亮条件");result.add(hbm);
        hbm.row("HDR",flag(s,"hdr_active","当前生效","当前未生效"),"");hbm.row("阳光增强",flag(s,"sunlight_active","当前生效","当前未生效"),"");
        hbm.row("HBM 控制器",flag(scenes,"hbm_controller_enabled","已启用","未启用"),"控制器开启不代表已经进入高亮。");
        hbm.row("HBM 时间条件",flag(scenes,"hbm_time_available","满足","不满足"),"");hbm.row("HBM 照度条件",flag(scenes,"hbm_ambient_allowed","满足","不满足"),"");hbm.row("HBM 省电阻止",flag(scenes,"hbm_low_power_block","正在阻止","未阻止"),"");
        Section config=new Section("已应用功能配置");result.add(config);
        config.row("暗光锁定",flag(control,"dark_lock_enabled","已启用","未启用"),"开关状态与是否正在保持分别显示。");
        config.row("暗光稳定",flag(s,"low_light_stability","已启用","未启用"),"");config.row("减少温控限亮",flag(s,"thermal_relax","已启用","未启用"),"");
        config.row("手动记忆强度",number(s,"memory_strength",""),"读取已应用状态，不读取未保存草稿。");
        Section history=new Section("历史观测与累计统计");result.add(history);
        JSONObject oldOutdoor=snapshot==null?null:snapshot.optJSONObject("outdoor");JSONArray trace=oldOutdoor==null?null:oldOutdoor.optJSONArray("limit_trace");int shown=0;
        if(trace!=null)for(int i=trace.length()-1;i>=0&&shown<6;i--){JSONObject row=trace.optJSONObject(i);if(row==null||!row.optBoolean("limited"))continue;long time=row.optLong("uptime_ms",-1);if(time<0||time>uptime||uptime-time>60000)continue;
            history.row(stage(row.optString("stage")),percent(row,"before")+" → "+percent(row,"after"),String.format(Locale.ROOT,"%.1f",(uptime-time)/1000d)+" 秒前 · 历史观测");shown++;}
        if(shown==0)history.row("最近一分钟", "未观察到降亮记录","不代表驱动没有限制；历史记录不作为当前原因。");
        history.row("主光感延长确认",number(snapshot,"low_light_main_adjustments"," 次"),"计数为本次系统进程累计，不代表当前生效。");
        history.row("辅助光感延长确认",number(snapshot,"low_light_assist_adjustments"," 次"),"");history.row("暗光阈值调整",number(snapshot,"low_light_threshold_adjustments"," 次"),"");
        return result;
    }
}
