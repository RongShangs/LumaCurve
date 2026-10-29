"""Production mapping presentation, draft-only interactions and secondary safe areas."""
from pathlib import Path
import os, re, json, ast, subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/control-center-settings-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'

def method(signature):
    text=(S/'MainActivity.java').read_text(encoding='utf-8');start=text.index(signature);at=text.index('{',start);end=at+1;depth=1
    while depth:depth+=(text[end]=='{')-(text[end]=='}');end+=1
    return text[start:end]

files={
'top/rongshangs/lumacurve/refactor/ControlCenterSetup.java':'package top.rongshangs.lumacurve.refactor;class ControlCenterSetup{int checks;ControlCenterSetup(MainActivity a,android.widget.LinearLayout p,android.widget.TextView h){p.addView(h);}void refresh(){}void check(boolean prompt){checks++;}}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable{public int color,stroke,strokeColor;public void setStroke(int w,int c){stroke=w;strokeColor=c;}}',
'android/text/InputType.java':'package android.text;public class InputType{public static final int TYPE_CLASS_NUMBER=2;}',
'android/widget/EditText.java':'package android.widget;public class EditText extends TextView{public boolean focused;public String error;public void setInputType(int t){}public String getText(){return text;}public boolean hasFocus(){return focused;}public void clearFocus(){focused=false;}public void setError(String x){error=x;}}',
'android/widget/SeekBar.java':'package android.widget;public class SeekBar extends android.view.View{public int maximum,progress;public OnSeekBarChangeListener listener;public SeekBar(Object c){}public interface OnSeekBarChangeListener{void onStartTrackingTouch(SeekBar v);void onStopTrackingTouch(SeekBar v);void onProgressChanged(SeekBar v,int x,boolean user);}public void setOnSeekBarChangeListener(OnSeekBarChangeListener l){listener=l;}public void setMax(int x){maximum=x;}public void setProgress(int x){progress=x;}}',
'top/rongshangs/lumacurve/refactor/ControlCenterDemo.java':'package top.rongshangs.lumacurve.refactor;class ControlCenterDemo extends android.view.View{int ordinary,upper;boolean automatic;ControlCenterDemo(MainActivity a){}void data(int o,int u,boolean b){ordinary=o;upper=u;automatic=b;}void refresh(){}void stop(){}}',

'android/view/View.java':'''package android.view;
public class View{public static int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2;public boolean enabled=true,selected,focusable,clickable;public float alpha=1;public int minimumHeight,visibility;public int[] padding={0,0,0,0};public Object background,params,tag;public String description;public Click action;public interface Click{void onClick(View v);}public void setOnClickListener(Click c){action=c;}public void performClick(){if(enabled&&action!=null)action.onClick(this);}public void setPadding(int l,int t,int r,int b){padding=new int[]{l,t,r,b};}public int getPaddingBottom(){return padding[3];}public void setMinimumWidth(int w){}public void setMinimumHeight(int h){minimumHeight=h;}public void setClickable(boolean x){clickable=x;}public void setFocusable(boolean x){focusable=x;}public void setEnabled(boolean x){enabled=x;}public boolean isEnabled(){return enabled;}public void setSelected(boolean x){selected=x;}public void setAlpha(float x){alpha=x;}public void setBackground(Object x){background=x;}public void setTag(Object x){tag=x;}public void setVisibility(int x){visibility=x;}public void setImportantForAccessibility(int x){}public void setContentDescription(String x){description=x;}}''',
'android/view/Gravity.java':'package android.view;public class Gravity{public static int CENTER_VERTICAL=16,CENTER=17;}',
'android/widget/LinearLayout.java':'''package android.widget;import java.util.*;import android.view.*;
public class LinearLayout extends View{public static final int HORIZONTAL=0,VERTICAL=1;public int orientation=VERTICAL,gravity;public boolean baseline=true;public List<View> children=new ArrayList<>();public static class LayoutParams{public int width,height,topMargin,leftMargin;public float weight;public LayoutParams(int w,int h){width=w;height=h;}public LayoutParams(int w,int h,float x){this(w,h);weight=x;}}public void setOrientation(int x){orientation=x;}public void setGravity(int x){gravity=x;}public void setBaselineAligned(boolean x){baseline=x;}public void addView(View x){addView(x,new LayoutParams(-1,-2));}public void addView(View x,LayoutParams p){x.params=p;children.add(x);}}''',
'android/widget/TextView.java':'package android.widget;public class TextView extends android.view.View{public String text="";public int color,size,gravity,maxLines;public void setText(String x){text=x;}public void setTextColor(int x){color=x;}public void setGravity(int x){gravity=x;}public void setMaxLines(int x){maxLines=x;}}',
'android/widget/Button.java':'package android.widget;public class Button extends TextView{public boolean singleLine;public void setSingleLine(boolean x){singleLine=x;}public void setMinHeight(int x){minimumHeight=x;}public void setTextSize(int x){size=x;}}',
'android/widget/ScrollView.java':'package android.widget;public class ScrollView extends android.view.View{public boolean clip=true;public int y=150;public void setClipToPadding(boolean x){clip=x;}}',
'top/rongshangs/lumacurve/refactor/MainActivity.java':'''package top.rongshangs.lumacurve.refactor;
import android.widget.*;import org.json.*;
class MainActivity{
 int BG=0xfffafafa,INK=0xff24303c,MUTED=0xff616d79,BLUE=0xff3265df;boolean busy,dirty,english,dark;JSONObject runtime,lastAnswer,rootFeatureReport;Controls controls=new Controls();ControlCenterSettings mapping;int checks,runs,selected,opened;String command;
 ScrollView[] settingsScrollers=new ScrollView[11];Feature featureCheckPage;static class Feature{ScrollView scroll=new ScrollView();}static class Controls{int nodeSliderMode,partitionMaximum;boolean partitionRestoreAuto=true,darkLock=true;}
 int dp(float x){return Math.round(x);}int themed(int x){return ThemePalette.color(x,dark);}String tr(String x){return UiText.translate(x,english);}LinearLayout column(){return new LinearLayout();}LinearLayout card(LinearLayout parent){LinearLayout card=column();parent.addView(card);return card;}TextView text(String x,int size,int color){TextView t=new TextView();t.text=tr(x);t.size=size;t.color=themed(color);return t;}Object ripple(int color,int radius){return ThemePalette.surface(color,dark);}android.graphics.drawable.GradientDrawable background(int color,int radius){android.graphics.drawable.GradientDrawable b=new android.graphics.drawable.GradientDrawable();b.color=(Integer)ThemePalette.surface(color,dark);return b;}EditText input(String h){return new EditText();}Button action(String label,Runnable run){Button b=new Button();b.text=tr(label);b.minimumHeight=48;b.setOnClickListener(v->run.run());return b;}void drawCurve(){if(mapping!=null)mapping.refresh();}void checkAppFeatures(){checks++;}void run(String command,String ignored){this.command=command;runs++;}void select(int p){selected=p;}void showFeatureChecks(){opened++;}
 INSETS
}'''.replace('INSETS',method('void updateSecondaryInsets(')),
}
for name,text in files.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')

