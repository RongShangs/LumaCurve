package top.rongshangs.lumacurve.refactor;

import java.util.*;
import org.json.*;

/** Latest observations, separate from the deduplicated historical trace. */
final class LiveLimitEvidence {
    static final long MAX_AGE_MS=5000;
    static final class Value {float before,after;long at;}
    final Map<String,Value> values=new LinkedHashMap<>();
    static boolean stage(String name){switch(name){
        case "adjustBrightnessByOpr":case "adjustBrightnessByThermal":case "adjustBrightnessByBattery":
        case "adjustBrightnessByPowerSaveMode":case "adjustBrightnessToPeak":case "adjustBrightnessByBcbc":
        case "adjustSdrBrightness":return true;default:return false;
    }}
    void record(String name,float before,float after,long now){
        if(!stage(name)||!Float.isFinite(before)||!Float.isFinite(after)||before<0||after<0||before>1||after>1||now<0)return;
        Value value=values.get(name);if(value==null){value=new Value();values.put(name,value);}
        value.before=before;value.after=after;value.at=now;
    }
    void clear(){values.clear();}
    JSONArray snapshot(long now)throws JSONException{
        JSONArray out=new JSONArray();for(Map.Entry<String,Value> entry:values.entrySet()){
            Value v=entry.getValue();long age=now-v.at;if(age<0||age>MAX_AGE_MS)continue;
            out.put(new JSONObject().put("stage",entry.getKey()).put("before",v.before).put("after",v.after)
                .put("uptime_ms",v.at).put("limited",v.after<v.before-1e-5f));
        }return out;
    }
}
