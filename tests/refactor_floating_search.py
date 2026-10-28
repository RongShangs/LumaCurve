"""Production UI policy/filter/inset/animation bodies on host widgets, not Android rendering."""
from pathlib import Path
import json,re,os,subprocess,hashlib
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/floating-search-tests';O.mkdir(exist_ok=True)
def method(file,signature):
    text=(S/file).read_text('utf-8');start=text.index(signature);at=text.index('{',start);depth=1;end=at+1
    while depth:depth+=int(text[end]=='{')-int(text[end]=='}');end+=1
    return text[start:end]
search='\n'.join(method('SettingsSearch.java',s) for s in ['void filter(','void open(','void finishAnimation(','void revealMode('])
search=search.replace('android.animation.ValueAnimator','ValueAnimator').replace('android.view.animation.DecelerateInterpolator','DecelerateInterpolator')
motion='\n'.join(method('FloatingNavigation.java',s) for s in ['void applyScale(','void press(','void touch(','void animateVisibilityScale(','float distance(','void present(','void show(','void move(','void watch(','void stop('])
test=r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;
class View{
 static final int VISIBLE=0,INVISIBLE=4,GONE=8,OVER_SCROLL_NEVER=2,IMPORTANT_FOR_ACCESSIBILITY_AUTO=0,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
 int visibility=VISIBLE,accessibility,height=72;float y,alpha=1,sx=1,sy=1;Runnable click;Object params;int[] padding=new int[4];Animator animator=new Animator(this);
 interface Click{void click(View v);}void setOnClickListener(Click c){click=()->c.click(this);}void setVisibility(int v){visibility=v;}void setPadding(int l,int t,int r,int b){padding=new int[]{l,t,r,b};}void setBackground(Object b){}void setClipToOutline(boolean b){}void setClickable(boolean b){}void setFocusable(boolean b){}void setImportantForAccessibility(int i){accessibility=i;}void setTranslationY(float y){this.y=y;}void setAlpha(float a){alpha=a;}Animator animate(){return animator;}int getVisibility(){return visibility;}float getTranslationY(){return y;}float getAlpha(){return alpha;}int getHeight(){return height;}int getWidth(){return 300;}void setScaleX(float x){sx=x;}void setScaleY(float y){sy=y;}
}
class Animator{View view;Runnable end;int starts;float y,alpha;Animator(View v){view=v;}Animator setListener(Object l){return this;}Animator withEndAction(Runnable r){end=r;return this;}void cancel(){end=null;}Animator translationY(float y){this.y=y;return this;}Animator alpha(float a){alpha=a;return this;}Animator setInterpolator(Object d){return this;}Animator setDuration(long n){return this;}void start(){starts++;view.y=y;view.alpha=alpha;}void finish(){Runnable r=end;end=null;if(r!=null)r.run();}}
class ValueAnimator{interface Update{void update(ValueAnimator v);}static boolean enabled=true;static boolean areAnimatorsEnabled(){return enabled;}float from,to,current;long duration;boolean cancelled;Object ease;Update update;static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.from=v.current=a;v.to=b;return v;}void cancel(){cancelled=true;}void setDuration(long d){duration=d;}void setInterpolator(Object e){ease=e;}void addUpdateListener(Update u){update=u;}Object getAnimatedValue(){return current;}void start(){}void tick(float f){if(cancelled)return;current=from+(to-from)*f;update.update(this);}}
class DecelerateInterpolator{}class OvershootInterpolator{OvershootInterpolator(float tension){}}
class MotionEvent{static int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3,ACTION_MOVE=2;int action;MotionEvent(int a){action=a;}int getActionMasked(){return action;}}
class SystemClock{static long now;static long uptimeMillis(){return now;}}
class LinearLayout extends View{List<View> children=new ArrayList<>();static class LayoutParams{LayoutParams(int w,int h){}LayoutParams(int w,int h,int weight){}}void addView(View v){children.add(v);}void addView(View v,LayoutParams p){children.add(v);v.params=p;}void removeAllViews(){children.clear();}}
class TextView extends View{String value;TextView(String s){value=s;}}
class Color{static int WHITE=1;}
class ScrollView extends View{interface Touch{boolean touch(View v,MotionEvent e);}interface Change{void changed(View v,int x,int y,int oldX,int oldY);}Touch touch;Change change;int position;void setOverScrollMode(int mode){}void setOnTouchListener(Touch t){touch=t;}void setOnScrollChangeListener(Change c){change=c;}int getScrollY(){return position;}void scrollTo(int x,int y){int old=position;position=y;if(change!=null)change.changed(this,x,y,0,old);}void event(int action){touch.touch(this,new MotionEvent(action));}}
class AboutPageStub{void active(boolean value){}}
class ElasticScrollView extends ScrollView{void observeGesture(java.util.function.Consumer<MotionEvent> listener){setOnTouchListener((v,e)->{listener.accept(e);return false;});}}
class FakeViewport{int calls;void refresh(){calls++;}}
class QuickSetup{int opens;void open(){opens++;}}
class StatusAdvice{static class Action{int group;String target;Action(String text,int g,String t){group=g;target=t;}}}
class MainActivity{
 boolean visible=true,english,featureChecksVisible,settingsHomeRestoring,dirty=true;int page=1,settingsGroup=-1,settingsHomeY=440,INK,MUTED,BLUE;long settingsScrollRestore;String savedOptions="unchanged",draft="unapplied";View root=new View(),header=new View(),bottomNav;SettingsSearchCandidate settingsSearch;MotionCandidate floatingNavigation;FakeViewport viewport=new FakeViewport();AboutPageStub aboutPage=new AboutPageStub();QuickSetup quickSetup=new QuickSetup();LinearLayout settingsActions=new LinearLayout(),settingsHome=new LinearLayout();ScrollView settingsHomeScroll=new ScrollView();StatusAdvice.Action action;int selected=-1,checks;
 MainActivity(){floatingNavigation=new MotionCandidate(this);bottomNav=floatingNavigation.view;settingsSearch=new SettingsSearchCandidate(this);}
 int dp(float n){return Math.round(n);}LinearLayout column(){return new LinearLayout();}LinearLayout card(LinearLayout p){LinearLayout b=column();p.addView(b);return b;}LinearLayout row(LinearLayout p){return card(p);}TextView text(String t,int size,int c){return new TextView(t);}Object ripple(int c,int r){return new Object();}class NavigationArrow extends View{NavigationArrow(boolean b){}}void openAdvice(StatusAdvice.Action action){this.action=action;}void select(int page){selected=page;}void showFeatureChecks(){checks++;}void restoreSettingsHome(){settingsHomeScroll.scrollTo(0,settingsHomeY);}
 CHROME
}
class SettingsSearchCandidate{
 MainActivity a;String input="";boolean restoring,searching;int homePosition;LinearLayout results=new LinearLayout(),header=new LinearLayout();TextView clear=new TextView("");int blurs;SettingsSearchCandidate(MainActivity a){this.a=a;}String query(){return input;}void blur(){blurs++;}
 SEARCH
}
class MotionCandidate{
 MainActivity a;View view=new View();NavigationPolicy motion=new NavigationPolicy();boolean present=true,keyboard,hidden,pressed;float pressScale=1,visibilityScale=1;ValueAnimator pressAnimation,visibilityAnimation;int bottom=24;long generation;MotionCandidate(MainActivity a){this.a=a;}
 void finishSelection(){}MOTION
}
class Safe{int left,top,right,bottom;Safe(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
class InsetsCandidate{MainActivity a;Safe safe;boolean keyboard;int ime;InsetsCandidate(MainActivity a){this.a=a;}
 INSETS
}
public class FloatingSearchTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}static void unchanged(MainActivity a){check(a.savedOptions.equals("unchanged")&&a.draft.equals("unapplied")&&a.dirty);}
 static SettingsSearchIndex.Entry find(String title){for(SettingsSearchIndex.Entry e:SettingsSearchIndex.ENTRIES)if(e.title.equals(title))return e;throw new AssertionError(title);}
 public static void main(String[] args){
  boolean[] groups=new boolean[11];for(SettingsSearchIndex.Entry entry:SettingsSearchIndex.ENTRIES){check(entry.group>=0&&entry.group<11);groups[entry.group]=true;check(SettingsSearchIndex.find(entry.title).contains(entry));check(SettingsSearchIndex.find(entry.enTitle.toUpperCase(Locale.ROOT)).contains(entry));check(!entry.description(true).isEmpty()&&!entry.section(true).isEmpty());}
  for(boolean group:groups)check(group);for(String empty:new String[]{null,"","   ",".*","——"})check(SettingsSearchIndex.find(empty).isEmpty());
  check(SettingsSearchIndex.find("永久保持").get(0)==find("永久保持记忆"));check(SettingsSearchIndex.find("日落 后 锁定").contains(find("日落后锁定")));check(SettingsSearchIndex.find("ＨＢＭ").contains(find("HBM 触发与预算")));check(SettingsSearchIndex.find("控制中心节点映射").contains(find("控制中心亮度条节点映射")));check(SettingsSearchIndex.find("深色模式").contains(find("深色模式基础曲线")));check(SettingsSearchIndex.find("no such option qxyz").isEmpty());
  MainActivity a=new MainActivity();SettingsSearchCandidate search=a.settingsSearch;a.settingsHomeScroll.position=440;search.input="永久保持";search.filter(false);check(a.settingsActions.visibility==View.GONE&&a.settingsHome.visibility==View.GONE&&search.results.visibility==View.VISIBLE);check(a.settingsHomeY==440);check(search.results.children.size()>0);search.results.children.get(0).click.run();check(a.action.group==1&&a.action.target.equals("memory")&&search.blurs==1);unchanged(a);
  search.input="温控";search.filter(true);check(a.settingsHomeY==0&&a.settingsHomeScroll.position==0&&a.settingsScrollRestore==1);unchanged(a);
  search.input="qxyz_not_existing";search.filter(true);check(search.results.children.size()==1);check(((TextView)((LinearLayout)search.results.children.get(0)).children.get(0)).value.equals("没有找到相关功能"));unchanged(a);
  search.input="";search.filter(true);check(a.settingsActions.visibility==View.VISIBLE&&a.settingsHome.visibility==View.VISIBLE&&search.results.visibility==View.GONE&&search.clear.visibility==View.GONE);check(a.settingsHomeScroll.position==440&&a.settingsHomeY==440);unchanged(a);
  a.english=true;search.input="SUNSET";search.filter(false);check(search.results.children.size()>0);LinearLayout card=(LinearLayout)search.results.children.get(0),line=(LinearLayout)card.children.get(0),copy=(LinearLayout)line.children.get(0);check(((TextView)copy.children.get(0)).value.equals("Lock after sunset"));
  search.open(find("功能自检与兼容路线"));check(a.selected==2&&a.checks==1);search.open(find("日志与分析包"));check(a.selected==2&&a.checks==1);search.open(find("快速设置"));check(a.quickSetup.opens==1);unchanged(a);
  for(int width:new int[]{240,280,320,360,412,600,900,1280})for(int bottom:new int[]{0,16,24,48})for(int h:new int[]{88,100,116}){int bar=NavigationPolicy.width(width,12,312);check(bar>0&&bar<=width-24&&bar<=312);check(NavigationPolicy.reserve(h,12,bottom)==h+12+bottom);}
  for(int hide:new int[]{12,18,24})for(int show:new int[]{6,9,12}){NavigationPolicy p=new NavigationPolicy();check(!p.scroll(4,0,hide,show));check(!p.scroll(7,4,hide,show));check(p.scroll(100,7,hide,show));check(p.scroll(99,100,hide,show));check(!p.scroll(80,99,hide,show));check(!p.scroll(0,80,hide,show));}
  MotionCandidate n=a.floatingNavigation;ScrollView scroll=new ScrollView();n.watch(scroll,1);a.page=1;a.settingsGroup=-1;a.featureChecksVisible=false;scroll.scrollTo(0,100);check(!n.hidden);scroll.event(MotionEvent.ACTION_DOWN);scroll.scrollTo(0,150);check(n.hidden);Runnable stale=n.view.animator.end;scroll.scrollTo(0,130);check(!n.hidden);stale.run();check(n.view.visibility==View.VISIBLE);n.view.animator.finish();check(n.view.y==0&&n.view.alpha==1);
  scroll.scrollTo(0,180);n.view.animator.finish();check(n.view.visibility==View.INVISIBLE&&n.view.accessibility==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);scroll.event(MotionEvent.ACTION_UP);SystemClock.now+=2000;scroll.scrollTo(0,200);check(n.hidden);scroll.scrollTo(0,0);check(n.hidden);scroll.event(MotionEvent.ACTION_DOWN);scroll.scrollTo(0,200);check(n.hidden);scroll.scrollTo(0,180);check(!n.hidden);
  a.settingsHomeRestoring=true;scroll.event(MotionEvent.ACTION_DOWN);scroll.scrollTo(0,200);check(!n.hidden);a.settingsHomeRestoring=false;a.page=2;scroll.scrollTo(0,300);check(!n.hidden);a.page=1;n.keyboard=true;scroll.scrollTo(0,400);check(!n.hidden);n.keyboard=false;
  n.move(true,false);n.view.animator.finish();n.present(false);check(n.view.visibility==View.GONE);n.present(true);check(n.view.visibility==View.VISIBLE&&!n.hidden);ValueAnimator.enabled=false;n.move(true,false);check(n.view.visibility==View.INVISIBLE);n.stop();check(n.view.visibility==View.VISIBLE&&n.view.y==0);ValueAnimator.enabled=true;
  InsetsCandidate insets=new InsetsCandidate(a);for(int bottom:new int[]{0,24,48})for(int ime:new int[]{0,240,400})for(boolean secondary:new boolean[]{false,true}){insets.safe=new Safe(10,28,12,bottom);insets.keyboard=ime>0;insets.ime=ime;a.settingsGroup=secondary?0:-1;insets.apply(a.root);check(a.root.padding[0]==10&&a.root.padding[1]==28&&a.root.padding[2]==12);check(a.root.padding[3]==(ime>0?ime:0));View tutorial=new View();insets.apply(tutorial);check(tutorial.padding[3]==Math.max(bottom,ime));}
  for(int page=0;page<4;page++)for(boolean secondary:new boolean[]{false,true}){a.page=page;a.settingsGroup=secondary?0:-1;a.featureChecksVisible=false;a.syncPageChrome();check(a.header.visibility==(page==0&&!secondary?View.VISIBLE:View.GONE));check(search.header.visibility==(page==1&&!secondary?View.VISIBLE:View.GONE));check(a.bottomNav.visibility==(secondary?View.GONE:View.VISIBLE));}
  // The capsule halo is included in its view, but gesture-only insets are not added again.
  int statusSpace=800-24-64-8-(NavigationPolicy.reserve(88,12,0)-12+8);check(statusSpace>=StatusPageLayout.minimumHeight(1,1));unchanged(a);
  MainActivity animated=new MainActivity();SettingsSearchCandidate f=animated.settingsSearch;f.input="亮度";f.filter(true);check(f.results.animator.starts==1);f.input="温控";f.filter(true);check(f.results.animator.starts==1);f.input="";f.filter(true);check(animated.settingsHome.animator.starts==1&&animated.settingsActions.animator.starts==1);f.finishAnimation();check(animated.settingsHome.alpha==1&&animated.settingsHome.y==0);ValueAnimator.enabled=false;f.input="日落";f.filter(true);check(f.results.animator.starts==1&&f.results.alpha==1);ValueAnimator.enabled=true;unchanged(animated);
  a.visible=true;a.settingsGroup=-1;n.keyboard=false;n.present=true;n.show(true);
  n.touch(MotionEvent.ACTION_DOWN,20,20);ValueAnimator hold=n.pressAnimation;check(n.pressed&&hold.to==.97f&&hold.duration==85);hold.tick(1);check(n.view.sx==.97f&&n.view.sy==.97f);n.touch(MotionEvent.ACTION_UP,20,20);ValueAnimator release=n.pressAnimation;check(hold.cancelled&&release.to==1&&release.duration==260&&release.ease instanceof OvershootInterpolator);release.tick(1);check(n.view.sx==1&&!n.pressed);
  n.touch(MotionEvent.ACTION_DOWN,20,20);n.pressAnimation.tick(.5f);float pressedScale=n.pressScale;n.move(true,false);ValueAnimator shrink=n.visibilityAnimation;check(shrink.to==.90f&&shrink.duration==210);shrink.tick(.5f);check(Math.abs(n.view.sx-pressedScale*.95f)<.00001f);n.move(false,false);check(shrink.cancelled&&n.visibilityAnimation.from==.95f&&n.visibilityAnimation.to==1);n.visibilityAnimation.tick(1);check(n.view.sx==pressedScale);n.touch(MotionEvent.ACTION_CANCEL,20,20);n.pressAnimation.tick(1);check(n.view.sx==1&&!n.pressed);
  n.touch(MotionEvent.ACTION_DOWN,20,20);n.pressAnimation.tick(1);n.touch(MotionEvent.ACTION_MOVE,-10,20);check(!n.pressed&&n.pressAnimation.to==1);n.stop();check(n.view.sx==1&&n.view.sy==1&&n.pressAnimation==null&&n.visibilityAnimation==null);ValueAnimator.enabled=false;n.touch(MotionEvent.ACTION_DOWN,20,20);check(n.view.sx==1);n.move(true,false);check(n.visibilityScale==.90f&&n.view.visibility==View.INVISIBLE);n.stop();ValueAnimator.enabled=true;
  System.out.println("Floating navigation and settings search: "+cases+" cases PASS; host UI model, no Android rendering");
 }
}'''
test=test.replace('CHROME',method('MainActivity.java','void syncPageChrome(')).replace('SEARCH',search).replace('MOTION',motion).replace('INSETS',method('ImmersiveLayout.java','void apply('))
p=O/'FloatingSearchTest.java';p.write_text(test,'utf-8');C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(C),*[str(S/n) for n in ['SettingsSearchIndex.java','NavigationPolicy.java','StatusPageLayout.java']],str(p)],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(C),'top.rongshangs.lumacurve.refactor.FloatingSearchTest'],check=True)
# Wiring contracts protect invisible compatibility paths and lifecycle restoration.
main=(S/'MainActivity.java').read_text('utf-8');immersive=(S/'ImmersiveLayout.java').read_text('utf-8')
assert 'setDecorFitsSystemWindows(false)' in main and 'setNavigationBarColor(Color.TRANSPARENT)' in main
assert 'setSystemUiVisibility(' not in main and 'setSystemBarsAppearance' in immersive
assert 'floatingNavigation.watch(settingsHomeScroll,1)' in main and 'floatingNavigation.watch(aboutScroll,3)' in main
assert 'settings_search_query' in main and 'SOFT_INPUT_STATE_ALWAYS_HIDDEN' in main
assert 'settingsActions=b;' in main and 'makeLauncherSetting();settingsSearch.bind();' in main
for name in ['FloatingNavigation.java','NavigationPolicy.java','ImmersiveLayout.java','SettingsSearch.java','SettingsSearchIndex.java']:
    code=(S/name).read_text('utf-8')
    assert 'Settings.Global.put' not in code and 'settings.put(' not in code and 'new ProcessBuilder' not in code
ids=re.search(r'IDS=\{([^}]+)\}',(S/'SceneOptions.java').read_text('utf-8'))[1]
for target in re.findall(r'"scene_([a-z_]+)"',(S/'SettingsSearchIndex.java').read_text('utf-8')):assert '"'+target+'"' in ids,target
print('Transparent system bars, actual-gesture collapse, saved query, existing destinations and no option writes: PASS')

# A fixed status surface, an in-bounds shadow, and one translating indicator.
status=method('MainActivity.java','void makeStatus(')
assert 'ScrollView' not in status and 'StatusPageLayout.fitScale' in status
nav=(S/'FloatingNavigation.java').read_text('utf-8')
assert 'a.dp(312)' in nav and 'a.dp(88)' in nav
assert 'setShadowLayer' in nav and 'clearShadowLayer' in nav
assert 'ValueAnimator.ofFloat(indicatorLeft,end)' in nav and 'indicator.cancel()' in nav
assert 'if(y<=0)' not in method('FloatingNavigation.java','void watch(')
assert 'tappableElement()|WindowInsets.Type.displayCutout()' in immersive
print('Fixed status viewport, explicit capsule shadow, translating indicator and no edge-triggered reveal: PASS')

# Execute the production indicator controller, including interruption and reduced motion.
indicator=method('FloatingNavigation.java','void positionIndicator(')+method('FloatingNavigation.java','void finishSelection(')
java=r'''package top.rongshangs.lumacurve.refactor;
class IndicatorView{int left,width=80;void invalidate(){}int getWidth(){return width;}int getLeft(){return left;}}
class IndicatorOwner{boolean visible=true;}
class IndicatorAnimator{interface Update{void update(IndicatorAnimator v);}static boolean enabled=true;float from,to,current;long duration;boolean cancelled;Update update;static boolean areAnimatorsEnabled(){return enabled;}static IndicatorAnimator ofFloat(float f,float t){IndicatorAnimator a=new IndicatorAnimator();a.from=a.current=f;a.to=t;return a;}void cancel(){cancelled=true;}void setDuration(long d){duration=d;}void setInterpolator(Object i){}void addUpdateListener(Update u){update=u;}void start(){}Object getAnimatedValue(){return current;}void tick(float p){if(!cancelled){current=from+(to-from)*p;update.update(this);}}}
class IndicatorEase{}
public class IndicatorTest{
 IndicatorView view=new IndicatorView();IndicatorView[] tabs=new IndicatorView[4];IndicatorOwner a=new IndicatorOwner();IndicatorAnimator indicator;boolean indicatorReady;float indicatorLeft;int selectedPage;
 METHODS
 static int count;static void check(boolean b){if(!b)throw new AssertionError("indicator case "+count);count++;}
 public static void main(String[] args){IndicatorTest n=new IndicatorTest();for(int i=0;i<4;i++){n.tabs[i]=new IndicatorView();n.tabs[i].left=i*80;}n.positionIndicator(true);check(n.indicatorReady&&n.indicatorLeft==0&&n.indicator==null);n.selectedPage=1;n.positionIndicator(true);IndicatorAnimator old=n.indicator;check(old.from==0&&old.to==80&&old.duration==240);old.tick(.5f);check(n.indicatorLeft==40);n.selectedPage=3;n.positionIndicator(true);check(old.cancelled&&n.indicator.from==40&&n.indicator.to==240);n.indicator.tick(.5f);check(n.indicatorLeft==140);n.selectedPage=0;n.positionIndicator(true);check(n.indicator.from==140&&n.indicator.to==0);n.finishSelection();check(n.indicator.cancelled&&n.indicatorLeft==0);IndicatorAnimator.enabled=false;n.selectedPage=2;n.positionIndicator(true);check(n.indicatorLeft==160);IndicatorAnimator.enabled=true;n.a.visible=false;n.selectedPage=1;n.positionIndicator(true);check(n.indicatorLeft==80);n.a.visible=true;n.tabs[3].width=0;n.selectedPage=3;n.positionIndicator(true);check(n.indicatorLeft==80);n.tabs[3].width=80;n.finishSelection();check(n.indicatorLeft==240);System.out.println("Navigation indicator: "+count+" cases PASS; production animation controller, host timing only");}
}'''
java=java.replace('METHODS',indicator.replace('ValueAnimator','IndicatorAnimator').replace('DecelerateInterpolator','IndicatorEase').replace('View target=','IndicatorView target='))
p=O/'IndicatorTest.java';p.write_text(java,'utf-8')
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(C),str(p)],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',str(C),'top.rongshangs.lumacurve.refactor.IndicatorTest'],check=True)
