"""Assemble HyperLux's static site with matching APK, source and recovery downloads."""
from pathlib import Path
import argparse,shutil,hashlib,json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT.parent/'web');a=p.parse_args()
site=ROOT/'website';assets=site/'assets'
thanks=json.loads((site/'thanks.json').read_text(encoding='utf-8'))
assert thanks['schema']==1 and isinstance(thanks['entries'],list) and len(thanks['entries'])<=200
for entry in thanks['entries']:
    assert isinstance(entry['name'],str) and entry['name'].strip() and len(entry['name'])<=80
    assert isinstance(entry['message'],str) and len(entry['message'])<=200
    assert '\n' not in entry['name']+entry['message'] and '\r' not in entry['name']+entry['message']
src=ROOT/'experimental/refactor_hook'
manifest=ET.parse(src/'AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
version=manifest.attrib[ns+'versionName'];code=int(manifest.attrib[ns+'versionCode'])
download=site/'downloads'
files=[f'HyperLux-{version}.apk',f'LumaCurve-{version}-source.zip',f'LumaCurve-{version}.zip']
checks=[]
for name in files:
    source=ROOT/'dist'/name
    if not source.is_file():raise FileNotFoundError('Build the APK first: '+str(source))
    checks.append(hashlib.sha256(source.read_bytes()).hexdigest()+'  '+name+'\n')
release_notes=ROOT/'docs/releases'/f'{version}.md'
notes=release_notes.read_text(encoding='utf-8').strip()
index=(site/'index.html').read_text(encoding='utf-8')
assert f'id="latest-version">{version}</span>' in index,'Update the website release heading before syncing'
assert all(f'downloads/{name}' in index for name in files),'Website download links must match this release'
assert f'{version} / {code}' in index,'Website version code mismatch'
metadata={'name':'HyperLux','version':version,'versionCode':code,'distribution':'apk','requires':['Root','LSPosed','HyperOS 4'],
          'apkUrl':f'https://lc.rongshangs.top/downloads/{files[0]}','sourceUrl':f'https://lc.rongshangs.top/downloads/{files[1]}',
          'bundleUrl':f'https://lc.rongshangs.top/downloads/{files[2]}','releaseUrl':f'https://github.com/RongShangs/LumaCurve/releases/tag/v{version}',
          'sha256':checks[0].split()[0],'changelog':notes}
# Validate every input before replacing any existing site files.
asset_names=['avatar.jpg','donate-wechat.jpg','donate-alipay.jpg']
for source in [ROOT/'LICENSE',*[src/'assets'/name for name in asset_names]]:
    if not source.is_file():raise FileNotFoundError(source)
assets.mkdir(parents=True,exist_ok=True);download.mkdir(parents=True,exist_ok=True)
(site/'thanks.js').write_text('window.HyperLuxThanks='+json.dumps(thanks,ensure_ascii=False)+';\n',encoding='utf-8',newline='\n')
for name in asset_names:shutil.copy2(src/'assets'/name,assets/name)
shutil.copy2(ROOT/'LICENSE',assets/'LICENSE.txt')
for name in files:shutil.copy2(ROOT/'dist'/name,download/name)
(download/'SHA256SUMS.txt').write_text(''.join(checks),encoding='utf-8',newline='\n')
(site/'update.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
(site/'update.js').write_text('window.HyperLuxRelease='+json.dumps(metadata,ensure_ascii=False)+';\n',encoding='utf-8',newline='\n')
for source in site.rglob('*'):
    if source.is_file():
        target=a.out/source.relative_to(site)
        if target.resolve()==source.resolve():continue
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
print('Complete static website: '+str(a.out/'index.html'))
print('APK, source, recovery bundle and SHA256 copied; no upload performed.')
