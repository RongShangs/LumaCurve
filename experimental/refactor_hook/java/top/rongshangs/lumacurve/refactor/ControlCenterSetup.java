package top.rongshangs.lumacurve.refactor;

import android.view.Gravity;
import android.widget.*;
import org.json.*;

/** On-demand scope guidance and user-confirmed SystemUI restart. Never changes scope/config. */
final class ControlCenterSetup {
    static final int SETTINGS_GROUP=8;
    final MainActivity a;final TextView status;final Button restart;JSONObject report;boolean checking,pendingPrompt;String lastPrompt="",promptSelection="";
    static boolean mappingChanged(JSONObject before,JSONObject after){
        if(after==null||after.optInt("control_center_node_mode",0)<=0)return false;
        if(before==null)return true;
        return before.optInt("control_center_node_mode",0)!=after.optInt("control_center_node_mode",0)||before.optInt("control_center_partition_maximum",0)!=after.optInt("control_center_partition_maximum",0)||before.optBoolean("control_center_partition_restore_auto",true)!=after.optBoolean("control_center_partition_restore_auto",true);
    }
    static boolean changedPayload(JSONObject before,String encoded){try{return mappingChanged(before,new JSONObject(new String(java.util.Base64.getDecoder().decode(encoded),java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception invalid){return false;}}
    static boolean needsScope(JSONObject report){
        return "missing".equals(report.optString("scope_state"))||report.has("module_enabled")&&!report.isNull("module_enabled")&&!report.optBoolean("module_enabled");
    }
    String selection(){return a.controls.nodeSliderMode+":"+a.controls.partitionMaximum+":"+a.controls.partitionRestoreAuto;}
    ControlCenterSetup(MainActivity a,LinearLayout parent,TextView heading){
        this.a=a;LinearLayout header=a.column();header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setBaselineAligned(false);parent.addView(header);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));restart=a.action("重启系统界面",this::confirmRestart);restart.setTextSize(12);restart.setSingleLine(false);restart.setMaxLines(2);restart.setMinHeight(a.dp(40));restart.setMinimumHeight(a.dp(40));LinearLayout.LayoutParams buttonSize=new LinearLayout.LayoutParams(a.dp(112),-2);buttonSize.leftMargin=a.dp(8);header.addView(restart,buttonSize);
        status=a.text("点击核对系统界面作用域",12,a.BLUE);status.setPadding(0,a.dp(8),0,a.dp(4));status.setOnClickListener(v->guide());parent.addView(status);refresh();
    }
    void refresh(){
        restart.setEnabled(!a.busy&&!checking);
        String scope=report==null?"unknown":report.optString("scope_state"),injection=report==null?"unconfirmed":report.optString("injection_state");
        String label=checking?"正在检查系统界面作用域…":scope.equals("missing")?"未勾选系统界面，点击查看添加方法":report!=null&&!report.isNull("module_enabled")&&!report.optBoolean("module_enabled")?"请在 LSPosed 启用 HyperLux，点击查看设置方法":injection.equals("old_build")?"系统界面仍在使用旧版，可重启后加载":injection.equals("current")?"系统界面已加载 · 点击核对作用域":scope.equals("present")?"已勾选系统界面，尚未确认加载；可尝试重启系统界面":"作用域暂无法确认，点击查看核对方法";
        status.setText(a.tr(label));
    }
    void check(boolean prompt){
        if(prompt)promptSelection=selection();pendingPrompt|=prompt;if(checking||a.destroyed)return;checking=true;refresh();
        a.worker.execute(()->{JSONObject answer;try{JSONObject result=a.request("mapping-setup",null);if(!result.optBoolean("ok"))throw new Exception(result.optString("message"));answer=result.getJSONObject("control_center_setup");}catch(Exception unavailable){answer=new JSONObject();try{answer.put("scope_state","unknown").put("detail",unavailable.toString());}catch(JSONException ignored){}}
            final JSONObject result=answer;a.ui.post(()->{if(a.destroyed)return;report=result;checking=false;boolean explain=pendingPrompt;pendingPrompt=false;refresh();if(!needsScope(report)){lastPrompt="";return;}if(explain&&a.page==1&&a.settingsGroup==SETTINGS_GROUP&&promptSelection.equals(selection())&&a.controls.nodeSliderMode>0&&!promptSelection.equals(lastPrompt)){lastPrompt=promptSelection;guide();}});
        });
    }
    void guide(){a.messageDialog("添加系统界面作用域","在 LSPosed 中打开 HyperLux 的作用域，勾选“系统界面（com.android.systemui）”。\n\n列表中找不到时，请开启“显示系统应用”或取消“隐藏系统应用”。添加后重新检测，再点击“重启系统界面”。","重新检测",()->check(false));}
    void confirmRestart(){
        if(a.busy||checking)return;
        if(report!=null&&(report.optString("scope_state").equals("missing")||report.optString("scope_state").equals("module_missing"))){guide();return;}
        a.messageDialog("重启系统界面","状态栏和控制中心会短暂消失，随后由系统重新启动。请先保存当前设置。","重启",()->{
            if(a.busy||checking||a.destroyed)return;checking=true;refresh();a.worker.execute(()->{String message;boolean ok=false;try{JSONObject result=a.request("restart-system-ui",null);if(!result.optBoolean("ok"))throw new Exception(result.optString("message"));message=result.optString("message");ok=true;}catch(Exception failure){message=failure.getMessage();}final String text=message;final boolean success=ok;a.ui.post(()->{if(a.destroyed)return;checking=false;refresh();a.show(text);if(success)a.ui.postDelayed(()->check(false),1200);});});
        });
    }
}
