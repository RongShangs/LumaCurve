package top.rongshangs.lumacurve.refactor;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** SettingsProvider caps each string. Keep live state separate from bounded histories. */
final class StatusTransport {
    static final int LIMIT=30000,CHUNK=12000,MAX_PARTS=32;
    static final String KEY="lumacurve_refactor_status_v1";
    static final String[] SECTIONS={"logs","pipeline_trace","output_trace","outdoor.limit_trace","factory_full_lux","factory_full_nit","current_anchors_lux","current_anchors_nit","memory_lifecycle","memory_live_points","memory_saved_anchors"};
    interface Reader{String get(String key)throws Exception;}
    static JSONObject copy(JSONObject source)throws JSONException{
        JSONObject out=new JSONObject();Iterator<String> keys=source.keys();while(keys.hasNext()){String key=keys.next();out.put(key,source.get(key));}return out;
    }
    static String key(int section,int part){return KEY+"_"+section+"_"+part;}
    static boolean fits(String text){return text.length()<=LIMIT&&text.getBytes(StandardCharsets.UTF_8).length<=LIMIT;}
    static LinkedHashMap<String,String> encode(JSONObject status)throws Exception{
        JSONObject core=copy(status);JSONObject outdoor=core.optJSONObject("outdoor");if(outdoor!=null)core.put("outdoor",copy(outdoor));
        String snapshot=UUID.randomUUID().toString();JSONObject parts=new JSONObject();LinkedHashMap<String,String> writes=new LinkedHashMap<>();
        for(int i=0;i<SECTIONS.length;i++){
            String name=SECTIONS[i];JSONObject container=core;String field=name;
            if(name.startsWith("outdoor.")){container=core.optJSONObject("outdoor");field=name.substring(8);}
            if(container==null||!container.has(field))continue;
            String data=container.getJSONArray(field).toString();container.remove(field);int count=0;
            for(int offset=0;offset<data.length();){
                if(count>=MAX_PARTS)throw new IOException("状态记录超过传输范围："+name);
                int end=Math.min(data.length(),offset+CHUNK);
                if(end<data.length()&&Character.isHighSurrogate(data.charAt(end-1)))end--;
                String encoded;
                while(true){encoded=new JSONObject().put("snapshot",snapshot).put("data",data.substring(offset,end)).toString();if(fits(encoded))break;end=offset+(end-offset)/2;if(end<=offset)throw new IOException("状态记录无法拆分");if(Character.isHighSurrogate(data.charAt(end-1)))end--;}
                writes.put(key(i,count++),encoded);offset=end;
            }
            parts.put(name,count);
        }
        core.put("status_transport",new JSONObject().put("schema",1).put("snapshot",snapshot).put("parts",parts));
        String serialized=core.toString();if(!fits(serialized))throw new IOException("实时状态超过传输范围");
        // Publish the manifest last; old chunks can never masquerade as this snapshot.
        writes.put(KEY,serialized);return writes;
    }
    static JSONObject restore(JSONObject core,Reader reader){
        JSONObject transport=core.optJSONObject("status_transport");if(transport==null)return core;
        JSONArray missing=new JSONArray();JSONObject parts=transport.optJSONObject("parts");String snapshot=transport.optString("snapshot");
        if(transport.optInt("schema")!=1||parts==null||snapshot.isEmpty())return core;
        for(int i=0;i<SECTIONS.length;i++){
            String name=SECTIONS[i];if(!parts.has(name))continue;
            try{int count=parts.getInt(name);if(count<1||count>MAX_PARTS)throw new IOException("无效记录分片");StringBuilder data=new StringBuilder();
                for(int p=0;p<count;p++){String raw=reader.get(key(i,p));if(raw==null||!fits(raw))throw new IOException("记录分片缺失");JSONObject chunk=new JSONObject(raw);if(!snapshot.equals(chunk.getString("snapshot")))throw new IOException("记录快照已更新");data.append(chunk.getString("data"));}
                JSONArray values=new JSONArray(data.toString());if(name.startsWith("outdoor.")){JSONObject outdoor=core.optJSONObject("outdoor");if(outdoor==null)throw new IOException("户外状态缺失");outdoor.put(name.substring(8),values);}else core.put(name,values);
            }catch(Exception unavailable){missing.put(name);}
        }
        if(missing.length()>0)try{core.put("status_missing_sections",missing);}catch(JSONException ignored){}
        return core;
    }
}
