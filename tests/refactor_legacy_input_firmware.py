"""Private supplied framework evidence for old DPC effective mode and unscaled lux ABI."""
from pathlib import Path
import argparse
R=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,default=R.parent/'diagnostics/unpacked/RedmiK60_23013RK75C/LumaCurve-analysis-20261103-215433-8b4142(1)/analysis');a=p.parse_args()
count=0
def check(value):
 global count
 assert value,count;count+=1
def read(name):return (a.fixtures/(name+'.txt')).read_text('utf-8')
def body(text,name):return text.split('METHOD '+name+' ',1)[1].split('\nMETHOD ',1)[0]
owner=read('DisplayPowerControllerImpl');dpc=read('DisplayPowerController');dual=read('DualSensorPolicy');abc=read('AutomaticBrightnessController')
check('FIELD mUseAutoBrightness ' not in owner);check('FIELD mAutomaticBrightnessStrategy ' not in dpc)
check('FIELD mUseAutoBrightness Z flags=private' in dpc)
for text,name,kind in [(owner,'mDisplayPowerController','Lcom/android/server/display/DisplayPowerController;'),(dpc,'mDisplayPowerControllerImpl','Lcom/android/server/display/DisplayPowerControllerStub;'),(dpc,'mAutomaticBrightnessController','Lcom/android/server/display/AutomaticBrightnessController;')]:check('FIELD '+name+' '+kind in text)
for text in [owner,dpc]:check('FIELD mDisplayId I ' in text)
check('->mUseAutoBrightness Z' in body(dpc,'updatePowerStateInternal'))
for name in ['mFovAmplifyFactor','mAssistFovAmplifyFactor']:check('FIELD '+name+' ' not in dual)
for name,kind in [('mBrightnessControllerImpl','Lcom/android/server/display/AutomaticBrightnessControllerImpl;'),('mSensorManager','Landroid/hardware/SensorManager;'),('mMainFastAmbientLux','F'),('mAssistFastAmbientLux','F'),('mAssistLightSensor','Landroid/hardware/Sensor;')]:check('FIELD '+name+' '+kind in dual)
main=body(dual,'updateMainLuxStatus');assist=body(dual,'handleAssistLightSensorEvent')
check(main.startswith('(F)V flags=protected'));check(assist.startswith('(J F)V flags=private'))
check('iput v2, v1, Lcom/android/server/display/DualSensorPolicy;->mMainFastAmbientLux F' in main)
check('invoke-virtual v0, v6, v7, v8, Lcom/android/server/display/AmbientLightRingBuffer;->push(J F)V' in assist)
for text in [main,assist]:check('mul-float' not in text and 'div-float' not in text)
check('FIELD mLightSensor Landroid/hardware/Sensor;' in abc)
check('FIELD mLightSensorEnabled Z ' in abc);check('FIELD mAmbientLuxValid Z ' in abc)
print(f'Legacy input firmware: {count} checks PASS; supplied ABI and unit flow only, not Android validation')
