package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import org.json.*;

/** Effective automatic mode, not the user's setting or the last output reason. Read only. */
final class AutomaticModeAccess {
    final Object owner,abc,dpc,strategy;
    final Field direct;
    final Method use;
    final String route;

    AutomaticModeAccess(Object owner,Object abc)throws Exception {
        this.owner=owner;this.abc=abc;
        Field preferred=null;
        try{preferred=TraditionalAdapter.find(owner.getClass(),"mUseAutoBrightness");}catch(NoSuchFieldException absent){}
        if(preferred!=null){
            requireBoolean(preferred);direct=preferred;dpc=strategy=null;use=null;route="owner_boolean";
        }else{
            if(display(owner)!=0)throw new IllegalArgumentException("自动亮度模式不属于主屏");
            dpc=link(owner,"mDisplayPowerController");
            if(dpc==null)throw new IllegalStateException("自动亮度控制器尚未就绪");
            Field strategyField=null;try{strategyField=TraditionalAdapter.find(dpc.getClass(),"mAutomaticBrightnessStrategy");}catch(NoSuchFieldException absent){}
            if(strategyField==null){
                Field legacy=TraditionalAdapter.find(dpc.getClass(),"mUseAutoBrightness");requireBoolean(legacy);
                if(display(dpc)!=0)throw new IllegalArgumentException("自动亮度控制器不属于主屏");
                strategy=null;use=null;direct=legacy;route="display_controller_boolean";owned();return;
            }
            direct=null;strategy=link(dpc,"mAutomaticBrightnessStrategy");
            if(strategy==null)throw new IllegalStateException("自动亮度策略尚未就绪");
            String name=strategy.getClass().getName();
            if(!name.equals("com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy")&&
               !name.equals("com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2"))throw new IllegalArgumentException("自动亮度策略接口未验证");
            if(display(dpc)!=0||display(strategy)!=0)throw new IllegalArgumentException("自动亮度策略不属于主屏");
            use=TraditionalAdapter.method(strategy.getClass(),"shouldUseAutoBrightness");
            if(use.getReturnType()!=boolean.class||Modifier.isStatic(use.getModifiers()))throw new IllegalArgumentException("自动亮度模式方法不兼容");
            route="native_automatic_strategy";owned();
        }
    }
    private static void requireBoolean(Field f){if(f.getType()!=boolean.class||Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("自动亮度模式字段不兼容");}
    private static Object link(Object owner,String name)throws Exception {Field f=TraditionalAdapter.find(owner.getClass(),name);if(f.getType().isPrimitive()||Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("自动亮度归属字段不兼容");return f.get(owner);}
    private static int display(Object owner)throws Exception {Field f=TraditionalAdapter.find(owner.getClass(),"mDisplayId");if(f.getType()!=int.class||Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("自动亮度屏幕身份不兼容");return f.getInt(owner);}
    private void owned()throws Exception {
        if(abc==null||link(owner,"mDisplayPowerController")!=dpc||
           link(dpc,"mDisplayPowerControllerImpl")!=owner||
           link(dpc,"mAutomaticBrightnessController")!=abc||
           strategy!=null&&(link(dpc,"mAutomaticBrightnessStrategy")!=strategy||link(strategy,"mAutomaticBrightnessController")!=abc))throw new IllegalStateException("自动亮度策略归属已改变");
    }
    boolean read()throws Exception {if(direct!=null){if(dpc!=null)owned();return direct.getBoolean(dpc==null?owner:dpc);}owned();return (Boolean)use.invoke(strategy);}
    static void put(JSONObject status,CurveBackend backend)throws JSONException {
        status.remove("auto_mode");status.remove("automatic_mode_error");
        try{Object mode=backend.get("mUseAutoBrightness");if(!(mode instanceof Boolean))throw new IllegalStateException("自动亮度模式尚未确认");status.put("auto_mode",mode);}
        catch(Throwable unavailable){Throwable cause=unavailable;for(int i=0;i<8&&cause.getCause()!=null&&cause.getCause()!=cause;i++)cause=cause.getCause();
            String detail=cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());status.put("automatic_mode_error",detail.length()>280?detail.substring(0,280):detail);}
    }
}
