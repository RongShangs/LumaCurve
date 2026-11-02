package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Root binds read-only coordinates to the current display and system-server generation. */
final class ControlCenterRoot {
    static final String PROFILE="hyperlux_control_center_profile_v1";
    static final String RECOVERY="hyperlux_mapping_startup_v1";
    static JSONObject recover(RootSettings settings,JSONObject runtime,String raw,long now)throws Exception{return recover(settings,runtime,raw,now,"android_boot_job");}
    static JSONObject recover(RootSettings settings,JSONObject runtime,String raw,long now,String route)throws Exception{
        JSONObject config=raw==null?new JSONObject():new JSONObject(raw);int mode=config.optInt("control_center_node_mode",0);
        JSONObject result=new JSONObject().put("ok",true).put("build",AppBuild.BUILD).put("checked_elapsed_ms",now).put("mode",mode).put("route",route);
        String state,message;
        if(!config.optBoolean("enabled")||mode==0){state="disabled";message="未开启控制中心映射";}
        else if(!BrightnessControlOptions.mappingRecoveryWanted(config)){state="unsupported";message="当前映射用户或模式不适用";}
        else if(runtime==null||!"active".equals(runtime.optString("phase"))||!config.optString("revision").equals(runtime.optString("revision"))){state="pending";message="等待当前系统亮度引擎就绪";}
        else if(!runtime.optBoolean(BrightnessControlOptions.capability(mode))){state="pending".equals(runtime.optString(BrightnessControlOptions.capability(mode)+"_state"))?"pending":"unsupported";message="等待本机映射接口检查";}
        else{
            try{
                reconcile(settings,runtime,raw);String value=settings.get(PROFILE);JSONObject profile=value==null?null:new JSONObject(value);
                boolean current=profile!=null&&AppBuild.BUILD.equals(profile.optString("build"))&&profile.optInt("pid",-1)==runtime.optInt("pid",-2)&&runtime.optString("process_start").equals(profile.optString("process_start"))&&config.optString("revision").equals(profile.optString("revision"))&&String.valueOf(settings.get("boot_count")).equals(profile.optString("boot_count"))&&profile.optInt("mode",-1)==mode;
                state=current?"restored":"pending";message=current?"已恢复映射绑定，等待手动操作":"等待本次开机的映射标定";
            }catch(Exception unavailable){state="unavailable";message=unavailable.toString();}
        }
        result.put("boot_count",settings.get("boot_count")).put("pid",runtime==null?-1:runtime.optInt("pid",-1));
        result.put("state",state).put("message",message.substring(0,Math.min(300,message.length())));
        try{settings.put(RECOVERY,result.toString());if(route.equals("root_service_directory"))settings.put(RECOVERY+"_root",result.toString());}catch(Exception diagnosticsUnavailable){}
        return result;
    }
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
