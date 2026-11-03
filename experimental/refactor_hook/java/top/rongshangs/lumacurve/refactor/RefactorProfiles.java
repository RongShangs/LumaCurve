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
        if(resetDerived(target.getClass()))return new RefactorResetAdapter(target,min,max);
        return new RefactorCoefficientsAdapter(target,min,max);
    }
    static boolean resetDerived(Class<?> type){
        try{type.getDeclaredField("mDefaultLuxToLogicalNit");return false;}catch(NoSuchFieldException absent){}
        for(Method m:type.getDeclaredMethods())if(m.getName().equals("getDefaultLogicalBrt")||m.getName().equals("calculateBrt"))return false;
        return true;
    }
    static Method defaultMethod(Class<?> type)throws Exception{
        if(!anchors(type)&&resetDerived(type)){RefactorResetAdapter.validateClass(type);return null;}
        if(!anchors(type))RefactorCoefficientsAdapter.validateClass(type);
        Method m=type.getDeclaredMethod(anchors(type)?"defaultAtLux":"getDefaultLogicalBrt",float.class);
        if(m.getReturnType()!=(anchors(type)?float.class:double.class)||Modifier.isStatic(m.getModifiers()))throw new IllegalArgumentException("Refactor 默认曲线签名不兼容");
        return m;
    }
    static Method currentMethod(Class<?> type)throws Exception{return !anchors(type)&&resetDerived(type)?RefactorResetAdapter.method(type,"getLogicalBrt",double.class,float.class):null;}
}
