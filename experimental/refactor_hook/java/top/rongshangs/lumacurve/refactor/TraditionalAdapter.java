package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntSupplier;

/** PhysicalMappingStrategy coordinates are physical nit / framework brightness. */
public final class TraditionalAdapter extends CurveBackend {
    final Object mapper,owner,abc,baseline;
    final TraditionalCurve original;
    final Method setter,reset,convert,splineMethod;
    final Constructor<?> configConstructor;
    final IntSupplier user;
    final String userLuxField;
    final Method adjustedInterpolation;
    private CurvePlan active;private Boolean persistenceSupported;
    Object applied;
    boolean updating;
    private static final java.util.concurrent.ConcurrentMap<Class<?>,java.util.concurrent.ConcurrentMap<String,Field>> sharedFields=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String,Field> fields=new HashMap<>();
    public TraditionalAdapter(Object owner,Object mapper,Object abc,IntSupplier user)throws Exception {
        super(bound(mapper,false),bound(mapper,true));this.owner=owner;this.mapper=mapper;this.abc=abc;this.user=user;
        if(!mapper.getClass().getName().equals("com.android.server.display.MiuiPhysicalBrightnessMappingStrategy")&&
           !mapper.getClass().getName().equals("com.android.server.display.BrightnessMappingStrategy$PhysicalMappingStrategy"))
            throw new IllegalArgumentException("未知物理映射策略："+mapper.getClass().getName());
        if(((Number)read(owner,"mDisplayId")).intValue()!=0||((Number)method(mapper.getClass(),"getMode").invoke(mapper)).intValue()!=0)
            throw new IllegalArgumentException("仅接入主屏普通模式");
        Object map=read(abc,"mBrightnessMappingStrategyMap");
        if(method(map.getClass(),"get",int.class).invoke(map,0)!=mapper)throw new IllegalStateException("主屏默认映射对象不一致");
        baseline=read(mapper,"mConfig");if(baseline==null)throw new IllegalStateException("设备本地曲线尚未就绪");
        Object pair=method(baseline.getClass(),"getCurve").invoke(baseline);
        original=new TraditionalCurve((float[])read(pair,"first"),(float[])read(pair,"second"));
        factoryLux=original.controlsLux();factoryNit=original.controlsNit();
        new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});
        configConstructor=baseline.getClass().getDeclaredConstructor(float[].class,float[].class,Map.class,Map.class,String.class,boolean.class,long.class,float.class,float.class);configConstructor.setAccessible(true);
        // Metadata and app corrections must survive a baseline edit intact.
        for(String name:new String[]{"mCorrectionsByPackageName","mCorrectionsByCategory","mDescription","mShouldCollectColorSamples","mShortTermModelTimeout","mShortTermModelLowerLuxMultiplier","mShortTermModelUpperLuxMultiplier"})read(baseline,name);
        setter=method(mapper.getClass(),"setBrightnessConfiguration",baseline.getClass());
        reset=method(abc.getClass(),"resetShortTermModel");convert=method(mapper.getClass(),"convertToBrightness",float.class);
        Object spline=read(mapper,"mBrightnessSpline");splineMethod=interpolator(spline);
        if(setter.getReturnType()!=boolean.class||reset.getReturnType()!=void.class||convert.getReturnType()!=float.class||splineMethod.getReturnType()!=float.class)
            throw new IllegalArgumentException("传统曲线方法签名已变化");
        userLuxField=mapper.getClass().getName().contains("MiuiPhysical")?"mShortTermModelUserLux":"mUserLux";
        if(find(mapper.getClass(),userLuxField).getType()!=float.class)throw new IllegalArgumentException("手动记忆坐标不兼容");
        if(userLuxField.equals("mUserLux")){Object adjusted=read(mapper,"mAdjustedNitsToBrightnessSpline");adjustedInterpolation=interpolator(adjusted);}else adjustedInterpolation=null;
    }
    static Field find(Class<?> type,String name)throws Exception {java.util.concurrent.ConcurrentMap<String,Field> cache=sharedFields.computeIfAbsent(type,k->new java.util.concurrent.ConcurrentHashMap<>());Field existing=cache.get(name);if(existing!=null)return existing;for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);cache.putIfAbsent(name,f);return cache.get(name);}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
    static Method interpolator(Object spline)throws Exception {Method found=null;for(Class<?> c=spline.getClass();c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod("interpolate",float.class);if(m.getReturnType()!=float.class)throw new IllegalArgumentException("样条接口返回类型不兼容");m.setAccessible(true);found=m;}catch(NoSuchMethodException absent){}if(found==null)throw new NoSuchMethodException("interpolate");return found;}
    static Object read(Object o,String name)throws Exception{return find(o.getClass(),name).get(o);}
    static Method method(Class<?> type,String name,Class<?>...params)throws Exception{for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,params);m.setAccessible(true);return m;}catch(NoSuchMethodException missing){}throw new NoSuchMethodException(name);}
    static float bound(Object mapper,boolean high)throws Exception{float[] n=(float[])read(mapper,"mNits");if(n==null||n.length<2)throw new IllegalArgumentException("设备亮度标定未就绪");for(int i=0;i<n.length;i++)if(!Float.isFinite(n[i])||n[i]<0||(i>0&&n[i]<=n[i-1]))throw new IllegalArgumentException("设备亮度标定无效");return high?n[n.length-1]:n[0];}
    public String name(){return "physical_mapping";}
    public boolean persistentMemorySupported(){if(persistenceSupported==null)try{persistenceSupported=method(abc.getClass(),"setScreenBrightnessByUser",float.class,float.class).getReturnType()==boolean.class && method(mapper.getClass(),"getUserBrightness").getReturnType()==float.class;}catch(Exception unavailable){persistenceSupported=false;}return persistenceSupported;}
    public int manualPointCapacity(){return persistentMemorySupported()?1:0;}
    public org.json.JSONArray manualPoints()throws Exception {
        org.json.JSONArray points=new org.json.JSONArray();float lux=number("mUserLux");
        if(Float.isFinite(lux)&&lux>=0){float value=((Number)method(mapper.getClass(),"getUserBrightness").invoke(mapper)).floatValue();if(Float.isFinite(value)&&value>=0&&value<=1)points.put(new org.json.JSONObject().put("lux",lux).put("value",value));}return points;
    }
    public float manualDisplayValue(float value)throws Exception{return ((Number)method(mapper.getClass(),"convertToNits",float.class).invoke(mapper,value)).floatValue();}
    public void restoreManualPoints(org.json.JSONArray points)throws Exception {restore(points,false);}
    public void replaceManualPoints(org.json.JSONArray points)throws Exception {restore(points,true);}
    private void restore(org.json.JSONArray points,boolean replace)throws Exception {
        PersistentMemory.validate(points);if(points.length()>1||read(abc,"mCurrentBrightnessMapper")!=mapper)throw new IllegalArgumentException("当前映射不能恢复此手动记忆");
        for(int i=0;i<points.length();i++){org.json.JSONObject p=points.getJSONObject(i);if(p.getDouble("lux")>factoryLux[3]||p.getDouble("value")>1)throw new IllegalArgumentException("手动记忆超出设备映射范围");}
        Map<Field,Object> before=snapshot(mapper);Object model=read(abc,"mShortTermModel");Map<Field,Object> modelBefore=snapshot(model);
        try{if(replace)clearMemory();for(int i=0;i<points.length();i++){org.json.JSONObject p=points.getJSONObject(i);if(!Boolean.TRUE.equals(method(abc.getClass(),"setScreenBrightnessByUser",float.class,float.class).invoke(abc,(float)p.getDouble("lux"),(float)p.getDouble("value"))))throw new IllegalStateException("系统未采用手动记忆");}}
        catch(Throwable error){restore(mapper,before);restore(model,modelBefore);throw new IllegalStateException("手动记忆恢复失败，已回滚",error);}
    }
    public CurvePlan plan(){return active;}
    public float[] fullLux(){return original.lux();}
    public float[] fullNit(){return original.nit();}
    public float currentAt(float lux)throws Exception{return ((Number)splineMethod.invoke(read(mapper,"mBrightnessSpline"),lux)).floatValue();}
    public float memoryAt(float lux)throws Exception{return adjustedInterpolation==null?((Number)convert.invoke(mapper,currentAt(lux))).floatValue():((Number)adjustedInterpolation.invoke(read(mapper,"mAdjustedNitsToBrightnessSpline"),currentAt(lux))).floatValue();}
    public void clearMemory()throws Exception{if(read(abc,"mCurrentBrightnessMapper")==mapper)reset.invoke(abc);else method(mapper.getClass(),"clearUserDataPoints").invoke(mapper);}
    public Object get(String name)throws Exception{
        // Compatibility surface used by shared state/tuning; never mix nit and drag coordinates.
        if(name.equals("mUserSerial"))return user.getAsInt();
        if(name.equals("mUseAutoBrightness"))return read(owner,"mUseAutoBrightness");
        if(name.equals("mUserLux"))return read(mapper,userLuxField);
        if(name.equals("mIsHaveGoodCurve"))return null; // Refactor-only concept, unknown here.
        Field f=fields.get(name);if(f==null){f=find(mapper.getClass(),name);fields.put(name,f);}return f.get(mapper);
    }
    public float[] currentLux()throws Exception {
        // Draw the OEM spline itself, including its between-node interpolation.
        TreeSet<Float> points=new TreeSet<>();for(float v:original.lux())points.add(v);
        float userLux=number("mUserLux");if(Float.isFinite(userLux)&&userLux>=0&&userLux<=factoryLux[3])points.add(userLux);
        for(int i=0;i<=128;i++)points.add((float)Math.expm1(Math.log1p(factoryLux[3])*i/128));
        float[] result=new float[points.size()];int i=0;for(float v:points)result[i++]=v;return result;
    }
    public float[] currentNit()throws Exception{float[] x=currentLux(),n=new float[x.length];for(int i=0;i<x.length;i++)n[i]=currentAt(x[i]);return n;}
    Object configuration(float[] factors,float floor)throws Exception {
        return configConstructor.newInstance(original.lux(),original.reshape(min,max,factors,floor),
            new HashMap<>((Map<?,?>)read(baseline,"mCorrectionsByPackageName")),new HashMap<>((Map<?,?>)read(baseline,"mCorrectionsByCategory")),
            read(baseline,"mDescription"),read(baseline,"mShouldCollectColorSamples"),read(baseline,"mShortTermModelTimeout"),
            read(baseline,"mShortTermModelLowerLuxMultiplier"),read(baseline,"mShortTermModelUpperLuxMultiplier"));
    }
    public void configure(float[] factors)throws Exception {
        configure(factors,0);
    }
    public void configure(float[] factors,float floor)throws Exception {
        CurvePlan next=factors==null?null:new CurvePlan(factoryLux,factoryNit,min,max,factors,floor);
        Object current=read(mapper,"mConfig");
        if(factors==null&&active==null)return;
        // An OEM/cloud/user replacement must not be overwritten by our recovery.
        if(current!=baseline&&current!=applied){active=null;applied=null;if(factors==null)return;throw new IllegalStateException("系统基准曲线已改变，请重启应用后重新读取");}
        Object desired=next==null?baseline:configuration(factors,floor);
        Map<Field,Object> before=snapshot(mapper),modelBefore=snapshot(read(abc,"mShortTermModel"));
        Object model=read(abc,"mShortTermModel");CurvePlan old=active;Object oldApplied=applied;
        updating=true;
        try {
            // Nested configuration hooks recognize our own object, not a polling rewrite.
            applied=next==null?null:desired;
            setter.invoke(mapper,desired);
            Object installed=read(mapper,"mConfig");
            if(!installed.equals(desired))throw new IllegalStateException("系统未采用提交的设备曲线");
            applied=next==null?null:installed;
            // Never reset idle/doze mappers as a side effect of editing the normal curve.
            if(read(abc,"mCurrentBrightnessMapper")==mapper)reset.invoke(abc);
            else method(mapper.getClass(),"clearUserDataPoints").invoke(mapper);
            active=next;
        }catch(Throwable error){restore(mapper,before);restore(model,modelBefore);active=old;applied=oldApplied;throw new IllegalStateException("传统曲线更新失败，已回滚",error);}finally{updating=false;}
    }
    static Map<Field,Object> snapshot(Object target)throws Exception {
        Map<Field,Object> out=new LinkedHashMap<>();
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers())||Modifier.isFinal(f.getModifiers()))continue;f.setAccessible(true);Object v=f.get(target);
            if(v instanceof Map)v=new HashMap<>((Map<?,?>)v);
            else if(v instanceof List)v=new ArrayList<>((List<?>)v);
            else if(v!=null&&v.getClass().isArray()){Object copy=Array.newInstance(v.getClass().getComponentType(),Array.getLength(v));System.arraycopy(v,0,copy,0,Array.getLength(v));v=copy;}
            out.put(f,v);
        }return out;
    }
    static void restore(Object target,Map<Field,Object> saved)throws Exception {for(Map.Entry<Field,Object> entry:saved.entrySet())entry.getKey().set(target,entry.getValue());}
}
