package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Root binds read-only coordinates to the current display and system-server generation. */
final class ControlCenterRoot {
    static final String PROFILE="hyperlux_control_center_profile_v1";
    /** Root node access is independent of the curve engine. Retain an unchanged dormant v3 choice. */
    static void captureBinding(JSONObject config,JSONObject runtime,JSONObject stored,int mode)throws Exception{
        JSONObject node=null;Exception unavailable=null;
        try{node=NativePanelRoot.status();}catch(Exception failure){unavailable=failure;}
        if(node!=null&&node.optBoolean("supported")&&runtime.optBoolean(BrightnessControlOptions.capability(mode))){
            config.put("control_center_node_maximum",node.getInt("maximum")).put("control_center_node_path",node.getString("canonical_path"));return;
        }
        if(mode>=3&&stored!=null&&stored.optInt("control_center_node_mode")==mode&&stored.optInt("control_center_node_maximum")>10&&stored.optInt("control_center_node_maximum")<=65535&&!stored.optString("control_center_node_path").isEmpty()){
            config.put("control_center_node_maximum",stored.getInt("control_center_node_maximum")).put("control_center_node_path",stored.getString("control_center_node_path"));return;
        }
        throw new IllegalStateException("主屏节点或亮度坐标转换尚未就绪",unavailable);
    }
    static void reconcile(RootSettings settings,JSONObject runtime,String raw)throws Exception{
        JSONObject config=raw==null?new JSONObject():new JSONObject(raw);
        BrightnessControlOptions options=BrightnessControlOptions.parseStored(config);
        boolean enabled=config.optBoolean("enabled")&&options.nodeSliderMode>0&&runtime!=null&&runtime.optBoolean(BrightnessControlOptions.capability(options.nodeSliderMode))&&runtime.optJSONObject("control_center_slider")!=null&&runtime.getJSONObject("control_center_slider").optBoolean("registered")&&runtime.getJSONObject("control_center_slider").optInt("mode")>0&&config.optString("revision").equals(runtime.optString("revision"))&&"active".equals(runtime.optString("phase"));
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
        if(!tables.optBoolean("enabled")||!config.optString("revision").equals(tables.optString("revision"))||tables.optInt("user_serial",-1)!=config.optInt("user_serial",-2)||!runtime.getJSONObject("control_center_slider").optBoolean("coordinate_ready")||!AppBuild.BUILD.equals(tables.optString("build"))||tables.optInt("pid")!=runtime.getInt("pid")||!runtime.getString("process_start").equals(tables.optString("process_start"))||boot==null||!boot.equals(tables.optString("boot_count"))){settings.put(PROFILE,null);return;}
        if(options.nodeSliderMode>=3){
            if(tables.optString("physical").isEmpty()||!runtime.getJSONObject("control_center_slider").optBoolean("raw_ready"))throw new IllegalStateException("主屏节点控制接口尚未就绪");
            JSONObject guard=null;try{guard=NativePanelClient.call("STATUS");}catch(Exception absent){}
            if(guard!=null&&guard.optBoolean("armed")&&!guard.optBoolean("control_center_listener"))throw new IllegalStateException("请先结束主屏手动面板保持，再保存完整节点控制");
            // A listener sleeps without a lease. Only an explicit fresh UI gesture can arm it.
            NativePanelRoot.start(node,true,runtime.getInt("pid"));
        }else retireIdleWriter();
        JSONObject profile=new JSONObject().put("build",AppBuild.BUILD).put("pid",runtime.getInt("pid")).put("process_start",runtime.getString("process_start")).put("boot_count",boot).put("revision",config.getString("revision")).put("user_serial",config.getInt("user_serial")).put("mode",options.nodeSliderMode).put("maximum",node.getInt("maximum")).put("kind",options.nodeSliderMode==4?"partition_node_v4":options.nodeSliderMode==3?"raw_node_v3":"coordinate_v2").put("calibration",tables.getJSONObject("calibration"));
        if(options.nodeSliderMode>=3)profile.put("physical",tables.getString("physical"));
        if(options.nodeSliderMode==4){JSONObject partition=tables.optJSONObject("partition");if(partition==null)throw new IllegalStateException("普通亮度区间尚未确认");profile.put("partition",partition).put("partition_maximum",options.partitionMaximum);}
        if(!profile.toString().equals(settings.get(PROFILE))&&!settings.put(PROFILE,profile.toString()))throw new IllegalStateException("节点映射配置未写入");
    }
    static void retireIdleWriter(){try{JSONObject guard=NativePanelClient.call("STATUS");if(guard.optBoolean("control_center_listener")&&!guard.optBoolean("armed"))NativePanelRoot.stop();}catch(Exception absent){}}
}
