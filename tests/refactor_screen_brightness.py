"""Run production readback/calibration, including stale framework and fold identity rejection."""
from pathlib import Path
import subprocess,sys
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-screen-reading-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
sources={
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long elapsedRealtime(){return 100;}}',
'top/rongshangs/lumacurve/refactor/ActivePanelNode.java':'package top.rongshangs.lumacurve.refactor;import org.json.*;class ActivePanelNode {static JSONObject display(){return new JSONObject();}static JSONObject discover(){return new JSONObject();}}',
'top/rongshangs/lumacurve/refactor/PanelNodeDiscovery.java':'package top.rongshangs.lumacurve.refactor;import java.io.*;class PanelNodeDiscovery {static int number(File f){return 0;}}',
'top/rongshangs/lumacurve/refactor/ReadingTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
public class ReadingTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void near(double a,double b){check(Math.abs(a-b)<.01);}
 public static class Config {public float[] mBacklight={.01f,1};boolean bad;public float getNitsFromBacklight(float b){return bad?Float.NaN:b*b*2000;}}
 public static class Owner {public Object mDisplayDeviceConfig=new Config();}
 static JSONObject raw()throws Exception{return new JSONObject().put("actual_nit",123).put("brightness_control",new JSONObject().put("owner","raw_panel"));}
 static JSONObject node(int n,int max)throws Exception{return new JSONObject().put("supported",true).put("actual",n).put("maximum",max);}
 public static void main(String[] args)throws Exception{
  JSONObject table=ScreenBrightnessReading.calibration(new Owner());check(table.getJSONArray("nit").length()==129);near(ScreenBrightnessReading.nit(table,.5),500.03);near(ScreenBrightnessReading.nit(table,0),0);check(Double.isNaN(ScreenBrightnessReading.nit(table,.005)));check(Double.isNaN(ScreenBrightnessReading.nit(null,.5)));
  for(int max:new int[]{4095,16383,65535}){JSONObject s=raw().put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(max,max),"local:1");near(s.getDouble("actual_nit"),2000);check(s.getInt("actual_node")==max&&s.getString("actual_nit_source").equals("node_readback_device_calibration"));}
  JSONObject s=raw();ScreenBrightnessReading.apply(s,node(12000,16383),"local:1");check(!s.has("actual_nit")&&s.getInt("actual_node")==12000);check(s.getString("actual_nit_source").equals("node_readback_uncalibrated"));
  s=raw().put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(16000,16383).put("driver_actual",0),"local:1");near(s.getDouble("actual_nit"),0);check(s.getInt("actual_node")==0&&s.getString("actual_node_source").equals("driver_actual_brightness"));
  s=raw();ScreenBrightnessReading.apply(s,new JSONObject().put("supported",false),"local:1");check(!s.has("actual_nit")&&!s.has("actual_node"));
  s=new JSONObject().put("actual_nit",321);ScreenBrightnessReading.apply(s,node(100,4095),"local:1");near(s.getDouble("actual_nit"),321); // old framework fallback remains available outside raw ownership
  s=raw();try{ScreenBrightnessReading.apply(s,node(4096,4095),"local:1");throw new AssertionError();}catch(IllegalArgumentException expected){check(!s.has("actual_nit"));}
  s=raw();try{ScreenBrightnessReading.apply(s,node(1,4095).put("physical_display_id","local:2"),"local:1");throw new AssertionError();}catch(IllegalStateException expected){check(!s.has("actual_nit"));}
  Owner owner=new Owner();((Config)owner.mDisplayDeviceConfig).bad=true;try{ScreenBrightnessReading.calibration(owner);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  // A zero-only driver getter must not override a lit screen's valid output.
  s=new JSONObject().put("screen_state",2).put("actual_nit",130.5890655517578).put("actual_nit_source","device_backlight_mapping").put("screen_nit_calibration",table);
  ScreenBrightnessReading.apply(s,node(1443,16383).put("driver_actual",0).put("bl_power",0),"local:1");near(s.getDouble("actual_nit"),130.5890655517578);check(s.getInt("actual_node")==1443&&s.getInt("actual_node_driver")==0&&s.getInt("actual_node_register")==1443);check(s.getString("actual_node_fallback_reason").equals("driver_zero_conflicts_with_lit_output")&&s.getString("actual_nit_source").equals("device_backlight_mapping"));
  s=raw().put("screen_state",2).put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(1443,16383).put("driver_actual",0),"local:1");check(s.getDouble("actual_nit")>0&&s.getInt("actual_node")==1443&&s.getString("actual_node_source").equals("sysfs_brightness_readback"));near(s.getDouble("actual_nit"),ScreenBrightnessReading.nit(table,1443d/16383));
  // Replay an already annotated broken zero; the independent framework range also proves output.
  s=raw().put("actual_nit",0).put("actual_nit_source","node_readback_device_calibration").put("screen_state",2).put("screen_nit_calibration",table);s.getJSONObject("brightness_control").put("range",new JSONObject().put("actual",.08126579));ScreenBrightnessReading.apply(s,node(1443,16383).put("driver_actual",0),"local:1");check(s.getDouble("actual_nit")>0&&s.getInt("actual_node")==1443);
  // Preserve genuine zeros, positive driver feedback, and known backlight blanking.
  for(int screen:new int[]{1,2,3,4}){s=raw().put("screen_state",screen).put("actual_nit",0).put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(16000,16383).put("driver_actual",0),"local:1");near(s.getDouble("actual_nit"),0);check(s.getInt("actual_node")==0&&!s.has("actual_node_fallback_reason"));}
  s=new JSONObject().put("screen_state",1).put("actual_nit",123).put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(16000,16383).put("driver_actual",0),"local:1");near(s.getDouble("actual_nit"),0);
  s=raw().put("screen_state",2).put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(16000,16383).put("driver_actual",0).put("bl_power",4),"local:1");near(s.getDouble("actual_nit"),0);check(!s.has("actual_node_fallback_reason"));
  s=new JSONObject().put("screen_state",2).put("actual_nit",123).put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(16000,16383).put("driver_actual",0).put("bl_power",4),"local:1");near(s.getDouble("actual_nit"),0);
  s=raw().put("screen_state",2).put("screen_nit_calibration",table);ScreenBrightnessReading.apply(s,node(16000,16383).put("driver_actual",8192),"local:1");check(s.getInt("actual_node")==8192&&s.getString("actual_node_source").equals("driver_actual_brightness"));
  s=raw().put("screen_state",2);ScreenBrightnessReading.apply(s,node(1443,16383).put("driver_actual",0),"local:1");check(!s.has("actual_nit")&&s.getInt("actual_node")==1443); // no nit extrapolation
  // Last handle remains monotonic, downwards only. Dense and saved baselines keep their original points.
  float[] base={2,20,200,1000},x={0,30,600,5000},one={1,1,1,1};float[] range=CurveEditor.bounds(3,base,1,1000,one);near(range[0],.2);near(range[1],1);
  for(int i=0;i<=100;i++){float[] f=one.clone();f[3]=CurveEditor.clamp(3,.1f+i*.01f,base,1,1000,one);CurvePlan p=new CurvePlan(x,base,1,1000,f);check(p.at(5000)>=p.at(600)&&p.at(5000)<=1000);}
  float[] f={1,1,1,.6f};TraditionalCurve dense=new TraditionalCurve(new float[]{0,10,30,100,600,1000,5000},new float[]{2,9,20,60,200,500,1000});float[] lowered=dense.reshape(1,1000,f);near(lowered[6],600);for(int i=1;i<lowered.length;i++)check(lowered[i]>=lowered[i-1]);
  SavedCurve saved=new SavedCurve(new float[]{0,10,30,100,600,1000,5000},new float[]{2,9,20,60,200,500,1000});CurvePlan loweredSaved=SavedCurve.plan(saved,x,base,1,1000,f,0);check(loweredSaved.lux().length==7);near(loweredSaved.at(5000),600);
  if(args.length>0){JSONObject capture=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8)),fresh=capture.getJSONObject("fresh_runtime"),broken=capture.getJSONObject("annotated_runtime"),realNode=capture.getJSONObject("node");String physical=fresh.getString("screen_nit_physical");double originalNit=fresh.getDouble("actual_nit");
   ScreenBrightnessReading.apply(fresh,realNode,physical);near(fresh.getDouble("actual_nit"),originalNit);check(fresh.getInt("actual_node")>0&&fresh.getInt("actual_node_driver")==0);check(fresh.getString("actual_nit_source").equals("device_backlight_mapping"));
   ScreenBrightnessReading.apply(broken,realNode,physical);check(broken.getDouble("actual_nit")>0&&broken.getInt("actual_node")>0);fresh.getJSONObject("brightness_control").put("owner","raw_panel");ScreenBrightnessReading.apply(fresh,realNode,physical);check(fresh.getDouble("actual_nit")>0&&fresh.getInt("actual_node")==realNode.getInt("actual"));check(fresh.getString("actual_nit_source").equals("node_readback_device_calibration"));System.out.println("Captured zero-feedback report replay: PASS; private input, no device execution");
  }
  System.out.println("Screen brightness readback: "+cases+" cases PASS; host only, no photometric/device claim");
 }
}'''}
files=[]
for name,t in sources.items():p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(t,'utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in ['ScreenBrightnessReading','CurveEditor','CurvePlan','TraditionalCurve','SavedCurve']],*map(str,files)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.ReadingTest',*sys.argv[1:]],check=True)
