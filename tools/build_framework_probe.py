"""Build an app_process DEX probe and run isolated controller restoration tests."""
from pathlib import Path
import argparse,subprocess,zipfile,hashlib,json,shutil
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,default=Path('D:/App/SDK'));p.add_argument('--build-tools',default='37.0.0');args=p.parse_args()
folder=ROOT/'build/framework-probe';classes=folder/'classes';classes.mkdir(parents=True,exist_ok=True)
src=ROOT/'tools/framework_probe'
subprocess.run(['javac','--release','8','-d',str(classes),str(src/'LumaFrameworkProbe.java'),str(src/'LumaFrameworkProbeTest.java')],check=True)
test=subprocess.run(['java','-cp',str(classes),'LumaFrameworkProbeTest'],capture_output=True,text=True,check=True)
print(test.stdout.splitlines()[-1])
inputs=[f for f in classes.glob('LumaFrameworkProbe*.class') if not f.name.startswith('LumaFrameworkProbeTest')]
jar=folder/'LumaFrameworkProbe.jar'
subprocess.run(['java','-cp',str(args.sdk/'build-tools'/args.build_tools/'lib/d8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',str(args.sdk/'platforms/android-37.0/android.jar'),'--output',str(jar)]+[str(f) for f in inputs],check=True)
with zipfile.ZipFile(jar) as z:assert z.testzip() is None and 'classes.dex' in z.namelist()
report={'ok':True,'host_cases':10,'android_device_verified':False,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
        'source_sha256':{f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in src.glob('*.java')}}
(folder/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(jar)
