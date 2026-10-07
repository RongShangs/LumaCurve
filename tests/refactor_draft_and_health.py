"""Exercise production draft completion and native health parsing without Android."""
from pathlib import Path
import subprocess

R = Path(__file__).resolve().parents[1]
S = R / 'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O = R / 'build/refactor-draft-health-tests'
J = R / 'build/refactor-diagnostics/json-20240303.jar'
text = (S / 'MainActivity.java').read_text(encoding='utf-8')
start = text.index('    boolean draftStillMatches(')
method = text[start:text.index('    void run(', start)]
completion = 'dirty=!draftStillMatches(payload);'
assert completion in text, 'apply completion must preserve changes made during the request'
config = (S / 'ConfigurationFile.java').read_text(encoding='utf-8')
start = config.index('    static boolean sameOptions(')
same = config[start:config.index('    static final String[] FLAGS', start)]
files = {
    'android/system/Os.java': '''package android.system;public class Os{public static void chmod(String p,int m){}public static void rename(String a,String b){}}''',
    'android/os/SystemClock.java': '''package android.os;public class SystemClock{public static long uptimeMillis(){return 10000;}}''',
    'top/rongshangs/lumacurve/refactor/ConfigurationFile.java': 'package top.rongshangs.lumacurve.refactor;import org.json.*;import java.util.*;class ConfigurationFile{'+same+'}',
    'top/rongshangs/lumacurve/refactor/MainActivity.java': '''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;
class MainActivity{JSONObject options=new JSONObject();boolean invalid,dirty;JSONObject configuration()throws Exception{if(invalid)throw new Exception("invalid draft");return options;}
'''+method+'void completed(String payload){'+completion+'}}',
    'top/rongshangs/lumacurve/refactor/DraftHealthTest.java': '''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;
public class DraftHealthTest{
static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
static String payload(JSONObject j){return Base64.getEncoder().encodeToString(j.toString().getBytes(StandardCharsets.UTF_8));}
public static void main(String[] args)throws Exception{
 MainActivity a=new MainActivity();a.options.put("dark_lock_enabled",true).put("memory_strength",.3f);String saved=payload(a.options);
 a.dirty=true;a.completed(saved);check(!a.dirty);
 a.options.put("dark_lock_enabled",false);a.completed(saved);check(a.dirty&&!a.options.getBoolean("dark_lock_enabled"));
 a.options.put("dark_lock_enabled",true).put("memory_strength",.4f);a.completed(saved);check(a.dirty);
 a.options=new JSONObject().put("memory_strength",.3d).put("dark_lock_enabled",true);a.completed(saved);check(!a.dirty);
 a.invalid=true;a.completed(saved);check(a.dirty);a.invalid=false;
 for(String bad:new String[]{"invalid!",payload(new JSONObject()),null}){a.completed(bad);check(a.dirty);}
 check(RawPanelLease.healthMatches("session 1 0 10000\\n","session",10000));
 check(RawPanelLease.healthMatches("session 1 0 5000\\n","session",10000));
 check(!RawPanelLease.healthMatches("session 1 0 4999\\n","session",10000));
 check(!RawPanelLease.healthMatches("session 1 0 10001\\n","session",10000));
 for(String bad:new String[]{"session 1 0", "old 1 0 10000", "session 0 0 10000", "session 1 13 10000", "session 1 0 -1", "session 1 0 9223372036854775808", "session 1 0 nan", "session 1 0 10000 extra", ""})check(!RawPanelLease.healthMatches(bad,"session",10000));
 // A stalled/killed writer's last success expires even while its session matches.
 check(RawPanelLease.healthMatches("session 1 0 10000","session",14999));
 check(!RawPanelLease.healthMatches("session 1 0 10000","session",15001));
 System.out.println("Draft completion/native health: "+cases+" cases PASS; production methods, Android not tested");
}}
''',
}
paths = []
for name, source in files.items():
    p = O / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(source, encoding='utf-8')
    paths.append(p)
classes = O / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '8', '-cp', str(J), '-d', str(classes), *map(str, paths), str(S / 'RawPanelLease.java')], check=True)
subprocess.run(['java', '-cp', str(classes)+';'+str(J), 'top.rongshangs.lumacurve.refactor.DraftHealthTest'], check=True, cwd=R)
