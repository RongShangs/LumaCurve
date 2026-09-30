"""Upgrade/lock isolation contracts: fake proc and signals; no device writes."""
from pathlib import Path
import subprocess,tempfile,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
BASH='C:/msys64/usr/bin/bash.exe'
def shell(p):
 s=Path(p).resolve().as_posix();return '/'+s[0].lower()+s[2:] if s[1:2]==':' else s
checks=[]
with tempfile.TemporaryDirectory(prefix='luma-audit-upgrade-') as tmp:
 base=Path(tmp)
 for case in ('released','stuck'):
  folder=base/case;folder.mkdir();proc=folder/'proc';proc.mkdir()
  for pid,cmd in ((900001,'app_process /system/bin LumaFrameworkOutputBroker luma.framework.prod.123 /tmp/run'),(900002,'sh /data/adb/modules/luma_curve/framework_daemon_launcher.sh'),(900003,'app_process unrelated')):
   d=proc/str(pid);d.mkdir();(d/'cmdline').write_bytes(cmd.replace(' ','\0').encode())
  code=f"""export PATH=/usr/bin:/bin:$PATH
IOS_PROC_ROOT='{shell(proc)}'; IOS_RUNTIME_DIR='{shell(folder)}'; IOS_PERSIST_DIR='{shell(folder)}/persist'
. '{shell(ROOT/'module/upgrade_data.sh')}'
ios_upgrade_stop_service_loop() {{ :; }}
pidof() {{ return 1; }}
sleep() {{ :; }}
luma_backlight_path() {{ return 1; }}
kill() {{ [ "$1" = -TERM ] || return 1; printf '%s\n' "$2" >> '{shell(folder)}/signals'; {'rm -f "$IOS_PROC_ROOT/$2/cmdline"' if case=='released' else ':'}; }}
ios_upgrade_quiesce_daemon
"""
  result=subprocess.run([BASH,'-c',code],capture_output=True,text=True,timeout=10)
  assert (result.returncode==0)==(case=='released'),(case,result.stdout,result.stderr)
  assert (folder/'signals').read_text().splitlines()==['900001','900002']
  assert (proc/'900003/cmdline').exists()
  checks.append(case)
 # A living owner remains protected even when lock age is over 30s; borrowed lock survives nested calls.
 folder=base/'lock';folder.mkdir()
 code=f"""export PATH=/usr/bin:/bin:$PATH
IOS_RUNTIME_DIR='{shell(folder)}'; IOS_PERSIST_DIR='{shell(folder)}/persist'
. '{shell(ROOT/'module/upgrade_data.sh')}'
sleep() {{ :; }}
ios_live_lock_acquire || exit 1
(ios_live_lock_acquire && ios_live_lock_release) || exit 2
[ -f "$IOS_LIVE_LOCK/pid" ] || exit 3
printf '123456\n' > "$IOS_LIVE_LOCK/pid"; touch -t 202001010000 "$IOS_LIVE_LOCK"
kill() {{ [ "$1 $2" = '-0 123456' ]; }}
if ios_live_lock_acquire; then exit 4; fi
[ "$(cat "$IOS_LIVE_LOCK/pid")" = 123456 ] || exit 5
kill() {{ return 1; }}
ios_live_lock_acquire || exit 6
ios_live_lock_release
[ ! -d "$IOS_LIVE_LOCK" ] || exit 7
"""
 result=subprocess.run([BASH,'-c',code],capture_output=True,text=True,timeout=10)
 assert result.returncode==0,(result.stdout,result.stderr)
 checks.append('live_old_lock_and_nested_borrow_and_dead_owner')
report={'ok':True,'checks':checks,'source_sha256':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ['module/upgrade_data.sh','module/framework_daemon_launcher.sh','module/customize.sh']},'android_device_verified':False}
(ROOT/'build/audit-upgrade-verification.json').write_text(json.dumps(report,indent=2)+'\n')
print('Upgrade: whole backend release, stuck-process gate, unrelated-process safety, live/stale/nested locks PASS')
