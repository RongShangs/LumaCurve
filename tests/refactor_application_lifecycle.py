"""Execute the real picker startup prefix against strict undecorated PhoneWindow doubles."""
from pathlib import Path
import os,re,subprocess,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/application-lifecycle-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
source=(S/'ApplicationListActivity.java').read_text('utf-8');start=source.index('public void onCreate(Bundle saved){')+len('public void onCreate(Bundle saved){');end=source.index('        root.setOnApplyWindowInsetsListener',start);prefix=source[start:end]
bars=next(line for line in prefix.splitlines(keepends=True) if 'getInsetsController()' in line)
regression=prefix.replace(bars,'').replace('        LinearLayout root=column();',bars+'        LinearLayout root=column();')
at=source.index('    void dialogAction(');end=source.index('    @Override public void onCreate',at);dialog_action=source[at:end]
def block(signature):
 start=source.index(signature);at=source.index('{',start);end=at+1;depth=1
 while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
card_source=block('    final class ApplicationRows extends ListView').replace('ApplicationListActivity.this','CardPicker.this')
insets_source=block('    WindowInsets applyInsets(')
transition_source='\n'.join(block(signature) for signature in ['    void configureTransitions(','    void transitionContent(','    void cancelContentTransition(','    void done(','    @Override public void onPause('])
fixture=r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
class Configuration{static final int UI_MODE_NIGHT_MASK=48,UI_MODE_NIGHT_YES=32;int uiMode;}
class Resources{final Configuration config=new Configuration();Configuration getConfiguration(){return config;}int getIdentifier(String n,String kind,String pkg){if(!kind.equals("anim")||!pkg.equals("top.rongshangs.lumacurve"))throw new AssertionError();return n.hashCode();}}
class Color{static final int TRANSPARENT=0,WHITE=-1;}
class WindowManager{static class LayoutParams{static final int SOFT_INPUT_ADJUST_NOTHING=48,SOFT_INPUT_STATE_ALWAYS_HIDDEN=3;}}
class WindowInsetsController{static final int APPEARANCE_LIGHT_STATUS_BARS=8,APPEARANCE_LIGHT_NAVIGATION_BARS=16;int appearance=-1;void setSystemBarsAppearance(int value,int mask){appearance=value;}}
class ColorDrawable{final int color;ColorDrawable(int color){this.color=color;}}
class Window{boolean decorated,absent;ColorDrawable background;final WindowInsetsController controller=new WindowInsetsController();void setBackgroundDrawable(ColorDrawable color){background=color;}void setDecorFitsSystemWindows(boolean on){}void setStatusBarColor(int c){}void setNavigationBarColor(int c){}void setNavigationBarContrastEnforced(boolean on){}void setStatusBarContrastEnforced(boolean on){}void setSoftInputMode(int mode){}WindowInsetsController getInsetsController(){if(!decorated)throw new NullPointerException("PhoneWindow decor not installed");return absent?null:controller;}}
class LinearLayout extends View{final java.util.List<LayoutParams> buttons=new java.util.ArrayList<>();void addView(Button button,LayoutParams lp){buttons.add(lp);}void setBackgroundColor(int color){}void setFocusableInTouchMode(boolean on){}static class LayoutParams{int width,height,left,top,right,bottom;float weight;LayoutParams(int w,int h,float weight){width=w;height=h;this.weight=weight;}void setMargins(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}}
class Gravity{static final int CENTER=17;}
class TextUtils{enum TruncateAt{END}}
class Button{int left,right,top,bottom,minimum,min,gravity;boolean allCaps=true,includePadding=true,singleLine;Object ellipsize;void setAllCaps(boolean b){allCaps=b;}void setIncludeFontPadding(boolean b){includePadding=b;}void setGravity(int g){gravity=g;}void setSingleLine(boolean b){singleLine=b;}void setEllipsize(Object e){ellipsize=e;}void setPadding(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}void setMinHeight(int v){min=v;}void setMinimumHeight(int v){minimum=v;}}
class View{float alpha=1,translation;final Animator animator=new Animator(this);void setAlpha(float value){alpha=value;}void setTranslationX(float value){translation=value;}Animator animate(){return animator;}int top,bottom,leftPadding,topPadding,rightPadding,bottomPadding;int getTop(){return top;}int getBottom(){return bottom;}void setPadding(int l,int t,int r,int b){leftPadding=l;topPadding=t;rightPadding=r;bottomPadding=b;}}
class Animator{final View view;float alpha,translation;long duration;boolean running;Runnable end;Animator(View view){this.view=view;}void cancel(){running=false;end=null;}Animator alpha(float v){alpha=v;return this;}Animator translationX(float v){translation=v;return this;}Animator setDuration(long v){duration=v;return this;}Animator setInterpolator(Object v){return this;}Animator withEndAction(Runnable v){end=v;return this;}void start(){running=true;}void end(){view.alpha=alpha;view.translation=translation;running=false;Runnable action=end;end=null;if(action!=null)action.run();}}
class Paint{static final int ANTI_ALIAS_FLAG=1;int color;Paint(int flags){}void setColor(int c){color=c;}}
class RectF{float left,top,right,bottom;void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
class Canvas{int draws,parents;RectF rect;float radius;void drawRoundRect(RectF r,float x,float y,Paint p){draws++;rect=new RectF();rect.set(r.left,r.top,r.right,r.bottom);radius=x;}}
class Insets{int left,top,right,bottom;Insets(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
class WindowInsets{static final WindowInsets CONSUMED=new WindowInsets(0);int keyboard;WindowInsets(int keyboard){this.keyboard=keyboard;}static class Type{static int systemBars(){return 1;}static int displayCutout(){return 2;}static int ime(){return 4;}}Insets getInsets(int type){return type==4?new Insets(0,0,0,keyboard):new Insets(5,24,6,24);}}
class ListView extends View{int first,headers=1,width=360,height=600;final List<View> children=new ArrayList<>();ListView(Object owner){}int getFirstVisiblePosition(){return first;}int getHeaderViewsCount(){return headers;}int getWidth(){return width;}int getHeight(){return height;}int getPaddingLeft(){return leftPadding;}int getPaddingRight(){return rightPadding;}int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}protected void dispatchDraw(Canvas c){c.parents++;}}
class CardPicker{int density=1;boolean dark;final Adapter adapter=new Adapter();ListView list;int dp(float n){return Math.round(n*density);}static class Adapter{int count;int getCount(){return count;}}CARD_SOURCE INSETS_SOURCE}
class AlertDialog{void dismiss(){}}
class TransitionPicker extends Activity{static final int RESULT_OK=-1,OVERRIDE_TRANSITION_OPEN=0,OVERRIDE_TRANSITION_CLOSE=1;int bg,density=1;View page=new View();boolean closing,visible=true;long pageTransition,generation;java.util.concurrent.ExecutorService worker;AlertDialog confirmation;final Set<String> iconPending=new HashSet<>(),selected=new LinkedHashSet<>(),moved=new LinkedHashSet<>();String listId="individual";int results;Intent result;int dp(float n){return Math.round(n*density);}void setResult(int code,Intent intent){if(code!=RESULT_OK)throw new AssertionError();results++;result=intent;}final Map<Integer,int[]> animations=new HashMap<>();void overrideActivityTransition(int type,int enter,int exit,int color){animations.put(type,new int[]{enter,exit,color});}TRANSITION_SOURCE}
class ActionPicker{int density=1;int dp(float n){return Math.round(n*density);}DIALOG_ACTION}
class Bundle extends HashMap<String,ArrayList<String>>{ArrayList<String> getStringArrayList(String key){return get(key);}}
class Intent{Map<String,Object> values=new HashMap<>();Intent putExtra(String key,String v){values.put(key,v);return this;}Intent putStringArrayListExtra(String key,ArrayList<String> v){values.put(key,v);return this;}String getStringExtra(String key){return (String)values.get(key);}@SuppressWarnings("unchecked")ArrayList<String> getStringArrayListExtra(String key){return (ArrayList<String>)values.get(key);}}
class Activity{final Window window=new Window();final Resources resources=new Resources();final Intent intent=new Intent();boolean finished;int finishes;void onCreate(Bundle saved){}void onPause(){}Window getWindow(){return window;}Resources getResources(){return resources;}Intent getIntent(){return intent;}boolean isFinishing(){return finished;}void finish(){finished=true;finishes++;}void setContentView(LinearLayout content){window.decorated=true;}}
FIELDS
public class PickerLifecycleTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("picker lifecycle "+cases);cases++;}
 static void extras(Activity a,String id){a.intent.values.put("list",id);a.intent.values.put("title",id);a.intent.values.put("assigned","{\"com.saved.current\":\"Current\",\"com.other.video\":\"Other group\"}");a.intent.values.put("selected",new ArrayList<>(Arrays.asList("com.saved.current")));}
 public static void main(String[] args){
  for(String list:new String[]{"pause","individual","videos"})for(boolean dark:new boolean[]{false,true})for(boolean rotated:new boolean[]{false,true}){
   GoodPicker good=new GoodPicker();extras(good,list);good.resources.config.uiMode=dark?32:16;Bundle saved=null;if(rotated){saved=new Bundle();saved.put("selected",new ArrayList<>(Arrays.asList("com.other.video")));saved.put("moved",new ArrayList<>(Arrays.asList("com.other.video")));}
   good.onCreate(saved);check(!good.finished&&good.window.decorated);check(good.window.controller.appearance==(dark?0:24));check(good.selected.contains(rotated?"com.other.video":"com.saved.current"));if(rotated)check(good.moved.contains("com.other.video")&&!good.others.contains("com.other.video"));
   BadPicker bad=new BadPicker();extras(bad,list);try{bad.onCreate(null);throw new AssertionError("old startup order did not reproduce native crash");}catch(NullPointerException expected){cases++;}
  }
  GoodPicker noController=new GoodPicker();extras(noController,"pause");noController.window.absent=true;noController.onCreate(null);check(noController.window.decorated&&!noController.finished);
  GoodPicker missing=new GoodPicker();missing.onCreate(null);check(missing.finished&&!missing.window.decorated);
  GoodPicker invalid=new GoodPicker();extras(invalid,"individual");invalid.intent.values.put("selected",new ArrayList<>(Arrays.asList("bad")));invalid.onCreate(null);check(invalid.finished&&!invalid.window.decorated);
  for(int density:new int[]{1,2,3}){ActionPicker picker=new ActionPicker();picker.density=density;LinearLayout actions=new LinearLayout();Button cancel=new Button(),confirm=new Button();picker.dialogAction(actions,cancel);picker.dialogAction(actions,confirm);check(actions.buttons.size()==2);for(LinearLayout.LayoutParams lp:actions.buttons){check(lp.width==0&&lp.weight==1&&lp.height==48*density&&lp.top==14*density&&lp.left==4*density&&lp.right==4*density);}for(Button b:new Button[]{cancel,confirm})check(b.left==10*density&&b.right==10*density&&b.min==48*density&&b.minimum==48*density&&!b.allCaps&&!b.includePadding&&b.singleLine&&b.gravity==Gravity.CENTER);}
  for(int density:new int[]{1,2,3}){
   CardPicker picker=new CardPicker();picker.density=density;CardPicker.ApplicationRows rows=picker.new ApplicationRows();picker.list=rows;rows.width=360*density;rows.height=600*density;rows.setPadding(18*density,0,18*density,36*density);picker.adapter.count=2;
   for(int[] bound:new int[][]{{0,100},{100,180},{180,260}}){View child=new View();child.top=bound[0]*density;child.bottom=bound[1]*density;rows.children.add(child);}Canvas canvas=new Canvas();rows.dispatchDraw(canvas);check(canvas.draws==1&&canvas.parents==1);check(canvas.rect.top==100*density&&canvas.rect.bottom==260*density&&canvas.rect.left==18*density&&canvas.rect.right==342*density&&canvas.radius==18*density);
   rows.first=4;picker.adapter.count=100;rows.children.remove(0);rows.children.get(0).top=-30*density;rows.children.get(1).bottom=130*density;canvas=new Canvas();rows.dispatchDraw(canvas);check(canvas.rect.top<=-18*density&&canvas.rect.bottom>=rows.height+18*density&&canvas.draws==1);
   rows.first=99;canvas=new Canvas();rows.dispatchDraw(canvas);check(canvas.rect.bottom==130*density&&canvas.draws==1);
   picker.adapter.count=0;canvas=new Canvas();rows.dispatchDraw(canvas);check(canvas.draws==0&&canvas.parents==1);
   for(int keyboard:new int[]{0,260}){View root=new View();check(picker.applyInsets(root,new WindowInsets(keyboard))==WindowInsets.CONSUMED);check(root.bottomPadding==0&&root.topPadding==24&&root.leftPadding==5&&root.rightPadding==6);check(rows.bottomPadding==12*density+Math.max(24,keyboard));}
  }
  for(boolean enabled:new boolean[]{true,false})for(int bg:new int[]{0xfffafafa,0xff11151c})for(int density:new int[]{1,2,3}){
   android.animation.ValueAnimator.enabled=enabled;TransitionPicker picker=new TransitionPicker();picker.bg=bg;picker.density=density;picker.configureTransitions();
   check(picker.animations.size()==2);int[] open=picker.animations.get(0),close=picker.animations.get(1);check(open[0]==0&&open[1]==0&&close[0]==0&&close[1]==0);check(open[2]==bg&&close[2]==bg&&picker.window.background.color==bg&&(bg>>>24)==255);
   picker.transitionContent(true,null);check(picker.page.alpha==(enabled?0:1)&&picker.page.translation==(enabled?12*density:0));
   if(enabled){check(picker.page.animator.running&&picker.page.animator.duration==180);picker.page.animator.end();}check(picker.page.alpha==1&&picker.page.translation==0&&picker.window.background.color==bg);
   picker.selected.add("com.demo.selected");picker.moved.add("com.demo.moved");picker.done();check(picker.results==1&&picker.result.getStringArrayListExtra("selected").contains("com.demo.selected")&&picker.result.getStringArrayListExtra("moved").contains("com.demo.moved"));
   picker.done();check(picker.results==1);if(enabled){check(!picker.finished&&picker.page.animator.duration==120&&picker.page.animator.translation==12*density);picker.page.animator.end();}check(picker.finished&&picker.finishes==1);picker.onPause();check(picker.page.alpha==1&&picker.page.translation==0&&picker.finishes==1);
  }
  // A quick Back interrupts entry; cancelling its exit on Home still delivers
  // the draft once. A stale end callback cannot finish the Activity twice.
  android.animation.ValueAnimator.enabled=true;TransitionPicker interrupted=new TransitionPicker();interrupted.transitionContent(true,null);interrupted.done();Runnable stale=interrupted.page.animator.end;interrupted.onPause();check(interrupted.results==1&&interrupted.finishes==1&&!interrupted.visible);check(interrupted.page.alpha==1&&interrupted.page.translation==0&&!interrupted.page.animator.running);stale.run();check(interrupted.finishes==1);
  TransitionPicker background=new TransitionPicker();background.transitionContent(true,null);background.onPause();check(!background.finished&&background.results==0&&background.page.alpha==1&&background.page.translation==0);background.transitionContent(false,()->{throw new AssertionError("cancelled completion ran");});Runnable cancelled=background.page.animator.end;background.cancelContentTransition();cancelled.run();check(background.page.alpha==1&&background.page.translation==0&&!background.page.animator.running);
  TransitionPicker absent=new TransitionPicker();absent.page=null;absent.done();check(absent.results==1&&absent.finishes==1);
  System.out.println("Application picker lifecycle: "+cases+" cases PASS; production startup, undecorated-window crash reproduced, Android not rendered");
 }
}'''
def picker(name,body):return 'class '+name+' extends Activity{String listId,title;JSONObject assigned=new JSONObject();final Set<String> selected=new LinkedHashSet<>(),others=new LinkedHashSet<>(),moved=new LinkedHashSet<>();boolean dark;int ink,muted,blue,bg;LinearLayout column(){return new LinearLayout();}public void onCreate(Bundle saved){'+body+'}}'
p=O/'PickerLifecycleTest.java';p.write_text(fixture.replace('TRANSITION_SOURCE',transition_source).replace('CARD_SOURCE',card_source).replace('INSETS_SOURCE',insets_source).replace('DIALOG_ACTION',dialog_action).replace('FIELDS',picker('GoodPicker',prefix)+picker('BadPicker',regression)),'utf-8');C=O/'classes';C.mkdir(exist_ok=True)
animation=O/'android/animation/ValueAnimator.java';animation.parent.mkdir(parents=True,exist_ok=True);animation.write_text('package android.animation;public class ValueAnimator{public static boolean enabled;public static boolean areAnimatorsEnabled(){return enabled;}}','utf-8')
interpolator=O/'android/view/animation/DecelerateInterpolator.java';interpolator.parent.mkdir(parents=True,exist_ok=True);interpolator.write_text('package android.view.animation;public class DecelerateInterpolator{}','utf-8')
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(p),str(animation),str(interpolator),*[str(S/(n+'.java')) for n in ['BrightnessControlOptions','ApplicationSelection','ThemePalette']]],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.PickerLifecycleTest'],check=True)

main=(S/'MainActivity.java').read_text('utf-8');main=main[main.index('    void transitionPage('):main.index('    void finishPageTransition(')]
shift=int(re.search(r'shift=dp\((\d+)\)',main).group(1));durations=[int(n) for n in re.findall(r'setDuration\((\d+)\)',main)];assert durations==[120,180]
ns='{http://schemas.android.com/apk/res/android}'
motion=block('    void transitionContent(');assert f'dp({shift})' in motion and 'entering?180:120' in motion and 'DecelerateInterpolator' in motion
assert 'transitionContent(true,null)' in source and 'page=root;configureTransitions()' in source
manifest=ET.parse(R/'experimental/refactor_hook/AndroidManifest.xml').getroot();activity=next(a for a in manifest.find('application').findall('activity') if a.attrib[ns+'name'].endswith('.ApplicationListActivity'));assert ns+'theme' not in activity.attrib
for theme in ['values','values-night']:
 xml=ET.parse(R/'experimental/refactor_hook/res'/theme/'resources.xml').getroot();style=next(s for s in xml.findall('style') if s.attrib['name']=='AppTheme');items={i.attrib['name']:i.text for i in style.findall('item')};assert items.get('android:windowIsTranslucent','false')=='false';assert items['android:windowBackground'] in ['#fafafa','#11151c']
print('Application picker transitions: stationary opaque window and lifecycle-safe content motion PASS; Android not rendered')
