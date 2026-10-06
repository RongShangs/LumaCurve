"""Check actual two-curve graph data against drafts, memory and active backend routes."""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-curve-comparison-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
test=O/'CurveComparisonTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
public class CurveComparisonTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void eq(float a,float b){check(Math.abs(a-b)<.001);}
 static JSONObject runtime()throws Exception{return new JSONObject().put("curve_backend","refactor").put("factory_lux",new JSONArray("[0,30,600,100000]")).put("factory_logical_nit",new JSONArray("[2,100,300,1060]")).put("min_logical_nit",1).put("max_logical_nit",1060).put("active_logical_nit",new JSONArray("[2,110,300,1060]")).put("current_anchors_lux",new JSONArray("[0,15,30,600,100000]")).put("current_anchors_nit",new JSONArray("[2,50,90,300,1060]"));}
 static void bad(float[] x,float[] y){try{new CurveComparison.Line(x,y);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  JSONObject r=runtime();String before=r.toString();float[] factors={1,1.2f,1,1};CurveComparison c=CurveComparison.read(r,factors,0,true);
  eq(c.reference.at(30),100);eq(c.baseline.at(30),120);eq(c.current.at(30),90);eq(c.current.at(15),50);eq(c.current.at(0),2);eq(c.current.at(200000),1060);check(r.toString().equals(before));
  c=CurveComparison.read(r,factors,0,false);eq(c.baseline.at(30),110);eq(c.current.at(30),90);
  // Editing a floor changes only the draft, not the learned curve or the live config.
  c=CurveComparison.read(r,factors,20,true);eq(c.baseline.at(0),20);eq(c.current.at(0),2);check(r.toString().equals(before));
  r.remove("active_logical_nit");c=CurveComparison.read(r,factors,0,false);eq(c.baseline.at(30),100);
  r.remove("current_anchors_nit");c=CurveComparison.read(r,factors,0,true);check(c.current==null);eq(c.baseline.at(30),120);
  r=runtime();r.put("current_anchors_nit",new JSONArray("[1,2]"));check(CurveComparison.read(r,factors,0,true).current==null);
  r=runtime();r.put("current_anchors_lux",new JSONArray("[0,15,15,600,100000]"));check(CurveComparison.read(r,factors,0,true).current==null);
  r=runtime();r.put("current_anchors_nit",new JSONArray("[2,50,\"90\",300,1060]"));check(CurveComparison.read(r,factors,0,true).current==null);
  r=runtime();r.put("last_pipeline",new JSONObject().put("route","good_curve"));check(CurveComparison.read(r,factors,0,false).current==null);
  r.put("last_pipeline",new JSONObject().put("route","refactor"));check(CurveComparison.read(r,factors,0,false).current!=null);
  r=runtime();r.put("curve_backend","physical_mapping").put("physical_mapping_active",true).put("factory_full_lux",new JSONArray("[0,5,30,100,600,1000,100000]")).put("factory_full_nit",new JSONArray("[2,20,100,150,300,500,1060]"));
  c=CurveComparison.read(r,factors,0,true);check(c.reference.lux.length==7);eq(c.reference.at(30),100);check(c.baseline.lux.length==7);eq(c.baseline.at(30),120);eq(c.current.at(30),90);eq(c.baseline.at(100000),1060);
  c=CurveComparison.read(r,factors,0,false);eq(c.baseline.at(30),110);eq(c.current.at(30),90);check(c.baseline.lux.length==7);
  r.put("physical_mapping_active",false);check(CurveComparison.read(r,factors,0,false).current==null);r.put("physical_mapping_active",true).put("last_pipeline",new JSONObject().put("route","mapping"));check(CurveComparison.read(r,factors,0,false).current!=null);
  float[] x={0,30},y={2,100};CurveComparison.Line line=new CurveComparison.Line(x,y);x[1]=90;y[1]=999;eq(line.at(30),100);eq(line.at(15),51);
  bad(new float[]{0,0},new float[]{2,3});bad(new float[]{-1,2},new float[]{2,3});bad(new float[]{0,Float.NaN},new float[]{2,3});bad(new float[]{0,1},new float[]{2,Float.POSITIVE_INFINITY});bad(new float[]{0,1},new float[]{2,-1});bad(new float[]{0,1},new float[]{2});bad(new float[]{0},new float[]{2});
  // Overlapping curves stay two separate datasets, even with no live memory anchors.
  r=runtime();r.put("current_anchors_lux",r.getJSONArray("factory_lux")).put("current_anchors_nit",r.getJSONArray("active_logical_nit"));c=CurveComparison.read(r,factors,0,false);check(c.current!=null&&c.baseline!=c.current);eq(c.baseline.at(30),c.current.at(30));
  // State styling depends on the applied baseline and live memory, never drafts or archives.
  r=runtime();c=CurveComparison.read(r,factors,0,false);check(!c.systemDefault());check(!c.memoryChanged(r));
  r.put("memory_saved_anchors",new JSONArray("[{\"lux\":30,\"display_nit\":90}]"));check(!c.memoryChanged(r));
  r.put("memory_live_points",new JSONArray("[{\"lux\":30,\"display_nit\":90}]"));check(c.memoryChanged(r));
  r.put("active_logical_nit",r.getJSONArray("factory_logical_nit"));c=CurveComparison.read(r,factors,0,false);check(c.systemDefault());check(c.memoryChanged(r));
  r.put("current_anchors_lux",r.getJSONArray("factory_lux")).put("current_anchors_nit",r.getJSONArray("factory_logical_nit"));c=CurveComparison.read(r,factors,0,false);check(c.systemDefault());check(!c.memoryChanged(r));
  r=runtime();r.put("manual_anchor_flags",new JSONArray("[false,true,false,false,false]"));c=CurveComparison.read(r,factors,0,false);check(c.memoryChanged(r));
  r.remove("manual_anchor_flags");r.put("memory_live_count",1);check(c.memoryChanged(r));r.put("memory_live_count",0);check(!c.memoryChanged(r));
  r.put("memory_live_points",new JSONArray("[{\"lux\":-1}]"));check(!c.memoryChanged(r));
  r.put("memory_live_points",new JSONArray("[{\"lux\":30}]")).put("current_anchors_lux",r.getJSONArray("factory_lux")).put("current_anchors_nit",new JSONArray("[2,110.00005,300,1060]"));c=CurveComparison.read(r,factors,0,false);check(!c.memoryChanged(r));
  r.remove("current_anchors_nit");c=CurveComparison.read(r,factors,0,false);check(!c.memoryChanged(r));check(c.current==null);
  // The traditional default stays default even though the OEM interpolates between its knots.
  r=runtime();r.remove("active_logical_nit");r.put("curve_backend","physical_mapping").put("physical_mapping_active",true).put("factory_full_lux",new JSONArray("[0,5,30,100,600,1000,100000]")).put("factory_full_nit",new JSONArray("[2,20,100,150,300,500,1060]"));c=CurveComparison.read(r,factors,0,false);check(c.systemDefault());check(!c.memoryChanged(r));
  System.out.println("Curve comparison: "+cases+" cases PASS; production graph data, Android rendering not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in ['CurveComparison','CurvePlan','TraditionalCurve']],str(test)],check=True)
subprocess.run(['java','-cp',str(classes)+';'+str(J),'top.rongshangs.lumacurve.refactor.CurveComparisonTest'],check=True)
