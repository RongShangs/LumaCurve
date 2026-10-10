package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Read-only explanations from applied settings and fresh observations. Never changes a draft. */
final class StatusAdvice {
    static final class Action {final String text,target;final int group;Action(String text,int group,String target){this.text=text;this.group=group;this.target=target;}}
    final String title,detail;final boolean inlineActions;final List<Action> actions=new ArrayList<>();
    StatusAdvice(String title,String detail){this(title,detail,false);}
    StatusAdvice(String title,String detail,boolean inlineActions){this.title=title;this.detail=detail;this.inlineActions=inlineActions;}
    StatusAdvice action(String text,int group,String target){if(actions.size()<2)actions.add(new Action(text,group,target));return this;}
    static StatusAdvice guidance(JSONObject s,long elapsed){
        long at=s==null?-1:s.optLong("elapsed_ms",-1);
        if(at<0||elapsed<at||elapsed-at>15000)return new StatusAdvice("曲线说明","曲线尚未取得，连接后显示实际曲线。",true);
        try{
            String kind=CurveComparison.read(s,new float[]{1,1,1,1},0,false).kind(s);
            if(kind.equals("memory"))return new StatusAdvice("曲线说明","已学习曲线 · 在手动记忆中调整偏好。",true).action("手动记忆",1,"memory");
            if(kind.equals("baseline"))return new StatusAdvice("曲线说明","基础曲线 · 按环境光线调整亮度起点。",true).action("基础曲线",0,"curve");
            if(kind.equals("system"))return new StatusAdvice("曲线说明","系统默认曲线 · 可在基础曲线中调整。",true).action("基础曲线",0,"curve");
        }catch(JSONException|IllegalArgumentException unavailable){}
        return new StatusAdvice("曲线说明","曲线尚未取得，连接后显示实际曲线。",true);
    }
    static StatusAdvice modeGuide(){
        return new StatusAdvice("特殊模式说明","暗光下亮度不稳定？在暗光设置中调整锁定与稳定。\n强光下亮度不足？在高亮设置中查看范围与画面限亮。",true).action("暗光设置",7,"low_light").action("高亮设置",2,"");
    }
    static boolean number(double v){return Double.isFinite(v)&&v>=0;}
    static boolean limited(JSONObject outdoor,String stage,long now){
        JSONArray rows=outdoor.optJSONArray("current_limits");if(rows==null)return false;
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||!stage.equals(row.optString("stage")))continue;
            long at=row.optLong("uptime_ms",-1);double before=row.optDouble("before",Double.NaN),after=row.optDouble("after",Double.NaN);
            return at>=0&&now>=at&&now-at<=LiveLimitEvidence.MAX_AGE_MS&&number(before)&&before<=1&&number(after)&&after<=1&&after<before-1e-5;
        }return false;
    }
    static StatusAdvice read(JSONObject s,long uptime,long elapsed){
        if(s==null)return new StatusAdvice("实时状态","连接后显示亮度状态与建议");
        long at=s.optLong("elapsed_ms",-1);if(at<0||elapsed<at||elapsed-at>15000)return new StatusAdvice("状态待刷新","等待新的系统读数，暂不判断限制原因");
        if(!"active".equals(s.optString("phase")))return new StatusAdvice("引擎未启用","保存并应用后查看实时状态");
        JSONObject control=s.optJSONObject("brightness_control"),o=s.optJSONObject("outdoor");if(o==null)o=new JSONObject();
        if(!s.optBoolean("auto_mode"))return new StatusAdvice(control!=null&&"dark".equals(control.optString("owner"))?"暗光锁定保持中":"手动亮度模式","系统自动亮度暂停，保留当前选择");
        if(s.optBoolean("hdr_active"))return new StatusAdvice("HDR 场景","沿用系统 HDR 与显示保护");
        String block=o.optString("blocking_condition",o.optString("reason"));
        if(limited(o,"adjustBrightnessByThermal",uptime)){
            StatusAdvice a=new StatusAdvice("温控正在限制亮度",s.optInt("thermal_severity",0)>=3?"系统温度保护优先，请等待降温":"显示温控已降低本次亮度目标");
            if(s.optInt("thermal_severity",0)<3&&s.optBoolean("thermal_supported")&&!s.optBoolean("thermal_relax"))a.action("减少温控限亮",9,"thermal");return a;
        }
        if(limited(o,"adjustBrightnessByPowerSaveMode",uptime)||"power_save".equals(block)||o.optBoolean("mIsBlockedByLowPowerMode"))return new StatusAdvice("省电限制生效","省电条件正在限制高亮范围");
        if(limited(o,"adjustBrightnessByBattery",uptime))return new StatusAdvice("电池保护限亮","沿用系统电量与电池保护条件");
        if("temperature".equals(block))return new StatusAdvice("温度条件限制高亮","温度条件未满足，等待降温后再试");
        if(!"active".equals(s.optString("sensor_status")))return new StatusAdvice("等待有效照度","光感尚未就绪，暂不判断环境与高亮条件");
        double lux=s.optDouble("official_effective_lux",s.optDouble("last_lux",Double.NaN));
        double low=s.optDouble("low_light_limit_lux",50);if(!number(low))low=50;
        if(number(lux)&&lux<=low){
            boolean lock=control!=null&&control.optBoolean("dark_lock_enabled"),stable=s.optBoolean("low_light_stability");
            StatusAdvice a=new StatusAdvice("暗光环境",!lock&&s.optBoolean("dark_lock_supported")||!stable&&s.optBoolean("low_light_supported")?"感觉亮度不稳定？试试启用":lock&&stable?"暗光锁定与暗光稳定已启用":"沿用当前暗光设置与系统保护");
            if(!lock&&s.optBoolean("dark_lock_supported"))a.action("暗光锁定",7,"dark_lock");
            if(!stable&&s.optBoolean("low_light_supported"))a.action("暗光稳定",7,"low_light");return a;
        }
        JSONObject options=o.optJSONObject("options");if(options==null)options=new JSONObject();
        double enter=options.optDouble("outdoor_enter_lux",10000);boolean strong=number(lux)&&number(enter)&&lux>=enter;
        if(!strong)return new StatusAdvice("自动亮度运行中","当前沿用已应用的曲线与系统保护");
        if("hbm_budget".equals(block)||o.optBoolean("controller_managed")&&o.has("mIsTimeAvailable")&&!o.optBoolean("mIsTimeAvailable"))return new StatusAdvice("系统高亮时间预算不足","等待高亮预算恢复，现有保护继续生效");
        if("hdr".equals(block)||"scene".equals(block)||"output_override".equals(block))return new StatusAdvice("系统场景正在限制高亮","临时、应用或特殊场景优先，沿用系统");
        boolean rangeOpen=options.optBoolean("outdoor_range_unlock"),oprOpen=options.optBoolean("outdoor_opr_relax");
        boolean opr=limited(o,"adjustBrightnessByOpr",uptime);
        double mapping=o.optDouble("device_mapping_max",Double.NaN),range=o.optDouble("display_range_max",Double.NaN);
        long requestAge=o.optLong("request_age_ms",-1);double actual=o.optDouble("actual_brightness",Double.NaN),request=requestAge>=0&&requestAge<=LiveLimitEvidence.MAX_AGE_MS?o.optDouble("requested_brightness",Double.NaN):Double.NaN;
        boolean capped=number(mapping)&&number(range)&&range<mapping-1e-5&&(number(actual)&&actual>=range-1e-4||number(request)&&request>range+1e-5);
        StatusAdvice a=new StatusAdvice(opr?"画面限亮正在降低亮度":capped?"当前高亮范围限制了亮度":"强光环境",opr?"已观察到画面灰阶限制本次输出":capped?"当前范围上限低于设备映射上限":"查看可用的高亮设置与当前条件");
        if((opr||capped)&&!rangeOpen&&s.optBoolean("outdoor_supported")&&s.optBoolean("outdoor_range_supported"))a.action("开放系统映射内的高光范围",2,"range");
        if(opr&&!oprOpen&&s.optBoolean("outdoor_supported")&&s.optBoolean("outdoor_range_supported")&&s.optBoolean("outdoor_opr_supported"))a.action("强光下放宽画面限亮",2,"opr");
        if(a.actions.isEmpty()&&o.has("clamper_max")&&number(request)&&request>o.optDouble("clamper_max",Double.NaN)+1e-5)return new StatusAdvice("系统输出上限生效","最终限亮器低于请求目标，详细分支可查看");
        if(a.actions.isEmpty())a.action("查看高亮条件",2,"");return a;
    }
}
