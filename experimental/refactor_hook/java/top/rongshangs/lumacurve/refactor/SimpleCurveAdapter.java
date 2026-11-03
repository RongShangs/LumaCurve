package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntSupplier;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Native SimpleMappingStrategy: lux -> framework brightness, never physical nit. */
final class SimpleCurveAdapter extends CurveBackend {
    static final String TYPE="com.android.server.display.BrightnessMappingStrategy$SimpleMappingStrategy";
    final Object owner,mapper,abc;
    final IntSupplier user;
    final float[] nativeLux,nativeBrightness,originalBrightness;
    final TraditionalCurve original;
    final Method compute,reset,interpolate,createSpline;
    private CurvePlan active;
    private Base applied;
    private int transactionUser=-1;
    private String lastError="";
    private static final class Base {final float[] lux,brightness;final Object spline;Base(float[] x,float[] y,Object s){lux=x.clone();brightness=y.clone();spline=s;}}
    private Boolean persistence;
    private AutomaticModeAccess automatic;

    SimpleCurveAdapter(Object owner,Object mapper,Object abc,IntSupplier user)throws Exception {
        super(0,1);this.owner=owner;this.mapper=mapper;this.abc=abc;this.user=user;
        if(!mapper.getClass().getName().equals(TYPE))throw new IllegalArgumentException("未知比例曲线接口");
        typed("mLux",float[].class);typed("mBrightness",float[].class);
        for(String n:new String[]{"mUserLux","mUserBrightness","mAutoBrightnessAdjustment","mMaxGamma"})typed(n,float.class);
        if(((Number)TraditionalAdapter.read(owner,"mDisplayId")).intValue()!=0||
           ((Number)instanceMethod("getMode",int.class).invoke(mapper)).intValue()!=0)
            throw new IllegalArgumentException("仅接入主屏普通比例曲线");
        Object map=TraditionalAdapter.read(abc,"mBrightnessMappingStrategyMap");
        if(TraditionalAdapter.method(map.getClass(),"get",int.class).invoke(map,0)!=mapper||TraditionalAdapter.read(owner,"mBrightnessMapper")!=mapper)
            throw new IllegalStateException("主屏比例曲线对象不一致");
        nativeLux=(float[])TraditionalAdapter.read(mapper,"mLux");nativeBrightness=(float[])TraditionalAdapter.read(mapper,"mBrightness");
        original=new TraditionalCurve(nativeLux,nativeBrightness);
        CurvePlan.points(nativeLux,nativeBrightness,min,max);originalBrightness=nativeBrightness.clone();
        factoryLux=original.controlsLux();factoryNit=original.controlsNit();
        new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});
        compute=instanceMethod("computeSpline",void.class);
        instanceMethod("addUserDataPoint",void.class,float.class,float.class);
        instanceMethod("clearUserDataPoints",void.class);
        instanceMethod("getBrightness",float.class,float.class,String.class,int.class);
        instanceMethod("getUserBrightness",float.class);
        instanceMethod("setAutoBrightnessAdjustment",boolean.class,float.class);
        reset=TraditionalAdapter.method(abc.getClass(),"resetShortTermModel");
        if(Modifier.isStatic(reset.getModifiers())||reset.getReturnType()!=void.class)throw new IllegalArgumentException("比例曲线重置接口无效");
        Field spline=TraditionalAdapter.find(mapper.getClass(),"mSpline");
        if(Modifier.isStatic(spline.getModifiers())||!spline.getType().getName().equals("android.util.Spline"))throw new IllegalArgumentException("比例样条类型无效");
        interpolate=TraditionalAdapter.interpolator(TraditionalAdapter.read(mapper,"mSpline"));
        Class<?> splineClass=spline.getType();createSpline=TraditionalAdapter.method(splineClass,"createSpline",float[].class,float[].class);
        if(!Modifier.isStatic(createSpline.getModifiers())||createSpline.getReturnType()!=splineClass)throw new IllegalArgumentException("比例曲线构造接口无效");
        instanceMethod("getUnadjustedBrightness",float.class,float.class);
        Method adjusted=TraditionalAdapter.method(mapper.getClass(),"getAdjustedCurve",float[].class,float[].class,float.class,float.class,float.class,float.class);
        if(Modifier.isStatic(adjusted.getModifiers())||!adjusted.getReturnType().getName().equals("android.util.Pair"))throw new IllegalArgumentException("比例曲线学习接口无效");
        splinePoints();
        validateSpline(); // Read only: construction never rewrites or resets native memory.
    }
    private void typed(String n,Class<?> type)throws Exception {Field f=TraditionalAdapter.find(mapper.getClass(),n);if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("比例曲线字段无效："+n);}
    private Method instanceMethod(String name,Class<?> result,Class<?>...params)throws Exception {Method m=TraditionalAdapter.method(mapper.getClass(),name,params);if(Modifier.isStatic(m.getModifiers())||m.getReturnType()!=result)throw new IllegalArgumentException("比例曲线方法无效："+name);return m;}
    private boolean owned()throws Exception {return TraditionalAdapter.read(mapper,"mLux")==nativeLux&&TraditionalAdapter.read(mapper,"mBrightness")==nativeBrightness&&Arrays.equals(nativeLux,original.lux())&&Arrays.equals(nativeBrightness,originalBrightness);}
    private boolean authorized(HookRuntime state){return transactionUser>=0?ForegroundUser.matches(transactionUser,user.getAsInt()):state.appliesToUser();}
    private IllegalStateException failed(String message,Throwable error){
        Throwable cause=error;for(int i=0;i<8&&cause.getCause()!=null&&cause.getCause()!=cause;i++)cause=cause.getCause();
        String detail=cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());if(detail.length()>240)detail=detail.substring(0,240);
        lastError=detail;return new IllegalStateException(message+"；原因："+detail,error);
    }
    private void validateSpline()throws Exception {for(float x:nativeLux){float v=currentAt(x);if(!Float.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("比例样条输出无效");}}
    private Map<Field,Object> snapshot()throws Exception {Map<Field,Object> out=new LinkedHashMap<>();for(String n:new String[]{"mSpline","mUserLux","mUserBrightness","mAutoBrightnessAdjustment"}){Field f=TraditionalAdapter.find(mapper.getClass(),n);out.put(f,f.get(mapper));}return out;}
    public String name(){return "simple_mapping";}
    public String coordinate(){return "framework_brightness";}
    public String interpolation(){return "brightness_spline";}
    public boolean nativeMappingHooks(){return true;}
    public void installMappingHooks(Class<?> type)throws Exception{TraditionalHooks.install(type,false);}
    public boolean usesRuntimeUserIdentity(){return true;}
    public String currentPipelineRoute(){return "mapping";}
    public boolean memoryContextActive()throws Exception{return TraditionalAdapter.read(abc,"mCurrentBrightnessMapper")==mapper;}
    public boolean memoryRestoreIncludesModel(){return true;}
    public boolean savedCurveSupported(){return true;}
    public int savedPointLimit(){return SavedCurve.MAX_POINTS;}
    public String memoryDescription(){return "本机使用亮度比例曲线，手动模型支持 1 个偏好点";}
    public void putCurveState(JSONObject s)throws Exception {s.put("simple_mapper_class",mapper.getClass().getName()).put("simple_mapping_active",memoryContextActive()).put("simple_curve_points",nativeLux.length).put("simple_curve_nit_calibrated",false).put("simple_curve_last_error",lastError);if(automatic!=null)s.put("automatic_mode_route",automatic.route);}
    public Object get(String n)throws Exception {if(n.equals("mUserSerial"))return user.getAsInt();if(n.equals("mUseAutoBrightness")){if(automatic==null)automatic=new AutomaticModeAccess(owner,abc);return automatic.read();}if(n.equals("mIsHaveGoodCurve"))return null;return TraditionalAdapter.read(mapper,n);}
    public CurvePlan plan(){return active;}
    public float[] fullLux(){return original.lux();}
    public float[] fullNit(){return original.nit();}
    public float[] baselineLux(){return applied==null?original.lux():applied.lux.clone();}
    public float[] baselineNit(){return (applied==null?originalBrightness:applied.brightness).clone();}
    public float currentAt(float lux)throws Exception {return ((Number)interpolate.invoke(TraditionalAdapter.read(mapper,"mSpline"),lux)).floatValue();}
    private float[][] splinePoints()throws Exception {
        Object spline=TraditionalAdapter.read(mapper,"mSpline");String type=spline.getClass().getName();
        if(!type.equals("android.util.Spline$LinearSpline")&&!type.equals("android.util.Spline$MonotoneCubicSpline"))throw new IllegalArgumentException("比例曲线节点结构未兼容");
        float[] x=((float[])TraditionalAdapter.read(spline,"mX")).clone(),y=((float[])TraditionalAdapter.read(spline,"mY")).clone();
        CurvePlan.points(x,y,min,max);if(x[x.length-1]<factoryLux[3])throw new IllegalArgumentException("比例曲线节点范围无效");
        for(int i=0;i<x.length;i++)if(Math.abs(currentAt(x[i])-y[i])>4*Math.max(Math.ulp(currentAt(x[i])),Math.ulp(y[i])))throw new IllegalArgumentException("比例曲线节点与实际输出不一致");
        return new float[][]{x,y};
    }
    public float[] currentLux()throws Exception {return splinePoints()[0];}
    public float[] currentNit()throws Exception {return splinePoints()[1];}
    public void clearMemory()throws Exception {
        if(memoryContextActive())reset.invoke(abc);else instanceMethod("clearUserDataPoints",void.class).invoke(mapper);
        // Native clearUserDataPoints can leave an adjustment when no anchor exists.
        if(number("mAutoBrightnessAdjustment")!=0)instanceMethod("setAutoBrightnessAdjustment",boolean.class,float.class).invoke(mapper,0f);
    }
    public void configure(float[] factors)throws Exception {configure(factors,0);}
    public void configure(float[] factors,float floor)throws Exception {
        if(factors==null&&active==null)return;
        CurvePlan next=factors==null?null:proposed(factors,floor);
        if(!owned()){active=null;applied=null;if(factors==null)return;throw new IllegalStateException("系统比例曲线已改变，已交回系统，请重新读取");}
        float[] x=next==null?original.lux():savedBase==null?original.lux():next.lux();
        float[] y=next==null?originalBrightness:savedBase==null?original.reshape(min,max,factors,floor):next.nits();
        CurvePlan.points(x,y,min,max);
        if(Arrays.equals(baselineLux(),x)&&Arrays.equals(baselineNit(),y)){active=next;return;}
        boolean neutral=Arrays.equals(original.lux(),x)&&Arrays.equals(originalBrightness,y);
        Base desired=neutral?null:new Base(x,y,createSpline.invoke(null,x.clone(),y.clone()));
        Map<Field,Object> fields=snapshot();Object model=TraditionalAdapter.read(abc,"mShortTermModel");Map<Field,Object> modelBefore=TraditionalAdapter.snapshot(model);
        Base old=applied;CurvePlan oldPlan=active;
        int previousUser=transactionUser,expectedUser=user.getAsInt();
        if(expectedUser<0)throw new IllegalStateException("当前用户尚未确认，未修改比例曲线");
        try {
            // reload() validates the config user before calling us, but commits boundUser later.
            // Authorize only this synchronous transaction with its verified current identity.
            transactionUser=expectedUser;
            // Native final arrays remain untouched. Hook arguments supply the selected baseline.
            applied=desired;clearMemory();compute.invoke(mapper);validateSpline();splinePoints();
            if(!ForegroundUser.matches(expectedUser,user.getAsInt()))throw new IllegalStateException("曲线提交期间用户已变化");
            if(!owned())throw new IllegalStateException("系统比例曲线已被其他所有者替换");
            float[][] installed=splinePoints();if(!Arrays.equals(installed[0],x)||!Arrays.equals(installed[1],y))throw new IllegalStateException("系统未采用比例曲线");
            active=next;lastError="";
        }catch(Throwable error){if(owned()){applied=old;active=oldPlan;TraditionalAdapter.restore(mapper,fields);TraditionalAdapter.restore(model,modelBefore);}else{applied=null;active=null;}throw failed("比例曲线更新未完成，已撤销本次接管",error);}
        finally{transactionUser=previousUser;}
    }
    static void installHooks(Class<?> type,List<XC_MethodHook.Unhook> hooks)throws Exception {
        Method adjusted=TraditionalAdapter.method(type,"getAdjustedCurve",float[].class,float[].class,float.class,float.class,float.class,float.class);
        Method unadjusted=TraditionalAdapter.method(type,"getUnadjustedBrightness",float.class);
        hooks.add(XposedBridge.hookMethod(adjusted,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            HookRuntime state=TraditionalHooks.state(p.thisObject);if(state==null||!(state.kernel instanceof SimpleCurveAdapter))return;
            SimpleCurveAdapter a=(SimpleCurveAdapter)state.kernel;
            try{if(a.applied==null||!a.authorized(state))return;if(!a.owned())throw new IllegalStateException("系统比例曲线已变化");if(p.args[0]!=a.nativeLux||p.args[1]!=a.nativeBrightness)return;
                p.args[0]=a.applied.lux.clone();p.args[1]=a.applied.brightness.clone();
            }catch(Throwable error){a.applied=null;state.fault(error);}
        }}));
        hooks.add(XposedBridge.hookMethod(unadjusted,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            HookRuntime state=TraditionalHooks.state(p.thisObject);if(state==null||!(state.kernel instanceof SimpleCurveAdapter))return;
            SimpleCurveAdapter a=(SimpleCurveAdapter)state.kernel;
            try{if(a.applied==null||!a.authorized(state))return;if(!a.owned())throw new IllegalStateException("系统比例曲线已变化");float lux=(Float)p.args[0];if(!Float.isFinite(lux)||lux<0)return;p.setResult(a.interpolate.invoke(a.applied.spline,lux));}
            catch(Throwable error){a.applied=null;state.fault(error);}
        }}));
    }
    public boolean persistentMemorySupported(){if(persistence==null)try{Method m=TraditionalAdapter.method(abc.getClass(),"setScreenBrightnessByUser",float.class,float.class);persistence=!Modifier.isStatic(m.getModifiers())&&m.getReturnType()==boolean.class;}catch(Exception missing){persistence=false;}return persistence;}
    public int manualPointCapacity(){return 1;}
    public JSONArray manualPoints()throws Exception {JSONArray out=new JSONArray();float lux=number("mUserLux"),value=number("mUserBrightness");if(Float.isFinite(lux)&&lux>=0&&Float.isFinite(value)&&value>=0&&value<=1)out.put(new JSONObject().put("lux",lux).put("value",value));return out;}
    public void restoreManualPoints(JSONArray points)throws Exception {restorePoints(points,false);}
    public void replaceManualPoints(JSONArray points)throws Exception {restorePoints(points,true);}
    private void restorePoints(JSONArray points,boolean replace)throws Exception {
        PersistentMemory.validate(points);if(!persistentMemorySupported()||points.length()>1||!memoryContextActive()||!owned())throw new IllegalArgumentException("当前比例曲线不能恢复此手动记忆");
        for(int i=0;i<points.length();i++){JSONObject p=points.getJSONObject(i);if(p.getDouble("lux")>factoryLux[3]||p.getDouble("value")>1)throw new IllegalArgumentException("手动记忆超出本机比例范围");}
        Map<Field,Object> before=snapshot();Object model=TraditionalAdapter.read(abc,"mShortTermModel");Map<Field,Object> modelBefore=TraditionalAdapter.snapshot(model);
        try{if(replace)clearMemory();for(int i=0;i<points.length();i++){JSONObject p=points.getJSONObject(i);if(!Boolean.TRUE.equals(TraditionalAdapter.method(abc.getClass(),"setScreenBrightnessByUser",float.class,float.class).invoke(abc,(float)p.getDouble("lux"),(float)p.getDouble("value"))))throw new IllegalStateException("系统未采用手动记忆");}validateSpline();}
        catch(Throwable error){if(owned()){TraditionalAdapter.restore(mapper,before);TraditionalAdapter.restore(model,modelBefore);}throw failed("比例曲线记忆恢复未完成，已撤销本次操作",error);}
    }
}
