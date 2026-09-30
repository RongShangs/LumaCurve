"""Run real export worker against a translated Android filesystem; no real nodes."""
from pathlib import Path
import tempfile,subprocess,re,tarfile,hashlib,json
ROOT=Path(__file__).resolve().parents[1];BASH='C:/msys64/usr/bin/bash.exe'
def shell(p):
 s=Path(p).resolve().as_posix();return '/'+s[0].lower()+s[2:]
checks=[]
with tempfile.TemporaryDirectory(prefix='luma-export-') as tmp:
 for mode in ('log','analysis','archive_failure','invalid_token'):
  folder=Path(tmp)/mode;folder.mkdir();module=folder/'module';module.mkdir()
  fixtures={'data/local/tmp/luma_curve_state':b'core_build=fixture\nframework_owned=1\n',
   'system/framework/framework.jar':b'framework','system/framework/services.jar':b'services',
   'vendor/etc/displayconfig/main.xml':b'<display/>','proc/uptime':b'1234',
   'sdcard/keep.txt':b'keep me'}
  for name,data in fixtures.items():
   p=folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
  (module/'luma_curvectl.sh').write_text("printf 'boot log fixture\\n'\n")
  def translate(text):
   return re.sub(r'/(sdcard|system_ext|system|product|vendor|odm|sys|data|proc)(?=[/\s\"])',lambda m:shell(folder/m[1]),text)
  collector=module/'collect_hyperos4_android.sh';collector.write_text(translate((ROOT/'tools/collect_hyperos4_android.sh').read_text(encoding='utf-8')),encoding='utf-8',newline='\n')
  script=module/'export.sh';source=(ROOT/'module/export_analysis.sh').read_text(encoding='utf-8')
  source=source.replace('MOD=${0%/*}',"MOD='"+shell(module)+"'")
  script.write_text(translate(source),encoding='utf-8',newline='\n')
  token='fixture' if mode!='invalid_token' else '../oops'
  work=folder/'data/local/tmp/luma_curve-export-fixture';work.mkdir();(work/'status').write_text('running\nready\n')
  actual='analysis' if mode=='archive_failure' else mode
  code=f"""export PATH=/usr/bin:/bin:$PATH LANG=C.UTF-8
id() {{ echo 0; }}
getprop() {{ echo fixture; }}
getenforce() {{ echo Enforcing; }}
settings() {{ echo 1; }}
dumpsys() {{ echo 'read-only fixture'; }}
cmd() {{ echo 'help fixture'; }}
sleep() {{ :; }}
timeout() {{ shift; if [ "$1" = sh ]; then shift; local script="$1"; (set --; . "$script"); else "$@"; fi; }}
{'tar() { return 1; }' if mode=='archive_failure' else ''}
. '{shell(script)}' worker '{actual}' '{token}'
"""
  if mode=='invalid_token':code=code.replace("worker 'invalid_token'","worker 'log'")
  result=subprocess.run([BASH,'-c',code],capture_output=True,encoding='utf-8',timeout=25)
  assert (result.returncode==0)==(mode in ('log','analysis')),(mode,result.stdout,result.stderr)
  for name,data in fixtures.items():assert (folder/name).read_bytes()==data,name
  status=(work/'status').read_text(encoding='utf-8')
  if mode in ('log','analysis'):
   assert status.startswith('done\n'),status
   files=[p for p in (folder/'sdcard').iterdir() if p.suffix in ('.log','.gz')]
   assert len(files)==1
   p=files[0];assert hashlib.sha256(p.read_bytes()).hexdigest() in Path(str(p)+'.sha256').read_text()
   if mode=='log':
    assert b'boot log fixture' in p.read_bytes() and fixtures['data/local/tmp/luma_curve_state'] in p.read_bytes()
   else:
    with tarfile.open(p) as z:
     names=z.getnames();assert 'current-boot.log' in names and 'device-state.txt' in names
     assert any(n.endswith('framework.jar') for n in names)
     assert any(n.endswith('display.txt') for n in names)
     assert any(n.endswith('vendor/etc/displayconfig/main.xml') for n in names)
    assert not (work/'device').exists()
  elif mode=='archive_failure':assert status.startswith('error\n')
  checks.append(mode)
(ROOT/'build/export-verification.json').write_text(json.dumps({'ok':True,'checks':checks,'device_verified':False,'source_sha256':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ['module/export_analysis.sh','tools/collect_hyperos4_android.sh']}},indent=2)+'\n')
print('Exports: log includes state, complete analysis archive, root-directory outputs/checksums, unchanged inputs, failure and traversal rejection PASS')
