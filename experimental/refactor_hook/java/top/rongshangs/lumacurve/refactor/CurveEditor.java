package top.rongshangs.lumacurve.refactor;

/** UI limits in the same logical coordinates as CurvePlan. No silent saturation. */
public final class CurveEditor {
    public static float[] bounds(int point,float[] base,float min,float max,float[] factors) {
        if(point<0||point>=3||base.length!=4||factors.length!=4||base[point]<=0)
            throw new IllegalArgumentException("该节点不可编辑");
        float previous=point==0?min:Math.max(min,Math.min(max,base[point-1]*factors[point-1]));
        float next=Math.max(min,Math.min(max,base[point+1]*factors[point+1]));
        float low=Math.max(.1f,previous/base[point]),high=Math.min(2f,Math.min(max,next)/base[point]);
        // Division can round across an adjacent float and break CurvePlan's ordering check.
        if(base[point]*low<previous)low=Math.nextUp(low);
        if(base[point]*high>next)high=Math.nextDown(high);
        if(low>high)throw new IllegalArgumentException("相邻节点限制了可调范围");
        return new float[]{low,high};
    }
    public static float clamp(int point,float requested,float[] base,float min,float max,float[] factors) {
        if(!Float.isFinite(requested))throw new IllegalArgumentException("请输入有效数字");
        float[] range=bounds(point,base,min,max,factors);
        return Math.max(range[0],Math.min(range[1],requested));
    }
    public static float[] preset(float[] wanted,float[] base,float min,float max) {
        if(wanted.length!=4||wanted[3]!=1)throw new IllegalArgumentException("最高照度节点固定为系统上限");
        float[] result={1,1,1,1};
        for(int i=2;i>=0;i--)result[i]=clamp(i,wanted[i],base,min,max,result);
        for(int i=0;i<3;i++)result[i]=clamp(i,wanted[i],base,min,max,result);
        return result;
    }
}
