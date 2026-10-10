"""Production advice/evidence regression checks on the host, without Android claims."""
from pathlib import Path
import subprocess, os
R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-status-advice-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
test=O/'StatusAdviceTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;
public class StatusAdviceTest {
 static int count;static void check(boolean v){if(!v)throw new AssertionError("case "+count);count++;}
 static JSONObject state()throws Exception{return new JSONObject().put("phase","active").put("elapsed_ms",1000).put("auto_mode",true).put("sensor_status","active").put("last_lux",20000)
 .put("outdoor_supported",true).put("outdoor_range_supported",true).put("outdoor_opr_supported",true).put("thermal_supported",true).put("dark_lock_supported",true).put("low_light_supported",true)
 .put("brightness_control",new JSONObject()).put("outdoor",new JSONObject().put("options",new JSONObject()));}
 static JSONObject outdoor(JSONObject s){return s.optJSONObject("outdoor");}
 static StatusAdvice read(JSONObject s){return StatusAdvice.read(s,1000,1000);}
 static void title(JSONObject s,String expected){check(read(s).title.equals(expected));}
 static void evidence(JSONObject s,String stage,double before,double after,long at)throws Exception{outdoor(s).put("current_limits",new JSONArray().put(new JSONObject().put("stage",stage).put("before",before).put("after",after).put("uptime_ms",at)));}
 static void action(StatusAdvice a,String text,int group,String target){boolean found=false;for(StatusAdvice.Action b:a.actions)if(b.text.equals(text)&&b.group==group&&b.target.equals(target))found=true;check(found);}
 public static void main(String[] args)throws Exception{
  check(read(null).actions.isEmpty());JSONObject s=state();check(read(s).actions.size()==1);action(read(s),"查看高亮条件",2,"");
  s.put("elapsed_ms",-1);title(s,"状态待刷新");check(read(s).actions.isEmpty());s.put("elapsed_ms",1001);title(s,"状态待刷新");s.put("elapsed_ms",1000);check(StatusAdvice.read(s,1000,16001).title.equals("状态待刷新"));check(!StatusAdvice.read(s,1000,16000).title.equals("状态待刷新"));
  s=state().put("phase","disabled");title(s,"引擎未启用");check(read(s).actions.isEmpty());
  s=state().put("auto_mode",false);evidence(s,"adjustBrightnessByThermal",1,.5,1000);title(s,"手动亮度模式");check(read(s).actions.isEmpty());s.optJSONObject("brightness_control").put("owner","dark");title(s,"暗光锁定保持中");
  s=state().put("hdr_active",true);evidence(s,"adjustBrightnessByThermal",1,.5,1000);title(s,"HDR 场景");check(read(s).actions.isEmpty());
  s=state();evidence(s,"adjustBrightnessByThermal",1,.5,1000);title(s,"温控正在限制亮度");action(read(s),"减少温控限亮",9,"thermal");
  s.put("thermal_relax",true);check(read(s).actions.isEmpty());s.put("thermal_relax",false).put("thermal_severity",3);check(read(s).actions.isEmpty());s.put("thermal_severity",0).put("thermal_supported",false);check(read(s).actions.isEmpty());
  for(String stage:new String[]{"adjustBrightnessByPowerSaveMode","adjustBrightnessByBattery"}){s=state();evidence(s,stage,1,.7,1000);check(read(s).actions.isEmpty());check(read(s).title.contains(stage.contains("Battery")?"电池":"省电"));}
  for(String block:new String[]{"power_save","temperature","hbm_budget","hdr","scene","output_override"}){s=state();outdoor(s).put("blocking_condition",block);check(read(s).actions.isEmpty());}
  s=state();outdoor(s).put("mIsBlockedByLowPowerMode",true);title(s,"省电限制生效");
  s=state();outdoor(s).put("controller_managed",true).put("mIsTimeAvailable",false);title(s,"系统高亮时间预算不足");
  for(String sensor:new String[]{"paused","warming","unknown",""}){s=state().put("sensor_status",sensor);title(s,"等待有效照度");check(read(s).actions.isEmpty());}
  s=state().put("last_lux",10);title(s,"暗光环境");check(read(s).detail.contains("感觉亮度不稳定"));action(read(s),"暗光锁定",7,"dark_lock");action(read(s),"暗光稳定",7,"low_light");
  s.optJSONObject("brightness_control").put("dark_lock_enabled",true);check(read(s).actions.size()==1);s.put("low_light_stability",true);check(read(s).actions.isEmpty());check(read(s).detail.contains("已启用"));
  s=state().put("last_lux",10).put("dark_lock_supported",false).put("low_light_supported",false);check(read(s).actions.isEmpty());check(!read(s).detail.contains("试试启用"));
  s=state().put("last_lux",20000).put("official_effective_lux",20);title(s,"暗光环境");s=state().put("last_lux",51);title(s,"自动亮度运行中");
  s=state();evidence(s,"adjustBrightnessByOpr",1,.5,1000);title(s,"画面限亮正在降低亮度");action(read(s),"开放系统映射内的高光范围",2,"range");action(read(s),"强光下放宽画面限亮",2,"opr");
  String unchanged=s.toString();read(s);check(s.toString().equals(unchanged));
  outdoor(s).optJSONObject("options").put("outdoor_range_unlock",true);check(read(s).actions.size()==1);action(read(s),"强光下放宽画面限亮",2,"opr");
  outdoor(s).optJSONObject("options").put("outdoor_opr_relax",true);check(read(s).actions.size()==1);action(read(s),"查看高亮条件",2,"");
  s=state();evidence(s,"adjustBrightnessByOpr",1,.5,1000);s.put("outdoor_opr_supported",false);check(read(s).actions.size()==1);action(read(s),"开放系统映射内的高光范围",2,"range");
  s.put("outdoor_range_supported",false);check(read(s).actions.size()==1);action(read(s),"查看高亮条件",2,"");
  s=state();outdoor(s).put("device_mapping_max",1).put("display_range_max",.7).put("actual_brightness",.7);title(s,"当前高亮范围限制了亮度");action(read(s),"开放系统映射内的高光范围",2,"range");
  outdoor(s).put("actual_brightness",.4);title(s,"强光环境");outdoor(s).put("requested_brightness",.8).put("request_age_ms",5000);title(s,"当前高亮范围限制了亮度");
  outdoor(s).put("request_age_ms",5001);title(s,"强光环境");outdoor(s).remove("request_age_ms");title(s,"强光环境");
  s=state();outdoor(s).put("requested_brightness",.9).put("request_age_ms",0).put("clamper_max",.7);title(s,"系统输出上限生效");check(read(s).actions.isEmpty());
  s=state();evidence(s,"adjustBrightnessByThermal",1,.5,1000);check(!StatusAdvice.read(s,6000,1000).actions.isEmpty());check(!StatusAdvice.read(s,6001,1000).title.contains("温控"));evidence(s,"adjustBrightnessByThermal",1,.5,1001);check(!read(s).title.contains("温控"));
  for(double[] pair:new double[][]{{1,1},{.5,1},{-1,0},{1,1.1}}){evidence(s,"adjustBrightnessByThermal",pair[0],pair[1],1000);check(!read(s).title.contains("温控"));}
  s=state();outdoor(s).put("limit_trace",new JSONArray().put(new JSONObject().put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5).put("uptime_ms",1000)));check(!read(s).title.contains("温控"));
  // Guidance follows the exact graph kind; enabled memory or an archive is not a learned curve.
  s=state().put("last_lux",500).put("curve_backend","refactor").put("factory_lux",new JSONArray("[0,30,600,5000]")).put("factory_logical_nit",new JSONArray("[1,10,100,1000]")).put("active_logical_nit",new JSONArray("[1,12,100,1000]")).put("min_logical_nit",1).put("max_logical_nit",1000).put("current_anchors_lux",new JSONArray("[0,30,600,5000]")).put("current_anchors_nit",new JSONArray("[1,12,100,1000]"));
  for(double strength:new double[]{0,1}){s.put("memory_strength",strength);StatusAdvice a=StatusAdvice.guidance(s,1000);check(a.inlineActions&&a.actions.size()==1);action(a,"基础曲线",0,"curve");check(a.detail.contains("基础曲线 ·"));}
  s.put("memory_saved_anchors",new JSONArray("[{\"lux\":30}]")).put("current_anchors_nit",new JSONArray("[1,14,100,1000]"));action(StatusAdvice.guidance(s,1000),"基础曲线",0,"curve");
  s.put("memory_live_count",1);StatusAdvice learned=StatusAdvice.guidance(s,1000);check(learned.detail.contains("已学习"));check(learned.actions.size()==1);action(learned,"手动记忆",1,"memory");
  s.put("current_anchors_nit",s.optJSONArray("active_logical_nit"));check(!StatusAdvice.guidance(s,1000).detail.contains("已学习"));
  s.put("active_logical_nit",s.optJSONArray("factory_logical_nit")).put("current_anchors_nit",s.optJSONArray("factory_logical_nit"));check(StatusAdvice.guidance(s,1000).detail.contains("系统默认曲线"));
  for(JSONObject snapshot:new JSONObject[]{null,state(),s.put("elapsed_ms",-1)}){check(StatusAdvice.guidance(snapshot,1000).actions.isEmpty());check(StatusAdvice.guidance(snapshot,1000).detail.contains("尚未取得"));}
  s.put("elapsed_ms",1000).put("last_pipeline",new JSONObject().put("route","good_curve"));check(StatusAdvice.guidance(s,1000).actions.isEmpty());
  LiveLimitEvidence live=new LiveLimitEvidence();live.record("adjustBrightnessByThermal",1,.5f,1000);check(live.snapshot(1000).length()==1);check(live.snapshot(6000).length()==1);check(live.snapshot(6001).length()==0);check(live.snapshot(999).length()==0);
  live.record("adjustBrightnessByThermal",1,.5f,6000);check(live.snapshot(6000).getJSONObject(0).getLong("uptime_ms")==6000);live.record("adjustBrightnessByThermal",1,1,6001);check(!live.snapshot(6001).getJSONObject(0).getBoolean("limited"));
  live.record("invalid",1,.5f,6001);live.record("adjustBrightnessByOpr",Float.NaN,0,6001);live.record("adjustBrightnessByOpr",1,-1,6001);live.record("adjustBrightnessByOpr",1,0,-1);check(live.snapshot(6001).length()==1);live.clear();check(live.snapshot(6001).length()==0);
  for(StatusAdvice a:new StatusAdvice[]{read(state()),read(state().put("last_lux",10))}){check(!UiText.translate(a.title,true).equals(a.title));check(!UiText.translate(a.detail,true).equals(a.detail));for(StatusAdvice.Action b:a.actions)check(!UiText.translate(b.text,true).equals(b.text));}
  System.out.println("Status advice: "+count+" cases PASS; production read-only explanations and fresh evidence; Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in ['LiveLimitEvidence','StatusAdvice','UiText','CurveComparison','CurvePlan','TraditionalCurve']],str(test)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.StatusAdviceTest'],check=True)
