"""Read-only log connection stages, independent feature counts and permission failures."""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/log-summary-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'LogSummaryTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;import org.json.*;
public class LogSummaryTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject report(String...states){JSONArray rows=new JSONArray();for(int i=0;i<states.length;i++)rows.put(new JSONObject().put("id","feature"+i).put("state",states[i]));return new JSONObject().put("features",rows);}
 static JSONObject active(){return new JSONObject().put("connected",true).put("lsp_loaded",true).put("root_access",new JSONObject().put("uid",0).put("settings",new JSONObject().put("readable",true).put("write_confirmed",false))).put("runtime",new JSONObject().put("phase","active").put("factory_lux",new JSONArray("[0,30,600,5000]")).put("feature_self_check",report("available","pending","inactive")));}
 public static void main(String[] args){
  LogCompatibilityState state=LogCompatibilityState.read(null,null,null,false);check(state.items[0].status.equals("等待读取")&&state.items[1].status.equals("待确认"));check(state.available==0&&state.pending==0&&state.unsupported==0);check(!state.title.contains("不兼容"));
  JSONObject source=active(),app=report("available","available","available"),root=report("available","unsupported");String before=source.toString(),beforeApp=app.toString(),beforeRoot=root.toString();state=LogCompatibilityState.read(source,root,app,false);check(state.items[0].status.equals("读取可用"));check(state.items[1].status.equals("已加载")&&state.items[2].status.equals("自定义生效"));check(state.available==5&&state.pending==1&&state.unsupported==1&&state.inactive==1);check(state.title.contains("正在运行"));check(source.toString().equals(before)&&app.toString().equals(beforeApp)&&root.toString().equals(beforeRoot));
  for(boolean writable:new boolean[]{false,true})for(String phase:new String[]{"active","attached","error"})for(long age:new long[]{0,14000,16000,60000}){
   JSONObject s=active();s.getJSONObject("root_access").getJSONObject("settings").put("write_confirmed",writable);s.getJSONObject("runtime").put("phase",phase);s.put("state_age_ms",age);String original=s.toString();state=LogCompatibilityState.read(s,null,app,false);check(state.items[0].status.equals(writable?"读写已确认":"读取可用"));check(state.items[1].status.equals("已加载"));check(age>15000?state.items[2].status.equals("状态待刷新"):phase.equals("active")?state.items[2].status.equals("自定义生效"):phase.equals("error")?state.items[2].status.equals("运行异常"):state.items[2].status.equals("系统基准"));check(s.toString().equals(original));
  }
  for(String issue:new String[]{"root_settings_unavailable","root_process_visibility"}){
   JSONObject restricted=new JSONObject().put("connected",false).put("hook_issue",issue).put("root_access",new JSONObject().put("uid",0));String original=restricted.toString();state=LogCompatibilityState.read(restricted,null,null,false);check(state.items[0].status.equals("部分受限"));check(state.items[1].status.equals("待确认")&&state.items[2].status.equals("待确认"));check(state.detail.contains("不表示系统中的模块已经停止"));check(state.unsupported==0);check(restricted.toString().equals(original));
  }
  for(boolean loaded:new boolean[]{false,true})for(String issue:new String[]{"","waiting_curve","curve_incompatible","restart_required"}){
   JSONObject s=new JSONObject().put("connected",false).put("lsp_loaded",loaded).put("hook_issue",issue);state=LogCompatibilityState.read(s,null,null,false);check(state.items[0].status.equals("桥接可用"));check(state.items[1].status.equals(loaded?"已加载":issue.equals("restart_required")?"等待重载":"尚未连接"));check(!state.title.contains("全部不兼容"));check(state.unsupported==0);
  }
  JSONObject missing=active();missing.getJSONObject("runtime").remove("factory_lux");state=LogCompatibilityState.read(missing,null,null,false);check(state.items[2].status.equals("接口待适配"));check(state.title.contains("曲线尚未就绪"));
  JSONObject current=active().put("root_feature_self_check",report("pending","pending"));state=LogCompatibilityState.read(current,report("unsupported"),null,false);check(state.unsupported==0&&state.pending==3);
  for(JSONObject s:new JSONObject[]{null,active(),new JSONObject().put("hook_issue","root_settings_unavailable"),new JSONObject().put("hook_issue","restart_required"),new JSONObject().put("lsp_loaded",true)}){state=LogCompatibilityState.read(s,root,app,true);for(String text:new String[]{state.title,state.detail,state.items[0].name,state.items[0].status,state.items[1].name,state.items[1].status,state.items[2].name,state.items[2].status})check(text.codePoints().noneMatch(n->n>=0x4e00&&n<=0x9fff));}
  System.out.println("Log compatibility summary: "+cases+" cases PASS; actual evidence model, no interface probes or Android rendering");
 }
}''','utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(S/'LogCompatibilityState.java'),str(p)],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.LogSummaryTest'],check=True)
panel=(S/'LogCompatibilityPanel.java').read_text('utf-8');main=(S/'MainActivity.java').read_text('utf-8')
assert 'detail.setMaxLines' not in panel and 'detail.setEllipsize' not in panel
assert 'a::showFeatureChecks' in panel and 'a.showFeatureChecks()' in panel
assert 'new LogCompatibilityPanel(this)' in main and 'logCompatibility.failed()' in main
assert 'recent 160' not in panel # runtime records remain a separate weighted region
print('Readable explanation retained, existing full-check destination and independent weighted log region: PASS')

assert 'Button check=' not in panel
assert panel.index('body.addView(detail)') < panel.index('LinearLayout stages=') < panel.index('routeEntry=a.action')
assert 'routeEntry.setBackground(a.ripple' in panel and 'routeEntry.setGravity(Gravity.CENTER)' in panel
print('Log explanation precedes three status tiles, with one blue report entry below: PASS')

assert 'routeEntry=a.action("详细兼容路线",a::showFeatureChecks)' in panel
assert 'routeEntry.setText' not in panel.replace('routeEntry.setTextSize','')
assert '最近自检' not in panel and 'Last checks' not in panel
assert 'counts.setText' not in panel and 'int total=' not in panel
print('Compatibility routes entry has one stable label, centered and without numeric summaries: PASS')
