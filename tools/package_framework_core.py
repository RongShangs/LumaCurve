"""Package the finite full-C-core experiment with verified sources; no firmware/module replacement."""
from pathlib import Path
import hashlib,json,subprocess,zipfile
from source_lists import sources_for
ROOT=Path(__file__).resolve().parents[1]
sha=lambda b:hashlib.sha256(b).hexdigest()
native=json.loads((ROOT/'build/framework-core-test04/build.json').read_text())
java=json.loads((ROOT/'build/framework-probe/verification.json').read_text())
assert java['ok'] and java['ramp_host_cases']==11 and java['output_session_host_cases']==16 and java['cadence_host_cases']==9 and java['client_lease_verified']
for report in (native,java):
 for source,digest in report['source_sha256'].items():assert sha((ROOT/source).read_bytes())==digest,source
assert sha((ROOT/'build/framework-core-test04/luma_framework_core').read_bytes())==native['sha256']
assert sha((ROOT/'build/framework-probe/LumaFrameworkProbe.jar').read_bytes())==java['jar_sha256']
for filename in ('framework_core_recovery.py','framework_core_shell.py','framework_core_selection.py'):
 subprocess.run(['python',str(ROOT/'tests'/filename)],check=True)
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-Wall','-Wextra','-Werror','-std=c11','-DLUMA_FRAMEWORK_BACKEND',
 '-I'+str(ROOT/'csrc'),'-I'+str(ROOT/'experimental/framework_output'),str(ROOT/'tests/framework_protocol.c'),
 '-o',str(ROOT/'build/framework-core-test04/protocol-test.exe')],check=True)
subprocess.run([str(ROOT/'build/framework-core-test04/protocol-test.exe')],check=True)
files={
 'build/framework-core-test04/luma_framework_core':'luma_framework_core',
 'build/framework-probe/LumaFrameworkProbe.jar':'LumaFrameworkProbe.jar',
 'module/process_scope.sh':'process_scope.sh','module/luma_curve.conf':'luma_curve.conf',
 'tools/probe_temporary_runtime.sh':'probe_temporary_runtime.sh',
 'tools/test_framework_core_android.sh':'test_framework_core_android.sh',
 'tools/framework_core_runtime.sh':'framework_core_runtime.sh','tools/framework_core_watchdog.sh':'framework_core_watchdog.sh',
 'docs/framework-core-test04使用说明.md':'README.md','docs/framework-core-test03实机结果.md':'PREVIOUS-RESULT.md',
 'LICENSE':'LICENSE','docs/来源与许可证.md':'PROVENANCE.md',
 'build/framework-core-test04/build.json':'native-build.json','build/framework-probe/verification.json':'java-verification.json',
 'build/framework-core-test04/selection-verification.json':'selection-verification.json',
}
sources=sources_for()+list((ROOT/'csrc').glob('*.h'))+list((ROOT/'experimental/framework_output').glob('*'))+list((ROOT/'tools/framework_probe').glob('*.java'))
sources += [ROOT/'tools'/name for name in ('build_framework_core.py','build_framework_probe.py','source_lists.py')]
sources += [ROOT/'tests'/name for name in ('framework_core_selection.py','framework_core_selection.c','framework_core_shell.py','framework_core_recovery.py','framework_protocol.c')]
for f in sources:
 if f.is_file():files[f.relative_to(ROOT).as_posix()]='source/'+f.relative_to(ROOT).as_posix()
payload={name:sha((ROOT/source).read_bytes()) for source,name in files.items()}
manifest={'build':native['build'],'android_device_verified':False,'github_published':False,'installed_module_replaced':False,
 'output_budget':'each acquisition feedback +/-0.01, live bounds take precedence','curve_coordinate':'legacy_raw_fraction_nominal',
 'checks':{'native_protocol_cases':15,'recovery_fixtures':6,'wrapper_fixtures':6,'session_cases':16,'ramp_cases':11,'cadence_cases':9,'independent_client_lease':True,'selection_negative_mutations':3},
 'payload_sha256':payload}
dist=ROOT/'dist';dist.mkdir(exist_ok=True);target=dist/'LumaCurve-framework-core-test04.zip'
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
 for source,name in files.items():
  data=(ROOT/source).read_bytes()
  if name.endswith('.sh'):assert b'\r' not in data,name
  info=zipfile.ZipInfo(name);info.compress_type=zipfile.ZIP_DEFLATED
  info.external_attr=((0o100755 if name.endswith('.sh') or name=='luma_framework_core' else 0o100644)<<16)
  z.writestr(info,data)
 z.writestr('manifest.json',json.dumps(manifest,indent=2)+'\n')
with zipfile.ZipFile(target) as z:
 assert z.testzip() is None
 for name,digest in payload.items():assert sha(z.read(name))==digest,name
 assert 'module.prop' not in z.namelist()
assert not any('firmware' in name.lower() or name.endswith('framework.jar') or name.endswith('services.jar') for name in files.values())
manifest['archive_sha256']=sha(target.read_bytes());manifest['size']=target.stat().st_size
(dist/'framework-core-test04-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(target);print('SHA256',manifest['archive_sha256'])
