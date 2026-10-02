"""Package the standalone HyperLux website, including local downloads."""
from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];site=ROOT.parent/'web'
manifest=ET.parse(ROOT/'experimental/refactor_hook/AndroidManifest.xml').getroot()
version=manifest.attrib['{http://schemas.android.com/apk/res/android}versionName']
required=['index.html','thanks.json','thanks.js','assets/site.js','assets/site.css','assets/donate-wechat.jpg','assets/donate-alipay.jpg',
          f'downloads/HyperLux-{version}.apk',f'downloads/LumaCurve-{version}-source.zip',f'downloads/LumaCurve-{version}.zip',
          'downloads/SHA256SUMS.txt','update.json','update.js']
assert all((site/name).is_file() for name in required),'Run tools/sync_website.py first'
output=ROOT/'dist'/f'LumaCurve-website-{version}.zip'
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for source in sorted(site.rglob('*')):
        if source.is_file():archive.write(source,source.relative_to(site).as_posix())
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    for name in required:assert archive.read(name)==(site/name).read_bytes()
print(json.dumps({'archive':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size,'files_at_archive_root':True}))
