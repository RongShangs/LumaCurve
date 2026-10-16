"""Production node settings, device maximum propagation and slider drawing with doubles."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/outdoor-node-settings-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
def method(file,signature):
    text=(S/file).read_text(encoding='utf-8');start=text.index(signature);at=text.index('{',start);depth=1;end=at+1
    while depth:depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]
source=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.io.*;
class MainActivity {
 JSONObject runtime;OutdoorOptions outdoorOptions=new OutdoorOptions();SeekBar nodeTargetSlider=new SeekBar();TextView nodeTargetLabel=new TextView();
 String tr(String s){return s;}void enableParameter(SeekBar slider,TextView label,boolean enabled){slider.enabled=enabled;}
 DRAW
}
class SeekBar {int maximum,progress;boolean enabled;void setMax(int v){if(v<0)throw new AssertionError();maximum=v;}void setProgress(int p){if(p<0||p>maximum)throw new AssertionError("range");progress=p;}}
class TextView {String value;void setText(String s){value=s;}}
class PanelNodeDiscovery {static int maximum;static boolean available=true;static JSONObject discover(File path)throws JSONException{return new JSONObject().put("supported",available).put("maximum",maximum);}}
class RootControl {static File DATA=new File("not-used");}
class OutdoorNodeRoot {
 static boolean supported(JSONObject state){return PanelNodeDiscovery.available&&state.optBoolean("outdoor_node_supported");}static JSONObject health(){return new JSONObject();}static int running(){return -1;}
 ANNOTATE
}
public class NodeSettingsTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void bad(Object v)throws Exception{try{OutdoorNodeTarget.parse(new JSONObject().put(OutdoorNodeTarget.KEY,v));throw new AssertionError("accepted "+v);}catch(IllegalArgumentException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  check(OutdoorNodeTarget.parse(new JSONObject())==0);
  for(Object v:new Object[]{-1,1,9,65536,10.5,"4095",JSONObject.NULL})bad(v);
  for(int maximum:new int[]{11,255,1023,2047,4095,8191,16383,32767,65535}){
   MainActivity ui=new MainActivity();ui.outdoorOptions.nodeBoost=true;JSONObject state=new JSONObject().put("outdoor_node_supported",true).put("max_logical_nit",3500);PanelNodeDiscovery.maximum=maximum;OutdoorNodeRoot.annotate(state);check(state.getInt("outdoor_node_maximum")==maximum);ui.runtime=state;
   ui.drawNodeTarget(true);check(ui.nodeTargetSlider.maximum==maximum-10);check(ui.nodeTargetSlider.progress==maximum-10);check(ui.nodeTargetSlider.enabled);check(ui.nodeTargetLabel.value.endsWith(maximum+" / "+maximum));check(ui.outdoorOptions.nodeTarget==0);
   for(int target:new int[]{10,maximum/2<10?10:maximum/2,maximum,65535}){
    ui.outdoorOptions.nodeTarget=target;ui.drawNodeTarget(true);int actual=Math.min(target,maximum);check(ui.nodeTargetSlider.progress==actual-10);check(ui.nodeTargetLabel.value.endsWith(actual+" / "+maximum));check(OutdoorNodeTarget.resolve(target,maximum)==actual);check(OutdoorNodeTarget.stored(target,maximum)==(actual==maximum?0:actual));
   }
   ui.drawNodeTarget(false);check(!ui.nodeTargetSlider.enabled);ui.outdoorOptions.nodeBoost=false;ui.drawNodeTarget(true);check(!ui.nodeTargetSlider.enabled);
   ui.outdoorOptions.nodeBoost=true;ui.runtime.put("outdoor_node_supported",false);ui.drawNodeTarget(true);check(!ui.nodeTargetSlider.enabled);
  }
  MainActivity ui=new MainActivity();ui.outdoorOptions.nodeBoost=true;for(int maximum:new int[]{0,10,-1,65536}){ui.runtime=new JSONObject().put("outdoor_node_maximum",maximum).put("outdoor_node_supported",true);ui.drawNodeTarget(true);check(!ui.nodeTargetSlider.enabled&&ui.nodeTargetSlider.maximum==0&&ui.nodeTargetSlider.progress==0);check(ui.nodeTargetLabel.value.equals("等待读取主屏节点上限"));}
  ui.runtime=null;ui.drawNodeTarget(true);check(!ui.nodeTargetSlider.enabled);
  JSONObject stored=new JSONObject().put("outdoor_node_target",3000);OutdoorOptions opts=OutdoorOptions.parse(stored);JSONObject wire=new JSONObject();opts.put(wire);check(wire.getInt("outdoor_node_target")==3000&&OutdoorOptions.parse(wire).same(opts));OutdoorOptions changed=OutdoorOptions.parse(wire);changed.nodeTarget=2000;check(!changed.same(opts));
  JSONObject stale=new JSONObject().put("outdoor_node_supported",true).put("outdoor_node_maximum",4095);PanelNodeDiscovery.available=false;OutdoorNodeRoot.annotate(stale);check(!stale.getBoolean("outdoor_node_supported"));check(!stale.has("outdoor_node_maximum"));
  System.out.println("Outdoor node settings: "+cases+" cases PASS; production range/model/UI methods, Android not tested");
 }
}'''.replace('DRAW',method('MainActivity.java','void drawNodeTarget(')).replace('ANNOTATE',method('OutdoorNodeRoot.java','static void annotate('))
p=O/'NodeSettingsTest.java';p.write_text(source,encoding='utf-8');C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(p),*[str(S/(n+'.java')) for n in ['OutdoorNodeTarget','OutdoorOptions','AdvancedPolicy']]],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.NodeSettingsTest'],check=True)
