"""Run actual root refresh/promotion code against a controlled settings transport."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-saved-transaction-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
source=(S/'RootControl.java').read_text(encoding='utf-8');start=source.index('    JSONObject refreshedLive()');refresh=source[start:source.index('    JSONObject inspect()',start)]
start=source.index('            if(command.equals("promote-memory"))');promotion=source[start:source.index('            if(command.equals("restore-defaults"))',start)]
p=O/'SavedTransactionTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
class RootControl {
 boolean selfCheckNext;String selfCheckReason="manual";static final String CONFIG="config",REFRESH="refresh";JSONObject before,after,applied;boolean respond=true,requested,themeRace;int applies;
 final Store settings=new Store();class Store {String config;String get(String key){return config;}boolean put(String k,String v){if(k.equals(REFRESH))requested=true;return true;}}
 JSONObject live(){return requested&&respond?after:before;}
 JSONObject apply(String payload)throws Exception{JSONObject options=new JSONObject(new String(Base64.getDecoder().decode(payload),StandardCharsets.UTF_8)),state=new JSONObject(after.toString());if(themeRace)state.put("theme_curve_dark",!state.optBoolean("theme_curve_dark"));if(options.has("captured_theme_dark")&&options.getBoolean("captured_theme_dark")!=state.optBoolean("theme_curve_dark"))throw new IOException("深浅色模式已变化，请重新保存当前曲线");applies++;applied=options;return new JSONObject().put("ok",true);}
'''+refresh+r'''
 JSONObject execute(String command)throws Exception{
'''+promotion+r'''
 throw new IOException("unknown");}
}
public class SavedTransactionTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static RootControl ready()throws Exception{RootControl c=new RootControl();c.before=new JSONObject().put("elapsed_ms",9000);c.after=new JSONObject().put("elapsed_ms",9500).put("phase","active").put("revision","revision").put("curve_backend","refactor").put("memory_live_count",1).put("factory_lux",new JSONArray("[0,30,600,5000]")).put("min_logical_nit",1).put("max_logical_nit",1000).put("current_anchors_lux",new JSONArray("[0,7,30,300,600,1200,5000]")).put("current_anchors_nit",new JSONArray("[2,5,40,90,150,350,1000]"));c.settings.config=new JSONObject().put("enabled",true).put("revision","revision").put("factors","1,1.2,1,1").put("memory_strength",.25).put("manual_brightness_apps",new JSONArray().put("com.demo.game")).toString();return c;}
 static void rejected(RootControl c)throws Exception{String config=c.settings.config;try{c.execute("promote-memory");throw new AssertionError("accepted");}catch(IOException|IllegalArgumentException expected){cases++;}check(c.applies==0);check(Objects.equals(config,c.settings.config));}
 public static void main(String[] args)throws Exception{
  RootControl c=ready();check(c.execute("promote-memory").getBoolean("ok"));check(c.applies==1&&c.requested);check(c.applied.getString("factors").equals("1.0,1.0,1.0,1.0"));check(c.applied.getDouble("curve_floor_nit")==0);check(c.applied.getDouble("memory_strength")==.25);check(c.applied.getJSONArray("manual_brightness_apps").getString(0).equals("com.demo.game"));check(c.applied.getString("reset_anchors").matches("[A-Za-z0-9_-]{1,80}"));check(SavedCurve.parse(c.applied).lux.length==7);check(SavedCurve.parse(c.applied).at(7)==5);
  c=ready();c.respond=false;rejected(c); // No new response: never save an old graph or clear live memory.
  c=ready();c.before=null;c.after=null;rejected(c);
  c=ready();c.after.put("revision","changed");rejected(c);
  c=ready();c.after.put("memory_live_count",0);rejected(c);
  c=ready();c.after.put("hdr_active",true);rejected(c);
  c=ready();c.settings.config=null;rejected(c);
  c=ready();c.settings.config=new JSONObject(c.settings.config).put("enabled",false).toString();rejected(c);
  c=ready();c.before.put("elapsed_ms",-100000);c.after.put("elapsed_ms",-99999);rejected(c);
  c=ready();c.after.put("elapsed_ms",10001);rejected(c);
  c=ready();float u=Math.ulp(4000f);c.after.put("curve_backend","physical_mapping").put("physical_mapping_active",true).put("max_logical_nit",4000).put("current_anchors_lux",new JSONArray("[0,30,600,1000,3000,5000]")).put("current_anchors_nit",new JSONArray(new float[]{2,100,300,4000+2*u,4000,4000-u}));
  c.settings.config=new JSONObject(c.settings.config).put("memory_persist",true).put("memory_hold_switch",true).put("outdoor_max_nit",1800).put("sentinel",new JSONObject().put("keep",17)).toString();
  check(c.execute("promote-memory").getBoolean("ok"));check(c.applies==1);SavedCurve physical=SavedCurve.parse(c.applied);physical.validate(1,4000,5000);check(physical.lux.length==6&&physical.nit[3]==4000&&physical.nit[4]==4000&&physical.nit[5]==4000);check(c.applied.getDouble("memory_strength")==.25&&c.applied.getBoolean("memory_persist")&&c.applied.getBoolean("memory_hold_switch"));check(c.applied.getDouble("outdoor_max_nit")==1800&&c.applied.getJSONObject("sentinel").getInt("keep")==17);check(c.applied.getJSONArray("manual_brightness_apps").getString(0).equals("com.demo.game"));
  c=ready();c.after.put("curve_backend","physical_mapping").put("physical_mapping_active",true).put("current_anchors_nit",new JSONArray("[2,5,40,90,150,350,1000.01]"));rejected(c);
  c=ready();c.after.put("curve_backend","physical_mapping").put("physical_mapping_active",true).put("current_anchors_nit",new JSONArray("[2,5,40,90,85,350,1000]"));rejected(c);
  c=ready();JSONObject darkOptions=new JSONObject(c.settings.config);ThemeCurveOptions theme=new ThemeCurveOptions();theme.enabled=true;theme.factors=new float[]{1,.8f,.9f,1};theme.put(darkOptions);c.settings.config=darkOptions.toString();c.after.put("theme_curve_dark",true);
  check(c.execute("promote-memory").getBoolean("ok"));check(c.applied.getString("factors").equals("1,1.2,1,1")&&!c.applied.has(SavedCurve.KEY));ThemeCurveOptions promoted=ThemeCurveOptions.parse(c.applied);check(promoted.enabled&&promoted.saved.lux.length==7&&promoted.factors[1]==1&&promoted.floor==0);check(c.applied.getDouble("memory_strength")==.25);
  c=ready();theme.put(darkOptions=new JSONObject(c.settings.config));c.settings.config=darkOptions.toString();check(c.execute("promote-memory").getBoolean("ok"));check(ThemeCurveOptions.parse(c.applied).factors[1]==.8f&&SavedCurve.parse(c.applied)!=null);
  c=ready();c.themeRace=true;rejected(c);
  System.out.println("Saved curve transaction: "+cases+" cases PASS; production root branch with transport doubles, Android not tested");
 }
}''',encoding='utf-8')
clock=O/'android/os/SystemClock.java';clock.parent.mkdir(parents=True,exist_ok=True);clock.write_text('package android.os;public class SystemClock{public static long elapsedRealtime(){return 10000;}}',encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),str(S/'ThemeCurveOptions.java'),str(S/'SavedCurve.java'),str(S/'CurvePlan.java'),str(clock),str(p)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.SavedTransactionTest'],check=True)
