"""Actual advice navigation and configuration serialization with a deferred layout model.
No Android rendering claim. Targets must resolve after layout, without changing options.
"""
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-navigation-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
main=(S/'MainActivity.java').read_text(encoding='utf-8')
def method(start,end):
 a=main.index(start);return main[a:main.index(end,a)]
navigation=method('    void openAdvice(','    android.text.SpannableStringBuilder adviceText(')+method('    void positionScene(','    void branches(){')
configuration=method('    JSONObject configuration(','    void exportConfiguration(')
settings_navigation=method('    void showSettingsGroup(','    void openAdvice(')
test=O/'NavigationTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import java.nio.charset.StandardCharsets;import org.json.*;
class Rect{int top;}
class ViewTreeObserver {
 interface OnPreDrawListener{boolean onPreDraw();}List<OnPreDrawListener> listeners=new ArrayList<>();
 void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}boolean isAlive(){return true;}
 void draw(){for(OnPreDrawListener l:new ArrayList<>(listeners))if(!l.onPreDraw())throw new AssertionError("drawing cancelled");}
}
class View{static final int VISIBLE=0,GONE=8;int top,focuses,visibility;boolean shown=true;View tagged;void setVisibility(int v){visibility=v;}View findViewWithTag(String tag){return tagged;}int getTop(){return top;}void getDrawingRect(Rect r){r.top=0;}boolean isShown(){return shown;}void requestFocus(){focuses++;}}
class ScrollView extends View{ViewTreeObserver tree=new ViewTreeObserver();int moves,y;int getScrollY(){return y;}void post(Runnable r){r.run();}void invalidate(){}ViewTreeObserver getViewTreeObserver(){return tree;}void offsetDescendantRectToMyCoords(View v,Rect r){r.top+=v.top;}void smoothScrollTo(int x,int y){moves++;this.y=y;}void scrollTo(int x,int y){moves++;this.y=y;}}
class Window{final View decor=new View();View getDecorView(){return decor;}}
class AlertDialog{Window window=new Window();boolean showing=true;Window getWindow(){return window;}boolean isShowing(){return showing;}}
class SceneSettings {View target;void commit(){}View find(String key){return target;}}
class MainActivity {
 final Map<String,Runnable> detailOpeners=new HashMap<>();final ScrollView[] settingsScrollers=new ScrollView[10];final View[] settingScreens=new View[10];
 View displayedPage,rangeSwitch=new View(),oprSwitch=new View(),thermalSwitch=new View(),darkLockSwitch=new View(),lowLightSwitch=new View(),settingsGraph=new View(),memorySwitch=new View();boolean destroyed,dirty=true;int page,group;
 float[] factoryLux={0,30,600,100000},factoryNit={2,100,300,1060},factors={1,1,1,1};float minimum=1,maximum=1060,curveFloor=0,thermalCeiling=43,memoryStrength=1,memoryLuxRange=.3f,thermalCooling=1,lowLightLimit=50;
 boolean thermalRelax,memoryEnabled=true,responseOverride,smallBrightenOverride,lowLightStability;long memoryWindow=1500,brightenDelay=1500,darkenDelay=5000,smallBrightenDelay=5000,lowLightBrighten=3000,lowLightDarken=4000;
 SceneSettings sceneSettings;SceneOptions sceneOptions=new SceneOptions();AdvancedOptions advanced=new AdvancedOptions();OutdoorOptions outdoorOptions=new OutdoorOptions();BrightnessControlOptions controls=new BrightnessControlOptions();LowLightThresholds lowThresholds=new LowLightThresholds();LowLightAssistGate assistGateOptions=new LowLightAssistGate();MemoryOptions memoryOptions=new MemoryOptions(true,30);
 MainActivity(){for(int i=0;i<10;i++){settingsScrollers[i]=new ScrollView();settingScreens[i]=new View();}}
 int dp(float x){return Math.round(x);}void select(int p){page=p;}void showSettingsGroup(int g){group=g;displayedPage=settingScreens[g];}
''' +navigation+configuration+r'''}
class HomeScroll extends ScrollView{void requestFocus(){super.requestFocus();y=0;}}
class Container{View home;View getChildAt(int at){return home;}}
class SettingsNavigation{
 ScrollView settingsHomeScroll=new HomeScroll();int settingsHomeY,page=1,settingsGroup=-1;long settingsScrollRestore;boolean settingsHomeRestoring,destroyed;
 View displayedPage=settingsHomeScroll,header=new View(),bottomNav=new View();View[] settingScreens=new View[10];Container content=new Container();
 SettingsNavigation(){content.home=settingsHomeScroll;for(int i=0;i<10;i++)settingScreens[i]=new View();}
 void transitionPage(View target,int direction){displayedPage=target;}void drawCurve(){}
''' +settings_navigation+r'''}
public class NavigationTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args)throws Exception{
  SettingsNavigation settings=new SettingsNavigation();ScrollView home=settings.settingsHomeScroll;
  for(int position:new int[]{12,450,900}){home.y=position;settings.showSettingsGroup(5);check(settings.settingsHomeY==position&&settings.displayedPage==settings.settingScreens[5]);check(settings.header.visibility==View.GONE&&settings.bottomNav.visibility==View.GONE);home.y=0;settings.showSettingsGroup(-1);check(home.y==0&&settings.settingsHomeRestoring);home.tree.draw();check(home.y==position&&!settings.settingsHomeRestoring);check(home.tree.listeners.isEmpty()&&settings.header.visibility==View.VISIBLE&&settings.bottomNav.visibility==View.VISIBLE);}
  // A quick second navigation must cancel pending restoration without replacing
  // the saved home position with the temporary focus-induced offset of zero.
  home.y=680;settings.showSettingsGroup(2);settings.showSettingsGroup(-1);int moves=home.moves;settings.showSettingsGroup(7);home.tree.draw();check(home.moves==moves&&settings.settingsHomeY==680);settings.showSettingsGroup(-1);home.tree.draw();check(home.y==680);
  settings.showSettingsGroup(5);settings.showSettingsGroup(-1);moves=home.moves;settings.rememberSettingsHome();settings.page=0;settings.displayedPage=new View();home.tree.draw();check(home.moves==moves&&settings.settingsHomeY==680);
  settings.page=1;settings.showSettingsGroup(-1);settings.destroyed=true;moves=home.moves;home.tree.draw();check(home.moves==moves&&!settings.settingsHomeRestoring);settings.destroyed=false;settings.restoreSettingsHome();home.tree.draw();check(home.y==680);settings.settingsHomeScroll=null;settings.rememberSettingsHome();settings.restoreSettingsHome();check(!settings.settingsHomeRestoring);
  // Dialog content is reparented by ResponsiveDialog. Position the actual viewport
  // only after layout; a detached input ScrollView must not receive the command.
  MainActivity scene=new MainActivity();AlertDialog dialog=new AlertDialog();ScrollView actual=new ScrollView(),detached=new ScrollView();dialog.window.decor.tagged=actual;View active=new View();
  scene.positionScene(dialog,active);check(actual.moves==0&&detached.moves==0);active.top=800;actual.tree.draw();check(actual.moves==1&&actual.y==772&&detached.moves==0);actual.tree.draw();check(actual.moves==1&&actual.tree.listeners.isEmpty());
  scene.positionScene(dialog,active);dialog.showing=false;actual.tree.draw();check(actual.moves==1);dialog.showing=true;active.shown=false;scene.positionScene(dialog,active);actual.tree.draw();check(actual.moves==1);active.shown=true;active.top=10;scene.positionScene(dialog,active);actual.tree.draw();check(actual.y==0);
  scene.positionScene(dialog,null);check(actual.tree.listeners.isEmpty());dialog.window.decor.tagged=new View();scene.positionScene(dialog,active);check(actual.tree.listeners.isEmpty());dialog.window=null;scene.positionScene(dialog,active);check(actual.tree.listeners.isEmpty());
  String[] targets={"range","opr","thermal","dark_lock","low_light","curve","memory"};int[] groups={2,2,9,7,7,0,1};
  for(int i=0;i<targets.length;i++){
   MainActivity a=new MainActivity();String before=a.configuration().toString();int[] expanded={0};a.detailOpeners.put("outdoor",()->expanded[0]++);a.detailOpeners.put("low",()->expanded[0]++);
   View target=i==0?a.rangeSwitch:i==1?a.oprSwitch:i==2?a.thermalSwitch:i==3?a.darkLockSwitch:i==4?a.lowLightSwitch:i==5?a.settingsGraph:a.memorySwitch;ScrollView scroll=a.settingsScrollers[groups[i]];
   a.openAdvice(new StatusAdvice.Action("link",groups[i],targets[i]));check(a.page==1&&a.group==groups[i]);check(scroll.moves==0&&target.focuses==0);check(expanded[0]==(i<2||i==4?1:0));
   // The target acquires its real offset only in the next layout traversal.
   target.top=700;scroll.tree.draw();check(scroll.moves==1&&scroll.y==684&&target.focuses==1);check(scroll.tree.listeners.isEmpty());scroll.tree.draw();check(scroll.moves==1);check(a.dirty&&before.equals(a.configuration().toString()));
  }
  for(int mode=0;mode<3;mode++){MainActivity a=new MainActivity();a.openAdvice(new StatusAdvice.Action("link",2,"range"));if(mode==0)a.displayedPage=new View();if(mode==1)a.destroyed=true;if(mode==2)a.rangeSwitch.shown=false;a.settingsScrollers[2].tree.draw();check(a.settingsScrollers[2].moves==0&&a.rangeSwitch.focuses==0);check(a.settingsScrollers[2].tree.listeners.isEmpty());}
  MainActivity a=new MainActivity();a.openAdvice(new StatusAdvice.Action("conditions",2,""));check(a.settingsScrollers[2].tree.listeners.isEmpty());check(a.dirty);
  for(boolean extreme:new boolean[]{false,true}){
   a=new MainActivity();if(extreme){a.advanced.values[5]=2000;a.outdoorOptions.values[1]=100000;a.factors=new float[]{1.234567f,1.234567f,1.234567f,1};a.curveFloor=1.234567f;a.thermalCeiling=50;a.memoryWindow=3000;a.memoryLuxRange=.1234567f;a.controls.exitLux=500;a.controls.exitSeconds=30;a.controls.minutes=30;}
   String options=a.configuration().toString(),payload=Base64.getEncoder().encodeToString(options.getBytes(StandardCharsets.UTF_8));String request=new JSONObject().put("command","apply").put("payload",payload).toString();
   check(payload.length()<=16384);check(request.length()<=16384);System.out.println("Apply request frame: "+request.length()+" chars");
  }

  for(int id=0;id<9;id++){MainActivity sceneNav=new MainActivity();sceneNav.sceneSettings=new SceneSettings();View target=new View();sceneNav.sceneSettings.target=target;String key="scene_"+SceneOptions.IDS[id];int group=id==7?2:5;int[] expanded={0};sceneNav.detailOpeners.put(key,()->expanded[0]++);sceneNav.openAdvice(new StatusAdvice.Action("scene",group,key));check(expanded[0]==1&&sceneNav.group==group);target.top=900;sceneNav.settingsScrollers[group].tree.draw();check(sceneNav.settingsScrollers[group].y==884&&target.focuses==0);}
  System.out.println("Advice navigation: "+cases+" cases PASS; actual navigation/configuration with deferred layout doubles; Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
names=['SceneOptions','CurvePlan','CurveComparison','TraditionalCurve','ThermalPolicy','MemoryPolicy','MemoryOptions','AdvancedOptions','AdvancedPolicy','OutdoorOptions','BrightnessControlOptions','LowLightThresholds','LowLightAssistGate','StatusAdvice','LiveLimitEvidence']
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),*[str(S/(n+'.java')) for n in names],str(test)],check=True)
subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.NavigationTest'],check=True)
