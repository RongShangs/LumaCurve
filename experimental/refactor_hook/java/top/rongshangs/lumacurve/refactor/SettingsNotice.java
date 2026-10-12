package top.rongshangs.lumacurve.refactor;

import android.app.AlertDialog;
import android.os.SystemClock;
import android.widget.*;

/** Acknowledgement is local to this installation and is never imported with brightness settings. */
final class SettingsNotice {
    static final String ACK_KEY="settings_notice_acknowledged_v1";
    static final String INTRO="HyperLux 是基于 HyperOS 4 的亮度个性化设置工具。安装后会自动读取本机原有的亮度配置；未修改并应用设置时，沿用系统默认亮度策略。";
    static final String CONFIG="每个人对屏幕亮度的感受都不同。照搬别人的配置，未必适合自己，就像买衣服不能直接照着别人的尺码买。\n\n配置导入、导出是为了备份和恢复自己的偏好，不提倡用户间分享配置，更不提倡跨机型套用。";
    static final String USAGE="• 觉得某种光线下亮度不合适：调整基础亮度曲线（蓝色曲线）。\n\n• 日常使用中亮度不舒服：随时调整控制中心的亮度条。开启手动记忆后，系统会根据你的调整学习偏好，显示为绿色曲线。\n\n• 户外屏幕太暗：按需开启户外高亮，并设置适合自己的增强幅度和亮度上限。\n\n• 暗光下亮度不稳定：调整暗光稳定或暗光锁定。\n\n• 温控相关设置只作用于显示亮度链路，不影响系统其他部分的温控。";
    static final String GUIDANCE="状态页提供实时说明和快捷跳转，各项设置按用途分类，并配有说明、详细参数和预设。这些设计是为了帮助你逐步调出适合自己的亮度体验；预设也应结合实际感受选择。";
    static final String CLOSING="请根据自己的使用感受调整，不必向别人索要配置。";
    final MainActivity a;
    AlertDialog current;Button confirm;NoticeCountdown countdown;Runnable afterDismiss;
    final Runnable update=()->refreshCountdown();
    SettingsNotice(MainActivity host){a=host;}
    boolean showing(){return current!=null&&current.isShowing();}
    void paragraph(LinearLayout body,String value){TextView text=a.text(value,14,a.MUTED);text.setLineSpacing(a.dp(3),1);body.addView(text);}
    void heading(LinearLayout body,String value){TextView title=a.text(value,16,a.INK);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(0,a.dp(14),0,a.dp(6));body.addView(title);}
    void showIfNeeded(){
        if(a.destroyed||!a.visible||a.isFinishing()||a.page!=1||current!=null||a.getPreferences(0).getBoolean(ACK_KEY,false)||!a.openDialogs.isEmpty()||a.permissionDialogVisible||a.legacyDialogVisible||a.updateDialog!=null)return;
        LinearLayout body=a.column();paragraph(body,INTRO);heading(body,"请使用适合自己的配置");paragraph(body,CONFIG);heading(body,"推荐的使用方式");paragraph(body,USAGE);heading(body,"按实际感受逐步调整");paragraph(body,GUIDANCE);paragraph(body,CLOSING);
        AlertDialog d=a.dialog("用户需知",body);current=d;d.setCancelable(false);d.setCanceledOnTouchOutside(false);
        d.setOnDismissListener(v->{a.ui.removeCallbacks(update);current=null;confirm=null;countdown=null;Runnable pending=afterDismiss;afterDismiss=null;if(!a.destroyed){if(pending!=null)pending.run();a.offerLegacyModules();a.offerUpdate();}});
        d.show();countdown=new NoticeCountdown(SystemClock.elapsedRealtime());LinearLayout actions=(LinearLayout)d.getWindow().getDecorView().findViewWithTag("actions");
        confirm=a.compact("确定",()->{if(countdown==null||countdown.remaining(SystemClock.elapsedRealtime())!=0)return;a.getPreferences(0).edit().putBoolean(ACK_KEY,true).apply();d.dismiss();},actions);
        confirm.setTextColor(a.darkTheme?a.BG:android.graphics.Color.WHITE);confirm.setBackground(a.ripple(a.BLUE,12));refreshCountdown();
    }
    void refreshCountdown(){
        a.ui.removeCallbacks(update);if(!showing()||confirm==null||countdown==null||a.destroyed)return;
        int seconds=countdown.remaining(SystemClock.elapsedRealtime());confirm.setEnabled(seconds==0);confirm.setAlpha(seconds==0?1:.55f);confirm.setText(a.tr("确定")+(seconds==0?"":" ("+seconds+" s)"));
        if(seconds>0&&a.visible)a.ui.postDelayed(update,1000);
    }
    void pause(){a.ui.removeCallbacks(update);}
    void resume(){if(showing())refreshCountdown();else showIfNeeded();}
}
