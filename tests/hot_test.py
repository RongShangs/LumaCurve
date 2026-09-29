"""Exercise the Android hot-test script in an isolated host fixture."""
from pathlib import Path
import hashlib,json,subprocess,tempfile,zipfile,sys
ROOT=Path(__file__).resolve().parents[1]
bash='C:/msys64/usr/bin/bash.exe'
def shellpath(p):
 s=Path(p).resolve().as_posix();return '/'+s[0].lower()+s[2:]
checks=[]
with tempfile.TemporaryDirectory(prefix='hot-test-',dir=ROOT/'build') as td:
 root=Path(td);helpers=root/'helpers';helpers.mkdir();
 extractor=helpers/'extract.py';extractor.write_text('import sys,zipfile\nwith zipfile.ZipFile(sys.argv[2]) as z: sys.stdout.buffer.write(z.read(sys.argv[3]))\n',encoding='utf-8');
 unzip=helpers/'unzip';unzip.write_text("#!/bin/sh\nexec '"+shellpath(sys.executable)+"' '"+shellpath(extractor)+"' \"$@\"\n",encoding='utf-8');
 subprocess.run([bash,'-c','export PATH=/usr/bin:/bin:$PATH; chmod +x \"$1\"','_',shellpath(unzip)],check=True);
 mod=root/'module';(mod/'system/bin').mkdir(parents=True)
 binary=mod/'system/bin/luma_curve_daemon';pause=root/'pause'
 old=b'#!/bin/sh\n[ "$1" != --build-info ] || echo old\nexit 0\n'
 # A running old daemon locks the node: the probe must run AFTER pause.
 new=('''#!/bin/sh
[ "$1" != --build-info ] || echo test03
if [ "$1" = --check-install ]; then
  [ -f "'''+shellpath(pause)+'''" ] || exit 2
fi
exit 0
''').encode()
 ctl=mod/'luma_curvectl.sh'
 ctl.write_text('''#!/bin/sh
case "$1" in
pause) touch "'''+shellpath(pause)+'''";;
resume|restart) rm -f "'''+shellpath(pause)+'''"; if grep -q FAIL_START "'''+shellpath(binary)+'''"; then exit 1; fi;;
esac
''',encoding='utf-8')
 script=(ROOT/'tools/hot_test_android.sh').read_text(encoding='utf-8').replace('[ "$(id -u)" = 0 ]','[ 1 = 1 ]').replace('MOD=/data/adb/modules/luma_curve',"MOD='"+shellpath(mod)+"'").replace('PAUSE=/data/local/tmp/luma_curve.paused',"PAUSE='"+shellpath(pause)+"'")
 runner=root/'runner.sh';runner.write_text('export PATH='+shellpath(helpers)+':/usr/bin:/bin:$PATH\n'+script,encoding='utf-8',newline='\n')
 for name,payload,identity in [('success',new,'luma_curve'),('wrong-id',new,'other'),('failed-probe',b'#!/bin/sh\nexit 1\n','luma_curve'),('startup-rollback',new+b'# FAIL_START\n','luma_curve'),('paused-preserved',new,'luma_curve')]:
  binary.write_bytes(old);pause.unlink(missing_ok=True)
  subprocess.run([bash,'-c','export PATH=/usr/bin:/bin:$PATH; chmod +x "$1"', '_',shellpath(binary)],check=True)
  if name=='paused-preserved':pause.touch()
  archive=root/(name+'.zip')
  with zipfile.ZipFile(archive,'w') as z:z.writestr('module.prop','id='+identity+'\n');z.writestr('system/bin/luma_curve_daemon',payload)
  result=subprocess.run([bash,shellpath(runner),shellpath(archive)],capture_output=True,text=True,encoding='utf-8')
  if name in ('success','paused-preserved'):assert result.returncode==0 and binary.read_bytes()==new,(name,result.stderr,result.stdout)
  else:assert result.returncode!=0 and binary.read_bytes()==old,(name,result.stderr,result.stdout)
  assert pause.exists()==(name=='paused-preserved'),name
  checks.append({'name':name,'ok':True})
report={'ok':True,'checks':checks,'host_fixture_only':True,'android_device_verified':False,
        'source_sha256':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in ('tools/hot_test_android.sh','tests/hot_test.py')}}
(ROOT/'build/hot-test-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Hot-test script: 5 success/validation/rollback/pause scenarios PASS')
