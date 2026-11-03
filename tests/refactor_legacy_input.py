"""Production sensor binding ABI regression, with independent host-only Android doubles."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/legacy-input-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
sources={
'android/hardware/Sensor.java':'''package android.hardware;public class Sensor{public static final int TYPE_LIGHT=5,TYPE_DEVICE_PRIVATE_BASE=65536,REPORTING_MODE_ON_CHANGE=1;public int type=5,id;public String kind="";public Sensor(int t,int i){type=t;id=i;}public int getType(){return type;}public int getHandle(){return id;}public String getName(){return "host ALS";}public String getStringType(){return kind;}public boolean isWakeUpSensor(){return false;}public int getReportingMode(){return 1;}public String getRequiredPermission(){return "";}public float getMaximumRange(){return 100000;}public float getResolution(){return 1;}}''',
'android/hardware/SensorManager.java':'package android.hardware;public class SensorManager{}',
'android/hardware/SensorEvent.java':'package android.hardware;public class SensorEvent{public float[] values;public SensorEvent(float f){values=new float[]{f};}}',
'com/android/server/display/DualSensorPolicy.java':'''package com.android.server.display;public class DualSensorPolicy{public Object mBrightnessControllerImpl;public android.hardware.SensorManager mSensorManager;public android.hardware.Sensor mAssistLightSensor=new android.hardware.Sensor(5,2);public float mMainFastAmbientLux,mAssistFastAmbientLux;private void handleAssistLightSensorEvent(long time,float lux){}protected void updateMainLuxStatus(float lux){}}''',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;class HookEntry{static Object get(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(n);}}''',
'top/rongshangs/lumacurve/refactor/LegacyInputTest.java':r'''package top.rongshangs.lumacurve.refactor;
import android.hardware.*;import com.android.server.display.DualSensorPolicy;
public class LegacyInputTest{
 static int cases;interface Work{void run()throws Exception;}static void check(boolean v){if(!v)throw new AssertionError("input "+cases);cases++;}static void bad(Work w)throws Exception{try{w.run();throw new AssertionError("invalid binding accepted "+cases);}catch(ReflectiveOperationException|IllegalArgumentException|IllegalStateException|NullPointerException expected){cases++;}}
 public static class Owner{int mDisplayId;Impl mAutomaticBrightnessControllerImpl=new Impl();}
 public static class Impl{ABC mAutomaticBrightnessController=new ABC();Object mDualSensorPolicy;}
 public static class ABC{Sensor mLightSensor=new Sensor(5,1);SensorManager mSensorManager=new SensorManager();}
 public static class Scaled{Sensor mAssistLightSensor=new Sensor(33171055,2);float mFovAmplifyFactor=2,mAssistFovAmplifyFactor=3;}
 public static class Partial{Sensor mAssistLightSensor;Float mFovAmplifyFactor=null;}
 public static class NullScaled{Sensor mAssistLightSensor;Float mFovAmplifyFactor=null,mAssistFovAmplifyFactor=null;}
 public static class Unknown{Sensor mAssistLightSensor;}
 static DualSensorPolicy bind(Owner o){DualSensorPolicy p=new DualSensorPolicy();p.mBrightnessControllerImpl=o.mAutomaticBrightnessControllerImpl;p.mSensorManager=o.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController.mSensorManager;o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy=p;return p;}
 public static void main(String[] args)throws Exception{
  Owner o=new Owner();DualSensorPolicy p=bind(o);BrightnessSensorAccess.Binding b=BrightnessSensorAccess.read(o);check(b.scaleRoute.equals("legacy_dual_unit_lux")&&b.mainScale==1&&b.assistScale==1&&b.assist==p.mAssistLightSensor);
  for(int i=0;i<120;i++){float lux=i*13.5f;check(BrightnessSensorAccess.mainValue(b,new SensorEvent(lux))==lux);}check(BrightnessSensorAccess.mainValue(b,new SensorEvent(0))==0);
  check(BrightnessSensorAccess.evidence(o).getString("lux_scale_route").equals("legacy_dual_unit_lux"));check(!BrightnessSensorAccess.evidence(o).has("input_detail"));
  p.mBrightnessControllerImpl=new Object();bad(()->BrightnessSensorAccess.read(o));p.mBrightnessControllerImpl=o.mAutomaticBrightnessControllerImpl;
  p.mSensorManager=new SensorManager();bad(()->BrightnessSensorAccess.read(o));p.mSensorManager=o.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController.mSensorManager;
  b.fov=true;bad(()->BrightnessSensorAccess.legacyUnitLux(b));b.fov=false;
  o.mDisplayId=1;bad(()->BrightnessSensorAccess.read(o));o.mDisplayId=0;
  p.mAssistLightSensor=o.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController.mLightSensor;bad(()->BrightnessSensorAccess.read(o));p.mAssistLightSensor=new Sensor(5,2);
  o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy=new Unknown();bad(()->BrightnessSensorAccess.read(o));
  o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy=new Partial();bad(()->BrightnessSensorAccess.read(o));
  o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy=new NullScaled();bad(()->BrightnessSensorAccess.read(o));
  Scaled s=new Scaled();o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy=s;BrightnessSensorAccess.Binding scaled=BrightnessSensorAccess.read(o);check(scaled.mainScale==2&&scaled.assistScale==3&&scaled.scaleRoute.equals("native_amplified_lux"));check(BrightnessSensorAccess.mainValue(scaled,new SensorEvent(20))==40);
  s.mFovAmplifyFactor=Float.NaN;bad(()->BrightnessSensorAccess.read(o));s.mFovAmplifyFactor=0;bad(()->BrightnessSensorAccess.read(o));s.mFovAmplifyFactor=2;s.mAssistFovAmplifyFactor=-1;bad(()->BrightnessSensorAccess.read(o));
  o.mAutomaticBrightnessControllerImpl.mDualSensorPolicy=null;check(BrightnessSensorAccess.read(o).scaleRoute.equals("single_unit_lux"));
  System.out.println("Legacy input: "+cases+" cases PASS; host only, original scaled and single/FOV paths preserved");
 }
}'''}
paths=[]
for name,value in sources.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,'utf-8');paths.append(str(p))
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*paths,str(S/'BrightnessSensorAccess.java'),str(S/'AuxiliarySensorIdentity.java')],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.LegacyInputTest'],check=True)
