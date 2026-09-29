#!/usr/bin/env python3
"""Run installer contracts in temp dirs, with no host process signals."""
import argparse, json, os, subprocess, tempfile, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--bash',default='bash');args=p.parse_args()
checks=[]
def shpath(p):
    s=Path(p).resolve().as_posix()
    return '/'+s[0].lower()+s[2:] if len(s)>1 and s[1]==':' else s
def run(name,code,files=None):
    with tempfile.TemporaryDirectory(prefix='luma-installer-') as td:
        d=Path(td)
        for part in ('modules','updates','runtime','data','proc','tmp','new'): (d/part).mkdir()
        for name_,data in (files or {}).items():
            f=d/name_;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
        base=shpath(d)
        prefix=f"""
export PATH=/usr/bin:/bin:$PATH; export LANG=C.UTF-8
LC_MODULES_DIR='{base}/modules'; LC_UPDATES_DIR='{base}/updates'
LC_RUNTIME_DIR='{base}/runtime'; LC_DATA_DIR='{base}/data'; LC_PROC_DIR='{base}/proc'
LC_LEGACY_DATA_DIR='{base}/legacy-data'
ui_print() {{ printf '%s\\n' "$*"; }}
. '{shpath(ROOT/'module/install_choice.sh')}'
lc_stop_legacy_processes() {{ return 0; }}
"""
        result=subprocess.run([args.bash,'-c',prefix+code],text=True,encoding="utf-8",capture_output=True)
        assert result.returncode==0,(name,result.stdout,result.stderr)
        checks.append({'name':name,'ok':True})
old={'modules/ios_auto_brightness/module.prop':b'id=ios_auto_brightness\n','runtime/ios_brightness.conf':b'user_config=kept\n','legacy-data/live/config':b'persistent=kept\n'}
run('No original: no key read or writes','lc_volume_choice() { return 9; }; lc_check_legacy && [ "$LC_REPLACE_OLD" = 0 ] && [ ! -e "$LC_RUNTIME_DIR/ios_brightness.paused" ]')
for decision in (1,2):
    run('Cancel/timeout preserves original '+str(decision),f'lc_volume_choice() {{ return {decision}; }}; ! lc_check_legacy && [ "$LC_REPLACE_OLD" = 0 ] && [ ! -e "$LC_MODULES_DIR/ios_auto_brightness/remove" ] && [ ! -e "$LC_DATA_DIR/legacy-backup" ]',old)
