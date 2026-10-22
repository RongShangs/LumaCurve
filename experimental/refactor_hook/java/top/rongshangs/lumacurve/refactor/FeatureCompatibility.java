package top.rongshangs.lumacurve.refactor;

import java.util.*;
import top.rongshangs.lumacurve.refactor.CompatibilityCatalog.Route;
import org.json.*;

/** Read-only catalogue of implemented routes, resolved from the current engine snapshot. */
public final class FeatureCompatibility {
    private FeatureCompatibility(){}
    static JSONObject object(JSONObject parent,String key){JSONObject value=parent==null?null:parent.optJSONObject(key);return value==null?new JSONObject():value;}
    static String text(JSONObject parent,String key){return parent==null?"":parent.optString(key,"");}
    static Route[] catalogue(String id){return CompatibilityCatalog.catalogue(id);}
    static String curveSelection(JSONObject runtime){String backend=text(runtime,"curve_backend");if(backend.equals("refactor"))return "refactor";if(!backend.equals("physical_mapping"))return "";String cls=text(runtime,"physical_mapper_class");if(cls.equals("com.android.server.display.MiuiPhysicalBrightnessMappingStrategy"))return "miui_physical";if(cls.equals("com.android.server.display.BrightnessMappingStrategy$PhysicalMappingStrategy"))return "aosp_physical";return "";}
    static Set<String> selected(String id,JSONObject runtime,JSONObject entry){
        Set<String> result=new LinkedHashSet<>();if(!"available".equals(entry.optString("state")))return result;
        Route[] routes=catalogue(id);String choice="";
        if(routes[0].id.equals("refactor"))choice=curveSelection(runtime);
        else switch(id){
        case "main_sensor":choice=text(object(runtime,"main_input_compatibility"),"profile");break;
        case "dark_lock_schedule":choice=text(object(object(runtime,"brightness_control"),"dark_lock_schedule"),"profile");break;
        case "animation_override":choice=text(runtime,"animation_compatibility_profile");break;
        case "node_output":String output=text(runtime,"outdoor_output_profile");choice=output.equals("automatic_strategy2_reason")?"automatic_strategy2_node":output.isEmpty()||output.equals("selected_strategy")?"raw_primary_output":"";break;
        case "outdoor_node_device":String device=text(runtime,"outdoor_node_device_profile");choice=device.equals("fold_bound_node")?"fold_bound_outdoor":device.isEmpty()||device.equals("primary_node")?"primary_node_profile":"";break;
        case "assist_sensor":JSONObject binding=object(runtime,"assist_compatibility");String mode=text(binding,"state"),kind=text(binding,"binding_kind");choice=kind.equals("native_auxiliary")||kind.equals("ported_auxiliary")?kind:mode.equals("native")?"native_auxiliary":mode.equals("bound")?"ported_auxiliary":"";break;
        case "hbm":choice=text(object(runtime,"outdoor"),"hbm_tuning_profile");String controller=text(object(runtime,"outdoor"),"hbm_controller_profile");if(controller.equals("enabled_field")||controller.equals("native_support_method"))result.add(controller);break;
        case "response":case "small_response":case "low_light":case "low_threshold":case "threshold_override":case "assist_gate":case "assist_override":choice=text(runtime,"response_scene_profile");break;
        case "outdoor":choice=text(object(runtime,"outdoor"),"target_compatibility_profile");break;
        case "opr":case "nit_limit":choice=text(object(runtime,"outdoor"),"sdr_compatibility_profile");break;
        case "apps":JSONObject control=object(runtime,"brightness_control");if(control.has("app_multiwindow_supported"))choice=control.optBoolean("app_multiwindow_supported")?"visible_tasks":"focus_fallback";break;
        case "outdoor_range":result.add("hbm_range");JSONObject out=object(runtime,"outdoor");if(out.optBoolean("dynamic_range_supported")&&out.optBoolean("dynamic_range_enabled"))result.add("dynamic_range");return result;
        case "main_panel_node":if(text(entry,"node_discovery").equals("fold_default_display")){choice="fold_default_display";break;}choice=text(entry,"node_class").equals("backlight")?"backlight_node":text(entry,"node_class").equals("leds")?"display_led_node":"";break;
        case "control_center_slider":if(text(object(runtime,"control_center_slider"),"kind").equals("coordinate_v2"))choice="coordinate_v2";break;
        default:if(routes.length==1&&!routes[0].id.equals("unreported"))choice=routes[0].id;
        }
        for(Route route:routes)if(route.id.equals(choice))result.add(choice);return result;
    }
    static String evidence(String id,JSONObject runtime,JSONObject entry){
        String backend=text(runtime,"curve_backend");JSONObject out=object(runtime,"outdoor"),assist=object(runtime,"assist_compatibility");
        if(catalogue(id)[0].id.equals("refactor")){String mapper=text(runtime,"physical_mapper_class");return "曲线后端："+(backend.isEmpty()?"未报告":backend)+(mapper.isEmpty()?"":"\n映射器："+mapper);}
        switch(id){
        case "main_sensor":JSONObject main=object(runtime,"main_input_compatibility");return "主光感来源："+text(main,"source")+"\n"+text(main,"sensor_name")+(text(main,"sensor_string_type").isEmpty()?"":" · "+text(main,"sensor_string_type"))+"\n配置光感："+text(main,"configured_sensor_name")+"\n选择方式："+text(main,"binding_source")+"\n"+text(main,"detail");
        case "animation_override":return "过渡适配："+(text(runtime,"animation_compatibility_profile").isEmpty()?"未报告":text(runtime,"animation_compatibility_profile"))+"\n仅调整普通自动亮度过渡；温控、唤醒、手动及其他特殊原因沿用系统。";
        case "assist_sensor":return "辅助绑定："+(text(assist,"state").isEmpty()?"未报告":text(assist,"state"))+(text(assist,"sensor_name").isEmpty()?"":"\n传感器："+text(assist,"sensor_name"))+(text(assist,"sensor_string_type").isEmpty()?"":" · "+text(assist,"sensor_string_type"))+(assist.has("dual_policy_cached")?"\n双光感能力缓存："+assist.optBoolean("dual_policy_cached"):"");
        case "dark_lock_schedule":JSONObject night=object(object(runtime,"brightness_control"),"dark_lock_schedule");return (night.optBoolean("known")?night.optBoolean("night")?"系统判定为日落后至日出前。":"系统判定为日出后至日落前。":"系统时间尚未提供或已过期，夜间锁定等待；全天暗光锁定不受影响。")+"\n"+text(night,"error");
        case "dark_lock":return "输入模式："+("single_main".equals(text(object(runtime,"main_input_compatibility"),"input_mode"))?"仅主光感":"dual_input".equals(text(object(runtime,"main_input_compatibility"),"input_mode"))?"主辅光感共同确认":"未报告")+"\n"+text(entry,"detail");
        case "hbm":return "计时适配："+(text(out,"hbm_tuning_profile").isEmpty()?"未报告":text(out,"hbm_tuning_profile"))+"\n"+"控制器适配："+(text(out,"hbm_controller_profile").isEmpty()?"未报告":text(out,"hbm_controller_profile"))+(out.has("original_minimum_lux")?"\n原生门槛："+out.optDouble("original_minimum_lux")+" lux":"")+(out.has("original_time_window_ms")?"\n原生计时（窗口 / 最大 / 最短）："+out.optLong("original_time_window_ms")+" / "+out.optLong("original_time_max_ms")+" / "+out.optLong("original_time_min_ms")+" ms":"");
        case "response":case "small_response":case "low_light":case "low_threshold":case "threshold_override":case "assist_gate":case "assist_override":return "场景适配："+(text(runtime,"response_scene_profile").isEmpty()?"未报告":text(runtime,"response_scene_profile"))+"\n"+text(entry,"detail");
        case "outdoor":return "目标适配："+(text(out,"target_compatibility_profile").isEmpty()?"未报告":text(out,"target_compatibility_profile"))+"\n"+text(entry,"detail");
        case "node_output":return "节点增强输出适配："+(text(runtime,"outdoor_output_profile").isEmpty()?"未报告":text(runtime,"outdoor_output_profile"))+"\n"+text(entry,"detail");
        case "outdoor_node_device":return "节点增强设备绑定："+(text(runtime,"outdoor_node_device_profile").isEmpty()?"未报告":text(runtime,"outdoor_node_device_profile"))+"\n"+text(entry,"detail");
        case "opr":case "nit_limit":return "SDR 适配："+(text(out,"sdr_compatibility_profile").isEmpty()?"未报告":text(out,"sdr_compatibility_profile"))+(text(out,"sdr_compatibility_detail").isEmpty()?"":"\n"+text(out,"sdr_compatibility_detail"));
        case "apps":JSONObject c=object(runtime,"brightness_control");return c.has("app_multiwindow_supported")?c.optBoolean("app_multiwindow_supported")?"本机可见任务接口完整；已接入多窗口判断。":"本机仅提供已验证的焦点回退；不能保证小窗失焦时继续暂停。":"多窗口接口信息尚未报告。";
        case "main_panel_node":return text(entry,"node_path").isEmpty()?(text(entry,"node_detail").isEmpty()?"尚未报告具体主屏节点。":"节点状态："+text(entry,"node_detail")):"实际节点："+text(entry,"node_path")+(text(entry,"node_physical_display_id").isEmpty()?"":"\n物理屏："+text(entry,"node_physical_display_id"))+(text(entry,"node_address_profile").isEmpty()?"":"\n地址类型："+text(entry,"node_address_profile"))+(text(entry,"node_binding").isEmpty()?"":"\n节点绑定："+(text(entry,"node_binding").equals("exclusive_internal_dsi")?"默认内部屏点亮 + 唯一活动 DSI":"原生端口配对")+" · 显示端口 "+entry.optInt("node_physical_port")+" / 背光节点 "+entry.optInt("node_kernel_port"))+"\n节点上限："+entry.optInt("node_maximum");
        case "control_center_slider":return "入口隐藏；不改变已有选择。\n"+text(object(runtime,"control_center_slider"),"error");
        default:return text(entry,"detail");
        }
    }
    static String unused(Route route,Set<String> chosen,String id,JSONObject runtime,JSONObject entry){
        if("inactive".equals(entry.optString("state")))return "系统未启用此策略；保存的设置保留。";
        if(!"available".equals(entry.optString("state")))return "本次未采用："+entry.optString("detail","等待实际接口检查。");
        if(chosen.isEmpty())return "本次报告未提供足够的路线信息；未确认，不推断为不支持。";
        if(id.equals("assist_sensor"))return chosen.contains("native_auxiliary")?"原生辅助槽已有效绑定，优先沿用原路径；不启用移植补位。":"原生辅助槽原为空，本次使用经检查的补位路径；保留原生优先规则。";
        if(id.equals("node_output"))return chosen.contains("automatic_strategy2_node")?"原生策略入口不存在，本次核验独立自动对象和亮度原因。":"原生策略入口已匹配，不叠加旧对象路线。";
        if(id.equals("outdoor_node_device"))return chosen.contains("fold_bound_outdoor")?"本次使用折叠物理租约，不使用固定主屏节点。":"当前使用原主屏节点路径，未启用折叠协作绑定。";
        if(id.equals("hbm")){if(route.id.equals("enabled_field")||route.id.equals("native_support_method"))return chosen.contains("enabled_field")?"已匹配原生启用字段，不采用旧控制器判断。":chosen.contains("native_support_method")?"本机没有启用字段，采用完整旧结构的原生支持方法。":"本次报告未提供控制器结构，尚未确认。";return chosen.contains("ambient_only")?"本机三个原生计时字段全零，没有可调整的时间预算。":"本机使用已验证的有限时间预算，不能套用零计时分支。";}
        if(id.equals("opr")||id.equals("nit_limit"))return chosen.contains("privacy_aware")?"已匹配隐私显示接口，不能降级套用传统 OPR。":"当前匹配传统 OPR 完整接口；未选中新式隐私显示分支。";
        if(id.equals("apps"))return chosen.contains("visible_tasks")?"已有完整可见任务判断，不采用精度较低的焦点回退。":"本机缺少完整可见任务能力，本次使用焦点回退。";
        if(id.equals("outdoor_range")&&route.id.equals("dynamic_range"))return object(runtime,"outdoor").optBoolean("dynamic_range_supported")?"补充接口已匹配，但本机未启用动态范围；当前不参与。":"未匹配该补充接口，保留 HBM 与峰值范围路径。";
        return "当前已选择与本机实际对象匹配的另一条路径；此路线不会同时接管。";
    }
    static int group(String id){return CompatibilityCatalog.group(id);}
    static boolean unconfirmed(String id,Route route,JSONObject runtime,JSONObject entry,Set<String> chosen){
        if(!"available".equals(entry.optString("state")))return false;
        if(chosen.isEmpty())return true;
        if(id.equals("hbm")&&(route.id.equals("enabled_field")||route.id.equals("native_support_method")))return !chosen.contains("enabled_field")&&!chosen.contains("native_support_method");
        JSONObject out=object(runtime,"outdoor");return id.equals("outdoor_range")&&route.id.equals("dynamic_range")&&out.optBoolean("dynamic_range_supported")&&!out.has("dynamic_range_enabled");
    }
    public static JSONObject annotate(JSONObject report,JSONObject runtime,String source)throws JSONException{
        JSONObject result=report==null?new JSONObject():new JSONObject(report.toString());JSONArray entries=result.optJSONArray("features");if(entries==null)entries=new JSONArray();result.put("features",entries);
        for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;String id=entry.optString("id");Set<String> chosen=selected(id,runtime,entry);JSONArray routes=new JSONArray();List<String> names=new ArrayList<>();
            for(Route route:catalogue(id)){boolean used=chosen.contains(route.id),unknown=!used&&unconfirmed(id,route,runtime,entry,chosen);if(used)names.add(route.name);routes.put(new JSONObject().put("id",route.id).put("name",route.name).put("condition",route.condition).put("selected",used).put("status",used?"selected":unknown?"unconfirmed":"not_selected").put("reason",used?"本机本次检查所采用的路线；接口可用不等于功能已开启。":unknown?"本次报告未提供完整的路线依据，尚未确认。":unused(route,chosen,id,runtime,entry)));}
            entry.put("group",group(id)).put("source",source).put("routes",routes).put("route_name",names.isEmpty()?"inactive".equals(entry.optString("state"))?"系统未启用此路径":"available".equals(entry.optString("state"))?"具体路线尚未确认":"尚无可执行路线":String.join(" ＋ ",names)).put("evidence",evidence(id,runtime,entry));
        }return result;
    }
    public static JSONArray combine(JSONObject runtime,JSONObject root,JSONObject app)throws JSONException{JSONArray all=new JSONArray();for(JSONObject report:new JSONObject[]{annotate(object(runtime,"feature_self_check"),runtime,"engine"),annotate(root,runtime,"root"),annotate(app,runtime,"app")}){JSONArray rows=report.getJSONArray("features");for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null){row.put("checked_elapsed_ms",report.optLong("checked_elapsed_ms",-1)).put("checked_unix_ms",report.optLong("checked_unix_ms",-1)).put("check_request",report.optString("request"));all.put(row);}}}return all;}
}
