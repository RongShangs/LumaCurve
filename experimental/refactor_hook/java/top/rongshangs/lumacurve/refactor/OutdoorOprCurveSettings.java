package top.rongshangs.lumacurve.refactor;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Independent high brightness chart. No ordinary-curve, learning or driver coordinates enter this view. */
final class OutdoorOprCurveSettings {
    final MainActivity a;final LinearLayout body;final TextView state,selection;final Button restore,simplify;final LinearLayout legend;final Graph graph;
    OutdoorCurvePreview model;String modelToken="";int selected=-1;
    OutdoorOprCurveSettings(MainActivity host,LinearLayout parent){
        a=host;body=a.column();parent.addView(body);LinearLayout header=a.row(body);header.setPadding(0,0,0,a.dp(4));header.addView(a.text("目标曲线",18,a.INK),new LinearLayout.LayoutParams(0,-2,1));restore=a.action("恢复默认",()->restoreDefaults(false));restore.setTextSize(12);restore.setSingleLine(false);restore.setMinimumHeight(a.dp(40));restore.setMinHeight(a.dp(40));restore.setPadding(a.dp(10),a.dp(6),a.dp(10),a.dp(6));header.addView(restore,new LinearLayout.LayoutParams(-2,-2));
        state=a.text("",11,a.MUTED);body.addView(state);graph=new Graph();body.addView(graph,new LinearLayout.LayoutParams(-1,a.dp(235)));
        legend=a.row(body);String[] labels={"━ 高亮目标","━ 最高亮度限制","■ 模式未开放"};int[] colors={a.BLUE,a.themed(0xffad5426),a.MUTED};for(int i=0;i<labels.length;i++)legend.addView(a.text(labels[i],12,colors[i]),new LinearLayout.LayoutParams(0,-2,1));
        selection=a.text("",12,a.BLUE);body.addView(selection);simplify=a.button("改为四点曲线",()->simplifyCurve(false),body);refresh();
    }
    JSONObject bounds(){return a.runtime==null?null:a.runtime.optJSONObject("outdoor");}
    double normalNit(){return model==null?Double.NaN:model.toNit(model.normal);}
    double maximumNit(){return model==null?Double.NaN:model.toNit(model.maximum(a.outdoorOptions));}
    boolean editingBlocked(){return a.busy||a.outdoorSettings!=null&&a.outdoorSettings.testing();}
    boolean strengthEditable(){return model!=null&&model.strengthEditable(a.outdoorOptions);}
    OutdoorOptions.OprCurve initial(){
        OutdoorOptions o=a.outdoorOptions;if(o.oprCurveEnabled&&o.oprCurve.lux.length>=2)return four(o.oprCurve);
        return generated(o);
    }
    OutdoorOptions.OprCurve generated(OutdoorOptions o){if(model==null)throw new IllegalArgumentException("本机nit标定尚未就绪");double first=Math.rint(o.values[0]*o.values[4]),last=Math.max(first+3,o.values[1]);double[] x=new double[4],y=new double[4];for(int i=0;i<4;i++){x[i]=Math.rint(first+(last-first)*i/3);y[i]=Math.max(.001,model.target(o,x[i]));}return new OutdoorOptions.OprCurve(x,y);}

