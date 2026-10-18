"""Production bidirectional coordinates, OEM callbacks and pure calibration with host doubles."""
from pathlib import Path
import os,re,subprocess
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';OUT=ROOT/'build/control-center-tests';OUT.mkdir(exist_ok=True)
files={
'android/animation/ValueAnimator.java':'package android.animation;public class ValueAnimator{public int cancelled;public void cancel(){cancelled++;}}',
'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}',
'android/database/ContentObserver.java':'package android.database;public class ContentObserver{public ContentObserver(android.os.Handler h){}public void onChange(boolean s){}}',
'android/content/ContentResolver.java':'package android.content;public class ContentResolver{public void registerContentObserver(Object u,boolean b,android.database.ContentObserver c){}}',
'android/content/Context.java':'package android.content;public class Context{public String getPackageName(){return "com.android.systemui";}public ContentResolver getContentResolver(){return new ContentResolver();}}',
'android/app/Application.java':'package android.app;public class Application extends android.content.Context{public void onCreate(){}}',
'android/provider/Settings.java':\
'''package android.provider;import java.util.concurrent.*;public class Settings{public static class System{public static String SCREEN_BRIGHTNESS_MODE="mode";public static int mode=1;public static int getInt(android.content.ContentResolver r,String k,int fallback){return mode;}public static Object getUriFor(String k){return k;}}public static class Global{public static final ConcurrentMap<String,String> values=new ConcurrentHashMap<>();public static int requests;public static String getString(android.content.ContentResolver r,String k){return values.get(k);}public static Object getUriFor(String k){return k;}public static boolean putString(android.content.ContentResolver r,String k,String v){if(v==null)values.remove(k);else values.put(k,v);if(k.equals("request"))requests++;return true;}}}''',
'com/android/systemui/plugins/miui/controlcenter/ToggleSliderBase.java':'package com.android.systemui.plugins.miui.controlcenter;public interface ToggleSliderBase{}',
'com/android/systemui/controlcenter/policy/Slider.java':'package com.android.systemui.controlcenter.policy;public class Slider implements com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase{public int min,max=1000,value;public int getMin(){return min;}public int getMax(){return max;}}',
'com/android/systemui/controlcenter/policy/BrightnessUtils.java':'package com.android.systemui.controlcenter.policy;public class BrightnessUtils{public static float convertGammaToLinearFloat(int p,float lo,float hi){return lo+(hi-lo)*(p/1000f)*(p/1000f);}public static int convertLinearToGammaFloat(float b,float lo,float hi){return Math.round((float)Math.sqrt((b-lo)/(hi-lo))*1000);}}',
'com/android/systemui/controlcenter/policy/HyperOSBrightnessPolicyV1.java':'package com.android.systemui.controlcenter.policy;public class HyperOSBrightnessPolicyV1{public boolean enabled;public boolean getSupportV1BrightnessPolicy(){return enabled;}public static int brightnessToSliderProgress(float x){return Math.round(x*1000);}}',
'com/android/systemui/controlcenter/policy/ToggleSlidersControllerImpl.java':'package com.android.systemui.controlcenter.policy;import com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase;public class ToggleSlidersControllerImpl{public int value;public void setValue(int p,ToggleSliderBase slider){value=p;}}',
'com/android/systemui/controlcenter/policy/MiuiBrightnessController.java':'''package com.android.systemui.controlcenter.policy;import com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase;import top.rongshangs.lumacurve.refactor.InputTest;
public class MiuiBrightnessController{public android.animation.ValueAnimator mSliderAnimator=new android.animation.ValueAnimator();public android.os.Handler mBackgroundHandler=new android.os.Handler(android.os.Looper.getMainLooper());public Runnable mUpdateSliderRunnable=()->{};public boolean mExternalChange,mAutomatic=true,fail;public int mDisplayId,calls,received;public boolean tracking,automatic,stop;public android.content.Context mContext=new android.content.Context();public float mMinimumBacklight=0,mMaximumBacklight=1,mSliderTargetValue=.7f;public ToggleSlidersControllerImpl mToggleSlidersController=new ToggleSlidersControllerImpl();public HyperOSBrightnessPolicyV1 mBrightnessPolicy=new HyperOSBrightnessPolicyV1();public void addToggleSlider(ToggleSliderBase s){}public void onChanged(ToggleSliderBase s,boolean tracking,boolean automatic,int value,boolean stop){calls++;this.tracking=tracking;this.automatic=automatic;this.stop=stop;received=value;InputTest.invoke(mToggleSlidersController,"setValue",new Class<?>[]{int.class,ToggleSliderBase.class},value,s);if(fail)throw new IllegalStateException("OEM failure");}public static void nestedUpdate(MiuiBrightnessController owner,float value){owner.updateSlider(value);}public void updateSlider(float value){int p=mBrightnessPolicy.enabled?(Integer)InputTest.invokeStatic(HyperOSBrightnessPolicyV1.class,"brightnessToSliderProgress",new Class<?>[]{float.class},mSliderTargetValue):(Integer)InputTest.invokeStatic(BrightnessUtils.class,"convertLinearToGammaFloat",new Class<?>[]{float.class,float.class,float.class},value,0f,1f);InputTest.invoke(mToggleSlidersController,"setValue",new Class<?>[]{int.class,ToggleSliderBase.class},p,null);if(fail)throw new IllegalStateException("OEM feedback failure");}}''',
'com/android/server/display/RefactorBrightnessUtil.java':'package com.android.server.display;public class RefactorBrightnessUtil{public static float sliderToLogicalBrightness(float x){return x*x*x*1000;}}',
'com/android/server/display/DisplayDeviceConfig.java':'package com.android.server.display;public class DisplayDeviceConfig{public float getBacklightFromBrightness(float x){return x*x;}public float getBacklightFromNits(float x){return x/1000;}}',
'com/android/server/display/Owner.java':'package com.android.server.display;public class Owner{public DisplayDeviceConfig mDisplayDeviceConfig=new DisplayDeviceConfig();}',
'top/rongshangs/lumacurve/refactor/ControlCenterRoot.java':'package top.rongshangs.lumacurve.refactor;class ControlCenterRoot{static final String PROFILE="profile";}',
'top/rongshangs/lumacurve/refactor/ControlCenterEngine.java':'package top.rongshangs.lumacurve.refactor;class ControlCenterEngine{static final String UI="ui",REQUEST="request",TABLES="tables";}',
'top/rongshangs/lumacurve/refactor/InputTest.java':r'''package top.rongshangs.lumacurve.refactor;
import android.provider.Settings;import org.json.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import top.rongshangs.lumacurve.refactor.hooking.*;import com.android.systemui.controlcenter.policy.*;import com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase;
public class InputTest{
 static int cases;static Map<Method,XC_MethodHook> hooks=new LinkedHashMap<>();static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void near(double a,double b,double tolerance){check(Math.abs(a-b)<=tolerance);}
 public static Object invokeStatic(Class<?> owner,String name,Class<?>[] types,Object...args){return invoke(owner,name,types,args);}public static Object invoke(Object owner,String name,Class<?>[] types,Object...args){try{Method m=(owner instanceof Class?(Class<?>)owner:owner.getClass()).getDeclaredMethod(name,types);m.setAccessible(true);XC_MethodHook h=hooks.get(m);XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam();p.thisObject=owner;p.method=m;p.args=args;if(h!=null)h.before(p);Object value=null;Throwable error=null;try{value=m.invoke(owner,p.args);}catch(InvocationTargetException f){error=f.getCause();}p.outcome(value,error);if(h!=null)h.after(p);if(p.hasThrowable())throw p.getThrowable();return p.getResult();}catch(Throwable error){throw error instanceof RuntimeException?(RuntimeException)error:new RuntimeException(error);}}
 static void call(MiuiBrightnessController c,Slider s,boolean tracking,int p,boolean stop){invoke(c,"onChanged",new Class<?>[]{ToggleSliderBase.class,boolean.class,boolean.class,int.class,boolean.class},s,tracking,true,p,stop);}
 static void feedback(MiuiBrightnessController c,float value){for(Method m:hooks.keySet())if(m.getName().equals("-$$Nest$mupdateSlider")){invokeStatic(MiuiBrightnessController.class,m.getName(),new Class<?>[]{MiuiBrightnessController.class,float.class},c,value);return;}invoke(c,"updateSlider",new Class<?>[]{float.class},value);}
 static void flush()throws Exception{CountDownLatch done=new CountDownLatch(1);ControlCenterHooks.io.execute(done::countDown);check(done.await(5,TimeUnit.SECONDS));}
 static JSONObject config(int mode)throws Exception{JSONObject calibration=ControlCenterCalibration.read(new com.android.server.display.Owner());return new JSONObject().put("build",AppBuild.BUILD).put("pid",123).put("process_start","42").put("boot_count","1").put("mode",mode).put("maximum",16383).put("kind","coordinate_v2").put("calibration",calibration);}
 public static void main(String[] args)throws Exception{
  ControlCenterCurve c=ControlCenterCurve.sample(x->x*x);near(c.toNode(.5),.25,.00002);near(c.toInput(.25),.5,.00002);near(c.toInput(2),1,0);near(c.toInput(-1),0,0);near(ControlCenterCurve.read(c.json()).toNode(.25),.0625,.00002);
  for(double n:new double[]{0,.001,.125,.333,.5,.9,1})near(c.toInput(c.toNode(n)),n,.000001);
  for(ControlCenterCurve.Probe bad:new ControlCenterCurve.Probe[]{x->1-x,x->Double.NaN,x->2*x,x->0})try{ControlCenterCurve.sample(bad);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  try{ControlCenterCurve.read(new JSONObject().put("input",new JSONArray().put(0).put(1)).put("node",new JSONArray().put(0).put(0)));throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  JSONObject calibration=ControlCenterCalibration.read(new com.android.server.display.Owner());near(ControlCenterCurve.read(calibration.getJSONObject("oem_v1")).toNode(.5),.125,.00002);check(calibration.has("standard"));class Basic{public float getBacklightFromBrightness(float x){return x;}}class BasicOwner{Object mDisplayDeviceConfig=new Basic();}JSONObject basic=ControlCenterCalibration.read(new BasicOwner());check(basic.has("standard")&&!basic.has("oem_v1"));
  try{ControlCenterCalibration.read(new Object());throw new AssertionError();}catch(NoSuchFieldException expected){cases++;}
  XposedBridge.initialize(new XposedBridge.Backend(){public XC_MethodHook.Unhook hook(Method m,XC_MethodHook h){hooks.put(m,h);return new XC_MethodHook.Unhook(()->hooks.remove(m));}public Object original(Method m,Object o,Object[] a)throws Throwable{return m.invoke(o,a);}public void log(String s){}});
  JSONObject p=config(2);Settings.Global.values.put("profile",p.toString());Settings.Global.values.put("tables",p.toString());Settings.Global.values.put("boot_count","1");ControlCenterHooks.install(InputTest.class.getClassLoader());ControlCenterHooks.bind(new android.app.Application());flush();check(ControlCenterHooks.profile!=null&&hooks.size()==8);
  MiuiBrightnessController owner=new MiuiBrightnessController();Slider bar=new Slider();invoke(owner,"addToggleSlider",new Class<?>[]{ToggleSliderBase.class},bar);check(ControlCenterHooks.abiReady);
  call(owner,bar,true,250,false);near(owner.received,707,1);check(owner.mAutomatic&&owner.tracking&&owner.automatic&&!owner.stop&&owner.mToggleSlidersController.value==250&&ControlCenterHooks.gesturePosition.get()==null);
  call(owner,bar,false,250,true);check(owner.stop&&!owner.tracking&&owner.mAutomatic);feedback(owner,.5f);near(owner.mToggleSlidersController.value,250,2);
  invoke(owner.mToggleSlidersController,"setValue",new Class<?>[]{int.class,ToggleSliderBase.class},500,null);check(owner.mToggleSlidersController.value==500);
  owner.mBrightnessPolicy.enabled=true;call(owner,bar,true,125,false);near(owner.received,500,1);check(owner.mAutomatic&&owner.mToggleSlidersController.value==125);
  ControlCenterHooks.profile=new ControlCenterHooks.Profile(config(2).put("calibration",new JSONObject(calibration.toString()).put("oem_v1",ControlCenterCurve.sample(x->x*.2).json())));feedback(owner,.9f);near(owner.mToggleSlidersController.value,810,2);ControlCenterHooks.profile=new ControlCenterHooks.Profile(config(2));float originalTarget=owner.mSliderTargetValue;feedback(owner,.5f);near(owner.mToggleSlidersController.value,250,2);check(owner.mSliderTargetValue==originalTarget&&ControlCenterHooks.feedbackPosition.get()==null);
  owner.fail=true;try{call(owner,bar,true,125,false);throw new AssertionError();}catch(IllegalStateException expected){check(ControlCenterHooks.gesturePosition.get()==null&&owner.mAutomatic);}
  try{feedback(owner,.5f);throw new AssertionError();}catch(IllegalStateException expected){check(owner.mSliderTargetValue==originalTarget&&ControlCenterHooks.feedbackPosition.get()==null);}owner.fail=false;
  ControlCenterHooks.profile=new ControlCenterHooks.Profile(config(1));call(owner,bar,true,125,false);check(owner.received==125&&owner.mAutomatic);owner.mAutomatic=false;call(owner,bar,true,125,false);near(owner.received,500,1);check(!owner.mAutomatic);
  owner.mDisplayId=1;call(owner,bar,true,125,false);check(owner.received==125);owner.mDisplayId=0;owner.mExternalChange=true;call(owner,bar,true,125,false);check(owner.received==125);owner.mExternalChange=false;
  p=config(2);p.getJSONObject("calibration").remove("oem_v1");ControlCenterHooks.profile=new ControlCenterHooks.Profile(p);call(owner,bar,true,125,false);check(owner.received==125);owner.mBrightnessPolicy.enabled=false;call(owner,bar,true,250,false);near(owner.received,707,1);
  ControlCenterHooks.profile=null;call(owner,bar,true,250,false);check(owner.received==250);
  Settings.Global.values.put("profile",config(2).put("process_start","old").toString());ControlCenterHooks.refresh();flush();check(ControlCenterHooks.profile==null);
  Settings.Global.values.put("profile",config(2).toString());Settings.Global.values.put("boot_count","2");ControlCenterHooks.refresh();flush();check(ControlCenterHooks.profile==null);
  Settings.Global.values.put("boot_count","1");ControlCenterHooks.refresh();flush();check(ControlCenterHooks.profile!=null);
  Settings.Global.values.remove("profile");ControlCenterHooks.refresh();flush();check(ControlCenterHooks.profile==null&&Settings.Global.requests==0);check(owner.mSliderAnimator.cancelled>0);
  flush();ControlCenterHooks.io.shutdownNow();XposedBridge.unhookAll();check(hooks.isEmpty());System.out.println("Control center input: "+cases+" cases PASS; production coordinate hooks, observed ABI doubles");
 }
}'''}
paths=[]
for name,text in files.items():
 p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');paths.append(p)
