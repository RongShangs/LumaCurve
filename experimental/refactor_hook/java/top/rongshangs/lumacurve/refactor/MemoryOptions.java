package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** Persistence, gesture grouping, and OEM wake reset are separate policies. */
final class MemoryOptions {
 final boolean persist,unlock,sameScene,replaceOnUnlock,resetOverride,timeoutOverride;
 final int days,maxPoints,settleMs,offMinutes,forceMinutes,timeoutMinutes;
 final float sceneRatio,sceneMinimum,resetLux;
 MemoryOptions(boolean persist,int days){this(persist,days,0);}
 MemoryOptions(boolean persist,int days,int maxPoints){this(persist,days,maxPoints,true,true,true,1500,.5f,5,false,6,30,20,false,30);}
 private MemoryOptions(boolean p,int d,int cap,boolean unlock,boolean scene,boolean replace,int settle,float ratio,float minimum,boolean reset,int off,int force,float lux,boolean timeout,int minutes){
  persist=p;days=d;maxPoints=cap;this.unlock=unlock;sameScene=scene;replaceOnUnlock=replace;settleMs=settle;sceneRatio=ratio;sceneMinimum=minimum;resetOverride=reset;offMinutes=off;forceMinutes=force;resetLux=lux;timeoutOverride=timeout;timeoutMinutes=minutes;
  if(d<1||d>90||cap<0||cap>7||settle<500||settle>5000||!Float.isFinite(ratio)||ratio<.1f||ratio>2||!Float.isFinite(minimum)||minimum<1||minimum>30||off<1||off>30||force<off||force>120||!Float.isFinite(lux)||lux<1||lux>200||minutes<1||minutes>120)throw new IllegalArgumentException("手动记忆参数超出范围");
 }
 static boolean flag(JSONObject j,String key,boolean fallback){Object v=j.opt(key);if(v==null)return fallback;if(!(v instanceof Boolean))throw new IllegalArgumentException("记忆开关必须是布尔值："+key);return (Boolean)v;}
 static double number(JSONObject j,String key,double fallback,double low,double high,boolean integer){Object v=j.opt(key);double n=v==null?fallback:v instanceof Number?((Number)v).doubleValue():Double.NaN;if(!Double.isFinite(n)||n<low||n>high||integer&&n!=Math.rint(n))throw new IllegalArgumentException("记忆参数无效："+key);return n;}
 static MemoryOptions parse(JSONObject j)throws JSONException {
  return new MemoryOptions(flag(j,"memory_persist",true),(int)number(j,"memory_retention_days",30,1,90,true),(int)number(j,"memory_max_points",0,0,7,true),
   flag(j,"memory_restore_unlock",true),flag(j,"memory_restore_same_scene",true),flag(j,"memory_restore_replace",true),(int)number(j,"memory_restore_settle",1500,500,5000,true),
   (float)number(j,"memory_restore_ratio",.5,.1,2,false),(float)number(j,"memory_restore_min_lux",5,1,30,false),flag(j,"memory_reset_override",false),
   (int)number(j,"memory_reset_off_minutes",6,1,30,true),(int)number(j,"memory_reset_force_minutes",30,1,120,true),(float)number(j,"memory_reset_lux",20,1,200,false),
   flag(j,"memory_timeout_override",false),(int)number(j,"memory_timeout_minutes",30,1,120,true));
 }
 MemoryOptions with(String key,Object value){try{JSONObject j=new JSONObject();put(j);j.put(key,value);return parse(j);}catch(JSONException invalid){throw new IllegalArgumentException(invalid);}}
 MemoryOptions fit(int capacity){return capacity>0&&maxPoints>capacity?with("memory_max_points",capacity):this;}
 void verify(JSONObject runtime){if(resetOverride&&!runtime.optBoolean("memory_reset_supported"))throw new IllegalArgumentException("本机暂不支持锁屏记忆重判设置");if(timeoutOverride&&!runtime.optBoolean("memory_timeout_supported"))throw new IllegalArgumentException("本机暂不支持短期记忆有效时间设置");}
 void put(JSONObject j)throws JSONException{j.put("memory_persist",persist).put("memory_retention_days",days).put("memory_max_points",maxPoints)
  .put("memory_restore_unlock",unlock).put("memory_restore_same_scene",sameScene).put("memory_restore_replace",replaceOnUnlock).put("memory_restore_settle",settleMs).put("memory_restore_ratio",sceneRatio).put("memory_restore_min_lux",sceneMinimum)
  .put("memory_reset_override",resetOverride).put("memory_reset_off_minutes",offMinutes).put("memory_reset_force_minutes",forceMinutes).put("memory_reset_lux",resetLux).put("memory_timeout_override",timeoutOverride).put("memory_timeout_minutes",timeoutMinutes);}
}
