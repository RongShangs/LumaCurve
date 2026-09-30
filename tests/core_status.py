"""Test daemon identity and module-description updates without host process signals."""
import argparse, hashlib, json, subprocess, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(); p.add_argument('--bash', default='bash'); args = p.parse_args()
def shellpath(path):
    value = Path(path).resolve().as_posix()
    return '/' + value[0].lower() + value[2:] if len(value) > 1 and value[1] == ':' else value
with tempfile.TemporaryDirectory(prefix='luma-core-status-') as td:
    folder = Path(td)
    prop = folder / 'module.prop'
    prop.write_bytes((ROOT / 'module/module.prop').read_bytes())
    base = shellpath(folder)
    fakeproc = folder/'proc/431'; fakeproc.mkdir(parents=True)
    source = (ROOT/'module/core_status.sh').read_text(encoding='utf-8').replace('/proc/', base+'/proc/')
    helper=folder/'core_status.sh'; helper.write_text(source,encoding='utf-8',newline='\n')
    script = rf'''
set -eu
export PATH=/usr/bin:/bin:$PATH; export LANG=C.UTF-8
MODDIR='{base}'; DAEMON="$MODDIR/system/bin/luma_curve_daemon"; PAUSE_FILE="$MODDIR/pause"
. '{shellpath(helper)}'
pidof() {{ printf '%s' "$mock_pids"; }}
kill() {{ [ "$mock_alive" = yes ]; }}
readlink() {{ printf '%s' "$mock_exe"; }}
mock_pids=431; mock_alive=yes; mock_exe="$DAEMON"
[ "$(lc_core_status)" = running ]
lc_core_description_refresh
grep -q '^description=\[LumaCurve核心✅\] ' "$MODDIR/module.prop"
signature=$(cksum "$MODDIR/module.prop")
lc_core_description_refresh
[ "$signature" = "$(cksum "$MODDIR/module.prop")" ]
printf 'do_freezer_trap' > '{base}/proc/431/wchan'
[ "$(lc_core_status)" = frozen ]; lc_core_description_refresh
grep -q '^description=\[LumaCurve核心⚠️\] ' "$MODDIR/module.prop"
printf 'nanosleep' > '{base}/proc/431/wchan'
[ "$(lc_core_status)" = running ]
mock_exe=/another/module/luma_curve_daemon
[ "$(lc_core_status)" = unknown ]; lc_core_description_refresh
grep -q '^description=\[LumaCurve核心⚠️\] ' "$MODDIR/module.prop"
mock_exe="$DAEMON"; mock_alive=no
[ "$(lc_core_status)" = stopped ]; lc_core_description_refresh
grep -q '^description=\[LumaCurve核心❌\] ' "$MODDIR/module.prop"
mock_alive=yes; mock_pids='431 432'
[ "$(lc_core_status)" = multiple ]; lc_core_description_refresh
grep -q '^description=\[LumaCurve核心⚠️\] ' "$MODDIR/module.prop"
mock_pids=invalid; [ "$(lc_core_status)" = unknown ]
mock_pids=''; [ "$(lc_core_status)" = stopped ]
touch "$PAUSE_FILE"; [ "$(lc_core_status)" = paused ]; lc_core_description_refresh
grep -q '^description=\[LumaCurve核心⚠️\] ' "$MODDIR/module.prop"
rm "$PAUSE_FILE"; mock_pids=431
before=$(cat "$MODDIR/module.prop")
mv() {{ return 1; }}
if lc_core_description_refresh; then exit 1; fi
[ "$before" = "$(cat "$MODDIR/module.prop")" ]; unset -f mv
lc_core_description_refresh
[ "$(grep -o '\[LumaCurve核心' "$MODDIR/module.prop" | wc -l)" -eq 1 ]
grep -q '^version=1.0.0$' "$MODDIR/module.prop"
grep -q '^versionCode=10000$' "$MODDIR/module.prop"
mock_pids=''
cksum() {{
  if [ -f "$MODDIR/checksum-seen" ]; then printf '# concurrent-upgrade\n' >> "$MODDIR/module.prop"; fi
  touch "$MODDIR/checksum-seen"
  command cksum "$@"
}}
if lc_core_description_refresh; then exit 1; fi
grep -q '^# concurrent-upgrade$' "$MODDIR/module.prop"
grep -q '^description=\[LumaCurve核心✅\] ' "$MODDIR/module.prop"
unset -f cksum
sleep_count=0; refresh_count=0
sleep() {{ [ "$1" = 20 ]; sleep_count=$((sleep_count+1)); }}
lc_core_description_refresh() {{ refresh_count=$((refresh_count+1)); }}
lc_core_watch_wait
[ "$sleep_count" -eq 3 ] && [ "$refresh_count" -eq 3 ]
'''
    result = subprocess.run([args.bash, '-c', script], capture_output=True, text=True, encoding='utf-8')
    assert result.returncode == 0, (result.stdout, result.stderr)
names = ['single live executable', 'frozen core is not reported running', 'unrelated executable rejected', 'dead PID rejected',
         'multiple cores flagged', 'malformed PID rejected', 'stopped and paused distinguished',
         'atomic failure preserves metadata', 'prefix never duplicated and version preserved',
         'concurrent metadata modification is preserved',
         'twenty-second checks preserve sixty-second maintenance cadence']
report = {'ok': True, 'checks': names, 'android_device_verified': False,
          'source_sha256': {name: hashlib.sha256((ROOT/name).read_bytes()).hexdigest()
                            for name in ('module/core_status.sh', 'module/service.sh', 'module/luma_curvectl.sh')}}
(ROOT/'build/core-status-verification.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print(json.dumps({'ok': True, 'checks': len(names)}))
