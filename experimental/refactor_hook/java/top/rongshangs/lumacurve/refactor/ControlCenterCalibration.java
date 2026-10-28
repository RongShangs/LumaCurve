package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;
import java.util.Arrays;
import org.json.*;

/** Reads this display's pure conversion functions. Unknown routes stay unchanged. */
final class ControlCenterCalibration {
    static Method method(Class<?> owner,String name,Class<?> result,Class<?>...args)throws Exception{Method m=owner.getMethod(name,args);if(m.getReturnType()!=result)throw new IllegalArgumentException("设备转换签名已变化");m.setAccessible(true);return m;}
    static Object configuration(Object owner)throws Exception{for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField("mDisplayDeviceConfig");f.setAccessible(true);Object value=f.get(owner);if(value==null)throw new IllegalStateException("主屏配置尚未就绪");return value;}catch(NoSuchFieldException next){}throw new NoSuchFieldException("mDisplayDeviceConfig");}
    static JSONObject read(Object owner)throws Exception{
        Object config=configuration(owner);Method brightness=method(config.getClass(),"getBacklightFromBrightness",float.class,float.class);
        ControlCenterCurve.Probe nativeBrightness=x->value(brightness,config,(float)x);
        Proof proof=null;ControlCenterCurve standard;String profile="coordinate_v2";
        try{standard=ControlCenterCurve.sample(nativeBrightness);}
        catch(ControlCenterCurve.NonInvertible clipped){proof=new Proof(config);proof.brightness(brightness);standard=ControlCenterCurve.sampleRange(nativeBrightness,proof.brightness[0],last(proof.brightness));proof.stable();profile="coordinate_v2_calibrated";}
        JSONObject result=new JSONObject().put("standard",standard.json()).put("kind","coordinate_v2").put("profile",profile);
        try{
            Class<?> util=Class.forName("com.android.server.display.RefactorBrightnessUtil",false,owner.getClass().getClassLoader());Method logical=method(util,"sliderToLogicalBrightness",float.class,float.class),fromNits=method(config.getClass(),"getBacklightFromNits",float.class,float.class);if(!Modifier.isStatic(logical.getModifiers()))throw new IllegalArgumentException("未知的逻辑亮度转换");
            ControlCenterCurve.Probe composed=x->value(fromNits,config,(float)value(logical,null,(float)x));ControlCenterCurve policy;
            try{policy=ControlCenterCurve.sample(composed);}
            catch(ControlCenterCurve.NonInvertible clipped){if(proof==null)proof=new Proof(config);double[] range=proof.logical(util,logical,fromNits);policy=ControlCenterCurve.sampleRange(composed,range[0],range[1]);proof.stable();result.put("profile","coordinate_v2_calibrated");}
            result.put("oem_v1",policy.json());
        }
        catch(Exception|LinkageError unavailable){result.put("oem_v1_detail",unavailable.toString());}if(proof!=null)proof.standardStable();return result;
    }
    static double last(float[] values){return values[values.length-1];}
    static float value(Method method,Object owner,float x)throws Exception{return ((Number)method.invoke(owner,x)).floatValue();}
    static Field field(Class<?> owner,String name)throws Exception{for(Class<?> c=owner;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    static float[] array(Object config,String name)throws Exception{Field f=field(config.getClass(),name);if(f.getType()!=float[].class||Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("未知的设备标定坐标");float[] v=(float[])f.get(config);if(v==null)throw new IllegalStateException("设备标定尚未就绪");return v.clone();}
    static void increasing(float[] values,boolean normalized){if(values.length<2||values.length>4096)throw new IllegalArgumentException("设备标定长度无效");for(int i=0;i<values.length;i++)if(!Float.isFinite(values[i])||values[i]<0||normalized&&values[i]>1||i>0&&values[i]<=values[i-1])throw new IllegalArgumentException("设备标定不可单调反解");}
    static void near(float value,float expected){if(!Float.isFinite(value)||Math.abs((double)value-expected)>16d*Math.max(Math.ulp(value),Math.ulp(expected)))throw new IllegalArgumentException("原生换算与设备标定不一致");}
    static void instance(Method method){if(Modifier.isStatic(method.getModifiers()))throw new IllegalArgumentException("未知的设备换算方法");}
    static void spline(Object config,String name,float[] x,float[] y)throws Exception{
        Object s=field(config.getClass(),name).get(config);if(s==null)throw new IllegalStateException("设备标定尚未就绪");
        String cls=s.getClass().getName();if(!cls.equals("android.util.Spline$LinearSpline")&&!cls.equals("android.util.Spline$MonotoneCubicSpline"))throw new IllegalArgumentException("未知的设备标定样条");
        if(!Arrays.equals(x,array(s,"mX"))||!Arrays.equals(y,array(s,"mY")))throw new IllegalArgumentException("设备标定样条与坐标不一致");
    }
    static void endpoints(ControlCenterCurve.Probe probe,double low,double high)throws Exception{
        if(!Double.isFinite(low)||!Double.isFinite(high)||low<0||high>1||high<=low)throw new IllegalArgumentException("设备有效坐标范围无效");
        float a=(float)probe.at(low),b=(float)probe.at(high);
        for(int i=0;i<=4;i++){near((float)probe.at(low*i/4),a);near((float)probe.at(high+(1-high)*i/4),b);}
    }
    /** Additive proof only after the established whole-domain path detects a flat interval.
     * Flat middle sections and malformed/new dimmer profiles remain rejected.
     */
    static final class Proof {
        final Object config,brightnessSpline,reverseSpline;final float[] brightness,backlight;final Field dimmer;
        Field logicalTable;float[] logicalLive,logicalSnapshot,nitSnapshot;Object nitSpline;
        Proof(Object config)throws Exception{
            this.config=config;
            if(!config.getClass().getName().equals("com.android.server.display.DisplayDeviceConfig"))throw new IllegalArgumentException("此设备的标定端点尚未验证");
            Field optional;try{optional=field(config.getClass(),"mEvenDimmerBrightnessData");}catch(NoSuchFieldException absent){optional=null;}dimmer=optional;
            if(dimmer!=null&&(Modifier.isStatic(dimmer.getModifiers())||!dimmer.getType().getName().equals("com.android.server.display.config.EvenDimmerBrightnessData")))throw new IllegalArgumentException("未知的额外低亮度标定接口");
            if(dimmer!=null&&dimmer.get(config)!=null)throw new IllegalStateException("额外低亮度标定正在生效");
            brightness=array(config,"mBrightness");backlight=array(config,"mBacklight");increasing(brightness,true);increasing(backlight,true);
            if(brightness.length!=backlight.length)throw new IllegalArgumentException("设备标定坐标数量不一致");
            spline(config,"mBrightnessToBacklightSpline",brightness,backlight);spline(config,"mBacklightToBrightnessSpline",backlight,brightness);
            brightnessSpline=field(config.getClass(),"mBrightnessToBacklightSpline").get(config);reverseSpline=field(config.getClass(),"mBacklightToBrightnessSpline").get(config);
        }
        void brightness(Method forward)throws Exception{
            Method reverse=method(config.getClass(),"getBrightnessFromBacklight",float.class,float.class);instance(forward);instance(reverse);
            for(int i=0;i<brightness.length;i++){near(value(forward,config,brightness[i]),backlight[i]);near(value(reverse,config,backlight[i]),brightness[i]);}
            endpoints(x->value(forward,config,(float)x),brightness[0],last(brightness));
        }
        double[] logical(Class<?> util,Method forward,Method fromNits)throws Exception{
            float[] nits=array(config,"mNits");increasing(nits,false);if(nits.length!=backlight.length)throw new IllegalArgumentException("nit标定坐标数量不一致");spline(config,"mNitsToBacklightSpline",nits,backlight);nitSpline=field(config.getClass(),"mNitsToBacklightSpline").get(config);nitSnapshot=nits;instance(fromNits);
            for(int i=0;i<nits.length;i++)near(value(fromNits,config,nits[i]),backlight[i]);
            Field table=field(util,"mLogicalBrightnessSet");if(table.getType()!=float[].class||!Modifier.isStatic(table.getModifiers()))throw new IllegalArgumentException("未知的逻辑亮度坐标");
            float[] live=(float[])table.get(null);if(live==null)throw new IllegalStateException("逻辑亮度标定尚未就绪");float[] points=live.clone();logicalTable=table;logicalLive=live;logicalSnapshot=points;increasing(points,false);
            // This verified OEM consumer addresses 100 equal slider intervals.
            if(points.length!=101)throw new IllegalArgumentException("未知的逻辑亮度表长度");
            Method reverse=method(util,"logicalBrightnessToSlider",float.class,float.class);if(!Modifier.isStatic(reverse.getModifiers()))throw new IllegalArgumentException("未知的逻辑亮度转换");
            for(int i=0;i<=100;i++){near(value(forward,null,i/100f),points[i]);near(value(reverse,null,points[i]),i/100f);}
            float lo=Math.max(nits[0],points[0]),hi=Math.min(nits[nits.length-1],points[points.length-1]);if(hi<=lo)throw new IllegalArgumentException("设备逻辑标定没有有效交集");
            float low=value(reverse,null,lo),high=value(reverse,null,hi);near(value(forward,null,low),lo);near(value(forward,null,high),hi);
            endpoints(x->value(fromNits,config,value(forward,null,(float)x)),low,high);
            if(table.get(null)!=live||!Arrays.equals(points,live)||!Arrays.equals(nits,array(config,"mNits")))throw new IllegalStateException("逻辑亮度标定正在变化");
            return new double[]{low,high};
        }
        void standardStable()throws Exception{
            if(dimmer!=null&&dimmer.get(config)!=null||brightnessSpline!=field(config.getClass(),"mBrightnessToBacklightSpline").get(config)||reverseSpline!=field(config.getClass(),"mBacklightToBrightnessSpline").get(config)||!Arrays.equals(brightness,array(config,"mBrightness"))||!Arrays.equals(backlight,array(config,"mBacklight")))throw new IllegalStateException("设备标定正在变化");
            spline(config,"mBrightnessToBacklightSpline",brightness,backlight);spline(config,"mBacklightToBrightnessSpline",backlight,brightness);
        }
        void stable()throws Exception{
            standardStable();
            if(logicalTable!=null){if(logicalTable.get(null)!=logicalLive||!Arrays.equals(logicalSnapshot,logicalLive)||nitSpline!=field(config.getClass(),"mNitsToBacklightSpline").get(config)||!Arrays.equals(nitSnapshot,array(config,"mNits")))throw new IllegalStateException("逻辑亮度标定正在变化");spline(config,"mNitsToBacklightSpline",nitSnapshot,backlight);}
        }
    }
}
