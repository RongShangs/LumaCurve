"""Check OEM control ordering and brightness units against local/private OS4 DEX.
Unknown firmware still must pass runtime ABI/bounds checks; no ROM hash whitelist.
"""
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
cases=0
def check(condition):
    global cases
    assert condition
    cases+=1
def method(text,name,signature=''):
    return text.split('METHOD '+name+' '+signature)[1].split('\nMETHOD ')[0]
for device in ['os41','os28','k90pm']:
    root=ROOT/'build/outdoor-research'/device
    def read(name):return (root/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
    hbm,owner,abc,impl,data=[read(name) for name in ['HighBrightnessModeController','DisplayPowerControllerImpl','AutomaticBrightnessController','AutomaticBrightnessControllerImpl','config.HighBrightnessModeData']]
    for name,kind in [('mBrightnessMax','F'),('mBrightnessMin','F'),('mAmbientLux','F'),('mBrightness','F'),('mHbmControllerIsEnabled','Z'),('mIsTimeAvailable','Z'),('mIsInAllowedAmbientRange','Z'),('mIsBlockedByLowPowerMode','Z'),('mIsAutoBrightnessEnabled','Z'),('mIsAutoBrightnessOffByState','Z'),('mIsHdrLayerPresent','Z'),('mDolbyEnable','Z')]:check('FIELD '+name+' '+kind in hbm)
    for sig in ['getCurrentBrightnessMax ()F','getNormalBrightnessMax ()F','isHbmCurrentlyAllowed ()Z','recalculateTimeAllowance ()V','onAmbientLuxChange (F)V','setAutoBrightnessEnabled (I)V']:check('METHOD '+sig in hbm)
    b=method(hbm,'isHbmCurrentlyAllowed');check(all('->'+name in b for name in ['hbmControllerEnabled','mIsTimeAvailable','mIsInAllowedAmbientRange','mIsBlockedByLowPowerMode']))
    b=method(hbm,'getCurrentBrightnessMax');check('->mBrightnessMax F' in b and '->transitionPoint F' in b and '->getMaxHbmBrightnessForPeak()F' in b)
    check('->minimumLux F' in method(hbm,'onAmbientLuxChange'))
    check('->timeMaxMillis J' in method(hbm,'calculateRemainingTime') and '->timeWindowMillis J' in method(hbm,'calculateRemainingTime'))
    b=method(hbm,'recalculateTimeAllowance');check('->timeMinMillis J' in b and '->postAtTime(Ljava/lang/Runnable; J)Z' in b)
    check('METHOD <init> (F F J J J Z F Landroid/util/Spline; Landroid/view/SurfaceControl$RefreshRateRange; Z)V' in data)
    for name,kind in [('minimumLux','F'),('transitionPoint','F'),('timeWindowMillis','J'),('timeMaxMillis','J'),('timeMinMillis','J')]:check('FIELD '+name+' '+kind in data)
    b=method(abc,'updateAutoBrightness','(Z Z)V');check(b.index('->getOverrideLimitBrightness(F Z F)F')<b.index('->clampScreenBrightness(F)F',b.index('->getOverrideLimitBrightness(F Z F)F')))
    check('METHOD getOverrideLimitBrightness (F Z F)F' in impl and '->mIsOverrideDragPolicy Z' in method(impl,'getOverrideLimitBrightness'))
    b=method(owner,'adjustSdrBrightness');check(b.index('->adjustBrightnessToPeak(')<b.index('->adjustBrightnessByThermal(')<b.index('->adjustBrightnessByBattery('))
    check('->getMaxHbmBrightnessForPeak()F' in method(owner,'adjustBrightnessToPeak'))
    check('METHOD getMaxHbmBrightnessForPeak ()F' in owner)
    for name in ['adjustBrightnessByOpr','adjustBrightnessByThermal','adjustBrightnessByBattery','adjustBrightnessByPowerSaveMode','adjustBrightnessToPeak','adjustBrightnessByBcbc']:check('METHOD '+name+' (F' in owner)
    check('->min(F F F)F' in method(owner,'adjustBrightnessByBattery'))
    check('->mCurrentGrayScale F' in method(owner,'adjustBrightnessByOpr'))
    for signature in ['adjustSdrBrightness (F Z Lcom/android/server/display/brightness/BrightnessReason; Z Z)F','adjustBrightnessByOpr (F Lcom/android/server/display/brightness/BrightnessReason;)F','shouldUsePrivacyOprBrightness ()Z','isHdrScene ()Z']:check('METHOD '+signature in owner)
    check('FIELD mAutoBrightnessEnable Z' in owner)
    reason=read('brightness.BrightnessReason');check('METHOD getModifier ()I' in reason);check('METHOD setModifier (I)V' in reason)
    b=method(owner,'adjustSdrBrightness');check(b.index('->adjustBrightnessByOpr(')<b.index('->adjustBrightnessToPeak(')<b.index('->adjustBrightnessByThermal(')<b.index('->adjustBrightnessByBattery('))
    check('const/16 v6, 8192' in method(owner,'adjustBrightnessByOpr'))
    range_text=read('BrightnessRangeController');check('->getCurrentBrightnessMax()F' in method(range_text,'getCurrentBrightnessMax'))
    dpc=read('DisplayPowerController');check('FIELD mBrightnessClamperController ' in dpc and 'FIELD mBrightnessRangeController ' in dpc)
    dynamic=read('BrightnessRangeControllerImpl');check('METHOD getCurrentBrightnessMax (Lcom/android/server/display/HighBrightnessModeController;)F' in dynamic)
    for name,kind in [('mSupportDynamicBrightnessRange','Z'),('mAutoBrightnessEnabled','Z'),('mMaxBrightness','F')]:check('FIELD '+name+' '+kind in dynamic)
    b=method(dynamic,'getCurrentBrightnessMax');check('->mMaxBrightness F' in b and '->getCurrentBrightnessMax()F' in b)
    b=method(dynamic,'recalculateMaxBrightness');check('->getBrightnessFromNit(F)F' in b and '->mDynamicMaxBrightnessSpline ' in b)
    clamp=read('brightness.clamper.BrightnessClamperController');check('METHOD getMaxBrightness ()F' in clamp)
print(f'Outdoor firmware: {cases} checks PASS on 3 private HyperOS 4 collections; no device test')
