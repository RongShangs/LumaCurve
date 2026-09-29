#!/usr/bin/env python3
"""Validate release identity and generate manager update metadata."""
import argparse, json, re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--tag');args=p.parse_args()
prop=dict(line.split('=',1) for line in (ROOT/'module/module.prop').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
version=prop['version'];code=int(prop['versionCode'],10)
assert prop['id']=='luma_curve' and re.fullmatch(r'\d+\.\d+\.\d+',version) and code>0
assert args.tag is None or args.tag=='v'+version,'Tag must match module version'
repo='https://github.com/RongShangs/LumaCurve'
raw='https://raw.githubusercontent.com/RongShangs/LumaCurve/main'
assert prop['updateJson']==raw+'/update.json'
info={'version':version,'versionCode':code,'zipUrl':f'{repo}/releases/download/v{version}/luma_curve-{version}.zip','changelog':raw+'/CHANGELOG.md'}
(ROOT/'update.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
print(json.dumps(info))
