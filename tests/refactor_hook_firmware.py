"""Assert the hook's targeted ABI and consumer semantics against supplied OEM DEX."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
collection=Path('D:/IOS/reverse-engineering/device-collections/official-test-20261001-100823/disassembly')
owner=(ROOT/'build/device-reports/framework02/disassembly/com.android.server.display.DisplayPowerControllerImpl.txt').read_text(encoding='utf-8')
ref=(collection/'com.android.server.display.RefactorNitController.txt').read_text(encoding='utf-8')
util=(collection/'com.android.server.display.RefactorBrightnessUtil.txt').read_text(encoding='utf-8')
abc=Path('D:/IOS/reverse-engineering/device-collections/official-curve-20261001-084148/curve-disassembly/com.android.server.display.AutomaticBrightnessController.txt').read_text(encoding='utf-8')
assert 'METHOD getRefactorBrightness (F F Z Z)F' in owner
assert 'METHOD updateAutoBrightness ()V' in owner
for name,kind in [('mContext','Landroid/content/Context;'),('mDisplayId','I'),('mRefactorNitController','Lcom/android/server/display/RefactorNitController;')]:assert f'FIELD {name} {kind}' in owner
assert 'FIELD mHandler Lcom/android/server/display/DisplayPowerControllerImpl$DisplayPowerControllerImplHandler;' in owner
for signature in ['defaultAtLux (F)F','resetDefaultSpline ()V','getCurrentNit (F F Z)F','updateLogicalCurve (F F)V','getDefaultLogicalBrt (F)D']:assert 'METHOD '+signature in ref
for name,kind in [('mDisplayId','I'),('mUserSerial','I'),('mDefaultLogicalCurve','[F')]:assert f'FIELD {name} {kind}' in ref
assert 'FIELD MIN_NIT F' in util and 'FIELD MAX_NIT F' in util
body=ref.split('METHOD getCurrentNit ')[1].split('\nMETHOD ')[0]
assert '->getLogicalBrt(F)D' in body and '->getFirstFactor(F)D' in body and '->getSecondFactor(F)D' in body
body=ref.split('METHOD updateLogicalCurve ')[1].split('\nMETHOD ')[0]
assert '->addDragOnArrays(' in body and '->saveLogicalNitToPreferences(F Z)V' in body
for method in ['defaultAtLux (F)F','resetDefaultSpline ()V']:
    body=ref.split('METHOD '+method)[1].split('\nMETHOD ')[0]
    for baseline in ['mDefNit0','mDefNit30','mDefNitMidHigh']:assert baseline in body
    assert '->MAX_NIT F' in body
body=abc.split('METHOD updateAutoBrightness (Z Z)V')[1].split('\nMETHOD ')[0]
assert body.index('->getBrightness(')<body.index('->getRefactorBrightness(')<body.index('->getOverrideLimitBrightness(')
assert '->getCustomBrightnessForRefactorPolicy(' in body
for signature in ['adjustBrightness (F Lcom/android/server/display/brightness/BrightnessReason;)F',
                  'adjustSdrBrightness (F Z Lcom/android/server/display/brightness/BrightnessReason; Z Z)F']:
    body=owner.split('METHOD '+signature)[1].split('\nMETHOD ')[0]
    assert '->adjustBrightnessByThermal(F Z Lcom/android/server/display/brightness/BrightnessReason;)F' in body
    assert '->adjustBrightnessByBattery(F Lcom/android/server/display/brightness/BrightnessReason;)F' in body
body=owner.split('METHOD adjustBrightnessByThermal ')[1].split('\nMETHOD ')[0]
assert '->mThermalMaxBrightness F' in body and '->isNaN(F)Z' in body
body=owner.split('METHOD getRefactorBrightness ')[1].split('\nMETHOD ')[0]
assert body.index('->updateLogicalCurve(F F)V')<body.index('->getCurrentNit(F F Z)F')
assert 'invoke-virtual v0, v5, v6, v8' in body  # Actual manual brightness uses the caller's requested nit separately.
dual=(ROOT/'build/device-reports/framework02/disassembly/com.android.server.display.DualSensorPolicy.txt').read_text(encoding='utf-8')
for name in ['mMainFastAmbientLux','mAssistFastAmbientLux','mAssistAmbientLuxValid','mUseLightSensorFlag']:assert 'FIELD '+name+' ' in dual
for name in ['mAmbientLux','mLastObservedLux','mAmbientBrighteningThreshold','mAmbientDarkeningThreshold']:assert 'FIELD '+name+' F' in abc
for name in ['mBrighteningLightDebounceConfig','mDarkeningLightDebounceConfig','mSmallBrighteningLightDebounceConfig']:assert 'FIELD '+name+' J' in abc
assert 'METHOD isInIdleMode ()Z' in abc
for signature in ['nextAmbientLightBrighteningTransition (J)J','nextAmbientLightBrighteningTransition (J F)J','nextAmbientLightDarkeningTransition (J)J']:
    body=abc.split('METHOD '+signature)[1].split('\nMETHOD ')[0]
    assert '->mAmbientLightRingBuffer' in body and '->getTime(I)J' in body
    assert ('->mBrighteningLightDebounceConfig J' if 'Brightening' in signature else '->mDarkeningLightDebounceConfig J') in body
    assert '->getDrivingStatus()Z' in body
    if '(J F)' not in signature:assert '->isInIdleMode()Z' in body
dark=abc.split('METHOD nextAmbientLightDarkeningTransition (J)J')[1].split('\nMETHOD ')[0]
assert '->mStepModeDarkenDebounceConfig J' in dark
small=abc.split('METHOD nextAmbientLightBrighteningTransition (J F)J')[1].split('\nMETHOD ')[0]
assert '->mSmallBrighteningLightDebounceConfig J' in small and '->mAmbientBrighteningSmallThreshold F' in small
impl=(ROOT/'build/device-reports/framework02/disassembly/com.android.server.display.AutomaticBrightnessControllerImpl.txt').read_text(encoding='utf-8')
assert 'METHOD getDrivingStatus ()Z' in impl
assert 'FIELD mAutomaticBrightnessController Lcom/android/server/display/AutomaticBrightnessController;' in impl
print('Refactor firmware: target ABI, actual consumer, OEM drag/coordinate/constraint and debounce chain PASS; static analysis only')

# The optional stability hook covers the distinct assist buffer, not only ABC.
ring=Path('D:/IOS/reverse-engineering/device-collections/dark-cycle-20261001-010613/official-sensor-disassembly/com.android.server.display.AmbientLightRingBuffer.txt').read_text(encoding='utf-8')
for signature in ['nextAmbientLightBrighteningTransition (J F)J','nextAmbientLightBrighteningTransition (J F F F)J','nextAmbientLightDarkeningTransition (J F)J']:
    body=ring.split('METHOD '+signature)[1].split('\nMETHOD ')[0]
    assert '->getTime(I)J' in body and 'add-long' in body
for name in ['mBrighteningLightDebounceConfig','mDarkeningLightDebounceConfig','mSmallBrighteningLightDebounceConfig']:
    assert 'FIELD '+name+' J' in ring
assert '->nextAmbientLightBrighteningTransition(J F F F)J' in dual and '->nextAmbientLightDarkeningTransition(J F)J' in dual
assert 'const-wide/16 v1, 5000' in dual and '->prune(J)V' in dual  # Guard's 4 s stays within retention.
assert 'FIELD mAmbientLuxValid Z' in abc and 'FIELD mFastAmbientLux F' in abc
assert 'METHOD isHdrScene ()Z' in owner
for signature in ['getCustomBrightnessForRefactorPolicy (F F Z)F','getOverrideLimitBrightness (F Z F)F']:
    assert 'METHOD '+signature in impl
dpc=(ROOT/'build/device-reports/framework02/disassembly/com.android.server.display.DisplayPowerController.txt').read_text(encoding='utf-8')
assert 'METHOD getLastSelectedStrategy ()Lcom/android/server/display/brightness/strategy/DisplayBrightnessStrategy;' in dpc
print('Stability/trace firmware: main and assist evidence deadlines, 5 s retention, HDR gate and read-only pipeline probes PASS; static analysis only')