phrases=[]
for name in ['ControlCenterStatus.java','ControlCenterSettings.java']:
    for literal in re.findall(r'"(?:[^"\\]|\\.)*"',(S/name).read_text(encoding='utf-8')):
        text=ast.literal_eval(literal)
        if re.search('[\u4e00-\u9fff]',text) and text not in phrases:phrases.append(text)
java=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import android.widget.*;import android.view.*;
public class MappingSettingsTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject runtime(boolean raw,boolean partition){return new JSONObject().put("control_center_raw_supported",raw).put("control_center_partition_supported",partition);}
 public static void main(String[] args){
  int[] modes=ControlCenterStatus.visibleModes();check(modes.length==3&&modes[0]==0&&modes[1]==3&&modes[2]==4);modes[0]=99;check(ControlCenterStatus.visibleModes()[0]==0);
  for(boolean raw:new boolean[]{false,true})for(boolean split:new boolean[]{false,true})for(int saved=0;saved<=4;saved++)for(int mode:new int[]{0,3,4})check(ControlCenterStatus.selectable(runtime(raw,split),mode,saved)==(mode==0||mode==saved||(mode==3?raw:split)));
  MainActivity a=new MainActivity();a.runtime=runtime(true,true);a.lastAnswer=new JSONObject().put("config",new JSONObject().put("control_center_node_mode",2));String saved=a.lastAnswer.toString();LinearLayout parent=new LinearLayout();a.mapping=new ControlCenterSettings(a,parent);ControlCenterSettings card=a.mapping;check(parent.children.size()==1&&card.root.tag.equals("node_mapping"));check(card.options[0].selected&&card.returnPolicy.visibility==View.GONE);check(a.lastAnswer.toString().equals(saved));
  card.options[2].performClick();check(a.controls.nodeSliderMode==4&&a.dirty&&a.controls.darkLock&&card.returnPolicy.visibility==View.VISIBLE);check(card.returns[0].selected&&!card.returns[1].selected);card.returns[1].performClick();check(!a.controls.partitionRestoreAuto&&card.returns[1].selected);check(a.runs==0&&a.lastAnswer.toString().equals(saved));
  a.dirty=false;card.returns[1].performClick();check(!a.dirty);a.busy=true;card.refresh();card.options[1].performClick();card.returns[0].performClick();check(a.controls.nodeSliderMode==4&&!a.controls.partitionRestoreAuto&&!card.setLimit.enabled&&!card.deviceLimit.enabled);a.busy=false;card.refresh();
  card.options[1].performClick();check(a.controls.nodeSliderMode==3&&card.returnPolicy.visibility==View.GONE);a.runtime=runtime(false,false);card.refresh();check(card.options[1].enabled&&!card.options[2].enabled&&card.options[0].enabled&&card.availability.visibility==View.VISIBLE);card.options[0].performClick();check(a.controls.nodeSliderMode==0&&card.availability.visibility==View.GONE);
  for(int legacy:new int[]{1,2}){a.controls.nodeSliderMode=legacy;card.refresh();for(LinearLayout option:card.options)check(!option.selected);check(a.controls.nodeSliderMode==legacy);}
  for(View child:card.root.children)check(!(child instanceof Button));check(a.opened==0&&a.runs==0&&a.checks==0);
  a.controls.nodeSliderMode=3;a.runtime=runtime(true,true);a.dark=true;card.refresh();check(((android.graphics.drawable.GradientDrawable)card.options[1].background).stroke==2&&card.badges[1].visibility==View.VISIBLE);
  a.runtime.put("control_center_slider",new JSONObject().put("maximum",16383).put("partition",new JSONObject().put("node",.5)));a.controls.nodeSliderMode=4;card.refresh();check(card.demo.ordinary==8192&&card.demo.upper==16383);check(card.partitionBody.children.contains(card.returnPolicy)&&card.options[2].children.contains(card.partitionBody));card.maximumInput.setText("12000");card.setLimit.performClick();check(a.controls.partitionMaximum==12000&&card.demo.upper==12000);card.maximumInput.setText("8000");card.setLimit.performClick();check(a.controls.partitionMaximum==12000&&card.maximumInput.error!=null);card.deviceLimit.performClick();check(a.controls.partitionMaximum==0&&card.demo.upper==16383);
  check(card.limitHeader.orientation==LinearLayout.HORIZONTAL&&card.limitHeader.children.contains(card.deviceLimit)&&!card.partitionBody.children.contains(card.deviceLimit));check(card.limitHeader.children.get(0)==card.limitLabel&&card.limitInputRow.children.get(0)==card.maximumInput&&card.limitInputRow.children.get(1)==card.setLimit);check(((LinearLayout.LayoutParams)card.maximumInput.params).weight==1&&card.deviceLimit.minimumHeight==40&&card.setLimit.minimumHeight==44);check(((LinearLayout.LayoutParams)card.maximumSlider.params).height==36&&card.limitLabel.text.equals("最高节点"));
  for(LinearLayout option:card.options){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)option.params;check(p.height==-2&&option.minimumHeight>=48&&option.orientation==LinearLayout.VERTICAL);check(option.children.get(0) instanceof LinearLayout);}
  for(int bottom:new int[]{0,18,24,48,80}){a.featureCheckPage=new MainActivity.Feature();for(int i=0;i<a.settingsScrollers.length;i++)a.settingsScrollers[i]=new ScrollView();a.updateSecondaryInsets(bottom);for(ScrollView scroll:a.settingsScrollers)check(!scroll.clip&&scroll.padding[3]==bottom&&scroll.y==150);a.updateSecondaryInsets(0);for(ScrollView scroll:a.settingsScrollers)check(scroll.padding[3]==0);}
  for(String text:new String[]{PHRASES}){String translated=UiText.translate(text,true);if(translated.codePoints().anyMatch(c->c>=0x4e00&&c<=0x9fff))throw new AssertionError(text+" -> "+translated);check(true);}
  System.out.println("Mapping settings and secondary insets: "+cases+" cases PASS; production card/model and safe areas, no Android rendering");
 }
}'''.replace('PHRASES',','.join(json.dumps(p,ensure_ascii=False) for p in phrases))
p=O/'top/rongshangs/lumacurve/refactor/MappingSettingsTest.java';p.write_text(java,encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
sources=[*O.rglob('*.java'),*[S/(n+'.java') for n in ['ControlCenterStatus','ControlCenterSettings','ThemePalette','UiText']]]
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(p) for p in sources]],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.MappingSettingsTest'],check=True)
main=(S/'MainActivity.java').read_text(encoding='utf-8');manual=method('void makeManualPanelSettings(')
assert 'controlCenterSettings=new ControlCenterSettings(this,settingsTarget)' in manual
assert manual.index('new ControlCenterSettings')<manual.index('主屏手动亮度')
assert '已保存' not in (S/'ControlCenterSettings.java').read_text(encoding='utf-8')
assert '旧版比例' not in (S/'ControlCenterSettings.java').read_text(encoding='utf-8')
assert 'void showControlCenterMapping(' not in main and 'LinearLayout mapping=column();b.addView(mapping)' not in manual
assert 'keyboard?ime:0' in (S/'ImmersiveLayout.java').read_text(encoding='utf-8')
assert 'settingsGroup>=0||a.featureChecksVisible?safe.bottom' not in (S/'ImmersiveLayout.java').read_text(encoding='utf-8')
print('Independent mapping card, no picker dialog, viewport-level navigation gap removed: PASS')
