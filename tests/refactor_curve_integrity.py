"""Regress the observed factory-baseline fallback without replacing OEM drag ownership."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-curve-integrity-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'CurveIntegrityTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
public class CurveIntegrityTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void eq(float a,float b){check(Math.abs(a-b)<.0001f);}
 static class OEM{
  int mDisplayId,mUserSerial,mAnchorCount=4,mGoodAnchorCount=4,mOrderCounter;boolean mUseAutoBrightness=true,mIsHaveGoodCurve,stale;
  float mLuxSeg0=30,mLuxSeg1=600,mLuxMax=5000,mDefNit0=3.2f,mDefNit30=216,mDefNitMidHigh=352,mUserLux=-1,mUserLogicalNit=-1;
  float[] mAnchorLux=new float[7],mAnchorNit=new float[7],mGoodAnchorLux=new float[7],mGoodAnchorNit=new float[7];int[] mAnchorOrder=new int[7],mGoodAnchorOrder=new int[7];boolean[] mAnchorIsUserDrag=new boolean[7],mGoodAnchorIsUserDrag=new boolean[7];
  float defaultAtLux(float lux){float[] x={0,30,600,5000},y=stale?new float[]{3.2f,216,352,1060}:new float[]{mDefNit0,mDefNit30,mDefNitMidHigh,1060};for(int i=1;i<4;i++)if(lux<=x[i])return y[i-1]+(y[i]-y[i-1])*(lux-x[i-1])/(x[i]-x[i-1]);return 1060;}
  void resetDefaultSpline(){float[] x={0,30,600,5000};for(int i=0;i<4;i++){mAnchorLux[i]=mGoodAnchorLux[i]=x[i];mAnchorNit[i]=mGoodAnchorNit[i]=defaultAtLux(x[i]);}Arrays.fill(mAnchorIsUserDrag,false);Arrays.fill(mGoodAnchorIsUserDrag,false);Arrays.fill(mAnchorOrder,0);Arrays.fill(mGoodAnchorOrder,0);mAnchorCount=mGoodAnchorCount=4;mOrderCounter=0;mUserLux=mUserLogicalNit=-1;}
  // Oracle transcribed from donor blendedDefaultAtLux/enforceMonotonicityAndRestore.
  float nativeBlend(float lux,float userLux,float userNit){float value=defaultAtLux(lux),start,end;if(userLux<30){start=0;end=30;}else if(userLux<600){start=30;end=600;}else{start=600;end=5000;}if(Math.abs(userLux-start)<.5f||Math.abs(userLux-end)<.5f)return value;if(Math.abs(lux-start)>=.5f&&Math.abs(lux-end)>=.5f)return value;return Math.max(1,Math.min(1060,value+(userNit-defaultAtLux(userLux))*((end-start-Math.abs(lux-userLux))/(end-start))));}
  void updateLogicalCurve(float lux,float nit){int pos=0;while(pos<mAnchorCount&&mAnchorLux[pos]<lux)pos++;if(pos==mAnchorCount||mAnchorLux[pos]!=lux){for(int i=mAnchorCount;i>pos;i--){mAnchorLux[i]=mAnchorLux[i-1];mAnchorNit[i]=mAnchorNit[i-1];mAnchorOrder[i]=mAnchorOrder[i-1];mAnchorIsUserDrag[i]=mAnchorIsUserDrag[i-1];}mAnchorCount++;}mAnchorLux[pos]=lux;mAnchorNit[pos]=nit;mAnchorOrder[pos]=++mOrderCounter;mAnchorIsUserDrag[pos]=true;mUserLux=lux;mUserLogicalNit=nit;float bound=nit;for(int i=pos-1;i>=0;i--){mAnchorNit[i]=Math.min(mAnchorIsUserDrag[i]?mAnchorNit[i]:nativeBlend(mAnchorLux[i],lux,nit),bound);bound=mAnchorNit[i];}bound=nit;for(int i=pos+1;i<mAnchorCount;i++){mAnchorNit[i]=Math.max(mAnchorIsUserDrag[i]?mAnchorNit[i]:nativeBlend(mAnchorLux[i],lux,nit),bound);bound=mAnchorNit[i];}}
 }
 static float[] factors={1,.34824574f,.51400137f,1};
 public static void main(String[] args)throws Exception{
  OEM o=new OEM();o.resetDefaultSpline();RefactorAdapter a=new RefactorAdapter(o,1,1060);a.configure(factors);eq(o.mDefNit30,75.221077f);check(!a.reconcile());
  // Reported package: native reconstruction uses factory values despite desired plan.
  o.stale=true;o.updateLogicalCurve(1.1475000381469727f,3.572000026702881f);eq(o.mAnchorNit[2],215.702896f);eq(o.mAnchorNit[3],352);
  float[] x=o.mAnchorLux.clone();boolean[] flags=o.mAnchorIsUserDrag.clone();int[] order=o.mAnchorOrder.clone();int count=o.mAnchorCount,counter=o.mOrderCounter;
  check(a.reconcile());check(Arrays.equals(x,o.mAnchorLux)&&Arrays.equals(flags,o.mAnchorIsUserDrag)&&Arrays.equals(order,o.mAnchorOrder)&&o.mAnchorCount==count&&o.mOrderCounter==counter);eq(o.mAnchorNit[1],3.572f);eq(o.mAnchorNit[3],180.928482f);check(o.mAnchorNit[2]<76);check(!a.reconcile());
  JSONObject status=new JSONObject();a.putVerification(status);JSONObject v=status.getJSONObject("refactor_curve_verification");check(v.getBoolean("baseline_fields_match")&&v.getBoolean("current_basis_verified")&&v.getLong("repair_count")==1);eq((float)v.getJSONArray("actual_base_nit").getDouble(1),75.221077f);
  // A reset bypassing a private helper hook is corrected at its public handover.
  o.resetDefaultSpline();eq(o.mAnchorNit[1],216);check(a.reconcile());eq(o.mAnchorNit[1],75.221077f);check(a.manualPoints().length()==0);
  o.mDefNit30=216;o.mDefNitMidHigh=352;o.resetDefaultSpline();a.afterReset();eq(o.mDefNit30,75.221077f);eq(o.mGoodAnchorNit[2],180.928482f);
  // A functioning native implementation needs no corrective write after a gesture.
  o.stale=false;o.updateLogicalCurve(10,20);check(!a.reconcile());float learned=a.currentAt(10);eq(learned,20);
  // Do not reinterpret an unknown OEM algorithm as the known factory fallback.
  o.mAnchorNit[2]+=3;float unknown=o.mAnchorNit[2];check(!a.reconcile());eq(o.mAnchorNit[2],unknown);a.putVerification(status);check(!status.getJSONObject("refactor_curve_verification").getBoolean("current_basis_verified"));
  // Malformed native coordinates are rejected before any fields/arrays are touched.
  o.mAnchorLux[2]=o.mAnchorLux[1];float[] before=o.mAnchorNit.clone();try{a.reconcile();throw new AssertionError();}catch(IllegalStateException expected){cases++;}check(Arrays.equals(before,o.mAnchorNit));
  o.resetDefaultSpline();a.configure(null);check(a.plan()==null&&!a.reconcile());eq(o.mDefNit30,216);
  // Replay goes through the same production repair, keeping recorded manual points.
  a.configure(factors);o.stale=true;a.replaceManualPoints(new JSONArray().put(new JSONObject().put("lux",1.1475).put("value",3.572)));check(a.manualPoints().length()==1);eq(o.mAnchorNit[3],180.928482f);check(o.mAnchorNit[2]<76);
  // Endpoints and a second segment retain OEM ordering and monotonicity.
  o.resetDefaultSpline();a.afterReset();o.updateLogicalCurve(30,80);check(a.reconcile());eq(a.currentAt(30),80);eq(o.mAnchorNit[2],180.928482f);
  o.updateLogicalCurve(100,120);check(a.reconcile());eq(a.currentAt(100),120);check(a.manualPoints().length()==2);for(int i=1;i<o.mAnchorCount;i++)check(o.mAnchorNit[i]>=o.mAnchorNit[i-1]);
  // Inlined resets must preserve the full learned shape saved as a blue base.
  o.resetDefaultSpline();SavedCurve saved=new SavedCurve(new float[]{0,7,30,300,600,1200,5000},new float[]{3.2f,8,75,120,181,350,1060});a.configure(new float[]{1,1,1,1},0,saved);check(o.mAnchorCount==7);o.resetDefaultSpline();check(o.mAnchorCount==4);check(a.reconcile());check(o.mAnchorCount==7&&o.mGoodAnchorCount==7);check(Arrays.equals(o.mAnchorLux,saved.lux)&&Arrays.equals(o.mAnchorNit,saved.nit));check(!a.reconcile());
  o.updateLogicalCurve(30,80);check(a.reconcile());eq(a.currentAt(30),80);check(o.mAnchorCount==7&&a.manualPoints().length()==1);
  System.out.println("Refactor curve integrity: "+cases+" cases PASS; observed anchors and OEM drag oracle, device not tested");
 }
}''',encoding='utf8')
C=O/'classes';C.mkdir(exist_ok=True)
names=['CurvePlan','CurveBackend','SavedCurve','RefactorAdapter','PersistentMemory','MemoryOptions']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],str(p)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.CurveIntegrityTest'],check=True)
