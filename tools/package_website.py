"""Package the complete assembled website, including module/source downloads."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[1];site=ROOT.parent/'web'
prop=dict(line.split('=',1) for line in (ROOT/'module/module.prop').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
required=['index.html','assets/site.js','assets/site.css','downloads/luma_curve-'+prop['version']+'.zip','downloads/LumaCurve-'+prop['version']+'-source.zip','downloads/SHA256SUMS.txt']
assert all((site/name).is_file() for name in required),'Run tools/sync_website.py first'
output=ROOT/'dist'/('LumaCurve-website-'+prop['version']+'.zip')
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for source in sorted(site.rglob('*')):
        if source.is_file():archive.write(source,source.relative_to(site).as_posix())
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert all(name in archive.namelist() for name in required)
    for name in required:assert archive.read(name)==(site/name).read_bytes()
print(json.dumps({'archive':str(output),'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'bytes':output.stat().st_size,'files_at_archive_root':True}))
