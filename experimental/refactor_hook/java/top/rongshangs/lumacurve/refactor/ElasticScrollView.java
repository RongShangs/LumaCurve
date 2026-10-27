package top.rongshangs.lumacurve.refactor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

/** Shared hidden-scrollbar viewport with bounded edge translation and a damped return. */
class ElasticScrollView extends ScrollView {
    final ScrollElasticity edge=new ScrollElasticity();ValueAnimator returning;boolean touching;java.util.function.Consumer<MotionEvent> gestureObserver;
    ElasticScrollView(Context context){super(context);setVerticalScrollBarEnabled(false);setHorizontalScrollBarEnabled(false);setScrollIndicators(0);setVerticalFadingEdgeEnabled(false);setHorizontalFadingEdgeEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);}
    void observeGesture(java.util.function.Consumer<MotionEvent> observer){gestureObserver=observer;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(gestureObserver!=null)gestureObserver.accept(event);
        int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){touching=true;cancelReturn();}
        try{return super.dispatchTouchEvent(event);}finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){touching=false;release();}}
    }
    @Override protected boolean overScrollBy(int dx,int dy,int x,int y,int rangeX,int rangeY,int maxX,int maxY,boolean touch){
        int next=dy;
        if(!ValueAnimator.areAnimatorsEnabled()||rangeY<=0||getChildCount()==0){finishEdge();}
        else if(touch||!touching&&returning==null){next=edge.scroll(dy,y,rangeY,getHeight(),getResources().getDisplayMetrics().density,touch);applyOffset();if(!touch)release();}
        return super.overScrollBy(dx,next,x,y,rangeX,rangeY,0,0,touch);
    }
    void applyOffset(){if(getChildCount()>0)getChildAt(0).setTranslationY(edge.offset());}
    void cancelReturn(){ValueAnimator old=returning;returning=null;if(old!=null)old.cancel();}
    void release(){
        if(edge.pull==0)return;if(!ValueAnimator.areAnimatorsEnabled()||!isShown()){finishEdge();return;}
        cancelReturn();ValueAnimator next=ValueAnimator.ofFloat(edge.pull,0);returning=next;next.setDuration(360);next.setInterpolator(ScrollElasticity::returnFraction);
        next.addUpdateListener(value->{if(returning==next){edge.setPull((Float)value.getAnimatedValue());applyOffset();}});
        next.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){if(returning==next){returning=null;edge.reset();applyOffset();}}});next.start();
    }
    void finishEdge(){if(edge==null)return;cancelReturn();edge.reset();applyOffset();}
    @Override public void scrollTo(int x,int y){finishEdge();super.scrollTo(x,y);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);if(w!=oldW||h!=oldH)finishEdge();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=View.VISIBLE)finishEdge();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=View.VISIBLE)finishEdge();}
    @Override public void onViewRemoved(View child){child.setTranslationY(0);finishEdge();super.onViewRemoved(child);}
    @Override protected void onDetachedFromWindow(){finishEdge();super.onDetachedFromWindow();}
}
