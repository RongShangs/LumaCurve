"""Exercise launcher migration and fail-closed behavior in a fake kernel hierarchy."""
from pathlib import Path
import hashlib,json,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
BASH='C:/msys64/usr/bin/bash.exe'
def shpath(p):
    s=Path(p).resolve().as_posix()
    return '/'+s[0].lower()+s[2:] if len(s)>1 and s[1]==':' else s
checks=[]
source=(ROOT/'module/process_scope.sh').read_text(encoding='utf-8')
cases=['already_root','migrate','write_failure','readback_mismatch','subtree_mount','missing_interface',
       'malformed_path','missing_proc','legacy_root','legacy_freezer','escaped_mount','missing_mount']
with tempfile.TemporaryDirectory(prefix='luma-scope-',dir=ROOT/'build') as td:
    base=Path(td)
    for case in cases+['negative_removed_migration']:
        folder=base/case;folder.mkdir();proc=folder/'proc';proc.mkdir();cg=folder/'cg';cg.mkdir()
        target=cg/'cgroup.procs'
        if case!='missing_interface':target.write_text('',encoding='utf-8')
        body=source.replace('/proc/',shpath(proc)+'/')
        if case=='negative_removed_migration':
            assert 'if ! printf \'%s\\n\' "$$" > "$_lc_scope_mount/cgroup.procs"; then' in body
            body=body.replace('if ! printf \'%s\\n\' "$$" > "$_lc_scope_mount/cgroup.procs"; then','if ! :; then')
        helper=folder/'helper.sh';helper.write_text(body,encoding='utf-8',newline='\n')
        script=f'''set -eu
export PATH=/usr/bin:/bin:$PATH; export LANG=C.UTF-8
mkdir -p '{shpath(proc)}/'"$$" '{shpath(proc)}/self'
'''
        path='/' if case=='already_root' else 'invalid' if case=='malformed_path' else '/apps/uid_10339/pid_11554'
        contents='0::'+path+'\n'
        if case=='legacy_root':contents='1:freezer:/\n'
        if case=='legacy_freezer':contents='1:freezer:/apps/uid_10339\n'
        if case!='missing_proc':script+=f"printf '%s' '{contents}' > '{shpath(proc)}/'\"$$\"'/cgroup'\n"
        mountroot='/apps/uid_10339' if case=='subtree_mount' else '/'
        mount=shpath(cg)+(r'\040escaped' if case=='escaped_mount' else '')
        mountline=f'9 1 0:2 {mountroot} {mount} rw - cgroup2 none rw\n'
        if case=='missing_mount':mountline=''
        script+=f"printf '%s' '{mountline}' > '{shpath(proc)}/self/mountinfo'\n. '{shpath(helper)}'\n"
        # Simulate kernel migration readback after the real shell writes cgroup.procs.
        script+=f'''lc_scope_path() {{
  if [ -s '{shpath(target)}' ] && [ '{case}' != readback_mismatch ]; then printf '/\\n';
  else awk -F: '$1=="0" && $2=="" {{print $3}}' '{shpath(proc)}/'"$1"'/cgroup' 2>/dev/null; fi
}}
'''
        if case=='write_failure':
            script+=r'''printf() { if [ "$1" = '%s\n' ] && [ "${2:-}" = "$$" ]; then return 1; fi; builtin printf "$@"; }
'''
        script+='lc_scope_detach_self\n'
        result=subprocess.run([BASH,'-c',script],capture_output=True,text=True,encoding='utf-8')
        wanted=case in ['already_root','migrate','legacy_root']
        assert (result.returncode==0)==wanted,(case,result.stdout,result.stderr)
        if case=='migrate':assert target.read_text().strip().isdigit()
        if case in ['already_root','subtree_mount','malformed_path','missing_mount','escaped_mount']:
            assert target.read_text()=='',case
        checks.append({'name':case,'ok':True})
    launcher=(ROOT/'module/daemon_launcher.sh').read_text(encoding='utf-8')
    assert 'lc_scope_detach_self || exit 1' in launcher and 'exec "$DAEMON"' in launcher
    for file in ['module/service.sh','module/luma_curvectl.sh']:
        s=(ROOT/file).read_text(encoding='utf-8')
        assert 'nohup sh "$MODDIR/daemon_launcher.sh"' in s and 'nohup "$DAEMON"' not in s
    checks.append({'name':'service and control share verified launcher','ok':True})
report={'ok':True,'checks':checks,'host_fixture_only':True,'android_device_verified':False,
        'source_sha256':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in
                         ['module/process_scope.sh','module/daemon_launcher.sh','module/service.sh','module/luma_curvectl.sh']}}
(ROOT/'build/process-scope-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Process scope:',len(checks),'checks PASS (including removed-migration negative)')
