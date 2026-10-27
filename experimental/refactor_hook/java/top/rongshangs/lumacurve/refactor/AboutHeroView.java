package top.rongshangs.lumacurve.refactor;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;
import org.json.*;

/** A rounded square app icon. Slow text stays clipped inside its scrolling container. */
final class AboutHeroView extends View {
    static final int CORNER_DP=48;
    final MainActivity a;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final Path curve=new Path();final AboutMarquee motion=new AboutMarquee();List<String> messages=Arrays.asList(AboutMarquee.TESTERS);boolean active,scheduled;float exposed;long last;
    final Runnable frame=()->{scheduled=false;if(!running()){last=0;return;}long now=SystemClock.uptimeMillis();if(last!=0)motion.advance((now-last)/1000f);last=now;invalidate();schedule();};
    AboutHeroView(MainActivity a){super(a);this.a=a;setBackground(a.background(0xffeef2ff,CORNER_DP));setClipToOutline(true);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);setLayerType(View.LAYER_TYPE_NONE,null);}
    void data(JSONObject data)throws JSONException {messages=AboutMarquee.messages(data);configure();invalidate();}
    void configure(){if(getWidth()<=0)return;float density=getResources().getDisplayMetrics().density,font=getResources().getDisplayMetrics().scaledDensity;paint.setTextSize(13*font);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));int lanes=Math.max(1,Math.min(10,(int)((getHeight()-28*density)/Math.max(1,paint.getTextSize()*3.4f))));motion.configure(messages,getWidth(),lanes,density,paint::measureText);}
    void active(boolean value){active=value;refresh();}
    void expose(float height){exposed=Math.max(0,height);refresh();}
    boolean running(){return active&&a.visible&&!a.destroyed&&a.page==3&&!a.welcome.showing()&&!a.quickSetup.showing()&&isShown()&&exposed>getResources().getDisplayMetrics().density*24&&ValueAnimator.areAnimatorsEnabled();}
    void refresh(){if(running())schedule();else{removeCallbacks(frame);scheduled=false;last=0;}}
    void schedule(){if(!scheduled&&running()){scheduled=true;postDelayed(frame,33);}}
    void dispose(){active=false;refresh();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);configure();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(a!=null)refresh();}
    @Override protected void onDetachedFromWindow(){dispose();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=getWidth();if(size<=0)return;canvas.save();canvas.clipRect(0,0,size,getHeight());paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(size*5/64);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(a.darkTheme?0xff344d73:0xffccdafa);curve.reset();curve.moveTo(size*14/64,size*47/64);curve.cubicTo(size*32/64,size*47/64,size*28/64,size*17/64,size*50/64,size*17/64);canvas.drawPath(curve,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(a.darkTheme?0xffc0cee8:0xff40577f);float inset=getResources().getDisplayMetrics().density*14;for(AboutMarquee.Line line:motion.lines){float y=inset+line.y*Math.max(0,getHeight()-2*inset)+paint.getTextSize()*.32f;canvas.drawText(line.text,line.x,y,paint);}canvas.restore();}
}
