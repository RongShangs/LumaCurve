"""Production relative-gesture and tile-state policy: no Android rendering claims."""
from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/panel-interaction-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'PanelInteractionTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;
public class PanelInteractionTest {
 static int cases;static void check(boolean value){if(!value)throw new AssertionError("case "+cases);cases++;}
 static JSONObject state()throws Exception{return new JSONObject().put("phase","active").put("auto_mode",false).put("brightness_control",new JSONObject().put("owner","raw_panel").put("auto",false).put("raw_confirmed",true).put("raw_target",8000).put("raw_paused",false));}
 public static void main(String[] args)throws Exception{
  RelativeNodeGesture g=new RelativeNodeGesture();g.begin(5000,10,16383,180,200,8);check(g.move(180)==5000&&!g.moving);check(g.move(184)==5000&&!g.moving);int n=g.move(160);check(n==6637&&g.moving);check(g.move(160)==n);check(g.move(180)==5000);
  // Touching a different position must produce the same displacement, not a new absolute value.
  g.begin(5000,10,16383,30,200,8);check(g.move(10)==6637);
  g.begin(16000,10,16383,100,200,0);check(g.move(-200)==16383);check(g.move(-190)==15564);
  g.begin(100,10,16383,100,200,0);check(g.move(300)==10);check(g.move(290)==829);
  g.begin(5000,10,16383,100,200,0);double expected=5000;for(int i=1;i<=50;i++){float y=100-i*.05f;int v=g.move(y);expected=5000+(100-y)*16373/200.0;check(Math.abs(v-expected)<=.51);}
  check(g.move(Float.NaN)==(int)Math.round(g.value));check(g.move(Float.POSITIVE_INFINITY)==(int)Math.round(g.value));
  g.begin(10,10,10,100,200,8);check(g.move(50)==10);g.begin(1000,10,16383,100,0,0);check(g.move(0)==16383);
  JSONObject r=state(),c=r.getJSONObject("brightness_control");check(RawPanelTilePolicy.active(r));check(!RawPanelTilePolicy.paused(r));c.put("raw_paused",true);check(!RawPanelTilePolicy.active(r)&&RawPanelTilePolicy.paused(r));r.put("auto_mode",true);check(!RawPanelTilePolicy.active(r)&&!RawPanelTilePolicy.paused(r));
  for(String phase:new String[]{"attached","disabled","error"}){r=state().put("phase",phase);check(!RawPanelTilePolicy.active(r));}
  for(String owner:new String[]{"none","dark","panel"}){r=state();r.getJSONObject("brightness_control").put("owner",owner);check(!RawPanelTilePolicy.active(r));}
  r=state();r.getJSONObject("brightness_control").put("raw_confirmed",false);check(!RawPanelTilePolicy.active(r));r=state();r.getJSONObject("brightness_control").put("raw_target",-1);check(!RawPanelTilePolicy.active(r));r=state();r.getJSONObject("brightness_control").put("auto",true);check(!RawPanelTilePolicy.active(r));check(!RawPanelTilePolicy.active(null));check(!RawPanelTilePolicy.active(new JSONObject()));
  System.out.println("Panel relative gesture/tile policy: "+cases+" cases PASS; production policies, Android UI not tested");
 }
}
''',encoding='utf8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),str(S/'RelativeNodeGesture.java'),str(S/'RawPanelTilePolicy.java'),str(p)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.PanelInteractionTest'],check=True)