    OutdoorOptions.OprCurve four(OutdoorOptions.OprCurve curve){if(curve.lux.length==4)return curve;double first=Math.min(curve.lux[0],999997),last=Math.max(first+3,curve.lux[curve.lux.length-1]);double[] x=new double[4],y=new double[4];for(int i=0;i<4;i++){x[i]=Math.rint(first+(last-first)*i/3);y[i]=curve.at((float)x[i]);}return new OutdoorOptions.OprCurve(x,y);}
    boolean oldPointCount(){return a.outdoorOptions.targetCurve.enabled&&a.outdoorOptions.targetCurve.points.lux.length!=4;}
    boolean ready(){return !editingBlocked()&&graph.point==Graph.NONE&&a.outdoorOptions.flags[0]&&model!=null&&a.runtime!=null&&a.runtime.optBoolean("outdoor_target_curve_supported")&&!a.outdoorOptions.nodeBoost&&!a.outdoorOptions.oprCurveEnabled;}
    void restoreDefaults(boolean confirmed){if(!ready())return;if(!confirmed){String token=modelToken;a.messageDialog("恢复默认曲线","仅恢复当前增强方式的四点曲线，触发门槛、最高亮度限制和其他设置保持不变。","恢复",()->{if(token.equals(modelToken))restoreDefaults(true);});return;}try{OutdoorOptions defaults=new OutdoorOptions();System.arraycopy(a.outdoorOptions.flags,0,defaults.flags,0,defaults.flags.length);for(int i:new int[]{0,1,4})defaults.values[i]=a.outdoorOptions.values[i];a.outdoorOptions.targetCurve.points=generated(defaults);a.outdoorOptions.targetCurve.enabled=true;selected=-1;a.dirty=true;a.drawOutdoor();}catch(Exception bad){a.show(bad.getMessage());}}
    void simplifyCurve(boolean confirmed){if(!ready()||!oldPointCount())return;if(!confirmed){String token=modelToken;OutdoorOptions.OprCurve before=a.outdoorOptions.targetCurve.points;a.messageDialog("改为四点曲线","旧曲线当前仍按原值生效。确认后将其简化为四个点；其他设置保持不变。","确定",()->{if(token.equals(modelToken)&&before.same(a.outdoorOptions.targetCurve.points))simplifyCurve(true);});return;}a.outdoorOptions.targetCurve.points=four(a.outdoorOptions.targetCurve.points);selected=-1;a.dirty=true;a.drawOutdoor();}
    OutdoorOptions.OprCurve shown(){return a.outdoorOptions.targetCurve.enabled?a.outdoorOptions.targetCurve.points:initial();}
    boolean prepare(){if(model==null)return false;try{if(a.outdoorOptions.oprCurve.lux.length<2)a.outdoorOptions.oprCurve=initial();return true;}catch(Exception bad){a.show(bad.getMessage());return false;}}
    void activate(boolean confirmed,Runnable after){
        if(editingBlocked()||model==null||!a.outdoorOptions.flags[0]||a.runtime==null||!a.runtime.optBoolean("outdoor_target_curve_supported"))return;OutdoorOptions o=a.outdoorOptions;
        if(!confirmed&&(o.nodeBoost||o.oprCurveEnabled)){a.messageDialog("改用高亮目标曲线","旧版节点和上限曲线数值会保留，确认后按目标曲线调节。","确定",()->activate(true,after));return;}
        try{OutdoorOptions.OprCurve curve=o.targetCurve.points.lux.length>=2?o.targetCurve.points:initial();o.nodeBoost=false;o.oprCurveEnabled=false;o.targetCurve.points=curve;o.targetCurve.enabled=true;a.dirty=true;if(after!=null)after.run();a.drawOutdoor();}catch(Exception bad){a.show(bad.getMessage());}
    }
    void resetPoints(){if(!ready())return;try{a.outdoorOptions.targetCurve.points=generated(a.outdoorOptions);a.outdoorOptions.targetCurve.enabled=true;selected=-1;a.dirty=true;a.drawOutdoor();}catch(Exception bad){a.show(bad.getMessage());}}

