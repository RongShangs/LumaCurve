"""Execute the actual secondary page/navigation with parent-aware widget doubles."""
from pathlib import Path
import os, subprocess

R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/feature-page-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
main=(S/'MainActivity.java').read_text(encoding='utf-8')
navigation=main[main.index('    void showFeatureChecks(){'):main.index('    void makeAdvancedSettings(')]
files={
'android/view/View.java':r'''package android.view;public class View{
 public static final int VISIBLE=0,GONE=8;public int visibility=VISIBLE,minHeight;public boolean enabled=true,clickable,focusable;public ViewGroup parent;public CharSequence description="";public OnClickListener listener;
 public interface OnClickListener{void onClick(View v);}public View(Object context){}public void setBackground(Object o){}public void setBackgroundColor(int c){}public void setVisibility(int v){visibility=v;}public Object getParent(){return parent;}public void setPadding(int l,int t,int r,int b){}public void setMinHeight(int n){minHeight=n;}public void setClickable(boolean b){clickable=b;}public void setFocusable(boolean b){focusable=b;}public void setContentDescription(CharSequence s){description=s;}public void setOnClickListener(OnClickListener l){listener=l;}public boolean performClick(){if(enabled&&listener!=null){listener.onClick(this);return true;}return false;}public void setEnabled(boolean b){enabled=b;}
}''',
'android/view/ViewGroup.java':r'''package android.view;import java.util.*;public class ViewGroup extends View{
 public List<View> children=new ArrayList<>();public ViewGroup(Object c){super(c);}public void addView(View v){if(v.parent!=null)throw new IllegalStateException("already parented");v.parent=this;children.add(v);}public void addView(View v,Object p){addView(v);}public void removeView(View v){if(children.remove(v))v.parent=null;}public void removeAllViews(){for(View v:children)v.parent=null;children.clear();}public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}
}''',
'android/text/TextUtils.java':'package android.text;public class TextUtils{public enum TruncateAt{END}}',
'android/view/Gravity.java':'package android.view;public class Gravity{public static final int CENTER_VERTICAL=16;}',
'android/widget/LinearLayout.java':r'''package android.widget;import android.view.*;public class LinearLayout extends ViewGroup{
 public LinearLayout(Object c){super(c);}public static class LayoutParams{public int leftMargin,bottomMargin;public LayoutParams(int w,int h){}public LayoutParams(int w,int h,int weight){}}
}''',
'android/widget/TextView.java':r'''package android.widget;import android.view.*;public class TextView extends View{public int maxLines;public CharSequence value="";public void setIncludeFontPadding(boolean v){}public void setMaxLines(int n){maxLines=n;}public void setEllipsize(Object o){}public TextView(Object c){super(c);}public void setText(CharSequence v){value=v;}public CharSequence getText(){return value;}public void setGravity(int g){}public void setTextSize(int s){}}''',
'android/widget/Button.java':'package android.widget;public class Button extends TextView{public Button(Object c){super(c);}}',
'android/widget/ScrollView.java':r'''package android.widget;import java.util.*;import android.view.*;public class ScrollView extends ViewGroup{
 public int y;public List<Runnable> pending=new ArrayList<>();public ScrollView(Object c){super(c);}public void setFillViewport(boolean b){}public int getScrollY(){return y;}public void scrollTo(int x,int y){this.y=y;}public void post(Runnable r){pending.add(r);}public void drain(){for(Runnable r:new ArrayList<>(pending))r.run();pending.clear();}
}''',
'android/os/SystemClock.java':'package android.os;public class SystemClock{public static long elapsedRealtime(){return 10000;}}',
'top/rongshangs/lumacurve/refactor/MainActivity.java':r'''package top.rongshangs.lumacurve.refactor;
import android.view.*;import android.widget.*;import org.json.*;
class MainActivity{
 int BG,INK,MUTED,BLUE,requests,appChecks,transitions,lastDirection,preferenceWrites;boolean busy,destroyed,visible=true,featureChecksVisible,featureCheckRefreshPending,dirty=true;
 String lastRequest="",storedOptions="unchanged";JSONObject runtime,rootFeatureReport,appFeatureReport=new JSONObject();FeatureCheckPage featureCheckPage;
 LinearLayout header=column(),bottomNav=column(),content=column();TextView note=text("",12,0);View displayed;
 MainActivity(){for(int i=0;i<4;i++)content.addView(column());featureCheckPage=new FeatureCheckPage(this);content.addView(featureCheckPage.root);displayed=content.getChildAt(2);}
 LinearLayout column(){return new LinearLayout(this);}LinearLayout row(LinearLayout p){LinearLayout v=column();p.addView(v);return v;}LinearLayout card(LinearLayout p){return row(p);}TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);return t;}Button action(String label,Runnable r){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->r.run());return b;}int dp(int v){return v;}String tr(String s){return s;}Object background(int c,int r){return new Object();}
 void checkAppFeatures(){appChecks++;}void run(String name,Object payload){requests++;busy=true;lastRequest=name;}void transitionPage(View v,int direction){displayed=v;transitions++;lastDirection=direction;}
'''+navigation+'''}''',
'top/rongshangs/lumacurve/refactor/FeaturePageTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import android.view.*;import android.widget.*;
public class FeaturePageTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static JSONObject feature(String id,String name,String state){return new JSONObject().put("id",id).put("name",name).put("state",state).put("detail","实际接口检查通过");}
 static JSONObject runtime(){return new JSONObject().put("build","test").put("curve_backend","refactor").put("assist_compatibility",new JSONObject().put("state","native")).put("feature_self_check",new JSONObject().put("checked_elapsed_ms",9000).put("features",new JSONArray().put(feature("curve","基础曲线","available")).put(feature("assist_sensor","辅助光感","available")).put(feature("scene_proximity","普通距离遮挡","inactive"))));}
 static TextView find(View root,String prefix){if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(prefix))return (TextView)root;if(root instanceof ViewGroup)for(View v:((ViewGroup)root).children){TextView found=find(v,prefix);if(found!=null)return found;}return null;}
 public static void main(String[] args)throws Exception{
  MainActivity a=new MainActivity();a.runtime=runtime();String original=a.runtime.toString();a.showFeatureChecks();
  check(a.featureChecksVisible&&a.displayed==a.featureCheckPage.root);check(a.header.visibility==View.GONE&&a.bottomNav.visibility==View.GONE);check(a.lastDirection==1&&a.requests==0);
  check(a.featureCheckPage.refresh.enabled);check(a.featureCheckPage.summary.getText().toString().contains("需关注：1"));
  TextView toggle=find(a.featureCheckPage.body,"路线");check(toggle!=null&&toggle.minHeight>=48);LinearLayout card=(LinearLayout)((View)toggle.getParent()).getParent();LinearLayout details=(LinearLayout)card.getChildAt(card.children.size()-1);check(details.visibility==View.GONE&&details.getChildCount()==0);
  int requests=a.requests;toggle.performClick();check(details.visibility==View.VISIBLE);check(toggle.description.toString().startsWith("收起"));check(find(details,"Refactor 逻辑曲线")!=null&&find(details,"小米传统物理映射")!=null&&find(details,"系统标准物理映射")!=null);check(a.requests==requests&&a.runtime.toString().equals(original)&&a.preferenceWrites==0&&a.dirty);
  a.featureCheckPage.scroll.drain();a.featureCheckPage.scroll.y=700;a.busy=false;a.finishFeatureCheckRequest("self-check",new JSONObject());a.featureCheckPage.scroll.drain();check(a.featureCheckPage.scroll.y==700);check(a.featureCheckPage.refresh.enabled);check(find(a.featureCheckPage.body,"路线")!=null);check(a.featureCheckPage.summary.getText().toString().contains("需关注：1"));
  toggle=find(a.featureCheckPage.body,"路线");toggle.performClick();check(a.featureCheckPage.expanded.isEmpty());
  TextView aux=find(a.featureCheckPage.body,"辅助光感");LinearLayout auxCard=(LinearLayout)((View)aux.getParent()).getParent();TextView auxToggle=find(auxCard,"路线");check(auxToggle!=null);auxToggle.performClick();check(find(auxCard,"移植系统辅助补位")!=null);check(find(auxCard,"原生辅助槽已有效绑定")!=null);check(a.requests==requests);
  a.featureCheckPage.failed("transport failed");check(a.featureCheckPage.summary.getText().toString().contains("已有结果保留"));check(a.featureCheckPage.refresh.enabled&&find(a.featureCheckPage.body,"移植系统辅助补位")!=null);
  a.featureCheckPage.refresh.performClick();check(a.requests==requests+1&&a.busy);a.featureCheckPage.refresh.performClick();check(a.requests==requests+1);
  a.closeFeatureChecks();int transitions=a.transitions;check(!a.featureChecksVisible&&!a.featureCheckRefreshPending);check(a.header.visibility==View.VISIBLE&&a.bottomNav.visibility==View.VISIBLE&&a.displayed==a.content.getChildAt(2)&&a.lastDirection==-1);
  a.busy=false;a.finishFeatureCheckRequest("self-check",new JSONObject().put("root_feature_self_check",new JSONObject().put("features",new JSONArray())));check(a.transitions==transitions&&!a.featureChecksVisible);check(a.rootFeatureReport!=null&&a.storedOptions.equals("unchanged"));
  MainActivity waiting=new MainActivity();waiting.busy=true;waiting.showFeatureChecks();check(!waiting.featureCheckRefreshPending&&waiting.requests==0&&waiting.featureChecksVisible);waiting.refreshFeatureChecks();check(waiting.featureCheckRefreshPending&&waiting.requests==0);waiting.busy=false;waiting.refreshFeatureChecks();check(!waiting.featureCheckRefreshPending&&waiting.requests==1);waiting.closeFeatureChecks();
  MainActivity noEngine=new MainActivity();noEngine.visible=false;noEngine.showFeatureChecks();check(noEngine.requests==0&&noEngine.featureCheckPage.summary.getText().toString().contains("尚未连接"));check(find(noEngine.featureCheckPage.body,"尚未取得自检结果")!=null);
  JSONObject snapshot=new JSONObject().put("checked_elapsed_ms",1000).put("checked_unix_ms",1792288800000L).put("request","self-check:manual:token");
  String checked=a.featureCheckPage.checkedAt(snapshot);check(checked.contains("手动重检")&&!checked.contains("秒前"));check(checked.equals(a.featureCheckPage.checkedAt(snapshot)));
  snapshot.put("request","startup-readiness");check(a.featureCheckPage.checkedAt(snapshot).contains("就绪补检"));snapshot.remove("checked_unix_ms");check(a.featureCheckPage.checkedAt(snapshot).contains("本次开机后 1"));
  int inspections=a.requests;a.closeFeatureChecks();a.showFeatureChecks();a.closeFeatureChecks();a.showFeatureChecks();check(a.requests==inspections);
    // Repeated refresh must detach/reparent the summary correctly and retain route expansion.
  a.runtime=runtime();a.featureChecksVisible=true;for(int i=0;i<12;i++){a.featureCheckPage.render(a.runtime,a.rootFeatureReport,a.appFeatureReport);a.featureCheckPage.scroll.drain();check(a.featureCheckPage.summary.getParent()!=null);check(a.preferenceWrites==0&&a.dirty);}
  System.out.println("Feature check page: "+cases+" cases PASS; actual page, navigation and parent-aware widgets; Android layout not tested");
 }
}'''
}
paths=[]
for name,code in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(p) for p in paths],str(S/'CompatibilityCatalog.java'),str(S/'FeatureCompatibility.java'),str(S/'FeatureCheckPage.java')],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.FeaturePageTest'],check=True)
settings=main[main.index('    void makeRefreshSettings(){'):main.index('    JSONObject appFeatureReport')]
assert '功能自检' not in settings
assert 'button("功能自检与兼容路线",this::showFeatureChecks,b)' in main
assert 'dialog("功能自检"' not in main
assert 'if(featureChecksVisible){closeFeatureChecks();return;}' in main
assert 'page==2&&!featureChecksVisible' in main
assert 'saved.putStringArrayList("feature_checks_expanded"' in main
print('Feature page entry, back handling, no modal and no extra inspection polling PASS')
