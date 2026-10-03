"""Exercise production identity resolution and refresh handshake with service doubles.
Not an Android/ART test; no device data is distributed.
"""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-user-tests';O.mkdir(parents=True,exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
def method(file,signature):
    text=(S/file).read_text(encoding='utf-8');start=text.index(signature);opening=text.index('{',start);depth=1;end=opening+1
    while depth:
        depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]

sources={
'android/os/Binder.java':'''package android.os;public class Binder {public static boolean cleared;public static int clears,restores;public static long clearCallingIdentity(){clears++;cleared=true;return 42;}public static void restoreCallingIdentity(long v){if(v!=42)throw new AssertionError();cleared=false;restores++;}}''',
'android/os/UserHandle.java':'''package android.os;public class UserHandle {public final int id;private UserHandle(int v){id=v;}public static UserHandle of(int id){return new UserHandle(id);}}''',
'android/os/UserManager.java':'''package android.os;import java.util.*;public class UserManager {public Map<Integer,Long> serials=new HashMap<>();public boolean unavailable;public long getSerialNumberForUser(UserHandle user){if(!Binder.cleared)throw new AssertionError("caller identity leaked");if(unavailable)throw new IllegalStateException("not ready");return serials.getOrDefault(user.id,-1L);}}''',
'android/app/ActivityManager.java':'''package android.app;import android.os.Binder;public class ActivityManager {public static int user,calls;public static boolean unavailable=true;public static int getCurrentUser(){calls++;if(!Binder.cleared)throw new AssertionError("caller identity leaked");if(unavailable)throw new IllegalStateException("AMS not ready");return user;}}''',
'android/content/Context.java':'''package android.content;public class Context {public static final String USER_SERVICE="user";public Object users;public Object getSystemService(String service){if(!USER_SERVICE.equals(service))throw new AssertionError();return users;}}''',
'top/rongshangs/lumacurve/refactor/HookEntry.java':'''package top.rongshangs.lumacurve.refactor;import android.os.*;class HookEntry {static final ForegroundUser currentUser=new ForegroundUser();'''+method('HookEntry.java','static void refreshCurrentUser(')+'}',
'top/rongshangs/lumacurve/refactor/RootControl.java':'''package top.rongshangs.lumacurve.refactor;import java.util.*;import org.json.*;class RootControl {
 static final String REFRESH="refresh";JSONObject state;android.content.Context context;boolean deliver=true;Settings settings=new Settings();
 JSONObject live(){return state;}class Settings{void put(String k,String v)throws Exception{if(!REFRESH.equals(k))throw new AssertionError();if(deliver){HookEntry.refreshCurrentUser(context);state=new JSONObject().put("elapsed_ms",state.getLong("elapsed_ms")+1).put("user_serial",HookEntry.currentUser.serial());}}}
'''+method('RootControl.java','JSONObject refreshedLive(')+'}',
'top/rongshangs/lumacurve/refactor/UserIdentityTest.java':'''package top.rongshangs.lumacurve.refactor;import android.os.*;import android.app.ActivityManager;import android.content.Context;import org.json.*;
public class UserIdentityTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void refresh(Context c){HookEntry.refreshCurrentUser(c);check(!Binder.cleared&&Binder.clears==Binder.restores);}
 public static void main(String[] args)throws Exception{
  UserManager manager=new UserManager();manager.serials.put(0,0L);manager.serials.put(101,47L);Context c=new Context();c.users=manager;ForegroundUser u=HookEntry.currentUser;
  check(u.serial()==-1);check(!ForegroundUser.matches(-1,-1));check(!ForegroundUser.canApply(0,u.serial()));
  refresh(c);check(u.serial()==-1);check(u.source().equals("unavailable"));
  // Reproduce the recorded failure: AMS was not ready at startup, but is ready at save.
  ActivityManager.unavailable=false;ActivityManager.user=0;RootControl root=new RootControl();root.context=c;root.state=new JSONObject().put("elapsed_ms",1).put("user_serial",-1);
  JSONObject fresh=root.refreshedLive();check(fresh.getInt("user_serial")==0);check(fresh.getLong("elapsed_ms")==2);check(ForegroundUser.canApply(0,fresh.getInt("user_serial")));check(u.source().equals("activity_manager"));
  check(ForegroundUser.matches(0,0));check(!ForegroundUser.matches(0,-1));
  ActivityManager.user=101;refresh(c);check(u.serial()==47);check(!ForegroundUser.matches(0,u.serial()));check(!ForegroundUser.canApply(101,u.serial()));check(!ForegroundUser.canApply(0,u.serial()));
  u.switched(47);check(u.source().equals("display_user_switch"));check(ForegroundUser.matches(47,u.serial()));
  ActivityManager.unavailable=true;refresh(c);check(u.serial()==-1);check(!ForegroundUser.canApply(0,u.serial()));
  ActivityManager.unavailable=false;ActivityManager.user=0;refresh(c);check(u.serial()==0);
  manager.unavailable=true;refresh(c);check(u.serial()==-1);manager.unavailable=false;refresh(c);check(u.serial()==0);
  c.users=null;refresh(c);check(u.serial()==-1);c.users=manager;refresh(c);check(u.serial()==0);
  ActivityManager.user=-10000;refresh(c);check(u.serial()==-1);ActivityManager.user=102;refresh(c);check(u.serial()==-1);
  manager.serials.put(102,((long)Integer.MAX_VALUE)+1);refresh(c);check(u.serial()==-1);
  u.switched(-1);check(u.serial()==-1);check(!ForegroundUser.matches(-1,u.serial()));u.switched(0);check(ForegroundUser.canApply(0,u.serial()));
  u.refresh(()->{throw new IllegalStateException("late service failure");});check(u.serial()==-1);check(!ForegroundUser.canApply(0,u.serial()));
  // An unacknowledged refresh cannot turn an old unknown snapshot into a valid owner.
  root.deliver=false;root.state=new JSONObject().put("elapsed_ms",3).put("user_serial",-1);check(!ForegroundUser.canApply(0,root.refreshedLive().getInt("user_serial")));
  root.deliver=true;ActivityManager.user=0;check(ForegroundUser.canApply(0,root.refreshedLive().getInt("user_serial")));
  // No attached state: do not issue provider notifications on a missing connection.
  root.state=null;check(root.refreshedLive()==null);check(Binder.clears==Binder.restores);
  System.out.println("Foreground user identity/refresh: "+cases+" cases PASS; service doubles, device not verified");
 }
}'''}
files=[]
for name,value in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf-8');files.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(C),*[str(p) for p in files],str(S/'ForegroundUser.java')],check=True)
subprocess.run(['java','-cp',str(C)+';'+str(J),'top.rongshangs.lumacurve.refactor.UserIdentityTest'],check=True)
