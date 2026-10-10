package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Bounded, portable scene admission preferences. Defaults preserve every OEM decision. */
final class SceneOptions {
    static final String[] IDS={"night_driving","step","touch","proximity","stream_proximity","reflection","night_wake","manual_sunlight","game"};
    static final String[] NAMES={"夜间驾驶","运动延迟","触摸遮挡","普通距离遮挡","流式距离遮挡","反射与 AON 确认","夜间唤醒","手动模式阳光屏","游戏防误降亮"};
    static final String[] SOURCES={"effective","main","assist"},COVERS={"any","proximity_near","proximity_clear","stream_near","stream_clear","touch","touch_clear"};
    static int index(String id){return Arrays.asList(IDS).indexOf(id);}
    static void only(JSONObject j,String... keys){Set<String> allowed=new HashSet<>(Arrays.asList(keys));Iterator<String> it=j.keys();while(it.hasNext())if(!allowed.contains(it.next()))throw new IllegalArgumentException("场景参数未知");}
    static boolean flag(JSONObject j,String k,boolean def){if(!j.has(k))return def;Object v=j.opt(k);if(!(v instanceof Boolean))throw new IllegalArgumentException("场景开关无效");return (Boolean)v;}
    static double number(JSONObject j,String k,double def,double min,double max){Object v=j.has(k)?j.opt(k):def;if(!(v instanceof Number))throw new IllegalArgumentException("场景数值无效");double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("场景数值超出范围");return n;}
    static int integer(JSONObject j,String k,int def,int min,int max){double n=number(j,k,def,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException("场景时间须为整数");return (int)n;}
    static String choice(JSONObject j,String k,String def,String... choices){Object v=j.has(k)?j.opt(k):def;if(!(v instanceof String)||!Arrays.asList(choices).contains(v))throw new IllegalArgumentException("场景选项无效");return (String)v;}
    static final class Condition {
        final boolean lux,time;final String source,cover;final double min,max;final int start,end,confirm,confirmEnter,confirmExit;
        Condition(JSONObject j){only(j,"lux","source","min","max","time","start","end","cover","confirm","confirm_enter","confirm_exit");lux=flag(j,"lux",false);time=flag(j,"time",false);source=choice(j,"source","effective",SOURCES);cover=choice(j,"cover","any",COVERS);min=number(j,"min",0,0,100000);max=number(j,"max",50,0,100000);if(min>max)throw new IllegalArgumentException("最低照度不能高于最高照度");start=integer(j,"start",1320,0,1439);end=integer(j,"end",420,0,1439);confirm=integer(j,"confirm",2000,0,60000);confirmEnter=integer(j,"confirm_enter",confirm,0,60000);confirmExit=integer(j,"confirm_exit",confirm,0,60000);if(time&&start==end)throw new IllegalArgumentException("时间段起止不能相同");}
        boolean empty(){return !lux&&!time&&cover.equals("any");}
        JSONObject json()throws JSONException{return new JSONObject().put("lux",lux).put("source",source).put("min",min).put("max",max).put("time",time).put("start",start).put("end",end).put("cover",cover).put("confirm",confirm).put("confirm_enter",confirmEnter).put("confirm_exit",confirmExit);}
        // -1 means unavailable: uncertainty never becomes a fabricated match/non-match.
        int test(JSONObject facts,int minute){boolean unknown=false,match=true;
            if(time)match=start<end?minute>=start&&minute<end:minute>=start||minute<end;
            if(lux){Object raw=facts.opt(source);if(!(raw instanceof Number)||!Double.isFinite(((Number)raw).doubleValue())||((Number)raw).doubleValue()<0)unknown=true;else{double n=((Number)raw).doubleValue();match&=n>=min&&n<=max;}}
            if(!cover.equals("any")){String key=cover.startsWith("stream")?"stream_near":cover.startsWith("touch")?"touch":"proximity_near";Object raw=facts.opt(key);if(!(raw instanceof Boolean))unknown=true;else match&=(Boolean)raw==!cover.endsWith("clear");}
            return !match?0:unknown?-1:1;
        }
    }
    static final class Entry {
        final String mode;final Condition condition;
        Entry(JSONObject j){only(j,"mode","condition");mode=choice(j,"mode","system","system","block","condition");if(j.has("condition")&&!(j.opt("condition") instanceof JSONObject))throw new IllegalArgumentException("场景条件格式无效");condition=new Condition(j.optJSONObject("condition")==null?new JSONObject():j.optJSONObject("condition"));if(mode.equals("condition")&&condition.empty())throw new IllegalArgumentException("请至少设置一个场景条件");}
        JSONObject json()throws JSONException{return new JSONObject().put("mode",mode).put("condition",condition.json());}
    }
    final Entry[] entries=new Entry[IDS.length];
    SceneOptions(){for(int i=0;i<entries.length;i++)entries[i]=new Entry(new JSONObject());}
    static SceneOptions parse(JSONObject config){SceneOptions o=new SceneOptions();if(!config.has("scene_options"))return o;Object raw=config.opt("scene_options");if(!(raw instanceof JSONObject))throw new IllegalArgumentException("场景配置格式无效");JSONObject j=(JSONObject)raw;only(j,"policies","rules");if(j.has("policies")&&!(j.opt("policies") instanceof JSONObject)||j.has("rules")&&!(j.opt("rules") instanceof JSONArray))throw new IllegalArgumentException("场景配置格式无效");JSONObject policies=j.optJSONObject("policies");if(policies!=null){only(policies,IDS);for(int i=0;i<IDS.length;i++)if(policies.has(IDS[i])){if(!(policies.opt(IDS[i]) instanceof JSONObject))throw new IllegalArgumentException("场景策略格式无效");o.entries[i]=new Entry(policies.optJSONObject(IDS[i]));}}
        // Retired r8 rules are accepted only for migration and never evaluated or exported.
        return o;}
    void put(JSONObject config)throws JSONException{JSONObject p=new JSONObject();for(int i=0;i<IDS.length;i++)if(!entries[i].mode.equals("system"))p.put(IDS[i],entries[i].json());config.put("scene_options",new JSONObject().put("policies",p));}
    void verify(JSONObject capabilities){for(int i=0;i<IDS.length;i++)if(!entries[i].mode.equals("system")&&!capabilities.optBoolean("scene_"+IDS[i]+"_supported"))throw new IllegalArgumentException("此系统场景接口尚未兼容："+NAMES[i]);}
    boolean custom(){for(Entry e:entries)if(!e.mode.equals("system"))return true;return false;}
}
