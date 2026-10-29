"""Actual scope reader, process guard and setup coordinator. No device or process signals."""
from pathlib import Path
import json,os,re,subprocess,ast
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/system-ui-setup-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'android/database/DatabaseErrorHandler.java':'package android.database;public interface DatabaseErrorHandler{void onCorruption(android.database.sqlite.SQLiteDatabase db);}',
'android/database/Cursor.java':'''package android.database;public class Cursor implements AutoCloseable{public static final int FIELD_TYPE_INTEGER=1;public int[] values;public int type=1;boolean first;public Cursor(int[] v,int t){values=v;type=t;}public boolean moveToFirst(){first=true;return values!=null;}public boolean moveToNext(){return false;}public int getType(int n){return type;}public int getInt(int n){return values[n];}public void close(){}}''',
'android/database/sqlite/SQLiteDatabase.java':'''package android.database.sqlite;import android.database.*;import java.util.*;public class SQLiteDatabase implements AutoCloseable{public static final int OPEN_READONLY=1,NO_LOCALIZED_COLLATORS=16;public static Map<String,int[]> rows=new HashMap<>();public static String bad="",typed="";String path;public static int opens;public static SQLiteDatabase openDatabase(String p,Object ignored,int flags,DatabaseErrorHandler handler){if(flags!=(OPEN_READONLY|NO_LOCALIZED_COLLATORS)||handler==null)throw new AssertionError("read-only flags/corruption handler");opens++;if(p.equals(bad))throw new SecurityException("denied");SQLiteDatabase d=new SQLiteDatabase();d.path=p;return d;}public Cursor rawQuery(String sql,String[] args){if(!sql.startsWith("SELECT ")||!sql.contains("LIMIT 2")||args.length!=3||!args[0].equals("com.android.systemui")||!args[1].equals("0")||!args[2].equals("top.rongshangs.lumacurve"))throw new AssertionError("scope query escaped own primary-user module");return new Cursor(rows.get(path),path.equals(typed)?3:1);}public void close(){}}''',
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 100;}}',
'android/system/Os.java':'package android.system;public class Os{public static void kill(int pid,int signal){throw new AssertionError("Real signal must not run in host tests");}}',
'android/system/OsConstants.java':'package android.system;public class OsConstants{public static final int SIGKILL=9;}',
'top/rongshangs/lumacurve/refactor/RootSettings.java':'package top.rongshangs.lumacurve.refactor;class RootSettings{String get(String key){return null;}}',
'top/rongshangs/lumacurve/refactor/RootCommand.java':'package top.rongshangs.lumacurve.refactor;class RootCommand{static String run(long t,String...a){throw new AssertionError("No shell operations in host tests");}}',
'top/rongshangs/lumacurve/refactor/ControlCenterEngine.java':'package top.rongshangs.lumacurve.refactor;class ControlCenterEngine{static final String UI="ui";}',
'top/rongshangs/lumacurve/refactor/SystemUiAccessTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.nio.file.*;import org.json.*;import android.database.sqlite.SQLiteDatabase;
public class SystemUiAccessTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("scope case "+cases);cases++;}
 static File db(File dir,String name)throws Exception{File f=new File(dir,name);f.createNewFile();return f;}
 static class Processes implements SystemUiAccess.Processes{SystemUiAccess.Identity current;int signals;public SystemUiAccess.Identity read(int pid){return current;}public void terminate(int pid){check(pid==42);signals++;}}
 static SystemUiAccess.Identity id(int pid,int uid,String name,String start){return new SystemUiAccess.Identity(pid,uid,name,start);}
 public static void main(String[] args)throws Exception{
  File dir=Files.createTempDirectory("hyperlux-scope-test").toFile();File one=db(dir,"one.db"),two=db(dir,"two.db");
  for(int active=0;active<=1;active++)for(int selected=0;selected<=1;selected++){SQLiteDatabase.rows.put(one.toString(),new int[]{active,selected});JSONObject r=SystemUiAccess.scopes(new File[]{one});check(r.getString("scope_state").equals(selected==1?"present":"missing"));check(r.getBoolean("module_enabled")== (active==1));}
  SQLiteDatabase.rows.put(one.toString(),new int[]{1,1});SQLiteDatabase.rows.put(two.toString(),new int[]{1,0});JSONObject r=SystemUiAccess.scopes(new File[]{one,two});check(r.getString("scope_state").equals("unknown")&&r.getBoolean("scope_conflict")&&r.isNull("module_enabled"));
  SQLiteDatabase.bad=two.toString();r=SystemUiAccess.scopes(new File[]{one,two});check(r.getString("scope_state").equals("unknown")&&r.isNull("module_enabled"));SQLiteDatabase.bad="";
  SQLiteDatabase.typed=one.toString();check(SystemUiAccess.scopes(new File[]{one}).getString("scope_state").equals("unknown"));SQLiteDatabase.typed="";
  SQLiteDatabase.rows.remove(one.toString());check(SystemUiAccess.scopes(new File[]{one}).getString("scope_state").equals("module_missing"));check(SystemUiAccess.scopes(new File[]{new File(dir,"absent")}).getString("scope_state").equals("unknown"));
  File proc=new File(dir,"proc"),p=new File(proc,"42");p.mkdirs();Files.write(new File(p,"cmdline").toPath(),"com.android.systemui\0".getBytes("UTF-8"));Files.write(new File(p,"stat").toPath(),("42 (SystemUI) S "+String.join(" ",java.util.Collections.nCopies(18,"0"))+" 123\n").getBytes("UTF-8"));Files.write(new File(p,"status").toPath(),"Uid:\t10123 10123 10123 10123\n".getBytes("UTF-8"));
  JSONObject ui=new JSONObject().put("build",AppBuild.BUILD).put("pid",42).put("process_start","123").put("coordinate_abi",true).put("profile_ready",false);
  r=SystemUiAccess.injection(new JSONObject(),ui.toString(),proc);check(r.getString("injection_state").equals("current")&&r.getBoolean("coordinate_abi")&&!r.getBoolean("profile_ready"));
  check(SystemUiAccess.injection(new JSONObject(),new JSONObject(ui.toString()).put("build","old").toString(),proc).getString("injection_state").equals("old_build"));
  for(String bad:new String[]{null,"bad",new JSONObject(ui.toString()).put("process_start","999").toString(),new JSONObject().put("build",AppBuild.BUILD).toString()})check(SystemUiAccess.injection(new JSONObject(),bad,proc).getString("injection_state").equals("unconfirmed"));
  Files.write(new File(p,"cmdline").toPath(),"system_server\0".getBytes("UTF-8"));check(SystemUiAccess.injection(new JSONObject(),ui.toString(),proc).getString("injection_state").equals("unconfirmed"));
  Processes fake=new Processes();SystemUiAccess.Identity expected=id(42,10123,"com.android.systemui","123");fake.current=expected;check(SystemUiAccess.restartTarget(fake,expected,10123)&&fake.signals==1);
  for(SystemUiAccess.Identity changed:new SystemUiAccess.Identity[]{id(42,10123,"system_server","123"),id(42,10123,"com.android.systemui:other","123"),id(42,10124,"com.android.systemui","123"),id(42,10123,"com.android.systemui","124"),id(43,10123,"com.android.systemui","123")}){fake.current=changed;check(!SystemUiAccess.restartTarget(fake,expected,10123));check(fake.signals==1);}
  check(!SystemUiAccess.restartTarget(fake,null,10123));check(!SystemUiAccess.restartTarget(fake,id(1,10123,"com.android.systemui","123"),10123));check(!SystemUiAccess.restartTarget(fake,expected,-1));
  check(SystemUiAccess.packageUid("package:com.android.systemui uid:10123")==10123);
  for(String bad:new String[]{"package:other uid:10123","package:com.android.systemui uid:no","uid:0","package:com.android.systemui uid:10123 ; reboot"}){try{SystemUiAccess.packageUid(bad);throw new AssertionError();}catch(IOException expectedError){cases++;}}
  System.out.println("SystemUI scope and restart guards: "+cases+" cases PASS; read-only DB/API and process doubles, no device signals");
 }
}'''}
for name,text in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,'utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(O/n) for n in files],str(S/'SystemUiAccess.java'),str(S/'AppBuild.java')],check=True)
result=subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.SystemUiAccessTest'],check=True,capture_output=True,text=True);print(result.stdout,end='');cases=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))

# Reuse widget doubles, but execute the actual new presentation coordinator.
widgets=R/'build/control-center-settings-tests/android';W=O/'widgets';W.mkdir(exist_ok=True)
for file in widgets.rglob('*.java'):
 target=W/'android'/file.relative_to(widgets);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(file.read_bytes())
activity=r'''package top.rongshangs.lumacurve.refactor;
import android.widget.*;import org.json.*;import java.util.*;
class MainActivity{boolean busy,destroyed;int page=1,settingsGroup=5,BLUE=1;String title,message,toast;Runnable confirm;JSONObject answer=new JSONObject();final Queue worker=new Queue(),ui=new Queue();final List<String> commands=new ArrayList<>();
 static class Queue{final ArrayDeque<Runnable> list=new ArrayDeque<>();void execute(Runnable r){list.add(r);}void post(Runnable r){r.run();}void postDelayed(Runnable r,long n){list.add(r);}void drain(){while(!list.isEmpty())list.remove().run();}}
 JSONObject request(String command,String payload){commands.add(command);return answer;}LinearLayout column(){return new LinearLayout();}int dp(int n){return n;}String tr(String s){return s;}TextView text(String s,int size,int color){TextView t=new TextView();t.setText(s);return t;}Button action(String s,Runnable r){Button b=new Button();b.setText(s);b.setOnClickListener(v->r.run());return b;}void messageDialog(String t,String m,String label,Runnable r){title=t;message=m;confirm=r;}void show(String s){toast=s;}}
