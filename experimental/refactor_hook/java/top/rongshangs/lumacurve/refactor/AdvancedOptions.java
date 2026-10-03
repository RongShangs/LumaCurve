package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** One validated schema for UI, root submission and the display-thread Hook. */
final class AdvancedOptions {
    static final String[] GROUPS={"threshold_override","assist_override","animation_override","sunlight_override","touch_override"};
    static final String[] KEYS={"threshold_brighten","threshold_darken","threshold_small","threshold_min_brighten","threshold_min_darken","threshold_limit",
        "assist_brighten","assist_darken","assist_small","animation_brighten","animation_darken","sunlight_enter","sunlight_exit","touch_release_seconds"};
    static final double[] DEFAULT={1,1,1,0,0,200,1000,2000,3000,1,1,5000,2000,1};
    static final double[] MIN={.5,.5,.5,0,0,10,500,500,500,.5,.5,1000,500,0};
    static final double[] MAX={3,3,3,10,10,2000,4000,4000,4000,3,3,8000,8000,5};
    static final double[] STEP={.1,.1,.1,.5,.5,10,500,500,500,.1,.1,500,500,1};
    final boolean[] enabled=new boolean[GROUPS.length]; final double[] values=DEFAULT.clone();
    static AdvancedOptions parse(JSONObject config){
        AdvancedOptions out=new AdvancedOptions();
        for(int i=0;i<GROUPS.length;i++)if(config.has(GROUPS[i])){Object value=config.opt(GROUPS[i]);if(!(value instanceof Boolean))throw new IllegalArgumentException("高级开关必须是布尔值");out.enabled[i]=(Boolean)value;}
        for(int i=0;i<KEYS.length;i++){if(config.has(KEYS[i])){Object value=config.opt(KEYS[i]);if(!(value instanceof Number))throw new IllegalArgumentException("高级参数必须是数值");out.values[i]=((Number)value).doubleValue();}AdvancedPolicy.range(out.values[i],MIN[i],MAX[i]);}
        if(out.values[13]!=Math.rint(out.values[13]))throw new IllegalArgumentException("触摸释放等待须为整数秒");
        return out;
    }
    void put(JSONObject config)throws JSONException{
        for(int i=0;i<GROUPS.length;i++)config.put(GROUPS[i],enabled[i]);
        for(int i=0;i<KEYS.length;i++)config.put(KEYS[i],values[i]);
    }
    void verify(JSONObject capabilities){
        for(int i=0;i<GROUPS.length;i++)if(enabled[i]&&!capabilities.optBoolean(GROUPS[i]+"_supported"))throw new IllegalArgumentException("此系统暂不支持："+GROUPS[i]);
    }
}
