package top.rongshangs.lumacurve.refactor;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

/** An optional six-question draft, isolated from the settings until final confirmation. */
final class QuickSetupPage {
    final MainActivity a;LinearLayout root;ScrollView scroll;boolean requested,checking;
    int step;int[] answers=QuickSetupPlan.empty();String baseline="";
    QuickSetupPage(MainActivity activity){a=activity;}
    boolean showing(){return root!=null&&root.getParent()!=null;}
    void restore(Bundle saved){if(saved==null||!saved.getBoolean("quick_setup_open",false))return;try{answers=QuickSetupPlan.checked(saved.getIntArray("quick_setup_answers"),false);step=Math.max(0,Math.min(QuickSetupPlan.COUNT,saved.getInt("quick_setup_step")));baseline=saved.getString("quick_setup_baseline","");requested=true;}catch(IllegalArgumentException invalid){answers=QuickSetupPlan.empty();}}
    void save(Bundle saved){saved.putBoolean("quick_setup_open",showing()||requested);saved.putIntArray("quick_setup_answers",answers);saved.putInt("quick_setup_step",step);saved.putString("quick_setup_baseline",baseline);}
    void invite(){invite(false);}
    void invite(boolean explicit){
        if(a.page!=1||a.settingsGroup>=0||a.destroyed||!a.visible||showing()||requested||a.welcome.showing()||!a.openDialogs.isEmpty())return;
        if(a.busy){if(explicit)a.show("另一项操作还在执行");return;}if(a.runtime==null||a.factoryLux==null){if(explicit)a.show("连接设备后再开始快速设置");return;}
        if(!explicit&&a.getPreferences(0).getBoolean(QuickSetupPlan.INVITATION_KEY,false))return;
        LinearLayout body=a.column();body.setPadding(a.dp(6),a.dp(12),a.dp(6),a.dp(8));body.setGravity(Gravity.CENTER);
        TextView description=center("回答6个简单的问题让亮度更符合你的习惯",18,a.INK);description.setTag("quick-invite-description");description.setLineSpacing(a.dp(6),1);description.setPadding(0,a.dp(6),0,a.dp(8));body.addView(description,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog d=a.dialog(center("试试快速设置",28,a.INK),body,480);
        d.show();a.getPreferences(0).edit().putBoolean(QuickSetupPlan.INVITATION_KEY,true).apply();a.dialogButton(d,"暂不设置",null,false);a.dialogButton(d,"开始设置",this::begin,true);
        LinearLayout actions=(LinearLayout)d.getWindow().getDecorView().findViewWithTag("actions");
        for(int i=0;i<actions.getChildCount();i++){Button button=(Button)actions.getChildAt(i);button.setTextSize(15);button.setMinHeight(a.dp(52));button.setMinimumHeight(a.dp(52));button.setMaxLines(2);button.setPadding(a.dp(10),a.dp(12),a.dp(10),a.dp(12));LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)button.getLayoutParams();lp.height=-1;button.setLayoutParams(lp);}
    }
    TextView center(String value,int size,int color){TextView view=a.text(value,size,color);view.setGravity(Gravity.CENTER);view.setIncludeFontPadding(false);return view;}
    TextView start(String value,int size,int color){TextView view=a.text(value,size,color);view.setGravity(Gravity.START);view.setIncludeFontPadding(false);return view;}
    Button navigation(String label,Runnable action){Button button=a.action(label,action);button.setTextSize(15);button.setMinHeight(a.dp(52));button.setMinimumHeight(a.dp(52));button.setMaxLines(2);button.setPadding(a.dp(10),a.dp(12),a.dp(10),a.dp(12));return button;}
    final class ChoiceCard extends LinearLayout implements Checkable {
        final RadioButton indicator;final TextView label;boolean checked;
        ChoiceCard(int question,int choice){
            super(a);setOrientation(HORIZONTAL);setBaselineAligned(false);setGravity(Gravity.CENTER_VERTICAL);setPadding(a.dp(14),a.dp(10),a.dp(14),a.dp(10));setMinimumHeight(a.dp(64));
            String unavailable=QuickSetupPlan.unavailable(question,choice,a.runtime),name=a.tr(QuickSetupPlan.LABELS[question][choice]),detail=a.tr(QuickSetupPlan.DETAILS[question][choice]);boolean available=unavailable.isEmpty();
            if(!available)detail+="\n"+a.tr("暂不可用：")+a.tr(unavailable);
            indicator=new RadioButton(a);indicator.setMinHeight(0);indicator.setMinimumHeight(0);indicator.setMinWidth(0);indicator.setMinimumWidth(0);indicator.setPadding(0,0,0,0);indicator.setClickable(false);indicator.setFocusable(false);indicator.setEnabled(available);indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);indicator.setButtonTintList(android.content.res.ColorStateList.valueOf(available?a.BLUE:a.MUTED));addView(indicator,new LinearLayout.LayoutParams(a.dp(28),a.dp(28)));
            LinearLayout copy=a.column();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=a.dp(12);addView(copy,cp);
            label=start(name,16,available?a.BLUE:a.MUTED);label.setTag("quick-choice-label");label.setPadding(0,0,0,a.dp(4));copy.addView(label,new LinearLayout.LayoutParams(-1,-2));
            TextView description=start(detail,13,a.MUTED);description.setTag("quick-choice-description");description.setPadding(0,0,0,0);description.setLineSpacing(a.dp(3),1);copy.addView(description,new LinearLayout.LayoutParams(-1,-2));
            copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);setContentDescription(name+"，"+detail);setEnabled(available);setClickable(available);setFocusable(available);setChecked(answers[question]==choice);
            setOnClickListener(v->{if(!isEnabled())return;boolean selected=!checked;answers[question]=selected?choice:0;for(ChoiceCard option:choiceCards)option.setChecked(selected&&option==this);});
        }
        public void setChecked(boolean value){checked=value;indicator.setChecked(value);setSelected(value);setBackground(a.ripple(value?0xffedf3ff:android.graphics.Color.WHITE,16));}
        public boolean isChecked(){return checked;}public void toggle(){performClick();}
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.RadioButton");info.setCheckable(true);info.setChecked(checked);}
    }
    final java.util.List<ChoiceCard> choiceCards=new java.util.ArrayList<>();
    void open(){invite(true);}
    void begin(){
        if(showing())return;if(a.busy){a.show("另一项操作还在执行");return;}if(a.runtime==null||a.factoryLux==null){a.show("连接设备后再开始快速设置");return;}
        requested=true;step=0;answers=QuickSetupPlan.empty();baseline=a.loadedBaseline;resume();
    }
    void resume(){
        if(!requested||showing()||a.destroyed||!a.visible||a.isFinishing()||a.busy||a.welcome.showing()||!a.openDialogs.isEmpty()||a.runtime==null||a.factoryLux==null)return;
        if(!baseline.equals(a.loadedBaseline)){requested=false;a.show("设备亮度基准已变化，请重新开始快速设置");return;}
        if(!(a.root.getParent() instanceof ViewGroup))return;requested=false;
        root=a.column();root.setBackgroundColor(a.BG);a.finishPageTransition();a.root.setVisibility(View.GONE);((ViewGroup)a.root.getParent()).addView(root,new ViewGroup.LayoutParams(-1,-1));a.getPreferences(0).edit().putBoolean(QuickSetupPlan.INVITATION_KEY,true).apply();a.ui.removeCallbacks(a.tick);render();reveal(1);
    }
    void primary(Button button){button.setTextColor(a.darkTheme?a.BG:android.graphics.Color.WHITE);button.setBackground(a.ripple(a.BLUE,12));}
    void render(){
        if(!showing())return;finishTransition();root.removeAllViews();choiceCards.clear();
        LinearLayout bar=a.row(root);bar.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(12));bar.addView(start("快速设置",18,a.INK),new LinearLayout.LayoutParams(0,-2,1));bar.addView(a.text(step<QuickSetupPlan.COUNT?(step+1)+" / "+QuickSetupPlan.COUNT:"确认设置",12,a.MUTED));
        scroll=new ScrollView(a);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=a.column();body.setPadding(a.dp(20),a.dp(14),a.dp(20),a.dp(16));scroll.addView(body);
        if(step<QuickSetupPlan.COUNT){
            final int question=step;TextView title=start(QuickSetupPlan.QUESTIONS[question],22,a.INK);title.setTag("quick-question");title.setLineSpacing(a.dp(4),1);title.setPadding(0,0,0,a.dp(10));body.addView(title,new LinearLayout.LayoutParams(-1,-2));
            TextView note=start(QuickSetupPlan.NOTES[question],13,a.MUTED);note.setTag("quick-question-description");note.setLineSpacing(a.dp(3),1);note.setPadding(0,0,0,a.dp(18));body.addView(note,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout group=a.column();body.addView(group,new LinearLayout.LayoutParams(-1,-2));
            for(int choice=1;choice<QuickSetupPlan.LABELS[question].length;choice++){
                ChoiceCard option=new ChoiceCard(question,choice);choiceCards.add(option);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=choice==QuickSetupPlan.LABELS[question].length-1?0:a.dp(8);group.addView(option,lp);
            }
        }else{
            TextView summary=start("确认你的亮度偏好",22,a.INK);summary.setPadding(0,0,0,a.dp(10));body.addView(summary,new LinearLayout.LayoutParams(-1,-2));TextView note=start(a.dirty?"所选预设与现有草稿一起保存，未选择的项目保持原样。":"仅修改你选择的项目，其余设置保持原样。",13,a.MUTED);note.setLineSpacing(a.dp(3),1);note.setPadding(0,0,0,a.dp(14));body.addView(note,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout list=a.card(body);list.setTag("quick-summary-list");list.setPadding(a.dp(14),a.dp(4),a.dp(8),a.dp(4));
            for(int i=0;i<QuickSetupPlan.COUNT;i++){
                final int question=i;LinearLayout line=a.row(list);line.setTag("quick-summary-row");line.setPadding(0,a.dp(8),0,a.dp(8));LinearLayout copy=a.column();line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView topic=start(QuickSetupPlan.TOPICS[i],12,a.MUTED);topic.setPadding(0,0,0,a.dp(3));copy.addView(topic,new LinearLayout.LayoutParams(-1,-2));
                int choice=answers[i];TextView selected=start(choice>0?QuickSetupPlan.LABELS[i][choice]:"未修改",15,choice>0?a.INK:a.MUTED);selected.setTag("quick-summary-selection");selected.setPadding(0,0,0,0);copy.addView(selected,new LinearLayout.LayoutParams(-1,-2));
                if(choice>0){String unavailable=QuickSetupPlan.unavailable(i,choice,a.runtime);if(!unavailable.isEmpty())copy.addView(start(unavailable,12,0xffbd3434),new LinearLayout.LayoutParams(-1,-2));}
                Button edit=a.action("修改",()->move(question));edit.setTextSize(13);edit.setMinHeight(a.dp(40));edit.setMinimumHeight(a.dp(40));edit.setPadding(a.dp(6),a.dp(6),a.dp(6),a.dp(6));line.addView(edit,new LinearLayout.LayoutParams(a.dp(56),-2));
                if(i<QuickSetupPlan.COUNT-1){View divider=new View(a);divider.setBackgroundColor(a.darkTheme?0xff303b49:0xffe5e9f0);list.addView(divider,new LinearLayout.LayoutParams(-1,a.dp(1)));}
            }
            if(answers[4]>0&&a.outdoorOptions.nodeBoost)body.addView(start("户外预设将关闭强制节点增强，保留已设置的 nit 上限与节点目标。",13,a.MUTED),new LinearLayout.LayoutParams(-1,-2));
        }
        LinearLayout footer=a.column();footer.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(18));root.addView(footer,new LinearLayout.LayoutParams(-1,-2));LinearLayout buttons=a.row(footer);
        Button cancel=navigation("取消",this::cancel);cancel.setTag("quick-setup-cancel");buttons.addView(cancel,new LinearLayout.LayoutParams(step==0?0:a.dp(64),-1,step==0?1:0));
        if(step>0){Button previous=navigation("上一步",()->move(step-1));LinearLayout.LayoutParams previousLp=new LinearLayout.LayoutParams(0,-1,1);previousLp.leftMargin=a.dp(8);buttons.addView(previous,previousLp);previous.setEnabled(!checking);}
        Button next=navigation(checking?"正在检查…":step==QuickSetupPlan.COUNT?hasChanges()?"保存并应用":"完成":"下一步",()->{if(step<QuickSetupPlan.COUNT)move(step+1);else apply();});next.setTag("quick-setup-next");primary(next);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.leftMargin=a.dp(8);buttons.addView(next,lp);next.setEnabled(!checking);
        TextView footnote=center("不选择将不会进行修改",12,a.MUTED);footnote.setTag("quick-footer-hint");footnote.setPadding(0,a.dp(12),0,0);footer.addView(footnote,new LinearLayout.LayoutParams(-1,-2));
    }
    boolean hasChanges(){for(int choice:answers)if(choice>0)return true;return false;}
    void apply(){
        if(a.busy||checking)return;if(!hasChanges()){close();return;}
        // Re-read the current device before composing: the screen or capabilities
        // may have changed while the user was answering. This is not a new probe.
        checking=true;render();a.run("inspect",null);
    }
    void refreshed(String command,boolean success){
        if(!checking||!command.equals("inspect")||!showing())return;checking=false;
        if(!success||a.runtime==null||a.factoryLux==null){render();return;}
        if(!baseline.equals(a.loadedBaseline)){render();a.show("设备亮度基准已变化，请重新开始快速设置");return;}
        if(!a.openDialogs.isEmpty()){render();return;}
        try{
            JSONObject original=a.configuration(),next=QuickSetupPlan.build(original,a.runtime,answers);
            // Validate curve coordinates before loadOptions can change any Activity fields.
            SavedCurve.plan(SavedCurve.parse(next),a.factoryLux,SavedCurve.numbers(a.runtime.getJSONArray("factory_logical_nit")),a.minimum,a.maximum,CurvePlan.factors(next.getString("factors")),CurvePlan.floor(next.opt("curve_floor_nit")));
            a.loadOptions(next);a.dirty=true;a.drawCurve();a.run("apply",java.util.Base64.getEncoder().encodeToString(next.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));close();
        }catch(Exception invalid){render();a.show(invalid.getMessage());}
    }
    void move(int value){if(!showing()||checking)return;int next=Math.max(0,Math.min(QuickSetupPlan.COUNT,value)),direction=Integer.compare(next,step);step=next;render();reveal(direction);}
    boolean back(){if(!showing())return false;if(step==0)cancel();else move(step-1);return true;}
    void cancel(){if(!hasChanges()){close();return;}a.messageDialog("退出快速设置？","本次选择尚未应用，退出后保留原有设置。","退出",this::close);}
    void finishTransition(){if(root!=null){root.animate().cancel();root.animate().setListener(null).withEndAction(null);root.setAlpha(1);root.setTranslationX(0);}}
    void reveal(int direction){finishTransition();if(root==null||!a.visible||!android.animation.ValueAnimator.areAnimatorsEnabled())return;root.setAlpha(0);root.setTranslationX(a.dp(12)*Integer.signum(direction));root.animate().alpha(1).translationX(0).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(180).start();}
    void close(){dispose();if(!a.destroyed&&a.visible)a.resumeRuntime();}
    void dispose(){finishTransition();requested=false;checking=false;if(root!=null&&root.getParent() instanceof ViewGroup)((ViewGroup)root.getParent()).removeView(root);root=null;scroll=null;choiceCards.clear();if(a.root!=null)a.root.setVisibility(View.VISIBLE);}
}
