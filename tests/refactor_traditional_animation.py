"""Run the production traditional-ramp adapter against OEM call-chain doubles."""
from pathlib import Path
import ast, os, subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/traditional-animation-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
tree=ast.parse((R/'tests/refactor_advanced_hooks.py').read_text(encoding='utf-8'))
fixtures=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
sources={k:v for k,v in fixtures.items() if '/hooking/' in k}
base='top/rongshangs/lumacurve/refactor/'
sources[base+'HookEntry.java']='''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;import java.util.*;
class HookEntry{static Map<Object,HookRuntime> states=new IdentityHashMap<>();static Field field(Class<?> c,String n)throws Exception{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}}'''
sources[base+'HookRuntime.java']='''package top.rongshangs.lumacurve.refactor;class HookRuntime{
 Object owner;AdvancedOptions advanced=new AdvancedOptions();boolean displayThread=true,allowed=true;long animationAdjustments;
 boolean onDisplayThread(){return displayThread;}boolean animationTuningAllowed(Object a,Object b){return allowed;}void log(String s){}
}'''
sources['com/android/server/display/RampAnimator.java']='''package com.android.server.display;
public class RampAnimator{
 public float mCurrentValue=.1f,mRate=.2f,mTargetLinearValue=.5f,lastTarget,lastRate;public boolean mFirstTime;public Object mDisplayPowerControllerImpl,mObject,mProperty;public boolean fail;public float limit;
 public boolean setAnimationTarget(float target,float rate,boolean idle,float lux){lastTarget=target;lastRate=rate;if(fail)throw new IllegalStateException("native");return setAnimationTarget(target,rate,limit,limit);}
 private boolean setAnimationTarget(float target,float rate,float up,float down){mRate=limit>0?Math.max(rate,Math.abs(target-mCurrentValue)/limit):rate;mTargetLinearValue=target;return true;}
 private float updateRampRate(float r,float t){return r;}
}'''
sources[base+'TraditionalAnimationTest.java']='''package top.rongshangs.lumacurve.refactor;
import java.util.*;import com.android.server.display.RampAnimator;import top.rongshangs.lumacurve.refactor.hooking.XposedBridge;
public class TraditionalAnimationTest{
 public static class Property{String name;public Property(String n){name=n;}public String getName(){return name;}}
 public static class Display{public static final Property SCREEN_BRIGHTNESS_FLOAT=new Property("screen"),SCREEN_SDR_BRIGHTNESS_FLOAT=new Property("sdr");}
 public static class Dual{public RampAnimator mFirst=new RampAnimator(),mSecond=new RampAnimator();}
 public static class Dpc{public Display display=new Display();public Dual mScreenBrightnessRampAnimator=new Dual();public Display getDisplayPowerState(){return display;}}
 public static class Record{public int mRateModifier,reason=21;private int getCurrentRateReason(String property){return reason;}}
 public static class Rate{public static final int REASON_AUTO_BRIGHTNESS_RATE=21,REASON_THERMAL_BRIGHTNESS_RATE=23,REASON_HDR_BRIGHTNESS_RATE=27,REASON_MANUAL_BRIGHTNESS_RATE=31,REASON_ZERO_RATE=41;public Object mDisplayPowerControllerImpl;public boolean mSlowChange=true;public Record mRateRecord=new Record();}
 public static class Impl{public Object mAutomaticBrightnessController=new Object();}
 public static class Owner{public int mDisplayId;public Dpc mDisplayPowerController=new Dpc();public Impl mAutomaticBrightnessControllerImpl=new Impl();public Object mBrightnessAnimator,mSdrBrightnessAnimator;public Rate mRampRateController=new Rate();public boolean refactor;public boolean supportRefactorBrightnessPolicy(){return refactor;}}
 static int cases;static void check(boolean v){if(!v)throw new AssertionError("case "+cases);cases++;}
 static HookRuntime runtime(){HookRuntime s=new HookRuntime();Owner o=new Owner();s.owner=o;o.mRampRateController.mDisplayPowerControllerImpl=o;Dual d=o.mDisplayPowerController.mScreenBrightnessRampAnimator;for(RampAnimator a:new RampAnimator[]{d.mFirst,d.mSecond}){a.mDisplayPowerControllerImpl=o;a.mObject=o.mDisplayPowerController.display;}d.mFirst.mProperty=Display.SCREEN_BRIGHTNESS_FLOAT;d.mSecond.mProperty=Display.SCREEN_SDR_BRIGHTNESS_FLOAT;s.advanced.enabled[2]=true;s.advanced.values[9]=2;s.advanced.values[10]=3;HookEntry.states.put(o,s);return s;}
 static float animate(RampAnimator a,float target,float rate){XposedBridge.call(a,"setAnimationTarget",new Class<?>[]{float.class,float.class,boolean.class,float.class},target,rate,false,10f);return a.lastRate;}
 static RampAnimator first(HookRuntime s){return ((Owner)s.owner).mDisplayPowerController.mScreenBrightnessRampAnimator.mFirst;}
 public static void main(String[] args)throws Exception{
  TraditionalAnimation.install(TraditionalAnimationTest.class.getClassLoader());HookRuntime s=runtime();Owner o=(Owner)s.owner;RampAnimator a=first(s),b=o.mDisplayPowerController.mScreenBrightnessRampAnimator.mSecond;
  check(TraditionalAnimation.supported(s));check(animate(a,.5f,.2f)==.1f);check(a.lastTarget==.5f&&s.animationAdjustments==1);check(animate(b,.01f,.3f)==.1f);check(s.animationAdjustments==2);
  TraditionalAnimation.restore(s);check(a.mRate==.2f&&b.mRate==.3f);check(TraditionalAnimation.rates.isEmpty());
  animate(a,.5f,.2f);a.mRate=.9f;TraditionalAnimation.restore(s);check(a.mRate==.9f);
  // Default off and runtime eligibility leave native calls byte-for-byte in the same coordinates.
  s.advanced.enabled[2]=false;check(animate(a,.5f,.2f)==.2f);s.advanced.enabled[2]=true;
  for(int i=0;i<11;i++){
   s=runtime();o=(Owner)s.owner;a=first(s);
   if(i==0)s.allowed=false;if(i==1)s.displayThread=false;if(i==2)o.mDisplayId=1;if(i==3)o.refactor=true;if(i==4)o.mBrightnessAnimator=new Object();if(i==5)o.mSdrBrightnessAnimator=new Object();
   if(i==6)a.mFirstTime=true;if(i==7)o.mRampRateController.mSlowChange=false;if(i==8)o.mRampRateController.mRateRecord.mRateModifier=1;if(i==9)a.mObject=new Display();if(i==10)a.mDisplayPowerControllerImpl=new Object();
   check(animate(a,.5f,.2f)==.2f);check(s.animationAdjustments==0);
  }
  for(int reason:new int[]{23,27,31,41,-1,0,22}){s=runtime();o=(Owner)s.owner;o.mRampRateController.mRateRecord.reason=reason;check(animate(first(s),.5f,.2f)==.2f);}
  s=runtime();a=first(s);check(animate(a,.5f,0)==0);check(animate(a,.5f,-1)==-1);check(Float.isNaN(animate(a,.5f,Float.NaN)));check(animate(a,.1f,.2f)==.2f);
  // Native downstream time limits still win over a slower requested rate.
  a.limit=1;check(animate(a,.5f,.2f)==.1f);check(a.mRate>=.4f&&s.animationAdjustments==0);TraditionalAnimation.restore(s);check(a.mRate>=.4f);
  a.limit=0;a.fail=true;try{animate(a,.5f,.2f);throw new AssertionError();}catch(RuntimeException expected){check(!TraditionalAnimation.rates.containsKey(a));}
  // Replacement objects and malformed bindings are never treated as a healthy alternate route.
  s=runtime();o=(Owner)s.owner;o.mRampRateController=null;check(!TraditionalAnimation.supported(s));check(animate(first(s),.5f,.2f)==.2f);
  s=runtime();o=(Owner)s.owner;o.mDisplayPowerController.mScreenBrightnessRampAnimator.mSecond=o.mDisplayPowerController.mScreenBrightnessRampAnimator.mFirst;check(!TraditionalAnimation.supported(s));
  s=runtime();o=(Owner)s.owner;first(s).mProperty=new Property("screen");check(!TraditionalAnimation.supported(s));
  s=runtime();o=(Owner)s.owner;o.mRampRateController.mDisplayPowerControllerImpl=new Owner();check(!TraditionalAnimation.supported(s));
  // Distinct owners restore only their own call-applied rates.
  s=runtime();HookRuntime other=runtime();animate(first(s),.5f,.2f);animate(first(other),.5f,.2f);TraditionalAnimation.restore(s);check(first(s).mRate==.2f&&first(other).mRate==.1f);TraditionalAnimation.restore(other);check(first(other).mRate==.2f);
  for(float current:new float[]{0,.2f,1,Float.NaN,-1,Float.POSITIVE_INFINITY})for(float target:new float[]{0,.5f,1,Float.NaN,-1,Float.POSITIVE_INFINITY})for(double scale:new double[]{.5,1,3,Double.NaN,0,4}){
   float r=TraditionalAnimation.rate(current,target,.3f,scale);boolean valid=Float.isFinite(current)&&Float.isFinite(target)&&current>=0&&current<=1&&target>=0&&target<=1&&current!=target&&Double.isFinite(scale)&&scale>=.5&&scale<=3;
   check(r==(valid?(float)(.3f/scale):.3f));
  }
  System.out.println("Traditional animation model: "+cases+" cases PASS; host call model, no target device execution");
 }
}'''
paths=[]
for name,code in sources.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
classes=O/'classes';classes.mkdir(exist_ok=True)
production=['TraditionalAnimation.java','AdvancedOptions.java','AdvancedPolicy.java','hooking/HookTransaction.java']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(classes),*map(str,paths),*[str(S/n) for n in production]],check=True)
import re
result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.TraditionalAnimationTest'],capture_output=True,text=True,check=True)
print(result.stdout,end='');total=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
# Execute the same eligibility, protection and rollback suite with the older
# three-argument native wrapper. Downstream limits and coordinate system match.
variants=dict(sources)
variants['com/android/server/display/RampAnimator.java']=variants['com/android/server/display/RampAnimator.java'].replace('boolean idle,float lux','boolean idle')
variants[base+'TraditionalAnimationTest.java']=variants[base+'TraditionalAnimationTest.java'].replace('float.class,float.class,boolean.class,float.class},target,rate,false,10f','float.class,float.class,boolean.class},target,rate,false')
out=O/'three-argument';paths=[]
for name,code in variants.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
c=out/'classes';c.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(c),*map(str,paths),*[str(S/n) for n in production]],check=True)
result=subprocess.run(['java','-cp',str(c)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.TraditionalAnimationTest'],capture_output=True,text=True,check=True)
print('three-argument wrapper: '+result.stdout.strip());total+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
for scenario in ['both_wrappers','malformed_preferred_with_old','static_preferred_with_old']:
 variants=dict(sources)
 ramp=variants['com/android/server/display/RampAnimator.java']
 ramp=ramp.replace('public class RampAnimator{','public class RampAnimator{ public boolean setAnimationTarget(float target,float rate,boolean idle){lastTarget=target;lastRate=rate;return true;}')
 if scenario=='malformed_preferred_with_old':ramp=ramp.replace('boolean setAnimationTarget(float target,float rate,boolean idle,float lux)','long setAnimationTarget(float target,float rate,boolean idle,float lux)').replace('return setAnimationTarget(target,rate,limit,limit);','setAnimationTarget(target,rate,limit,limit);return 1L;')
 if scenario=='static_preferred_with_old':
  start=ramp.index(' public boolean setAnimationTarget(float target,float rate,boolean idle,float lux)')
  end=ramp.index(' private boolean setAnimationTarget',start)
  ramp=ramp[:start]+' public static boolean setAnimationTarget(float target,float rate,boolean idle,float lux){return true;}\n'+ramp[end:]
 variants['com/android/server/display/RampAnimator.java']=ramp
 code=variants[base+'TraditionalAnimationTest.java'];start=code.index(' public static void main(')
 expected='check(TraditionalAnimation.supported(s));check(animate(first(s),.5f,.2f)==.1f);check(first(s).mRate==.1f);' if scenario=='both_wrappers' else 'check(!TraditionalAnimation.supported(s));check(TraditionalAnimation.installed.isEmpty());'
 variants[base+'TraditionalAnimationTest.java']=code[:start]+''' public static void main(String[] args)throws Exception{TraditionalAnimation.install(TraditionalAnimationTest.class.getClassLoader());HookRuntime s=runtime();'''+expected+'''System.out.println("Wrapper selection: "+cases+" cases PASS");}}'''
 out=O/scenario;paths=[]
 for name,code in variants.items():
  p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
 c=out/'classes';c.mkdir(exist_ok=True)
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(c),*map(str,paths),*[str(S/n) for n in production]],check=True)
 result=subprocess.run(['java','-cp',str(c)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.TraditionalAnimationTest'],capture_output=True,text=True,check=True)
 print(scenario+': '+result.stdout.strip());total+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
for scenario in ['wrong_first_flag','wrong_modifier','wrong_reason_result','aliased_reason']:
 variants=dict(sources)
 code=variants[base+'TraditionalAnimationTest.java'];start=code.index(' public static void main(')
 code=code[:start]+''' public static void main(String[] args)throws Exception{TraditionalAnimation.install(TraditionalAnimationTest.class.getClassLoader());HookRuntime s=runtime();check(!TraditionalAnimation.supported(s));check(animate(first(s),.5f,.2f)==.2f);System.out.println("Rejected ABI: "+cases+" cases PASS");}}'''
 if scenario=='wrong_first_flag':variants['com/android/server/display/RampAnimator.java']=variants['com/android/server/display/RampAnimator.java'].replace('boolean mFirstTime','int mFirstTime')
 if scenario=='wrong_modifier':code=code.replace('public int mRateModifier,reason=21;','public boolean mRateModifier;public int reason=21;')
 if scenario=='wrong_reason_result':code=code.replace('private int getCurrentRateReason','private long getCurrentRateReason')
 if scenario=='aliased_reason':code=code.replace('REASON_AUTO_BRIGHTNESS_RATE=21','REASON_AUTO_BRIGHTNESS_RATE=23')
 variants[base+'TraditionalAnimationTest.java']=code
 out=O/scenario;paths=[]
 for name,code in variants.items():
  p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
 c=out/'classes';c.mkdir(exist_ok=True)
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(c),*map(str,paths),*[str(S/n) for n in production]],check=True)
 result=subprocess.run(['java','-cp',str(c)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.TraditionalAnimationTest'],capture_output=True,text=True,check=True)
 print(scenario+': '+result.stdout.strip());total+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
print(f'Traditional animation: {total} cases PASS; original, alternate and malformed ABIs; no target device execution')
