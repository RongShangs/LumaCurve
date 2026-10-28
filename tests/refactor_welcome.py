"""Execute the production welcome/tutorial with parent-aware widgets and stable preferences.

Checks mandatory first launch, tutorial navigation, replay, saved state, dialog ordering,
and unchanged user settings. These are host lifecycle tests, not Android rendering.
"""
from pathlib import Path
import ast,subprocess,os,re,json

R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/welcome-tests';O.mkdir(exist_ok=True)
def literal_entry(file,assignment,key):
    tree=ast.parse((R/'tests'/file).read_text(encoding='utf-8'))
    dictionary=next(node.value for node in tree.body if isinstance(node,ast.Assign) and any(isinstance(t,ast.Name) and t.id==assignment for t in node.targets))
    return ast.literal_eval(next(value for name,value in zip(dictionary.keys,dictionary.values) if ast.literal_eval(name)==key))
tree=ast.parse((R/'tests/refactor_feature_page.py').read_text(encoding='utf-8'))
dictionary=next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='files' for t in n.targets))
files={ast.literal_eval(k):ast.literal_eval(v) for k,v in zip(dictionary.keys,dictionary.values) if ast.literal_eval(k).startswith('android/')}
files['android/view/Gravity.java']=files['android/view/Gravity.java'].replace('CENTER_VERTICAL=16','CENTER_VERTICAL=16,CENTER=17')
files['android/view/ViewGroup.java']=files['android/view/ViewGroup.java'].replace('public List<View> children=', 'public static class LayoutParams{public LayoutParams(int w,int h){}}public List<View> children=')
files['android/widget/LinearLayout.java']=files['android/widget/LinearLayout.java'].replace('public LinearLayout(Object c)','public void setGravity(int g){}public LinearLayout(Object c)').replace('leftMargin,bottomMargin','leftMargin,bottomMargin,topMargin')
files['android/view/View.java']=files['android/view/View.java'].replace('public View(Object context){}','''public float alpha=1,translation,translationY;public void setTranslationY(float y){translationY=y;}public final ViewPropertyAnimator animator=new ViewPropertyAnimator(this);public ViewPropertyAnimator animate(){return animator;}public void setAlpha(float a){alpha=a;}public void setTranslationX(float x){translation=x;}public View(Object context){}''')
files['android/view/ViewPropertyAnimator.java']='''package android.view;public class ViewPropertyAnimator{
 final View view;public int starts,cancels;public boolean pending;public long duration;float alpha=1,x;
 ViewPropertyAnimator(View v){view=v;}public void cancel(){cancels++;pending=false;}public ViewPropertyAnimator setListener(Object listener){return this;}public ViewPropertyAnimator withEndAction(Runnable callback){if(callback!=null)throw new AssertionError("unexpected asynchronous navigation");return this;}
 public ViewPropertyAnimator alpha(float n){alpha=n;return this;}public ViewPropertyAnimator translationX(float n){x=n;return this;}public ViewPropertyAnimator setDuration(long n){duration=n;return this;}public ViewPropertyAnimator setInterpolator(Object i){return this;}
 public void start(){starts++;pending=true;}public void finish(){if(pending){pending=false;view.setAlpha(alpha);view.setTranslationX(x);}}
}'''
files['android/animation/ValueAnimator.java']='package android.animation;public class ValueAnimator{public static final int INFINITE=-1;public static boolean enabled;public static boolean areAnimatorsEnabled(){return enabled;}}'
files['android/animation/ObjectAnimator.java']='''package android.animation;import android.view.View;public class ObjectAnimator{
 public final View target;public final float[] values;public long duration;public int repeat;public Object interpolator;public boolean running;
 ObjectAnimator(View v,float[] a){target=v;values=a;}public static ObjectAnimator ofFloat(Object o,String property,float... values){if(!property.equals("translationY"))throw new AssertionError(property);return new ObjectAnimator((View)o,values);}
 public void setDuration(long d){duration=d;}public void setRepeatCount(int r){repeat=r;}public void setInterpolator(Object i){interpolator=i;}public void start(){running=true;}public void cancel(){running=false;}public void sample(float value){if(running)target.setTranslationY(value);}
}'''
files['android/view/animation/AccelerateDecelerateInterpolator.java']='package android.view.animation;public class AccelerateDecelerateInterpolator{}'
files['android/view/animation/DecelerateInterpolator.java']='package android.view.animation;public class DecelerateInterpolator{}'
files['android/widget/TextView.java']=files['android/widget/TextView.java'].replace('public void setTextSize(int s){}','public void setTextSize(int s){}public void setTextColor(int c){}public void setLineSpacing(float extra,int multiplier){}')
files['android/widget/ImageView.java']='package android.widget;public class ImageView extends android.view.View{public ImageView(Object c){super(c);}}'
files['android/text/Spanned.java']='package android.text;public interface Spanned extends CharSequence{int SPAN_EXCLUSIVE_EXCLUSIVE=33;}'
files['android/text/SpannableString.java']='''package android.text;public class SpannableString implements Spanned{final String text;public Object span;public int start,end,flags;public SpannableString(CharSequence value){text=value.toString();}public int length(){return text.length();}public char charAt(int at){return text.charAt(at);}public CharSequence subSequence(int a,int b){return text.subSequence(a,b);}public String toString(){return text;}public void setSpan(Object value,int a,int b,int f){if(a<0||a>=b||b>length())throw new AssertionError("span bounds");span=value;start=a;end=b;flags=f;}}'''
files['android/text/style/ForegroundColorSpan.java']='package android.text.style;public class ForegroundColorSpan{public final int color;public ForegroundColorSpan(int c){color=c;}}'
files['android/graphics/Color.java']='package android.graphics;public class Color{public static final int WHITE=0xffffffff;}'
files['android/os/Handler.java']='package android.os;public class Handler{public int removes;public void removeCallbacks(Runnable r){removes++;}}'
files['android/os/Bundle.java']='''package android.os;import java.util.*;public class Bundle{Map<String,Object> values=new HashMap<>();public void putBoolean(String k,boolean v){values.put(k,v);}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public void putInt(String k,int v){values.put(k,v);}public int getInt(String k,int d){return values.containsKey(k)?(Integer)values.get(k):d;}}'''
files['android/content/SharedPreferences.java']=literal_entry('refactor_ui_preferences.py','files','android/content/SharedPreferences.java')
files['top/rongshangs/lumacurve/refactor/UpdateChecker.java']='package top.rongshangs.lumacurve.refactor;class UpdateChecker{static final String REPO="https://github.com/RongShangs/LumaCurve";}'
files['top/rongshangs/lumacurve/refactor/MainActivity.java']='''package top.rongshangs.lumacurve.refactor;
import android.view.*;import android.widget.*;import android.os.*;import android.content.*;import java.util.*;
class MainActivity{
 int INK=1,MUTED=2,BLUE=3,BG=4,resumes,finishes;boolean visible=true,destroyed,finishing,darkTheme,english,permissionDialogVisible,legacyDialogVisible,dirty=true;
 Object updateDialog;final Set<Object> openDialogs=new HashSet<>();final Handler ui=new Handler();final Runnable tick=()->{};
 final SharedPreferences prefs;LinearLayout root=column(),parent=column();final WelcomeGuide welcome=new WelcomeGuide(this);
 MainActivity(){this(new SharedPreferences());}MainActivity(SharedPreferences p){prefs=p;parent.addView(root);}
 int dp(int n){return n;}String tr(String s){return UiText.translate(s,english);}SharedPreferences getPreferences(int n){return prefs;}boolean isFinishing(){return finishing;}void finish(){finishes++;finishing=true;}
 void resumeRuntime(){if(visible&&!destroyed&&!welcome.showing())resumes++;}
 String lastOpened="";void open(String url){lastOpened=url;}
 LinearLayout column(){return new LinearLayout(this);}LinearLayout row(LinearLayout parent){LinearLayout v=column();parent.addView(v);return v;}LinearLayout card(LinearLayout parent){return row(parent);}
 TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(tr(value));return v;}
 Button action(String label,Runnable callback){Button b=new Button(this);b.setText(tr(label));b.setOnClickListener(v->callback.run());return b;}
 ImageView image(String asset,int size,int radius){return new ImageView(this);}Object ripple(int color,int radius){return new Object();}
 int refreshSeconds=2;TextView refreshLabel;View refreshSlider;final LinearLayout settingsTarget=column();
 View parameter(LinearLayout parent,int maximum,int value,java.util.function.IntConsumer changed){View v=new View(this);parent.addView(v);return v;}
 void button(String label,Runnable callback,LinearLayout parent){parent.addView(action(label,callback));}
 __REFRESH_SETTINGS__
}
'''
main=(S/'MainActivity.java').read_text(encoding='utf-8')
settings=main[main.index('    void makeRefreshSettings(){'):main.index('    JSONObject appFeatureReport')]
files['top/rongshangs/lumacurve/refactor/MainActivity.java']=files['top/rongshangs/lumacurve/refactor/MainActivity.java'].replace('__REFRESH_SETTINGS__',settings)
files['top/rongshangs/lumacurve/refactor/ElasticScrollView.java']='package top.rongshangs.lumacurve.refactor;class ElasticScrollView extends android.widget.ScrollView{ElasticScrollView(Object c){super(c);}}'
files['top/rongshangs/lumacurve/refactor/WelcomeTest.java']=r'''package top.rongshangs.lumacurve.refactor;
import android.view.*;import android.widget.*;import android.content.*;import android.os.*;import java.util.*;
public class WelcomeTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("welcome case "+cases);cases++;}
 static TextView find(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return (TextView)root;if(root instanceof ViewGroup)for(View child:((ViewGroup)root).children){TextView v=find(child,text);if(v!=null)return v;}return null;}
 static void press(WelcomeGuide guide,String text){TextView button=find(guide.root,text);check(button!=null&&button.enabled);button.performClick();}
 static Map<String,Object> seed(SharedPreferences p){p.edit().putString("draft","1,0.8,0.7,1").putBoolean("detail_outdoor",true).putInt("refresh_seconds",3).putBoolean("settings_notice_acknowledged_v1",true).apply();return new HashMap<>(p.values);}
 static void unchanged(SharedPreferences p,Map<String,Object> before){for(String key:before.keySet())check(Objects.equals(before.get(key),p.values.get(key)));}
 public static void main(String[] args){
  MainActivity a=new MainActivity();Map<String,Object> original=seed(a.prefs);int writes=a.prefs.writes;WelcomeGuide g=a.welcome;View originalRoot=a.root;
  g.resume();check(g.showing()&&g.page==0&&g.firstRun);check(a.root.visibility==View.GONE&&a.parent.getChildCount()==2);check(find(g.root,"你好")!=null);check(find(g.root,"开始使用")==null&&find(g.root,"查看教程")==null&&find(g.root,"下一步")!=null);check(a.resumes==0&&a.prefs.writes==writes);
  check(g.root.getChildCount()==2&&g.root.getChildAt(0) instanceof ScrollView);check(find(g.root,"HyperLux")==null&&find(g.root,TutorialContent.REPLAY)==null);
  check(!TutorialContent.INTRO.matches("(?s).*[。，,.!！].*"));
  TextView intro=find(g.root,TutorialContent.INTRO);check(intro!=null&&intro.getText() instanceof android.text.SpannableString);
  android.text.SpannableString emphasis=(android.text.SpannableString)intro.getText();check(emphasis.subSequence(emphasis.start,emphasis.end).toString().equals("HyperLux"));check(emphasis.span instanceof android.text.style.ForegroundColorSpan&&((android.text.style.ForegroundColorSpan)emphasis.span).color==a.BLUE);
  g.resume();check(a.parent.getChildCount()==2);g.openTutorial();check(g.page==0&&g.firstRun); // No duplicate guide can replace an active first-run flow.
  g.close(true);g.close(false);check(g.showing()&&a.prefs.writes==writes);for(int page=0;page<TutorialContent.pages();page++){check(g.page==page&&find(g.root,"开始使用")==null);press(g,"下一步");check(a.prefs.writes==writes);}press(g,"开始使用");check(!g.showing()&&a.root==originalRoot&&a.root.visibility==View.VISIBLE&&a.parent.getChildCount()==1);check(a.prefs.getBoolean(WelcomeGuide.COMPLETE_KEY,false)&&a.resumes==1);unchanged(a.prefs,original);check(a.prefs.writes==writes+1);
  g.close(true);g.resume();check(a.resumes==1&&!g.showing()&&a.prefs.writes==writes+1);
  MainActivity reopened=new MainActivity(a.prefs);reopened.welcome.resume();check(!reopened.welcome.showing());
  // Replay keeps the exact underlying settings views, drafts and one-time completion.
  int beforeWrites=a.prefs.writes;g.openTutorial();check(g.showing()&&g.page==0&&!g.firstRun);check(find(g.root,"你好")!=null);press(g,"下一步");check(g.page==1);g.back();check(g.page==0&&g.showing());g.back();check(!g.showing()&&a.resumes==2&&a.prefs.writes==beforeWrites);unchanged(a.prefs,original);
  g.openTutorial();check(g.page==0);press(g,"下一步");for(int page=1;page<=TutorialContent.pages();page++){check(g.page==page);check(find(g.root,TutorialContent.TITLES[page-1])!=null);check(find(g.root,TutorialContent.REPLAY)!=null&&find(g.scroll,TutorialContent.REPLAY)==null);check(find(g.root,TutorialContent.REPLAY).getParent()==g.root.getChildAt(g.root.getChildCount()-1));for(String heading:TutorialContent.HEADINGS[page-1])check(find(g.root,heading)!=null);if(page<TutorialContent.pages())press(g,"下一步");else press(g,"完成");}
  check(!g.showing()&&a.resumes==3&&a.prefs.writes==beforeWrites);unchanged(a.prefs,original);
  g.openTutorial();for(int page=1;page<=TutorialContent.pages();page++){g.move(page);check(find(g.root,"返回")==null&&find(g.root,"上一步")!=null);}g.close(false);
  // Finishing the first tutorial records completion, but merely opening/backing out does not.
  MainActivity first=new MainActivity();g=first.welcome;g.resume();press(g,"下一步");check(g.page==1&&g.firstRun);g.back();check(g.page==0&&!first.prefs.getBoolean(WelcomeGuide.COMPLETE_KEY,false));press(g,"下一步");for(int i=1;i<TutorialContent.pages();i++)press(g,"下一步");check(!first.prefs.getBoolean(WelcomeGuide.COMPLETE_KEY,false));press(g,"开始使用");check(first.prefs.getBoolean(WelcomeGuide.COMPLETE_KEY,false)&&first.prefs.writes==1);
  MainActivity abandoned=new MainActivity();abandoned.welcome.resume();check(abandoned.welcome.back());check(abandoned.finishes==1&&abandoned.prefs.writes==0);abandoned.destroyed=true;abandoned.welcome.dispose();check(!abandoned.welcome.showing()&&abandoned.resumes==0);
  // Rotation/process recreation restores the tutorial page and scroll; it never acknowledges it.
  MainActivity rotating=new MainActivity();g=rotating.welcome;g.resume();press(g,"下一步");press(g,"下一步");g.scroll.drain();g.scroll.y=123;Bundle saved=new Bundle();g.save(saved);rotating.destroyed=true;g.dispose();
  MainActivity restored=new MainActivity(rotating.prefs);g=restored.welcome;g.restore(saved);g.resume();check(g.page==2&&g.firstRun&&g.showing());g.scroll.drain();check(g.scroll.y==123&&restored.prefs.writes==0);
  // A delayed scroll restore from an old page cannot scroll a newer tutorial page.
  g.scroll.y=200;Bundle late=new Bundle();g.save(late);restored.destroyed=true;g.dispose();MainActivity race=new MainActivity(restored.prefs);g=race.welcome;g.restore(late);g.resume();ScrollView stale=g.scroll;g.move(3);stale.drain();g.scroll.drain();check(g.page==3&&g.scroll.y==0);
  for(String gate:new String[]{"background","destroyed","finishing","permission","legacy","update","dialog"}){
   MainActivity blocked=new MainActivity();if(gate.equals("background"))blocked.visible=false;if(gate.equals("destroyed"))blocked.destroyed=true;if(gate.equals("finishing"))blocked.finishing=true;if(gate.equals("permission"))blocked.permissionDialogVisible=true;if(gate.equals("legacy"))blocked.legacyDialogVisible=true;if(gate.equals("update"))blocked.updateDialog=new Object();if(gate.equals("dialog"))blocked.openDialogs.add(new Object());blocked.welcome.resume();check(!blocked.welcome.showing()&&blocked.prefs.writes==0);
   blocked.visible=true;blocked.destroyed=blocked.finishing=blocked.permissionDialogVisible=blocked.legacyDialogVisible=false;blocked.updateDialog=null;blocked.openDialogs.clear();blocked.welcome.resume();check(blocked.welcome.showing()&&blocked.welcome.page==0);blocked.destroyed=true;blocked.welcome.dispose();check(blocked.resumes==0);
  }
  // Complete content, both language choices, and corrupted saved page bounds.
  check(TutorialContent.pages()==5&&TutorialContent.clamp(-1)==0&&TutorialContent.clamp(Integer.MAX_VALUE)==5);
  for(int p=0;p<TutorialContent.pages();p++){check(TutorialContent.HEADINGS[p].length==3&&TutorialContent.PARAGRAPHS[p].length==3);for(String text:TutorialContent.PARAGRAPHS[p])check(!text.isEmpty()&&text.length()<180);}
  MainActivity english=new MainActivity();english.english=true;english.welcome.resume();check(find(english.welcome.root,"Hello")!=null);check(find(english.welcome.root,"Get started")==null);check(find(english.welcome.root,"View tutorial")==null);check(find(english.welcome.root,"Next")!=null);
  TextView englishIntro=find(english.welcome.root,UiText.translate(TutorialContent.INTRO,true));check(englishIntro!=null&&englishIntro.getText() instanceof android.text.SpannableString);
  android.text.SpannableString englishStyle=(android.text.SpannableString)englishIntro.getText();check(englishStyle.subSequence(englishStyle.start,englishStyle.end).toString().equals("HyperLux")&&((android.text.style.ForegroundColorSpan)englishStyle.span).color==english.BLUE);
  // Replay is its own settings card, with no refresh control mixed into it.
  MainActivity cards=new MainActivity();cards.makeRefreshSettings();check(cards.settingsTarget.getChildCount()==2);
  View interfaceCard=cards.settingsTarget.getChildAt(0),tutorialCard=cards.settingsTarget.getChildAt(1);
  check(find(interfaceCard,"界面")!=null&&find(interfaceCard,"再次查看使用教程")==null);
  check(find(tutorialCard,"使用教程")!=null&&find(tutorialCard,"再次查看使用教程")!=null);
  check(cards.refreshSlider.getParent()==interfaceCard&&find(tutorialCard,"再次查看使用教程").getParent()==tutorialCard);
  // Feedback is an actual fifth page; viewing it never edits saved preferences.
  MainActivity feedback=new MainActivity();g=feedback.welcome;g.resume();g.move(5);
  check(find(g.root,"遇到 BUG 或不兼容的情况")!=null&&find(g.root,"1. 导出分析包")!=null);
  check(find(g.root,"2. 选择反馈方式")!=null&&find(g.root,"3. 一起确认问题")!=null);
  check(find(g.root,"只要是 HyperOS，HyperLux 都会尽力适配。")!=null&&feedback.prefs.writes==0);
  check(find(g.root,TutorialContent.PARAGRAPHS[4][1])!=null&&TutorialContent.PARAGRAPHS[4][1].contains("群号")&&TutorialContent.PARAGRAPHS[4][1].contains("GitHub Issues"));
  check(find(g.root,"GitHub Issues")==null&&feedback.lastOpened.isEmpty()&&g.showing()&&g.page==5&&feedback.prefs.writes==0);
  // Actual transition calls, rapid reversal, disabled animations and lifecycle cleanup.
  android.animation.ValueAnimator.enabled=true;MainActivity motion=new MainActivity();g=motion.welcome;g.resume();
  check(g.root.alpha==0&&g.root.translation==12&&g.root.animator.pending&&g.root.animator.duration==180);
  g.root.animator.finish();check(g.root.alpha==1&&g.root.translation==0);
  g.move(1);check(g.page==1&&g.root.animator.pending&&g.root.translation==12);
  g.move(2);check(g.page==2&&g.root.animator.pending&&g.root.translation==12);
  g.back();check(g.page==1&&g.root.animator.pending&&g.root.translation==-12);
  g.root.animator.finish();check(g.root.alpha==1&&g.root.translation==0);
  g.move(5);check(g.page==5&&find(g.root,"5 / 5")!=null&&g.root.translation==12);
  motion.visible=false;g.finishTransition();check(!g.root.animator.pending&&g.root.alpha==1&&g.root.translation==0);
  motion.visible=true;g.resume();check(g.page==5&&g.root.alpha==1);
  g.firstRun=false;g.back();View old=g.root;g.close(false);check(!old.animator.pending&&old.alpha==1&&old.translation==0);
  check(!g.showing()&&motion.root.animator.pending&&motion.root.translation==-12&&motion.prefs.writes==0);
  g.openTutorial();check(!motion.root.animator.pending&&motion.root.alpha==1&&g.root.animator.pending);
  motion.destroyed=true;g.dispose();check(!old.animator.pending&&!g.showing()&&motion.root.alpha==1);
  android.animation.ValueAnimator.enabled=false;MainActivity disabled=new MainActivity();g=disabled.welcome;g.resume();g.move(5);
  check(g.root.alpha==1&&g.root.translation==0&&!g.root.animator.pending);
  press(g,"开始使用");check(disabled.root.alpha==1&&disabled.root.translation==0&&!disabled.root.animator.pending);
  // Floating logo stays within 4 dp, uses nonlinear easing, and owns no background work.
  android.animation.ValueAnimator.enabled=true;MainActivity floating=new MainActivity();g=floating.welcome;g.resume();
  android.animation.ObjectAnimator float1=g.logoFloat;ImageView logo1=g.logo;
  check(float1!=null&&float1.running&&float1.duration==3600&&float1.repeat==-1);
  check(float1.values.length==3&&float1.values[0]==0&&float1.values[1]==-4&&float1.values[2]==0);
  check(float1.interpolator instanceof android.view.animation.AccelerateDecelerateInterpolator);
  g.resume();check(g.logoFloat==float1);float1.sample(-3);check(logo1.translationY==-3);
  floating.visible=false;g.finishTransition();check(!float1.running&&g.logoFloat==null&&logo1.translationY==0);
  floating.visible=true;g.resume();check(g.logoFloat!=null&&g.logoFloat!=float1&&g.logoFloat.running);
  android.animation.ObjectAnimator float2=g.logoFloat;press(g,"下一步");check(!float2.running&&g.logoFloat==null&&g.logo==null&&logo1.translationY==0);
  g.back();check(g.page==0&&g.logoFloat!=null&&g.logoFloat.running);
  android.animation.ObjectAnimator float3=g.logoFloat;ImageView logo3=g.logo;floating.destroyed=true;g.dispose();check(!float3.running&&logo3.translationY==0&&g.logo==null);
  android.animation.ValueAnimator.enabled=false;MainActivity still=new MainActivity();still.welcome.resume();check(still.welcome.logo!=null&&still.welcome.logoFloat==null&&still.welcome.logo.translationY==0);
  System.out.println("Welcome and tutorial: "+cases+" cases PASS; production lifecycle and parent-aware widgets, Android layout not tested");
 }
}'''
paths=[]
for name,code in files.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf-8');paths.append(p)
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(C),*map(str,paths),*[str(S/(name+'.java')) for name in ['TutorialContent','WelcomeGuide','UiText']]],check=True)
subprocess.run(['java','-cp',str(C),'top.rongshangs.lumacurve.refactor.WelcomeTest'],check=True)
main=(S/'MainActivity.java').read_text(encoding='utf-8')
assert 'SettingsNotice' not in main and 'settingsNotice' not in main
assert 'welcome.resume();quickSetup.resume();if(!welcome.showing()&&!quickSetup.showing())resumeRuntime()' in main
assert 'if(welcome.back())return;' in main
assert 'button("再次查看使用教程",welcome::openTutorial,tutorial)' in main
pause=main[main.index('@Override public void onPause()'):]
assert pause.index('visible=false;')<pause.index('welcome.finishTransition();')<pause.index('super.onPause();')
assert 'if(visible&&!welcome.showing()&&!quickSetup.showing())' in main
assert 'welcome.save(saved)' in main and 'welcome.restore(saved)' in main
assert '"勿忘","灭","春暖花开","无缺"' in (S/"AboutMarquee.java").read_text("utf-8") and '兼容 HyperOS 的亮度个性化工具' in (S/"AboutPage.java").read_text("utf-8")
guide=(S/'WelcomeGuide.java').read_text(encoding='utf-8')
assert not any(token in guide for token in ['RootControl','BrightnessControl','postDelayed','CountDown','run("apply"','putString('])
print('Welcome integration: no settings gate, no countdown, startup ordering and no brightness writes PASS')

# Every new content phrase must remain readable in the existing English UI.
phrases=[]
for name in ['WelcomeGuide.java','TutorialContent.java']:
    for literal in re.findall(r'"(?:[^"\\]|\\.)*"',(S/name).read_text(encoding='utf-8')):
        value=ast.literal_eval(literal)
        if re.search('[\u4e00-\u9fff]',value) and value not in phrases:phrases.append(value)
java=O/'TutorialLanguageTest.java'
java.write_text('package top.rongshangs.lumacurve.refactor;public class TutorialLanguageTest{public static void main(String[] args){String[] phrases={'+','.join(json.dumps(s,ensure_ascii=False) for s in phrases)+'};for(String text:phrases){String en=UiText.translate(text,true);if(en.matches("(?s).*[\\u4e00-\\u9fff].*"))throw new AssertionError(text);}System.out.println("Tutorial English: "+phrases.length+" phrases PASS");}}',encoding='utf-8')
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(C),'-d',str(C),str(java)],check=True)
subprocess.run(['java','-cp',str(C),'top.rongshangs.lumacurve.refactor.TutorialLanguageTest'],check=True)
