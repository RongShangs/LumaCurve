"""Exercise current modes, missing sensor reasons, report sections and production collectors."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-status-presentation-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
hook=(S/'HookRuntime.java').read_text(encoding='utf-8')
def method(signature):
 a=hook.index(signature);b=hook.index('{',a);depth=1;i=b+1
 while depth:
  depth+=(hook[i]=='{')-(hook[i]=='}');i+=1
 return hook[a:i]
collector='\n'.join(method(x) for x in ['    void collectOfficial(', '    void collectScenes(', '    static void readBoolean(', '    static void readNumber(', '    static float optionalNumber(', '    static Boolean optionalBoolean(', '    String sensorName('])
stub=O/'AutomaticBrightnessControllerStub.java';stub.write_text('package com.android.server.display;public class AutomaticBrightnessControllerStub{public static final int USE_MAIN_LIGHT_SENSOR=1,USE_ASSIST_LIGHT_SENSOR=2;}',encoding='utf-8')
test=O/'StatusPresentationTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.lang.reflect.*;
class HookEntry{static Field field(Object o,String name)throws Exception{Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}static Object get(Object o,String name)throws Exception{return field(o,name).get(o);}}
class Collector{Object owner;boolean probingScenes;Collector(Object o){owner=o;}String outputStrategy(Object d){return null;}
'''+collector+r'''}
public class StatusPresentationTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject state()throws Exception{return new JSONObject().put("elapsed_ms",1000).put("phase","active").put("auto_mode",true).put("sensor_status","active").put("main_fast_lux",10).put("assist_fast_lux",0).put("assist_valid",true).put("assist_sampling_enabled",true).put("sensor_reference_name","main").put("memory_strength",1).put("system_scene",new JSONObject().put("night_driving",false));}
 static void sensor(JSONObject s,boolean assist,String text){StatusPresentation.Reading r=StatusPresentation.sensor(s,assist,1000);check(r.text.equals(text));check(!Double.isFinite(r.lux));}
 static BranchReport.Row row(List<BranchReport.Section> sections,String title){for(BranchReport.Section s:sections)for(BranchReport.Row r:s.rows)if(r.title.equals(title))return r;throw new AssertionError(title);}
 static boolean chinese(String s){return s.codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff);}
 static class ABC{boolean mLightSensorEnabled=true,mAmbientLuxValid=true,mIsNightWakeMode;float mAmbientLux=20,mLastObservedLux=21,mAmbientBrighteningThreshold=30,mAmbientDarkeningThreshold=10;long mAmbientLightHorizonLong=10000,mStepModeDarkenDebounceConfig=3000;}
 static class Dual{boolean mAssistAmbientLuxValid=true,mAssistLightSensorEnable=true,mIsPendingResetAssistValue;float mMainFastAmbientLux=10,mAssistFastAmbientLux=0,mAssistBrighteningThreshold=30,mAssistDarkeningThreshold=5;int mUseLightSensorFlag=1;}
 static class Scene{boolean mIsDriving=true,mIsNightDrivingMode=true,mIsReflectiveScene,mIsStepMode,mDrivingSensorRegistered=true,mUseDrivingModeForBrt=true;int mSunriseMinutes=360,mSunsetMinutes=1080;float mMinDrivingEnableNit=3;}
 static class Impl{ABC mAutomaticBrightnessController=new ABC();Dual mDualSensorPolicy=new Dual();Scene mSceneDetector=new Scene();boolean mProximityPositive;}
 static class Owner{Impl mAutomaticBrightnessControllerImpl=new Impl();}
 public static void main(String[] args)throws Exception{
  JSONObject s=state();check(StatusPresentation.sensor(s,true,1000).lux==0);check(StatusPresentation.sensor(s,false,1000).lux==10);
  s.optJSONObject("system_scene").put("night_driving",true);sensor(s,true,"场景停用");check(StatusPresentation.sensor(s,false,1000).lux==10);check(StatusPresentation.modeSummary(s,1000).contains("夜间驾驶（系统判定）"));check(StatusPresentation.modeDetail(s,1000).contains("不代表你正在开车"));
  s.put("sensor_reference_name","assist");check(StatusPresentation.sensor(s,true,1000).lux==0);s.put("sensor_reference_name","main").put("assist_sampling_enabled",false);sensor(s,true,"采样暂停");
  s.put("assist_sampling_enabled",true);s.optJSONObject("system_scene").put("assist_reset_pending",true);sensor(s,true,"重置等待");
  s=state();s.remove("assist_fast_lux");sensor(s,true,"未取得有效读数");s.put("assist_reading_state","reset");sensor(s,true,"读数已重置");s.put("assist_valid",false);sensor(s,true,"等待采样");
  for(String state:new String[]{"paused","warming","unknown"}){s=state().put("sensor_status",state);sensor(s,true,state.equals("paused")?"采样暂停":state.equals("warming")?"等待采样":"未取得");}
  sensor(null,true,"等待读取");for(long at:new long[]{-1,1001,-20000})sensor(state().put("elapsed_ms",at),true,"状态待刷新");
  s=state().put("auto_mode",false);sensor(s,false,"采样暂停");s.put("brightness_control",new JSONObject().put("owner","dark").put("listening",true).put("watch_assist_lux",0));check(StatusPresentation.sensor(s,true,1000).lux==0);sensor(s,false,"等待监听");check(StatusPresentation.modeSummary(s,1000).contains("暗光锁定"));
  s=state().put("hdr_active",true);s.optJSONObject("system_scene").put("night_driving",true).put("reflective",true);check(StatusPresentation.modes(s,1000).size()==3);check(StatusPresentation.modeSummary(s,1000).contains("HDR"));
  // A newer scene snapshot updates the UI independently of unchanged primary limit text.
  check(!StatusPresentation.modeSummary(state(),1000).equals(StatusPresentation.modeSummary(s,1000)));
  s=state();s.remove("system_scene");check(StatusPresentation.modeSummary(s,1000).contains("场景未取得"));
  Owner owner=new Owner();Collector c=new Collector(owner);JSONObject collected=new JSONObject().put("auto_mode",true);c.collectOfficial(collected);
  check(collected.getString("sensor_status").equals("active"));check(collected.getDouble("assist_fast_lux")==0);check(collected.getBoolean("assist_sampling_enabled"));check(collected.getBoolean("assist_ambient_lux_valid"));check(collected.getString("assist_reading_state").equals("valid"));check(collected.getString("sensor_reference_name").equals("main"));
  JSONObject scene=collected.getJSONObject("system_scene");check(scene.getBoolean("night_driving")&&scene.getBoolean("driving")&&scene.getBoolean("night_driving_allowed"));check(scene.getInt("sunrise_minutes")==360&&scene.getInt("sunset_minutes")==1080);
  owner.mAutomaticBrightnessControllerImpl.mDualSensorPolicy.mAssistFastAmbientLux=-1;collected=new JSONObject().put("auto_mode",true);c.collectOfficial(collected);check(!collected.has("assist_fast_lux"));check(collected.getString("assist_reading_state").equals("reset"));
  owner.mAutomaticBrightnessControllerImpl.mDualSensorPolicy.mAssistAmbientLuxValid=false;collected=new JSONObject().put("auto_mode",true);c.collectOfficial(collected);check(!collected.getBoolean("assist_valid")&&!collected.has("assist_fast_lux"));check(!c.probingScenes);
  owner.mAutomaticBrightnessControllerImpl.mSceneDetector.mSunriseMinutes=-1;collected=new JSONObject().put("auto_mode",true);c.collectOfficial(collected);check(!collected.getJSONObject("system_scene").has("sunrise_minutes"));
  for(JSONObject snapshot:new JSONObject[]{null,state(),state().put("elapsed_ms",-1),state().put("elapsed_ms",1001),state().put("auto_mode",false),state().put("hdr_active",true),state().put("sensor_status","paused"),state().put("system_scene",scene),state().put("outdoor",new JSONObject().put("blocking_condition","temperature"))}){
   String before=snapshot==null?"":snapshot.toString();List<BranchReport.Section> sections=BranchReport.read(snapshot,1000,1000);check(sections.size()==7);Set<String> titles=new HashSet<>();
   for(BranchReport.Section part:sections){check(titles.add(part.title));check(!part.rows.isEmpty());check(!chinese(UiText.translate(part.title,true)));for(BranchReport.Row item:part.rows){check(!item.title.isEmpty()&&!item.value.isEmpty());String tr=UiText.translate(item.title+"\n"+item.value+"\n"+item.detail,true);if(chinese(tr))throw new AssertionError(tr);check(!chinese(tr));}}
   check(snapshot==null||snapshot.toString().equals(before));
  }
  s=state();JSONObject frame=new JSONObject().put("uptime_ms",0).put("route","mapping").put("scene_changed",true).put("short_term_memory",false);s.put("last_pipeline",frame).put("outdoor",new JSONObject().put("limit_trace",new JSONArray().put(new JSONObject().put("uptime_ms",0).put("limited",true).put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5))));
  List<BranchReport.Section> sections=BranchReport.read(s,1000,1000);check(row(sections,"曲线路径").value.equals("标准映射曲线"));check(row(sections,"场景修正").value.equals("已改写目标"));check(row(sections,"当前主要状态").value.equals("自动亮度运行中"));check(sections.get(6).rows.get(0).title.equals("显示温控"));
  frame.put("uptime_ms",1001);check(row(BranchReport.read(s,1000,1000),"曲线路径").value.equals("未取得"));
  check(BranchReport.flag(new JSONObject().put("active","false"),"active","yes","no").equals("未取得"));check(BranchReport.percent(new JSONObject().put("x",1.1),"x").equals("未取得"));check(BranchReport.clock(new JSONObject().put("x",1440),"x").equals("未取得"));
  System.out.println("Status presentation: "+cases+" cases PASS; production collectors, mode/sensor explanations and report sections with doubles; Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in ['SensorState','StatusPresentation','BranchReport','StatusAdvice','LiveLimitEvidence','UiText','CurveComparison','CurvePlan','TraditionalCurve']],str(stub),str(test)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.StatusPresentationTest'],check=True)
