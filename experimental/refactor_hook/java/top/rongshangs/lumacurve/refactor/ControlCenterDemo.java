package top.rongshangs.lumacurve.refactor;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Animated explanation inside the selected partition card; has no brightness bridge. */
final class ControlCenterDemo extends View {
    final MainActivity a;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path track=new Path();
    long started;boolean scheduled;int ordinary,upper;boolean restoreAuto=true;
    final Runnable frame=()->{scheduled=false;if(!running())return;invalidate();schedule();};
    ControlCenterDemo(MainActivity a){super(a);this.a=a;setBackground(a.background(0xffeef2fa,14));setClipToOutline(true);setContentDescription(a.tr("分区滑条操作示意"));setOnClickListener(v->replay());}
    void data(int ordinary,int upper,boolean restoreAuto){if(this.ordinary!=ordinary||this.upper!=upper||this.restoreAuto!=restoreAuto){this.ordinary=ordinary;this.upper=upper;this.restoreAuto=restoreAuto;invalidate();}refresh();}
    boolean running(){return a.visible&&!a.destroyed&&a.page==1&&a.settingsGroup==8&&isShown()&&a.controls.nodeSliderMode==4&&ValueAnimator.areAnimatorsEnabled();}
    void replay(){started=SystemClock.uptimeMillis();invalidate();refresh();}
    void refresh(){if(running()){if(started==0)started=SystemClock.uptimeMillis();schedule();}else stop();}
    void schedule(){if(!scheduled&&running()){scheduled=true;postDelayed(frame,33);}}
    void stop(){removeCallbacks(frame);scheduled=false;}
    @Override protected void onVisibilityChanged(View v,int visibility){super.onVisibilityChanged(v,visibility);if(a!=null)refresh();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    float dp(float x){return x*getResources().getDisplayMetrics().density;}
    void label(Canvas canvas,String value,float x,float y,float width,int size,int color){
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(Math.min(size*getResources().getDisplayMetrics().scaledDensity,dp(size*1.25f)));
        String text=a.tr(value);while(paint.measureText(text)>width&&paint.getTextSize()>dp(10))paint.setTextSize(paint.getTextSize()-.5f);
        if(paint.measureText(text)>width){while(text.length()>1&&paint.measureText(text+"…")>width)text=text.substring(0,text.length()-1);text+="…";}
        canvas.drawText(text,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        ControlCenterDemoState.Frame f=ControlCenterDemoState.at(SystemClock.uptimeMillis()-started,ValueAnimator.areAnimatorsEnabled());
        int ink=a.INK,muted=a.MUTED,blue=a.BLUE,amber=a.darkTheme?0xffe0ba78:0xff96702d;
        String[] phases={"拖到普通上限","停住，松手","快速再次上滑","返回普通区间","自动高亮，随亮度显示"};
        label(canvas,phases[f.phase],dp(14),dp(25),w-dp(28),15,ink);
        float left=dp(24),right=dp(84),top=dp(44),bottom=h-dp(37),height=bottom-top,boundary=bottom-height*.75f;
        track.reset();track.addRoundRect(left,top,right,bottom,dp(14),dp(14),Path.Direction.CW);canvas.save();canvas.clipPath(track);
        paint.setStyle(Paint.Style.FILL);paint.setColor(a.darkTheme?0xff3c3429:0xffffefd6);canvas.drawRect(left,top,right,bottom,paint);
        paint.setColor(a.darkTheme?0xff304763:0xffd8e4f8);canvas.drawRect(left,boundary,right,bottom,paint);
        float knob=bottom-height*(float)f.progress;
        paint.setColor(blue);canvas.drawRect(left,Math.max(knob,boundary),right,bottom,paint);
        if(knob<boundary){paint.setColor(amber);canvas.drawRect(left,knob,right,boundary,paint);}canvas.restore();
        paint.setColor(a.darkTheme?0xffaab8cb:0xff718197);paint.setStrokeWidth(dp(1));canvas.drawLine(right+dp(10),boundary,w-dp(15),boundary,paint);
        float x=dp(112),space=w-x-dp(14);
        label(canvas,"增强区间 · 25%",x,top+dp(16),space,12,ink);
        label(canvas,upper>0?"最高节点 "+upper:"最高节点 —",x,top+dp(34),space,12,muted);
        label(canvas,ordinary>0?"普通上限 "+ordinary:"普通上限 —",x,boundary+dp(18),space,12,ink);
        label(canvas,"普通区间 · 75%",x,boundary+dp(44),space,12,ink);
        label(canvas,"自动时可记忆",x,boundary+dp(63),space,12,muted);
        if(f.touching){float y=Math.max(top+dp(12),Math.min(bottom-dp(12),bottom-height*(float)f.finger));paint.setColor(a.darkTheme?0xfff0f4fc:0xff17263c);paint.setStyle(Paint.Style.FILL);canvas.drawCircle((left+right)/2,y,dp(9),paint);}
        label(canvas,f.phase==4?"自动高亮无需二次滑动":f.phase==3?(restoreAuto?"返回后恢复自动":"返回后保持手动"):"点按示意图可重播",dp(14),h-dp(13),w-dp(28),12,muted);
    }
}
