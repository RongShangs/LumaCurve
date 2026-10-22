package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;

/** Only replaces base anchors. All OEM conversion, user-drag and output methods run. */
public final class RefactorAdapter extends CurveBackend {
    private final Object target;
    private final Map<String,Field> fields=new HashMap<>();
    private final Method reset;
    private final CurvePlan factoryPlan;
    private volatile CurvePlan active;
    private float[] activeFactors;
    private float activeFloor;
    private long repairs;
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
        factoryPlan=new CurvePlan(factoryLux,factoryNit,min,max,new float[]{1,1,1,1});
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
    public Object target(){return target;}
    public String name(){return "refactor";}
    public void putCurveState(org.json.JSONObject status)throws Exception{
        int count=integer("mAnchorCount");boolean[] user=(boolean[])get("mAnchorIsUserDrag");if(count<0||count>user.length)throw new IllegalStateException("系统锚点数量异常");
        org.json.JSONArray flags=new org.json.JSONArray();for(int i=0;i<count;i++)flags.put(user[i]);status.put("manual_anchor_flags",flags);
    }
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
            for(int i=0;i<points.length();i++){org.json.JSONObject p=points.getJSONObject(i);method.invoke(target,(float)p.getDouble("lux"),(float)p.getDouble("value"));reconcile();}
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
        CurvePlan next=factors==null?null:proposed(factors,floor);
        Map<String,Object> before=new HashMap<>();
        for(String name:ARRAYS){Object a=get(name);Object copy=Array.newInstance(a.getClass().getComponentType(),Array.getLength(a));System.arraycopy(a,0,copy,0,Array.getLength(a));before.put(name,copy);}
        for(String name:SCALARS)before.put(name,get(name));
        CurvePlan previous=active;
        try {
            active=next;
            float[] desired=next==null?factoryNit:new float[]{next.at(factoryLux[0]),next.at(factoryLux[1]),next.at(factoryLux[2]),next.at(factoryLux[3])};
            // Change the real baseline fields read by defaultAtLux and resetDefaultSpline.
            // This works even if private interpolation helpers were inlined by ART.
            for(int i=0;i<3;i++)set(BASE[i],desired[i]);
            reset.invoke(target);afterReset();
            if(next!=null&&!baselineMatches(next))throw new IllegalStateException("基础曲线字段读回不一致");
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
        int expected=plan.lux().length;
        if((integer("mAnchorCount")!=4&&integer("mAnchorCount")!=expected)||(integer("mGoodAnchorCount")!=4&&integer("mGoodAnchorCount")!=expected))
            throw new IllegalStateException("系统默认锚点数量已变化");
        float[] x=plan.lux(),y=plan.nits();if(x.length>7)throw new IllegalStateException("本机基础曲线容器不足");
        for(String prefix:new String[]{"mAnchor","mGoodAnchor"}) {
            System.arraycopy(x,0,get(prefix+"Lux"),0,x.length);
            System.arraycopy(y,0,get(prefix+"Nit"),0,y.length);
        }
        set("mAnchorCount",x.length);set("mGoodAnchorCount",x.length);
        writeBaseline(plan);
    }
    private void writeBaseline(CurvePlan p)throws Exception{for(int i=0;i<BASE.length;i++)set(BASE[i],p.at(factoryLux[i]));}
    private boolean baselineMatches(CurvePlan p)throws Exception{for(int i=0;i<BASE.length;i++)if(!close(number(BASE[i]),p.at(factoryLux[i])))return false;return true;}
    private static boolean close(float a,float b){return Float.isFinite(a)&&Float.isFinite(b)&&Math.abs(a-b)<=Math.max(.0001f,Math.abs(b)*.000001f);}
    // Same OEM segment-local preference rule, using the selected base instead of a
    // possibly cached factory default. OEM still owns insertion, eviction and order.
    private float blended(CurvePlan p,float lux,float dragLux,float dragNit){
        float base=p.at(lux),start=dragLux<factoryLux[1]?factoryLux[0]:dragLux<factoryLux[2]?factoryLux[1]:factoryLux[2];
        float end=dragLux<factoryLux[1]?factoryLux[1]:dragLux<factoryLux[2]?factoryLux[2]:factoryLux[3];
        if(Math.abs(dragLux-start)<.5f||Math.abs(dragLux-end)<.5f||Math.abs(lux-start)>=.5f&&Math.abs(lux-end)>=.5f)return base;
        float weight=((end-start)-Math.abs(lux-dragLux))/(end-start);
        return Math.max(min,Math.min(max,base+(dragNit-p.at(dragLux))*weight));
    }
    private float[] corrected(String prefix,CurvePlan p)throws Exception{
        int count=integer(prefix+"Count");float[] x=(float[])get(prefix+"Lux"),y=(float[])get(prefix+"Nit");boolean[] flags=(boolean[])get(prefix+"IsUserDrag");int[] order=(int[])get(prefix+"Order");
        if(count<2||count>x.length||count>y.length||count>flags.length||count>order.length)throw new IllegalStateException("系统锚点数量异常");
        float[] next=y.clone();int newest=-1;
        for(int i=0;i<count;i++){
            if(!Float.isFinite(x[i])||x[i]<0||x[i]>factoryLux[3]||i>0&&x[i]<=x[i-1]||!Float.isFinite(y[i])||y[i]<min||y[i]>max)throw new IllegalStateException("系统锚点坐标异常");
            if(flags[i]&&(newest<0||order[i]>order[newest]))newest=i;
        }
        if(newest<0){for(int i=0;i<count;i++)next[i]=p.at(x[i]);return next;}
        float dragLux=x[newest],dragNit=y[newest];
        for(int i=newest-1;i>=0;i--)next[i]=Math.min(flags[i]?next[i]:blended(p,x[i],dragLux,dragNit),next[i+1]);
        for(int i=newest+1;i<count;i++)next[i]=Math.max(flags[i]?next[i]:blended(p,x[i],dragLux,dragNit),next[i-1]);
        return next;
    }
    /** Event-only repair after reset, drag or OEM good-curve handover; never on a sensor timer. */
    public boolean reconcile()throws Exception{
        CurvePlan p=active;if(p==null)return false;
        if(p.lux().length!=4&&nativeReset("mAnchor",p)&&nativeReset("mGoodAnchor",p)){
            // A saved curve can have extra knots. Do not merely sample it back to
            // the four OEM defaults when an inlined reset erased those knots.
            Map<String,Object> before=new HashMap<>();for(String name:ARRAYS){Object a=get(name),copy=Array.newInstance(a.getClass().getComponentType(),Array.getLength(a));System.arraycopy(a,0,copy,0,Array.getLength(a));before.put(name,copy);}for(String name:SCALARS)before.put(name,get(name));
            try{afterReset();repairs++;return true;}
            catch(Exception failure){for(String name:ARRAYS){Object a=before.get(name);System.arraycopy(a,0,get(name),0,Array.getLength(a));}for(String name:SCALARS)set(name,before.get(name));throw failure;}
        }
        float[] current=(float[])get("mAnchorNit"),good=(float[])get("mGoodAnchorNit"),next=corrected("mAnchor",p),nextGood=corrected("mGoodAnchor",p);
        // Only recognize a proven return to factory/sampled baseline semantics.
        // An unknown OEM learning algorithm must not be overwritten by our model.
        CurvePlan sampled=null;try{sampled=new CurvePlan(factoryLux,new float[]{number(BASE[0]),number(BASE[1]),number(BASE[2]),max},min,max,new float[]{1,1,1,1});}catch(IllegalArgumentException unknown){}
        if(!sameCurve(current,next)&&!sameCurve(current,corrected("mAnchor",factoryPlan))&&(sampled==null||!sameCurve(current,corrected("mAnchor",sampled))))next=current;
        if(!sameCurve(good,nextGood)&&!sameCurve(good,corrected("mGoodAnchor",factoryPlan))&&(sampled==null||!sameCurve(good,corrected("mGoodAnchor",sampled))))nextGood=good;
        boolean changed=!baselineMatches(p);
        for(int i=0;i<current.length;i++)changed|=!close(current[i],next[i]);
        for(int i=0;i<good.length;i++)changed|=!close(good[i],nextGood[i]);
        if(!changed)return false;
        float[] before=current.clone(),beforeGood=good.clone(),base=new float[BASE.length];for(int i=0;i<base.length;i++)base[i]=number(BASE[i]);
        try{writeBaseline(p);System.arraycopy(next,0,current,0,current.length);System.arraycopy(nextGood,0,good,0,good.length);if(!baselineMatches(p))throw new IllegalStateException("基础字段读回不一致");repairs++;return true;}
        catch(Exception failure){System.arraycopy(before,0,current,0,current.length);System.arraycopy(beforeGood,0,good,0,good.length);for(int i=0;i<base.length;i++)set(BASE[i],base[i]);throw failure;}
    }
    private boolean nativeReset(String prefix,CurvePlan p)throws Exception{
        if(integer(prefix+"Count")!=4)return false;
        float[] x=(float[])get(prefix+"Lux"),y=(float[])get(prefix+"Nit");boolean[] flags=(boolean[])get(prefix+"IsUserDrag");boolean factory=true,sampled=true;
        for(int i=0;i<4;i++){if(flags[i]||!close(x[i],factoryLux[i]))return false;factory&=close(y[i],factoryNit[i]);sampled&=close(y[i],i<3?number(BASE[i]):max);}
        return factory||sampled;
    }
    private static boolean sameCurve(float[] a,float[] b){if(a.length!=b.length)return false;for(int i=0;i<a.length;i++)if(!close(a[i],b[i]))return false;return true;}
    public void putVerification(org.json.JSONObject status)throws Exception{
        float[] actual=new float[BASE.length];for(int i=0;i<actual.length;i++)actual[i]=number(BASE[i]);
        org.json.JSONObject check=new org.json.JSONObject().put("actual_base_nit",new org.json.JSONArray(actual)).put("repair_count",repairs).put("scope","reset_drag_handover");
        if(active!=null){check.put("baseline_fields_match",baselineMatches(active));float[] expected=corrected("mAnchor",active),actualCurve=(float[])get("mAnchorNit");boolean matches=true;for(int i=0;i<integer("mAnchorCount");i++)matches&=close(actualCurve[i],expected[i]);check.put("current_basis_verified",matches);}
        status.put("refactor_curve_verification",check);
    }
}
