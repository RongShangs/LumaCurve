package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Persisted independently of the device's curve and native brightness coordinates. */
final class OutdoorOptions {
    static final String[] FLAGS={"outdoor_enabled","outdoor_hbm_tuning","outdoor_range_unlock","outdoor_opr_relax"};
    static final int MAX_NIT=10;
    static final String[] KEYS={"outdoor_enter_lux","outdoor_full_lux","outdoor_enter_ms","outdoor_exit_ms","outdoor_exit_ratio","outdoor_strength","outdoor_session_ms","outdoor_cooldown_ms","outdoor_hbm_lux_factor","outdoor_hbm_budget_factor","outdoor_max_nit"};
    static final double[] DEFAULT={10000,30000,3000,5000,.65,.85,180000,60000,1,1,0};
    static final double[] MIN={2000,4000,500,1000,.4,.1,30000,15000,.25,1,0};
    static final double[] MAX={50000,100000,10000,15000,.9,1,600000,300000,1.5,2,20000};
    static final double[] STEP={1000,2000,500,500,.05,.05,30000,15000,.05,.1,1};
    final boolean[] flags=new boolean[FLAGS.length];final double[] values=DEFAULT.clone();boolean nodeBoost;int nodeTarget;
    static final String OPR_CURVE_ENABLED="outdoor_opr_curve_enabled",OPR_CURVE="outdoor_opr_curve";
    OutdoorTargetCurve targetCurve=new OutdoorTargetCurve();
    boolean oprCurveEnabled;OprCurve oprCurve=new OprCurve(new double[0],new double[0]);
    /** Physical nit ceiling versus ambient lux, independent of the native lux curve. */
    static final class OprCurve {
        static final int LIMIT=16;final double[] lux,nit;
        OprCurve(double[] x,double[] y){
            if(x==null||y==null||x.length!=y.length||x.length>LIMIT)throw new IllegalArgumentException("高亮曲线最多16个节点");
            lux=x.clone();nit=y.clone();
            for(int i=0;i<lux.length;i++)if(!Double.isFinite(lux[i])||lux[i]<0||lux[i]>1000000||lux[i]!=Math.rint(lux[i])||!Double.isFinite(nit[i])||nit[i]<=0||nit[i]>20000||i>0&&(lux[i]<=lux[i-1]||nit[i]<nit[i-1]))throw new IllegalArgumentException("照度须递增，nit须大于0且不递减");
        }
        static OprCurve parse(Object raw){
            if(raw==null)return new OprCurve(new double[0],new double[0]);
            if(!(raw instanceof JSONArray))throw new IllegalArgumentException("高亮曲线须为节点数组");
            JSONArray a=(JSONArray)raw;if(a.length()>LIMIT)throw new IllegalArgumentException("高亮曲线最多16个节点");double[] x=new double[a.length()],y=new double[a.length()];
            for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p==null||p.length()!=2||!(p.opt("lux") instanceof Number)||!(p.opt("nit") instanceof Number))throw new IllegalArgumentException("高亮节点须包含lux和nit数值");x[i]=((Number)p.opt("lux")).doubleValue();y[i]=((Number)p.opt("nit")).doubleValue();}return new OprCurve(x,y);
        }
        JSONArray json()throws JSONException{JSONArray a=new JSONArray();for(int i=0;i<lux.length;i++)a.put(new JSONObject().put("lux",lux[i]).put("nit",nit[i]));return a;}
        double at(float light){if(lux.length<2||!Float.isFinite(light)||light<0)return Double.NaN;if(light<=lux[0])return nit[0];for(int i=1;i<lux.length;i++)if(light<=lux[i])return nit[i-1]+(nit[i]-nit[i-1])*(light-lux[i-1])/(lux[i]-lux[i-1]);return nit[nit.length-1];}
        boolean same(OprCurve other){return java.util.Arrays.equals(lux,other.lux)&&java.util.Arrays.equals(nit,other.nit);}
        OprCurve change(double oldLux,double x,double y,boolean remove){
            java.util.TreeMap<Double,Double> points=new java.util.TreeMap<>();for(int i=0;i<lux.length;i++)points.put(lux[i],nit[i]);
            if(Double.isFinite(oldLux)&&points.remove(oldLux)==null)throw new IllegalArgumentException("节点已变化，请重新打开");
            if(!remove){if(points.containsKey(x))throw new IllegalArgumentException("该照度已有节点");points.put(x,y);}double[] a=new double[points.size()],b=new double[points.size()];int i=0;for(java.util.Map.Entry<Double,Double> p:points.entrySet()){a[i]=p.getKey();b[i++]=p.getValue();}return new OprCurve(a,b);
        }
    }
    static OutdoorOptions parse(JSONObject json){OutdoorOptions o=new OutdoorOptions();
        for(int i=0;i<FLAGS.length;i++)if(json.has(FLAGS[i])){Object v=json.opt(FLAGS[i]);if(!(v instanceof Boolean))throw new IllegalArgumentException("户外增强开关必须是布尔值");o.flags[i]=(Boolean)v;}
        for(int i=0;i<KEYS.length;i++){if(json.has(KEYS[i])){Object v=json.opt(KEYS[i]);if(!(v instanceof Number))throw new IllegalArgumentException("户外增强参数必须是数值");o.values[i]=((Number)v).doubleValue();}AdvancedPolicy.range(o.values[i],MIN[i],MAX[i]);}
        if(json.has("outdoor_node_boost")){Object v=json.opt("outdoor_node_boost");if(!(v instanceof Boolean))throw new IllegalArgumentException("节点高亮开关必须是布尔值");o.nodeBoost=(Boolean)v;}
        o.nodeTarget=OutdoorNodeTarget.parse(json);
        if(json.has(OPR_CURVE_ENABLED)){Object v=json.opt(OPR_CURVE_ENABLED);if(!(v instanceof Boolean))throw new IllegalArgumentException("高亮曲线开关必须是布尔值");o.oprCurveEnabled=(Boolean)v;}
        o.oprCurve=OprCurve.parse(json.opt(OPR_CURVE));o.targetCurve=OutdoorTargetCurve.parse(json);if(o.targetCurve.enabled&&(o.nodeBoost||o.oprCurveEnabled))throw new IllegalArgumentException("目标曲线不能与旧上限曲线或节点模式同时启用");
        if(o.oprCurveEnabled&&(o.nodeBoost||o.oprCurve.lux.length<2))throw new IllegalArgumentException("高亮曲线需至少2个节点，并关闭强制节点高亮");
        if(o.nodeBoost&&(!o.flags[0]||!o.flags[2]||!o.flags[3]||o.values[MAX_NIT]!=0))throw new IllegalArgumentException("节点兼容需开启户外增强、高亮范围和画面放宽，并将 nit 上限设为不限制");
        if(o.values[1]<=o.values[0])throw new IllegalArgumentException("全强度照度须高于进入照度");
        for(int i:new int[]{0,1,2,3,6,7})if(o.values[i]!=Math.rint(o.values[i]))throw new IllegalArgumentException("户外照度与时间须为整数");
        if(!o.flags[0]&&(o.flags[1]||o.flags[2]||o.flags[3]))throw new IllegalArgumentException("请先开启户外增强，或关闭附加高亮选项");if(o.flags[3]&&!o.flags[2])throw new IllegalArgumentException("请先开放高亮范围，再放宽画面限亮");return o;
    }
    void put(JSONObject json)throws JSONException{for(int i=0;i<FLAGS.length;i++)json.put(FLAGS[i],flags[i]);for(int i=0;i<KEYS.length;i++)json.put(KEYS[i],values[i]);targetCurve.put(json);json.put("outdoor_node_boost",nodeBoost).put(OutdoorNodeTarget.KEY,nodeTarget).put(OPR_CURVE_ENABLED,oprCurveEnabled).put(OPR_CURVE,oprCurve.json());}
    void verify(JSONObject status){if(flags[0]&&targetCurve.enabled&&!status.optBoolean("outdoor_target_curve_supported"))throw new IllegalArgumentException("本机高亮目标换算尚未就绪");if(flags[0]&&!status.optBoolean("outdoor_supported"))throw new IllegalArgumentException("此固件的户外增强接口尚未兼容");
        if(flags[1]&&!status.optBoolean("outdoor_hbm_supported"))throw new IllegalArgumentException("此设备的 HBM 触发接口尚未兼容");
        if(flags[2]&&!status.optBoolean("outdoor_range_supported"))throw new IllegalArgumentException("此固件的高亮范围接口尚未兼容");
        if(flags[0]&&values[MAX_NIT]>0&&!status.optBoolean("outdoor_nit_limit_supported"))throw new IllegalArgumentException("此固件无法可靠设置 nit 亮度上限");
        if(flags[3]&&!status.optBoolean("outdoor_opr_supported"))throw new IllegalArgumentException("此固件的画面限亮接口尚未兼容");
        // Saved curve intent may remain dormant; fit() disables only the runtime copy.
        if(nodeBoost&&!status.optBoolean("outdoor_node_supported"))throw new IllegalArgumentException("此固件的节点高亮路径尚未核验");}
    boolean same(OutdoorOptions other){return targetCurve.same(other.targetCurve)&&oprCurveEnabled==other.oprCurveEnabled&&oprCurve.same(other.oprCurve)&&nodeBoost==other.nodeBoost&&nodeTarget==other.nodeTarget&&java.util.Arrays.equals(flags,other.flags)&&java.util.Arrays.equals(values,other.values);}
}
