package top.rongshangs.lumacurve.refactor;

import android.content.*;
import android.content.pm.*;
import android.text.*;
import android.widget.*;
import java.util.*;

/** Installed-app browsing runs only while editing; no app process/service stays alive. */
final class ApplicationBrightnessSettings {
    final MainActivity h;final LinearLayout selected;final TextView status;
    String signature="";
    final Map<String,String> labels=new HashMap<>();
    ApplicationBrightnessSettings(MainActivity host,LinearLayout parent){
        h=host;LinearLayout card=h.card(parent);card.addView(h.text("应用内暂停自动亮度",18,h.INK));
        card.addView(h.text("名单应用在主屏前台时暂停自动亮度，切到其他应用后恢复。进入前已是手动模式时不接管；你主动开启自动亮度后，本次应用停留期间不再关闭。",12,h.MUTED));
        card.addView(h.text("使用系统前台切换事件，不增加常驻进程，不定时扫描应用。保存并应用后生效。",12,h.MUTED));
        status=h.text("尚未选择应用",13,h.BLUE);card.addView(status);
        h.button("选择应用",this::picker,card);h.button("按包名添加",this::addPackage,card);
        selected=h.column();card.addView(selected);
    }
    String label(String pkg){String value=labels.get(pkg);if(value!=null)return value;try{value=h.getPackageManager().getApplicationLabel(h.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception absent){value=pkg;}labels.put(pkg,value);return value;}
    void refresh(){
        boolean supported=h.runtime!=null&&h.runtime.optBoolean("app_auto_supported");
        String next=supported+":"+h.controls.manualApps.toString();if(next.equals(signature))return;signature=next;
        status.setText(h.tr(supported?"已选择 ":"前台接口尚未就绪 · 已选择 ")+h.controls.manualApps.size()+h.tr(" 个应用"));selected.removeAllViews();
        for(String pkg:new ArrayList<>(h.controls.manualApps)){LinearLayout row=h.row(selected);TextView name=h.text(label(pkg)+"\n"+pkg,13,h.INK);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));h.compact("移除",()->{h.controls.manualApps.remove(pkg);h.dirty=true;h.drawCurve();},row);}
    }
    void picker(){
        if(h.runtime==null||!h.runtime.optBoolean("app_auto_supported")){h.show("本机前台应用事件接口尚未兼容");return;}
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String,String> installed=new TreeMap<>();for(ResolveInfo info:h.getPackageManager().queryIntentActivities(launcher,0))if(info.activityInfo!=null){String pkg=info.activityInfo.packageName;if(BrightnessControlOptions.validPackage(pkg)){String label=info.loadLabel(h.getPackageManager()).toString();installed.put(pkg,label);labels.put(pkg,label);}}
        List<String> order=new ArrayList<>(installed.keySet());Collections.sort(order,(a,b)->installed.get(a).compareToIgnoreCase(installed.get(b)));
        LinearLayout body=h.column();EditText search=h.input("搜索应用名称或包名");body.addView(search,new LinearLayout.LayoutParams(-1,h.dp(48)));
        ScrollView scroll=new ScrollView(h);LinearLayout choices=h.column();scroll.addView(choices);body.addView(scroll,new LinearLayout.LayoutParams(-1,h.dp(340)));
        Runnable draw=()->{choices.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);for(String pkg:order){String title=installed.get(pkg);if(!(title+" "+pkg).toLowerCase(Locale.ROOT).contains(q))continue;CheckBox item=new CheckBox(h);item.setText(title+"\n"+pkg);item.setTextSize(13);item.setTextColor(h.INK);item.setMinimumHeight(h.dp(64));item.setChecked(h.controls.manualApps.contains(pkg));item.setOnCheckedChangeListener((v,on)->{if(on&&h.controls.manualApps.size()>=64){item.setChecked(false);h.show("应用名单最多 64 项");return;}if(on)h.controls.manualApps.add(pkg);else h.controls.manualApps.remove(pkg);h.dirty=true;h.drawCurve();});choices.addView(item,new LinearLayout.LayoutParams(-1,-2));}};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){draw.run();}public void afterTextChanged(Editable s){}});draw.run();
        android.app.AlertDialog dialog=h.dialog("选择暂停自动亮度的应用",body);dialog.show();h.dialogButton(dialog,"完成",null,true);
    }
    void addPackage(){
        EditText input=h.input("com.example.app");LinearLayout body=h.column();body.addView(input,new LinearLayout.LayoutParams(-1,h.dp(48)));android.app.AlertDialog d=h.dialog("按包名添加",body);d.show();h.dialogButton(d,"取消",null,false);h.dialogButton(d,"添加",()->{String pkg=input.getText().toString().trim();if(!BrightnessControlOptions.validPackage(pkg)){h.show("应用包名无效");return;}if(h.controls.manualApps.size()>=64&&!h.controls.manualApps.contains(pkg)){h.show("应用名单最多 64 项");return;}h.controls.manualApps.add(pkg);h.dirty=true;h.drawCurve();},true);
    }
}
