#!/usr/bin/env python3
"""Build a local installable HyperOS 4 module; never publish or replace formal dist."""
from pathlib import Path
import hashlib,json,subprocess,shutil,tempfile,zipfile,struct,re
from source_lists import sources_for

ROOT=Path(__file__).resolve().parents[1]
NDK=Path('D:/App/SDK/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64')
OUT=ROOT/'build/framework-module-local';OUT.mkdir(parents=True,exist_ok=True)
JAVA=ROOT/'build/framework-probe/LumaFrameworkProbe.jar'
proof=json.loads((ROOT/'build/framework-probe/verification.json').read_text(encoding='utf-8'))
assert proof['ok'] and proof['output_session_host_cases']==17 and proof['cadence_host_cases']==11 and proof['legacy_coordinate_cases']==13 and proof['physical_feedback_cases']==10 and proof['slider_override_cases']==11 and proof['frame_liveness_cases']==5
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert sha(JAVA)==proof['jar_sha256']
for source,digest in proof['source_sha256'].items():assert sha(ROOT/source)==digest,source
sources=sources_for()+[ROOT/'experimental/framework_output/framework_backend.c']
elf=OUT/'luma_curve_daemon'
command=[str(NDK/'bin/clang.exe'),'--target=aarch64-linux-android26',f'--sysroot={NDK/"sysroot"}',
 '-O2','-g',f'-ffile-prefix-map={ROOT}=.','-fPIE','-pie','-ffp-contract=off','-fno-strict-aliasing',
 '-std=c11','-D_DEFAULT_SOURCE','-Wall','-Wextra','-Werror','-Wno-unused-label',
 '-DIOS_PRODUCTION','-DIOS_BUSINESS_MAIN','-DLUMA_FRAMEWORK_BACKEND','-DLUMA_FRAMEWORK_PRODUCTION_BUILD',
 '-I'+str(ROOT/'csrc'),'-I'+str(ROOT/'experimental/framework_output')]+[str(s) for s in sources]+[
 '-lm','-ldl','-Wl,--no-relax','-Wl,-z,max-page-size=16384','-Wl,-z,relro','-Wl,-z,now','-o',str(elf)]
subprocess.run(command,check=True)
stage=Path(tempfile.mkdtemp(prefix='module-stage-',dir=OUT))
shutil.copytree(ROOT/'module',stage,dirs_exist_ok=True)
schema=re.search(r'^config_version=(\d+)$',(stage/'luma_curve.conf').read_text(encoding='utf-8'),re.M)
web_schema=re.search(r"var CONFIG_VERSION = '(\d+)';",(stage/'webroot/app.js').read_text(encoding='utf-8'))
assert schema and web_schema and schema.group(1)==web_schema.group(1),'WebUI configuration schema mismatch'
properties=stage/'module.prop'
properties.write_text(''.join(line for line in properties.read_text(encoding='utf-8').splitlines(keepends=True)
                              if not line.startswith('updateJson=')),encoding='utf-8')
binary=stage/'system/bin/luma_curve_daemon';binary.parent.mkdir(parents=True,exist_ok=True)
shutil.copy2(elf,binary)
subprocess.run([str(NDK/'bin/llvm-strip.exe'),'--strip-debug',str(binary)],check=True)
shutil.copy2(JAVA,stage/'framework-broker.jar')
corresponding=(sources+sorted((ROOT/'csrc').glob('*.h'))+
    sorted((ROOT/'experimental/framework_output').glob('*.java'))+
    sorted((ROOT/'experimental/framework_output').glob('*.h'))+
    sorted((ROOT/'tools/framework_probe').glob('*.java'))+
    [ROOT/'tools/source_lists.py',ROOT/'tools/build_framework_probe.py',ROOT/'tools/build_framework_module.py'])
for source in corresponding:
 destination=stage/'source'/source.relative_to(ROOT)
 destination.parent.mkdir(parents=True,exist_ok=True)
 shutil.copy2(source,destination)
info={'local_only':True,'published':False,'build':'20260930-framework-hold01-ui01',
      'firmware_framework_sha256':'1d2bf53f6c2684103dadbeef0d2665a7f033b7746a75f3e7404145dd999600fd',
      'firmware_services_sha256':'ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20',
      'panel_unique_id':'local:4630946949513469331','panel_codes_per_framework_float':17848,
      'coordinate_evidence':'framework-core-test02/03/04 settled normal-range node / adjustedBrightness',
      'full_range_device_verified':False,'hbm_device_verified':False,
      'daemon_sha256':sha(binary),'jar_sha256':sha(stage/'framework-broker.jar'),
      'source_sha256':{p.relative_to(ROOT).as_posix():sha(p) for p in sources+sorted((ROOT/'csrc').glob('*.h'))}}
(stage/'local-build-info.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
target=ROOT/'dist/luma_curve-1.0.0-local-framework-hold01-ui01.zip';target.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as archive:
 for source in sorted(stage.rglob('*')):
  if not source.is_file():continue
  name=source.relative_to(stage).as_posix()
  data=source.read_bytes()
  if name.endswith('.sh'):assert b'\r' not in data,name
  entry=zipfile.ZipInfo(name);entry.compress_type=zipfile.ZIP_DEFLATED;entry.create_system=3
  entry.external_attr=(0o100000|(0o755 if name.endswith('.sh') or name.endswith('luma_curve_daemon') or name.endswith('update-binary') else 0o644))<<16
  archive.writestr(entry,data)
with zipfile.ZipFile(target) as archive:
 assert archive.testzip() is None and archive.read('framework-broker.jar')==JAVA.read_bytes()
 assert archive.read('system/bin/luma_curve_daemon')==binary.read_bytes()
 assert archive.read('module.prop').decode('utf-8').find('version=1.0.0')>=0
 assert b'updateJson=' not in archive.read('module.prop')
 assert all(name in archive.namelist() for name in ('LICENSE','customize.sh','service.sh','current_boot_log.sh','webroot/index.html',
    'META-INF/com/google/android/update-binary','source/csrc/main_business.c',
    'source/experimental/framework_output/LumaLegacyBacklightCoordinate.java'))
 image=archive.read('system/bin/luma_curve_daemon')
 assert image[:6]==b'\x7fELF\x02\x01' and struct.unpack_from('<HH',image,16)==(3,183)
 assert b'20260930-framework-hold01' in image
 with zipfile.ZipFile(stage/'framework-broker.jar') as dex:
  assert dex.testzip() is None and 'classes.dex' in dex.namelist()
print(target);print('SHA256',sha(target))
