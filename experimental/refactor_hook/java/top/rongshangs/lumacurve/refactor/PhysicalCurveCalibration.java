package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Read-only proof for OEM configurations extending beyond a physical calibration table. */
final class PhysicalCurveCalibration {
    static final String CLAMPED_ROUTE="aosp_physical_clamped";
    final float min,max,originalMinimum,originalMaximum;
    final int clippedPoints;
    final boolean clamped;

    private PhysicalCurveCalibration(float min,float max,float[] raw,int count){
        this.min=min;this.max=max;originalMinimum=raw[0];originalMaximum=raw[raw.length-1];
        clippedPoints=count;clamped=count!=0;
    }
    static PhysicalCurveCalibration capture(Object mapper,TraditionalCurve original,float min,float max)throws Exception{
        float[] raw=original.nit();int count=0;
        for(float n:raw)if(n<min||n>max)count++;
        PhysicalCurveCalibration result=new PhysicalCurveCalibration(min,max,raw,count);
        // The established in-range paths acquire no additional ABI requirements.
        if(count==0)return result;
        if(!mapper.getClass().getName().equals("com.android.server.display.BrightnessMappingStrategy$PhysicalMappingStrategy"))
            throw new IllegalArgumentException("此物理映射尚未验证超出标定范围的原生曲线");
        Object nitsValue=TraditionalAdapter.read(mapper,"mNits"),brightnessValue=TraditionalAdapter.read(mapper,"mBrightness");
        if(!(nitsValue instanceof float[])||!(brightnessValue instanceof float[]))throw new IllegalArgumentException("物理标定坐标类型不兼容");
        float[] nits=(float[])nitsValue,brightness=(float[])brightnessValue;
        if(nits.length<2||nits.length!=brightness.length||nits[0]!=min||nits[nits.length-1]!=max)
            throw new IllegalArgumentException("物理标定坐标不一致");
        for(int i=0;i<nits.length;i++)if(!Float.isFinite(nits[i])||nits[i]<0||!Float.isFinite(brightness[i])||brightness[i]<0||brightness[i]>1||
                i>0&&(nits[i]<=nits[i-1]||brightness[i]<=brightness[i-1]))throw new IllegalArgumentException("物理标定表不可单调换算");
        if(!Boolean.FALSE.equals(TraditionalAdapter.read(mapper,"mBrightnessRangeAdjustmentApplied")))
            throw new IllegalStateException("物理标定范围正在调整，等待原生标定恢复");
        Object forward=TraditionalAdapter.read(mapper,"mNitsToBrightnessSpline"),reverse=TraditionalAdapter.read(mapper,"mBrightnessToNitsSpline");
        if(TraditionalAdapter.read(mapper,"mAdjustedNitsToBrightnessSpline")!=forward||
                TraditionalAdapter.read(mapper,"mBrightnessToAdjustedNitsSpline")!=reverse)
            throw new IllegalStateException("修正标定与原生标定尚未一致");
        verifySpline(forward,nits,brightness);verifySpline(reverse,brightness,nits);
        Method toBrightness=conversion(mapper,"convertToBrightness"),toNits=conversion(mapper,"convertToNits");
        // Probe pure coordinate conversion only; never change mode, config, memory or a node.
        for(int i=0;i<nits.length;i++){
            requireNear(value(toBrightness,mapper,nits[i]),brightness[i]);
            requireNear(value(toNits,mapper,brightness[i]),nits[i]);
        }
        for(float n:raw)if(n<min||n>max){
            float b=value(toBrightness,mapper,n);
            requireNear(b,n<min?brightness[0]:brightness[brightness.length-1]);
            requireNear(value(toNits,mapper,b),Math.max(min,Math.min(max,n)));
        }
        return result;
    }
    private static Method conversion(Object mapper,String name)throws Exception{
        Method method=TraditionalAdapter.method(mapper.getClass(),name,float.class);
        if(method.getReturnType()!=float.class||Modifier.isStatic(method.getModifiers()))throw new IllegalArgumentException("物理换算方法不兼容");
        return method;
    }
    private static void verifySpline(Object spline,float[] x,float[] y)throws Exception{
        if(spline==null)throw new IllegalArgumentException("物理标定样条尚未就绪");
        String name=spline.getClass().getName();
        if(!name.equals("android.util.Spline$LinearSpline")&&!name.equals("android.util.Spline$MonotoneCubicSpline"))
            throw new IllegalArgumentException("物理标定样条的端点行为尚未验证");
        Object sx=TraditionalAdapter.read(spline,"mX"),sy=TraditionalAdapter.read(spline,"mY");
        if(!(sx instanceof float[])||!(sy instanceof float[])||!Arrays.equals(x,(float[])sx)||!Arrays.equals(y,(float[])sy))
            throw new IllegalArgumentException("物理标定样条与坐标表不一致");
        Method interpolate=TraditionalAdapter.interpolator(spline);
        if(Modifier.isStatic(interpolate.getModifiers()))throw new IllegalArgumentException("物理标定样条方法不兼容");
        for(int i=0;i<x.length;i++)requireNear(value(interpolate,spline,x[i]),y[i]);
    }
    private static float value(Method method,Object owner,float x)throws Exception{return ((Number)method.invoke(owner,x)).floatValue();}
    private static void requireNear(float actual,float expected){
        double tolerance=4d*Math.max(Math.ulp(actual),Math.ulp(expected));
        if(!Float.isFinite(actual)||Math.abs((double)actual-expected)>tolerance)throw new IllegalArgumentException("物理换算未遵循本机标定端点");
    }
    float[] effective(float[] raw){
        float[] values=raw.clone();
        if(clamped)for(int i=0;i<values.length;i++)values[i]=Math.max(min,Math.min(max,values[i]));
        return values;
    }
    boolean keepOriginal(float[] factors,float floor,SavedCurve saved){
        if(!clamped||saved!=null||floor!=0||factors==null||factors.length!=4)return false;
        for(float factor:factors)if(factor!=1)return false;
        return true;
    }
}
