package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Persisted independently of the device's curve and native brightness coordinates. */
final class OutdoorOptions {
    static final String[] FLAGS={"outdoor_enabled","outdoor_hbm_tuning","outdoor_range_unlock","outdoor_opr_relax"};
    static final String[] KEYS={"outdoor_enter_lux","outdoor_full_lux","outdoor_enter_ms","outdoor_exit_ms","outdoor_exit_ratio","outdoor_strength","outdoor_session_ms","outdoor_cooldown_ms","outdoor_hbm_lux_factor","outdoor_hbm_budget_factor"};
    static final double[] DEFAULT={10000,30000,3000,5000,.65,.85,180000,60000,1,1};
    static final double[] MIN={2000,4000,500,1000,.4,.1,30000,15000,.25,1};
    static final double[] MAX={50000,100000,10000,15000,.9,1,600000,300000,1.5,2};
    static final double[] STEP={1000,2000,500,500,.05,.05,30000,15000,.05,.1};
    final boolean[] flags=new boolean[FLAGS.length];final double[] values=DEFAULT.clone();
    static OutdoorOptions parse(JSONObject json){OutdoorOptions o=new OutdoorOptions();
        for(int i=0;i<FLAGS.length;i++)if(json.has(FLAGS[i])){Object v=json.opt(FLAGS[i]);if(!(v instanceof Boolean))throw new IllegalArgumentException("户外增强开关必须是布尔值");o.flags[i]=(Boolean)v;}
        for(int i=0;i<KEYS.length;i++){if(json.has(KEYS[i])){Object v=json.opt(KEYS[i]);if(!(v instanceof Number))throw new IllegalArgumentException("户外增强参数必须是数值");o.values[i]=((Number)v).doubleValue();}AdvancedPolicy.range(o.values[i],MIN[i],MAX[i]);}
        if(o.values[1]<=o.values[0])throw new IllegalArgumentException("全强度照度须高于进入照度");
        for(int i:new int[]{0,1,2,3,6,7})if(o.values[i]!=Math.rint(o.values[i]))throw new IllegalArgumentException("户外照度与时间须为整数");
        if(!o.flags[0]&&(o.flags[1]||o.flags[2]||o.flags[3]))throw new IllegalArgumentException("请先开启户外增强，或关闭附加高亮选项");if(o.flags[3]&&!o.flags[2])throw new IllegalArgumentException("请先开放高亮范围，再放宽画面限亮");return o;
    }
    void put(JSONObject json)throws JSONException{for(int i=0;i<FLAGS.length;i++)json.put(FLAGS[i],flags[i]);for(int i=0;i<KEYS.length;i++)json.put(KEYS[i],values[i]);}
    void verify(JSONObject status){if(flags[0]&&!status.optBoolean("outdoor_supported"))throw new IllegalArgumentException("此固件的户外增强接口尚未兼容");
        if(flags[1]&&!status.optBoolean("outdoor_hbm_supported"))throw new IllegalArgumentException("此设备未使用可调整的 HBM 时间控制器");
        if(flags[2]&&!status.optBoolean("outdoor_range_supported"))throw new IllegalArgumentException("此固件的高亮范围接口尚未兼容");
        if(flags[3]&&!status.optBoolean("outdoor_opr_supported"))throw new IllegalArgumentException("此固件的画面限亮接口尚未兼容");}
    boolean same(OutdoorOptions other){return java.util.Arrays.equals(flags,other.flags)&&java.util.Arrays.equals(values,other.values);}
}
