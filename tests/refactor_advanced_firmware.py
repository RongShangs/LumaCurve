"""Check optional Hook consumers against private OS4 disassembly; firmware is never bundled."""
from pathlib import Path
import argparse,re,struct
p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,default=Path(__file__).resolve().parents[1]/'build/research-203');a=p.parse_args()
names=['RefactorAutoBrightnessAnimator','HysteresisLevelsImpl','SunlightController','DualSensorPolicy','AutomaticBrightnessController','TouchCoverProtectionHelper']
cases=0
def check(value):
 global cases
 assert value
 cases+=1
def method(text,name,signature=''):return text.split('METHOD '+name+' '+signature)[1].split('\nMETHOD ')[0]
for variant in ['os41','os28']:
 root=a.fixtures/variant
 def read(name):return (root/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
 abc,ring,dual,hyst,animation,sun,owner=[read(n) for n in ['AutomaticBrightnessController','AmbientLightRingBuffer','DualSensorPolicy','HysteresisLevelsImpl','RefactorAutoBrightnessAnimator','SunlightController','DisplayPowerControllerImpl']]
 for sig in ['getBrighteningThreshold','getDarkeningThreshold','getBrighteningSmallThreshold']:
  check('METHOD '+sig+' (F)F' in hyst);check('->'+sig+'(F)F' in abc);check('->'+sig+'(F)F' in dual)
 for sig in ['setAmbientLuxWhenInvalid','updateDualSensorPolicy','updateSingleSensorPolicy']:check('METHOD '+sig+' ' in dual)
 check('METHOD setAmbientLux (F)V' in abc and 'METHOD setAmbientLux (I F Z Z)V' in abc)
 check('->prune(J)V' in dual and 'const-wide/16 v3, 5000' in method(dual,'updateAssistLightSensorAmbientLux'))
 check('->mSmallBrighteningLightDebounceConfig J' in method(ring,'nextAmbientLightBrighteningTransition','(J F F F)J'))
 check('->mRingTime [J' in method(ring,'prune') and 'aput-wide' in method(ring,'prune'))
 for field in ['mDuration','mDurationTime','mLogicalStart','mLogicalTarget']:check('FIELD '+field+' D' in animation)
 body=method(animation,'updatePerceptualDuration');check('->mDuration D' in body and '->mDurationTime D' in body and '->mStartDurationTime J' in body)
 body=method(animation,'getCurrentLogical');check('->mDuration D' in body and 'div-double' in body and '->perceptualToLogicalBrt(D)D' in body)
 body=method(animation,'getDuration');check('->mDurationTime D' in body and '4652007308841189376' in body)
 check(struct.unpack('<d',struct.pack('<Q',4652007308841189376))[0]==1000)
 check('->updateLux(F F F)V' in method(owner,'updateRampAnimatorLux'))
 for field in ['mBrightnessAnimator','mSdrBrightnessAnimator']:check('FIELD '+field+' Lcom/android/server/display/RefactorAutoBrightnessAnimator;' in owner)
 for name,base in [('nextEnterSunlightModeTransition',5000),('nextExitSunlightModeTransition',2000)]:
  body=method(sun,name);check('->getTime(I)J' in body and 'const-wide/16 v3, '+str(base) in body and 'add-long' in body)
 check('const-wide/16 v3, 10000' in method(sun,'updateAmbientLux') and '->prune(J)V' in method(sun,'updateAmbientLux'))
 check('->mBelowThresholdNit Z' in sun and '->mAutoBrightnessSettingsEnable Z' in sun)
 dark=method(abc,'nextAmbientLightDarkeningTransition');check('->mStepModeDarkenDebounceConfig J' in dark and 'add-long' in dark)
 touch=read('TouchCoverProtectionHelper');impl=read('AutomaticBrightnessControllerImpl')
 check('FIELD mTouchEventDebounce I' in touch and 'METHOD isTouchCoverProtectionActive ()Z' in touch)
 body=method(touch,'isTouchCoverProtectionActive');check('->isTouchingInArea()Z' in body and '->mTouchEventDebounce I' in body and 'mul-int/lit16 v0, v0, 1000' in body)
 check('->isTouchCoverProtectionActive()Z' in method(impl,'dropAmbientLuxIfNeeded'))
 check('180000' in method(touch,'isGameSceneWithinTouchTime'))
for name in names:
 texts=[(a.fixtures/v/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8') for v in ['os41','os28']]
 check(re.sub(r'0x[0-9A-Fa-f]+','ADDR',texts[0])==re.sub(r'0x[0-9A-Fa-f]+','ADDR',texts[1]))
print(f'Advanced firmware: {cases} checks PASS on 2 private OS4 collections; consumer semantics/units verified, not a device test')
