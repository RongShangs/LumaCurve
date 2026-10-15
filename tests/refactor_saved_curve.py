"""Preserve actual green graph points, native adapter rollback, and blue graph identity."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-saved-curve-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'SavedCurveTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
public class SavedCurveTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void eq(float a,float b){check(Math.abs(a-b)<.001f);}
 static void bad(Runnable r){try{r.run();throw new AssertionError("accepted");}catch(IllegalArgumentException expected){cases++;}}
 static class OEM {
  int mDisplayId=0,mUserSerial=0,mAnchorCount=4,mGoodAnchorCount=4,mOrderCounter;boolean mUseAutoBrightness=true,mIsHaveGoodCurve,fail;
  float mLuxSeg0=30,mLuxSeg1=600,mLuxMax=5000,mDefNit0=2,mDefNit30=100,mDefNitMidHigh=300,mUserLux=-1,mUserLogicalNit=-1;
  float[] mAnchorLux=new float[7],mAnchorNit=new float[7],mGoodAnchorLux=new float[7],mGoodAnchorNit=new float[7];int[] mAnchorOrder=new int[7],mGoodAnchorOrder=new int[7];boolean[] mAnchorIsUserDrag=new boolean[7],mGoodAnchorIsUserDrag=new boolean[7];
  void resetDefaultSpline(){float[] x={0,30,600,5000},y={mDefNit0,mDefNit30,mDefNitMidHigh,1000};for(float[] a:new float[][]{mAnchorLux,mAnchorNit,mGoodAnchorLux,mGoodAnchorNit})Arrays.fill(a,0);System.arraycopy(x,0,mAnchorLux,0,4);System.arraycopy(y,0,mAnchorNit,0,4);System.arraycopy(x,0,mGoodAnchorLux,0,4);System.arraycopy(y,0,mGoodAnchorNit,0,4);mAnchorCount=mGoodAnchorCount=4;mUserLux=mUserLogicalNit=-1;Arrays.fill(mAnchorIsUserDrag,false);Arrays.fill(mGoodAnchorIsUserDrag,false);Arrays.fill(mAnchorOrder,0);Arrays.fill(mGoodAnchorOrder,0);mIsHaveGoodCurve=false;if(fail)throw new IllegalStateException("reset failed");}
  float defaultAtLux(float x){return 0;}
 }
 static JSONObject runtime(SavedCurve curve)throws Exception{return new JSONObject().put("phase","active").put("curve_backend","refactor").put("memory_live_count",2).put("factory_lux",new JSONArray(new float[]{0,30,600,5000})).put("factory_logical_nit",new JSONArray(new float[]{2,100,300,1000})).put("min_logical_nit",1).put("max_logical_nit",1000).put("current_anchors_lux",new JSONArray(curve.lux)).put("current_anchors_nit",new JSONArray(curve.nit));}
 public static void main(String[] args)throws Exception{
  float[] x={0,7,30,300,600,1200,5000},y={2,5,40,90,150,350,1000},one={1,1,1,1};SavedCurve c=new SavedCurve(x,y);x[1]=9;y[1]=10;check(c.lux[1]==7&&c.nit[1]==5);
  JSONObject file=new JSONObject();c.put(file);SavedCurve copy=SavedCurve.parse(file);check(SavedCurve.same(c,copy));check(SavedCurve.parse(new JSONObject())==null);c.validate(1,1000,5000);
  float[] handles={0,30,600,5000};CurvePlan plan=SavedCurve.plan(c,handles,new float[]{2,100,300,1000},1,1000,one,0);check(Arrays.equals(plan.lux(),c.lux));check(Arrays.equals(plan.nits(),c.nit));
  for(int i=0;i<=1000;i++)eq(plan.at(5*i),c.at(5*i));
  // Saved shapes can omit an editing handle without overflowing the seven OEM slots.
  SavedCurve absentHandle=new SavedCurve(new float[]{0,7,40,300,650,1200,5000},new float[]{2,5,40,90,150,350,1000});
  check(SavedCurve.plan(absentHandle,handles,new float[]{2,100,300,1000},1,1000,one,0).lux().length==7);
  CurvePlan changed=SavedCurve.plan(c,handles,new float[]{2,100,300,1000},1,1000,new float[]{1,1.2f,.9f,1},0);float prev=0;for(int i=0;i<=1000;i++){float value=changed.at(i*5);check(value>=prev&&value<=1000);prev=value;}
  OEM o=new OEM();o.resetDefaultSpline();RefactorAdapter a=new RefactorAdapter(o,1,1000);a.configure(one,0,c);check(a.savedBase==c&&o.mAnchorCount==7&&o.mGoodAnchorCount==7);check(a.manualPoints().length()==0);check(Arrays.equals(Arrays.copyOf(o.mAnchorLux,7),c.lux));
  for(int i=0;i<=1000;i++)eq(a.currentAt(i*5),c.at(i*5));
  a.afterReset();check(o.mAnchorCount==7);o.resetDefaultSpline();a.afterReset();check(o.mAnchorCount==7&&Arrays.equals(o.mAnchorNit,c.nit));a.clearMemory();check(o.mAnchorCount==7&&a.savedBase==c);
  o.mAnchorNit[2]=45;o.mAnchorIsUserDrag[2]=true;o.mUserLux=30;o.mUserLogicalNit=45;o.fail=true;
  try{a.configure(one,0,absentHandle);throw new AssertionError();}catch(IllegalStateException expected){cases++;}check(a.savedBase==c&&o.mAnchorNit[2]==45&&o.mAnchorIsUserDrag[2]&&a.plan().at(30)==40);o.fail=false;
  a.configure(null);check(a.plan()==null&&o.mAnchorCount==4&&o.mDefNit30==100);a.configure(one,0,null);check(a.savedBase==null&&o.mAnchorCount==4);
  JSONObject live=runtime(c);SavedCurve captured=SavedCurve.capture(live);check(SavedCurve.same(c,captured));
  live.put("applied_base_lux",new JSONArray(c.lux)).put("applied_base_nit",new JSONArray(c.nit)).put("active_logical_nit",new JSONArray(c.controls(handles))).put("memory_live_count",0);
  CurveComparison comparison=CurveComparison.read(live,one,0,false);check(!comparison.memoryChanged(live));check(comparison.kind(live).equals("baseline"));for(float lux:c.lux)eq(comparison.baseline.at(lux),c.at(lux));
  comparison=CurveComparison.read(live,one,0,true,c);for(float lux:c.lux)eq(comparison.baseline.at(lux),c.at(lux));
  try{SavedCurve.capture(live);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}live.put("memory_live_count",1).put("hdr_active",true);try{SavedCurve.capture(live);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  for(float[] invalid:new float[][]{{2,5,40,90,150,350,Float.NaN},{2,5,40,90,85,350,1000}})bad(()->new SavedCurve(c.lux,invalid));
  bad(()->c.validate(1,1000,6000));bad(()->c.validate(10,1000,5000));bad(()->new SavedCurve(new float[513],new float[513]));
  float[] denseX=new float[400],denseY=new float[400];for(int i=0;i<400;i++){denseX[i]=i*5000f/399;denseY[i]=1+i*999f/399;}SavedCurve dense=new SavedCurve(denseX,denseY);CurvePlan densePlan=SavedCurve.plan(dense,handles,new float[]{2,100,300,1000},1,1000,one,0);check(Arrays.equals(densePlan.nits(),denseY));
  System.out.println("Saved baseline curve: "+cases+" cases PASS; actual adapter/graph, Android not tested");
 }
}''',encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
names=['CurvePlan','CurveBackend','SavedCurve','RefactorAdapter','PersistentMemory','MemoryOptions','CurveComparison','TraditionalCurve']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],str(p)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.SavedCurveTest'],check=True)
