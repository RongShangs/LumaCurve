"""Inject installation failures into actual production transactions and outdoor hooks.
No Android services or physical brightness measurements are simulated as device evidence.
"""
from pathlib import Path
import ast, os, re, subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/hook-transaction-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'

def run(name, sources, production, main, argument=None):
    out=O/name;out.mkdir(exist_ok=True);paths=[]
    for path,code in sources.items():
        file=out/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(code,encoding='utf-8');paths.append(file)
    classes=out/'classes';classes.mkdir(exist_ok=True)
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(classes),*map(str,paths),*[str(S/p) for p in production]],check=True)
    result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),main,*([argument] if argument else [])],check=True,capture_output=True,text=True)
    print(result.stdout,end='');return int(re.search(r'(\d+) cases PASS',result.stdout).group(1))

test=r'''package top.rongshangs.lumacurve.refactor.hooking;
import java.util.*;
public class TransactionTest {
 static int cases;static void check(boolean value){if(!value)throw new AssertionError(cases);cases++;}
 public static void main(String[] ignored){
  check(XposedBridge.initialize(new XposedBridge.Backend(){
   public XC_MethodHook.Unhook hook(java.lang.reflect.Method m,XC_MethodHook h){throw new AssertionError("no install expected");}
   public Object original(java.lang.reflect.Method m,Object receiver,Object[] args){throw new AssertionError("no invocation expected");}
   public void log(String text){throw new IllegalStateException("logger unavailable");}
  }));
  List<Integer> order=new ArrayList<>();
  try(HookTransaction tx=new HookTransaction()){
   tx.add(new XC_MethodHook.Unhook(()->order.add(1)));
   tx.add(new XC_MethodHook.Unhook(()->{order.add(2);throw new IllegalStateException("rollback failure");}));
   tx.add(new XC_MethodHook.Unhook(()->order.add(3)));
   throw new IllegalArgumentException("installation failure");
  }catch(IllegalArgumentException expected){check(expected.getMessage().equals("installation failure"));}
  check(order.equals(Arrays.asList(3,2,1)));
  HookTransaction tx=new HookTransaction();tx.add(new XC_MethodHook.Unhook(()->order.add(4)));tx.commit();tx.close();check(order.size()==3);
  try{tx.add(new XC_MethodHook.Unhook(()->{}));throw new AssertionError();}catch(IllegalStateException expected){cases++;}
  List<XC_MethodHook.Unhook> parent=new ArrayList<>();
  try(HookTransaction child=new HookTransaction()){child.add(new XC_MethodHook.Unhook(()->order.add(5)));child.commitTo(parent);}
  check(parent.size()==1&&order.size()==3);parent.get(0).unhook();parent.get(0).unhook();check(order.equals(Arrays.asList(3,2,1,5)));
  tx=new HookTransaction();tx.add(new XC_MethodHook.Unhook(()->order.add(6)));tx.close();tx.close();check(order.equals(Arrays.asList(3,2,1,5,6)));
  System.out.println("Hook transaction ownership: "+cases+" cases PASS");
 }
}'''
total=run('scope',{'top/rongshangs/lumacurve/refactor/hooking/TransactionTest.java':test},['hooking/XC_MethodHook.java','hooking/XposedBridge.java','hooking/HookTransaction.java'],'top.rongshangs.lumacurve.refactor.hooking.TransactionTest')

