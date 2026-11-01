package top.rongshangs.lumacurve.refactor;

import android.widget.*;
import org.json.*;

/** Independent saved preference. Reading this page does not inspect or write the system. */
final class AppIsolationSettings {
    final MainActivity a;final Switch choice;final TextView state;boolean updating;
    AppIsolationSettings(MainActivity host,LinearLayout parent){
        a=host;LinearLayout box=a.card(parent);choice=new Switch(a);choice.setText(a.tr("隔离应用临时亮度"));choice.setTextSize(16);choice.setMinimumHeight(a.dp(48));box.addView(choice,new LinearLayout.LayoutParams(-1,-2));
        box.addView(a.text("应用内临时调亮或调暗（非控制中心操作），退出应用后恢复系统原有的亮度调节。例如视频播放、付款码展示。",13,a.MUTED));
        box.addView(a.text("你在控制中心的主动调整仍按原有设置处理。默认关闭，保存并应用后生效。",12,a.MUTED));
        state=a.text("等待兼容检测",13,a.BLUE);state.setPadding(0,a.dp(12),0,0);box.addView(state);
        choice.setOnCheckedChangeListener((v,on)->{if(updating)return;a.controls.appIsolation=on;a.dirty=true;a.drawCurve();});
    }
    void refresh(){
        boolean supported=a.runtime!=null&&a.runtime.optBoolean("app_brightness_isolation_supported");updating=true;try{choice.setChecked(a.controls.appIsolation);choice.setEnabled(!a.busy&&(supported||a.controls.appIsolation));}finally{updating=false;}
        JSONObject report=a.runtime==null?null:a.runtime.optJSONObject("app_brightness_isolation");if(report==null)report=new JSONObject();String route=report.optString("profile");
        String text=!supported?"接口尚未就绪，已保存的选择保留":route.equals(AppBrightnessIsolation.NATIVE)?"未发现需要隔离的同步链路，沿用系统窗口覆盖":report.optBoolean("user_adjustment_priority")?"已尊重本次控制中心手动调整":report.optBoolean("window_active")?"已检测到应用临时亮度":"支持隔离应用亮度同步";
        state.setText(a.tr(text));
    }
}
