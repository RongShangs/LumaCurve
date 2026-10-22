"""Resolve the production route catalogue without changing any supplied runtime/configuration."""
from pathlib import Path
import subprocess, os

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/feature-compatibility-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'FeatureCompatibilityTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
public class FeatureCompatibilityTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject row(String id,String state){return new JSONObject().put("id",id).put("name",id).put("state",state).put("detail","probe detail");}
 static JSONObject enrich(String id,String state,JSONObject r){return FeatureCompatibility.annotate(new JSONObject().put("features",new JSONArray().put(row(id,state))),r,"engine").getJSONArray("features").getJSONObject(0);}
 static Set<String> selected(JSONObject row){Set<String> ids=new HashSet<>();JSONArray routes=row.getJSONArray("routes");for(int i=0;i<routes.length();i++){JSONObject route=routes.getJSONObject(i);check(!route.getString("condition").isEmpty());check(!route.getString("reason").isEmpty());if(route.getBoolean("selected")){check(route.getString("status").equals("selected"));ids.add(route.getString("id"));}}return ids;}
 static void picked(String id,JSONObject r,String expected){Set<String> actual=selected(enrich(id,"available",r));check(actual.equals(new HashSet<>(Arrays.asList(expected))));}
 public static void main(String[] args)throws Exception{
  JSONObject r=new JSONObject().put("curve_backend","refactor");
  String[] memory={"curve","manual_memory","saved_curve","persistent_memory","memory_mode","memory_forever","memory_reset","memory_timeout"};
  for(String id:memory){picked(id,r,"refactor");JSONObject row=enrich(id,"available",r);check(row.getJSONArray("routes").length()==4);}
  r.put("curve_route","refactor_coefficients");picked("curve",r,"refactor_coefficients");check(selected(enrich("persistent_memory","unsupported",r)).isEmpty());r.put("curve_route","unknown");check(selected(enrich("curve","available",r)).isEmpty());r.remove("curve_route");
  r.put("curve_backend","physical_mapping").put("physical_mapper_class","com.android.server.display.MiuiPhysicalBrightnessMappingStrategy");for(String id:memory)picked(id,r,"miui_physical");
  r.put("physical_mapper_class","com.android.server.display.BrightnessMappingStrategy$PhysicalMappingStrategy");for(String id:memory)picked(id,r,"aosp_physical");
  r.remove("physical_mapper_class");check(selected(enrich("curve","available",r)).isEmpty());check(enrich("curve","available",r).getString("route_name").contains("未确认"));r.put("curve_backend","unknown");check(selected(enrich("curve","available",r)).isEmpty());
  check(selected(enrich("main_sensor","available",r)).isEmpty());
  r.put("main_input_compatibility",new JSONObject().put("profile","oem_selected_secondary").put("source","automatic_brightness_controller").put("sensor_name","selected").put("sensor_string_type","xiaomi.sensor.light_secondary"));picked("main_sensor",r,"oem_selected_secondary");
  check(enrich("main_sensor","available",r).getString("evidence").contains("xiaomi.sensor.light_secondary"));
  r.getJSONObject("main_input_compatibility").put("profile","oem_selected_fov");picked("main_sensor",r,"oem_selected_fov");
  JSONObject auxiliary=new JSONObject().put("state","native").put("sensor_string_type","zte.sensor.light_second");r.put("assist_compatibility",auxiliary);picked("assist_sensor",r,"native_auxiliary");
  auxiliary.put("state","bound");picked("assist_sensor",r,"ported_auxiliary");auxiliary.put("binding_kind","native_auxiliary");picked("assist_sensor",r,"native_auxiliary");
  auxiliary.put("state","inactive").put("binding_kind","ported_auxiliary");picked("assist_sensor",r,"ported_auxiliary");
  auxiliary.remove("binding_kind");auxiliary.put("state","no_verified_candidate");check(selected(enrich("assist_sensor","available",r)).isEmpty());
  for(String state:new String[]{"pending","unsupported","inactive"}){auxiliary.put("state","bound");check(selected(enrich("assist_sensor",state,r)).isEmpty());}
  for(String animation:new String[]{"oem_animation","traditional_ramp"}){r.put("animation_compatibility_profile",animation);picked("animation_override",r,animation);}r.remove("animation_compatibility_profile");check(selected(enrich("animation_override","available",r)).isEmpty());
  JSONObject outdoor=new JSONObject();r.put("outdoor",outdoor);
  for(String profile:new String[]{"timed_budget","ambient_only"}){outdoor.put("hbm_tuning_profile",profile);picked("hbm",r,profile);}
  outdoor.put("hbm_tuning_profile","unsupported");check(selected(enrich("hbm","available",r)).isEmpty());
  for(String profile:new String[]{"privacy_aware","legacy_opr"}){outdoor.put("sdr_compatibility_profile",profile);picked("opr",r,profile);picked("nit_limit",r,profile);}
  outdoor.remove("sdr_compatibility_profile");check(selected(enrich("opr","available",r)).isEmpty());
  JSONObject control=new JSONObject();r.put("brightness_control",control);check(selected(enrich("apps","available",r)).isEmpty());control.put("app_multiwindow_supported",true);picked("apps",r,"visible_tasks");control.put("app_multiwindow_supported",false);picked("apps",r,"focus_fallback");
  outdoor.put("dynamic_range_supported",true).put("dynamic_range_enabled",false);picked("outdoor_range",r,"hbm_range");outdoor.put("dynamic_range_enabled",true);check(selected(enrich("outdoor_range","available",r)).equals(new HashSet<>(Arrays.asList("hbm_range","dynamic_range"))));
  for(String sensor:new String[]{"proximity","stream_proximity","night_driving","night_wake","step","touch","reflection","game","manual_sunlight"}){picked("scene_"+sensor,r,"oem_"+sensor);check(selected(enrich("scene_"+sensor,"inactive",r)).isEmpty());}
  outdoor.remove("hbm_controller_profile");outdoor.put("hbm_tuning_profile","ambient_only");JSONObject partial=enrich("hbm","available",r);
  for(int i=0;i<partial.getJSONArray("routes").length();i++){JSONObject route=partial.getJSONArray("routes").getJSONObject(i);if(route.getString("id").equals("enabled_field")||route.getString("id").equals("native_support_method"))check(route.getString("status").equals("unconfirmed"));}
  outdoor.remove("dynamic_range_enabled");JSONObject dynamic=enrich("outdoor_range","available",r);for(int i=0;i<dynamic.getJSONArray("routes").length();i++){JSONObject route=dynamic.getJSONArray("routes").getJSONObject(i);if(route.getString("id").equals("dynamic_range"))check(route.getString("status").equals("unconfirmed"));}outdoor.put("dynamic_range_enabled",true);
  JSONObject disabled=enrich("scene_proximity","inactive",r);check(disabled.getString("route_name").contains("系统未启用"));check(disabled.getJSONArray("routes").getJSONObject(0).getString("condition").contains("不混用"));
  for(String id:new String[]{"scene_unknown","unrecognized_feature"})check(selected(enrich(id,"available",r)).isEmpty());
  r.put("response_scene_profile","native_driving");outdoor.put("target_compatibility_profile","override_target");
  r.put("main_input_compatibility",new JSONObject().put("profile","oem_main"));
  r.put("animation_compatibility_profile","oem_animation");
  String[] simple={"main_sensor","dark_lock","manual_panel","node_output","thermal","response","small_response","low_light","low_threshold","assist_gate","threshold_override","assist_override","animation_override","sunlight_override","touch_override","outdoor","root_storage","stored_config","outdoor_node_device","native_payload","launcher","preferences","app_files"};
  for(String id:simple){JSONObject row=enrich(id,"available",r);check(selected(row).size()==1);for(String state:new String[]{"pending","unsupported","inactive"})check(selected(enrich(id,state,r)).isEmpty());}
  r.put("response_scene_profile","native_without_driving");for(String id:new String[]{"response","small_response","low_light","low_threshold","threshold_override","assist_gate","assist_override"})picked(id,r,"native_without_driving");
  outdoor.put("target_compatibility_profile","legacy_custom_target");picked("outdoor",r,"legacy_custom_target");
  outdoor.put("hbm_controller_profile","native_support_method").put("hbm_tuning_profile","ambient_only");check(selected(enrich("hbm","available",r)).equals(new HashSet<>(Arrays.asList("native_support_method","ambient_only"))));
  outdoor.put("hbm_controller_profile","enabled_field").put("hbm_tuning_profile","timed_budget");check(selected(enrich("hbm","available",r)).equals(new HashSet<>(Arrays.asList("enabled_field","timed_budget"))));
    JSONObject node=row("main_panel_node","available").put("node_class","leds").put("node_path","/sys/devices/display/brightness").put("node_maximum",4095);
  JSONObject report=new JSONObject().put("checked_elapsed_ms",99).put("features",new JSONArray().put(node));JSONObject result=FeatureCompatibility.annotate(report,r,"root");check(selected(result.getJSONArray("features").getJSONObject(0)).contains("display_led_node"));check(result.getJSONArray("features").getJSONObject(0).getString("evidence").contains("4095"));
  node.put("node_class","backlight");check(selected(FeatureCompatibility.annotate(report,r,"root").getJSONArray("features").getJSONObject(0)).contains("backlight_node"));
  node.put("node_discovery","fold_default_display").put("node_binding","exclusive_internal_dsi").put("node_physical_port",132).put("node_kernel_port",1);
  JSONObject fold=FeatureCompatibility.annotate(report,r,"root").getJSONArray("features").getJSONObject(0);check(selected(fold).contains("fold_default_display"));check(fold.getString("evidence").contains("唯一活动 DSI"));check(fold.getString("evidence").contains("132 / 背光节点 1"));
  r.put("outdoor_output_profile","automatic_strategy2_reason");picked("node_output",r,"automatic_strategy2_node");check(enrich("node_output","available",r).getJSONArray("routes").length()==2);r.put("outdoor_output_profile","selected_strategy");picked("node_output",r,"raw_primary_output");
  r.put("outdoor_node_device_profile","fold_bound_node");picked("outdoor_node_device",r,"fold_bound_outdoor");check(enrich("outdoor_node_device","available",r).getJSONArray("routes").length()==2);r.put("outdoor_node_device_profile","primary_node");picked("outdoor_node_device",r,"primary_node_profile");
  JSONObject app=new JSONObject().put("checked_elapsed_ms",101).put("features",new JSONArray().put(row("preferences","available")));
  r.put("feature_self_check",new JSONObject().put("checked_elapsed_ms",100).put("checked_unix_ms",1792288800000L).put("request","engine-start").put("features",new JSONArray().put(row("hbm","available")).put(row("scene_proximity","inactive"))));
  String originalRuntime=r.toString(),originalRoot=report.toString(),originalApp=app.toString();JSONArray combined=FeatureCompatibility.combine(r,report,app);check(combined.length()==4);check(combined.getJSONObject(0).getLong("checked_unix_ms")==1792288800000L);check(combined.getJSONObject(0).getString("check_request").equals("engine-start"));check(combined.getJSONObject(0).getLong("checked_elapsed_ms")==100);check(combined.getJSONObject(2).getString("source").equals("root"));check(combined.getJSONObject(3).getLong("checked_elapsed_ms")==101);
  check(originalRuntime.equals(r.toString())&&originalRoot.equals(report.toString())&&originalApp.equals(app.toString()));
  combined.getJSONObject(0).put("state","mutated");check(originalRuntime.equals(r.toString()));
  check(FeatureCompatibility.combine(null,null,null).length()==0);
  System.out.println("Feature compatibility catalogue: "+cases+" cases PASS; actual presentation model, no device or brightness probes");
 }
}''',encoding='utf-8')
# All catalogue/page phrases also remain readable in the existing English UI.
import re,ast,json
phrases=[]
for name in ['CompatibilityCatalog.java','FeatureCompatibility.java','FeatureCheckPage.java']:
 for literal in re.findall(r'"(?:[^"\\]|\\.)*"',(S/name).read_text(encoding='utf-8')):
  value=ast.literal_eval(literal)
  if re.search('[\u4e00-\u9fff]',value) and value not in phrases:phrases.append(value)
java=p.read_text(encoding='utf-8')
array=','.join(json.dumps(value,ensure_ascii=False) for value in phrases)
java=java.replace('  System.out.println("Feature compatibility catalogue:', '  for(String text:new String[]{'+array+'})check(UiText.translate(text,true).codePoints().noneMatch(n->n>=0x4e00&&n<=0x9fff));\n  System.out.println("Feature compatibility catalogue:')
p.write_text(java,encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(S/'CompatibilityCatalog.java'),str(S/'FeatureCompatibility.java'),str(S/'UiText.java'),str(p)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.FeatureCompatibilityTest'],check=True)
