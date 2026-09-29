"""Verify isolated probe pause/restore behavior, without Android or host process signals."""
from pathlib import Path
import subprocess,tempfile,json
ROOT=Path(__file__).resolve().parents[1];BASH='C:/msys64/usr/bin/bash.exe'
def sp(p):
    s=Path(p).resolve().as_posix();return '/'+s[0].lower()+s[2:]
cases=['inspect','inspect_error','control','originally_paused','preflight_error','unconfirmed_restore','pause_error']
with tempfile.TemporaryDirectory(prefix='framework-shell-',dir=ROOT/'build') as td:
    base=Path(td)
    for name in cases:
        d=base/name;d.mkdir();runtime=d/'runtime';runtime.mkdir();mod=d/'module';mod.mkdir()
        pause=runtime/'luma_curve.paused';running=d/'running'
        if name=='originally_paused':pause.touch()
        else:running.touch()
        ctl=mod/'luma_curvectl.sh'
        ctl.write_text(f'''case "$1" in
pause) touch '{sp(pause)}'; rm -f '{sp(running)}'; {'exit 1' if name=='pause_error' else ':'};;
resume) rm -f '{sp(pause)}'; touch '{sp(running)}';;
esac
''',encoding='utf-8',newline='\n')
        failed=name in ['inspect_error','preflight_error','unconfirmed_restore']
        payload='result=read_only_completed\n'
        if name in ['control','originally_paused']:payload='SAVED mode=1 brightness=0.12\nRESTORE mode=ok brightness_request=sent\n'
        if name=='unconfirmed_restore':payload='SAVED mode=1 brightness=0.12\nresult=fail\n'
        if name=='preflight_error':payload='result=fail\n'
        (d/'LumaFrameworkProbe.jar').write_bytes(b'fixture');(d/'process_scope.sh').touch()
        (d/'probe_framework_runtime.sh').write_text("printf '%s' '"+payload+"'\nexit "+('2' if failed else '0')+'\n',encoding='utf-8',newline='\n')
        app=d/'app_process';app.write_text('#!/bin/sh\nexit 0\n',encoding='utf-8')
        script=(ROOT/'tools/probe_framework_android.sh').read_text(encoding='utf-8')
        script=script.replace('[ "$(id -u)" = 0 ]','[ 1 = 1 ]').replace('/data/local/tmp',sp(runtime)).replace('/data/adb/modules/luma_curve',sp(mod)).replace('APP_PROCESS=/system/bin/app_process','APP_PROCESS='+sp(app)).replace('sleep 3',':')
        prefix=f'''export PATH=/usr/bin:/bin:$PATH; export LANG=C.UTF-8
chmod +x '{sp(app)}'
getprop() {{ :; }}; getenforce() {{ :; }}; cmd() {{ :; }}; service() {{ :; }}; ps() {{ :; }}; dumpsys() {{ :; }}
timeout() {{ shift; "$@"; }}
pidof() {{ if [ -f '{sp(running)}' ]; then printf 777; else return 1; fi; }}
'''
        runner=d/'runner.sh';runner.write_text(prefix+script,encoding='utf-8',newline='\n')
        mode='inspect' if name.startswith('inspect') else 'control'
        r=subprocess.run([BASH,sp(runner),mode],cwd=d,capture_output=True,text=True,encoding='utf-8')
        assert (r.returncode==0)==(not failed and name!='pause_error'),(name,r.stdout,r.stderr)
        should_pause=name in ['originally_paused','unconfirmed_restore']
        assert pause.exists()==should_pause,(name,r.stdout,r.stderr)
        assert running.exists()==(not should_pause),name
        assert not list(runtime.glob('luma-framework-probe.*')),name
report={'ok':True,'host_cases':len(cases),'android_device_verified':False}
(ROOT/'build/framework-probe/shell-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Framework probe shell:',len(cases),'pause/restoration cases PASS')
