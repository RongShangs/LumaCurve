"""Run the standalone collector against fixtures, never a real Android filesystem."""
from pathlib import Path
import subprocess, tempfile, tarfile, re

ROOT=Path(__file__).resolve().parents[1]
BASH='C:/msys64/usr/bin/bash.exe'
source=(ROOT/'tools/collect_hyperos4_android.sh').read_text(encoding='utf-8')
subprocess.run([BASH,'-n',str(ROOT/'tools/collect_hyperos4_android.sh')],check=True)

def shellpath(path):
    value=path.resolve().as_posix()
    return '/'+value[0].lower()+value[2:]

for case in ('success','nonroot','archive_failure'):
    with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='collection-test-') as temporary:
        folder=Path(temporary)
        storage=folder/'sdcard';storage.mkdir()
        sentinel=storage/'keep.txt';sentinel.write_text('user file')
        fixtures={
            'system/framework/framework.jar':b'new firmware fixture',
            'system/framework/framework-res.apk':b'resource fixture',
            'system/framework/services.jar':b'service fixture',
            'system_ext/framework/miui-framework.jar':b'vendor fixture',
            'vendor/etc/displayconfig/main.xml':b'<display/>',
            'sys/class/backlight/panel0/brightness':b'250',
            'sys/class/backlight/panel0/max_brightness':b'16383',
            'proc/uptime':b'1000 100',
            'data/local/tmp/luma_curve.conf':b'config_version=12\n',
        }
        for relative,data in fixtures.items():
            path=folder/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        translated=re.sub(r'/(sdcard|system_ext|system|product|vendor|odm|sys|data|proc)(?=[/\s"])',
                          lambda match:shellpath(folder/match.group(1)),source)
        script=folder/'collect.sh';script.write_text(translated,encoding='utf-8',newline='\n')
        setup='''export PATH=/usr/bin:/bin:$PATH LANG=C.UTF-8
id() { echo %s; }
getprop() { echo fixture; }
getenforce() { echo Enforcing; }
settings() { [ "$1" = get ] || exit 90; echo 1; }
dumpsys() { echo "fixture $1"; }
cmd() { case "$1 $2" in 'display help'|'overlay list'|'overlay lookup') echo help;; *) exit 91;; esac; }
timeout() { shift; "$@"; }
sleep() { :; }
%s
. '%s'
'''%('2000' if case=='nonroot' else '0',
     'tar() { return 1; }' if case=='archive_failure' else '',shellpath(script))
        result=subprocess.run([BASH,'-c',setup],capture_output=True,encoding='utf-8',timeout=20)
        assert (result.returncode==0)==(case=='success'),(case,result.stdout,result.stderr)
        assert sentinel.read_text()=='user file'
        for relative,data in fixtures.items():assert (folder/relative).read_bytes()==data,relative
        archives=list(storage.glob('LumaCurve-device-info-*.tar.gz'))
        raw=list(storage.glob('LumaCurve-device-info-*'))
        if case=='success':
            assert len(archives)==1 and raw==archives
            with tarfile.open(archives[0]) as archive:
                entries=archive.getnames()
                for suffix in ('system/framework/framework.jar','system/framework/services.jar',
                               'system/framework/framework-res.apk','brightness-resources.txt',
                               'vendor/etc/displayconfig/main.xml','display.txt','sensorservice.txt',
                               'display-second.txt','backlight.txt','module/luma_curve.conf'):
                    assert any(name.endswith('/'+suffix) for name in entries),suffix
        elif case=='archive_failure':
            assert len(raw)==1 and raw[0].is_dir(), 'failed packaging must preserve collected files'
        else:assert not raw
print('read-only collector: syntax, complete archive, unchanged inputs, nonroot rejection, failure preservation PASS')
