package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;

/** Only replaces base anchors. All OEM conversion, user-drag and output methods run. */
public final class RefactorAdapter {
    private final Object target;
    private final Map<String,Field> fields=new HashMap<>();
    private final Method reset;
    public final float[] factoryLux, factoryNit;
    public final float min, max;
    private volatile CurvePlan active;
    private static final String[] ARRAYS={"mAnchorLux","mAnchorNit","mAnchorOrder","mAnchorIsUserDrag",
        "mGoodAnchorLux","mGoodAnchorNit","mGoodAnchorOrder","mGoodAnchorIsUserDrag"};
    private static final String[] BASE={"mDefNit0","mDefNit30","mDefNitMidHigh"};
    private static final String[] SCALARS={"mAnchorCount","mGoodAnchorCount","mOrderCounter","mUserLux","mUserLogicalNit","mIsHaveGoodCurve","mDefNit0","mDefNit30","mDefNitMidHigh"};
    public RefactorAdapter(Object target, float min, float max) throws Exception {
        this.target=target; this.min=min; this.max=max;
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
    public float currentAt(float lux)throws Exception{
        float[] x=(float[])get("mAnchorLux"),y=(float[])get("mAnchorNit");int count=integer("mAnchorCount");
        if(count<2||count>x.length)throw new IllegalStateException("系统锚点数量异常");
        if(lux<=x[0])return y[0];
        for(int i=1;i<count;i++)if(lux<=x[i])return y[i-1]+(y[i]-y[i-1])*(lux-x[i-1])/(x[i]-x[i-1]);
        return y[count-1];
    }
    // Must run on DisplayPowerControllerImpl's handler. Roll back all changed fields on failure.
    public void configure(float[] factors) throws Exception {
        if(factors!=null && (factors.length!=4 || factors[3]!=1f))throw new IllegalArgumentException("本应用高照度端保持官方上限");
        CurvePlan next=factors==null?null:new CurvePlan(factoryLux,factoryNit,min,max,factors);
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
