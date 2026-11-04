"""Execute production DIM hooks and rollback with host framework doubles (no Android claims)."""
from pathlib import Path
import os, subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/dim-aod-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 1234;}}',
'android/hardware/display/DisplayManagerInternal.java':'''package android.hardware.display;
public class DisplayManagerInternal{public static class DisplayPowerRequest{public static int POLICY_OFF=0,POLICY_DOZE=1,POLICY_DIM=2,POLICY_BRIGHT=3;public int policy=POLICY_DIM;}}''',
'com/android/server/display/DisplayPowerState.java':'package com.android.server.display;public class DisplayPowerState{public int screen=2;public int getScreenState(){return screen;}}',
'com/android/server/display/DisplayPowerController.java':'''package com.android.server.display;
public class DisplayPowerController{public int mDisplayId;public android.hardware.display.DisplayManagerInternal.DisplayPowerRequest mPowerRequest=new android.hardware.display.DisplayManagerInternal.DisplayPowerRequest();public DisplayPowerState state=new DisplayPowerState();public DisplayPowerState getDisplayPowerState(){return state;}}''',
'com/android/server/display/brightness/BrightnessReason.java':'''package com.android.server.display.brightness;
public class BrightnessReason{public static int REASON_AUTOMATIC=4,MODIFIER_DIMMED=1;public int reason=REASON_AUTOMATIC,modifier=MODIFIER_DIMMED;public int getReason(){return reason;}public int getModifier(){return modifier;}}''',
'com/android/server/display/brightness/strategy/DozeBrightnessStrategyImpl.java':'''package com.android.server.display.brightness.strategy;
public class DozeBrightnessStrategyImpl{public boolean full=true;public float value=.008160762f;public boolean getFullAodStateForBrightness(){return full;}public float getAodScreenBrightness(){return value;}}''',
'com/android/server/display/DisplayPowerControllerImpl.java':'''package com.android.server.display;
import com.android.server.display.brightness.*;import com.android.server.display.brightness.strategy.*;import top.rongshangs.lumacurve.refactor.DimTest;
public class DisplayPowerControllerImpl{
 public int mDisplayId;public boolean mAutoBrightnessEnable=true,full=true,hdr,throwProbe,throwInner,throwOuter;
 public DisplayPowerController mDisplayPowerController=new DisplayPowerController();public DozeBrightnessStrategyImpl mDozeBrightnessStrategy=new DozeBrightnessStrategyImpl();
 public float downstreamInput;public DisplayPowerControllerImpl nested;
 public boolean isFullAodStateForBrightness(){if(throwProbe)throw new IllegalStateException();return full;}
 private boolean isHdrScene(){return hdr;}
 private float adjustBrightnessWhenDimming(float b,boolean dim){if(throwInner)throw new IllegalStateException("inner");return full&&dim?mDozeBrightnessStrategy.value:b;}
 public float adjustSdrBrightness(float b,boolean auto,BrightnessReason why,boolean fast,boolean dim)throws Throwable{
  if(nested!=null)DimTest.output(nested,b,true,new BrightnessReason(),true);
  float result=(Float)DimTest.call(this,"adjustBrightnessWhenDimming",new Class<?>[]{float.class,boolean.class},new Object[]{b,dim});
  downstreamInput=result;if(throwOuter)throw new IllegalStateException("outer");return Math.min(result,.5f);
 }
}''',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;import java.util.*;
class HookEntry{static final Map<Object,HookRuntime> states=new IdentityHashMap<>();static HookRuntime ownerState(Object o){return states.get(o);}static Field field(Class<?> c,String n)throws Exception{for(Class<?> t=c;t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(n);}}''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':'''package top.rongshangs.lumacurve.refactor;
class HookRuntime{final Object owner;final DimAodCompatibility.Counters dimAod=new DimAodCompatibility.Counters();final Kernel kernel=new Kernel();boolean closed,changing,onThread=true,user=true;String phase="active";int logs,publishes;HookRuntime(Object o){owner=o;}boolean onDisplayThread(){return onThread;}boolean appliesToUser(){return user;}void queuePublish(){publishes++;}void log(String s){logs++;}static class Kernel{boolean enabled=true,automatic=true;Object plan(){return enabled?this:null;}Object get(String k){return automatic;}}}''',
}
for name,text in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
p=O/'top/rongshangs/lumacurve/refactor/DimTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import java.util.*;import java.net.*;import com.android.server.display.*;import com.android.server.display.brightness.*;
import top.rongshangs.lumacurve.refactor.hooking.*;
public class DimTest implements XposedBridge.Backend{
 static int cases,attempts,failAt=-1;static final Map<Method,XC_MethodHook> hooks=new HashMap<>();
 static void check(boolean yes){if(!yes)throw new AssertionError("case "+cases);cases++;}
 public XC_MethodHook.Unhook hook(Method m,XC_MethodHook c){attempts++;if(attempts==failAt)throw new IllegalStateException("install failure");hooks.put(m,c);return new XC_MethodHook.Unhook(()->hooks.remove(m));}
 public Object original(Method m,Object owner,Object[] args)throws Throwable{try{return m.invoke(owner,args);}catch(InvocationTargetException e){throw e.getCause();}}
 public void log(String t){}
 public static Object call(Object owner,String name,Class<?>[] types,Object[] args)throws Throwable{
  Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);XC_MethodHook c=hooks.get(m);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=owner;p.method=m;p.args=args;
  if(c!=null)c.before(p);if(!p.returnEarly())try{p.outcome(m.invoke(owner,args),null);}catch(InvocationTargetException e){p.outcome(null,e.getCause());}
  if(c!=null)c.after(p);if(p.hasThrowable())throw p.getThrowable();return p.getResult();
 }
 public static float output(DisplayPowerControllerImpl o,float b,boolean auto,BrightnessReason reason,boolean dim)throws Throwable{return (Float)call(o,"adjustSdrBrightness",new Class<?>[]{float.class,boolean.class,BrightnessReason.class,boolean.class,boolean.class},new Object[]{b,auto,reason,false,dim});}
 static float output(DisplayPowerControllerImpl o)throws Throwable{return output(o,.00036625599f,true,new BrightnessReason(),true);}
 static DisplayPowerControllerImpl fresh(){DisplayPowerControllerImpl o=new DisplayPowerControllerImpl();HookEntry.states.put(o,new HookRuntime(o));return o;}
 static void untouched(DisplayPowerControllerImpl o)throws Throwable{long count=HookEntry.ownerState(o).dimAod.corrections;float result=output(o);check(result==Math.min(o.mDozeBrightnessStrategy.value,.5f));check(HookEntry.ownerState(o).dimAod.corrections==count);check(DimAodCompatibility.current.get()==null);}
 static class OldOwner{}
 static class PartialOwner extends DisplayPowerControllerImpl{public float adjustSdrBrightness(float b,boolean a,BrightnessReason r,boolean f,boolean d){return b;}}
 public static void main(String[] args)throws Throwable{
  XposedBridge.initialize(new DimTest());check(DimAodCompatibility.install(DisplayPowerControllerImpl.class));check(hooks.size()==2&&XposedBridge.hookCount()==2);check(DimAodCompatibility.install(DisplayPowerControllerImpl.class)&&hooks.size()==2);
  DisplayPowerControllerImpl o=fresh();HookRuntime s=HookEntry.ownerState(o);check(DimAodCompatibility.supported(o));
  check(output(o)==.00036625599f);check(o.downstreamInput==.00036625599f&&s.dimAod.corrections==1&&s.dimAod.lastElapsed==1234&&s.logs==1);
  check(DimAodCompatibility.status(s).getString("profile").equals(DimAodCompatibility.ROUTE));check(DimAodCompatibility.status(s).getDouble("last_native_aod_result")>.008);
  // Replay repeated AOD rises from the diagnostic; the already-dimmed target stays unchanged.
  for(float target:new float[]{.008160762f,.0079f,.00036625599f,.0082f,.00036625599f,.0078f}){o.mDozeBrightnessStrategy.value=target;check(output(o)==.00036625599f);}check(s.logs==1);
  // Lower AOD values, invalid coordinates and native failures remain untouched.
  for(float target:new float[]{0f,.0001f,Float.NaN,Float.POSITIVE_INFINITY,1.2f,-1f}){o.mDozeBrightnessStrategy.value=target;float result=output(o);check(Float.isNaN(target)?Float.isNaN(result):result==Math.min(target,.5f));}
  o.mDozeBrightnessStrategy.value=.008f;for(float input:new float[]{0f,-1f,Float.NaN,1.1f,Float.POSITIVE_INFINITY})check(output(o,input,true,new BrightnessReason(),true)==.008f);
  o.mDozeBrightnessStrategy.value=.9f;check(output(o)==.00036625599f);check(o.downstreamInput==.00036625599f);check(output(o,.7f,true,new BrightnessReason(),false)==.5f); // downstream OEM cap still executes
  o.mDozeBrightnessStrategy.value=.008f;
  for(int i=0;i<15;i++){o=fresh();s=HookEntry.ownerState(o);switch(i){
   case 0:s.closed=true;break;case 1:s.changing=true;break;case 2:s.onThread=false;break;case 3:s.user=false;break;case 4:s.phase="attached";break;case 5:s.kernel.enabled=false;break;case 6:s.kernel.automatic=false;break;
   case 7:o.mDisplayId=1;break;case 8:o.mDisplayPowerController.mDisplayId=1;break;case 9:o.mAutoBrightnessEnable=false;break;case 10:o.hdr=true;break;case 11:o.mDozeBrightnessStrategy.full=false;break;
   case 12:o.mDisplayPowerController.mPowerRequest=null;break;case 13:o.mDisplayPowerController.state=null;break;case 14:o.throwProbe=true;break;}
   untouched(o);
  }
  for(int policy:new int[]{0,1,3}){o=fresh();o.mDisplayPowerController.mPowerRequest.policy=policy;untouched(o);}
  for(int state:new int[]{0,1,3,4,5,6}){o=fresh();o.mDisplayPowerController.state.screen=state;untouched(o);}
  for(int reason:new int[]{0,1,2,3,5,6,7,8,9,10}){o=fresh();BrightnessReason why=new BrightnessReason();why.reason=reason;check(output(o,.001f,true,why,true)==o.mDozeBrightnessStrategy.value);}
  o=fresh();check(output(o,.001f,false,new BrightnessReason(),true)==o.mDozeBrightnessStrategy.value);
  BrightnessReason why=new BrightnessReason();why.modifier=0;check(output(o,.001f,true,why,true)==o.mDozeBrightnessStrategy.value);why.modifier=3;check(output(o,.001f,true,why,true)==.001f);
  check(output(o,.001f,true,null,true)==o.mDozeBrightnessStrategy.value);o.full=false;check(output(o)==.00036625599f&&HookEntry.ownerState(o).dimAod.corrections==1);
  o=fresh();check((Float)call(o,"adjustBrightnessWhenDimming",new Class<?>[]{float.class,boolean.class},new Object[]{.001f,true})==o.mDozeBrightnessStrategy.value); // no outer context
  HookEntry.states.remove(o);check(output(o)==o.mDozeBrightnessStrategy.value);
  o=fresh();s=HookEntry.ownerState(o);o.nested=fresh();o.nested.mDisplayId=1;check(output(o)==.00036625599f);check(o.nested.downstreamInput==o.nested.mDozeBrightnessStrategy.value);check(s.dimAod.corrections==1&&HookEntry.ownerState(o.nested).dimAod.corrections==0);check(DimAodCompatibility.current.get()==null);
  for(boolean inner:new boolean[]{true,false}){o=fresh();o.throwInner=inner;o.throwOuter=!inner;try{output(o);throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().equals(inner?"inner":"outer"));}check(DimAodCompatibility.current.get()==null);}
  check(!DimAodCompatibility.install(OldOwner.class)&&!DimAodCompatibility.install(PartialOwner.class)&&hooks.size()==2);check(output(fresh())==.00036625599f);
  // Constants are read from the actual interface; aliases and zero masks must reject installation.
  android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_DIM=3;
  try{new DimAodCompatibility.Profile(DisplayPowerControllerImpl.class);throw new AssertionError();}catch(IllegalStateException expected){check(true);}
  android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_DIM=2;BrightnessReason.MODIFIER_DIMMED=0;
  try{new DimAodCompatibility.Profile(DisplayPowerControllerImpl.class);throw new AssertionError();}catch(IllegalStateException expected){check(true);}BrightnessReason.MODIFIER_DIMMED=1;
  // Same class name in another firmware loader is isolated; second-hook failure rolls back only that unit.
  URL classes=new java.io.File(args[0]).toURI().toURL();URL json=new java.io.File(args[1]).toURI().toURL();
  try(URLClassLoader loader=new URLClassLoader(new URL[]{classes,json},null)){
   Class<?> another=Class.forName(DisplayPowerControllerImpl.class.getName(),false,loader);check(another!=DisplayPowerControllerImpl.class);check(new DimAodCompatibility.Profile(another).outer.getDeclaringClass()==another);
   failAt=attempts+2;check(!DimAodCompatibility.install(another));check(hooks.size()==2&&XposedBridge.hookCount()==2);check(!DimAodCompatibility.install(another));check(output(fresh())==.00036625599f);
  }
  check(DimAodCompatibility.current.get()==null);System.out.println("DIM AOD coordination: "+cases+" cases PASS; production callbacks and transactional install; no Android device test");
 }
}''',encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
sources=[*O.rglob('*.java'),S/'DimAodCompatibility.java',*[S/'hooking'/(n+'.java') for n in ['XC_MethodHook','HookTransaction','XposedBridge']]]
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(p) for p in sources]],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.DimTest',str(C),str(J)],check=True)

