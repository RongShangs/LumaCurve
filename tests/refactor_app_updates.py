"""Run actual update UI methods and release policy with local service doubles."""
from pathlib import Path
import os,re,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-update-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
main=(S/'MainActivity.java').read_text(encoding='utf-8');start=main.index('    void checkUpdate(');methods=main[start:main.index('    void select(',start)]
java=O/'UpdateTest.java'
java.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.util.function.*;
class TextView{String value="";void setText(String v){value=v;}}
class LinearLayout{void addView(Object v){}static class LayoutParams{LayoutParams(int w,int h){}}}
class ScrollView{ScrollView(Object host){}void addView(Object v){}void setLayoutParams(Object p){}}
class AlertDialog{Consumer<AlertDialog> dismissed;boolean visible;void setOnDismissListener(Consumer<AlertDialog> c){dismissed=c;}void show(){visible=true;}void dismiss(){visible=false;if(dismissed!=null)dismissed.accept(this);}}
class Queue{final ArrayDeque<Runnable> tasks=new ArrayDeque<>();void execute(Runnable r){tasks.add(r);}void post(Runnable r){tasks.add(r);}void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
class Preferences{Map<String,String> values=new HashMap<>();String getString(String k,String fallback){return values.getOrDefault(k,fallback);}Preferences edit(){return this;}Preferences putString(String k,String v){values.put(k,v);return this;}void apply(){}}
class UpdateChecker{static final String WEBSITE="https://lc.rongshangs.top";static JSONObject reply;static boolean fail;static int calls;static JSONObject check()throws Exception{calls++;if(fail)throw new Exception("offline");return new JSONObject(reply.toString());}}
class MainActivity{
 boolean updateBusy,updateChecked,destroyed,visible=true,permissionDialogVisible,legacyDialogVisible;Object pendingLegacy;JSONObject pendingUpdate;AlertDialog updateDialog;
 final Queue network=new Queue(),ui=new Queue();final TextView updateLabel=new TextView();final Preferences prefs;final Map<String,Runnable> buttons=new LinkedHashMap<>();String opened="",notice="";int dialogs;
 MainActivity(Preferences p){prefs=p;}Preferences getPreferences(int mode){return prefs;}String tr(String s){return s;}int dp(int n){return n;}void show(String s){notice=s;}void open(String url){opened=url;}
 LinearLayout column(){return new LinearLayout();}TextView text(String s,int size,int color){TextView t=new TextView();t.setText(s);return t;}int BLUE,MUTED;
 AlertDialog dialog(String title,Object body){dialogs++;buttons.clear();return new AlertDialog();}void offerLegacyModules(){}
 void button(String title,Runnable click,LinearLayout parent){buttons.put(title,click);}void dialogButton(AlertDialog d,String title,Runnable click,boolean primary){buttons.put(title,()->{d.dismiss();if(click!=null)click.run();});}
 void complete(){network.drain();ui.drain();}
''' + methods + r'''}
public class UpdateTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void release(String version)throws Exception{UpdateChecker.fail=false;UpdateChecker.reply=new JSONObject().put("newer",true).put("version",version).put("notes","changes").put("url","https://github.com/RongShangs/LumaCurve/releases/download/v"+version+"/HyperLux-"+version+".apk");}
 public static void main(String[] args)throws Exception{
  for(String version:new String[]{"2.5.0","v2.5.0","V2.5.0"}){check(!ReleasePolicy.shouldOffer(version,"2.5.0",false));check(ReleasePolicy.shouldOffer(version,"2.5.0",true));check(ReleasePolicy.shouldOffer(version,"2.4.0",false));}
  for(String ignored:new String[]{"",null,"bad","2.50.0"})check(ReleasePolicy.shouldOffer("2.5.0",ignored,false));
  for(String invalid:new String[]{"",null,"2.5.0-beta01","bad"})for(boolean manual:new boolean[]{false,true})check(!ReleasePolicy.shouldOffer(invalid,"",manual));
  Preferences prefs=new Preferences();release("2.5.0");MainActivity a=new MainActivity(prefs);a.checkUpdate(false);check(a.updateBusy&&a.updateDialog==null);a.complete();check(!a.updateBusy&&a.updateDialog.visible);
  check(a.buttons.keySet().equals(new LinkedHashSet<>(Arrays.asList("官网下载","GitHub 下载","稍后","忽略此版本"))));
  int requests=UpdateChecker.calls;a.checkUpdate(false);a.complete();check(UpdateChecker.calls==requests&&a.pendingUpdate==null);
  a.buttons.get("忽略此版本").run();check(a.updateDialog==null&&prefs.getString("ignored_update_version","").equals("2.5.0"));check(a.opened.isEmpty());
  a=new MainActivity(prefs);a.checkUpdate(false);a.complete();check(a.dialogs==0&&a.pendingUpdate==null&&a.updateLabel.value.endsWith("2.5.0"));
  a.checkUpdate(true);a.complete();check(a.dialogs==1&&a.updateDialog.visible);a.buttons.get("官网下载").run();check(a.opened.equals(UpdateChecker.WEBSITE)&&a.updateDialog==null);check(prefs.getString("ignored_update_version","").equals("2.5.0"));
  a.checkUpdate(true);a.complete();a.buttons.get("GitHub 下载").run();check(a.opened.equals(UpdateChecker.reply.getString("url"))&&a.updateDialog==null);
  release("2.5.1");a.checkUpdate(false);a.complete();check(a.updateDialog.visible);a.buttons.get("稍后").run();check(prefs.getString("ignored_update_version","").equals("2.5.0"));a.checkUpdate(false);a.complete();check(a.updateDialog.visible);
  a.buttons.get("忽略此版本").run();check(prefs.getString("ignored_update_version","").equals("2.5.1"));
  // A deferred automatic offer must recheck the preference before showing.
  release("2.6.0");a=new MainActivity(prefs);a.visible=false;a.checkUpdate(false);a.complete();check(a.pendingUpdate!=null&&a.dialogs==0);prefs.putString("ignored_update_version","2.6.0");a.visible=true;a.offerUpdate();check(a.updateDialog==null&&a.pendingUpdate==null);
  // Explicit checks can survive deferred permission/legacy dialogs.
  a.visible=false;a.checkUpdate(true);a.complete();a.visible=true;a.offerUpdate();check(a.updateDialog.visible);a.buttons.get("稍后").run();
  a=new MainActivity(new Preferences());a.checkUpdate(false);a.network.drain();a.destroyed=true;a.ui.drain();check(a.dialogs==0&&a.pendingUpdate==null);
  a=new MainActivity(new Preferences());UpdateChecker.fail=true;a.checkUpdate(true);a.complete();check(!a.updateBusy&&a.updateDialog==null&&!a.notice.isEmpty());
  UpdateChecker.fail=false;UpdateChecker.reply=new JSONObject().put("newer",false);a.checkUpdate(true);a.complete();check(!a.updateBusy&&a.pendingUpdate==null&&a.updateDialog==null);
  check(ReleasePolicy.trustedApk("2.5.0","HyperLux-2.5.0.apk","https://github.com/RongShangs/LumaCurve/releases/download/v2.5.0/HyperLux-2.5.0.apk"));
  check(!ReleasePolicy.trustedApk("2.5.0","HyperLux-2.5.0.apk","https://example.invalid/HyperLux-2.5.0.apk"));
  System.out.println("App update dialog: "+cases+" cases PASS; production UI methods with service doubles, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),str(S/'ReleasePolicy.java'),str(java)],check=True)
ui_result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.UpdateTest'],check=True,capture_output=True,text=True)
cases=int(re.search(r'(\d+) cases PASS',ui_result.stdout).group(1))
# Execute the production HTTP parser against an in-process connection, without network.
channel=O/'ChannelUpdateTest.java'
channel.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.net.*;import org.json.*;
public class ChannelUpdateTest{
 static String response;static int cases;
 static class Connection extends HttpURLConnection{
  Connection(URL u){super(u);}public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
  public int getResponseCode(){return 200;}public InputStream getInputStream()throws IOException{return new ByteArrayInputStream(response.getBytes("UTF-8"));}
 }
 static JSONObject release(String tag)throws Exception{String version=tag.replaceFirst("^[vV]","");String file="HyperLux-"+version+".apk";return new JSONObject().put("tag_name",tag).put("assets",new JSONArray().put(new JSONObject().put("name",file).put("browser_download_url","https://github.com/RongShangs/LumaCurve/releases/download/v"+version+"/"+file)));}
 static void check(JSONObject release,boolean expected)throws Exception{
  response=release.toString();if(UpdateChecker.check().optBoolean("newer")!=expected)throw new AssertionError(response);cases++;
 }
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Connection(u);}}:null);
  boolean preview=AppBuild.TEST||AppBuild.INTERNAL;String current=AppBuild.VERSION;String[] parts=current.split("\\.");String next=parts[0]+"."+parts[1]+"."+(Integer.parseInt(parts[2])+1);
  check(release(current),preview);check(release("v"+current),preview);check(release("V"+current),preview);
  check(release("0.0.0"),false);check(release(current+"-internal"),false);
  check(release(current).put("draft",true),false);check(release(current).put("prerelease",true),false);
  check(release(current).put("assets",new JSONArray()),false);
  JSONObject future=release(next);check(future,true);
  future.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://example.invalid/HyperLux-"+next+".apk");check(future,false);
  System.out.println(cases);
 }
}''',encoding='utf-8')
for name,test,internal in [('internal',False,True),('release',False,False),('compatibility',True,False)]:
    target=O/name;target.mkdir(exist_ok=True)
    identity=S/'AppBuild.java'
    if name!='internal':
        identity=target/'AppBuild.java'
        current=re.search(r'\bVERSION="([^"]+)"',(S/'AppBuild.java').read_text(encoding='utf-8')).group(1)
        identity.write_text('package top.rongshangs.lumacurve.refactor;final class AppBuild{static final String VERSION="'+current+'";static final boolean TEST='+str(test).lower()+',INTERNAL='+str(internal).lower()+';}',encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(target),str(identity),str(S/'ReleasePolicy.java'),str(S/'UpdateChecker.java'),str(channel)],check=True)
    result=subprocess.run(['java','-cp',str(target)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.ChannelUpdateTest'],check=True,capture_output=True,text=True)
    cases+=int(result.stdout.strip())
print('App update dialog and channels: '+str(cases)+' cases PASS; production UI/HTTP methods with local service doubles, Android not tested')
