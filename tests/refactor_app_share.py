"""Retired sharing retains stored choices but cannot replay window values."""
from pathlib import Path
import ast,os,re,subprocess
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/app-share-retirement-tests';J=R/'build/refactor-diagnostics/json-20240303.jar'
tree=ast.parse((R/'tests/refactor_app_isolation.py').read_text('utf-8'))
files=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='files' for t in n.targets)))
base='top/rongshangs/lumacurve/refactor/'
files[base+'IsolationTest.java']=files[base+'IsolationTest.java'].replace('s.brightnessControl.options.appIsolation=true;','s.brightnessControl.options.appIsolation=true;s.brightnessControl.options.appIsolationShare=true;').replace('check(AppBrightnessIsolation.supported(s));','check(AppBrightnessIsolation.supported(s));check(!AppBrightnessIsolation.shareSupported(s));check(!AppBrightnessIsolation.status(s).getBoolean("share_enabled"));').replace('check(hooks.isEmpty()&&AppBrightnessIsolation.profiles.get(cls).route.equals(AppBrightnessIsolation.NATIVE));','check(hooks.isEmpty()&&AppBrightnessIsolation.profiles.get(cls).route.equals(AppBrightnessIsolation.NATIVE));check(AppBrightnessIsolation.status(s).getBoolean("native_isolated")&&AppBrightnessIsolation.status(s).getBoolean("enabled"));').replace('JSONObject old=new JSONObject();','JSONObject dormant=new JSONObject().put("app_brightness_isolation_share",true);BrightnessControlOptions legacy=BrightnessControlOptions.parseStored(dormant);JSONObject retained=new JSONObject();legacy.put(retained);check(BrightnessControlOptions.parseStored(retained).appIsolationShare);check(!AppBrightnessIsolation.status(s).getBoolean("share_enabled"));JSONObject old=new JSONObject();')
total=0
for scenario in ['scoped','native']:
 out=O/scenario;out.mkdir(parents=True,exist_ok=True);paths=[]
 for name,code in files.items():
  if scenario=='native':
   if name.endswith('DisplayPowerControllerImpl.java'):code=code.replace('requestBrightnessSync(float b)','oldSync(float b)').replace('consumePendingBrightnessSync()','oldConsume()')
   if name.endswith('DisplayPowerController.java'):code=code.replace('syncBrightnessToSettingAndUi(','oldSync(')
  p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,'utf-8');paths.append(p)
 C=out/'classes';C.mkdir(exist_ok=True)
 names=['AppBrightnessIsolation','BrightnessControlOptions','hooking/XposedBridge','hooking/XC_MethodHook','hooking/HookTransaction']
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*[str(S/(n+'.java')) for n in names],*map(str,paths)],check=True)
 result=subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.IsolationTest',scenario],check=True,capture_output=True,text=True);print(result.stdout,end='');total+=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
print(f'App brightness share total: {total} cases PASS; sharing retired, old flag retained, native isolation effective')
