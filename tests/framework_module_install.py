"""Exercise the local framework installer branch without Android system access."""
from pathlib import Path
import subprocess,tempfile

ROOT=Path(__file__).resolve().parents[1]
BASH='C:/msys64/usr/bin/bash.exe'
def shellpath(path):
    value=path.resolve().as_posix()
    return '/'+value[0].lower()+value[2:]
with tempfile.TemporaryDirectory(dir=ROOT/'build',prefix='framework-install-') as temp:
    for case in ('match','firmware_hash_changed','preflight_failure'):
        folder=Path(temp)/case;folder.mkdir()
        (folder/'framework-broker.jar').write_bytes(b'fixture')
        script=f'''export PATH=/usr/bin:/bin:$PATH
ui_print() {{ :; }}
lc_compat_fail() {{ echo "FAIL:$1"; return 1; }}
sha256sum() {{ case "$1" in
 */framework.jar) echo "{'wrong' if case=='firmware_hash_changed' else '1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd'} $1";;
 */services.jar) echo "ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20 $1";; esac; }}
timeout() {{
  [ "$CLASSPATH" = '{shellpath(folder)}/framework-broker.jar' ] || return 3
  [ "$4 $5" = 'LumaFrameworkDeviceProfile preflight' ] || return 4
  {'return 2' if case=='preflight_failure' else "printf 'MAIN_DISPLAY verified\\nPREFLIGHT ok brightness=0.1 min=0.01 max=0.37\\n'"}
}}
. '{shellpath(ROOT/'module/compatibility.sh')}'
lc_compat_probe /does-not-need-native-check '{shellpath(folder)}'
'''
        result=subprocess.run([BASH,'-c',script],capture_output=True,text=True,timeout=10)
        assert (result.returncode==0)==(case!='preflight_failure'),(case,result.returncode,result.stdout,result.stderr)
        if case!='preflight_failure':
            assert 'PREFLIGHT ok brightness=' in (folder/'compatibility-report.txt').read_text()
print('framework module installer: hash changes accepted, read-only capability failures rejected; 3 cases PASS')
