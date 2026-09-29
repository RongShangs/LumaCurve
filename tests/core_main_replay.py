#!/usr/bin/env python3
"""Replay full fork main against its pre-optimization C, using recovered IO fixtures.

The external fixture toolkit and original ELF are test dependencies only.
Neither this test nor the package modifies Android files or settings.
"""
import argparse,ctypes,hashlib,json,os,subprocess,sys,time,struct,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--original',type=Path,required=True)
p.add_argument('--cc',default='C:/msys64/mingw64/bin/gcc.exe');a=p.parse_args()
sys.path.insert(0,str(ROOT/'tools'));from source_lists import sources_for
out=ROOT/'build/core-main-replay';out.mkdir(parents=True,exist_ok=True)
before=ROOT/'tests/fixtures/core-before-optimization'
manifest=json.loads((before/'manifest.json').read_text())
for name,digest in manifest['sha256'].items():assert hashlib.sha256((before/name).read_bytes()).hexdigest()==digest
dlls=[]
for baseline in (True,False):
    sources=[before/s.name if baseline and (before/s.name).exists() else s for s in sources_for() if s.name!='platform_native.c']
    if baseline:
        for index,source in enumerate(sources):
            if source.name in ('business_startup.c','write_state_file.c'):
                normalized=out/('version-normalized-'+source.name)
                normalized.write_text(source.read_text(encoding='utf-8').replace('\"0.0.1\"','\"1.0.0\"'),encoding='utf-8')
                sources[index]=normalized
    if not baseline:sources.append(ROOT/'tests/scene_trace_bridge.c')
    dll=out/('baseline.dll' if baseline else 'current.dll')
    subprocess.run([a.cc,'-shared','-O1','-std=c11','-DIOS_PRODUCTION','-DIOS_TEST_ABI','-DIOS_BUSINESS_MAIN','-mfma','-ffp-contract=off','-fno-strict-aliasing','-I'+str(ROOT/'csrc')]+[str(s) for s in sources]+[str(a.fixtures/'tests/native_io_bridge.c'),'-lm','-o',str(dll)],check=True)
    dlls.append(dll)
os.environ['IOS_TEST_DLL']=str(dlls[0]);sys.argv=[sys.argv[0],'--original',str(a.original)]
sys.path[:0]=[str(a.fixtures/'tests'),str(a.fixtures/'tools')]
import native_c as native
import full_main_scenarios as scenarios
from unicorn.arm64_const import UC_ARM64_REG_X0,UC_ARM64_REG_X1,UC_ARM64_REG_PC
libraries=[ctypes.CDLL(str(dll)) for dll in dlls]
for library in libraries:
    library.ios_test_ram.restype=ctypes.c_uint64;library.ios_test_ro.restype=ctypes.c_uint64
    library.ios_test_call.argtypes=[ctypes.c_uint64,ctypes.POINTER(native.Cpu)]
    library.ios_test_hook.argtypes=[native.HOOK]
SCENE_FIELDS={'curve_learned_points','preference_anchor','thermal_enabled','thermal_zone_count','thermal_scan_done','scene_relation','scene_hold_active','scene_hold_left_ms','scene_confirm_samples','front_reporting_mode','back_reporting_mode','preference_offset','preference_effective_offset','preference_learning','preference_samples','preference_pending','preference_dirty','preference_evidence','curve_points','curve_custom','curve_gamma','curve_circadian_factor','curve_sunlight_threshold','curve_sunlight_extreme','curve_sunlight_boost','curve_sunlight_min'}
def without_scene_fields(text):
    return ''.join(line for line in text.splitlines(keepends=True) if line.split('=',1)[0] not in SCENE_FIELDS)
