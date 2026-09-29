"""Build an isolated framework candidate; never overwrite the maintained module."""
from pathlib import Path
import hashlib,json,subprocess
from source_lists import sources_for
ROOT=Path(__file__).resolve().parents[1]
ndk=Path('D:/App/SDK/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64')
out=ROOT/'build/framework-core-test04';out.mkdir(parents=True,exist_ok=True)
sources=sources_for()+[ROOT/'experimental/framework_output/framework_backend.c']
target=out/'luma_framework_core'
command=[str(ndk/'bin/clang.exe'),'--target=aarch64-linux-android26',f'--sysroot={ndk/"sysroot"}',
 '-O2','-g','-fPIE','-pie','-ffp-contract=off','-fno-strict-aliasing','-std=c11','-D_DEFAULT_SOURCE',
 '-Wall','-Wextra','-Werror','-Wno-unused-label','-DIOS_PRODUCTION','-DIOS_BUSINESS_MAIN','-DLUMA_FRAMEWORK_BACKEND',
 '-I'+str(ROOT/'csrc')]+[str(s) for s in sources]+['-lm','-ldl','-Wl,--no-relax','-Wl,-z,max-page-size=16384',
 '-Wl,-z,relro','-Wl,-z,now','-o',str(target)]
subprocess.run(command,check=True)
inputs=sources+sorted((ROOT/'csrc').glob('*.h'))+sorted((ROOT/'experimental/framework_output').glob('*.h'))
(out/'build.json').write_text(json.dumps({'build':'20260930-framework-core-test04','android_verified':False,
 'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'source_sha256':{f.relative_to(ROOT).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in inputs}},indent=2)+'\n')
print(target)
