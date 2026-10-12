"""Reproduce alias-dependent Activity preference files, then run the actual stable-store override."""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-ui-preferences-tests';O.mkdir(exist_ok=True)
main=(S/'MainActivity.java').read_text(encoding='utf-8')
override=next(line.strip() for line in main.splitlines() if 'SharedPreferences getPreferences(int mode)' in line)
files={
'android/content/SharedPreferences.java':r'''package android.content;import java.util.*;public class SharedPreferences{
 public final Map<String,Object> values=new HashMap<>();public int writes;
 public Map<String,?> getAll(){return new HashMap<>(values);}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}
 public Editor edit(){return new Editor();}public class Editor{Map<String,Object> pending=new HashMap<>();public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor putInt(String k,int v){pending.put(k,v);return this;}public Editor putLong(String k,long v){pending.put(k,v);return this;}public Editor putFloat(String k,float v){pending.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){pending.put(k,v);return this;}public void apply(){values.putAll(pending);writes++;}}
}''',
'android/content/Context.java':'''package android.content;import java.util.*;public class Context{public static final int MODE_PRIVATE=0;public static final Map<String,SharedPreferences> stores=new HashMap<>();public String getPackageName(){return "top.rongshangs.lumacurve";}public SharedPreferences getSharedPreferences(String n,int mode){return stores.computeIfAbsent(n,k->new SharedPreferences());}}''',
'android/app/Activity.java':'''package android.app;import android.content.*;public class Activity extends Context{final String localName;public Activity(String local){localName=local;}public SharedPreferences getPreferences(int mode){return getSharedPreferences(localName,mode);}}''',
'top/rongshangs/lumacurve/refactor/SettingsNotice.java':'''package top.rongshangs.lumacurve.refactor;class SettingsNotice{static final String ACK_KEY="settings_notice_acknowledged_v1";}''',
'top/rongshangs/lumacurve/refactor/StablePreferencesTest.java':r'''package top.rongshangs.lumacurve.refactor;
import android.app.*;import android.content.*;import java.util.*;
class SettingsActivity extends Activity{SettingsActivity(String alias){super(alias);}
'''+override+r'''
}
public class StablePreferencesTest{
 static int cases;static void check(boolean c){if(!c)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args){
  String ack=SettingsNotice.ACK_KEY;Activity oldDesktop=new Activity("Launcher"),oldLsp=new Activity("refactor.MainActivity");oldDesktop.getPreferences(0).edit().putBoolean(ack,true).putInt("refresh_seconds",3).apply();
  check(!oldLsp.getPreferences(0).getBoolean(ack,false)); // Reproduce the old repeat prompt.
  oldLsp.getPreferences(0).edit().putInt("refresh_seconds",4).putString("presets","keep").apply();
  SharedPreferences alias=oldDesktop.getPreferences(0);alias.edit().putBoolean("detail_outdoor",true).putString("draft","learned").putLong("counter",12L).putFloat("draft_floor",.25f).putStringSet("set",new HashSet<>(Arrays.asList("a","b"))).apply();
  SettingsActivity desktop=new SettingsActivity("Launcher"),lsp=new SettingsActivity("refactor.MainActivity");SharedPreferences stable=desktop.getPreferences(0);
  check(stable==lsp.getPreferences(0));check(stable.getBoolean(ack,false));check(stable.getInt("refresh_seconds",0)==4);check(stable.values.get("presets").equals("keep"));check(stable.getBoolean("detail_outdoor",false));check(stable.values.get("draft").equals("learned"));check(stable.values.get("counter").equals(12L));check(stable.values.get("draft_floor").equals(.25f));check(stable.values.get("set").equals(new HashSet<>(Arrays.asList("a","b"))));
  stable.edit().putBoolean("detail_outdoor",false).putInt("refresh_seconds",2).apply();int writes=stable.writes;
  for(int i=0;i<30;i++){SettingsActivity restarted=new SettingsActivity(i%2==0?"Launcher":"refactor.MainActivity");check(restarted.getPreferences(0)==stable);check(restarted.getPreferences(0).getBoolean(ack,false));check(restarted.getPreferences(0).getInt("refresh_seconds",0)==2);check(!restarted.getPreferences(0).getBoolean("detail_outdoor",true));}
  check(stable.writes==writes);check(alias.getBoolean(ack,false));
  // Existing acknowledgement from either route wins over an old false value.
  for(String route:new String[]{"Launcher","refactor.MainActivity","top.rongshangs.lumacurve.Launcher","top.rongshangs.lumacurve.refactor.MainActivity"}){
   Context.stores.clear();new Activity("refactor.MainActivity").getPreferences(0).edit().putBoolean(ack,false).apply();new Activity(route).getPreferences(0).edit().putBoolean(ack,true).apply();
   check(new SettingsActivity("Launcher").getPreferences(0).getBoolean(ack,false));check(new SettingsActivity("refactor.MainActivity").getPreferences(0).getBoolean(ack,false));
  }
  Context.stores.clear();desktop=new SettingsActivity("Launcher");lsp=new SettingsActivity("refactor.MainActivity");check(!desktop.getPreferences(0).getBoolean(ack,false));desktop.getPreferences(0).edit().putBoolean(ack,true).apply();check(lsp.getPreferences(0).getBoolean(ack,false));
  Context.stores.clear();check(!new SettingsActivity("Launcher").getPreferences(0).getBoolean(ack,false)); // Clearing app data still requires confirmation.
  System.out.println("Stable UI preferences: "+cases+" cases PASS; alias switches, migration and recreation with service doubles");
 }
}'''
}
for name,text in files.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(classes),str(S/'UiPreferences.java'),*[str(O/p) for p in files]],check=True)
subprocess.run(['java','-cp',str(classes),'top.rongshangs.lumacurve.refactor.StablePreferencesTest'],check=True)
