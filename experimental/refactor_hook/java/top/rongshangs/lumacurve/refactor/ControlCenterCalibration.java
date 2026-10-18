package top.rongshangs.lumacurve.refactor;
import java.lang.reflect.*;
import org.json.*;

/** Reads this display's pure conversion functions. Unknown routes stay unchanged. */
final class ControlCenterCalibration {
    static Method method(Class<?> owner,String name,Class<?> result,Class<?>...args)throws Exception{Method m=owner.getMethod(name,args);if(m.getReturnType()!=result)throw new IllegalArgumentException("设备转换签名已变化");m.setAccessible(true);return m;}
    static Object configuration(Object owner)throws Exception{for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField("mDisplayDeviceConfig");f.setAccessible(true);Object value=f.get(owner);if(value==null)throw new IllegalStateException("主屏配置尚未就绪");return value;}catch(NoSuchFieldException next){}throw new NoSuchFieldException("mDisplayDeviceConfig");}
    static JSONObject read(Object owner)throws Exception{
        Object config=configuration(owner);Method brightness=method(config.getClass(),"getBacklightFromBrightness",float.class,float.class);
        ControlCenterCurve standard=ControlCenterCurve.sample(x->((Number)brightness.invoke(config,(float)x)).doubleValue());JSONObject result=new JSONObject().put("standard",standard.json()).put("kind","coordinate_v2");
        try{Class<?> util=Class.forName("com.android.server.display.RefactorBrightnessUtil",false,owner.getClass().getClassLoader());Method logical=method(util,"sliderToLogicalBrightness",float.class,float.class),fromNits=method(config.getClass(),"getBacklightFromNits",float.class,float.class);if(!Modifier.isStatic(logical.getModifiers()))throw new IllegalArgumentException("未知的逻辑亮度转换");ControlCenterCurve policy=ControlCenterCurve.sample(x->{float nit=((Number)logical.invoke(null,(float)x)).floatValue();return ((Number)fromNits.invoke(config,nit)).doubleValue();});result.put("oem_v1",policy.json());}
        catch(Exception|LinkageError unavailable){result.put("oem_v1_detail",unavailable.toString());}return result;
    }
}