run('Positive preflight leaves original untouched','lc_volume_choice() { return 0; }; lc_check_legacy && [ "$LC_REPLACE_OLD" = 1 ] && [ ! -e "$LC_MODULES_DIR/ios_auto_brightness/remove" ]',old)
run('Commit backs up config and marks installed/pending originals','''
lc_volume_choice() { return 0; }
lc_check_legacy && lc_commit_legacy_removal &&
[ -f "$LC_MODULES_DIR/ios_auto_brightness/disable" ] && [ -f "$LC_MODULES_DIR/ios_auto_brightness/remove" ] &&
[ -f "$LC_UPDATES_DIR/ios_auto_brightness/remove" ] && [ -f "$LC_LEGACY_BACKUP/persistent/live/config" ] &&
[ "$(cat "$LC_RUNTIME_DIR/ios_brightness.conf")" = "$(cat "$LC_LEGACY_BACKUP/ios_brightness.conf")" ]
''',dict(old,**{'updates/ios_auto_brightness/module.prop':b'id=ios_auto_brightness\n'}))
run('Stop failure rolls back new flags and preserves existing disable','''
lc_volume_choice() { return 0; }; lc_stop_legacy_processes() { return 1; }
lc_check_legacy && ! lc_commit_legacy_removal &&
[ -f "$LC_MODULES_DIR/ios_auto_brightness/disable" ] && [ ! -e "$LC_MODULES_DIR/ios_auto_brightness/remove" ] &&
[ ! -e "$LC_RUNTIME_DIR/ios_brightness.paused" ]
''',dict(old,**{'modules/ios_auto_brightness/disable':b'preexisting'}))
run('Backup failure leaves original untouched','''
lc_volume_choice() { return 0; }; lc_backup_legacy_data() { return 1; }
lc_check_legacy && ! lc_commit_legacy_removal && [ ! -e "$LC_MODULES_DIR/ios_auto_brightness/disable" ]
''',old)
run('PID identity excludes unrelated and LumaCurve processes','''
lc_legacy_pid_matches 123 && ! lc_legacy_pid_matches 456 && ! lc_legacy_pid_matches 789 && ! lc_legacy_pid_matches invalid
''',{'proc/123/cmdline':b'/data/adb/modules/ios_auto_brightness/system/bin/ios_brightness_daemon\0','proc/456/cmdline':b'/data/adb/modules/luma_curve/system/bin/luma_curve_daemon\0','proc/789/cmdline':b'/system/bin/unrelated\0'})
for event,label,expected in [('EV_KEY KEY_VOLUMEUP DOWN','up',0),('EV_KEY KEY_VOLUMEDOWN DOWN','down',1),('0001 0073 00000001','raw up',0),('0001 0072 00000001','raw down',1),('EV_KEY KEY_VOLUMEUP UP','release ignored',2)]:
    run('Volume event '+label,f'''
LC_GETEVENT="$LC_RUNTIME_DIR/getevent"; LC_KEY_TIMEOUT={1 if expected==2 else 3}
printf '#!/bin/sh\\nprintf "{event}\\\\n"\\n' > "$LC_GETEVENT"; chmod 755 "$LC_GETEVENT"
lc_volume_choice; result=$?; [ "$result" -eq {expected} ]
''')
for decision in (0,1,3):
    with tempfile.TemporaryDirectory(prefix='luma-modern-') as td:
        d=Path(td);base=shpath(d)
        (d/'modules/ios_auto_brightness').mkdir(parents=True)
        (d/'tmp').mkdir();(d/'new').mkdir();(d/'runtime').mkdir()
        helper=(ROOT/'module/install_choice.sh').read_text(encoding='utf-8')+f'\nlc_volume_choice() {{ return {0 if decision==3 else decision}; }}\nlc_stop_legacy_processes() {{ return 0; }}\n'
        upgrade='''
ios_upgrade_begin() { printf begin > "$TMPDIR/began"; }
ios_upgrade_finish() { printf finish > "$TMPDIR/finished"; }
ios_upgrade_restore() { printf restored > "$TMPDIR/restored"; }; ios_upgrade_release_pause() { :; }
'''
        with zipfile.ZipFile(d/'input.zip','w') as z:
            z.writestr('install_choice.sh',helper);z.writestr('upgrade_data.sh',upgrade)
            z.writestr('compatibility.sh',f'lc_compat_environment() {{ :; }}; lc_compat_probe() {{ return {1 if decision==3 else 0}; }}\n')
            for name in ('system/bin/luma_curve_daemon','luma_curve.conf','webroot/index.html'):z.writestr(name,'fixture')
        unzip_path='/d/App/Git/usr/bin' if os.name=='nt' and Path('D:/App/Git/usr/bin/unzip.exe').exists() else ''
        script=f"""
TMPDIR='{base}/tmp'; MODPATH='{base}/new'; ZIPFILE='{base}/input.zip'; ARCH=arm64; API=26
export PATH=/usr/bin:/bin:{unzip_path}:$PATH; export LANG=C.UTF-8
LC_MODULES_DIR='{base}/modules'; LC_UPDATES_DIR='{base}/updates'; LC_RUNTIME_DIR='{base}/runtime'; LC_DATA_DIR='{base}/data'; LC_PROC_DIR='{base}/proc'
ui_print() {{ :; }}; abort() {{ exit 7; }}; set_perm_recursive() {{ :; }}; set_perm() {{ :; }}
. '{shpath(ROOT/'module/customize.sh')}'
"""
        result=subprocess.run([args.bash,'-c',script],capture_output=True,text=True,encoding="utf-8")
        assert result.returncode==(0 if decision==0 else 7),(result.stdout,result.stderr)
        assert (d/'tmp/began').exists()==(decision!=1)
        assert (d/'new/system/bin/luma_curve_daemon').exists()==(decision!=1)
        assert (d/'modules/ios_auto_brightness/remove').exists()==(decision==0)
        if decision==3:assert (d/'tmp/restored').exists() and not (d/'tmp/finished').exists()
        checks.append({'name':'Modern sourced customize '+('install' if decision==0 else 'compatibility failure restores data before original removal' if decision==3 else 'cancel before writes'),'ok':True})
(ROOT/'build').mkdir(exist_ok=True)
(ROOT/'build/installer-verification.json').write_text(json.dumps({'ok':True,'checks':checks,'physical_volume_key_verified':False},indent=2),encoding='utf-8')
print(json.dumps({'ok':True,'checks':len(checks)}))
