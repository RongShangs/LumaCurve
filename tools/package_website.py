"""Package the standalone HyperLux website, including local downloads."""
from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];site=ROOT.parent/'web'
manifest=ET.parse(ROOT/'experimental/refactor_hook/AndroidManifest.xml').getroot()
version=manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
code=int(manifest.attrib['{http://schemas.android.com/apk/res/android}versionCode'])
required=['index.html','thanks.json','thanks.js','assets/site.js','assets/site.css','assets/donate-wechat.jpg','assets/donate-alipay.jpg',
          f'downloads/HyperLux-{version}.apk',f'downloads/LumaCurve-{version}-source.zip',f'downloads/LumaCurve-{version}.zip',
          'downloads/SHA256SUMS.txt','update.json','update.js']
assert all((site/name).is_file() for name in required),'Run tools/sync_website.py first'
metadata=json.loads((site/'update.json').read_text(encoding='utf-8'))
assert metadata['version']==version and metadata['versionCode']==code,'Website update metadata mismatch'
assert (site/'update.js').read_text(encoding='utf-8')=='window.HyperLuxRelease='+json.dumps(metadata,ensure_ascii=False)+';\n','Website update script mismatch'
index=(site/'index.html').read_text(encoding='utf-8')
assert f'id="latest-version">{version}</span>' in index and f'{version} / {code}' in index,'Website release heading mismatch'
downloads=[f'HyperLux-{version}.apk',f'LumaCurve-{version}-source.zip',f'LumaCurve-{version}.zip']
checks=[]
for name in downloads:
    assert f'downloads/{name}' in index,'Website download link mismatch'
    payload=(site/'downloads'/name).read_bytes()
    assert payload==(ROOT/'dist'/name).read_bytes(),'Website download differs from built release: '+name
    checks.append(hashlib.sha256(payload).hexdigest()+'  '+name+'\n')
assert (site/'downloads/SHA256SUMS.txt').read_text(encoding='utf-8')==''.join(checks),'Website checksum list mismatch'
assert metadata['sha256']==checks[0].split()[0],'Website APK checksum mismatch'
for key,name in zip(['apkUrl','sourceUrl','bundleUrl'],downloads):
    assert metadata[key]==f'https://lc.rongshangs.top/downloads/{name}','Website update URL mismatch'
output=ROOT/'dist'/f'LumaCurve-website-{version}.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for source in sorted(site.rglob('*')):
        if source.is_file():archive.write(source,source.relative_to(site).as_posix())
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    for name in required:assert archive.read(name)==(site/name).read_bytes()
print(json.dumps({'archive':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size,'files_at_archive_root':True}))
