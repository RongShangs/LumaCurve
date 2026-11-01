package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** Four explicit equal-width actions. No nested weighted rows or fixed text height. */
final class UpdateDialogActions extends LinearLayout {
    final int gap,bottomSpace;final int[] rowHeight=new int[2];
    UpdateDialogActions(Context context){super(context);gap=dp(8);bottomSpace=dp(4);setOrientation(VERTICAL);setBaselineAligned(false);setTag("update-footer");}
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onMeasure(int w,int h){
        int width=MeasureSpec.getSize(w),content=Math.max(0,width-getPaddingLeft()-getPaddingRight());
        int cell=Math.max(0,(content-gap)/2);rowHeight[0]=rowHeight[1]=0;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            child.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            rowHeight[i/2]=Math.max(rowHeight[i/2],child.getMeasuredHeight());
        }
        // Equal height within each row; natural multiline text and touch targets win.
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()!=GONE)child.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(rowHeight[i/2],MeasureSpec.EXACTLY));}
        int height=getPaddingTop()+rowHeight[0]+(rowHeight[1]>0?gap:0)+rowHeight[1]+bottomSpace+getPaddingBottom();
        setMeasuredDimension(width,height);
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int cell=Math.max(0,(right-left-getPaddingLeft()-getPaddingRight()-gap)/2);
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
            int x=getPaddingLeft()+(i%2)*(cell+gap),y=getPaddingTop()+(i/2==0?0:rowHeight[0]+gap);
            child.layout(x,y,x+cell,y+rowHeight[i/2]);
        }
    }
}
