"""Private liuqin consumer/coordinate evidence; do not distribute input firmware."""
from pathlib import Path
import json,re,subprocess,sys,hashlib
R=Path(__file__).resolve().parents[1]
F=R.parent/'diagnostics/unpacked/liuqin_23046RP50C/LumaCurve-analysis-20261027-094708-dd9327/analysis'
D=F/'disassembly';count=0
def check(b):
 global count
 if not b:raise AssertionError('Physical calibration firmware '+str(count))
 count+=1
def read(n):return (D/(n+'.txt')).read_text('utf-8')
def body(s,n):return next(x for x in s.split('\nMETHOD ') if x.startswith(n+' '))
s=read('com.android.server.display.BrightnessMappingStrategy$PhysicalMappingStrategy')
for name,kind in [('mNits','[F'),('mBrightness','[F'),('mBrightnessRangeAdjustmentApplied','Z'),('mNitsToBrightnessSpline','Landroid/util/Spline;'),('mBrightnessToNitsSpline','Landroid/util/Spline;'),('mAdjustedNitsToBrightnessSpline','Landroid/util/Spline;'),('mBrightnessToAdjustedNitsSpline','Landroid/util/Spline;'),('mConfig','Landroid/hardware/display/BrightnessConfiguration;'),('mUserLux','F')]:check('FIELD '+name+' '+kind in s)
compute=body(s,'computeSpline');check(compute.index('->getCurve()')<compute.index('->mAdjustedNitsToBrightnessSpline')<compute.index('->getAdjustedCurve(')<compute.index('->mBrightnessToAdjustedNitsSpline',compute.index('->getAdjustedCurve('))<compute.index('->createSpline('))
for name,field in [('convertToBrightness','mNitsToBrightnessSpline'),('convertToNits','mBrightnessToNitsSpline')]:
 b=body(s,name);check('->'+field+' Landroid/util/Spline;' in b and '->interpolate(F)F' in b);check('iput' not in b and 'sput' not in b and '->set' not in b)
for name in ['android.util.Spline$LinearSpline','android.util.Spline$MonotoneCubicSpline']:
 spline=read(name);b=body(spline,'interpolate')
 for field in ['mX','mY']:check('FIELD '+field+' [F' in spline)
 # Both endpoint early-returns precede any interior interpolation.
 prefix=b[:b.index('000052')]
 check(prefix.count('->mX [F')>=3 and prefix.count('->mY [F')==2 and prefix.count('return v')>=3)
 check('iput' not in b and 'sput' not in b)
config=read('android.hardware.display.BrightnessConfiguration')
check('METHOD <init> ([F [F Ljava/util/Map; Ljava/util/Map; Ljava/lang/String; Z J F F)V' in config)
for n,k in [('mCorrectionsByPackageName','Ljava/util/Map;'),('mCorrectionsByCategory','Ljava/util/Map;'),('mDescription','Ljava/lang/String;'),('mShouldCollectColorSamples','Z'),('mShortTermModelTimeout','J'),('mShortTermModelLowerLuxMultiplier','F'),('mShortTermModelUpperLuxMultiplier','F')]:check('FIELD '+n+' '+k in config)
sample=json.loads((F/'physical-sample.json').read_text('utf-8'))
check(sample['archive_sha256']=='2686ddaf68bf448abbc2d1df0fd18f2f0ce101b9d1b6c297f303870378e8a301')
check(len(sample['lux'])==108 and sample['lux'][0]==0 and sample['lux'][-1]==100000)
check(sample['nit'][0]==4.2 and sample['nit'][-1]==1000 and sample['calibration_nits']==[1,450,550])
check(sum(v>550 for v in sample['nit'])==44)
state=json.loads((F.parent/'state.json').read_text('utf-8'));check(state['injection']['stage']=='curve_incompatible' and '曲线节点无效' in state['injection']['message'])
subprocess.run([sys.executable,str(R/'tests/refactor_physical_calibration.py'),'--sample',str(F/'physical-sample.json')],check=True)
print('Physical calibration firmware: '+str(count)+' checks PASS; actual OEM consumer and 108-node adapter replay, no device execution')
