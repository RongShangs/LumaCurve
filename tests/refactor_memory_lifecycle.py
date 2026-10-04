"""Exercise real native-memory observers/tuning with reflection and Hook doubles."""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-memory-lifecycle';O.mkdir(parents=True,exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
base=(R/'tests/refactor_persistent_memory.py').read_text(encoding='utf-8').split('files=[]')[0]
namespace={'__file__':str(R/'tests/refactor_persistent_memory.py')};exec(base,namespace)
sources=namespace['sources'].copy()
sources.pop('top/rongshangs/lumacurve/refactor/MemoryLifecycle.java')
sources.pop('top/rongshangs/lumacurve/refactor/MemoryHostTest.java')
sources['de/robv/android/xposed/XC_MethodHook.java']='''package de.robv.android.xposed;import java.util.*;public class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
 public static class MethodHookParam {public Object thisObject;public Object[] args;Object result;Throwable error;Map<String,Object> extras=new HashMap<>();public Object getResult(){return result;}public void setResult(Object v){result=v;error=null;}public boolean hasThrowable(){return error!=null;}public void setObjectExtra(String k,Object v){extras.put(k,v);}public Object getObjectExtra(String k){return extras.get(k);}}
}'''
sources['de/robv/android/xposed/XposedBridge.java']='''package de.robv.android.xposed;import java.lang.reflect.*;import java.util.*;public class XposedBridge {
 public static Map<Method,List<XC_MethodHook>> hooks=new HashMap<>();public static void hookMethod(Method m,XC_MethodHook h){hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);}
 public static Object call(Object target,String name,Class<?>[] types,Object... args)throws Throwable{Method m=null;for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try{m=c.getDeclaredMethod(name,types);break;}catch(NoSuchMethodException ignored){}m.setAccessible(true);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=target;p.args=args;List<XC_MethodHook> list=hooks.getOrDefault(m,Collections.emptyList());for(XC_MethodHook h:list)h.beforeHookedMethod(p);try{p.result=m.invoke(target,p.args);}catch(InvocationTargetException e){p.error=e.getCause();}for(int i=list.size()-1;i>=0;i--)list.get(i).afterHookedMethod(p);if(p.error!=null)throw p.error;return p.result;}
}'''
sources['top/rongshangs/lumacurve/refactor/HookRuntime.java']='''package top.rongshangs.lumacurve.refactor;import android.os.*;import android.content.*;class HookRuntime {
 CurveBackend kernel;Handler handler=new Handler();Context context=new Context();PowerManager power=new PowerManager();Object owner;
 String fingerprint="firmware",phase="active";boolean closed,changing,user=true,onThread=true,hdr;float memoryStrength=1;int logs;
 final MemoryLifecycle memoryLifecycle;final MemoryPersistence persistentMemory;
 HookRuntime(CurveBackend k,Object owner){kernel=k;this.owner=owner;memoryLifecycle=new MemoryLifecycle(this);persistentMemory=new MemoryPersistence(this);}
 String baselineId(){return "device";}boolean appliesToUser(){return user;}boolean onDisplayThread(){return onThread;}Boolean hdrActive(){return hdr;}void log(String m){logs++;}void queuePublish(){}void requestRecalculation(){}
}'''
sources['top/rongshangs/lumacurve/refactor/LifecycleHostTest.java']=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import de.robv.android.xposed.*;import android.os.SystemClock;
public class LifecycleHostTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static class Model {float mAnchor=30,mBrightness=.2f;boolean mIsValid=true;void setUserBrightness(float x,float y){mAnchor=x;mBrightness=y;mIsValid=true;}void invalidate(){mIsValid=false;}}
 static class Mapper {long getShortTermModelTimeout(){return 1800000;}}
 static class ABC {Model mShortTermModel=new Model();Mapper mCurrentBrightnessMapper=new Mapper();int mDisplayPolicy=3;float mAmbientLux=30;boolean mAmbientLuxValid=true,mLightSensorEnabled=true,idle;
  static boolean isInteractivePolicy(int policy){return policy==2||policy==3;}boolean isInIdleMode(){return idle;}boolean setDisplayPolicy(int policy){mDisplayPolicy=policy;return true;}void setAmbientLux(float lux){mAmbientLux=lux;mAmbientLuxValid=true;}void resetShortTermModel(){mShortTermModel.mAnchor=-1;mShortTermModel.mIsValid=false;}
 }
 static class Impl {ABC mAutomaticBrightnessController=new ABC();long mLastCloseTime=1000;float mLastCloseScreenLux=30;boolean needResetShortTermModelPolicy(){return false;}boolean isNeedResetShortTermModel(float lux,long now){return false;}}
 static class Owner {Impl mAutomaticBrightnessControllerImpl=new Impl();Mapper mBrightnessMapper=mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController.mCurrentBrightnessMapper;}
 static class Backend extends CurveBackend {String kind="model";boolean auto=true;JSONArray points=new JSONArray();CurvePlan p;Backend(){super(1,1000);factoryLux=new float[]{0,30,600,5000};factoryNit=new float[]{2,20,100,1000};p=new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});}
  public Object get(String k){return k.equals("mUseAutoBrightness")?auto:k.equals("mUserSerial")?0:k.equals("mIsHaveGoodCurve")?false:null;}public String name(){return kind;}public CurvePlan plan(){return p;}public void configure(float[] f){}public void clearMemory(){points=new JSONArray();}public float currentAt(float x){return p.at(x);}public boolean persistentMemorySupported(){return true;}public int manualPointCapacity(){return 7;}public JSONArray manualPoints(){return points;}public void restoreManualPoints(JSONArray p){points=p;}
 }
 static Object call(Object target,String name,Class<?>[] types,Object... args)throws Throwable{return XposedBridge.call(target,name,types,args);}
 public static void main(String[] args)throws Throwable{
  Owner owner=new Owner();Backend k=new Backend();HookRuntime s=new HookRuntime(k,owner);MemoryLifecycle l=s.memoryLifecycle;l.attach();Impl impl=owner.mAutomaticBrightnessControllerImpl;ABC abc=impl.mAutomaticBrightnessController;Mapper mapper=owner.mBrightnessMapper;
  check(l.resetSupported&&l.timeoutSupported&&l.modelSupported);check(l.ambientLux()==30);abc.mAmbientLuxValid=false;check(Float.isNaN(l.ambientLux()));abc.mAmbientLuxValid=true;abc.mLightSensorEnabled=false;check(Float.isNaN(l.ambientLux()));abc.mLightSensorEnabled=true;
  check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);s.persistentMemory.options=s.persistentMemory.options.with("memory_timeout_override",true).with("memory_timeout_minutes",60);check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==3600000);
  s.hdr=true;check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);s.hdr=false;abc.idle=true;check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);abc.idle=false;s.user=false;check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);s.user=true;s.phase="attached";check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);s.phase="active";
  abc.mCurrentBrightnessMapper=new Mapper();check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);abc.mCurrentBrightnessMapper=mapper;
  check(!(Boolean)call(impl,"isNeedResetShortTermModel",new Class<?>[]{float.class,long.class},100f,1801000L));s.persistentMemory.options=s.persistentMemory.options.with("memory_reset_override",true);
  check((Boolean)call(impl,"isNeedResetShortTermModel",new Class<?>[]{float.class,long.class},51f,361000L));check(!(Boolean)call(impl,"isNeedResetShortTermModel",new Class<?>[]{float.class,long.class},50f,361000L));check(!(Boolean)call(impl,"isNeedResetShortTermModel",new Class<?>[]{float.class,long.class},51f,360999L));check((Boolean)call(impl,"isNeedResetShortTermModel",new Class<?>[]{float.class,long.class},30f,1801000L));
  for(int mode=0;mode<5;mode++){s.hdr=mode==0;s.changing=mode==1;s.user=mode!=2;s.persistentMemory.replaying=mode==3;k.auto=mode!=4;check(!(Boolean)call(impl,"isNeedResetShortTermModel",new Class<?>[]{float.class,long.class},100f,1801000L));}s.hdr=s.changing=s.persistentMemory.replaying=false;s.user=k.auto=true;
  call(abc,"setDisplayPolicy",new Class<?>[]{int.class},0);check(s.persistentMemory.asleep);check(s.persistentMemory.closeLux==30);s.power.awake=false;call(abc,"setDisplayPolicy",new Class<?>[]{int.class},3);s.power.awake=true;check(!s.persistentMemory.asleep&&s.persistentMemory.pendingRestore&&s.persistentMemory.wakeReadyAt==0);
  int events=l.events.size();call(abc,"setDisplayPolicy",new Class<?>[]{int.class},3);check(l.events.size()==events);
  call(abc,"setAmbientLux",new Class<?>[]{float.class},30f);check(s.persistentMemory.wakeReadyAt==SystemClock.now+1500);call(abc.mShortTermModel,"invalidate",new Class<?>[]{});check(!abc.mShortTermModel.mIsValid&&l.lastEvent.equals("invalidate"));call(abc,"resetShortTermModel",new Class<?>[]{});check(l.lastEvent.equals("resetShortTermModel"));
  for(int i=0;i<25;i++)l.event("sample");check(l.events.size()==16);JSONObject out=new JSONObject();l.put(out);check(out.getBoolean("memory_reset_supported")&&out.getJSONArray("memory_lifecycle").length()==16);
  abc.mShortTermModel.setUserBrightness(40,.3f);JSONObject record=l.modelRecord();check(record.getDouble("lux")==40);k.kind="refactor";abc.mShortTermModel.mAnchor=-1;check(l.restoreModel(record));check(abc.mShortTermModel.mIsValid&&abc.mShortTermModel.mAnchor==40);check(!l.restoreModel(new JSONObject().put("lux",-1).put("brightness",.2)));check(!l.restoreModel(new JSONObject().put("lux",40).put("brightness",2)));
  k.kind="model";s.onThread=false;check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);s.onThread=true;l.close();check(MemoryLifecycle.active(mapper)==null);check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);
  HookRuntime replacement=new HookRuntime(new Backend(),owner);replacement.memoryLifecycle.attach();replacement.persistentMemory.options=replacement.persistentMemory.options.with("memory_timeout_override",true).with("memory_timeout_minutes",10);check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==600000);replacement.closed=true;check((Long)call(mapper,"getShortTermModelTimeout",new Class<?>[]{})==1800000);
  System.out.println("Native memory lifecycle: "+cases+" cases PASS; reflection/Hook doubles, no Android execution");
 }
}'''
files=[]
for name,source in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
names=['MemoryScene','MemoryOptions','PersistentMemory','MemoryPersistence','MemoryLifecycle','CurveBackend','CurvePlan','CurveIdentity','RefactorAdapter','TraditionalAdapter','TraditionalCurve']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],*map(str,files)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.LifecycleHostTest'],check=True)
