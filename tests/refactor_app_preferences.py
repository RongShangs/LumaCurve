"""Verify launcher aliases; first-run guidance is covered by refactor_welcome.py."""
from pathlib import Path
import subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';OUT=ROOT/'build/refactor-app-preferences-tests';OUT.mkdir(exist_ok=True)
sources={'android/content/ComponentName.java': 'package android.content;public class ComponentName{public final String name;public ComponentName(Context c,String n){name=n;}}', 'android/content/Context.java': 'package android.content;import android.content.pm.PackageManager;public class Context{public final PackageManager pm=new PackageManager();public PackageManager getPackageManager(){return pm;}public String getPackageName(){return "top.rongshangs.lumacurve";}}', 'android/content/pm/PackageManager.java': 'package android.content.pm;import android.content.ComponentName;public class PackageManager{public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,COMPONENT_ENABLED_STATE_DISABLED=2,COMPONENT_ENABLED_STATE_DISABLED_USER=3,COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED=4,DONT_KILL_APP=1;public int state,flags,calls;public boolean fail;public String changed="";public int getComponentEnabledSetting(ComponentName n){return state;}public void setComponentEnabledSetting(ComponentName n,int s,int f){if(fail)throw new SecurityException("denied");state=s;flags=f;calls++;changed=n.name;}}', 'top/rongshangs/lumacurve/refactor/AppPreferencesTest.java': 'package top.rongshangs.lumacurve.refactor;\nimport android.content.*;import android.content.pm.*;\npublic class AppPreferencesTest{\n static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}\n public static void main(String[] args){\n  Context c=new Context();check(!LauncherVisibility.hidden(c));LauncherVisibility.setHidden(c,true);check(LauncherVisibility.hidden(c));check(c.pm.changed.equals("top.rongshangs.lumacurve.Launcher"));check(c.pm.flags==PackageManager.DONT_KILL_APP);\n  LauncherVisibility.setHidden(c,false);check(!LauncherVisibility.hidden(c));check(c.pm.state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED);\n  c.pm.state=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;check(LauncherVisibility.hidden(c));c.pm.state=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;check(LauncherVisibility.hidden(c));\n  c.pm.fail=true;try{LauncherVisibility.setHidden(c,false);throw new AssertionError();}catch(SecurityException expected){cases++;}check(LauncherVisibility.hidden(c));c.pm.fail=false;LauncherVisibility.setHidden(c,false);check(!LauncherVisibility.hidden(c));\n  System.out.println("App preferences: "+cases+" cases PASS; actual launcher alias control, Android not tested");\n }\n}'}
for name,text in sources.items():
 p=OUT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
C=OUT/'launcher-classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(C),str(SRC/'LauncherVisibility.java'),*[str(OUT/name) for name in sources]],check=True)
subprocess.run(['java','-cp',str(C),'top.rongshangs.lumacurve.refactor.AppPreferencesTest'],check=True)

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
