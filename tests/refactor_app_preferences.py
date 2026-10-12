"""Run launcher visibility and first-settings acknowledgement against Android service doubles.
Tests production dialog callbacks, elapsed-time boundaries, persistence and lifecycle cleanup.
No launcher/device rendering claim.
"""
from pathlib import Path
import json, os, subprocess, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
OUT=ROOT/'build/refactor-app-preferences-tests';OUT.mkdir(parents=True,exist_ok=True)
sources={
'android/content/ComponentName.java':'''package android.content;public class ComponentName{public final String name;public ComponentName(Context c,String n){name=n;}}''',
'android/content/Context.java':'''package android.content;import android.content.pm.PackageManager;public class Context{public final PackageManager pm=new PackageManager();public PackageManager getPackageManager(){return pm;}public String getPackageName(){return "top.rongshangs.lumacurve";}}''',
'android/content/pm/PackageManager.java':'''package android.content.pm;import android.content.ComponentName;public class PackageManager{public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,COMPONENT_ENABLED_STATE_DISABLED=2,COMPONENT_ENABLED_STATE_DISABLED_USER=3,COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED=4,DONT_KILL_APP=1;public int state,flags,calls;public boolean fail;public String changed="";public int getComponentEnabledSetting(ComponentName n){return state;}public void setComponentEnabledSetting(ComponentName n,int s,int f){if(fail)throw new SecurityException("denied");state=s;flags=f;calls++;changed=n.name;}}''',
'android/content/SharedPreferences.java':'''package android.content;import java.util.*;public class SharedPreferences{final Map<String,Boolean> values=new HashMap<>();public int writes;public boolean getBoolean(String k,boolean fallback){return values.getOrDefault(k,fallback);}public SharedPreferences edit(){return this;}public SharedPreferences putBoolean(String k,boolean value){values.put(k,value);writes++;return this;}public void apply(){}}''',
'android/os/SystemClock.java':'''package android.os;public class SystemClock{public static long now=1000;public static long elapsedRealtime(){return now;}}''',
'android/os/Handler.java':'''package android.os;import java.util.*;public class Handler{public final Map<Runnable,Long> pending=new HashMap<>();public void removeCallbacks(Runnable r){pending.remove(r);}public boolean postDelayed(Runnable r,long delay){pending.put(r,SystemClock.now+delay);return true;}public void drain(){for(Runnable r:new ArrayList<>(pending.keySet()))if(pending.get(r)<=SystemClock.now){pending.remove(r);r.run();}}}''',
'android/graphics/Color.java':'''package android.graphics;public class Color{public static final int WHITE=-1;}''',
'android/graphics/Typeface.java':'''package android.graphics;public class Typeface{public static final int BOLD=1;}''',
'android/view/View.java':'''package android.view;public class View{public Object findViewWithTag(String tag){return new android.widget.LinearLayout();}}''',
'android/view/Window.java':'''package android.view;public class Window{public View getDecorView(){return new View();}}''',
'android/widget/TextView.java':'''package android.widget;public class TextView{public String value="";public void setText(String s){value=s;}public void setLineSpacing(float extra,float mult){}public void setTypeface(Object face,int style){}public void setPadding(int l,int t,int r,int b){}}''',
'android/widget/Button.java':'''package android.widget;public class Button extends TextView{public boolean enabled=true;public Runnable click;public void setEnabled(boolean e){enabled=e;}public void setAlpha(float a){}public void setTextColor(int c){}public void setBackground(Object o){}public void performClick(){if(enabled)click.run();}}''',
'android/widget/LinearLayout.java':'''package android.widget;import java.util.*;public class LinearLayout{public final List<TextView> texts=new ArrayList<>();public void addView(TextView v){texts.add(v);}}''',
'android/app/AlertDialog.java':'''package android.app;import java.util.function.Consumer;import android.view.Window;public class AlertDialog{public boolean visible,cancelable=true,outside=true;Consumer<AlertDialog> listener;public void setCancelable(boolean b){cancelable=b;}public void setCanceledOnTouchOutside(boolean b){outside=b;}public void setOnDismissListener(Consumer<AlertDialog> c){listener=c;}public void show(){visible=true;}public boolean isShowing(){return visible;}public Window getWindow(){return new Window();}public void dismiss(){visible=false;if(listener!=null)listener.accept(this);}public void back(){if(cancelable)dismiss();}public void outside(){if(outside)dismiss();}}''',
'top/rongshangs/lumacurve/refactor/MainActivity.java':'''package top.rongshangs.lumacurve.refactor;
import android.content.*;import android.app.*;import android.os.*;import android.widget.*;import java.util.*;
class MainActivity extends Context{
 boolean destroyed,visible=true,finishing,permissionDialogVisible,legacyDialogVisible,darkTheme;int page=1,MUTED,INK,BLUE,BG,dialogs,legacyOffers,updateOffers;AlertDialog updateDialog;final Set<AlertDialog> openDialogs=new HashSet<>();final Handler ui=new Handler();final SharedPreferences prefs;LinearLayout lastBody;
 MainActivity(){this(new SharedPreferences());}MainActivity(SharedPreferences p){prefs=p;}
 boolean isFinishing(){return finishing;}SharedPreferences getPreferences(int mode){return prefs;}String tr(String s){return s;}int dp(int n){return n;}LinearLayout column(){return new LinearLayout();}Object ripple(int color,int radius){return new Object();}
 TextView text(String s,int size,int color){TextView v=new TextView();v.setText(s);return v;}AlertDialog dialog(String title,LinearLayout body){dialogs++;lastBody=body;return new AlertDialog();}Button compact(String name,Runnable task,LinearLayout parent){Button b=new Button();b.setText(name);b.click=task;return b;}
 void offerLegacyModules(){legacyOffers++;}void offerUpdate(){updateOffers++;}
}''',
'top/rongshangs/lumacurve/refactor/AppPreferencesTest.java':r'''package top.rongshangs.lumacurve.refactor;
import android.app.*;import android.content.*;import android.content.pm.*;import android.os.*;import android.widget.*;
public class AppPreferencesTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args){
  Context c=new Context();check(!LauncherVisibility.hidden(c));LauncherVisibility.setHidden(c,true);check(LauncherVisibility.hidden(c));check(c.pm.changed.equals("top.rongshangs.lumacurve.Launcher"));check(c.pm.flags==PackageManager.DONT_KILL_APP);
  LauncherVisibility.setHidden(c,false);check(!LauncherVisibility.hidden(c));check(c.pm.state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
  c.pm.state=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;check(LauncherVisibility.hidden(c));c.pm.state=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;check(LauncherVisibility.hidden(c));
  c.pm.fail=true;try{LauncherVisibility.setHidden(c,false);throw new AssertionError();}catch(SecurityException expected){cases++;}check(LauncherVisibility.hidden(c));c.pm.fail=false;LauncherVisibility.setHidden(c,false);check(!LauncherVisibility.hidden(c));
  NoticeCountdown timer=new NoticeCountdown(1000);check(timer.remaining(999)==10);check(timer.remaining(1000)==10);check(timer.remaining(1001)==10);check(timer.remaining(1999)==10);check(timer.remaining(2000)==9);check(timer.remaining(10999)==1);check(timer.remaining(11000)==0);check(timer.remaining(999999)==0);
  for(int second=0;second<=10;second++)check(timer.remaining(1000+second*1000)==10-second);
  MainActivity a=new MainActivity();SettingsNotice n=new SettingsNotice(a);n.showIfNeeded();check(n.showing()&&a.dialogs==1);check(!n.current.cancelable&&!n.current.outside);check(!n.confirm.enabled&&n.confirm.value.contains("10"));check(a.prefs.writes==0&&a.ui.pending.size()==1);
  n.showIfNeeded();check(a.dialogs==1);n.current.back();n.current.outside();check(n.showing()&&a.prefs.writes==0);n.confirm.performClick();check(n.showing());n.confirm.click.run();check(n.showing()&&a.prefs.writes==0);
  SystemClock.now=10999;n.refreshCountdown();check(!n.confirm.enabled&&n.confirm.value.contains("1"));n.confirm.click.run();check(n.showing()&&a.prefs.writes==0);
  SystemClock.now=11000;n.refreshCountdown();check(n.confirm.enabled&&n.confirm.value.equals("确定"));check(n.showing()&&a.prefs.writes==0&&a.ui.pending.isEmpty());
  int[] deferred={0};n.afterDismiss=()->deferred[0]++;n.confirm.performClick();check(!n.showing()&&a.prefs.getBoolean(SettingsNotice.ACK_KEY,false));check(a.prefs.writes==1&&deferred[0]==1&&a.legacyOffers==1&&a.updateOffers==1);check(a.ui.pending.isEmpty());
  n.showIfNeeded();check(a.dialogs==1);MainActivity reopened=new MainActivity(a.prefs);new SettingsNotice(reopened).showIfNeeded();check(reopened.dialogs==0);
  // A destroyed/recreated activity must not record acknowledgement or retain its timer.
  SystemClock.now=20000;a=new MainActivity();n=new SettingsNotice(a);n.showIfNeeded();a.destroyed=true;n.current.dismiss();check(a.prefs.writes==0&&a.ui.pending.isEmpty());check(a.legacyOffers==0&&a.updateOffers==0);reopened=new MainActivity(a.prefs);n=new SettingsNotice(reopened);n.showIfNeeded();check(n.showing()&&!n.confirm.enabled);
  // Leaving the foreground stops callbacks. Returning uses elapsed time, with no auto-dismiss.
  reopened.visible=false;n.pause();check(reopened.ui.pending.isEmpty());SystemClock.now+=10000;reopened.visible=true;n.resume();check(n.confirm.enabled&&n.showing()&&reopened.prefs.writes==0);
  n.current.dismiss();check(reopened.prefs.writes==0);n.showIfNeeded();check(n.showing()&&!n.confirm.enabled);
  for(String gate:new String[]{"status","hidden","destroyed","finishing","permission","legacy","update","dialog"}){
   MainActivity gated=new MainActivity();if(gate.equals("status"))gated.page=0;if(gate.equals("hidden"))gated.visible=false;if(gate.equals("destroyed"))gated.destroyed=true;if(gate.equals("finishing"))gated.finishing=true;if(gate.equals("permission"))gated.permissionDialogVisible=true;if(gate.equals("legacy"))gated.legacyDialogVisible=true;if(gate.equals("update"))gated.updateDialog=new AlertDialog();if(gate.equals("dialog"))gated.openDialogs.add(new AlertDialog());SettingsNotice queued=new SettingsNotice(gated);queued.showIfNeeded();check(gated.dialogs==0);
   gated.page=1;gated.visible=true;gated.destroyed=gated.finishing=gated.permissionDialogVisible=gated.legacyDialogVisible=false;gated.updateDialog=null;gated.openDialogs.clear();queued.resume();check(queued.showing()&&gated.dialogs==1);
  }
  // Long text and all section labels remain present and translated.
  a=new MainActivity();n=new SettingsNotice(a);n.showIfNeeded();String all="";for(TextView t:a.lastBody.texts)all+=t.value+"\n";check(all.contains("跨机型")&&all.contains("蓝色曲线")&&all.contains("绿色曲线")&&all.contains("不影响系统其他部分的温控"));
  for(String value:new String[]{SettingsNotice.INTRO,SettingsNotice.CONFIG,SettingsNotice.USAGE,SettingsNotice.GUIDANCE,SettingsNotice.CLOSING,"用户需知","隐藏桌面图标"})check(!UiText.translate(value,true).equals(value));
  check(UiText.translate(SettingsNotice.USAGE,true).contains("green curve"));
  System.out.println("App preferences: "+cases+" cases PASS; production launcher/dialog lifecycle with doubles, Android not tested");
 }
}'''
}
for path,text in sources.items():
    file=OUT/path;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(text,encoding='utf-8')
