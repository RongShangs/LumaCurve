"""Actual boundary adapter and first/second-stroke policy. No phone or writes."""
from pathlib import Path
import subprocess,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/control-center-partition-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'com/android/server/display/config/HighBrightnessModeData.java':'package com.android.server.display.config;public class HighBrightnessModeData{public float transitionPoint=.5f;}',
'ControlCenterCalibration.java':'''package top.rongshangs.lumacurve.refactor;class ControlCenterCalibration{static Object configuration(Object o){return o;}static java.lang.reflect.Field field(Class<?> c,String n)throws Exception{java.lang.reflect.Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}}''',
'PartitionTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import com.android.server.display.config.HighBrightnessModeData;
public class PartitionTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("partition "+cases);cases++;}
 public static class Hbm{public float getNormalBrightnessMax(){return .6f;}}
 public static class Config{public HighBrightnessModeData data=new HighBrightnessModeData();public Hbm mHbmController=new Hbm();public HighBrightnessModeData getHighBrightnessModeData(){return data;}}
 public static class Legacy{public Hbm mHbmController=new Hbm();}
 public static class Broken{public Hbm mHbmController=new Hbm();public Object getHighBrightnessModeData(){return new Object();}}
 public static void main(String[] args)throws Exception{
  JSONObject table=new JSONObject().put("standard",ControlCenterCurve.sample(x->x*x).json());Config c=new Config();JSONObject split=ControlCenterPartition.read(c,table);check(split.getString("profile").equals("ddc_hbm_transition"));check(Math.abs(split.getDouble("node")-.25)<.00002);check(split.getDouble("brightness")==.5);
  c.data=null;split=ControlCenterPartition.read(c,table);check(split.getString("profile").equals("hbm_normal_ceiling")&&Math.abs(split.getDouble("node")-.36)<.00002);check(ControlCenterPartition.read(new Legacy(),table).getString("profile").equals("hbm_normal_ceiling"));
  for(Object bad:new Object[]{new Broken(),new Object()})try{ControlCenterPartition.read(bad,table);throw new AssertionError();}catch(ReflectiveOperationException|IllegalArgumentException expected){cases++;}
  for(float bad:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,1.1f,1}){c.data=new HighBrightnessModeData();c.data.transitionPoint=bad;try{ControlCenterPartition.read(c,table);throw new AssertionError();}catch(IllegalArgumentException expected){cases++;}}
  for(int boundary:new int[]{1,100,500,999})for(long start:new long[]{0,100,123456}){
   ControlCenterPartition.Gate g=new ControlCenterPartition.Gate();check(g.move(boundary-1,boundary,true,false,start,false)==g.NORMAL);check(g.move(boundary+1,boundary,true,false,start+10,false)==g.PROMPT);
   check(g.move(boundary+10,boundary,true,false,start+20,false)==g.CAP);check(g.move(boundary+10,boundary,false,true,start+30,false)==g.CAP);
   check(g.move(boundary+1,boundary,true,false,start+1010,false)==g.RAW);check(g.confirmed==start+1010);check(g.move(boundary+2,boundary,true,true,start+5000,true)==g.RAW);check(g.move(boundary-1,boundary,true,false,start+5100,true)==g.RELEASE);
   check(g.move(boundary-1,boundary,true,false,start+5110,true)==g.NORMAL);check(g.move(boundary-1,boundary,false,true,start+5120,false)==g.NORMAL);g.reset();check(!g.raw&&g.since==-1&&!g.released);
   g=new ControlCenterPartition.Gate();check(g.move(boundary,boundary,true,false,start,false)==g.PROMPT);check(g.move(boundary+1,boundary,true,false,start+100,false)==g.CAP);check(g.move(boundary+1,boundary,false,true,start+200,false)==g.CAP);check(g.move(boundary+1,boundary,true,false,start+3001,false)==g.PROMPT);check(g.move(boundary+2,boundary,true,false,start+3010,false)==g.CAP);
  }
  // Real sliders report the second finger-down at/below the clamped thumb before
  // an upward move. That ordinary input must not erase the released first stroke.
  for(int boundary:new int[]{100,750,75000})for(int offset:new int[]{1,10,50}){
   ControlCenterPartition.Gate g=new ControlCenterPartition.Gate();
   check(g.move(boundary+20,boundary,true,false,100,false)==g.PROMPT);
   check(g.move(boundary,boundary,false,true,200,false)==g.CAP);
   check(g.move(boundary-offset,boundary,true,false,300,false)==g.NORMAL);
   check(g.move(boundary-1,boundary,true,false,350,false)==g.NORMAL);
   check(g.move(boundary,boundary,true,false,400,false)==g.CAP);
   check(g.move(boundary+1,boundary,true,false,450,false)==g.RAW);
   check(g.since==100&&g.confirmed==450);
   JSONObject proof=new JSONObject().put("partition_since",g.since).put("partition_confirmed",g.confirmed).put("elapsed",450);check(g.proof(proof));
  }
  for(int scenario=0;scenario<4;scenario++){
   ControlCenterPartition.Gate g=new ControlCenterPartition.Gate();g.move(800,750,true,false,100,false);
   if(scenario!=0)g.move(750,750,false,true,150,false);
   check(g.move(700,750,true,false,scenario==1?3101:200,false)==g.NORMAL);
   if(scenario==2)g.move(700,750,false,true,250,false);
   if(scenario==3)g.reset();
   check(g.move(800,750,true,false,scenario==1?3150:300,false)==g.PROMPT);
  }
  ControlCenterPartition.Gate abandoned=new ControlCenterPartition.Gate();abandoned.move(500,500,true,false,0,false);abandoned.move(500,500,false,true,10,false);check(abandoned.move(600,500,true,false,20,false)==abandoned.RAW);check(abandoned.move(700,500,true,false,1600,false)==abandoned.PROMPT);
  for(int maximum:new int[]{4095,16383,65535})for(double ordinary:new double[]{.1,.3,.5,.8})for(int requested:new int[]{0,(int)(maximum*.9),maximum}){
    int upper=ControlCenterPartition.upper(requested,maximum,ordinary);check(Math.abs(ControlCenterPartition.node(.75,ordinary,upper,maximum)-ordinary)<.000001);check(Math.abs(ControlCenterPartition.node(1,ordinary,upper,maximum)*maximum-upper)<.000001);
    for(int i=0;i<=100;i++){double pos=i/100.;double node=ControlCenterPartition.node(pos,ordinary,upper,maximum);check(Math.abs(ControlCenterPartition.position(node,ordinary,upper,maximum)-pos)<.000001);}
  }
  for(long delay:new long[]{0,1,500,1000,1001,2000,3000,3001}){JSONObject proof=new JSONObject().put("partition_since",10).put("partition_confirmed",10+delay).put("elapsed",20+delay);check(ControlCenterPartition.Gate.proof(proof)==(delay<=3000));}
  for(JSONObject bad:new JSONObject[]{new JSONObject(),new JSONObject().put("partition_since",-1).put("partition_confirmed",0).put("elapsed",1),new JSONObject().put("partition_since",0).put("partition_confirmed",2).put("elapsed",1),new JSONObject().put("partition_since","0").put("partition_confirmed",1).put("elapsed",2)})check(!ControlCenterPartition.Gate.proof(bad));
  System.out.println("Control center partition: "+cases+" cases PASS; actual adapter and gesture policy, Android not tested");
 }
}'''}
for name,text in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(O/n) for n in files],str(S/'ControlCenterCurve.java'),str(S/'ControlCenterPartition.java')],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.PartitionTest'],check=True)
