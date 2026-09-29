"""Build an app_process DEX probe and run isolated controller restoration tests."""
from pathlib import Path
import argparse,subprocess,zipfile,hashlib,json,shutil
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,default=Path('D:/App/SDK'));p.add_argument('--build-tools',default='37.0.0');args=p.parse_args()
folder=ROOT/'build/framework-probe';classes=folder/'classes';classes.mkdir(parents=True,exist_ok=True)
src=ROOT/'tools/framework_probe'
compile_sources=list(src.glob('LumaFrameworkProbe*.java'))+list((ROOT/'experimental/framework_output').glob('Luma*.java'))
subprocess.run(['javac','--release','8','-d',str(classes)]+[str(f) for f in compile_sources],check=True)
test=subprocess.run(['java','-cp',str(classes),'LumaFrameworkProbeTest'],capture_output=True,text=True,check=True)
print(test.stdout.splitlines()[-1])
temporary=subprocess.run(['java','-cp',str(classes),'LumaFrameworkProbeTemporaryTest'],capture_output=True,text=True,check=True)
print(temporary.stdout.splitlines()[-1])
session=subprocess.run(['java','-cp',str(classes),'LumaFrameworkOutputSessionTest'],capture_output=True,text=True,check=True)
print(session.stdout.splitlines()[-1])
ramp=subprocess.run(['java','-cp',str(classes),'LumaFrameworkOutputRampTest'],capture_output=True,text=True,check=True)
print(ramp.stdout.splitlines()[-1])
lease=subprocess.run(['java','-cp',str(classes),'LumaFrameworkOutputLeaseTest'],capture_output=True,text=True,check=True)
print(lease.stdout.splitlines()[-1])
cadence=subprocess.run(['java','-cp',str(classes),'LumaFrameworkOutputCadenceTest'],capture_output=True,text=True,check=True)
print(cadence.stdout.splitlines()[-1])
legacy=subprocess.run(['java','-cp',str(classes),'LumaLegacyBacklightCoordinateTest'],capture_output=True,text=True,check=True)
print(legacy.stdout.splitlines()[-1])
feedback=subprocess.run(['java','-cp',str(classes),'LumaFrameworkOutputFeedbackTest'],capture_output=True,text=True,check=True)
print(feedback.stdout.splitlines()[-1])
slider=subprocess.run(['java','-cp',str(classes),'LumaFrameworkSliderOverrideTest'],capture_output=True,text=True,check=True)
print(slider.stdout.splitlines()[-1])
inputs=[f for f in classes.glob('Luma*.class') if 'Test' not in f.name]
jar=folder/'LumaFrameworkProbe.jar'
subprocess.run(['java','-cp',str(args.sdk/'build-tools'/args.build_tools/'lib/d8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',str(args.sdk/'platforms/android-37.0/android.jar'),'--output',str(jar)]+[str(f) for f in inputs],check=True)
with zipfile.ZipFile(jar) as z:assert z.testzip() is None and 'classes.dex' in z.namelist()
report={'ok':True,'host_cases':10,'temporary_host_cases':12,'output_session_host_cases':16,'ramp_host_cases':11,'cadence_host_cases':9,'legacy_coordinate_cases':10,'physical_feedback_cases':10,'slider_override_cases':11,'client_lease_verified':True,'android_device_verified':False,'jar_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
        'source_sha256':{f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in compile_sources}}
(folder/'verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(jar)
