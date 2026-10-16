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
    Object abc()throws Exception{return HookEntry.get(HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),"mAutomaticBrightnessController");}
    boolean sensor(boolean assist)throws Exception{Object abc=abc();Object value=assist?HookEntry.get(HookEntry.get(HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),"mDualSensorPolicy"),"mAssistLightSensor"):HookEntry.get(abc,"mLightSensor");if(value==null)return false;Sensor sensor=(Sensor)value;return assist?(sensor.getType()==Sensor.TYPE_LIGHT||sensor.getType()==33171055):sensor.getType()==Sensor.TYPE_LIGHT;}
    boolean darkSensors()throws Exception{
        if(!sensor(false)||HookEntry.get(abc(),"mSensorManager")==null)return false;
        Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),dual;
        try{dual=HookEntry.get(impl,"mDualSensorPolicy");}catch(NoSuchFieldException optional){return true;}
        if(dual==null)return true;
        for(String key:new String[]{"mFovAmplifyFactor","mAssistFovAmplifyFactor"}){double scale=((Number)HookEntry.get(dual,key)).doubleValue();if(!Double.isFinite(scale)||scale<=0||scale>100)return false;}
        Object assist=HookEntry.get(dual,"mAssistLightSensor");return assist==null||sensor(true);
    }
    boolean pending(){JSONArray entries=report.optJSONArray("features");if(entries!=null)for(int i=0;i<entries.length();i++)if("pending".equals(entries.optJSONObject(i).optString("state")))return true;return false;}
    void run(String request){
        checkedHbm=null;
        FeatureSelfCheck f=new FeatureSelfCheck();
        f.check("curve","基础曲线",null,false,()->{new CurvePlan(s.kernel.factoryLux,s.kernel.factoryNit,s.kernel.min,s.kernel.max,new float[]{1,1,1,1});return true;});
        f.check("main_sensor","主光感",null,true,()->sensor(false));f.check("assist_sensor","辅助光感",null,true,()->sensor(true));
        f.check("manual_memory","手动记忆",null,false,()->s.kernel.manualPointCapacity()>0);
        f.check("saved_curve","记忆转基础曲线",null,false,()->s.kernel.name().equals("refactor")||s.kernel.name().equals("physical_mapping"));
        f.check("persistent_memory","记忆持久化","persistent_memory_supported",false,()->s.kernel.persistentMemorySupported());
        f.check("memory_reset","记忆重置条件","memory_reset_supported",false,()->s.memoryLifecycle.resetSupported);
        f.check("memory_timeout","记忆超时","memory_timeout_supported",false,()->s.memoryLifecycle.timeoutSupported);
        f.check("dark_lock","暗光锁定","dark_lock_supported",true,()->s.receiverRegistered&&s.brightnessControl.available()&&darkSensors());
        f.check("manual_panel","主屏手动亮度","manual_panel_supported",true,()->s.receiverRegistered&&RawPanelOutputHooks.supports(s.owner.getClass()));
        f.check("apps","应用自动亮度名单","app_auto_supported",true,()->s.receiverRegistered&&ForegroundApps.install(s.owner.getClass().getClassLoader()));
        f.check("thermal","温控调整","thermal_supported",true,()->s.receiverRegistered&&s.thermalRegistered&&HookEntry.thermalSupported.contains(s.owner.getClass()));
        f.check("response","变化确认","response_supported",false,()->ResponseTuning.supported(s.owner));
        f.check("small_response","微小变亮确认","small_response_supported",false,()->ResponseTuning.smallSupported(s.owner));
        f.check("low_light","暗光稳定","low_light_supported",false,()->LowLightTuning.supported(s));
        f.check("low_threshold","暗光阈值","low_light_threshold_supported",false,()->AdvancedTuning.supported(0,s));
        f.check("assist_gate","辅助光感闸门","low_light_assist_gate_supported",false,()->LowLightAssistEvidence.supported(s));
        String[] advanced={"光感变化阈值","辅助光感确认","亮度过渡","阳光模式条件","触摸遮挡"};
        for(int i=0;i<AdvancedOptions.GROUPS.length;i++){final int group=i;f.check(AdvancedOptions.GROUPS[i],advanced[i],AdvancedOptions.GROUPS[i]+"_supported",false,()->AdvancedTuning.supported(group,s));}
        for(int i=0;i<SceneOptions.IDS.length;i++){final int group=i;final String id=SceneOptions.IDS[i];f.check("scene_"+id,SceneOptions.NAMES[i],"scene_"+id+"_supported",false,()->SceneTuning.supported(s,group));}
        f.check("outdoor","户外增强","outdoor_supported",true,()->s.receiverRegistered&&outdoor());
        f.check("hbm","HBM 触发与预算","outdoor_hbm_supported",false,()->hbm().tunable());
        f.check("outdoor_range","高亮范围","outdoor_range_supported",false,()->range());
        f.check("opr","画面限亮放宽","outdoor_opr_supported",false,()->range()&&OutdoorOpr.owners.contains(s.owner.getClass()));
        f.check("nit_limit","户外 nit 上限","outdoor_nit_limit_supported",true,()->OutdoorTuning.nitCaps.contains(s.owner.getClass())&&OutdoorPolicy.valid(new NitBrightnessLimit(s.owner).resolve(0,HookRuntime.optionalNumber(hbm().controller,"mBrightnessMin"),hbm().max())));
        f.check("node_output","节点高亮框架接口","outdoor_node_supported",true,()->OutdoorNodeBridge.interfaces(s,range()));
        try{caps=f.capabilities();report=f.report(SystemClock.elapsedRealtime(),request);runs++;report.put("runs",runs);}catch(JSONException impossible){s.log("功能自检结果生成失败");}
    }
}
