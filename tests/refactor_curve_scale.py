"""Ordinary manual scale, coordinate isolation and full-domain round trips; no output writes."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/curve-scale-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
stub=O/'ControlCenterCalibration.java';stub.write_text('''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;import org.json.*;
class ControlCenterCalibration{static Object configuration(Object owner){return ((ScaleTest.Owner)owner).config;}static Method method(Class<?> c,String name,Class<?> type,Class<?>... args)throws Exception{Method m=c.getMethod(name,args);if(m.getReturnType()!=type)throw new IllegalArgumentException();return m;}static float value(Method m,Object o,float x)throws Exception{return (Float)m.invoke(o,x);}}
class ControlCenterPartition{static JSONObject boundary(Object o)throws Exception{return new JSONObject().put("profile","ddc_hbm_transition").put("brightness",((ScaleTest.Owner)o).config.boundary);}}''','utf-8')
u=O/'com/android/server/display/RefactorBrightnessUtil.java';u.parent.mkdir(parents=True,exist_ok=True);u.write_text('package com.android.server.display;public class RefactorBrightnessUtil{public static float end=1060;public static float sliderToLogicalBrightness(float f){return f*end;}}','utf-8')
p=O/'ScaleTest.java';p.write_text(r'''package top.rongshangs.lumacurve.refactor;import org.json.*;public class ScaleTest{
static int cases;static void check(boolean b){if(!b)throw new AssertionError("scale "+cases);cases++;}static void eq(float a,float b){check(Math.abs(a-b)<=Math.max(.002,Math.ulp(b)*8));}
public static class Config{public float boundary=.25f,multiplier=1;public float getBacklightFromBrightness(float v){return v*multiplier;}public float getNitsFromBacklight(float v){return v<=.75f?v*2400:1800+(v-.75f)*5600;}}
static class Owner{Config config=new Config();}
public static void main(String[] args)throws Exception{
 Owner o=new Owner();JSONObject range=CurveDisplayRange.capture(o,"physical_mapping",2,3200);eq((float)range.getDouble("maximum_nit"),600);
 JSONObject runtime=new JSONObject().put("curve_backend","physical_mapping").put("curve_coordinate","physical_nit").put("min_logical_nit",2).put("curve_display_range",range);eq(CurveDisplayRange.maximum(runtime,3200),600);
 for(String mapper:new String[]{"MiuiPhysicalBrightnessMappingStrategy","PhysicalMappingStrategy"}){runtime.put("physical_mapper_class",mapper);eq(CurveDisplayRange.maximum(runtime,3200),600);runtime.put("brightness_control",new JSONObject().put("range",new JSONObject().put("maximum",.1)));runtime.put("thermal_last_cap",.01);eq(CurveDisplayRange.maximum(runtime,3200),600);}
 for(String backend:new String[]{"refactor","refactor_coefficients"}){runtime.put("curve_backend","refactor").put("curve_route",backend).put("curve_coordinate","logical_nit");range=CurveDisplayRange.capture(o,"refactor",1,1060);runtime.put("curve_display_range",range);eq(CurveDisplayRange.maximum(runtime,1060),1060);eq(CurveDisplayRange.percent(1060,1060,1060),100);}
 runtime.put("curve_backend","physical_mapping");eq(CurveDisplayRange.maximum(runtime,3200),3200);runtime.put("curve_coordinate","physical_nit").put("curve_display_range",CurveDisplayRange.capture(o,"physical_mapping",2,3200));eq(CurveDisplayRange.maximum(runtime,3200),600);
 for(Object bad:new Object[]{-1,0,JSONObject.NULL,3300,"wrong"}){runtime.getJSONObject("curve_display_range").put("maximum_nit",bad);eq(CurveDisplayRange.maximum(runtime,3200),3200);}
 for(float full:new float[]{1060,3200,10000})for(float scale:new float[]{300,600,1060})if(scale<=full){for(int i=0;i<=1000;i++){float value=full*i/1000f,percent=CurveDisplayRange.percent(value,full,scale);eq(CurveDisplayRange.fromPercent(percent,full,scale),value);}eq(CurveDisplayRange.percent(scale,full,scale),100);eq(CurveDisplayRange.fromPercent(100,full,scale),scale);eq(CurveDisplayRange.fromPercent(CurveDisplayRange.percent(full,full,scale),full,scale),full);}
 o.config.multiplier=.5f;eq((float)CurveDisplayRange.capture(o,"physical_mapping",2,3200).getDouble("maximum_nit"),300);o.config.boundary=0;try{CurveDisplayRange.capture(o,"physical_mapping",2,3200);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
 for(float bad:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY,534})try{CurveDisplayRange.fromPercent(bad,3200,600);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
 System.out.println("Curve manual scale: "+cases+" cases PASS; actual coordinate adapter/policy, logical and physical ABI doubles, not device brightness measurements");}}
''','utf-8')
C=O/'classes';C.mkdir(exist_ok=True);subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(stub),str(u),str(p),str(S/'CurveDisplayRange.java'),str(S/'CurveEditor.java'),str(S/'CurvePlan.java')],check=True);subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.ScaleTest'],check=True)
# All graph instances, drag and numeric input use the same scale; storage still uses full maximum.
main=(S/'MainActivity.java').read_text('utf-8');assert 'float curveScale(){return CurveDisplayRange.maximum(runtime,maximum);}' in main
assert 'nit/curveScale()' in main and 'CurveDisplayRange.percent(current,maximum,curveScale())' in main and 'CurveDisplayRange.fromPercent(Float.parseFloat(entered),maximum,curveScale())' in main
assert 'CurveEditor.edit(point,requested,base,minimum,maximum' in main
