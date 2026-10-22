"""Exercise production outdoor provenance, root binding and native fold protocol; no device writes."""
from pathlib import Path
import ast, os, subprocess, re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/outdoor-fold-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
base='top/rongshangs/lumacurve/refactor/'
fixtures=ast.literal_eval(next(n.value for n in ast.parse((R/'tests/refactor_outdoor_node.py').read_text(encoding='utf-8')).body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
sources={k:v for k,v in fixtures.items() if k.startswith('android/') or k.endswith('/hooking/XposedBridge.java')}
sources[base+'HookEntry.java']='''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;class HookEntry{static Field field(Class<?> c,String n)throws Exception{for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(n);}static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}}'''
sources[base+'OutdoorSdrScope.java']=fixtures[base+'OutdoorSdrScope.java']
sources['android/system/Os.java']='package android.system;public class Os{public static void chmod(String p,int m){}public static void rename(String a,String b){}public static void kill(int pid,int signal){throw new AssertionError("no process actions");}}'
sources['com/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2.java']='''package com.android.server.display.brightness.strategy;public class AutomaticBrightnessStrategy2{public int mDisplayId;public Object mAutomaticBrightnessController;public boolean enabled=true,use=true,applied=true;public boolean isAutoBrightnessEnabled(){return enabled;}public boolean shouldUseAutoBrightness(){return use;}public boolean hasAppliedAutoBrightness(){return applied;}}'''
sources['com/android/server/display/brightness/BrightnessReason.java']='''package com.android.server.display.brightness;public class BrightnessReason{public static final int REASON_AUTOMATIC=37,REASON_MANUAL=41,REASON_OVERRIDE=43,REASON_DOZE=47,REASON_BOOST=53;public static final int MODIFIER_BCBC=1,MODIFIER_OPR=2,MODIFIER_PEAK=4,MODIFIER_THERMAL=8,MODIFIER_HDR=16,MODIFIER_DIMMED=32,MODIFIER_LOW_POWER=64,MODIFIER_THROTTLED=128;public int reason=37,modifier;public int getReason(){return reason;}public int getModifier(){return modifier;}}'''
runtime=fixtures[base+'HookRuntime.java']
runtime=runtime.replace('final Owner owner=new Owner();','Owner owner=new Owner();')
runtime=runtime.replace('public Dpc mDisplayPowerController=new Dpc();','public Object mDisplayPowerController=new Dpc();public Impl mAutomaticBrightnessControllerImpl=new Impl();')
start=runtime.index(' public static class Dpc{');end=runtime.index(' public static class Clamp',start)
runtime=runtime[:start]+''' public static class Impl{public Object mAutomaticBrightnessController;}
 public static class Dpc{public Object mAutomaticBrightnessController;public String mUniqueDisplayId="local:4630947024259405956";public com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2 mAutomaticBrightnessStrategy=new com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2();public com.android.server.display.brightness.BrightnessReason mBrightnessReason=new com.android.server.display.brightness.BrightnessReason(),mBrightnessReasonTemp=new com.android.server.display.brightness.BrightnessReason();public Clamp mBrightnessClamperController=new Clamp();public Screen screen=new Screen();public Screen getDisplayPowerState(){return screen;}}
 public static class AutomaticBrightnessStrategy{} public static class TemporaryBrightnessStrategy{}
 public static class Preferred extends Dpc{public Object selected=new AutomaticBrightnessStrategy();public Object getLastSelectedStrategy(){return selected;}}
 public static class InheritedPreferred extends Preferred{}
 public static class BrokenPreferred extends Dpc{public int getLastSelectedStrategy(){return 1;}}
 public static class ThrowingPreferred extends Dpc{public Object getLastSelectedStrategy(){throw new IllegalStateException("OEM call failed");}}
''' +runtime[end:]
sources[base+'HookRuntime.java']=runtime
sources[base+'ActivePanelNode.java']='''package top.rongshangs.lumacurve.refactor;import org.json.*;class ActivePanelNode{static final String PROFILE="fold_default_display";static JSONObject selected;static JSONObject discover(){return new JSONObject(selected.toString());}}'''
sources[base+'NativePanelRoot.java']='''package top.rongshangs.lumacurve.refactor;import java.io.*;import org.json.*;class NativePanelRoot{static final File BIN=new File("/unused/native");static int probes;static void probe(JSONObject node){probes++;}static void install(){throw new AssertionError("read-only tests must not install");}}'''
sources[base+'RootControl.java']='''package top.rongshangs.lumacurve.refactor;import java.io.*;class RootControl{static final File DATA=new File("build/outdoor-fold-tests/no-health");}'''
sources[base+'RawPanelOutputHooks.java']=fixtures[base+'RawPanelOutputHooks.java']
sources[base+'OutdoorFoldTest.java']=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import com.android.server.display.brightness.*;
public class OutdoorFoldTest{
 static int cases;static void check(boolean v){if(!v)throw new AssertionError("case "+cases);cases++;}
 static HookRuntime runtime(){HookRuntime s=new HookRuntime();HookRuntime.Dpc d=(HookRuntime.Dpc)s.owner.mDisplayPowerController;Object abc=new Object();d.mAutomaticBrightnessController=abc;d.mAutomaticBrightnessStrategy.mAutomaticBrightnessController=abc;s.owner.mAutomaticBrightnessControllerImpl.mAutomaticBrightnessController=abc;return s;}
 static HookRuntime.Dpc dpc(HookRuntime s){return (HookRuntime.Dpc)s.owner.mDisplayPowerController;}
 static OutdoorNodeBridge bridge(HookRuntime s,List<String> writes){OutdoorNodeBridge b=new OutdoorNodeBridge(s,Runnable::run,writes::add);b.frame=1;return b;}
 static JSONObject node(long physical,int port){return new JSONObject().put("supported",true).put("discovery","fold_default_display").put("physical_display_id","local:"+physical).put("fold_port",port).put("maximum",4095).put("path","/sys/class/backlight/panel"+port+"-backlight/brightness").put("canonical_path","/sys/devices/controller/backlight/panel"+port+"-backlight/brightness").put("fold_physical_ids",new JSONArray().put(4630947024259405955L).put(4630947024259405956L));}
 static JSONObject state(long id){return new JSONObject().put("outdoor_node_supported",true).put("outdoor_node_physical_id",id).put("curve_backend","physical_mapping").put("max_logical_nit",2600).put("manual_panel_supported",true).put("outdoor_range_supported",true).put("outdoor",new JSONObject().put("device_mapping_max",1).put("sdr_compatibility_profile","legacy_opr"));}
 public static void main(String[] args)throws Exception{
  HookRuntime s=runtime();List<String> writes=new ArrayList<>();OutdoorNodeBridge b=bridge(s,writes);
  check(OutdoorNodeBridge.interfaces(s,true));check(OutdoorOutputAccess.profile(s).equals("automatic_strategy2_reason"));check(b.allowed());b.evaluate();check(writes.get(0).startsWith("v3 ")&&writes.get(0).endsWith("0 1 4630947024259405956\n"));
  for(int modifier:new int[]{1,2,4,3,7}){dpc(s).mBrightnessReason.modifier=modifier;dpc(s).mBrightnessReasonTemp.modifier=modifier;check(b.allowed());}
  for(int modifier:new int[]{8,16,32,64,128,256,1024,-1}){dpc(s).mBrightnessReason.modifier=modifier;check(!b.allowed());dpc(s).mBrightnessReason.modifier=0;dpc(s).mBrightnessReasonTemp.modifier=modifier;check(!b.allowed());}dpc(s).mBrightnessReasonTemp.modifier=0;
  for(int reason:new int[]{0,41,43,47,53,99}){dpc(s).mBrightnessReason.reason=reason;check(!b.allowed());dpc(s).mBrightnessReason.reason=37;dpc(s).mBrightnessReasonTemp.reason=reason;check(!b.allowed());}dpc(s).mBrightnessReasonTemp.reason=37;
  for(int gate=0;gate<3;gate++){s=runtime();if(gate==0)dpc(s).mAutomaticBrightnessStrategy.enabled=false;if(gate==1)dpc(s).mAutomaticBrightnessStrategy.use=false;if(gate==2)dpc(s).mAutomaticBrightnessStrategy.applied=false;check(OutdoorNodeBridge.interfaces(s,true));check(!bridge(s,writes).allowed());}
  s=runtime();dpc(s).mAutomaticBrightnessStrategy.mDisplayId=1;try{OutdoorOutputAccess.read(s.owner);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}
  s=runtime();dpc(s).mAutomaticBrightnessStrategy.mAutomaticBrightnessController=new Object();check(OutdoorOutputAccess.name(s)==null);
  s=runtime();HookRuntime.Preferred preferred=new HookRuntime.Preferred();s.owner.mDisplayPowerController=preferred;check(OutdoorOutputAccess.profile(s).equals("selected_strategy"));check(bridge(s,writes).allowed());preferred.selected=new HookRuntime.TemporaryBrightnessStrategy();check(!bridge(s,writes).allowed());preferred.selected=null;check(!bridge(s,writes).allowed());
  s.owner.mDisplayPowerController=new HookRuntime.BrokenPreferred();try{OutdoorOutputAccess.read(s.owner);throw new AssertionError();}catch(NoSuchMethodException expected){cases++;}
  s.owner.mDisplayPowerController=new HookRuntime.ThrowingPreferred();check(OutdoorOutputAccess.profile(s).equals("selected_strategy"));check(OutdoorOutputAccess.name(s)==null);
  s.owner.mDisplayPowerController=new HookRuntime.InheritedPreferred();check(OutdoorOutputAccess.profile(s).equals("selected_strategy"));check(bridge(s,writes).allowed());
  for(String id:new String[]{"", "network:123","local:-1","local:0","local:9223372036854775808"}){s=runtime();dpc(s).mUniqueDisplayId=id;check(OutdoorOutputAccess.physical(s)==0);}
  long inner=4630947024259405955L,outer=4630947024259405956L;
  for(long id:new long[]{inner,outer,inner,outer}){ActivePanelNode.selected=node(id,id==inner?0:1);JSONObject state=state(id);check(OutdoorNodeRoot.supported(state));OutdoorNodeRoot.annotate(state);check(state.getInt("outdoor_node_maximum")==4095&&state.getString("outdoor_node_device_profile").equals("fold_bound_node"));check(OutdoorNodeRoot.command(ActivePanelNode.selected).size()==5);check(OutdoorNodeRoot.command(ActivePanelNode.selected).get(4).equals("--fold-ids="+inner+","+outer));check(!OutdoorNodeRoot.supported(state(id==inner?outer:inner)));}
  ActivePanelNode.selected=node(outer,1);ActivePanelNode.selected.put("supported",false);check(!OutdoorNodeRoot.supported(state(outer)));
  for(JSONArray ids:new JSONArray[]{new JSONArray(),new JSONArray().put(inner),new JSONArray().put(inner).put(inner),new JSONArray().put(-1).put(outer)}){JSONObject n=node(outer,1).put("fold_physical_ids",ids);try{OutdoorNodeRoot.foldIds(n);throw new AssertionError();}catch(Exception expected){cases++;}}
  ActivePanelNode.selected=new JSONObject().put("supported",true).put("maximum",16383).put("path","/sys/class/backlight/panel0-backlight/brightness");check(OutdoorNodeRoot.supported(state(0)));check(OutdoorNodeRoot.command(ActivePanelNode.selected).size()==4);check(OutdoorNodeRoot.foldIds(ActivePanelNode.selected).isEmpty());
  System.out.println("Outdoor provenance and root binding: "+cases+" cases PASS; production adapters, no device writes");
 }
}'''
paths=[]
for name,code in sources.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
production=['OutdoorOutputAccess','OutdoorNodeBridge','OutdoorNodeRoot','OutdoorNodeProfile','OutdoorNodeTarget','OutdoorOptions','OutdoorPolicy','AdvancedPolicy']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*map(str,paths),*[str(S/(n+'.java')) for n in production]],check=True)
result=subprocess.check_output(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.OutdoorFoldTest'],text=True,encoding='utf-8');print(result,end='');total=int(re.search(r'(\d+) cases PASS',result).group(1))
for mode in ['wrong_reason_return','static_enabled','wrong_display_type','aliased_modifier','missing_applied']:
 variants=dict(sources)
 reason='com/android/server/display/brightness/BrightnessReason.java';strategy='com/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2.java'
 if mode=='wrong_reason_return':variants[reason]=variants[reason].replace('int getReason()','long getReason()')
 if mode=='static_enabled':variants[strategy]=variants[strategy].replace('public boolean isAutoBrightnessEnabled(){return enabled;}','public static boolean isAutoBrightnessEnabled(){return true;}')
 if mode=='wrong_display_type':variants[strategy]=variants[strategy].replace('int mDisplayId','long mDisplayId')
 if mode=='aliased_modifier':variants[reason]=variants[reason].replace('MODIFIER_THERMAL=8','MODIFIER_THERMAL=1')
 if mode=='missing_applied':variants[strategy]=variants[strategy].replace('hasAppliedAutoBrightness()','unknownApplied()')
 driver=variants[base+'OutdoorFoldTest.java'];pos=driver.index(' public static void main(')
 variants[base+'OutdoorFoldTest.java']=driver[:pos]+''' public static void main(String[] args)throws Exception{HookRuntime s=runtime();try{OutdoorOutputAccess.read(s.owner);throw new AssertionError("Malformed older ABI accepted");}catch(ReflectiveOperationException expected){cases++;}s.owner.mDisplayPowerController=new HookRuntime.Preferred();check(OutdoorOutputAccess.profile(s).equals("selected_strategy"));check(OutdoorOutputAccess.read(s.owner).automatic());System.out.println("Rejected partial ABI: "+cases+" cases PASS");}}'''
 folder=O/mode;paths=[]
 for name,code in variants.items():
  p=folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
 classes=folder/'classes';classes.mkdir(exist_ok=True)
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(classes),*map(str,paths),*[str(S/(n+'.java')) for n in production]],check=True)
 result=subprocess.check_output(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.OutdoorFoldTest'],text=True,encoding='utf-8');print(mode+': '+result.strip());total+=int(re.search(r'(\d+) cases PASS',result).group(1))
c=O/'fold.c';c.write_text(r'''
#include "outdoor_node_policy.h"
static int cases;
#define CHECK(v) do{if(!(v)){fprintf(stderr,"case %d\n",cases);return 1;}cases++;}while(0)
int main(void){
 struct outdoor_fold_ids ids;struct outdoor_lease l;
 CHECK(outdoor_fold_ids_parse("4630947024259405955,4630947024259405956",&ids));
 CHECK(outdoor_lease_parse("v3 123 2500 0 1 4630947024259405956",1000,&l));CHECK(l.raw&&l.target==0&&l.physical==4630947024259405956ULL);CHECK(outdoor_fold_authorized(&ids,&l));
 CHECK(outdoor_fold_decide(1,0,1,&l,1)==2);CHECK(outdoor_fold_decide(1,l.physical,1,&l,1)==1);CHECK(outdoor_fold_decide(0,4630947024259405955ULL,1,&l,1)==2);
 CHECK(outdoor_fold_decide(0,l.physical,1,&l,1)==0);CHECK(outdoor_fold_decide(1,4630947024259405955ULL,1,&l,1)==0);CHECK(outdoor_fold_decide(1,l.physical,-1,&l,1)==0);
 CHECK(outdoor_fold_decide(1,l.physical,1,NULL,0)==1);CHECK(outdoor_fold_decide(1,l.physical,0,NULL,0)==0);
 for(int port=-1;port<=8;port++)for(int live=0;live<2;live++)for(int active=0;active<2;active++){l.active=active;int decision=outdoor_fold_decide(1,l.physical,port,&l,live);CHECK(port==1?decision==1:decision==0);}
 const char *bad[]={"","1","1,1","-1,2","1,0","1,2,","1,9223372036854775808","1,2,3,4,5,6,7,8,9","1,2 garbage"};
 for(unsigned i=0;i<sizeof(bad)/sizeof(*bad);i++)CHECK(!outdoor_fold_ids_parse(bad[i],&ids));
 const char *leases[]={"v3 123 2500 0 1 0","v3 123 2500 0 1 -1","v3 123 2500 0 1 9223372036854775808","v3 123 2500 0 1 1 extra","v3 123 2500 0 1","v3 123 3000 0 1 1"};
 for(unsigned i=0;i<sizeof(leases)/sizeof(*leases);i++)CHECK(!outdoor_lease_parse(leases[i],1000,&l));
 CHECK(outdoor_fold_ids_parse("1,2",&ids));CHECK(outdoor_lease_parse("v3 123 2500 0 1 3",1000,&l));CHECK(!outdoor_fold_authorized(&ids,&l));
 CHECK(outdoor_lease_parse("v2 123 2500 0 1",1000,&l));CHECK(l.physical==0&&!outdoor_fold_authorized(&ids,&l));CHECK(outdoor_lease_parse("v1 123 2500 1000 1",1000,&l));CHECK(!outdoor_fold_authorized(&ids,&l));
 printf("Outdoor fold native protocol: %d cases PASS; no kernel writes\n",cases);return 0;
}''',encoding='utf-8')
exe=O/'fold.exe';subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-I',str(R/'experimental/refactor_hook/native'),str(c),'-o',str(exe)],check=True)
result=subprocess.check_output([str(exe)],text=True,encoding='utf-8');print(result,end='');total+=int(re.search(r'(\d+) cases PASS',result).group(1))
# Execute the production rebinding block with a panel that lights at zero first.
writer=(R/'experimental/refactor_hook/native/outdoor_node.h').read_text(encoding='utf-8')
binding=writer[writer.index('   if(decision==2){'):writer.index('\n  }\n  int manualLock')]
transition=O/'transition.c';transition.write_text(r'''
#include "outdoor_node_policy.h"
static int active_fold_port=0,maximum=4095,selection_fails;
static uint64_t outdoor_physical=4630947024259405955ULL;
static struct outdoor_node_state state;
static int outdoor_fold_select(int port){if(selection_fails)return -1;active_fold_port=port;return 4095;}
static void outdoor_health(const struct outdoor_node_state *s,const char *reason){(void)s;(void)reason;}
static int transition(int port,const struct outdoor_lease *requested){
 struct outdoor_lease lease=*requested;
 int decision=outdoor_fold_decide(active_fold_port,outdoor_physical,port,&lease,1);
''' +binding+r'''
 return decision;
wait_events:return 0;
}
static int cases;
#define CHECK(v) do{if(!(v)){fprintf(stderr,"transition case %d\n",cases);return 1;}cases++;}while(0)
int main(void){
 struct outdoor_lease lease={.pid=123,.active=1,.raw=1,.target=0,.until=5000,.physical=4630947024259405956ULL};
 state.held=1;state.base=1000;state.target=4095;
 CHECK(transition(1,&lease)==2);CHECK(active_fold_port==1&&outdoor_physical==lease.physical&&!state.held);
 CHECK(outdoor_node_step(&state,&lease,0,maximum,0,1000)==-1&&!state.held);
 CHECK(transition(1,&lease)==1);CHECK(outdoor_node_step(&state,&lease,2047,maximum,0,2000)==-1);
 CHECK(outdoor_node_step(&state,&lease,2047,maximum,0,2499)==-1);
 CHECK(outdoor_node_step(&state,&lease,2047,maximum,0,2500)==4095&&state.held);
 lease.physical=4630947024259405955ULL;selection_fails=1;
 CHECK(transition(0,&lease)==0&&!state.held);CHECK(active_fold_port==1&&outdoor_physical!=lease.physical);
 selection_fails=0;CHECK(transition(0,&lease)==2);CHECK(active_fold_port==0&&outdoor_physical==lease.physical&&maximum==4095);
 printf("Outdoor production fold transition: %d cases PASS; zero-brightness handoff and retry\n",cases);return 0;
}''',encoding='utf-8')
transition_exe=O/'transition.exe';subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-I',str(R/'experimental/refactor_hook/native'),str(transition),'-o',str(transition_exe)],check=True)
result=subprocess.check_output([str(transition_exe)],text=True,encoding='utf-8');print(result,end='');total+=int(re.search(r'(\d+) cases PASS',result).group(1))
private=R/'build/beta-4.0.2-beta04/disassembly'
if private.is_dir():
 def read(name):return (private/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
 dpc=read('DisplayPowerController');strategy=read('brightness.strategy.AutomaticBrightnessStrategy2');reason=read('brightness.BrightnessReason')
 checks=[('METHOD getLastSelectedStrategy ' not in dpc,'preferred accessor absent'),('FIELD mAutomaticBrightnessStrategy Lcom/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2;' in dpc,'actual separate automatic object')]
 for name in ['isAutoBrightnessEnabled','shouldUseAutoBrightness','hasAppliedAutoBrightness']:checks.append(('METHOD '+name+' ()Z' in strategy,name))
 for name in ['mAutomaticBrightnessController','mBrightnessReason','mBrightnessReasonTemp']:checks.append(('FIELD '+name+' ' in dpc,name))
 for name in ['getReason','getModifier']:checks.append(('METHOD '+name+' ()I' in reason,name))
 for name in ['REASON_AUTOMATIC','REASON_MANUAL','REASON_OVERRIDE','REASON_DOZE','REASON_BOOST','MODIFIER_BCBC','MODIFIER_OPR','MODIFIER_PEAK','MODIFIER_THERMAL','MODIFIER_HDR','MODIFIER_DIMMED','MODIFIER_LOW_POWER','MODIFIER_THROTTLED']:checks.append(('FIELD '+name+' I' in reason,name))
 controller=read('brightness.DisplayBrightnessController');clamper=read('brightness.clamper.BrightnessClamperController')
 checks.append(('->getAutomaticBrightnessStrategy()Lcom/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2;' in dpc,'same native automatic strategy'))
 checks.append(('->setAutomaticBrightnessController(Lcom/android/server/display/AutomaticBrightnessController;)V' in controller,'native ABC handoff'))
 checks.append(('METHOD getMaxBrightness ()F' in clamper,'actual native clamper accessor'))
 for valid,detail in checks:assert valid,detail
 print(f'Outdoor fold firmware: {len(checks)} checks PASS; actual private classes, no device execution')
else:print('Outdoor fold private firmware unavailable; static device check not performed')
print(f'Outdoor fold compatibility: {total} cases PASS; production adapters/protocol, target device pending')