public class SetupUiTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("setup UI "+cases);cases++;}static JSONObject answer(String scope,String loaded)throws Exception{return new JSONObject().put("ok",true).put("control_center_setup",new JSONObject().put("scope_state",scope).put("module_enabled",true).put("injection_state",loaded));}
 public static void main(String[] args)throws Exception{MainActivity a=new MainActivity();LinearLayout parent=new LinearLayout();TextView heading=new TextView();ControlCenterSetup setup=new ControlCenterSetup(a,parent,heading);check(parent.children.size()==2);LinearLayout row=(LinearLayout)parent.children.get(0);check(row.orientation==LinearLayout.HORIZONTAL&&row.children.get(0)==heading&&row.children.get(1)==setup.restart);check(((LinearLayout.LayoutParams)heading.params).weight==1&&setup.restart.minimumHeight==40);
 a.answer=answer("missing","unconfirmed");setup.check(true);check(a.commands.isEmpty());a.worker.drain();check(a.commands.size()==1&&a.commands.get(0).equals("mapping-setup")&&a.title.equals("添加系统界面作用域")&&setup.status.text.contains("未勾选"));setup.restart.performClick();check(a.title.equals("添加系统界面作用域")&&a.commands.size()==1&&a.message.contains("显示系统应用"));
 a.answer=answer("present","unconfirmed");a.confirm.run();a.worker.drain();check(setup.status.text.contains("已勾选"));setup.restart.performClick();check(a.title.equals("重启系统界面")&&a.commands.size()==2);a.answer=new JSONObject().put("ok",true).put("message","requested");a.confirm.run();check(a.commands.size()==2);a.worker.drain();check(a.commands.get(2).equals("restart-system-ui")&&a.toast.equals("requested"));
 a.answer=answer("unknown","current");a.ui.drain();a.worker.drain();check(setup.status.text.contains("已加载"));a.answer=answer("unknown","unconfirmed");setup.check(true);a.worker.drain();check(setup.status.text.contains("暂无法确认"));a.busy=true;setup.refresh();check(!setup.restart.enabled);a.busy=false;a.destroyed=true;int count=a.commands.size();setup.check(true);a.worker.drain();check(a.commands.size()==count);
 System.out.println("SystemUI setup UI: "+cases+" cases PASS; user-confirmed actions, Android rendering not tested");}}
'''
p=W/'top/rongshangs/lumacurve/refactor/SetupUiTest.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(activity,'utf-8')
WC=W/'classes';WC.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(WC),*[str(p) for p in W.rglob('*.java')],str(S/'ControlCenterSetup.java')],check=True)
result=subprocess.run(['java','-cp',str(WC)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.SetupUiTest'],check=True,capture_output=True,text=True);print(result.stdout,end='');cases+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
main=(S/'MainActivity.java').read_text('utf-8');root=(S/'RootControl.java').read_text('utf-8')
assert 'group==5&&controlCenterSettings!=null)controlCenterSettings.setup.check' in main
assert 'command.equals("restart-system-ui"))return SystemUiAccess.restart(settings)' in root
assert 'command.equals("mapping-setup")' in root
for phrase in re.findall(r'"(?:[^"\\]|\\.)*"',(S/'ControlCenterSetup.java').read_text('utf-8')):
 text=ast.literal_eval(phrase)
 if re.search('[\u4e00-\u9fff]',text):assert json.dumps(text,ensure_ascii=False) in (S/'UiText.java').read_text('utf-8'),text
print(f'SystemUI setup total: {cases} cases PASS; no DB writes, real signals or Android device execution')
