"""Production readiness generation/ACK and protected wake-up. Host only; no Root or phone."""
from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/mapping-readiness-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'android/content/Context.java':'''package android.content;public class Context{public boolean allowed=true;public int broadcasts;public Intent last;public int checkSelfPermission(String p){if(!p.equals("android.permission.STATUS_BAR_SERVICE"))throw new AssertionError();return allowed?0:-1;}public void sendBroadcast(Intent i){broadcasts++;last=i;}}''',
'android/content/Intent.java':'''package android.content;import java.util.*;public class Intent{public final String action;public ComponentName component;public Map<String,String> extras=new HashMap<>();public Intent(String a){action=a;}public Intent setComponent(ComponentName c){component=c;return this;}public Intent putExtra(String k,String v){extras.put(k,v);return this;}}''',
'android/content/ComponentName.java':'''package android.content;public class ComponentName{public final String pkg,name;public ComponentName(String p,String n){pkg=p;name=n;}}''',
'ReadinessTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import android.content.*;
public class ReadinessTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("readiness "+cases);cases++;}
 static JSONObject table()throws Exception{return new JSONObject().put("build",AppBuild.BUILD).put("pid",4127).put("process_start","1032").put("boot_count","86").put("revision","saved").put("user_serial",0).put("mode",4).put("maximum",16383).put("physical","main").put("enabled",true).put("calibration",new JSONObject());}
 static JSONObject ui(JSONObject p)throws Exception{return new JSONObject().put("build",AppBuild.BUILD).put("pid",25419).put("process_start","12867").put("coordinate_abi",true).put("profile_ready",true).put("bound_profile",MappingRecoveryState.identity(p));}
 public static void main(String[] a)throws Exception{
  JSONObject t=table();String original=t.toString(),key=MappingRecoveryState.generation(t,"86");check(!key.isEmpty()&&t.toString().equals(original));check(MappingRecoveryState.generation(t,"85").isEmpty());check(MappingRecoveryState.generation(null,"86").isEmpty());
  for(String field:new String[]{"enabled","build","pid","process_start","boot_count","revision","user_serial","mode","calibration"}){JSONObject bad=new JSONObject(t.toString());bad.remove(field);check(MappingRecoveryState.generation(bad,"86").isEmpty());}
  for(int mode:new int[]{-1,0,5})check(MappingRecoveryState.generation(new JSONObject(t.toString()).put("mode",mode),"86").isEmpty());
  JSONObject ack=ui(t);check(MappingRecoveryState.acknowledged(t,ack));check(!MappingRecoveryState.acknowledged(t,null));
  for(String field:MappingRecoveryState.IDENTITY){JSONObject stale=ui(t);stale.getJSONObject("bound_profile").put(field,field.equals("pid")||field.equals("mode")||field.equals("maximum")||field.equals("user_serial")?999:"old");check(!MappingRecoveryState.acknowledged(t,stale));}
  for(String field:new String[]{"coordinate_abi","profile_ready","bound_profile","pid","process_start","build"}){JSONObject bad=ui(t);bad.remove(field);check(!MappingRecoveryState.acknowledged(t,bad));}
  // The supplied 4.4.1 record had profile_ready, but no bound engine identity: not an ACK.
  JSONObject old=ui(t);old.remove("bound_profile");check(!MappingRecoveryState.acknowledged(t,old));
  MappingRecoveryState.Gate gate=new MappingRecoveryState.Gate();check(!gate.claim("",0));check(gate.claim(key,100));for(int i=0;i<100;i++)check(!gate.claim(key,101+i));check(gate.claim(key,5100));for(int i=0;i<100;i++)check(!gate.claim(key,10000+i));check(gate.claim(key+"new revision",20000));
  Context c=new Context();c.allowed=false;try{MappingRecoverySignal.send(c,key);throw new AssertionError();}catch(SecurityException expected){check(c.broadcasts==0);}c.allowed=true;MappingRecoverySignal.send(c,key);check(c.broadcasts==1&&c.last.action.equals(MappingRecoverySignal.ACTION)&&c.last.component.pkg.equals(MappingRecoverySignal.APP)&&c.last.component.name.endsWith("MappingStartup$ReadyReceiver")&&c.last.extras.get("generation").equals(key));
  System.out.println("Mapping readiness: "+cases+" cases PASS; actual contracts and explicit broadcast with service doubles, no device startup execution");
 }
}'''}
paths=[]
for name,text in files.items():p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,'utf-8');paths.append(str(p))
C=O/'classes';C.mkdir(exist_ok=True);subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*paths,*[str(S/(n+'.java')) for n in ['MappingRecoveryState','MappingRecoverySignal','AppBuild']]],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.ReadinessTest'],check=True)
