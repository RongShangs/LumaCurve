"""Run actual root refresh/promotion code against a controlled settings transport."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-saved-transaction-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
source=(S/'RootControl.java').read_text(encoding='utf-8');start=source.index('    JSONObject refreshedLive()');refresh=source[start:source.index('    JSONObject inspect()',start)]
start=source.index('            if(command.equals("promote-memory"))');promotion=source[start:source.index('            if(command.equals("stop"))',start)]
p=O/'SavedTransactionTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
class RootControl {
 boolean selfCheckNext;static final String CONFIG="config",REFRESH="refresh";JSONObject before,after,applied;boolean respond=true,requested;int applies;
 final Store settings=new Store();class Store {String config;String get(String key){return config;}boolean put(String k,String v){if(k.equals(REFRESH))requested=true;return true;}}
 JSONObject live(){return requested&&respond?after:before;}
 JSONObject apply(String payload)throws Exception{applies++;applied=new JSONObject(new String(Base64.getDecoder().decode(payload),StandardCharsets.UTF_8));return new JSONObject().put("ok",true);}
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
  System.out.println("Saved curve transaction: "+cases+" cases PASS; production root branch with transport doubles, Android not tested");
 }
}''',encoding='utf-8')
clock=O/'android/os/SystemClock.java';clock.parent.mkdir(parents=True,exist_ok=True);clock.write_text('package android.os;public class SystemClock{public static long elapsedRealtime(){return 10000;}}',encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),str(S/'SavedCurve.java'),str(S/'CurvePlan.java'),str(clock),str(p)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.SavedTransactionTest'],check=True)
