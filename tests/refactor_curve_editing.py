"""Production curve editing against device coordinates, including linked handles.
Host numerical/serialization coverage, not a device luminance measurement.
"""
from pathlib import Path
import os,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/curve-editing-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
test=O/'EditingTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
public class EditingTest{
 static int cases;static final float[] X={0,30,600,100000},ONE={1,1,1,1};
 static void check(boolean b){if(!b)throw new AssertionError("editing case "+cases);cases++;}
 static void bad(Runnable r){try{r.run();throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){cases++;}}
 static void edit(float[] base,float min,float max,int point,float request,boolean strict){
  float[] saved=base.clone(),f=ONE.clone();CurveEditor.Edit e=CurveEditor.edit(point,request,base,min,max,f,0,strict);
  float[] y=new CurvePlan(X,base,min,max,e.factors,e.floor).nits();float[] bound=CurveEditor.editBounds(point,min,max,strict);
  check(Math.abs(y[point]-Math.max(bound[0],Math.min(bound[1],request)))<=Math.max(.0001f,Math.ulp(max)*4));
  for(int i=0;i<4;i++){check(Float.isFinite(y[i])&&y[i]>=min&&y[i]<=max);if(i>0)check(strict?y[i]>y[i-1]:y[i]>=y[i-1]);}
  check(Arrays.equals(base,saved)&&Arrays.equals(f,ONE));
  check(Arrays.equals(CurvePlan.factors(CurvePlan.encode(e.factors)),e.factors));
  check(Arrays.equals(CurveEditor.preset(e.factors,base,min,max,e.floor),e.factors));
  // Dense physical curve preserves every lux coordinate and both endpoints.
  if(!strict){TraditionalCurve full=new TraditionalCurve(new float[]{0,10,30,100,600,10000,100000},new float[]{base[0],(base[0]+base[1])/2,base[1],(base[1]+base[2])/2,base[2],(base[2]+base[3])/2,base[3]});
   float[] dense=full.reshape(min,max,e.factors,e.floor);check(dense.length==7&&dense[0]==y[0]&&dense[6]==y[3]);for(int i=1;i<dense.length;i++)check(dense[i]>=dense[i-1]&&dense[i]>=min&&dense[i]<=max);
  }
 }
 public static void main(String[] args)throws Exception{
  float[] report={3.5f,83.13f,126.14f,3200};
  for(boolean strict:new boolean[]{false,true})for(int p=0;p<4;p++)for(float target:new float[]{2,960,1600,3200,-10,4000})edit(report,2,3200,p,target,strict);
  CurveEditor.Edit raised=CurveEditor.edit(1,960,report,2,3200,ONE,166.26f,false);check(raised.factors[1]>2);check(new CurvePlan(X,report,2,3200,raised.factors,raised.floor).at(30)>=959.99f);
  // Gesture reversal uses the original basis, not the previously linked frame.
  CurveEditor.Edit back=CurveEditor.edit(1,report[1],report,2,3200,ONE,0,false);check(Arrays.equals(new CurvePlan(X,report,2,3200,back.factors,back.floor).nits(),report));
  Random random=new Random(43302);
  for(int n=0;n<1500;n++){float max=10+random.nextFloat()*9990,min=random.nextFloat();float[] base={min+(max-min)*.001f,min+(max-min)*.02f,min+(max-min)*.2f,max};for(boolean strict:new boolean[]{false,true})edit(base,min,max,random.nextInt(4),min+random.nextFloat()*(max-min),strict);}
  for(float[] base:new float[][]{{0,20,100,1000},{0,0,100,1000},{0,0,0,1000}})for(int p=0;p<4;p++)for(float value:new float[]{0,300,1000}){
   if(p>0&&base[p]==0)continue;
   // Zero references are represented by the shared floor, not division by zero.
   if(p>1&&base[1]==0&&value==0)continue;
   edit(base,0,1000,p,value,false);
  }
  for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})bad(()->CurveEditor.edit(1,invalid,report,2,3200,ONE,0,false));
  bad(()->CurvePlan.factors("-0.1,1,1,1"));bad(()->new CurvePlan(X,report,2,3200,new float[]{Float.MAX_VALUE,1,1,1}));
  // Profile parsing and imports retain expanded values instead of restoring defaults.
  JSONObject j=new JSONObject();ThemeCurveOptions theme=new ThemeCurveOptions();theme.enabled=true;theme.factors=raised.factors;theme.floor=raised.floor;theme.put(j);ThemeCurveOptions copy=ThemeCurveOptions.parse(j);check(Arrays.equals(copy.factors,raised.factors)&&copy.floor==raised.floor);
  System.out.println("Device-range curve editing: "+cases+" cases PASS; actual production editor, physical reshape and profile round trips");
 }
}''','utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
names=['CurvePlan', 'CurveEditor', 'TraditionalCurve', 'ThemeCurveOptions', 'SavedCurve', 'CurveCompatibility']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],str(test)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.EditingTest'],check=True)
main=(S/'MainActivity.java').read_text('utf-8')
assert 'dragStartFactors=factors.clone();dragStartFloor=curveFloor;' in main
assert 'applyCurveEdit(point,requested,darkProfile,dragStartFactors,dragStartFloor)' in main
assert 'applyCurveEdit(point,value,darkProfile)' in main
