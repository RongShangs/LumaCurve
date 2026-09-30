"""Check that the formal framework package, installer, and static update feed agree."""
from pathlib import Path
import hashlib
import json
import re
import zipfile

root = Path(__file__).resolve().parents[1]
module = root / 'dist/luma_curve-1.0.0.zip'
source = root / 'dist/LumaCurve-1.0.0-source.zip'
site = root / 'website'
web = root.parent / 'web'
required = {
    'framework-broker.jar', 'framework_daemon_launcher.sh',
    'current_boot_log.sh', 'migrate_log_retention.sh',
    'webroot/curve_math.js', 'webroot/app.js', 'webroot/index.html',
    'system/bin/luma_curve_daemon', 'LICENSE', 'build-info.json',
}

with zipfile.ZipFile(module) as archive:
    assert archive.testzip() is None
    names = set(archive.namelist())
    assert required <= names, sorted(required - names)
    assert archive.read('update-binary') == archive.read('META-INF/com/google/android/update-binary')
    script = archive.read('update-binary').decode('utf-8')
    for listing in re.findall(r'for _file in (.*?); do', script):
        prefix = 'webroot/' if 'index.html' in listing else ''
        assert all(prefix + name in names for name in listing.split())
    for entry in required - {'build-info.json'}:
        if entry.endswith('.sh'):
            assert (archive.getinfo(entry).external_attr >> 16) & 0o777 == 0o755
    prop = archive.read('module.prop').decode('utf-8')
    assert 'version=1.0.0\nversionCode=10000\n' in prop
    assert 'updateJson=https://lc.rongshangs.top/update.json' in prop
    assert b'20260930-framework-hold03' in archive.read('system/bin/luma_curve_daemon')
    assert archive.read('webroot/curve_math.js') == (root / 'module/webroot/curve_math.js').read_bytes()
    info = json.loads(archive.read('build-info.json'))
    assert info['build'] == '20260930-framework-hold03-ui04' and not info['local_only']

with zipfile.ZipFile(source) as archive:
    assert archive.testzip() is None
    assert {'csrc/curve_pct_for_lux.c', 'experimental/framework_output/LumaFrameworkOutputBroker.java',
            'module/webroot/app.js', 'LICENSE', 'README.md'} <= set(archive.namelist())

metadata = json.loads((site / 'update.json').read_text(encoding='utf-8'))
assert metadata['versionCode'] == 10000
assert metadata['zipUrl'] == 'https://lc.rongshangs.top/downloads/luma_curve-1.0.0.zip'
assert (site / 'update.js').read_text(encoding='utf-8').strip() == (
    'window.LumaCurveUpdate(' + json.dumps(metadata, ensure_ascii=False, separators=(',', ':')) + ');')
for folder in (site, web):
    assert (folder / 'update.json').read_bytes() == (site / 'update.json').read_bytes()
    assert (folder / 'update.js').read_bytes() == (site / 'update.js').read_bytes()
    for archive in (module, source):
        assert (folder / 'downloads' / archive.name).read_bytes() == archive.read_bytes()
    sums = (folder / 'downloads/SHA256SUMS.txt').read_text(encoding='utf-8')
    for archive in (module, source):
        assert hashlib.sha256(archive.read_bytes()).hexdigest() + '  ' + archive.name in sums

print('formal package, full source, installer inventory, static downloads and update endpoints: PASS')
