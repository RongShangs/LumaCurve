package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** Bounded extra confirmation for a main-only rise. Missing evidence always releases. */
final class LowLightAssistGate {
 boolean enabled;long waitMs=6000;float stableRatio=.2f;
 long started=-1,coolUntil;float assistAnchor;String reason="off";long holds,releases;
 void configure(JSONObject j){enabled=MemoryOptions.flag(j,"low_light_assist_gate",false);waitMs=(long)MemoryOptions.number(j,"low_light_assist_wait",6000,1000,10000,true);stableRatio=(float)MemoryOptions.number(j,"low_light_assist_tolerance",.2,.1,.5,false);reset();}
 void put(JSONObject j)throws JSONException {j.put("low_light_assist_gate",enabled).put("low_light_assist_wait",waitMs).put("low_light_assist_tolerance",stableRatio);}
 void reset(){started=-1;coolUntil=0;reason="off";}
 void release(String why){if(started>=0)releases++;started=-1;reason=why;}
 long deadline(long now,long nativeDeadline,boolean eligible,float confirmed,float candidate,float threshold,boolean valid,float assist,float min,float max,long oldest,long newest){
  if(!enabled||!eligible){release("inactive");return nativeDeadline;}
  if(!Float.isFinite(confirmed)||confirmed<0||!Float.isFinite(candidate)||!Float.isFinite(threshold)||threshold<confirmed||candidate<=threshold){release("no_main_rise");return nativeDeadline;}
  if(candidate-confirmed>=Math.max(30,confirmed*1.5f)){release("large_main_rise");return nativeDeadline;}
  // Fresh, valid zero-lux history is evidence of darkness, not a missing sensor.
  // Keep the same finite wait and release on stale/invalid or changing evidence.
  if(!valid||!Float.isFinite(assist)||assist<0||!Float.isFinite(min)||!Float.isFinite(max)||min<0||max<min||oldest<0||newest<oldest||newest>now||now-newest>5000||newest-oldest<1000){
   release("assist_unreliable");return nativeDeadline;
  }
  if(max-min>Math.max(2,assist*stableRatio)){release("assist_changed");coolUntil=now+20000;return nativeDeadline;}
  if(started>=0&&Math.abs(assist-assistAnchor)>Math.max(2,assistAnchor*stableRatio)){release("assist_changed");coolUntil=now+20000;return nativeDeadline;}
  if(now<coolUntil){reason="released_cooldown";return nativeDeadline;}
  if(started<0){started=now;assistAnchor=assist;}
  if(now<started||now-started>=waitMs){release("wait_complete");coolUntil=now+20000;return nativeDeadline;}
  reason="confirming_main_only";holds++;
  // This is a finite handover wait, not an extension of the native evidence buffer.
  return Math.max(nativeDeadline,started+waitMs);
 }
}
