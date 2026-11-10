package top.rongshangs.lumacurve.refactor;

import android.widget.*;
import org.json.*;

/** Effective native isolation is displayed without changing the saved module preference. */
final class AppIsolationSettings {
    final MainActivity a;final Switch choice;final TextView state;boolean updating;
    AppIsolationSettings(MainActivity host,LinearLayout parent){
        a=host;LinearLayout box=a.card(parent);choice=new Switch(a);choice.setText(a.tr("隔离应用临时亮度"));choice.setTextSize(16);choice.setMinimumHeight(a.dp(48));box.addView(choice,new LinearLayout.LayoutParams(-1,-2));
        box.addView(a.text("阻止应用窗口的临时亮度调整同步成系统亮度偏好。例如视频播放、付款码等页面的临时调亮或调暗（非控制中心操作）。",13,a.MUTED));
        box.addView(a.text("离开这些页面后恢复系统原有的亮度调节。你在控制中心的主动调整仍按原有设置处理；这里不记忆或延续应用的临时值。",12,a.MUTED));
        state=a.text("等待兼容检测",13,a.BLUE);state.setPadding(0,a.dp(12),0,0);box.addView(state);
        choice.setOnCheckedChangeListener((v,on)->{if(updating)return;a.controls.appIsolation=on;a.dirty=true;a.drawCurve();});
    }
    void refresh(){
        boolean supported=a.runtime!=null&&a.runtime.optBoolean("app_brightness_isolation_supported");JSONObject report=a.runtime==null?null:a.runtime.optJSONObject("app_brightness_isolation");boolean nativeIsolation=supported&&report!=null&&report.optBoolean("native_isolated");
        updating=true;try{choice.setChecked(nativeIsolation||a.controls.appIsolation);choice.setEnabled(!a.busy&&!nativeIsolation&&(supported||a.controls.appIsolation));}finally{updating=false;}
        state.setText(a.tr(nativeIsolation?"系统原生已隔离，无需模块重复处理":!supported?"接口尚未就绪，已保存的选择保留":a.controls.appIsolation?"已启用窗口临时亮度同步隔离":"沿用系统窗口亮度同步行为"));
    }
}
