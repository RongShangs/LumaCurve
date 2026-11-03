"""Verify the supplied old mapper ABI privately; firmware and disassembly are never packaged."""
from pathlib import Path
import argparse
R=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,default=R.parent/'diagnostics/unpacked/RedmiK60_23013RK75C/LumaCurve-analysis-20261103-215433-8b4142(1)/analysis');a=p.parse_args()
count=0
def check(ok):
    global count
    assert ok,count;count+=1
def read(n):return (a.fixtures/(n+'.txt')).read_text('utf-8')
def body(t,m):return t.split('METHOD '+m+' ',1)[1].split('\nMETHOD ',1)[0]
abc=read('AutomaticBrightnessController');owner=read('DisplayPowerControllerImpl');base=read('BrightnessMappingStrategy');config=read('BrightnessConfiguration')
for t in [abc,base]:check('METHOD getMode ' not in t)
check('FIELD mBrightnessMappingStrategyMap ' not in abc)
for n in ['mCurrentBrightnessMapper','mInteractiveModeBrightnessMapper','mIdleModeBrightnessMapper']:
    check('FIELD '+n+' Lcom/android/server/display/BrightnessMappingStrategy;' in abc)
check('FIELD mDisplayId I ' in owner);check('FIELD mBrightnessMapper Lcom/android/server/display/BrightnessMappingStrategy;' in owner)
setup=body(owner,'setUpAutoBrightness');check('->mBrightnessMapper ' in setup and '->setUpAutoBrightness(' in setup)
constructor=body(abc,'<init>')
init_bodies=abc.split('METHOD <init> ')[1:]
check(any('->mInteractiveModeBrightnessMapper ' in b.split('\nMETHOD ',1)[0] and '->mCurrentBrightnessMapper ' in b.split('\nMETHOD ',1)[0] for b in init_bodies))
for n in ['switchToIdleMode','switchToInteractiveScreenBrightnessMode']:
    b=body(abc,n);check('->isForIdleMode()Z' in b and '->mCurrentBrightnessMapper ' in b)
check('->mIdleModeBrightnessMapper ' in body(abc,'switchToIdleMode'))
check('->mInteractiveModeBrightnessMapper ' in body(abc,'switchToInteractiveScreenBrightnessMode'))
reset=body(abc,'resetShortTermModel');check('->mCurrentBrightnessMapper ' in reset and '->clearUserDataPoints()V' in reset and '->mShortTermModel ' in reset)
replay=abc.split('METHOD setScreenBrightnessByUser (F F)Z',1)[1].split('\nMETHOD ',1)[0]
check('->mCurrentBrightnessMapper ' in replay and '->addUserDataPoint(F F)V' in replay and '->mShortTermModel ' in replay)
for n in ['MiuiPhysicalBrightnessMappingStrategy','BrightnessMappingStrategy$PhysicalMappingStrategy']:
    t=read(n);check('METHOD getMode ' not in t)
    for sig in ['isForIdleMode ()Z','getUserBrightness ()F','setBrightnessConfiguration (Landroid/hardware/display/BrightnessConfiguration;)Z','getBrightness (F Ljava/lang/String; I)F','addUserDataPoint (F F)V','convertToBrightness (F)F','convertToNits (F)F','clearUserDataPoints ()V']:
        check('METHOD '+sig in t)
    for field,kind in [('mConfig','Landroid/hardware/display/BrightnessConfiguration;'),('mBrightnessSpline','Landroid/util/Spline;'),('mNits','[F')]:check('FIELD '+field+' '+kind in t)
    check('->mConfig ' in body(t,'setBrightnessConfiguration') and '->computeSpline()V' in body(t,'setBrightnessConfiguration'))
    check('->getCurve()Landroid/util/Pair;' in body(t,'computeSpline') and '->mBrightnessSpline ' in body(t,'computeSpline'))
miui=read('MiuiPhysicalBrightnessMappingStrategy');idle=body(miui,'isForIdleMode');check('const/4 v0, 0' in idle and 'return v0' in idle)
check('METHOD addUserDataPoint (F F Ljava/lang/String;)V' in miui)
check('FIELD mShortTermModelUserLux F ' in miui)
check('METHOD <init> ([F [F Ljava/util/Map; Ljava/util/Map; Ljava/lang/String; Z J F F)V' in config)
for n in ['mCorrectionsByPackageName','mCorrectionsByCategory','mDescription','mShouldCollectColorSamples','mShortTermModelTimeout','mShortTermModelLowerLuxMultiplier','mShortTermModelUpperLuxMultiplier']:check('FIELD '+n+' ' in config)
print(f'Mapper context firmware: {count} checks PASS; supplied ABI and consumers, device not tested')
