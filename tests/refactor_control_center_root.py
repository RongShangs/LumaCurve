"""Exercise production Root coordination without starting any node writer."""
from pathlib import Path
import subprocess,re
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';OUT=ROOT/'build/control-center-root-tests';OUT.mkdir(exist_ok=True);J=ROOT/'build/refactor-diagnostics/json-20240303.jar'
p=OUT/'CoordinateRootTest.java';p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
class RootSettings{final Map<String,String> values=new HashMap<>();String get(String k){return values.get(k);}boolean put(String k,String v){if(v==null)values.remove(k);else values.put(k,v);return true;}}
class BrightnessControlOptions{int nodeSliderMode;static BrightnessControlOptions parseStored(JSONObject c){BrightnessControlOptions o=new BrightnessControlOptions();o.nodeSliderMode=c.optInt("control_center_node_mode");return o;}}
class ControlCenterEngine{static final String TABLES="tables";}
class NativePanelClient{static JSONObject guard=new JSONObject();static JSONObject call(String s){return guard;}}
class NativePanelRoot{static JSONObject node;static int stops;static JSONObject status(){return node;}static void stop(){stops++;}}
public class CoordinateRootTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args)throws Exception{
 RootSettings s=new RootSettings();s.put("boot_count","1");JSONObject calibration=new JSONObject().put("standard",new JSONObject().put("input",new JSONArray().put(0).put(1)).put("node",new JSONArray().put(0).put(1)));
 JSONObject table=new JSONObject().put("build",AppBuild.BUILD).put("pid",123).put("process_start","42").put("boot_count","1").put("calibration",calibration);s.put("tables",table.toString());
 NativePanelRoot.node=new JSONObject().put("supported",true).put("maximum",16383).put("canonical_path","primary");JSONObject config=new JSONObject().put("enabled",true).put("control_center_node_mode",2).put("revision","r").put("control_center_node_maximum",16383).put("control_center_node_path","primary");JSONObject runtime=new JSONObject().put("control_center_slider_supported",true).put("control_center_slider",new JSONObject().put("registered",true).put("mode",2).put("coordinate_ready",true)).put("revision","r").put("phase","active").put("pid",123).put("process_start","42");
 ControlCenterRoot.reconcile(s,runtime,config.toString());JSONObject profile=new JSONObject(s.get(ControlCenterRoot.PROFILE));check(profile.getString("kind").equals("coordinate_v2")&&profile.getInt("maximum")==16383);check(NativePanelRoot.stops==0);check(profile.getJSONObject("calibration").toString().equals(calibration.toString()));
 NativePanelClient.guard.put("control_center_listener",true).put("armed",true);config.put("enabled",false);ControlCenterRoot.reconcile(s,runtime,config.toString());check(s.get(ControlCenterRoot.PROFILE)==null&&NativePanelRoot.stops==0);
 config.put("enabled",true);ControlCenterRoot.reconcile(s,runtime,config.toString());check(NativePanelRoot.stops==0);NativePanelClient.guard.put("armed",false);ControlCenterRoot.reconcile(s,runtime,config.toString());check(NativePanelRoot.stops==1);
 table.put("pid",999);s.put("tables",table.toString());ControlCenterRoot.reconcile(s,runtime,config.toString());check(s.get(ControlCenterRoot.PROFILE)==null);table.put("pid",123);table.put("process_start","old");s.put("tables",table.toString());ControlCenterRoot.reconcile(s,runtime,config.toString());check(s.get(ControlCenterRoot.PROFILE)==null);table.put("process_start","42");table.put("boot_count","old");s.put("tables",table.toString());ControlCenterRoot.reconcile(s,runtime,config.toString());check(s.get(ControlCenterRoot.PROFILE)==null);
 table.put("boot_count","1");s.put("tables",table.toString());runtime.getJSONObject("control_center_slider").put("coordinate_ready",false);ControlCenterRoot.reconcile(s,runtime,config.toString());check(s.get(ControlCenterRoot.PROFILE)==null);runtime.getJSONObject("control_center_slider").put("coordinate_ready",true);ControlCenterRoot.reconcile(s,runtime,config.toString());check(s.get(ControlCenterRoot.PROFILE)!=null);
 NativePanelRoot.node.put("canonical_path","other");try{ControlCenterRoot.reconcile(s,runtime,config.toString());throw new AssertionError();}catch(IllegalStateException expected){check(s.get(ControlCenterRoot.PROFILE)==null);}
 System.out.println("Coordinate Root: "+cases+" cases PASS; no native writer start API in service double");
 }
}''',encoding='utf-8')
C=OUT/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(p),str(SRC/'ControlCenterRoot.java'),str(SRC/'AppBuild.java')],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.CoordinateRootTest'],check=True)
