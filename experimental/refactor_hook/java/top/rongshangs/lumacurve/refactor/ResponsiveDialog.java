package top.rongshangs.lumacurve.refactor;

import android.app.Activity;
import android.graphics.Insets;
import android.view.*;
import android.widget.*;

/** Keep the title and actions visible; only the body scrolls in short windows. */
final class ResponsiveDialog extends LinearLayout {
    final Activity host;final View title;final LinearLayout actions;final ScrollView body;
    int bodyLimit=Integer.MAX_VALUE;Insets previousInsets;
    ResponsiveDialog(Activity host,View title,View content,LinearLayout actions){
        super(host);this.host=host;this.title=title;this.actions=actions;setOrientation(VERTICAL);
        addView(title,new LayoutParams(-1,-2));
        body=new ScrollView(host){@Override protected void onMeasure(int w,int h){int limit=bodyLimit;if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)limit=Math.min(limit,MeasureSpec.getSize(h));super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.max(1,limit),MeasureSpec.AT_MOST));}};
        body.setFillViewport(false);
        // Existing detail dialogs already supply a ScrollView. Reuse its contents,
        // so neither nested scrolling nor an old fixed height constrains this viewport.
        if(content instanceof ScrollView&&((ScrollView)content).getChildCount()==1){ScrollView old=(ScrollView)content;content=old.getChildAt(0);old.removeView(content);}
        if(content!=null)body.addView(content,new android.widget.FrameLayout.LayoutParams(-1,-2));
        LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(10);addView(body,lp);LayoutParams footer=actions.getLayoutParams() instanceof LayoutParams?(LayoutParams)actions.getLayoutParams():new LayoutParams(-1,-2);addView(actions,footer);
        setOnApplyWindowInsetsListener((v,insets)->{Insets next=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());if(!next.equals(previousInsets)){previousInsets=next;requestLayout();}return insets;});
    }
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onMeasure(int w,int h){
        WindowMetrics metrics=host.getSystemService(WindowManager.class).getCurrentWindowMetrics();
        WindowInsets insets=getRootWindowInsets();if(insets==null)insets=metrics.getWindowInsets();
        Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
        int cap=Math.max(1,metrics.getBounds().height()-safe.top-safe.bottom-dp(24));
        if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));
        int natural=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        measureChildWithMargins(title,w,0,natural,0);measureChildWithMargins(actions,w,0,natural,0);
        LayoutParams lp=(LayoutParams)body.getLayoutParams();
        LayoutParams footer=(LayoutParams)actions.getLayoutParams();bodyLimit=Math.max(1,cap-getPaddingTop()-getPaddingBottom()-title.getMeasuredHeight()-actions.getMeasuredHeight()-lp.topMargin-lp.bottomMargin-footer.topMargin-footer.bottomMargin);
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
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
