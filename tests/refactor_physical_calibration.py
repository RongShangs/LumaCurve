"""Physical mapper boundary adaptation with old-route and fail-closed integration tests."""
from pathlib import Path
import runpy,subprocess,os,sys
R=Path(__file__).resolve().parents[1]
base=runpy.run_path(str(R/'tests/refactor_traditional.py'))
S=base['S'];J=base['J'];old=base['C'];O=R/'build/physical-calibration-tests';O.mkdir(exist_ok=True)
files={
'android/util/Spline.java':r"""package android.util;
public class Spline {
 public static class LinearSpline {public float[] mX,mY;public LinearSpline(float[] x,float[] y){mX=x.clone();mY=y.clone();}public float interpolate(float x){if(x<=mX[0])return mY[0];for(int i=1;i<mX.length;i++)if(x<=mX[i])return mY[i-1]+(mY[i]-mY[i-1])*(x-mX[i-1])/(mX[i]-mX[i-1]);return mY[mY.length-1];}}
 // Endpoint/knot double only; real cubic bytecode is checked by the private firmware test.
 public static class MonotoneCubicSpline extends LinearSpline {public MonotoneCubicSpline(float[] x,float[] y){super(x,y);}}
 public static class UnknownSpline extends LinearSpline {public UnknownSpline(float[] x,float[] y){super(x,y);}}
}
""",
'com/android/server/display/BrightnessMappingStrategy.java':r"""package com.android.server.display;
import android.hardware.display.BrightnessConfiguration;import android.util.Spline.LinearSpline;
public class BrightnessMappingStrategy {
 public static class PhysicalMappingStrategy {
  public float[] mNits={1,450,550},mBrightness={.001709819f,.49975574f,1};
  public boolean mBrightnessRangeAdjustmentApplied,fail,wrongForward,wrongReverse;
  public LinearSpline mNitsToBrightnessSpline,mBrightnessToNitsSpline,mAdjustedNitsToBrightnessSpline,mBrightnessToAdjustedNitsSpline;
  public BrightnessConfiguration mConfig,mDefaultConfig;public Curve mBrightnessSpline;
  public float mUserLux=-1,mUserBrightness=-1,mAutoBrightnessAdjustment;public int setters,resets,mode;
  public PhysicalMappingStrategy(BrightnessConfiguration c){mConfig=mDefaultConfig=c;calibrate();compute();}
  public void calibrate(){mNitsToBrightnessSpline=new LinearSpline(mNits,mBrightness);mBrightnessToNitsSpline=new LinearSpline(mBrightness,mNits);mAdjustedNitsToBrightnessSpline=mNitsToBrightnessSpline;mBrightnessToAdjustedNitsSpline=mBrightnessToNitsSpline;}
  public int getMode(){return mode;}
  public float convertToBrightness(float n){return wrongForward&&n>mNits[mNits.length-1]?2:mNitsToBrightnessSpline.interpolate(n);}
  public float convertToNits(float b){return wrongReverse?10000:mBrightnessToNitsSpline.interpolate(b);}
  public boolean setBrightnessConfiguration(BrightnessConfiguration c){setters++;if(c==null)c=mDefaultConfig;if(c.equals(mConfig))return false;mConfig=c;compute();return true;}
  void compute(){float[] values=mConfig.mNits.clone();for(int i=0;i<values.length;i++)values[i]=convertToNits(convertToBrightness(values[i]));mBrightnessSpline=new Curve(mConfig.mLux,values);if(fail)throw new IllegalStateException("rebuild failed");}
  public void clearUserDataPoints(){resets++;mUserLux=mUserBrightness=-1;compute();}
  public void addUserDataPoint(float lux,float b){mUserLux=lux;mUserBrightness=b;}
  public float getUserBrightness(){return mUserBrightness;}
  public float getBrightness(float lux,String pkg,int cat){return mAdjustedNitsToBrightnessSpline.interpolate(mBrightnessSpline.interpolate(lux));}
  public static class Curve extends LinearSpline {public Curve(float[] x,float[] y){super(x,y);}}
 }
}
""",
'top/rongshangs/lumacurve/refactor/PhysicalCalibrationTest.java':r"""package top.rongshangs.lumacurve.refactor;
import java.util.*;import android.hardware.display.BrightnessConfiguration;import android.util.Spline;import com.android.server.display.BrightnessMappingStrategy.PhysicalMappingStrategy;import org.json.*;
public class PhysicalCalibrationTest {
 static int cases;static float[] one={1,1,1,1},x={0,30,600,5000,100000},y={2,30,100,700,1000};
 interface Work{void run()throws Exception;}
 static void check(boolean b){if(!b)throw new AssertionError("calibration case "+cases);cases++;}
 static void near(float a,float b){check(Math.abs(a-b)<=4*Math.max(Math.ulp(a),Math.ulp(b)));}
 static void bad(Work w)throws Exception {try{w.run();throw new AssertionError("unsafe calibration accepted "+cases);}catch(ReflectiveOperationException|IllegalArgumentException|IllegalStateException expected){cases++;}}
 static BrightnessConfiguration config(float[] x,float[] y){Map<String,Object> p=new HashMap<>();p.put("camera","correction");Map<Integer,Object> c=new HashMap<>();c.put(1,"category");return new BrightnessConfiguration(x,y,p,c,"OEM metadata",true,300000,.6f,.8f);}
 public static class Owner{int mDisplayId;boolean mUseAutoBrightness=true;}
 public static class Map0{Object value;public Object get(int i){return i==0?value:null;}}
 public static class Model{boolean valid=true;}
 public static class ABC{Map0 mBrightnessMappingStrategyMap=new Map0();Object mCurrentBrightnessMapper;Model mShortTermModel=new Model();public void resetShortTermModel(){((PhysicalMappingStrategy)mCurrentBrightnessMapper).clearUserDataPoints();}public boolean setScreenBrightnessByUser(float lux,float b){((PhysicalMappingStrategy)mCurrentBrightnessMapper).addUserDataPoint(lux,b);return true;}}
 static TraditionalAdapter attach(PhysicalMappingStrategy m)throws Exception {ABC a=new ABC();a.mBrightnessMappingStrategyMap.value=a.mCurrentBrightnessMapper=m;return new TraditionalAdapter(new Owner(),m,a,()->0);}
 static PhysicalMappingStrategy mapper(){return new PhysicalMappingStrategy(config(x,y));}
 static void replay(float[] lux,float[] nit,float[] calibration,float[] brightness)throws Exception {
  BrightnessConfiguration raw=config(lux,nit);PhysicalMappingStrategy m=new PhysicalMappingStrategy(raw);m.mNits=calibration.clone();m.mBrightness=brightness.clone();m.calibrate();
  bad(()->new CurvePlan(new TraditionalCurve(lux,nit).controlsLux(),new TraditionalCurve(lux,nit).controlsNit(),calibration[0],calibration[calibration.length-1],one));
  TraditionalAdapter a=attach(m);check(m.mConfig==raw&&m.setters==0&&m.resets==0);check(a.calibration.clamped);near(a.max,calibration[calibration.length-1]);
  float[] effective=a.fullNit();check(effective.length==nit.length&&Arrays.equals(a.fullLux(),lux));for(int i=0;i<nit.length;i++)near(effective[i],Math.max(a.min,Math.min(a.max,nit[i])));
  check(Arrays.equals(raw.mNits,nit));a.configure(one);check(m.mConfig==raw);check(Arrays.equals(a.baselineNit(),effective));check(a.baselineNit().length==nit.length);
  a.configure(new float[]{.7f,.8f,.9f,1});check(m.mConfig!=raw);for(float v:m.mConfig.mNits)check(v>=a.min&&v<=a.max);check(m.mConfig.mCorrectionsByPackageName.equals(raw.mCorrectionsByPackageName)&&m.mConfig.mCorrectionsByCategory.equals(raw.mCorrectionsByCategory));
  check(m.mConfig.mDescription.equals(raw.mDescription)&&m.mConfig.mShortTermModelTimeout==300000&&m.mConfig.mShortTermModelLowerLuxMultiplier==.6f);
  a.configure(null);check(m.mConfig==raw&&Arrays.equals(raw.mNits,nit));
  a.configure(one);SavedCurve saved=new SavedCurve(lux,effective);a.configure(one,0,saved);check(Arrays.equals(m.mConfig.mNits,effective));check(m.mConfig!=raw);a.configure(null);check(m.mConfig==raw);
  JSONObject state=new JSONObject();a.putCurveState(state);check(state.getString("physical_baseline_route").equals("aosp_physical_clamped"));check(state.getDouble("physical_original_max_nit")==nit[nit.length-1]);near((float)state.getDouble("physical_calibration_max_nit"),a.max);
 }
 public static void main(String[] args)throws Exception {
  replay(x,y,new float[]{1,450,550},new float[]{.001709819f,.49975574f,1});
  // No new requirements for the established in-range route, even with a nonstandard spline.
  PhysicalMappingStrategy direct=new PhysicalMappingStrategy(config(x,new float[]{2,30,100,500,550}));direct.mBrightnessRangeAdjustmentApplied=true;direct.mNitsToBrightnessSpline=new Spline.UnknownSpline(direct.mNits,direct.mBrightness);TraditionalAdapter d=attach(direct);check(!d.calibration.clamped);check(d.fullNit()[4]==550);
  PhysicalMappingStrategy m=mapper();m.mNitsToBrightnessSpline=new Spline.MonotoneCubicSpline(m.mNits,m.mBrightness);m.mBrightnessToNitsSpline=new Spline.MonotoneCubicSpline(m.mBrightness,m.mNits);m.mAdjustedNitsToBrightnessSpline=m.mNitsToBrightnessSpline;m.mBrightnessToAdjustedNitsSpline=m.mBrightnessToNitsSpline;check(attach(m).calibration.clamped);
  m=mapper();m.mBrightnessRangeAdjustmentApplied=true;final PhysicalMappingStrategy pending=m;bad(()->attach(pending));
  m=mapper();m.mBrightnessToAdjustedNitsSpline=new Spline.LinearSpline(m.mBrightness,m.mNits);final PhysicalMappingStrategy changed=m;bad(()->attach(changed));
  m=mapper();m.mNitsToBrightnessSpline.mY[1]=.4f;final PhysicalMappingStrategy mismatch=m;bad(()->attach(mismatch));
  m=mapper();m.mBrightness[1]=2;m.calibrate();final PhysicalMappingStrategy invalid=m;bad(()->attach(invalid));
  m=mapper();m.mBrightness[1]=m.mBrightness[0];m.calibrate();final PhysicalMappingStrategy flat=m;bad(()->attach(flat));
  m=mapper();m.mBrightness=new float[]{0,1};m.calibrate();final PhysicalMappingStrategy length=m;bad(()->attach(length));
  m=mapper();m.wrongForward=true;final PhysicalMappingStrategy extrapolation=m;bad(()->attach(extrapolation));
  m=mapper();m.wrongReverse=true;final PhysicalMappingStrategy inverse=m;bad(()->attach(inverse));
  m=mapper();m.mNitsToBrightnessSpline=new Spline.UnknownSpline(m.mNits,m.mBrightness);m.mAdjustedNitsToBrightnessSpline=m.mNitsToBrightnessSpline;final PhysicalMappingStrategy unknown=m;bad(()->attach(unknown));
  bad(()->attach(new PhysicalMappingStrategy(config(x,new float[]{2,30,100,800,700}))));bad(()->attach(new PhysicalMappingStrategy(config(x,new float[]{2,30,Float.NaN,700,1000}))));
  PhysicalMappingStrategy failed=mapper();TraditionalAdapter a=attach(failed);Object before=failed.mConfig;failed.fail=true;bad(()->a.configure(new float[]{.8f,.8f,.9f,1}));check(failed.mConfig==before&&a.plan()==null);
  PhysicalMappingStrategy foreign=mapper();TraditionalAdapter b=attach(foreign);b.configure(one);BrightnessConfiguration other=config(x,new float[]{3,35,110,710,1050});foreign.mConfig=other;bad(()->b.configure(one));check(foreign.mConfig==other);
  // Both upper and lower clamps are derived from actual tables, without model constants.
  for(float cap:new float[]{50,100,550,1000,3000})for(float minimum:new float[]{0,.1f,1}){
   PhysicalMappingStrategy v=new PhysicalMappingStrategy(config(x,new float[]{0,cap*.05f,cap*.2f,cap*2,cap*3}));v.mNits=new float[]{minimum,cap*.7f,cap};v.mBrightness=new float[]{.001f,.5f,1};v.calibrate();TraditionalAdapter q=attach(v);check(q.calibration.clamped&&q.min==minimum&&q.max==cap);q.configure(one);check(q.max==cap&&v.mConfig.mNits[4]==cap*3);q.configure(new float[]{.9f,.9f,.9f,1});for(float value:v.mConfig.mNits)check(value>=minimum&&value<=cap);
  }
  if(args.length>0){JSONObject sample=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8));replay(array(sample,"lux"),array(sample,"nit"),array(sample,"calibration_nits"),array(sample,"calibration_brightness"));System.out.println("Actual physical sample replay: "+sample.getJSONArray("lux").length()+" OEM nodes PASS; model replay, no device execution");}
  System.out.println("Physical calibration: "+cases+" cases PASS; production adapter, neutral/edited/rollback/old and rejected routes; no Android execution");
 }
 static float[] array(JSONObject j,String key){JSONArray a=j.getJSONArray(key);float[] v=new float[a.length()];for(int i=0;i<v.length;i++)v[i]=(float)a.getDouble(i);return v;}
}
"""}
paths=[]
for n,s in files.items():
 p=O/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,'utf-8');paths.append(str(p))
C=O/'classes';C.mkdir(exist_ok=True);cp=str(old)+os.pathsep+str(J)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',cp,'-d',str(C),*paths],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(C)+os.pathsep+cp,'top.rongshangs.lumacurve.refactor.PhysicalCalibrationTest'],check=True)
# Private replay is optional here; firmware verification explicitly requires its evidence.
sample=Path(sys.argv[sys.argv.index('--sample')+1]) if '--sample' in sys.argv else None
if sample is not None:subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(C)+os.pathsep+cp,'top.rongshangs.lumacurve.refactor.PhysicalCalibrationTest',str(sample)],check=True)
