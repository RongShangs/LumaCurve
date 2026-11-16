"""Private native consumer evidence, not a device execution result."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]
paths=[
 'build/device-reports/pandora-20261113-182748/display-consumers/com.android.server.display.AutomaticBrightnessController.txt',
 'build/analysis-20261102-fed360/display-consumers/com.android.server.display.AutomaticBrightnessController.txt',
 'build/beta-4.0.1-beta01/redmagic-disassembly/com.android.server.display.AutomaticBrightnessController.txt',
 'build/beta-4.0.2-beta04/disassembly/com.android.server.display.AutomaticBrightnessController.txt',
]
checks=0
def check(value,message):
 global checks
 assert value,message
 checks+=1
def method(text,signature):
 start=text.index('METHOD '+signature);end=text.find('\nMETHOD ',start+1)
 return text[start:end if end>=0 else len(text)]
for name in paths:
 text=(R/name).read_text('utf-8')
 for signature in ['setAmbientLux (F)V','updateAutoBrightness (Z Z)V','updateAmbientLux ()V','resetShortTermModel ()V','resetShortTermModelIfNeeded ()V','setScreenBrightnessByUser (F)Z','setScreenBrightnessByUser (F F)Z','setAmbientLux (I F Z Z)V']:
  check('flags=static' not in method(text,signature).splitlines()[0],name+': static entry')
 setter=method(text,'setAmbientLux (F)V');check('->mAmbientLux F' in setter,name+': lux coordinate');check('->onAmbientLuxChange(F)V' in setter,name+': range notification');check('->resetShortTermModelIfNeeded()V' in setter,name+': reset boundary')
 user=method(text,'setScreenBrightnessByUser (F)Z');check('->mAmbientLux F' in user and '->setScreenBrightnessByUser(F F)Z' in user,name+': manual anchor source')
 dual=method(text,'setAmbientLux (I F Z Z)V');check('->mSupportDualSensorPolicy Z' in dual and '->updateAutoBrightness(Z Z)V' in dual,name+': dual native calculation')
 update=method(text,'updateAutoBrightness (Z Z)V');check('->getBrightness(F Ljava/lang/String; I)F' in update,name+': native mapper consumer');check('Settings$System;->put' not in update,name+': persisted brightness write')
 check('->mRawScreenAutoBrightness F' in update,name+': calculation output witness')
 check('->resetShortTermModel' not in update,name+': calculation must not reset learning')
code=(R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorLightTest.java').read_text('utf-8')
check('DURATION=20000' in code,'test deadline');check('resetShortTermModel' not in code or 'access.reset' in code,'model seam');check('SensorEvent' not in code and 'mAmbientLightRingBuffer' not in code,'raw buffers must stay real')
check('access.ambient.invoke' not in code,'synthetic lux must never enter native setter or its inlined resets')
check('FieldOverride.apply(access.abc,access.lux,TEST_LUX)' in code and '((FieldOverride)value).close()' in code,'calculation-scoped input restoration')
check('input_route_changed' in code and 'access.raw(),calculatedRaw' in code,'native output changes without scoped input must stop test')
print(f'Outdoor light test firmware: {checks} checks PASS; supplied native consumer evidence, no device execution')
