package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import org.json.*;

/** Actual ordinary-brightness boundary, plus the per-slider two-stroke admission policy. */
final class ControlCenterPartition {
    static final long WINDOW_MS=3000;
    static final double ORDINARY_SPAN=.75;
    static int upper(int requested,int maximum,double ordinary){
        if(maximum<=10||maximum>65535||!Double.isFinite(ordinary)||ordinary<=0||ordinary>=1||requested<0||requested>65535)throw new IllegalArgumentException("分区节点范围无效");
        int value=requested==0?maximum:Math.min(requested,maximum);
        if(value<=Math.ceil(ordinary*maximum))throw new IllegalArgumentException("最高节点必须高于普通区间上限");return value;
    }
    static double node(double position,double ordinary,int upper,int maximum){
        if(!Double.isFinite(position)||position<0||position>1)throw new IllegalArgumentException("分区滑条位置无效");upper(upper,maximum,ordinary);
        return position<=ORDINARY_SPAN?ordinary*position/ORDINARY_SPAN:ordinary+(upper/(double)maximum-ordinary)*(position-ORDINARY_SPAN)/(1-ORDINARY_SPAN);
    }
    static double position(double node,double ordinary,int upper,int maximum){
        if(!Double.isFinite(node))throw new IllegalArgumentException("分区节点坐标无效");upper(upper,maximum,ordinary);
        double value=node<=ordinary?ORDINARY_SPAN*node/ordinary:ORDINARY_SPAN+(1-ORDINARY_SPAN)*(node-ordinary)/(upper/(double)maximum-ordinary);
        return Math.max(0,Math.min(1,value));
    }
    static JSONObject read(Object owner,JSONObject calibration)throws Exception{
        Object config=ControlCenterCalibration.configuration(owner);float ceiling;String profile;
        Method data=null;
        try{data=config.getClass().getMethod("getHighBrightnessModeData");}catch(NoSuchMethodException absent){}
        if(data!=null){
            if(Modifier.isStatic(data.getModifiers())||!data.getReturnType().getName().equals("com.android.server.display.config.HighBrightnessModeData"))throw new IllegalArgumentException("普通亮度边界接口格式变化");
            Object value=data.invoke(config);
            if(value!=null){Field point=value.getClass().getField("transitionPoint");if(point.getType()!=float.class||Modifier.isStatic(point.getModifiers()))throw new IllegalArgumentException("普通亮度边界类型变化");ceiling=point.getFloat(value);profile="ddc_hbm_transition";}
            else{ceiling=normal(owner);profile="hbm_normal_ceiling";}
        }else{ceiling=normal(owner);profile="hbm_normal_ceiling";}
        if(!Float.isFinite(ceiling)||ceiling<=0||ceiling>1)throw new IllegalArgumentException("普通亮度边界无效");
        ControlCenterCurve curve=ControlCenterCurve.read(calibration.getJSONObject("standard"));
        if(ceiling<curve.input[0]||ceiling>curve.input[curve.input.length-1])throw new IllegalArgumentException("普通亮度边界超出实际标定范围");
        double node=curve.toNode(ceiling);
        if(!Double.isFinite(node)||node<=0||node>=1)throw new IllegalArgumentException("本机没有可确认的额外节点区间");
        if(ControlCenterCalibration.configuration(owner)!=config)throw new IllegalStateException("主屏配置正在变化");
        return new JSONObject().put("profile",profile).put("brightness",ceiling).put("node",node);
    }
    static float normal(Object owner)throws Exception{
        Field field=ControlCenterCalibration.field(owner.getClass(),"mHbmController");Object controller=field.get(owner);
        if(controller==null)throw new IllegalStateException("普通亮度控制器尚未就绪");
        Method method=controller.getClass().getDeclaredMethod("getNormalBrightnessMax");method.setAccessible(true);
        if(method.getReturnType()!=float.class||Modifier.isStatic(method.getModifiers()))throw new IllegalArgumentException("普通亮度上限接口格式变化");
        return (Float)method.invoke(controller);
    }
    static final class Gate {
        static final int NORMAL=0,CAP=1,PROMPT=2,RAW=3,RELEASE=4;
        long since=-1,confirmed=-1;boolean stroke,released,raw,releasePending,automaticHigh;
        void reset(){since=confirmed=-1;stroke=released=raw=releasePending=automaticHigh=false;}
        void fromAutomaticHigh(long now){reset();raw=automaticHigh=true;since=confirmed=now;}
        int move(int position,int boundary,boolean tracking,boolean stop,long now,boolean holding){
            if(raw&&!holding&&confirmed>=0&&now>=confirmed&&now-confirmed>1500){raw=automaticHigh=false;since=confirmed=-1;released=false;}
            if(!holding)releasePending=false;
            if(holding&&!releasePending)raw=true;
            boolean newStroke=tracking&&!stroke;
            if(tracking)stroke=true;
            int action;
            if(raw){
                if(position<=boundary){raw=automaticHigh=false;releasePending=true;since=confirmed=-1;released=false;action=RELEASE;}
                else action=RAW;
            }else if(position<boundary){since=confirmed=-1;released=false;action=NORMAL;}
            else if(since>=0&&now>=since&&now-since<=WINDOW_MS&&released&&tracking&&!stop&&position>boundary){raw=true;confirmed=now;action=RAW;}
            else if(since<0||now<since||now-since>WINDOW_MS&&newStroke){since=now;released=automaticHigh=false;action=PROMPT;}
            else action=CAP;
            if(stop){stroke=false;if(since>=0&&now>=since&&now-since<=WINDOW_MS)released=true;}
            return action;
        }
        static boolean proof(JSONObject request){
            Object a=request.opt("partition_since"),b=request.opt("partition_confirmed");
            if(!(a instanceof Number)||!(b instanceof Number))return false;
            long since=((Number)a).longValue(),confirmed=((Number)b).longValue(),at=request.optLong("elapsed",-1);
            return ((Number)a).doubleValue()==since&&((Number)b).doubleValue()==confirmed&&since>=0&&confirmed>=since&&confirmed-since<=WINDOW_MS&&confirmed<=at;
        }
    }
}
