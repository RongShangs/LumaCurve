"""Verify private foldable evidence locally; proprietary files never enter the source archive."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

R=Path(__file__).resolve().parents[1]
F=R/'build/fold-compat/fa7857'
cases=0
def check(value):
    global cases
    assert value, cases
    cases+=1
def method(file,name):
    text=(F/'disassembly'/file).read_text(encoding='utf-8')
    return text.split('\nMETHOD '+name+' ',1)[1].split('\nMETHOD ',1)[0]

display=(F/'display.txt').read_text(encoding='utf-8')
sensors=(F/'sensorservice.txt').read_text(encoding='utf-8')
check('type=33171081' in display and 'TCS3760 Light Flip Non-wakeup' in display)
check('xiaomi.sensor.light_secondary' in sensors)
for raw in ['xiaomi.sensor.ambientlight.raw_sec','xiaomi.sensor.ambientlightsecondary.factory','xiaomi.sensor.light_primary2_strm']:
    check(raw in sensors)
check('mUniqueDisplayId: local:' in display)
check('com.android.server.display.AutomaticBrightnessController |' in sensors and 'Optimization light fov Sensor 0x5f666f76' in sensors)
check('mLightFovSensor={Sensor name="Optimization light fov Sensor"' in display)
check('mDisplayId=1' in display and 'mLightSensor=null' in display)
layouts=list((F/'display-config').rglob('display_layout_configuration.xml'))
check(len(layouts)==1)
root=ET.parse(layouts[0]).getroot()
addresses={e.text for e in root.iter('address')}
check(len(addresses)>=2)
enable=method('com.android.server.display.AutomaticBrightnessController.txt','setLightSensorEnabled')
check('->mLightSensor Landroid/hardware/Sensor;' in enable)
check('->switchLightSensor(Landroid/hardware/Sensor;)Landroid/hardware/Sensor;' in enable)
check('->registerListener(' in enable and '->mSensorManager ' in enable)
selection=method('com.android.server.display.AutomaticBrightnessControllerImpl.txt','switchLightSensor')
check('->mSupportFovSensor Z' in selection and '->mDisplayId I' in selection)
check('return-object v3' in selection)  # no FOV/rear override: use the supplied ABC source
event=method('com.android.server.display.AutomaticBrightnessController$2.txt','onSensorChanged')
check('Landroid/hardware/SensorEvent;->values [F' in event)
check('const/4 v3, 0\n000094 aget v2, v2, v3' in event)
check('->getDrivingStatus()Z' in event and '->getFovAmplifyFactor()F' in event)
check('->-$$Nest$mhandleLightSensorEvent' in event)
state=json.loads((F/'state.json').read_text(encoding='utf-8'))
runtime=state.get('runtime',state)
check(runtime.get('build')=='release-4.0.0')
print(f'Fold sensor firmware: {cases} checks PASS; private topology and bytecode only, no device execution')
