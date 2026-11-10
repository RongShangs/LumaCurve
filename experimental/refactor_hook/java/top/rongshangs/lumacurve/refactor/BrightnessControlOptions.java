package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Optional mode ownership, independent of curve learning and ordinary low-light tuning. */
final class BrightnessControlOptions {
    boolean appIsolation,appIsolationShare,darkLock,sunsetOnly,manualPanel=true,manualExit,partitionRestoreAuto=true;int nodeSliderMode,partitionMaximum,manualMaximum;
    float enterLux=10,exitLux=50;
    int minutes=1,exitSeconds=1;
    final java.util.Set<String> manualApps=new java.util.LinkedHashSet<>();
    final java.util.Set<String> independentApps=new java.util.LinkedHashSet<>();
    final java.util.List<AppGroup> independentGroups=new java.util.ArrayList<>();
    boolean independentKeepLock,independentKeepReboot;
    static final class AppGroup {
        final String id;String name;final java.util.Set<String> apps=new java.util.LinkedHashSet<>();
        AppGroup(String id,String name){if(id==null||id.equals("pause")||id.equals("individual")||!id.matches("[a-zA-Z0-9_-]{1,40}")||name==null||name.trim().isEmpty()||name.length()>32||name.indexOf('\n')>=0)throw new IllegalArgumentException("应用分组无效");this.id=id;this.name=name.trim();}
    }
    static java.util.Set<String> readApps(JSONObject j,String key)throws JSONException{
        java.util.Set<String> out=new java.util.LinkedHashSet<>();if(!j.has(key))return out;Object raw=j.get(key);if(!(raw instanceof JSONArray))throw new IllegalArgumentException("应用名单格式无效");JSONArray list=(JSONArray)raw;
        if(list.length()>64)throw new IllegalArgumentException("应用名单最多 64 项");for(int i=0;i<list.length();i++){Object value=list.get(i);if(!(value instanceof String)||!validPackage((String)value))throw new IllegalArgumentException("应用包名无效");out.add((String)value);}return out;
    }
    void readIndependent(JSONObject j)throws JSONException{
        independentApps.addAll(readApps(j,"independent_brightness_apps"));independentKeepLock=bool(j,"independent_brightness_keep_lock",false);independentKeepReboot=bool(j,"independent_brightness_keep_reboot",false);
        if(j.has("independent_brightness_groups")){Object raw=j.get("independent_brightness_groups");if(!(raw instanceof JSONArray))throw new IllegalArgumentException("应用分组格式无效");JSONArray groups=(JSONArray)raw;if(groups.length()>3)throw new IllegalArgumentException("最多 3 个应用分组");java.util.Set<String> ids=new java.util.HashSet<>();
            for(int i=0;i<groups.length();i++){Object value=groups.get(i);if(!(value instanceof JSONObject))throw new IllegalArgumentException("应用分组格式无效");JSONObject item=(JSONObject)value;Object id=item.get("id"),name=item.get("name");if(!(id instanceof String)||!(name instanceof String))throw new IllegalArgumentException("应用分组无效");AppGroup group=new AppGroup((String)id,(String)name);if(!ids.add(group.id))throw new IllegalArgumentException("应用分组重复");group.apps.addAll(readApps(item,"apps"));independentGroups.add(group);}}
        validateApplications();
    }
    void validateApplications(){java.util.Set<String> seen=new java.util.HashSet<>(manualApps);for(String pkg:independentApps)if(!seen.add(pkg))throw new IllegalArgumentException("应用只能存在于一个名单或分组");for(AppGroup group:independentGroups)for(String pkg:group.apps)if(!seen.add(pkg))throw new IllegalArgumentException("应用只能存在于一个名单或分组");if(seen.size()>64)throw new IllegalArgumentException("全部名单合计最多 64 个应用");}
    java.util.Set<String> applications(){java.util.Set<String> out=new java.util.LinkedHashSet<>(manualApps);out.addAll(independentApps);for(AppGroup g:independentGroups)out.addAll(g.apps);return out;}
    String independentKey(String pkg){if(independentApps.contains(pkg))return "app:"+pkg;for(AppGroup g:independentGroups)if(g.apps.contains(pkg))return "group:"+g.id;return "";}
    java.util.Set<String> applicationList(String id){if(id.equals("pause"))return manualApps;if(id.equals("individual"))return independentApps;for(AppGroup g:independentGroups)if(id.equals(g.id))return g.apps;throw new IllegalArgumentException("应用分组已移除");}
    void selectApplications(String id,java.util.Collection<String> packages){selectApplications(id,packages,java.util.Collections.emptySet());}
    void selectApplications(String id,java.util.Collection<String> packages,java.util.Collection<String> confirmedMoves){
        java.util.Set<String> next=new java.util.LinkedHashSet<>(packages),moves=new java.util.LinkedHashSet<>(confirmedMoves),target=applicationList(id);if(next.size()>64||moves.size()>64)throw new IllegalArgumentException("应用名单最多 64 项");
        for(String pkg:next)if(!validPackage(pkg))throw new IllegalArgumentException("应用包名无效");for(String pkg:moves)if(!validPackage(pkg))throw new IllegalArgumentException("应用包名无效");
        java.util.Set<String> all=applications();all.removeAll(target);all.removeAll(moves);all.addAll(next);if(all.size()>64)throw new IllegalArgumentException("全部名单合计最多 64 个应用");
        moves.addAll(next);manualApps.removeAll(moves);independentApps.removeAll(moves);for(AppGroup g:independentGroups)g.apps.removeAll(moves);target.clear();target.addAll(next);validateApplications();
    }

