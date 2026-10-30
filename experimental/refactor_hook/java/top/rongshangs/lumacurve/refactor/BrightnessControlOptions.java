package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Optional mode ownership, independent of curve learning and ordinary low-light tuning. */
final class BrightnessControlOptions {
    boolean darkLock,sunsetOnly,manualPanel=true,manualExit,partitionRestoreAuto=true;int nodeSliderMode,partitionMaximum,manualMaximum;
    float enterLux=10,exitLux=50;
    int minutes=1,exitSeconds=1;
    final java.util.Set<String> manualApps=new java.util.LinkedHashSet<>();
    static BrightnessControlOptions parse(JSONObject j)throws JSONException {
        BrightnessControlOptions o=new BrightnessControlOptions();
        o.nodeSliderMode=integer(j,"control_center_node_mode",0,0,4);o.partitionRestoreAuto=bool(j,"control_center_partition_restore_auto",true);o.partitionMaximum=integer(j,"control_center_partition_maximum",0,0,65535);
        o.darkLock=bool(j,"dark_lock_enabled",o.darkLock);o.sunsetOnly=bool(j,"dark_lock_sunset_only",false);o.manualPanel=bool(j,"manual_panel_enabled",true);
        o.manualMaximum=integer(j,"manual_panel_maximum",0,0,65535);if(o.manualMaximum>0&&o.manualMaximum<10)throw new IllegalArgumentException("主屏手动节点上限至少为 10");
        // The replacement switch defaults to continuing the lock. Legacy fields
        // are type-checked, but cannot silently enable the new exit behavior.
        if(j.has("dark_lock_manual_hold"))bool(j,"dark_lock_manual_hold",false);
        o.manualExit=bool(j,"dark_lock_manual_exit",false);
        if(j.has("manual_brightness_apps")){
            Object raw=j.get("manual_brightness_apps");if(!(raw instanceof JSONArray))throw new IllegalArgumentException("应用名单格式无效");
            JSONArray apps=(JSONArray)raw;if(apps.length()>64)throw new IllegalArgumentException("应用名单最多 64 项");
            for(int i=0;i<apps.length();i++){Object value=apps.get(i);if(!(value instanceof String)||!validPackage((String)value))throw new IllegalArgumentException("应用包名无效");o.manualApps.add((String)value);}
        }
        o.enterLux=number(j,"dark_lock_enter_lux",o.enterLux,1,30);o.exitLux=number(j,"dark_lock_exit_lux",o.exitLux,5,500);
        o.minutes=integer(j,"dark_lock_minutes",o.minutes,1,30);o.exitSeconds=integer(j,"dark_lock_exit_seconds",o.exitSeconds,1,30);
        if(o.exitLux<o.enterLux+5)throw new IllegalArgumentException("退出照度至少比进入照度高 5 lux");
        return o;
    }
    // Both fresh drafts and missing legacy flags stay off; explicit saved choices win.
    static BrightnessControlOptions parseStored(JSONObject j)throws JSONException {
        BrightnessControlOptions o=parse(j);if(!j.has("dark_lock_enabled"))o.darkLock=false;return o;
    }
    void holdInDarkness(){BrightnessControlOptions o=new BrightnessControlOptions();darkLock=true;sunsetOnly=false;manualExit=o.manualExit;enterLux=o.enterLux;exitLux=o.exitLux;minutes=o.minutes;exitSeconds=o.exitSeconds;}
    void afterSunset(){holdInDarkness();sunsetOnly=true;}
    static boolean bool(JSONObject j,String key,boolean def){if(!j.has(key))return def;Object v=j.opt(key);if(!(v instanceof Boolean))throw new IllegalArgumentException("开关无效："+key);return (Boolean)v;}
    static float number(JSONObject j,String key,float def,float min,float max){if(!j.has(key))return def;Object v=j.opt(key);if(!(v instanceof Number))throw new IllegalArgumentException("参数无效："+key);float n=((Number)v).floatValue();if(!Float.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("参数超出范围："+key);return n;}
    static int integer(JSONObject j,String key,int def,int min,int max){float n=number(j,key,def,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException("参数必须为整数："+key);return (int)n;}
    static boolean validPackage(String p){return p!=null&&p.length()<=160&&p.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");}
    void put(JSONObject j)throws JSONException{j.put("manual_panel_maximum",manualMaximum);j.remove("dark_lock_manual_hold");j.put("control_center_node_mode",nodeSliderMode).put("control_center_partition_restore_auto",partitionRestoreAuto).put("control_center_partition_maximum",partitionMaximum);j.put("dark_lock_enabled",darkLock).put("dark_lock_sunset_only",sunsetOnly).put("manual_panel_enabled",manualPanel).put("dark_lock_manual_exit",manualExit).put("manual_brightness_apps",new JSONArray(manualApps)).put("dark_lock_enter_lux",enterLux).put("dark_lock_exit_lux",exitLux).put("dark_lock_minutes",minutes).put("dark_lock_exit_seconds",exitSeconds);}
    static String capability(int mode){return mode==4?"control_center_partition_supported":mode==3?"control_center_raw_supported":"control_center_slider_supported";}
    void verify(JSONObject state){if(nodeSliderMode>0&&!state.optBoolean(capability(nodeSliderMode)))throw new IllegalArgumentException("本机控制中心节点输出接口尚未兼容");if(darkLock&&!state.optBoolean("dark_lock_supported"))throw new IllegalArgumentException("本机暗光锁定所需的主屏与光感接口尚未就绪");if(!manualApps.isEmpty()&&!state.optBoolean("app_auto_supported"))throw new IllegalArgumentException("本机前台应用事件接口尚未兼容");}
    static int manualCeiling(int configured,int device){return configured==0?device:Math.min(configured,device);}
    boolean same(BrightnessControlOptions o){return nodeSliderMode==o.nodeSliderMode&&partitionRestoreAuto==o.partitionRestoreAuto&&partitionMaximum==o.partitionMaximum&&manualMaximum==o.manualMaximum&&sameOwnership(o);}
    // Coordinate presentation does not own automatic mode or a held dark-lock target.
    boolean sameOwnership(BrightnessControlOptions o){return darkLock==o.darkLock&&sunsetOnly==o.sunsetOnly&&manualPanel==o.manualPanel&&manualExit==o.manualExit&&manualApps.equals(o.manualApps)&&enterLux==o.enterLux&&exitLux==o.exitLux&&minutes==o.minutes&&exitSeconds==o.exitSeconds;}
}
