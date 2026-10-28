package top.rongshangs.lumacurve.refactor;
import java.util.*;
import org.json.*;

/** Immutable device coordinates. No brightness ownership or output writes. */
final class ControlCenterCurve {
    static final int MAX_POINTS=2049;static final double ERROR=1.0/65535;
    interface Probe {double at(double value)throws Exception;}
    final double[] input,node;
    static final class NonInvertible extends IllegalArgumentException {NonInvertible(){super("设备转换不能单调反解");}}
    ControlCenterCurve(double[] x,double[] y){this(x,y,false);}
    private ControlCenterCurve(double[] x,double[] y,boolean bounded){
        if(x==null||y==null||x.length!=y.length||x.length<2||x.length>MAX_POINTS)throw new IllegalArgumentException("设备映射表长度无效");input=x.clone();node=y.clone();
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i])||!Double.isFinite(y[i])||x[i]<0||x[i]>1||y[i]<0||y[i]>1||i>0&&(x[i]<=x[i-1]||y[i]<=y[i-1]))throw new IllegalArgumentException("设备映射必须有限且单调");
        if(!bounded&&(x[0]!=0||x[x.length-1]!=1))throw new IllegalArgumentException("设备映射范围不完整");
    }
    double toNode(double value){return interpolate(input,node,value);}
    double toInput(double value){return interpolate(node,input,value);}
    static double interpolate(double[] x,double[] y,double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("亮度坐标无效");if(value<=x[0])return y[0];int last=x.length-1;if(value>=x[last])return y[last];
        int lo=0,hi=last;while(hi-lo>1){int m=(lo+hi)>>>1;if(x[m]<=value)lo=m;else hi=m;}return y[lo]+(y[hi]-y[lo])*(value-x[lo])/(x[hi]-x[lo]);
    }
    JSONObject json()throws JSONException{JSONArray x=new JSONArray(),y=new JSONArray();for(int i=0;i<input.length;i++){x.put(input[i]);y.put(node[i]);}JSONObject result=new JSONObject().put("input",x).put("node",y);if(input[0]!=0||input[input.length-1]!=1)result.put("range",new JSONArray().put(input[0]).put(input[input.length-1]));return result;}
    static ControlCenterCurve read(JSONObject json)throws JSONException{
        if(json==null)throw new IllegalArgumentException("设备映射尚未就绪");JSONArray x=json.getJSONArray("input"),y=json.getJSONArray("node");if(x.length()!=y.length()||x.length()>MAX_POINTS)throw new IllegalArgumentException("设备映射表过长");double[] a=new double[x.length()],b=new double[y.length()];for(int i=0;i<a.length;i++){if(!(x.get(i) instanceof Number)||!(y.get(i) instanceof Number))throw new IllegalArgumentException("映射包含非数值");a[i]=x.getDouble(i);b[i]=y.getDouble(i);}boolean bounded=json.has("range");if(bounded){JSONArray range=json.getJSONArray("range");if(a.length<2||range.length()!=2||!(range.get(0) instanceof Number)||!(range.get(1) instanceof Number)||a[0]!=range.getDouble(0)||a[a.length-1]!=range.getDouble(1))throw new IllegalArgumentException("设备映射范围不一致");}return new ControlCenterCurve(a,b,bounded);
    }
    static ControlCenterCurve sample(Probe probe)throws Exception{
        return sampleRange(probe,0,1);
    }
    /** Called with an independently verified native calibration domain, never guessed from a plateau. */
    static ControlCenterCurve sampleRange(Probe probe,double minimum,double maximum)throws Exception{
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<0||maximum>1||maximum<=minimum)throw new IllegalArgumentException("设备映射范围无效");
        TreeMap<Double,Double> values=new TreeMap<>();double start=probe.at(minimum);values.put(minimum,start);for(int i=0;i<16;i++){double lo=i==0?minimum:minimum+(maximum-minimum)*i/16,hi=i==15?maximum:minimum+(maximum-minimum)*(i+1)/16,end=probe.at(hi);refine(probe,values,lo,hi,start,end,0);start=end;}double[] x=new double[values.size()],y=new double[values.size()];int at=0;for(Map.Entry<Double,Double> e:values.entrySet()){x[at]=e.getKey();y[at++]=e.getValue();}return new ControlCenterCurve(x,y,true);
    }
    static void refine(Probe probe,TreeMap<Double,Double> values,double lo,double hi,double a,double b,int depth)throws Exception{
        if(!Double.isFinite(a)||!Double.isFinite(b)||a<0||b>1)throw new IllegalArgumentException("设备转换不能单调反解");if(b<=a)throw new NonInvertible();double mid=(lo+hi)/2,m=probe.at(mid),q1=probe.at((lo+mid)/2),q3=probe.at((mid+hi)/2);
        if(!Double.isFinite(m)||!Double.isFinite(q1)||!Double.isFinite(q3)||q1<a||m<q1||q3<m||q3>b)throw new IllegalArgumentException("设备转换采样无效");
        if(Math.abs(m-(a+b)/2)>ERROR||Math.abs(q1-(3*a+b)/4)>ERROR||Math.abs(q3-(a+3*b)/4)>ERROR){if(depth>=16||values.size()>=MAX_POINTS-2)throw new IllegalArgumentException("设备转换精度不足");refine(probe,values,lo,mid,a,m,depth+1);refine(probe,values,mid,hi,m,b,depth+1);}else{values.put(hi,b);if(values.size()>MAX_POINTS)throw new IllegalArgumentException("设备转换表过长");}
    }
}
