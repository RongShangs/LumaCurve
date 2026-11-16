package top.rongshangs.lumacurve.refactor;

import android.widget.*;
import android.view.*;
import org.json.*;

/** One user choice; native sync isolation and WM filtering retain independent capabilities. */
final class AppIsolationSettings {
    final MainActivity a;final LinearLayout rules;final Switch choice;final TextView state;boolean updating;
    AppIsolationSettings(MainActivity host,LinearLayout parent){
        a=host;LinearLayout box=a.card(parent);box.setTag("suspended_window_brightness");choice=new Switch(a);choice.setText(a.tr("隔离应用临时亮度"));choice.setTextSize(16);choice.setMinimumHeight(a.dp(48));box.addView(choice,new LinearLayout.LayoutParams(-1,-2));
        box.addView(a.text("隔离视频、付款码等非控制中心操作的临时亮度，避免同步成系统亮度偏好。",13,a.MUTED));
        rules=a.column();rules.setBackground(a.background(0xfff4f5f7,14));rules.setClipToOutline(true);LinearLayout.LayoutParams tableSize=new LinearLayout.LayoutParams(-1,-2);tableSize.topMargin=a.dp(12);tableSize.bottomMargin=a.dp(8);box.addView(rules,tableSize);
        rule("窗口状态","处理方式",true);
        String[][] rows={{"正常小窗","保留临时亮度请求"},{"最小浮窗、贴边挂起","忽略该窗口请求，交回系统或其他有效窗口"},{"窗口关闭、完全进入后台","撤销该窗口请求"},{"控制中心主动调整","用户操作优先"}};
        for(String[] row:rows){View divider=new View(a);divider.setBackgroundColor(a.themed(0xffe5eaf2));rules.addView(divider,new LinearLayout.LayoutParams(-1,a.dp(1)));rule(row[0],row[1],false);}
        state=a.text("等待兼容检测",13,a.BLUE);state.setPadding(0,a.dp(12),0,0);box.addView(state);
        choice.setOnCheckedChangeListener((v,on)->{if(updating)return;a.controls.setIsolation(on);a.dirty=true;a.drawCurve();});
    }
    void rule(String state,String behavior,boolean header){
        LinearLayout row=a.row(rules);row.setGravity(Gravity.TOP);row.setMinimumHeight(a.dp(header?42:52));if(header)row.setBackgroundColor(a.themed(0xffe5eaf2));
        TextView label=a.text(state,header?12:13,header?a.MUTED:a.INK),description=a.text(behavior,header?12:13,header?a.MUTED:a.INK);label.setGravity(Gravity.START|Gravity.TOP);description.setGravity(Gravity.START|Gravity.TOP);label.setPadding(a.dp(12),a.dp(10),a.dp(8),a.dp(10));description.setPadding(a.dp(8),a.dp(10),a.dp(12),a.dp(10));row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.addView(description,new LinearLayout.LayoutParams(0,-2,1.6f));
    }
    void refresh(){
        boolean supported=a.runtime!=null&&a.runtime.optBoolean("app_brightness_isolation_supported"),windowSupported=a.runtime!=null&&a.runtime.optBoolean("suspended_window_brightness_supported");JSONObject report=a.runtime==null?null:a.runtime.optJSONObject("app_brightness_isolation");boolean nativeIsolation=supported&&report!=null&&report.optBoolean("native_isolated"),requested=a.controls.isolationRequested();
        updating=true;try{choice.setChecked(requested);choice.setEnabled(!a.busy&&(supported||windowSupported||requested));}finally{updating=false;}
        String note=!requested?(nativeIsolation?"模块隔离未开启；系统原生的亮度同步隔离仍然保留":""):!supported&&!windowSupported?"接口尚未就绪，已保存的选择保留":!windowSupported?(nativeIsolation?"系统原生隔离亮度同步；小窗状态处理暂不可用":"同步隔离已开启；小窗状态处理暂不可用"):!supported?"小窗状态处理已开启；同步隔离暂不可用":"";
        state.setText(a.tr(note));state.setVisibility(note.isEmpty()?View.GONE:View.VISIBLE);
    }
}
