"""Actual WM adapter/filter hooks with native-decision and handler doubles; no device writes."""
from pathlib import Path
import subprocess,os,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/window-brightness-tests';J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'android/os/Handler.java':r'''package android.os;import java.util.*;public class Handler{public boolean reject;public List<Runnable> jobs=new ArrayList<>();public boolean post(Runnable r){if(reject)return false;jobs.add(r);return true;}public void drain(){List<Runnable> copy=new ArrayList<>(jobs);jobs.clear();for(Runnable r:copy)r.run();}}''',
'android/view/WindowManager.java':'package android.view;public class WindowManager{public static class LayoutParams{public int type=2;public float screenBrightness=.8f;}}',
'com/android/server/wm/WindowManagerGlobalLock.java':'package com.android.server.wm;public class WindowManagerGlobalLock{}',
'com/android/server/wm/MiuiFreeFormManagerServiceStub.java':'package com.android.server.wm;public class MiuiFreeFormManagerServiceStub{}',
'com/android/server/wm/MiuiFreeFormManagerService.java':r'''package com.android.server.wm;import java.util.*;public class MiuiFreeFormManagerService extends MiuiFreeFormManagerServiceStub{public final Map<Integer,MiuiFreeFormActivityStack> stacks=new HashMap<>();public boolean fail;public boolean isInMiniFreeFormMode(int id){if(fail)throw new IllegalStateException("unknown mini state");MiuiFreeFormActivityStack s=stacks.get(id);return s!=null&&s.mode==1;}public boolean inPinMode(int id){MiuiFreeFormActivityStack s=stacks.get(id);return s!=null&&(s.mode==2||s.mode==3);}}''',
'com/android/server/wm/ActivityTaskManagerService.java':'package com.android.server.wm;public class ActivityTaskManagerService{public MiuiFreeFormManagerServiceStub mMiuiFreeFormManagerService=new MiuiFreeFormManagerService();}',
'com/android/server/wm/WindowManagerService.java':r'''package com.android.server.wm;public class WindowManagerService{public final WindowManagerGlobalLock mGlobalLock=new WindowManagerGlobalLock();public final H mH=new H();public ActivityTaskManagerService mAtmService=new ActivityTaskManagerService();public int traversals;public static class H extends android.os.Handler{}public void requestTraversal(){if(!Thread.holdsLock(mGlobalLock))throw new AssertionError("unlocked traversal");traversals++;}}''',
'com/android/server/wm/Task.java':'package com.android.server.wm;public class Task{public int mUserId,root=17;public WindowManagerService mWmService;public Task(WindowManagerService w){mWmService=w;}public int getRootTaskId(){return root;}}',
'com/android/server/wm/MiuiFreeFormActivityStack.java':'package com.android.server.wm;public class MiuiFreeFormActivityStack{public Task mTask;public int mode;public MiuiFreeFormActivityStack(Task t){mTask=t;}public int getMiuiFreeFromWindowMode(){return mode;}public void setStackFreeFormMode(int n){mode=n;}}',
'com/android/server/wm/WindowState.java':'package com.android.server.wm;public class WindowState{public android.view.WindowManager.LayoutParams mAttrs=new android.view.WindowManager.LayoutParams();public int display;public Task task;public WindowState(Task t){task=t;}public Task getTask(){return task;}public int getDisplayId(){return display;}}',
'com/android/server/wm/WindowManagerServiceImpl.java':'package com.android.server.wm;public class WindowManagerServiceImpl{public WindowManagerService mWmService=new WindowManagerService();public boolean nativeAllowed=true,fail;public int decisions;public boolean shouldApplyOverrideBrightness(WindowState w){decisions++;if(fail)throw new IllegalStateException("native decision failure");return nativeAllowed;}}',
'fixture/top/rongshangs/lumacurve/refactor/HookEntry.java':r'''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;class HookEntry{static int user;static final User currentUser=new User();static class User{int serial(){return user;}}static Field field(Class<?> c,String n)throws Exception{for(Class<?> p=c;p!=null;p=p.getSuperclass())try{Field f=p.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(n);}}''',
'fixture/top/rongshangs/lumacurve/refactor/HookRuntime.java':'package top.rongshangs.lumacurve.refactor;class HookRuntime{final Object owner=new Owner();static class Owner{}boolean closed,user=true;Control brightnessControl=new Control();boolean appliesToUser(){return user;}static class Control{boolean main=true;boolean mainDisplay(){return main;}}}',
'fixture/top/rongshangs/lumacurve/refactor/WindowBrightnessTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import java.util.*;import com.android.server.wm.*;import top.rongshangs.lumacurve.refactor.hooking.*;
public class WindowBrightnessTest{
 static int cases,installs,failAt;static Map<Method,List<XC_MethodHook>> hooks=new HashMap<>();static void check(boolean b){if(!b)throw new AssertionError("window case "+cases);cases++;}
 static Object invoke(Method m,Object o,Object[] a)throws Throwable{try{return m.invoke(o,a);}catch(InvocationTargetException e){throw e.getCause();}}
 static Object call(Object o,String n,Class<?>[] types,Object...a)throws Throwable{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=o;p.method=m;p.args=a;List<XC_MethodHook> hs=hooks.getOrDefault(m,Collections.emptyList());int count=0;for(XC_MethodHook h:hs){h.before(p);count++;if(p.returnEarly())break;}if(!p.returnEarly())try{p.outcome(invoke(m,o,a),null);}catch(Throwable e){p.outcome(null,e);}for(int i=count-1;i>=0;i--)hs.get(i).after(p);if(p.hasThrowable())throw p.getThrowable();return p.getResult();}
 static boolean gate(WindowManagerServiceImpl wm,WindowState w)throws Throwable{synchronized(wm.mWmService.mGlobalLock){return (Boolean)call(wm,"shouldApplyOverrideBrightness",new Class<?>[]{WindowState.class},w);}}
 static void mode(MiuiFreeFormActivityStack stack,int n)throws Throwable{call(stack,"setStackFreeFormMode",new Class<?>[]{int.class},n);}
 public static void main(String[] args)throws Throwable{
  XposedBridge.initialize(new XposedBridge.Backend(){public XC_MethodHook.Unhook hook(Method m,XC_MethodHook cb){if(++installs==failAt)throw new IllegalStateException("installation failure");hooks.computeIfAbsent(m,k->new ArrayList<>()).add(cb);return new XC_MethodHook.Unhook(()->{hooks.get(m).remove(cb);if(hooks.get(m).isEmpty())hooks.remove(m);});}public Object original(Method m,Object o,Object[] a)throws Throwable{return invoke(m,o,a);}public void log(String m){}});
  ClassLoader loader=WindowBrightnessTest.class.getClassLoader();String scenario=args.length==0?"normal":args[0];
  if(scenario.equals("install_failure")){for(int i=1;i<=2;i++){failAt=installs+i;AppWindowBrightness.install(loader);check(!AppWindowBrightness.bindings.containsKey(loader)&&hooks.isEmpty());}System.out.println("Window brightness: "+cases+" cases PASS; transactional rollback");return;}
  AppWindowBrightness.install(loader);
  if(scenario.equals("partial")){check(hooks.isEmpty()&&!AppWindowBrightness.bindings.containsKey(loader)&&!AppWindowBrightness.errors.isEmpty());System.out.println("Window brightness: "+cases+" cases PASS; partial ABI rejected");return;}
  HookRuntime s=new HookRuntime();check(AppWindowBrightness.supported(s));check(hooks.size()==2);AppWindowBrightness.install(loader);check(hooks.size()==2);
  WindowManagerServiceImpl wm=new WindowManagerServiceImpl();Task task=new Task(wm.mWmService);WindowState w=new WindowState(task);MiuiFreeFormActivityStack stack=new MiuiFreeFormActivityStack(task);MiuiFreeFormManagerService manager=(MiuiFreeFormManagerService)wm.mWmService.mAtmService.mMiuiFreeFormManagerService;manager.stacks.put(task.root,stack);
  stack.mode=1;check(gate(wm,w));check(!AppWindowBrightness.status(s).getBoolean("enabled"));AppWindowBrightness.configure(s,true);check(wm.mWmService.traversals==0&&wm.mWmService.mH.jobs.size()==1);wm.mWmService.mH.drain();check(wm.mWmService.traversals==1);check(!gate(wm,w));check(w.mAttrs.screenBrightness==.8f&&w.mAttrs.type==2);
  for(int n:new int[]{0,1,2,3,0}){mode(stack,n);check(gate(wm,w)==(n==0));check(w.mAttrs.screenBrightness==.8f);}check(wm.mWmService.mH.jobs.size()==1);wm.mWmService.mH.drain();check(wm.mWmService.traversals==2&&gate(wm,w));mode(stack,0);check(wm.mWmService.mH.jobs.isEmpty());
  // Native manual/QR/HDR policy rejection always wins.
  wm.nativeAllowed=false;for(int n:new int[]{0,1,2,3}){stack.mode=n;check(!gate(wm,w));}wm.nativeAllowed=true;stack.mode=1;
  // Normal PiP/no vendor stack, another user/display and non-app windows retain native behavior.
  manager.stacks.clear();check(gate(wm,w));manager.stacks.put(task.root,stack);task.mUserId=10;check(gate(wm,w));task.mUserId=0;w.display=1;check(gate(wm,w));w.display=0;w.mAttrs.type=2000;check(gate(wm,w));w.mAttrs.type=2;HookEntry.user=10;check(gate(wm,w));HookEntry.user=0;check((Boolean)call(wm,"shouldApplyOverrideBrightness",new Class<?>[]{WindowState.class},w));
  manager.fail=true;check(gate(wm,w));check(!AppWindowBrightness.status(s).getString("detail").isEmpty());manager.fail=false;wm.fail=true;boolean threw=false;try{gate(wm,w);}catch(IllegalStateException expected){threw=true;}check(threw);wm.fail=false;
  // Filter changes eligibility; a native traversal can select the next eligible window.
  Task other=new Task(wm.mWmService);other.root=18;WindowState normal=new WindowState(other);normal.mAttrs.screenBrightness=.3f;float selected=Float.NaN;for(WindowState candidate:new WindowState[]{w,normal})if(gate(wm,candidate)){selected=candidate.mAttrs.screenBrightness;break;}check(selected==.3f);mode(stack,0);check(gate(wm,w));
  AppWindowBrightness.configure(s,false);wm.mWmService.mH.drain();stack.mode=1;check(gate(wm,w));check(!AppWindowBrightness.status(s).getBoolean("enabled"));int before=wm.mWmService.traversals;mode(stack,0);check(wm.mWmService.mH.jobs.isEmpty()&&wm.mWmService.traversals==before);
  AppWindowBrightness.configure(s,true);AppWindowBrightness.configure(s,false);AppWindowBrightness.configure(s,true);check(wm.mWmService.mH.jobs.size()==1);wm.mWmService.mH.drain();check(AppWindowBrightness.status(s).getBoolean("enabled"));s.closed=true;AppWindowBrightness.configure(s,true);wm.mWmService.mH.drain();check(!AppWindowBrightness.status(s).getBoolean("enabled"));
  check(AppWindowBrightness.status(s).getLong("blocked_requests")>=5);System.out.println("Window brightness: "+cases+" cases PASS; actual adapter/hooks, no Settings or window-attribute writes; Android not tested");
 }
}'''
}
count=0
for scenario in ['normal','install_failure','partial']:
 out=O/scenario;out.mkdir(parents=True,exist_ok=True);sources=[]
 for name,raw in files.items():
  text=raw
  if scenario=='partial' and name.endswith('MiuiFreeFormManagerService.java'):text=text.replace('boolean inPinMode(int id)','int inPinMode(int id)').replace('return s!=null&&(s.mode==2||s.mode==3);','return 0;')
  p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,'utf-8');sources.append(p)
 sources += [S/(n+'.java') for n in ['WindowBrightnessAccess','AppWindowBrightness']]+list((S/'hooking').glob('*.java'))
 c=out/'classes';c.mkdir(exist_ok=True);subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(c),*[str(p) for p in sources]],check=True)
 result=subprocess.run(['java','-cp',str(c)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.WindowBrightnessTest',scenario],capture_output=True,text=True,encoding='utf-8',errors='replace');print(result.stdout+result.stderr,end='');result.check_returncode();count+=int(re.search(r'Window brightness: (\d+) cases PASS',result.stdout).group(1))
print('Window brightness total: '+str(count)+' cases PASS; host only')
