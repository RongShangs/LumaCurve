package top.rongshangs.lumacurve.refactor;

import android.view.Gravity;
import android.widget.*;

/** Tile-only ceiling, independent from CC partition and outdoor limits. */
final class ManualPanelLimitSettings {
    final MainActivity a;final TextView label;final SeekBar slider;final EditText input;final Button apply,reset;
    boolean updating;int maximum;
    ManualPanelLimitSettings(MainActivity a,LinearLayout parent){
        this.a=a;LinearLayout header=a.column();header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setBaselineAligned(false);parent.addView(header);
        label=a.text("磁贴最高节点",14,a.INK);header.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        reset=a.action("使用设备上限",this::resetMaximum);reset.setTextSize(12);reset.setMinHeight(a.dp(40));reset.setMinimumHeight(a.dp(40));header.addView(reset,new LinearLayout.LayoutParams(-2,-2));
        slider=new SeekBar(a);parent.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(36)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int value,boolean user){if(user&&!updating&&!a.busy&&maximum>=10){a.controls.manualMaximum=value+10;a.dirty=true;a.drawCurve();}}});
        LinearLayout row=a.column();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);parent.addView(row);
        input=a.input("输入节点数值");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);row.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        apply=a.action("设定",()->{if(a.busy)return;try{int value=Integer.parseInt(input.getText().toString());if(value<10||value>maximum)throw new IllegalArgumentException();a.controls.manualMaximum=value;input.clearFocus();a.dirty=true;a.drawCurve();}catch(Exception bad){input.setError(a.tr("请输入范围内的节点值"));}});apply.setMinHeight(a.dp(44));apply.setMinimumHeight(a.dp(44));apply.setMinimumWidth(0);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(a.dp(64),-2);size.leftMargin=a.dp(8);row.addView(apply,size);
        parent.addView(a.text("仅限制磁贴面板；控制中心分区和户外高亮上限分别设置。",12,a.MUTED));refresh();
    }
    void resetMaximum(){if(!a.busy){a.controls.manualMaximum=0;input.clearFocus();a.dirty=true;a.drawCurve();}}
    void refresh(){
        maximum=ControlCenterStatus.maximum(a.runtime,a.rootFeatureReport);boolean known=maximum>=10;int value=BrightnessControlOptions.manualCeiling(a.controls.manualMaximum,maximum);
        boolean enabled=known&&!a.busy&&a.controls.manualPanel;
        updating=true;slider.setMax(known?maximum-10:0);slider.setProgress(known?Math.max(0,value-10):0);updating=false;
        slider.setEnabled(enabled);input.setEnabled(enabled);apply.setEnabled(enabled);reset.setEnabled(!a.busy);
        if(!input.hasFocus())input.setText(known?String.valueOf(value):a.controls.manualMaximum>0?String.valueOf(a.controls.manualMaximum):"");
        label.setText(a.tr("磁贴最高节点")+(known?" · "+value+" / "+maximum:""));
    }
}
