package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
/** Isolates only positive brightness frames on the owned, unlocked main display. */
final class RawPanelOutputHooks {
 static final Set<Class<?>> owners=new HashSet<>(),powerStates=new HashSet<>();
 static boolean supports(Class<?> owner){return owners.contains(owner);}
 static HookRuntime state(Object powerState){
  HookRuntime[] snapshot;
  synchronized(HookEntry.states){snapshot=HookEntry.states.values().toArray(new HookRuntime[0]);}
  for(HookRuntime s:snapshot)try{
   if(!s.onDisplayThread())continue;Object dpc=HookEntry.get(s.owner,"mDisplayPowerController");
   if(dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc)==powerState)return s;
  }catch(Throwable unavailable){}
  return null;
 }
 static void install(Class<?> owner){
  if(owners.contains(owner))return;List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
  try{
   Class<?> power=Class.forName("com.android.server.display.DisplayPowerState",false,owner.getClassLoader());
   Class<?> controller=HookEntry.field(owner,"mDisplayPowerController").getType();
   if(controller.getMethod("getDisplayPowerState").getReturnType()!=power)throw new IllegalStateException("raw display identity ABI");
   if(!powerStates.contains(power)){
    Method brightness=power.getDeclaredMethod("getScreenBrightness");if(brightness.getReturnType()!=float.class)throw new IllegalStateException("raw wake brightness ABI");
    for(String name:new String[]{"setScreenBrightness","setSdrScreenBrightness"}){
     Method m=power.getDeclaredMethod(name,float.class);if(m.getReturnType()!=void.class)throw new IllegalStateException("raw output ABI");
     hooks.add(XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
      HookRuntime s=state(p.thisObject);if(s!=null&&s.brightnessControl.rawOutputBlocked((Float)p.args[0]))try{
       // Leave the first positive frame after a zero/unset brightness to the system.
       float current=(Float)brightness.invoke(p.thisObject);
       if(Float.isFinite(current)&&current>0){s.brightnessControl.rawBlockedWrites++;p.setResult(null);}
      }catch(Throwable unavailable){}
     }}));
    }
    Method off=power.getDeclaredMethod("setScreenState",int.class,int.class);if(off.getReturnType()!=void.class)throw new IllegalStateException("raw screen ABI");
    hooks.add(XposedBridge.hookMethod(off,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
     HookRuntime s=state(p.thisObject);if(s!=null&&((Integer)p.args[0])!=2)s.brightnessControl.screenChanging();
    }}));
    powerStates.add(power);
   }
   owners.add(owner);
  }catch(Throwable unavailable){for(XC_MethodHook.Unhook hook:hooks)hook.unhook();XposedBridge.log("HyperLux raw output isolation unavailable: "+unavailable);}
 }
}