# Newer firmware: four-argument SDR entry, no dimming substage, mAppliedDimming carries the state.
O2=R/'build/dim-aod-modern-tests';O2.mkdir(exist_ok=True)
modern={name:text for name,text in files.items() if not name.endswith('DisplayPowerControllerImpl.java')}
modern['com/android/server/display/ModernOwner.java']='''package com.android.server.display;
import com.android.server.display.brightness.BrightnessReason;
public class ModernOwner{
 public int mDisplayId;public boolean mAutoBrightnessEnable=true,mAppliedDimming=true,hdr,throwOuter;
 public DisplayPowerController mDisplayPowerController=new DisplayPowerController();
 public float aod=.008160762f,cap=.5f,result=Float.NaN;
 private boolean isHdrScene(){return hdr;}
 public float adjustSdrBrightness(float b,boolean auto,BrightnessReason why,boolean appliedAuto){
  if(throwOuter)throw new IllegalStateException("outer");
  if(Float.isFinite(result))return result;
  return Math.min(mAppliedDimming?aod:b,cap);
 }
}'''
for name,text in modern.items():
 p=O2/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
p=O2/'top/rongshangs/lumacurve/refactor/DimModernTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;import java.util.*;import com.android.server.display.*;import com.android.server.display.brightness.*;
import top.rongshangs.lumacurve.refactor.hooking.*;
public class DimModernTest implements XposedBridge.Backend{
 static int cases,attempts,failAt=-1;static final Map<Method,XC_MethodHook> hooks=new HashMap<>();
 static void check(boolean yes){if(!yes)throw new AssertionError("case "+cases);cases++;}
 public XC_MethodHook.Unhook hook(Method m,XC_MethodHook c){attempts++;if(attempts==failAt)throw new IllegalStateException("install failure");hooks.put(m,c);return new XC_MethodHook.Unhook(()->hooks.remove(m));}
 public Object original(Method m,Object owner,Object[] args)throws Throwable{try{return m.invoke(owner,args);}catch(InvocationTargetException e){throw e.getCause();}}
 public void log(String t){}
 public static Object call(Object owner,float b,boolean auto,BrightnessReason why,boolean applied)throws Throwable{
  Method m=owner.getClass().getDeclaredMethod("adjustSdrBrightness",float.class,boolean.class,BrightnessReason.class,boolean.class);m.setAccessible(true);
  XC_MethodHook c=hooks.get(m);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=owner;p.method=m;p.args=new Object[]{b,auto,why,applied};
  if(c!=null)c.before(p);if(!p.returnEarly())try{p.outcome(m.invoke(owner,p.args),null);}catch(InvocationTargetException e){p.outcome(null,e.getCause());}
  if(c!=null)c.after(p);if(p.hasThrowable())throw p.getThrowable();return p.getResult();
 }
 static float output(ModernOwner o)throws Throwable{return (Float)call(o,.00036625599f,true,new BrightnessReason(),true);}
 static ModernOwner fresh(){ModernOwner o=new ModernOwner();HookEntry.states.put(o,new HookRuntime(o));return o;}
 static void untouched(ModernOwner o)throws Throwable{long count=HookEntry.ownerState(o).dimAod.corrections;float result=output(o);check(result==Math.min(o.mAppliedDimming?o.aod:.00036625599f,o.cap));check(HookEntry.ownerState(o).dimAod.corrections==count);check(DimAodCompatibility.current.get()==null);}
 static class NoOuter{public int mDisplayId;public boolean mAutoBrightnessEnable=true,mAppliedDimming=true;public DisplayPowerController mDisplayPowerController=new DisplayPowerController();}
 static class WrongDimming{public int mDisplayId;public boolean mAutoBrightnessEnable=true;public int mAppliedDimming=1;public DisplayPowerController mDisplayPowerController=new DisplayPowerController();private boolean isHdrScene(){return false;}public float adjustSdrBrightness(float b,boolean a,BrightnessReason r,boolean p){return b;}}
 static class BothShapes{public int mDisplayId;public boolean mAutoBrightnessEnable=true,mAppliedDimming=true;public DisplayPowerController mDisplayPowerController=new DisplayPowerController();private boolean isHdrScene(){return false;}private float adjustBrightnessWhenDimming(float b,boolean d){return b;}public float adjustSdrBrightness(float b,boolean a,BrightnessReason r,boolean p){return b;}}
 public static void main(String[] args)throws Throwable{
  XposedBridge.initialize(new DimModernTest());check(DimAodCompatibility.install(ModernOwner.class));check(hooks.size()==1&&XposedBridge.hookCount()==1);check(DimAodCompatibility.install(ModernOwner.class)&&hooks.size()==1);
  ModernOwner o=fresh();HookRuntime s=HookEntry.ownerState(o);check(DimAodCompatibility.supported(o));
  check(output(o)==.00036625599f);check(s.dimAod.corrections==1&&s.dimAod.lastElapsed==1234&&s.logs==1);
  check(DimAodCompatibility.status(s).getString("profile").equals(DimAodCompatibility.ROUTE_MODERN));check(DimAodCompatibility.status(s).getDouble("last_native_aod_result")>.008);check(DimAodCompatibility.status(s).getDouble("last_output")==.00036625599f);
  for(float target:new float[]{.008160762f,.0079f,.00036625599f,.0082f,.0078f}){o.aod=target;check(output(o)==.00036625599f);}check(s.logs==1);
  // Native lowering below the dim input is a real protection and must survive.
  long base=s.dimAod.corrections;o.aod=.008f;o.cap=.0001f;check(output(o)==.0001f);check(s.dimAod.corrections==base);o.cap=.5f;
  o.result=1.2f;check(output(o)==1.2f);o.result=-1f;check(output(o)==-1f);o.result=Float.NaN;
  o.aod=.008f;for(float input:new float[]{0f,-1f,Float.NaN,1.1f,Float.POSITIVE_INFINITY})check((Float)call(o,input,true,new BrightnessReason(),true)==.008f);
  o.mAppliedDimming=false;check(output(o)==.00036625599f);check(s.dimAod.corrections==base);o.mAppliedDimming=true;
  for(int i=0;i<14;i++){o=fresh();s=HookEntry.ownerState(o);switch(i){
   case 0:s.closed=true;break;case 1:s.changing=true;break;case 2:s.onThread=false;break;case 3:s.user=false;break;case 4:s.phase="attached";break;case 5:s.kernel.enabled=false;break;case 6:s.kernel.automatic=false;break;
   case 7:o.mDisplayId=1;break;case 8:o.mDisplayPowerController.mDisplayId=1;break;case 9:o.mAutoBrightnessEnable=false;break;case 10:o.hdr=true;break;
   case 11:o.mDisplayPowerController.mPowerRequest=null;break;case 12:o.mDisplayPowerController.state=null;break;case 13:o.mAppliedDimming=false;break;}
   untouched(o);
  }
  for(int policy:new int[]{0,1,3}){o=fresh();o.mDisplayPowerController.mPowerRequest.policy=policy;untouched(o);}
  for(int state:new int[]{0,1,3,4,5,6}){o=fresh();o.mDisplayPowerController.state.screen=state;untouched(o);}
  for(int reason:new int[]{0,1,2,3,5,6,7,8,9,10}){o=fresh();BrightnessReason why=new BrightnessReason();why.reason=reason;float expect=Math.min(o.aod,o.cap);check((Float)call(o,.00036625599f,true,why,true)==expect);}
  o=fresh();check((Float)call(o,.00036625599f,false,new BrightnessReason(),true)==Math.min(o.aod,o.cap));
  BrightnessReason why=new BrightnessReason();why.modifier=0;check((Float)call(o,.00036625599f,true,why,true)==Math.min(o.aod,o.cap));why.modifier=3;check((Float)call(o,.00036625599f,true,why,true)==.00036625599f);
  check((Float)call(o,.00036625599f,true,null,true)==Math.min(o.aod,o.cap));
  o=fresh();o.throwOuter=true;try{output(o);throw new AssertionError();}catch(IllegalStateException expected){check(expected.getMessage().equals("outer"));}check(DimAodCompatibility.current.get()==null);
  o=fresh();HookEntry.states.remove(o);check(output(o)==Math.min(o.aod,o.cap));
  check(!DimAodCompatibility.install(NoOuter.class)&&!DimAodCompatibility.install(WrongDimming.class)&&!DimAodCompatibility.install(BothShapes.class)&&hooks.size()==1);
  android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_DIM=3;
  try{new DimAodCompatibility.ModernProfile(ModernOwner.class);throw new AssertionError();}catch(IllegalStateException expected){check(true);}
  android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_DIM=2;BrightnessReason.MODIFIER_DIMMED=0;
  try{new DimAodCompatibility.ModernProfile(ModernOwner.class);throw new AssertionError();}catch(IllegalStateException expected){check(true);}BrightnessReason.MODIFIER_DIMMED=1;
  check(DimAodCompatibility.current.get()==null);System.out.println("DIM AOD modern entry: "+cases+" cases PASS; inferred mapping with native lowering preserved");
 }
}''',encoding='utf-8')
C2=O2/'classes';C2.mkdir(exist_ok=True)
sources2=[*O2.rglob('*.java'),S/'DimAodCompatibility.java',*[S/'hooking'/(n+'.java') for n in ['XC_MethodHook','HookTransaction','XposedBridge']]]
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C2),*[str(p) for p in sources2]],check=True)
subprocess.run(['java','-cp',str(C2)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.DimModernTest'],check=True)
