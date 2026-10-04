package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** Wider evidence thresholds in low light; never changes sensor readings or output brightness. */
final class LowLightThresholds {
 static final String[] KEYS={"low_light_brighten_ratio","low_light_brighten_floor","low_light_darken_ratio","low_light_darken_floor"};
 static final double[] DEFAULT={.6,10,.4,2},MIN={.1,1,.1,0},MAX={3,30,.9,10};
 final boolean enabled;final double[] values;
 LowLightThresholds(){this(true,DEFAULT.clone());}
 LowLightThresholds(boolean enabled,double[] values){this.enabled=enabled;this.values=values.clone();}
 // Older configurations only enabled longer confirmation; do not silently opt them
 // into a new threshold Hook that their firmware may not support.
 static LowLightThresholds parse(JSONObject j){boolean enabled=MemoryOptions.flag(j,"low_light_threshold",false);double[] values=new double[4];for(int i=0;i<4;i++)values[i]=MemoryOptions.number(j,KEYS[i],DEFAULT[i],MIN[i],MAX[i],false);return new LowLightThresholds(enabled,values);}
 void put(JSONObject j)throws JSONException {j.put("low_light_threshold",enabled);for(int i=0;i<4;i++)j.put(KEYS[i],values[i]);}
 void verify(boolean stability,boolean supported){if(stability&&enabled&&!supported)throw new IllegalArgumentException("此系统的暗光阈值接口暂未兼容");}
 float threshold(float lux,float original,boolean bright){int i=bright?0:2;float floor=(float)Math.max(lux*values[i],values[i+1]);return AdvancedPolicy.threshold(lux,original,bright,1,floor);}
}
