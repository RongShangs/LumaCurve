"""Exercise actual advice styling/render methods; Android text/view services are doubled."""
from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-advice-text-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
main=(S/'MainActivity.java').read_text(encoding='utf-8')
def method(start,end):
    a=main.index(start);return main[a:main.index(end,a)]
style=method('    android.text.SpannableStringBuilder adviceText(','    void transitionPage(')
refresh=method('        void refreshAdvice(){','        void prepareLabel(')
local_text=method('    class LocalText extends TextView{','    class LocalButton extends Button{')
stubs={
'android/view/View.java':'package android.view;public class View{}',
'android/text/Spanned.java':'package android.text;public interface Spanned{int SPAN_EXCLUSIVE_EXCLUSIVE=33;}',
'android/text/TextPaint.java':'package android.text;public class TextPaint{public int color;public boolean underline=true;public void setColor(int c){color=c;}public void setUnderlineText(boolean u){underline=u;}}',
'android/text/style/ClickableSpan.java':'package android.text.style;public abstract class ClickableSpan{public abstract void onClick(android.view.View v);public void updateDrawState(android.text.TextPaint p){}}',
'android/text/style/ForegroundColorSpan.java':'package android.text.style;public class ForegroundColorSpan{public final int color;public ForegroundColorSpan(int c){color=c;}}',
'android/text/SpannableStringBuilder.java':r'''package android.text;import java.util.*;
public class SpannableStringBuilder implements CharSequence,Spanned{
 public static class Item{public Object span;public int start,end;Item(Object s,int a,int b){span=s;start=a;end=b;}}
 public final List<Item> spans=new ArrayList<>();final StringBuilder text=new StringBuilder();
 public SpannableStringBuilder append(String s){text.append(s);return this;}public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}public CharSequence subSequence(int a,int b){return text.subSequence(a,b);}public String toString(){return text.toString();}
 public void setSpan(Object s,int a,int b,int flags){if(a<0||b>length()||b<=a)throw new AssertionError("invalid span");spans.add(new Item(s,a,b));}
}'''}
files=[]
for name,code in stubs.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');files.append(p)
test=O/'AdviceTextTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import android.view.View;import android.text.*;import android.text.style.*;import org.json.*;
class SystemClock{static long now=1000;static long uptimeMillis(){return now;}static long elapsedRealtime(){return now;}}
class TextView{enum BufferType{NORMAL}CharSequence value="";int visibility,color=0xff616d79;TextView(){}TextView(Object context){}void setText(CharSequence s){setText(s,BufferType.NORMAL);}public void setText(CharSequence s,BufferType type){value=s;}void setTextColor(int c){color=c;}void setVisibility(int v){visibility=v;}void setContentDescription(CharSequence s){}}
class ScrollView{int resets;void scrollTo(int x,int y){resets++;}}
class AdviceCandidate{
 boolean english;final int BLUE=0xff3265df,GONE=8,VISIBLE=0;StatusAdvice.Action selected;JSONObject runtime,adviceRuntime;long adviceAt=-1;String adviceKey="";
 final TextView fusedValue=new TextView(),fusedScene=new TextView(),curveGuide=new TextView();final TextView[] protectionStates={new TextView(),new TextView(),new TextView()},protectionDetails={new LocalText(),new LocalText(),new LocalText()};
 String reading(String key,String unit){return runtime==null?"unavailable":runtime.optString(key,"unavailable")+unit;}
 String tr(String s){return UiText.translate(s,english);}int themed(int color){return color;}void openAdvice(StatusAdvice.Action a){selected=a;}
''' + local_text.replace('MainActivity.this','AdviceCandidate.this') + style + refresh + r'''}
public class AdviceTextTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject state(double strength)throws Exception{return new JSONObject().put("phase","active").put("elapsed_ms",1000).put("auto_mode",true).put("sensor_status","active").put("last_lux",500).put("memory_strength",strength).put("curve_backend","refactor").put("factory_lux",new JSONArray("[0,30,600,5000]")).put("factory_logical_nit",new JSONArray("[1,10,100,1000]")).put("active_logical_nit",new JSONArray("[1,12,100,1000]")).put("min_logical_nit",1).put("max_logical_nit",1000).put("current_anchors_lux",new JSONArray("[0,30,600,5000]")).put("current_anchors_nit",new JSONArray("[1,12,100,1000]"));}
 public static void main(String[] args)throws Exception{
  for(boolean english:new boolean[]{false,true})for(String kind:new String[]{"baseline","memory","system","unavailable"}){
   AdviceCandidate a=new AdviceCandidate();a.english=english;a.runtime=state(1);
   if(kind.equals("memory"))a.runtime.put("memory_live_count",1).put("current_anchors_nit",new JSONArray("[1,14,100,1000]"));
   if(kind.equals("system"))a.runtime.put("active_logical_nit",a.runtime.getJSONArray("factory_logical_nit")).put("current_anchors_nit",a.runtime.getJSONArray("factory_logical_nit"));
   if(kind.equals("unavailable"))a.runtime.remove("current_anchors_nit");
   String original=a.runtime.toString();a.refreshAdvice();SpannableStringBuilder text=(SpannableStringBuilder)a.curveGuide.value;
   check(text.toString().equals(a.tr(StatusAdvice.guidance(a.runtime,1000).detail)));check(text.spans.size()==(kind.equals("unavailable")?0:1));check(a.protectionStates[0].value.length()>0&&a.protectionStates[1].value.length()>0);
   if(!kind.equals("unavailable")){SpannableStringBuilder.Item item=text.spans.get(0);boolean memory=kind.equals("memory");check(text.subSequence(item.start,item.end).toString().equals(a.tr(memory?"手动记忆":"基础曲线")));ClickableSpan link=(ClickableSpan)item.span;TextPaint paint=new TextPaint();link.updateDrawState(paint);check(paint.color==a.BLUE&&!paint.underline);link.onClick(new View());check(a.selected.group==(memory?1:0)&&a.selected.target.equals(memory?"memory":"curve"));}
   check(a.runtime.toString().equals(original));String key=a.adviceKey;a.refreshAdvice();check(a.adviceKey.equals(key));
   a.runtime=state(1).put("thermal_supported",true).put("outdoor",new JSONObject().put("current_limits",new JSONArray().put(new JSONObject().put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5).put("uptime_ms",1000))));a.refreshAdvice();
   check(a.protectionStates[2].value.toString().equals(a.tr("温度未取得")));check(a.protectionDetails[2].value.toString().contains(a.tr("正在限亮")));
   a.runtime=null;a.refreshAdvice();check(a.protectionStates[0].value.length()>0&&a.protectionStates[1].value.length()>0);check(((SpannableStringBuilder)a.curveGuide.value).spans.isEmpty());
  }
  // All status branches keep special-mode help, while curve/memory help stays in the curve card.
  for(boolean english:new boolean[]{false,true})for(String scenario:new String[]{"normal","missing","stale","future","disabled","manual","lock","hdr","sensor","dark","temperature","hot","power_save","hbm_budget","scene","output_override","thermal","battery","opr","range","clamper"}){
   AdviceCandidate a=new AdviceCandidate();a.english=english;JSONObject s=state(1),o=new JSONObject().put("options",new JSONObject());s.put("outdoor",o);
   if(scenario.equals("missing"))s=null;
   else if(scenario.equals("stale"))s.put("elapsed_ms",-1);
   else if(scenario.equals("future"))s.put("elapsed_ms",1001);
   else if(scenario.equals("disabled"))s.put("phase","disabled");
   else if(scenario.equals("manual")||scenario.equals("lock")){s.put("auto_mode",false);s.put("brightness_control",new JSONObject().put("owner",scenario.equals("lock")?"dark":"manual"));}
   else if(scenario.equals("hdr"))s.put("hdr_active",true);
   else if(scenario.equals("sensor"))s.put("sensor_status","warming");
   else if(scenario.equals("dark"))s.put("last_lux",10).put("dark_lock_supported",true).put("low_light_supported",true);
   else if(scenario.equals("hot"))s.put("thermal_severity",3);
   else if(!scenario.equals("normal")){s.put("last_lux",20000).put("outdoor_supported",true).put("outdoor_range_supported",true).put("outdoor_opr_supported",true).put("thermal_supported",true);
    if(scenario.equals("thermal")||scenario.equals("battery")||scenario.equals("opr"))o.put("current_limits",new JSONArray().put(new JSONObject().put("stage",scenario.equals("thermal")?"adjustBrightnessByThermal":scenario.equals("battery")?"adjustBrightnessByBattery":"adjustBrightnessByOpr").put("before",1).put("after",.5).put("uptime_ms",1000)));
    else if(scenario.equals("range"))o.put("device_mapping_max",1).put("display_range_max",.7).put("actual_brightness",.7);
    else if(scenario.equals("clamper"))o.put("request_age_ms",0).put("requested_brightness",.9).put("clamper_max",.7);
    else o.put("blocking_condition",scenario);
   }
   if(s!=null)s.put("battery_temperature",47.1);
   a.runtime=s;String before=s==null?"":s.toString();a.refreshAdvice();OutputProtection.Item[] items=OutputProtection.read(s,1000,1000);
   for(int i=0;i<3;i++){check(a.protectionStates[i].value.toString().equals(a.tr(OutputProtection.primary(items[i],s,1000))));check(a.protectionDetails[i].value.toString().equals(a.tr(OutputProtection.secondary(items[i]))));check(items[i].group==new int[]{7,2,9}[i]);
    boolean limiting=i==2&&(scenario.equals("thermal")||scenario.equals("temperature"));check(a.protectionStates[i].color==(limiting?0xffbd3434:a.BLUE));
    check(!(a.protectionDetails[i].value instanceof Spanned));if(i==2)check(a.protectionDetails[i].value.toString().equals(a.tr(items[i].state)));
   }
   check(a.curveGuide.value.length()>0);check(((SpannableStringBuilder)a.curveGuide.value).spans.size()==(s==null||scenario.equals("stale")||scenario.equals("future")?0:1));
   check(s==null||s.toString().equals(before));String key=a.adviceKey;a.refreshAdvice();check(a.adviceKey.equals(key));
   if(english)check((a.protectionStates[0].value.toString()+a.protectionDetails[0].value+a.protectionStates[1].value+a.protectionDetails[1].value+a.protectionStates[2].value+a.protectionDetails[2].value+a.curveGuide.value).codePoints().noneMatch(c->c>=0x4e00&&c<=0x9fff));
  }
  for(boolean english:new boolean[]{false,true}){
   AdviceCandidate a=new AdviceCandidate();a.english=english;a.runtime=state(1);a.refreshAdvice();String normal=a.fusedScene.value.toString();
   a.runtime=state(1).put("system_scene",new JSONObject().put("night_driving",true));a.refreshAdvice();check(!a.fusedScene.value.toString().equals(normal));check(a.fusedScene.value.toString().contains("夜间驾驶")||a.fusedScene.value.toString().contains("Night driving"));check(a.protectionStates[0].value.toString().equals(a.tr("未开启")));
   a.runtime=state(1);a.refreshAdvice();check(a.fusedScene.value.toString().equals(normal));check(a.protectionStates[0].value.toString().equals(a.tr("未开启")));
   a.runtime=state(1).put("memory_live_count",1).put("current_anchors_nit",new JSONArray("[1,14,100,1000]"));a.refreshAdvice();check(a.curveGuide.value.toString().contains(a.tr("手动记忆")));check(!a.curveGuide.value.toString().contains(a.tr("基础曲线")));
  }
  AdviceCandidate changing=new AdviceCandidate();changing.runtime=state(1).put("battery_temperature",31.2);changing.refreshAdvice();check(changing.protectionStates[2].value.toString().equals("31.2 ℃"));String previous=changing.adviceKey;changing.runtime.put("battery_temperature",32.4);SystemClock.now=2000;changing.refreshAdvice();check(changing.protectionStates[2].value.toString().equals("32.4 ℃")&&!changing.adviceKey.equals(previous));
  SystemClock.now=1000;changing.runtime=state(1).put("battery_temperature",47.8).put("outdoor",new JSONObject().put("blocking_condition","temperature"));changing.refreshAdvice();check(changing.protectionStates[2].color==0xffbd3434);check(changing.protectionStates[2].value.toString().equals("47.8 ℃"));check(changing.protectionDetails[2].value.toString().equals("温度阻止高亮"));
  changing.runtime=state(1).put("battery_temperature",47.1).put("outdoor",new JSONObject().put("current_limits",new JSONArray().put(new JSONObject().put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5).put("uptime_ms",1000))));changing.refreshAdvice();check(changing.protectionStates[2].color==0xffbd3434&&changing.protectionStates[2].value.toString().equals("47.1 ℃"));check(changing.protectionDetails[2].value.toString().equals("正在限亮"));
  changing.runtime=state(1).put("battery_temperature",30);changing.refreshAdvice();check(changing.protectionStates[2].color==changing.BLUE);
  for(Object invalid:new Object[]{JSONObject.NULL,"47.1",101,-41,Double.NaN}){JSONObject snapshot=state(1).put("outdoor",new JSONObject().put("blocking_condition","temperature"));if(invalid instanceof Double&&Double.isNaN((Double)invalid))snapshot.remove("battery_temperature");else snapshot.put("battery_temperature",invalid);changing.runtime=snapshot;changing.refreshAdvice();check(changing.protectionStates[2].color==changing.BLUE);}
  System.out.println("Advice text rendering: "+cases+" cases PASS; production spans, colors and callbacks with doubles, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(name+'.java')) for name in ['OutputProtection','SceneOptions','SceneCatalog','StatusLimits','LiveLimitEvidence','StatusAdvice','StatusPresentation','UiText','CurveComparison','CurvePlan','TraditionalCurve']],*[str(p) for p in files],str(test)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.AdviceTextTest'],check=True)
