"""Execute screen-session ownership and physical node conversion without a device."""
from pathlib import Path
import os, subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
OUT=ROOT/'build/session-tools-tests';OUT.mkdir(exist_ok=True)
files={
'android/content/Context.java':'''package android.content;public class Context{public static final String POWER_SERVICE="power",KEYGUARD_SERVICE="keyguard";public android.os.PowerManager power=new android.os.PowerManager();public android.app.KeyguardManager keyguard=new android.app.KeyguardManager();public Object getSystemService(String s){return s.equals(POWER_SERVICE)?power:keyguard;}}''',
'android/app/KeyguardManager.java':'''package android.app;public class KeyguardManager{public boolean locked;public boolean isKeyguardLocked(){return locked;}}''',
'android/os/PowerManager.java':'''package android.os;public class PowerManager{public static final int SCREEN_BRIGHT_WAKE_LOCK=10;public boolean interactive=true;public int creates;public WakeLock last;public boolean isInteractive(){return interactive;}public WakeLock newWakeLock(int level,String tag){if(level!=10||!tag.equals("HyperLux:TemporaryScreenOn"))throw new AssertionError();creates++;return last=new WakeLock();}public static class WakeLock{public boolean held;public int acquires,releases;public void setReferenceCounted(boolean b){if(b)throw new AssertionError();}public boolean isHeld(){return held;}public void acquire(){held=true;acquires++;}public void release(){held=false;releases++;}}}''',
'top/rongshangs/lumacurve/refactor/SessionTest.java':r'''package top.rongshangs.lumacurve.refactor;import android.content.Context;import org.json.*;public class SessionTest{
static int cases;static void check(boolean v){if(!v)throw new AssertionError("case "+cases);cases++;}
public static void main(String[] args)throws Exception{
 Context c=new Context();check(!TemporaryScreenOn.active());TemporaryScreenOn.set(c,true);check(TemporaryScreenOn.active()&&c.power.creates==1&&c.power.last.acquires==1);TemporaryScreenOn.set(c,true);check(c.power.creates==1);TemporaryScreenOn.set(c,false);check(!TemporaryScreenOn.active()&&c.power.last.releases==1);TemporaryScreenOn.clear();check(c.power.last.releases==1);
 c.keyguard.locked=true;try{TemporaryScreenOn.set(c,true);throw new AssertionError();}catch(IllegalStateException expected){check(!TemporaryScreenOn.active()&&c.power.creates==1);}c.keyguard.locked=false;c.power.interactive=false;try{TemporaryScreenOn.set(c,true);throw new AssertionError();}catch(IllegalStateException expected){check(!TemporaryScreenOn.active());}c.power.interactive=true;TemporaryScreenOn.set(c,true);TemporaryScreenOn.clear();check(!TemporaryScreenOn.active()&&c.power.creates==2);
 for(int max:new int[]{4095,16383,65535}){check(ControlCenterMapping.target(0,max)==10);check(ControlCenterMapping.target(1,max)==max);check(Math.abs(ControlCenterMapping.target(.5,max)-max*.5)<=.5);check(ControlCenterMapping.target(.25,max)<=ControlCenterMapping.target(.5,max));}
 for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1})try{ControlCenterMapping.target(bad,16383);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
 for(int bad:new int[]{-1,0,10,65536})try{ControlCenterMapping.target(.5,bad);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
 check(ControlCenterMapping.fraction(500,0,1000)==.5);check(ControlCenterMapping.fraction(60,10,110)==.5);check(ControlCenterMapping.fraction(0,0,65535)==0);check(ControlCenterMapping.fraction(65535,0,65535)==1);
 for(int[] bad:new int[][]{{0,0,0},{5,10,100},{101,0,100}})try{ControlCenterMapping.fraction(bad[0],bad[1],bad[2]);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
 check(BrightnessControlOptions.parse(new JSONObject()).nodeSliderMode==0);for(int n=0;n<=4;n++){JSONObject j=new JSONObject().put("control_center_node_mode",n);BrightnessControlOptions o=BrightnessControlOptions.parse(j);check(o.nodeSliderMode==n);JSONObject exported=new JSONObject();o.put(exported);check(BrightnessControlOptions.parse(exported).nodeSliderMode==n);}
 for(Object bad:new Object[]{-1,5,.5,"1",true})try{BrightnessControlOptions.parse(new JSONObject().put("control_center_node_mode",bad));throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
 System.out.println("Session tools: "+cases+" cases PASS; production policies, Android service doubles");
}}'''
}
sources=[]
for name,text in files.items():
    p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(p)
classes=OUT/'classes';classes.mkdir(exist_ok=True)
jar=ROOT/'build/refactor-diagnostics/json-20240303.jar'
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(jar),'-d',str(classes),*[str(p) for p in sources],*[str(SRC/(name+'.java')) for name in ['TemporaryScreenOn','ControlCenterMapping','BrightnessControlOptions']]],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(jar),'top.rongshangs.lumacurve.refactor.SessionTest'],check=True)
