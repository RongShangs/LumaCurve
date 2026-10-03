"""Check traditional mapper ABI against private OS4 disassembly, never distributed."""
from pathlib import Path
import argparse
R=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,default=R/'build/device-reports/k90pm-20261003/disassembly');a=p.parse_args()
count=0
def check(value):
    global count
    assert value
    count+=1
def read(name):return (a.fixtures/(name+'.txt')).read_text(encoding='utf-8')
def body(text,name):return text.split('METHOD '+name+' ',1)[1].split('\nMETHOD ',1)[0]
owner=read('com.android.server.display.DisplayPowerControllerImpl');abc=read('com.android.server.display.AutomaticBrightnessController');config=read('android.hardware.display.BrightnessConfiguration')
for name,kind in [('mBrightnessMapper','Lcom/android/server/display/BrightnessMappingStrategy;'),('mDisplayId','I'),('mUseAutoBrightness','Z'),('mAutomaticBrightnessControllerImpl','Lcom/android/server/display/AutomaticBrightnessControllerImpl;')]:check('FIELD '+name+' '+kind in owner)
check('METHOD handleOnSwitchUser (I)V' in owner)
for name,kind in [('mCurrentBrightnessMapper','Lcom/android/server/display/BrightnessMappingStrategy;'),('mBrightnessMappingStrategyMap','Landroid/util/SparseArray;'),('mUseRefactorBrightnessPolicy','Z'),('mShortTermModel','Lcom/android/server/display/AutomaticBrightnessController$ShortTermModel;')]:check('FIELD '+name+' '+kind in abc)
calculation=body(abc,'updateAutoBrightness');check(calculation.index('->getBrightness(')<calculation.index('->getCustomBrightness('));check('->mUseRefactorBrightnessPolicy Z' in calculation and '->getRefactorBrightness(' in calculation);check('->getDefaultBrightness(' in calculation)
reset=body(abc,'resetShortTermModel');check('->mCurrentBrightnessMapper ' in reset);check('->clearUserDataPoints()V' in reset);check('->mShortTermModel ' in reset)
check('METHOD <init> ([F [F Ljava/util/Map; Ljava/util/Map; Ljava/lang/String; Z J F F)V' in config)
for name,kind in [('mCorrectionsByPackageName','Ljava/util/Map;'),('mCorrectionsByCategory','Ljava/util/Map;'),('mDescription','Ljava/lang/String;'),('mShouldCollectColorSamples','Z'),('mShortTermModelTimeout','J'),('mShortTermModelLowerLuxMultiplier','F'),('mShortTermModelUpperLuxMultiplier','F')]:check('FIELD '+name+' '+kind in config)
for name in ['com.android.server.display.MiuiPhysicalBrightnessMappingStrategy','com.android.server.display.BrightnessMappingStrategy$PhysicalMappingStrategy']:
    mapper=read(name)
    for field,kind in [('mConfig','Landroid/hardware/display/BrightnessConfiguration;'),('mBrightnessSpline','Landroid/util/Spline;'),('mNits','[F')]:check('FIELD '+field+' '+kind in mapper)
    for signature in ['getMode ()I','setBrightnessConfiguration (Landroid/hardware/display/BrightnessConfiguration;)Z','clearUserDataPoints ()V','getBrightness (F Ljava/lang/String; I)F','convertToBrightness (F)F','convertToNits (F)F']:check('METHOD '+signature in mapper)
    compute=body(mapper,'computeSpline');check('->mConfig ' in compute);check('->getCurve()Landroid/util/Pair;' in compute);check('->mBrightnessSpline Landroid/util/Spline;' in compute)
    setter=body(mapper,'setBrightnessConfiguration');check('->mConfig ' in setter);check('->computeSpline()V' in setter)
    brightness=mapper.split('METHOD getBrightness (F Ljava/lang/String; I)F',1)[1].split('\nMETHOD ',1)[0]
    check('->mBrightnessSpline ' in brightness);check('->'+('mNitsToBrightnessSpline' if 'MiuiPhysical' in name else 'mAdjustedNitsToBrightnessSpline')+' ' in brightness)
    check('METHOD addUserDataPoint (F F'+(' Ljava/lang/String;' if 'MiuiPhysical' in name else '')+')V' in mapper)
    check('FIELD '+('mShortTermModelUserLux' if 'MiuiPhysical' in name else 'mUserLux')+' F' in mapper)
print(f'Traditional firmware: {count} checks PASS; mapper, units, metadata and consumer ABI verified, device not tested')
