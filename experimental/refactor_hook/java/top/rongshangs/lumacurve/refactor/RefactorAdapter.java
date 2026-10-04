package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;

/** Only replaces base anchors. All OEM conversion, user-drag and output methods run. */
public final class RefactorAdapter extends CurveBackend {
    private final Object target;
    private final Map<String,Field> fields=new HashMap<>();
    private final Method reset;
    private volatile CurvePlan active;
    private float[] activeFactors;
    private float activeFloor;
    private static final String[] ARRAYS={"mAnchorLux","mAnchorNit","mAnchorOrder","mAnchorIsUserDrag",
        "mGoodAnchorLux","mGoodAnchorNit","mGoodAnchorOrder","mGoodAnchorIsUserDrag"};
    private static final String[] BASE={"mDefNit0","mDefNit30","mDefNitMidHigh"};
    private static final String[] SCALARS={"mAnchorCount","mGoodAnchorCount","mOrderCounter","mUserLux","mUserLogicalNit","mIsHaveGoodCurve","mDefNit0","mDefNit30","mDefNitMidHigh"};
    public RefactorAdapter(Object target, float min, float max) throws Exception {
        super(min,max);this.target=target;
        if(integer("mDisplayId")!=0) throw new IllegalArgumentException("仅接入内置主屏");
        for(String name:new String[]{"mLuxSeg0","mLuxSeg1","mLuxMax","mDefNit0","mDefNit30","mDefNitMidHigh","mUserLux","mUserLogicalNit"})
            if(field(name).getType()!=float.class)throw new IllegalArgumentException("曲线字段类型已变化："+name);
        for(String name:new String[]{"mDisplayId","mAnchorCount","mGoodAnchorCount","mOrderCounter"})
            if(field(name).getType()!=int.class)throw new IllegalArgumentException("锚点字段类型已变化："+name);
        if(field("mIsHaveGoodCurve").getType()!=boolean.class)throw new IllegalArgumentException("曲线状态类型已变化");
        factoryLux=new float[]{0,number("mLuxSeg0"),number("mLuxSeg1"),number("mLuxMax")};
        factoryNit=new float[]{number("mDefNit0"),number("mDefNit30"),number("mDefNitMidHigh"),max};
        new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});
        for(String name:ARRAYS) {
            Object array=get(name);
            Class<?> required=name.endsWith("Order")?int[].class:name.endsWith("Drag")?boolean[].class:float[].class;
            if(array==null || array.getClass()!=required || Array.getLength(array)!=7)
                throw new IllegalArgumentException("锚点容器不兼容："+name);
        }
        for(String name:SCALARS) field(name);
        reset=target.getClass().getDeclaredMethod("resetDefaultSpline");
        if(target.getClass().getDeclaredMethod("defaultAtLux",float.class).getReturnType()!=float.class)throw new IllegalArgumentException("默认曲线接口不兼容");
        if(reset.getReturnType()!=void.class) throw new IllegalArgumentException("重置接口不兼容");
        reset.setAccessible(true);
    }
    private Field field(String name) throws Exception {
        Field f=fields.get(name); if(f==null){f=target.getClass().getDeclaredField(name);f.setAccessible(true);fields.put(name,f);}return f;
    }
    public Object get(String name) throws Exception{return field(name).get(target);}
    private void set(String name,Object value)throws Exception{field(name).set(target,value);}
    public float number(String name)throws Exception{return ((Number)get(name)).floatValue();}
    public int integer(String name)throws Exception{return ((Number)get(name)).intValue();}
    public CurvePlan plan(){return active;}
    Object target(){return target;}
    public String name(){return "refactor";}
    public boolean persistentMemorySupported(){return true;}
    public int manualPointCapacity(){try{return Math.min(PersistentMemory.MAX_POINTS,java.lang.reflect.Array.getLength(get("mAnchorLux")));}catch(Exception unavailable){return 0;}}
    public org.json.JSONArray manualPoints()throws Exception {
        int count=integer("mAnchorCount");float[] x=(float[])get("mAnchorLux"),y=(float[])get("mAnchorNit");boolean[] flags=(boolean[])get("mAnchorIsUserDrag");int[] order=(int[])get("mAnchorOrder");
        if(count<0||count>x.length)throw new IllegalStateException("系统锚点数量异常");
        List<Integer> indices=new ArrayList<>();for(int i=0;i<count;i++)if(flags[i])indices.add(i);
        Collections.sort(indices,(a,b)->Integer.compare(order[a],order[b]));org.json.JSONArray points=new org.json.JSONArray();
        for(int i:indices)points.put(new org.json.JSONObject().put("lux",x[i]).put("value",y[i]));return points;
    }
    public void restoreManualPoints(org.json.JSONArray points)throws Exception {restore(points,false);}
    public void replaceManualPoints(org.json.JSONArray points)throws Exception {restore(points,true);}
    private void restore(org.json.JSONArray points,boolean replace)throws Exception {
        PersistentMemory.validate(points);
        for(int i=0;i<points.length();i++){org.json.JSONObject p=points.getJSONObject(i);float x=(float)p.getDouble("lux"),y=(float)p.getDouble("value");if(x>factoryLux[3]||y<min||y>max)throw new IllegalArgumentException("手动记忆超出设备曲线范围");}
        Map<String,Object> before=new HashMap<>();
        for(String name:ARRAYS){Object array=get(name),copy=Array.newInstance(array.getClass().getComponentType(),Array.getLength(array));System.arraycopy(array,0,copy,0,Array.getLength(array));before.put(name,copy);}
        for(String name:SCALARS)before.put(name,get(name));
        try{if(replace){reset.invoke(target);afterReset();}Method method=target.getClass().getDeclaredMethod("updateLogicalCurve",float.class,float.class);method.setAccessible(true);
            for(int i=0;i<points.length();i++){org.json.JSONObject p=points.getJSONObject(i);method.invoke(target,(float)p.getDouble("lux"),(float)p.getDouble("value"));}
        }catch(Throwable error){for(String name:ARRAYS){Object copy=before.get(name);System.arraycopy(copy,0,get(name),0,Array.getLength(copy));}for(String name:SCALARS)set(name,before.get(name));throw new IllegalStateException("手动记忆恢复失败，已回滚",error);}
    }
    public void clearMemory()throws Exception{configure(activeFactors,activeFloor);}
    public float currentAt(float lux)throws Exception{
        float[] x=(float[])get("mAnchorLux"),y=(float[])get("mAnchorNit");int count=integer("mAnchorCount");
        if(count<2||count>x.length)throw new IllegalStateException("系统锚点数量异常");
        if(lux<=x[0])return y[0];
        for(int i=1;i<count;i++)if(lux<=x[i])return y[i-1]+(y[i]-y[i-1])*(lux-x[i-1])/(x[i]-x[i-1]);
        return y[count-1];
    }
    // Must run on DisplayPowerControllerImpl's handler. Roll back all changed fields on failure.
    public void configure(float[] factors) throws Exception {
        configure(factors,0);
    }
    public void configure(float[] factors,float floor) throws Exception {
        if(factors!=null && (factors.length!=4 || factors[3]!=1f))throw new IllegalArgumentException("本应用高照度端保持官方上限");
        CurvePlan next=factors==null?null:new CurvePlan(factoryLux,factoryNit,min,max,factors,floor);
        Map<String,Object> before=new HashMap<>();
        for(String name:ARRAYS){Object a=get(name);Object copy=Array.newInstance(a.getClass().getComponentType(),Array.getLength(a));System.arraycopy(a,0,copy,0,Array.getLength(a));before.put(name,copy);}
        for(String name:SCALARS)before.put(name,get(name));
        CurvePlan previous=active;
        try {
            active=next;
            float[] desired=next==null?factoryNit:next.nits();
            // Change the real baseline fields read by defaultAtLux and resetDefaultSpline.
            // This works even if private interpolation helpers were inlined by ART.
            for(int i=0;i<3;i++)set(BASE[i],desired[i]);
            reset.invoke(target);afterReset();
            activeFactors=factors==null?null:factors.clone();
            activeFloor=factors==null?0:floor;
        }
        catch(Throwable failure) {
            active=previous;
            for(String name:ARRAYS){Object copy=before.get(name);System.arraycopy(copy,0,get(name),0,Array.getLength(copy));}
            for(String name:SCALARS)set(name,before.get(name));
            throw new IllegalStateException("曲线更新失败，已回滚内存锚点",failure);
        }
    }
    public void afterReset() throws Exception {
        CurvePlan plan=active;if(plan==null)return;
        // resetDefaultSpline already cleared the OEM short-term model and drag flags.
        if(integer("mAnchorCount")!=4 || integer("mGoodAnchorCount")!=4)
            throw new IllegalStateException("系统默认锚点数量已变化");
        float[] x=plan.lux(),y=plan.nits();
        for(String prefix:new String[]{"mAnchor","mGoodAnchor"}) {
            System.arraycopy(x,0,get(prefix+"Lux"),0,4);
            System.arraycopy(y,0,get(prefix+"Nit"),0,4);
        }
    }
}
