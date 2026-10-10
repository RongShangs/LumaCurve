"""Scene schema, admission state machine and independent brightness evidence; no device claims."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-scene-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'SceneTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;
public class SceneTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void bad(JSONObject j){try{SceneOptions.parse(j);throw new AssertionError("bad scene accepted "+j);}catch(IllegalArgumentException expected){cases++;}}
 static JSONObject options(String id,String mode,JSONObject condition)throws Exception{return new JSONObject().put("scene_options",new JSONObject().put("policies",new JSONObject().put(id,new JSONObject().put("mode",mode).put("condition",condition))));}
 static JSONObject condition()throws Exception{return new JSONObject().put("lux",true).put("min",0).put("max",50).put("confirm",2000);}
 static JSONObject rule(String id,String target,JSONObject condition)throws Exception{return new JSONObject().put("id",id).put("name","Night indoors").put("target",target).put("condition",condition);}
 static JSONObject state()throws Exception{return new JSONObject().put("elapsed_ms",1000).put("auto_mode",true).put("phase","active").put("sensor_status","active").put("system_scene",new JSONObject()).put("outdoor",new JSONObject());}
 static boolean denied(SceneOptions o,JSONObject facts,int minute,long now,int id,ScenePolicy p){return p.evaluate(o,facts,minute,now)[id];}
 public static void main(String[] args)throws Exception{
  SceneOptions defaults=SceneOptions.parse(new JSONObject());check(!defaults.custom());JSONObject encoded=new JSONObject();defaults.put(encoded);check(!SceneOptions.parse(encoded).custom());for(SceneOptions.Entry e:defaults.entries)check(e.mode.equals("system"));
  for(int i=0;i<SceneOptions.IDS.length;i++){
   SceneOptions block=SceneOptions.parse(options(SceneOptions.IDS[i],"block",new JSONObject()));boolean[] mask=new ScenePolicy().evaluate(block,new JSONObject(),0,1000);for(int k=0;k<mask.length;k++)check(mask[k]==(k==i));
   JSONObject caps=new JSONObject();try{block.verify(caps);throw new AssertionError("unsupported admitted");}catch(IllegalArgumentException expected){cases++;}caps.put("scene_"+SceneOptions.IDS[i]+"_supported",true);block.verify(caps);cases++;
  }
  for(Object bad:new Object[]{true,4,"invalid",JSONObject.NULL})bad(new JSONObject().put("scene_options",bad));
  for(String key:new String[]{"arbitrary","driver_unlock","force_hdr"})bad(new JSONObject().put("scene_options",new JSONObject().put(key,true)));
  bad(options("unknown","block",new JSONObject()));bad(options("night_driving","force",new JSONObject()));bad(options("night_driving","condition",new JSONObject()));
  for(String key:new String[]{"lux","time"})bad(options("night_driving","condition",condition().put(key,"true")));
  for(double n:new double[]{-1,100001})bad(options("night_driving","condition",condition().put("max",n)));
  for(double n:new double[]{-1,60001,1.5})bad(options("night_driving","condition",condition().put("confirm",n)));
  bad(options("night_driving","condition",condition().put("source","camera")));bad(options("night_driving","condition",condition().put("cover","rear_is_zero")));bad(options("night_driving","condition",condition().put("min",51)));bad(options("night_driving","condition",condition().put("time",true).put("start",60).put("end",60)));
  SceneOptions.Condition c=new SceneOptions.Condition(condition());JSONObject f=new JSONObject().put("effective",25);check(c.test(f,0)==1);check(c.test(new JSONObject(),0)==-1);check(c.test(new JSONObject().put("effective",-1),0)==-1);check(c.test(new JSONObject().put("effective",51),0)==0);check(c.test(new JSONObject().put("effective",0),0)==1);check(c.test(new JSONObject().put("effective",50),0)==1);
  c=new SceneOptions.Condition(new JSONObject().put("time",true).put("start",1320).put("end",420));for(int minute:new int[]{0,419,1320,1439})check(c.test(new JSONObject(),minute)==1);for(int minute:new int[]{420,600,1319})check(c.test(new JSONObject(),minute)==0);
  c=new SceneOptions.Condition(condition().put("time",true).put("start",60).put("end",120));check(c.test(new JSONObject(),90)==-1);check(c.test(new JSONObject(),180)==0);
  for(String cover:SceneOptions.COVERS)if(!cover.equals("any")){c=new SceneOptions.Condition(new JSONObject().put("cover",cover));String key=cover.startsWith("stream")?"stream_near":cover.startsWith("touch")?"touch":"proximity_near";check(c.test(new JSONObject(),0)==-1);check(c.test(new JSONObject().put(key,!cover.endsWith("clear")),0)==1);check(c.test(new JSONObject().put(key,cover.endsWith("clear")),0)==0);}
  JSONObject rules=new JSONObject().put("rules",new JSONArray().put(rule("a","night_driving",condition())));SceneOptions retired=SceneOptions.parse(new JSONObject().put("scene_options",rules));check(!retired.custom());JSONObject cleaned=new JSONObject();retired.put(cleaned);check(!cleaned.getJSONObject("scene_options").has("rules"));for(boolean b:new ScenePolicy().evaluate(retired,f,0,1000))check(!b);
  // Per-scene conditional entry and exit retain confirmation and unknown fallback.
  SceneOptions custom=SceneOptions.parse(options("night_driving","condition",condition()));ScenePolicy p=new ScenePolicy();check(denied(custom,f,0,1000,0,p));check(denied(custom,f,0,2999,0,p));check(!denied(custom,f,0,3000,0,p));
  JSONObject bright=new JSONObject().put("effective",100);check(!denied(custom,bright,0,4000,0,p));check(!denied(custom,bright,0,5999,0,p));check(denied(custom,bright,0,6000,0,p));
  check(denied(custom,f,0,10000,0,p));check(!denied(custom,new JSONObject(),0,12000,0,p));check(denied(custom,f,0,13000,0,p));check(!denied(custom,f,0,15000,0,p));check(denied(custom,f,0,14000,0,p));
  p.reset();check(denied(custom,f,0,1,0,p));check(p.next==2001);p.reset();check(p.next<0&&p.states.isEmpty());
  // Legacy malformed/duplicate custom entries cannot execute or poison valid policies.
  JSONArray legacy=new JSONArray().put(123).put(rule("same","step",condition())).put(rule("same","step",condition()));JSONObject oldConfig=options("step","block",new JSONObject());oldConfig.getJSONObject("scene_options").put("rules",legacy);SceneOptions old=SceneOptions.parse(oldConfig);check(old.entries[1].mode.equals("block"));old.verify(new JSONObject().put("scene_step_supported",true));check(new ScenePolicy().evaluate(old,f,0,1)[1]);
  SceneOptions gate=SceneOptions.parse(options("night_driving","condition",condition().put("confirm",0)));check(!denied(gate,new JSONObject(),0,1,0,new ScenePolicy()));check(denied(gate,bright,0,1,0,new ScenePolicy()));check(!denied(gate,f,0,1,0,new ScenePolicy()));

  // Separate entry/exit delays, including legacy fallback and exact timer deadlines.
  c=new SceneOptions.Condition(condition().put("confirm",7000));check(c.confirmEnter==7000&&c.confirmExit==7000);
  c=new SceneOptions.Condition(condition().put("confirm_enter",300).put("confirm_exit",900));check(c.confirmEnter==300&&c.confirmExit==900);
  SceneOptions.Condition copy=new SceneOptions.Condition(c.json());check(copy.confirmEnter==300&&copy.confirmExit==900);
  for(String key:new String[]{"confirm_enter","confirm_exit"}){for(Object n:new Object[]{-1,60001,1.5,"100",true,JSONObject.NULL})bad(options("night_driving","condition",condition().put(key,n)));}
  for(int id=0;id<9;id++){
   SceneOptions split=SceneOptions.parse(options(SceneOptions.IDS[id],"condition",condition().put("confirm_enter",300).put("confirm_exit",900)));p=new ScenePolicy();
   check(denied(split,f,0,1000,id,p)&&p.next==1300);check(denied(split,f,0,1299,id,p));check(!denied(split,f,0,1300,id,p)&&p.next<0);
   check(!denied(split,bright,0,2000,id,p)&&p.next==2900);check(!denied(split,bright,0,2899,id,p));check(denied(split,bright,0,2900,id,p));
   check(denied(split,f,0,3000,id,p));check(denied(split,bright,0,3100,id,p));check(denied(split,f,0,3200,id,p)&&p.next==3500);check(!denied(split,f,0,3500,id,p));
   check(!denied(split,bright,0,4000,id,p));check(!denied(split,f,0,4100,id,p)&&p.next<0);check(!denied(split,new JSONObject(),0,4200,id,p)&&p.next<0);check(denied(split,f,0,4300,id,p)&&p.next==4600);
   JSONObject round=new JSONObject();split.put(round);check(SceneOptions.parse(round).entries[id].condition.confirmExit==900);
  }
  JSONObject s=state();JSONObject sc=s.getJSONObject("system_scene"),o=s.getJSONObject("outdoor");sc.put("reflective",true);check(!StatusLimits.low(s,1000).detail.contains("AON 反射确认"));sc.put("need_aon",true).put("need_proximity",true);check(StatusLimits.low(s,1000).detail.contains("AON 反射确认")&&StatusLimits.low(s,1000).detail.contains("流式距离遮挡确认"));
  sc.put("night_driving",true).put("step_mode",true).put("step_extra_ms",3000).put("touch_protection_active",true);String low=StatusLimits.low(s,1000).detail;for(String reason:new String[]{"夜间驾驶","运动","触摸","AON"})check(low.contains(reason));
  s.put("scene_control",new JSONObject().put("denied",new JSONObject().put("night_driving",true).put("reflection",true)));check(!StatusLimits.low(s,1000).detail.contains("夜间驾驶"));check(!StatusLimits.low(s,1000).detail.contains("AON 反射确认"));check(StatusLimits.low(s,1000).detail.contains("触摸"));
  o.put("current_limits",new JSONArray().put(new JSONObject().put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5).put("uptime_ms",1000)).put(new JSONObject().put("stage","adjustBrightnessByOpr").put("before",1).put("after",.7).put("uptime_ms",1000)));String high=StatusLimits.high(s,1000,1000).detail;check(high.contains("温控")&&high.contains("画面灰阶"));check(StatusLimits.high(s,1000,1000).actions.get(1).group==9);check(!StatusLimits.high(s,6001,1000).detail.contains("画面灰阶"));
  s=state();o=s.getJSONObject("outdoor");o.put("limit_trace",new JSONArray().put(new JSONObject().put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5).put("uptime_ms",1000)));check(!StatusLimits.high(s,1000,1000).detail.contains("温控"));
  for(JSONObject snapshot:new JSONObject[]{null,state(),state().put("elapsed_ms",-1),state().put("elapsed_ms",1001),state().put("auto_mode",false)}){StatusAdvice hi=StatusLimits.high(snapshot,1000,1000),lo=StatusLimits.low(snapshot,1000);check(!hi.detail.isEmpty()&&!lo.detail.isEmpty());check(!hi.detail.contains("\n")&&!lo.detail.contains("\n"));}
  s=state();String original=s.toString();List<SceneCatalog.Row> rows=SceneCatalog.read(s,1000);check(rows.size()>=13);for(SceneCatalog.Row row:rows){check(!row.title.isEmpty()&&!row.state.isEmpty()&&!row.condition.isEmpty()&&!row.effect.isEmpty());check(row.group<10);for(String text:new String[]{row.title,row.condition,row.effect})check(UiText.translate(text,true).codePoints().noneMatch(x->x>=0x4e00&&x<=0x9fff));}check(s.toString().equals(original));check(SceneCatalog.read(null,1000).size()==rows.size());
  JSONObject all=new JSONObject();for(int i=0;i<SceneOptions.IDS.length;i++)all.put(SceneOptions.IDS[i],new JSONObject().put("mode","condition").put("condition",condition()));JSONArray six=new JSONArray();for(int i=0;i<6;i++)six.put(rule("rule"+i,SceneOptions.IDS[i],condition()));SceneOptions max=SceneOptions.parse(new JSONObject().put("scene_options",new JSONObject().put("policies",all).put("rules",six)));JSONObject out=new JSONObject();max.put(out);String b64=Base64.getEncoder().encodeToString(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));check(b64.length()+4000<16384);JSONObject again=new JSONObject();SceneOptions.parse(out).put(again);check(again.toString().equals(out.toString()));
  s=state();sc=s.getJSONObject("system_scene");sc.put("proximity_near",true).put("proximity_policy_enabled",false);check(!StatusLimits.high(s,1000,1000).detail.contains("被遮挡"));check(!StatusLimits.low(s,1000).detail.contains("普通距离"));
  s=state();sc=s.getJSONObject("system_scene");sc.put("reflective",true).put("need_aon",false).put("game",true).put("game_darken_blocked",false);check(!SceneCatalog.summary(s,1000).contains("AON")&&!SceneCatalog.summary(s,1000).contains("游戏"));sc.put("need_aon",true);check(SceneCatalog.summary(s,1000).contains("AON"));sc.put("need_aon",false).put("stream_near",true).put("need_proximity",false);check(!SceneCatalog.summary(s,1000).contains("流式"));sc.put("need_proximity",true);check(SceneCatalog.summary(s,1000).contains("流式"));
  check(SceneCatalog.read(state(),1000).get(7).group==2);
  s=state();sc=s.getJSONObject("system_scene");sc.put("reflective",true).put("need_aon",false);check(!SceneCatalog.active("reflection",s,1000));sc.put("need_aon",true);check(SceneCatalog.active("reflection",s,1000));s.put("scene_control",new JSONObject().put("denied",new JSONObject().put("reflection",true)));check(!SceneCatalog.active("reflection",s,1000));check(!SceneCatalog.active("reflection",s,17000));
  for(SceneCatalog.Row row:SceneCatalog.read(s,1000)){check(!SceneCatalog.brief(row).isEmpty()&&SceneCatalog.brief(row).length()<45);check(!SceneCatalog.briefState(row,s,1000).isEmpty());check(UiText.translate(SceneCatalog.brief(row),true).codePoints().noneMatch(x->x>=0x4e00&&x<=0x9fff));}
  // All three output areas exist even before connection or after stale snapshots.
  for(JSONObject snapshot:new JSONObject[]{null,state(),state().put("elapsed_ms",-1),state().put("elapsed_ms",1001),state().put("auto_mode",false)}){String before=snapshot==null?"":snapshot.toString();OutputProtection.Item[] items=OutputProtection.read(snapshot,1000,1000);check(items.length==3);for(int i=0;i<3;i++){check(!items[i].state.isEmpty()&&!items[i].detail.isEmpty());check(items[i].group==new int[]{7,2,9}[i]);}check(snapshot==null||snapshot.toString().equals(before));}
  s=state();o=s.getJSONObject("outdoor");o.put("current_limits",new JSONArray().put(new JSONObject().put("stage","adjustBrightnessByThermal").put("before",1).put("after",.5).put("uptime_ms",1000)));check(OutputProtection.read(s,1000,1000)[2].state.equals("正在限亮"));check(!OutputProtection.read(s,6001,1000)[2].state.equals("正在限亮"));o.put("blocking_condition","temperature");check(OutputProtection.read(s,1000,1000)[1].state.equals("温度限制"));o.remove("blocking_condition");o.remove("current_limits");o.put("active",true);check(OutputProtection.read(s,1000,1000)[1].state.equals("增强生效中"));s.put("brightness_control",new JSONObject().put("owner","dark"));check(OutputProtection.read(s,1000,1000)[0].state.equals("锁定保持中"));
  for(Object raw:new Object[]{JSONObject.NULL,"40.0",101,-41})check(OutputProtection.temperature(state().put("battery_temperature",raw),1000).equals("温度未取得"));
  for(double t:new double[]{-40,0,37.6,100})check(OutputProtection.temperature(state().put("battery_temperature",t),1000).equals(String.format(Locale.ROOT,"%.1f ℃",t)));
  check(OutputProtection.temperature(null,1000).equals("等待读取"));check(OutputProtection.temperature(state().put("battery_temperature",38),17000).equals("状态待刷新"));check(OutputProtection.temperature(state().put("battery_temperature",38),999).equals("状态待刷新"));
  System.out.println("Scene schema/policy/catalog/limits: "+cases+" cases PASS; production Java, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
names=['OutputProtection','SceneOptions','ScenePolicy','SceneCatalog','StatusLimits','StatusAdvice','LiveLimitEvidence','StatusPresentation','CurveComparison','CurvePlan','TraditionalCurve','UiText']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in names],str(p)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.SceneTest'],check=True)
