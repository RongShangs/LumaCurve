package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Snapshot boundary: decode native/legacy differences before shared graph/configuration code. */
final class CurveCompatibility {
    static final String KEY="curve_contract";
    static final class Profile {
        final String coordinate,interpolation,description;
        final boolean strict,currentAvailable,save;
        final int savedLimit,liveCount;
        Profile(String coordinate,String interpolation,String description,boolean strict,boolean current,boolean save,int limit,int live){
            this.coordinate=coordinate;this.interpolation=interpolation;this.description=description;this.strict=strict;currentAvailable=current;this.save=save;savedLimit=limit;liveCount=live;
        }
    }
    static boolean normalPipeline(JSONObject state,String route){JSONObject frame=state.optJSONObject("last_pipeline");return frame==null||route.equals(frame.optString("route"));}
    static JSONObject descriptor(String coordinate,String interpolation,String description,boolean strict,boolean current,boolean save,int limit,int live)throws JSONException{
        return new JSONObject().put("schema",1).put("coordinate",coordinate).put("interpolation",interpolation).put("memory_description",description).put("strict_order",strict).put("current_available",current).put("save_supported",save).put("saved_point_limit",limit).put("live_count",live);
    }
    static Profile read(JSONObject state){
        if(state==null)throw new IllegalArgumentException("曲线状态尚未就绪");
        // An absent old field can be decoded; an invalid new contract must never downgrade.
        if(state.has(KEY))try{
            JSONObject c=state.getJSONObject(KEY);
            if(integer(c,"schema")!=1||!(c.get("strict_order") instanceof Boolean)||!(c.get("current_available") instanceof Boolean)||!(c.get("save_supported") instanceof Boolean))throw new JSONException("Invalid curve contract");
            String coordinate=c.getString("coordinate"),interpolation=c.getString("interpolation");
            int limit=integer(c,"saved_point_limit"),live=integer(c,"live_count");
            if(!coordinate.equals(state.getString("curve_coordinate"))||!(coordinate.equals("physical_nit")||coordinate.equals("logical_nit"))||!(interpolation.equals("anchors")||interpolation.equals("physical_spline")||interpolation.equals("coefficients"))||interpolation.equals("physical_spline")!=coordinate.equals("physical_nit")||limit<0||limit>SavedCurve.MAX_POINTS||live<0||live>SavedCurve.MAX_POINTS||c.getBoolean("save_supported")&&limit<2)throw new JSONException("Invalid curve contract domain");
            return new Profile(coordinate,interpolation,c.getString("memory_description"),c.getBoolean("strict_order"),c.getBoolean("current_available"),c.getBoolean("save_supported"),limit,live);
        }catch(JSONException bad){throw new IllegalArgumentException("曲线兼容契约无效",bad);}
        return legacy(state);
    }
    // Kept in the boundary only, so old exported/live state remains readable without UI selectors.
    private static Profile legacy(JSONObject state){
        String backend=state.optString("curve_backend"),route=state.optString("curve_route");
        boolean physical=backend.equals("physical_mapping"),coefficients=route.equals("refactor_coefficients"),known=physical||backend.equals("refactor");
        int count=state.optJSONArray("current_anchors_lux")==null?0:state.optJSONArray("current_anchors_lux").length();
        String description=coefficients?"本机采用分段拟合；仅报告最近一次手动输入":physical?"本机保留完整基础曲线，手动模型支持 1 个偏好点":"当前曲线节点："+count+" / 7（包含基础与手动点）";
        boolean current=known&&normalPipeline(state,physical?"mapping":"refactor")&&(!physical||state.optBoolean("physical_mapping_active"));
        return new Profile(physical?"physical_nit":"logical_nit",physical?"physical_spline":coefficients?"coefficients":"anchors",description,coefficients,current,known&&!coefficients,physical?SavedCurve.MAX_POINTS:known&&!coefficients?7:0,legacyLiveCount(state,physical));
    }
    private static int legacyLiveCount(JSONObject state,boolean physical){
        if(state.has("memory_live_count"))return Math.max(0,state.optInt("memory_live_count"));
        if(physical)return state.optDouble("user_anchor_lux",-1)>=0?1:0;
        JSONArray flags=state.optJSONArray("manual_anchor_flags");int live=0;if(flags!=null)for(int i=0;i<flags.length();i++)if(flags.optBoolean(i))live++;return live;
    }
    // No backend names leave this compatibility decoder. Coordinates stay explicitly tagged.
    static final class Curves {
        float[] referenceLux,referenceNit,baselineLux,baselineNit,currentLux,currentNit;
    }
    static Curves curves(JSONObject state,float[] factors,float floor,boolean draft,SavedCurve saved)throws JSONException{
        Profile profile=read(state);Curves out=new Curves();
        float[] x=numbers(state.getJSONArray("factory_lux")),y=numbers(state.getJSONArray("factory_logical_nit"));
        float min=(float)state.getDouble("min_logical_nit"),max=(float)state.getDouble("max_logical_nit");
        float[] base=draft?new CurvePlan(x,saved==null?y:saved.controls(x),min,max,factors,floor).nits():state.has("active_logical_nit")?numbers(state.getJSONArray("active_logical_nit")):y;
        out.referenceLux=x;out.referenceNit=y;out.baselineLux=x;out.baselineNit=base;
        if(profile.interpolation.equals("physical_spline")){
            TraditionalCurve full=new TraditionalCurve(numbers(state.getJSONArray("factory_full_lux")),numbers(state.getJSONArray("factory_full_nit")));
            out.referenceLux=full.lux();out.referenceNit=full.nit();
            float[] live={1,1,1,1};for(int i=1;i<4;i++)live[i]=base[i]==0&&y[i]==0?1:base[i]/y[i];
            if(!(draft&&saved!=null)&&!(!draft&&state.has("applied_base_lux")&&state.has("applied_base_nit"))){out.baselineLux=full.lux();out.baselineNit=draft?full.reshape(min,max,factors,floor):full.reshape(min,max,live,base[0]);}
        }
        if(draft&&saved!=null){CurvePlan plan=SavedCurve.plan(saved,x,y,min,max,factors,floor);out.baselineLux=plan.lux();out.baselineNit=plan.nits();}
        if(!draft&&state.has("applied_base_lux")&&state.has("applied_base_nit")){out.baselineLux=numbers(state.getJSONArray("applied_base_lux"));out.baselineNit=numbers(state.getJSONArray("applied_base_nit"));}
        if(profile.currentAvailable)try{out.currentLux=numbers(state.getJSONArray("current_anchors_lux"));out.currentNit=numbers(state.getJSONArray("current_anchors_nit"));}catch(JSONException unavailable){out.currentLux=out.currentNit=null;}
        return out;
    }
    static void verifySaved(Profile profile,int points){if(!profile.save||points>profile.savedLimit)throw new IllegalArgumentException("当前曲线路径或节点数量不支持保存基础曲线");}
    static void verifySaved(String backend,int points){int limit=backend.equals("refactor")?7:backend.equals("physical_mapping")?SavedCurve.MAX_POINTS:0;if(limit==0||points>limit)throw new IllegalArgumentException("当前曲线路径或节点数量不支持保存基础曲线");}
    static SavedCurve capture(JSONObject state)throws JSONException{
        Profile p=read(state);
        if(!p.save)throw new IllegalArgumentException("此曲线路径尚不支持记忆转基础曲线");
        if(!"active".equals(state.optString("phase"))||p.liveCount==0)throw new IllegalArgumentException("当前没有可保存的手动记忆曲线");
        if(state.optBoolean("hdr_active")||!p.currentAvailable)throw new IllegalArgumentException("当前输出路径不是可保存的普通亮度曲线");
        float min=(float)state.getDouble("min_logical_nit"),max=(float)state.getDouble("max_logical_nit");float[] samples=SavedCurve.numbers(state.getJSONArray("current_anchors_nit"));
        if(p.coordinate.equals("physical_nit"))samples=physicalCapture(samples,min,max);
        SavedCurve curve=new SavedCurve(SavedCurve.numbers(state.getJSONArray("current_anchors_lux")),samples);
        verifySaved(p,curve.lux.length);
        float[] handles=SavedCurve.numbers(state.getJSONArray("factory_lux"));curve.validate(min,max,handles[3]);return curve;
    }
    private static float[] physicalCapture(float[] samples,float min,float max){
        if(!Float.isFinite(min)||!Float.isFinite(max)||min<0||max<=min)throw new IllegalArgumentException("保存的基础曲线无效");float[] values=samples.clone();
        for(int i=0;i<values.length;i++){float v=values[i];if(!Float.isFinite(v))throw new IllegalArgumentException("保存的基础曲线节点无效");if(v<min&&roundingOnly(v,min))v=min;if(v>max&&roundingOnly(v,max))v=max;if(i>0&&v<values[i-1]&&roundingOnly(v,values[i-1]))v=values[i-1];values[i]=v;}return values;
    }
    private static boolean roundingOnly(float a,float b){return Math.abs(a-b)<=4f*Math.max(Math.ulp(a),Math.ulp(b));}
    private static int integer(JSONObject j,String key)throws JSONException{Object raw=j.get(key);if(!(raw instanceof Number))throw new JSONException("Invalid curve contract integer");double n=((Number)raw).doubleValue();if(!Double.isFinite(n)||n!=Math.rint(n)||n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new JSONException("Invalid curve contract integer");return (int)n;}
    private static float[] numbers(JSONArray a)throws JSONException{if(a.length()>4096)throw new JSONException("Invalid curve size");float[] out=new float[a.length()];for(int i=0;i<out.length;i++){Object v=a.get(i);if(!(v instanceof Number))throw new JSONException("Invalid curve number");out[i]=((Number)v).floatValue();}return out;}
}
