package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Older Refactor 9-coefficient / 22-state ABI. Native fitting and output stay native. */
final class RefactorCoefficientsAdapter extends CurveBackend {
    private static final String[] DOUBLES={"mDefaultLuxToLogicalNit","mLuxToLogicalNit","mGoodLuxToLogicalNit"};
    private static final String[] FLOATS={"mLogicalCurve","mGoodLogicalCurve","mLogicalData"};
    private static final String[] SCALARS={"mUserLux","mUserLogicalNit","mIsHaveGoodCurve"};
    private final Object target;private final Map<String,Field> fields=new HashMap<>();
    private final Method reset,calculate;private final double[] original;
    private CurvePlan active;private double[] installed;private float[] factors;private float floor;private long repairs;
    static void validateClass(Class<?> type)throws Exception{
        for(String name:DOUBLES)require(type,name,double[].class);
        for(String name:FLOATS)require(type,name,float[].class);
        for(String name:new String[]{"mUserLux","mUserLogicalNit"})require(type,name,float.class);
        for(String name:new String[]{"mUseAutoBrightness","mIsHaveGoodCurve"})require(type,name,boolean.class);
        for(String name:new String[]{"mDisplayId","mUserSerial"})require(type,name,int.class);
        method(type,"resetDefaultSpline",void.class);
        method(type,"calculateBrt",double.class,float.class,double[].class);
        method(type,"getDefaultLogicalBrt",double.class,float.class);
        method(type,"getCurrentNit",float.class,float.class,float.class,boolean.class);
        method(type,"updateLogicalCurve",void.class,float.class,float.class);
        method(type,"shouldUseGoodCurve",void.class);method(type,"setAutoBrightnessState",void.class,boolean.class);
    }
    private static void require(Class<?> t,String name,Class<?> kind)throws Exception{
        Field f=t.getDeclaredField(name);if(f.getType()!=kind||Modifier.isStatic(f.getModifiers()))throw new IllegalArgumentException("Refactor 系数字段不兼容："+name);
    }
    private static Method method(Class<?> t,String name,Class<?> result,Class<?>... args)throws Exception{
        Method m=t.getDeclaredMethod(name,args);if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new IllegalArgumentException("Refactor 系数接口不兼容："+name);m.setAccessible(true);return m;
    }
    RefactorCoefficientsAdapter(Object target,float min,float max)throws Exception{
        super(min,max);this.target=target;validateClass(target.getClass());
        if(integer("mDisplayId")!=0)throw new IllegalArgumentException("仅接入内置主屏");
        reset=method(target.getClass(),"resetDefaultSpline",void.class);calculate=method(target.getClass(),"calculateBrt",double.class,float.class,double[].class);
        validateArrays();original=((double[])get(DOUBLES[0])).clone();
        if(!Float.isFinite(min)||!Float.isFinite(max)||min<0||max<=min||original[0]!=0||original[2]<=0||original[5]<=original[2]||original[8]<=0)throw new IllegalArgumentException("Refactor 系数基准无效");
        double end=original[5]+(max-original[7])/original[8];
        if(!Double.isFinite(end)||end<=original[5]||end>Float.MAX_VALUE)throw new IllegalArgumentException("无法读取本机逻辑曲线上限坐标");
        factoryLux=new float[]{0,(float)original[2],(float)original[5],(float)end};
        factoryNit=new float[]{at(original,factoryLux[0]),at(original,factoryLux[1]),at(original,factoryLux[2]),at(original,factoryLux[3])};
        new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});
        double left=original[1]+(original[3]-original[1])/(original[2]-original[0])*original[2];
        if(!close(left,factoryNit[1])||!close(factoryNit[3],max))throw new IllegalArgumentException("Refactor 系数分段不连续");
    }
    private Field field(String name)throws Exception{Field f=fields.get(name);if(f==null){f=target.getClass().getDeclaredField(name);f.setAccessible(true);fields.put(name,f);}return f;}
    public Object get(String name)throws Exception{return field(name).get(target);}
    private void set(String name,Object v)throws Exception{field(name).set(target,v);}
    private void validateArrays()throws Exception{
        Set<Object> identities=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(String name:DOUBLES){double[] a=(double[])get(name);if(a==null||a.length!=9||!identities.add(a))throw new IllegalArgumentException("Refactor 系数容器不兼容："+name);for(double x:a)if(!Double.isFinite(x))throw new IllegalArgumentException("Refactor 系数不是有限值");}
        for(String name:FLOATS){float[] a=(float[])get(name);int size=name.equals("mLogicalData")?9:22;if(a==null||a.length!=size||!identities.add(a))throw new IllegalArgumentException("Refactor 状态容器不兼容："+name);for(float x:a)if(!Float.isFinite(x))throw new IllegalArgumentException("Refactor 状态不是有限值");}
    }
    private float at(double[] c,float lux)throws Exception{
        float value=((Number)calculate.invoke(target,lux,c)).floatValue();if(!Float.isFinite(value)||value<min||value>max)throw new IllegalStateException("Refactor 返回超出逻辑范围的亮度");return value;
    }
    public String name(){return "refactor";}
    public String compatibilityRoute(){return "refactor_coefficients";}
    public Object target(){return target;}
    public CurvePlan plan(){return active;}
    // This ABI has no verified user-anchor export / replay. Preserve settings on disk,
    // but do not pretend its virtual fitting references are portable manual anchors.
    public boolean savedCurveSupported(){return false;}
    public boolean memoryHandoverSupported(){return false;}
    public int manualPointCapacity(){return 1;}
    public JSONArray manualPoints()throws Exception{
        float x=number("mUserLux"),y=number("mUserLogicalNit");JSONArray points=new JSONArray();
        if(Float.isFinite(x)&&x>=0&&x<=factoryLux[3]&&Float.isFinite(y)&&y>=min&&y<=max)points.put(new JSONObject().put("lux",x).put("value",y));return points;
    }
    public float currentAt(float lux)throws Exception{if(!Float.isFinite(lux)||lux<0)throw new IllegalArgumentException("照度无效");return at((double[])get("mLuxToLogicalNit"),lux);}
    public float[] currentLux()throws Exception{double[] c=(double[])get("mLuxToLogicalNit");double end=c[8]>0?c[5]+(max-c[7])/c[8]:factoryLux[3];float last=(float)Math.max(factoryLux[3],end);if(!Float.isFinite(last)||c[2]<=0||c[5]<=c[2]||last<=c[5])throw new IllegalStateException("当前系数曲线坐标不兼容");return new float[]{0,(float)c[2],(float)c[5],last};}
    public float[] currentNit()throws Exception{float[] x=currentLux(),y=new float[x.length];for(int i=0;i<x.length;i++)y[i]=currentAt(x[i]);return y;}
    public void configure(float[] next)throws Exception{configure(next,0);}
    public void clearMemory()throws Exception{configure(factors,floor);}
    public void configure(float[] next,float nextFloor)throws Exception{
        if(next!=null&&(next.length!=4||next[3]!=1))throw new IllegalArgumentException("本应用高照度端保持官方上限");
        if(savedBase!=null)throw new IllegalArgumentException("此系数路线尚不支持记忆转基础曲线；已保存设置保持不变");
        CurvePlan proposed=next==null?null:proposed(next,nextFloor);if(proposed!=null)coefficients(proposed);validateArrays();
        double[] actual=(double[])get(DOUBLES[0]);
        if(!Arrays.equals(actual,installed==null?original:installed))throw new IllegalStateException("系统曲线基准已被其他所有者替换，停止覆盖");
        Map<String,Object> values=new HashMap<>(),copies=new HashMap<>();
        for(String name:arrayNames()){Object a=get(name);values.put(name,a);Object copy=Array.newInstance(a.getClass().getComponentType(),Array.getLength(a));System.arraycopy(a,0,copy,0,Array.getLength(a));copies.put(name,copy);}
        for(String name:SCALARS)values.put(name,get(name));
        CurvePlan old=active;double[] oldInstalled=installed;
        try{
            active=proposed;reset.invoke(target);
            if(proposed==null){System.arraycopy(original,0,get(DOUBLES[0]),0,9);installed=null;}
            else afterReset();
            factors=next==null?null:next.clone();floor=next==null?0:nextFloor;
        }catch(Throwable failure){
            active=old;installed=oldInstalled;
            for(String name:arrayNames()){Object a=values.get(name),copy=copies.get(name);System.arraycopy(copy,0,a,0,Array.getLength(copy));set(name,a);}
            for(String name:SCALARS)set(name,values.get(name));
            throw new IllegalStateException("系数曲线更新失败，已回滚",failure);
        }
    }
    private static String[] arrayNames(){return new String[]{"mDefaultLuxToLogicalNit","mLuxToLogicalNit","mGoodLuxToLogicalNit","mLogicalCurve","mGoodLogicalCurve","mLogicalData"};}
    private double[] coefficients(CurvePlan p){
        float[] x=p.lux(),y=p.nits();for(int i=1;i<y.length;i++)if(y[i]<=y[i-1])throw new IllegalArgumentException("此原生拟合路线的相邻亮度必须递增，避免零斜率拟合");double slope=(y[2]-y[1])/(double)(x[2]-x[1]);
        return new double[]{0,y[0],x[1],y[1],0,x[2],y[1]-slope*x[1],y[2],(y[3]-y[2])/(double)(x[3]-x[2])};
    }
    public void afterReset()throws Exception{
        if(active==null)return;validateArrays();
        double[] base=(double[])get(DOUBLES[0]);if(!Arrays.equals(base,original)&&!Arrays.equals(base,installed))throw new IllegalStateException("系统曲线所有权已改变");
        double[] current=(double[])get("mLuxToLogicalNit");if(!same(current,original)&&!same(current,installed))throw new IllegalStateException("重建后的系数不是已知原生基准，保留系统曲线");
        double[] c=coefficients(active);
        // Verify the real, read-only native consumer before changing any field.
        for(float x:factoryLux)if(!close(at(c,x),active.at(x)))throw new IllegalStateException("系数曲线消费者读回不一致");
        for(String name:DOUBLES)System.arraycopy(c,0,get(name),0,9);
        for(String name:new String[]{"mLogicalCurve","mGoodLogicalCurve"}){float[] a=(float[])get(name);for(int i=0;i<8;i++)a[i]=(float)c[i];a[21]=(float)c[8];}
        installed=c;
    }
    public boolean reconcile()throws Exception{
        if(active==null)return false;validateArrays();double[] base=(double[])get(DOUBLES[0]);
        if(!Arrays.equals(base,installed)&&!Arrays.equals(base,original))throw new IllegalStateException("系统曲线所有权已改变");
        if(!same(installed,original)&&number("mUserLux")<0&&same((double[])get("mLuxToLogicalNit"),original)){afterReset();repairs++;return true;}
        if(!Arrays.equals(base,installed)){System.arraycopy(installed,0,base,0,9);repairs++;return true;}return false;
    }
    private static boolean close(double a,double b){return Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=Math.max(.0002,Math.abs(b)*.000002);}
    private static boolean same(double[] a,double[] b){if(a==null||b==null||a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(!close(a[i],b[i]))return false;return true;}
    public void putVerification(JSONObject status)throws Exception{
        status.put("refactor_curve_verification",new JSONObject().put("profile","coefficients_9_22").put("actual_base_coefficients",new JSONArray((double[])get(DOUBLES[0]))).put("baseline_fields_match",active==null||same((double[])get(DOUBLES[0]),installed)).put("repair_count",repairs).put("manual_anchor_export","last_input_only").put("portable_memory_supported",false));
    }
}
