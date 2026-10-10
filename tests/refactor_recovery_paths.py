"""Exercise actual recovery and decode methods with service doubles; no Android claim."""
from pathlib import Path
import subprocess, os, json

R=Path(__file__).resolve().parents[1];O=R/'build/refactor-recovery-tests';O.mkdir(exist_ok=True)
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
J=R/'build/refactor-diagnostics/json-20240303.jar'
def between(file,start,end):
 text=(S/file).read_text(encoding='utf-8');a=text.index(start);return text[a:text.index(end,a)]
stop=between('RootControl.java','    JSONObject stop()','    String export()')
start=between('NativePanelRoot.java',' static void start(',' static IOException startFailure')
matches=between('NativePanelRoot.java',' static boolean matches(',' static void install(')
runtime_close=between('HookRuntime.java','    void close()','    boolean relaxThermal()')

decode=between('MainActivity.java','    Bitmap decodeAsset(','    AlertDialog dialog(')
test=O/'edges/ReviewEdges.java';test.parent.mkdir(exist_ok=True)
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.util.*;import org.json.*;
class RootControl{
 static final File DATA=new File("build/refactor-recovery-tests/edges");static final String CONFIG="config";
 final Store settings=new Store();boolean connected,confirm=true;int resumes;
 JSONObject live(){return connected?new JSONObject():null;}boolean waitFor(String rev,boolean enabled){return confirm;}void resumeOld(){resumes++;}
 static class Store{Map<String,String> values=new HashMap<>();String mode="0";boolean rejectMode;int modeWrites;
 String get(String k){return values.get(k);}boolean put(String k,String v){if(v==null)values.remove(k);else values.put(k,v);return true;}
 String getSystem(String k){return mode;}boolean putSystem(String k,String v){modeWrites++;if(rejectMode)return false;mode=v;return true;}}
''' +stop+r'''}
class PanelNodeDiscovery{static JSONObject value;static int scans;static JSONObject discover(File f)throws Exception{scans++;return new JSONObject(value.toString());}}
class NativePanelClient{static JSONObject reply;static int probes;static JSONObject call(String action)throws Exception{probes++;return new JSONObject(reply.toString());}}
class NativePanelRoot{
 static File BIN=new File("never-launch-on-host");static int installs,stops;static boolean changeDuringInstall;
 static void install()throws Exception{installs++;if(changeDuringInstall)PanelNodeDiscovery.value.put("maximum",100);}
 static void stop(){stops++;}static IOException startFailure(File log,String reason){return new IOException(reason);}
'''+matches+start+r'''}
class Bitmap{int width,height;Bitmap(int w,int h){width=w;height=h;}static class Config{}}
class AssetStream extends InputStream{final int w,h;boolean closed;AssetStream(int w,int h){this.w=w;this.h=h;}public int read(){return -1;}public void close(){closed=true;}}
class BitmapFactory{static int boundsCalls,decodeCalls;static Options last;
 static class Options{boolean inJustDecodeBounds;int outWidth,outHeight,inSampleSize=1;}
 static Bitmap decodeStream(InputStream stream,Object ignored,Options o){AssetStream s=(AssetStream)stream;if(o.inJustDecodeBounds){boundsCalls++;o.outWidth=s.w;o.outHeight=s.h;return null;}decodeCalls++;last=o;return new Bitmap((s.w+o.inSampleSize-1)/o.inSampleSize,(s.h+o.inSampleSize-1)/o.inSampleSize);}}
