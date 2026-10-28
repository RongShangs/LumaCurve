package top.rongshangs.lumacurve.refactor;
import android.os.SystemClock;
import android.hardware.Sensor;
import org.json.*;

/** Run on the display handler at engine/UI startup; never modifies brightness or sensors. */
final class RuntimeFeatureChecks {
    final HookRuntime s;JSONObject caps=new JSONObject(),report=new JSONObject();long runs;
    RuntimeFeatureChecks(HookRuntime s){this.s=s;}
    HbmAccess checkedHbm;
    HbmAccess hbm()throws Exception{if(checkedHbm==null)checkedHbm=new HbmAccess(HookEntry.get(s.owner,"mHbmController"));return checkedHbm;}
    boolean outdoor()throws Exception{return OutdoorTuning.owners.contains(s.owner.getClass())&&hbm()!=null;}
    boolean range()throws Exception{return outdoor()&&OutdoorTuning.ranges.contains(hbm().controller.getClass())&&OutdoorTuning.peaks.contains(s.owner.getClass());}
    boolean sensor(boolean assist)throws Exception{
        if(!assist)return !BrightnessSensorAccess.selection(s.owner).profile.isEmpty();
        Object value=HookEntry.get(HookEntry.get(HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),"mDualSensorPolicy"),"mAssistLightSensor");
        return value instanceof Sensor&&AuxiliarySensorIdentity.accepted((Sensor)value);
    }
    boolean darkSensors()throws Exception{BrightnessSensorAccess.read(s.owner);return true;}
    boolean assistAbsent(){try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),dual=HookEntry.get(impl,"mDualSensorPolicy");return dual==null||HookEntry.get(dual,"mAssistLightSensor")==null;}catch(Throwable unknown){return false;}}
    boolean pending(){JSONArray entries=report.optJSONArray("features");if(entries!=null)for(int i=0;i<entries.length();i++)if("pending".equals(entries.optJSONObject(i).optString("state")))return true;return false;}
    void run(String request){
        checkedHbm=null;
        FeatureSelfCheck f=new FeatureSelfCheck();
        f.check("curve","基础曲线",null,false,()->{new CurvePlan(s.kernel.factoryLux,s.kernel.factoryNit,s.kernel.min,s.kernel.max,new float[]{1,1,1,1});return true;});
        f.check("dim_aod","熄屏前降亮协调",null,DimAodCompatibility.matched(s.owner.getClass()),()->DimAodCompatibility.supported(s.owner));
        f.check("main_sensor","主光感",null,true,()->sensor(false));
        boolean missingAssist=assistAbsent();
        if(missingAssist)f.inactive("assist_sensor","辅助光感",null,"当前系统未接入辅助光感，沿用主光感输入；不能据此认定硬件不存在。其他功能独立检查，用户设置保留。");
        else f.check("assist_sensor","辅助光感",null,true,()->sensor(true));
        f.check("manual_memory","手动记忆",null,false,()->s.kernel.manualPointCapacity()>0);
        f.check("saved_curve","记忆转基础曲线","saved_curve_supported",false,()->s.kernel.savedCurveSupported());
        f.check("persistent_memory","记忆持久化","persistent_memory_supported",false,()->s.kernel.persistentMemorySupported());
        f.check("memory_mode","跨开关保持记忆","memory_mode_supported",false,()->s.memoryLifecycle.modeSupported);
        f.check("memory_forever","永久保持记忆","memory_forever_supported",false,()->s.memoryLifecycle.foreverSupported);
        f.check("memory_reset","记忆重置条件","memory_reset_supported",false,()->s.memoryLifecycle.resetSupported);
        f.check("memory_timeout","记忆超时","memory_timeout_supported",false,()->s.memoryLifecycle.timeoutSupported);
        f.check("dark_lock","暗光锁定","dark_lock_supported",true,()->s.receiverRegistered&&s.brightnessControl.available()&&darkSensors());
        f.check("dark_lock_schedule","日落后暗光锁定","dark_lock_schedule_supported",true,()->DarkLockSchedule.supported(s.owner.getClass().getClassLoader()));
        f.check("manual_panel","主屏手动亮度","manual_panel_supported",true,()->s.receiverRegistered&&RawPanelOutputHooks.supports(s.owner.getClass()));
        f.check("control_center_slider","控制中心节点映射","control_center_slider_supported",s.controlCenter.waiting(),()->s.receiverRegistered&&s.controlCenter.supported());
        f.check("control_center_raw","控制中心完整节点控制","control_center_raw_supported",s.controlCenter.waiting()&&s.controlCenter.nodes.registered,()->s.receiverRegistered&&s.controlCenter.nodes.supported());
        f.check("control_center_partition","控制中心分区节点控制","control_center_partition_supported",s.controlCenter.waiting()&&s.controlCenter.nodes.registered,()->s.receiverRegistered&&s.controlCenter.nodes.supported()&&s.controlCenter.partition!=null);
        if(!s.controlCenter.partitionError.isEmpty())f.explain("control_center_partition",s.controlCenter.partitionError);
        if(!s.controlCenter.nodes.error.isEmpty())f.explain("control_center_raw",s.controlCenter.nodes.error);
        if(!s.controlCenter.error.isEmpty())f.explain("control_center_slider",s.controlCenter.error);
        f.check("apps","暂停自动亮度名单","app_auto_supported",true,()->s.receiverRegistered&&ForegroundApps.supported());
        f.check("thermal","温控调整","thermal_supported",true,()->s.receiverRegistered&&s.thermalRegistered&&HookEntry.thermalSupported.contains(s.owner.getClass()));
        f.check("response","变化确认","response_supported",false,()->ResponseTuning.supported(s.owner));
        f.check("small_response","微小变亮确认","small_response_supported",false,()->ResponseTuning.smallSupported(s.owner));
        f.check("low_light","暗光稳定","low_light_supported",false,()->LowLightTuning.supported(s));
        f.check("low_threshold","暗光阈值","low_light_threshold_supported",false,()->AdvancedTuning.supported(0,s));
        if(missingAssist)f.inactive("assist_gate","辅助光感闸门","low_light_assist_gate_supported","当前未接入辅助光感，无法进行双侧对照；主光感暗光稳定继续独立运行，保存的设置保留。");
        else f.check("assist_gate","辅助光感闸门","low_light_assist_gate_supported",false,()->LowLightAssistEvidence.supported(s));
        String[] advanced={"光感变化阈值","辅助光感确认","亮度过渡","阳光模式条件","触摸遮挡"};
        for(int i=0;i<AdvancedOptions.GROUPS.length;i++){final int group=i;
            if(i==1&&missingAssist)f.inactive(AdvancedOptions.GROUPS[i],advanced[i],AdvancedOptions.GROUPS[i]+"_supported","当前未接入辅助光感，辅助确认不参与；主光感的确认时间可独立设置，保存的设置保留。");
            else f.check(AdvancedOptions.GROUPS[i],advanced[i],AdvancedOptions.GROUPS[i]+"_supported",false,()->AdvancedTuning.supported(group,s));}
        for(int i=0;i<SceneOptions.IDS.length;i++){final int group=i;final String id=SceneOptions.IDS[i];String inactive=SceneTuning.inactiveReason(s,group);if(!inactive.isEmpty())f.inactive("scene_"+id,SceneOptions.NAMES[i],"scene_"+id+"_supported",inactive);else f.check("scene_"+id,SceneOptions.NAMES[i],"scene_"+id+"_supported",false,()->SceneTuning.supported(s,group));}
        f.check("outdoor","户外增强","outdoor_supported",true,()->s.receiverRegistered&&outdoor());
        f.check("hbm","HBM 触发与预算","outdoor_hbm_supported",false,()->hbm().tunable());
        if(checkedHbm!=null&&checkedHbm.tunable()&&!checkedHbm.budgetTunable())f.explain("hbm","照度门槛可调；本机未启用 HBM 时间预算，保留系统的零计时配置。增强时长仍由户外会话设置控制。");
        f.check("outdoor_range","高亮范围","outdoor_range_supported",false,()->range());
        f.check("opr","画面限亮放宽","outdoor_opr_supported",false,()->range()&&OutdoorOpr.owners.contains(s.owner.getClass()));
        f.check("nit_limit","户外 nit 上限","outdoor_nit_limit_supported",true,()->OutdoorTuning.nitCaps.contains(s.owner.getClass())&&OutdoorPolicy.valid(new NitBrightnessLimit(s.owner).resolve(0,HookRuntime.optionalNumber(hbm().controller,"mBrightnessMin"),hbm().max())));
        f.check("node_output","节点高亮框架接口","outdoor_node_supported",true,()->OutdoorNodeBridge.interfaces(s,range()));
        try{caps=f.capabilities();try{caps.put("outdoor_output_profile",OutdoorOutputAccess.profile(s));}catch(Exception unavailable){}report=f.report(SystemClock.elapsedRealtime(),request);runs++;report.put("runs",runs);}catch(JSONException impossible){s.log("功能自检结果生成失败");}
    }
}
