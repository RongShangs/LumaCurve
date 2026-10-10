package top.rongshangs.lumacurve.refactor;

/** Shared geometry for status measurement and placement. Short viewports scroll. */
final class StatusPageLayout {
    final int height,gap,thresholdGap,infoHeight;final int[] top=new int[5],bottom=new int[5];
    private static int dp(float value,float density){return Math.round(value*density);}
    private static float scale(float fontScale){return Float.isFinite(fontScale)?Math.max(1,Math.min(2,fontScale)):1;}
    static int minimumHeight(float density,float fontScale){
        float scale=scale(fontScale);
        return 2*dp(80+56*(scale-1),density)+dp(88+56*(scale-1),density)+dp(120+24*(scale-1),density)+dp(32+24*(scale-1),density)+dp(108+64*(scale-1),density)+3*dp(16+6*(scale-1),density)+dp(32+12*(scale-1),density);
    }
    StatusPageLayout(int availableHeight,float density,float fontScale){
        float scale=scale(fontScale);int minimum=minimumHeight(density,scale);height=Math.max(minimum,availableHeight);
        gap=dp(16+6*(scale-1),density);thresholdGap=dp(32+12*(scale-1),density);infoHeight=dp(32+24*(scale-1),density);
        int row=dp(80+56*(scale-1),density);
        int advice=dp(108+64*(scale-1),density);
        int fused=dp(88+56*(scale-1),density);int curve=height-2*row-fused-advice-3*gap-thresholdGap;int[] sizes={row,fused,curve,advice,row};
        for(int i=0;i<sizes.length;i++){top[i]=i==0?0:bottom[i-1]+(i==2?thresholdGap:gap);bottom[i]=top[i]+sizes[i];}
    }
}