class AssetLoader{int width,height;List<AssetStream> streams=new ArrayList<>();InputStream open(String name){AssetStream s=new AssetStream(width,height);streams.add(s);return s;}}
class ImageCandidate{final AssetLoader loader=new AssetLoader();int density=1;int dp(int v){return v*density;}AssetLoader getAssets(){return loader;}
'''+decode+r'''}
class RuntimeCandidate{
 boolean closed,receiverRegistered=true,thermalRegistered=true;final Part persistentMemory=new Part(),brightnessControl=new Part(),memoryLifecycle=new Part(),kernel=new Part(),stateWriter=new Part();
 final Object observer=new Object(),refresh=new Object(),batteryListener=new Object(),thermalListener=new Object();final ContextDouble context=new ContextDouble(observer);final Part power=new Part();int disabled;
 void disableOverrides(){disabled++;}
 static class Part{int closes,configures,removed;void close(){closes++;}Object plan(){return new Object();}void configure(Object p){configures++;}void removeThermalStatusListener(Object p){removed++;}}
 static class ContextDouble{final Object first;int firstCalls,secondCalls,receivers;boolean failFirst;ContextDouble(Object o){first=o;}ContextDouble getContentResolver(){return this;}void unregisterContentObserver(Object o){if(o==first){firstCalls++;if(failFirst)throw new IllegalStateException("injected unregister failure");}else secondCalls++;}void unregisterReceiver(Object r){receivers++;}}
''' + runtime_close + r'''}
public class ReviewEdges{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static RootControl owner(String kind,boolean wasAuto,int user)throws Exception{NativePanelRoot.stops=0;RootControl c=new RootControl();c.settings.put("hyperlux_brightness_owner_v1",new JSONObject().put("owner",kind).put("was_auto",wasAuto).put("user",user).put("session","session").toString());return c;}
 static JSONObject node()throws Exception{return new JSONObject().put("supported",true).put("path","/sys/class/backlight/panel0-backlight/brightness").put("canonical_path","/sys/devices/panel0/brightness").put("maximum",16383);}
 static void nodes()throws Exception{PanelNodeDiscovery.value=node();PanelNodeDiscovery.scans=NativePanelClient.probes=NativePanelRoot.installs=0;NativePanelRoot.changeDuringInstall=false;NativePanelClient.reply=node().put("ok",true).put("native_build","raw07-openprobe");}
 public static void main(String[] args)throws Exception{
  for(String kind:new String[]{"dark","raw_panel"}){RootControl c=owner(kind,true,0);check(c.stop().getBoolean("ok"));check(c.settings.mode.equals("1")&&c.settings.modeWrites==1);check(c.settings.get("hyperlux_brightness_owner_v1")==null);check(!new JSONObject(c.settings.get("config")).getBoolean("enabled"));check(NativePanelRoot.stops==1&&c.resumes==0);}
  for(String kind:new String[]{"dark","raw_panel","panel","unknown"}){RootControl c=owner(kind,false,0);c.stop();check(c.settings.mode.equals("0")&&c.settings.modeWrites==0);}
  RootControl c=owner("raw_panel",true,10);c.stop();check(c.settings.mode.equals("0")&&c.settings.modeWrites==0);
  c=owner("raw_panel",true,0);c.settings.mode="1";c.stop();check(c.settings.modeWrites==0);
  c=owner("raw_panel",true,0);c.settings.rejectMode=true;try{c.stop();throw new AssertionError("mode failure accepted");}catch(IOException expected){check(true);}check(c.settings.get("hyperlux_brightness_owner_v1")!=null&&c.settings.get("config")==null);
  c=owner("raw_panel",true,0);c.connected=true;c.stop();check(c.settings.modeWrites==0&&c.resumes==1);
  c=owner("raw_panel",true,0);c.connected=true;c.confirm=false;try{c.stop();throw new AssertionError("unconfirmed stop accepted");}catch(IOException expected){check(true);}check(c.resumes==0);
  nodes();JSONObject wrong=node().put("maximum",100);try{NativePanelRoot.start(wrong);throw new AssertionError("changed selection accepted");}catch(IOException expected){check(true);}check(NativePanelRoot.installs==0&&NativePanelClient.probes==0);
  nodes();NativePanelRoot.changeDuringInstall=true;try{NativePanelRoot.start(node());throw new AssertionError("post-install change accepted");}catch(IOException expected){check(true);}check(NativePanelRoot.installs==1&&PanelNodeDiscovery.scans==2&&NativePanelClient.probes==0);
  nodes();NativePanelRoot.start(node());check(NativePanelRoot.installs==1&&PanelNodeDiscovery.scans==2&&NativePanelClient.probes==1);
  nodes();NativePanelClient.reply.put("path","/sys/class/leds/lcd-backlight/brightness");try{NativePanelRoot.start(node());throw new AssertionError("other guard accepted");}catch(IOException expected){check(true);}check(NativePanelClient.probes==1);
  int[][] assets={{1080,1080,46},{748,748,240},{571,571,240}};
  for(int density:new int[]{1,2,3,4})for(int[] asset:assets){ImageCandidate image=new ImageCandidate();image.density=density;image.loader.width=asset[0];image.loader.height=asset[1];BitmapFactory.boundsCalls=BitmapFactory.decodeCalls=0;Bitmap b=image.decodeAsset("fixture.jpg",asset[2]);check(b.width>0&&b.height>0);check(b.width<=image.dp(asset[2])*2+1&&b.height<=image.dp(asset[2])*2+1);int sample=BitmapFactory.last.inSampleSize;check(sample>=1&&(sample&(sample-1))==0);check(image.loader.streams.size()==2&&image.loader.streams.get(0).closed&&image.loader.streams.get(1).closed);check(BitmapFactory.boundsCalls==1&&BitmapFactory.decodeCalls==1);}
  for(boolean failure:new boolean[]{false,true}){RuntimeCandidate runtime=new RuntimeCandidate();runtime.context.failFirst=failure;runtime.close();check(runtime.closed);check(runtime.context.firstCalls==1&&runtime.context.secondCalls==1);check(runtime.context.receivers==1&&runtime.power.removed==1);check(runtime.persistentMemory.closes==1&&runtime.brightnessControl.closes==1&&runtime.memoryLifecycle.closes==1);check(runtime.disabled==1&&runtime.kernel.configures==1);runtime.close();check(runtime.context.firstCalls==1&&runtime.context.secondCalls==1&&runtime.disabled==1);}
  System.out.println("Review edges: "+cases+" cases PASS; disconnected recovery, node ordering and image decode models; Android not tested");
 }
}
''',encoding='utf-8')
classes=O/'edges/classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),str(test)],check=True)
run=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.ReviewEdges'],check=True,capture_output=True,text=True)
print(run.stdout,end='');(O/'edges-result.log').write_text(run.stdout,encoding='utf-8')
