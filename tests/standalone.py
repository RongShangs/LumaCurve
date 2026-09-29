#!/usr/bin/env python3
"""Extract the delivered source ZIP and build without analysis dependencies."""
import argparse, hashlib, json, os, subprocess, sys, tempfile, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--ndk',required=True,type=Path);p.add_argument('--cmake',type=Path);p.add_argument('--ninja',type=Path);a=p.parse_args()
prop=dict(line.split('=',1) for line in (ROOT/'module/module.prop').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
archive=ROOT/f"dist/LumaCurve-{prop['version']}-source.zip"
env={k:v for k,v in os.environ.items() if not k.upper().startswith(('PYTHON','IOS_','LUMA_'))}
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
with tempfile.TemporaryDirectory(prefix='standalone-',dir=ROOT/'build') as temp:
    directory=Path(temp);source=directory/'source'
    with zipfile.ZipFile(archive) as z: z.extractall(source)
    subprocess.run([sys.executable,'tools/build.py','--ndk',str(a.ndk),'--package'],cwd=source,env=env,check=True)
    assert sha(source/'build/highlevel/luma_curve_daemon')==sha(ROOT/'build/highlevel/luma_curve_daemon')
    if a.cmake:
        subprocess.run([str(a.cmake),'-S',str(source),'-B',str(directory/'cmake'),'-G','Ninja',f'-DCMAKE_MAKE_PROGRAM={a.ninja}',f'-DCMAKE_TOOLCHAIN_FILE={a.ndk}/build/cmake/android.toolchain.cmake','-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-26','-DCMAKE_BUILD_TYPE=Release'],env=env,check=True,stdout=subprocess.DEVNULL)
        subprocess.run([str(a.cmake),'--build',str(directory/'cmake')],env=env,check=True,stdout=subprocess.DEVNULL)
result={'ok':True,'source_archive_sha256':sha(archive),'python_build_identical_elf':True,'cmake_build_passed':bool(a.cmake),'analysis_dependencies_used':False}
(ROOT/'build/standalone-verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
