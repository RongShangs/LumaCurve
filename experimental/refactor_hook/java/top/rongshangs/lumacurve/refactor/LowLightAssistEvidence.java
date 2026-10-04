package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;
/** Read only the OEM assist history. No new sensor listener or lux substitution. */
final class LowLightAssistEvidence {
 static boolean supported(HookRuntime s){try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),dual=HookEntry.get(impl,"mDualSensorPolicy"),ring=HookEntry.get(dual,"mAssistLightSensorRingBuffer");
  for(String name:new String[]{"mAssistAmbientLuxValid","mAssistLightSensorEnable","mIsPendingResetAssistValue"})if(HookEntry.field(dual.getClass(),name).getType()!=boolean.class)return false;
  if(HookEntry.field(dual.getClass(),"mAssistLightSensorEnableTime").getType()!=long.class||HookEntry.field(dual.getClass(),"mAssistLightSensorWarmUpTime").getType()!=int.class)return false;
  return ring.getClass().getMethod("size").getReturnType()==int.class&&ring.getClass().getMethod("getTime",int.class).getReturnType()==long.class&&ring.getClass().getMethod("getLux",int.class).getReturnType()==float.class&&s.sensorName(0)!=null&&ResponseTuning.supported(s.owner);
 }catch(Throwable unavailable){return false;}}
 static long deadline(HookRuntime s,Object abc,Object impl,long now,long nativeDeadline,float threshold){try{
  Object dual=HookEntry.get(impl,"mDualSensorPolicy");boolean eligible=s.lowLightEnabled&&s.normalTuningAllowed(abc,impl)&&s.lowLightApplies(abc,impl,s.mainCandidate(abc,impl))&&"main".equals(s.sensorName(((Number)HookEntry.get(dual,"mUseLightSensorFlag")).intValue()));
  float confirmed=HookRuntime.optionalNumber(abc,"mAmbientLux"),candidate=s.mainCandidate(abc,impl),assist=HookRuntime.optionalNumber(dual,"mAssistFastAmbientLux");eligible&=confirmed<=s.lowLightLimit;
  if(!s.assistGate.enabled||!eligible){s.assistGate.release("inactive");return nativeDeadline;}
  long enabledAt=((Number)HookEntry.get(dual,"mAssistLightSensorEnableTime")).longValue();int warm=((Number)HookEntry.get(dual,"mAssistLightSensorWarmUpTime")).intValue();
  boolean valid=Boolean.TRUE.equals(HookRuntime.optionalBoolean(dual,"mAssistAmbientLuxValid"))&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(dual,"mAssistLightSensorEnable"))&&Boolean.FALSE.equals(HookRuntime.optionalBoolean(dual,"mIsPendingResetAssistValue"))&&enabledAt>=0&&warm>=0&&now>=enabledAt&&now-enabledAt>=warm;
  Object ring=HookEntry.get(dual,"mAssistLightSensorRingBuffer");Method size=ring.getClass().getMethod("size"),time=ring.getClass().getMethod("getTime",int.class),lux=ring.getClass().getMethod("getLux",int.class);int count=((Number)size.invoke(ring)).intValue();
  if(count<2||count>4096)valid=false;long oldest=-1,newest=-1;float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
  if(valid){newest=((Number)time.invoke(ring,count-1)).longValue();for(int i=count-1;i>=Math.max(0,count-128);i--){long at=((Number)time.invoke(ring,i)).longValue();if(at<now-3000)break;float value=((Number)lux.invoke(ring,i)).floatValue();if(!Float.isFinite(value)||at>now||at<0){valid=false;break;}oldest=at;min=Math.min(min,value);max=Math.max(max,value);}}
  return s.assistGate.deadline(now,nativeDeadline,eligible,confirmed,candidate,threshold,valid,assist,min,max,oldest,newest);
 }catch(Throwable unavailable){s.assistGate.release("assist_unreliable");return nativeDeadline;}}
}
