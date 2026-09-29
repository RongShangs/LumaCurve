"""Assemble a standalone static site and corresponding GPL source downloads."""
from pathlib import Path
import argparse, shutil, hashlib
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT.parent/'web');a=p.parse_args()
site=ROOT/'website'
for name,source in [('avatar.jpg','module/webroot/avatar.jpg'),('LICENSE.txt','LICENSE'),('curve_math.js','module/webroot/curve_math.js')]:
    shutil.copy2(ROOT/source,site/'assets'/name)
download=site/'downloads';download.mkdir(parents=True,exist_ok=True)
lines=[]
for name in ['luma_curve-1.0.0.zip','LumaCurve-1.0.0-source.zip']:
    source=ROOT/'dist'/name;shutil.copy2(source,download/name)
    lines.append(hashlib.sha256(source.read_bytes()).hexdigest()+'  '+name+'\n')
(download/'SHA256SUMS.txt').write_text(''.join(lines),encoding='utf-8',newline='\n')
for source in site.rglob('*'):
    if source.is_file():
        target=a.out/source.relative_to(site)
        if target.resolve()==source.resolve():continue
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
print('Ready-to-deploy website: '+str(site))
print('Static website assembled: '+str(a.out/'index.html'))
