"""End-to-end wrapper fixtures: isolate Android APIs, keep actual watchdog logic."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]; BASH='C:/msys64/usr/bin/bash.exe'
def sp(p):
 s=p.resolve().as_posix();return '/'+s[0].lower()+s[2:]
cases=('normal','sustained','originally_paused','firmware_mismatch','preflight_failure','existing_temporary')
with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='framework03-shell-') as td:
 for case in cases:
  d=Path(td)/case;d.mkdir();runtime=d/'runtime';runtime.mkdir()
  pause=d/'paused';running=d/'running';ctl=d/'ctl.sh';log=d/'ctl.log'
  if case=='originally_paused':pause.touch()
  else:running.touch()
  ctl.write_text(f'''echo "$1" >> '{sp(log)}'
case "$1" in
pause) touch '{sp(pause)}'; rm -f '{sp(running)}';;
resume) rm -f '{sp(pause)}'; touch '{sp(running)}';;
esac
''',encoding='utf-8',newline='\n')
  (d/'process_scope.sh').write_text('lc_scope_detach_self() { return 0; }\n',encoding='utf-8',newline='\n')
  (d/'LumaFrameworkProbe.jar').write_bytes(b'fixture')
  payload=f'''case "$3" in
preflight) {'exit 2' if case=='preflight_failure' else 'echo PREFLIGHT ok'};;
test|sustained) sleep .05; echo result=request_sequence_completed;;
release) echo RELEASE_REQUEST sent;;
esac
'''
  (d/'probe_temporary_runtime.sh').write_text(payload,encoding='utf-8',newline='\n')
  temp='0.09' if case=='existing_temporary' else 'NaN'
  prefix=f'''export PATH=/usr/bin:/bin:$PATH
sleep() {{ /usr/bin/sleep .05; }}
timeout() {{ shift; "$@"; }}
dumpsys() {{ printf '%s\\n' 'Display Power Controller:' '  mDisplayId=0' '  mUseAutoBrightness=true' '  mTemporaryScreenBrightness:{temp}' 'Display Power Controller:' '  mDisplayId=1'; }}
'''
  guard=(ROOT/'tools/probe_temporary_watchdog.sh').read_text(encoding='utf-8')
  guard=guard.replace('/data/local/tmp/luma-framework03.*',sp(runtime)+'/luma-framework03.*').replace('/data/adb/modules/luma_curve/luma_curvectl.sh',sp(ctl))
  (d/'probe_temporary_watchdog.sh').write_text(prefix+guard,encoding='utf-8',newline='\n')
  app=d/'app';app.write_text('fixture',encoding='utf-8')
  script=(ROOT/'tools/probe_framework03_android.sh').read_text(encoding='utf-8')
  script=script.replace('[ "$(id -u)" = 0 ]','[ 1 = 1 ]').replace('/data/local/tmp/luma-framework03.',sp(runtime)+'/luma-framework03.').replace('/data/local/tmp/luma_curve.paused',sp(pause)).replace('/data/adb/modules/luma_curve/luma_curvectl.sh',sp(ctl)).replace('APP=/system/bin/app_process;', 'APP='+sp(app)+';')
  prefix+=f'''getenforce() {{ :; }}; getprop() {{ :; }}
pidof() {{ test -f '{sp(running)}' && echo 1234; }}
sha256sum() {{
case "$1" in
*/framework.jar) echo {'wrong' if case=='firmware_mismatch' else '1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd'} "$1";;
*/services.jar) echo ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20 "$1";;
esac
}}
'''
  if case=='sustained':prefix+='LUMA_FRAMEWORK_PROFILE=sustained; export LUMA_FRAMEWORK_PROFILE\n'
  runner=d/'runner.sh';runner.write_text(prefix+script,encoding='utf-8',newline='\n')
  # Android app_process is executable; our file is just the placeholder.
  subprocess.run([BASH,'-c',"/usr/bin/chmod +x '"+sp(app)+"'"],check=True)
  result=subprocess.run([BASH,sp(runner)],cwd=d,capture_output=True,timeout=15)
  ok=case in ('normal','sustained','originally_paused')
  assert (result.returncode==0)==ok,(case,result.stdout,result.stderr)
  commands=log.read_text().splitlines() if log.exists() else []
  assert commands==(['pause','resume'] if case in ('normal','sustained') else ['pause'] if case=='originally_paused' else []),(case,commands)
  assert pause.exists()==(case=='originally_paused'),case
  assert running.exists()==(case!='originally_paused'),case
print('Framework03/04 wrapper: 6 cases PASS; no Android APIs exercised')
