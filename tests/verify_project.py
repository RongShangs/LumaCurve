#!/usr/bin/env python3
"""Validate the fork's build, namespace, license and installer extraction contract."""
import argparse, hashlib, json, re, struct, subprocess, sys, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from source_lists import sources_for
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--upstream', type=Path, help='Compare untouched units and pinned pre-optimization fixtures with recovered source')
p.add_argument('--bash', help='Shell executable for syntax checks; scripts are never executed')
args = p.parse_args()
checks = []
sha = lambda b: hashlib.sha256(b).hexdigest()
def passed(name): checks.append({'name': name, 'ok': True})
def main():
    properties = dict(line.split('=',1) for line in (ROOT/'module/module.prop').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
    assert properties['id']=='luma_curve' and properties['version']=='1.0.0' and properties['versionCode']=='10000'
    assert properties['author']=='酷安@戎Shang' and '流光亮度' in properties['name']
    assert properties['description'].startswith('[LumaCurve核心✘] ') and '凌乱' not in properties['description']
    core_status=json.loads((ROOT/'build/core-status-verification.json').read_text(encoding='utf-8'))
    assert core_status['ok'] and len(core_status['checks'])>=9
    for name,digest in core_status['source_sha256'].items(): assert sha((ROOT/name).read_bytes())==digest,name
    passed('Module identity, release version, live core indicator and maintainer')
    scope=json.loads((ROOT/'build/process-scope-verification.json').read_text(encoding='utf-8'))
    assert scope['ok'] and len(scope['checks'])==14
    for name,digest in scope['source_sha256'].items(): assert sha((ROOT/name).read_bytes())==digest,name
    passed('Verified launcher detaches its own process; migration failures stop startup and removed-migration negative is detected')
    state_source=(ROOT/'csrc/write_state_file.c').read_text(encoding='utf-8')
    assert re.search(r'state_text\(io, stream, "version=%s\\n", "([^"]+)"\);',state_source).group(1)==properties['version'],'State version must match module version'
    passed('Device state reports the current module version')
    update=json.loads((ROOT/'update.json').read_text(encoding='utf-8'))
    assert update['version']==properties['version'] and update['versionCode']==int(properties['versionCode'],10)
    assert update['zipUrl'].endswith('/v1.0.0/luma_curve-1.0.0.zip')
    assert properties['updateJson']=='https://raw.githubusercontent.com/RongShangs/LumaCurve/main/update.json'
    passed('Manager update endpoint and decimal versionCode match module identity')
    metadata = json.loads((ROOT/'build/highlevel/build.json').read_text(encoding='utf-8'))
    assert metadata['project']=='LumaCurve' and metadata['target']=='highlevel'
    assert len(metadata['inputs'])==36
    blob = (ROOT/'build/highlevel/luma_curve_daemon').read_bytes()
    assert sha(blob)==metadata['sha256'] and blob[:6]==b'\x7fELF\x02\x01' and struct.unpack_from('<HH',blob,16)==(3,183)
    phoff=struct.unpack_from('<Q',blob,32)[0];stride,count=struct.unpack_from('<HH',blob,54)
    aligns=[struct.unpack_from('<Q',blob,phoff+i*stride+48)[0] for i in range(count) if struct.unpack_from('<I',blob,phoff+i*stride)[0]==1]
    assert aligns and all(value>=16384 for value in aligns)
    for name,wanted in metadata['input_sha256'].items(): assert sha((ROOT/name).read_bytes())==wanted, name
    assert b'/data/local/tmp/ios_brightness' not in blob
    assert b'/data/local/tmp/luma_curve.conf\0' in blob and b'/data/local/tmp/luma_curve_state\0' in blob
    assert b'--check-install\0' in blob and b'probe_protocol=1\0' in blob
    passed('Fresh 36-source AArch64 PIE, input hashes, 16KB segments and independent runtime paths')
    package=ROOT/'dist/luma_curve-1.0.0.zip'
    with zipfile.ZipFile(package) as z:
        assert z.testzip() is None
        build=json.loads(z.read('build-info.json'))
        assert build==metadata
        expected={path.relative_to(ROOT/'module').as_posix() for path in (ROOT/'module').rglob('*') if path.is_file()} | {'build-info.json','system/bin/luma_curve_daemon'}
        assert set(z.namelist())==expected,'Unexpected or missing files in module ZIP'
        assert sha(z.read('system/bin/luma_curve_daemon'))==build['packaged_daemon_sha256']
        for name,wanted in build['module_files_sha256'].items(): assert sha(z.read(name))==wanted,name
        assert z.read('update-binary')==z.read('META-INF/com/google/android/update-binary')
        installer=z.read('update-binary').decode('utf-8')
        assert 'ui-bg.jpg' not in installer and 'pm uninstall' not in installer
        for members in re.findall(r'for _file in (.*?); do',installer):
            prefix='webroot/' if 'index.html' in members else ''
            for name in members.split(): assert prefix+name in z.namelist(),prefix+name
        for info in z.infolist():
            if info.filename.endswith(('.sh','update-binary','luma_curve_daemon')): assert (info.external_attr>>16)&0o777==0o755,info.filename
        for path in (ROOT/'module').rglob('*'):
            if path.is_file() and (path.suffix=='.sh' or path.name=='update-binary'): assert b'\r' not in path.read_bytes(),path
    for name,digest in {'donate-wechat.jpg':'d361590aea964130035318b49253b5058414e1aeb29030e08a7ac4ed2a429fce','donate-alipay.jpg':'7d46f553bcb89f57faac07a06064e4d196cf1f4acd0d28523c3c6c172f49e051'}.items():
        assert sha((ROOT/'module/webroot'/name).read_bytes())==digest,name
    passed('Exact packaged assets, original QR images, installer extraction list, duplicate installer consistency, LF and executable modes')
    license=(ROOT/'LICENSE').read_bytes()
    assert b'GNU GENERAL PUBLIC LICENSE' in license and b'Version 3, 29 June 2007' in license and len(license)>30000
    assert license==(ROOT/'module/LICENSE').read_bytes()==(ROOT/'module/webroot/LICENSE.txt').read_bytes()
    assert not (ROOT/'NOTICE.md').exists() and not (ROOT/'module/NOTICE.md').exists()
    assert '凌乱的风&' in (ROOT/'docs/来源与许可证.md').read_text(encoding='utf-8')
    assert 'GPL-3.0' in (ROOT/'module/webroot/index.html').read_text(encoding='utf-8')
    passed('GPL-3.0 full text and provenance included in project and module')
    localized=json.loads((ROOT/'build/log-locale-verification.json').read_text(encoding='utf-8'))
    assert localized['ok'] and localized['translated_formats']==56 and localized['io_paths']==['production','test-abi']
    for name,digest in localized['source_sha256'].items(): assert sha((ROOT/name).read_bytes())==digest,name
    passed('56 Chinese daemon formats preserve printf arguments, both IO paths and quiet ALS diagnostics')
    for path in (ROOT/'module').rglob('*'):
        if path.is_file() and (path.suffix=='.sh' or path.name=='update-binary'):
            text=path.read_text(encoding='utf-8')
            if path.name != 'install_choice.sh': assert 'ios_brightness' not in text and '/data/adb/ios_auto_brightness' not in text,path
            if args.bash: subprocess.run([args.bash,'-n',str(path)],check=True)
    if args.bash: passed('All Android shell scripts individually syntax-checked without execution')
    if args.upstream:
        for source in sources_for():
            if source.name in ('brightness_preference.c','curve_pct_for_lux.c','platform_native.c'): continue  # Maintained feature code bound to production feature tests below.
            old=(args.upstream/'csrc'/source.name).read_text(encoding='utf-8')
            expected=re.sub(r'"(?:\\.|[^"\\])*"',lambda m:m.group(0).replace('ios_brightness','luma_curve').replace('iOS_Bright','LumaCurve'),old)
            if source.name=='business_startup.c': expected=expected.replace('iOS Brightness v%s','LumaCurve v%s').replace('("2.5.5")','("0.0.1")')
            if source.name=='write_state_file.c': expected=expected.replace('"version=%s\\n", "2.5.5"','"version=%s\\n", "0.0.1"')
            if source.name=='main_business.c': expected=expected.replace('\n  \n','\n\n')
            if source.name=='platform_native.c':
                expected=expected.replace('int main(void) {','#include "install_probe.h"\nint main(int argc, char **argv) {\n    if (argc == 2 && !strcmp(argv[1], "--check-install")) return luma_install_probe();\n    if (argc != 1) { fprintf(stderr,"Usage: luma_curve_daemon [--check-install]\\n"); return 2; }')
            maintained=set(json.loads((ROOT/'tests/fixtures/core-before-optimization/manifest.json').read_text())['sha256'])
            comparison=ROOT/'tests/fixtures/core-before-optimization'/source.name if source.name in maintained else source
            assert expected==comparison.read_text(encoding='utf-8'),source.name
        header_text=(ROOT/'csrc/domain_io.h').read_text(encoding='utf-8').replace('#include "log_locale.h"\n','').replace('    format = luma_log_format(format);\n','')
        assert header_text==(args.upstream/'csrc/domain_io.h').read_text(encoding='utf-8')
        for header in (ROOT/'csrc').glob('*.h'):
            if header.name not in ('backlight_handle.h','state_observer.h','install_probe.h','backlight_paths.h','temporal_stability.h','scene_adaptation.h','log_locale.h','domain_io.h','brightness_preference.h','brightness_curve.h','log_timestamp.h','backlight_write_diagnostics.h','actuator_diagnostics.h','core_build.h'): assert header.read_text(encoding='utf-8')==(args.upstream/'csrc'/header.name).read_text(encoding='utf-8'),header.name
        passed('Untouched units and hash-pinned pre-optimization fixtures match the branded recovered baseline')
    for name in ('core-optimization-verification.json','core-main-replay-verification.json'):
        core=json.loads((ROOT/'build'/name).read_text(encoding='utf-8'))
        assert core['ok'],name
        for source,wanted in core['source_sha256'].items():assert sha((ROOT/source).read_bytes())==wanted,(name,source)
        if name.startswith('core-main'):
            assert len(core['cases'])==37 and all(c['ok'] for c in core['cases'])
            assert core['indoor_stability_exercised']
            assert len(core['scene_adaptation_cases'])==12 and all(c['ok'] for c in core['scene_adaptation_cases'])
            assert len(core['default_configuration_cases'])==38 and all(c['ok'] for c in core['default_configuration_cases'])
            assert len(core['preference_cases'])==7 and all(c['ok'] for c in core['preference_cases'])
            assert len(core['curve_reload_cases'])==4 and all(c['ok'] and c['backlight_writes']>0 for c in core['curve_reload_cases'])
            assert core['sensor_latency_negative_detected']
        else:assert len(core['negative'])==4 and all(c['semantic_failure_detected'] for c in core['negative'])
    passed('Current source hashes bind core regressions, semantic negatives, 37 baseline replays, 38 default temporal cases and 12 dual-sensor main scenarios')
    preference=json.loads((ROOT/'build/preference-verification.json').read_text(encoding='utf-8'))
    assert preference['ok'] and len(preference['negative'])==5
    assert all(c['semantic_failure_detected'] for c in preference['negative'])
    for source,wanted in preference['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
    passed('Gradual preference, intent gates, persistence/IO rollback, strict custom curves, timestamps and 201 C/JS curve comparisons; five semantic negatives')
    reliability=json.loads((ROOT/'build/core-reliability-verification.json').read_text(encoding='utf-8'))
    assert reliability['ok'] and len(reliability['negative'])==10
    assert all(c['semantic_failure_detected'] for c in reliability['negative'])
    for source,wanted in reliability['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
    passed('Production lux/clock, NDK enable, screen cache, backlight selection and temporal/config regressions with ten semantic negatives; four curve reload/IO latency main cases')
    diagnostics=json.loads((ROOT/'build/backlight-diagnostics-verification.json').read_text(encoding='utf-8'))
    assert diagnostics['ok'] and len(diagnostics['checks'])==4
    for source,wanted in diagnostics['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
    passed('Production native backlight error stages, throttling, preserved errno and syscall return values')
    ownership=json.loads((ROOT/'build/backlight-ownership-verification.json').read_text(encoding='utf-8'))
    assert ownership['ok'] and ownership['negative_detected']
    for source,wanted in ownership['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
    passed('Kernfs permission model: persistent FD, seek, release/reacquire and old-close semantic negative')
    for report_name,expected in [('android-actuator-verification.json',5),('hot-test-verification.json',5)]:
        report=json.loads((ROOT/'build'/report_name).read_text(encoding='utf-8'))
        assert report['ok'] and len(report.get('cases',report.get('checks',[])))==expected
        for source,wanted in report['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
        if report_name.startswith('android-'):assert report['elf_sha256']==metadata['sha256']
    passed('Actual production ARM64 ELF writes/readbacks and errno failures; isolated hot-test validation, rollback and pause preservation')
    compatibility=json.loads((ROOT/'build/compatibility-verification.json').read_text(encoding='utf-8'))
    assert compatibility['ok'] and len(compatibility['checks'])>=10
    for source,wanted in compatibility['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
    passed('Installer requires real current-value write/readback and rejects unverified reports')
    ui=json.loads((ROOT/'build/webui-verification.json').read_text(encoding='utf-8'))
    assert ui['ok'] and len(ui['checks'])>=10
    for source,wanted in ui['source_sha256'].items(): assert sha((ROOT/source).read_bytes())==wanted,source
    passed('Browser layout/bridge/validation/failure checks passed')
    installation=json.loads((ROOT/'build/installer-verification.json').read_text(encoding='utf-8'))
    assert installation['ok'] and len(installation['checks'])>=15
    passed('Isolated installer choice, backup, rollback and modern source-entry contracts passed')
    result={'ok':True,'checks':checks,'module_sha256':sha(package.read_bytes()),'elf_sha256':metadata['sha256'],'android_device_verified':False,'upstream_source_comparison':bool(args.upstream),'full_original_elf_differential_rerun':False}
    (ROOT/'build/project-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'ok':True,'checks':len(checks),'module_bytes':package.stat().st_size,'module_sha256':result['module_sha256']},indent=2))
if __name__=='__main__': main()
