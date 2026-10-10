"""Check the explanatory claims against supplied private OS4 code, never bundled."""
from pathlib import Path
R=Path(__file__).resolve().parents[1];checks=0
def check(value):
 global checks
 assert value
 checks+=1
def method(source,name):return source.split('METHOD '+name+' ',1)[1].split('\nMETHOD ',1)[0]
for variant in ['os41','os28']:
 root=R/'build/research-203'/variant
 def read(name):return (root/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
 scene=read('SceneDetector');dual=read('DualSensorPolicy');listener=read('DualSensorPolicy$1');abc=read('AutomaticBrightnessController')
 body=method(scene,'applyNightDrivingBrightness')
 for token in ['->isNightTime()Z','->mIsDriving Z','->mUseDrivingModeForBrt Z','->mIsNightDrivingMode Z','->shouldResetAssistSensorValue()V']:check(token in body)
 check(body.index('->isNightTime()Z')<body.index('->mIsNightDrivingMode Z'))
 body=method(scene,'isNightTime')
 for token in ['->mSunriseMinutes I','->mSunsetMinutes I','->getCurrentMinutesOfDay()I']:check(token in body)
 body=method(scene,'getDrivingStatus');check('->mIsNightDrivingMode Z' in body)
 body=method(dual,'resetAssistSensorValue')
 for token in ['->clear()V','const/high16 v0, -1082130432','->mAssistFastAmbientLux F','->mAssistSlowAmbientLux F']:check(token in body)
 body=method(dual,'shouldResetAssistSensorValue')
 for token in ['->USE_MAIN_LIGHT_SENSOR I','->resetAssistSensorValue()V','->mIsPendingResetAssistValue Z']:check(token in body)
 body=method(listener,'onSensorChanged')
 for token in ['->getDrivingStatus()Z','->USE_MAIN_LIGHT_SENSOR I','mIsPendingResetAssistValue(','drop assistant light sensor lux','return-void','->-$$Nest$mhandleAssistLightSensorEvent']:check(token in body)
 check(body.index('->getDrivingStatus()Z')<body.index('drop assistant light sensor lux')<body.index('->-$$Nest$mhandleAssistLightSensorEvent'))
 for name in ['nextAmbientLightBrighteningTransition','nextAmbientLightDarkeningTransition']:check('->getNightDrivingDebounceConfig()J' in method(abc,name))
 # Admission hooks must bind to actual consumer ABIs on each supplied firmware.
 impl=read('AutomaticBrightnessControllerImpl');touch=read('TouchCoverProtectionHelper');sun=read('SunlightController')
 for source,name,signature,tokens in [
  (abc,'updateStepModeDarkenDebounceTime','(J)V',['->mStepModeDarkenDebounceConfig J']),
  (abc,'updateNightWakeMode','(Z Z)V',['->mIsNightWakeMode Z','->mSetNightWakeBrightness Z']),
  (touch,'isTouchCoverProtectionActive','()Z',['->isTouchingInArea()Z','->mTouchEventDebounce I']),
  (impl,'dropAmbientLuxIfNeeded','()Z',['->isTouchCoverProtectionActive()Z','->mUseProximityEnabled Z','->mProximityPositive Z']),
  (impl,'dropDecreaseLuxIfNeeded','()Z',['->useProximityInGameEnabled()Z','->mUseProxAndTouchEnabled Z','->mUseProximityEnabled Z','->mIsGameSceneEnable Z','->isGameSceneWithinTouchTime()Z']),
  (scene,'updateAmbientLux','(I F Z)V',['->mTransientProximityEnabled Z','->mMinAonFlareEnableLux F','->mMaxAonFlareEnableLux F','->mNeedCheckProximitySensor Z','->mNeedCheckAonFlare Z']),
  (scene,'isNeedSkipBrightnessChange','()Z',['->mNeedCheckProximitySensor Z','->mNeedCheckAonFlare Z']),
  (scene,'isReflectiveScene','()Z',['->mIsReflectiveScene Z']),
  (sun,'setSunLightModeActive','(Z)V',['->mSunlightModeActive Z','->notifySunlightStateChange(Z)V']),
  (scene,'updateBrightnessByDrivingMode','(F F Z Z)F',['->mMinDrivingEnableNit F','->mIsManualUpdateBrightnessOnDrivingMode Z']),
  (impl,'checkAssistSensorValid','()Z',['->mIsBackCamera Z','->mUseAssistSensorEnabled Z','->mIsTorchOpen Z','->mTorchCloseTime J','1800']),
  (dual,'handleAssistLightSensorEvent','(J F)V',['->checkAssistSensorValid()Z','->getIsTorchOpen()Z','->isRearScreenDevice()Z','->mOtherDisplayState I'])
 ]:
  body=method(source,name);check(body.startswith(signature+' '))
  for token in tokens:
   assert token in body,(variant,name,token)
   check(True)
print(f'Scene explanation firmware: {checks} checks PASS; private OEM entry conditions, reset sentinel and assist event exclusion; no device execution')
