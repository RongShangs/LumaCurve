package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.view.Display;
import java.lang.reflect.Method;
import org.json.*;

/** Main display framework values. Never chmod or write panel sysfs nodes. */
final class FrameworkBrightness {
    final Object manager,global;final Method set,get,info;
    FrameworkBrightness(Context c)throws Exception{
        manager=c.getSystemService(Context.DISPLAY_SERVICE);if(manager==null)throw new IllegalStateException("未取得主屏显示接口");
        set=manager.getClass().getMethod("setBrightness",int.class,float.class);
        get=manager.getClass().getMethod("getBrightness",int.class);
        Class<?> type=Class.forName("android.hardware.display.DisplayManagerGlobal");
        global=type.getMethod("getInstance").invoke(null);if(global==null)throw new IllegalStateException("主屏亮度信息接口尚未就绪");
        info=type.getMethod("getBrightnessInfo",int.class);
        Class<?> shape=info.getReturnType();for(String key:new String[]{"brightnessMinimum","brightnessMaximum","brightness"})if(shape.getField(key).getType()!=float.class)throw new IllegalStateException("主屏亮度信息格式已改变");
    }
    static float field(Object object,String key)throws Exception{return ((Number)object.getClass().getField(key).get(object)).floatValue();}
    JSONObject read()throws Exception{
        Object i=info.invoke(global,Display.DEFAULT_DISPLAY);if(i==null)throw new IllegalStateException("主屏亮度范围尚未就绪");
        float min=field(i,"brightnessMinimum"),max=field(i,"brightnessMaximum"),value=((Number)get.invoke(manager,Display.DEFAULT_DISPLAY)).floatValue();
        if(!Float.isFinite(min)||!Float.isFinite(max)||!Float.isFinite(value)||min<0||max>1||max<=min||value<0||value>1)throw new IllegalStateException("主屏亮度范围无效");
        return new JSONObject().put("minimum",min).put("maximum",max).put("value",value).put("actual",field(i,"brightness"));
    }
    float write(float value)throws Exception{
        if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("亮度值无效");
        JSONObject range=read();float request=Math.max((float)range.getDouble("minimum"),Math.min((float)range.getDouble("maximum"),value));
        set.invoke(manager,Display.DEFAULT_DISPLAY,request);return request;
    }
}
