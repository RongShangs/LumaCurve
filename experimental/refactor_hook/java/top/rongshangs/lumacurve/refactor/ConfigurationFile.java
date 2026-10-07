package top.rongshangs.lumacurve.refactor;

import org.json.*;
import java.util.*;

/** Portable preferences, separate from firmware/user-bound live Hook commands. */
final class ConfigurationFile {
    static final int LIMIT=32768;
    static final String FORMAT="hyperlux_config_v2";
    /** Compare flat option values independently of JSON key order and number boxing. */
    static boolean sameOptions(JSONObject submitted,JSONObject current)throws JSONException {
        if(submitted.length()!=current.length())return false;
        Iterator<String> keys=submitted.keys();while(keys.hasNext()){
            String key=keys.next();if(!current.has(key)||!new JSONArray().put(submitted.get(key)).toString().equals(new JSONArray().put(current.get(key)).toString()))return false;
        }
        return true;
    }
    static final String[] FLAGS={"thermal_relax","response_override","small_brighten_override","low_light_stability"};
    static final String[] KEYS={"thermal_ceiling","memory_strength","memory_window","memory_lux_range","thermal_cooling","brighten_delay","darken_delay","small_brighten_delay","low_light_limit","low_light_brighten","low_light_darken"};
    static final double[] DEFAULT={43,1,1500,.3,1,1500,5000,5000,50,3000,4000},MIN={38,0,500,.1,1,500,1000,500,5,1000,1000},MAX={50,1,3000,.5,3,10000,15000,15000,100,4000,4000};
    static final class Imported {final JSONObject options;final List<String> notes;final JSONObject ui;Imported(JSONObject o,List<String> n,JSONObject ui){options=o;notes=n;this.ui=ui;}}
    static JSONObject export(JSONObject options,JSONObject runtime,boolean dirty,int refresh,boolean autoScroll)throws JSONException {
        return new JSONObject().put("format",FORMAT).put("schema",2).put("app_version",AppBuild.ARTIFACT_VERSION).put("build",AppBuild.BUILD).put("unsaved_changes",dirty)
            .put("curve_backend",runtime.optString("curve_backend")).put("baseline_id",runtime.optString("baseline_id")).put("options",options)
            .put("interface",new JSONObject().put("refresh_seconds",refresh).put("log_auto_scroll",autoScroll));
    }
    static double number(JSONObject j,String k,double def,double low,double high,List<String> notes)throws JSONException {
        if(!j.has(k))return def;Object v=j.opt(k);if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw new IllegalArgumentException("配置参数无效："+k);
        double raw=((Number)v).doubleValue(),n=Math.max(low,Math.min(high,raw));if(n!=raw)notes.add("已调整超出范围的参数："+k);return n;
    }
    static boolean flag(JSONObject j,String k){if(!j.has(k))return false;Object v=j.opt(k);if(!(v instanceof Boolean))throw new IllegalArgumentException("配置开关无效："+k);return (Boolean)v;}
    static Imported read(String text,JSONObject runtime,JSONObject current)throws JSONException {
        if(text.length()>LIMIT)throw new IllegalArgumentException("配置文件过大");JSONObject file=new JSONObject(text),source;boolean legacy=false;String format=file.optString("format");
        if(FORMAT.equals(format)){Object schema=file.opt("schema");if(!(schema instanceof Number)||((Number)schema).doubleValue()!=2)throw new IllegalArgumentException("配置文件版本暂不支持，请更新应用");source=file.getJSONObject("options");}
        else if("hyperlux_config_v1".equals(format)){source=file.getJSONObject("options");}
        else if(format.isEmpty()&&file.optInt("schema")==1&&file.has("factors")){source=file;legacy=true;}
        else throw new IllegalArgumentException("不是兼容的 HyperLux 配置文件");
        JSONObject out=new JSONObject();List<String> notes=new ArrayList<>();BrightnessControlOptions controls=BrightnessControlOptions.parseStored(source);if(controls.darkLock&&!runtime.optBoolean("dark_lock_supported")){controls.darkLock=false;notes.add("本机暗光锁定接口未就绪，已关闭");}controls.put(out);MemoryOptions memoryOptions=MemoryOptions.parse(source),fitMemory=memoryOptions.fit(runtime.optInt("memory_point_capacity",0));fitMemory.put(out);disable(out,"memory_reset_override",runtime.optBoolean("memory_reset_supported"),notes);disable(out,"memory_timeout_override",runtime.optBoolean("memory_timeout_supported"),notes);if(memoryOptions.maxPoints!=fitMemory.maxPoints)notes.add("持久化节点上限已按本机能力调整");
        for(String k:FLAGS)out.put(k,flag(source,k));
        LowLightThresholds lowThresholds=LowLightThresholds.parse(source);lowThresholds.put(out);if(out.getBoolean("low_light_stability"))disable(out,"low_light_threshold",runtime.optBoolean("low_light_threshold_supported"),notes);
        LowLightAssistGate assistGate=new LowLightAssistGate();assistGate.configure(source);assistGate.put(out);if(out.getBoolean("low_light_stability"))disable(out,"low_light_assist_gate",runtime.optBoolean("low_light_assist_gate_supported"),notes);
        for(int i=0;i<KEYS.length;i++){double n=number(source,KEYS[i],DEFAULT[i],MIN[i],MAX[i],notes);if(i==2||i>=5&&i!=8){if(n!=Math.rint(n))throw new IllegalArgumentException("配置时间必须是整数："+KEYS[i]);}out.put(KEYS[i],n);}
        for(int i=0;i<AdvancedOptions.GROUPS.length;i++)out.put(AdvancedOptions.GROUPS[i],flag(source,AdvancedOptions.GROUPS[i]));
        for(int i=0;i<AdvancedOptions.KEYS.length;i++)out.put(AdvancedOptions.KEYS[i],number(source,AdvancedOptions.KEYS[i],AdvancedOptions.DEFAULT[i],AdvancedOptions.MIN[i],AdvancedOptions.MAX[i],notes));
        for(String k:OutdoorOptions.FLAGS)out.put(k,flag(source,k));
        for(int i=0;i<OutdoorOptions.KEYS.length;i++)out.put(OutdoorOptions.KEYS[i],number(source,OutdoorOptions.KEYS[i],OutdoorOptions.DEFAULT[i],OutdoorOptions.MIN[i],OutdoorOptions.MAX[i],notes));
        if(out.getDouble("outdoor_full_lux")<=out.getDouble("outdoor_enter_lux")){out.put("outdoor_full_lux",Math.min(100000,out.getDouble("outdoor_enter_lux")+2000));notes.add("已调整全强度照度");}
        if(!out.getBoolean("outdoor_enabled")){out.put("outdoor_hbm_tuning",false).put("outdoor_range_unlock",false).put("outdoor_opr_relax",false);}
        boolean same=!runtime.optString("baseline_id").isEmpty()&&runtime.optString("baseline_id").equals(file.optString("baseline_id"))&&runtime.optString("curve_backend").equals(file.optString("curve_backend"));
        if(legacy&&!file.has("baseline_id"))same=runtime.optString("fingerprint").equals(file.optString("fingerprint"))&&runtime.optString("curve_backend").equals(file.optString("curve_backend",runtime.optString("curve_backend").equals("refactor")?"refactor":""));
        float[] factors=same?CurvePlan.factors(source.getString("factors")):CurvePlan.factors(current.getString("factors"));
        float floor=same?CurvePlan.floor(source.has("curve_floor_nit")?source.opt("curve_floor_nit"):null):CurvePlan.floor(current.has("curve_floor_nit")?current.opt("curve_floor_nit"):null);
        if(!same)notes.add("曲线基准不同，已保留本机曲线与亮度下限");
        float[] lux=RootControl.numbers(runtime.getJSONArray("factory_lux")),nit=RootControl.numbers(runtime.getJSONArray("factory_logical_nit"));
        new CurvePlan(lux,nit,(float)runtime.getDouble("min_logical_nit"),(float)runtime.getDouble("max_logical_nit"),factors,floor);out.put("factors",CurvePlan.encode(factors)).put("curve_floor_nit",floor);
        String[][] caps={{"thermal_relax","thermal_supported"},{"response_override","response_supported"},{"small_brighten_override","small_response_supported"},{"low_light_stability","low_light_supported"},{"outdoor_enabled","outdoor_supported"},{"outdoor_hbm_tuning","outdoor_hbm_supported"},{"outdoor_range_unlock","outdoor_range_supported"},{"outdoor_opr_relax","outdoor_opr_supported"}};
        for(String[] pair:caps)disable(out,pair[0],runtime.optBoolean(pair[1]),notes);
        for(String group:AdvancedOptions.GROUPS)disable(out,group,runtime.optBoolean(group+"_supported"),notes);
        if(!out.getBoolean("outdoor_enabled")){out.put("outdoor_hbm_tuning",false).put("outdoor_range_unlock",false).put("outdoor_opr_relax",false);}
        if(!out.getBoolean("outdoor_range_unlock"))disable(out,"outdoor_opr_relax",false,notes);
        AdvancedOptions.parse(out).verify(runtime);OutdoorOptions.parse(out).verify(runtime);
        ThermalPolicy.validate((float)out.getDouble("thermal_ceiling"));ThermalPolicy.validateCooling((float)out.getDouble("thermal_cooling"));MemoryPolicy.validate((float)out.getDouble("memory_strength"));MemoryPolicy.validateGrouping(out.getLong("memory_window"),(float)out.getDouble("memory_lux_range"));
        DelayPolicy.validate(out.getLong("brighten_delay"),out.getLong("darken_delay"));DelayPolicy.validateSmall(out.getLong("small_brighten_delay"));LowLightPolicy.validate((float)out.getDouble("low_light_limit"),out.getLong("low_light_brighten"),out.getLong("low_light_darken"));
        Set<String> known=new HashSet<>();out.keys().forEachRemaining(known::add);Collections.addAll(known,"schema","enabled","revision","fingerprint","user_serial","curve_backend","baseline_id","reset_anchors");int unknown=0;Iterator<String> keys=source.keys();while(keys.hasNext())if(!known.contains(keys.next()))unknown++;if(unknown>0)notes.add("已忽略当前版本不认识的设置："+unknown);
        JSONObject ui=new JSONObject(),rawUi=file.optJSONObject("interface");if(rawUi!=null){ui.put("refresh_seconds",number(rawUi,"refresh_seconds",2,1,5,notes));if(ui.getDouble("refresh_seconds")!=ui.getLong("refresh_seconds"))throw new IllegalArgumentException("刷新时间必须是整数");if(rawUi.has("log_auto_scroll"))ui.put("log_auto_scroll",flag(rawUi,"log_auto_scroll"));}
        return new Imported(out,notes,ui);
    }
    static void disable(JSONObject out,String key,boolean supported,List<String> notes)throws JSONException{if(out.getBoolean(key)&&!supported){out.put(key,false);notes.add("本机不支持，已关闭："+key);}}
}
