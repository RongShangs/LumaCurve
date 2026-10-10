package top.rongshangs.lumacurve.refactor;

import org.json.*;
import java.util.Locale;

/** Compact output states from applied configuration and current evidence, never UI drafts. */
final class OutputProtection {
    static final class Item {
        final String title,state,detail;final int group;final String target;
        Item(String title,String state,String detail,int group,String target){this.title=title;this.state=state;this.detail=detail;this.group=group;this.target=target;}
    }
    static Item item(int at,String state,String detail){return new Item(new String[]{"暗光稳定","户外高亮","温控"}[at],state,detail,new int[]{7,2,9}[at],new String[]{"low_light","","thermal"}[at]);}
    static String temperature(JSONObject s,long elapsed){
        if(!StatusPresentation.fresh(s,elapsed))return s==null?"等待读取":"状态待刷新";
        Object raw=s.opt("battery_temperature");if(!(raw instanceof Number))return "温度未取得";
        double c=((Number)raw).doubleValue();return Double.isFinite(c)&&c>=-40&&c<=100?String.format(Locale.ROOT,"%.1f ℃",c):"温度未取得";
    }
    static String primary(Item item,JSONObject s,long elapsed){return item.group==9?temperature(s,elapsed):item.state;}
    static String secondary(Item item){return item.group==9?item.state:item.detail;}
    static boolean temperatureAlert(Item item,JSONObject s,long elapsed){
        if(item.group!=9||!StatusPresentation.fresh(s,elapsed))return false;
        Object raw=s.opt("battery_temperature");if(!(raw instanceof Number))return false;double c=((Number)raw).doubleValue();
        return Double.isFinite(c)&&c>=-40&&c<=100&&("正在限亮".equals(item.state)||"温度阻止高亮".equals(item.state));
    }
    static Item[] read(JSONObject s,long uptime,long elapsed){
        if(!StatusPresentation.fresh(s,elapsed)){String state=s==null?"等待读取":"状态待刷新";return new Item[]{item(0,state,"点击查看设置"),item(1,state,"点击查看设置"),item(2,state,"点击查看设置")};}
        JSONObject c=s.optJSONObject("brightness_control"),o=s.optJSONObject("outdoor");if(o==null)o=new JSONObject();
        Item dark,outdoor,thermal;
        boolean lock=c!=null&&c.optBoolean("dark_lock_enabled"),stable=s.optBoolean("low_light_stability");
        if(c!=null&&"dark".equals(c.optString("owner")))dark=item(0,"锁定保持中","自动亮度暂让位");
        else if(!"active".equals(s.optString("phase")))dark=item(0,"引擎未启用","保存并应用后生效");
        else if(!s.optBoolean("auto_mode"))dark=item(0,"手动亮度","自动调节已暂停");
        else if(!stable&&!lock)dark=item(0,"未开启","可减少暗处波动");
        else {double lux=s.optDouble("official_effective_lux",s.optDouble("last_lux",Double.NaN));boolean valid=Double.isFinite(lux)&&lux>=0&&"active".equals(s.optString("sensor_status"));
            String detail=s.optBoolean("hdr_active")?"HDR 优先，沿用系统":!valid?"等待有效照度":stable&&lux<=s.optDouble("low_light_limit_lux",50)?"已达到暗光门槛":stable?"等待进入暗光":"等待锁定条件";
            dark=item(0,stable&&lock?"稳定＋锁定":stable?"稳定已开启":"锁定已开启",detail);}
        String reason=o.optString("blocking_condition",o.optString("reason"));
        if(s.optBoolean("hdr_active")||"hdr".equals(reason))outdoor=item(1,"HDR 优先","沿用系统输出范围");
        else if(StatusPresentation.scenes(s).optBoolean("manual_sunlight_active"))outdoor=item(1,"手动阳光屏","临时提高阳光可读性");
        else if("temperature".equals(reason))outdoor=item(1,"温度限制","降温后恢复判断");
        else if("power_save".equals(reason)||StatusAdvice.limited(o,"adjustBrightnessByPowerSaveMode",uptime))outdoor=item(1,"省电限制","系统保护优先");
        else if("hbm_budget".equals(reason))outdoor=item(1,"预算不足","等待高亮预算恢复");
        else if("scene".equals(reason)||"output_override".equals(reason))outdoor=item(1,"场景优先","沿用系统输出策略");
        else if(StatusAdvice.limited(o,"adjustBrightnessByOpr",uptime))outdoor=item(1,"画面限亮","可查看高亮范围设置");
        else if(o.optBoolean("active"))outdoor=item(1,"增强生效中","仍受系统保护限制");
        else if("enter_confirm".equals(reason))outdoor=item(1,"确认强光中","持续满足后进入");
        else if("cooldown".equals(reason))outdoor=item(1,"增强冷却中","等待下一次增强");
        else if(!s.optBoolean("auto_mode"))outdoor=item(1,"手动亮度","阳光屏由系统判断");
        else if(!"active".equals(s.optString("phase")))outdoor=item(1,"引擎未启用","保存并应用后生效");
        else if(!o.has("enabled"))outdoor=item(1,"状态未取得","点击查看高亮条件");
        else if(!o.optBoolean("enabled"))outdoor=item(1,"沿用系统","户外增强未开启");
        else outdoor=item(1,"等待强光",reason.equals("auto_not_ready")?"等待有效照度":"达到条件后增强");
        boolean limit=StatusAdvice.limited(o,"adjustBrightnessByThermal",uptime);
        if(limit)thermal=item(2,"正在限亮",s.optInt("thermal_severity",0)>=3?"系统高温保护优先":"温控已降低目标");
        else if("temperature".equals(reason))thermal=item(2,"温度阻止高亮","等待温度下降");
        else if(s.has("thermal_severity")&&s.optInt("thermal_severity")>=3)thermal=item(2,"高温保护","放宽条件已暂停");
        else if(s.optBoolean("thermal_relax"))thermal=item(2,s.optBoolean("thermal_permitted")?"已放宽条件":"放宽暂未生效","严重温控仍保留");
        else thermal=item(2,"沿用系统","未观察到当前限亮");
        return new Item[]{dark,outdoor,thermal};
    }
}