    String independentSignature(){try{JSONObject value=new JSONObject();putIndependent(value);return value.toString();}catch(JSONException invalid){throw new IllegalStateException(invalid);}}
    void putIndependent(JSONObject j)throws JSONException{JSONArray groups=new JSONArray();for(AppGroup g:independentGroups)groups.put(new JSONObject().put("id",g.id).put("name",g.name).put("apps",new JSONArray(g.apps)));j.put("independent_brightness_apps",new JSONArray(independentApps)).put("independent_brightness_groups",groups).put("independent_brightness_keep_lock",independentKeepLock).put("independent_brightness_keep_reboot",independentKeepReboot);}
    static BrightnessControlOptions parse(JSONObject j)throws JSONException {
        BrightnessControlOptions o=new BrightnessControlOptions();o.appIsolation=bool(j,"app_brightness_isolation",false);
        o.appIsolationShare=bool(j,"app_brightness_isolation_share",false);
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
        o.readIndependent(j);
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
    void put(JSONObject j)throws JSONException{putIndependent(j);j.put("app_brightness_isolation",appIsolation).put("app_brightness_isolation_share",appIsolationShare);j.put("manual_panel_maximum",manualMaximum);j.remove("dark_lock_manual_hold");j.put("control_center_node_mode",nodeSliderMode).put("control_center_partition_restore_auto",partitionRestoreAuto).put("control_center_partition_maximum",partitionMaximum);j.put("dark_lock_enabled",darkLock).put("dark_lock_sunset_only",sunsetOnly).put("manual_panel_enabled",manualPanel).put("dark_lock_manual_exit",manualExit).put("manual_brightness_apps",new JSONArray(manualApps)).put("dark_lock_enter_lux",enterLux).put("dark_lock_exit_lux",exitLux).put("dark_lock_minutes",minutes).put("dark_lock_exit_seconds",exitSeconds);}
    static boolean mappingRecoveryWanted(JSONObject config){return config!=null&&config.optBoolean("enabled")&&config.optInt("user_serial",-1)==0&&config.optInt("control_center_node_mode",0)>0&&config.optInt("control_center_node_mode",0)<=4;}
    static String capability(int mode){return mode==4?"control_center_partition_supported":mode==3?"control_center_raw_supported":"control_center_slider_supported";}
    void verify(JSONObject state){if(nodeSliderMode>0&&!state.optBoolean(capability(nodeSliderMode)))throw new IllegalArgumentException("本机控制中心节点输出接口尚未兼容");if(darkLock&&!state.optBoolean("dark_lock_supported"))throw new IllegalArgumentException("本机暗光锁定所需的主屏与光感接口尚未就绪");if(!manualApps.isEmpty()&&!state.optBoolean("app_auto_supported"))throw new IllegalArgumentException("本机前台应用事件接口尚未兼容");}
    static int manualCeiling(int configured,int device){return configured==0?device:Math.min(configured,device);}
    boolean same(BrightnessControlOptions o){return appIsolation==o.appIsolation&&nodeSliderMode==o.nodeSliderMode&&partitionRestoreAuto==o.partitionRestoreAuto&&partitionMaximum==o.partitionMaximum&&manualMaximum==o.manualMaximum&&sameOwnership(o);}
    // Coordinate presentation does not own automatic mode or a held dark-lock target.
    boolean sameOwnership(BrightnessControlOptions o){return darkLock==o.darkLock&&sunsetOnly==o.sunsetOnly&&manualPanel==o.manualPanel&&manualExit==o.manualExit&&manualApps.equals(o.manualApps)&&independentSignature().equals(o.independentSignature())&&enterLux==o.enterLux&&exitLux==o.exitLux&&minutes==o.minutes&&exitSeconds==o.exitSeconds;}
}
