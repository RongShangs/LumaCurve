"""Build/test the current production backend. No publication or device writes."""
from pathlib import Path
import subprocess,sys,json,hashlib,os,zipfile
ROOT=Path(__file__).resolve().parents[1]
os.chdir(ROOT)
env=os.environ.copy()
env.setdefault('PLAYWRIGHT_MODULE','D:/IOS/reverse-engineering/.tools/webui-tests/node_modules/playwright')
env.setdefault('BROWSER_EXECUTABLE','C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
checks=[]
commands=[
 [sys.executable,'tools/build_framework_probe.py'],
 [sys.executable,'tools/audit_logic_20260930.py'],
 [sys.executable,'tests/audit_negative.py'],
 [sys.executable,'tests/audit_upgrade.py'],
 [sys.executable,'tests/hyperos4_collection.py'],
 [sys.executable,'tests/diagnostic_export.py'],
 [sys.executable,'tests/core_reliability.py'],
 [sys.executable,'tests/core_optimization.py','--negative'],
 [sys.executable,'tests/brightness_preference.py'],
 [sys.executable,'tests/framework_core_selection.py'],
 [sys.executable,'tests/process_scope.py'],
 [sys.executable,'tests/framework_module_install.py'],
 [sys.executable,'tests/installer.py','--bash','C:/msys64/usr/bin/bash.exe'],
 ['node','tests/webui.cjs'],
 [sys.executable,'tools/build_framework_module.py']]
for command in commands:
 print('VERIFY '+command[1],flush=True)
 subprocess.run(command,check=True,env=env)
 checks.append(command[1])
package=ROOT/'dist/luma_curve-1.0.0-local-audit-repair01.zip'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(package) as archive:
 assert archive.testzip() is None
 info=json.loads(archive.read('local-build-info.json'))
 assert info['local_only'] and not info['published'] and info['build']=='20260930-audit-repair01'
 for name,digest in info['module_files_sha256'].items():
  assert hashlib.sha256(archive.read(name)).hexdigest()==digest,name
 for path in (ROOT/'module').rglob('*'):
  if not path.is_file():continue
  name=path.relative_to(ROOT/'module').as_posix()
  expected=path.read_bytes()
  if name=='module.prop':expected=b''.join(line for line in expected.splitlines(keepends=True) if not line.startswith(b'updateJson='))
  assert archive.read(name)==expected,name
 for name,digest in info['source_sha256'].items():assert sha(ROOT/name)==digest,name
 assert archive.read('collect_hyperos4_android.sh')==(ROOT/'tools/collect_hyperos4_android.sh').read_bytes()
 assert archive.read('framework-broker.jar')==(ROOT/'build/framework-probe/LumaFrameworkProbe.jar').read_bytes()
 assert archive.read('system/bin/luma_curve_daemon')[:4]==b'\x7fELF'
 checks.append('archive_sources_and_resources_match')
# Bind the complete verdict to current files, so it cannot validate later untested edits.
manifest={}
for folder in ['csrc','module','experimental/framework_output','tools','tests']:
 for path in (ROOT/folder).rglob('*'):
  if path.is_file() and '__pycache__' not in path.parts:manifest[path.relative_to(ROOT).as_posix()]=sha(path)
report={'ok':True,'checks':checks,'package':str(package),'package_sha256':sha(package),'source_sha256':manifest,'android_device_verified':False,'published':False}
(ROOT/'build/project-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Production verification PASS; '+str(package),flush=True)
