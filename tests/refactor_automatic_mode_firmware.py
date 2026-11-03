"""Check effective auto-mode provenance against the privately supplied OS3 framework."""
from pathlib import Path
import argparse
R=Path(__file__).resolve().parents[1];p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,default=R.parent/'diagnostics/unpacked/Xiaomi12SPro_2206122SC/LumaCurve-analysis-20261103-221955-e69e2d(1)/analysis');a=p.parse_args()
count=0
def check(ok):
 global count
 assert ok,count;count+=1
def read(n):return (a.fixtures/(n+'.txt')).read_text('utf-8')
def body(t,m):return t.split('METHOD '+m+' ',1)[1].split('\nMETHOD ',1)[0]
owner=read('DisplayPowerControllerImpl');dpc=read('DisplayPowerController');abc=read('AutomaticBrightnessController')
check('FIELD mUseAutoBrightness ' not in owner)
check('FIELD mAutoBrightnessEnable Z flags=private' in owner)
configured=body(owner,'updateAutoBrightnessMode');check('screen_brightness_mode' in configured and '->getIntForUser(' in configured and '->mAutoBrightnessEnable Z' in configured)
for t in [owner,dpc]:check('FIELD mDisplayId I ' in t)
check('FIELD mDisplayPowerController Lcom/android/server/display/DisplayPowerController;' in owner)
check('FIELD mDisplayPowerControllerImpl Lcom/android/server/display/DisplayPowerControllerStub;' in dpc)
check('FIELD mAutomaticBrightnessController Lcom/android/server/display/AutomaticBrightnessController;' in dpc)
check('FIELD mAutomaticBrightnessStrategy Lcom/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2;' in dpc)
for n in ['AutomaticBrightnessStrategy','AutomaticBrightnessStrategy2']:
 t=read(n)
 for f,kind in [('mDisplayId','I'),('mAutomaticBrightnessController','Lcom/android/server/display/AutomaticBrightnessController;'),('mUseAutoBrightness','Z'),('mIsAutoBrightnessEnabled','Z')]:check('FIELD '+f+' '+kind in t)
 use=body(t,'shouldUseAutoBrightness');enabled=body(t,'isAutoBrightnessEnabled')
 check(use.splitlines()[0]=='()Z flags=public');check('->mUseAutoBrightness Z' in use and 'return v0' in use)
 check('->mIsAutoBrightnessEnabled Z' in enabled and '->mUseAutoBrightness Z' not in enabled)
 update=body(t,'setUseAutoBrightness');check('(Z)V flags=public' in update and 'iput-boolean' in update and '->mUseAutoBrightness Z' in update)
state=body(dpc,'updatePowerStateInternal');check('->shouldUseAutoBrightness()Z' in state)
check('->adjustSdrBrightness(F Z Lcom/android/server/display/brightness/BrightnessReason; Z)F' in state)
# The effective-use value is passed to native SDR adjustment; the user setting is a separate OEM flag.
lines=state.splitlines();i=next(i for i,l in enumerate(lines) if '->adjustSdrBrightness(F Z Lcom/android/server/display/brightness/BrightnessReason; Z)F' in l);segment='\n'.join(lines[max(0,i-18):i+1])
check('->mAutomaticBrightnessStrategy ' in segment and '->shouldUseAutoBrightness()Z' in segment and '->adjustSdrBrightness(' in segment)
for n in ['MiuiPhysicalBrightnessMappingStrategy','BrightnessMappingStrategy$PhysicalMappingStrategy']:
 t=read(n);check('METHOD getMode ()I' in t);check('METHOD getUserBrightness ()F' in t);check('METHOD getBrightness (F Ljava/lang/String; I)F' in t)
print(f'Automatic mode firmware: {count} checks PASS; native effective-use semantics, no device execution')
