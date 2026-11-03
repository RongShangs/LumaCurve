package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;

/** Normal mapper ownership only. No mode changes, curve writes or version guesses. */
final class MapperContextAccess {
    final Object owner,mapper,abc;
    final Method mode,get;
    final Field normal,current,idle,ownerMapper;
    final boolean legacy;
    final String route;

    MapperContextAccess(Object owner,Object mapper,Object abc)throws Exception {
        this.owner=owner;this.mapper=mapper;this.abc=abc;
        if(((Number)TraditionalAdapter.read(owner,"mDisplayId")).intValue()!=0)
            throw new IllegalArgumentException("仅接入主屏普通模式");
        Method modernMode=null;Field modernMap=null;
        try{modernMode=TraditionalAdapter.method(mapper.getClass(),"getMode");}catch(NoSuchMethodException absent){}
        try{modernMap=TraditionalAdapter.find(abc.getClass(),"mBrightnessMappingStrategyMap");}catch(NoSuchFieldException absent){}
        current=instanceField(abc,"mCurrentBrightnessMapper");
        // One modern marker commits to that ABI; a broken half must never select the old ABI.
        legacy=modernMode==null&&modernMap==null;
        if(!legacy){
            if(modernMode==null||modernMap==null)throw new IllegalArgumentException("普通映射模式接口不完整");
            mode=instanceMethod(modernMode,int.class);normal=modernMap;
            if(Modifier.isStatic(normal.getModifiers()))throw new IllegalArgumentException("映射表不可为静态字段");
            Object map=normal.get(abc);if(map==null)throw new IllegalStateException("普通映射表尚未就绪");
            get=TraditionalAdapter.method(map.getClass(),"get",int.class);
            if(Modifier.isStatic(get.getModifiers())||get.getReturnType().isPrimitive())throw new IllegalArgumentException("映射表读取接口不兼容");
            idle=ownerMapper=null;route="mode_map";
        }else{
            mode=instanceMethod(TraditionalAdapter.method(mapper.getClass(),"isForIdleMode"),boolean.class);
            normal=instanceField(abc,"mInteractiveModeBrightnessMapper");
            idle=instanceField(abc,"mIdleModeBrightnessMapper");
            ownerMapper=instanceField(owner,"mBrightnessMapper");get=null;route="interactive_idle";
            Object sleep=idle.get(abc),live=current.get(abc);
            if(sleep==mapper||live!=mapper&&(sleep==null||live!=sleep))throw new IllegalStateException("普通与休眠映射归属不一致");
            if(sleep!=null){Method sleepMode=instanceMethod(TraditionalAdapter.method(sleep.getClass(),"isForIdleMode"),boolean.class);
                if(!Boolean.TRUE.equals(sleepMode.invoke(sleep)))throw new IllegalArgumentException("休眠映射标记不一致");}
        }
        if(!normalOwned())throw new IllegalStateException("主屏默认映射对象不一致");
    }
    private static Field instanceField(Object owner,String name)throws Exception {
        Field f=TraditionalAdapter.find(owner.getClass(),name);
        if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())throw new IllegalArgumentException("映射归属字段不兼容："+name);
        return f;
    }
    private static Method instanceMethod(Method m,Class<?> result)throws Exception {
        if(Modifier.isStatic(m.getModifiers())||m.getReturnType()!=result)throw new IllegalArgumentException("映射模式方法不兼容");
        return m;
    }
    private boolean normalOwned()throws Exception {
        Object value=mode.invoke(mapper);
        if(legacy)return Boolean.FALSE.equals(value)&&normal.get(abc)==mapper&&ownerMapper.get(owner)==mapper;
        Object map=normal.get(abc);
        return Integer.valueOf(0).equals(value)&&map!=null&&get.getDeclaringClass().isInstance(map)&&get.invoke(map,0)==mapper;
    }
    boolean active()throws Exception {return current.get(abc)==mapper&&normalOwned();}
    void requireNormal()throws Exception {if(!normalOwned())throw new IllegalStateException("普通映射对象已改变");}
    String description(){return legacy?"普通与休眠双映射对象校验":"模式编号与映射表校验";}
}
