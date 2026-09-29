#!/usr/bin/env python3
"""Run production reliability and temporal tests, including semantic mutations."""
import hashlib, json, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
out=ROOT/'build/core-reliability';out.mkdir(parents=True,exist_ok=True)
names=['curve_pct_for_lux.c','brightness_preference.c','state.c','read_screen.c','read_lux_fallback.c','business_sensor_init.c',
       'set_light_sensors_enabled.c','business_debounce.c','load_config.c']
common=['C:/msys64/mingw64/bin/gcc.exe','-O1','-std=c11','-DIOS_PRODUCTION',
        '-DIOS_TEST_ABI','-ffp-contract=off','-fno-strict-aliasing','-I'+str(ROOT/'csrc'),
        '-Wall','-Wextra','-Werror']
def build(alteration=None):
    paths=[ROOT/'csrc'/n for n in names]
    name='positive'
    if alteration:
        name,filename,old,new=alteration
        text=(ROOT/'csrc'/filename).read_text(encoding='utf-8');assert text.count(old)==1,(name,text.count(old))
        changed=out/(name+'.c');changed.write_text(text.replace(old,new),encoding='utf-8')
        paths=[changed if p.name==filename else p for p in paths]
    exe=out/(name+'.exe')
    subprocess.run(common+[str(p) for p in paths]+[str(ROOT/'tests/core_reliability.c'),'-lm','-o',str(exe)],check=True)
    return subprocess.run([str(exe)],text=True,capture_output=True)
result=build();print(result.stdout);print(result.stderr);result.check_returncode()
mutations=[
 ('clock-check','read_lux_fallback.c','(int64_t)*legacy_result != 0','false'),
 ('screen-unknown','read_screen.c','return last_screen == 1;','return true;'),
 ('front-enable','business_sensor_init.c','(int32_t)NDK_switch(&io,ARG(ios_state.fn_enable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor)) == 0','(int32_t)NDK_switch(&io,ARG(ios_state.fn_enable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor)) >= -1'),
 ('temporal-off','business_debounce.c','if (!luma_indoor_stability || bypass','if (true || !luma_indoor_stability || bypass'),
 ('scene-front-priority','business_debounce.c','if (!direction) *lux = scene.accepted_front;','if (!direction) *lux = scene.sides[1].value;'),
 ('scene-common-evidence','business_debounce.c','bool common = scene.kind == SCENE_COMMON_RISE || scene.kind == SCENE_COMMON_FALL;','bool common = true;'),
 ('scene-timestamp-pairing','business_debounce.c','if (onset_gap > UINT64_C(1500000000) || sample_gap > UINT64_C(1500000000))','if (onset_gap > UINT64_C(150000000000) || sample_gap > UINT64_C(150000000000))'),
 ('scene-onchange-only','business_debounce.c','ios_scene_reporting_mode(front) == 1','ios_scene_reporting_mode(front) >= -1'),
 ('scene-timed-smoothing','business_debounce.c','if (!luma_indoor_stability || (!front && !back)','if (true || !luma_indoor_stability || (!front && !back)'),
]
negative=[]
for item in mutations:
    broken=build(item)
    assert broken.returncode and 'Assertion failed' in broken.stderr,(item[0],broken.stdout,broken.stderr)
    negative.append({'name':item[0],'compiled':True,'semantic_failure_detected':True})
sources=['csrc/'+n for n in names]+['csrc/backlight_paths.h','csrc/temporal_stability.h','csrc/scene_adaptation.h']
report={'ok':True,'host':'Windows GCC with controlled libc/NDK boundaries',
        'checks':result.stdout.splitlines(),'negative':negative,'android_device_verified':False,
        'source_sha256':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in sources}}
(ROOT/'build/core-reliability-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('9 independently compiled semantic mutations rejected')
