"""Exercise production memory persistence and adapter rollback with service doubles.
No Android/ART device claim. Includes malformed archives, lifecycle races and theme contrast.
"""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-memory-tests'
J=R/'build/refactor-diagnostics/json-20240303.jar'
sources={
'android/content/Context.java':'''package android.content;public class Context {public Object getContentResolver(){return this;}}''',
'android/provider/Settings.java':'''package android.provider;import java.util.*;public class Settings {public static class Global {public static Map<String,String> data=new HashMap<>();public static int writes;public static boolean fail;public static String getString(Object r,String k){return data.get(k);}public static boolean putString(Object r,String k,String v){if(fail)return false;writes++;if(v==null)data.remove(k);else data.put(k,v);return true;}}}''',
'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler {public boolean accept=true;public final List<Runnable> jobs=new ArrayList<>();public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long delay){if(!accept)return false;jobs.add(r);return true;}public void removeCallbacks(Runnable r){jobs.removeIf(x->x==r);}public void drain(){List<Runnable> copy=new ArrayList<>(jobs);jobs.clear();for(Runnable r:copy)r.run();}}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock {public static long now=10000;public static long uptimeMillis(){return now;}}''',
'android/os/PowerManager.java':'''package android.os;public class PowerManager {public boolean awake=true;public int reads;public boolean isInteractive(){reads++;return awake;}}''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;import android.os.*;import android.content.*;class HookRuntime {CurveBackend kernel;final MemoryLifecycle memoryLifecycle=new MemoryLifecycle();Handler handler=new Handler();Context context=new Context();PowerManager power=new PowerManager();String fingerprint="firmware-a",phase="active";boolean closed,changing,user=true,onThread=true,hdr;float memoryStrength=1;int logs;HookRuntime(CurveBackend k){kernel=k;}String baselineId(){return "device-baseline";}boolean appliesToUser(){return user;}boolean onDisplayThread(){return onThread;}Boolean hdrActive(){return hdr;}void requestRecalculation(){}void queuePublish(){}void log(String m){logs++;}}''',
'top/rongshangs/lumacurve/refactor/MemoryLifecycle.java':'''package top.rongshangs.lumacurve.refactor;import org.json.*;class MemoryLifecycle {float lux=30;JSONObject record;int restores;float ambientLux(){return lux;}JSONObject modelRecord(){return record;}boolean restoreModel(JSONObject j){if(j!=null)restores++;return j!=null;}void event(String key){}void put(JSONObject j){}}''',
'top/rongshangs/lumacurve/refactor/MemoryHostTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import android.provider.Settings;
public class MemoryHostTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 interface Task{void run()throws Exception;}static void bad(Task t){try{t.run();throw new AssertionError("accepted malformed memory");}catch(IllegalArgumentException|JSONException expected){cases++;}catch(Exception unexpected){throw new RuntimeException(unexpected);}}
 static JSONArray points(float... values)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<values.length;i+=2)out.put(new JSONObject().put("lux",values[i]).put("value",values[i+1]));return out;}
 static class Backend extends CurveBackend {
  JSONArray anchors=new JSONArray();CurvePlan active;int user,capacity=7,restores;boolean auto=true,supported=true,fail;
  Backend(){super(1,1000);factoryLux=new float[]{0,30,600,5000};factoryNit=new float[]{2,20,100,1000};active=new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});}
  public Object get(String n){if(n.equals("mUserSerial"))return user;if(n.equals("mUseAutoBrightness"))return auto;return null;}
  public CurvePlan plan(){return active;}public String name(){return "model";}public void configure(float[] f){active=f==null?null:new CurvePlan(factoryLux,factoryNit,min,max,f);}public void clearMemory(){anchors=new JSONArray();}public float currentAt(float lux){return active.at(lux);}
  public boolean persistentMemorySupported(){return supported;}public int manualPointCapacity(){return supported?capacity:0;}public JSONArray manualPoints(){return anchors;}
  public void restoreManualPoints(JSONArray p)throws Exception{if(fail)throw new IllegalStateException("modeled OEM refusal");restores++;anchors=new JSONArray(p.toString());}
 }
 public static class OEM {
  int mDisplayId,mAnchorCount=4,mGoodAnchorCount=4,mOrderCounter,mUserSerial;
  float mLuxSeg0=30,mLuxSeg1=600,mLuxMax=5000,mDefNit0=2,mDefNit30=20,mDefNitMidHigh=100,mUserLux=-1,mUserLogicalNit=-1;boolean mIsHaveGoodCurve,fail;
  final float[] mAnchorLux=new float[7],mAnchorNit=new float[7],mGoodAnchorLux=new float[7],mGoodAnchorNit=new float[7];final int[] mAnchorOrder=new int[7],mGoodAnchorOrder=new int[7];final boolean[] mAnchorIsUserDrag=new boolean[7],mGoodAnchorIsUserDrag=new boolean[7];
  void resetDefaultSpline(){float[] x={0,30,600,5000},y={mDefNit0,mDefNit30,mDefNitMidHigh,1000};Arrays.fill(mAnchorIsUserDrag,false);Arrays.fill(mGoodAnchorIsUserDrag,false);System.arraycopy(x,0,mAnchorLux,0,4);System.arraycopy(y,0,mAnchorNit,0,4);mAnchorCount=mGoodAnchorCount=4;mUserLux=-1;}
  float defaultAtLux(float x){return 20;}
  void updateLogicalCurve(float x,float y){mAnchorLux[mAnchorCount]=x;mAnchorNit[mAnchorCount]=y;mAnchorIsUserDrag[mAnchorCount]=true;mAnchorOrder[mAnchorCount]=++mOrderCounter;mAnchorCount++;mUserLux=x;mUserLogicalNit=y;if(fail)throw new IllegalStateException("modeled mid-replay failure");}
 }
 static double luminance(int c){double out=0;double[] weights={.2126,.7152,.0722};int[] shift={16,8,0};for(int i=0;i<3;i++){double v=((c>>shift[i])&255)/255d;out+=weights[i]*(v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));}return out;}
 static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 public static void main(String[] args)throws Exception {
  long now=System.currentTimeMillis();JSONArray p=points(30,80,600,200);JSONObject record=PersistentMemory.encode("scope",p,now-1000);String raw=record.toString();
  check(PersistentMemory.read(raw,"scope",now,30).length()==2);check(PersistentMemory.read(raw,"other",now,30).length()==0);check(PersistentMemory.read(raw,"scope",now+31*86400000L,30).length()==0);check(PersistentMemory.read(raw,"scope",now-2000,30).length()==0);check(PersistentMemory.read(null,"scope",now,30).length()==0);
  JSONArray read=PersistentMemory.read(raw,"scope",now,30);read.getJSONObject(0).put("value",999);check(record.getJSONArray("points").getJSONObject(0).getInt("value")==80);
  bad(()->PersistentMemory.read("{","scope",now,30));bad(()->PersistentMemory.read(raw,"scope",now,0));bad(()->PersistentMemory.encode("",p,now));bad(()->PersistentMemory.encode("scope",points(-1,10),now));bad(()->PersistentMemory.encode("scope",points(1,-1),now));bad(()->PersistentMemory.encode("scope",points(1,10,1,20),now));bad(()->PersistentMemory.encode("scope",new JSONArray("[{lux:1,value:'x'}]"),now));bad(()->PersistentMemory.read(new JSONObject(record.toString()).put("saved_at","123").toString(),"scope",now,30));
  for(Object schema:new Object[]{"1",1.5,2,JSONObject.NULL})bad(()->PersistentMemory.read(new JSONObject(record.toString()).put("schema",schema).toString(),"scope",now,30));
  JSONArray excess=new JSONArray();for(int i=0;i<8;i++)excess.put(new JSONObject().put("lux",i).put("value",i));bad(()->PersistentMemory.encode("scope",excess,now));
  check(MemoryOptions.parse(new JSONObject()).persist);check(MemoryOptions.parse(new JSONObject()).days==30);check(MemoryOptions.parse(new JSONObject()).maxPoints==0);check(new MemoryOptions(true,30,7).fit(1).maxPoints==1);
  for(Object invalid:new Object[]{"1",1.5,-1,8,JSONObject.NULL})bad(()->MemoryOptions.parse(new JSONObject().put("memory_max_points",invalid)));
  bad(()->MemoryOptions.parse(new JSONObject().put("memory_persist",1)));bad(()->MemoryOptions.parse(new JSONObject().put("memory_retention_days",10000000000L)));
  Backend b=new Backend();HookRuntime s=new HookRuntime(b);MemoryPersistence m=new MemoryPersistence(s);m.options=m.options.with("memory_restore_same_scene",false);Settings.Global.data.clear();Settings.Global.writes=0;
  b.anchors=points(30,80);m.manualApplied();b.anchors=points(30,90);m.manualApplied();check(Settings.Global.writes==0);check(s.handler.jobs.size()==1);s.handler.drain();check(Settings.Global.writes==1);check(m.pendingWrite==null);check(PersistentMemory.read(Settings.Global.data.get(PersistentMemory.KEY),m.scope(),now+10000,30).getJSONObject(0).getInt("value")==90);
  b.clearMemory();m.configure(new MemoryOptions(true,30).with("memory_restore_same_scene",false),true);s.handler.drain();check(b.restores==1);check(b.anchors.getJSONObject(0).getInt("value")==90);check(m.restored==1);
  b.clearMemory();m.afterReset();s.handler.drain();check(b.restores==1); // Environmental reset remains effective.
  b.auto=false;m.afterReset();s.handler.drain();check(m.pendingRestore&&b.restores==1);b.auto=true;m.maybeRestore();check(b.restores==2);
  b.clearMemory();s.power.awake=false;m.afterReset();s.handler.drain();check(m.pendingRestore);s.power.awake=true;m.screenOn();m.ambientReady(30);android.os.SystemClock.now+=2000;s.user=false;m.maybeRestore();check(m.pendingRestore);s.user=true;s.hdr=true;m.maybeRestore();check(m.pendingRestore);s.hdr=false;s.changing=true;m.maybeRestore();check(m.pendingRestore);s.changing=false;s.memoryStrength=0;m.maybeRestore();check(m.pendingRestore);s.memoryStrength=1;m.maybeRestore();check(b.restores==3);
  // Fresh live manual choice wins over archived data; replay never counts as a new gesture.
  b.anchors=points(30,110);m.scheduleRestore();s.handler.drain();check(b.anchors.getJSONObject(0).getInt("value")==110);check(b.restores==3);m.replaying=true;m.manualApplied();check(m.pendingWrite==null);m.replaying=false;
  b.anchors=points(10,10,30,30,100,100);m.configure(new MemoryOptions(true,30,2).with("memory_restore_same_scene",false),false);m.manualApplied();s.handler.drain();b.clearMemory();m.scheduleRestore();s.handler.drain();check(b.anchors.length()==2);check(b.anchors.getJSONObject(0).getInt("lux")==30);b.capacity=1;b.clearMemory();m.scheduleRestore();s.handler.drain();check(b.anchors.length()==1);check(b.anchors.getJSONObject(0).getInt("lux")==100);
  // Disable cancels pending writes, retains disk data and cannot replay it.
  int writes=Settings.Global.writes;b.anchors=points(30,120);m.manualApplied();m.configure(new MemoryOptions(false,30),false);s.handler.drain();check(Settings.Global.writes==writes);b.clearMemory();m.scheduleRestore();m.maybeRestore();check(b.anchors.length()==0);
  m.configure(new MemoryOptions(true,30).with("memory_restore_same_scene",false),true);s.handler.drain();check(b.anchors.length()==1);m.clear();b.clearMemory();m.scheduleRestore();s.handler.drain();check(b.anchors.length()==0);check(!Settings.Global.data.containsKey(PersistentMemory.KEY));
  // Curve, firmware and user changes invalidate archives. Ordinary tuning does not.
  b.anchors=points(30,50);m.manualApplied();s.handler.drain();b.clearMemory();String originalScope=m.scope();b.configure(new float[]{1,1.1f,1,1});check(!originalScope.equals(m.scope()));m.scheduleRestore();s.handler.drain();check(b.anchors.length()==0);b.configure(new float[]{1,1,1,1});s.fingerprint="firmware-b";m.scheduleRestore();s.handler.drain();check(b.anchors.length()==0);s.fingerprint="firmware-a";b.user=1;m.scheduleRestore();s.handler.drain();check(b.anchors.length()==0);b.user=0;
  b.fail=true;m.scheduleRestore();s.handler.drain();check(!m.pendingRestore&&!m.replaying&&b.anchors.length()==0);b.fail=false;
  b.anchors=points(30,60);Settings.Global.fail=true;m.manualApplied();s.handler.drain();check(m.pendingWrite!=null);Settings.Global.fail=false;m.close();check(m.pendingWrite==null);check(PersistentMemory.read(Settings.Global.data.get(PersistentMemory.KEY),m.scope(),now+10000,30).getJSONObject(0).getInt("value")==60);
  b.anchors=points(30,70);m.manualApplied();int oldWrites=Settings.Global.writes;s.power.awake=false;m.screenOff();check(Settings.Global.writes==oldWrites+1);b.clearMemory();s.handler.drain();check(m.pendingRestore);s.power.awake=true;m.screenOn();m.ambientReady(30);android.os.SystemClock.now+=2000;m.maybeRestore();check(b.anchors.getJSONObject(0).getInt("value")==70);
  b.clearMemory();s.closed=true;m.scheduleRestore();s.handler.drain();check(b.anchors.length()==0);s.closed=false;b.supported=false;m.maybeRestore();check(b.anchors.length()==0);
  OEM o=new OEM();o.resetDefaultSpline();RefactorAdapter ref=new RefactorAdapter(o,1,1000);ref.configure(new float[]{1,1,1,1});check(ref.manualPointCapacity()==7);ref.restoreManualPoints(points(10,10,100,50));check(ref.manualPoints().length()==2);check(ref.manualPoints().getJSONObject(1).getInt("lux")==100);
  int count=o.mAnchorCount,order=o.mOrderCounter;float[] x=o.mAnchorLux.clone(),y=o.mAnchorNit.clone();o.fail=true;try{ref.restoreManualPoints(points(200,70));throw new AssertionError();}catch(IllegalStateException expected){cases++;}check(o.mAnchorCount==count&&o.mOrderCounter==order&&Arrays.equals(x,o.mAnchorLux)&&Arrays.equals(y,o.mAnchorNit));
  bad(()->ref.restoreManualPoints(points(6000,30)));bad(()->ref.restoreManualPoints(points(200,1001)));
  // New options are portable, strictly typed, and independent of native grouping.
  MemoryOptions defaults=MemoryOptions.parse(new JSONObject());check(defaults.unlock&&defaults.sameScene&&defaults.replaceOnUnlock&&!defaults.resetOverride&&!defaults.timeoutOverride);JSONObject packed=new JSONObject();defaults.with("memory_restore_settle",2500).with("memory_timeout_minutes",90).put(packed);check(MemoryOptions.parse(packed).settleMs==2500&&MemoryOptions.parse(packed).timeoutMinutes==90);check(defaults.with("memory_restore_unlock",false).fit(1).unlock==false);
  for(String key:new String[]{"memory_restore_unlock","memory_restore_same_scene","memory_restore_replace","memory_reset_override","memory_timeout_override"})bad(()->MemoryOptions.parse(new JSONObject().put(key,1)));
  bad(()->defaults.with("memory_restore_settle",100));bad(()->defaults.with("memory_restore_ratio",0));bad(()->defaults.with("memory_reset_off_minutes",31));bad(()->defaults.with("memory_timeout_minutes",1.5));bad(()->defaults.with("memory_reset_force_minutes",1));bad(()->defaults.with("memory_reset_override",true).verify(new JSONObject()));
  check(MemoryScene.near(30,40,.5f,5));check(!MemoryScene.near(30,50,.5f,5));check(MemoryScene.near(0,5,.5f,5));check(!MemoryScene.near(Float.NaN,5,.5f,5));check(!MemoryScene.near(10,-1,.5f,5));check(!MemoryScene.reset(30,51,359999,defaults));check(MemoryScene.reset(30,51,360000,defaults));check(!MemoryScene.reset(30,50,360000,defaults));check(MemoryScene.reset(30,30,1800000,defaults));
  // Wake restore waits for fresh ambient input, then settles. It can replace an old Good curve.
  Backend wb=new Backend();HookRuntime ws=new HookRuntime(wb);MemoryPersistence wm=new MemoryPersistence(ws);Settings.Global.data.clear();ws.memoryLifecycle.lux=30;ws.memoryLifecycle.record=new JSONObject().put("lux",30).put("brightness",.2);
  wb.anchors=points(30,80);wm.manualApplied();ws.handler.drain();check(PersistentMemory.model(Settings.Global.data.get(PersistentMemory.KEY)).getDouble("brightness")==.2);
  wm.screenOff();wb.anchors=points(30,100);ws.power.awake=false;wm.maybeRestore();check(wb.restores==0);ws.power.awake=true;wm.screenOn();wm.maybeRestore();check(wb.restores==0);wm.ambientReady(30);wm.maybeRestore();check(wb.restores==0);android.os.SystemClock.now+=1600;wm.maybeRestore();check(wb.anchors.getJSONObject(0).getInt("value")==80&&wb.restores==1&&ws.memoryLifecycle.restores==1);
  // A significant scene change ends the attempt but retains the saved archive.
  wm.screenOff();wb.clearMemory();wm.screenOn();ws.memoryLifecycle.lux=100;wm.ambientReady(100);android.os.SystemClock.now+=1600;wm.maybeRestore();check(!wm.pendingRestore&&wb.restores==1&&wb.anchors.length()==0);check(Settings.Global.data.containsKey(PersistentMemory.KEY));
  // Re-locking in the new room cannot bypass archive light matching.
  wm.screenOff();wm.screenOn();wm.ambientReady(100);android.os.SystemClock.now+=1600;wm.maybeRestore();check(!wm.pendingRestore&&wb.restores==1&&wb.anchors.length()==0);check(Settings.Global.data.containsKey(PersistentMemory.KEY));
  // A new user adjustment during warmup cancels any old archive replay.
  ws.memoryLifecycle.lux=30;wm.screenOff();wb.clearMemory();wm.screenOn();wm.ambientReady(30);wb.anchors=points(30,120);wm.manualApplied();android.os.SystemClock.now+=2000;wm.maybeRestore();check(wb.anchors.getJSONObject(0).getInt("value")==120&&wb.restores==1);ws.handler.drain();
  // Missing-only mode keeps a live OEM point; disabling unlock restoration leaves wake untouched.
  wm.configure(wm.options.with("memory_restore_replace",false),false);wm.screenOff();wb.anchors=points(30,140);wm.screenOn();wm.ambientReady(30);android.os.SystemClock.now+=1600;wm.maybeRestore();check(wb.anchors.getJSONObject(0).getInt("value")==140&&wb.restores==1);
  wm.configure(wm.options.with("memory_restore_unlock",false),false);wm.screenOff();wb.clearMemory();wm.screenOn();wm.ambientReady(30);android.os.SystemClock.now+=1600;wm.maybeRestore();check(!wm.pendingRestore&&wb.anchors.length()==0);
  wm.configure(defaults,false);wm.screenOff();wm.screenOn();wm.ambientReady(30);android.os.SystemClock.now+=21000;wm.maybeRestore();check(!wm.pendingRestore&&wb.anchors.length()==0);
  // Replacement failure restores all Refactor arrays and flags, including final arrays.
  // Wake expiry must work even if HDR/manual mode prevents readiness or no sensor event arrives.
  wm.configure(defaults,false);wm.screenOff();wm.screenOn();ws.hdr=true;android.os.SystemClock.now+=20000;wm.maybeRestore();check(!wm.pendingRestore);ws.hdr=false;
  wm.screenOff();wm.screenOn();android.os.SystemClock.now+=20000;wm.expiry.run();check(!wm.pendingRestore&&wm.status.equals("亮屏确认超时，本次不恢复"));
  wm.screenOff();wm.screenOn();wm.manualApplied();check(!ws.handler.jobs.contains(wm.expiry));
  o.fail=false;ref.replaceManualPoints(points(10,10));check(o.mAnchorCount==5&&ref.manualPoints().length()==1);float[] beforeY=o.mAnchorNit.clone();int beforeCount=o.mAnchorCount;o.fail=true;try{ref.replaceManualPoints(points(100,80));throw new AssertionError();}catch(IllegalStateException expected){cases++;}check(Arrays.equals(beforeY,o.mAnchorNit)&&o.mAnchorCount==beforeCount);
  // A delayed save must not attach a model from a replacement baseline/user.
  Backend scoped=new Backend();HookRuntime scopedState=new HookRuntime(scoped);MemoryPersistence scopedMemory=new MemoryPersistence(scopedState);
  scoped.anchors=points(30,80);scopedMemory.manualApplied();String oldScope=scopedMemory.scope();scopedState.memoryLifecycle.record=new JSONObject().put("lux",30).put("brightness",.9).put("valid",true);
  scoped.configure(new float[]{1,1.5f,1,1});scopedMemory.flush();JSONObject scopedRecord=new JSONObject(Settings.Global.data.get(PersistentMemory.KEY));check(scopedRecord.getString("scope").equals(oldScope));check(!scopedRecord.has("model"));check(scopedRecord.getJSONArray("points").getJSONObject(0).getInt("value")==80);
  scopedMemory.manualApplied();scoped.user=10;scopedMemory.flush();check(!new JSONObject(Settings.Global.data.get(PersistentMemory.KEY)).has("model"));
  scoped.user=0;scopedMemory.manualApplied();scoped.configure(null);scopedMemory.flush();check(scopedMemory.pendingWrite==null);check(!new JSONObject(Settings.Global.data.get(PersistentMemory.KEY)).has("model"));
  // A mapper read can request restoration, but must finish before model writes or service reads.
  Backend deferred=new Backend();HookRuntime deferredState=new HookRuntime(deferred);MemoryPersistence deferredMemory=new MemoryPersistence(deferredState);
  deferred.anchors=points(30,75);deferredMemory.manualApplied();deferredMemory.flush();deferred.clearMemory();deferredState.handler.jobs.clear();deferredState.power.reads=0;
  deferredMemory.scheduleRestore();for(int i=0;i<10000;i++)deferredMemory.requestRestore();
  check(deferred.restores==0&&deferred.anchors.length()==0);check(deferredState.power.reads==0);check(deferredState.handler.jobs.size()==1&&deferredMemory.restoreQueued);
  deferredState.handler.drain();check(deferred.restores==1&&deferred.anchors.getJSONObject(0).getInt("value")==75);check(!deferredMemory.restoreQueued&&!deferredMemory.pendingRestore);
  // A newer user gesture cancels an already queued archive restoration.
  deferred.clearMemory();deferredMemory.scheduleRestore();deferred.anchors=points(30,95);deferredMemory.manualApplied();deferredState.handler.drain();check(deferred.restores==1&&deferred.anchors.getJSONObject(0).getInt("value")==95);
  // Inner clearUserDataPoints hooks cannot query power or flush before the outer reset returns.
  deferredState.handler.jobs.clear();deferredState.power.reads=0;for(int i=0;i<10000;i++)deferredMemory.requestAfterReset();check(deferredState.power.reads==0&&deferredState.handler.jobs.size()==1&&deferredMemory.resetQueued);
  deferredState.handler.drain();check(deferredState.power.reads>0&&!deferredMemory.resetQueued);
  deferredMemory.replaying=true;deferredMemory.requestAfterReset();check(deferredState.handler.jobs.isEmpty());deferredMemory.replaying=false;
  deferredMemory.requestAfterReset();deferredMemory.manualApplied();check(!deferredState.handler.jobs.contains(deferredMemory.resetter)&&!deferredMemory.resetQueued);
  deferredState.handler.jobs.clear();deferred.clearMemory();deferredState.handler.accept=false;deferredMemory.scheduleRestore();check(deferredMemory.pendingRestore&&!deferredMemory.restoreQueued);deferredState.handler.accept=true;deferredMemory.requestRestore();check(deferredMemory.restoreQueued);deferredMemory.close();check(!deferredState.handler.jobs.contains(deferredMemory.restorer)&&!deferredMemory.restoreQueued);
  deferredMemory.requestAfterReset();deferredMemory.close();check(!deferredState.handler.jobs.contains(deferredMemory.resetter));
  // Canvas and native widgets share readable light/dark semantic surfaces.
  for(boolean dark:new boolean[]{false,true})for(int surface:new int[]{0xfffafafa,0xffffffff,0xffeef2fa,0xffedf3ff})for(int text:new int[]{0xff24303c,0xff616d79,0xff3265df})check(contrast(ThemePalette.color(text,dark),ThemePalette.color(surface,dark))>=4.5);
  check(contrast(0xff11151c,ThemePalette.color(0xff3265df,true))>=4.5);check(ThemePalette.color(0xfffafafa,true)!=0xfffafafa);check(ThemePalette.color(0xff3265df,false)==0xff3265df);
  System.out.println("Persistent memory/theme: "+cases+" cases PASS; service and OEM models, device not verified");
 }
}'''
}
files=[]
for name,text in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
names=['MemoryScene','MemoryOptions','PersistentMemory','MemoryPersistence','ThemePalette','CurveBackend','CurvePlan','CurveIdentity','RefactorAdapter','TraditionalAdapter','TraditionalCurve']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],*map(str,files)],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.MemoryHostTest'],check=True)
