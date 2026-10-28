"""Production calibration-domain route, wire tables and engine with framework doubles."""
from pathlib import Path
import os, subprocess, sys

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/control-center-calibration-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
 'top/rongshangs/lumacurve/refactor/ControlCenterPartition.java':'package top.rongshangs.lumacurve.refactor;class ControlCenterPartition{static org.json.JSONObject read(Object o,org.json.JSONObject c){throw new IllegalArgumentException("no partition in coordinate fixture");}}',
 'top/rongshangs/lumacurve/refactor/ControlCenterNodes.java':'''package top.rongshangs.lumacurve.refactor;class ControlCenterNodes{ControlCenterNodes(ControlCenterEngine e){}void start(){}void deactivate(){}void deactivate(boolean restore){}void close(){}boolean supported(){return false;}String physical(){return "";}org.json.JSONObject status(){return new org.json.JSONObject();}}''',
'com/android/server/display/config/EvenDimmerBrightnessData.java':'package com.android.server.display.config;public class EvenDimmerBrightnessData{}',
'android/util/Spline.java':'''package android.util;
public abstract class Spline{
 public abstract float interpolate(float x);
 public static class LinearSpline extends Spline{public float[] mX,mY;public LinearSpline(float[] x,float[] y){mX=x.clone();mY=y.clone();}public float interpolate(float x){if(x<=mX[0])return mY[0];int n=mX.length-1;if(x>=mX[n])return mY[n];for(int i=1;i<=n;i++)if(x<=mX[i])return mY[i-1]+(mY[i]-mY[i-1])*(x-mX[i-1])/(mX[i]-mX[i-1]);throw new AssertionError();}}
 public static class MonotoneCubicSpline extends LinearSpline{public MonotoneCubicSpline(float[] x,float[] y){super(x,y);}}
}''',
'com/android/server/display/DisplayDeviceConfig.java':'''package com.android.server.display;
import android.util.Spline;
public class DisplayDeviceConfig{
 public float[] mBrightness,mBacklight,mNits;public com.android.server.display.config.EvenDimmerBrightnessData mEvenDimmerBrightnessData;public Spline mBrightnessToBacklightSpline,mBacklightToBrightnessSpline,mNitsToBacklightSpline;public boolean middleFlat,middleReverse;
 public DisplayDeviceConfig(){set(new float[]{.02f,.2f,.6f,.9f},new float[]{.01f,.15f,.7f,1},new float[]{1,50,300,800});}
 public void set(float[] x,float[] y,float[] n){mBrightness=x;mBacklight=y;mNits=n;mBrightnessToBacklightSpline=new Spline.LinearSpline(x,y);mBacklightToBrightnessSpline=new Spline.LinearSpline(y,x);mNitsToBacklightSpline=new Spline.LinearSpline(n,y);}
 public float getBacklightFromBrightness(float x){if(middleReverse&&x>.3f&&x<.4f)return .1f;if(middleFlat&&x>.3f&&x<.4f)return .25f;return mBrightnessToBacklightSpline.interpolate(x);}
 public float getBrightnessFromBacklight(float x){return mBacklightToBrightnessSpline.interpolate(x);}
 public float getBacklightFromNits(float x){return mNitsToBacklightSpline.interpolate(x);}
}''',
'com/android/server/display/RefactorBrightnessUtil.java':'''package com.android.server.display;
public class RefactorBrightnessUtil{
 public static float[] mLogicalBrightnessSet;public static boolean badInverse;
 static{reset();}public static void reset(){mLogicalBrightnessSet=new float[101];for(int i=0;i<=100;i++)mLogicalBrightnessSet[i]=100*i;badInverse=false;}
 public static float sliderToLogicalBrightness(float x){if(x==1)return mLogicalBrightnessSet[100];float p=x*100;int i=(int)p;return mLogicalBrightnessSet[i]+(mLogicalBrightnessSet[i+1]-mLogicalBrightnessSet[i])*(p-i);}
 public static float logicalBrightnessToSlider(float n){if(badInverse)return .5f;for(int i=0;i<100;i++)if(n>=mLogicalBrightnessSet[i]&&n<=mLogicalBrightnessSet[i+1])return (i+(n-mLogicalBrightnessSet[i])/(mLogicalBrightnessSet[i+1]-mLogicalBrightnessSet[i]))/100;return .01f;}
}''',
'com/android/server/display/Owner.java':'package com.android.server.display;public class Owner{public Object mDisplayDeviceConfig=new DisplayDeviceConfig();}',
'android/os/Process.java':'package android.os;public class Process{public static int myPid(){return 42;}}',
'android/provider/Settings.java':'package android.provider;public class Settings{public static class Global{public static String getString(Object ignored,String key){return "1";}}}',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;
class HookRuntime{final Object owner;final Control brightnessControl=new Control();final Writer stateWriter=new Writer();final Context context=new Context();HookRuntime(Object o){owner=o;}static String processStart(){return "start";}static class Context{Object getContentResolver(){return this;}}static class Control{Options options=new Options();boolean mainDisplay(){return true;}}static class Options{int nodeSliderMode;}static class Writer{java.util.Map<String,String> values=new java.util.HashMap<>();synchronized void submit(String group,java.util.Map<String,String> data){if(!group.equals("slider_tables"))throw new AssertionError("unexpected setting write");values.putAll(data);}}}''',
}
for name,text in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
p=O/'top/rongshangs/lumacurve/refactor/CalibrationTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import com.android.server.display.*;import android.util.Spline;import org.json.*;import java.util.concurrent.*;
public class CalibrationTest{
 static int cases;static void check(boolean yes){if(!yes)throw new AssertionError("case "+cases);cases++;}static void near(double a,double b){check(Math.abs(a-b)<.00005);}
 static void reject(Runnable action){try{action.run();throw new AssertionError("unexpected success "+cases);}catch(IllegalArgumentException expected){cases++;}}
 interface Throwing{void run()throws Exception;}static void unavailable(Throwing action)throws Exception{try{action.run();throw new AssertionError("unexpected compatibility "+cases);}catch(IllegalArgumentException|IllegalStateException expected){cases++;}}
 static void drain()throws Exception{ControlCenterEngine.IO.submit(()->{}).get(5,TimeUnit.SECONDS);}
 static void exercise(Owner owner)throws Exception{
  DisplayDeviceConfig config=(DisplayDeviceConfig)owner.mDisplayDeviceConfig;JSONObject data=ControlCenterCalibration.read(owner);check(data.getString("profile").equals("coordinate_v2_calibrated"));check(data.has("oem_v1"));
  ControlCenterCurve standard=ControlCenterCurve.read(data.getJSONObject("standard")),v1=ControlCenterCurve.read(data.getJSONObject("oem_v1"));
  check(standard.input[0]==config.mBrightness[0]&&standard.input[standard.input.length-1]==config.mBrightness[config.mBrightness.length-1]);check(data.getJSONObject("standard").has("range"));
  for(int i=0;i<=1000;i++){
   double b=i/1000d;near(standard.toNode(b),config.getBacklightFromBrightness((float)b));near(standard.toInput(standard.toNode(b)),Math.max(standard.input[0],Math.min(standard.input[standard.input.length-1],b)));
   double target=i/1000d;near(standard.toNode(standard.toInput(target)),Math.max(config.mBacklight[0],Math.min(config.mBacklight[config.mBacklight.length-1],target)));
   near(v1.toNode(b),config.getBacklightFromNits(RefactorBrightnessUtil.sliderToLogicalBrightness((float)b)));
   near(v1.toNode(v1.toInput(target)),Math.max(config.mBacklight[0],Math.min(config.mBacklight[config.mBacklight.length-1],target)));
  }
 }
 public static void main(String[] args)throws Exception{
  Owner owner=new Owner();exercise(owner);
  // Other actual endpoint ranges, including only-low / only-high and identity-like mappings.
  for(float[] bounds:new float[][]{{.00036625599f,1},{0,.85f},{.12f,.98f}}){DisplayDeviceConfig c=new DisplayDeviceConfig();c.set(new float[]{bounds[0],.4f,bounds[1]},new float[]{.01f,.5f,1},new float[]{1,400,3500});owner.mDisplayDeviceConfig=c;exercise(owner);}
  // Unclipped full-domain original route gains no new table or spline requirements.
  class Basic{public float getBacklightFromBrightness(float x){return x;}}owner.mDisplayDeviceConfig=new Basic();JSONObject direct=ControlCenterCalibration.read(owner);check(direct.getString("profile").equals("coordinate_v2")&&!direct.has("oem_v1"));
  ControlCenterCurve old=ControlCenterCurve.sample(x->x*x);check(!old.json().has("range"));near(ControlCenterCurve.read(old.json()).toInput(.25),.5);
  for(ControlCenterCurve.Probe bad:new ControlCenterCurve.Probe[]{x->Math.max(.02,Math.min(.9,x)),x->0,x->1-x})unavailable(()->ControlCenterCurve.sample(bad));
  JSONObject bounded=new JSONObject().put("input",new JSONArray().put(.02).put(.9)).put("node",new JSONArray().put(.01).put(1));unavailable(()->ControlCenterCurve.read(bounded));bounded.put("range",new JSONArray().put(.02).put(.9));near(ControlCenterCurve.read(bounded).toInput(0),.02);near(ControlCenterCurve.read(bounded).toNode(1),1);
  for(JSONArray bad:new JSONArray[]{new JSONArray().put(.01).put(.9),new JSONArray().put(".02").put(.9),new JSONArray().put(.02),new JSONArray().put(.02).put(.9).put(1)}){bounded.put("range",bad);unavailable(()->ControlCenterCurve.read(bounded));}
  for(int i=0;i<8;i++){
   DisplayDeviceConfig c=new DisplayDeviceConfig();owner.mDisplayDeviceConfig=c;
   switch(i){case 0:c.mEvenDimmerBrightnessData=new com.android.server.display.config.EvenDimmerBrightnessData();break;case 1:((Spline.LinearSpline)c.mBrightnessToBacklightSpline).mY[1]=.3f;break;case 2:c.mBrightness[1]=c.mBrightness[0];break;case 3:c.mBacklight[0]=Float.NaN;break;case 4:c.mBacklight=new float[]{.01f,1};break;case 5:c.middleReverse=true;break;case 6:c.middleFlat=true;break;case 7:c.mBrightnessToBacklightSpline=new Spline(){public float interpolate(float x){return Math.max(.01f,x);}};break;}
   final Owner current=owner;unavailable(()->ControlCenterCalibration.read(current));
  }
  // A broken optional Refactor route cannot remove a verified standard route or fake V1 support.
  for(int i=0;i<4;i++){owner.mDisplayDeviceConfig=new DisplayDeviceConfig();RefactorBrightnessUtil.reset();switch(i){case 0:RefactorBrightnessUtil.badInverse=true;break;case 1:RefactorBrightnessUtil.mLogicalBrightnessSet[50]=RefactorBrightnessUtil.mLogicalBrightnessSet[49];break;case 2:RefactorBrightnessUtil.mLogicalBrightnessSet=new float[102];break;case 3:((DisplayDeviceConfig)owner.mDisplayDeviceConfig).mNits[1]=Float.NaN;break;}JSONObject data=ControlCenterCalibration.read(owner);check(data.has("standard")&&!data.has("oem_v1")&&data.has("oem_v1_detail"));}
  if(args.length>0){JSONObject fixture=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8));float[][] arrays=new float[3][];String[] keys={"brightness","backlight","nits"};for(int k=0;k<3;k++){JSONArray a=fixture.getJSONArray(keys[k]);arrays[k]=new float[a.length()];for(int j=0;j<a.length();j++)arrays[k][j]=(float)a.getDouble(j);}DisplayDeviceConfig actual=new DisplayDeviceConfig();actual.set(arrays[0],arrays[1],arrays[2]);owner.mDisplayDeviceConfig=actual;RefactorBrightnessUtil.reset();exercise(owner);}
  RefactorBrightnessUtil.reset();owner.mDisplayDeviceConfig=new DisplayDeviceConfig();JSONObject before=ControlCenterCalibration.read(owner);check(before.has("oem_v1"));
  HookRuntime runtime=new HookRuntime(owner);ControlCenterEngine engine=new ControlCenterEngine(runtime);engine.start();drain();check(engine.supported());drain();JSONObject status=engine.status();check(status.getString("calibration_profile").equals("coordinate_v2_calibrated")&&status.getBoolean("oem_v1_ready")&&!engine.unsupported);
  JSONObject config=new JSONObject().put("enabled",true).put("control_center_node_mode",2).put("revision","r").put("user_serial",0);engine.configure(config);String original=config.toString();check(new JSONObject(runtime.stateWriter.values.get(ControlCenterEngine.TABLES)).getBoolean("enabled"));
  DisplayDeviceConfig broken=new DisplayDeviceConfig();broken.middleFlat=true;owner.mDisplayDeviceConfig=broken;engine.calibrate();drain();check(!engine.waiting()&&engine.unsupported&&engine.status().getString("compatibility_state").equals("unsupported"));check(runtime.stateWriter.values.get(ControlCenterEngine.TABLES)==null&&config.toString().equals(original));
  owner.mDisplayDeviceConfig=new DisplayDeviceConfig();engine.calibrate();drain();check(engine.waiting()&&engine.status().getString("compatibility_state").equals("available"));engine.deactivate();check(!new JSONObject(runtime.stateWriter.values.get(ControlCenterEngine.TABLES)).getBoolean("enabled"));engine.close();check(!engine.registered);ControlCenterEngine.IO.shutdownNow();
  System.out.println("Control center calibration domain: "+cases+" cases PASS; actual adapters and engine, synthetic framework; no Android device execution");
 }
}''',encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
sources=[*O.rglob('*.java'),*[S/(n+'.java') for n in ['ControlCenterCurve','ControlCenterCalibration','ControlCenterEngine','AppBuild']]]
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(p) for p in sources]],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.CalibrationTest',*sys.argv[1:]],check=True,timeout=20)
