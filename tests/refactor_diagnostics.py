"""Host-check the production JSON feed parser and bounded collector with real org.json."""
from pathlib import Path
import hashlib,subprocess,urllib.request
R=Path(__file__).resolve().parents[1];src=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';out=R/'build/refactor-diagnostics';out.mkdir(exist_ok=True)
jar=out/'json-20240303.jar'
if not jar.exists():
    with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=30) as response:jar.write_bytes(response.read())
# Download is used for host verification only, never packaged into the APK.
expected='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
assert hashlib.sha256(jar.read_bytes()).hexdigest()==expected,'host JSON dependency changed'
stub='''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.zip.*;
final class RootControl {
 static final File DATA=new File(System.getProperty("java.io.tmpdir"));
 static void progress(String s){}
 static void write(ZipOutputStream z,String n,String s)throws IOException{z.putNextEntry(new ZipEntry(n));z.write(s.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}
'''
test='''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;import org.json.*;
public final class DiagnosticHostTest {
 static int cases;static void check(boolean b)throws Exception{if(!b)throw new AssertionError();cases++;}
 static String read(ZipFile zip,String name)throws Exception{try(InputStream in=zip.getInputStream(zip.getEntry(name));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),"UTF-8").trim();}}
 static void invalid(String text)throws Exception{try{ThanksFeed.parse(text);throw new AssertionError("invalid feed accepted");}catch(Exception expected){cases++;}}
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("sleep")){System.out.println("partial-before-timeout");System.out.flush();Thread.sleep(30000);return;}
  if(args.length>0&&args[0].equals("failed")){System.out.println("failure-details");System.exit(3);return;}
  if(args.length>0&&args[0].equals("large")){byte[] b=new byte[2*1024*1024+4096];Arrays.fill(b,(byte)'x');System.out.write(b);System.out.print("LATEST-FAULT-MARKER");return;}
  String actual=new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8");JSONObject feed=ThanksFeed.parse(actual);check(feed.getJSONArray("entries").length()==new JSONObject(actual).getJSONArray("entries").length());
  check(ThanksFeed.parse("{\\"schema\\":1,\\"entries\\":[{\\"name\\":\\"Albert_L\\",\\"message\\":\\"能露点什么呢🤔\\"}]}").getJSONArray("entries").getJSONObject(0).getString("message").contains("🤔"));
  invalid("{\\"schema\\":2,\\"entries\\":[]}");invalid("{\\"schema\\":1,\\"entries\\":[{\\"name\\":\\"\\",\\"message\\":\\"x\\"}]}");
  invalid("{\\"schema\\":1,\\"entries\\":[{\\"name\\":7,\\"message\\":\\"x\\"}]}");invalid("x");invalid(null);
  invalid("{\\"schema\\":1,\\"entries\\":[{\\"name\\":\\"x\\",\\"message\\":\\"x\\\\ny\\"}]}");
  char[] longText=new char[65537];Arrays.fill(longText,'x');invalid(new String(longText));
  JSONArray many=new JSONArray();for(int i=0;i<201;i++)many.put(new JSONObject().put("name","x").put("message","y"));invalid(new JSONObject().put("schema",1).put("entries",many).toString());
  check(ThanksFeed.parse("{\\"schema\\":1,\\"entries\\":[]}").getJSONArray("entries").length()==0);
  File dir=Files.createTempDirectory("hyperlux-diagnostics-").toFile(),archive=new File(dir,"bundle.zip"),file=new File(dir,"sample.txt"),large=new File(dir,"large.bin");
  Files.write(file.toPath(),"sample-content".getBytes("UTF-8"));try(RandomAccessFile huge=new RandomAccessFile(large,"rw")){huge.setLength(DiagnosticCollector.FILE_LIMIT+1);}
  DiagnosticCollector c;String cp=System.getProperty("java.class.path");String java=new File(System.getProperty("java.home"),"bin/java").toString();
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(archive))){
   c=new DiagnosticCollector(z);c.copy(file,"sample.txt");c.copy(large,"large.bin");c.copy(new File(dir,"missing"),"missing.txt");
   c.command("ok.txt",5,java,"-version");c.command("failed.txt",5,java,"-cp",cp,DiagnosticHostTest.class.getName(),"failed");
   c.command("timeout.txt",1,java,"-cp",cp,DiagnosticHostTest.class.getName(),"sleep");c.command("missing-command.txt",1,new File(dir,"no-executable").toString());
   RootControl.write(z,"manifest.json",c.manifest.toString());
  }
  try(ZipFile z=new ZipFile(archive)){
   check(z.getEntry("large.bin")==null&&z.getEntry("missing.txt")==null);check(z.getEntry("sample.txt").getSize()==14);
   check(z.getEntry("ok.txt")!=null);check(z.getEntry("timeout.txt").getSize()>0);check(z.getEntry("failed.txt").getSize()>0);
   check(c.manifest.getJSONObject(0).getString("sha256").length()==64);
   check(c.manifest.getJSONObject(1).getString("status").equals("skipped_limit"));check(c.manifest.getJSONObject(2).getString("status").equals("unavailable"));
   check(c.manifest.getJSONObject(4).getString("status").equals("nonzero"));check(c.manifest.getJSONObject(5).getString("status").equals("timeout"));check(c.manifest.getJSONObject(6).getString("status").equals("unavailable"));
  }
  // Oversized command output is bounded; a timed-out collection never starts fault commands.
  File faultArchive=new File(dir,"faults.zip");
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(faultArchive))){
   DiagnosticCollector bounded=new DiagnosticCollector(z);bounded.command("large-output.txt",5,java,"-cp",cp,DiagnosticHostTest.class.getName(),"large");
   check(bounded.manifest.getJSONObject(0).getString("status").equals("truncated"));
  }
  try(ZipFile z=new ZipFile(faultArchive)){check(z.getEntry("large-output.txt").getSize()==2*1024*1024);}
  // Latest fault evidence must survive truncation; non-fault snapshots keep their head.
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(faultArchive))){
   DiagnosticCollector bounded=new DiagnosticCollector(z);bounded.command("faults/large-output.txt",5,java,"-cp",cp,DiagnosticHostTest.class.getName(),"large");
   check(bounded.manifest.getJSONObject(0).getString("detail").contains("retained=tail"));
  }
  try(ZipFile z=new ZipFile(faultArchive)){check(z.getEntry("faults/large-output.txt").getSize()==2*1024*1024);check(read(z,"faults/large-output.txt").endsWith("LATEST-FAULT-MARKER"));}
  // AOSP dump searches AND-combined exact date terms. A lower-bound date would hide today.
  for(String tag:DiagnosticCollector.FAULT_TAGS){String[] command=DiagnosticCollector.dropboxArguments(tag);check(Arrays.equals(command,new String[]{"dumpsys","-t","4","dropbox","--print",tag}));}
  boolean invalidTag=false;try{DiagnosticCollector.dropboxArguments("system_server_watchdog --erase");}catch(IllegalArgumentException expected){invalidTag=true;}check(invalidTag);
  File faultInput=new File(dir,"fault-input");check(faultInput.mkdir());long now=System.currentTimeMillis();
  for(String name:new String[]{"anr_new","anr_older","anr_expired","unrelated"}){File f=new File(faultInput,name);Files.write(f.toPath(),name.getBytes("UTF-8"));check(f.setLastModified(now-(name.equals("anr_expired")?4L*86400000:name.equals("anr_older")?2000:1000)));}
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(faultArchive))){DiagnosticCollector recent=new DiagnosticCollector(z);recent.recentFaultFiles(faultInput,"faults/anr",new String[]{"anr_"},1,now);check(recent.manifest.getJSONObject(1).getString("status").equals("skipped_limit"));}
  try(ZipFile z=new ZipFile(faultArchive)){check(read(z,"faults/anr/anr_new").equals("anr_new"));check(z.size()==1);}
  File oversized=new File(faultInput,"anr_oversized");try(RandomAccessFile huge=new RandomAccessFile(oversized,"rw")){huge.setLength(8L*1024*1024+1);}check(oversized.setLastModified(now));
  File gz=new File(faultInput,"system_server_watchdog@fixture.txt.gz");try(GZIPOutputStream g=new GZIPOutputStream(new FileOutputStream(gz))){g.write("WATCHDOG-STACK".getBytes("UTF-8"));}check(gz.setLastModified(now));
  byte[] compressed=Files.readAllBytes(gz.toPath());
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(faultArchive))){DiagnosticCollector recent=new DiagnosticCollector(z);recent.recentFaultFiles(faultInput,"faults/anr",new String[]{"anr_"},6,now);check(recent.manifest.getJSONObject(0).getString("status").equals("skipped_limit"));recent.recentFaultFiles(faultInput,"faults/dropbox-files",new String[]{"system_server_watchdog@"},10,now);recent.recentFaultFiles(new File(dir,"absent"),"faults/absent",new String[]{"anr_"},6,now);check(recent.manifest.getJSONObject(recent.manifest.length()-1).getString("status").equals("unavailable"));}
  try(ZipFile z=new ZipFile(faultArchive)){check(z.getEntry("faults/anr/anr_oversized")==null);check(z.getEntry("faults/anr/anr_expired")==null);check(z.getEntry("faults/anr/unrelated")==null);try(InputStream in=z.getInputStream(z.getEntry("faults/dropbox-files/"+gz.getName()))){byte[] archived=new byte[compressed.length];new DataInputStream(in).readFully(archived);check(in.read()==-1&&Arrays.equals(archived,compressed));}}
  try(ZipOutputStream z=new ZipOutputStream(new ByteArrayOutputStream())){DiagnosticCollector expired=new DiagnosticCollector(z,1);Thread.sleep(10);expired.recentFaultFiles(faultInput,"faults/anr",new String[]{"anr_"},6,now);check(expired.manifest.getJSONObject(0).getString("status").equals("skipped_budget"));}
  for(String name:new String[]{"watchdog_pid_123.txt","pre_watchdog_pid_123.txt","BinderTraces_pid123.txt","system_sever_123.hprof"})Files.write(new File(faultInput,name).toPath(),name.getBytes("UTF-8"));
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(faultArchive))){DiagnosticCollector recent=new DiagnosticCollector(z);recent.recentFaultFiles(faultInput,"faults/miui-watchdog",DiagnosticCollector.MIUI_WATCHDOG_PREFIXES,6,now);recent.recentFaultFiles(faultInput,"faults/binder",new String[]{DiagnosticCollector.ANR_PREFIXES[2]},6,now);}
  try(ZipFile z=new ZipFile(faultArchive)){check(z.size()==3);check(read(z,"faults/miui-watchdog/watchdog_pid_123.txt").equals("watchdog_pid_123.txt"));check(read(z,"faults/miui-watchdog/pre_watchdog_pid_123.txt").equals("pre_watchdog_pid_123.txt"));check(read(z,"faults/binder/BinderTraces_pid123.txt").equals("BinderTraces_pid123.txt"));check(z.getEntry("faults/miui-watchdog/system_sever_123.hprof")==null);}
  for(File f:faultInput.listFiles())Files.delete(f.toPath());Files.delete(faultInput.toPath());
  try(ZipOutputStream z=new ZipOutputStream(new ByteArrayOutputStream())){
   DiagnosticCollector expired=new DiagnosticCollector(z,1);Thread.sleep(10);expired.collectFaults();
   check(expired.manifest.length()==8);
   for(int i=0;i<expired.manifest.length();i++)check(expired.manifest.getJSONObject(i).getString("status").equals("skipped_budget"));
  }
  // Zip write errors propagate instead of being reported as successful diagnostics.
  ZipOutputStream closed=new ZipOutputStream(new ByteArrayOutputStream());closed.close();boolean threw=false;try{new DiagnosticCollector(closed).command("disk-full.txt",5,java,"-version");}catch(IOException expected){threw=true;}check(threw);
  // Overall budget skips later commands/files without corrupting the archive.
  try(ZipOutputStream z=new ZipOutputStream(new ByteArrayOutputStream())){DiagnosticCollector budget=new DiagnosticCollector(z,1);Thread.sleep(10);budget.command("budget-command.txt",5,java,"-version");budget.copy(file,"budget-file.txt");check(budget.manifest.length()==2);check(budget.manifest.getJSONObject(0).getString("status").equals("skipped_budget"));check(budget.manifest.getJSONObject(1).getString("status").equals("skipped_budget"));}
  // Direct-provider export preserves numeric zero and fractional adjustment, without shell parsing.
  File settingsArchive=new File(dir,"settings.zip");int[] calls={0};
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(settingsArchive))){
   DiagnosticCollector direct=new DiagnosticCollector(z,key->{calls[0]++;return key.equals("screen_brightness_mode")?"0":key.equals("screen_brightness")?"179":"0.0008";});direct.collectSettings();
   check(calls[0]==3);check(direct.manifest.length()==4);check(direct.manifest.getJSONObject(0).getString("status").equals("ok"));
  }
  try(ZipFile z=new ZipFile(settingsArchive)){
   check(read(z,"settings/screen_brightness_mode.txt").equals("0"));check(read(z,"settings/screen_brightness.txt").equals("179"));check(read(z,"settings/screen_auto_brightness_adj.txt").equals("0.0008"));
   JSONObject snapshot=new JSONObject(read(z,"settings/system.json"));check(snapshot.getString("source").equals("SettingsProvider"));check(snapshot.getString("namespace").equals("system"));check(snapshot.getInt("user")==0);check(snapshot.getLong("collected_unix_ms")>0);
   check(snapshot.getJSONObject("entries").getJSONObject("screen_brightness_mode").getString("value").equals("0"));
  }
  // Missing keys and provider failures must remain distinct, and one failure cannot suppress other reads.
  try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(settingsArchive))){
   DiagnosticCollector direct=new DiagnosticCollector(z,key->{if(key.equals("screen_brightness_mode"))throw new IOException("Failed transaction (2147483646)");return key.equals("screen_brightness")?null:"0";});direct.collectSettings();
   check(direct.manifest.getJSONObject(0).getString("status").equals("error"));check(direct.manifest.getJSONObject(1).getString("status").equals("missing"));check(direct.manifest.getJSONObject(2).getString("status").equals("ok"));
  }
  try(ZipFile z=new ZipFile(settingsArchive)){
   check(read(z,"settings/screen_brightness_mode.txt").startsWith("[error]"));check(read(z,"settings/screen_brightness.txt").startsWith("[missing]"));check(read(z,"settings/screen_auto_brightness_adj.txt").equals("0"));
   JSONObject entries=new JSONObject(read(z,"settings/system.json")).getJSONObject("entries");check(entries.getJSONObject("screen_brightness_mode").isNull("value"));check(entries.getJSONObject("screen_brightness").isNull("value"));check(entries.getJSONObject("screen_brightness_mode").getString("error").contains("Failed transaction"));
  }
  try(ZipOutputStream z=new ZipOutputStream(new ByteArrayOutputStream())){int[] reads={0};DiagnosticCollector budget=new DiagnosticCollector(z,key->{reads[0]++;return "1";},1);Thread.sleep(10);budget.collectSettings();check(reads[0]==0);check(budget.manifest.getJSONObject(0).getString("status").equals("skipped_budget"));}
  ZipOutputStream closedSettings=new ZipOutputStream(new ByteArrayOutputStream());closedSettings.close();threw=false;try{new DiagnosticCollector(closedSettings,key->"1").collectSettings();}catch(IOException expected){threw=true;}check(threw);
  for(File f:dir.listFiles())Files.delete(f.toPath());Files.delete(dir.toPath());
  System.out.println("Feed/diagnostics: "+cases+" host cases PASS; Android services not simulated");
 }
}
'''
(out/'RootControl.java').write_text(stub,encoding='utf-8');(out/'DiagnosticHostTest.java').write_text(test,encoding='utf-8')
classes=out/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(jar),'-d',str(classes),str(src/'ThanksFeed.java'),str(src/'DiagnosticCollector.java'),str(src/'AppBuild.java'),str(out/'RootControl.java'),str(out/'DiagnosticHostTest.java')],check=True)
subprocess.run(['java','-cp',str(classes)+';'+str(jar),'top.rongshangs.lumacurve.refactor.DiagnosticHostTest',str(R/'website/thanks.json')],check=True)
print('Host-only JSON dependency SHA256:',hashlib.sha256(jar.read_bytes()).hexdigest())
