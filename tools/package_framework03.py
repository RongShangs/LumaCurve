"""Package the standalone temporary-brightness experiment, never device firmware."""
from pathlib import Path
import hashlib,json,zipfile,subprocess
ROOT=Path(__file__).resolve().parents[1]
for test in ('framework03_recovery.py','framework03_shell.py'):
 subprocess.run(['python',str(ROOT/'tests'/test)],cwd=ROOT,check=True)
verification=json.loads((ROOT/'build/framework-probe/verification.json').read_text())
jar=ROOT/'build/framework-probe/LumaFrameworkProbe.jar'
assert verification['ok'] and verification['temporary_host_cases']==8
assert hashlib.sha256(jar.read_bytes()).hexdigest()==verification['jar_sha256']
for f,h in verification['source_sha256'].items():assert hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==h,f
files={
 'build/framework-probe/LumaFrameworkProbe.jar':'LumaFrameworkProbe.jar',
 'tools/probe_framework03_android.sh':'probe_framework03_android.sh',
 'tools/probe_temporary_runtime.sh':'probe_temporary_runtime.sh',
 'tools/probe_temporary_watchdog.sh':'probe_temporary_watchdog.sh',
 'module/process_scope.sh':'process_scope.sh',
 'docs/framework03手机控制试验.md':'README.md',
 'docs/framework02实机分析与接管结论.md':'ANALYSIS.md',
 'build/framework-probe/verification.json':'host-verification.json',
}
for f in (ROOT/'tools/framework_probe').glob('LumaFrameworkProbe*.java'):
 files[f.relative_to(ROOT).as_posix()]='source/'+f.name
out=ROOT/'dist/LumaCurve-framework-probe03.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for source,name in files.items():z.write(ROOT/source,name)
with zipfile.ZipFile(out) as z:assert z.testzip() is None
manifest={'build':'20260929-framework03','purpose':'temporary framework strategy experiment','size':out.stat().st_size,
 'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'controller_cases':8,'shell_recovery_cases':6,'shell_wrapper_cases':5,
 'android_device_verified':False,'module_core_changed':False,'github_published':False,
 'payload_sha256':{name:hashlib.sha256((ROOT/source).read_bytes()).hexdigest() for source,name in files.items()}}
(ROOT/'dist/framework03-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(out);print('SHA256',manifest['sha256'])
