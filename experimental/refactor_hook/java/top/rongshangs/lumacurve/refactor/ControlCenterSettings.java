package top.rongshangs.lumacurve.refactor;

import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

/** Explicit radio cards. The partition's diagram, limit and return behavior live inside it. */
final class ControlCenterSettings {
    final MainActivity a;final LinearLayout root,partitionBody,returnPolicy,limitHeader,limitInputRow;final TextView heading,availability,limitLabel;
    final int[] modes=ControlCenterStatus.visibleModes();final LinearLayout[] options=new LinearLayout[3];
    final TextView[] marks=new TextView[3],badges=new TextView[3];final Button[] returns=new Button[2];final Button setLimit,deviceLimit;
    final ControlCenterDemo demo;final SeekBar maximumSlider;final EditText maximumInput;
    boolean updating;int maximum,ordinary;
    ControlCenterSettings(MainActivity a,LinearLayout parent){
        this.a=a;root=a.card(parent);root.setTag("node_mapping");root.setFocusable(true);heading=a.text("控制中心亮度条映射",18,a.INK);root.addView(heading);
        String[] summaries={"系统原有调节方式","全段节点控制 · 不参与记忆","普通 75% + 增强 25%"};
        for(int i=0;i<modes.length;i++){
            final int mode=modes[i];LinearLayout option=a.column();options[i]=option;option.setPadding(a.dp(12),a.dp(12),a.dp(12),a.dp(12));option.setMinimumHeight(a.dp(58));option.setClickable(true);option.setFocusable(true);
            LinearLayout header=a.column();header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setBaselineAligned(false);option.addView(header);
            marks[i]=a.text("○",24,a.BLUE);marks[i].setGravity(Gravity.CENTER);marks[i].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);header.addView(marks[i],new LinearLayout.LayoutParams(a.dp(32),a.dp(36)));
            LinearLayout words=a.column();header.addView(words,new LinearLayout.LayoutParams(0,-2,1));words.addView(a.text(ControlCenterStatus.label(mode),15,a.INK));words.addView(a.text(summaries[i],12,a.MUTED));
            badges[i]=a.text("已选中",12,a.BLUE);badges[i].setGravity(Gravity.CENTER);header.addView(badges[i],new LinearLayout.LayoutParams(a.dp(48),-2));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=a.dp(10);root.addView(option,size);
            option.setOnClickListener(v->{if(a.busy||!ControlCenterStatus.selectable(a.runtime,mode,a.controls.nodeSliderMode))return;if(a.controls.nodeSliderMode!=mode){a.controls.nodeSliderMode=mode;a.dirty=true;a.drawCurve();}});
        }
        partitionBody=a.column();options[2].addView(partitionBody);demo=new ControlCenterDemo(a);LinearLayout.LayoutParams preview=new LinearLayout.LayoutParams(-1,a.dp(252));preview.topMargin=a.dp(12);partitionBody.addView(demo,preview);
        limitHeader=a.column();limitHeader.setOrientation(LinearLayout.HORIZONTAL);limitHeader.setGravity(Gravity.CENTER_VERTICAL);limitHeader.setBaselineAligned(false);LinearLayout.LayoutParams headerSize=new LinearLayout.LayoutParams(-1,-2);headerSize.topMargin=a.dp(10);partitionBody.addView(limitHeader,headerSize);
        limitLabel=a.text("最高节点",14,a.INK);limitHeader.addView(limitLabel,new LinearLayout.LayoutParams(0,-2,1));
        maximumSlider=new SeekBar(a);partitionBody.addView(maximumSlider,new LinearLayout.LayoutParams(-1,a.dp(36)));
        maximumSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){}public void onProgressChanged(SeekBar v,int value,boolean user){if(user&&!updating&&!a.busy&&maximum>ordinary){a.controls.partitionMaximum=ordinary+1+value;a.dirty=true;a.drawCurve();}}});
        LinearLayout input=a.column();limitInputRow=input;input.setOrientation(LinearLayout.HORIZONTAL);input.setGravity(Gravity.CENTER_VERTICAL);input.setBaselineAligned(false);partitionBody.addView(input);
        maximumInput=a.input("输入节点数值");maximumInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.addView(maximumInput,new LinearLayout.LayoutParams(0,-2,1));
        deviceLimit=a.action("使用设备上限",()->{if(!a.busy){a.controls.partitionMaximum=0;maximumInput.clearFocus();a.dirty=true;a.drawCurve();}});deviceLimit.setTextSize(12);deviceLimit.setMinHeight(a.dp(40));deviceLimit.setMinimumHeight(a.dp(40));deviceLimit.setSingleLine(false);deviceLimit.setMaxLines(Integer.MAX_VALUE);limitHeader.addView(deviceLimit,new LinearLayout.LayoutParams(-2,-2));
        setLimit=a.action("设定",()->{if(a.busy)return;try{int value=Integer.parseInt(maximumInput.getText().toString());if(value<=ordinary||value>maximum)throw new IllegalArgumentException();a.controls.partitionMaximum=value;a.dirty=true;maximumInput.clearFocus();a.drawCurve();}catch(Exception invalid){maximumInput.setError(a.tr("请输入范围内的节点值"));}});setLimit.setMinHeight(a.dp(44));setLimit.setMinimumHeight(a.dp(44));setLimit.setMinimumWidth(0);LinearLayout.LayoutParams applySize=new LinearLayout.LayoutParams(a.dp(64),-2);applySize.leftMargin=a.dp(8);input.addView(setLimit,applySize);
        returnPolicy=a.column();returnPolicy.setPadding(0,a.dp(14),0,0);partitionBody.addView(returnPolicy);returnPolicy.addView(a.text("回到普通区间",14,a.INK));
        String[] labels={"恢复自动亮度","继续保持手动"};for(int i=0;i<2;i++){final boolean automatic=i==0;returns[i]=a.action(labels[i],()->{if(!a.busy&&a.controls.partitionRestoreAuto!=automatic){a.controls.partitionRestoreAuto=automatic;a.dirty=true;a.drawCurve();}});returns[i].setSingleLine(false);returns[i].setMaxLines(Integer.MAX_VALUE);LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=a.dp(6);returnPolicy.addView(returns[i],size);}
        availability=a.text("",12,a.MUTED);root.addView(availability);
        refresh();
    }
    void refresh(){
        boolean partition=a.controls.nodeSliderMode==4;partitionBody.setVisibility(partition?View.VISIBLE:View.GONE);returnPolicy.setVisibility(partition?View.VISIBLE:View.GONE);
        for(int i=0;i<modes.length;i++){boolean chosen=modes[i]==a.controls.nodeSliderMode;options[i].setSelected(chosen);options[i].setEnabled(!a.busy&&ControlCenterStatus.selectable(a.runtime,modes[i],a.controls.nodeSliderMode));options[i].setAlpha(options[i].isEnabled()?1:.5f);GradientDrawable background=a.background(chosen?0xffe5edff:0xfff2f4f8,16);background.setStroke(a.dp(chosen?2:1),chosen?a.BLUE:((a.MUTED&0xffffff)|0x55000000));options[i].setBackground(background);marks[i].setText(chosen?"●":"○");badges[i].setVisibility(chosen?View.VISIBLE:View.GONE);options[i].setContentDescription(a.tr(ControlCenterStatus.label(modes[i]))+(chosen?" · "+a.tr("已选中"):""));}
        String[] labels={"恢复自动亮度","继续保持手动"};for(int i=0;i<2;i++){boolean chosen=a.controls.partitionRestoreAuto==(i==0);returns[i].setSelected(chosen);returns[i].setEnabled(!a.busy);returns[i].setText((chosen?"✓ ":"")+a.tr(labels[i]));returns[i].setTextColor(chosen?a.BG:a.INK);returns[i].setBackground(a.ripple(chosen?a.BLUE:0xffeef2fa,12));}
        maximum=ControlCenterStatus.maximum(a.runtime,a.rootFeatureReport);double fraction=ControlCenterStatus.ordinary(a.runtime);ordinary=maximum>10&&Double.isFinite(fraction)?(int)Math.ceil(fraction*maximum):0;
        boolean known=maximum>ordinary&&ordinary>=10;int value=a.controls.partitionMaximum==0?maximum:Math.min(a.controls.partitionMaximum,maximum);updating=true;maximumSlider.setMax(known?maximum-ordinary-1:0);maximumSlider.setProgress(known?Math.max(0,value-ordinary-1):0);updating=false;
        maximumSlider.setEnabled(known&&!a.busy);maximumInput.setEnabled(known&&!a.busy);setLimit.setEnabled(known&&!a.busy);deviceLimit.setEnabled(known&&!a.busy);if(!maximumInput.hasFocus())maximumInput.setText(known?String.valueOf(value):"");limitLabel.setText(a.tr("最高节点"));
        demo.data(known?ordinary:0,known?value:0,a.controls.partitionRestoreAuto);
        String detail="";if(a.controls.nodeSliderMode>=3){String key=a.controls.nodeSliderMode==4?"control_center_partition_supported":"control_center_raw_supported";if(a.runtime==null||!a.runtime.optBoolean(key))detail="本机相关接口暂未就绪，已有选择保留；可查看详细兼容路线。";else detail=a.runtime.optString("control_center_node_detail");if(partition&&known&&value<=ordinary)detail="最高节点必须高于普通区间上限";}
        availability.setText(a.tr(detail));availability.setVisibility(detail.isEmpty()?View.GONE:View.VISIBLE);
    }
}
