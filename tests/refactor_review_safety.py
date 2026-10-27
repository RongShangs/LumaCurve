"""Review regressions: actual temporary-field scopes and production callback bodies."""
from pathlib import Path
import ast,os,re,subprocess

R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/review-safety-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
P='top/rongshangs/lumacurve/refactor/'

def method(name,signature):
    text=(S/name).read_text('utf-8');start=text.index(signature);opening=text.index('{',start);depth=1;end=opening+1
    while depth:depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]

tree=ast.parse((R/'tests/refactor_advanced_hooks.py').read_text('utf-8'))
common=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
fixtures={k:v for k,v in common.items() if k.endswith('/XC_MethodHook.java') or k.endswith('/XposedBridge.java')}
fixtures.update({
'android/os/Looper.java':'package android.os;public class Looper{public static Looper same=new Looper();public static Looper myLooper(){return same;}}',
'android/os/Handler.java':'package android.os;public class Handler{public Looper getLooper(){return Looper.same;}}',
'com/android/server/display/brightness/BrightnessReason.java':'package com.android.server.display.brightness;public class BrightnessReason{}',
'com/android/server/display/TouchCoverProtectionHelper.java':r'''package com.android.server.display;public class TouchCoverProtectionHelper{public int mTouchEventDebounce=500,seen;public int replacement=-1;public boolean fail;public boolean isTouchCoverProtectionActive(){seen=mTouchEventDebounce;if(replacement>=0)mTouchEventDebounce=replacement;if(fail)throw new IllegalStateException("vendor error");return true;}}''',
P+'HookEntry.java':r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import java.util.*;import android.os.*;import top.rongshangs.lumacurve.refactor.hooking.*;
class HookEntry{static Set<Class<?>> thermalSupported=new HashSet<>();static Map<Object,HookRuntime> states=new LinkedHashMap<>();static HookRuntime current;
 static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}static HookRuntime ownerState(Object o){return current!=null&&current.owner==o?current:null;}
THERMAL
}
class HookRuntime{Object owner;boolean closed,probingScenes,allowed=true;Handler handler=new Handler();Advanced advanced=new Advanced();int touchAdjustments;boolean relaxThermal(){return allowed;}void thermalCall(float cap,float in){}boolean onDisplayThread(){return true;}boolean normalTuningAllowed(Object abc,Object impl){return allowed;}}
class Advanced{boolean[] enabled=new boolean[5];double[] values=new double[14];}
class TouchCandidate{static Set<Class<?>> touches=new HashSet<>();TOUCH}
class SceneCandidate{SCENE_SAVE SCENE_RESTORE}
'''.replace('THERMAL',method('HookEntry.java','static void installThermal(')).replace('TOUCH',method('AdvancedTuning.java','static void installTouch(')).replace('SCENE_SAVE',method('SceneTuning.java','static void save(')).replace('SCENE_RESTORE',method('SceneTuning.java','static void restore(')),
P+'SafetyTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import top.rongshangs.lumacurve.refactor.hooking.*;import com.android.server.display.*;import com.android.server.display.brightness.*;
public class SafetyTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static class Owner{public float mThermalMaxBrightness=.4f,seen;public float replacement=Float.NaN;public boolean fail;public Impl mAutomaticBrightnessControllerImpl=new Impl();public float adjustBrightness(float v,BrightnessReason r){seen=mThermalMaxBrightness;if(Float.isFinite(replacement))mThermalMaxBrightness=replacement;if(fail)throw new IllegalStateException("vendor error");return v;}public float adjustSdrBrightness(float v,boolean b,BrightnessReason r,boolean x,boolean y){return adjustBrightness(v,r);}}
 public static class Impl{public Object mAutomaticBrightnessController=new Object();public TouchCoverProtectionHelper mTouchAreaHelper=new TouchCoverProtectionHelper();}
 public static class Fields{public boolean gate=true;public float cap=.5f;public int ms=100;}
 public static void main(String[] args)throws Exception{
  Fields f=new Fields();Field cap=Fields.class.getField("cap"),ms=Fields.class.getField("ms");
  FieldOverride one=FieldOverride.apply(f,cap,Float.NaN);check(Float.isNaN(f.cap));one.close();check(f.cap==.5f);one.close();check(f.cap==.5f);
  one=FieldOverride.apply(f,cap,Float.NaN);f.cap=.2f;one.close();check(f.cap==.2f);
  FieldOverride outer=FieldOverride.apply(f,ms,200),inner=FieldOverride.apply(f,ms,300);inner.close();check(f.ms==200);outer.close();check(f.ms==100);
  outer=FieldOverride.apply(f,ms,200);inner=FieldOverride.apply(f,ms,300);f.ms=80;inner.close();outer.close();check(f.ms==80);
  one=FieldOverride.apply(f,ms,400);one.replace(600);check(f.ms==600);one.close();check(f.ms==80);try{one.replace(100);throw new AssertionError("closed scope reused");}catch(IllegalStateException expected){cases++;}
  Owner o=new Owner();HookRuntime s=new HookRuntime();s.owner=o;HookEntry.current=s;HookEntry.states.put(o,s);HookEntry.installThermal(Owner.class);check(HookEntry.thermalSupported.contains(Owner.class));
  Class<?>[] hdr={float.class,BrightnessReason.class},sdr={float.class,boolean.class,BrightnessReason.class,boolean.class,boolean.class};
  for(boolean ordinary:new boolean[]{true,false})for(boolean fail:new boolean[]{false,true})for(float next:new float[]{Float.NaN,.1f,.8f}){
   o.mThermalMaxBrightness=.4f;o.fail=fail;o.replacement=next;boolean threw=false;
   try{if(ordinary)XposedBridge.call(o,"adjustBrightness",hdr,.9f,new BrightnessReason());else XposedBridge.call(o,"adjustSdrBrightness",sdr,.9f,false,new BrightnessReason(),false,false);}catch(RuntimeException error){threw=true;}
   check(threw==fail);check(Float.isNaN(o.seen));check(o.mThermalMaxBrightness==(Float.isNaN(next)?.4f:next));
  }
  o.fail=false;s.allowed=false;o.mThermalMaxBrightness=.4f;o.replacement=Float.NaN;XposedBridge.call(o,"adjustBrightness",hdr,.9f,new BrightnessReason());check(o.seen==.4f&&o.mThermalMaxBrightness==.4f);s.allowed=true;
  s.advanced.enabled[4]=true;s.advanced.values[13]=50;TouchCandidate.installTouch(SafetyTest.class.getClassLoader());
  TouchCoverProtectionHelper touch=o.mAutomaticBrightnessControllerImpl.mTouchAreaHelper;
  for(boolean fail:new boolean[]{false,true})for(int next:new int[]{-1,250,700}){touch.fail=fail;touch.replacement=next;touch.mTouchEventDebounce=500;boolean threw=false;try{XposedBridge.call(touch,"isTouchCoverProtectionActive",new Class<?>[]{});}catch(RuntimeException error){threw=true;}check(threw==fail);check(touch.seen==50);check(touch.mTouchEventDebounce==(next<0?500:next));}
  for(boolean changed:new boolean[]{false,true}){f.gate=true;f.cap=.5f;XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=f;SceneCandidate.save(p,"gate",false);SceneCandidate.save(p,"cap",Float.MAX_VALUE);SceneCandidate.save(p,"cap",.8f);check(!f.gate&&f.cap==.8f);if(changed){f.gate=true;f.cap=.25f;}SceneCandidate.restore(p);check(f.gate&&f.cap==(changed?.25f:.5f));SceneCandidate.restore(p);check(f.cap==(changed?.25f:.5f));}
  XC_MethodHook.MethodHookParam a=new XC_MethodHook.MethodHookParam(),b=new XC_MethodHook.MethodHookParam();a.thisObject=b.thisObject=f;f.ms=100;SceneCandidate.save(a,"ms",200);SceneCandidate.save(b,"ms",300);SceneCandidate.restore(b);check(f.ms==200);SceneCandidate.restore(a);check(f.ms==100);
  System.out.println("Review field ownership: "+cases+" cases PASS; production scopes and callbacks, host model only");
 }
}''',
})
files=[]
for n,text in fixtures.items():
    p=O/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,'utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(S/'FieldOverride.java'),str(S/'hooking/HookTransaction.java'),*map(str,files)],check=True)
