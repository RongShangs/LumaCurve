"""Production Root adapters with service doubles; no Android permission grants implied."""
from pathlib import Path
import os, subprocess, json, re

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/root-access-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
P='top/rongshangs/lumacurve/refactor/'
files={
'android/os/IBinder.java':'package android.os;public interface IBinder{}',
'android/os/Binder.java':'package android.os;public class Binder implements IBinder{}',
'android/os/Process.java':'package android.os;public class Process{public static int uid=0;public static int myUid(){return uid;}public static int myPid(){return 123;}}',
'android/os/Build.java':'package android.os;public class Build{public static String MODEL="test",FINGERPRINT="test-fingerprint";}',
'android/os/Bundle.java':'''package android.os;public class Bundle{java.util.Map<String,Object> m=new java.util.HashMap<>();public void putInt(String k,int v){m.put(k,v);}public void putString(String k,String v){m.put(k,v);}public String getString(String k){return (String)m.get(k);}}''',
'android/content/AttributionSource.java':'''package android.content;public class AttributionSource{public int uid;public String pkg;public static class Builder{AttributionSource s=new AttributionSource();public Builder(int uid){s.uid=uid;}public Builder setPackageName(String p){s.pkg=p;return this;}public Builder setPid(int p){return this;}public AttributionSource build(){return s;}}}''',
'android/app/ActivityManager.java':'''package android.app;import android.os.*;import android.content.*;public class ActivityManager{
 public static Manager manager=new Manager();public static Manager getService(){return manager;}
 public static class Holder{public Provider provider=new Provider();}
 public static class Manager{public boolean denyGet,denyWrite,nullGet,nullPut=true;public int puts,gets,closed,uid;public String pkg;public java.util.Map<String,String> map=new java.util.HashMap<>();
 public Holder getContentProviderExternal(String n,int u,IBinder t,String tag){if(denyGet)throw new SecurityException("Permission denied");return new Holder();}
 public void removeContentProviderExternalAsUser(String n,IBinder t,int u){closed++;}}
 public static class Provider{public Bundle call(AttributionSource source,String authority,String method,String key,Bundle extras){Manager m=manager;m.uid=source.uid;m.pkg=source.pkg;if(method.startsWith("GET")){m.gets++;if(m.denyGet)throw new SecurityException("denied get");if(m.nullGet)return null;Bundle out=new Bundle();out.putString("value",m.map.get(key));return out;}m.puts++;if(m.denyWrite)throw new SecurityException("denied put");String v=extras.getString("value");if(v==null)m.map.remove(key);else m.map.put(key,v);return m.nullPut?null:new Bundle();}}
}''',
P+'RootWorkspace.java':'package top.rongshangs.lumacurve.refactor;class RootWorkspace{static org.json.JSONObject report(){return new org.json.JSONObject();}}',
P+'RootCommand.java':'''package top.rongshangs.lumacurve.refactor;import java.io.*;import java.util.*;class RootCommand{static boolean cmd=true,cli=true,denyWrite,mismatch;static int calls,writes;static String output;static String[] last;static Map<String,String> map=new HashMap<>();static String run(long timeout,String...a)throws Exception{last=a;calls++;if(a[0].endsWith("cmd")&&!cmd||a[0].endsWith("settings")&&!cli)throw new IOException("unavailable");int i=a[0].endsWith("cmd")?4:3;String op=a[i],key=a[i+2];if(op.equals("get"))return output!=null?output:map.containsKey(key)?map.get(key):"null";writes++;if(denyWrite)throw new IOException("Permission denied");if(op.equals("delete")){map.remove(key);return "Deleted 1 rows";}if(!mismatch)map.put(key,a[i+3]);return "";}}''',
P+'RootAdapterTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.io.*;import java.util.*;import android.app.ActivityManager;import android.os.Process;
public class RootAdapterTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("case "+cases);cases++;}
 static void reset(){ActivityManager.manager=new ActivityManager.Manager();Process.uid=0;RootCommand.cmd=RootCommand.cli=true;RootCommand.denyWrite=RootCommand.mismatch=false;RootCommand.output=null;RootCommand.calls=RootCommand.writes=0;RootCommand.map.clear();}
 static void fails(RunnableThrows action)throws Exception{try{action.run();throw new AssertionError("accepted");}catch(IOException|SecurityException expected){cases++;}}
 interface RunnableThrows{void run()throws Exception;}
 public static void main(String[] args)throws Exception{
  reset();RootSettings s=new RootSettings();check(s.available()&&s.report().getString("route").equals("external_provider"));check(RootCommand.calls==0&&ActivityManager.manager.puts==0&&!s.report().getBoolean("write_confirmed"));
  check(s.put("config","{\"unknown\":true}"));check(s.get("config").equals("{\"unknown\":true}"));check(s.report().getBoolean("write_confirmed"));check(ActivityManager.manager.puts==1);check(s.put("config",null)&&s.get("config")==null);s.close();check(ActivityManager.manager.closed==1);
  for(int uid:new int[]{0,1000,2000,12345}){reset();Process.uid=uid;s=new RootSettings();check(ActivityManager.manager.uid==uid);check(ActivityManager.manager.pkg.equals(uid==0||uid==1000?"android":uid==2000?"com.android.shell":"top.rongshangs.lumacurve"));}
  reset();ActivityManager.manager.nullGet=true;s=new RootSettings();check(s.available()&&s.get("config")==null);check(s.put("absent",null));check(RootCommand.calls==0);
  reset();ActivityManager.manager.denyWrite=true;final RootSettings primary=new RootSettings();fails(()->primary.put("config","value"));check(RootCommand.calls==0&&ActivityManager.manager.puts==1);check(!primary.report().getBoolean("write_confirmed"));
  reset();ActivityManager.manager.denyGet=true;s=new RootSettings();check(s.report().getString("route").equals("cmd_settings")&&s.attempts.length()==2);check(RootCommand.writes==0);check(s.put("odd' key","value ' \" $()\nline"));check(s.get("odd' key").contains("$()\nline"));check(s.put("odd' key",null));
  RootCommand.output="{\"error\":\"Permission denied in saved log\"}";check(s.get("config").contains("Permission denied"));RootCommand.output=null;
  for(String message:new String[]{"Error: denied","java.lang.SecurityException: denied","Permission denial: no","Unknown command: settings","Usage: settings","Exception: denied"}){RootCommand.output=message;final RootSettings selected=s;fails(()->selected.get("config"));}RootCommand.output=null;
  RootCommand.denyWrite=true;final RootSettings cmd=s;int before=RootCommand.calls;fails(()->cmd.put("config","x"));check(RootCommand.calls==before+1);check(cmd.report().getString("route").equals("cmd_settings"));
  reset();ActivityManager.manager.denyGet=true;RootCommand.cmd=false;s=new RootSettings();check(s.report().getString("route").equals("settings_cli")&&s.attempts.length()==3);check(s.putSystem("screen_brightness_mode","1"));check(Arrays.equals(RootCommand.last,new String[]{"/system/bin/settings","--user","0","get","system","screen_brightness_mode"}));
  RootCommand.mismatch=true;check(!s.put("config","not saved"));check(s.get("config")==null);
  reset();ActivityManager.manager.denyGet=true;RootCommand.cmd=RootCommand.cli=false;s=new RootSettings();check(!s.available()&&s.attempts.length()==3);final RootSettings none=s;fails(()->none.get("config"));check(RootCommand.writes==0);check(none.report().getString("route").isEmpty());
  // A launched but unauthorized su must not trigger another authorization path.
  int[] attempts={0};java.lang.Process fake=new ProcessBuilder("java","-version").start();final java.lang.Process initial=fake;check(RootLaunch.open("/data/app/odd' file/base.apk",a->{attempts[0]++;check(a.length==3&&a[1].equals("-c"));check(a[2].contains("odd'\\'' file"));return initial;})==fake);check(attempts[0]==1);fake.waitFor();
  attempts[0]=0;fake=RootLaunch.open("/data/app/base.apk",a->{attempts[0]++;if(attempts[0]<3)throw new IOException("error=2");return new ProcessBuilder("java","-version").start();});check(attempts[0]==3);fake.waitFor();
  attempts[0]=0;fails(()->RootLaunch.open("/data/app/base.apk",a->{attempts[0]++;throw new IOException("error=13 Permission denied");}));check(attempts[0]==1);
  attempts[0]=0;fails(()->RootLaunch.open("/data/app/base.apk",a->{attempts[0]++;throw new IOException("ENOENT");}));check(attempts[0]==6);
  for(String bad:new String[]{null,"relative.apk","/data/a\n.apk","/data/a\0.apk"})fails(()->RootLaunch.open(bad,a->{throw new AssertionError("started");}));
  for(Throwable failure:new Throwable[]{new SecurityException("blocked"),new java.nio.file.AccessDeniedException("proc"),new IOException("Permission denied"),new IOException("EPERM: Operation not permitted"),new IOException("wrapped",new SecurityException("inner"))})check(RootAccess.denied(failure));
  check(!RootAccess.denied(new IOException("No such file")));check(!RootAccess.denied(new IOException("node changed")));check(RootAccess.report(none).getInt("uid")==0);
  System.out.println("Root adapter host: "+cases+" cases PASS");
 }
}''',
}

def run_java(name,production,fixtures,folder):
    dest=O/folder;dest.mkdir(exist_ok=True)
    paths=[]
    for n,t in fixtures.items():
        p=dest/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(t,'utf-8');paths.append(p)
    classes=dest/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/n) for n in production],*map(str,paths)],check=True)
    output=subprocess.check_output(['java','-Dfile.encoding=UTF-8','-Dsun.stdout.encoding=UTF-8','-cp',str(classes)+os.pathsep+str(J),P.replace('/','.')+name],cwd=R).decode('utf-8')
    print(output.strip());return int(re.search(r'(\d+) cases PASS',output)[1])

total=run_java('RootAdapterTest',['RootSettings.java','RootLaunch.java','RootAccess.java'],files,'adapters')
# Run the real bounded transport, including noisy child, timeout, and large output.
command={P+'CommandTest.java':r'''package top.rongshangs.lumacurve.refactor;import java.io.*;
public class CommandTest{static int cases;static void check(boolean v){if(!v)throw new AssertionError("case "+cases);cases++;}
 static String[] cmd(String mode){return new String[]{"java","-cp",System.getProperty("java.class.path"),"top.rongshangs.lumacurve.refactor.CommandTest",mode};}
 public static void main(String[] args)throws Exception{if(args.length>0){if(args[0].equals("normal")){System.out.print("hello");return;}if(args[0].equals("exit")){System.out.print("refused");System.exit(3);}if(args[0].equals("wait")){Thread.sleep(3000);return;}if(args[0].equals("large")){byte[] data=new byte[1024*1024];for(int i=0;i<10;i++)System.out.write(data);return;}}
  check(RootCommand.run(5000,cmd("normal")).equals("hello"));
  for(String mode:new String[]{"exit","wait","large"}){long before=System.currentTimeMillis();try{RootCommand.run(mode.equals("wait")?100:5000,cmd(mode));throw new AssertionError("accepted "+mode);}catch(IOException expected){check(mode.equals("exit")?expected.getMessage().contains("refused"):true);}check(System.currentTimeMillis()-before<7000);}
  System.out.println("Root command host: "+cases+" cases PASS");}}
