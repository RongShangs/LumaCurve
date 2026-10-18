package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Root binds read-only coordinates to the current display and system-server generation. */
final class ControlCenterRoot {
    static final String PROFILE="hyperlux_control_center_profile_v1";
    static void reconcile(RootSettings settings,JSONObject runtime,String raw)throws Exception{
        JSONObject config=raw==null?new JSONObject():new JSONObject(raw);
        BrightnessControlOptions options=BrightnessControlOptions.parseStored(config);
        boolean enabled=config.optBoolean("enabled")&&options.nodeSliderMode>0&&runtime!=null&&runtime.optBoolean("control_center_slider_supported")&&runtime.optJSONObject("control_center_slider")!=null&&runtime.getJSONObject("control_center_slider").optBoolean("registered")&&runtime.getJSONObject("control_center_slider").optInt("mode")>0&&config.optString("revision").equals(runtime.optString("revision"))&&"active".equals(runtime.optString("phase"));
        if(!enabled){
            if(settings.get(PROFILE)==null)return;settings.put(PROFILE,null);
            retireIdleWriter();
            return;
        }
        JSONObject node=NativePanelRoot.status();
        if(!node.optBoolean("supported")||node.optInt("maximum")!=config.optInt("control_center_node_maximum")||!node.optString("canonical_path").equals(config.optString("control_center_node_path"))){settings.put(PROFILE,null);throw new IllegalStateException("控制中心主屏节点身份已变化，请重新保存");}
        String tableRaw=settings.get(ControlCenterEngine.TABLES),boot=settings.get("boot_count");
        if(tableRaw==null||tableRaw.length()>260000){settings.put(PROFILE,null);return;}
        JSONObject tables=new JSONObject(tableRaw);
        if(!runtime.getJSONObject("control_center_slider").optBoolean("coordinate_ready")||!AppBuild.BUILD.equals(tables.optString("build"))||tables.optInt("pid")!=runtime.getInt("pid")||!runtime.getString("process_start").equals(tables.optString("process_start"))||boot==null||!boot.equals(tables.optString("boot_count"))){settings.put(PROFILE,null);return;}
        retireIdleWriter();
        String profile=new JSONObject().put("build",AppBuild.BUILD).put("pid",runtime.getInt("pid")).put("process_start",runtime.getString("process_start")).put("boot_count",boot).put("mode",options.nodeSliderMode).put("maximum",node.getInt("maximum")).put("kind","coordinate_v2").put("calibration",tables.getJSONObject("calibration")).toString();if(!profile.equals(settings.get(PROFILE)))settings.put(PROFILE,profile);
    }
    static void retireIdleWriter(){try{JSONObject guard=NativePanelClient.call("STATUS");if(guard.optBoolean("control_center_listener")&&!guard.optBoolean("armed"))NativePanelRoot.stop();}catch(Exception absent){}}
}
