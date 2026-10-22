"""Private supplied firmware and captured output; never distributed with source."""
from pathlib import Path
import json,re

R=Path(__file__).resolve().parents[1]
D=R.parent/'diagnostics/unpacked/RedmiK100ProMax_用户确认/LumaCurve-analysis-20261022-203000-cdfc4a'
A=R/'build/k100pm-cdfc4a/disassembly/com.android.server.display.DisplayPowerControllerImpl.txt'
cases=0
def check(ok):
 global cases
 assert ok,f'check {cases}'
 cases+=1
runtime=json.loads((D/'state.json').read_text(encoding='utf-8'))['runtime']
config=json.loads((D/'config.json').read_text(encoding='utf-8'))
display=(D/'display.txt').read_text(encoding='utf-8')
code=A.read_text(encoding='utf-8')
def method(name):
 return next(x for x in code.split('\nMETHOD ') if x.startswith(name+' '))
opr=method('getRestrictedOprBrightness')
for text in ['mOprGrayscaleThreshold [I','mOprNitThreshold [I','getBrightnessFromNit(F)F','aget']:
 check(text in opr)
sdr=method('adjustSdrBrightness')
check(sdr.index('adjustBrightnessByOpr')<sdr.index('adjustBrightnessByBcbc')<sdr.index('adjustBrightnessToPeak')<sdr.index('adjustBrightnessByThermal')<sdr.index('adjustBrightnessByBattery'))
convert=method('convertBrightnessToNit');check('getNitsFromBacklight(F)F' in convert)
table=json.loads(re.search(r'mOprNitThreshold=(\[[^\]]+\])',display).group(1))
check(table[0]==4500 and table[-1]==1200 and all(table[i]>=table[i+1] for i in range(len(table)-1)))
check('mNits=[1.0, 2.0, 5.0, 10.0, 60.0, 850.0, 1060.0, 2200.0, 4500.0, 10000.0]' in display)
check('0.9766207' in display and '10000.0' in display)
check(config['outdoor_opr_relax'] and config['outdoor_range_unlock'])
check(not config['outdoor_node_boost'])
check(runtime['outdoor']['mapping_max_nit']==10000)
traces=runtime['outdoor']['limit_trace'];check(any(x['stage']=='outdoor_opr_native' and x['before']==1 and x['after']<.6 for x in traces))
check(any(x['stage']=='outdoor_opr_relax' and x['after']==1 for x in traces))
check('brt=1.0(100.0%), nits=10000.0' in display)
check(not runtime['outdoor_node_guard']['running'])
for field in ['mOprBrightnessControlAvailable Z','mOprGrayscaleThreshold [I','mOprNitThreshold [I']:
 check('FIELD '+field in code)
print(f'OPR envelope firmware: {cases} checks PASS; static/captured evidence, color change not device-verified')