'''}
total+=run_java('CommandTest',['RootCommand.java'],command,'command')

workspace={
'android/os/Process.java':files['android/os/Process.java'],
'android/system/StructStat.java':'package android.system;public class StructStat{public int st_uid,st_mode=0040700;}',
'android/system/ErrnoException.java':'package android.system;public class ErrnoException extends Exception{public int errno;public ErrnoException(int e){errno=e;}}',
'android/system/OsConstants.java':'package android.system;public class OsConstants{public static int EEXIST=17;public static boolean S_ISDIR(int mode){return (mode&0170000)==0040000;}}',
'android/system/Os.java':r'''package android.system;import java.io.*;import java.util.*;
public class Os{public static int mkdirs,chmods;public static String denied="",wrongOwner="",unsafe="",shared="";public static StructStat lstat(String p)throws Exception{if(p.equals(denied))throw new IOException("Permission denied");if(!new File(p).exists())throw new IOException("ENOENT");StructStat s=new StructStat();s.st_uid=p.equals(wrongOwner)?1000:0;s.st_mode=p.equals(unsafe)?0040777:p.equals(shared)?0040755:0040700;return s;}public static void mkdir(String p,int mode)throws Exception{mkdirs++;if(!new File(p).mkdir())throw new ErrnoException(17);}public static void chmod(String p,int mode){chmods++;shared="";}}''',
P+'WorkspaceTest.java':r'''package top.rongshangs.lumacurve.refactor;import java.io.*;import android.system.*;import org.json.*;
public class WorkspaceTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void failure(RunnableThrows r)throws Exception{try{r.run();throw new AssertionError("accepted");}catch(IOException expected){cases++;}}
 interface RunnableThrows{void run()throws Exception;}
 public static void main(String[] a)throws Exception{
  RootWorkspace.PRIMARY.getParentFile().mkdirs();RootWorkspace.temporary().getParentFile().mkdirs();
  check(RootWorkspace.require().equals(RootWorkspace.PRIMARY));check(Os.mkdirs==1);check(RootWorkspace.report().getBoolean("selected_usable"));check(Os.mkdirs==1&&Os.chmods==0);
  RootWorkspace.selected=null;Os.denied=RootWorkspace.PRIMARY.getParent();check(RootWorkspace.require().equals(RootWorkspace.temporary()));check(Os.mkdirs==2);failure(()->RootWorkspace.requireNative());check(Os.mkdirs==2);check(RootWorkspace.report().getBoolean("selected_usable"));
  Os.wrongOwner=RootWorkspace.temporary().toString();check(!RootWorkspace.report().getBoolean("selected_usable"));check(!RootWorkspace.report().getString("selected_error").isEmpty());failure(()->RootWorkspace.require());check(Os.chmods==0);
  RootWorkspace.selected=null;Os.wrongOwner="";Os.unsafe=RootWorkspace.temporary().getParent();failure(()->RootWorkspace.require());check(Os.mkdirs==2);RootWorkspace.initialize();check(RootWorkspace.report().getString("selected").isEmpty());check(Os.mkdirs==2);
  Os.denied="";Os.unsafe="";Os.wrongOwner=RootWorkspace.PRIMARY.toString();failure(()->RootWorkspace.requireNative());check(Os.chmods==0);
  Os.wrongOwner="";Os.shared=RootWorkspace.PRIMARY.toString();failure(()->RootWorkspace.secure(RootWorkspace.PRIMARY,false));check(Os.chmods==0);RootWorkspace.secure(RootWorkspace.PRIMARY,true);check(Os.chmods==1);
  android.os.Process.uid=1000;failure(()->RootWorkspace.requireNative());check(Os.chmods==1);
  System.out.println("Root workspace host: "+cases+" cases PASS");
 }}'''}
# Android fixed directories are relocated only in this host fixture; checks stay production.
workspace_source=(S/'RootWorkspace.java').read_text('utf-8')
unique=O/('workspace-fixture-'+str(__import__('time').time_ns()))
primary=unique/'adb'/'root';alternate=unique/'local'/'root'
workspace_source=workspace_source.replace('new File("/data/adb/luma_curve_refactor_test")','new File('+json.dumps(str(primary))+')').replace('new File("/data/local/.hyperlux-root-"+android.os.Process.myUid())','new File('+json.dumps(str(alternate))+')')
workspace[P+'RootWorkspace.java']=workspace_source
total+=run_java('WorkspaceTest',[],workspace,'workspace')

diagnostics={
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 1234;}}',
'android/os/Build.java':files['android/os/Build.java'],
'android/net/Uri.java':'package android.net;public class Uri{public String toString(){return "content://downloads/1";}}',
'android/content/ContentValues.java':'''package android.content;public class ContentValues{public java.util.Map<String,Object> v=new java.util.HashMap<>();public void put(String k,String s){v.put(k,s);}public void put(String k,Integer n){v.put(k,n);}}''',
'android/content/ContentResolver.java':'''package android.content;import android.net.Uri;import java.io.*;public class ContentResolver{public boolean failOpen,failUpdate;public int deleted;public ContentValues inserted,updated;public ByteArrayOutputStream bytes=new ByteArrayOutputStream();public Uri insert(Uri u,ContentValues v){inserted=v;return u;}public OutputStream openOutputStream(Uri u,String m)throws IOException{if(failOpen)throw new IOException("denied");return bytes;}public int update(Uri u,ContentValues v,String s,String[] a){updated=v;return failUpdate?0:1;}public int delete(Uri u,String s,String[] a){deleted++;return 1;}}''',
'android/content/Context.java':'package android.content;public class Context{public ContentResolver resolver=new ContentResolver();public ContentResolver getContentResolver(){return resolver;}}',
'android/provider/MediaStore.java':'package android.provider;public class MediaStore{public static class Downloads{public static android.net.Uri EXTERNAL_CONTENT_URI=new android.net.Uri();}public static class MediaColumns{public static String DISPLAY_NAME="name",MIME_TYPE="mime",RELATIVE_PATH="path",IS_PENDING="pending";}}',
P+'DiagnosticsTest.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import android.content.*;import java.io.*;import java.util.zip.*;import java.nio.charset.StandardCharsets;
public class DiagnosticsTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject unpack(byte[] bytes)throws Exception{try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){check(zip.getNextEntry().getName().equals("app-root-diagnostics.json"));ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] data=new byte[1024];int n;while((n=zip.read(data))>0)b.write(data,0,n);JSONObject out=new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8));check(zip.getNextEntry().getName().equals("analysis-readme.txt"));check(zip.read(data)>0);check(zip.getNextEntry()==null);return out;}}
 public static void main(String[] args)throws Exception{JSONObject previous=new JSONObject().put("ok",true).put("config",new JSONObject().put("unchanged",true));String original=previous.toString();JSONObject report=unpack(AppRootDiagnostics.archive(previous,new IOException("Permission denied")));check(report.getBoolean("partial_root_report")&&report.getString("root_error").contains("Permission denied"));check(previous.toString().equals(original));check(report.getJSONObject("last_known_state").getJSONObject("config").getBoolean("unchanged"));check(!report.has("runtime"));check(!unpack(AppRootDiagnostics.archive(null,new IOException("missing"))).has("last_known_state"));
 JSONObject unavailable=AppRootDiagnostics.unavailable(new IOException("cannot start"));check(unavailable.getString("mode").equals("app_observed_failure"));check(unavailable.getJSONArray("features").length()==8);for(int i=0;i<8;i++)check(unavailable.getJSONArray("features").getJSONObject(i).getString("state").equals("pending"));
 Context context=new Context();JSONObject result=AppRootDiagnostics.export(context,previous,new IOException("denied"));check(result.getBoolean("ok")&&result.getBoolean("partial_root_report"));check(result.getString("path").startsWith("Download/HyperLux/"));check(context.resolver.inserted.v.get("pending").equals(1)&&context.resolver.updated.v.get("pending").equals(0));check(context.resolver.deleted==0);check(unpack(context.resolver.bytes.toByteArray()).getBoolean("partial_root_report"));
 for(boolean open:new boolean[]{true,false}){Context bad=new Context();bad.resolver.failOpen=open;bad.resolver.failUpdate=!open;try{AppRootDiagnostics.export(bad,null,new IOException("denied"));throw new AssertionError("accepted");}catch(IOException expected){cases++;}check(bad.resolver.deleted==1);}
 System.out.println("APP partial diagnostics host: "+cases+" cases PASS");}}
'''}
total+=run_java('DiagnosticsTest',['AppRootDiagnostics.java','AppBuild.java','FeatureSelfCheck.java'],diagnostics,'diagnostics')

