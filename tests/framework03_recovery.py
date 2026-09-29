"""Host fixtures for independent recovery; no device permission claims."""
from pathlib import Path
import subprocess, tempfile, os
ROOT=Path(__file__).resolve().parents[1]
BASH='C:/msys64/usr/bin/bash.exe'
def sp(p):
 s=p.resolve().as_posix();return '/'+s[0].lower()+s[2:]
cases=('normal','originally_paused','release_failure','uncleared','auto_changed','deadline')
with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='framework03-') as tmp:
 for case in cases:
  folder=Path(tmp)/case;folder.mkdir();run=folder/'run';run.mkdir();out=folder/'out';out.mkdir()
  if case!='deadline':(run/'test-finished').touch()
  (folder/'process_scope.sh').write_text('lc_scope_detach_self() { return 0; }\n',encoding='utf-8',newline='\n')
  release_ok=case!='release_failure'
  (folder/'probe_temporary_runtime.sh').write_text('echo "RELEASE_REQUEST sent"\nexit '+('0' if release_ok else '2')+'\n',encoding='utf-8',newline='\n')
  ctl=folder/'ctl.sh';ctl.write_text('test "$1" = resume && touch "'+sp(out/'resumed')+'"\n',encoding='utf-8',newline='\n')
  script=(ROOT/'tools/probe_temporary_watchdog.sh').read_text(encoding='utf-8')
  script=script.replace('/data/local/tmp/luma-framework03.*',sp(run))
  script=script.replace('/data/adb/modules/luma_curve/luma_curvectl.sh',sp(ctl))
  value='0.09' if case=='uncleared' else 'NaN'
  auto='false' if case=='auto_changed' else 'true'
  prefix='''export PATH=/usr/bin:/bin:$PATH
sleep() { :; }
timeout() { shift; "$@"; }
dumpsys() { printf '%s\\n' 'Display Power Controller:' '  mDisplayId=0' '  mTemporaryScreenBrightness:'''+value+"' '  mUseAutoBrightness="+auto+"'; }\n"
  runner=folder/'watchdog.sh';runner.write_text(prefix+script,encoding='utf-8',newline='\n')
  result=subprocess.run([BASH,sp(runner),sp(run),sp(out),'0' if case=='originally_paused' else '1','fixture_app'],capture_output=True)
  ok=case in ('normal','originally_paused','deadline')
  assert (result.returncode==0)==ok,(case,result.stderr)
  assert (run/'restored').exists()==ok,case
  assert (out/'resumed').exists()==(ok and case!='originally_paused'),case
  assert (run/'restore-failed').exists()==(not ok),case
  if case=='deadline':assert 'deadline reached' in (out/'recovery.txt').read_text()
print('Framework03 recovery: 6 cases PASS; Android watchdog still needs validation')