classes=OUT/'classes';classes.mkdir(exist_ok=True);jar=ROOT/'build/refactor-diagnostics/json-20240303.jar'
paths += [SRC/(name+'.java') for name in ['ControlCenterHooks','ControlCenterMapping','ControlCenterCurve','ControlCenterCalibration','AppBuild']]
paths += [SRC/'hooking'/(name+'.java') for name in ['XC_MethodHook','XposedBridge']]
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(jar),'-d',str(classes),*map(str,paths)],check=True)
result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(jar),'top.rongshangs.lumacurve.refactor.InputTest'],check=True,timeout=20,capture_output=True,text=True);print(result.stdout,end='');ui_cases=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))


# JVM constant-pool rename: Java syntax cannot express the actual R8 synthetic method name.
import struct
owner=classes/'com/android/systemui/controlcenter/policy/MiuiBrightnessController.class'
data=owner.read_bytes();count=struct.unpack_from('>H',data,8)[0];at=10;parts=[data[:10]];index=1
while index<count:
 start=at;tag=data[at];at+=1
 if tag==1:
  n=struct.unpack_from('>H',data,at)[0];value=data[at+2:at+2+n];at+=2+n
  if value==b'nestedUpdate':value=b'-$$Nest$mupdateSlider'
  parts.append(bytes([tag])+struct.pack('>H',len(value))+value)
 else:
  size={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,18:4}[tag];at+=size;parts.append(data[start:at])
  if tag in (5,6):index+=1
 index+=1