independent={
'android/os/SystemClock.java':diagnostics['android/os/SystemClock.java'],
P+'Doubles.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.io.*;
class RootSettings{boolean readable;boolean available(){return readable;}}
class RootWorkspace{static File PRIMARY=new File("/original");}
class RootAccess{static JSONObject report(RootSettings s){return new JSONObject().put("uid",0).put("workspace",new JSONObject().put("selected","/alternative").put("selected_usable",false).put("selection_error","Permission denied")).put("settings",new JSONObject().put("route",s.readable?"cmd_settings":"").put("write_confirmed",false).put("routes",new JSONArray().put(new JSONObject().put("id","external_provider").put("readable",false).put("detail","provider denied"))));}}
class ActivePanelNode{static int calls;static JSONObject discover(){calls++;return new JSONObject().put("supported",true);}}
class NativePanelRoot{static void probe(JSONObject n)throws Exception{throw new IOException("node denied");}}
class OutdoorNodeRoot{static int calls;static boolean supported(JSONObject n){calls++;return false;}}
class NativePanelAsset{static int calls;static byte[] read(File f){calls++;return new byte[0];}}
''',
P+'RootFeatureTest.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;
public class RootFeatureTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static JSONObject find(JSONObject r,String id){JSONArray a=r.getJSONArray("features");for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);throw new AssertionError(id);}
 public static void main(String[] args)throws Exception{
  RootSettings settings=new RootSettings();JSONObject r=RootFeatureChecks.run(null,null,settings);check(r.getJSONArray("features").length()==8);check(find(r,"root_settings").getString("state").equals("pending"));check(find(r,"root_identity").getString("state").equals("available"));check(find(r,"stored_config").getString("state").equals("pending"));check(find(r,"main_panel_node").getString("state").equals("pending"));check(find(r,"main_panel_node").getString("detail").contains("node denied"));check(ActivePanelNode.calls==1&&OutdoorNodeRoot.calls==1&&NativePanelAsset.calls==1);check(find(r,"root_storage").getString("state").equals("pending"));check(find(r,"root_storage").getString("detail").contains("Permission denied"));
  settings.readable=true;r=RootFeatureChecks.run(new JSONObject(),"{}",settings);check(find(r,"root_settings").getString("state").equals("available"));check(find(r,"root_settings").getString("settings_route").equals("cmd_settings"));check(find(r,"root_settings").getString("detail").contains("自检不试写"));check(find(r,"root_settings").getJSONArray("settings_attempts").length()==1);check(find(r,"root_process_identity").getString("state").equals("available"));check(find(r,"stored_config").getString("state").equals("available"));check(ActivePanelNode.calls==2&&OutdoorNodeRoot.calls==2&&NativePanelAsset.calls==2);
  r=RootFeatureChecks.run(new JSONObject(),"invalid-json",settings);check(find(r,"stored_config").getString("state").equals("pending"));check(find(r,"root_settings").getString("state").equals("available"));check(NativePanelAsset.calls==3);System.out.println("Root independent checks host: "+cases+" cases PASS");
 }}'''}