result=subprocess.check_output(['java','-Dfile.encoding=UTF-8','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.SafetyTest']).decode('utf-8')
print(result.strip())
total=int(re.search(r'(\d+) cases PASS',result)[1])

# Restore each old unconditional callback separately: all three must fail the new cases.
original=fixtures[P+'HookEntry.java']
mutations=[
 ('thermal','((FieldOverride)saved).close();','field(owner,"mThermalMaxBrightness").setFloat(p.thisObject,(Float)((FieldOverride)saved).original);'),
 ('touch','((FieldOverride)original).close();','HookEntry.field(cls,"mTouchEventDebounce").setInt(p.thisObject,(Integer)((FieldOverride)original).original);'),
 ('scene','scope.close();','scope.field.set(scope.target,scope.original);'),
]
for name,before,after in mutations:
    assert before in original,name
    source=O/P/'HookEntry.java';source.write_text(original.replace(before,after),'utf-8')
    dest=O/('negative-'+name);dest.mkdir(exist_ok=True)
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(dest),str(S/'FieldOverride.java'),str(S/'hooking/HookTransaction.java'),*map(str,files)],check=True)
    bad=subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(dest)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.SafetyTest'],capture_output=True)
    assert bad.returncode!=0 and b'AssertionError: case ' in bad.stderr,name
    total+=1
(O/P/'HookEntry.java').write_text(original,'utf-8')
print('Old thermal/touch/scene unconditional restoration reproduced: 3 cases PASS')

