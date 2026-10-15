package top.rongshangs.lumacurve.refactor;

import org.json.*;
import java.util.*;

/** Device-bound baseline captured from the actual green graph; no four-point fitting. */
public final class SavedCurve {
    public static final String KEY="saved_base_curve";
    public static final int MAX_POINTS=512;
    public final float[] lux,nit;
    public SavedCurve(float[] x,float[] y){
        if(x==null||y==null||x.length!=y.length||x.length<2||x.length>MAX_POINTS)throw new IllegalArgumentException("当前曲线节点数量无法安全保存");
        CurvePlan.points(x,y,0,Float.MAX_VALUE);lux=x.clone();nit=y.clone();
    }
    static float[] numbers(JSONArray a)throws JSONException {
        if(a==null||a.length()>MAX_POINTS)throw new IllegalArgumentException("保存的曲线数据无效");float[] out=new float[a.length()];
        for(int i=0;i<out.length;i++){Object v=a.get(i);if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw new IllegalArgumentException("保存的曲线数值无效");out[i]=((Number)v).floatValue();}return out;
    }
    public static SavedCurve parse(JSONObject options)throws JSONException {
        if(options==null||!options.has(KEY))return null;Object raw=options.get(KEY);if(!(raw instanceof JSONObject))throw new IllegalArgumentException("保存的基础曲线格式无效");
        JSONObject curve=(JSONObject)raw;return new SavedCurve(numbers(curve.getJSONArray("lux")),numbers(curve.getJSONArray("nit")));
    }
    public void validate(float min,float max,float last){CurvePlan.points(lux,nit,min,max);if(lux[lux.length-1]!=last)throw new IllegalArgumentException("保存的曲线与本机照度范围不同");}
    public void verifyBackend(String backend){if("refactor".equals(backend)&&lux.length>7)throw new IllegalArgumentException("本机最多支持 7 个基础曲线节点");if(!"refactor".equals(backend)&&!"physical_mapping".equals(backend))throw new IllegalArgumentException("当前曲线路径不支持保存基础曲线");}
    public float at(float value){if(value<=lux[0])return nit[0];for(int i=1;i<lux.length;i++)if(value<=lux[i])return nit[i-1]+(nit[i]-nit[i-1])*(value-lux[i-1])/(lux[i]-lux[i-1]);return nit[nit.length-1];}
    public float[] controls(float[] handles){float[] y=new float[handles.length];for(int i=0;i<y.length;i++)y[i]=at(handles[i]);return y;}
    public void put(JSONObject options)throws JSONException{options.put(KEY,new JSONObject().put("lux",new JSONArray(lux)).put("nit",new JSONArray(nit)));}
    public static CurvePlan plan(SavedCurve saved,float[] x,float[] y,float min,float max,float[] factors,float floor){
        if(saved==null)return new CurvePlan(x,y,min,max,factors,floor);
        saved.validate(min,max,x[3]);float[] base=saved.controls(x),desired=new CurvePlan(x,base,min,max,factors,floor).nits();
        // Keep the OEM point budget; a Refactor curve can already occupy all seven slots.
        TreeSet<Float> points=new TreeSet<>();for(float v:saved.lux)points.add(v);
        float[] xx=new float[points.size()],yy=new float[points.size()];int i=0;
        for(float v:points){int segment=0;while(segment<2&&v>x[segment+1])segment++;
            float original=saved.at(v),span=base[segment+1]-base[segment];
            float t=span>0?(original-base[segment])/span:(v-x[segment])/(x[segment+1]-x[segment]);
            xx[i]=v;yy[i]=desired[segment]==base[segment]&&desired[segment+1]==base[segment+1]?original:desired[segment]+t*(desired[segment+1]-desired[segment]);i++;
        }
        return CurvePlan.points(xx,yy,min,max);
    }
    public static boolean same(SavedCurve a,SavedCurve b){return a==b||a!=null&&b!=null&&Arrays.equals(a.lux,b.lux)&&Arrays.equals(a.nit,b.nit);}
    public static SavedCurve capture(JSONObject state)throws JSONException {
        if(!"active".equals(state.optString("phase"))||state.optInt("memory_live_count")==0)throw new IllegalArgumentException("当前没有可保存的手动记忆曲线");
        String backend=state.optString("curve_backend");JSONObject pipeline=state.optJSONObject("last_pipeline");
        if(state.optBoolean("hdr_active")||"physical_mapping".equals(backend)&&!state.optBoolean("physical_mapping_active")||pipeline!=null&&!("refactor".equals(backend)&&"refactor".equals(pipeline.optString("route"))||"physical_mapping".equals(backend)&&"mapping".equals(pipeline.optString("route"))))throw new IllegalArgumentException("当前输出路径不是可保存的普通亮度曲线");
        SavedCurve curve=new SavedCurve(numbers(state.getJSONArray("current_anchors_lux")),numbers(state.getJSONArray("current_anchors_nit")));curve.verifyBackend(backend);
        float[] handles=numbers(state.getJSONArray("factory_lux"));curve.validate((float)state.getDouble("min_logical_nit"),(float)state.getDouble("max_logical_nit"),handles[3]);return curve;
    }
}
