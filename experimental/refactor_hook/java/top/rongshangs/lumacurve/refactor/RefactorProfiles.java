package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;

/** Mutually exclusive Refactor ABIs, selected by members, never device or OS names. */
final class RefactorProfiles {
    static boolean anchors(Class<?> type){
        for(String name:new String[]{"mLuxSeg0","mLuxSeg1","mLuxMax","mAnchorLux","mAnchorNit","mDefNit0"})
            try{type.getDeclaredField(name);return true;}catch(NoSuchFieldException absent){}
        try{type.getDeclaredMethod("defaultAtLux",float.class);return true;}catch(NoSuchMethodException absent){}
        return false;
    }
    static CurveBackend create(Object target,float min,float max)throws Exception{
        if(anchors(target.getClass()))return new RefactorAdapter(target,min,max);
        return new RefactorCoefficientsAdapter(target,min,max);
    }
    static Method defaultMethod(Class<?> type)throws Exception{
        if(!anchors(type))RefactorCoefficientsAdapter.validateClass(type);
        Method m=type.getDeclaredMethod(anchors(type)?"defaultAtLux":"getDefaultLogicalBrt",float.class);
        if(m.getReturnType()!=(anchors(type)?float.class:double.class)||Modifier.isStatic(m.getModifiers()))throw new IllegalArgumentException("Refactor 默认曲线签名不兼容");
        return m;
    }
}
