"""Execute actual finite wrapper/recovery scripts with substituted device boundaries."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];BASH='C:/msys64/usr/bin/bash.exe'
def sp(path):
    s=path.resolve().as_posix();return '/'+s[0].lower()+s[2:]
cases=('normal','originally_paused','broker_failure','core_failure','firmware_mismatch','preflight_failure')
with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='core-wrapper-') as temp:
 for case in cases:
    d=Path(temp)/case;d.mkdir();runtime=d/'runtime';runtime.mkdir();pause=d/'paused';running=d/'running';log=d/'ctl.log';ctl=d/'ctl.sh'
    if case=='originally_paused':pause.touch()
    else:running.touch()
    ctl.write_text(f'''echo "$1" >> '{sp(log)}'
case "$1" in
pause) touch '{sp(pause)}'; rm -f '{sp(running)}';;
resume) rm -f '{sp(pause)}'; touch '{sp(running)}';;
esac
''',newline='\n')
    (d/'process_scope.sh').write_text('lc_scope_detach_self() { return 0; }\n',newline='\n')
    for name in ('LumaFrameworkProbe.jar','luma_framework_core','luma_curve.conf'):(d/name).write_bytes(b'fixture')
    (d/'probe_temporary_runtime.sh').write_text('case "$3" in\npreflight) '+('exit 2' if case=='preflight_failure' else 'echo PREFLIGHT ok')+';;\nrelease) echo RELEASE_REQUEST sent;;\nesac\n',newline='\n')
    (d/'framework_core_runtime.sh').write_text(f'''case "$3" in
broker) {'exit 2' if case=='broker_failure' else 'touch "$1/broker-ready"'};;
core) {'exit 2' if case=='core_failure' else 'echo test_state=1 > "$1/luma_curve_state"'};;
esac
while [ ! -f "$1/test-finished" ]; do /usr/bin/sleep .03; done
''',newline='\n')
    prefix='''export PATH=/usr/bin:/bin:$PATH
sleep() { /usr/bin/sleep .02; }
timeout() { shift; "$@"; }
dumpsys() { printf '%s\\n' 'Display Power Controller:' ' mDisplayId=0' ' mUseAutoBrightness=true' ' mTemporaryScreenBrightness:NaN'; }
'''
    guard=(ROOT/'tools/framework_core_watchdog.sh').read_text().replace('/data/local/tmp/luma-framework-core.*',sp(runtime)+'/luma-framework-core.*').replace('/data/adb/modules/luma_curve/luma_curvectl.sh',sp(ctl)).replace('seq 1 150','seq 1 30')
    (d/'framework_core_watchdog.sh').write_text(prefix+guard,newline='\n')
    script=(ROOT/'tools/test_framework_core_android.sh').read_text(encoding='utf-8')
    script=script.replace('[ "$(id -u)" = 0 ]','[ 1 = 1 ]').replace('/data/local/tmp/luma-framework-core.',sp(runtime)+'/luma-framework-core.').replace('/data/local/tmp/luma_curve.paused',sp(pause)).replace('/data/local/tmp/luma_curve.conf',sp(d/'luma_curve.conf')).replace('/data/adb/modules/luma_curve/luma_curvectl.sh',sp(ctl)).replace('seq 1 120','seq 1 6')
    prefix+=f'''getprop() {{ :; }}
pidof() {{ test -f '{sp(running)}' && echo 1234; }}
sha256sum() {{ case "$1" in
*/framework.jar) echo {'wrong' if case=='firmware_mismatch' else '1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd'} "$1";;
*/services.jar) echo ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20 "$1";; esac; }}
'''
    runner=d/'runner.sh';runner.write_text(prefix+script,encoding='utf-8',newline='\n')
    result=subprocess.run([BASH,sp(runner)],cwd=d,capture_output=True,timeout=15)
    ok=case in ('normal','originally_paused')
    assert (result.returncode==0)==ok,(case,result.stdout,result.stderr)
    commands=log.read_text().splitlines() if log.exists() else []
    expected=[] if case in ('firmware_mismatch','preflight_failure') else ['pause'] if case=='originally_paused' else ['pause','resume']
    assert commands==expected,(case,commands,result.stdout,result.stderr)
    assert pause.exists()==(case=='originally_paused'),case
    assert running.exists()==(case!='originally_paused'),case
print('Framework full-core wrapper: 6 fixtures PASS; Android APIs are mocked')
