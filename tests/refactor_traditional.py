"""Run production physical adapter and hooks against a bounded OEM callback model.
Not an Android/ART test. Fixtures are synthetic; no device firmware is distributed.
"""
from pathlib import Path
import ast, subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-traditional-tests';O.mkdir(exist_ok=True)
# Reuse only the callback engine fixture, with early-result behavior exercised here.
tree=ast.parse((R/'tests/refactor_advanced_hooks.py').read_text(encoding='utf-8'))
all_sources=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
sources={k:all_sources[k] for k in ['de/robv/android/xposed/XC_MethodHook.java','de/robv/android/xposed/XposedBridge.java']}
sources['de/robv/android/xposed/XC_MethodHook.java']=sources['de/robv/android/xposed/XC_MethodHook.java'].replace('Object result;Throwable','boolean early;Object result;Throwable').replace('setResult(Object r){result=r;}','setResult(Object r){result=r;early=true;}')
sources['de/robv/android/xposed/XposedBridge.java']=sources['de/robv/android/xposed/XposedBridge.java'].replace('try{p.result=m.invoke(o,args);}','try{if(!p.early)p.result=m.invoke(o,args);}')
sources.update({
'android/os/SystemClock.java':'''package android.os;public class SystemClock {public static long time=1000;public static long uptimeMillis(){return time;}public static long elapsedRealtime(){return time;}}''',
'android/os/Handler.java':'''package android.os;public class Handler {public void post(Runnable r){pending=r;}public Runnable pending;}''',
'android/hardware/display/BrightnessConfiguration.java':'''package android.hardware.display;import java.util.*;
public class BrightnessConfiguration {
 public final float[] mLux,mNits;public final Map mCorrectionsByPackageName,mCorrectionsByCategory;public final String mDescription;
 public final boolean mShouldCollectColorSamples;public final long mShortTermModelTimeout;public final float mShortTermModelLowerLuxMultiplier,mShortTermModelUpperLuxMultiplier;
 public BrightnessConfiguration(float[] x,float[] y,Map pkg,Map cat,String desc,boolean color,long timeout,float lower,float upper){mLux=x.clone();mNits=y.clone();mCorrectionsByPackageName=pkg;mCorrectionsByCategory=cat;mDescription=desc;mShouldCollectColorSamples=color;mShortTermModelTimeout=timeout;mShortTermModelLowerLuxMultiplier=lower;mShortTermModelUpperLuxMultiplier=upper;}
 public Pair getCurve(){return new Pair(mLux.clone(),mNits.clone());}public static class Pair{public Object first,second;Pair(Object x,Object y){first=x;second=y;}}
 public boolean equals(Object o){if(!(o instanceof BrightnessConfiguration))return false;BrightnessConfiguration b=(BrightnessConfiguration)o;return Arrays.equals(mLux,b.mLux)&&Arrays.equals(mNits,b.mNits)&&mCorrectionsByPackageName.equals(b.mCorrectionsByPackageName)&&mCorrectionsByCategory.equals(b.mCorrectionsByCategory)&&Objects.equals(mDescription,b.mDescription)&&mShouldCollectColorSamples==b.mShouldCollectColorSamples&&mShortTermModelTimeout==b.mShortTermModelTimeout&&mShortTermModelLowerLuxMultiplier==b.mShortTermModelLowerLuxMultiplier&&mShortTermModelUpperLuxMultiplier==b.mShortTermModelUpperLuxMultiplier;}
}''',
'com/android/server/display/MiuiPhysicalBrightnessMappingStrategy.java':'''package com.android.server.display;
import android.hardware.display.BrightnessConfiguration;import java.util.*;import de.robv.android.xposed.XposedBridge;
public class MiuiPhysicalBrightnessMappingStrategy {
 public float[] mNits={1,100,1000};public BrightnessConfiguration mConfig,mDefaultConfig;public Spline mBrightnessSpline;
 public float mShortTermModelUserLux=-1,mShortTermModelUserBrightness=-1,mAutoBrightnessAdjustment;
 public HashMap<Integer,Object> mSplineGroup=new HashMap<>(),mAdjustedSplineMapper=new HashMap<>();public boolean fail;public int mode,computes,dragCalls;
 public MiuiPhysicalBrightnessMappingStrategy(BrightnessConfiguration c){mConfig=mDefaultConfig=c;compute();}
 public int getMode(){return mode;}
 public boolean setBrightnessConfiguration(BrightnessConfiguration c){if(c==null)c=mDefaultConfig;if(c.equals(mConfig))return false;mConfig=c;compute();return true;}
 void compute(){computes++;mSplineGroup.put(0,"changed");mAdjustedSplineMapper.put(0,true);mBrightnessSpline=new Spline(mConfig.mLux,mConfig.mNits);if(fail)throw new IllegalStateException("synthetic rebuild failure");}
 public void clearUserDataPoints(){mShortTermModelUserLux=mShortTermModelUserBrightness=-1;mAutoBrightnessAdjustment=0;compute();}
 public void addUserDataPoint(float lux,float br){XposedBridge.call(this,"addUserDataPoint",new Class<?>[]{float.class,float.class,String.class},lux,br,null);}
 public void addUserDataPoint(float lux,float br,String pkg){dragCalls++;mShortTermModelUserLux=lux;mShortTermModelUserBrightness=br;mBrightnessSpline=new Spline(mConfig.mLux,mConfig.mNits);mBrightnessSpline.userLux=lux;mBrightnessSpline.userNit=convertToNits(br);}
 public float getUserBrightness(){return mShortTermModelUserBrightness;}
 public float convertToBrightness(float nit){return nit/1000;}public float convertToNits(float br){return br*1000;}
 public float getBrightness(float lux,String pkg,int category){return convertToBrightness(mBrightnessSpline.interpolate(lux));}
 public static class Spline {float[] x,y;float userLux=-1,userNit;Spline(float[] x,float[] y){this.x=x;this.y=y;}public float interpolate(float lux){if(lux==userLux)return userNit;if(lux<=x[0])return y[0];for(int i=1;i<x.length;i++)if(lux<=x[i])return y[i-1]+(y[i]-y[i-1])*(lux-x[i-1])/(x[i]-x[i-1]);return y[y.length-1];}}
}''',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;import java.util.*;
class HookEntry {static Map<Object,HookRuntime> states=new IdentityHashMap<>();static int reattached;static Object get(Object o,String n)throws Exception{return TraditionalAdapter.read(o,n);}static void reattach(Object owner,Object mapper){reattached++;}}''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;
class HookRuntime {CurveBackend kernel;Object owner;boolean closed,changing,faultPending,userMatches=true,onThread=true;String phase="active";float memoryStrength=1;long lastManualAdjustment;int events,samples;float remembered,lastNit;MemoryPolicy memoryPolicy=new MemoryPolicy();MemoryPersistence persistentMemory=new MemoryPersistence();android.os.Handler handler=new android.os.Handler();
 boolean onDisplayThread(){return onThread&&!closed;}boolean appliesToUser(){return userMatches;}void memoryEvent(float l,float d,float r){events++;remembered=r;}void queuePublish(){}void sample(float l,float n){samples++;lastNit=n;}void fault(Throwable e){faultPending=true;}}
''',
'top/rongshangs/lumacurve/refactor/TraditionalHostTest.java':'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import android.hardware.display.BrightnessConfiguration;import com.android.server.display.MiuiPhysicalBrightnessMappingStrategy;import de.robv.android.xposed.XposedBridge;
public class TraditionalHostTest {
 static int cases;static void check(boolean value){if(!value)throw new AssertionError("case "+cases);cases++;}
 static void near(float a,float b){check(Math.abs(a-b)<.001f);}static void bad(Runnable r){try{r.run();throw new AssertionError("invalid accepted");}catch(IllegalArgumentException e){cases++;}}
 public static class BaseInterp{public float interpolate(float f){return 0;}}public static class ChildA extends BaseInterp{public float interpolate(float f){return 1;}}public static class ChildB extends BaseInterp{public float interpolate(float f){return 2;}}
 public static class Owner{int mDisplayId;boolean mUseAutoBrightness=true;}
 public static class Map0 {Object mapper;public Object get(int i){return i==0?mapper:null;}}
 public static class Model {float anchor=30;boolean valid=true;}
 public static class ABC {Map0 mBrightnessMappingStrategyMap=new Map0();Object mCurrentBrightnessMapper;Model mShortTermModel=new Model();int resets;
   public boolean setScreenBrightnessByUser(float lux,float value){try{TraditionalAdapter.method(mCurrentBrightnessMapper.getClass(),"addUserDataPoint",float.class,float.class).invoke(mCurrentBrightnessMapper,lux,value);mShortTermModel.anchor=lux;mShortTermModel.valid=true;return true;}catch(Exception e){throw new RuntimeException(e);}}
   public void resetShortTermModel(){resets++;try{TraditionalAdapter.method(mCurrentBrightnessMapper.getClass(),"clearUserDataPoints").invoke(mCurrentBrightnessMapper);}catch(Exception e){throw new RuntimeException(e);}mShortTermModel.anchor=-1;mShortTermModel.valid=false;}}
 static BrightnessConfiguration config(float[] x,float[] y){Map<String,Object> pkg=new HashMap<>();pkg.put("video","correction");Map<Integer,Object> cat=new HashMap<>();cat.put(3,"category");return new BrightnessConfiguration(x,y,pkg,cat,"device local",true,300000,.6f,.8f);}
 static Object call(Object o,String method,Class<?>[] types,Object...a){return XposedBridge.call(o,method,types,a);}
 static final Class<?>[] DRAG={float.class,float.class,String.class},CURVE={BrightnessConfiguration.class};
 public static void main(String[] args)throws Exception {
  check(((Float)TraditionalAdapter.interpolator(new ChildA()).invoke(new ChildB(),1f))==2f);
  check(BackendSelection.choose(false,true,true).equals("physical_mapping"));check(BackendSelection.choose(true,false,true).equals("waiting"));check(BackendSelection.choose(true,true,false).equals("refactor"));check(BackendSelection.choose(null,false,false).equals("waiting"));
  float[] x={0,1,5,10,30,100,300,600,1000,5000,10000,100000},y={2,6,8,12,30,45,80,120,200,600,1000,1000},one={1,1,1,1},f={.5f,.8f,.9f,1};
  TraditionalCurve dense=new TraditionalCurve(x,y);check(Arrays.equals(dense.controlsLux(),new float[]{0,30,600,100000}));check(Arrays.equals(dense.reshape(1,1000,one),y));
  float[] shape=dense.reshape(1,1000,f);near(shape[0],1);near(shape[4],24);near(shape[7],108);near(shape[shape.length-1],1000);check(shape[10]==shape[11]);
  for(int i=1;i<shape.length;i++)check(shape[i]>=shape[i-1]);
  Random random=new Random(24);for(int t=0;t<200;t++){float[] p={.1f+random.nextFloat()*1.9f,.1f+random.nextFloat()*1.9f,.1f+random.nextFloat()*1.9f,1};try{float[] n=dense.reshape(1,1000,p);for(int i=1;i<n.length;i++)check(n[i]>=n[i-1]&&n[i]<=1000);}catch(IllegalArgumentException rejected){cases++;}}
  bad(()->new TraditionalCurve(new float[]{0,1,1,10},new float[]{2,3,4,5}));bad(()->dense.reshape(1,1000,new float[]{1,1,1,.5f}));bad(()->dense.reshape(1,1000,new float[]{Float.NaN,1,1,1}));
  float[] cloned=dense.nit();cloned[0]=900;check(dense.nit()[0]==2);
  String id=CurveIdentity.of("physical_mapping",x,y,1,1000);check(!id.equals(CurveIdentity.of("refactor",x,y,1,1000)));check(!id.equals(CurveIdentity.of("physical_mapping",x,shape,1,1000)));
  Owner owner=new Owner();BrightnessConfiguration nativeConfig=config(x,y);MiuiPhysicalBrightnessMappingStrategy m=new MiuiPhysicalBrightnessMappingStrategy(nativeConfig);ABC abc=new ABC();abc.mBrightnessMappingStrategyMap.mapper=abc.mCurrentBrightnessMapper=m;
  TraditionalAdapter a=new TraditionalAdapter(owner,m,abc,()->0);check(a.name().equals("physical_mapping"));check(Arrays.equals(a.fullNit(),y));
  a.configure(one);check(a.plan()!=null&&m.mConfig==nativeConfig&&abc.resets==1);a.configure(f);check(Arrays.equals(m.mConfig.mNits,shape));check(m.mConfig.mCorrectionsByPackageName.equals(nativeConfig.mCorrectionsByPackageName));check(m.mConfig.mCorrectionsByCategory.equals(nativeConfig.mCorrectionsByCategory));check(m.mConfig.mDescription.equals(nativeConfig.mDescription)&&m.mConfig.mShortTermModelTimeout==300000&&m.mConfig.mShouldCollectColorSamples&&m.mConfig.mShortTermModelLowerLuxMultiplier==.6f);
  near(a.currentAt(30),24);near(a.memoryAt(30),.024f);check(a.currentLux().length>=x.length);check(a.currentLux().length==a.currentNit().length);
  check(a.persistentMemorySupported()&&a.manualPointCapacity()==1);check(a.manualPoints().length()==0);
  org.json.JSONArray record=new org.json.JSONArray().put(new org.json.JSONObject().put("lux",30).put("value",.15));
  a.restoreManualPoints(record);near(a.currentAt(30),150);check(abc.mShortTermModel.anchor==30&&abc.mShortTermModel.valid);check(a.manualPoints().getJSONObject(0).getDouble("value")>.149);a.clearMemory();
  try{a.restoreManualPoints(new org.json.JSONArray().put(new org.json.JSONObject().put("lux",30).put("value",2)));throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}check(a.manualPoints().length()==0);
  m.addUserDataPoint(30,.1f,"video");near(a.currentAt(30),100);check(a.plan().at(30)==24);check(a.number("mUserLux")==30);a.clearMemory();near(a.currentAt(30),24);check(a.plan()!=null);
  m.addUserDataPoint(30,.15f,"video");Object previousConfig=m.mConfig,previousSpline=m.mBrightnessSpline;abc.mShortTermModel.anchor=30;abc.mShortTermModel.valid=true;m.mSplineGroup.put(0,"before");m.fail=true;
  try{a.configure(new float[]{1,1,1,1});throw new AssertionError("failure accepted");}catch(IllegalStateException expected){check(m.mConfig==previousConfig&&m.mBrightnessSpline==previousSpline);near(m.mShortTermModelUserBrightness,.15f);check(m.mSplineGroup.get(0).equals("before"));check(abc.mShortTermModel.anchor==30&&abc.mShortTermModel.valid);check(a.plan().at(30)==24);}m.fail=false;
  a.configure(null);check(a.plan()==null&&m.mConfig==nativeConfig);check(Arrays.equals(m.mConfig.mNits,y));
  a.configure(one,20);near(m.mConfig.mNits[0],20);near(a.plan().at(0),20);near(m.mConfig.mNits[4],30);check(m.mConfig.mNits[m.mConfig.mNits.length-1]==1000);for(int i=1;i<m.mConfig.mNits.length;i++)check(m.mConfig.mNits[i]>=m.mConfig.mNits[i-1]);a.clearMemory();near(m.mConfig.mNits[0],20);a.configure(null);check(m.mConfig==nativeConfig&&m.mConfig.mNits[0]==2);
  TraditionalCurve zero=new TraditionalCurve(new float[]{0,1,30,600,100000},new float[]{0,1,20,100,1000});float[] raisedZero=zero.reshape(0,1000,one,10);near(raisedZero[0],10);check(raisedZero[1]>=10&&raisedZero[1]<=20);near(raisedZero[2],20);near(raisedZero[4],1000);bad(()->zero.reshape(0,1000,one,21));
  Object idle=new Object();abc.mCurrentBrightnessMapper=idle;int resets=abc.resets;a.configure(f);check(abc.resets==resets&&abc.mCurrentBrightnessMapper==idle);a.configure(null);abc.mCurrentBrightnessMapper=m;
  owner.mDisplayId=1;try{new TraditionalAdapter(owner,m,abc,()->0);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}owner.mDisplayId=0;
  abc.mBrightnessMappingStrategyMap.mapper=new Object();try{new TraditionalAdapter(owner,m,abc,()->0);throw new AssertionError();}catch(IllegalStateException expected){cases++;}abc.mBrightnessMappingStrategyMap.mapper=m;
  HookRuntime s=new HookRuntime();s.kernel=a;s.owner=owner;HookEntry.states.put(m,s);TraditionalHooks.install(m.getClass());s.changing=true;a.configure(f);s.changing=false;
  s.memoryStrength=.5f;call(m,"addUserDataPoint",DRAG,30f,.2f,"video");near(m.mShortTermModelUserBrightness,.112f);check(s.events==1);android.os.SystemClock.time+=50;call(m,"addUserDataPoint",DRAG,30f,.3f,"video");near(m.mShortTermModelUserBrightness,.162f);check(s.events==2); // One gesture uses its start baseline.
  s.memoryStrength=0;s.persistentMemory.pendingRestore=true;int calls=m.dragCalls;call(m,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.4f);check(m.dragCalls==calls&&Float.isNaN(s.remembered));check(!s.persistentMemory.pendingRestore);
  s.memoryStrength=1;s.userMatches=false;call(m,"addUserDataPoint",DRAG,30f,.4f,"video");near(m.mShortTermModelUserBrightness,.4f);s.userMatches=true;
  int events=s.events;abc.mCurrentBrightnessMapper=idle;call(m,"addUserDataPoint",DRAG,30f,.5f,"video");check(s.events==events);abc.mCurrentBrightnessMapper=m;
  s.changing=true;a.clearMemory();s.changing=false;call(m,"getBrightness",new Class<?>[]{float.class,String.class,int.class},30f,"video",0);check(s.samples==1);near(s.lastNit,24);
  call(m,"setBrightnessConfiguration",CURVE,(Object)null);check(m.mConfig==a.applied&&s.kernel.plan()!=null); // Normal system configure must not erase the custom baseline.
  int computes=m.computes;call(m,"setBrightnessConfiguration",CURVE,nativeConfig);check(m.mConfig==a.applied&&m.computes==computes);
  s.changing=true;a.configure(null);s.changing=false;check(m.mConfig==nativeConfig);s.phase="attached";call(m,"setBrightnessConfiguration",CURVE,(Object)null);check(m.mConfig==nativeConfig);
  s.phase="active";s.changing=true;a.configure(f);s.changing=false;BrightnessConfiguration external=config(x,dense.reshape(1,1000,new float[]{1,.9f,1,1}));call(m,"setBrightnessConfiguration",CURVE,external);check(s.faultPending&&m.mConfig==external);a.configure(null);check(m.mConfig==external&&a.plan()==null);check(s.handler.pending!=null);s.handler.pending.run();check(HookEntry.reattached==1);
  System.out.println("Traditional curve/adapter/hooks: "+cases+" cases PASS; synthetic callback model, device not verified");
 }
}'''
})
# AOSP physical mapping uses adjusted nit conversion and a two-argument memory method.
aosp=sources['com/android/server/display/MiuiPhysicalBrightnessMappingStrategy.java']
aosp=aosp.replace('public class MiuiPhysicalBrightnessMappingStrategy {','public class BrightnessMappingStrategy {public static class PhysicalMappingStrategy {').replace('public MiuiPhysicalBrightnessMappingStrategy(','public PhysicalMappingStrategy(')
aosp=aosp.replace('mShortTermModelUserLux','mUserLux').replace('mShortTermModelUserBrightness','mUserBrightness')
aosp=aosp.replace('public float[] mNits={1,100,1000};','public float[] mNits={1,100,1000};public Spline mAdjustedNitsToBrightnessSpline=new Spline(new float[]{1,1000},new float[]{.002f,2});')
aosp=aosp.replace('public void addUserDataPoint(float lux,float br){XposedBridge.call(this,"addUserDataPoint",new Class<?>[]{float.class,float.class,String.class},lux,br,null);}','')
aosp=aosp.replace('public void addUserDataPoint(float lux,float br,String pkg)','public void addUserDataPoint(float lux,float br)')
aosp=aosp+'}'
sources['com/android/server/display/BrightnessMappingStrategy.java']=aosp
fixture=sources['top/rongshangs/lumacurve/refactor/TraditionalHostTest.java']
fixture=fixture.replace('  System.out.println("Traditional curve/adapter/hooks:','''  com.android.server.display.BrightnessMappingStrategy.PhysicalMappingStrategy other=new com.android.server.display.BrightnessMappingStrategy.PhysicalMappingStrategy(nativeConfig);
  ABC otherAbc=new ABC();otherAbc.mBrightnessMappingStrategyMap.mapper=otherAbc.mCurrentBrightnessMapper=other;
  TraditionalAdapter otherAdapter=new TraditionalAdapter(owner,other,otherAbc,()->0);near(otherAdapter.memoryAt(30),.06f);check(Arrays.equals(otherAdapter.fullNit(),y));
  TraditionalHooks.install(other.getClass());HookRuntime otherState=new HookRuntime();otherState.kernel=otherAdapter;otherState.owner=owner;otherState.memoryStrength=.5f;HookEntry.states.put(other,otherState);
  call(other,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.2f);near(other.mUserBrightness,.13f);check(otherState.events==1);check(otherAdapter.number("mUserLux")==30);
  otherState.changing=true;otherAdapter.configure(f);otherState.changing=false;near(other.mConfig.mNits[4],24);check(otherAbc.resets==1);otherAdapter.configure(null);check(other.mConfig==nativeConfig&&otherAdapter.plan()==null);
  otherState.memoryStrength=0;int beforeDrag=other.dragCalls;call(other,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.4f);check(other.dragCalls==beforeDrag);
  System.out.println("Traditional curve/adapter/hooks:''')
sources['top/rongshangs/lumacurve/refactor/TraditionalHostTest.java']=fixture

sources['top/rongshangs/lumacurve/refactor/MemoryPersistence.java']='''package top.rongshangs.lumacurve.refactor;class MemoryPersistence {boolean replaying,pendingRestore;int captures,resets;void cancelRestore(){pendingRestore=false;}void manualApplied(){captures++;}void afterReset(){resets++;}void maybeRestore(){}}'''
J=R/'build/refactor-diagnostics/json-20240303.jar'
files=[]
for name,value in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
production=['CurvePlan','CurveBackend','TraditionalCurve','TraditionalAdapter','TraditionalHooks','CurveIdentity','BackendSelection','MemoryPolicy','MemoryOptions','PersistentMemory']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(f) for f in files],*[str(S/(n+'.java')) for n in production]],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.TraditionalHostTest'],check=True)
