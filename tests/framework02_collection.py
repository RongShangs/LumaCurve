"""Verify read-only collector copies complete inputs and stops on missing firmware."""
from pathlib import Path
import subprocess, tempfile

ROOT = Path(__file__).resolve().parents[1]
BASH = 'C:/msys64/usr/bin/bash.exe'
def shell_path(p):
    s = p.resolve().as_posix()
    return '/' + s[0].lower() + s[2:]

with tempfile.TemporaryDirectory(dir=ROOT/'build', prefix='framework02-') as tmp:
    for case in ('complete', 'missing', 'reject_control'):
        folder=Path(tmp)/case; folder.mkdir()
        firmware=folder/'input'; firmware.mkdir()
        files=['framework.jar','services.jar','miui-framework.jar','miui-services.jar']
        for name in files:
            if case=='missing' and name=='miui-services.jar': continue
            (firmware/name).write_bytes(('fixture:'+name).encode())
        script=(ROOT/'tools/probe_framework02_android.sh').read_text(encoding='utf-8')
        script=script.replace('[ "$(id -u)" = 0 ]','[ 1 = 1 ]')
        for prefix in ('/system/framework/','/system_ext/framework/'):
            script=script.replace(prefix, shell_path(firmware)+'/')
        runner=folder/'probe_framework02_android.sh';runner.write_text(script,encoding='utf-8',newline='\n')
        (folder/'probe_framework_android.sh').write_text('test "$1" = inspect || exit 9\ntouch inspected\n',encoding='utf-8',newline='\n')
        command=[BASH,shell_path(runner)]+(['control'] if case=='reject_control' else [])
        result=subprocess.run(command,cwd=folder,env={'PATH':'C:/msys64/usr/bin;C:/Windows/System32'},capture_output=True)
        assert (result.returncode==0)==(case=='complete'),(case,result.stderr)
        outputs=list(folder.glob('luma-framework02-*'))
        assert sum((out/'inspected').exists() for out in outputs)==(case=='complete')
        if case=='complete':
            for name in files:
                assert (outputs[0]/'firmware'/name).read_bytes()==(firmware/name).read_bytes()
        if case=='reject_control': assert not outputs
print('Framework02 collection: 3 cases PASS')
