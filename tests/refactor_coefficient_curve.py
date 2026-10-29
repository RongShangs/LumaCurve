"""New coefficient ABI, legacy coexistence and transactional rollback; no Android claim."""
from pathlib import Path
import subprocess,os,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/refactor-coefficient-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
java=r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import java.net.*;import java.io.*;import org.json.*;
public class CoefficientTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("case "+cases);cases++;}static void eq(double a,double b){check(Math.abs(a-b)<Math.max(.0003,Math.abs(b)*.000003));}
 public static class OEM{
  int mDisplayId,mUserSerial;boolean mUseAutoBrightness=true,mIsHaveGoodCurve,fail,wrong;
  float mUserLux=-1,mUserLogicalNit=-1;
  double[] mDefaultLuxToLogicalNit,mLuxToLogicalNit=new double[9],mGoodLuxToLogicalNit=new double[9];
  float[] mLogicalCurve=new float[22],mGoodLogicalCurve=new float[22],mLogicalData=new float[9];
  public OEM(){resetDefaultSpline();mDefaultLuxToLogicalNit=mLuxToLogicalNit.clone();}
  public double calculateBrt(float x,double[] c){if(wrong)return 1;double slope,offset;if(x<c[2]){slope=(c[3]-c[1])/(c[2]-c[0]);offset=c[1];}else if(x<c[5]){slope=(c[7]-c[6])/(c[5]-c[4]);offset=c[6];}else{slope=c[8];offset=c[7]-c[5]*slope;}return Math.max(1,Math.min(1060,(float)(offset+x*slope)));}
  public double getDefaultLogicalBrt(float x){return calculateBrt(Math.min(x,8000),mDefaultLuxToLogicalNit);}
  public float getCurrentNit(float x,float manual,boolean special){return (Float.isNaN(manual)?(float)calculateBrt(Math.min(x,8000),mLuxToLogicalNit):manual)*1.6f;}
  public void resetDefaultSpline(){
   // Known consumer layout, not a replacement for the proprietary fitting algorithm.
   mLogicalCurve=new float[22];mLogicalCurve[0]=0;mLogicalCurve[1]=2;mLogicalCurve[2]=30;mLogicalCurve[3]=135;mLogicalCurve[5]=600;mLogicalCurve[7]=220;mLogicalCurve[6]=220-600*(220-135f)/(600-30);mLogicalCurve[21]=(1060-220f)/(5000-600);
   mLogicalCurve[8]=0;mLogicalCurve[9]=0;mLogicalCurve[10]=600;mLogicalCurve[11]=2;mLogicalCurve[12]=2;mLogicalCurve[13]=135;mLogicalCurve[16]=-1;mLogicalCurve[18]=2;mLogicalCurve[19]=1;
   mGoodLogicalCurve=mLogicalCurve.clone();mLogicalData=new float[]{1.5915494f,.06366198f,4.4666667f,0,0,0,0,1060,1};
   if(mLuxToLogicalNit==null||mLuxToLogicalNit.length!=9)mLuxToLogicalNit=new double[9];for(int i=0;i<8;i++)mLuxToLogicalNit[i]=mLogicalCurve[i];mLuxToLogicalNit[8]=mLogicalCurve[21];mGoodLuxToLogicalNit=new double[9];mIsHaveGoodCurve=false;mUserLux=mUserLogicalNit=-1;if(fail)throw new IllegalStateException("native reset failed");
  }
  public void updateLogicalCurve(float x,float y){mUserLux=x;mUserLogicalNit=y;/* Native-fit output fixture: a valid first-segment preference. */mLuxToLogicalNit[1]=y;mLogicalCurve[1]=y;}
  public void shouldUseGoodCurve(){}public void setAutoBrightnessState(boolean b){mUseAutoBrightness=b;}
 }
 static class Legacy{float mLuxSeg0;float defaultAtLux(float x){return x;}}
 interface Task{void run()throws Exception;}static void rejects(Task t)throws Exception{try{t.run();throw new AssertionError("unexpected acceptance");}catch(AssertionError e){throw e;}catch(Exception expected){cases++;}}
 public static void main(String[] args)throws Exception{
  OEM o=new OEM();double[] untouched=o.mDefaultLuxToLogicalNit.clone();RefactorCoefficientsAdapter a=new RefactorCoefficientsAdapter(o,1,1060);
  check(a.name().equals("refactor"));check(a.compatibilityRoute().equals("refactor_coefficients"));check(a.target()==o);check(a.plan()==null);check(!a.persistentMemorySupported()&&!a.savedCurveSupported()&&!a.memoryHandoverSupported());check(a.manualPointCapacity()==1);check(Arrays.equals(untouched,o.mDefaultLuxToLogicalNit));
  eq(a.factoryLux[1],30);eq(a.factoryLux[2],600);eq(a.factoryLux[3],5000);eq(a.factoryNit[0],2);eq(a.factoryNit[1],135);eq(a.factoryNit[2],220);eq(a.factoryNit[3],1060);
  check(RefactorProfiles.create(new OEM(),1,1060) instanceof RefactorCoefficientsAdapter);check(RefactorProfiles.defaultMethod(OEM.class).getReturnType()==double.class);check(RefactorProfiles.defaultMethod(Legacy.class).getReturnType()==float.class);check(RefactorProfiles.anchors(Legacy.class));rejects(()->RefactorProfiles.create(new Legacy(),1,1060));
  for(float[] factors:new float[][]{{1,1,1,1},{.8f,.8f,.9f,1},{1.2f,1.1f,1.1f,1},{1,.5f,.75f,1}}){
   a.configure(factors);CurvePlan plan=a.plan();for(int x=0;x<=8000;x+=5){eq(a.currentAt(x),plan.at(x));eq(o.getDefaultLogicalBrt(x),plan.at(x));eq(o.getCurrentNit(x,Float.NaN,false),plan.at(x)*1.6f);}check(!a.reconcile());
   o.resetDefaultSpline();a.afterReset();for(float x:a.factoryLux)eq(a.currentAt(x),plan.at(x));
  }
  a.configure(new float[]{1,.7f,.9f,1});o.updateLogicalCurve(0,4);double[] learned=o.mLuxToLogicalNit.clone();check(!a.reconcile());check(Arrays.equals(learned,o.mLuxToLogicalNit));eq(a.currentAt(0),4);check(a.manualPoints().length()==1);eq(a.manualPoints().getJSONObject(0).getDouble("value"),4);
  o.resetDefaultSpline();check(a.reconcile());for(float x:a.factoryLux)eq(a.currentAt(x),a.plan().at(x));
  JSONObject report=new JSONObject();a.putCurveState(report);check(!report.has("manual_anchor_flags"));a.putVerification(report);check(report.getJSONObject("refactor_curve_verification").getString("profile").equals("coefficients_9_22"));check(!report.getJSONObject("refactor_curve_verification").getBoolean("portable_memory_supported"));
  Object[] refs={o.mDefaultLuxToLogicalNit,o.mLuxToLogicalNit,o.mGoodLuxToLogicalNit,o.mLogicalCurve,o.mGoodLogicalCurve,o.mLogicalData};String before=Arrays.toString(o.mLuxToLogicalNit);CurvePlan plan=a.plan();o.fail=true;rejects(()->a.configure(new float[]{1,1,1,1}));o.fail=false;
  check(refs[0]==o.mDefaultLuxToLogicalNit&&refs[1]==o.mLuxToLogicalNit&&refs[2]==o.mGoodLuxToLogicalNit&&refs[3]==o.mLogicalCurve&&refs[4]==o.mGoodLogicalCurve&&refs[5]==o.mLogicalData);check(before.equals(Arrays.toString(o.mLuxToLogicalNit)));check(a.plan()==plan);
  o.wrong=true;rejects(()->a.configure(new float[]{1,1,1,1}));o.wrong=false;check(before.equals(Arrays.toString(o.mLuxToLogicalNit)));check(a.plan()==plan);
  rejects(()->a.configure(new float[]{1,1,1,1.1f}));rejects(()->a.configure(new float[]{Float.NaN,1,1,1}));check(a.plan()==plan);
  a.configure(null);check(a.plan()==null);check(Arrays.equals(o.mDefaultLuxToLogicalNit,untouched));
  OEM bad=new OEM();bad.mDefaultLuxToLogicalNit=new double[8];rejects(()->new RefactorCoefficientsAdapter(bad,1,1060));bad.mDefaultLuxToLogicalNit=untouched.clone();bad.mLogicalCurve=new float[21];rejects(()->new RefactorCoefficientsAdapter(bad,1,1060));bad.mLogicalCurve=new float[22];bad.mDisplayId=1;rejects(()->new RefactorCoefficientsAdapter(bad,1,1060));bad.mDisplayId=0;bad.mDefaultLuxToLogicalNit[8]=Double.NaN;rejects(()->new RefactorCoefficientsAdapter(bad,1,1060));bad.mDefaultLuxToLogicalNit=untouched.clone();bad.mGoodLuxToLogicalNit=bad.mLuxToLogicalNit;rejects(()->new RefactorCoefficientsAdapter(bad,1,1060));
  OEM first=new OEM(),second=new OEM();RefactorCoefficientsAdapter one=new RefactorCoefficientsAdapter(first,1,1060),two=new RefactorCoefficientsAdapter(second,1,1060);one.configure(new float[]{1,.5f,.7f,1});two.configure(new float[]{1,1.1f,1.2f,1});eq(one.currentAt(30),67.5);eq(two.currentAt(30),148.5);one.configure(null);eq(two.currentAt(30),148.5);
  URLClassLoader loader=new URLClassLoader(new URL[]{new File(args[0]).toURI().toURL()},null);Object separate=Class.forName("top.rongshangs.lumacurve.refactor.CoefficientTest$OEM",true,loader).getConstructor().newInstance();CurveBackend isolated=RefactorProfiles.create(separate,1,1060);isolated.configure(new float[]{1,.8f,.9f,1});eq(isolated.currentAt(30),108);eq(two.currentAt(30),148.5);loader.close();
  rejects(()->SavedCurve.capture(new JSONObject().put("curve_route","refactor_coefficients")));
  OEM terminal=new OEM();RefactorCoefficientsAdapter lower=new RefactorCoefficientsAdapter(terminal,1,1060);lower.configure(new float[]{1,1,1,.6f});eq(lower.currentAt(5000),636);eq(lower.currentAt(100000),636);eq(lower.limitedLux(100000,terminal.mDefaultLuxToLogicalNit),5000);eq(lower.limitedLux(100000,terminal.mLuxToLogicalNit.clone()),100000);lower.configure(null);eq(lower.limitedLux(100000,terminal.mLuxToLogicalNit),100000);eq(lower.currentAt(100000),1060);
  System.out.println("Refactor coefficient curve: "+cases+" cases PASS; production adapters, class-loader isolation and reset rollback; Android not tested");
 }
}'''
p=O/'CoefficientTest.java';p.write_text(java,encoding='utf-8');classes=O/'classes';classes.mkdir(exist_ok=True)
names=['CurveBackend','CurvePlan','SavedCurve','PersistentMemory','MemoryOptions','RefactorAdapter','RefactorProfiles','RefactorCoefficientsAdapter']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in names],str(p)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.CoefficientTest',str(classes)],check=True)

# Runtime status must not read fields belonging only to the anchor ABI.
runtime=(S/"HookRuntime.java").read_text(encoding="utf-8")
assert "kernel.putCurveState(status)" in runtime
for field in ["mAnchorCount","mAnchorIsUserDrag","mAnchorLux","mAnchorNit"]:assert 'kernel.get("'+field+'")' not in runtime and 'kernel.integer("'+field+'")' not in runtime
print("Coefficient status publication: shared formatter, no anchor-only field reads PASS")
