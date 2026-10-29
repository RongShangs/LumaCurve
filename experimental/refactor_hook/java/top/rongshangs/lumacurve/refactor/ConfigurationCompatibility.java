package top.rongshangs.lumacurve.refactor;
import org.json.*;

/** An OTA may change version text while the detected device baseline is unchanged. */
public final class ConfigurationCompatibility {
    /** Submission validation may retain unchanged dormant auxiliary choices. Never use this copy as stored config or runtime input. */
    public static JSONObject submissionValidation(JSONObject proposed,JSONObject caps,JSONObject stored,String fingerprint)throws JSONException{
        JSONObject checked=new JSONObject(proposed.toString());
        if(!canReuse(stored,caps,fingerprint))return checked;
        String sliderCap=BrightnessControlOptions.capability(proposed.optInt("control_center_node_mode"));
        if(caps.has(sliderCap)&&!caps.optBoolean(sliderCap)&&proposed.optInt("control_center_node_mode",0)>0&&proposed.optInt("control_center_node_mode")==stored.optInt("control_center_node_mode"))checked.put("control_center_node_mode",0);
        if(caps.has("outdoor_node_supported")&&!caps.optBoolean("outdoor_node_supported")&&proposed.optBoolean("outdoor_node_boost")&&stored.optBoolean("outdoor_node_boost")&&OutdoorOptions.parse(proposed).same(OutdoorOptions.parse(stored)))checked.put("outdoor_node_boost",false);
        String[][] groups={{"assist_override","assist_override_supported","assist_brighten","assist_darken","assist_small"},
            {"low_light_assist_gate","low_light_assist_gate_supported","low_light_assist_wait","low_light_assist_tolerance"}};
        for(String[] group:groups){
            if(!Boolean.TRUE.equals(proposed.opt(group[0]))||!Boolean.TRUE.equals(stored.opt(group[0]))||!caps.has(group[1])||caps.optBoolean(group[1]))continue;
            boolean unchanged=true;for(int i=2;i<group.length;i++){Object a=proposed.opt(group[i]),b=stored.opt(group[i]);
                if(a instanceof Number&&b instanceof Number){double x=((Number)a).doubleValue(),y=((Number)b).doubleValue();if(!Double.isFinite(x)||Double.compare(x,y)!=0)unchanged=false;}
                else if(!java.util.Objects.equals(a,b))unchanged=false;
            }
            if(unchanged)checked.put(group[0],false);
        }
        return checked;
    }
    public static JSONObject fit(JSONObject original,JSONObject caps,JSONArray paused)throws JSONException{
        JSONObject out=new JSONObject(original.toString());
        MemoryOptions holds=MemoryOptions.parse(out);holds.put(out);
        if(caps.has("persistent_memory_supported")&&!caps.optBoolean("persistent_memory_supported")&&holds.enabled()){out.put("memory_persist",false).put("memory_restore_unlock",false).put("memory_hold_switch",false).put("memory_hold_forever",false).put("memory_hold_theme",false);paused.put("保持记忆");}
        if(caps.has("memory_mode_supported")&&!caps.optBoolean("memory_mode_supported")&&(holds.modeSwitch||holds.forever)){out.put("memory_hold_switch",false).put("memory_hold_forever",false);paused.put("跨自动亮度开关保持记忆");}
        if(caps.has("memory_forever_supported")&&!caps.optBoolean("memory_forever_supported")&&out.optBoolean("memory_hold_forever")){out.put("memory_hold_forever",false);paused.put("永久保持记忆");}
        String sliderCap=BrightnessControlOptions.capability(out.optInt("control_center_node_mode"));
        if(out.optInt("control_center_node_mode",0)>0&&caps.has(sliderCap)&&!caps.optBoolean(sliderCap)){out.put("control_center_node_mode",0);paused.put("控制中心节点映射");}
        if(caps.has("persistent_memory_supported")&&!caps.optBoolean("persistent_memory_supported")&&out.optBoolean("memory_persist",false)){out.put("memory_persist",false);paused.put("记忆持久化");}
        String[][] flags={{"dark_lock_enabled","dark_lock_supported","暗光锁定"},{"manual_panel_enabled","manual_panel_supported","主屏手动亮度"},{"memory_persist","persistent_memory_supported","记忆持久化"},{"thermal_relax","thermal_supported","温控调整"},{"response_override","response_supported","变化确认"},{"small_brighten_override","small_response_supported","微小变亮确认"},{"low_light_stability","low_light_supported","暗光稳定"},{"low_light_threshold","low_light_threshold_supported","暗光阈值"},{"low_light_assist_gate","low_light_assist_gate_supported","辅助光感闸门"},{"outdoor_enabled","outdoor_supported","户外增强"},{"outdoor_hbm_tuning","outdoor_hbm_supported","HBM 预算"},{"outdoor_range_unlock","outdoor_range_supported","高亮范围"},{"outdoor_opr_relax","outdoor_opr_supported","画面限亮放宽"},{"outdoor_node_boost","outdoor_node_supported","节点高亮兼容"},{"memory_reset_override","memory_reset_supported","记忆重置条件"},{"memory_timeout_override","memory_timeout_supported","记忆超时"},{"threshold_override","threshold_override_supported","光感阈值"},{"assist_override","assist_override_supported","辅助光感确认"},{"animation_override","animation_override_supported","亮度过渡"},{"sunlight_override","sunlight_override_supported","阳光模式条件"},{"touch_override","touch_override_supported","触摸遮挡"}};
        for(String[] f:flags)if(Boolean.TRUE.equals(out.opt(f[0]))&&caps.has(f[1])&&!caps.optBoolean(f[1])){out.put(f[0],false);paused.put(f[2]);}
        if(!out.optBoolean("outdoor_enabled"))out.put("outdoor_hbm_tuning",false).put("outdoor_range_unlock",false).put("outdoor_opr_relax",false).put("outdoor_node_boost",false);
        if(!out.optBoolean("outdoor_range_unlock"))out.put("outdoor_opr_relax",false).put("outdoor_node_boost",false);
        if(!out.optBoolean("outdoor_opr_relax"))out.put("outdoor_node_boost",false);
        if(out.optDouble("outdoor_max_nit",0)>0&&caps.has("outdoor_nit_limit_supported")&&!caps.optBoolean("outdoor_nit_limit_supported")){out.put("outdoor_max_nit",0);paused.put("户外 nit 上限");}
        JSONArray apps=out.optJSONArray("manual_brightness_apps");if(apps!=null&&apps.length()>0&&caps.has("app_auto_supported")&&!caps.optBoolean("app_auto_supported")){out.put("manual_brightness_apps",new JSONArray());paused.put("暂停自动亮度名单");}
        JSONObject scenes=out.optJSONObject("scene_options"),policies=scenes==null?null:scenes.optJSONObject("policies");if(policies!=null){java.util.List<String> remove=new java.util.ArrayList<>();java.util.Iterator<String> keys=policies.keys();while(keys.hasNext()){String id=keys.next();String cap="scene_"+id+"_supported";if(caps.has(cap)&&!caps.optBoolean(cap))remove.add(id);}for(String id:remove){policies.remove(id);paused.put("部分场景自定义");}}
        return out;
    }
    static String hardware(String fingerprint){int split=fingerprint.indexOf(':');return split>0?fingerprint.substring(0,split):"";}
    public static boolean canReuse(JSONObject config,JSONObject runtime,String fingerprint){
        if(config==null||runtime==null)return false;
        String backend=runtime.optString("curve_backend"),baseline=runtime.optString("baseline_id");
        if(!backend.equals(config.optString("curve_backend",backend.equals("refactor")?"refactor":""))||config.has("baseline_id")&&!baseline.equals(config.optString("baseline_id"))||config.has("user_serial")&&config.optInt("user_serial",-1)!=runtime.optInt("user_serial",-1))return false;
        try{revalidate(config,fingerprint,backend,baseline);return true;}catch(IllegalArgumentException incompatible){return false;}
    }
    public static boolean revalidate(JSONObject config,String fingerprint,String backend,String baseline){
        String saved=config.optString("fingerprint");if(fingerprint.equals(saved))return false;
        if(hardware(fingerprint).isEmpty()||!hardware(fingerprint).equals(hardware(saved))||baseline==null||baseline.isEmpty()||!baseline.equals(config.optString("baseline_id"))||!backend.equals(config.optString("curve_backend")))
            throw new IllegalArgumentException("系统曲线基准或控制路径已变化，请重新应用设置");
        // The caller must still validate every requested feature against the current ABI.
        return true;
    }
}
