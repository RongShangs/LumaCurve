"""Exercise production root raw-panel orchestration with native/provider doubles."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/raw-panel-transaction-tests';O.mkdir(parents=True,exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
text=(S/'RootControl.java').read_text(encoding='utf-8');start=text.index('    JSONObject rawPanel(String payload)');end=text.index('    JSONObject execute(',start);method=text[start:end]
files={
'android/app/ActivityManager.java':'package android.app;public class ActivityManager{public static int getCurrentUser(){return 0;}}',
'top/rongshangs/lumacurve/refactor/RootControl.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
class RootControl{
static final File DATA=new File("build/raw-panel-transaction-tests");Settings settings=new Settings();boolean reject,failMode,failHook;String session="";int hookCalls,aborts;List<String> calls=new ArrayList<>();
class Settings{String mode="1";String getSystem(String key){return mode;}boolean putSystem(String key,String value){mode=value;return true;}}
JSONObject live()throws Exception{return new JSONObject().put("pid",123).put("phase","active");}
JSONObject refreshedLive()throws Exception{return live();}
JSONObject brightness(String encoded)throws Exception{hookCalls++;JSONObject request=new JSONObject(new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8));String action=request.getString("action");calls.add(action);
 if(failHook)throw new IOException("no hook");
 if(action.equals("raw_abort")){aborts++;settings.mode="1";session="";}
 if(reject||action.equals("raw_validate")&&!session.equals(request.getString("session")))return new JSONObject().put("ok",false).put("message","stale gesture");
 if(action.equals("raw_begin")){session=request.getString("session");if(!failMode)settings.mode="0";}
 if(action.equals("raw_restore")){settings.mode="1";session="";}
 return new JSONObject().put("ok",true).put("state",new JSONObject().put("auto",settings.mode.equals("1")).put("owner",session.isEmpty()?"none":"raw_panel").put("session",session));}
'''+method+'}',
'top/rongshangs/lumacurve/refactor/NativePanelRoot.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.io.*;
class NativePanelRoot{static boolean failStart;static int starts,stops;static void reset(){failStart=false;starts=stops=0;NativePanelClient.reset();}static JSONObject status()throws Exception{return new JSONObject().put("supported",true).put("maximum",16383).put("actual",NativePanelClient.actual);}static void start()throws Exception{starts++;if(failStart)throw new IOException("exit=6 escape_app_freezer");}static void stop(){stops++;}}
''',
'top/rongshangs/lumacurve/refactor/NativePanelClient.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.io.*;import java.util.*;
class NativePanelClient{static int actual=700;static boolean failArm,failSet;static List<String> calls=new ArrayList<>();static void reset(){actual=700;failArm=failSet=false;calls.clear();}static void require(String request)throws Exception{calls.add(request);if(request.startsWith("ARM")&&failArm)throw new IOException("lease rejected");}static JSONObject call(String request)throws Exception{calls.add(request);if(failSet)return new JSONObject().put("ok",false).put("reason","write_or_readback_failed").put("errno",13);actual=Integer.parseInt(request.split(" ")[2]);return new JSONObject().put("ok",true);}}
''',
'top/rongshangs/lumacurve/refactor/RawTransactionTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;
public class RawTransactionTest{
static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
static RootControl ctl(){NativePanelRoot.reset();return new RootControl();}
static String req(String action,Object value,String token)throws Exception{return Base64.getEncoder().encodeToString(new JSONObject().put("action",action).put("value",value).put("session",token).toString().getBytes(StandardCharsets.UTF_8));}
static String failed(RootControl c,String action,Object value,String token)throws Exception{try{c.rawPanel(req(action,value,token));throw new AssertionError("operation passed");}catch(IOException expected){String message=expected.getMessage();check(new String(Files.readAllBytes(new File(RootControl.DATA,"last-panel-error.txt").toPath()),StandardCharsets.UTF_8).contains(message));return message;}}
public static void main(String[] ignored)throws Exception{
RootControl c=ctl();check(c.rawPanel(req("begin",12000,"new")).getBoolean("ok"));check(c.settings.mode.equals("0")&&NativePanelClient.actual==12000);check(NativePanelClient.calls.equals(Arrays.asList("ARM new 123","SET new 12000")));check(c.calls.equals(Arrays.asList("raw_begin","raw_ready")));check(NativePanelRoot.starts==1);
check(c.rawPanel(req("set",16000,"new")).getBoolean("ok"));check(NativePanelClient.actual==16000&&NativePanelRoot.starts==1);check(NativePanelClient.calls.get(2).equals("SET new 16000"));
check(c.rawPanel(req("set",16383,"new")).getBoolean("ok"));check(NativePanelClient.actual==16383&&NativePanelClient.calls.get(3).equals("SET new 16383"));
c=ctl();NativePanelRoot.failStart=true;check(failed(c,"begin",4000,"a").contains("启动主屏原生守护"));check(c.settings.mode.equals("1")&&c.hookCalls==0&&NativePanelClient.calls.isEmpty());
c=ctl();c.reject=true;check(failed(c,"begin",4000,"a").contains("关闭自动亮度并取得系统许可"));check(c.settings.mode.equals("1")&&NativePanelClient.calls.isEmpty());
c=ctl();c.failMode=true;check(failed(c,"begin",4000,"a").contains("自动亮度仍开启"));check(NativePanelClient.calls.equals(Arrays.asList("STOP a"))&&c.aborts==1&&NativePanelClient.actual==700);
c=ctl();NativePanelClient.failArm=true;check(failed(c,"begin",4000,"a").contains("传递节点接管许可"));check(c.settings.mode.equals("1")&&c.aborts==1&&NativePanelClient.actual==700);check(NativePanelClient.calls.equals(Arrays.asList("ARM a 123","STOP a")));
c=ctl();NativePanelClient.failSet=true;check(failed(c,"begin",4000,"a").contains("写入主屏节点并读回"));check(c.settings.mode.equals("1")&&c.aborts==1&&NativePanelClient.actual==700);check(NativePanelClient.calls.get(2).equals("STOP a"));
for(Object value:new Object[]{0,9,16384,4000.5}){c=ctl();failed(c,"begin",value,"a");check(NativePanelRoot.starts==0&&c.hookCalls==0&&c.settings.mode.equals("1"));}
c=ctl();c.rawPanel(req("begin",4000,"new"));check(failed(c,"set",8000,"old").contains("确认当前接管会话"));check(c.session.equals("new")&&c.settings.mode.equals("0")&&NativePanelClient.actual==4000);
c=ctl();c.failHook=true;check(failed(c,"begin",4000,"a").contains("no hook"));check(c.settings.mode.equals("1")&&NativePanelClient.calls.isEmpty());
c=ctl();c.rawPanel(req("begin",4000,"a"));check(c.rawPanel(req("restore",0,"a")).getBoolean("ok"));check(NativePanelRoot.stops==1&&c.settings.mode.equals("1"));
c=ctl();c.failHook=true;check(c.rawPanel(req("restore",0,"a")).getBoolean("ok"));check(NativePanelRoot.stops==1&&c.settings.mode.equals("1"));
System.out.println("Raw panel root transaction: "+cases+" cases PASS; production orchestration with service doubles, Android not tested");
}}
'''}
paths=[]
for name,source in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');paths.append(p)
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(p) for p in paths]],check=True)
subprocess.run(['java','-cp',str(classes)+';'+str(J),'top.rongshangs.lumacurve.refactor.RawTransactionTest'],check=True,cwd=R)
