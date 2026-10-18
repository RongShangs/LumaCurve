package top.rongshangs.lumacurve.refactor;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** A full-page introduction above the existing UI. Closing restores the same views and drafts. */
final class WelcomeGuide {
    static final String COMPLETE_KEY="welcome_completed_v1";
    final MainActivity a;
    LinearLayout root;ScrollView scroll;ViewGroup parent;ImageView logo;
    android.animation.ObjectAnimator logoFloat;
    boolean requested,firstRun;int page,restoreY;
    WelcomeGuide(MainActivity a){this.a=a;}
    boolean showing(){return root!=null&&root.getParent()!=null;}
    void restore(Bundle saved){if(saved!=null&&saved.getBoolean("welcome_open",false)){requested=true;firstRun=saved.getBoolean("welcome_first",false);page=TutorialContent.clamp(saved.getInt("welcome_page",0));restoreY=Math.max(0,saved.getInt("welcome_y",0));}}
    void save(Bundle saved){saved.putBoolean("welcome_open",showing()||requested);saved.putBoolean("welcome_first",firstRun);saved.putInt("welcome_page",page);saved.putInt("welcome_y",scroll==null?restoreY:scroll.getScrollY());}
    void openTutorial(){if(showing())return;requested=true;firstRun=false;page=0;restoreY=0;resume();}
    void resume(){
        if(a.destroyed||!a.visible||a.isFinishing())return;
        if(showing()){startLogoFloat();return;}
        if(!requested&&a.getPreferences(0).getBoolean(COMPLETE_KEY,false))return;
        if(a.permissionDialogVisible||a.legacyDialogVisible||a.updateDialog!=null||!a.openDialogs.isEmpty())return;
        if(!(a.root.getParent() instanceof ViewGroup))return;
        if(!requested){firstRun=true;page=0;restoreY=0;}
        requested=false;parent=(ViewGroup)a.root.getParent();root=a.column();root.setBackgroundColor(a.BG);
        resetTransition(a.root);a.root.setVisibility(View.GONE);parent.addView(root,new ViewGroup.LayoutParams(-1,-1));a.ui.removeCallbacks(a.tick);render();reveal(root,1);
    }
    TextView copy(String value,int size,int color){TextView view=a.text(value,size,color);view.setLineSpacing(a.dp(3),1);return view;}
    void render(){
        if(!showing())return;stopLogoFloat();logo=null;page=TutorialContent.clamp(page);root.removeAllViews();
        if(page>0){
            LinearLayout bar=a.row(root);bar.setPadding(a.dp(18),a.dp(6),a.dp(18),a.dp(6));
            TextView brand=a.text("使用教程",19,a.INK);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
            bar.addView(a.text(page+" / "+TutorialContent.pages(),12,a.MUTED));
        }
        scroll=new ScrollView(a);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=a.column();body.setPadding(a.dp(24),a.dp(16),a.dp(24),a.dp(20));scroll.addView(body);
        if(page==0){
            body.setGravity(Gravity.CENTER);logo=a.image(null,88,26);logo.setContentDescription("HyperLux");body.addView(logo);startLogoFloat();
            TextView hello=centerCopy(TutorialContent.HELLO,34,a.INK);hello.setPadding(0,a.dp(30),0,a.dp(12));body.addView(hello,new LinearLayout.LayoutParams(-1,-2));
            TextView intro=centerCopy(TutorialContent.INTRO,20,a.INK);
            android.text.SpannableString styled=new android.text.SpannableString(intro.getText());
            int at=styled.toString().indexOf("HyperLux");
            if(at>=0)styled.setSpan(new android.text.style.ForegroundColorSpan(a.BLUE),at,at+"HyperLux".length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            intro.setText(styled);body.addView(intro,new LinearLayout.LayoutParams(-1,-2));
        }else{
            int index=page-1;body.addView(copy(TutorialContent.TITLES[index],25,a.INK));TextView summary=copy(TutorialContent.SUMMARIES[index],14,a.MUTED);summary.setPadding(0,a.dp(6),0,a.dp(16));body.addView(summary);
            for(int i=0;i<TutorialContent.HEADINGS[index].length;i++){LinearLayout card=a.card(body);card.setPadding(a.dp(16),a.dp(12),a.dp(16),a.dp(12));
                int color=page==2&&i==0?a.BLUE:page==2&&i==1?0xff38775c:a.INK;
                card.addView(copy(TutorialContent.HEADINGS[index][i],16,color));card.addView(copy(TutorialContent.PARAGRAPHS[index][i],14,a.MUTED));}
        }
        LinearLayout actions=a.column();actions.setPadding(a.dp(24),a.dp(8),a.dp(24),a.dp(16));root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        if(page==0){
            Button next=a.action("下一步",()->move(1));primary(next);actions.addView(next,new LinearLayout.LayoutParams(-1,a.dp(52)));
        }else{
            LinearLayout buttons=a.row(actions);Button previous=a.action("上一步",this::back);buttons.addView(previous,new LinearLayout.LayoutParams(0,a.dp(48),1));
            Button next=a.action(page==TutorialContent.pages()?(firstRun?"开始使用":"完成"):"下一步",()->{if(page==TutorialContent.pages())close(true);else move(page+1);});primary(next);LinearLayout.LayoutParams buttonSize=new LinearLayout.LayoutParams(0,a.dp(48),1);buttonSize.leftMargin=a.dp(12);buttons.addView(next,buttonSize);
            TextView hint=centerCopy(TutorialContent.REPLAY,11,a.MUTED);hint.setPadding(0,a.dp(14),0,0);actions.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        }
        final int y=restoreY;final ScrollView rendered=scroll;restoreY=0;rendered.post(()->{if(showing()&&!a.destroyed&&scroll==rendered)rendered.scrollTo(0,y);});
    }
    TextView centerCopy(String value,int size,int color){TextView view=copy(value,size,color);view.setGravity(Gravity.CENTER);return view;}
    void startLogoFloat(){
        if(logo==null||page!=0||!showing()||!a.visible||a.destroyed||logoFloat!=null||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        logoFloat=android.animation.ObjectAnimator.ofFloat(logo,"translationY",0,-a.dp(4),0);
        logoFloat.setDuration(3600);logoFloat.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        logoFloat.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());logoFloat.start();
    }
    void stopLogoFloat(){if(logoFloat!=null){logoFloat.cancel();logoFloat=null;}if(logo!=null)logo.setTranslationY(0);}
    void primary(Button button){button.setTextColor(a.darkTheme?a.BG:android.graphics.Color.WHITE);button.setBackground(a.ripple(a.BLUE,12));}
    void resetTransition(View view){if(view==null)return;view.animate().cancel();view.animate().setListener(null).withEndAction(null);view.setAlpha(1);view.setTranslationX(0);}
    void reveal(View view,int direction){
        resetTransition(view);
        if(view==null||a.destroyed||!a.visible||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
        view.setAlpha(0);view.setTranslationX(a.dp(12)*Integer.signum(direction));
        view.animate().alpha(1).translationX(0).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(180).start();
    }
    void finishTransition(){stopLogoFloat();resetTransition(root);resetTransition(a.root);}
    void move(int value){int next=TutorialContent.clamp(value);if(!showing()||next==page)return;int direction=Integer.compare(next,page);finishTransition();page=next;restoreY=0;render();reveal(root,direction);}
    boolean back(){if(!showing())return false;if(page==0){if(firstRun)a.finish();else close(false);return true;}move(page-1);return true;}
    void close(boolean completed){
        if(!showing())return;
        if(firstRun&&(!completed||page!=TutorialContent.pages()))return;
        if(completed&&firstRun&&!a.destroyed&&!a.getPreferences(0).getBoolean(COMPLETE_KEY,false))a.getPreferences(0).edit().putBoolean(COMPLETE_KEY,true).apply();
        dispose();if(!a.destroyed&&a.visible){reveal(a.root,-1);a.resumeRuntime();}
    }
    void dispose(){finishTransition();requested=false;if(root!=null&&root.getParent() instanceof ViewGroup)((ViewGroup)root.getParent()).removeView(root);root=null;scroll=null;parent=null;logo=null;if(a.root!=null)a.root.setVisibility(View.VISIBLE);}
}
