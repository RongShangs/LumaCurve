package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.Field;

/** Bounds optional OPR relaxation by the panel's configured OPR envelope, not the
 * endpoint of its conversion spline. Read only; native grayscale selection still runs.
 * Missing tables retain the previously verified route; a partial table never does.
 */
final class OutdoorOprEnvelope {
    final Object owner;
    final Field available,levels,nits;
    final boolean present;
    NitBrightnessLimit mapping;
    String profile="unconfirmed",error="";
    float maximumNit=Float.NaN,maximumBrightness=Float.NaN;
    OutdoorOprEnvelope(Object owner)throws Exception {
        this.owner=owner;
        Field a=find(owner.getClass(),"mOprBrightnessControlAvailable"),l=find(owner.getClass(),"mOprGrayscaleThreshold"),n=find(owner.getClass(),"mOprNitThreshold");
        present=a!=null||l!=null||n!=null;
        available=a;levels=l;nits=n;
        if(!present){profile="mapping_only";return;}
        // A present but incomplete/wrong ABI must not masquerade as no envelope.
        if(a==null||l==null||n==null)throw new IllegalStateException("Incomplete OPR envelope");
        OutdoorSdrScope.typed(owner.getClass(),a.getName(),boolean.class);
        OutdoorSdrScope.typed(owner.getClass(),l.getName(),int[].class);
        OutdoorSdrScope.typed(owner.getClass(),n.getName(),int[].class);
    }
    static Field find(Class<?> type,String name)throws Exception {
        try{return HookEntry.field(type,name);}catch(NoSuchFieldException absent){return null;}
    }
    float bound(float proposed,float nativeValue,float minimum,float maximum){
        if(!OutdoorPolicy.valid(proposed)||!OutdoorPolicy.valid(nativeValue)||proposed<=nativeValue)return nativeValue;
        if(!present)return proposed;
        try{
            if(!available.getBoolean(owner))throw new IllegalStateException("OPR envelope inactive");
            int[] x=(int[])levels.get(owner),y=(int[])nits.get(owner);
            if(x==null||y==null||x.length==0||x.length>128||y.length!=x.length+1)throw new IllegalStateException("Invalid OPR envelope shape");
            for(int i=0;i<x.length;i++)if(x[i]<0||x[i]>255||i>0&&x[i]<=x[i-1])throw new IllegalStateException("Invalid OPR grayscale bins");
            for(int i=0;i<y.length;i++)if(y[i]<=0||i>0&&y[i]>y[i-1])throw new IllegalStateException("Invalid OPR nit bins");
            if(mapping==null)mapping=new NitBrightnessLimit(owner);
            float cap=mapping.resolve(y[0],minimum,maximum);
            if(y[0]>mapping.maximumNit)throw new IllegalStateException("OPR envelope exceeds physical mapping");
            maximumNit=y[0];maximumBrightness=cap;profile="native_opr_envelope";error="";
            // Do not lower OEM output. We only bound this module's added relaxation.
            return Math.max(nativeValue,Math.min(proposed,cap));
        }catch(Throwable unknown){profile="native_limit_retained";error=unknown.toString();maximumNit=maximumBrightness=Float.NaN;return nativeValue;}
    }
}
