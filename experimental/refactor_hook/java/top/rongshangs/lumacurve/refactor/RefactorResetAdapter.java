package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Native reset-derived 9/22 coefficient ABI. No invented default array or portable anchors. */
final class RefactorResetAdapter extends CurveBackend {
    private static final String[] ARRAYS={"mLuxToLogicalNit","mGoodLuxToLogicalNit","mLogicalCurve","mGoodLogicalCurve","mLogicalData"};
    private static final String[] SCALARS={"mUserLux","mUserLogicalNit","mIsHaveGoodCurve"};
    private final Object target;private final Method reset,read;
    private final Map<String,Field> fields=new HashMap<>();
    private final Snapshot nativeBase;
    private final double[] original;
    private Object ownedCurrent,ownedLogical;private CurvePlan active;private float[] factors;private float floor;private long repairs;

    static void validateClass(Class<?> type)throws Exception{
        for(String n:new String[]{"mDefaultLuxToLogicalNit"})try{type.getDeclaredField(n);throw new IllegalArgumentException("独立默认系数字段存在，不能改用重建路线");}catch(NoSuchFieldException absent){}
        for(String n:new String[]{"getDefaultLogicalBrt","calculateBrt"})for(Method m:type.getDeclaredMethods())if(m.getName().equals(n))throw new IllegalArgumentException("默认系数接口残缺，不能改用重建路线");
        for(String n:ARRAYS)require(type,n,n.equals("mLuxToLogicalNit")||n.equals("mGoodLuxToLogicalNit")?double[].class:float[].class);
        for(String n:new String[]{"mUserLux","mUserLogicalNit"})require(type,n,float.class);
        for(String n:new String[]{"mUseAutoBrightness","mIsHaveGoodCurve"})require(type,n,boolean.class);
        for(String n:new String[]{"mDisplayId","mUserSerial"})require(type,n,int.class);
        method(type,"getLogicalBrt",double.class,float.class);method(type,"resetDefaultSpline",void.class);
        method(type,"getCurrentNit",float.class,float.class,float.class,boolean.class);
        method(type,"updateLogicalCurve",void.class,float.class,float.class);
        method(type,"shouldUseGoodCurve",void.class);method(type,"setAutoBrightnessState",void.class,boolean.class);
    }
    private static void require(Class<?> t,String n,Class<?> kind)throws Exception{
        Field f=t.getDeclaredField(n);if(f.getType()!=kind||Modifier.isStatic(f.getModifiers())||Modifier.isFinal(f.getModifiers()))throw new IllegalArgumentException("重建曲线字段不兼容："+n);
    }
    static Method method(Class<?> t,String n,Class<?> result,Class<?>... args)throws Exception{
        Method m=t.getDeclaredMethod(n,args);if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new IllegalArgumentException("重建曲线接口不兼容："+n);m.setAccessible(true);return m;
    }
    RefactorResetAdapter(Object target,float min,float max)throws Exception{
        super(min,max);this.target=target;validateClass(target.getClass());
        if(integer("mDisplayId")!=0||!Float.isFinite(min)||!Float.isFinite(max)||min<0||max<=min)throw new IllegalArgumentException("主屏逻辑亮度范围无效");
        reset=method(target.getClass(),"resetDefaultSpline",void.class);read=method(target.getClass(),"getLogicalBrt",double.class,float.class);
        validateArrays();Snapshot before=new Snapshot();Snapshot base;
        try{reset.invoke(target);validateArrays();if(number("mUserLux")>=0||number("mUserLogicalNit")>=0||Boolean.TRUE.equals(get("mIsHaveGoodCurve")))throw new IllegalStateException("原生重建未清除学习状态");base=new Snapshot();}
        finally{before.restore();}
        nativeBase=base;original=((double[])base.copies.get("mLuxToLogicalNit")).clone();
        if(original[0]!=0||original[2]<=0||original[5]<=original[2]||original[8]<=0)throw new IllegalArgumentException("原生重建基准无效");
        double end=original[5]+(max-original[7])/original[8];if(!Double.isFinite(end)||end<=original[5]||end>Float.MAX_VALUE)throw new IllegalArgumentException("原生重建上限无效");
        factoryLux=new float[]{0,(float)original[2],(float)original[5],(float)end};factoryNit=new float[4];
        for(int i=0;i<4;i++)factoryNit[i]=evaluate(original,factoryLux[i]);
        new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});
        if(!close(factoryNit[3],max)||!close(original[6]+(original[7]-original[6])/(original[5]-original[4])*original[2],factoryNit[1]))throw new IllegalArgumentException("原生重建分段不连续");
        // Verify the private consumer against the live native curve without resetting it again.
        double[] current=(double[])get("mLuxToLogicalNit");for(float x:factoryLux)if(!close(nativeAt(x),evaluate(current,x)))throw new IllegalArgumentException("原生分段消费者不兼容");
        ownedCurrent=get("mLuxToLogicalNit");ownedLogical=get("mLogicalCurve");
    }
    private Field field(String n)throws Exception{Field f=fields.get(n);if(f==null){f=target.getClass().getDeclaredField(n);f.setAccessible(true);fields.put(n,f);}return f;}
    public Object get(String n)throws Exception{return field(n).get(target);}
    private void validateArrays()throws Exception{
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(String n:ARRAYS){Object a=get(n);int length=n.equals("mLogicalCurve")||n.equals("mGoodLogicalCurve")?22:9;if(a==null||Array.getLength(a)!=length||!seen.add(a))throw new IllegalArgumentException("重建曲线容器不兼容："+n);for(int i=0;i<length;i++)if(!Double.isFinite(((Number)Array.get(a,i)).doubleValue()))throw new IllegalArgumentException("重建曲线包含无效值");}
    }
    private final class Snapshot {
        final Map<String,Object> values=new LinkedHashMap<>(),copies=new LinkedHashMap<>();
        Snapshot()throws Exception{for(String n:ARRAYS){Object a=get(n),copy=Array.newInstance(a.getClass().getComponentType(),Array.getLength(a));System.arraycopy(a,0,copy,0,Array.getLength(a));values.put(n,a);copies.put(n,copy);}for(String n:SCALARS)values.put(n,get(n));}
        void restore()throws Exception{for(String n:ARRAYS){Object a=values.get(n),copy=copies.get(n);System.arraycopy(copy,0,a,0,Array.getLength(a));field(n).set(target,a);}for(String n:SCALARS)field(n).set(target,values.get(n));}
    }
    private float evaluate(double[] c,float lux){
        double value;if(lux<c[2])value=c[1]+lux*(c[3]-c[1])/(c[2]-c[0]);else if(lux<c[5])value=c[6]+lux*(c[7]-c[6])/(c[5]-c[4]);else value=c[7]+(lux-c[5])*c[8];
        if(!Double.isFinite(value))throw new IllegalArgumentException("分段曲线无效");return Math.max(min,Math.min(max,(float)value));
    }
    private float nativeAt(float lux)throws Exception{float y=((Number)read.invoke(target,lux)).floatValue();if(!Float.isFinite(y)||y<min||y>max)throw new IllegalStateException("原生逻辑亮度超出范围");return y;}
    private static boolean close(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=Math.max(.0003,Math.abs(b)*.000004);}
    private static boolean same(Object a,Object b){if(a==null||b==null||a.getClass()!=b.getClass()||Array.getLength(a)!=Array.getLength(b))return false;for(int i=0;i<Array.getLength(a);i++)if(!close(((Number)Array.get(a,i)).doubleValue(),((Number)Array.get(b,i)).doubleValue()))return false;return true;}
    private boolean resetState()throws Exception{if(number("mUserLux")>=0||number("mUserLogicalNit")>=0||Boolean.TRUE.equals(get("mIsHaveGoodCurve")))return false;for(String n:ARRAYS)if(!same(get(n),nativeBase.copies.get(n)))return false;return true;}
    private boolean knownCurrent()throws Exception{return get("mLuxToLogicalNit")==ownedCurrent&&get("mLogicalCurve")==ownedLogical||resetState()||(!Boolean.TRUE.equals(get("mIsHaveGoodCurve"))&&same(get("mLuxToLogicalNit"),get("mGoodLuxToLogicalNit"))&&same(get("mLogicalCurve"),get("mGoodLogicalCurve")));}
    private double[] coefficients(CurvePlan p){float[] x=p.lux(),y=p.nits();for(int i=1;i<4;i++)if(y[i]<=y[i-1])throw new IllegalArgumentException("原生拟合要求相邻亮度递增");double slope=(y[2]-y[1])/(double)(x[2]-x[1]);return new double[]{0,y[0],x[1],y[1],0,x[2],y[1]-slope*x[1],y[2],(y[3]-y[2])/(double)(x[3]-x[2])};}
    private void installBase()throws Exception{
        if(!resetState())throw new IllegalStateException("重建结果不匹配本机原生基准，停止覆盖");
        double[] c=coefficients(active);System.arraycopy(c,0,get("mLuxToLogicalNit"),0,9);
        for(String n:new String[]{"mLogicalCurve","mGoodLogicalCurve"}){float[] a=(float[])get(n);for(int i=0;i<8;i++)a[i]=(float)c[i];a[8]=(float)c[0];a[10]=(float)c[5];a[11]=a[12]=a[18]=(float)c[1];a[13]=(float)c[7];a[21]=(float)c[8];}
        for(float x:factoryLux)if(!close(nativeAt(x),active.at(x)))throw new IllegalStateException("重建曲线原生读回不一致");ownedCurrent=get("mLuxToLogicalNit");ownedLogical=get("mLogicalCurve");
    }
    public void configure(float[] next)throws Exception{configure(next,0);}
    public void configure(float[] next,float nextFloor)throws Exception{
        if(savedBase!=null)throw new IllegalArgumentException("本机拟合曲线尚不支持记忆转存；保留已保存设置");
        CurvePlan proposed=next==null?null:proposed(next,nextFloor);if(proposed!=null)coefficients(proposed);validateArrays();
        if(!knownCurrent())throw new IllegalStateException("曲线已被其他所有者替换，停止覆盖");
        if(active==null&&(proposed==null||nextFloor==0&&Arrays.equals(next,new float[]{1,1,1,1}))){active=proposed;factors=next==null?null:next.clone();floor=nextFloor;return;}
        Snapshot before=new Snapshot();CurvePlan old=active;float[] oldFactors=factors;float oldFloor=floor;Object oldOwned=ownedCurrent,oldLogical=ownedLogical;
        try{reset.invoke(target);validateArrays();active=proposed;if(proposed!=null)installBase();else if(!resetState())throw new IllegalStateException("原生恢复结果不匹配");factors=next==null?null:next.clone();floor=next==null?0:nextFloor;ownedCurrent=get("mLuxToLogicalNit");ownedLogical=get("mLogicalCurve");}
        catch(Throwable failure){before.restore();active=old;factors=oldFactors;floor=oldFloor;ownedCurrent=oldOwned;ownedLogical=oldLogical;throw new IllegalStateException("重建曲线更新失败，已回滚",failure);}
    }
    public void afterReset()throws Exception{if(active==null)return;validateArrays();Snapshot before=new Snapshot();try{installBase();}catch(Throwable failure){before.restore();throw new IllegalStateException("重建曲线接续失败，已回滚",failure);}}
    public boolean reconcile()throws Exception{if(active==null)return false;validateArrays();if(get("mLogicalCurve")!=ownedLogical&&resetState()&&!(floor==0&&Arrays.equals(factors,new float[]{1,1,1,1}))){afterReset();repairs++;return true;}if(!knownCurrent())throw new IllegalStateException("当前分段曲线所有权已变化");ownedCurrent=get("mLuxToLogicalNit");ownedLogical=get("mLogicalCurve");return false;}
    public void clearMemory()throws Exception{CurvePlan old=active;if(old==null){if(!knownCurrent())throw new IllegalStateException("曲线所有权已变化，停止清除");Snapshot before=new Snapshot();try{reset.invoke(target);validateArrays();if(!resetState())throw new IllegalStateException("原生重置结果无效");ownedCurrent=get("mLuxToLogicalNit");ownedLogical=get("mLogicalCurve");}catch(Throwable e){before.restore();throw new IllegalStateException("清除记忆失败，已回滚",e);}}else configure(factors,floor);}
    public float currentInputLux(float lux){return active!=null&&factors!=null&&factors[3]<1&&Float.isFinite(lux)&&lux>factoryLux[3]?factoryLux[3]:lux;}
    public float currentAt(float lux)throws Exception{if(!Float.isFinite(lux)||lux<0)throw new IllegalArgumentException("照度无效");return nativeAt(currentInputLux(lux));}
    public float[] currentLux()throws Exception{double[] c=(double[])get("mLuxToLogicalNit");double end=c[8]>0?c[5]+(max-c[7])/c[8]:factoryLux[3];float last=(float)Math.max(factoryLux[3],end);if(c[2]<=0||c[5]<=c[2]||!Float.isFinite(last)||last<=c[5])throw new IllegalStateException("实时曲线区间无效");return new float[]{0,(float)c[2],(float)c[5],last};}
    public float[] currentNit()throws Exception{float[] x=currentLux(),y=new float[x.length];for(int i=0;i<x.length;i++)y[i]=currentAt(x[i]);return y;}
    public String name(){return "refactor";}public String legacyConfigBackend(){return "refactor";}public String compatibilityRoute(){return "refactor_reset_coefficients";}
    public String coordinate(){return "logical_nit";}public String interpolation(){return "coefficients";}public boolean strictOrder(){return true;}
    public String currentPipelineRoute(){return "refactor";}public Object target(){return target;}public CurvePlan plan(){return active;}
    public boolean memoryHandoverSupported(){return false;}public int manualPointCapacity(){return 1;}
    public String memoryDescription(){return "本机采用原生重建分段曲线；仅报告最近一次手动输入";}
    public JSONArray manualPoints()throws Exception{JSONArray out=new JSONArray();float x=number("mUserLux"),y=number("mUserLogicalNit");if(Float.isFinite(x)&&x>=0&&Float.isFinite(y)&&y>=min&&y<=max)out.put(new JSONObject().put("lux",x).put("value",y));return out;}
    public void putVerification(JSONObject j)throws Exception{j.put("refactor_curve_verification",new JSONObject().put("profile","reset_coefficients_9_22").put("baseline_source","native_reset_transaction").put("native_state_restored_after_probe",true).put("repair_count",repairs).put("portable_memory_supported",false));}
}
