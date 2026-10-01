"""Build a LumaCurve 2.0.0 LSPosed app and recovery bundle."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, urllib.request, zipfile, re

parser=argparse.ArgumentParser();parser.add_argument('--skip-device-fixtures',action='store_true',help='Rebuild without locally supplied proprietary firmware disassembly');args=parser.parse_args()

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'experimental/refactor_hook'
OUT=ROOT/'build/refactor-hook'
SDK=Path('D:/App/SDK'); BT=SDK/'build-tools/37.0.0'; ANDROID=SDK/'platforms/android-37.0/android.jar'
OUT.mkdir(parents=True,exist_ok=True)
DEP=OUT/'deps/api-82.jar';DEP.parent.mkdir(exist_ok=True)
API_URL='https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar'
API_SHA='f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'
if not DEP.exists():
    with urllib.request.urlopen(API_URL,timeout=30) as response:DEP.write_bytes(response.read())
assert hashlib.sha256(DEP.read_bytes()).hexdigest()==API_SHA,'compile dependency changed'
def run(args):subprocess.run([str(a) for a in args],check=True)
CLASSES=OUT/'classes'
if CLASSES.exists():
    assert CLASSES.resolve().parent==OUT.resolve(),'unexpected generated class directory'
    shutil.rmtree(CLASSES)
CLASSES.mkdir()
run(['javac','-encoding','UTF-8','--release','8','-cp',str(ANDROID)+';'+str(DEP),'-d',CLASSES,*sorted((SRC/'java').rglob('*.java'))])
run(['javac','-encoding','UTF-8','--release','8','-cp',CLASSES,'-d',CLASSES,*sorted((SRC/'tests').glob('*.java'))])
tested=subprocess.run(['java','-cp',str(CLASSES),'top.rongshangs.lumacurve.refactor.RefactorTest'],check=True,capture_output=True,text=True)
print(tested.stdout,end='');host_cases=int(re.search(r'(\d+) cases PASS',tested.stdout).group(1))
if not args.skip_device_fixtures:run(['python',ROOT/'tests/refactor_hook_firmware.py'])
else:print('OEM firmware checks skipped explicitly; runtime compatibility validation remains enabled')
COMPILED=OUT/'resources.zip';UNSIGNED=OUT/'unsigned.apk'
run([BT/'aapt2.exe','compile','--dir',SRC/'res','-o',COMPILED])
run([BT/'aapt2.exe','link','-I',ANDROID,'--manifest',SRC/'AndroidManifest.xml','-A',SRC/'assets','-o',UNSIGNED,COMPILED])
DEX=OUT/'dex';DEX.mkdir(exist_ok=True)
inputs=[p for p in CLASSES.rglob('*.class') if not p.name.startswith('RefactorTest')]
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','34','--lib',ANDROID,'--lib',DEP,'--output',DEX,*inputs])
with zipfile.ZipFile(UNSIGNED,'a',zipfile.ZIP_DEFLATED) as z:z.write(DEX/'classes.dex','classes.dex')
ALIGNED=OUT/'aligned.apk';run([BT/'zipalign.exe','-f','-p','4',UNSIGNED,ALIGNED])
KEY=OUT/'test-signing.p12'
if not KEY.exists():run(['keytool','-genkeypair','-keystore',KEY,'-storepass','androidtest','-keypass','androidtest','-alias','lumacurve-experimental','-dname','CN=LumaCurve Experimental,O=RongShangs','-keyalg','RSA','-keysize','3072','-validity','3650'])
DIST=ROOT/'dist';DIST.mkdir(exist_ok=True)
APK=DIST/'HyperLux-2.0.0.apk'
run(['java','-jar',BT/'lib/apksigner.jar','sign','--ks',KEY,'--ks-pass','pass:androidtest','--ks-key-alias','lumacurve-experimental','--out',APK,ALIGNED])
run(['java','-jar',BT/'lib/apksigner.jar','verify','--verbose',APK])
run([BT/'zipalign.exe','-c','4',APK])
# Root recovery does not load any Xposed classes, and works if the app is uninstalled.
HELPER=DIST/'luma-refactor-helper.jar'
names={'RootControl.class','RootSettings.class','CurvePlan.class','ThermalPolicy.class','MemoryPolicy.class','DelayPolicy.class','LegacyModules.class'}
run(['java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--min-api','34','--lib',ANDROID,'--output',HELPER,*[p for p in inputs if p.name in names]])
run(['C:/msys64/usr/bin/bash.exe','-n',SRC/'restore_refactor_hook_android.sh'])
meta={'build':'release-2.0.0-r2','version':'2.0.0','version_code':20000,'app_name':'HyperLux','architecture':'oem_refactor_base_anchor_hook',
      'device_verified':False,'compile_api':82,'compile_api_sha256':API_SHA,
      'sdk_compile':37,'sdk_min':34,'separate_output_daemon':False,
      'firmware_static_checks':not args.skip_device_fixtures,'host_cases':host_cases,
      'package':'top.rongshangs.lumacurve','languages':['zh','en'],'direct_point_editor':True,'system_light_change_delays_optional':True,'apk_update_repo':'RongShangs/LumaCurve',
      'module_long_term_learning':False,'oem_anchor_memory_adjustable':True,'thermal_display_relaxation':True,'source_sha256':{p.relative_to(SRC).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in (SRC/'java').rglob('*.java')},
      'apk_sha256':hashlib.sha256(APK.read_bytes()).hexdigest()}
(OUT/'verification.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
with zipfile.ZipFile(APK) as z:
    assert z.testzip() is None
    assert z.read('assets/xposed_init').strip()==b'top.rongshangs.lumacurve.refactor.HookEntry'
    assert 'classes.dex' in z.namelist()
    assert b'Lde/robv/android/xposed/XposedBridge;' in z.read('classes.dex')
    assert not any(name.endswith('.jar') or 'firmware' in name for name in z.namelist())
source=DIST/'LumaCurve-2.0.0-source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as z:
    for p in sorted(SRC.rglob('*')):
        if p.is_file():z.write(p,p.relative_to(ROOT))
    z.write(ROOT/'LICENSE','LICENSE');z.write(ROOT/'README.md','README.md');z.write(Path(__file__),'tools/build_refactor_hook_test.py');z.write(ROOT/'tests/refactor_hook_firmware.py','tests/refactor_hook_firmware.py')
    for name in ['sync_website.py','package_website.py']:z.write(ROOT/'tools'/name,'tools/'+name)
    for p in sorted((ROOT/'website').rglob('*')):
        if p.is_file() and 'downloads' not in p.relative_to(ROOT/'website').parts and p.name not in ['update.json','update.js']:
            z.write(p,p.relative_to(ROOT))
script=DIST/'restore_refactor_hook_android.sh';shutil.copy2(SRC/script.name,script)
shutil.copy2(SRC/'README.md',DIST/'LumaCurve-2.0.0-README.md')
bundle=DIST/'LumaCurve-2.0.0.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
    for file in [APK,HELPER,script,DIST/'LumaCurve-2.0.0-README.md',source]:z.write(file,file.name)
    z.write(OUT/'verification.json','build-info.json')
print(APK);print('SHA256',meta['apk_sha256']);print(bundle)
