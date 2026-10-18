"""Validate both SDR layouts against locally supplied OEM bytecode/dump evidence.
Only paths, checks and counts are shared; firmware and disassembly remain private.
"""
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[1]
cases=0
def check(value):
    global cases
    assert value
    cases+=1
def method(text,name):return next(p for p in text.split('\nMETHOD ') if p.startswith(name+' '))
for bundle,profile in [('analysis-20261013-110952-ba70da','legacy_opr'),('analysis-20261013-090550-ce9bbe','privacy_aware')]:
    text=(ROOT/'build'/bundle/'disassembly/com.android.server.display.DisplayPowerControllerImpl.txt').read_text(encoding='utf-8')
    for sig in ['adjustSdrBrightness (F Z Lcom/android/server/display/brightness/BrightnessReason; Z Z)F','adjustBrightnessByOpr (F Lcom/android/server/display/brightness/BrightnessReason;)F','isHdrScene ()Z','convertBrightnessToNit (F)F']:
        check('METHOD '+sig in text)
    check('FIELD mAutoBrightnessEnable Z' in text)
    sdr=method(text,'adjustSdrBrightness');opr=method(text,'adjustBrightnessByOpr')
    check(sdr.index('->isHdrScene()Z')<sdr.index('->adjustBrightnessByOpr('))
    check(sdr.index('->adjustBrightnessByOpr(')<sdr.index('->adjustBrightnessToPeak(')<sdr.index('->adjustBrightnessByThermal(')<sdr.index('->adjustBrightnessByBattery('))
    check(bool(re.search(r'const/16 v\d+, 8192',opr)))
    check('->mCurrentGrayScale F' in opr or '->getCurrentPrivacyGrayScaleForOpr()F' in opr)
    check('->getNitsFromBacklight(F)F' in method(text,'convertBrightnessToNit'))
    if profile=='privacy_aware':
        check('METHOD shouldUsePrivacyOprBrightness ()Z' in text)
        check('->shouldUsePrivacyOprBrightness()Z' in opr)
        check('->isPrivacyUhbmControlEnabled()Z' in method(text,'shouldUsePrivacyOprBrightness'))
    else:
        check('privacy' not in text.lower())
        check('uhbm' not in text.lower())
        for sig in ['getRestrictedOprBrightness (F)F','resetScreenGrayscaleState ()V','isGrayScaleLegal (F)Z']:check('METHOD '+sig in text)
        for field,kind in [('mOprBrightnessControlAvailable','Z'),('mCurrentGrayScale','F'),('mOprGrayscaleThreshold','[I'),('mOprNitThreshold','[I'),('mOprAmbientLuxThreshold','I'),('mOprMaxNitThreshold','I'),('mPendingResetGrayscaleStateForOpr','Z'),('mAppliedMaxOprBrightness','F'),('mDisplayDeviceConfig','Lcom/android/server/display/DisplayDeviceConfig;')]:
            check('FIELD '+field+' '+kind in text)
        restricted=method(text,'getRestrictedOprBrightness')
        check('->isGrayScaleLegal(F)Z' in restricted)
        check('->getBrightnessFromNit(F)F' in restricted)
        check('->mOprGrayscaleThreshold [I' in restricted and '->mOprNitThreshold [I' in restricted)
        # N cut points and N+1 outputs match the real gray=232 / 1060-nit case.
        dump=(ROOT/'build'/bundle/'display.txt').read_text(encoding='utf-8',errors='replace')
        levels=[int(v.strip()) for v in re.search(r'mOprGrayscaleThreshold=\[([^]]+)\]',dump).group(1).split(',')]
        nits=[int(v.strip()) for v in re.search(r'mOprNitThreshold=\[([^]]+)\]',dump).group(1).split(',')]
        check(len(nits)==len(levels)+1)
        check(all(a<b for a,b in zip(levels,levels[1:])))
        check(all(a>=b>0 for a,b in zip(nits,nits[1:])))
# Optional evidence for the added native ambient-only HBM path; private input stays local.
latest=ROOT/'build/compat-20261018-8ba0bd'
if (latest/'state.json').exists():
    import json
    runtime=json.loads((latest/'state.json').read_bytes())['runtime'];outdoor=runtime['outdoor']
    check(all(outdoor[k]==0 for k in ['original_time_window_ms','original_time_max_ms','original_time_min_ms']))
    check(outdoor['original_minimum_lux']==4475 and outdoor['mIsTimeAvailable'] is True)
    check(runtime['system_scene']['proximity_policy_enabled'] is False)
    hbm=(latest/'disassembly/com.android.server.display.HighBrightnessModeController.txt').read_text(encoding='utf-8')
    check('->minimumLux F' in method(hbm,'onAmbientLuxChange'))
    timer=method(hbm,'recalculateTimeAllowance')
    check('->timeMinMillis J' in timer and '->mIsTimeAvailable Z' in timer and 'if-ltz' in timer)
    impl=(latest/'disassembly/com.android.server.display.AutomaticBrightnessControllerImpl.txt').read_text(encoding='utf-8')
    drop=method(impl,'dropAmbientLuxIfNeeded');check(drop.index('->isTouchCoverProtectionActive()Z')<drop.index('->mUseProximityEnabled Z'))
    check('->mProximityPositive Z' in drop)
    init=method(impl,'initialize');check(init.index('->mUseProximityEnabled Z')<init.index('->getDefaultSensor(I)'))
print(f'Outdoor compatibility firmware: {cases} checks PASS on private actual user firmware collections; device not tested')
