"""Exercise production memory notice callbacks with UI doubles; no Android rendering claim."""
from pathlib import Path
import subprocess,re
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java'
O=R/'build/memory-controls-tests';O.mkdir(parents=True,exist_ok=True);source=S.read_text('utf-8')
def method(signature):
 a=source.index(signature);b=source.index('{',a)+1;depth=1
 while depth:depth+=(source[b]=='{')-(source[b]=='}');b+=1
 return source[a:b]
memory=method('    void makeMemorySettings()');warning=method('    void updateMemoryWarning()')
assert all(('compact("'+label+'"') not in memory for label in ['完整记忆','渐进记忆','不记忆'])
assert 'memoryToggleTouched=false;memoryStrengthDetails=null;' in memory
assert 'memoryStrengthDetails=b;updateMemoryWarning();' in memory
listener=memory.split('memorySwitch.setOnCheckedChangeListener(',1)[1].split(');\n',1)[0]
details=method('    LinearLayout settingsDetails(')
details_listener=details.split('toggle.setOnClickListener(',1)[1].split(');detailOpeners',1)[0]
test=r'''class View{static final int VISIBLE=0,GONE=8;int visibility=GONE;void setVisibility(int n){visibility=n;}int getVisibility(){return visibility;}}
class Toggle extends View{interface Click{void run(View v);}interface Change{void run(Toggle t,boolean checked);}Click click;Change change;String text;boolean checked;
 void setText(String s){text=s;}void setOnClickListener(Click c){click=c;}void performClick(){click.run(this);}void setOnCheckedChangeListener(Change c){change=c;}void setChecked(boolean value){if(checked!=value){checked=value;change.run(this,value);}}}
class Candidate{boolean memoryEnabled=true,memoryToggleTouched,dirty;View memoryDisabledWarning=new View(),memoryStrengthDetails=new View();Toggle memorySwitch=new Toggle(),toggle=new Toggle();int draws;Prefs prefs=new Prefs();
 static class Prefs{String key;boolean value;int writes;Prefs edit(){return this;}Prefs putBoolean(String k,boolean v){key=k;value=v;return this;}void apply(){writes++;}}
 Prefs getPreferences(int n){return prefs;}String tr(String s){return s;}void drawCurve(){draws++;updateMemoryWarning();}
 void attachSwitch(){memorySwitch.setOnCheckedChangeListener(LISTENER);}
 void attachDetails(String key){View body=memoryStrengthDetails;final String openText="收起详细参数",closedText="展开详细参数";toggle.setOnClickListener(DETAILS);}
 WARNING
}
public class MemoryControlsTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("memory notice "+cases);cases++;}static boolean shown(Candidate c){return c.memoryDisabledWarning.visibility==View.VISIBLE;}
 public static void main(String[] args){
 for(boolean enabled:new boolean[]{false,true}){Candidate c=new Candidate();c.memoryEnabled=enabled;c.attachSwitch();c.attachDetails("memory-strength");c.updateMemoryWarning();check(!shown(c));c.memorySwitch.setChecked(enabled);check(!c.memoryToggleTouched&&!c.dirty);c.updateMemoryWarning();check(!shown(c));
 c.toggle.performClick();check(shown(c)&&!c.memoryToggleTouched&&!c.dirty);check(c.prefs.writes==1&&c.prefs.key.equals("detail_memory-strength")&&c.prefs.value);c.toggle.performClick();check(!shown(c));
 c.memorySwitch.setChecked(!enabled);check(shown(c)&&c.memoryToggleTouched&&c.dirty&&c.draws==1);c.memorySwitch.setChecked(enabled);check(shown(c)&&c.draws==2);c.toggle.performClick();c.toggle.performClick();check(shown(c));
 c.memoryToggleTouched=false;c.memoryStrengthDetails.setVisibility(View.VISIBLE);c.updateMemoryWarning();check(shown(c));c.memoryStrengthDetails.setVisibility(View.GONE);c.updateMemoryWarning();check(!shown(c));
 c.attachDetails("outdoor");c.toggle.performClick();check(!shown(c));c.updateMemoryWarning();check(shown(c));
 c.memoryDisabledWarning=null;c.updateMemoryWarning();check(c.memoryEnabled==enabled);
 }
 System.out.println("Memory controls: "+cases+" cases PASS; actual callbacks, old preferences retained, Android not rendered");
 }}'''.replace('LISTENER',listener).replace('DETAILS',details_listener).replace('WARNING',warning)
p=O/'MemoryControlsTest.java';p.write_text(test,encoding='utf-8');C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(C),str(p)],check=True)
result=subprocess.run(['java','-cp',str(C),'MemoryControlsTest'],capture_output=True,text=True)
print(result.stdout,end='')
if result.returncode:print(result.stderr,end='')
result.check_returncode()
