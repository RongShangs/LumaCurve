package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Read-only graph data. Never applies drafts to live memory or synthesizes a learned curve. */
final class CurveComparison {
    static final class Line {
        final float[] lux,nit;
        Line(float[] lux,float[] nit){
            if(lux.length!=nit.length||lux.length<2||lux.length>4096)throw new IllegalArgumentException("Invalid curve data");
            for(int i=0;i<lux.length;i++)if(!Float.isFinite(lux[i])||lux[i]<0||!Float.isFinite(nit[i])||nit[i]<0||i>0&&lux[i]<=lux[i-1])throw new IllegalArgumentException("Invalid curve point");
            this.lux=lux.clone();this.nit=nit.clone();
        }
        float at(float value){if(value<=lux[0])return nit[0];for(int i=1;i<lux.length;i++)if(value<=lux[i])return nit[i-1]+(nit[i]-nit[i-1])*(value-lux[i-1])/(lux[i]-lux[i-1]);return nit[nit.length-1];}
    }
    final Line reference,baseline,current;
    CurveComparison(Line reference,Line baseline,Line current){this.reference=reference;this.baseline=baseline;this.current=current;}
    boolean memoryChanged(JSONObject runtime){
        if(current==null||runtime==null)return false;
        JSONArray live=runtime.optJSONArray("memory_live_points"),flags=runtime.optJSONArray("manual_anchor_flags");boolean hasLive=runtime.optInt("memory_live_count")>0;
        if(live!=null)for(int i=0;i<live.length();i++){JSONObject point=live.optJSONObject(i);double lux=point==null?Double.NaN:point.optDouble("lux",Double.NaN);if(Double.isFinite(lux)&&lux>=0){hasLive=true;break;}}
        if(flags!=null)for(int i=0;i<flags.length();i++)if(flags.optBoolean(i)){hasLive=true;break;}
        if(!hasLive)return false; // An archive is not live memory; spline sampling alone is not memory either.
        return !sameCurve(baseline,current);
    }
    boolean systemDefault(){return sameCurve(reference,baseline);}
    String kind(JSONObject runtime){return current==null?"unavailable":memoryChanged(runtime)?"memory":systemDefault()?"system":"baseline";}
    static boolean sameCurve(Line left,Line right){for(Line line:new Line[]{left,right})for(float lux:line.lux){float a=left.at(lux),b=right.at(lux);if(Math.abs(a-b)>Math.max(.01f,Math.max(a,b)*.001f))return false;}return true;}
    static float[] numbers(JSONArray a)throws JSONException{float[] out=new float[a.length()];for(int i=0;i<out.length;i++){Object v=a.get(i);if(!(v instanceof Number))throw new JSONException("Invalid curve number");out[i]=((Number)v).floatValue();}return out;}
    static CurveComparison read(JSONObject runtime,float[] factors,float floor,boolean draft)throws JSONException{
        float[] factoryLux=numbers(runtime.getJSONArray("factory_lux")),factoryNit=numbers(runtime.getJSONArray("factory_logical_nit"));
        float min=(float)runtime.getDouble("min_logical_nit"),max=(float)runtime.getDouble("max_logical_nit");
        float[] base=draft?new CurvePlan(factoryLux,factoryNit,min,max,factors,floor).nits()
            :runtime.has("active_logical_nit")?numbers(runtime.getJSONArray("active_logical_nit")):factoryNit;
        Line reference=new Line(factoryLux,factoryNit),baseline=new Line(factoryLux,base);
        if("physical_mapping".equals(runtime.optString("curve_backend"))){
            TraditionalCurve full=new TraditionalCurve(numbers(runtime.getJSONArray("factory_full_lux")),numbers(runtime.getJSONArray("factory_full_nit")));
            reference=new Line(full.lux(),full.nit());
            float[] liveFactors=new float[]{1,1,1,1};for(int i=1;i<3;i++)liveFactors[i]=base[i]==0&&factoryNit[i]==0?1:base[i]/factoryNit[i];
            baseline=new Line(full.lux(),draft?full.reshape(min,max,factors,floor):full.reshape(min,max,liveFactors,base[0]));
        }
        Line current=null;
        JSONObject frame=runtime.optJSONObject("last_pipeline");String backend=runtime.optString("curve_backend");
        boolean activeRoute=frame==null||"refactor".equals(backend)&&"refactor".equals(frame.optString("route"))
            ||"physical_mapping".equals(backend)&&"mapping".equals(frame.optString("route"));
        if(activeRoute&&(!"physical_mapping".equals(backend)||runtime.optBoolean("physical_mapping_active")))
            try{current=new Line(numbers(runtime.getJSONArray("current_anchors_lux")),numbers(runtime.getJSONArray("current_anchors_nit")));}catch(JSONException|IllegalArgumentException unavailable){/* Show unavailable; never infer it from saved anchors. */}
        return new CurveComparison(reference,baseline,current);
    }
}