LOG_PAIRS=[(json.loads(a),json.loads(b)) for a,b in re.findall(r'\{("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")\}', (ROOT/'csrc/log_locale.h').read_text(encoding='utf-8'))]
LOG_REFERENCE={chinese:original for original,chinese in LOG_PAIRS}
class Fork(scenarios.Fixture,native.NativeMachine):
    def cstr(self,address):
        value=super().cstr(address).replace('/data/local/tmp/luma_curve','/data/local/tmp/ios_brightness')
        return LOG_REFERENCE.get(value,value)
    def __init__(self,case):
        self.started=time.monotonic();self.import_count=0;self.scene_trace=[];self.sensor_last={};self.sensor_tick=None;self.sensor_origin=None
        native.NativeMachine.__init__(self)
        self.files['/data/local/tmp/ios_brightness.conf']=(ROOT/'module/luma_curve.conf').read_text(encoding='utf-8').replace('indoor_stability=1',f"indoor_stability={case.get('indoor_stability',0)}")
        self.files['/data/local/tmp/ios_brightness.conf'] = self.files['/data/local/tmp/ios_brightness.conf'].replace('preference_learning=1', f"preference_learning={case.get('preference_learning',0)}")
        for key,value in case.get('preference_config',{}).items():
            self.files['/data/local/tmp/ios_brightness.conf']=re.sub('^'+key+'=.*$',key+'='+str(value),self.files['/data/local/tmp/ios_brightness.conf'],flags=re.M)
        self.setup(case)
    def wait(self,duration):
        if self.case.get('scene_sequence'):
            fn=native.library.luma_test_scene_class;fn.restype=ctypes.c_char_p
            left=native.library.luma_test_scene_hold_left;left.argtypes=[ctypes.c_uint64];left.restype=ctypes.c_uint64
            self.scene_trace.append({'time_us':self.now_us,'relation':fn().decode(),
                                     'hold_ms':left(self.now_us//1000),
                                     'holding':bool(native.library.luma_test_scene_holding()),'state':scenarios.state(self)})
        super().wait(duration)
        if self.case.get('curve_reload') and not hasattr(self, 'curve_reload_ms') and self.now_us >= 60000000:
            self.curve_reload_ms = self.now_us // 1000
            path = '/data/local/tmp/ios_brightness.conf'
            for key, value in self.case['curve_reload'].items():
                self.files[path] = re.sub('^' + key + '=.*$', key + '=' + str(value), self.files[path], flags=re.M)
            self.write('g_reload_config', 1)
        if self.case.get('indoor_jitter'):
            self.files[self.lux_path]=str([17,23,18,22][(self.sleeps//7)%4])+'\n'
    def dynamic(self,address):
        if self.case.get('scene_sequence') and address==scenarios.NDK['ASensorEventQueue_getEvents']:
            a=[self.uc.reg_read(UC_ARM64_REG_X0+i) for i in range(3)]
            if self.sensor_origin is None:self.sensor_origin=self.now_us
            tick=self.now_us//1000000
            if tick!=self.sensor_tick:
                self.sensor_tick=tick;elapsed=(self.now_us-self.sensor_origin)/1000000
                selected=self.case['scene_sequence'][0]
                for item in self.case['scene_sequence']:
                    if item[0]<=elapsed:selected=item
                self.events=[]
                for kind,value in ((5,selected[1]),(0x1fa266f,selected[2])):
                    if kind not in self.enabled_sensors:continue
                    if self.case.get('on_change') and self.sensor_last.get(kind)==value:continue
                    self.sensor_last[kind]=value;self.events.append((kind,value))
            count=min(a[2],len(self.events));payload=bytearray()
            for i in range(count):
                kind,value=self.events.pop(0);event=bytearray(104)
                struct.pack_into('<i',event,0,104);struct.pack_into('<i',event,8,kind)
                struct.pack_into('<q',event,16,self.now_us*1000)
                struct.pack_into('<f',event,24,value);payload+=event
            if payload:self.uc.mem_write(a[1],bytes(payload))
            if count:self.now_us += self.case.get('sensor_io_latency_us',0)
            self.coverage['ndk_events']+=count;self.finish_import(count);return
        if self.case.get('on_change') and address==0x891000:
            self.finish_import(1);return
        super().dynamic(address)
    def hook(self,uc,address,size,data):
        name=self.elf.plt[address]
        self.import_count+=1
        if self.import_count%1000==0 and time.monotonic()-self.started>20:
            raise TimeoutError((self.case,self.sleeps,self.now_us,name,self.import_count))
        if name=='dlsym' and self.case.get('on_change') and self.cstr(uc.reg_read(UC_ARM64_REG_X1))=='ASensor_getReportingMode':
            self.finish_import(0x891000);return
        if name=='popen':
            command=self.cstr(uc.reg_read(UC_ARM64_REG_X0))
            if 'BrightnessEvent:' in command:
                self.commands.append(command);handle=0x801000+len(self.handles)*0x100
                user=self.adjustment != 0 and self.case.get('user_event',False)
                response=f"09-29 12:00:{1 if self.adjustment else 0:02d}.000 - BrightnessEvent: brt=0.6000{'(user_set)' if user else ''} (60%), lux=100, reason=automatic, state=ON, initBrt=0.3000, autoBrightness=true (default), logicalId=0\n"
                self.handles[handle]=dict(path=command,pos=0,text=response,mode='r');self.finish_import(handle);return
            if 'screen_brightness_mode' in command and '&&' in command:
                self.commands.append(command)
                handle=0x801000+len(self.handles)*0x100
                self.handles[handle]=dict(path=command,pos=0,text=f'{self.mode}\n{self.adjustment}\n{self.slider}\n',mode='r')
                self.finish_import(handle);return
        super().hook(uc,address,size,data)
records=[]
legacy_current={}
for case in scenarios.CASES:
    results=[]
    for library in libraries:
        native.library=library;scenarios.library=library
        machine=Fork(case)
        result=scenarios.observe(machine)
        result['commands']=[part.strip() for command in result['commands'] for part in command.split('&&')]
        # NDK request counts are intentionally batched; ordered events and all
        # business state still match at each original control wait boundary.
        result.pop('ndk_calls')
        # Compare all logs after reversible translation of format strings above;
        # the sole permitted deletion is the quiet event_age warning.
        for key in ('stdout','log','logs'):
            if key in result and isinstance(result[key],str): result[key]=''.join(line for line in result[key].splitlines(keepends=True) if 'WARN: sensor stale (event_age,' not in line)
            elif key in result and isinstance(result[key],list): result[key]=[line for line in result[key] if 'WARN: sensor stale (event_age,' not in line]
        results.append(result)
    legacy_current[case['name']]=results[1]
    differences=[key for key in results[0] if results[0][key]!=results[1][key]]
    expected_change=False
    if case['name']=='ndk_front_missing':
        old=results[0];new=results[1]
        get=lambda obj,key:int.from_bytes(bytes.fromhex(obj['final_state'][key]),'little')
        assert get(old,'g_ndk_ok')==0 and get(new,'g_ndk_ok')==1
        assert get(new,'g_back_ok')==1 and get(new,'g_lux_valid')==1
        assert new['coverage']['ndk_events']>0 and new['coverage']['lux_reads']==0
        assert 'lux_source=back\n' in new['files']['/data/local/tmp/ios_brightness_state']
        expected_change=True
    record={'name':case['name'],'ok':not differences or expected_change,'differences':differences,
            'expected_behavior_change':expected_change,'coverage':results[1]['coverage']}
    records.append(record);print(json.dumps(record),flush=True)
    if differences and not expected_change:
        (out/'failure.json').write_text(json.dumps({'case':case,'results':results},indent=2),encoding='utf-8');break
default_cases=[]
scene_cases=[]
if len(records)==len(scenarios.CASES) and all(r['ok'] for r in records):
    native.library=libraries[1];scenarios.library=libraries[1]
    for case in scenarios.CASES:
        machine=Fork(dict(case,indoor_stability=1));result=scenarios.observe(machine)
        result['commands']=[part.strip() for command in result['commands'] for part in command.split('&&')]
        result.pop('ndk_calls')
        for point in result['trace']:
            state=point['state']
            maximum=int.from_bytes(bytes.fromhex(state['g_max']),'little')
            held=int.from_bytes(bytes.fromhex(state['g_target_debounce_br']),'little',signed=True)
            assert 0<=held<=maximum,(case['name'],held,maximum)
        # These exercise unchanged protective/ownership paths, independently of
        # the jitter test. Require the entire recorded control trace to match.
        protected=case['name'] in ('thermal_hot','ndk_hot_hbm','sunlight_hbm','sunlight_exit',
                                  'ndk_proximity_block','registered_term_cleanup')
        if protected:
            comparable=dict(result)
            comparable['files']=dict(result['files'])
            config_path='/data/local/tmp/ios_brightness.conf'
            comparable['files'][config_path]=comparable['files'][config_path].replace('indoor_stability=1','indoor_stability=0')
            state_path='/data/local/tmp/ios_brightness_state'
            if state_path in comparable['files']:comparable['files'][state_path]=without_scene_fields(comparable['files'][state_path])
            if comparable!=legacy_current[case['name']]:
                (out/'protected-failure.json').write_text(json.dumps({'case':case,'current':comparable,
                    'legacy':legacy_current[case['name']]},indent=2),encoding='utf-8')
            assert comparable==legacy_current[case['name']],(case['name'],[k for k in comparable if comparable[k]!=legacy_current[case['name']][k]])
        default_cases.append({'name':case['name'],'ok':True,'bounded_target':True,
                              'protected_full_trace_unchanged':protected})
    jitter=[]
    for enabled in (0,1):
        print('indoor jitter main enabled='+str(enabled),flush=True)
        result=scenarios.observe(Fork({'name':'indoor_jitter','sysfs':True,'lux':20,
                                      'indoor_jitter':True,'indoor_stability':enabled,'waits':400}))
        jitter.append(result)
    counts=[r['coverage']['backlight_writes'] for r in jitter]
    assert counts[1]<counts[0],counts
    # Integration witness: prove the switched production main changes its actual
    # control state on indoor jitter, not just an uncalled helper.
    assert jitter[0]['trace']!=jitter[1]['trace'],'temporal option had no observable main-loop effect'
    default_cases.append({'name':'indoor_jitter_switch_witness','ok':True,
                          'legacy_backlight_writes':counts[0],'stable_backlight_writes':counts[1]})
    scene_runs={}
    definitions=[
        ('dual_stable',[(0,200,40)],False),
        ('rear_flashlight',[(0,200,40),(60,200,10000)],False),
        ('front_only_rise',[(0,200,40),(60,400,40)],False),
        ('paired_rise',[(0,200,40),(60,400,80)],False),
        ('paired_fall',[(0,200,40),(60,20,4)],False),
        ('front_occlusion',[(0,200,40),(60,1,40),(62,200,40)],False),
        ('divergent',[(0,200,40),(60,20,80)],False),
        ('onchange_quiet',[(0,200,40)],True),
        ('onchange_front_rise',[(0,200,40),(60,400,40)],True),
        ('onchange_back_only',[(0,200,40)],True),
        ('onchange_dark_zero',[(0,200,40),(60,0,40)],True),
        ('onchange_low_lux_rise',[(0,5,1),(60,100,1)],True),
    ]
    for name,sequence,on_change in definitions:
        machine=Fork({'name':name,'ndk':True,'indoor_stability':1,'waits':300,
                      'scene_sequence':sequence,'on_change':on_change,'front_missing':name=='onchange_back_only'})
        result=scenarios.observe(machine)
        trace=machine.scene_trace;assert trace,(name,'classifier never observed')
        def number(point,key):return int.from_bytes(bytes.fromhex(point['state'][key]),'little',signed=True)
        def real(point,key):return struct.unpack('<f',bytes.fromhex(point['state'][key]))[0]
        holds=[p for p in trace if p['holding']]
        if name=='rear_flashlight':
            assert any(p['relation']=='back_only' for p in trace)
            assert not holds
        if name in ('front_only_rise','divergent','onchange_front_rise'):
            if not holds:
                (out/'scene-failure.json').write_text(json.dumps({'name':name,'trace':[
                    dict(time_us=p['time_us'],relation=p['relation'],hold_ms=p['hold_ms'],state={k:p['state'][k] for k in
                    ('lux_prev1','g_sensor_stale','g_lux_source','g_brightness_owner','g_last_processed_lux_event_ms','g_front_lux_event_ms')})
                    for p in trace]},indent=2),encoding='utf-8')
            assert holds,(name,'unilateral transition never held')
        if name in ('paired_rise','paired_fall'):
            expected='common_rise' if name=='paired_rise' else 'common_fall'
            assert any(p['relation']==expected for p in trace),(name,'missing paired evidence')
            assert not holds,(name,'paired transition delayed by unilateral gate')
        if name=='front_occlusion':
            assert holds
            assert all(real(p,'lux_prev1')>100 for p in trace if p['time_us']>machine.sensor_origin+59000000)
            assert all(number(p,'g_actuator_fast_dark')==0 for p in trace)
        if on_change:
            assert all(number(p,'g_sensor_stale')==0 for p in trace),(name,'quiet on-change marked stale')
            assert result['coverage']['ndk_events']==(1 if name=='onchange_back_only' else 2 if name=='onchange_quiet' else 3)
        if name=='onchange_front_rise':
            assert real(trace[-1],'g_actuator_smooth_lux')>=399,(name,'smoothing stalled without repeated events')
        if name=='onchange_dark_zero':
            assert real(trace[-1],'g_actuator_smooth_lux')<10,(name,'zero guard or smoothing never completed')
            assert number(trace[-1],'g_zero_lux_suspect')==0
        if name=='onchange_low_lux_rise':
            assert number(trace[-1],'g_low_lux_bright_spike_guard')==0
            assert real(trace[-1],'g_actuator_smooth_lux')>=95,(name,'spike guard or smoothing never completed')
        if name=='onchange_back_only':
            final=result['files']['/data/local/tmp/ios_brightness_state']
            assert 'lux_source=back\n' in final and 'lux_valid=1\n' in final
            assert result['coverage']['lux_reads']==0
        if name in ('front_only_rise','onchange_front_rise'):
            assert any(real(p,'lux_prev1')>=399 for p in trace),(name,'front change never accepted')
        scene_runs[name]=(machine,result)
        scene_cases.append({'name':name,'ok':True,'wait_boundaries':len(trace),
                            'held_wait_boundaries':len(holds),'ndk_events':result['coverage']['ndk_events'],
                            'relations':sorted(set(p['relation'] for p in trace))})
        print('scene main '+json.dumps(scene_cases[-1]),flush=True)
    def control_trace(name):
        return [(p['time_us'],tuple(p['state'][k] for k in
                ('g_target_debounce_br','g_target_candidate_br','g_actuator_smooth_lux','lux_prev1')))
                for p in scene_runs[name][0].scene_trace]
    assert control_trace('dual_stable')==control_trace('rear_flashlight'),'rear alone changed front control'
    def first_rise(name):
        return min(p['time_us'] for p in scene_runs[name][0].scene_trace
                   if struct.unpack('<f',bytes.fromhex(p['state']['lux_prev1']))[0]>=399)
    assert first_rise('paired_rise')<first_rise('front_only_rise'),'paired change did not reach control earlier'
    for name in ('paired_rise','front_only_rise','onchange_front_rise'):
        item=next(c for c in scene_cases if c['name']==name)
        item['front_accept_delay_ms']=(first_rise(name)-scene_runs[name][0].sensor_origin)//1000-60000
preference_cases=[]
native.library=libraries[1];scenarios.library=libraries[1]
for name,config,learn,user in [('preference_neutral',{},0,False),('preference_brighter',{'preference_offset':20,'preference_revision':1},0,False),
    ('custom_flat',{'curve_custom':1,'curve_points':','.join(['10']*14)},0,False),
    ('preference_thermal',{'curve_custom':1,'curve_points':','.join(['100']*14)},0,False),
    ('learn_user',{},1,True),('ignore_system',{},1,False),('learn_off',{},0,True)]:
    case={'name':name,'sysfs':True,'lux':100,'waits':240,'indoor_stability':1,'preference_config':config,'preference_learning':learn,'user_event':user}
    if name=='preference_thermal':case.update(thermal=70000)
    if name in ('learn_user','ignore_system','learn_off'):case['adjustment_changes']={35:.75}
    machine=Fork(case);result=scenarios.observe(machine)
    final=result['files']['/data/local/tmp/ios_brightness_state'];values=dict(line.split('=',1) for line in final.splitlines() if '=' in line)
    target=int(values['target_br']);sample=int(values.get('preference_samples',0))
    if name=='preference_neutral':neutral=target
    if name=='preference_brighter':assert target>=neutral*1.18,(name,neutral,target)
    if name=='custom_flat':assert .09<=target/int(values['max_br'])<=.11,(name,target,values['max_br'])
    if name=='preference_thermal':assert target<=int(values['max_br'])*.201,(name,target,values['max_br'])
    if name=='learn_user':assert sample==1 and float(values['preference_offset'])==0 and float(values['curve_learned_points'].split(',')[5]) > float(values['curve_points'].split(',')[5]),(name,values)
    if name in ('ignore_system','learn_off'):assert sample==0 and float(values.get('preference_offset',0))==0,(name,values)
    preference_cases.append({'name':name,'ok':True,'target':target,'learned_samples':sample})
    print('preference main '+json.dumps(preference_cases[-1]),flush=True)
curve_reload_cases=[]
for name,points,thermal in [('quiet_front_raise', '60', 0), ('quiet_front_lower', '0.1', 0), ('quiet_front_thermal', '100', 70000), ('sensor_io_latency', '60', 0)]:
    case={'name':name,'ndk':True,'on_change':True,'scene_sequence':[(0,.9,11)],'waits':180,
          'indoor_stability':1,'preference_learning':0,
          'curve_reload':{'curve_custom':1,'curve_points':','.join([points]*14),'preference_revision':1}}
    if name=='quiet_front_lower':case['preference_config']={'curve_custom':1,'curve_points':','.join(['60']*14)}
    if name=='sensor_io_latency':case.update(on_change=False,sensor_io_latency_us=25000,scene_sequence=[(0,100,7),(65,600,7)])
    if thermal:case['thermal']=thermal
    machine=Fork(case);result=scenarios.observe(machine)
    after=[p for p in machine.scene_trace if 1000 <= p['time_us']//1000-machine.curve_reload_ms <= 12000]
    assert after,(name,'no control passes after reload')
    word=lambda p,k:int.from_bytes(bytes.fromhex(p['state'][k]),'little',signed=True)
    maximum=word(after[0],'g_max')
    reached=[p for p in after if (word(p,'g_tr_1')>=maximum*.55 if points=='60' else
             word(p,'g_tr_1')<=maximum*.002 if points=='0.1' else
             word(p,'g_tr_1')<=maximum*.201 and word(p,'g_heat_guard_active')==1)]
    assert reached,(name,'explicit curve edit did not reach protected transition target',
                    [(p['time_us']//1000-machine.curve_reload_ms,word(p,'g_tr_1')) for p in after])
    if thermal:assert all(word(p,'g_tr_1')<=maximum*.201 for p in after)
    if name=='sensor_io_latency':
        assert all(word(p,'g_sensor_hold_active')==0 for p in after),(name,'new samples misclassified as future/stale')
        final=dict(line.split('=',1) for line in result['files']['/data/local/tmp/ios_brightness_state'].splitlines() if '=' in line)
        assert float(final['smooth'])>550 and final['sensor_stale']=='0',(name,final)
    assert result['coverage']['backlight_writes']>0,(name,'no actual actuator writes')
    curve_reload_cases.append({'name':name,'ok':True,'target_delay_ms':reached[0]['time_us']//1000-machine.curve_reload_ms,
                               'target':word(reached[0],'g_tr_1'),'backlight_writes':result['coverage']['backlight_writes'],
                               'on_change_sensor':case['on_change']})
    print('curve reload main '+json.dumps(curve_reload_cases[-1]),flush=True)
mutated=out/'main-without-post-poll-clock.c'
text=(ROOT/'csrc/main_business.c').read_text(encoding='utf-8')
assert text.count('now_ms = domain_now_ms(&io);')==4
mutated.write_text(text.replace('now_ms = domain_now_ms(&io);','/* removed post-poll clock refresh */'),encoding='utf-8')
negative_dll=out/'latency-negative.dll'
negative_sources=[mutated if s.name=='main_business.c' else s for s in sources_for() if s.name!='platform_native.c']
subprocess.run([a.cc,'-shared','-O1','-std=c11','-DIOS_PRODUCTION','-DIOS_TEST_ABI','-DIOS_BUSINESS_MAIN','-mfma','-ffp-contract=off','-fno-strict-aliasing','-I'+str(ROOT/'csrc')]+[str(s) for s in negative_sources]+[str(ROOT/'tests/scene_trace_bridge.c'),str(a.fixtures/'tests/native_io_bridge.c'),'-lm','-o',str(negative_dll)],check=True)
broken=ctypes.CDLL(str(negative_dll));broken.ios_test_ram.restype=ctypes.c_uint64;broken.ios_test_ro.restype=ctypes.c_uint64
broken.ios_test_call.argtypes=[ctypes.c_uint64,ctypes.POINTER(native.Cpu)];broken.ios_test_hook.argtypes=[native.HOOK]
native.library=broken;scenarios.library=broken
machine=Fork(case);result=scenarios.observe(machine)
assert any(int.from_bytes(bytes.fromhex(p['state']['g_sensor_hold_active']),'little')==1
           for p in machine.scene_trace if p['time_us']//1000>machine.curve_reload_ms), 'latency mutant did not reproduce false sensor hold'
print('post-poll clock negative: false sensor hold reproduced',flush=True)
report={'ok':len(records)==len(scenarios.CASES) and all(r['ok'] for r in records),'cases':records,
        'baseline_commit':manifest['git_commit'],'full_main_replay':True,'original_elf_differential':False,
        'android_device_verified':False,'ui_subscription_exercised':False,'indoor_stability_exercised':True,
        'legacy_configuration_override':'indoor_stability=0, preference_learning=0: healthy baseline comparison',
        'log_comparison':'Chinese format strings reversed without changing argument values; only event_age warnings omitted',
        'default_configuration_cases':default_cases,'scene_adaptation_cases':scene_cases,'preference_cases':preference_cases,
        'curve_reload_cases':curve_reload_cases,'sensor_latency_negative_detected':True,'fixture_toolkit':str(a.fixtures)}
report['source_sha256']={s.relative_to(ROOT).as_posix():hashlib.sha256(s.read_bytes()).hexdigest() for s in sources_for()+list((ROOT/'csrc').glob('*.h'))}
(ROOT/'build/core-main-replay-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
raise SystemExit(0 if report['ok'] else 1)
