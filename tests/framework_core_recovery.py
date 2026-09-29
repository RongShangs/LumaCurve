"""Execute independent supervisor recovery logic with mocked Android boundaries."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];BASH='C:/msys64/usr/bin/bash.exe'
def sp(path):
    s=path.resolve().as_posix();return '/'+s[0].lower()+s[2:]
cases=('normal','originally_stopped','release_failure','uncleared','user_manual','deadline')
with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='core-recovery-') as temp:
    for case in cases:
        folder=Path(temp)/case;folder.mkdir();run=folder/'run';run.mkdir();out=folder/'out';out.mkdir()
        if case!='deadline':(run/'test-finished').touch()
        (folder/'process_scope.sh').write_text('lc_scope_detach_self() { return 0; }\n',newline='\n')
        (folder/'probe_temporary_runtime.sh').write_text('echo RELEASE_REQUEST sent\nexit '+('2' if case=='release_failure' else '0')+'\n',newline='\n')
        ctl=folder/'ctl.sh';ctl.write_text('test "$1" = resume && touch "'+sp(out/'resumed')+'"\n',newline='\n')
        script=(ROOT/'tools/framework_core_watchdog.sh').read_text()
        script=script.replace('/data/local/tmp/luma-framework-core.*',sp(run)).replace('/data/adb/modules/luma_curve/luma_curvectl.sh',sp(ctl))
        value='0.09' if case=='uncleared' else 'NaN';auto='false' if case=='user_manual' else 'true'
        prefix=f'''export PATH=/usr/bin:/bin:$PATH
sleep() {{ :; }}
timeout() {{ shift; "$@"; }}
dumpsys() {{ printf '%s\\n' 'Display Power Controller:' ' mDisplayId=0' ' mTemporaryScreenBrightness:{value}' ' mUseAutoBrightness={auto}'; }}
'''
        runner=folder/'watchdog.sh';runner.write_text(prefix+script,newline='\n')
        result=subprocess.run([BASH,sp(runner),sp(run),sp(out),'0' if case=='originally_stopped' else '1','fixture'],capture_output=True,timeout=10)
        ok=case not in ('release_failure','uncleared')
        assert (result.returncode==0)==ok,(case,result.stderr)
        assert (run/'restored').exists()==ok,case
        assert (out/'resumed').exists()==(ok and case!='originally_stopped'),case
        assert (run/'restore-failed').exists()==(not ok),case
print('Framework core recovery: 6 fixtures PASS; Android behavior not yet verified')