old_classpath=os.environ.get('CLASSPATH');os.environ['CLASSPATH']='fixture-only.apk'
total+=run_java('RootFeatureTest',['RootFeatureChecks.java','FeatureSelfCheck.java'],independent,'independent')
if old_classpath is None:os.environ.pop('CLASSPATH',None)
else:os.environ['CLASSPATH']=old_classpath


config_test={P+'ConfigRootTest.java':r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.io.*;
+public class ConfigRootTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
+ public static void main(String[] args)throws Exception{
+  JSONObject stored=new JSONObject().put("fingerprint","maker/device:old").put("curve_backend","physical_mapping").put("baseline_id","same").put("user_serial",0).put("outdoor_enabled",true).put("outdoor_range_unlock",true).put("outdoor_opr_relax",true).put("outdoor_node_boost",true).put("outdoor_node_target",5000);
+  JSONObject caps=new JSONObject().put("curve_backend","physical_mapping").put("baseline_id","same").put("user_serial",0).put("outdoor_node_supported",false);JSONObject proposed=new JSONObject(stored.toString());String old=stored.toString(),draft=proposed.toString();
+  JSONObject validation=ConfigurationCompatibility.submissionValidation(proposed,caps,stored,"maker/device:new");check(!validation.getBoolean("outdoor_node_boost"));check(stored.toString().equals(old)&&proposed.toString().equals(draft));check(proposed.getBoolean("outdoor_node_boost")&&proposed.getInt("outdoor_node_target")==5000);
+  for(JSONObject invalid:new JSONObject[]{null,new JSONObject(stored.toString()).put("baseline_id","other"),new JSONObject(stored.toString()).put("outdoor_node_boost",false)})check(ConfigurationCompatibility.submissionValidation(proposed,caps,invalid,"maker/device:new").getBoolean("outdoor_node_boost"));
+  check(ConfigurationCompatibility.submissionValidation(new JSONObject(draft).put("outdoor_strength",.5),caps,stored,"maker/device:new").getBoolean("outdoor_node_boost"));check(ConfigurationCompatibility.submissionValidation(proposed,new JSONObject(caps.toString()).put("outdoor_node_supported",true),stored,"maker/device:new").getBoolean("outdoor_node_boost"));
+  for(int mask=0;mask<4;mask++){OutdoorNodeRoot.fail=(mask&1)!=0;ControlCenterRoot.fail=(mask&2)!=0;OutdoorNodeRoot.calls=ControlCenterRoot.calls=0;RootControl root=new RootControl();JSONObject result=root.afterApply(proposed);check(result.getBoolean("ok"));check(OutdoorNodeRoot.calls==1&&ControlCenterRoot.calls==1);check(result.getString("root_warning").isEmpty()==(mask==0));check(stored.toString().equals(old)&&proposed.toString().equals(draft));}
+  System.out.println("Core/root independence host: "+cases+" cases PASS");
+ }}
+class OutdoorNodeRoot{static int calls;static boolean fail;static void reconcileAfterApply(JSONObject s,String raw)throws Exception{calls++;if(fail)throw new IOException("node denied");}}
+class ControlCenterRoot{static int calls;static boolean fail;static void reconcile(Object settings,JSONObject s,String raw)throws Exception{calls++;if(fail)throw new IOException("profile denied");}}
+class RootControl{Object settings;JSONObject live(){return new JSONObject();}AFTER_APPLY}
+'''.replace('\n+','\n')}
source=(S/'RootControl.java').read_text('utf-8');start=source.index('    JSONObject afterApply(');end=source.index('    JSONObject stop()',start)
config_test[P+'ConfigRootTest.java']=config_test[P+'ConfigRootTest.java'].replace('AFTER_APPLY',source[start:end])
total+=run_java('ConfigRootTest',['BrightnessControlOptions.java','ConfigurationCompatibility.java','MemoryOptions.java','OutdoorOptions.java','OutdoorNodeTarget.java','AdvancedPolicy.java'],config_test,'configuration')

# Production call sites retain independent optional-tool failures and no write on probe.
control=(S/'RootControl.java').read_text('utf-8')
assert 'if(android.os.Process.myUid()!=0)' not in control
assert 'RootWorkspace.initialize();' in control
assert 'root_settings_unavailable' in control and 'root_process_visibility' in control
assert 'RootAccess.report(settings)' in control
assert 'root_warning' in control and control.index('if(!waitFor(revision,true))')<control.index('return afterApply(config);')
assert 'if(requireFresh||selfCheckNext)throw denied;return state;' in control
assert 'RootWorkspace.requireNative();' in (S/'NativePanelRoot.java').read_text('utf-8')
assert 'AppRootDiagnostics.export(this,lastAnswer,rootFailure)' in (S/'MainActivity.java').read_text('utf-8')
assert 'if(!selfCheckNext){' in control
total+=9
print(f'Root access total: {total} cases PASS; service doubles and host processes, no target-device validation')
