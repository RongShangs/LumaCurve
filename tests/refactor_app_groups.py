"""Execute production group UI callbacks after configuration object replacement."""
from pathlib import Path
import os, subprocess, sys

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/application-group-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
files={
'android/view/Gravity.java':'package android.view;public class Gravity{public static final int START=1,CENTER_VERTICAL=2;}',
'android/text/InputFilter.java':'package android.text;public interface InputFilter{class LengthFilter implements InputFilter{public LengthFilter(int n){}}}',
'android/widget/TextView.java':'''package android.widget;public class TextView{public String text="";public boolean enabled=true;public void setText(String s){text=s;}public void setTextSize(int n){}public void setMinimumHeight(int n){}public void setTextColor(int c){}public void setGravity(int g){}public void setBackground(Object o){}public void setEnabled(boolean on){enabled=on;}}''',
'android/widget/Button.java':'''package android.widget;public class Button extends TextView{public Runnable action;public void click(){if(enabled)action.run();}}''',
'android/widget/Switch.java':'''package android.widget;public class Switch extends Button{public Switch(Object h){}public interface Listener{void onChange(Switch v,boolean on);}public Listener listener;boolean checked;public void setChecked(boolean on){boolean changed=checked!=on;checked=on;if(changed&&listener!=null)listener.onChange(this,on);}public void setOnCheckedChangeListener(Listener l){listener=l;}}''',
'android/widget/EditText.java':'''package android.widget;public class EditText extends TextView{public void setFilters(android.text.InputFilter[] f){}public void setSingleLine(boolean b){}public String getText(){return text;}}''',
'android/widget/LinearLayout.java':'''package android.widget;import java.util.*;public class LinearLayout{public final List<Object> children=new ArrayList<>();public void addView(Object v){children.add(v);}public void addView(Object v,LayoutParams lp){children.add(v);}public void removeAllViews(){children.clear();}public static class LayoutParams{public LayoutParams(int w,int h){}public LayoutParams(int w,int h,int weight){}}}''',
'android/app/AlertDialog.java':'''package android.app;import java.util.*;public class AlertDialog{public final Map<String,Runnable> actions=new HashMap<>();public boolean shown;public void show(){shown=true;}public void press(String key){shown=false;Runnable r=actions.get(key);if(r!=null)r.run();}}''',
'top/rongshangs/lumacurve/refactor/MainActivity.java':'''package top.rongshangs.lumacurve.refactor;
import android.app.AlertDialog;import android.widget.*;import org.json.*;
class MainActivity{
 BrightnessControlOptions controls=new BrightnessControlOptions();boolean busy,dirty;int INK,MUTED,BLUE,draws;JSONObject runtime=new JSONObject();ApplicationBrightnessSettings page;AlertDialog last;EditText input;String error="";
 String tr(String s){return s;}int dp(int n){return n;}Object ripple(int c,int r){return new Object();}
 LinearLayout column(){return new LinearLayout();}LinearLayout card(LinearLayout p){LinearLayout v=column();p.addView(v);return v;}LinearLayout row(LinearLayout p){return card(p);}
 TextView text(String s,int size,int c){TextView v=new TextView();v.setText(s);return v;}
 Button button(String s,Runnable r,LinearLayout p){Button b=new Button();b.setText(s);b.action=r;p.addView(b);return b;}Button compact(String s,Runnable r,LinearLayout p){return button(s,r,p);}
 EditText input(String hint){input=new EditText();return input;}AlertDialog dialog(String title,LinearLayout body){last=new AlertDialog();return last;}void dialogButton(AlertDialog d,String key,Runnable r,boolean primary){d.actions.put(key,r);}
 void show(String s){error=s;}void openApplicationList(String id){}void drawCurve(){draws++;if(page!=null)page.refresh();}
}''',
'top/rongshangs/lumacurve/refactor/GroupUiTest.java':r'''package top.rongshangs.lumacurve.refactor;
import android.widget.*;import android.app.AlertDialog;import org.json.*;import java.util.*;
public class GroupUiTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("group UI "+cases);cases++;}
 static MainActivity host()throws Exception{MainActivity h=new MainActivity();h.controls.manualApps.add("com.old.pause");h.controls.independentApps.add("com.old.solo");BrightnessControlOptions.AppGroup g=new BrightnessControlOptions.AppGroup("videos","Videos");g.apps.add("com.demo.a");h.controls.independentGroups.add(g);h.page=new ApplicationBrightnessSettings(h,new LinearLayout());h.page.refresh();return h;}
 static Button action(MainActivity h,int row,String label){for(Object v:((LinearLayout)h.page.groups.children.get(row)).children)if(v instanceof Button&&((Button)v).text.equals(label))return (Button)v;throw new AssertionError(label);}
 static void reload(MainActivity h)throws Exception{JSONObject j=new JSONObject();h.controls.put(j);h.controls=BrightnessControlOptions.parseStored(j);h.page.refresh();}
 public static void main(String[] args)throws Exception{
  // This is the original failure: same configuration, new objects, same visible buttons.
  MainActivity h=host();Button oldDelete=action(h,0,"删除");Object old=h.controls.independentGroups.get(0);reload(h);check(h.controls.independentGroups.get(0)!=old&&action(h,0,"删除")==oldDelete);oldDelete.click();check(h.last!=null&&h.last.shown);h.last.press("删除");check(h.controls.independentGroups.isEmpty()&&h.dirty);check(h.controls.manualApps.contains("com.old.pause")&&h.controls.independentApps.contains("com.old.solo"));
  // Repeated read-only refreshes do not require editing a selected application first.
  for(int count:new int[]{1,3,10}){h=host();oldDelete=action(h,0,"删除");for(int i=0;i<count;i++)reload(h);oldDelete.click();h.last.press("删除");check(h.controls.independentGroups.isEmpty());}
  // Resolve again at confirmation, including configuration replacement while a dialog is open.
  h=host();oldDelete=action(h,0,"删除");oldDelete.click();AlertDialog confirmation=h.last;reload(h);confirmation.press("删除");check(h.controls.independentGroups.isEmpty()&&h.dirty);
  // Rename the current object and retain its latest app membership.
  h=host();Button oldRename=action(h,0,"重命名");reload(h);oldRename.click();h.input.setText("Renamed");confirmation=h.last;reload(h);h.controls.independentGroups.get(0).apps.clear();h.controls.independentGroups.get(0).apps.add("com.demo.latest");confirmation.press("确定");check(h.controls.independentGroups.get(0).name.equals("Renamed"));check(h.controls.independentGroups.get(0).apps.equals(new LinkedHashSet<>(Arrays.asList("com.demo.latest"))));check(h.dirty);
  // Cancel neither deletes nor renames; a same-named different ID must never be affected.
  h=host();action(h,0,"删除").click();reload(h);h.last.press("取消");check(h.controls.independentGroups.size()==1&&!h.dirty);
  h=host();action(h,0,"重命名").click();h.input.setText("Wrong");reload(h);h.last.press("取消");check(h.controls.independentGroups.get(0).name.equals("Videos")&&!h.dirty);
  h=host();action(h,0,"删除").click();confirmation=h.last;h.controls.independentGroups.clear();h.controls.independentGroups.add(new BrightnessControlOptions.AppGroup("other","Videos"));confirmation.press("删除");check(h.controls.independentGroups.size()==1&&h.controls.independentGroups.get(0).id.equals("other")&&!h.dirty);
  h=host();action(h,0,"重命名").click();confirmation=h.last;h.controls.independentGroups.clear();h.controls.independentGroups.add(new BrightnessControlOptions.AppGroup("other","Videos"));h.input.setText("Wrong");confirmation.press("确定");check(h.controls.independentGroups.get(0).name.equals("Videos")&&!h.dirty);
  // The group maximum is checked again when a creation dialog is confirmed.
  h=host();h.page.add.click();confirmation=h.last;h.input.setText("Fourth");h.controls.independentGroups.add(new BrightnessControlOptions.AppGroup("g2","Two"));h.controls.independentGroups.add(new BrightnessControlOptions.AppGroup("g3","Three"));confirmation.press("确定");check(h.controls.independentGroups.size()==3&&!h.dirty);
  h=host();h.busy=true;h.page.refresh();Button disabled=action(h,0,"删除");check(!disabled.enabled);disabled.click();check(h.last==null);
  System.out.println("Application group callbacks: "+cases+" cases PASS; production UI, configuration replacement and confirmation, Android not rendered");
 }
}'''
}
paths=[]
for name,code in files.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,'utf-8');paths.append(p)
group_source=Path(sys.argv[1]) if len(sys.argv)>1 else S/'ApplicationBrightnessSettings.java'
C=O/('negative-classes' if len(sys.argv)>1 else 'classes');C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(group_source),str(S/'BrightnessControlOptions.java'),*map(str,paths)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.GroupUiTest'],check=True)
