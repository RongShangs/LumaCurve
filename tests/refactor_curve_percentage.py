"""Actual curve edit dialog and commit paths with host widgets. No Android rendering claim."""
from pathlib import Path
import os,re,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/curve-percentage-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
main=(S/'MainActivity.java').read_text('utf-8');a=main.index('    void editPoint(int point){');b=main.index('    void open(String url)',a);actual=main[a:b]
java=r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
class Color{static final int WHITE=-1;}
class TextView{String value;TextView(String s){value=s;}void setText(String s){value=s;}void setTextColor(int n){}void setBackground(Object b){}}
class EditText extends TextView{EditText(){super("");}void setInputType(int n){}String getText(){return value;}}
class Button extends TextView{Runnable action;Button(String s,Runnable r){super(s);action=r;}void click(){action.run();}}
class LinearLayout{List<TextView> children=new ArrayList<>();void addView(TextView v){children.add(v);}}
class AlertDialog{String title;LinearLayout body,actions=new LinearLayout();boolean showing;AlertDialog(String t,LinearLayout b){title=t;body=b;}void show(){showing=true;}void dismiss(){showing=false;}AlertDialog getWindow(){return this;}AlertDialog getDecorView(){return this;}Object findViewWithTag(String tag){return actions;}}
class MainActivity{
 float[] factoryLux={0,30,600,100000},factoryNit={3.5f,83.13f,126.14f,3200},factors={1,8.832f,8.063f,1};float minimum=2,maximum=3200,curveFloor;int MUTED=1,BLUE=2,BG=3;boolean darkTheme,dirty;int draws;AlertDialog latest;EditText number;String toast;ThemeCurveOptions themeCurve=new ThemeCurveOptions();JSONObject runtime=new JSONObject();
 class DarkGraph{float[] editNit(){return factoryNit;}}DarkGraph darkCurveGraph=new DarkGraph();
 LinearLayout column(){return new LinearLayout();}TextView text(String s,int size,int color){return new TextView(s);}EditText input(String label){number=new EditText();return number;}String format(float f){return Float.toString(f);}String curvePercent(double v){return String.format(Locale.ROOT,"%.4f",v).replaceAll("0+$","").replaceAll("\\.$","");}
 AlertDialog dialog(String t,LinearLayout body){latest=new AlertDialog(t,body);return latest;}void dialogButton(AlertDialog d,String label,Runnable action,boolean primary){d.actions.addView(new Button(label,()->{if(action!=null)action.run();d.dismiss();}));}Button compact(String t,Runnable r,LinearLayout row){Button b=new Button(t,r);row.addView(b);return b;}Object ripple(int color,int radius){return null;}void drawCurve(){draws++;}void show(String s){toast=s;}
''' + actual + r'''}
public class PercentageTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("percentage case "+cases);cases++;}static void near(float a,float b){check(Math.abs(a-b)<=Math.max(.001f,Math.ulp(b)*8));}
 static void confirm(MainActivity a){((Button)a.latest.actions.children.get(1)).click();}
 static float[] curve(MainActivity a,boolean dark){return new CurvePlan(a.factoryLux,a.factoryNit,a.minimum,a.maximum,dark?a.themeCurve.factors:a.factors,dark?a.themeCurve.floor:a.curveFloor).nits();}
 static void bad(Runnable r){try{r.run();throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  for(float max:new float[]{100,1060,3200,10000})for(int i=0;i<=1000;i++){float p=i/10f;float nit=CurveEditor.fromPercent(p,max);near(CurveEditor.percent(nit,max),p);check(nit>=0&&nit<=max);}
  for(float p:new float[]{-1,100.001f,Float.NaN,Float.POSITIVE_INFINITY})bad(()->CurveEditor.fromPercent(p,3200));
  for(float m:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){bad(()->CurveEditor.percent(1,m));bad(()->CurveEditor.fromPercent(20,m));}
  MainActivity a=new MainActivity();near(CurveEditor.percent(curve(a,false)[1],a.maximum),22.94313f);near(CurveEditor.percent(curve(a,false)[2],a.maximum),31.78334f);
  for(boolean dark:new boolean[]{false,true})for(int point=0;point<4;point++){
   a=new MainActivity();a.themeCurve.factors=new float[]{.5f,.6f,.7f,1};a.themeCurve.floor=7;float[] before=a.factors.clone(),night=a.themeCurve.factors.clone();float floor=a.curveFloor,darkFloor=a.themeCurve.floor;float expected=CurveEditor.percent(curve(a,dark)[point],a.maximum);
   a.editPoint(point,dark);check(a.latest.showing);check(a.latest.body.children.get(1).value.equals("可调范围：0% ～ 100%"));near(Float.parseFloat(a.number.getText()),expected);check(Float.parseFloat(a.number.getText())<=100);confirm(a);
   check(!a.latest.showing&&!a.dirty&&a.draws==0&&Arrays.equals(before,a.factors)&&Arrays.equals(night,a.themeCurve.factors)&&floor==a.curveFloor&&darkFloor==a.themeCurve.floor);
   a.editPoint(point,dark);a.number.setText("30");confirm(a);check(!a.latest.showing&&a.dirty&&a.draws==1);near(curve(a,dark)[point],960);
   check(Arrays.equals(dark?before:night,dark?a.factors:a.themeCurve.factors));
  }
  for(String invalid:new String[]{"", "bad", "-1", "100.001", "NaN", "Infinity"}){a=new MainActivity();float[] before=a.factors.clone();a.editPoint(1,false);a.number.setText(invalid);confirm(a);check(a.latest.showing&&!a.dirty&&a.draws==0&&Arrays.equals(before,a.factors));check(a.latest.body.children.get(a.latest.body.children.size()-1).value.equals("请输入 0～100 的有效数值"));}
  for(boolean strict:new boolean[]{false,true})for(float p:new float[]{0,50,100}){a=new MainActivity();if(strict)a.runtime.put("curve_route","refactor_coefficients");a.editPoint(1,false);a.number.setText(Float.toString(p));confirm(a);check(!a.latest.showing&&a.dirty);float[] out=curve(a,false);near(out[1],Math.max(a.minimum,CurveEditor.fromPercent(p,a.maximum)));for(int i=1;i<4;i++)check(strict?out[i]>out[i-1]:out[i]>=out[i-1]);}
  a=new MainActivity();float[] before=a.factors.clone();a.editFloor(false);((Button)a.latest.actions.children.get(0)).click();check(!a.latest.showing&&!a.dirty&&Arrays.equals(before,a.factors));
  System.out.println("Curve percentage dialog: "+cases+" cases PASS; production form and commits with widget doubles, Android pixels not tested");
 }
}'''
p=O/'PercentageTest.java';p.write_text(java,'utf-8');stub=O/'android/text/InputType.java';stub.parent.mkdir(parents=True,exist_ok=True);stub.write_text('package android.text;public class InputType{public static final int TYPE_CLASS_NUMBER=2,TYPE_NUMBER_FLAG_DECIMAL=8192;}','utf-8');C=O/'classes';C.mkdir(exist_ok=True)
names=['CurveEditor','CurvePlan','SavedCurve','ThemeCurveOptions']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],str(stub),str(p)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.PercentageTest'],check=True)
# Draft dragging must start from actual curve height and share the dialog's nit commit.
assert 'startFactor=values[point];dragBounds=pointBounds(point);' in main
assert 'CurveEditor.percent(actual,maximum)' in main
assert 'factors[point]*100' not in main and 'requested*base[point]' not in main
