"""Simple brightness-coordinate production adapter: synthetic ABI and native callback regression."""
from pathlib import Path
import runpy,subprocess,os,sys
R=Path(__file__).resolve().parents[1];base=runpy.run_path(str(R/'tests/refactor_traditional.py'));S=base['S'];J=base['J'];old=base['C']
O=R/'build/simple-mapping-tests';O.mkdir(exist_ok=True)
sources={
 'android/util/Pair.java':r'''package android.util;public class Pair{public final Object first,second;public Pair(Object x,Object y){first=x;second=y;}}''',
 'android/util/Spline.java':r'''package android.util;
public abstract class Spline {public abstract float interpolate(float x);public static Spline createSpline(float[] x,float[] y){return new LinearSpline(x,y);}
 public static class LinearSpline extends Spline {public final float[] mX,mY;LinearSpline(float[] x,float[] y){mX=x.clone();mY=y.clone();}public float interpolate(float v){if(v<=mX[0])return mY[0];for(int i=1;i<mX.length;i++)if(v<=mX[i])return mY[i-1]+(mY[i]-mY[i-1])*(v-mX[i-1])/(mX[i]-mX[i-1]);return mY[mY.length-1];}}}''',
'com/android/server/display/BrightnessMappingStrategy.java':r'''package com.android.server.display;
import android.util.Spline;import android.util.Pair;import java.util.*;import android.hardware.display.BrightnessConfiguration;import top.rongshangs.lumacurve.refactor.hooking.XposedBridge;
public class BrightnessMappingStrategy {public static class SimpleMappingStrategy {
 public final float[] mLux,mBrightness;public Spline mSpline;public float mUserLux=-1,mUserBrightness=Float.NaN,mAutoBrightnessAdjustment,mMaxGamma=3;public boolean fail,badOutput;public Runnable beforeCompute;public int mode,computes,drags;
 public SimpleMappingStrategy(float[] x,float[] y){mLux=x.clone();mBrightness=y.clone();computeSpline();}
 private void computeSpline(){computes++;if(beforeCompute!=null)beforeCompute.run();Pair p=(Pair)XposedBridge.call(this,"getAdjustedCurve",new Class<?>[]{float[].class,float[].class,float.class,float.class,float.class,float.class},mLux,mBrightness,mUserLux,mUserBrightness,mAutoBrightnessAdjustment,mMaxGamma);mSpline=Spline.createSpline((float[])p.first,(float[])p.second);if(fail)throw new IllegalStateException("native compute failed");if(badOutput)mSpline=Spline.createSpline(new float[]{0,100000},new float[]{2,2});}
 private float getUnadjustedBrightness(float lux){return Spline.createSpline(mLux,mBrightness).interpolate(lux);}
 protected Pair getAdjustedCurve(float[] x,float[] y,float lux,float b,float adjustment,float gamma){TreeMap<Float,Float> points=new TreeMap<>();for(int i=0;i<x.length;i++)points.put(x[i],lux<0?y[i]:x[i]<lux?Math.min(y[i],b):Math.max(y[i],b));if(lux>=0)points.put(lux,b);float[] xx=new float[points.size()],yy=new float[points.size()];int i=0;for(Map.Entry<Float,Float> e:points.entrySet()){xx[i]=e.getKey();yy[i++]=e.getValue();}return new Pair(xx,yy);}
 public void clearUserDataPoints(){mUserLux=-1;mUserBrightness=Float.NaN;mAutoBrightnessAdjustment=0;computeSpline();}
 public void addUserDataPoint(float lux,float b){drags++;XposedBridge.call(this,"getUnadjustedBrightness",new Class<?>[]{float.class},lux);mUserLux=lux;mUserBrightness=b;computeSpline();}
 public boolean setAutoBrightnessAdjustment(float a){mAutoBrightnessAdjustment=a;computeSpline();return true;}
 public int getMode(){return mode;}public float getUserBrightness(){return mUserBrightness;}
 public float getBrightness(float lux,String p,int c){return mSpline.interpolate(lux);}
 public boolean setBrightnessConfiguration(BrightnessConfiguration ignored){return false;}
 public float convertToNits(float b){return -1;}
 }}''',
 'top/rongshangs/lumacurve/refactor/SimpleHostTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import com.android.server.display.BrightnessMappingStrategy.SimpleMappingStrategy;import top.rongshangs.lumacurve.refactor.hooking.XposedBridge;
public class SimpleHostTest {
 static int cases;static float[] x={0,5,30,100,600,5000,100000},y={.002f,.01f,.03f,.05f,.1f,.25f,.25f},one={1,1,1,1};
 static void check(boolean b){if(!b)throw new AssertionError("simple case "+cases);cases++;}static void near(float a,float b){check(Math.abs(a-b)<.00001f);}
 interface Work{void run()throws Exception;}static void bad(Work w)throws Exception{try{w.run();throw new AssertionError("bad accepted "+cases);}catch(IllegalArgumentException|IllegalStateException|ReflectiveOperationException expected){cases++;}}
 public static class Owner {int mDisplayId;boolean mUseAutoBrightness=true;Object mBrightnessMapper;Impl mAutomaticBrightnessControllerImpl=new Impl();}
 public static class Impl{Object mAutomaticBrightnessController;}
 public static class Map0 {Object mapper;public Object get(int i){return i==0?mapper:null;}}
 public static class NoReplayABC {Object mBrightnessMappingStrategyMap,mCurrentBrightnessMapper,mShortTermModel;public void resetShortTermModel(){}}
 public static class Model {float mAnchor=30,mBrightness=.1f;boolean mIsValid=true;}
 public static class ABC {Map0 mBrightnessMappingStrategyMap=new Map0();Object mCurrentBrightnessMapper;Model mShortTermModel=new Model();boolean reject;int resets;
 public void resetShortTermModel(){resets++;((SimpleMappingStrategy)mCurrentBrightnessMapper).clearUserDataPoints();mShortTermModel.mAnchor=-1;mShortTermModel.mIsValid=false;}
 public boolean setScreenBrightnessByUser(float lux,float b){((SimpleMappingStrategy)mCurrentBrightnessMapper).addUserDataPoint(lux,b);mShortTermModel.mAnchor=lux;mShortTermModel.mBrightness=b;mShortTermModel.mIsValid=true;return !reject;}}
 static SimpleCurveAdapter create(Owner o,SimpleMappingStrategy m,ABC a)throws Exception{o.mBrightnessMapper=m;o.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController=a;a.mBrightnessMappingStrategyMap.mapper=m;a.mCurrentBrightnessMapper=m;return new SimpleCurveAdapter(o,m,a,()->7);}
 static Object call(Object m,String n,Class<?>[] types,Object...args){return XposedBridge.call(m,n,types,args);}
 public static void main(String[] args)throws Exception {
  Owner o=new Owner();ABC abc=new ABC();SimpleMappingStrategy m=new SimpleMappingStrategy(x,y);m.addUserDataPoint(30,.12f);Object spline=m.mSpline;int computes=m.computes;SimpleCurveAdapter a=create(o,m,abc);HookRuntime bound=new HookRuntime();bound.kernel=a;bound.owner=o;HookEntry.states.put(m,bound);int hooksBefore=XposedBridge.live();XposedBridge.failAt=XposedBridge.installs+4;bad(()->a.installMappingHooks(m.getClass()));check(XposedBridge.live()==hooksBefore&&!TraditionalHooks.installed.contains(m.getClass()));XposedBridge.failAt=-1;a.installMappingHooks(m.getClass());
  check(m.mSpline==spline&&m.computes==computes&&abc.resets==0);check(a.name().equals("simple_mapping")&&a.coordinate().equals("framework_brightness")&&a.nativeMappingHooks());check(a.integer("mUserSerial")==7);check(a.min==0&&a.max==1);check(a.savedCurveSupported());check(CurveBackendFactory.create(o,m) instanceof SimpleCurveAdapter);check(Float.isNaN(a.mappingOutputNit(.2f)));
  a.configure(one);check(a.plan()!=null&&m.mSpline==spline&&abc.resets==0);near(a.currentAt(30),.12f);near(a.memoryAt(30),.12f);
  bound.phase="attached";bound.changing=true;check(!bound.appliesToUser());m.mAutoBrightnessAdjustment=.1f; a.configure(new float[]{1,2,2,2});check(m.mAutoBrightnessAdjustment==0);near(a.baselineNit()[2],.06f);near(a.currentAt(30),.06f);check(abc.resets==1&&m.mLux.length==x.length);bound.boundUser=7;bound.changing=false;bound.phase="active";check(Arrays.equals(a.fullNit(),y)&&Arrays.equals(a.baselineLux(),x));
  for(int i=1;i<m.mBrightness.length;i++)check(m.mBrightness[i]>=m.mBrightness[i-1]);
  a.configure(null);check(Arrays.equals(m.mBrightness,y)&&a.plan()==null);
  check(a.persistentMemorySupported()&&a.manualPointCapacity()==1);JSONArray point=new JSONArray().put(new JSONObject().put("lux",30).put("value",.4));a.restoreManualPoints(point);near(a.currentAt(30),.4f);near(a.manualDisplayValue(.4f),.4f);check(a.manualPoints().length()==1&&abc.mShortTermModel.mAnchor==30);
  Object before=m.mSpline;float[] curveBefore=m.mBrightness.clone();abc.reject=true;bad(()->a.replaceManualPoints(new JSONArray().put(new JSONObject().put("lux",100).put("value",.6))));check(m.mSpline==before&&m.mUserLux==30&&abc.mShortTermModel.mAnchor==30);abc.reject=false;
  m.fail=true;bad(()->a.configure(new float[]{1,1.5f,2,2}));check(m.mSpline==before&&m.mUserLux==30&&abc.mShortTermModel.mAnchor==30&&Arrays.equals(m.mBrightness,curveBefore));m.fail=false;
  m.badOutput=true;bad(()->a.configure(new float[]{1,1.5f,2,2}));check(m.mSpline==before&&Arrays.equals(m.mBrightness,curveBefore));m.badOutput=false;
  bad(()->a.configure(new float[]{1,Float.NaN,1,1}));m.addUserDataPoint(75,.35f);float[] learnedX=a.currentLux(),learnedY=a.currentNit();SavedCurve saved=new SavedCurve(learnedX,learnedY);a.configure(one,0,saved);check(Arrays.equals(a.currentLux(),learnedX)&&Arrays.equals(a.currentNit(),learnedY));check(Arrays.equals(m.mLux,x)&&Arrays.equals(m.mBrightness,y));a.configure(null);a.savedBase=null;a.configure(one);
  bad(()->a.restoreManualPoints(new JSONArray().put(new JSONObject().put("lux",100001).put("value",.5))));bad(()->a.restoreManualPoints(new JSONArray().put(new JSONObject().put("lux",30).put("value",2))));
  Object idle=new Object();abc.mCurrentBrightnessMapper=idle;int resets=abc.resets;a.configure(new float[]{1,2,2,2});check(abc.resets==resets&&abc.mCurrentBrightnessMapper==idle);check(!a.memoryContextActive());bad(()->a.restoreManualPoints(point));a.configure(null);abc.mCurrentBrightnessMapper=m;
  a.configure(new float[]{1,2,2,2});m.mBrightness[2]=.07f;bad(()->a.configure(one));a.configure(null);near(m.mBrightness[2],.07f); // Foreign replacement remains intact.
  SimpleMappingStrategy other=new SimpleMappingStrategy(x,y);SimpleCurveAdapter b=create(new Owner(),other,new ABC());check(Arrays.equals(other.mBrightness,y));
  o.mDisplayId=1;bad(()->new SimpleCurveAdapter(o,m,abc,()->0));o.mDisplayId=0;m.mode=1;bad(()->new SimpleCurveAdapter(o,m,abc,()->0));m.mode=0;abc.mBrightnessMappingStrategyMap.mapper=idle;bad(()->new SimpleCurveAdapter(o,m,abc,()->0));
  for(float[] invalid:new float[][]{{.1f,.2f,.3f,.4f,.5f,1,1.1f},{.1f,.2f,Float.NaN,.4f,.5f,1,1},{.1f,.2f,.1f,.4f,.5f,1,1}}){SimpleMappingStrategy invalidMapper=new SimpleMappingStrategy(x,invalid);bad(()->create(new Owner(),invalidMapper,new ABC()));}
  HookRuntime state=new HookRuntime();state.boundUser=7;state.kernel=b;state.owner=b.owner;HookEntry.states.put(other,state);TraditionalHooks.install(other.getClass(),false);state.memoryStrength=.5f;
  call(other,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.5f);near(other.mUserBrightness,.265f);check(state.events==1);
  state.memoryStrength=0;int drags=other.drags;call(other,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.7f);check(other.drags==drags);
  call(other,"getBrightness",new Class<?>[]{float.class,String.class,int.class},30f,"video",0);check(state.samples==1&&Float.isNaN(state.lastNit)); // No fabricated physical nit.
  state.userMatches=false;state.memoryStrength=0;call(other,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.6f);check(other.drags==drags+1);state.userMatches=true;
  state.onThread=false;int events=state.events;call(other,"addUserDataPoint",new Class<?>[]{float.class,float.class},30f,.7f);check(state.events==events);state.onThread=true;
  JSONObject status=new JSONObject().put("curve_coordinate",a.coordinate()).put("factory_lux",new JSONArray(a.factoryLux)).put("factory_logical_nit",new JSONArray(a.factoryNit)).put("factory_full_lux",new JSONArray(x)).put("factory_full_nit",new JSONArray(y)).put("min_logical_nit",0).put("max_logical_nit",1).put("current_anchors_lux",new JSONArray(x)).put("current_anchors_nit",new JSONArray(y)).put("memory_live_count",1);abc.mCurrentBrightnessMapper=m;status.put(CurveCompatibility.KEY,a.curveContract(status));CurveCompatibility.Profile profile=CurveCompatibility.read(status);check(profile.save&&profile.coordinate.equals("framework_brightness"));check(Arrays.equals(CurveCompatibility.curves(status,one,0,true,null).baselineLux,x));
  status.getJSONObject(CurveCompatibility.KEY).put("coordinate","physical_nit");bad(()->CurveCompatibility.read(status));
  if(args.length>0){JSONObject input=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8));float[] xx=SavedCurve.numbers(input.getJSONArray("lux")),yy=SavedCurve.numbers(input.getJSONArray("brightness"));SimpleMappingStrategy replay=new SimpleMappingStrategy(xx,yy);SimpleCurveAdapter route=create(new Owner(),replay,new ABC());HookRuntime replayState=new HookRuntime();replayState.kernel=route;replayState.owner=route.owner;HookEntry.states.put(replay,replayState);route.configure(one);check(Arrays.equals(replay.mLux,xx)&&Arrays.equals(replay.mBrightness,yy));route.configure(new float[]{1,1.1f,1.1f,1.1f});check(route.baselineNit()[yy.length-1]>yy[yy.length-1]);for(int i=0;i<xx.length;i++)near(route.currentAt(xx[i]),route.baselineNit()[i]);route.configure(null);check(Arrays.equals(replay.mBrightness,yy));}
  // Same production ordering as reload(): validate current user, configure, then commit boundUser.
  String reload=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args.length>1?args[1]:"experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java")),java.nio.charset.StandardCharsets.UTF_8);
  check(reload.indexOf("config.getInt(\"user_serial\")!=kernel.integer")<reload.indexOf("kernel.configure(factors,floor,nextBase)"));check(reload.indexOf("kernel.configure(factors,floor,nextBase)")<reload.indexOf("boundUser=config.getInt"));
  final int[] identity={7};Owner bootOwner=new Owner();ABC bootAbc=new ABC();SimpleMappingStrategy bootMapper=new SimpleMappingStrategy(x,y);bootOwner.mBrightnessMapper=bootMapper;bootAbc.mBrightnessMappingStrategyMap.mapper=bootAbc.mCurrentBrightnessMapper=bootMapper;
  SimpleCurveAdapter boot=new SimpleCurveAdapter(bootOwner,bootMapper,bootAbc,()->identity[0]);HookRuntime bootState=new HookRuntime();bootState.kernel=boot;bootState.owner=bootOwner;bootState.phase="attached";bootState.changing=true;HookEntry.states.put(bootMapper,bootState);
  check(!bootState.appliesToUser());boot.configure(new float[]{1,2,2,2});near(boot.currentAt(30),.06f);check((Integer)TraditionalAdapter.read(boot,"transactionUser")==-1);near((Float)call(bootMapper,"getUnadjustedBrightness",new Class<?>[]{float.class},30f),.03f); // Scope ends at commit return.
  bootState.boundUser=7;near((Float)call(bootMapper,"getUnadjustedBrightness",new Class<?>[]{float.class},30f),.06f);boot.configure(null);bootState.boundUser=-1;
  SavedCurve bootSaved=new SavedCurve(new float[]{0,5,30,75,100,600,5000,100000},new float[]{.002f,.01f,.04f,.06f,.08f,.15f,.4f,.4f});boot.configure(one,0,bootSaved);check(Arrays.equals(boot.currentLux(),bootSaved.lux)&&Arrays.equals(boot.currentNit(),bootSaved.nit));boot.configure(null);boot.savedBase=null;
  int nativeBefore=bootMapper.computes;Object nativeSpline=bootMapper.mSpline;identity[0]=-1;bad(()->boot.configure(new float[]{1,2,2,2}));check(nativeBefore==bootMapper.computes&&nativeSpline==bootMapper.mSpline&&boot.plan()==null);identity[0]=7;
  bootMapper.fail=true;bad(()->boot.configure(new float[]{1,2,2,2}));check((Integer)TraditionalAdapter.read(boot,"transactionUser")==-1&&bootMapper.mSpline==nativeSpline);JSONObject failure=new JSONObject();boot.putCurveState(failure);check(failure.getString("simple_curve_last_error").contains("native compute failed"));bootMapper.fail=false;
  bootMapper.beforeCompute=()->identity[0]=8;bad(()->boot.configure(new float[]{1,2,2,2}));check(boot.plan()==null&&bootMapper.mSpline==nativeSpline&&(Integer)TraditionalAdapter.read(boot,"transactionUser")==-1);bootMapper.beforeCompute=null;identity[0]=7;
  boot.configure(new float[]{1,2,2,2});near(boot.currentAt(30),.06f);JSONObject recovered=new JSONObject();boot.putCurveState(recovered);check(recovered.getString("simple_curve_last_error").isEmpty());boot.configure(null);check(Arrays.equals(bootMapper.mLux,x)&&Arrays.equals(bootMapper.mBrightness,y));
  Owner readOwner=new Owner();ABC donor=new ABC();SimpleMappingStrategy learned=new SimpleMappingStrategy(x,y);donor.mBrightnessMappingStrategyMap.mapper=learned;
  NoReplayABC noReplay=new NoReplayABC();noReplay.mBrightnessMappingStrategyMap=donor.mBrightnessMappingStrategyMap;noReplay.mCurrentBrightnessMapper=learned;noReplay.mShortTermModel=donor.mShortTermModel;readOwner.mBrightnessMapper=learned;readOwner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController=noReplay;
  SimpleCurveAdapter readOnly=new SimpleCurveAdapter(readOwner,learned,noReplay,()->7);check(readOnly.manualPointCapacity()==1&&!readOnly.persistentMemorySupported());learned.addUserDataPoint(30,.4f);check(readOnly.manualPoints().length()==1);near(readOnly.currentAt(30),.4f);bad(()->readOnly.restoreManualPoints(new JSONArray().put(new JSONObject().put("lux",30).put("value",.5))));near(readOnly.currentAt(30),.4f);
  System.out.println("Simple mapping: "+cases+" cases PASS; production adapter/hooks, ABI doubles; not Android/ART verification");
 }}'''
}
sources['top/rongshangs/lumacurve/refactor/HookEntry.java']=base['sources']['top/rongshangs/lumacurve/refactor/HookEntry.java'].replace('class HookEntry {','class HookEntry {static final String REFACTOR="com.android.server.display.RefactorNitController";static User currentUser=new User();static class User{int serial(){return 7;}}static java.lang.reflect.Field field(Class<?> c,String n)throws Exception{return TraditionalAdapter.find(c,n);}')
sources['top/rongshangs/lumacurve/refactor/HookRuntime.java']=base['sources']['top/rongshangs/lumacurve/refactor/HookRuntime.java'].replace('CurveBackend kernel;', 'int boundUser=-1;CurveBackend kernel;').replace('return userMatches;', 'return userMatches&&ForegroundUser.matches(boundUser,kernel.integer("mUserSerial"));').replace('boolean appliesToUser(){','boolean appliesToUser(){try{').replace('kernel.integer("mUserSerial"));}', 'kernel.integer("mUserSerial"));}catch(Exception e){return false;}}')
sources['top/rongshangs/lumacurve/refactor/RefactorProfiles.java']='package top.rongshangs.lumacurve.refactor;class RefactorProfiles{static CurveBackend create(Object o,float min,float max){throw new IllegalArgumentException("synthetic Refactor profile unavailable");}}'
bridge=base['sources']['top/rongshangs/lumacurve/refactor/hooking/XposedBridge.java']
bridge=bridge.replace('static Map<Method,List<XC_MethodHook>> hooks=', 'public static int installs,failAt=-1;public static int live(){int total=0;for(List<XC_MethodHook> h:hooks.values())total+=h.size();return total;}static Map<Method,List<XC_MethodHook>> hooks=')
bridge=bridge.replace('hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);','if(installs++==failAt)throw new IllegalStateException("synthetic install failure");hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);')
sources['top/rongshangs/lumacurve/refactor/hooking/XposedBridge.java']=bridge
files=[]
for name,value in sources.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,'utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True);cp=str(C)+os.pathsep+str(old)+os.pathsep+str(J)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(old)+os.pathsep+str(J),'-d',str(C),*[str(p) for p in files],str(S/'SimpleCurveAdapter.java'),str(S/'CurveBackendFactory.java'),str(S/'ForegroundUser.java')],check=True)
subprocess.run(['java','-cp',cp,'top.rongshangs.lumacurve.refactor.SimpleHostTest',*([sys.argv[sys.argv.index('--sample')+1]] if '--sample' in sys.argv else [])],check=True)
