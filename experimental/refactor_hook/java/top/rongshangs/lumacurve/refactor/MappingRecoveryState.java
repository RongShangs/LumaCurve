package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** One protected readiness signal, never a gesture, node write, or Activity launch. */
final class MappingRecoveryState {
    static final String[] IDENTITY={"build","pid","process_start","boot_count","revision","user_serial","mode","maximum","physical"};
    static JSONObject identity(JSONObject profile)throws JSONException{JSONObject result=new JSONObject();for(String key:IDENTITY)if(profile.has(key))result.put(key,profile.get(key));return result;}
    static String generation(JSONObject tables,String boot){
        if(tables==null||!tables.optBoolean("enabled")||!AppBuild.BUILD.equals(tables.optString("build"))||tables.optInt("pid",0)<=0||tables.optString("process_start").isEmpty()||tables.optString("revision").isEmpty()||tables.optInt("user_serial",-1)!=0||tables.optInt("mode",0)<1||tables.optInt("mode",0)>4||boot==null||!boot.equals(tables.optString("boot_count"))||tables.optJSONObject("calibration")==null)return "";
        try{return identity(tables).toString();}catch(JSONException invalid){return "";}
    }
    static boolean acknowledged(JSONObject profile,JSONObject ui){
        if(profile==null||!AppBuild.BUILD.equals(profile.optString("build"))||ui==null||!AppBuild.BUILD.equals(ui.optString("build"))||ui.optInt("pid",0)<=0||ui.optString("process_start").isEmpty()||!ui.optBoolean("coordinate_abi")||!ui.optBoolean("profile_ready"))return false;
        JSONObject bound=ui.optJSONObject("bound_profile");if(bound==null)return false;
        for(String key:IDENTITY)if(!String.valueOf(profile.opt(key)).equals(String.valueOf(bound.opt(key))))return false;
        return true;
    }
    static final class Gate {
        String key="";int attempts;long last;
        synchronized boolean claim(String next,long now){if(next.isEmpty())return false;if(!next.equals(key)){key=next;attempts=0;}if(attempts>=2||attempts>0&&(now<last||now-last<5000))return false;attempts++;last=now;return true;}
    }
}