classes=OUT/'classes';classes.mkdir(exist_ok=True)
names=['LauncherVisibility','NoticeCountdown','SettingsNotice','UiText']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(classes),*[str(SRC/(n+'.java')) for n in names],*[str(OUT/p) for p in sources]],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.AppPreferencesTest'],check=True)

# The disabled component must contain only LAUNCHER; INFO and LSPosed settings stay enabled.
ns='{http://schemas.android.com/apk/res/android}'
application=ET.parse(ROOT/'experimental/refactor_hook/AndroidManifest.xml').getroot().find('application')
main=next(n for n in application.findall('activity') if n.get(ns+'name').endswith('.MainActivity'))
alias=next(n for n in application.findall('activity-alias') if n.get(ns+'name').endswith('.Launcher'))
categories=lambda n:{e.get(ns+'name') for e in n.findall('intent-filter/category')}
assert categories(alias)=={'android.intent.category.LAUNCHER'}
assert alias.get(ns+'targetActivity')==main.get(ns+'name') and alias.get(ns+'enabled')=='true'
assert categories(main)=={'android.intent.category.INFO','de.robv.android.xposed.category.MODULE_SETTINGS'}
assert main.get(ns+'exported')=='true' and main.get(ns+'enabled','true')=='true'
print('Launcher manifest: independent enabled module settings and toggleable launcher PASS')
