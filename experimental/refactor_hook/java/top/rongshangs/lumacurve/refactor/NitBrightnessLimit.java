package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.Method;

/** Inverts the primary display's physical-nit conversion, never the editable lux curve.
 * Cache the inversion; ordinary output calls verify its endpoints and capped value.
 */
final class NitBrightnessLimit {
    final Object owner;
    final Method converter;
    double requested=Double.NaN;
    float minimum=Float.NaN,maximum=Float.NaN,minimumNit=Float.NaN,maximumNit=Float.NaN,brightness=Float.NaN;
    NitBrightnessLimit(Object owner)throws Exception{
        this.owner=owner;converter=owner.getClass().getMethod("convertBrightnessToNit",float.class);
        if(converter.getReturnType()!=float.class)throw new IllegalStateException("Physical nit conversion unavailable");
    }
    float read(float value)throws Exception{
        float nit=((Number)converter.invoke(owner,value)).floatValue();
        if(!Float.isFinite(nit)||nit<0)throw new IllegalStateException("Invalid physical nit mapping");
        return nit;
    }
    float resolve(double nit,float min,float max)throws Exception{
        if(!Double.isFinite(nit)||nit<0||!OutdoorPolicy.valid(min)||!OutdoorPolicy.valid(max)||max<=min)throw new IllegalArgumentException("Invalid outdoor nit limit");
        float lowNit=read(min),highNit=read(max);
        if(highNit<=lowNit)throw new IllegalStateException("Physical nit mapping has no range");
        if(Double.compare(requested,nit)==0&&Float.compare(minimum,min)==0&&Float.compare(maximum,max)==0&&Float.compare(minimumNit,lowNit)==0&&Float.compare(maximumNit,highNit)==0&&OutdoorPolicy.valid(brightness)&&(nit==0||read(brightness)<=nit))return brightness;
        // Reject unknown/non-monotonic mappings before enabling a user-visible nit limit.
        float previous=lowNit;
        for(int i=1;i<=32;i++){float next=read(min+(max-min)*i/32f);if(next<previous)throw new IllegalStateException("Physical nit mapping is not monotonic");previous=next;}
        if(nit>0&&nit<lowNit)throw new IllegalArgumentException("户外亮度上限低于本机最小亮度");
        float result=max;
        if(nit>0&&nit<highNit){
            float low=min,high=max;
            for(int i=0;i<26;i++){float middle=low+(high-low)/2;if(middle==low||middle==high)break;if(read(middle)<=nit)low=middle;else high=middle;}
            result=low;
            if(result<=0)throw new IllegalArgumentException("户外亮度上限低于本机最小亮度");
            if(read(result)>nit)throw new IllegalStateException("Physical nit limit verification failed");
        }
        requested=nit;minimum=min;maximum=max;minimumNit=lowNit;maximumNit=highNit;brightness=result;
        return result;
    }
}
