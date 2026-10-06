package top.rongshangs.lumacurve.refactor;

import android.app.AlertDialog;
import android.widget.*;
import android.view.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** A transient control surface. The app never runs a background brightness writer. */
final class ManualBrightnessPanel {
    final MainActivity a;AlertDialog dialog;SeekBar slider;TextView value,status;String session="";
    float minimum,maximum;boolean dragging,claimed,sending,closed;Float pending;int generation;long lastState=-1;
    final Runnable poll,flush;
    ManualBrightnessPanel(MainActivity a){this.a=a;poll=new Runnable(){public void run(){if(closed)return;if(a.visible&&!a.busy)a.run("inspect",null);a.ui.postDelayed(this,1000);}};flush=()->flush();}
    void show(){
        JSONObject c=a.runtime==null?null:a.runtime.optJSONObject("brightness_control");
        if(c==null||!c.optBoolean("manual_panel_supported")||!c.optBoolean("manual_panel_enabled")||!"active".equals(a.runtime.optString("phase"))){a.messageDialog("主屏手动亮度","先启用引擎并保存主屏手动面板设置；若刚更新应用，请重启以载入新接口。","知道了",null);return;}
        LinearLayout body=a.column();status=a.text("",13,a.BLUE);body.addView(status);value=a.text("",20,a.INK);body.addView(value);
        slider=new SeekBar(a);slider.setMax(10000);body.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(48)));
        body.addView(a.text("滑动后关闭自动亮度。100% 是系统当前允许的上限，温控、HDR 与驱动仍可限亮。重新开启自动亮度后停止写入。",12,a.MUTED));
        a.button("输入亮度值",this::number,body);a.button("恢复自动亮度",()->send("restore",0,false),body);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){dragging=true;claimed=false;pending=null;session=UUID.randomUUID().toString();send("begin",selected(),true);}
            public void onProgressChanged(SeekBar s,int p,boolean user){if(user){showValue();if(!dragging&&!claimed&&!sending){send("begin",selected(),true);return;}pending=selected();a.ui.removeCallbacks(flush);a.ui.postDelayed(flush,80);}}
            public void onStopTrackingTouch(SeekBar s){dragging=false;pending=selected();flush();}
        });
        dialog=a.dialog("主屏手动亮度",body);dialog.setOnDismissListener(v->{closed=true;generation++;pending=null;a.ui.removeCallbacks(poll);a.ui.removeCallbacks(flush);endSession();a.manualPanel=null;});dialog.show();a.dialogButton(dialog,"关闭",null,false);update(c);a.ui.postDelayed(poll,1000);
    }
    float selected(){return minimum+(maximum-minimum)*slider.getProgress()/10000f;}
    void showValue(){value.setText(String.format(Locale.ROOT,"%.1f%% · %.4f",slider.getProgress()/100f,selected()));}
    void update(JSONObject c){if(closed||c==null)return;long at=c.optLong("elapsed_ms",0);if(at<lastState)return;lastState=at;JSONObject r=c.optJSONObject("range");if(r==null){slider.setEnabled(false);status.setText(a.tr("主屏亮度范围尚未就绪"));return;}
        float low=(float)r.optDouble("minimum",Double.NaN),high=(float)r.optDouble("maximum",Double.NaN);if(!Float.isFinite(low)||!Float.isFinite(high)||high<=low){slider.setEnabled(false);return;}
        minimum=low;maximum=high;slider.setEnabled(c.optBoolean("manual_panel_enabled")&&c.optBoolean("manual_panel_supported"));
        boolean owns=!c.optBoolean("auto")&&session.equals(c.optString("session"))&&c.optString("owner").equals("panel");
        if(claimed&&!owns){claimed=false;pending=null;dragging=false;slider.cancelPendingInputEvents();status.setText(a.tr("已退让；再次滑动才会关闭自动亮度"));}
        else status.setText(a.tr(c.optBoolean("auto")?"自动亮度中；滑动切换为手动":"手动亮度；可使用完整允许范围"));
        if(!dragging&&!sending&&pending==null){float n=owns?(float)c.optDouble("held",r.optDouble("value",minimum)):(float)r.optDouble("actual",r.optDouble("value",minimum));slider.setProgress(Math.round((Math.max(minimum,Math.min(maximum,n))-minimum)/(maximum-minimum)*10000));}showValue();
    }
    void flush(){if(closed||sending||!claimed||pending==null)return;float n=pending;pending=null;send("set",n,false);}
    void send(String action,float n,boolean begin){
        if(closed)return;if(begin){generation++;session=UUID.randomUUID().toString();}final int epoch=generation;final String token=session.isEmpty()?UUID.randomUUID().toString():session;sending=true;
        try{String payload=Base64.getEncoder().encodeToString(new JSONObject().put("action",action).put("session",token).put("value",n).put("gesture_elapsed",android.os.SystemClock.elapsedRealtime()).toString().getBytes(StandardCharsets.UTF_8));
            a.worker.execute(()->{JSONObject response=null;Throwable failure=null;try{response=a.request("brightness",payload);if(!response.optBoolean("ok"))throw new IllegalStateException(response.optString("message"));}catch(Throwable e){failure=e;}final JSONObject answer=response;final Throwable error=failure;
                a.ui.post(()->{if(closed||epoch!=generation)return;sending=false;if(error!=null){claimed=false;pending=null;status.setText(error.getMessage());return;}
                    if(begin)claimed=true;if(action.equals("restore")){claimed=false;pending=null;dragging=false;}JSONObject state=answer.optJSONObject("state");if(state!=null)update(state);flush();});});
        }catch(Throwable error){sending=false;status.setText(error.getMessage());}
    }
    void number(){EditText input=a.input("系统亮度值");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(String.format(Locale.ROOT,"%.4f",selected()));
        LinearLayout box=a.column();box.addView(a.text(String.format(Locale.ROOT,"%.4f～%.4f",minimum,maximum),13,a.MUTED));box.addView(input);AlertDialog d=a.dialog("输入亮度值",box);d.show();a.dialogButton(d,"取消",null,false);a.dialogButton(d,"应用",()->{try{float n=Float.parseFloat(input.getText().toString());if(!Float.isFinite(n)||n<minimum||n>maximum)throw new IllegalArgumentException("亮度超出当前允许范围");send("begin",n,true);}catch(Throwable error){status.setText(a.tr("亮度超出当前允许范围"));}},true);
    }
    void close(){if(dialog!=null)dialog.dismiss();}
    void endSession(){if(session.isEmpty())return;try{String payload=Base64.getEncoder().encodeToString(new JSONObject().put("action","end").put("session",session).toString().getBytes(StandardCharsets.UTF_8));a.worker.execute(()->{try{a.request("brightness",payload);}catch(Throwable ignored){}finally{if(!a.visible)a.closeBridge();}});}catch(Throwable ignored){}}
}
