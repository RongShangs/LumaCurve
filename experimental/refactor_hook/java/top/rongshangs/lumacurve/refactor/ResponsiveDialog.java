package top.rongshangs.lumacurve.refactor;

import android.app.Activity;
import android.graphics.Insets;
import android.view.*;
import android.widget.*;

/** Keep the title and actions visible; only the body scrolls in short windows. */
final class ResponsiveDialog extends LinearLayout {
    final Activity host;final View title;final LinearLayout actions;final ScrollView body;
    int bodyLimit=Integer.MAX_VALUE;float heightFraction=1;Insets previousInsets;
    ResponsiveDialog(Activity host,View title,View content,LinearLayout actions){
        super(host);this.host=host;this.title=title;this.actions=actions;setOrientation(VERTICAL);
        addView(title,new LayoutParams(-1,-2));
        body=new ScrollView(host){@Override protected void onMeasure(int w,int h){int limit=bodyLimit;if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(h));super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.max(0,limit),MeasureSpec.AT_MOST));}};
        body.setFillViewport(false);
        body.setTag("dialog-scroll");
        // Existing detail dialogs already supply a ScrollView. Reuse its contents,
        // so neither nested scrolling nor an old fixed height constrains this viewport.
        if(content instanceof ScrollView&&((ScrollView)content).getChildCount()==1){ScrollView old=(ScrollView)content;content=old.getChildAt(0);old.removeView(content);}
        if(content!=null)body.addView(content,new android.widget.FrameLayout.LayoutParams(-1,-2));
        LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(10);addView(body,lp);LayoutParams footer=actions.getLayoutParams() instanceof LayoutParams?(LayoutParams)actions.getLayoutParams():new LayoutParams(-1,-2);addView(actions,footer);
        setOnApplyWindowInsetsListener((v,insets)->{Insets next=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());if(!next.equals(previousInsets)){previousInsets=next;requestLayout();}return insets;});
    }
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void setHeightFraction(float fraction){heightFraction=Math.max(.5f,Math.min(1,fraction));requestLayout();}
    @Override protected void onMeasure(int w,int h){
        WindowMetrics metrics=host.getSystemService(WindowManager.class).getCurrentWindowMetrics();
        WindowInsets insets=getRootWindowInsets();if(insets==null)insets=metrics.getWindowInsets();
        Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
        int available=Math.max(1,metrics.getBounds().height()-safe.top-safe.bottom-dp(24));
        if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)available=Math.min(available,MeasureSpec.getSize(h));
        int cap=Math.max(1,Math.round(available*heightFraction));
        int width=MeasureSpec.getMode(w)==MeasureSpec.UNSPECIFIED?metrics.getBounds().width()-safe.left-safe.right:MeasureSpec.getSize(w);
        int fixedWidth=MeasureSpec.makeMeasureSpec(Math.max(1,width),MeasureSpec.EXACTLY);
        int natural=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        // Measure fixed chrome independently of the notes. A second LinearLayout
        // measuring pass can otherwise shrink the final action row to the remainder.
        measureChildWithMargins(title,fixedWidth,0,natural,0);measureChildWithMargins(actions,fixedWidth,0,natural,0);
        LayoutParams heading=(LayoutParams)title.getLayoutParams();
        LayoutParams lp=(LayoutParams)body.getLayoutParams();
        LayoutParams footer=(LayoutParams)actions.getLayoutParams();
        int fixed=getPaddingTop()+getPaddingBottom()+title.getMeasuredHeight()+heading.topMargin+heading.bottomMargin+actions.getMeasuredHeight()+footer.topMargin+footer.bottomMargin+lp.topMargin+lp.bottomMargin;
        if(fixed>available){
            int previous=title.getMeasuredHeight();
            title.measure(MeasureSpec.makeMeasureSpec(title.getMeasuredWidth(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(0,available-fixed+previous),MeasureSpec.AT_MOST));
            fixed+=title.getMeasuredHeight()-previous;
        }
        // Short windows / larger fonts may need more than the preferred fraction.
        // Grow only enough to retain the title, both action rows and a small viewport.
        cap=Math.min(available,Math.max(cap,fixed+dp(48)));
        bodyLimit=Math.max(0,cap-fixed);
        body.measure(MeasureSpec.makeMeasureSpec(Math.max(0,width-getPaddingLeft()-getPaddingRight()-lp.leftMargin-lp.rightMargin),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(bodyLimit,MeasureSpec.AT_MOST));
        setMeasuredDimension(width,Math.min(cap,fixed+body.getMeasuredHeight()));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int y=getPaddingTop();
        for(View child:new View[]{title,body,actions}){
            LayoutParams lp=(LayoutParams)child.getLayoutParams();y+=lp.topMargin;
            int x=getPaddingLeft()+lp.leftMargin;
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
            y+=child.getMeasuredHeight()+lp.bottomMargin;
        }
    }
    static void configure(Activity host,Window window,int maximumWidth){
        if(window==null)return;
        WindowMetrics metrics=host.getSystemService(WindowManager.class).getCurrentWindowMetrics();
        Insets safe=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int margin=Math.round(32*host.getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams params=window.getAttributes();params.width=Math.max(1,Math.min(maximumWidth,metrics.getBounds().width()-safe.left-safe.right-margin));params.height=WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);window.setGravity(Gravity.CENTER);window.setBackgroundDrawableResource(android.R.color.transparent);window.getDecorView().setElevation(0);window.setWindowAnimations(0);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}
