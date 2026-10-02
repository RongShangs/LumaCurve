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
 static void invalid(String text)throws Exception{try{ThanksFeed.parse(text);throw new AssertionError("invalid feed accepted");}catch(Exception expected){cases++;}}
 public static void main(String[] args)throws Exception{
  if(args.length>0&&args[0].equals("sleep")){System.out.println("partial-before-timeout");System.out.flush();Thread.sleep(30000);return;}
  if(args.length>0&&args[0].equals("failed")){System.out.println("failure-details");System.exit(3);return;}
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
  // Zip write errors propagate instead of being reported as successful diagnostics.
  ZipOutputStream closed=new ZipOutputStream(new ByteArrayOutputStream());closed.close();boolean threw=false;try{new DiagnosticCollector(closed).command("disk-full.txt",5,java,"-version");}catch(IOException expected){threw=true;}check(threw);
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
