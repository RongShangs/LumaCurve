"""Reproduce provider string limits and exercise the actual confirmation methods.
Host service doubles; this does not claim Android device verification.
"""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-status-tests';O.mkdir(parents=True,exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
def method(file,signature):
    text=(S/file).read_text(encoding='utf-8');start=text.index(signature);opening=text.index('{',start);depth=1;end=opening+1
    while depth:
        depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]

sources={
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 5000;}}',
'android/os/Process.java':'package android.os;public class Process{public static int myPid(){return 42;}}',
'android/content/Context.java':'package android.content;public class Context{public Object getContentResolver(){return this;}}',
'android/provider/Settings.java':'''package android.provider;import java.util.*;public class Settings{public static class Global{public static Map<String,String> data=new HashMap<>();public static boolean failRead,failWrite;public static String getString(Object c,String k){if(failRead)throw new IllegalStateException("read failure");return data.get(k);}public static boolean putString(Object c,String k,String v){if(v!=null&&v.length()>32768)throw new IllegalArgumentException("setting too long");if(failWrite)return false;data.put(k,v);return true;}}}''',
'de/robv/android/xposed/XposedBridge.java':'package de.robv.android.xposed;public class XposedBridge{public static void log(String text){}}',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;import java.util.*;import java.io.*;import org.json.*;import android.os.*;import android.provider.Settings;import de.robv.android.xposed.XposedBridge;
class HookRuntime{static final String CONFIG="config",BUILD="test";String fingerprint="firmware",revision="",phase="attached",message="ready",processedConfig;boolean configProcessed,closed,changing,lowLightEnabled;int reloads;android.content.Context context=new android.content.Context();static String processStart(){return "123";}void refreshUserIdentity(){}
'''+method('HookRuntime.java','void reload(').split('if(text==null')[0]+'''reloads++;JSONObject j=new JSONObject(text);revision=j.getString("revision");phase=j.optBoolean("enabled")?"active":"attached";publishAcknowledgement();}catch(Exception invalid){phase="error";message="bad settings";publishAcknowledgement();}finally{changing=false;}}
'''+method('HookRuntime.java','void refreshConfiguration(')+method('HookRuntime.java','void publishAcknowledgement(')+'}',
'top/rongshangs/lumacurve/refactor/RootControl.java':'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.util.*;import java.io.*;class RootControl{static final String ACK="lumacurve_refactor_ack_v1",REFRESH="refresh",STATUS="status";JSONObject old;HookRuntime hook;boolean deliver=true;int refreshes,sleeps;Writer settings=new Writer();class Writer{boolean put(String k,String v){refreshes++;if(deliver)hook.refreshConfiguration();return true;}}JSONObject validated(String key){if(!key.equals(ACK))return old;try{String raw=android.provider.Settings.Global.data.get(ACK);if(raw==null)return null;JSONObject ack=new JSONObject(raw);return ack.getString("build").equals("test")&&ack.getInt("pid")==42&&ack.getString("process_start").equals("123")&&ack.getString("fingerprint").equals("firmware")?ack:null;}catch(Exception unavailable){return null;}}JSONObject live(){return old;}
'''+method('RootControl.java','boolean waitFor(').replace('Thread.sleep(100);','sleeps++;')+method('RootControl.java','String confirmationDetail(')+'}',
'top/rongshangs/lumacurve/refactor/StatusTest.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.util.*;import android.provider.Settings;
public class StatusTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("case "+cases);cases++;}
 static String repeat(String text,int count){StringBuilder b=new StringBuilder();for(int i=0;i<count;i++)b.append(text);return b.toString();}
 static void equal(Object a,Object b){check(a.toString().equals(b.toString()));}
 public static void main(String[] args)throws Exception{
  JSONObject state=new JSONObject().put("phase","active").put("revision","new").put("factory_lux",new JSONArray("[0,30,600,5000]"));
  JSONArray logs=new JSONArray(),pipeline=new JSONArray(),outputs=new JSONArray(),limits=new JSONArray();
  for(int i=0;i<160;i++)logs.put("record "+i+repeat("中\"\\😀",180));
  for(int i=0;i<24;i++)pipeline.put(new JSONObject().put("sequence",i).put("detail",repeat("x",700)));
  for(int i=0;i<16;i++)outputs.put(new JSONObject().put("sequence",i));
  for(int i=0;i<32;i++)limits.put(new JSONObject().put("sequence",i));
  state.put("logs",logs).put("pipeline_trace",pipeline).put("output_trace",outputs).put("outdoor",new JSONObject().put("enabled",true).put("limit_trace",limits));
  check(state.toString().length()>32768);boolean rejected=false;try{Settings.Global.putString(null,StatusTransport.KEY,state.toString());}catch(IllegalArgumentException limit){rejected=true;}check(rejected);
  Map<String,String> chunks=StatusTransport.encode(state);check(chunks.size()>8);check(new ArrayList<>(chunks.keySet()).get(chunks.size()-1).equals(StatusTransport.KEY));
  for(Map.Entry<String,String> chunk:chunks.entrySet()){check(chunk.getValue().length()<=30000);check(chunk.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=30000);check(Settings.Global.putString(null,chunk.getKey(),chunk.getValue()));}
  JSONObject core=new JSONObject(chunks.get(StatusTransport.KEY));check(!core.has("logs"));equal(core.getJSONArray("factory_lux"),state.getJSONArray("factory_lux"));
  JSONObject restored=StatusTransport.restore(core,k->Settings.Global.data.get(k));equal(restored.getJSONArray("logs"),logs);equal(restored.getJSONArray("pipeline_trace"),pipeline);equal(restored.getJSONArray("output_trace"),outputs);equal(restored.getJSONObject("outdoor").getJSONArray("limit_trace"),limits);check(!restored.has("status_missing_sections"));equal(state.getJSONArray("logs"),logs);check(state.getJSONObject("outdoor").has("limit_trace"));
  // A partial/concurrent publication cannot pair the new histories with old metadata.
  Map<String,String> newer=StatusTransport.encode(state);String first=StatusTransport.key(0,0);Settings.Global.data.put(first,newer.get(first));
  restored=StatusTransport.restore(new JSONObject(chunks.get(StatusTransport.KEY)),k->Settings.Global.data.get(k));check(!restored.has("logs"));check(restored.getJSONArray("status_missing_sections").toString().contains("logs"));equal(restored.getJSONArray("pipeline_trace"),pipeline);check(restored.getString("revision").equals("new"));
  Settings.Global.data.remove(first);restored=StatusTransport.restore(new JSONObject(chunks.get(StatusTransport.KEY)),k->Settings.Global.data.get(k));check(!restored.has("logs"));
  JSONObject legacy=new JSONObject().put("revision","old").put("logs",logs);check(StatusTransport.restore(legacy,k->null)==legacy);
  // Large JSON escaped control characters and surrogate pairs round-trip without loss.
  JSONObject escaped=new JSONObject().put("logs",new JSONArray().put(repeat("\u0000\n\"\\😀",6000)));Map<String,String> escapeChunks=StatusTransport.encode(escaped);
  for(String value:escapeChunks.values())check(StatusTransport.fits(value));equal(StatusTransport.restore(new JSONObject(escapeChunks.get(StatusTransport.KEY)),escapeChunks::get).getJSONArray("logs"),escaped.getJSONArray("logs"));
  HookRuntime hook=new HookRuntime();RootControl root=new RootControl();root.hook=hook;Settings.Global.data.clear();Settings.Global.data.put("config",new JSONObject().put("revision","submitted").put("enabled",true).toString());
  // Full status is stale/missing, but the config observer/refresh still confirms application.
  check(root.waitFor("submitted",true));check(hook.reloads==1);check(root.sleeps==0);equal(root.validated(RootControl.ACK).getString("revision"),"submitted");
  hook.lowLightEnabled=true;hook.reload();check(hook.lowLightEnabled);check(hook.reloads==1);check(!hook.changing);
  check(root.waitFor("submitted",true));check(hook.reloads==1);check(hook.lowLightEnabled); // no anchor-resetting duplicate reload
  Settings.Global.data.put("config",new JSONObject().put("revision","off").put("enabled",false).toString());check(root.waitFor("off",false));check(hook.reloads==2);
  hook.phase="error";hook.message="interface mismatch";hook.publishAcknowledgement();boolean error=false;try{root.waitFor("off",false);}catch(java.io.IOException failure){error=failure.getMessage().equals("interface mismatch");}check(error);
  Settings.Global.data.clear();root.deliver=false;root.old=new JSONObject().put("revision","old").put("phase","active");check(!root.waitFor("new",true));check(root.sleeps==60);check(root.confirmationDetail().contains("active"));
  // Identity validation does not let a stale boot/build acknowledgment count as success.
  Settings.Global.data.put(RootControl.ACK,new JSONObject().put("build","old-build").put("pid",42).put("process_start","123").put("fingerprint","firmware").put("revision","new").put("phase","active").toString());check(root.validated(RootControl.ACK)==null);check(!root.waitFor("new",true));
  root.old=new JSONObject().put("revision","legacy").put("phase","active");check(root.waitFor("legacy",true));
  hook.closed=true;int before=hook.reloads;hook.refreshConfiguration();check(hook.reloads==before);hook.closed=false;hook.changing=true;hook.refreshConfiguration();check(hook.reloads==before);
  hook.changing=false;Settings.Global.failRead=true;hook.refreshConfiguration();check(hook.reloads==before);Settings.Global.failRead=false;
  System.out.println("Status transport/confirmation: "+cases+" cases PASS; host service doubles, device not verified");
 }
}'''}
files=[]
for name,text in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(p) for p in files],str(S/'StatusTransport.java')],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.StatusTest'],check=True)
