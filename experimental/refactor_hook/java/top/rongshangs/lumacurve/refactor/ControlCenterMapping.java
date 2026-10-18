package top.rongshangs.lumacurve.refactor;

/** Physical slider position, not Android's perceptual/gamma brightness value. */
final class ControlCenterMapping {
    static int progress(double fraction,int minimum,int maximum){
        if(!Double.isFinite(fraction)||fraction<0||fraction>1||maximum<=minimum||minimum<0||maximum>1000000)throw new IllegalArgumentException("控制中心坐标无效");
        return minimum+(int)Math.round(fraction*(maximum-minimum));
    }
    static int target(double fraction,int maximum){
        if(!Double.isFinite(fraction)||fraction<0||fraction>1||maximum<=10||maximum>65535)throw new IllegalArgumentException("节点映射范围无效");
        return Math.max(10,Math.min(maximum,(int)Math.round(fraction*maximum)));
    }
    static double fraction(int progress,int minimum,int maximum){
        if(maximum<=minimum||progress<minimum||progress>maximum)throw new IllegalArgumentException("控制中心滑块范围无效");
        return (progress-minimum)/(double)(maximum-minimum);
    }
}
