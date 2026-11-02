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
        return read(runtime,factors,floor,draft,null);
    }
    static CurveComparison read(JSONObject runtime,float[] factors,float floor,boolean draft,SavedCurve saved)throws JSONException{
        CurveCompatibility.Curves data=CurveCompatibility.curves(runtime,factors,floor,draft,saved);
        Line current=null;
        if(data.currentLux!=null&&data.currentNit!=null)try{current=new Line(data.currentLux,data.currentNit);}catch(IllegalArgumentException unavailable){/* No archive or synthetic curve substitutes for live memory. */}
        return new CurveComparison(new Line(data.referenceLux,data.referenceNit),new Line(data.baselineLux,data.baselineNit),current);
    }
}
