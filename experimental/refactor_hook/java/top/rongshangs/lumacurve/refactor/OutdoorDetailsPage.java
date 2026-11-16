package top.rongshangs.lumacurve.refactor;

import android.view.*;
import android.widget.*;

/** Shared drafts on a normal secondary surface; navigation never submits settings. */
final class OutdoorDetailsPage {
    final MainActivity a;final LinearLayout root,body;final ScrollView scroll;final Button apply;final TextView hint;boolean visible;
    OutdoorDetailsPage(MainActivity host){
        a=host;root=a.column();root.setBackgroundColor(a.BG);LinearLayout bar=a.row(root);bar.setPadding(a.dp(10),a.dp(6),a.dp(18),a.dp(6));bar.setBaselineAligned(false);
        MainActivity.NavigationArrow back=a.new NavigationArrow(true);back.setBackground(a.ripple(android.graphics.Color.TRANSPARENT,24));back.setClipToOutline(true);back.setClickable(true);back.setFocusable(true);back.setContentDescription(a.tr("返回高亮增强"));back.setOnClickListener(v->a.closeOutdoorDetails());bar.addView(back,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        LinearLayout words=a.column();LinearLayout.LayoutParams titleSize=new LinearLayout.LayoutParams(0,-2,1);titleSize.leftMargin=a.dp(6);bar.addView(words,titleSize);TextView title=a.text("详细设置",18,a.INK);words.addView(title);hint=a.text("有未保存的设置",10,0xffbd3434);hint.setPadding(0,0,0,0);words.addView(hint);
        apply=a.action("保存并应用",a::submit);apply.setTextSize(12);apply.setSingleLine(false);bar.addView(apply,new LinearLayout.LayoutParams(a.dp(104),a.dp(48)));
        scroll=new ElasticScrollView(a);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=a.column();body.setPadding(a.dp(18),a.dp(10),a.dp(18),a.dp(22));scroll.addView(body);root.setVisibility(View.GONE);refresh(false,false);
    }
    void refresh(boolean canApply,boolean dirty){apply.setEnabled(canApply);hint.setVisibility(dirty?View.VISIBLE:View.GONE);}
}
