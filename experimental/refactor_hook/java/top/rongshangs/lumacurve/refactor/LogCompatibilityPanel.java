package top.rongshangs.lumacurve.refactor;

import android.view.*;
import android.widget.*;
import android.graphics.Color;
import org.json.*;

/** Readable connection stages, independent feature results, and one full-report destination. */
final class LogCompatibilityPanel {
    final MainActivity a;final ScrollView view;final TextView[] states=new TextView[3];final TextView title,detail;final Button routeEntry;
    LogCompatibilityPanel(MainActivity owner){
        a=owner;view=new ElasticScrollView(a){@Override protected void onMeasure(int w,int h){int available=MeasureSpec.getSize(h);int maximum=Math.max(a.dp(88),Math.round(available*.45f));super.onMeasure(w,MeasureSpec.makeMeasureSpec(maximum,MeasureSpec.AT_MOST));}};view.setFillViewport(false);view.setBackground(a.background(Color.WHITE,18));view.setClipToOutline(true);
        LinearLayout body=a.column();body.setPadding(a.dp(14),a.dp(10),a.dp(14),a.dp(12));view.addView(body);
        TextView heading=a.text("运行与兼容",17,a.INK);heading.setPadding(0,a.dp(2),0,a.dp(6));body.addView(heading);
        title=a.text("",15,a.INK);title.setPadding(0,0,0,a.dp(4));body.addView(title);detail=a.text("",12,a.MUTED);detail.setPadding(0,0,0,a.dp(8));body.addView(detail);
        LinearLayout stages=a.row(body);String[] names={a.english?"Root access":"Root 访问",a.english?"Injection":"系统注入",a.english?"Brightness":"亮度引擎"};
        for(int i=0;i<3;i++){LinearLayout tile=a.column();tile.setGravity(Gravity.CENTER);tile.setPadding(a.dp(4),a.dp(9),a.dp(4),a.dp(9));tile.setBackground(a.ripple(0xffeef3ff,12));tile.setClipToOutline(true);tile.setClickable(true);tile.setFocusable(true);tile.setOnClickListener(v->a.showFeatureChecks());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(a.dp(i==0?0:3),a.dp(4),a.dp(i==2?0:3),a.dp(8));stages.addView(tile,lp);TextView name=a.text(names[i],11,a.MUTED);name.setGravity(Gravity.CENTER);name.setPadding(0,0,0,a.dp(4));tile.addView(name,new LinearLayout.LayoutParams(-1,-2));states[i]=a.text("",13,a.BLUE);states[i].setGravity(Gravity.CENTER);states[i].setMinLines(2);states[i].setMaxLines(2);states[i].setPadding(0,0,0,0);tile.addView(states[i],new LinearLayout.LayoutParams(-1,-2));}
        routeEntry=a.action("详细兼容路线",a::showFeatureChecks);routeEntry.setTextSize(14);routeEntry.setMaxLines(2);routeEntry.setEllipsize(null);routeEntry.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(10));routeEntry.setGravity(Gravity.CENTER);routeEntry.setBackground(a.ripple(0xffe5edff,12));body.addView(routeEntry,new LinearLayout.LayoutParams(-1,-2));update(null,null);
    }
    void update(JSONObject answer,JSONObject rootReport){LogCompatibilityState state=LogCompatibilityState.read(answer,rootReport,a.appFeatureReport,a.english);render(state);}
    void render(LogCompatibilityState state){for(int i=0;i<3;i++){states[i].setText(state.items[i].status);states[i].setTextColor(state.items[i].tone==2?a.themed(0xffbd6a28):state.items[i].tone==1?a.BLUE:a.MUTED);}title.setText(state.title);detail.setText(state.detail);}

    void failed(){render(LogCompatibilityState.read(null,null,a.appFeatureReport,a.english));states[0].setText(a.english?"Read failed":"读取失败");states[0].setTextColor(a.themed(0xffbd6a28));title.setText(a.english?"APP connection interrupted":"APP 读取连接中断");detail.setText(a.english?"This does not prove that the injected module stopped. Restore Root access and check again; your settings are retained.":"本次读取失败不表示系统中的模块已经停止。请恢复所需访问权限后重新检测；原设置仍保留。");}
}