rollback=O/'rollback';rollback.mkdir(exist_ok=True)
test=rollback/'RollbackTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor.hooking;
import java.lang.reflect.*;import java.util.*;
public class RollbackTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static class Target{public void a(){}public void b(){}public void c(){}}
 public static void main(String[] args)throws Exception{
  List<String> removed=new ArrayList<>();Set<String> failed=new HashSet<>();boolean[] deny={true};
  XposedBridge.initialize(new XposedBridge.Backend(){public XC_MethodHook.Unhook hook(Method method,XC_MethodHook callback){String name=method.getName();return new XC_MethodHook.Unhook(()->{removed.add(name);if(deny[0]&&name.equals("b")){failed.add(name);throw new IllegalStateException("backend refused");}});}public Object original(Method m,Object o,Object[] a){return null;}public void log(String s){throw new IllegalStateException("logger unavailable");}});
  List<XC_MethodHook.Unhook> owned=new ArrayList<>();for(String name:new String[]{"a","b","c"})owned.add(XposedBridge.hookMethod(Target.class.getMethod(name),new XC_MethodHook(){}));check(XposedBridge.hookCount()==3);
  HookTransaction.rollbackAll(owned);check(removed.equals(Arrays.asList("c","b","a")));check(XposedBridge.hookCount()==1&&failed.contains("b"));
  deny[0]=false;XposedBridge.unhookAll();check(removed.equals(Arrays.asList("c","b","a","b")));check(XposedBridge.hookCount()==0);XposedBridge.unhookAll();check(removed.size()==4);
  removed.clear();deny[0]=true;for(String name:new String[]{"a","b","c"})XposedBridge.hookMethod(Target.class.getMethod(name),new XC_MethodHook(){});XposedBridge.unhookAll();check(removed.equals(Arrays.asList("c","b","a")));check(XposedBridge.hookCount()==1);deny[0]=false;XposedBridge.unhookAll();check(XposedBridge.hookCount()==0);
  System.out.println("Review rollback recovery: "+cases+" cases PASS");
 }}''','utf-8')
dest=rollback/'classes';dest.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(dest),*[str(S/'hooking'/n) for n in ['XC_MethodHook.java','XposedBridge.java','HookTransaction.java']],str(test)],check=True)
result=subprocess.check_output(['java','-cp',str(dest),'top.rongshangs.lumacurve.refactor.hooking.RollbackTest']).decode('utf-8')
print(result.strip());total+=int(re.search(r'(\d+) cases PASS',result)[1])

# The UI consumes the acknowledged state; an OTA string alone must not claim fallback.
ui=O/'firmware';ui.mkdir(exist_ok=True)
test=ui/'FirmwareNoticeTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;import org.json.*;
class Build{static String FINGERPRINT="maker/device:new";}
class AlertDialog{Notice owner;AlertDialog(Notice n){owner=n;}void show(){owner.dialogs++;}}
class Notice{boolean firmwarePrompted;JSONObject runtime;int dialogs;String description;static int MUTED=0;Object text(String t,int size,int c){description=t;return t;}AlertDialog dialog(String title,Object body){return new AlertDialog(this);}void dialogButton(AlertDialog d,String t,Object r,boolean primary){}PRODUCTION}
public class FirmwareNoticeTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}public static void main(String[] args){
 JSONObject config=new JSONObject().put("enabled",true).put("fingerprint","maker/device:old").put("revision","saved");String original=config.toString();
 for(String phase:new String[]{"active","error","attached"})for(String rev:new String[]{"saved","other",""}){Notice view=new Notice();view.runtime=new JSONObject().put("phase",phase).put("revision",rev);String before=view.runtime.toString();view.offerFirmwareNotice(config);check(view.dialogs==(phase.equals("active")&&rev.equals("saved")?0:1));check(view.firmwarePrompted);view.offerFirmwareNotice(config);check(view.dialogs<=1);check(config.toString().equals(original)&&view.runtime.toString().equals(before));if(view.description!=null)check(!view.description.contains("已恢复系统曲线"));}
 for(JSONObject ignored:new JSONObject[]{null,new JSONObject(original).put("enabled",false),new JSONObject(original).put("fingerprint",Build.FINGERPRINT)}){Notice view=new Notice();view.offerFirmwareNotice(ignored);check(view.dialogs==0&&!view.firmwarePrompted);}
 Notice view=new Notice();view.offerFirmwareNotice(config);check(view.dialogs==1);check(config.toString().equals(original));
 System.out.println("OTA UI state consistency: "+cases+" cases PASS");}}
'''.replace('PRODUCTION',method('MainActivity.java','void offerFirmwareNotice(')),'utf-8')
dest=ui/'classes';dest.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(dest),str(test)],check=True)
result=subprocess.check_output(['java','-cp',str(dest)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.FirmwareNoticeTest']).decode('utf-8')
print(result.strip());total+=int(re.search(r'(\d+) cases PASS',result)[1])
print(f'Review safety total: {total} cases PASS; host callbacks and fault injection, no Android execution')
