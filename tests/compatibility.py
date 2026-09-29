#!/usr/bin/env python3
import argparse,hashlib,json,os,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--bash',default='C:/msys64/usr/bin/bash.exe');p.add_argument('--cc',default='C:/msys64/mingw64/bin/gcc.exe');a=p.parse_args()
out=ROOT/'build/compatibility';out.mkdir(parents=True,exist_ok=True)
exe=out/'probe-test.exe'
subprocess.run([a.cc,'-std=c11','-O1','-Wall','-Wextra','-Werror','-I'+str(ROOT/'csrc'),str(ROOT/'tests/install_probe.c'),'-lm','-o',str(exe)],check=True)
r=subprocess.run([str(exe)],text=True,capture_output=True);print(r.stdout);print(r.stderr);r.check_returncode()
checks=['Native probe: ten cases and no brightness write/truncate']
def shpath(path):
 s=Path(path).resolve().as_posix();return '/'+s[0].lower()+s[2:] if len(s)>1 and s[1]==':' else s
for name,body in [
 ('environment valid','ARCH=arm64; API=26; lc_compat_environment'),
 ('wrong ABI','ARCH=x86_64; API=26; ! lc_compat_environment'),
 ('old API','ARCH=arm64; API=25; ! lc_compat_environment'),
 ('unknown API','ARCH=arm64; API=invalid; ! lc_compat_environment'),
 ('nonroot','id() { printf 2000; }; ARCH=arm64; API=26; ! lc_compat_environment'),
 ('successful probe','lc_compat_probe "$engine" "$folder"'),
 ('failed probe','probe_status=2; ! lc_compat_probe "$engine" "$folder"'),
 ('bad protocol','probe_text="result=pass"; ! lc_compat_probe "$engine" "$folder"'),
 ('timeout','probe_status=124; ! lc_compat_probe "$engine" "$folder"')]:
 with tempfile.TemporaryDirectory(prefix='luma-compat-') as td:
  code=f'''export PATH=/usr/bin:/bin:$PATH; export LANG=C.UTF-8
folder='{shpath(td)}'; engine="$folder/engine"; probe_status=0
probe_text='probe_protocol=1
result=pass'
ui_print() {{ :; }}; id() {{ printf 0; }}; settings() {{ printf '1\\n'; }}
timeout() {{ if [ "$2" = "$engine" ]; then [ "$3" = --check-install ] || exit 99; printf '%s\\n' "$probe_text"; return "$probe_status"; else shift; "$@"; fi; }}
. '{shpath(ROOT/'module/compatibility.sh')}'
{body}
'''
  subprocess.run([a.bash,'-c',code],check=True,capture_output=True)
  checks.append(name)
with tempfile.TemporaryDirectory(prefix='backlight-') as td:
 root=Path(td)
 helper=shpath(ROOT/'module/upgrade_data.sh');directory=shpath(root)
 def panel(name,maximum,current):
  p=root/name;p.mkdir(exist_ok=True);(p/'max_brightness').write_text(str(maximum));(p/'brightness').write_text(str(current))
 def query():
  return subprocess.run([a.bash,'-c',f"export PATH=/usr/bin:/bin:$PATH; . '{helper}'; luma_backlight_path '{directory}'"],text=True,capture_output=True)
 assert query().returncode
 panel('panel1',1000,100);assert query().stdout.strip()==directory+'/panel1/brightness'
 panel('panel2',1000,100);assert query().returncode
 panel('panel0-backlight',1000,100);assert query().stdout.strip()==directory+'/panel0-backlight/brightness'
 panel('panel0-backlight',0,100);assert query().returncode
 checks.append('Shell cleanup/status discovery: missing, unique, ambiguous, original preference and invalid range')
with tempfile.TemporaryDirectory(prefix='luma-preference-upgrade-') as td:
 root=Path(td);(root/'runtime').mkdir();(root/'persist').mkdir();base=shpath(root);helper=shpath(ROOT/'module/upgrade_data.sh')
 (root/'runtime/luma_curve.conf').write_bytes((ROOT/'module/luma_curve.conf').read_bytes())
 (root/'runtime/luma_curve_preference').write_text('format=1\noffset=3\nsamples=2\nconfig_offset=0\nconfig_revision=0\n',encoding='utf-8')
 (root/'runtime/luma_curve_presets.json').write_text('{"format":1,"entries":[]}',encoding='utf-8')
 code=f'''export PATH=/usr/bin:/bin:$PATH
 IOS_RUNTIME_DIR='{base}/runtime'; IOS_PERSIST_DIR='{base}/persist'
 . '{helper}'
 ios_live_refresh test || exit 1
 for name in luma_curve_preference luma_curve_presets.json; do
   [ "$(cat "$IOS_RUNTIME_DIR/$name")" = "$(cat "$IOS_LIVE_DIR/$name")" ] || exit 2
   mv "$IOS_RUNTIME_DIR/$name" '{base}/'"$name" || exit 3
 done
 ios_live_restore_missing || exit 4
 mkdir -p "$IOS_UPGRADE_DIR" || exit 5
 printf 'luma_curve_preference\\nluma_curve_presets.json\\n' > "$IOS_UPGRADE_DIR/manifest"
 ios_upgrade_quiesce_daemon() {{ return 0; }}
 ios_upgrade_refresh_backup || exit 6
 for name in luma_curve_preference luma_curve_presets.json; do
   [ "$(cat "$IOS_RUNTIME_DIR/$name")" = "$(cat '{base}/'"$name")" ] || exit 7
   printf replacement > "$IOS_RUNTIME_DIR/$name"
 done
 ios_upgrade_restore || exit 8
 for name in luma_curve_preference luma_curve_presets.json; do
   [ "$(cat "$IOS_RUNTIME_DIR/$name")" = "$(cat '{base}/'"$name")" ] || exit 9
 done
 '''
 result=subprocess.run([a.bash,'-c',code],capture_output=True,text=True,encoding='utf-8');assert result.returncode==0,(result.stdout,result.stderr)
 assert (root/'runtime/luma_curve_preference').read_bytes()==(root/'luma_curve_preference').read_bytes()
 assert (root/'runtime/luma_curve_presets.json').read_bytes()==(root/'luma_curve_presets.json').read_bytes()
 checks.append('Preference and named presets survive live snapshots, missing runtime files and upgrade backup/restore byte-for-byte')
report={'ok':True,'checks':checks,'android_device_verified':False,'source_sha256':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ('csrc/install_probe.h','csrc/backlight_paths.h','csrc/platform_native.c','module/compatibility.sh','module/upgrade_data.sh','module/customize.sh','module/update-binary')}}
(ROOT/'build/compatibility-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps({'ok':True,'checks':len(checks)}))
