"""Bundle read-only prerequisites and the output-session prototype; no firmware."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
verification=json.loads((ROOT/'build/framework-probe/verification.json').read_text())
jar=ROOT/'build/framework-probe/LumaFrameworkProbe.jar'
assert verification['ok'] and verification['output_session_host_cases']==12
assert hashlib.sha256(jar.read_bytes()).hexdigest()==verification['jar_sha256']
for f,h in verification['source_sha256'].items():assert hashlib.sha256((ROOT/f).read_bytes()).hexdigest()==h,f
files={
 'build/framework-probe/LumaFrameworkProbe.jar':'LumaFrameworkProbe.jar',
 'tools/probe_framework05_android.sh':'probe_framework05_android.sh',
 'tools/probe_settings_runtime.sh':'probe_settings_runtime.sh',
 'module/process_scope.sh':'process_scope.sh',
 'docs/framework05模式与单位读取.md':'README.md',
 'docs/framework04实机结果与核心适配.md':'PREVIOUS-RESULT.md',
 'build/framework-probe/verification.json':'host-verification.json',
}
for f in list((ROOT/'tools/framework_probe').glob('LumaFrameworkProbe*.java'))+list((ROOT/'experimental/framework_output').glob('*.java')):
 files[f.relative_to(ROOT).as_posix()]='source/'+f.name
out=ROOT/'dist/LumaCurve-framework-probe05.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for source,name in files.items():z.write(ROOT/source,name)
with zipfile.ZipFile(out) as z:assert z.testzip() is None
manifest={'build':'20260929-framework05-settings','purpose':'read-only mode/preference/unit bridge validation','size':out.stat().st_size,
 'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'session_host_cases':12,
 'settings_android_verified':False,'module_core_changed':False,'github_published':False,
 'payload_sha256':{name:hashlib.sha256((ROOT/source).read_bytes()).hexdigest() for source,name in files.items()}}
(ROOT/'dist/framework05-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(out);print('SHA256',manifest['sha256'])