    void refresh(){
        JSONObject b=bounds();model=OutdoorCurvePreview.read(b);JSONObject mapping=b==null?null:b.optJSONObject("preview_mapping");modelToken=(mapping==null?"":mapping.toString())+"|"+(a.runtime==null?"":a.runtime.optString("revision")+"|"+a.runtime.optLong("outdoor_node_physical_id"));OutdoorOptions o=a.outdoorOptions;
        if(selected>=o.targetCurve.points.lux.length)selected=-1;boolean editable=!editingBlocked()&&o.flags[0]&&model!=null&&a.runtime!=null&&a.runtime.optBoolean("outdoor_target_curve_supported")&&!o.nodeBoost&&!o.oprCurveEnabled;graph.setEnabled(editable&&!oldPointCount());restore.setEnabled(editable);simplify.setVisibility(oldPointCount()?View.VISIBLE:View.GONE);simplify.setEnabled(editable);
        String info=model==null?"设备nit标定未就绪，原设置保留。":oldPointCount()?"旧曲线已保留，可确认简化为四个点。":!o.flags[0]?"沿用系统，以下曲线不参与调节":o.targetCurve.enabled?"四个点设置不同照度下的高亮目标":"当前为原参数预览，拖动后使用目标曲线";state.setText(a.tr(info));state.setVisibility(model!=null&&o.flags[0]&&o.targetCurve.enabled&&!oldPointCount()?View.GONE:View.VISIBLE);updateSelection();graph.invalidate();
    }
    void updateSelection(){OutdoorOptions.OprCurve c=a.outdoorOptions.targetCurve.points;selection.setTextColor(selected>=0||graph.point==Graph.CAP?a.BLUE:a.MUTED);selection.setText(graph.point==Graph.CAP?a.tr("最高亮度限制")+"  "+a.format(a.outdoorOptions.values[OutdoorOptions.MAX_NIT])+" nit":selected>=0&&selected<c.lux.length?a.format(c.lux[selected])+" lux  →  "+a.format(c.nit[selected])+" nit":a.tr("拖动四个蓝点调整照度和亮度"));}
    final class Graph extends View {
        static final int NONE=-1,CAP=-2;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int point=NONE;double minY,maxY,maxX;float downX,downY;boolean moved,beforeEnabled,lastEnabled,wasDirty,node,legacy;boolean[] flags;double[] lastValues;OutdoorOptions.OprCurve display,before,last;double beforeCap,lastCap;String token,configBefore="";
        Graph(){super(a);setFocusable(true);setContentDescription(a.tr("拖动高亮目标节点或亮度上限"));}
        float left(){return a.dp(42);}float top(){return a.dp(20);}float width(){return Math.max(1,getWidth()-left()-a.dp(12));}float height(){return Math.max(1,getHeight()-top()-a.dp(28));}
        float x(double lux){return left()+(float)(lux/maxX)*width();}float y(double nit){return top()+height()*(1-(float)((nit-minY)/(maxY-minY)));}
        double clamp(double nit){return Math.max(minY,Math.min(maxY,nit));}
        void axes(){if(point!=NONE)return;minY=0;maxY=model==null?1:model.nit[model.nit.length-1];if(!Double.isFinite(minY)||!Double.isFinite(maxY)||maxY<=minY){minY=0;maxY=1;}try{display=shown();maxX=Math.min(1000000,Math.max(5000,Math.ceil(display.lux[display.lux.length-1]/5000)*5000));}catch(Exception missing){display=null;maxX=40000;}}
        double nice(double raw){double scale=Math.pow(10,Math.floor(Math.log10(raw))),n=raw/scale;return (n<=1?1:n<=2?2:n<=5?5:10)*scale;}
        String n(double value){return Long.toString(Math.round(value));}
        void grid(Canvas c,double nit){float yy=y(nit);paint.setStrokeWidth(a.dp(1));paint.setColor(a.themed(0xffe5eaf2));c.drawLine(left(),yy,left()+width(),yy,paint);paint.setColor(a.MUTED);String text=n(nit);c.drawText(text,Math.max(0,left()-a.dp(8)-paint.measureText(text)),yy+a.dp(3),paint);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);axes();paint.setTextSize(a.dp(10));paint.setStyle(Paint.Style.FILL);if(model==null){paint.setColor(a.MUTED);c.drawText(a.tr("等待设备nit标定"),left(),top()+a.dp(40),paint);return;}
            double cap=a.outdoorOptions.values[OutdoorOptions.MAX_NIT];double available=normalNit();if(a.outdoorOptions.flags[0]&&!a.outdoorOptions.flags[2]&&Double.isFinite(available)&&available<maxY){paint.setColor(a.themed(0xfff0f2f5));c.drawRect(left(),top(),left()+width(),y(clamp(available)),paint);}grid(c,minY);double step=nice((maxY-minY)/4);for(double value=Math.ceil(minY/step)*step;value<maxY;value+=step)if(y(minY)-y(value)>a.dp(15)&&y(value)-top()>a.dp(15))grid(c,value);grid(c,maxY);if(Double.isFinite(available)&&available>minY&&available<maxY&&Math.abs(available/step-Math.rint(available/step))*(height()*step/(maxY-minY))>a.dp(15)&&y(minY)-y(available)>a.dp(15)&&y(available)-top()>a.dp(15))grid(c,available);
            paint.setColor(a.MUTED);c.drawText("nit",a.dp(2),top()-a.dp(10),paint);String lastLabel=n(maxX)+" lux";float lastStart=left()+width()-paint.measureText(lastLabel);double tick=nice(maxX/4);for(double value=0;value<maxX;value+=tick){String text=n(value);float xx=value==0?left():x(value)-paint.measureText(text)/2;if(value>0&&xx+paint.measureText(text)>lastStart-a.dp(8))continue;c.drawText(text,xx,top()+height()+a.dp(18),paint);}c.drawText(lastLabel,lastStart,top()+height()+a.dp(18),paint);
            OutdoorOptions.OprCurve curve=point!=NONE&&moved&&a.outdoorOptions.targetCurve.enabled?a.outdoorOptions.targetCurve.points:display;if(curve==null)return;c.save();c.clipRect(left()-a.dp(8),top()-a.dp(8),left()+width()+a.dp(8),top()+height()+a.dp(8));Path path=new Path();path.moveTo(left(),y(clamp(curve.nit[0])));for(int i=0;i<curve.lux.length;i++)path.lineTo(x(curve.lux[i]),y(clamp(curve.nit[i])));path.lineTo(left()+width(),y(clamp(curve.nit[curve.nit.length-1])));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2.5f));paint.setColor(a.BLUE);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            if(cap>0){paint.setColor(a.themed(0xffad5426));paint.setStrokeWidth(a.dp(1.5f));c.drawLine(left(),y(clamp(cap)),left()+width(),y(clamp(cap)),paint);c.drawCircle(left()+width(),y(clamp(cap)),a.dp(point==CAP?7:5),paint);}
            for(int i=0;i<curve.lux.length&&curve.lux.length==4;i++){float xx=x(curve.lux[i]),yy=y(clamp(curve.nit[i]));paint.setColor(a.themed(Color.WHITE));c.drawCircle(xx,yy,a.dp(selected==i?8:6),paint);paint.setColor(a.BLUE);c.drawCircle(xx,yy,a.dp(selected==i?5:3.5f),paint);}c.restore();
        }
        void intercept(boolean value){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(value);}
        void finish(){point=NONE;intercept(false);invalidate();}
        @Override protected void onDetachedFromWindow(){finish();super.onDetachedFromWindow();}
        boolean current(){OutdoorOptions o=a.outdoorOptions;return token!=null&&token.equals(modelToken)&&o.targetCurve.enabled==lastEnabled&&node==o.nodeBoost&&legacy==o.oprCurveEnabled&&Arrays.equals(flags,o.flags)&&Arrays.equals(lastValues,o.values)&&last.same(o.targetCurve.points)&&Double.compare(lastCap,o.values[OutdoorOptions.MAX_NIT])==0;}
        @Override public boolean onTouchEvent(MotionEvent e){if(editingBlocked()||!isEnabled()){finish();return false;}int action=e.getActionMasked();OutdoorOptions o=a.outdoorOptions;
            if(action==MotionEvent.ACTION_DOWN){finish();axes();if(display==null)return false;float nearest=a.dp(24);for(int i=0;i<display.lux.length;i++){float distance=(float)Math.hypot(e.getX()-x(display.lux[i]),e.getY()-y(clamp(display.nit[i])));if(distance<nearest){point=i;nearest=distance;}}double cap=o.values[OutdoorOptions.MAX_NIT];if(cap>0&&a.runtime.optBoolean("outdoor_nit_limit_supported")){float distance=(float)Math.hypot(e.getX()-(left()+width()),e.getY()-y(clamp(cap)));if(distance<nearest)point=CAP;}if(point==NONE)return false;downX=e.getX();downY=e.getY();moved=false;beforeEnabled=lastEnabled=o.targetCurve.enabled;node=o.nodeBoost;legacy=o.oprCurveEnabled;flags=o.flags.clone();lastValues=o.values.clone();before=last=o.targetCurve.points;beforeCap=lastCap=cap;token=modelToken;wasDirty=a.dirty;try{configBefore=a.configuration(false).toString();}catch(Exception unavailable){configBefore="";}selected=point>=0?point:-1;updateSelection();intercept(true);invalidate();return true;}
            if(point==NONE||!current()){finish();return false;}
            if(action==MotionEvent.ACTION_MOVE){if(Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(a).getScaledTouchSlop())moved=true;if(!moved)return true;double dy=(downY-e.getY())/height()*(maxY-minY);
                if(point==CAP){o.values[OutdoorOptions.MAX_NIT]=Math.max(Math.min(1,maxY),Math.rint(clamp(beforeCap+dy)));}
                else{double[] xx=display.lux.clone(),yy=display.nit.clone();double dx=(e.getX()-downX)/width()*maxX;xx[point]=Math.rint(Math.max(point==0?0:display.lux[point-1]+1,Math.min(point==xx.length-1?1000000:display.lux[point+1]-1,display.lux[point]+dx)));yy[point]=Math.max(Math.max(.001,model.nit[0]),clamp(display.nit[point]+dy));for(int i=point-1;i>=0;i--)yy[i]=Math.min(yy[i],yy[point]);for(int i=point+1;i<yy.length;i++)yy[i]=Math.max(yy[i],yy[point]);o.targetCurve.points=new OutdoorOptions.OprCurve(xx,yy);o.targetCurve.enabled=true;}
                lastEnabled=o.targetCurve.enabled;lastValues=o.values.clone();last=o.targetCurve.points;lastCap=o.values[OutdoorOptions.MAX_NIT];a.dirty=true;updateSelection();invalidate();return true;}
            if(action==MotionEvent.ACTION_CANCEL){o.targetCurve.enabled=beforeEnabled;o.targetCurve.points=before;o.values[OutdoorOptions.MAX_NIT]=beforeCap;try{if(!configBefore.isEmpty()&&configBefore.equals(a.configuration(false).toString()))a.dirty=wasDirty;}catch(Exception changed){}finish();a.drawOutdoor();return true;}
            if(action==MotionEvent.ACTION_UP){finish();performClick();a.drawOutdoor();return true;}return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
