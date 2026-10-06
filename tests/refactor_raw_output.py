"""Exercise production output hooks on a main/rear display callback model."""
from pathlib import Path
import subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/raw-output-tests'
files={
'de/robv/android/xposed/XC_MethodHook.java':'''package de.robv.android.xposed;public class XC_MethodHook{public static class MethodHookParam{public Object thisObject;public Object[] args;public boolean skipped;public void setResult(Object value){skipped=true;}}protected void beforeHookedMethod(MethodHookParam p){}public void before(MethodHookParam p){beforeHookedMethod(p);}public class Unhook{public void unhook(){}}}''',
'de/robv/android/xposed/XposedBridge.java':'''package de.robv.android.xposed;import java.lang.reflect.*;import java.util.*;public class XposedBridge{public static final Map<Method,XC_MethodHook> hooks=new HashMap<>();public static XC_MethodHook.Unhook hookMethod(Method m,XC_MethodHook hook){hooks.put(m,hook);return hook.new Unhook();}public static void log(String text){}}''',
'com/android/server/display/DisplayPowerState.java':'''package com.android.server.display;public class DisplayPowerState{public int writes,state=2;public float brightness=.25f;public boolean pausedBeforeOff;public float getScreenBrightness(){return brightness;}public void setScreenBrightness(float value){brightness=value;writes++;}public void setSdrScreenBrightness(float value){writes++;}public void setScreenState(int value,int reason){state=value;}}''',
'com/android/server/display/DisplayPowerController.java':'''package com.android.server.display;public class DisplayPowerController{public final DisplayPowerState power=new DisplayPowerState();public DisplayPowerState getDisplayPowerState(){return power;}}''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;public class HookRuntime{public final Owner owner=new Owner();public final Control brightnessControl=new Control();public boolean thread=true;public boolean onDisplayThread(){return thread;}public static class Owner{public com.android.server.display.DisplayPowerController mDisplayPowerController=new com.android.server.display.DisplayPowerController();}public static class Control{public boolean active;public int rawBlockedWrites,pauses;public boolean rawOutputBlocked(float value){return active&&Float.isFinite(value)&&value>0;}public void screenOff(){pauses++;active=false;}}}''',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;import java.util.*;public class HookEntry{static final Map<Object,HookRuntime> states=new IdentityHashMap<>();static Field field(Class<?> c,String key)throws Exception{Field f=c.getDeclaredField(key);f.setAccessible(true);return f;}static Object get(Object o,String key)throws Exception{return field(o.getClass(),key).get(o);}}''',
'top/rongshangs/lumacurve/refactor/RawOutputTest.java':'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import de.robv.android.xposed.*;import com.android.server.display.*;
public class RawOutputTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static boolean invoke(DisplayPowerState target,String name,float value)throws Exception{Method m=DisplayPowerState.class.getDeclaredMethod(name,float.class);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=target;p.args=new Object[]{value};XposedBridge.hooks.get(m).before(p);if(!p.skipped)m.invoke(target,value);return p.skipped;}
 public static void main(String[] args)throws Exception{HookRuntime s=new HookRuntime();HookEntry.states.put(new Object(),s);RawPanelOutputHooks.install(s.owner.getClass());check(RawPanelOutputHooks.supports(s.owner.getClass()));check(XposedBridge.hooks.size()==3);RawPanelOutputHooks.install(s.owner.getClass());check(XposedBridge.hooks.size()==3);DisplayPowerState main=s.owner.mDisplayPowerController.power,rear=new DisplayPowerState();
  for(String name:new String[]{"setScreenBrightness","setSdrScreenBrightness"}){s.brightnessControl.active=false;check(!invoke(main,name,.5f));s.brightnessControl.active=true;check(invoke(main,name,.5f));check(!invoke(rear,name,.5f));check(!invoke(main,name,0));check(!invoke(main,name,-1));check(!invoke(main,name,Float.NaN));s.thread=false;check(!invoke(main,name,.5f));s.thread=true;}
  check(s.brightnessControl.rawBlockedWrites==2);
  s.brightnessControl.active=true;main.brightness=0;check(!invoke(main,"setScreenBrightness",.25f));check(invoke(main,"setScreenBrightness",.5f));main.brightness=Float.NaN;check(!invoke(main,"setScreenBrightness",.25f));check(invoke(main,"setSdrScreenBrightness",.5f));
  Method off=DisplayPowerState.class.getDeclaredMethod("setScreenState",int.class,int.class);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=main;p.args=new Object[]{3,0};s.brightnessControl.active=true;XposedBridge.hooks.get(off).before(p);check(!s.brightnessControl.active&&s.brightnessControl.pauses==1&&!p.skipped);off.invoke(main,3,0);check(main.state==3);p.thisObject=rear;p.args=new Object[]{1,0};XposedBridge.hooks.get(off).before(p);check(s.brightnessControl.pauses==1);p.thisObject=main;p.args=new Object[]{2,0};XposedBridge.hooks.get(off).before(p);check(s.brightnessControl.pauses==1);
  System.out.println("Raw main output isolation: "+cases+" cases PASS; actual hooks with callback doubles, Android not tested");
 }
}'''}
paths=[]
for name,text in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8');paths.append(str(p))
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(classes),str(S/'RawPanelOutputHooks.java'),*paths],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.RawOutputTest'],check=True)