tree=ast.parse((R/'tests/refactor_outdoor.py').read_text(encoding='utf-8'))
original=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
sources={k:v for k,v in original.items() if not k.endswith('/OutdoorTest.java')}
key='top/rongshangs/lumacurve/refactor/hooking/XposedBridge.java'
sources[key]=sources[key].replace('static Map<Method,List<XC_MethodHook>> hooks=', 'public static String fail="";public static int calls;public static int count(){int n=0;for(List<XC_MethodHook> v:hooks.values())n+=v.size();return n;} public static int targetCount(){int n=0;for(Method m:hooks.keySet())if(m.getName().equals("getOverrideLimitBrightness"))n+=hooks.get(m).size();return n;} static Map<Method,List<XC_MethodHook>> hooks=')
sources[key]=sources[key].replace('hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);','calls++;if((m.getDeclaringClass().getSimpleName()+"."+m.getName()).equals(fail))throw new IllegalStateException("injected "+fail);hooks.computeIfAbsent(m,k->new ArrayList<>()).add(h);')
sources['top/rongshangs/lumacurve/refactor/InstallFailureTest.java']=r'''package top.rongshangs.lumacurve.refactor;
import top.rongshangs.lumacurve.refactor.hooking.*;import com.android.server.display.*;
public class InstallFailureTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("installation "+cases);cases++;}
 public static class Malformed{public static float value;public static float read(){return 1;}public float instance;public float good(){return 1;}}
 public static void main(String[] args){
  if(args[0].equals("static_abi")){
   try{HbmAccess.typed(Malformed.class,"value",float.class);throw new AssertionError("static field accepted");}catch(Exception expected){cases++;}
   try{HbmAccess.method(Malformed.class,"read",float.class);throw new AssertionError("static getter accepted");}catch(Exception expected){cases++;}
   try{HbmAccess.typed(Malformed.class,"instance",float.class);check(HbmAccess.method(Malformed.class,"good",float.class)!=null);}catch(Exception failure){throw new AssertionError(failure);}
   System.out.println("Instance ownership ABI: "+cases+" cases PASS");return;
  }
  String fail=args[0];boolean required=fail.startsWith("HighBrightnessModeController.")||fail.startsWith("AutomaticBrightnessControllerImpl.");
  Class<?> owner=DisplayPowerControllerImpl.class;
  XposedBridge.fail=fail;OutdoorTuning.install(owner);
  if(required){check(!OutdoorTuning.owners.contains(owner));check(!OutdoorTuning.ranges.contains(HighBrightnessModeController.class));check(OutdoorTuning.targetProfile(owner).equals("unsupported"));check(XposedBridge.count()==0);check(OutdoorTuning.peaks.isEmpty()&&OutdoorTuning.dynamicRanges.isEmpty());}
  else {check(OutdoorTuning.owners.contains(owner));check(OutdoorTuning.ranges.contains(HighBrightnessModeController.class));check(OutdoorTuning.targetProfile(owner).equals("override_target"));check(XposedBridge.targetCount()==1);}
  XposedBridge.fail="";OutdoorTuning.install(owner);
  check(OutdoorTuning.owners.contains(owner));check(OutdoorTuning.ranges.contains(HighBrightnessModeController.class));check(XposedBridge.targetCount()==1);
  int before=XposedBridge.count(),calls=XposedBridge.calls;OutdoorTuning.install(owner);check(XposedBridge.count()==before&&XposedBridge.calls==calls);
  check(OutdoorTuning.targetProfile(owner).equals("override_target"));
  System.out.println("Outdoor install "+fail+": "+cases+" cases PASS");
 }
}'''
production=['hooking/HookTransaction.java']+[name+'.java' for name in ['OutdoorNodeTarget','OutdoorOptions','OutdoorPolicy','HbmAccess','OutdoorController','OutdoorTuning','OutdoorOpr','OutdoorSdrScope','ThermalPolicy','AdvancedPolicy','LiveLimitEvidence','NitBrightnessLimit']]
for method in ['static_abi','AutomaticBrightnessControllerImpl.getOverrideLimitBrightness','HighBrightnessModeController.onAmbientLuxChange','HighBrightnessModeController.getCurrentBrightnessMax','BrightnessRangeControllerImpl.getCurrentBrightnessMax','DisplayPowerControllerImpl.getMaxHbmBrightnessForPeak','DisplayPowerControllerImpl.adjustBrightnessByThermal','DisplayPowerControllerImpl.adjustSdrBrightness']:
    total+=run(method.replace('.','-'),sources,production,'top.rongshangs.lumacurve.refactor.InstallFailureTest',method)
hook=(S/'HookEntry.java').read_text(encoding='utf-8')
ref=hook[hook.index('try (HookTransaction refactorHooks'):hook.index('XC_MethodHook attachHook=')]
assert 'refactorHooks.commitTo(added)' in ref and 'added.add(' not in ref
print(f'Hook installation isolation: {total} cases PASS; production callbacks with injected installation failures, not Android')
