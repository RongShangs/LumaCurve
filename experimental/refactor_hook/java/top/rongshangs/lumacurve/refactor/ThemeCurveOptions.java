package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Two device-bound base profiles. Theme selection changes neither the stored light profile nor protections. */
public final class ThemeCurveOptions {
    public static final String KEY="dark_mode_curve";
    public boolean enabled,initialized;
    public float[] factors={1,1,1,1};
    public float floor;
    public SavedCurve saved;
    public static ThemeCurveOptions parse(JSONObject config)throws JSONException {
        ThemeCurveOptions out=new ThemeCurveOptions();if(config==null||!config.has(KEY))return out;
        Object raw=config.get(KEY);if(!(raw instanceof JSONObject))throw new IllegalArgumentException("深色模式基础曲线格式无效");JSONObject value=(JSONObject)raw;
        if(value.has("enabled")&&!(value.get("enabled") instanceof Boolean))throw new IllegalArgumentException("深色模式曲线开关无效");
        out.enabled=value.optBoolean("enabled",false);out.factors=CurvePlan.factors(value.optString("factors","1,1,1,1"));
        if(value.has("initialized")&&!(value.get("initialized") instanceof Boolean))throw new IllegalArgumentException("深色模式曲线初始化状态无效");
        out.initialized=value.optBoolean("initialized",out.enabled||value.has("factors")||value.has(SavedCurve.KEY));
        out.floor=CurvePlan.floor(value.has("curve_floor_nit")?value.opt("curve_floor_nit"):null);out.saved=SavedCurve.parse(value);return out;
    }
    public void verify(String backend,float[] lux,float[] nit,float min,float max){
        if(factors[3]!=1f)throw new IllegalArgumentException("深色模式最高照度节点保持设备原始值");
        if(saved!=null)saved.verifyBackend(backend);SavedCurve.plan(saved,lux,nit,min,max,factors,floor);
    }
    public JSONObject profile()throws JSONException {JSONObject value=new JSONObject().put("factors",CurvePlan.encode(factors)).put("curve_floor_nit",floor);if(saved!=null)saved.put(value);return value;}
    public void put(JSONObject config)throws JSONException {config.put(KEY,profile().put("enabled",enabled).put("initialized",initialized||enabled||saved!=null));}
    public JSONObject select(JSONObject config,boolean dark)throws JSONException {
        if(!enabled||!dark)return config;JSONObject out=new JSONObject(config.toString());out.put("factors",CurvePlan.encode(factors)).put("curve_floor_nit",floor);out.remove(SavedCurve.KEY);if(saved!=null)saved.put(out);return out;
    }
    public static String scope(boolean enabled,boolean dark){return enabled?dark?":theme-dark":":theme-light":"";}
}
