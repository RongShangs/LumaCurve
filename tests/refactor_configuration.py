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
  r=ConfigurationFile.read(unsupported.toString(),noCaps,c);for(String key:ConfigurationFile.FLAGS)check(!r.options.getBoolean(key));for(String key:AdvancedOptions.GROUPS)check(!r.options.getBoolean(key));for(String key:OutdoorOptions.FLAGS)check(!r.options.getBoolean(key));check(r.notes.size()==13);
  JSONObject wider=clone(file);wider.getJSONObject("options").put("thermal_ceiling",99).put("assist_darken",90000).put("outdoor_enter_lux",50000).put("outdoor_full_lux",4000).put("new_unknown",true);r=ConfigurationFile.read(wider.toString(),s,c);check(r.options.getInt("thermal_ceiling")==50);check(r.options.getInt("assist_darken")==4000);check(r.options.getInt("outdoor_full_lux")==52000);check(!r.options.has("new_unknown"));check(r.notes.size()==4);
  JSONObject upperThermal=clone(file);upperThermal.getJSONObject("options").put("thermal_ceiling",50);r=ConfigurationFile.read(upperThermal.toString(),s,c);check(r.options.getInt("thermal_ceiling")==50&&r.notes.isEmpty());
  upperThermal.getJSONObject("options").put("thermal_ceiling",45);r=ConfigurationFile.read(upperThermal.toString(),s,c);check(r.options.getInt("thermal_ceiling")==45&&r.notes.isEmpty());
  JSONObject bad=clone(file);bad.put("schema",3);bad(bad,s,c);bad=clone(file);bad.put("format","hyperlux_config_v3");bad(bad,s,c);
  for(String key:new String[]{"thermal_ceiling","memory_strength","assist_brighten","outdoor_enter_lux","curve_floor_nit"}){bad=clone(file);bad.getJSONObject("options").put(key,"12");bad(bad,s,c);}
  bad=clone(file);bad.getJSONObject("options").put("thermal_relax",1);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put("memory_window",1000.5);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put("curve_floor_nit",21);bad(bad,s,c);
  bad=clone(file);bad.getJSONObject("interface").put("refresh_seconds",1.5);bad(bad,s,c);bad=clone(file);bad.put("oversized",String.join("",Collections.nCopies(32769,"x")));bad(bad,s,c);
  check(c.getDouble("curve_floor_nit")==10&&s.getString("baseline_id").equals("basis"));
  check(r.options.getBoolean("memory_persist"));check(r.options.getInt("memory_retention_days")==30);check(r.options.getInt("memory_max_points")==0);
  JSONObject tuned=clone(file);tuned.getJSONObject("options").put("memory_persist",false).put("memory_retention_days",90).put("memory_max_points",7);
  JSONObject traditional=clone(s).put("memory_point_capacity",1);r=ConfigurationFile.read(tuned.toString(),traditional,c);check(!r.options.getBoolean("memory_persist"));check(r.options.getInt("memory_retention_days")==90);check(r.options.getInt("memory_max_points")==1);check(r.notes.size()==1);
  for(String key:new String[]{"memory_retention_days","memory_max_points"}){bad=clone(file);bad.getJSONObject("options").put(key,1.5);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put(key,"1");bad(bad,s,c);}
  bad=clone(file);bad.getJSONObject("options").put("memory_persist",1);bad(bad,s,c);
  JSONObject wake=clone(file);wake.getJSONObject("options").put("memory_restore_unlock",false).put("memory_restore_settle",2500).put("memory_restore_ratio",.8).put("memory_reset_override",true).put("memory_timeout_override",true).put("memory_timeout_minutes",60);
  r=ConfigurationFile.read(wake.toString(),s,c);check(!r.options.getBoolean("memory_reset_override")&&!r.options.getBoolean("memory_timeout_override"));check(!r.options.getBoolean("memory_restore_unlock"));check(r.options.getInt("memory_restore_settle")==2500);check(Math.abs(r.options.getDouble("memory_restore_ratio")-.8)<.0001);check(r.notes.size()==2);
  JSONObject caps=clone(s).put("memory_reset_supported",true).put("memory_timeout_supported",true);r=ConfigurationFile.read(wake.toString(),caps,c);check(r.options.getBoolean("memory_reset_override")&&r.options.getBoolean("memory_timeout_override"));check(r.options.getInt("memory_timeout_minutes")==60);check(r.notes.isEmpty());
  bad=clone(file);bad.getJSONObject("options").put("memory_reset_off_minutes",20).put("memory_reset_force_minutes",10);bad(bad,s,c);
  bad=clone(file);bad.getJSONObject("options").put("memory_restore_settle",0);bad(bad,s,c);
  bad=clone(file);bad.getJSONObject("options").put("memory_restore_same_scene","true");bad(bad,s,c);
  JSONObject olderStable=clone(file);olderStable.getJSONObject("options").put("low_light_stability",true);ConfigurationFile.Imported older=ConfigurationFile.read(olderStable.toString(),s,c);check(older.options.getBoolean("low_light_stability"));check(!older.options.getBoolean("low_light_threshold"));
  JSONObject stable=clone(file);stable.getJSONObject("options").put("low_light_stability",true).put("low_light_threshold",true).put("low_light_brighten_ratio",1.2).put("low_light_brighten_floor",15);
  r=ConfigurationFile.read(stable.toString(),s,c);check(!r.options.getBoolean("low_light_threshold"));check(r.notes.size()==1);
  JSONObject lowCaps=clone(s).put("low_light_threshold_supported",true);r=ConfigurationFile.read(stable.toString(),lowCaps,c);check(r.options.getBoolean("low_light_threshold"));check(r.options.getDouble("low_light_brighten_floor")==15);check(r.options.getDouble("low_light_brighten_ratio")==1.2);check(r.notes.isEmpty());
  bad=clone(file);bad.getJSONObject("options").put("low_light_darken_ratio",1);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put("low_light_threshold",1);bad(bad,s,c);
  stable.getJSONObject("options").put("low_light_assist_gate",true).put("low_light_assist_wait",8000).put("low_light_assist_tolerance",.3);r=ConfigurationFile.read(stable.toString(),lowCaps,c);check(!r.options.getBoolean("low_light_assist_gate"));check(r.notes.size()==1);lowCaps.put("low_light_assist_gate_supported",true);r=ConfigurationFile.read(stable.toString(),lowCaps,c);check(r.options.getBoolean("low_light_assist_gate"));check(r.options.getLong("low_light_assist_wait")==8000);check(r.notes.isEmpty());
  // Every quick choice must survive real config validation without touching other groups.
  JSONObject presetCaps=clone(s).put("low_light_threshold_supported",true).put("low_light_assist_gate_supported",true);
  JSONObject draft=ConfigurationFile.read(file.toString(),presetCaps,c).options;draft.put("memory_strength",.37).put("memory_max_points",3);
  for(String group:new String[]{"low","outdoor","response","thermal","threshold_override","assist_override","animation_override","sunlight_override","touch_override"})for(int mode=0;mode<3;mode++){
   JSONObject patch=SettingsPresets.patch(group,mode,presetCaps),next=SettingsPresets.merge(draft,patch);
   check(draft.getDouble("memory_strength")==.37&&next.getDouble("memory_strength")==.37);check(next.getInt("memory_max_points")==3);check(next.getDouble("curve_floor_nit")==10);
   Iterator<String> keys=draft.keys();while(keys.hasNext()){String key=keys.next();if(!patch.has(key))check(JSONObject.valueToString(draft.get(key)).equals(JSONObject.valueToString(next.get(key))));}
   ConfigurationFile.Imported validated=ConfigurationFile.read(ConfigurationFile.export(next,presetCaps,true,2,true).toString(),presetCaps,c);check(validated.notes.isEmpty());
   check(SettingsPresets.patch(group,0,new JSONObject()).length()>0);
   try{SettingsPresets.patch(group,1,new JSONObject());throw new AssertionError("unsupported preset accepted");}catch(IllegalArgumentException expected){cases++;}
  }
  JSONObject withoutAux=clone(presetCaps).put("low_light_assist_gate_supported",false);check(!SettingsPresets.patch("low",1,withoutAux).getBoolean("low_light_assist_gate"));
  check(!SettingsPresets.patch("outdoor",2,presetCaps).getBoolean("outdoor_hbm_tuning"));check(!SettingsPresets.patch("outdoor",2,presetCaps).getBoolean("outdoor_range_unlock"));
  JSONObject maxCaps=clone(presetCaps).put("outdoor_range_supported",true).put("outdoor_hbm_supported",true);
  JSONObject maximum=SettingsPresets.patch("outdoor",3,maxCaps);check(maximum.getDouble("outdoor_strength")==1);check(maximum.getBoolean("outdoor_range_unlock"));check(maximum.getBoolean("outdoor_hbm_tuning"));check(maximum.getDouble("outdoor_hbm_budget_factor")==1);check(maximum.getDouble("outdoor_hbm_lux_factor")==.5);
  JSONObject maximumConfig=SettingsPresets.merge(draft,maximum);check(maximumConfig.getDouble("memory_strength")==.37);check(ConfigurationFile.read(ConfigurationFile.export(maximumConfig,maxCaps,true,2,true).toString(),maxCaps,c).notes.isEmpty());
  check(!SettingsPresets.patch("outdoor",3,clone(maxCaps).put("outdoor_hbm_supported",false)).getBoolean("outdoor_hbm_tuning"));
  try{SettingsPresets.patch("outdoor",3,clone(maxCaps).put("outdoor_range_supported",false));throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  check(!maximum.getBoolean("outdoor_opr_relax"));JSONObject oprCaps=clone(maxCaps).put("outdoor_opr_supported",true);JSONObject strongOpr=SettingsPresets.patch("outdoor",3,oprCaps);check(strongOpr.getBoolean("outdoor_opr_relax"));
  JSONObject oprConfig=SettingsPresets.merge(draft,strongOpr);ConfigurationFile.Imported oprImported=ConfigurationFile.read(ConfigurationFile.export(oprConfig,oprCaps,true,2,true).toString(),oprCaps,c);check(oprImported.options.getBoolean("outdoor_opr_relax")&&oprImported.notes.isEmpty());
  oprImported=ConfigurationFile.read(ConfigurationFile.export(oprConfig,oprCaps,true,2,true).toString(),maxCaps,c);check(!oprImported.options.getBoolean("outdoor_opr_relax")&&oprImported.notes.size()==1);
  oprConfig.put("outdoor_range_unlock",false);oprImported=ConfigurationFile.read(ConfigurationFile.export(oprConfig,oprCaps,true,2,true).toString(),oprCaps,c);check(!oprImported.options.getBoolean("outdoor_opr_relax")&&oprImported.notes.size()==1);
  check(!ConfigurationFile.read(old.toString(),s,c).options.getBoolean("outdoor_opr_relax"));
  try{SettingsPresets.patch("response",3,maxCaps);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  try{SettingsPresets.patch("unknown",1,presetCaps);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  check(!ConfigurationFile.read(old.toString(),s,c).options.getBoolean("dark_lock_enabled"));
  JSONObject dark=clone(file);dark.getJSONObject("options").put("dark_lock_enabled",true).put("dark_lock_minutes",12).put("dark_lock_enter_lux",10).put("dark_lock_exit_lux",80).put("dark_lock_exit_seconds",8).put("manual_panel_enabled",false);
  r=ConfigurationFile.read(dark.toString(),new JSONObject(s.toString()).put("dark_lock_supported",true),c);check(r.options.getBoolean("dark_lock_enabled")&&!r.options.getBoolean("manual_panel_enabled"));check(r.options.getInt("dark_lock_minutes")==12&&r.options.getDouble("dark_lock_enter_lux")==10&&r.options.getDouble("dark_lock_exit_lux")==80&&r.options.getInt("dark_lock_exit_seconds")==8);check(r.notes.isEmpty());
  r=ConfigurationFile.read(dark.toString(),s,c);check(!r.options.getBoolean("dark_lock_enabled")&&!r.options.getBoolean("manual_panel_enabled"));check(r.options.getInt("dark_lock_minutes")==12&&r.notes.size()==1);
  for(String key:new String[]{"dark_lock_enabled","manual_panel_enabled"}){bad=clone(file);bad.getJSONObject("options").put(key,1);bad(bad,s,c);}
  for(String key:new String[]{"dark_lock_minutes","dark_lock_enter_lux","dark_lock_exit_lux","dark_lock_exit_seconds"}){bad=clone(file);bad.getJSONObject("options").put(key,"5");bad(bad,s,c);}
  bad=clone(file);bad.getJSONObject("options").put("dark_lock_minutes",1.5);bad(bad,s,c);bad=clone(file);bad.getJSONObject("options").put("dark_lock_enter_lux",10).put("dark_lock_exit_lux",10);bad(bad,s,c);
  System.out.println("Configuration migration: "+cases+" cases PASS; cross-version settings and curve identity, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
names=['BrightnessControlOptions','ConfigurationFile','CurvePlan','AdvancedOptions','AdvancedPolicy','OutdoorOptions','ThermalPolicy','MemoryPolicy','MemoryOptions','DelayPolicy','LowLightPolicy','LowLightThresholds','LowLightAssistGate','SettingsPresets','AppBuild']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in names],str(package/'RootControl.java'),str(package/'ConfigHostTest.java')],check=True)
subprocess.run(['java','-cp',str(classes)+';'+str(J),'top.rongshangs.lumacurve.refactor.ConfigHostTest'],check=True)
