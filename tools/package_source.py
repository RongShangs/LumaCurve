#!/usr/bin/env python3
"""Package the standalone recovery source, excluding build artifacts."""
import zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
properties=dict(line.split('=',1) for line in (ROOT/'module/module.prop').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
archive=ROOT/f"dist/LumaCurve-{properties['version']}-source.zip";archive.parent.mkdir(exist_ok=True)
excluded={'build','dist','__pycache__','.git'}
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for path in sorted(ROOT.rglob('*')):
        relative=path.relative_to(ROOT)
        if not path.is_file() or any(p in excluded for p in relative.parts) or path.suffix=='.pyc':continue
        z.write(path,relative.as_posix())
print(archive)
