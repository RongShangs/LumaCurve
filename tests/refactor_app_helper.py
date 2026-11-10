"""Execute grouped config using only the actual selected Root-helper class archive."""
from pathlib import Path
import os,subprocess,sys,zipfile
R=Path(__file__).resolve().parents[1];archive=Path(sys.argv[1]);O=R/'build/app-helper-config-tests';O.mkdir(exist_ok=True)
J=R/'build/refactor-diagnostics/json-20240303.jar'
with zipfile.ZipFile(archive) as z:
 assert 'top/rongshangs/lumacurve/refactor/BrightnessControlOptions$AppGroup.class' in z.namelist(), 'Root helper must include grouped configuration type'
p=O/'AppHelperTest.java';p.write_text('''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;
public class AppHelperTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("helper config "+cases);cases++;}
 public static void main(String[] args)throws Exception{
  JSONObject source=new JSONObject().put("manual_brightness_apps",new JSONArray(Arrays.asList("com.old.pause"))).put("independent_brightness_apps",new JSONArray(Arrays.asList("com.demo.single"))).put("independent_brightness_groups",new JSONArray().put(new JSONObject().put("id","videos").put("name","Videos").put("apps",new JSONArray(Arrays.asList("com.demo.a","com.demo.b"))))).put("independent_brightness_keep_lock",true).put("independent_brightness_keep_reboot",true);
  BrightnessControlOptions options=BrightnessControlOptions.parseStored(source);check(options.manualApps.contains("com.old.pause"));check(options.independentKey("com.demo.a").equals("group:videos"));check(options.independentKey("com.demo.single").equals("app:com.demo.single"));check(options.independentKeepLock&&options.independentKeepReboot);
  JSONObject saved=new JSONObject();options.put(saved);BrightnessControlOptions round=BrightnessControlOptions.parseStored(saved);check(round.applications().size()==4&&round.independentGroups.size()==1);check(round.manualApps.equals(options.manualApps));
  round.selectApplications("videos",Arrays.asList("com.old.pause"),Arrays.asList("com.old.pause"));check(round.manualApps.isEmpty()&&round.independentGroups.get(0).apps.contains("com.old.pause"));
  System.out.println("App helper configuration: "+cases+" cases PASS; only packaged helper classes, no full-app classpath");
 }
}''','utf-8')
C=O/'classes';C.mkdir(exist_ok=True);classpath=os.pathsep.join([str(archive),str(J)])
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',classpath,'-d',str(C),str(p)],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(C),classpath]),'top.rongshangs.lumacurve.refactor.AppHelperTest'],check=True)
