package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Conditions are OEM facts, not a claim that sensor presence implies an active scene. */
final class SceneCatalog {
    static final String[] FIELDS={"night_driving","step_mode","touch_protection_active","proximity_near","stream_near","reflective","night_wake","manual_sunlight_active","game"};
    static final String[] BRIEF={
        "抬高夜间亮度下限，延长确认；使用主光感时暂停辅助输入。",
        "延后降亮，减少步行时光线波动的影响。",
        "忽略手指遮挡造成的照度变化。",
        "遮挡时暂停照度更新，避免误降亮。",
        "变暗前确认是否被遮挡，确认期间暂缓降亮。",
        "等待反射确认，避免短时遮挡造成降亮。",
        "唤醒时临时使用系统夜间亮度目标。",
        "手动亮度下临时增亮，提高阳光可读性。",
        "游戏入场或触摸期间暂缓降亮。"
    };
    static final String[] CONDITIONS={
        "系统驾驶传感器识别为驾驶，且当前时间在系统日落之后或日出之前，设备允许夜间驾驶亮度策略。退出：驾驶识别结束或进入白天。识别标签不证明正在开车。",
        "设备启用步行检测且收到步行事件；每次事件续期，超过系统有效时间退出。仅增加降亮确认时间，不改变基础曲线。",
        "手指进入系统定义的光感保护区域，或离开后尚未超过触摸释放等待。保护区域由机型、旋转和折叠姿态决定。",
        "普通距离采样开启，距离读数非负且小于本机距离阈值。离开阈值后解除；本机可能没有启用此策略。",
        "系统游戏策略允许流式距离检测，发生待确认的变暗事件后读取流式传感器。系统按原始通道与两级阈值判定遮挡；无遮挡才继续降亮。位置以本机传感器为准。",
        "主显示发生变暗候选，照度落在本机 AON 区间；服务返回反射判定或等待确认期间暂缓降亮。反射标记与等待标记分别显示，不把旧的反射标记当作当前正在阻止降亮。",
        "上游夜间唤醒策略传入两个状态位；亮屏时使用夜间唤醒目标。解除由上游策略通知，具体识别时间段未在该亮度消费者中定义。",
        "系统自动亮度关闭、阳光屏开关开启、屏幕亮起、未被用户临时停用且未挂起；照度持续超过系统门槛后进入。与自动亮度 HBM 是两条不同链路。",
        "应用在系统游戏名单内，且本机采用游戏触摸防误降亮分支；入场短期或最近触摸期间抑制降亮。设备的距离/触摸策略决定是否走此分支。"
    };
    static final String[] EFFECTS={
        "使用主光感时重置并忽略辅助事件；提高夜间亮度下限，并增加变亮/变暗确认时间。手动调低后系统可停止抬高下限。",
        "延后降亮，避免步行造成的短时光线波动。",
        "暂不采纳受触摸遮挡影响的环境照度，可能同时延后变亮和变暗。",
        "丢弃遮挡期间照度更新，避免被误认为环境变暗；此设置不改变通话息屏策略。",
        "只作用于亮度的变暗确认，不修改距离传感器、通话息屏或传感器原始阈值。",
        "延后受遮挡/反射影响的变暗事件，不是额外增加亮度。",
        "夜间唤醒目标可以临时替代一般自动亮度目标。",
        "在手动亮度下临时提高阳光可读性；关闭此场景不关闭自动亮度的高亮范围。",
        "暂缓游戏中的降亮；不改变游戏画质、应用名单或应用自定义曲线。"
    };
    static final class Row {final String id,title,state,condition,effect,values;final int group;Row(String id,String title,String state,String condition,String effect,String values,int group){this.id=id;this.title=title;this.state=state;this.condition=condition;this.effect=effect;this.values=values;this.group=group;}}
    static String flag(JSONObject j,String key){Object v=j.opt(key);return !(v instanceof Boolean)?"未取得":(Boolean)v?"是":"否";}
    static String number(JSONObject j,String key,String unit){Object v=j.opt(key);if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue())||((Number)v).doubleValue()<0)return "未取得";return String.format(Locale.ROOT,"%.0f",((Number)v).doubleValue())+unit;}
    static String time(JSONObject j,String key){int n=j.optInt(key,-1);return n<0||n>1439?"未取得":String.format(Locale.ROOT,"%02d:%02d",n/60,n%60);}
    static boolean denied(JSONObject s,String id){JSONObject c=s==null?null:s.optJSONObject("scene_control"),d=c==null?null:c.optJSONObject("denied");return d!=null&&Boolean.TRUE.equals(d.opt(id));}
    static List<Row> read(JSONObject s,long elapsed){List<Row> rows=new ArrayList<>();boolean fresh=StatusPresentation.fresh(s,elapsed);JSONObject o=fresh?StatusPresentation.scenes(s):new JSONObject();
        for(int i=0;i<SceneOptions.IDS.length;i++){String id=SceneOptions.IDS[i];String state=!fresh?s==null?"等待读取":"状态待刷新":"系统判定："+flag(o,FIELDS[i])+(denied(s,id)?" · 已阻止参与亮度控制":"");String values="";
            if(i==0)values="驾驶识别："+flag(o,"driving")+" · 日出 "+time(o,"sunrise_minutes")+" · 日落 "+time(o,"sunset_minutes")+" · 下限 "+number(o,"night_driving_min_nit"," nit")+" · 额外确认 "+number(o,"night_driving_extra_ms"," ms");
            if(i==1)values="有效时间 "+number(o,"step_effective_ms"," ms")+" · 当前额外降亮确认 "+number(o,"step_extra_ms"," ms");
            if(i==2)values="释放等待 "+number(o,"touch_release_seconds"," 秒");
            if(i==3)values="策略启用："+flag(o,"proximity_policy_enabled")+" · 采样开启："+flag(o,"proximity_sampling")+" · 距离阈值 "+number(o,"proximity_threshold"," cm");
            if(i==4)values="待确认："+flag(o,"need_proximity")+" · 流式状态 "+number(o,"stream_state","")+" · "+o.optString("stream_sensor_name","传感器名称未取得");
            if(i==5)values="待确认："+flag(o,"need_aon")+" · 照度 "+number(o,"aon_min_lux"," lux")+"～"+number(o,"aon_max_lux"," lux")+" · 最长等待 "+number(o,"aon_max_wait_ms"," ms");
            if(i==6)values="使用唤醒目标："+flag(o,"night_wake_target");
            if(i==7)values="用户开关："+flag(o,"manual_sunlight_enabled")+" · 用户临时停用："+flag(o,"manual_sunlight_user_disabled")+" · 手动亮度门槛 "+number(o,"manual_sunlight_nit_condition"," nit");
            if(i==8)values="距离与触摸联合策略："+flag(o,"prox_touch_enabled");
            rows.add(new Row(id,SceneOptions.NAMES[i],state,CONDITIONS[i],EFFECTS[i],values,i==7?2:5));
        }
        rows.add(new Row("back_cover","背面距离与辅助光感","亮度控制状态：未取得","即使本机存在背面距离传感器，也不表示亮度框架公开了其遮挡判定。辅助光感为 0 或被重置不能作为背面遮挡证据。","只展示已由系统亮度链路提供的状态；不新增后台采样，不虚构遮挡门槛。","",-1));
        rows.add(new Row("hdr","HDR 与杜比显示",fresh?"HDR："+flag(s,"hdr_active")+" · 杜比："+flag(o,"dolby_enabled"):"等待读取","显示合成器检测 HDR 图层，或系统杜比显示策略生效；结束由显示内容与系统策略决定。","可能替代普通 SDR 输出范围；场景页面展示条件，亮度工具不关闭内容格式或系统显示保护。","",-1));
        rows.add(new Row("hbm","自动高亮 HBM",fresh?"照度条件满足："+flag(o,"hbm_ambient_allowed")+" · 时间预算可用："+flag(o,"hbm_time_available"):"等待读取","自动亮度、亮屏、达到本机 HBM 照度门槛，并满足温度、省电、预算和显示范围条件。","决定可用高亮范围；最终仍受驱动、温控和功率限制。","照度门槛 "+number(o,"hbm_minimum_lux"," lux"),2));
        rows.add(new Row("thermal","温控、省电与电池保护",fresh?"温控等级 "+number(s,"thermal_severity","")+" · 低功耗阻止 HBM："+flag(o,"hbm_low_power_block"):"等待读取","由系统温度、电池、功耗及高亮时间预算等条件触发，分别作用于输出上限。","实际正在限亮的原因在状态页下方分别显示；严重温控和驱动保护继续有效。","",9));
        rows.add(new Row("assist_interference","手电、相机与辅助光感",fresh?"手电："+flag(o,"torch_open")+" · 后置相机："+flag(o,"back_camera")+" · 输入可用："+flag(o,"assist_source_valid"):"等待读取","系统辅助输入有效性检查综合后置相机、辅助输入开关、手电状态与关闭后的等待；以本机检查结果为准。","干扰期间可能丢弃辅助光感事件；这与夜间驾驶清空辅助读数是不同原因。","",-1));
        rows.add(new Row("rear_display","背屏与姿态",fresh?"另一显示器状态 "+number(o,"other_display_state",""):"等待读取","支持背屏的设备，在另一屏亮起且主光感处于暗处时，系统可把辅助输入按零照度处理；旋转与折叠也可能改变触摸保护区域。","不把辅助照度为零解释成硬件故障或背面距离遮挡。","",-1));
        rows.add(new Row("app_curve","应用自定义亮度","以本机曲线与输出策略为准","系统可按前台应用类别、方向和自定义亮度控制器调整目标；是否启用由机型与系统配置决定。","发生在映射与输出之间；查看完整亮度链路可以区分基础曲线目标与场景调整后的输出。","",-1));
        JSONObject control=fresh?s.optJSONObject("brightness_control"):null;
        rows.add(new Row("output_owner","手动、暗光锁定与显示状态",control==null?"等待读取":"输出归属："+control.optString("owner","未取得"),"手动操作、暗光锁定、锁屏、息屏与常亮显示分别影响自动采样或输出归属；条件在对应功能中设置。","没有自动采样时保留有效旧值并标明暂停，不把它当作新的环境判定。","",7));
        return rows;
    }
    static boolean active(String id,JSONObject s,long elapsed){if(!StatusPresentation.fresh(s,elapsed))return false;JSONObject o=StatusPresentation.scenes(s);int i=SceneOptions.index(id);
        if(i>=0)return Boolean.TRUE.equals(o.opt(i==4?"need_proximity":i==5?"need_aon":i==6?"night_wake_target":i==8?"game_darken_blocked":FIELDS[i]))&&(i!=1||o.optDouble("step_extra_ms",0)>0)&&(i!=3||Boolean.TRUE.equals(o.opt("proximity_policy_enabled")))&&!denied(s,id);
        if(id.equals("hdr"))return s.optBoolean("hdr_active")||o.optBoolean("dolby_enabled");
        if(id.equals("hbm"))return s.optInt("hbm_mode",0)==1;
        if(id.equals("assist_interference"))return o.optBoolean("torch_open")||o.optBoolean("back_camera");
        if(id.equals("output_owner")){JSONObject c=s.optJSONObject("brightness_control");return s.has("auto_mode")&&!s.optBoolean("auto_mode")||c!=null&&Arrays.asList("dark","panel","raw_panel").contains(c.optString("owner"));}
        return false;
    }
    static String brief(Row r){int i=SceneOptions.index(r.id);if(i>=0)return BRIEF[i];switch(r.id){
        case "back_cover":return "背面遮挡未取得；不根据辅助照度为零推断。";
        case "hdr":return "按显示内容使用 HDR 或杜比输出范围。";
        case "hbm":return "强光下开放高亮范围，仍受温控与时间预算限制。";
        case "thermal":return "温度、电量或功耗保护可降低输出上限。";
        case "assist_interference":return "相机或手电干扰时，系统可能暂停辅助输入。";
        case "rear_display":return "背屏与姿态可能改变辅助输入或触摸保护区域。";
        case "app_curve":return "系统可按前台应用调整亮度目标。";
        default:return "手动或暗光锁定保持亮度时，自动输出暂时让位。";
    }}
    static String briefState(Row r,JSONObject s,long elapsed){if(!StatusPresentation.fresh(s,elapsed))return s==null?"等待读取":"状态待刷新";if(denied(s,r.id))return "已阻止参与";if(active(r.id,s,elapsed))return "生效中";int i=SceneOptions.index(r.id);JSONObject o=StatusPresentation.scenes(s);if(i>=0){String key=i==4?"need_proximity":i==5?"need_aon":i==6?"night_wake_target":i==8?"game_darken_blocked":FIELDS[i];return o.opt(key) instanceof Boolean?"未生效":"状态未取得";}return "back_cover".equals(r.id)||"app_curve".equals(r.id)||"rear_display".equals(r.id)?"状态未取得":"系统管理";}
    static String summary(JSONObject s,long elapsed){if(!StatusPresentation.fresh(s,elapsed))return s==null?"场景：等待读取":"场景：状态待刷新";JSONObject o=StatusPresentation.scenes(s);List<String> names=new ArrayList<>();for(String id:SceneOptions.IDS)if(active(id,s,elapsed))names.add(SceneOptions.NAMES[SceneOptions.index(id)]);if(active("hdr",s,elapsed))names.add("HDR 与杜比显示");return "场景："+(names.isEmpty()?o.length()==0?"未取得":"常规":String.join(" · ",names));}
}
