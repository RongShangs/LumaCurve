"""Run actual update UI methods and release policy with local service doubles."""
from pathlib import Path
import os,re,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-update-tests';O.mkdir(parents=True,exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
main=(S/'MainActivity.java').read_text(encoding='utf-8');start=main.index('    void finishUpdateVisit(');methods=main[start:main.index('    void select(',start)]
java=O/'UpdateTest.java'
java.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.util.function.*;
class TextView{String value="";int minimum;void setText(String v){value=v;}void setMinHeight(int v){minimum=v;}void setLineSpacing(int gap,float scale){}}
class LinearLayout{static final int VERTICAL=1;int orientation;String tag;LinearLayout parent;List<Object> children=new ArrayList<>();void addView(Object v){children.add(v);if(v instanceof LinearLayout)((LinearLayout)v).parent=this;}Object getParent(){return parent;}void setTag(String t){tag=t;}void setOrientation(int v){orientation=v;}Object findViewWithTag(String t){if(t.equals(tag))return this;for(Object v:children)if(v instanceof LinearLayout){Object found=((LinearLayout)v).findViewWithTag(t);if(found!=null)return found;}return null;}static class LayoutParams{static final int WRAP_CONTENT=-2;int height;LayoutParams(int w,int h){height=h;}}}
class Button extends TextView{LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,48);boolean singleLine=true;int maxLines=1,paddingTop,paddingBottom;Object ellipsize=new Object();Object getLayoutParams(){return lp;}void setLayoutParams(LinearLayout.LayoutParams p){lp=p;}void setSingleLine(boolean v){singleLine=v;}void setMaxLines(int n){maxLines=n;}void setEllipsize(Object e){ellipsize=e;}void setPadding(int l,int t,int r,int b){paddingTop=t;paddingBottom=b;}}
class ResponsiveDialog extends LinearLayout{float heightFraction=1;void setHeightFraction(float v){heightFraction=v;}}
class ScrollView{ScrollView(Object host){}void addView(Object v){}void setLayoutParams(Object p){}}
class Window{LinearLayout decor=new ResponsiveDialog();LinearLayout getDecorView(){return decor;}}
class AlertDialog{final Window window=new Window();Window getWindow(){return window;}Consumer<AlertDialog> dismissed;boolean visible;void setOnDismissListener(Consumer<AlertDialog> c){dismissed=c;}void show(){visible=true;}void dismiss(){visible=false;if(dismissed!=null)dismissed.accept(this);}}
class Queue{final ArrayDeque<Runnable> tasks=new ArrayDeque<>();void execute(Runnable r){tasks.add(r);}void post(Runnable r){tasks.add(r);}void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
class Preferences{Map<String,String> values=new HashMap<>();String getString(String k,String fallback){return values.getOrDefault(k,fallback);}Preferences edit(){return this;}Preferences putString(String k,String v){values.put(k,v);return this;}void apply(){}}
class UpdateChecker{static final String WEBSITE="https://lc.rongshangs.top";static JSONObject reply;static boolean fail;static int calls;static JSONObject check()throws Exception{calls++;if(fail)throw new Exception("offline");return new JSONObject(reply.toString());}}
class WelcomeGuide{boolean visible;boolean showing(){return visible;}}
class MainActivity{
 final WelcomeGuide welcome=new WelcomeGuide(),quickSetup=new WelcomeGuide();
 int page=0;boolean updateBusy,updateChecked,updateOffered,destroyed,visible=true,permissionDialogVisible,legacyDialogVisible;Object pendingLegacy;JSONObject pendingUpdate;AlertDialog updateDialog;
 final Queue network=new Queue(),ui=new Queue();final TextView updateLabel=new TextView();final Preferences prefs;final Map<String,Runnable> buttons=new LinkedHashMap<>();String opened="",notice="";int dialogs;LinearLayout dialogBody;Map<String,LinearLayout> buttonParents=new HashMap<>();
 MainActivity(Preferences p){prefs=p;}Preferences getPreferences(int mode){return prefs;}String tr(String s){return s;}int dp(int n){return n;}void show(String s){notice=s;}void open(String url){opened=url;}
 LinearLayout column(){return new LinearLayout();}LinearLayout row(LinearLayout parent){LinearLayout r=new LinearLayout();parent.addView(r);return r;}TextView text(String s,int size,int color){TextView t=new TextView();t.setText(s);return t;}int BLUE,MUTED;
 AlertDialog dialog(String title,Object body){dialogs++;buttons.clear();buttonParents.clear();dialogBody=(LinearLayout)body;AlertDialog d=new AlertDialog();LinearLayout actions=new LinearLayout();actions.setTag("actions");d.window.decor.addView(actions);return d;}void offerLegacyModules(){}
 Button compact(String title,Runnable click,LinearLayout parent){buttons.put(title,click);Button b=new Button();b.setText(title);parent.addView(b);buttonParents.put(title,parent);return b;}void dialogButton(AlertDialog d,String title,Runnable click,boolean primary){compact(title,()->{d.dismiss();if(click!=null)click.run();},(LinearLayout)d.window.decor.findViewWithTag("actions"));}
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
  check(a.dialogBody.children.size()==2&&a.dialogBody.children.get(1) instanceof TextView);check(((TextView)a.dialogBody.children.get(1)).minimum==0);
  check(((ResponsiveDialog)a.updateDialog.window.decor).heightFraction==.82f);
  check(a.buttonParents.get("官网下载")==a.buttonParents.get("GitHub 下载"));check(a.buttonParents.get("官网下载")!=a.dialogBody);
  check(a.buttonParents.get("稍后")==a.buttonParents.get("忽略此版本"));check(a.buttonParents.get("稍后")!=a.buttonParents.get("官网下载"));
  check(((LinearLayout)a.updateDialog.window.decor.findViewWithTag("update-footer")).orientation==LinearLayout.VERTICAL);
  for(LinearLayout parent:new HashSet<>(a.buttonParents.values()))for(Object child:parent.children){Button b=(Button)child;check(b.lp.height==LinearLayout.LayoutParams.WRAP_CONTENT&&!b.singleLine&&b.maxLines==Integer.MAX_VALUE&&b.ellipsize==null);check(b.paddingTop==10&&b.paddingBottom==10);}
  int requests=UpdateChecker.calls;a.checkUpdate(false);a.complete();check(UpdateChecker.calls==requests&&a.pendingUpdate==null);
  a.buttons.get("忽略此版本").run();check(a.updateDialog==null&&prefs.getString("ignored_update_version","").equals("2.5.0"));check(a.opened.isEmpty());
  a=new MainActivity(prefs);a.checkUpdate(false);a.complete();check(a.dialogs==0&&a.pendingUpdate==null&&a.updateLabel.value.endsWith("2.5.0"));
  a.checkUpdate(true);a.complete();check(a.dialogs==1&&a.updateDialog.visible);a.buttons.get("官网下载").run();check(a.opened.equals(UpdateChecker.WEBSITE)&&a.updateDialog==null);check(prefs.getString("ignored_update_version","").equals("2.5.0"));
  a.checkUpdate(true);a.complete();a.buttons.get("GitHub 下载").run();check(a.opened.equals(UpdateChecker.reply.getString("url"))&&a.updateDialog==null);
  release("2.5.1");a.finishUpdateVisit();a.checkUpdate(false);a.complete();check(a.updateDialog.visible);a.buttons.get("稍后").run();check(prefs.getString("ignored_update_version","").equals("2.5.0"));int shown=a.dialogs;a.checkUpdate(false);a.complete();check(a.updateDialog==null&&a.dialogs==shown);a.finishUpdateVisit();a.checkUpdate(false);a.complete();check(a.updateDialog.visible&&a.dialogs==shown+1);
  a.buttons.get("忽略此版本").run();check(prefs.getString("ignored_update_version","").equals("2.5.1"));
  // A deferred automatic offer must recheck the preference before showing.
  release("2.6.0");a=new MainActivity(prefs);a.visible=false;a.checkUpdate(false);a.complete();check(a.pendingUpdate!=null&&a.dialogs==0);prefs.putString("ignored_update_version","2.6.0");a.visible=true;a.offerUpdate();check(a.updateDialog==null&&a.pendingUpdate==null);
  release("2.6.1");a=new MainActivity(prefs);a.welcome.visible=true;a.checkUpdate(false);a.complete();check(a.pendingUpdate!=null&&a.updateDialog==null);a.welcome.visible=false;a.offerUpdate();check(a.updateDialog.visible);a.buttons.get("稍后").run();
  // Explicit checks can survive deferred permission/legacy dialogs.
  a.visible=false;a.checkUpdate(true);a.complete();a.visible=true;a.offerUpdate();check(a.updateDialog.visible);a.buttons.get("稍后").run();
  a=new MainActivity(new Preferences());a.checkUpdate(false);a.network.drain();a.destroyed=true;a.ui.drain();check(a.dialogs==0&&a.pendingUpdate==null);
  a=new MainActivity(new Preferences());a.page=3;UpdateChecker.fail=true;a.checkUpdate(true);a.complete();check(!a.updateBusy&&a.updateDialog==null&&!a.notice.isEmpty());
  UpdateChecker.fail=false;UpdateChecker.reply=new JSONObject().put("newer",false);a.checkUpdate(true);a.complete();check(!a.updateBusy&&a.pendingUpdate==null&&a.updateDialog==null);
  // Status auto-offers once per foreground visit. About retains its label and explicit check only.
  release("2.7.0");a=new MainActivity(new Preferences());a.page=3;int before=UpdateChecker.calls;a.checkUpdate(false);a.complete();check(UpdateChecker.calls==before+1&&a.dialogs==0&&a.pendingUpdate!=null);check(a.updateLabel.value.contains("2.7.0"));
  a.page=1;a.offerUpdate();check(a.dialogs==0);a.page=2;a.checkUpdate(false);a.offerUpdate();a.complete();check(UpdateChecker.calls==before+1&&a.dialogs==0);
  a.page=0;a.checkUpdate(false);a.complete();check(a.updateDialog!=null&&a.updateDialog.visible&&a.dialogs==1);a.buttons.get("稍后").run();
  for(int page:new int[]{1,3,2,0,3,0}){a.page=page;a.checkUpdate(false);a.complete();a.offerUpdate();check(a.dialogs==1&&a.updateDialog==null);}check(UpdateChecker.calls==before+1);
  a.finishUpdateVisit();a.page=0;a.checkUpdate(false);a.complete();check(a.dialogs==2&&a.updateDialog.visible);a.buttons.get("忽略此版本").run();a.finishUpdateVisit();a.checkUpdate(false);a.complete();check(a.dialogs==2&&a.updateDialog==null);
  a.page=3;a.checkUpdate(true);a.complete();check(a.dialogs==3&&a.updateDialog.visible);a.buttons.get("稍后").run();a.page=0;a.checkUpdate(false);a.complete();check(a.dialogs==3&&a.updateDialog==null);
  // An async completion after leaving Status must stay pending until Status returns.
  a=new MainActivity(new Preferences());a.checkUpdate(false);a.network.drain();a.page=3;a.ui.drain();check(a.dialogs==0&&a.pendingUpdate!=null);a.offerUpdate();check(a.dialogs==0);a.page=0;a.offerUpdate();check(a.dialogs==1);a.buttons.get("稍后").run();
  // Permission, legacy and quick-setup surfaces defer rather than consume the once-only offer.
  for(int blocked=0;blocked<3;blocked++){a=new MainActivity(new Preferences());a.permissionDialogVisible=blocked==0;a.legacyDialogVisible=blocked==1;a.quickSetup.visible=blocked==2;a.checkUpdate(false);a.complete();check(a.dialogs==0&&a.pendingUpdate!=null&&!a.updateOffered);a.permissionDialogVisible=a.legacyDialogVisible=a.quickSetup.visible=false;a.offerUpdate();check(a.dialogs==1&&a.updateOffered);a.buttons.get("稍后").run();a.offerUpdate();check(a.dialogs==1);}
  // Background stops close the old popup and reset only transient visit state, not ignored preferences.
  a=new MainActivity(new Preferences());a.checkUpdate(false);a.complete();a.finishUpdateVisit();check(a.updateDialog==null&&!a.updateOffered&&!a.updateChecked&&a.pendingUpdate==null);a.checkUpdate(false);a.complete();check(a.dialogs==2);
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
assert 'if(!isChangingConfigurations())finishUpdateVisit();super.onStop();' in main
assert 'updateOffered=Boolean.TRUE.equals(getLastNonConfigurationInstance())' in main
assert 'onRetainNonConfigurationInstance(){return Boolean.valueOf(updateOffered);}' in main
assert 'if((page==0||page==3)&&page!=previous&&visible)checkUpdate(false);' in main
assert 'if(page==0||page==3)checkUpdate(false);if(page==3)refreshThanks();' in main
assert 'if(page!=previous&&updateDialog!=null)updateDialog.dismiss();' in main
