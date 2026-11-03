"""Reset-derived coefficient adapter: actual production transactions with synthetic native consumers."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/reset-curve-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
java=r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
public class ResetCurveTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void eq(double x,double y){check(Double.isFinite(x)&&Math.abs(x-y)<=Math.max(.0005,Math.abs(y)*.00001));}
 interface Task{void run()throws Exception;}static void bad(Task t)throws Exception{try{t.run();throw new AssertionError("accepted invalid ABI/state");}catch(AssertionError e){throw e;}catch(Exception expected){cases++;}}
 public static class OEM {
  double[] mLuxToLogicalNit=new double[9],mGoodLuxToLogicalNit=new double[9];float[] mLogicalCurve,mGoodLogicalCurve,mLogicalData;
  int mDisplayId,mUserSerial;float mUserLux=-1,mUserLogicalNit=-1;boolean mUseAutoBrightness=true,mIsHaveGoodCurve,failReset,badRead,wrongReset;float bottom=2,middle=135,upper=220,maximum=1060;
  public OEM(){resetDefaultSpline();}
  public void resetDefaultSpline(){mLogicalCurve=new float[22];float[] a=mLogicalCurve;a[0]=0;a[1]=bottom;a[2]=30;a[3]=middle;a[5]=600;a[7]=upper;a[6]=upper-600*(upper-middle)/570;a[8]=0;a[10]=600;a[11]=a[12]=a[18]=bottom;a[13]=upper;a[16]=-1;a[19]=1;a[21]=(maximum-upper)/4400;mGoodLogicalCurve=a.clone();mLogicalData=new float[]{1.5915494f,.06366198f,4.4666667f,0,0,0,0,maximum,1};for(int i=0;i<8;i++)mLuxToLogicalNit[i]=a[i];mLuxToLogicalNit[8]=a[21];mGoodLuxToLogicalNit=new double[9];mUserLux=mUserLogicalNit=-1;mIsHaveGoodCurve=false;if(wrongReset)mLuxToLogicalNit[0]=2;if(failReset)throw new IllegalStateException("native modeled reset failure");}
  private double getLogicalBrt(float lux){double[] c=mLuxToLogicalNit;double y=lux<c[2]?c[1]+lux*(c[3]-c[1])/(c[2]-c[0]):lux<c[5]?c[6]+lux*(c[7]-c[6])/(c[5]-c[4]):c[7]+(lux-c[5])*c[8];return Math.max(1,Math.min(maximum,(float)y))+(badRead?1:0);}
  public float getCurrentNit(float lux,float requested,boolean manual){return (float)getLogicalBrt(lux)*1.6f;}
  public void updateLogicalCurve(float lux,float desired){if(!mUseAutoBrightness)return;mUserLux=lux;mUserLogicalNit=desired;mLogicalCurve[1]=desired;mLuxToLogicalNit[1]=desired;}
  public void shouldUseGoodCurve(){if(mIsHaveGoodCurve){mLogicalCurve=mGoodLogicalCurve.clone();mLuxToLogicalNit=mGoodLuxToLogicalNit.clone();mIsHaveGoodCurve=false;}}
  public void setAutoBrightnessState(boolean b){mUseAutoBrightness=b;}
 }
 static class Partial extends OEM {double[] mDefaultLuxToLogicalNit=new double[8];}
 static class WrongDefault extends OEM {double getDefaultLogicalBrt(float x){return x;}}
 static Object[] refs(OEM o){return new Object[]{o.mLuxToLogicalNit,o.mGoodLuxToLogicalNit,o.mLogicalCurve,o.mGoodLogicalCurve,o.mLogicalData};}
 static String contents(OEM o){return Arrays.deepToString(refs(o))+o.mUserLux+":"+o.mUserLogicalNit+":"+o.mIsHaveGoodCurve;}
 static void identity(OEM o,Object[] before){Object[] now=refs(o);for(int i=0;i<now.length;i++)check(now[i]==before[i]);}
 public static void main(String[] args)throws Exception{
  OEM o=new OEM();o.updateLogicalCurve(0,50.7f);o.mIsHaveGoodCurve=true;o.mGoodLuxToLogicalNit=o.mLuxToLogicalNit.clone();Object[] refs=refs(o);String before=contents(o);
  RefactorResetAdapter a=new RefactorResetAdapter(o,1,1060);identity(o,refs);check(before.equals(contents(o)));eq(a.factoryNit[0],2);eq(a.factoryNit[1],135);eq(a.factoryNit[2],220);eq(a.factoryLux[3],5000);eq(a.currentAt(0),50.7);
  check(a.compatibilityRoute().equals("refactor_reset_coefficients")&&a.coordinate().equals("logical_nit")&&a.strictOrder());check(a.manualPointCapacity()==1&&!a.persistentMemorySupported()&&!a.savedCurveSupported()&&!a.memoryHandoverSupported());check(a.manualPoints().length()==1);
  check(RefactorProfiles.create(new OEM(),1,1060) instanceof RefactorResetAdapter);check(RefactorProfiles.defaultMethod(OEM.class)==null);check(RefactorProfiles.currentMethod(OEM.class).getReturnType()==double.class);
  a.configure(new float[]{1,1,1,1});check(before.equals(contents(o)));identity(o,refs);for(int i=0;i<10;i++)check(!a.reconcile());check(before.equals(contents(o)));
  for(float[] f:new float[][]{{1,.7f,.9f,1},{1,1.3f,1.2f,1},{1,1,1,.6f}}){a.configure(f);for(int x=0;x<=100000;x+=53)eq(a.currentAt(x),a.plan().at(x));for(int i=0;i<4;i++)eq(a.currentAt(a.factoryLux[i]),a.plan().nits()[i]);check(!a.reconcile());o.resetDefaultSpline();a.afterReset();check(!a.reconcile());for(int i=0;i<4;i++)eq(a.currentAt(a.factoryLux[i]),a.plan().nits()[i]);}
  eq(a.currentInputLux(100000),5000);a.configure(new float[]{1,1,1,1});eq(a.currentInputLux(100000),100000);
  a.configure(new float[]{1,1,1,.999f});o.resetDefaultSpline();check(a.reconcile());eq(a.currentAt(5000),1060*.999f);check(!a.reconcile());a.configure(new float[]{1,1,1,1});
  o.updateLogicalCurve(0,70);check(!a.reconcile());eq(a.currentAt(0),70);check(a.manualPoints().length()==1);a.clearMemory();check(a.manualPoints().length()==0);eq(a.currentAt(0),2);
  o.resetDefaultSpline();check(!a.reconcile());a.configure(new float[]{1,.8f,.9f,1});o.resetDefaultSpline();check(a.reconcile());eq(a.currentAt(30),108);check(!a.reconcile());
  o.mGoodLogicalCurve=o.mLogicalCurve.clone();o.mGoodLogicalCurve[1]=9;o.mGoodLuxToLogicalNit=o.mLuxToLogicalNit.clone();o.mGoodLuxToLogicalNit[1]=9;o.mIsHaveGoodCurve=true;o.shouldUseGoodCurve();check(!a.reconcile());eq(a.currentAt(0),9);
  refs=refs(o);before=contents(o);CurvePlan plan=a.plan();o.failReset=true;bad(()->a.configure(new float[]{1,1,1,1}));o.failReset=false;identity(o,refs);check(before.equals(contents(o))&&a.plan()==plan);
  o.badRead=true;bad(()->a.configure(new float[]{1,1,1,1}));o.badRead=false;identity(o,refs);check(before.equals(contents(o))&&a.plan()==plan);
  o.wrongReset=true;bad(()->a.configure(new float[]{1,1,1,1}));o.wrongReset=false;identity(o,refs);check(before.equals(contents(o))&&a.plan()==plan);
  double[] foreign=o.mLuxToLogicalNit.clone();foreign[1]+=1;o.mLuxToLogicalNit=foreign;bad(()->a.configure(null));check(o.mLuxToLogicalNit==foreign);o.mLuxToLogicalNit=(double[])refs[0];a.configure(null);check(a.plan()==null);eq(a.currentAt(0),2);
  bad(()->RefactorProfiles.create(new Partial(),1,1060));bad(()->RefactorProfiles.defaultMethod(Partial.class));bad(()->RefactorProfiles.create(new WrongDefault(),1,1060));
  OEM failedProbe=new OEM();failedProbe.updateLogicalCurve(0,40);failedProbe.failReset=true;Object[] original=refs(failedProbe);String copy=contents(failedProbe);bad(()->new RefactorResetAdapter(failedProbe,1,1060));identity(failedProbe,original);check(copy.equals(contents(failedProbe)));
  OEM invalid=new OEM();invalid.mLogicalData=new float[8];bad(()->new RefactorResetAdapter(invalid,1,1060));OEM secondary=new OEM();secondary.mDisplayId=1;bad(()->new RefactorResetAdapter(secondary,1,1060));
  for(float maximum:new float[]{650,1060,1600})for(float bottom:new float[]{1,4,20}){OEM variant=new OEM();variant.maximum=maximum;variant.bottom=bottom;variant.resetDefaultSpline();RefactorResetAdapter adapter=new RefactorResetAdapter(variant,1,maximum);eq(adapter.factoryNit[0],bottom);eq(adapter.factoryNit[3],maximum);adapter.configure(new float[]{1,.8f,.9f,1});eq(adapter.currentAt(30),108);adapter.configure(null);eq(adapter.currentAt(0),bottom);}
  JSONObject report=new JSONObject();a.putVerification(report);check(report.getJSONObject("refactor_curve_verification").getString("baseline_source").equals("native_reset_transaction"));
  System.out.println("Reset-derived Refactor: "+cases+" cases PASS; synthetic native transactions, Android not tested");
 }
}'''
p=O/'ResetCurveTest.java';p.write_text(java,'utf-8');C=O/'classes';C.mkdir(exist_ok=True)
names=['CurveBackend','CurvePlan','SavedCurve','CurveCompatibility','TraditionalCurve','PersistentMemory','MemoryOptions','RefactorAdapter','RefactorProfiles','RefactorCoefficientsAdapter','RefactorResetAdapter']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],str(p)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.ResetCurveTest'],check=True)
