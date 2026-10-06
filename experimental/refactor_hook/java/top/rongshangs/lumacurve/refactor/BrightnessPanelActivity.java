package top.rongshangs.lumacurve.refactor;
import android.app.Activity;
import android.os.Bundle;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import java.util.function.Consumer;
/** Dedicated popup with one coordinated alpha/backdrop animation, no scale or translation. */
public final class BrightnessPanelActivity extends Activity {
 RawBrightnessPanel panel;ValueAnimator fade;Consumer<Boolean> blurListener;boolean blurAvailable,dismissing;float opacity;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setWindowAnimations(0);
  window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_BLUR_BEHIND);window.setDimAmount(0);
  window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  WindowManager wm=getSystemService(WindowManager.class);blurListener=enabled->{blurAvailable=enabled;backdrop();};wm.addCrossWindowBlurEnabledListener(getMainExecutor(),blurListener);blurAvailable=wm.isCrossWindowBlurEnabled();
  panel=new RawBrightnessPanel(this,this::dismissPopup);panel.root.setAlpha(0);setContentView(panel.root);panel.start();animate(1,null);
 }
 void backdrop(){WindowManager.LayoutParams p=getWindow().getAttributes();p.setBlurBehindRadius(blurAvailable?Math.round(28*getResources().getDisplayMetrics().density*opacity):0);p.dimAmount=(blurAvailable?.14f:.24f)*opacity;getWindow().setAttributes(p);}
 void animate(float target,Runnable end){if(fade!=null){fade.removeAllListeners();fade.cancel();}if(!ValueAnimator.areAnimatorsEnabled()){opacity=target;panel.root.setAlpha(target);backdrop();if(end!=null)end.run();return;}
  fade=ValueAnimator.ofFloat(opacity,target);fade.setDuration(180);fade.addUpdateListener(frame->{opacity=(Float)frame.getAnimatedValue();panel.root.setAlpha(opacity);backdrop();});
  fade.addListener(new android.animation.AnimatorListenerAdapter(){public void onAnimationEnd(android.animation.Animator a){if(end!=null)end.run();}});fade.start();
 }
 void dismissPopup(){if(dismissing)return;dismissing=true;if(panel!=null)panel.close();animate(0,this::finish);}
 @Override public void onBackPressed(){dismissPopup();}
 @Override protected void onPause(){super.onPause();if(panel!=null)panel.close();}
 @Override protected void onStop(){super.onStop();if(!isChangingConfigurations())finish();}
 @Override protected void onDestroy(){if(fade!=null){fade.removeAllListeners();fade.cancel();}if(panel!=null)panel.close();if(blurListener!=null)getSystemService(WindowManager.class).removeCrossWindowBlurEnabledListener(blurListener);super.onDestroy();}
}
