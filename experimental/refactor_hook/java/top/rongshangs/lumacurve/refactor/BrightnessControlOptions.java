package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Optional mode ownership, independent of curve learning and ordinary low-light tuning. */
final class BrightnessControlOptions {
    boolean darkLock=true,manualPanel=true,manualHold;
    float enterLux=10,exitLux=50;
    int minutes=1,exitSeconds=1;
    final java.util.Set<String> manualApps=new java.util.LinkedHashSet<>();
    static BrightnessControlOptions parse(JSONObject j)throws JSONException {
        BrightnessControlOptions o=new BrightnessControlOptions();
        o.darkLock=bool(j,"dark_lock_enabled",o.darkLock);o.manualPanel=bool(j,"manual_panel_enabled",true);
        o.manualHold=bool(j,"dark_lock_manual_hold",false);
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
    // Existing pre-feature configurations never opted in; new UI drafts use the defaults above.
    static BrightnessControlOptions parseStored(JSONObject j)throws JSONException {
        BrightnessControlOptions o=parse(j);if(!j.has("dark_lock_enabled"))o.darkLock=false;return o;
    }
    void bedtime(){BrightnessControlOptions o=new BrightnessControlOptions();darkLock=o.darkLock;enterLux=o.enterLux;exitLux=o.exitLux;minutes=o.minutes;exitSeconds=o.exitSeconds;}
    static boolean bool(JSONObject j,String key,boolean def){if(!j.has(key))return def;Object v=j.opt(key);if(!(v instanceof Boolean))throw new IllegalArgumentException("开关无效："+key);return (Boolean)v;}
    static float number(JSONObject j,String key,float def,float min,float max){if(!j.has(key))return def;Object v=j.opt(key);if(!(v instanceof Number))throw new IllegalArgumentException("参数无效："+key);float n=((Number)v).floatValue();if(!Float.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("参数超出范围："+key);return n;}
    static int integer(JSONObject j,String key,int def,int min,int max){float n=number(j,key,def,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException("参数必须为整数："+key);return (int)n;}
    static boolean validPackage(String p){return p!=null&&p.length()<=160&&p.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");}
    void put(JSONObject j)throws JSONException{j.put("dark_lock_enabled",darkLock).put("manual_panel_enabled",manualPanel).put("dark_lock_manual_hold",manualHold).put("manual_brightness_apps",new JSONArray(manualApps)).put("dark_lock_enter_lux",enterLux).put("dark_lock_exit_lux",exitLux).put("dark_lock_minutes",minutes).put("dark_lock_exit_seconds",exitSeconds);}
    void verify(JSONObject state){if(darkLock&&!state.optBoolean("dark_lock_supported"))throw new IllegalArgumentException("本机暗光锁定所需的主屏与光感接口尚未就绪");if(!manualApps.isEmpty()&&!state.optBoolean("app_auto_supported"))throw new IllegalArgumentException("本机前台应用事件接口尚未兼容");}
    boolean same(BrightnessControlOptions o){return darkLock==o.darkLock&&manualPanel==o.manualPanel&&manualHold==o.manualHold&&manualApps.equals(o.manualApps)&&enterLux==o.enterLux&&exitLux==o.exitLux&&minutes==o.minutes&&exitSeconds==o.exitSeconds;}
}
