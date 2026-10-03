"""Cross-version configuration migration, never submits commands or touches Android."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-config-tests';O.mkdir(parents=True,exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
package=O/'top/rongshangs/lumacurve/refactor';package.mkdir(parents=True,exist_ok=True)
(package/'RootControl.java').write_text('''package top.rongshangs.lumacurve.refactor;import org.json.*;class RootControl{static float[] numbers(JSONArray a)throws JSONException{float[] f=new float[a.length()];for(int i=0;i<f.length;i++)f[i]=(float)a.getDouble(i);return f;}}''',encoding='utf-8')
(package/'ConfigHostTest.java').write_text('''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
public class ConfigHostTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject clone(JSONObject o)throws Exception{return new JSONObject(o.toString());}
 static void bad(JSONObject o,JSONObject s,JSONObject c){try{ConfigurationFile.read(o.toString(),s,c);throw new AssertionError("accepted malformed configuration");}catch(IllegalArgumentException|JSONException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  JSONObject s=new JSONObject().put("baseline_id","basis").put("fingerprint","device").put("curve_backend","physical_mapping").put("factory_lux",new JSONArray(new float[]{0,30,600,100000})).put("factory_logical_nit",new JSONArray(new float[]{0,20,100,1000})).put("min_logical_nit",0).put("max_logical_nit",1000);
  for(String key:new String[]{"thermal_supported","response_supported","small_response_supported","low_light_supported","outdoor_supported","outdoor_hbm_supported","outdoor_range_supported"})s.put(key,true);for(String key:AdvancedOptions.GROUPS)s.put(key+"_supported",true);
  JSONObject c=new JSONObject().put("factors","1,1,1,1").put("curve_floor_nit",10),file=ConfigurationFile.export(c,s,true,3,true);
  ConfigurationFile.Imported r=ConfigurationFile.read(file.toString(),s,c);check(r.options.getDouble("curve_floor_nit")==10);check(r.options.getDouble("memory_strength")==1);check(!r.options.getBoolean("outdoor_enabled"));check(r.ui.getInt("refresh_seconds")==3);check(r.ui.getBoolean("log_auto_scroll"));check(file.getBoolean("unsaved_changes"));check(!file.has("fingerprint")&&!file.has("user_serial"));check(r.notes.isEmpty());
  JSONObject old=new JSONObject().put("schema",1).put("enabled",true).put("factors","1,1,1,1").put("baseline_id","basis").put("curve_backend","physical_mapping").put("fingerprint","device").put("revision","old").put("user_serial",999).put("reset_anchors","do-not-replay");
  r=ConfigurationFile.read(old.toString(),s,c);check(r.options.getDouble("curve_floor_nit")==0);check(!r.options.has("revision")&&!r.options.has("user_serial")&&!r.options.has("reset_anchors"));check(r.notes.isEmpty());
  JSONObject first=clone(file);first.put("format","hyperlux_config_v1");first.remove("schema");check(ConfigurationFile.read(first.toString(),s,c).options.getDouble("curve_floor_nit")==10);
  JSONObject changed=clone(file);changed.put("baseline_id","other");changed.getJSONObject("options").put("factors","1,1.5,1,1").put("curve_floor_nit",15).put("thermal_ceiling",40);r=ConfigurationFile.read(changed.toString(),s,c);check(r.options.getString("factors").equals("1.0,1.0,1.0,1.0"));check(r.options.getDouble("curve_floor_nit")==10);check(r.options.getInt("thermal_ceiling")==40);check(r.notes.size()==1);
  JSONObject unsupported=clone(file);JSONObject opts=unsupported.getJSONObject("options");for(String key:ConfigurationFile.FLAGS)opts.put(key,true);for(String key:AdvancedOptions.GROUPS)opts.put(key,true);for(String key:OutdoorOptions.FLAGS)opts.put(key,true);
  JSONObject noCaps=clone(s);for(String key:new String[]{"thermal_supported","response_supported","small_response_supported","low_light_supported","outdoor_supported","outdoor_hbm_supported","outdoor_range_supported"})noCaps.put(key,false);for(String key:AdvancedOptions.GROUPS)noCaps.put(key+"_supported",false);
  r=ConfigurationFile.read(unsupported.toString(),noCaps,c);for(String key:ConfigurationFile.FLAGS)check(!r.options.getBoolean(key));for(String key:AdvancedOptions.GROUPS)check(!r.options.getBoolean(key));for(String key:OutdoorOptions.FLAGS)check(!r.options.getBoolean(key));check(r.notes.size()==12);
  JSONObject wider=clone(file);wider.getJSONObject("options").put("thermal_ceiling",99).put("assist_darken",90000).put("outdoor_enter_lux",50000).put("outdoor_full_lux",4000).put("new_unknown",true);r=ConfigurationFile.read(wider.toString(),s,c);check(r.options.getInt("thermal_ceiling")==45);check(r.options.getInt("assist_darken")==4000);check(r.options.getInt("outdoor_full_lux")==52000);check(!r.options.has("new_unknown"));check(r.notes.size()==4);
  JSONObject bad=clone(file);bad.put("schema",3);bad(bad,s,c);bad=clone(file);bad.put("format","hyperlux_config_v3");bad(bad,s,c);
  for(String key:new String[]{"thermal_ceiling","memory_strength","assist_brighten","outdoor_enter_lux","curve_floor_nit"}){bad=clone(file);bad.getJSONObject("options").put(key,"12");bad(bad,s,c);}
  bad=clone(file);bad.getJSONObject("options").put("thermal_relax",1);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put("memory_window",1000.5);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put("curve_floor_nit",21);bad(bad,s,c);
  bad=clone(file);bad.getJSONObject("interface").put("refresh_seconds",1.5);bad(bad,s,c);bad=clone(file);bad.put("oversized",String.join("",Collections.nCopies(32769,"x")));bad(bad,s,c);
  check(c.getDouble("curve_floor_nit")==10&&s.getString("baseline_id").equals("basis"));
  System.out.println("Configuration migration: "+cases+" cases PASS; cross-version settings and curve identity, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
names=['ConfigurationFile','CurvePlan','AdvancedOptions','AdvancedPolicy','OutdoorOptions','ThermalPolicy','MemoryPolicy','DelayPolicy','LowLightPolicy','AppBuild']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in names],str(package/'RootControl.java'),str(package/'ConfigHostTest.java')],check=True)
subprocess.run(['java','-cp',str(classes)+';'+str(J),'top.rongshangs.lumacurve.refactor.ConfigHostTest'],check=True)
