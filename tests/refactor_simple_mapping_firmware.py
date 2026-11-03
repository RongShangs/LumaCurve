"""Private native Simple-mapper consumers; never package proprietary inputs."""
from pathlib import Path
import json,subprocess,sys
R=Path(__file__).resolve().parents[1]
D=R.parent/'diagnostics/unpacked/RedmiK50Gaming_21121210C/LumaCurve-analysis-20261103-123141-06d2c6/analysis'
if not (D/'simple-sample.json').is_file():
 print('Simple mapping firmware: 0 checks PASS; SKIP private fixture absent, no device verification')
 raise SystemExit()
cases=0
def check(b):
 global cases
 if not b:raise AssertionError('Simple firmware '+str(cases))
 cases+=1
def read(n):return (D/(n+'.txt')).read_text('utf-8')
def body(s,n):return next(v for v in s.split('\nMETHOD ') if v.startswith(n+' '))
s=read('BrightnessMappingStrategy$SimpleMappingStrategy')
for n,k in [('mLux','[F'),('mBrightness','[F'),('mUserLux','F'),('mUserBrightness','F'),('mAutoBrightnessAdjustment','F'),('mMaxGamma','F'),('mSpline','Landroid/util/Spline;')]:check('FIELD '+n+' '+k+'\n' in s)
check('FIELD mNits ' not in s)
for signature in ['getBrightness (F Ljava/lang/String; I)F','computeSpline ()V','getUnadjustedBrightness (F)F','setAutoBrightnessAdjustment (F)Z','addUserDataPoint (F F)V','getMode ()I','getUserBrightness ()F']:check('METHOD '+signature in s)
b=body(s,'computeSpline');check(b.index('->mLux [F')<b.index('->getAdjustedCurve(')<b.index('->createSpline(')<b.index('->mSpline Landroid/util/Spline;'))
b=body(s,'getUnadjustedBrightness');check('->mLux [F' in b and '->mBrightness [F' in b and '->createSpline(' in b and '->interpolate(F)F' in b)
b=body(s,'addUserDataPoint');check(b.index('->getUnadjustedBrightness')<b.index('->inferAutoBrightnessAdjustment')<b.index('->mUserBrightness F')<b.index('->computeSpline()V'))
for n in ['convertToBrightness','convertToNits']:check('const/high16 v0, -1082130432' in body(s,n))
check('const/4 v0, 0' in body(s,'setBrightnessConfiguration'))
base=read('BrightnessMappingStrategy');check('METHOD getAdjustedCurve ([F [F F F F F)Landroid/util/Pair; flags=protected' in base)
b=body(base,'getAdjustedCurve');check('->insertControlPoint(' in b and '->smoothNewCurveV2(' in body(base,'insertControlPoint') and '->copyOf(' in b)
b=body(base,'create');check('->getAutoBrightnessBrighteningLevelsLux(I I)[F' in b and '->getAutoBrightnessBrighteningLevels(I I)[F' in b and 'SimpleMappingStrategy;-><init>([F [F' in b)
abc=read('AutomaticBrightnessController')
for n,k in [('mCurrentBrightnessMapper','Lcom/android/server/display/BrightnessMappingStrategy;'),('mBrightnessMappingStrategyMap','Landroid/util/SparseArray;'),('mShortTermModel','Lcom/android/server/display/AutomaticBrightnessController$ShortTermModel;')]:check('FIELD '+n+' '+k in abc)
check('->clearUserDataPoints()V' in body(abc,'resetShortTermModel'))
check('METHOD setScreenBrightnessByUser (F F)Z' in abc)
for n in ['Spline$LinearSpline','Spline$MonotoneCubicSpline']:
 spline=read(n)
 for field in ['mX','mY']:check('FIELD '+field+' [F' in spline)
 check('METHOD interpolate (F)F' in spline)
check('METHOD createSpline ([F [F)Landroid/util/Spline; flags=public static' in read('Spline'))
# Optional newer inspection of the same native firmware, not a synthetic HBM layout.
if (D/'HighBrightnessModeController.txt').is_file():
 hbm=read('HighBrightnessModeController')
 for signature in ['deviceSupportsHbm ()Z','getNormalBrightnessMax ()F','getCurrentBrightnessMax ()F','hbmControllerEnabled ()Z']:check('METHOD '+signature in hbm)
 b=body(hbm,'deviceSupportsHbm');check('->mHbmData ' in b and '->mHighBrightnessModeMetadata ' in b and 'if-eqz' in b)
 b=body(hbm,'getNormalBrightnessMax');check(b.index('->hbmControllerEnabled()Z')<b.index('->transitionPoint F')<b.index('->mBrightnessMax F'))
 b=body(hbm,'getCurrentBrightnessMax');check('->hbmControllerEnabled()Z' in b and '->mBrightnessMax F' in b)
 b=body(hbm,'hbmControllerEnabled');check(b.index('->deviceSupportsHbm()Z')<b.index('->mHbmControllerIsEnabled Z'))
sample=json.loads((D/'simple-sample.json').read_text('utf-8'));check(sample['archive_sha256']=='b831e46eee2b426a6bfb8748fc7f68d59d065414f63365b96d879cd65ccd988c');check(len(sample['lux'])==len(sample['brightness'])==108);check(sample['coordinate']=='framework_brightness' and sample['lux'][0]==0 and sample['lux'][-1]==100000 and max(sample['brightness'])<1)
subprocess.run([sys.executable,str(R/'tests/refactor_simple_mapping.py'),'--sample',str(D/'simple-sample.json')],check=True)
recent=D.parent.parent/'LumaCurve-analysis-20261103-150237-0c1cf1/state.json'
if recent.is_file():subprocess.run([sys.executable,str(R/'tests/refactor_screen_brightness.py'),'--simple',str(recent)],check=True)
print(f'Simple mapping firmware: {cases} checks PASS; actual native consumers and 108-knot replay, no Android/ART verification')
