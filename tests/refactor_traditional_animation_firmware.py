"""Optional private K90 firmware check. Firmware and disassembly are never shipped."""
from pathlib import Path

R=Path(__file__).resolve().parents[1]
root=R/'build/beta-4.0.1-beta01'
cases=0
def check(value,message):
 global cases
 assert value,message
 cases+=1
for version in ['k90-original','k90-latest']:
 folder=root/version
 if not (folder/'com.android.server.display.RampAnimator.txt').is_file():
  raise FileNotFoundError('Private traditional animation fixture missing: '+version)
 def text(name):return (folder/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
 ramp=text('RampAnimator');dual=text('RampAnimator$DualRampAnimator');owner=text('DisplayPowerControllerImpl')
 check('METHOD setAnimationTarget (F F Z F)Z' in ramp,version+' target wrapper')
 check('METHOD setAnimationTarget (F F F F)Z' in ramp,version+' time-limited consumer')
 for field,typ in [('mCurrentValue','F'),('mRate','F'),('mFirstTime','Z'),('mTargetLinearValue','F')]:check('FIELD '+field+' '+typ in ramp,version+' '+field)
 check('METHOD animateTo (F F F Z F)Z' in dual,version+' dual dispatch')
 for name in ['mFirst','mSecond']:check('->'+name+' Lcom/android/server/display/RampAnimator;' in dual,version+' actual child dispatch')
 check('->setAnimationTarget(F F Z F)Z' in dual,version+' native target consumption')
 check('->updateRampRate(Ljava/lang/String; F F F)F' in ramp,version+' rate calculation precedes target')
 check('METHOD supportRefactorBrightnessPolicy ()Z' in owner,version+' native policy selector')
 check('->mRampRateController Lcom/android/server/display/RampRateController;' in owner,version+' native controller')
 check('->updateBrightnessRate(Ljava/lang/String; F F F)F' in owner,version+' native rate consumer')
 check('->mAnimationIncreaseMaxTimeSecs F' in ramp and '->mAnimationDecreaseMaxTimeSecs F' in ramp,version+' downstream caps intact')
 check('FIELD mBrightnessAnimator Lcom/android/server/display/RefactorAutoBrightnessAnimator;' in owner and 'FIELD mSdrBrightnessAnimator Lcom/android/server/display/RefactorAutoBrightnessAnimator;' in owner,version+' old slots preserved')
for version in ['k90-original','k90-latest']:
 rate=(root/version/'com.android.server.display.RampRateController.txt').read_text(encoding='utf-8')
 record=(root/version/'com.android.server.display.RampRateController$RateStateRecord.txt').read_text(encoding='utf-8')
 for name in ['REASON_AUTO_BRIGHTNESS_RATE','REASON_THERMAL_BRIGHTNESS_RATE','REASON_HDR_BRIGHTNESS_RATE','REASON_MANUAL_BRIGHTNESS_RATE','REASON_ZERO_RATE']:check('FIELD '+name+' I' in rate,name)
 check('FIELD mSlowChange Z' in rate,'native slow gate')
 check('FIELD mRateModifier I' in record,'native modifier gate')
 check('METHOD getCurrentRateReason (Ljava/lang/String;)I flags=private' in record,'native per-property reason')
fold=R/'build/fold3-f26a8d/disassembly'
if fold.is_dir():
 def text(name):return (fold/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
 ramp=text('RampAnimator');dual=text('RampAnimator$DualRampAnimator');owner=text('DisplayPowerControllerImpl');rate=text('RampRateController');record=text('RampRateController$RateStateRecord')
 check('METHOD setAnimationTarget (F F Z)Z' in ramp,'older three-argument wrapper')
 check('METHOD setAnimationTarget (F F Z F)Z' not in ramp,'preferred wrapper actually absent')
 check('METHOD setAnimationTarget (F F F F)Z' in ramp,'older downstream time-limit consumer')
 check('->setAnimationTarget(F F F F)Z' in ramp,'wrapper uses native time-limit consumer')
 check('METHOD animateTo (F F F Z)Z' in dual and '->setAnimationTarget(F F Z)Z' in dual,'actual older dual dispatch')
 check('->updateRampRate(Ljava/lang/String; F F F)F' in ramp,'actual older rate dispatch')
 for name in ['mFirst','mSecond']:check('->'+name+' Lcom/android/server/display/RampAnimator;' in dual,'same older children')
 for field,typ in [('mCurrentValue','F'),('mRate','F'),('mFirstTime','Z'),('mTargetLinearValue','F')]:check('FIELD '+field+' '+typ in ramp,'older '+field)
 check('METHOD supportRefactorBrightnessPolicy ()Z' in owner,'older native policy selector')
 for name in ['REASON_AUTO_BRIGHTNESS_RATE','REASON_THERMAL_BRIGHTNESS_RATE','REASON_HDR_BRIGHTNESS_RATE','REASON_MANUAL_BRIGHTNESS_RATE','REASON_ZERO_RATE']:check('FIELD '+name+' I' in rate,'older '+name)
 check('FIELD mSlowChange Z' in rate and 'FIELD mRateModifier I' in record and 'METHOD getCurrentRateReason (Ljava/lang/String;)I flags=private' in record,'older slow/reason/modifier guards')
 check('->mAnimationIncreaseMaxTimeSecs F' in ramp and '->mAnimationDecreaseMaxTimeSecs F' in ramp,'older downstream time limits retained')
print(f'Traditional animation firmware: {cases} checks PASS; private static evidence, no device test')
