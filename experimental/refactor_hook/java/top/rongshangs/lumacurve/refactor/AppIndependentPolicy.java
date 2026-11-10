package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Fixed framework-ratio preferences, never sampled window/HDR output. */
final class AppIndependentPolicy {
    final Map<String,Float> values=new LinkedHashMap<>();String binding="";
    synchronized void clear(){values.clear();}
    synchronized void bind(String identity){if(!binding.equals(identity)){clear();binding=identity;}}
    synchronized float get(String key,float fallback,float ceiling){Float value=values.get(key);float chosen=value==null?fallback:value;return Float.isFinite(chosen)&&Float.isFinite(ceiling)&&ceiling>0?Math.max(0,Math.min(chosen,ceiling)):Float.NaN;}
    static boolean ordinary(float value,float ceiling){return Float.isFinite(value)&&value>0&&value<1&&Float.isFinite(ceiling)&&ceiling>0&&ceiling<=1&&value<=ceiling;}
    synchronized boolean remember(String key,float value,float ceiling){if(key.isEmpty()||!ordinary(value,ceiling))return false;values.put(key,value);return true;}
    synchronized void retain(BrightnessControlOptions options){Set<String> keys=new HashSet<>();for(String pkg:options.independentApps)keys.add("app:"+pkg);for(BrightnessControlOptions.AppGroup g:options.independentGroups)keys.add("group:"+g.id);values.keySet().retainAll(keys);}
    synchronized JSONObject export(String fingerprint)throws JSONException{JSONObject data=new JSONObject();for(Map.Entry<String,Float> e:values.entrySet())data.put(e.getKey(),e.getValue());return new JSONObject().put("schema",1).put("fingerprint",fingerprint).put("binding",binding).put("user",0).put("values",data);}
    synchronized void merge(String raw,String fingerprint)throws JSONException{
        if(raw==null||raw.isEmpty())return;if(raw.length()>32768)throw new IllegalArgumentException("独立亮度记忆过大");JSONObject root=new JSONObject(raw);
        if(root.optInt("schema")!=1||root.optInt("user",-1)!=0||!fingerprint.equals(root.optString("fingerprint"))||!binding.equals(root.optString("binding")))return;
        JSONObject data=root.getJSONObject("values");if(data.length()>256)throw new IllegalArgumentException("独立亮度记忆过多");Map<String,Float> valid=new LinkedHashMap<>();Iterator<String> names=data.keys();while(names.hasNext()){String key=names.next();Object value=data.get(key);if(!(value instanceof Number))throw new IllegalArgumentException("独立亮度记忆无效");float n=((Number)value).floatValue();if(!(key.startsWith("app:")&&BrightnessControlOptions.validPackage(key.substring(4))||key.matches("group:[a-zA-Z0-9_-]{1,40}"))||!ordinary(n,1))throw new IllegalArgumentException("独立亮度记忆无效");valid.put(key,n);}for(Map.Entry<String,Float> e:valid.entrySet())values.putIfAbsent(e.getKey(),e.getValue());
    }
}
