"""Run production independent probes and fallback policy with host service doubles."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/feature-self-check-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
sources={
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 1234;}}',
'android/hardware/Sensor.java':'package android.hardware;public class Sensor{public static final int TYPE_LIGHT=5;public int getType(){return TYPE_LIGHT;}}',
'top/rongshangs/lumacurve/refactor/Doubles.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
class HookRuntime{
 Object owner=new Object();Kernel kernel=new Kernel();Control brightnessControl=new Control();Memory memoryLifecycle=new Memory();Outdoor outdoor=new Outdoor();Scenes scenes=new Scenes();boolean closed,configProcessed=true,receiverRegistered=true,thermalRegistered=true;long selfCheckGeneration;String selfCheckRequest="",phase="active";int reloads,publishes;Handler handler=new Handler();
 RuntimeFeatureChecks featureChecks=new RuntimeFeatureChecks(this);void log(String s){}void reload(){reloads++;}void publish(){publishes++;}static float optionalNumber(Object o,String name){return .01f;}
 class Handler{List<Runnable> jobs=new ArrayList<>();List<Long> delays=new ArrayList<>();void postDelayed(Runnable r,long ms){jobs.add(r);delays.add(ms);}void drain(){List<Runnable> copy=new ArrayList<>(jobs);jobs.clear();for(Runnable r:copy)r.run();}}
 // Production lifecycle methods are inserted by the test driver below.
 LIFECYCLE
}
class Kernel{float[] factoryLux={0,100},factoryNit={2,500};float min=1,max=500;int manualPointCapacity(){return 7;}String name(){return "physical_mapping";}boolean persistentMemorySupported(){return true;}}
class Control{boolean available(){return true;}}class Memory{boolean resetSupported=true,timeoutSupported=true;}
class HookEntry{static Set<Class<?>> thermalSupported=new HashSet<>(Arrays.asList(Object.class));static boolean ready=true;static String fail="";static Object get(Object o,String k)throws Exception{if(k.equals(fail))throw new NoSuchFieldException(k);if(k.equals("mLightSensor")||k.equals("mAssistLightSensor"))return ready?new android.hardware.Sensor():null;if(k.contains("AmplifyFactor"))return 1f;return new Object();}}
class CurvePlan{CurvePlan(float[] a,float[] b,float c,float d,float[] e){}}
class RawPanelOutputHooks{static boolean supports(Class<?> c){return true;}}
class ForegroundApps{static boolean install(ClassLoader c){return true;}}
class ResponseTuning{static boolean supported(Object o){return true;}static boolean smallSupported(Object o){return true;}}
class LowLightTuning{static boolean supported(Object o){return true;}}
class LowLightAssistEvidence{static boolean supported(Object o){return true;}}
class AdvancedOptions{static String[] GROUPS={"threshold_override","assist_override","animation_override","sunlight_override","touch_override"};}
class AdvancedTuning{static int fail=-1;static boolean supported(int i,Object o){if(i==fail)throw new IllegalStateException("not ready");return true;}}
class SceneOptions{static String[] IDS={"night_driving","step","touch","proximity","stream_proximity","reflection","night_wake","manual_sunlight","game"},NAMES=IDS;}
class Scenes{void put(JSONObject j)throws JSONException{for(String id:SceneOptions.IDS)j.put("scene_"+id+"_supported",true);}}
class Outdoor{boolean supported(){return true;}HbmAccess binding(){return new HbmAccess();}boolean rangeSupported(){return true;}boolean oprSupported(){return true;}float nitCeiling(int ignored){return 500;}}
class HbmAccess{Object controller=new Object();HbmAccess(){}HbmAccess(Object c){controller=c;}boolean tunable(){return true;}float max(){return 1;}}
class OutdoorTuning{static Set<Class<?>> owners=new HashSet<>(Arrays.asList(Object.class)),ranges=owners,peaks=owners,nitCaps=owners;}
class OutdoorOpr{static Set<Class<?>> owners=OutdoorTuning.owners;}
class NitBrightnessLimit{NitBrightnessLimit(Object o){}float resolve(double n,float min,float max){return max;}}
class SceneTuning{static boolean supported(HookRuntime s,int group){return true;}}
class OutdoorPolicy{static boolean valid(float n){return Float.isFinite(n)&&n>0;}}
class OutdoorNodeProfile{static boolean matches(String b,double n,int m,boolean output,boolean range){return output&&range;}}
class OutdoorNodeBridge{static boolean interfaces(HookRuntime s,boolean range){return true;}}
''',
'top/rongshangs/lumacurve/refactor/SelfCheckTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
public class SelfCheckTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject find(JSONArray a,String id){for(int i=0;i<a.length();i++)if(id.equals(a.getJSONObject(i).getString("id")))return a.getJSONObject(i);throw new AssertionError(id);}
 public static void main(String[] args)throws Exception{
  Throwable[] failures={new NoSuchFieldException("field"),new NoSuchMethodException("method"),new ClassNotFoundException("class"),new NoClassDefFoundError("link"),new IllegalStateException("not ready"),new java.io.IOException("service")};
  for(Throwable failure:failures){FeatureSelfCheck f=new FeatureSelfCheck();int[] calls={0};f.check("bad","bad","bad_supported",false,()->{if(failure instanceof Error)throw (Error)failure;throw (Exception)failure;});f.check("good","good","good_supported",false,()->{calls[0]++;return true;});check(calls[0]==1);check(f.capabilities().getBoolean("good_supported"));check(!f.capabilities().getBoolean("bad_supported"));check(f.report(7,"run").getJSONArray("features").length()==2);}
  FeatureSelfCheck f=new FeatureSelfCheck();f.check("wait","wait","wait",true,()->false);f.check("absent","absent","absent",false,()->false);check(f.capabilities().getString("wait_state").equals("pending"));check(f.capabilities().getString("absent_state").equals("unsupported"));
  f.check("long","long",null,false,()->{throw new Exception(new String(new char[900]).replace('\0','x'));});check(find(f.report(7,"x").getJSONArray("features"),"long").getString("detail").length()==160);
  HookRuntime s=new HookRuntime();s.featureChecks.run("first");JSONArray entries=s.featureChecks.report.getJSONArray("features");check(entries.length()>=36);Set<String> ids=new HashSet<>();for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);check(ids.add(e.getString("id")));check(e.getString("state").equals("available"));}check(!s.featureChecks.pending());
  AdvancedTuning.fail=2;s.featureChecks.run("second");check(find(s.featureChecks.report.getJSONArray("features"),"animation_override").getString("state").equals("pending"));check(s.featureChecks.caps.getBoolean("outdoor_opr_supported"));check(s.featureChecks.caps.getBoolean("scene_game_supported"));check(s.featureChecks.report.getLong("runs")==2);AdvancedTuning.fail=-1;
  HookEntry.ready=false;s.featureChecks.run("boot");check(s.featureChecks.pending());check(!s.featureChecks.caps.getBoolean("dark_lock_supported"));s.scheduleReadinessChecks();check(s.handler.delays.equals(Arrays.asList(1000L,5000L,15000L)));HookEntry.ready=true;s.handler.drain();check(!s.featureChecks.pending());check(s.reloads==1);check(s.handler.jobs.size()==0);
  int before=s.reloads;s.checkFeatures("unchanged");check(s.reloads==before);check(s.featureChecks.report.getString("request").equals("unchanged"));
  s.thermalRegistered=false;s.featureChecks.run("thermal-event-failure");check(!s.featureChecks.caps.getBoolean("thermal_supported"));check(s.featureChecks.caps.getBoolean("outdoor_supported"));check(s.featureChecks.caps.getBoolean("response_supported"));s.thermalRegistered=true;
  s.receiverRegistered=false;s.featureChecks.run("screen-event-failure");check(!s.featureChecks.caps.getBoolean("dark_lock_supported"));check(!s.featureChecks.caps.getBoolean("outdoor_supported"));check(s.featureChecks.caps.getBoolean("low_light_supported"));s.receiverRegistered=true;
  HookEntry.fail="mAssistLightSensor";s.featureChecks.run("broken-assist");check(find(s.featureChecks.report.getJSONArray("features"),"assist_sensor").getString("state").equals("unsupported"));check(s.featureChecks.caps.getBoolean("response_supported"));HookEntry.fail="";
  HookEntry.ready=false;s.featureChecks.run("wait");s.scheduleReadinessChecks();s.closed=true;long count=s.featureChecks.runs;s.handler.drain();check(s.featureChecks.runs==count);
  String[][] flags={{"thermal_relax","thermal_supported"},{"response_override","response_supported"},{"memory_persist","persistent_memory_supported"},{"dark_lock_enabled","dark_lock_supported"},{"manual_panel_enabled","manual_panel_supported"},{"low_light_stability","low_light_supported"},{"animation_override","animation_override_supported"}};
  for(String[] failed:flags){JSONObject original=new JSONObject(),caps=new JSONObject();for(String[] pair:flags){original.put(pair[0],true);caps.put(pair[1],!pair[0].equals(failed[0]));}JSONArray paused=new JSONArray();JSONObject fitted=ConfigurationCompatibility.fit(original,caps,paused);check(paused.length()==1);for(String[] pair:flags){check(original.getBoolean(pair[0]));check(fitted.getBoolean(pair[0])!=pair[0].equals(failed[0]));}}
  JSONObject original=new JSONObject().put("outdoor_enabled",true).put("outdoor_range_unlock",true).put("outdoor_opr_relax",true).put("outdoor_node_boost",true).put("manual_brightness_apps",new JSONArray().put("a.b")).put("scene_options",new JSONObject().put("policies",new JSONObject().put("touch",new JSONObject()).put("game",new JSONObject())));
  JSONArray paused=new JSONArray();JSONObject fitted=ConfigurationCompatibility.fit(original,new JSONObject().put("outdoor_range_supported",false).put("app_auto_supported",false).put("scene_touch_supported",false),paused);check(fitted.getBoolean("outdoor_enabled"));check(!fitted.getBoolean("outdoor_opr_relax"));check(!fitted.getBoolean("outdoor_node_boost"));check(fitted.getJSONArray("manual_brightness_apps").length()==0);check(fitted.getJSONObject("scene_options").getJSONObject("policies").has("game"));check(!fitted.getJSONObject("scene_options").getJSONObject("policies").has("touch"));check(original.getJSONObject("scene_options").getJSONObject("policies").has("touch"));
  System.out.println("Independent feature self-check: "+cases+" cases PASS; production probes with service doubles, Android not tested");
 }
}'''}
def method(signature):
    text=(S/'HookRuntime.java').read_text(encoding='utf-8');start=text.index(signature);at=text.index('{',start);depth=1;end=at+1
    while depth:depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]
sources['top/rongshangs/lumacurve/refactor/Doubles.java']=sources['top/rongshangs/lumacurve/refactor/Doubles.java'].replace('LIFECYCLE',method('void checkFeatures(')+method('void scheduleReadinessChecks('))
paths=[]
for name,body in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8');paths.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(p) for p in paths],*[str(S/n) for n in ['FeatureSelfCheck.java','ConfigurationCompatibility.java','RuntimeFeatureChecks.java']]],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.SelfCheckTest'],check=True)
hook=(S/'HookEntry.java').read_text(encoding='utf-8');ui=(S/'MainActivity.java').read_text(encoding='utf-8')
assert 'major!=4' not in hook and 'SystemVersion.read().unsupported()' not in ui
assert 'run("self-check",null)' in ui[ui.index('@Override public void onResume()'):]
print('Startup entry points: actual-interface checks, no OS-major gate PASS')
