#!/usr/bin/env python3
"""Build and execute production C optimization tests; baseline fixtures are hash-pinned."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--cc',default='C:/msys64/mingw64/bin/gcc.exe');p.add_argument('--negative',action='store_true');a=p.parse_args()
fixture=ROOT/'tests/fixtures/core-before-optimization'
manifest=json.loads((fixture/'manifest.json').read_text())
for name,digest in manifest['sha256'].items():assert hashlib.sha256((fixture/name).read_bytes()).hexdigest()==digest
out=ROOT/'build/core-optimization';out.mkdir(parents=True,exist_ok=True)
common=[a.cc,'-O1','-std=c11','-DIOS_PRODUCTION','-DIOS_TEST_ABI','-ffp-contract=off','-fno-strict-aliasing','-I'+str(ROOT/'csrc'),'-Wall','-Wextra','-Werror']
objects=[]
for name,rename in [('poll_sensors.c','ios_sensor_poll=baseline_sensor_poll'),('write_state_file.c','ios_write_state_frame=baseline_write_state_frame')]:
    source=fixture/name
    if name=='write_state_file.c':
        source=out/('version-normalized-'+name)
        source.write_text((fixture/name).read_text(encoding='utf-8').replace('\"0.0.1\"','\"1.0.0\"'),encoding='utf-8')
    obj=out/(name+'.o');subprocess.run(common+['-D'+rename,'-c',str(source),'-o',str(obj)],check=True);objects.append(str(obj))
exe=out/'core-optimization.exe'
subprocess.run(common+[str(ROOT/'csrc'/n) for n in ('curve_pct_for_lux.c','brightness_preference.c','state.c','refresh_settings.c','poll_sensors.c','write_state_file.c','read_screen.c','business_debounce.c')]+[str(ROOT/'tests/core_optimization.c')]+objects+['-lm','-o',str(exe)],check=True)
result=subprocess.run([str(exe)],text=True,capture_output=True)
if result.returncode:
    print(result.stdout);print(result.stderr);result.check_returncode()
print(result.stdout)
negative=[]
if a.negative:
    mutations=[('settings','refresh_settings.c','if (errno || end == text || !trailing_space(end)) return false;','if (errno) return false;'),
               ('sensors','poll_sensors.c','timestamp <= last_timestamp','timestamp < last_timestamp'),
               ('state-write','write_state_file.c','written != output.length','written > output.length'),
               ('lease','write_state_file.c','wall - modified < 4','wall - modified < 4000')]
    for name,filename,old,new in mutations:
        text=(ROOT/'csrc'/filename).read_text(encoding='utf-8');assert text.count(old)==1
        altered=out/(name+'.c');altered.write_text(text.replace(old,new),encoding='utf-8')
        broken=out/(name+'.exe')
        src=[altered if n==filename else ROOT/'csrc'/n for n in ('curve_pct_for_lux.c','brightness_preference.c','state.c','refresh_settings.c','poll_sensors.c','write_state_file.c','read_screen.c','business_debounce.c')]
        subprocess.run(common+[str(s) for s in src]+[str(ROOT/'tests/core_optimization.c')]+objects+['-lm','-o',str(broken)],check=True)
        failure=subprocess.run([str(broken)],text=True,capture_output=True)
        assert failure.returncode!=0 and 'Assertion failed' in failure.stderr,(name,failure.stdout,failure.stderr)
        negative.append({'name':name,'compiled':True,'semantic_failure_detected':True})
    print('negative: all 4 independently compiled semantic mutations rejected')
source_hashes={f'csrc/{n}':hashlib.sha256((ROOT/'csrc'/n).read_bytes()).hexdigest() for n in ('curve_pct_for_lux.c','brightness_preference.c','state.c','refresh_settings.c','poll_sensors.c','write_state_file.c','read_screen.c','business_debounce.c','state_observer.h')}
(ROOT/'build/core-optimization-verification.json').write_text(json.dumps({'ok':True,'baseline_commit':manifest['git_commit'],'baseline_hashes_verified':True,'source_sha256':source_hashes,'host':'Windows x64 GCC; mocked libc/NDK','full_main_replay':False,'android_device_verified':False,'checks':result.stdout.splitlines(),'negative':negative},indent=2)+'\n',encoding='utf-8')
