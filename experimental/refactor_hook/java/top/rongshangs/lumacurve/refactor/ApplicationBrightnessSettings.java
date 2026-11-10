package top.rongshangs.lumacurve.refactor;

import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** Three mutually exclusive lists; old pause packages keep their original configuration key. */
final class ApplicationBrightnessSettings {
    final MainActivity h;final TextView status;final Switch lock,reboot;final Button pause,individual,add;final LinearLayout groups;boolean updating;String groupSignature="";
    ApplicationBrightnessSettings(MainActivity host,LinearLayout parent){
        h=host;LinearLayout intro=h.card(parent);intro.addView(h.text("应用独立亮度",18,h.INK));
        intro.addView(h.text("暂停名单沿用进入时的亮度；独立名单每个应用分别记忆；同一分组共用记忆。独立应用在前台时使用固定亮度，离开后恢复此前系统调节。每个应用只能属于一个名单或分组。",13,h.MUTED));
        intro.addView(h.text("通过控制中心的普通亮度调节更新独立值；应用窗口、付款码和 HDR 的临时提亮不进入记忆。尚未记忆时沿用系统亮度，用户主动开启自动亮度后尊重本次选择。保存并应用后生效。",12,h.MUTED));
        lock=new Switch(h);lock.setText(h.tr("跨锁屏保留独立亮度"));lock.setTextSize(15);lock.setMinimumHeight(h.dp(48));intro.addView(lock,new LinearLayout.LayoutParams(-1,-2));
        reboot=new Switch(h);reboot.setText(h.tr("跨重启保留独立亮度"));reboot.setTextSize(15);reboot.setMinimumHeight(h.dp(48));intro.addView(reboot,new LinearLayout.LayoutParams(-1,-2));
        intro.addView(h.text("默认锁屏清除。跨重启保留同时需要跨锁屏保留；记忆只在对应应用进入前台时使用，锁屏与桌面不接管。",12,h.MUTED));
        lock.setOnCheckedChangeListener((v,on)->{if(updating)return;h.controls.independentKeepLock=on;if(!on)h.controls.independentKeepReboot=false;h.dirty=true;h.drawCurve();});
        reboot.setOnCheckedChangeListener((v,on)->{if(updating)return;h.controls.independentKeepReboot=on;if(on)h.controls.independentKeepLock=true;h.dirty=true;h.drawCurve();});
        status=h.text("等待兼容检测",12,h.BLUE);intro.addView(status);
        LinearLayout paused=h.card(parent);paused.addView(h.text("暂停自动亮度名单",18,h.INK));paused.addView(h.text("暂停期间不自动记忆独立值，所有名单应用离开可见区域后恢复。",12,h.MUTED));pause=entry("pause","应用管理",paused);
        LinearLayout own=h.card(parent);own.addView(h.text("独立亮度名单",18,h.INK));own.addView(h.text("每个应用拥有各自的固定亮度记忆，普通页面同样生效。",12,h.MUTED));individual=entry("individual","应用管理",own);
        LinearLayout grouped=h.card(parent);grouped.addView(h.text("分组独立亮度名单",18,h.INK));grouped.addView(h.text("同组应用共享固定亮度，最多创建 3 个组。全部名单合计最多 64 个应用。",12,h.MUTED));groups=h.column();grouped.addView(groups);add=h.button("新增分组",()->edit(null),grouped);
    }
    Button entry(String id,String title,LinearLayout parent){Button b=h.button(title,()->h.openApplicationList(id),parent);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setTextColor(h.INK);b.setBackground(h.ripple(0xfff2f4f8,14));return b;}
    void refresh(){
        updating=true;try{lock.setChecked(h.controls.independentKeepLock);reboot.setChecked(h.controls.independentKeepReboot);lock.setEnabled(!h.busy);reboot.setEnabled(!h.busy);}finally{updating=false;}
        pause.setText(h.tr("应用管理")+"   "+h.controls.manualApps.size()+h.tr("个"));individual.setText(h.tr("应用管理")+"   "+h.controls.independentApps.size()+h.tr("个"));pause.setEnabled(!h.busy);individual.setEnabled(!h.busy);
        boolean pauseReady=h.runtime!=null&&h.runtime.optBoolean("app_auto_supported"),independentReady=h.runtime!=null&&h.runtime.optBoolean("app_independent_supported");status.setText(h.tr(independentReady?"应用独立亮度接口已就绪":pauseReady?"暂停名单可用；独立亮度接口尚未就绪，保存设置保留":"前台应用接口尚未就绪，保存设置保留"));
        String signature=h.controls.independentSignature()+h.busy;if(!signature.equals(groupSignature)){groupSignature=signature;groups.removeAllViews();for(BrightnessControlOptions.AppGroup group:h.controls.independentGroups){String id=group.id;LinearLayout row=h.row(groups);TextView name=h.text(group.name,16,h.INK);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));h.compact("重命名",()->edit(id),row).setEnabled(!h.busy);h.compact("删除",()->remove(id),row).setEnabled(!h.busy);Button b=entry(id,"应用管理",groups);b.setText(h.tr("应用管理")+"   "+group.apps.size()+h.tr("个"));b.setEnabled(!h.busy);}}
        add.setEnabled(!h.busy&&h.controls.independentGroups.size()<3);
    }
    BrightnessControlOptions.AppGroup find(String id){for(BrightnessControlOptions.AppGroup group:h.controls.independentGroups)if(group.id.equals(id))return group;return null;}
    void edit(String id){
        BrightnessControlOptions.AppGroup group=find(id);
        if(h.busy||id!=null&&group==null||id==null&&h.controls.independentGroups.size()>=3)return;EditText input=h.input("分组名称");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});input.setSingleLine(true);input.setText(group==null?h.tr("分组")+(h.controls.independentGroups.size()+1):group.name);LinearLayout body=h.column();body.addView(input,new LinearLayout.LayoutParams(-1,h.dp(48)));AlertDialog dialog=h.dialog(group==null?"新增分组":"重命名",body);dialog.show();h.dialogButton(dialog,"取消",null,false);h.dialogButton(dialog,"确定",()->{try{BrightnessControlOptions.AppGroup current=find(id);if(id!=null&&current==null){h.drawCurve();return;}if(id==null&&h.controls.independentGroups.size()>=3){h.drawCurve();return;}String name=input.getText().toString().trim();BrightnessControlOptions.AppGroup checked=new BrightnessControlOptions.AppGroup(id==null?UUID.randomUUID().toString():id,name);if(id==null)h.controls.independentGroups.add(checked);else current.name=checked.name;h.dirty=true;h.drawCurve();}catch(Exception invalid){h.show(invalid.getMessage());}},true);
    }
    void remove(String id){if(h.busy||find(id)==null)return;LinearLayout body=h.column();body.addView(h.text("删除后，该组应用将不再使用独立亮度。",14,h.INK));AlertDialog dialog=h.dialog("删除分组",body);dialog.show();h.dialogButton(dialog,"取消",null,false);h.dialogButton(dialog,"删除",()->{if(h.controls.independentGroups.removeIf(group->group.id.equals(id)))h.dirty=true;h.drawCurve();},true);}
}
