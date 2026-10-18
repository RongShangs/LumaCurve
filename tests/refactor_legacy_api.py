"""Exercise the production legacy adapter, including multiple hooks sharing one frame."""
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
OUT=ROOT/'build/legacy-api-tests';OUT.mkdir(exist_ok=True)
sources={
'de/robv/android/xposed/IXposedHookLoadPackage.java':r'''package de.robv.android.xposed;
public interface IXposedHookLoadPackage {void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam p)throws Throwable;}''',
'de/robv/android/xposed/callbacks/XC_LoadPackage.java':r'''package de.robv.android.xposed.callbacks;
public class XC_LoadPackage {public static class LoadPackageParam {public String packageName,processName;public ClassLoader classLoader;}}''',
'de/robv/android/xposed/XC_MethodHook.java':r'''package de.robv.android.xposed;
import java.lang.reflect.*;import java.util.*;
public abstract class XC_MethodHook {
 protected void beforeHookedMethod(MethodHookParam p)throws Throwable{}protected void afterHookedMethod(MethodHookParam p)throws Throwable{}
 public void before(MethodHookParam p)throws Throwable{beforeHookedMethod(p);}public void after(MethodHookParam p)throws Throwable{afterHookedMethod(p);}
 public class Unhook {public void unhook(){if(XposedBridge.hooks.remove(XC_MethodHook.this))XposedBridge.removed++;}}
 public static class MethodHookParam {public Member method;public Object thisObject;public Object[] args;public boolean early;Object result;Throwable error;Map<String,Object> extras=new HashMap<>();
 public void setObjectExtra(String k,Object v){extras.put(k,v);}public Object getObjectExtra(String k){return extras.get(k);}
 public Object getResult(){return result;}public Throwable getThrowable(){return error;}
 public void setResult(Object v){result=v;error=null;early=true;}public void setThrowable(Throwable t){error=t;result=null;early=true;}
 }
}''',
'de/robv/android/xposed/XposedBridge.java':r'''package de.robv.android.xposed;
import java.lang.reflect.*;import java.util.*;
public final class XposedBridge {
 public static List<XC_MethodHook> hooks=new ArrayList<>();public static int originals,removed,logs;
 public static XC_MethodHook.Unhook hookMethod(Method m,XC_MethodHook h){hooks.add(h);return h.new Unhook();}
 public static void log(String s){logs++;}
 public static Object invokeOriginalMethod(Method m,Object receiver,Object[] args)throws Throwable{originals++;try{return m.invoke(receiver,args);}catch(InvocationTargetException e){throw e.getCause();}}
 public static Object invoke(Method method,int value)throws Throwable{
  XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.method=method;p.args=new Object[]{value};List<XC_MethodHook> chain=new ArrayList<>(hooks);int entered=0;
  for(XC_MethodHook h:chain){entered++;try{h.before(p);}catch(Throwable ignored){p.result=null;p.error=null;p.early=false;}if(p.early)break;}
  if(!p.early)try{p.result=invokeOriginalMethod(method,null,p.args);}catch(Throwable e){p.error=e;}
  for(int i=entered-1;i>=0;i--){Object result=p.result;Throwable error=p.error;try{chain.get(i).after(p);}catch(Throwable ignored){p.result=result;p.error=error;}}
  if(p.error!=null)throw p.error;return p.result;
 }
}''',
'top/rongshangs/lumacurve/refactor/Doubles.java':r'''package top.rongshangs.lumacurve.refactor;
final class AppBuild {static final String BUILD="test-legacy";}
final class HookEntry {static int calls;static class LoadParam {LoadParam(String a,String b,ClassLoader c){}}static void handleLoadPackage(LoadParam p){calls++;}}
final class ControlCenterHooks {static int calls;static void install(ClassLoader c){calls++;}}''',
'top/rongshangs/lumacurve/refactor/LegacyTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import top.rongshangs.lumacurve.refactor.hooking.*;
public final class LegacyTest {
 static int cases;static void check(boolean v){if(!v)throw new AssertionError("case "+cases);cases++;}
 public static int sample(int n){if(n<0)throw new IllegalStateException("original");return n*2;}
 static Object invoke(Method m,int n)throws Throwable{return de.robv.android.xposed.XposedBridge.invoke(m,n);}
 public static void main(String[] args)throws Throwable{
  check(XposedBridge.initialize(new LegacyEntry.LegacyBackend()));Method m=LegacyTest.class.getMethod("sample",int.class);
  XC_MethodHook.Unhook a=XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){check(p.method.equals(m));p.args[0]=4;p.setObjectExtra("mark","first");}protected void afterHookedMethod(MethodHookParam p){check(p.getObjectExtra("mark").equals("first"));p.setResult((Integer)p.getResult()+1);}});
  XC_MethodHook.Unhook b=XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){p.setObjectExtra("mark","second");}protected void afterHookedMethod(MethodHookParam p){check(p.getObjectExtra("mark").equals("second"));p.setResult((Integer)p.getResult()+10);}});
  check(XposedBridge.hookCount()==2);check(invoke(m,2).equals(19));a.unhook();a.unhook();b.unhook();check(XposedBridge.hookCount()==0&&de.robv.android.xposed.XposedBridge.removed==2);
  int original=de.robv.android.xposed.XposedBridge.originals;
  a=XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){p.setResult(33);}protected void afterHookedMethod(MethodHookParam p){p.setResult((Integer)p.getResult()+2);}});
  check(invoke(m,1).equals(35)&&de.robv.android.xposed.XposedBridge.originals==original);a.unhook();
  a=XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){check(p.hasThrowable()&&p.getThrowable().getMessage().equals("original"));p.setResult(77);}});
  check(invoke(m,-1).equals(77));a.unhook();
  a=XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){p.setThrowable(new IllegalArgumentException("stop"));}});
  original=de.robv.android.xposed.XposedBridge.originals;try{invoke(m,1);throw new AssertionError();}catch(IllegalArgumentException e){check(e.getMessage().equals("stop")&&de.robv.android.xposed.XposedBridge.originals==original);}a.unhook();
  check(XposedBridge.invokeOriginalMethod(m,null,new Object[]{5}).equals(10));
  a=XposedBridge.hookMethod(m,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){throw new IllegalStateException("callback");}});check(invoke(m,3).equals(6));XposedBridge.unhookAll();check(XposedBridge.hookCount()==0&&de.robv.android.xposed.XposedBridge.hooks.isEmpty());
  de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam p=new de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam();p.packageName="com.android.systemui";p.processName=p.packageName;p.classLoader=LegacyTest.class.getClassLoader();new LegacyEntry().handleLoadPackage(p);check(ControlCenterHooks.calls==1&&HookEntry.calls==0);p.packageName="android";p.processName="system_server";new LegacyEntry().handleLoadPackage(p);check(HookEntry.calls==1);check(de.robv.android.xposed.XposedBridge.logs==2);
  System.out.println("Legacy API adapter: "+cases+" cases PASS; production callback bridge with framework doubles");
 }
}'''
}
paths=[]
for name,body in sources.items():
    p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8');paths.append(p)
classes=OUT/'classes';classes.mkdir(exist_ok=True)
production=[SRC/'LegacyEntry.java',SRC/'hooking/XC_MethodHook.java',SRC/'hooking/XposedBridge.java']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(classes),*[str(p) for p in paths+production]],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.LegacyTest'],check=True)
