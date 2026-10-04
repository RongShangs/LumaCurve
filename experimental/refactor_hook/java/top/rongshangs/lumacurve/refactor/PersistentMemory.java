package top.rongshangs.lumacurve.refactor;

import java.nio.charset.StandardCharsets;
import org.json.*;

/** Only bounded OEM user anchors are saved. No brightness controller or sensor polling. */
final class PersistentMemory {
    static final String KEY="lumacurve_manual_memory_v1";
    static final int LIMIT=8192,MAX_POINTS=7;
    static JSONObject encode(String scope,JSONArray points,long now)throws JSONException {
        validate(points);if(scope==null||scope.isEmpty()||scope.length()>256||now<=0)throw new IllegalArgumentException("记忆身份无效");
        JSONObject j=new JSONObject().put("schema",1).put("scope",scope).put("saved_at",now).put("points",points);
        if(j.toString().getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IllegalArgumentException("记忆数据过大");return j;
    }
    static JSONArray read(String raw,String scope,long now,int days)throws JSONException {
        new MemoryOptions(true,days);if(raw==null||raw.isEmpty())return new JSONArray();
        if(raw.length()>LIMIT||raw.getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IllegalArgumentException("记忆数据过大");
        JSONObject j=new JSONObject(raw);Object schema=j.get("schema");if(!(schema instanceof Number)||((Number)schema).doubleValue()!=1)throw new IllegalArgumentException("记忆格式不兼容");
        Object timestamp=j.get("saved_at");if(!(timestamp instanceof Number)||((Number)timestamp).doubleValue()!=Math.rint(((Number)timestamp).doubleValue()))throw new IllegalArgumentException("记忆时间无效");
        long saved=j.getLong("saved_at");
        if(!scope.equals(j.getString("scope"))||saved<=0||saved>now||now-saved>days*86400000L)return new JSONArray();
        JSONArray points=j.getJSONArray("points");validate(points);return new JSONArray(points.toString());
    }
    static JSONObject model(String raw)throws JSONException {
        if(raw==null||raw.length()>LIMIT)return null;JSONObject j=new JSONObject(raw),model=j.optJSONObject("model");if(model==null)return null;
        Object x=model.opt("lux"),y=model.opt("brightness");if(!(x instanceof Number)||!(y instanceof Number))return null;
        double lux=((Number)x).doubleValue(),br=((Number)y).doubleValue();if(!Double.isFinite(lux)||lux<0||lux>Float.MAX_VALUE||!Double.isFinite(br)||br<0||br>1)return null;
        return new JSONObject(model.toString());
    }
    static void validate(JSONArray points)throws JSONException {
        if(points.length()>MAX_POINTS)throw new IllegalArgumentException("手动记忆节点过多");
        for(int i=0;i<points.length();i++){
            JSONObject p=points.getJSONObject(i);for(String k:new String[]{"lux","value"}){Object v=p.get(k);if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue())||((Number)v).doubleValue()<0||((Number)v).doubleValue()>Float.MAX_VALUE)throw new IllegalArgumentException("手动记忆节点无效");}
            for(int a=0;a<i;a++)if((float)points.getJSONObject(a).getDouble("lux")== (float)p.getDouble("lux"))throw new IllegalArgumentException("手动记忆照度重复");
        }
    }
}
