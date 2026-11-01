package top.rongshangs.lumacurve.refactor;

/** UI limits in the same logical coordinates as CurvePlan. No silent saturation. */
public final class CurveEditor {
    /** The graph uses zero to device maximum; stored factors remain unchanged. */
    public static float percent(float brightness,float maximum){
        if(!Float.isFinite(maximum)||maximum<=0||!Float.isFinite(brightness)||brightness<0||brightness>maximum)throw new IllegalArgumentException("曲线坐标不兼容");
        return (float)((double)brightness/maximum*100);
    }
    public static float fromPercent(float percent,float maximum){
        if(!Float.isFinite(maximum)||maximum<=0||!Float.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("请输入范围内的数值");
        return (float)(maximum*((double)percent/100));
    }
    public static float[] bounds(int point,float[] base,float min,float max,float[] factors) {
        return bounds(point,base,min,max,factors,0);
    }
    public static float[] bounds(int point,float[] base,float min,float max,float[] factors,float floor) {
        return bounds(point,base,min,max,factors,floor,false);
    }
    public static float[] bounds(int point,float[] base,float min,float max,float[] factors,float floor,boolean strict) {
        if(point<0||point>=4||base.length!=4||factors.length!=4||base[point]<=0)
            throw new IllegalArgumentException("该节点不可编辑");
        float previous=point==0?min:Math.max(min,Math.min(max,base[point-1]*factors[point-1]));
        if(point==1)previous=Math.max(previous,floor);
        float next=point==3?max:Math.max(min,Math.min(max,base[point+1]*factors[point+1]));
        if(strict){if(point>0)previous=Math.nextUp(previous);if(point<3)next=Math.nextDown(next);}
        float low=Math.max(0,previous/base[point]),high=Math.min(max,next)/base[point];
        // Division can round across an adjacent float and break CurvePlan's ordering check.
        if(base[point]*low<previous)low=Math.nextUp(low);
        if(base[point]*high>next)high=Math.nextDown(high);
        if(low>high)throw new IllegalArgumentException("相邻节点限制了可调范围");
        return new float[]{low,high};
    }
    public static float clamp(int point,float requested,float[] base,float min,float max,float[] factors) {
        return clamp(point,requested,base,min,max,factors,0);
    }
    public static float clamp(int point,float requested,float[] base,float min,float max,float[] factors,float floor) {
        if(!Float.isFinite(requested))throw new IllegalArgumentException("请输入有效数字");
        float[] range=bounds(point,base,min,max,factors,floor);
        return Math.max(range[0],Math.min(range[1],requested));
    }
    public static float[] floorBounds(float[] base,float min,float max,float[] factors){
        if(base.length!=4||factors.length!=4)throw new IllegalArgumentException("曲线坐标不兼容");
        float low=min,high=max;
        if(!Float.isFinite(low)||!Float.isFinite(high)||low>high)throw new IllegalArgumentException("相邻节点限制了可调范围");return new float[]{low,high};
    }
    public static float[] preset(float[] wanted,float[] base,float min,float max) {
        return preset(wanted,base,min,max,0);
    }
    public static float[] preset(float[] wanted,float[] base,float min,float max,float floor) {
        if(wanted.length!=4||!Float.isFinite(wanted[3])||wanted[3]<0)throw new IllegalArgumentException("曲线预设节点无效");
        for(float value:wanted)if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("曲线预设节点无效");
        try{new CurvePlan(new float[]{0,1,2,3},base,min,max,wanted,floor);return wanted.clone();}catch(IllegalArgumentException unordered){}
        float[] result={1,1,1,wanted[3]};
        for(int i=2;i>=0;i--)result[i]=i==0&&base[0]==0?1:clamp(i,wanted[i],base,min,max,result);
        for(int i=0;i<3;i++)result[i]=i==0&&base[0]==0?1:clamp(i,wanted[i],base,min,max,result);
        return result;
    }
    public static final class Edit {
        public final float[] factors;public final float floor;
        Edit(float[] factors,float floor){this.factors=factors;this.floor=floor;}
    }
    // Device range only. Strict native coefficient curves need representable,
    // nonzero slopes; reserve a few ULPs for quotient/product round trips.
    public static float[] editBounds(int point,float min,float max,boolean strict){
        if(point<0||point>3||!Float.isFinite(min)||!Float.isFinite(max)||min<0||max<=min)throw new IllegalArgumentException("曲线坐标不兼容");
        float low=min,high=max;
        if(strict){for(int i=0;i<point*4;i++)low=Math.nextUp(low);for(int i=0;i<(3-point)*4;i++)high=Math.nextDown(high);}
        if(low>high)throw new IllegalArgumentException("本机曲线范围不足以编辑");return new float[]{low,high};
    }
    public static Edit edit(int point,float requested,float[] base,float min,float max,float[] factors,float floor,boolean strict){
        if(!Float.isFinite(requested))throw new IllegalArgumentException("请输入有效数字");
        float[] desired=new CurvePlan(new float[]{0,1,2,3},base,min,max,factors,floor).nits();
        float[] range=editBounds(point,min,max,strict);desired[point]=Math.max(range[0],Math.min(range[1],requested));
        for(int i=point-1;i>=0;i--){float upper=desired[i+1];if(strict)for(int step=0;step<4;step++)upper=Math.nextDown(upper);desired[i]=Math.min(desired[i],upper);}
        for(int i=point+1;i<4;i++){float lower=desired[i-1];if(strict)for(int step=0;step<4;step++)lower=Math.nextUp(lower);desired[i]=Math.max(desired[i],lower);}
        float[] next=factors.clone();next[0]=0;float nextFloor=desired[0],previous=nextFloor;
        for(int i=1;i<4;i++){
            if(base[i]==0){if(strict||desired[i]!=nextFloor)throw new IllegalArgumentException("本机零值参考点暂不能表达此曲线");next[i]=0;continue;}
            float target=desired[i];next[i]=target/base[i];float actual=Math.max(nextFloor,Math.max(min,Math.min(max,base[i]*next[i])));
            // Compensate only float rounding, never jump to an arbitrary brightness.
            for(int step=0;step<4&&(strict?actual<=previous:actual<previous);step++){next[i]=Math.nextUp(next[i]);actual=Math.max(nextFloor,Math.max(min,Math.min(max,base[i]*next[i])));}
            previous=actual;
        }
        float[] output=new CurvePlan(new float[]{0,1,2,3},base,min,max,next,nextFloor).nits();
        if(strict)for(int i=1;i<4;i++)if(output[i]<=output[i-1])throw new IllegalArgumentException("本机曲线接口要求相邻亮度递增");
        return new Edit(next,nextFloor);
    }
}
