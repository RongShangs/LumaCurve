package top.rongshangs.lumacurve.refactor;
import org.json.*;

/** Category-local draft patches. Presets never submit settings or change another group. */
final class SettingsPresets {
 static JSONObject patch(String group,int mode,JSONObject caps)throws JSONException {
  if(mode<0||mode>2)throw new IllegalArgumentException("未知预设");
  JSONObject p=new JSONObject();boolean enabled=mode!=0;
  switch(group){
   case "low":
    require(caps,"low_light_supported",enabled);require(caps,"low_light_threshold_supported",enabled);
    p.put("low_light_stability",enabled);if(!enabled)return p;
    p.put("low_light_limit",50).put("low_light_brighten",3000).put("low_light_darken",4000);
    new LowLightThresholds(true,mode==1?LowLightThresholds.DEFAULT:new double[]{1,15,.5,3}).put(p);
    p.put("low_light_assist_gate",caps.optBoolean("low_light_assist_gate_supported")).put("low_light_assist_wait",mode==1?6000:8000).put("low_light_assist_tolerance",.2);return p;
   case "outdoor":
    require(caps,"outdoor_supported",enabled);OutdoorOptions o=new OutdoorOptions();o.flags[0]=enabled;o.values[5]=mode==1?.5:.85;o.put(p);return p;
   case "response":
    require(caps,"response_supported",enabled);p.put("response_override",enabled).put("small_brighten_override",enabled&&caps.optBoolean("small_response_supported"));
    if(enabled)p.put("brighten_delay",mode==1?1500:3000).put("darken_delay",mode==1?2000:5000).put("small_brighten_delay",mode==1?1500:5000);return p;
   case "thermal":
    require(caps,"thermal_supported",enabled);p.put("thermal_relax",enabled);if(enabled)p.put("thermal_ceiling",43).put("thermal_cooling",1);return p;
   default:
    int g=-1;for(int i=0;i<AdvancedOptions.GROUPS.length;i++)if(group.equals(AdvancedOptions.GROUPS[i]))g=i;
    if(g<0)throw new IllegalArgumentException("未知预设分类");require(caps,group+"_supported",enabled);p.put(group,enabled);if(!enabled)return p;
    int[] from={0,6,9,11,13},to={6,9,11,13,14};double[] v=AdvancedOptions.DEFAULT.clone();
    if(g==0){v[0]=v[1]=v[2]=mode==1?1.5:.8;v[3]=mode==1?2:0;v[4]=mode==1?1:0;}
    if(g==1){v[6]=mode==1?1000:3000;v[7]=mode==1?2000:4000;v[8]=mode==1?2000:4000;}
    if(g==2)v[9]=v[10]=mode==1?1.5:.8;
    if(g==3){v[11]=mode==1?1000:8000;v[12]=mode==1?1000:5000;}
    if(g==4)v[13]=mode==1?0:2;
    for(int i=from[g];i<to[g];i++)p.put(AdvancedOptions.KEYS[i],v[i]);return p;
  }
 }
 static void require(JSONObject caps,String key,boolean needed){if(needed&&!caps.optBoolean(key))throw new IllegalArgumentException("此系统接口暂未兼容");}
 static JSONObject merge(JSONObject draft,JSONObject patch)throws JSONException {JSONObject next=new JSONObject(draft.toString());java.util.Iterator<String> keys=patch.keys();while(keys.hasNext()){String key=keys.next();next.put(key,patch.get(key));}return next;}
}
