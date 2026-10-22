"""Actual node lease policy and Java control bridge; no Android/brightness claims."""
from pathlib import Path
import subprocess,os,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/outdoor-node-tests';O.mkdir(exist_ok=True)
C=O/'test.c'
C.write_text(r'''
#include <stdio.h>
#include "outdoor_node_policy.h"
static int cases;
#define CHECK(x) do{if(!(x)){fprintf(stderr,"case %d at %d\n",cases,__LINE__);return 1;}cases++;}while(0)
int main(void){
 struct outdoor_lease l;CHECK(outdoor_lease_parse("v1 123 2500 1000 1",1000,&l));CHECK(l.pid==123&&l.active&&l.fraction==1000);
 const char *bad[]={"v3 123 2500 1000 1","v1 0 2500 1000 1","v1 123 999 1000 1","v1 123 2501 1000 1","v1 123 2500 1001 1","v1 123 2500 999 2","v1 123 2500 -1 1","v1 123 2500 1000 1 extra"};
 for(unsigned i=0;i<sizeof(bad)/sizeof(bad[0]);i++)CHECK(!outdoor_lease_parse(bad[i],1000,&l));
 l=(struct outdoor_lease){.pid=123,.active=1,.fraction=1000,.until=2500};
 for(int maximum=11;maximum<=65535;maximum+=73){
  struct outdoor_node_state s={0};int base=maximum/2;if(base<10)base=10;
  CHECK(outdoor_node_step(&s,&l,base,maximum,0,1000)==-1);
  CHECK(outdoor_node_step(&s,&l,base,maximum,0,1499)==-1);
  CHECK(outdoor_node_step(&s,&l,base,maximum,0,1500)==maximum);
  CHECK(s.held&&s.base==base);
  CHECK(outdoor_node_step(&s,&l,maximum,maximum,0,2000)==-1);
  CHECK(outdoor_node_step(&s,&l,base,maximum,0,2500)==-1&&s.inhibited&&!s.held);
  CHECK(outdoor_node_step(&s,&l,base,maximum,0,5000)==-1);
  CHECK(outdoor_node_step(&s,NULL,base,maximum,0,5100)==-1&&!s.inhibited);
 }
 struct outdoor_node_state s={0};int max=4095;
 CHECK(outdoor_node_step(&s,&l,2047,max,0,1000)==-1);CHECK(outdoor_node_step(&s,&l,2047,max,0,1500)==4095);
 CHECK(outdoor_node_step(&s,NULL,4095,max,0,1600)==2047&&!s.held);
 CHECK(outdoor_node_step(&s,&l,2047,max,0,2000)==-1);CHECK(outdoor_node_step(&s,&l,2047,max,0,2500)==4095);
 CHECK(outdoor_node_step(&s,&l,4095,max,1,2600)==-1&&!s.held); // Manual owner wins, no restore write.
 CHECK(outdoor_node_step(&s,&l,2047,max,0,3000)==-1);CHECK(outdoor_node_step(&s,&l,2047,max,0,3500)==4095);
 CHECK(outdoor_node_step(&s,&l,4095,max,0,33500)==-1&&s.held&&!s.inhibited);
 CHECK(outdoor_node_step(&s,&l,4095,max,0,34000)==-1);CHECK(outdoor_node_step(&s,NULL,4095,max,0,35000)==2047);
 l.fraction=500;outdoor_node_step(&s,&l,2047,max,0,36000);CHECK(outdoor_node_step(&s,&l,2047,max,0,36500)==3071);
 CHECK(outdoor_node_step(&s,NULL,0,max,0,37000)==-1); // Never raise an extinguished panel.

 const char *raw_bad[]={"v2 123 2500 9 1","v2 123 2500 65536 1","v2 123 2500 -1 1","v2 123 2500 2047 2","v2 123 2500 2047 1 extra"};
 for(unsigned i=0;i<sizeof(raw_bad)/sizeof(raw_bad[0]);i++)CHECK(!outdoor_lease_parse(raw_bad[i],1000,&l));
 CHECK(outdoor_lease_parse("v2 123 2500 0 1",1000,&l)&&l.raw&&l.target==0);
 int spans[]={30000,60000,180000,600000};
 for(unsigned i=0;i<sizeof(spans)/sizeof(spans[0]);i++){
  memset(&s,0,sizeof(s));l=(struct outdoor_lease){.pid=123,.active=1,.raw=1,.target=0,.until=2500};
  CHECK(outdoor_node_step(&s,&l,2047,max,0,1000)==-1);CHECK(outdoor_node_step(&s,&l,2047,max,0,1500)==max);
  for(int64_t now=2000;now<1500+spans[i];now+=500){char raw[80];snprintf(raw,sizeof(raw),"v2 123 %lld 0 1",(long long)(now+1500));CHECK(outdoor_lease_parse(raw,now,&l));CHECK(outdoor_node_step(&s,&l,max,max,0,now)==-1&&s.held&&!s.inhibited);}
  l.active=0;CHECK(outdoor_node_step(&s,&l,max,max,0,1500+spans[i])==2047&&!s.held);
 }
 memset(&s,0,sizeof(s));CHECK(outdoor_lease_parse("v2 123 2500 3000 1",1000,&l));
 CHECK(outdoor_node_step(&s,&l,2047,max,0,1000)==-1);CHECK(outdoor_node_step(&s,&l,2047,max,0,1500)==3000);
 l.target=2500;CHECK(outdoor_node_step(&s,&l,3000,max,0,2000)==2500&&s.held);
 l.target=1000;CHECK(outdoor_node_step(&s,&l,2500,max,0,2100)==2047&&!s.held);
 CHECK(outdoor_node_step(&s,&l,2047,max,0,2200)==-1);
 memset(&s,0,sizeof(s));l.target=6000;outdoor_node_step(&s,&l,2047,max,0,1000);CHECK(outdoor_node_step(&s,&l,2047,max,0,1500)==max);CHECK(outdoor_node_step(&s,NULL,0,max,0,3000)==-1);
 printf("Outdoor native node: %d cases PASS\n",cases);return 0;
}''',encoding='utf-8')
exe=O/'test.exe';subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-I',str(R/'experimental/refactor_hook/native'),str(C),'-o',str(exe)],check=True)
native_output=subprocess.check_output([str(exe)],text=True,encoding="utf-8");print(native_output,end="")
sources={
'top/rongshangs/lumacurve/refactor/OutdoorOutputAccess.java':'''package top.rongshangs.lumacurve.refactor;class OutdoorOutputAccess{static String name(HookRuntime s){return s.outputStrategy(s.owner);}static String profile(HookRuntime s){return "selected_strategy";}static long physical(HookRuntime s){return 0;}}''',

'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long now=1000;public static long uptimeMillis(){return now;}}',
'android/os/Process.java':'package android.os;public class Process{public static int myPid(){return 123;}}',
'android/system/Os.java':'package android.system;public class Os{public static void chmod(String s,int m){}public static void rename(String a,String b){throw new AssertionError("Host must use test sink");}}',
'top/rongshangs/lumacurve/refactor/hooking/XposedBridge.java':'package top.rongshangs.lumacurve.refactor.hooking;public class XposedBridge{public static void log(String s){}}',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;class HookEntry{static Object get(Object o,String n)throws Exception{java.lang.reflect.Field f=o.getClass().getField(n);return f.get(o);}}''',
'top/rongshangs/lumacurve/refactor/RawPanelOutputHooks.java':'package top.rongshangs.lumacurve.refactor;class RawPanelOutputHooks{static boolean ok=true;static boolean supports(Class<?> c){return ok;}}',
'top/rongshangs/lumacurve/refactor/OutdoorSdrScope.java':'''package top.rongshangs.lumacurve.refactor;class OutdoorSdrScope{static OutdoorSdrScope instance=new OutdoorSdrScope();java.lang.reflect.Method privacy;boolean allow=true;static OutdoorSdrScope resolve(Class<?> c){return instance;}boolean allows(Object o){return allow;}}''',
'top/rongshangs/lumacurve/refactor/HookRuntime.java':r'''package top.rongshangs.lumacurve.refactor;
class HookRuntime {
 boolean closed,user=true,on=true,hdr,thread=true;String phase="active",revision="revision";final Kernel kernel=new Kernel();final Outdoor outdoor=new Outdoor();final Control brightnessControl=new Control();final Power power=new Power();final Handler handler=new Handler();final Owner owner=new Owner();int thermalSeverity,batteryLevel=90;float batteryTemperature=30,thermalCeiling=43;String strategy="AutomaticBrightnessStrategy";
 boolean appliesToUser(){return user;}boolean onDisplayThread(){return thread;}boolean screenOn(){return on;}Boolean hdrActive(){return hdr;}String outputStrategy(Object o){return strategy;}
 public static class Kernel{public float max=1200;String name(){return "physical_mapping";}}
 public static class Power{boolean interactive=true,save;boolean isInteractive(){return interactive;}boolean isPowerSaveMode(){return save;}}
 public static class Handler{final java.util.Map<Runnable,Long> tasks=new java.util.IdentityHashMap<>();boolean post(Runnable r){tasks.put(r,android.os.SystemClock.now);return true;}boolean postDelayed(Runnable r,long t){tasks.put(r,android.os.SystemClock.now+t);return true;}void removeCallbacks(Runnable r){tasks.remove(r);}}
 public static class Owner{public Dpc mDisplayPowerController=new Dpc();public float mBatteryThermalLimitBrightness=1;}
 public static class Dpc{public Clamp mBrightnessClamperController=new Clamp();public Screen screen=new Screen();public Screen getDisplayPowerState(){return screen;}}
 public static class Clamp{public float cap=1;public float getMaxBrightness(){return cap;}}
 public static class Screen{public float value=1,sdr=1;public float getScreenBrightness(){return value;}public float getSdrScreenBrightness(){return sdr;}}
 public static class Control{String owner="none";boolean automatic=true,unlocked=true;boolean auto(){return automatic;}boolean unlocked(){return unlocked;}}
 public static class Outdoor{OutdoorOptions options=new OutdoorOptions();OutdoorPolicy policy=new OutdoorPolicy();Access access=new Access();String blocked;float lux=50000;Outdoor(){options.nodeBoost=true;options.flags[0]=options.flags[2]=options.flags[3]=true;options.values[5]=1;policy.active=true;policy.started=1000;}boolean rangeSupported(){return true;}String block(){return blocked;}float lux(){return lux;}}
 public static class Access{float max(){return 1;}}
}
''',
'top/rongshangs/lumacurve/refactor/NodeBridgeTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import java.util.concurrent.*;
public class NodeBridgeTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args)throws Exception{
  List<String> writes=new ArrayList<>();HookRuntime s=new HookRuntime();OutdoorNodeBridge b=new OutdoorNodeBridge(s,Runnable::run,writes::add);b.frame=1;
  check(b.allowed());b.evaluate();check(b.requested&&writes.size()==1&&writes.get(0).startsWith("v2 ")&&writes.get(0).endsWith("0 1\n"));
  s.owner.mDisplayPowerController.screen.value=.9f;check(!b.allowed());b.evaluate();check(!b.requested&&writes.get(1).endsWith("0 0\n")&&!s.handler.tasks.containsKey(b.check));s.owner.mDisplayPowerController.screen.value=1;
  for(int i=0;i<15;i++){
   s=new HookRuntime();b=new OutdoorNodeBridge(s,Runnable::run,writes::add);b.frame=1;
   switch(i){case 0:s.on=false;break;case 1:s.hdr=true;break;case 2:s.user=false;break;case 3:s.thermalSeverity=1;break;case 4:s.batteryTemperature=44;break;case 5:s.batteryTemperature=Float.NaN;break;case 6:s.batteryLevel=29;break;case 7:s.brightnessControl.owner="raw_panel";break;case 8:s.brightnessControl.automatic=false;break;case 9:s.brightnessControl.unlocked=false;break;case 10:s.owner.mDisplayPowerController.mBrightnessClamperController.cap=.99f;break;case 11:s.owner.mBatteryThermalLimitBrightness=.9f;break;case 12:s.outdoor.options.values[10]=2000;break;case 13:s.outdoor.lux=20000;break;case 14:s.strategy="TemporaryBrightnessStrategy";}
   check(!b.allowed());int count=writes.size();b.evaluate();check(writes.size()==count&&!b.requested);
  }
  s=new HookRuntime();b=new OutdoorNodeBridge(s,Runnable::run,writes::add);b.frame=1;OutdoorSdrScope.instance.privacy=Object.class.getMethod("toString");OutdoorSdrScope.instance.allow=false;check(!b.allowed());OutdoorSdrScope.instance.privacy=null;OutdoorSdrScope.instance.allow=true;
  List<Runnable> tasks=new ArrayList<>();writes.clear();b=new OutdoorNodeBridge(s,tasks::add,writes::add);b.send(true,1000);b.send(false,0);check(tasks.size()==1);tasks.remove(0).run();check(writes.size()==1&&writes.get(0).endsWith("0 0\n"));
  b=new OutdoorNodeBridge(s,Runnable::run,v->{throw new java.io.IOException("test");});b.frame=1;b.evaluate();check(b.failed);b.evaluate();check(!b.requested&&!s.handler.tasks.containsKey(b.check));
  for(int max:new int[]{11,4095,16383,65535})check(OutdoorNodeProfile.matches("physical_mapping",1200,max,true,true));
  check(!OutdoorNodeProfile.matches("unknown",1200,4095,true,true));check(!OutdoorNodeProfile.matches("refactor",1200,65536,true,true));check(!OutdoorNodeProfile.matches("refactor",1200,4095,false,true));

  for(double strength:new double[]{.1,.5,.85,1}){s=new HookRuntime();s.outdoor.options.values[5]=strength;List<String> actual=new ArrayList<>();b=new OutdoorNodeBridge(s,Runnable::run,actual::add);b.frame=1;b.evaluate();check(actual.get(0).endsWith("0 1\n"));s.outdoor.options.nodeTarget=3000;b.evaluate();check(actual.get(1).endsWith("3000 1\n"));b.close();}
  for(long duration:new long[]{30000,60000,180000,600000}){
   s=new HookRuntime();s.outdoor.options.values[6]=duration;s.outdoor.policy.reset();s.outdoor.policy.step(s.outdoor.options,50000,0,null);
   android.os.SystemClock.now=3000;check(s.outdoor.policy.step(s.outdoor.options,50000,3000,null));
   List<String> actual=new ArrayList<>();b=new OutdoorNodeBridge(s,Runnable::run,actual::add);b.frame=1;b.evaluate();check(b.requested);
   android.os.SystemClock.now=3000+duration-1;check(s.outdoor.policy.step(s.outdoor.options,50000,android.os.SystemClock.now,null));b.evaluate();check(b.requested&&actual.get(actual.size()-1).endsWith("0 1\n"));
   android.os.SystemClock.now=3000+duration;check(!s.outdoor.policy.step(s.outdoor.options,50000,android.os.SystemClock.now,null));b.evaluate();check(!b.requested&&actual.get(actual.size()-1).endsWith("0 0\n"));check(!s.handler.tasks.containsKey(b.check));
  }
  System.out.println("Outdoor Java bridge: "+cases+" cases PASS; actual bridge, Android not tested");
 }
}'''}
paths=[]
for name,text in sources.items():p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');paths.append(p)
classes=O/'classes';classes.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(p) for p in paths],*[str(S/(n+'.java')) for n in ['OutdoorNodeBridge','OutdoorNodeProfile','OutdoorNodeTarget','OutdoorOptions','OutdoorPolicy','AdvancedPolicy']]],check=True)
java_output=subprocess.check_output(['java','-cp',str(classes)+';'+str(J),'top.rongshangs.lumacurve.refactor.NodeBridgeTest'],text=True,encoding='utf-8');print(java_output,end='')

total=sum(int(re.search(r'(\d+) cases PASS',out).group(1)) for out in [native_output,java_output]);print(f'Outdoor node compatibility: {total} cases PASS; native policy and Java bridge, device not tested')
