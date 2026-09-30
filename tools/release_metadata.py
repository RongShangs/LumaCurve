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
site='https://lc.rongshangs.top'
assert prop['updateJson']==site+'/update.json'
info={'version':version,'versionCode':code,'zipUrl':f'{site}/downloads/luma_curve-{version}.zip','changelog':site+'/#download'}
payload=json.dumps(info,ensure_ascii=False,indent=2)+'\n'
(ROOT/'update.json').write_text(payload,encoding='utf-8')
(ROOT/'website/update.json').write_text(payload,encoding='utf-8')
(ROOT/'website/update.js').write_text('window.LumaCurveUpdate('+json.dumps(info,ensure_ascii=False,separators=(',',':'))+');\n',encoding='utf-8')
print(json.dumps(info))
