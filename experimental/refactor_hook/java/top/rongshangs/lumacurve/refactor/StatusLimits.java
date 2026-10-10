package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Independent high/low explanations. Every state has both rows; historical flags are not limits. */
final class StatusLimits {
    static StatusAdvice high(JSONObject s,long uptime,long elapsed){
        if(s==null)return advice("高光受阻","等待读取系统状态，暂不能判断高光限制。",2,"高亮设置","");
        if(!StatusPresentation.fresh(s,elapsed))return advice("高光受阻","读数待刷新，暂不能判断高光限制。",2,"高亮设置","");
        List<String> reasons=new ArrayList<>();JSONObject o=s.optJSONObject("outdoor");if(o==null)o=new JSONObject();JSONObject sc=StatusPresentation.scenes(s);String block=o.optString("blocking_condition",o.optString("reason"));
        if(!s.optBoolean("auto_mode"))reasons.add("当前由手动亮度保持");
        if(!"active".equals(s.optString("sensor_status"))||Boolean.TRUE.equals(sc.opt("touch_protection_active"))&&!SceneCatalog.denied(s,"touch")||Boolean.TRUE.equals(sc.opt("proximity_near"))&&Boolean.TRUE.equals(sc.opt("proximity_policy_enabled"))&&!SceneCatalog.denied(s,"proximity"))reasons.add("光感更新暂停或被遮挡保护拦截");
        boolean thermal=StatusAdvice.limited(o,"adjustBrightnessByThermal",uptime)||"temperature".equals(block);if(thermal)reasons.add("温度条件或温控上限");
        if(StatusAdvice.limited(o,"adjustBrightnessByPowerSaveMode",uptime)||"power_save".equals(block)||Boolean.TRUE.equals(o.opt("mIsBlockedByLowPowerMode")))reasons.add("省电条件");
        if(StatusAdvice.limited(o,"adjustBrightnessByBattery",uptime))reasons.add("电池保护");
        if(StatusAdvice.limited(o,"adjustBrightnessByOpr",uptime))reasons.add("画面灰阶限亮");
        if("hbm_budget".equals(block)||o.optBoolean("controller_managed")&&Boolean.FALSE.equals(o.opt("mIsTimeAvailable")))reasons.add("高亮时间预算不足");
        if(s.optBoolean("hdr_active")||"hdr".equals(block))reasons.add("HDR 输出策略优先");
        if("scene".equals(block)||"output_override".equals(block))reasons.add("应用或临时输出策略优先");
        double range=o.optDouble("display_range_max",Double.NaN),mapping=o.optDouble("device_mapping_max",Double.NaN),actual=o.optDouble("actual_brightness",Double.NaN),age=o.optLong("request_age_ms",-1),request=age>=0&&age<=5000?o.optDouble("requested_brightness",Double.NaN):Double.NaN;
        if(valid(range)&&valid(mapping)&&range<mapping-1e-5&&(valid(actual)&&actual>=range-1e-4||valid(request)&&request>range+1e-5))reasons.add("系统高亮范围上限");
        if(valid(request)&&valid(o.optDouble("clamper_max",Double.NaN))&&request>o.optDouble("clamper_max")+1e-5)reasons.add("最终输出上限");
        if(reasons.isEmpty())return advice("高光受阻","当前未观察到阻止升亮的限制；强光不足可查看高亮设置。",2,"高亮设置","");
        StatusAdvice a=new StatusAdvice("高光受阻",String.join("、",reasons)+"。查看高亮设置"+(thermal?"或温控设置":"")+"。",true).action("高亮设置",2,"");if(thermal)a.action("温控设置",9,"thermal");return a;
    }
    static boolean valid(double n){return Double.isFinite(n)&&n>=0&&n<=1;}
    static StatusAdvice low(JSONObject s,long elapsed){
        if(s==null)return advice("低光受阻","等待读取系统状态，暂不能判断低光限制。",7,"暗光设置","low_light");
        if(!StatusPresentation.fresh(s,elapsed))return advice("低光受阻","读数待刷新，暂不能判断低光限制。",7,"暗光设置","low_light");
        JSONObject o=StatusPresentation.scenes(s),control=s.optJSONObject("brightness_control");List<String> reasons=new ArrayList<>();
        if(!s.optBoolean("auto_mode"))reasons.add(control!=null&&"dark".equals(control.optString("owner"))?"暗光锁定保持亮度":"手动亮度保持");
        if(Boolean.TRUE.equals(o.opt("night_driving"))&&!SceneCatalog.denied(s,"night_driving"))reasons.add("夜间驾驶下限与额外确认");
        if(Boolean.TRUE.equals(o.opt("step_mode"))&&o.optDouble("step_extra_ms",0)>0&&!SceneCatalog.denied(s,"step"))reasons.add("运动降亮延迟");
        if(Boolean.TRUE.equals(o.opt("touch_protection_active"))&&!SceneCatalog.denied(s,"touch"))reasons.add("触摸遮挡保护");
        if(Boolean.TRUE.equals(o.opt("proximity_near"))&&Boolean.TRUE.equals(o.opt("proximity_policy_enabled"))&&!SceneCatalog.denied(s,"proximity"))reasons.add("普通距离遮挡");
        if(Boolean.TRUE.equals(o.opt("need_proximity"))&&!SceneCatalog.denied(s,"stream_proximity"))reasons.add("流式距离遮挡确认");
        if(Boolean.TRUE.equals(o.opt("need_aon"))&&!SceneCatalog.denied(s,"reflection"))reasons.add("AON 反射确认");
        if(Boolean.TRUE.equals(o.opt("game_darken_blocked"))&&!SceneCatalog.denied(s,"game"))reasons.add("游戏防误降亮");
        if(Boolean.TRUE.equals(o.opt("night_wake_target"))&&!SceneCatalog.denied(s,"night_wake"))reasons.add("夜间唤醒目标");
        if(reasons.isEmpty())return advice("低光受阻","当前未观察到阻止降亮的场景；暗处波动可查看暗光设置。",7,"暗光设置","low_light");
        return new StatusAdvice("低光受阻",String.join("、",reasons)+"。查看场景设置或暗光设置。",true).action("场景设置",5,"").action("暗光设置",7,"low_light");
    }
    static StatusAdvice advice(String title,String detail,int group,String link,String target){StatusAdvice a=new StatusAdvice(title,detail,true);if(detail.contains(link))a.action(link,group,target);return a;}
}
