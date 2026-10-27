package top.rongshangs.lumacurve.refactor;

import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;

/** One capsule over the content; only real settings/about gestures may collapse it. */
final class FloatingNavigation {
    final MainActivity a;final LinearLayout view;final NavigationPolicy motion=new NavigationPolicy();
    final LinearLayout[] tabs=new LinearLayout[4];final MainActivity.NavIcon[] icons=new MainActivity.NavIcon[4];final TextView[] labels=new TextView[4];
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);ValueAnimator indicator,pressAnimation,visibilityAnimation;float indicatorLeft,pressScale=1,visibilityScale=1;boolean indicatorReady,pressed;int selectedPage;boolean present=true,keyboard,hidden;int bottom;long generation;
    FloatingNavigation(MainActivity owner,FrameLayout parent){
        a=owner;view=new LinearLayout(a){@Override protected void onDraw(Canvas canvas){super.onDraw(canvas);drawCapsule(canvas);}@Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action!=MotionEvent.ACTION_UP&&action!=MotionEvent.ACTION_CANCEL)touch(action,event.getX(),event.getY());try{return super.dispatchTouchEvent(event);}finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)touch(action,event.getX(),event.getY());}}};view.setOrientation(LinearLayout.HORIZONTAL);view.setGravity(Gravity.CENTER_VERTICAL);view.setBaselineAligned(false);
        // The halo is inside the view bounds so parent clipping cannot erase its shadow.
        view.setWillNotDraw(false);view.setLayerType(View.LAYER_TYPE_SOFTWARE,null);view.setPadding(a.dp(16),a.dp(16),a.dp(16),a.dp(16));view.setMinimumHeight(a.dp(88));
        String[] names={"状态","设置","日志","关于"};
        for(int i=0;i<4;i++){final int target=i;LinearLayout tab=a.column();tab.setGravity(Gravity.CENTER);tab.setPadding(a.dp(3),a.dp(5),a.dp(3),a.dp(5));tab.setMinimumHeight(a.dp(56));tab.setBackground(new RippleDrawable(ColorStateList.valueOf(a.darkTheme?0x3091b3ff:0x203265df),a.background(Color.TRANSPARENT,36),a.background(Color.WHITE,36)));tab.setClickable(true);tab.setFocusable(true);tab.setClipToOutline(true);tab.setOnClickListener(v->a.select(target));
            MainActivity.NavIcon icon=a.new NavIcon(i);tab.addView(icon,new LinearLayout.LayoutParams(a.dp(22),a.dp(22)));TextView label=a.text(names[i],11,a.MUTED);label.setGravity(Gravity.CENTER);label.setIncludeFontPadding(false);label.setPadding(0,a.dp(2),0,0);label.setMaxLines(2);tab.addView(label,new LinearLayout.LayoutParams(-1,-2));view.addView(tab,new LinearLayout.LayoutParams(0,-1,1));tabs[i]=tab;icons[i]=icon;labels[i]=label;
        }
        parent.setClipChildren(false);parent.setClipToPadding(false);parent.addView(view,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        parent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)layout();});
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)positionIndicator(false);if(b-t!=ob-ot){a.updatePageInsets(reserve());if(hidden)view.setTranslationY(distance());}});
    }
    void layout(){
        View parent=(View)view.getParent();if(parent==null)return;int width=NavigationPolicy.width(parent.getWidth(),a.dp(12),a.dp(312));
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)view.getLayoutParams();int margin=bottom+a.dp(6);
        if(lp.width!=width||lp.bottomMargin!=margin){lp.width=width;lp.bottomMargin=margin;view.setLayoutParams(lp);}a.updatePageInsets(reserve());
    }
    int reserve(){return keyboard?0:NavigationPolicy.reserve(Math.max(a.dp(88),view.getHeight()),a.dp(12),bottom);}
    float distance(){return Math.max(a.dp(88),view.getHeight())+bottom+a.dp(28);}
    void insets(int safeBottom,boolean ime){boolean changed=bottom!=safeBottom||keyboard!=ime;bottom=safeBottom;keyboard=ime;layout();if(changed)show(true);}
    void drawCapsule(Canvas canvas){float halo=a.dp(12),radius=(view.getHeight()-2*halo)/2;paint.setColor(a.themed(Color.WHITE));paint.setShadowLayer(a.dp(8),0,a.dp(2),a.darkTheme?0x90000000:0x35000000);canvas.drawRoundRect(halo,halo,view.getWidth()-halo,view.getHeight()-halo,radius,radius,paint);paint.clearShadowLayer();if(indicatorReady){View tab=tabs[selectedPage];paint.setColor(a.themed(0xffe5edff));canvas.drawRoundRect(indicatorLeft,tab.getTop(),indicatorLeft+tab.getWidth(),tab.getBottom(),a.dp(36),a.dp(36),paint);}}
    void positionIndicator(boolean animate){View target=tabs[selectedPage];if(target.getWidth()<=0)return;float end=target.getLeft();if(indicator!=null)indicator.cancel();if(!indicatorReady||!animate||!a.visible||!ValueAnimator.areAnimatorsEnabled()){indicatorReady=true;indicatorLeft=end;view.invalidate();return;}if(indicatorLeft==end)return;indicator=ValueAnimator.ofFloat(indicatorLeft,end);indicator.setDuration(240);indicator.setInterpolator(new DecelerateInterpolator());indicator.addUpdateListener(value->{indicatorLeft=(Float)value.getAnimatedValue();view.invalidate();});indicator.start();}
    void select(int selected){selectedPage=selected;for(int i=0;i<4;i++){boolean on=i==selected;tabs[i].setSelected(on);labels[i].setTextColor(on?a.BLUE:a.MUTED);icons[i].selected=on;icons[i].invalidate();}positionIndicator(true);}
    void finishSelection(){positionIndicator(false);}
    void applyScale(){float scale=pressScale*visibilityScale;view.setScaleX(scale);view.setScaleY(scale);}
    void press(boolean down,boolean immediate){
        if(pressed==down&&!immediate)return;pressed=down;ValueAnimator old=pressAnimation;pressAnimation=null;if(old!=null)old.cancel();
        if(immediate||!a.visible||!present||keyboard||!ValueAnimator.areAnimatorsEnabled()){pressScale=1;applyScale();return;}
        ValueAnimator next=ValueAnimator.ofFloat(pressScale,down?.97f:1f);pressAnimation=next;next.setDuration(down?85:260);next.setInterpolator(down?new DecelerateInterpolator():new OvershootInterpolator(1.5f));next.addUpdateListener(value->{if(pressAnimation==next){pressScale=(Float)value.getAnimatedValue();applyScale();}});next.start();
    }
    void touch(int action,float x,float y){if(action==MotionEvent.ACTION_DOWN)press(true,false);else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_MOVE&&(x<0||x>view.getWidth()||y<0||y>view.getHeight()))press(false,false);}
    void animateVisibilityScale(float target,boolean immediate,long duration){
        ValueAnimator old=visibilityAnimation;visibilityAnimation=null;if(old!=null)old.cancel();
        if(immediate){visibilityScale=target;applyScale();return;}
        ValueAnimator next=ValueAnimator.ofFloat(visibilityScale,target);visibilityAnimation=next;next.setDuration(duration);next.setInterpolator(new DecelerateInterpolator());next.addUpdateListener(value->{if(visibilityAnimation==next){visibilityScale=(Float)value.getAnimatedValue();applyScale();}});next.start();
    }
    void present(boolean value){if(present==value)return;present=value;show(true);}
    void show(boolean immediate){motion.reset();if(!immediate&&!hidden&&view.getVisibility()==View.VISIBLE&&view.getTranslationY()==0&&view.getAlpha()==1)return;move(false,immediate);}
    void move(boolean hide,boolean immediate){
        hidden=hide;final long token=++generation;view.animate().setListener(null).withEndAction(null);view.animate().cancel();
        boolean enabled=present&&!keyboard;view.setImportantForAccessibility(enabled&&!hide?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(!enabled){press(false,true);animateVisibilityScale(1,true,0);view.setVisibility(View.GONE);view.setTranslationY(0);view.setAlpha(1);return;}
        view.setVisibility(View.VISIBLE);
        boolean snap=immediate||!a.visible||!ValueAnimator.areAnimatorsEnabled();animateVisibilityScale(hide?.90f:1f,snap,hide?210:240);
        if(snap){view.setTranslationY(hide?distance():0);view.setAlpha(hide?0:1);if(hide)view.setVisibility(View.INVISIBLE);return;}
        view.animate().translationY(hide?distance():0).alpha(hide?0:1).setInterpolator(new DecelerateInterpolator()).setDuration(hide?210:240).withEndAction(()->{if(token==generation&&hidden)view.setVisibility(View.INVISIBLE);}).start();
    }
    void watch(ScrollView scroll,int page){
        final long[] gestureUntil={0};final boolean[] dragging={false};
        java.util.function.Consumer<MotionEvent> tracking=event->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){dragging[0]=true;gestureUntil[0]=Long.MAX_VALUE;}else if(action==MotionEvent.ACTION_UP){dragging[0]=false;gestureUntil[0]=SystemClock.uptimeMillis()+1400;}else if(action==MotionEvent.ACTION_CANCEL){dragging[0]=false;gestureUntil[0]=0;}};
        if(scroll instanceof ElasticScrollView)((ElasticScrollView)scroll).observeGesture(tracking);else scroll.setOnTouchListener((v,event)->{tracking.accept(event);return false;});
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(!a.visible||a.page!=page||a.settingsGroup>=0||a.featureChecksVisible||!present||keyboard||a.settingsHomeRestoring)return;
            if(!dragging[0]&&SystemClock.uptimeMillis()>=gestureUntil[0])return;
            boolean next=motion.scroll(y,oldY,a.dp(18),a.dp(9));if(next!=hidden)move(next,false);
        });
    }
    void stop(){press(false,true);finishSelection();show(true);}
}
