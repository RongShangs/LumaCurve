"""Compile the live candidate helpers and reject targeted semantic regressions."""
from pathlib import Path
import subprocess,json,hashlib
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'build/framework-core-test03';out.mkdir(parents=True,exist_ok=True)
header=ROOT/'csrc/framework_wake.h';debounce=ROOT/'csrc/business_debounce.c'
common=['C:/msys64/mingw64/bin/gcc.exe','-Wall','-Wextra','-Werror','-std=c11','-DIOS_PRODUCTION']
fixture=ROOT/'tests/framework_core_selection.c'
def run(name,framework=True,mutated=None):
    folder=out/name;folder.mkdir(exist_ok=True)
    text=header.read_text(encoding='utf-8')
    if mutated:
        old,new=mutated;assert text.count(old)==1;text=text.replace(old,new)
    (folder/'framework_wake.h').write_text(text,encoding='utf-8')
    (folder/fixture.name).write_bytes(fixture.read_bytes())
    exe=folder/'test.exe'
    subprocess.run(common+(['-DLUMA_FRAMEWORK_BACKEND'] if framework else [])+['-I'+str(ROOT/'csrc'),str(folder/fixture.name),str(debounce),'-lm','-o',str(exe)],check=True)
    return subprocess.run([str(exe)],capture_output=True,text=True)
positive=run('positive');positive.check_returncode();print(positive.stdout)
negative=[('restore-staircase',False,None),
 ('ignore-reporting-mode',True,('ios_scene_reporting_mode(front)==1','ios_scene_reporting_mode(front)>=0')),
 ('accept-unobserved-sample',True,('s->g_wake_last_lux_event_ms==(front?', 's->g_wake_last_lux_event_ms!=(front?'))]
for name,flag,change in negative:
    result=run(name,flag,change)
    assert result.returncode and 'Assertion failed' in result.stderr,(name,result.stdout,result.stderr)
print('3 compiled selection/wake semantic mutations rejected')
sources=[header,debounce,ROOT/'csrc/main_business.c',fixture,Path(__file__).resolve()]
(out/'selection-verification.json').write_text(json.dumps({'ok':True,'android_verified':False,'negative':[x[0] for x in negative],
 'checks':positive.stdout.splitlines(),'source_sha256':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}},indent=2)+'\n',encoding='utf-8')
