"""Production snapshot/manager boundary: native differences converge; legacy data is additive."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/curve-boundary-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'BoundaryTest.java';p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
public class BoundaryTest {
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("boundary "+cases);cases++;}
 interface Task{void run()throws Exception;}static void bad(Task t)throws Exception{try{t.run();throw new AssertionError("invalid contract accepted");}catch(IllegalArgumentException expected){cases++;}}
 static JSONObject state(String backend,boolean coefficients)throws Exception{return new JSONObject().put("curve_backend",backend).put("curve_route",coefficients?"refactor_coefficients":backend).put("curve_coordinate",backend.equals("physical_mapping")?"physical_nit":"logical_nit").put("phase","active").put("factory_lux",new JSONArray("[0,30,600,5000]")).put("factory_logical_nit",new JSONArray("[2,20,100,1000]")).put("factory_full_lux",new JSONArray("[0,5,30,600,1000,5000]")).put("factory_full_nit",new JSONArray("[2,5,20,100,300,1000]")).put("current_anchors_lux",new JSONArray("[0,30,600,5000]")).put("current_anchors_nit",new JSONArray("[2,25,110,1000]")).put("min_logical_nit",1).put("max_logical_nit",1000).put("memory_live_count",1).put("physical_mapping_active",true);}
 static JSONObject modern(JSONObject r)throws Exception{CurveCompatibility.Profile old=CurveCompatibility.read(r);r.put(CurveCompatibility.KEY,CurveCompatibility.descriptor(old.coordinate,old.interpolation,old.description,old.strict,old.currentAvailable,old.save,old.savedLimit,old.liveCount));return r;}
 static class Backend extends CurveBackend {
  Backend(){super(1,1000);factoryLux=new float[]{0,30,600,5000};factoryNit=new float[]{2,20,100,1000};}
  public Object get(String n){throw new AssertionError("shared backend read native field "+n);}public CurvePlan plan(){return null;}public void configure(float[] f){}public void clearMemory(){}public float currentAt(float x){return x;}public String name(){return "opaque";}
 }
 public static void main(String[] args)throws Exception{
  float[] factors={1,1.1f,1.2f,1};
  for(String backend:new String[]{"physical_mapping","refactor"})for(boolean coeff:new boolean[]{false,true}){
   if(coeff&&backend.equals("physical_mapping"))continue;
   JSONObject old=state(backend,coeff),current=modern(new JSONObject(old.toString()));
   for(boolean draft:new boolean[]{false,true})for(SavedCurve saved:new SavedCurve[]{null,new SavedCurve(new float[]{0,30,600,5000},new float[]{2,22,120,1000})}){
    CurveComparison a=CurveComparison.read(old,factors,0,draft,saved),b=CurveComparison.read(current,factors,0,draft,saved);
    check(CurveComparison.sameCurve(a.reference,b.reference)&&CurveComparison.sameCurve(a.baseline,b.baseline)&&CurveComparison.sameCurve(a.current,b.current));
    // Native identity changes cannot select branches after a valid capability contract exists.
    JSONObject renamed=new JSONObject(current.toString()).put("curve_backend","future_device").put("curve_route","unrelated");CurveComparison c=CurveComparison.read(renamed,factors,0,draft,saved);check(CurveComparison.sameCurve(b.baseline,c.baseline));
   }
   check(CurveCompatibility.read(current).strict==coeff);
   if(!coeff){SavedCurve a=SavedCurve.capture(old),b=SavedCurve.capture(current);check(SavedCurve.same(a,b));}else bad(()->SavedCurve.capture(current));
   for(String field:new String[]{"schema","coordinate","interpolation","strict_order","current_available","save_supported","saved_point_limit","live_count","memory_description"}){
    JSONObject broken=new JSONObject(current.toString());broken.getJSONObject(CurveCompatibility.KEY).remove(field);bad(()->CurveCompatibility.read(broken));bad(()->SavedCurve.capture(broken));
   }
   for(Object value:new Object[]{2,"1",1.5,JSONObject.NULL}){JSONObject broken=new JSONObject(current.toString());broken.getJSONObject(CurveCompatibility.KEY).put("schema",value);bad(()->CurveCompatibility.read(broken));}
   for(Object value:new Object[]{-1,513,1.1,"7",JSONObject.NULL}){JSONObject broken=new JSONObject(current.toString());broken.getJSONObject(CurveCompatibility.KEY).put("saved_point_limit",value);bad(()->CurveCompatibility.read(broken));}
   JSONObject mismatch=new JSONObject(current.toString()).put("curve_coordinate","unknown");bad(()->CurveCompatibility.read(mismatch));
   JSONObject unavailable=new JSONObject(current.toString());unavailable.getJSONObject(CurveCompatibility.KEY).put("current_available",false);check(CurveComparison.read(unavailable,factors,0,false).current==null);bad(()->SavedCurve.capture(unavailable));
   JSONObject hdr=new JSONObject(current.toString()).put("hdr_active",true);bad(()->SavedCurve.capture(hdr));
  }
  JSONObject p=state("physical_mapping",false);p.put("physical_mapping_active",false);check(CurveComparison.read(p,factors,0,false).current==null);final JSONObject inactive=p;bad(()->SavedCurve.capture(inactive));
  p=state("physical_mapping",false);p.put("last_pipeline",new JSONObject().put("route","hdr"));check(CurveComparison.read(p,factors,0,false).current==null);final JSONObject rejected=p;bad(()->SavedCurve.capture(rejected));
  JSONObject legacy=state("physical_mapping",false);legacy.remove("memory_live_count");legacy.put("user_anchor_lux",30);check(CurveCompatibility.read(legacy).liveCount==1);legacy.put("user_anchor_lux",-1);check(CurveCompatibility.read(legacy).liveCount==0);
  legacy=state("refactor",false);legacy.remove("memory_live_count");legacy.put("manual_anchor_flags",new JSONArray("[false,true,true,false]"));check(CurveCompatibility.read(legacy).liveCount==2);legacy.put("memory_live_count",1);check(CurveCompatibility.read(legacy).liveCount==1);
  Backend k=new Backend();check(!k.savedCurveSupported()&&k.savedPointLimit()==0&&k.memoryLifecycleTarget()==null&&!k.memoryRestoreIncludesModel());
  try{k.currentLux();throw new AssertionError();}catch(IllegalStateException unsupported){cases++;}
  try{k.currentNit();throw new AssertionError();}catch(IllegalStateException unsupported){cases++;}
  // Decoding and display cannot mutate persisted archives/options or input snapshots.
  JSONObject r=modern(state("physical_mapping",false));r.put("saved_base_curve",new JSONObject().put("lux",new JSONArray("[0,30,600,5000]")).put("nit",new JSONArray("[2,20,100,1000]"))).put("unknown_future_preference",37);String before=r.toString();
  CurveComparison.read(r,factors,0,true,SavedCurve.parse(r));SavedCurve.capture(r);check(before.equals(r.toString()));
  System.out.println("Curve compatibility boundary: "+cases+" cases PASS; production data contracts, no Android/device execution");
 }
}''','utf-8')
C=O/'classes';C.mkdir(exist_ok=True);names=['CurveBackend','CurveCompatibility','CurveComparison','SavedCurve','CurvePlan','TraditionalCurve']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],str(p)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.BoundaryTest'],check=True)
