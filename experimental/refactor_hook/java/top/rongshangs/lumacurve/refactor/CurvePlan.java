package top.rongshangs.lumacurve.refactor;

import java.util.Arrays;

/** OEM logical-nit coordinates, not physical nits or raw panel codes. */
public final class CurvePlan {
    private final float[] lux, nit;
    public CurvePlan(float[] factoryLux, float[] factoryNit, float min, float max, float[] factors) {
        if (factoryLux.length != 4 || factoryNit.length != 4 || factors.length != 4 ||
                !Float.isFinite(min) || !Float.isFinite(max) || min < 0 || max <= min)
            throw new IllegalArgumentException("曲线坐标不兼容");
        lux=factoryLux.clone(); nit=new float[4];
        for(int i=0;i<4;i++) {
            if(!Float.isFinite(lux[i]) || !Float.isFinite(factoryNit[i]) ||
                    !Float.isFinite(factors[i]) || factors[i]<.1f || factors[i]>2f ||
                    lux[i]<0 || (i==0 && lux[i]!=0) || (i>0 && lux[i]<=lux[i-1]) ||
                    factoryNit[i]<min || factoryNit[i]>max || (i>0 && factoryNit[i]<factoryNit[i-1]))
                throw new IllegalArgumentException("曲线节点无效");
            nit[i]=Math.max(min, Math.min(max, factoryNit[i]*factors[i]));
            if(i>0 && nit[i]<nit[i-1]) throw new IllegalArgumentException("节点必须随照度不递减，请降低前一个节点或提高后一个节点");
        }
    }
    public float at(float value) {
        if(!Float.isFinite(value) || value<0) throw new IllegalArgumentException("照度无效");
        if(value<=lux[0]) return nit[0];
        for(int i=1;i<4;i++) if(value<=lux[i]) return nit[i-1]+(nit[i]-nit[i-1])*(value-lux[i-1])/(lux[i]-lux[i-1]);
        return nit[3];
    }
    public float[] lux(){return lux.clone();}
    public float[] nits(){return nit.clone();}
    public static float[] factors(String value) {
        if(value==null || value.length()>100) throw new IllegalArgumentException("曲线参数无效");
        String[] words=value.split(",",-1); if(words.length!=4) throw new IllegalArgumentException("需要四个曲线参数");
        float[] values=new float[4];
        for(int i=0;i<4;i++) {values[i]=Float.parseFloat(words[i]); if(!Float.isFinite(values[i]) || values[i]<.1 || values[i]>2) throw new IllegalArgumentException("节点强度范围是 10%～200%");}
        return values;
    }
    public static String encode(float[] values) {
        if(values.length!=4) throw new IllegalArgumentException();
        StringBuilder out=new StringBuilder(); for(float value:values){if(out.length()>0)out.append(',');out.append(value);}return out.toString();
    }
}
