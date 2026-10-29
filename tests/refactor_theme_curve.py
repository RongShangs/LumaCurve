"""Execute theme selection, the production handover block and reset command with service doubles."""
from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-theme-curve-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
source=(S/'HookRuntime.java').read_text(encoding='utf-8')
start=source.index('                    ThemeCurveOptions theme=')
handover=source[start:source.index('                    AdvancedTuning.restoreAnimation',start)]
start=source.index('                    persistentMemory.configure(memoryOptions,curveChanged);')
finish=source[start:source.index('                    log(curveChanged?',start)]
root=(S/'RootControl.java').read_text(encoding='utf-8');start=root.index('    JSONObject restoreDefaults()')
reset=root[start:root.index('    String export()',start)]
java=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.io.*;
class Kernel {
 final float[] factoryLux={0,30,600,5000},factoryNit={2,30,200,1000};final float min=1,max=1000;CurvePlan active;SavedCurve savedBase;int clears,configures;
 boolean persistentMemorySupported(){return true;}String name(){return "refactor";}CurvePlan plan(){return active;}void clearMemory(){clears++;}void configure(float[] f,float floor,SavedCurve base){active=SavedCurve.plan(base,factoryLux,factoryNit,min,max,f, floor);savedBase=base;configures++;}
}
class MemoryPersistence {static class ThemeHandover {int mode;ThemeHandover(int m){mode=m;}}int clears;boolean pending;void clear(){clears++;pending=false;}void configure(Object o,boolean changed){pending=true;}ThemeHandover beforeTheme(int mode){if(mode==0)clear();return new ThemeHandover(mode);}void afterTheme(ThemeHandover h){if(h!=null&&h.mode==0)clear();}void flush(){}void detachTheme(){pending=false;}void invalidateTheme(){}}
class MemoryPolicy {void reset(){}}
class Lifecycle {void event(String s){}}
class Runtime {
 Kernel kernel=new Kernel();MemoryPersistence persistentMemory=new MemoryPersistence();MemoryPolicy memoryPolicy=new MemoryPolicy();Lifecycle memoryLifecycle=new Lifecycle();MemoryOptions memoryOptions=new MemoryOptions(false,30);
 boolean themeCurveEnabled,themeCurveDark,dark,themeKnown;int themeMemoryMode;String themeMemoryScope="";boolean systemDark(){return dark;}void log(String s){}
 void apply(JSONObject config,boolean dark)throws Exception{this.dark=dark;HANDOVER FINISH}
}
class ControlCenterRoot {static String PROFILE="profile";}
class OutdoorNodeRoot {static int stops;static void stop(){stops++;}}
class NativePanelRoot {static int stops;static void stop(){stops++;}}
class Store {Map<String,String> values=new HashMap<>();boolean write=true;boolean put(String k,String v){if(!write)return false;if(v==null)values.remove(k);else values.put(k,v);return true;}}
class Root {static String CONFIG="config";Store settings=new Store();boolean connected=true,ack=true;JSONObject live(){return connected?new JSONObject():null;}boolean waitFor(String r,boolean active){return ack&&!active;}RESET}
public class ThemeTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject config()throws Exception{return new JSONObject().put("factors","1,1.1,1,1").put("curve_floor_nit",0).put("memory_hold_forever",true).put("outdoor_max_nit",1800);}
 static void reject(JSONObject c)throws Exception{try{ThemeCurveOptions.parse(c).verify("refactor",new float[]{0,30,600,5000},new float[]{2,30,200,1000},1,1000);throw new AssertionError();}catch(IllegalArgumentException|JSONException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  JSONObject normal=config();String original=normal.toString();ThemeCurveOptions old=ThemeCurveOptions.parse(normal);check(!old.enabled&&old.factors[1]==1);check(old.select(normal,true)==normal&&normal.toString().equals(original));
  JSONObject untouched=new JSONObject();new ThemeCurveOptions().put(untouched);check(!ThemeCurveOptions.parse(untouched).initialized);
  ThemeCurveOptions t=new ThemeCurveOptions();t.enabled=true;t.factors=new float[]{1,.7f,.8f,1};t.floor=1.5f;t.put(normal);String stored=normal.toString();JSONObject dark=t.select(normal,true);check(dark.getString("factors").equals(CurvePlan.encode(t.factors)));check(normal.toString().equals(stored));check(dark.getBoolean("memory_hold_forever")&&dark.getDouble("outdoor_max_nit")==1800);check(t.select(normal,false)==normal);
  ThemeCurveOptions read=ThemeCurveOptions.parse(normal);check(read.enabled&&read.factors[1]==.7f&&read.floor==1.5f);check(ThemeCurveOptions.scope(false,false).isEmpty());check(!ThemeCurveOptions.scope(true,false).equals(ThemeCurveOptions.scope(true,true)));
  t.enabled=false;t.put(normal);read=ThemeCurveOptions.parse(normal);check(!read.enabled&&read.factors[1]==.7f);check(read.select(normal,true)==normal);
  for(Object bad:new Object[]{"x",false,42,new JSONObject().put("enabled","true"),new JSONObject().put("factors","1,1,1,1.1"),new JSONObject().put("curve_floor_nit",-1)})reject(config().put(ThemeCurveOptions.KEY,bad));
  reject(config().put(ThemeCurveOptions.KEY,new JSONObject().put(SavedCurve.KEY,new JSONObject().put("lux",new JSONArray("[0,5000]")).put("nit",new JSONArray("[1,2000]")))));
  Runtime runtime=new Runtime();t.enabled=true;t.put(normal);runtime.apply(normal,false);check(runtime.kernel.configures==1&&runtime.kernel.clears==0&&!runtime.themeCurveDark);int clears=runtime.persistentMemory.clears;
  runtime.apply(normal,true);check(runtime.themeCurveDark&&runtime.themeMemoryScope.equals(":theme-dark"));check(runtime.kernel.clears==1&&runtime.persistentMemory.clears==clears+2&&!runtime.persistentMemory.pending);check(Math.abs(runtime.kernel.plan().nits()[1]-21)<.001);
  runtime.apply(normal,true);check(runtime.kernel.clears==1);runtime.apply(normal,false);check(runtime.kernel.clears==2&&runtime.kernel.plan().nits()[1]==33);
  // Even numerically equal profiles still discard hand adjustments on every real theme transition.
  t.factors=new float[]{1,1.1f,1,1};t.floor=0;t.put(normal);runtime.apply(normal,false);int writes=runtime.kernel.configures;runtime.apply(normal,true);check(runtime.kernel.configures==writes&&runtime.kernel.clears==3&&!runtime.persistentMemory.pending);
  t.enabled=false;t.put(normal);runtime.apply(normal,false);int count=runtime.kernel.clears;runtime.apply(normal,true);check(runtime.kernel.clears==count+1&&runtime.themeMemoryScope.isEmpty());
  // Invalid other profile is rejected before mutating memory or the applied curve.
  JSONObject invalid=config().put(ThemeCurveOptions.KEY,new JSONObject().put("enabled",true).put("factors","1,1,1,2"));count=runtime.kernel.configures;try{runtime.apply(invalid,false);throw new AssertionError();}catch(IllegalArgumentException expected){check(runtime.kernel.configures==count&&runtime.kernel.clears==5);}
  t.enabled=true;t.saved=new SavedCurve(new float[]{0,30,600,5000},new float[]{2,20,100,1000});t.factors=new float[]{1,1,1,1};t.put(normal);read=ThemeCurveOptions.parse(normal);check(SavedCurve.same(t.saved,read.saved));check(!read.select(normal,true).has("captured_theme_dark"));
  Root root=new Root();root.settings.values.put("profile","old");root.settings.values.put("config",stored);check(root.restoreDefaults().getBoolean("ok"));JSONObject reset=new JSONObject(root.settings.values.get("config"));check(reset.getBoolean("restore_system_defaults")&&!reset.getBoolean("enabled")&&reset.getString("reset_anchors").length()>0&&!reset.has(ThemeCurveOptions.KEY)&&root.settings.values.get("profile")==null);check(OutdoorNodeRoot.stops==1&&NativePanelRoot.stops==1);
  root=new Root();root.connected=false;try{root.restoreDefaults();throw new AssertionError();}catch(IOException expected){check(root.settings.values.isEmpty());}
  root=new Root();root.settings.write=false;try{root.restoreDefaults();throw new AssertionError();}catch(IOException expected){check(root.settings.values.isEmpty());}
  root=new Root();root.ack=false;try{root.restoreDefaults();throw new AssertionError();}catch(IOException expected){check(root.settings.values.containsKey("config"));}
  System.out.println("Theme curve and system defaults: "+cases+" cases PASS; production transaction blocks, Android not tested");
 }
}'''.replace('HANDOVER',handover).replace('FINISH',finish).replace('RESET',reset)
p=O/'ThemeTest.java';p.write_text(java,encoding='utf-8');C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in ['ThemeCurveOptions','MemoryOptions','SavedCurve','CurvePlan']],str(p)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.ThemeTest'],check=True)
