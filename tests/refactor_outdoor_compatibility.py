"""Run actual production hooks against mutually exclusive OEM ABI fixtures.
No firmware is distributed, no system/device brightness measurements are claimed.
"""
from pathlib import Path
import ast, re, subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
JSON=ROOT/'build/refactor-diagnostics/json-20240303.jar'
tree=ast.parse((ROOT/'tests/refactor_outdoor.py').read_text(encoding='utf-8'))
original=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
legacy=r'''
 public boolean mOprBrightnessControlAvailable=true,mPendingResetGrayscaleStateForOpr,grayLegal=true;
 public float mCurrentGrayScale=232,mAppliedMaxOprBrightness;
 public int[] mOprGrayscaleThreshold={64,77,90,103,116,122,127,132,137,142,147,152,157,162};
 public int[] mOprNitThreshold={3500,3200,3000,2600,2200,2000,1800,1700,1600,1500,1400,1300,1200,1100,1060};
 public int mOprAmbientLuxThreshold=5090,mOprMaxNitThreshold=1060;
 public DisplayDeviceConfig mDisplayDeviceConfig=new DisplayDeviceConfig();
 private float getRestrictedOprBrightness(float gray){return opr;}
 private void resetScreenGrayscaleState(){}
 private boolean isGrayScaleLegal(float gray){return grayLegal&&gray>=0&&gray<=255;}
'''
test=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import com.android.server.display.*;import com.android.server.display.brightness.*;import de.robv.android.xposed.*;import android.os.*;
public class OutdoorCompatibilityTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void eq(float a,float b){check(Math.abs(a-b)<.00001f);}
 static HookRuntime state(DisplayPowerControllerImpl o)throws Exception{HookRuntime s=new HookRuntime();s.owner=o;HookEntry.states.put(o,s);return s;}
 static JSONObject config(boolean ceiling)throws Exception{return new JSONObject().put("outdoor_enabled",true).put("outdoor_range_unlock",true).put("outdoor_opr_relax",true).put("outdoor_max_nit",ceiling?2000:0);}
 public static void main(String[] args)throws Exception{
  String scenario=args[0];boolean old=scenario.equals("legacy"),modern=scenario.equals("modern")||scenario.equals("modern_dual"),supported=old||modern;
  DisplayPowerControllerImpl owner=new DisplayPowerControllerImpl();HookRuntime s=state(owner);owner.thermal=1;owner.opr=.5f;
  OutdoorTuning.install(owner.getClass());int hooks=XposedBridge.hookCount();
  for(int i=0;i<20;i++){OutdoorTuning.install(owner.getClass());OutdoorOpr.install(owner.getClass());OutdoorTuning.installNitCap(owner.getClass());check(XposedBridge.hookCount()==hooks);}
  check(s.outdoor.oprSupported()==supported);check(OutdoorTuning.nitCaps.contains(owner.getClass())==supported);
  check(OutdoorSdrScope.profile(owner.getClass()).equals(old?"legacy_opr":modern?"privacy_aware":"unsupported"));
  check(OutdoorOpr.owners.contains(owner.getClass())==supported);
  s.outdoor.configure(OutdoorOptions.parse(config(false)));s.handler.advance(SystemClock.now+3000);
  if(!supported){
   check(!OutdoorSdrScope.detail(owner.getClass()).isEmpty());
   for(int i=0;i<20;i++){eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);check(OutdoorOpr.current.get()==null);}
   check(s.outdoor.oprRaises==0);check(!s.outdoor.status().getBoolean("nit_limit_supported"));
   System.out.println("Outdoor ABI "+scenario+": "+cases+" cases PASS");return;
  }
  check(OutdoorSdrScope.resolve(owner.getClass())==OutdoorSdrScope.resolve(owner.getClass()));
  check(OutdoorTuning.nitGuards.get(owner.getClass())==OutdoorSdrScope.resolve(owner.getClass()));
  for(int i=0;i<20;i++){long count=s.outdoor.oprRaises;BrightnessReason r=new BrightnessReason();r.addModifier(2);eq(owner.adjustSdrBrightness(1,true,r,false,false),1);check(s.outdoor.oprRaises==count+1);check(r.getModifier()==2);check(OutdoorOpr.current.get()==null);}
  BrightnessReason prior=new BrightnessReason();prior.addModifier(8192|2);eq(owner.adjustSdrBrightness(1,true,prior,false,false),1);check(prior.getModifier()==(8192|2));
  owner.thermal=.3f;eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.3f);owner.thermal=1;
  owner.battery=.2f;eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.2f);owner.battery=1;
  owner.power=.25f;eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.25f);owner.power=1;
  s.outdoor.configure(OutdoorOptions.parse(config(true)));s.handler.advance(SystemClock.now+3000);
  float cap=s.outdoor.nitCeiling(2000);check(cap>.5f&&cap<1);eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),cap);check(owner.convertBrightnessToNit(cap)<=2000);
  for(int condition=0;condition<10;condition++){
   if(condition==0)owner.hdrScene=true;if(condition==1)s.hdr=true;if(condition==2)s.thread=false;if(condition==3)s.user=false;
   if(condition==4)s.screen=false;if(condition==5)s.power.low=true;if(condition==6)s.scene=false;if(condition==7)s.kernel.auto=false;
   if(condition==8)owner.mAutoBrightnessEnable=false;if(condition==9)s.strategy="OverrideBrightnessStrategy";
   long raised=s.outdoor.oprRaises;eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);eq(owner.lastSdrInput,1);check(s.outdoor.oprRaises==raised);check(OutdoorOpr.current.get()==null);
   owner.hdrScene=false;s.hdr=false;s.thread=true;s.user=true;s.screen=true;s.power.low=false;s.scene=true;s.kernel.auto=true;owner.mAutoBrightnessEnable=true;s.strategy="AutomaticBrightnessStrategy";
   s.outdoor.stop();s.outdoor.configure(OutdoorOptions.parse(config(true)));s.handler.advance(SystemClock.now+3000);
  }
  eq(owner.adjustSdrBrightness(1,false,new BrightnessReason(),false,false),.5f);eq(owner.lastSdrInput,1);
  // Invalid data/modern guard failures never switch profiles or apply either hook.
  if(old){
   OutdoorSdrScope profile=OutdoorSdrScope.resolve(owner.getClass());Class<?> c=owner.getClass();
   for(String field:new String[]{"mOprGrayscaleThreshold","mOprNitThreshold","mCurrentGrayScale","mOprBrightnessControlAvailable","mDisplayDeviceConfig"}){
    java.lang.reflect.Field f=c.getField(field);Object saved=f.get(owner);
    f.set(owner,field.equals("mCurrentGrayScale")?Float.NaN:field.equals("mOprBrightnessControlAvailable")?false:null);
    check(!OutdoorTuning.nitScope(owner));eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);eq(owner.lastSdrInput,1);check(OutdoorSdrScope.resolve(c)==profile);f.set(owner,saved);
   }
   java.lang.reflect.Field f=c.getField("mOprNitThreshold");Object saved=f.get(owner);
   for(int[] table:new int[][]{new int[]{3500},new int[]{0},new int[130]}){f.set(owner,table);check(!OutdoorTuning.nitScope(owner));}f.set(owner,saved);
   java.lang.reflect.Field legal=c.getField("grayLegal");legal.setBoolean(owner,false);check(!OutdoorTuning.nitScope(owner));eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);legal.setBoolean(owner,true);
  }else{
   java.lang.reflect.Field privacy=owner.getClass().getField("privacy"),failure=owner.getClass().getField("throwPrivacy");
   privacy.setBoolean(owner,true);eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);eq(owner.lastSdrInput,1);privacy.setBoolean(owner,false);
   failure.setBoolean(owner,true);eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);eq(owner.lastSdrInput,1);check(OutdoorSdrScope.profile(owner.getClass()).equals("privacy_aware"));failure.setBoolean(owner,false);
   if(scenario.equals("modern_dual")){java.lang.reflect.Field f=owner.getClass().getField("mOprNitThreshold");f.set(owner,null);eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),cap);}
  }
  // A different instance cannot inherit this instance's active outdoor policy.
  DisplayPowerControllerImpl other=new DisplayPowerControllerImpl();other.opr=.4f;other.thermal=1;HookRuntime otherState=state(other);
  eq(other.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.4f);check(otherState.outdoor.oprRaises==0);check(OutdoorOpr.current.get()==null);
  owner.throwSdr=true;try{owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false);throw new AssertionError();}catch(RuntimeException expected){cases++;}check(OutdoorOpr.current.get()==null);owner.throwSdr=false;
  s.outdoor.stop();eq(owner.adjustSdrBrightness(1,true,new BrightnessReason(),false,false),.5f);eq(owner.lastSdrInput,1);
  System.out.println("Outdoor ABI "+scenario+": "+cases+" cases PASS");
 }
}'''
total=0
for scenario in ['modern','modern_dual','legacy','unknown_privacy','unknown_layout','wrong_privacy','missing_hdr','wrong_legacy_field']:
    sources={k:v for k,v in original.items() if not k.endswith('/OutdoorTest.java')}
    key='com/android/server/display/DisplayPowerControllerImpl.java'
    owner=sources[key]
    if scenario!='modern':owner=owner.replace('public float getMaxHbmBrightnessForPeak()',legacy+'\n public float getMaxHbmBrightnessForPeak()')
    if scenario not in ['modern','modern_dual']:
        owner=re.sub(r' private boolean shouldUsePrivacyOprBrightness\(\)\{[^\n]+\}\n','',owner)
        owner=owner.replace('hdrScene,privacy,throwPrivacy,throwSdr','hdrScene,throwSdr')
    if scenario=='unknown_privacy':owner=owner.replace('public boolean mOprBrightnessControlAvailable','public boolean mPrivacyUhbmEnabled; public boolean mOprBrightnessControlAvailable')
    if scenario=='unknown_layout':owner=owner.replace('mOprNitThreshold','unrecognizedNitTable')
    if scenario=='wrong_privacy':owner=owner.replace('public float getMaxHbmBrightnessForPeak()','private int shouldUsePrivacyOprBrightness(){return 0;} public float getMaxHbmBrightnessForPeak()')
    if scenario=='missing_hdr':owner=owner.replace('private boolean isHdrScene()','private boolean unavailableHdr()')
    if scenario=='wrong_legacy_field':owner=owner.replace('int[] mOprNitThreshold={3500,3200,3000,2600,2200,2000,1800,1700,1600,1500,1400,1300,1200,1100,1060}','float[] mOprNitThreshold={3500}')
    sources[key]=owner
    sources['com/android/server/display/DisplayDeviceConfig.java']='package com.android.server.display;public class DisplayDeviceConfig{}'
    sources['top/rongshangs/lumacurve/refactor/OutdoorCompatibilityTest.java']=test
    bridge='de/robv/android/xposed/XposedBridge.java'
    sources[bridge]=sources[bridge].replace('public static void log(String s){}','public static void log(String s){}public static int hookCount(){int n=0;for(java.util.List<XC_MethodHook> list:hooks.values())n+=list.size();return n;}')
    out=ROOT/'build/refactor-outdoor-compatibility-tests'/scenario;out.mkdir(parents=True,exist_ok=True)
    for name,text in sources.items():
        dest=out/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text,encoding='utf-8')
    classes=out/'classes';classes.mkdir(exist_ok=True)
    production=[SRC/(n+'.java') for n in ['OutdoorNodeTarget','OutdoorOptions','OutdoorPolicy','HbmAccess','OutdoorController','OutdoorTuning','OutdoorOpr','OutdoorSdrScope','ThermalPolicy','AdvancedPolicy','LiveLimitEvidence','NitBrightnessLimit']]
    subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(JSON),'-d',str(classes),*map(str,production),*[str(out/n) for n in sources]],check=True)
    result=subprocess.run(['java','-cp',str(classes)+';'+str(JSON),'top.rongshangs.lumacurve.refactor.OutdoorCompatibilityTest',scenario],check=True,capture_output=True,text=True)
    print(result.stdout,end='');total+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
coexist=r'''package top.rongshangs.lumacurve.refactor;
import java.net.*;import java.io.*;import java.util.*;
public class ScopeCoexistenceTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("coexistence "+cases);cases++;}
 static class Loader extends URLClassLoader {
  Loader(String path)throws Exception{super(new URL[]{new File(path).toURI().toURL()},ScopeCoexistenceTest.class.getClassLoader());}
  protected synchronized Class<?> loadClass(String n,boolean resolve)throws ClassNotFoundException{
   if(!n.startsWith("com.android.server.display."))return super.loadClass(n,resolve);
   Class<?> c=findLoadedClass(n);if(c==null)c=findClass(n);if(resolve)resolveClass(c);return c;
  }
 }
 public static void main(String[] args)throws Exception{
  try(Loader modernLoader=new Loader(args[0]);Loader oldLoader=new Loader(args[1])){
   String name="com.android.server.display.DisplayPowerControllerImpl";
   Class<?> modern=modernLoader.loadClass(name),old=oldLoader.loadClass(name);check(modern!=old);check(modern.getName().equals(old.getName()));
   Object a=modern.newInstance(),b=old.newInstance();OutdoorSdrScope first=OutdoorSdrScope.resolve(modern),second=OutdoorSdrScope.resolve(old);
   check(first!=second);check(first.name.equals("privacy_aware")&&second.name.equals("legacy_opr"));
   for(int i=0;i<100;i++){check(OutdoorSdrScope.resolve(modern)==first);check(OutdoorSdrScope.resolve(old)==second);check(first.allows(a));check(second.allows(b));}
   modern.getField("privacy").setBoolean(a,true);check(!first.allows(a));check(second.allows(b));
   modern.getField("throwPrivacy").setBoolean(a,true);try{first.allows(a);throw new AssertionError();}catch(java.lang.reflect.InvocationTargetException expected){cases++;}
   check(OutdoorSdrScope.resolve(modern)==first);check(first.name.equals("privacy_aware"));check(second.allows(b));
   List<Thread> threads=new ArrayList<>();List<Throwable> errors=Collections.synchronizedList(new ArrayList<Throwable>());
   for(int i=0;i<8;i++){Thread t=new Thread(()->{try{for(int j=0;j<100;j++)if(OutdoorSdrScope.resolve(modern)!=first||OutdoorSdrScope.resolve(old)!=second)throw new AssertionError("profile replaced");}catch(Throwable e){errors.add(e);}});threads.add(t);t.start();}
   for(Thread t:threads)t.join();check(errors.isEmpty());check(OutdoorSdrScope.profiles.size()==2);
  }
  System.out.println("Outdoor profile coexistence: "+cases+" cases PASS");
 }
}'''
out=ROOT/'build/refactor-outdoor-compatibility-tests';main=out/'ScopeCoexistenceTest.java';main.write_text(coexist,encoding='utf-8')
classes=out/'modern/classes'
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(classes)+';'+str(JSON),'-d',str(classes),str(main)],check=True)
result=subprocess.run(['java','-cp',str(classes)+';'+str(JSON),'top.rongshangs.lumacurve.refactor.ScopeCoexistenceTest',str(out/'modern_dual/classes'),str(out/'legacy/classes')],check=True,capture_output=True,text=True)
print(result.stdout,end='');total+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
print(f'Outdoor compatibility total: {total} cases PASS; 8 ABI fixtures and simultaneous class loaders, device not tested')