owner.write_bytes(b''.join(parts)+data[at:])
result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(jar),'top.rongshangs.lumacurve.refactor.InputTest'],check=True,timeout=20,capture_output=True,text=True);print('R8 synthetic feedback: '+result.stdout,end='');ui_cases+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
result=subprocess.run(['python',str(ROOT/'tests/refactor_control_center_root.py')],check=True,capture_output=True,text=True);print(result.stdout,end='');root_cases=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))

c=OUT/'lease.c'
c.write_text(r'''#include "control_center_policy.h"
#include <assert.h>
int main(void){int cases=0;struct panel_lease l;assert(panel_read_lease("cc_first 123 8000 1 8192",16383,1,&l));cases++;assert(control_center_admit(&l,123,16383,1));cases++;
assert(!control_center_admit(&l,124,16383,1));cases++;assert(!control_center_admit(&l,0,16383,1));cases++;assert(!control_center_admit(&l,123,4095,1));cases++;assert(!control_center_admit(&l,123,16383,8000));cases++;assert(!control_center_admit(&l,123,16383,-1));cases++;
l.active=0;assert(!control_center_admit(&l,123,16383,1));cases++;l.active=1;strcpy(l.token,"panel");assert(!control_center_admit(&l,123,16383,1));cases++;strcpy(l.token,"cc_bad token");assert(!control_center_admit(&l,123,16383,1));cases++;strcpy(l.token,"cc_valid");l.target=9;assert(!control_center_admit(&l,123,16383,1));cases++;l.target=16383;assert(control_center_admit(&l,123,16383,1));cases++;
printf("Control center native: %d cases PASS\n",cases);return 0;}
''',encoding='utf-8')
exe=OUT/'lease.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-Wno-unused-function','-I',str(ROOT/'experimental/refactor_hook/native'),str(c),'-o',str(exe)],check=True)
result=subprocess.run([str(exe)],check=True,capture_output=True,text=True);print(result.stdout,end='');native_cases=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
print(f'Control center total: {ui_cases+native_cases+root_cases} cases PASS; coordinate hooks and legacy lease regression, Android not tested')
