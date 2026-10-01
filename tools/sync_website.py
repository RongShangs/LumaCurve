"""Assemble HyperLux's static site with matching APK, source and recovery downloads."""
from pathlib import Path
import argparse,shutil,hashlib,json,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--out',type=Path,default=ROOT.parent/'web');a=p.parse_args()
site=ROOT/'website';assets=site/'assets';assets.mkdir(parents=True,exist_ok=True)
src=ROOT/'experimental/refactor_hook'
manifest=ET.parse(src/'AndroidManifest.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
version=manifest.attrib[ns+'versionName'];code=int(manifest.attrib[ns+'versionCode'])
for name in ['avatar.jpg','donate-wechat.jpg','donate-alipay.jpg']:
    shutil.copy2(src/'assets'/name,assets/name)
shutil.copy2(ROOT/'LICENSE',assets/'LICENSE.txt')
download=site/'downloads';download.mkdir(parents=True,exist_ok=True)
files=[f'HyperLux-{version}.apk',f'LumaCurve-{version}-source.zip',f'LumaCurve-{version}.zip']
checks=[]
for name in files:
    source=ROOT/'dist'/name
    if not source.is_file():raise FileNotFoundError('Build the APK first: '+str(source))
    shutil.copy2(source,download/name)
    checks.append(hashlib.sha256(source.read_bytes()).hexdigest()+'  '+name+'\n')
(download/'SHA256SUMS.txt').write_text(''.join(checks),encoding='utf-8',newline='\n')
notes='HyperLux 2.0.0：Root + LSPosed APP，系统曲线编辑与手动记忆、双参考场景控制、可选温控与变化确认时间。四页双语界面、旧模块与权限检测、分析包导出和自动更新。关于页按简介/交流群、打赏、感谢名单、作者排列。'
metadata={'name':'HyperLux','version':version,'versionCode':code,'distribution':'apk','requires':['Root','LSPosed','HyperOS 4'],
          'apkUrl':f'https://lc.rongshangs.top/downloads/{files[0]}','sourceUrl':f'https://lc.rongshangs.top/downloads/{files[1]}',
          'bundleUrl':f'https://lc.rongshangs.top/downloads/{files[2]}','releaseUrl':f'https://github.com/RongShangs/LumaCurve/releases/tag/v{version}',
          'sha256':checks[0].split()[0],'changelog':notes}
(site/'update.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
(site/'update.js').write_text('window.HyperLuxRelease='+json.dumps(metadata,ensure_ascii=False)+';\n',encoding='utf-8',newline='\n')
for source in site.rglob('*'):
    if source.is_file():
        target=a.out/source.relative_to(site)
        if target.resolve()==source.resolve():continue
        target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
print('Complete static website: '+str(a.out/'index.html'))
print('APK, source, recovery bundle and SHA256 copied; no upload performed.')
