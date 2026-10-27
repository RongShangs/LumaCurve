package top.rongshangs.lumacurve.refactor;

import android.view.*;
import android.widget.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.json.*;

/** A read-only secondary page. Route expansion never probes or changes a controller. */
final class FeatureCheckPage {
    final MainActivity a;final LinearLayout root,body;final ScrollView scroll;
    final TextView summary;final Button refresh;final Set<String> expanded=new LinkedHashSet<>();
    final String[] groups={"曲线与记忆","光感与场景","范围与保护","运行与工具"};
    FeatureCheckPage(MainActivity a){this.a=a;root=a.column();root.setBackgroundColor(a.BG);
        LinearLayout bar=a.row(root);bar.setPadding(a.dp(10),a.dp(6),a.dp(18),a.dp(6));bar.setBaselineAligned(false);MainActivity.NavigationArrow back=a.new NavigationArrow(true);back.setBackground(a.ripple(android.graphics.Color.TRANSPARENT,24));back.setClipToOutline(true);back.setClickable(true);back.setFocusable(true);back.setContentDescription(a.tr("返回日志"));back.setOnClickListener(v->a.closeFeatureChecks());bar.addView(back,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView title=a.text("功能自检",18,a.INK);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setAutoSizeTextTypeUniformWithConfiguration(12,18,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout.LayoutParams titleSize=new LinearLayout.LayoutParams(0,-2,1);titleSize.leftMargin=a.dp(6);bar.addView(title,titleSize);refresh=a.action("重新自检",a::refreshFeatureChecks);refresh.setTextSize(12);refresh.setMinWidth(0);refresh.setMinimumWidth(0);refresh.setMaxLines(2);bar.addView(refresh,new LinearLayout.LayoutParams(a.dp(104),a.dp(48)));
        scroll=new ElasticScrollView(a);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=a.column();body.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(20));scroll.addView(body);summary=a.text("",12,a.MUTED);
    }
    void checking(){refresh.setEnabled(false);summary.setText(a.tr("正在逐项检查；不试调亮度、不切换兼容路线…"));}
    void failed(String detail){refresh.setEnabled(true);summary.setText(a.tr("本次自检未完成；已有结果保留，可能不是当前状态。")+"\n"+detail);}
    static String stateLabel(String state){return state.equals("available")?"接口可用":state.equals("inactive")?"系统未启用":state.equals("pending")?"等待就绪":"接口不支持";}
    void render(JSONObject runtime,JSONObject rootReport,JSONObject appReport){int y=scroll.getScrollY();if(summary.getParent() instanceof ViewGroup)((ViewGroup)summary.getParent()).removeView(summary);body.removeAllViews();
        LinearLayout intro=a.card(body);intro.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(8));intro.addView(summary);intro.addView(a.text("显示最近一次检测；打开页面不重检。接口可用不代表功能已开启，点击条目查看全部路线。",12,a.MUTED));
        try{JSONArray rows=FeatureCompatibility.combine(runtime,rootReport,appReport);int available=0,attention=0;
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if("available".equals(row.optString("state")))available++;else attention++;}
            summary.setText(a.tr("接口可用：")+available+a.tr(" 项 · 需关注：")+attention+a.tr(" 项")+(runtime==null?"\n"+a.tr("系统框架尚未连接；不使用上次缓存推断当前路线。"):"\n"+a.tr("框架检测：")+checkedAt(runtime.optJSONObject("feature_self_check")))+(rootReport==null?"":"\n"+a.tr("工具检测：")+checkedAt(rootReport)));
            for(int group=0;group<groups.length;group++){boolean heading=false;for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optInt("group")!=group)continue;if(!heading){TextView h=a.text(groups[group],14,a.MUTED);h.setPadding(a.dp(8),a.dp(10),0,a.dp(8));body.addView(h);heading=true;}item(row);}}
            if(rows.length()==0)body.addView(a.text("尚未取得自检结果，请点击重新自检。",14,a.MUTED));
        }catch(JSONException invalid){summary.setText(a.tr("结果暂时无法读取，请重新自检。"));}
        refresh.setEnabled(!a.busy);scroll.post(()->{if(!a.destroyed)scroll.scrollTo(0,y);});
    }
    String checkedAt(JSONObject report){
        if(report==null)return a.tr("尚未取得检测记录");long wall=report.optLong("checked_unix_ms",-1),elapsed=report.optLong("checked_elapsed_ms",-1);
        String at=wall>0?new SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(wall)):elapsed>=0?a.tr("本次开机后 ")+elapsed/1000+a.tr(" 秒"):a.tr("检测时间未报告");
        String request=report.optString("check_request",report.optString("request"));String trigger=request.contains(":manual:")||request.equals("manual")?"手动重检":request.contains("app-start")||request.equals("app-open")?"应用启动":request.equals("startup-readiness")?"就绪补检":request.equals("configuration-start")?"应用设置后":"引擎启动";
        return at+" · "+a.tr(trigger);
    }
    TextView compactText(String value,int size,int color){TextView text=a.text(value,size,color);text.setPadding(0,a.dp(2),0,a.dp(2));text.setIncludeFontPadding(false);return text;}
    void item(JSONObject row)throws JSONException{
        String id=row.optString("source")+":"+row.optString("id"),state=row.optString("state");LinearLayout card=a.column();card.setPadding(a.dp(12),a.dp(2),a.dp(12),a.dp(8));card.setBackground(a.background(0xffffffff,14));LinearLayout.LayoutParams cardSize=new LinearLayout.LayoutParams(-1,-2);cardSize.bottomMargin=a.dp(6);body.addView(card,cardSize);LinearLayout line=a.row(card);
        TextView title=compactText(row.optString("name"),14,a.INK);line.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView badge=compactText(stateLabel(state),10,state.equals("unsupported")?0xffbd3434:state.equals("available")?0xff38775c:a.MUTED);line.addView(badge);
        JSONArray routes=row.getJSONArray("routes");TextView toggle=a.text("",11,a.BLUE);toggle.setMinHeight(a.dp(48));toggle.setGravity(Gravity.CENTER_VERTICAL);toggle.setPadding(a.dp(10),0,0,0);toggle.setClickable(true);toggle.setFocusable(true);line.addView(toggle,new LinearLayout.LayoutParams(-2,-2));
        TextView current=compactText(row.optString("route_name"),12,a.INK);current.setMaxLines(2);current.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(current);
        String evidence=row.optString("evidence"),detail=row.optString("detail");String preview=!state.equals("available")&&!detail.isEmpty()?detail:evidence;
        if(!preview.isEmpty()&&!preview.equals("实际接口检查通过")){TextView hint=compactText(preview,11,a.MUTED);hint.setMaxLines(2);hint.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(hint);}
        LinearLayout details=a.column();card.addView(details);
        Runnable sync=()->{boolean open=expanded.contains(id);if(open&&details.getChildCount()==0){
            if(!evidence.isEmpty())details.addView(a.text(evidence,12,a.MUTED));if(!detail.isEmpty()&&!detail.equals(evidence))details.addView(a.text(detail,12,a.MUTED));
            details.addView(a.text(checkedAt(row),10,a.MUTED));routeRows(details,routes);
        }details.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(a.tr("路线")+" "+routes.length()+" "+(open?"▴":"▾"));toggle.setContentDescription(a.tr(open?"收起兼容路线":"查看全部兼容路线")+"（"+routes.length()+"）");};sync.run();
        View.OnClickListener expand=v->{if(!expanded.add(id))expanded.remove(id);sync.run();};toggle.setOnClickListener(expand);card.setClickable(true);card.setFocusable(true);card.setOnClickListener(expand);
    }
    void routeRows(LinearLayout details,JSONArray routes){for(int i=0;i<routes.length();i++){JSONObject route=routes.optJSONObject(i);if(route==null)continue;LinearLayout option=a.column();option.setPadding(a.dp(12),a.dp(10),a.dp(12),a.dp(10));option.setBackground(a.background(route.optBoolean("selected")?0xffeef3ff:0xfff4f5f7,12));LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=a.dp(8);details.addView(option,size);
            String label=route.optBoolean("selected")?"已采用":route.optString("status").equals("unconfirmed")?"尚未确认":"未采用";option.addView(a.text(route.optString("name")+" · "+a.tr(label),13,a.INK));option.addView(a.text(route.optString("condition"),12,a.MUTED));option.addView(a.text(route.optString("reason"),12,a.MUTED));}
    }

}
