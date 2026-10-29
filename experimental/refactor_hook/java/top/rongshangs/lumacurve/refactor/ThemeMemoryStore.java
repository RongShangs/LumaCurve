package top.rongshangs.lumacurve.refactor;

import java.util.HashMap;
import java.util.Map;
import org.json.*;

/** Session-only theme banks. Disk persistence remains an explicit, separate option. */
final class ThemeMemoryStore {
    private final Map<String,String> records=new java.util.LinkedHashMap<String,String>(4,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>3;}};
    void remember(String scope,String raw){if(raw!=null&&!raw.isEmpty())records.put(scope,raw);}
    String get(String scope){return records.get(scope);}
    void clear(String scope){records.remove(scope);}
    void clear(){records.clear();}
    static String key(String suffix){return PersistentMemory.KEY+(suffix.equals(":independent-dark")?"_dark":suffix.equals(":independent-light")?"_light":"");}
    static String transfer(String raw,String oldScope,String newScope,int days,boolean forever)throws JSONException{
        long now=System.currentTimeMillis();JSONArray points=PersistentMemory.read(raw,oldScope,now,days,forever);
        if(points.length()==0)return null;
        JSONObject out=PersistentMemory.encode(newScope,points,new JSONObject(raw).getLong("saved_at"));
        JSONObject model=PersistentMemory.model(raw);if(model!=null)out.put("model",model);
        return out.toString();
    }
}
