"""Check private donor DEX and actual sensor inventory; firmware is never redistributed."""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[1];P=R/'build/port-compat/nubia-20261017-83931a';cases=0
if not (P/'disassembly/miui/com.android.server.display.DualSensorPolicy.txt').exists():
 print('Assist compatibility firmware: 0 checks PASS; private fixture unavailable, skipped');raise SystemExit(0)
def check(value):
 global cases
 assert value,'check '+str(cases);cases+=1
text=(P/'disassembly/miui/com.android.server.display.DualSensorPolicy.txt').read_text(encoding='utf-8')
constructor=text.split('METHOD <init> ')[1].split('\nMETHOD ')[0]
check('33171055' in constructor and '->getDefaultSensor(I)' in constructor)
check('->mAssistLightSensor Landroid/hardware/Sensor;' in constructor)
body=text.split('METHOD setSensorEnabled (Z)V')[1].split('\nMETHOD ')[0]
check('->registerListener(Landroid/hardware/SensorEventListener; Landroid/hardware/Sensor; I Landroid/os/Handler;)Z' in body)
check('->unregisterListener(Landroid/hardware/SensorEventListener; Landroid/hardware/Sensor;)V' in body)
check('->mAssistAmbientLuxValid Z' in body and '->clear()V' in body)
listener=(P/'disassembly/miui/com.android.server.display.DualSensorPolicy$1.txt').read_text(encoding='utf-8').split('METHOD onSensorChanged ')[1]
check('->values [F' in listener and 'aget' in listener and '33171055' not in listener)
check('night driving mode: drop assistant light sensor lux.' in listener)
check('handleAssistLightSensorEvent' in listener)
main=text.split('METHOD handleAssistLightSensorEvent ')[1].split('\nMETHOD ')[0]
check('->getIsTorchOpen()Z' in main)
sensors=(P/'sensorservice.txt').read_text(encoding='utf-8')
check('zte.sensor.light_second(33172007)' in sensors)
check('(33171055)' not in sensors)
check('zte.sensor.zte_dual_light(33172037)' in sensors)
check('Ambient Light Second Sensor Non-wakeup' in sensors)
state=json.loads((P/'state.json').read_bytes())['runtime']
check(state['phase']=='active' and state['curve_backend']=='refactor')
check(state['assist_sampling_enabled'] and not state['assist_ambient_lux_valid'])
check(state['assist_reading_state']=='reset')
check(state['system_scene']['assist_policy_enabled'])
check(state['sensor_status']=='active')
chain=R/'build/port-compat/nubia-20261017-84376b/disassembly-chain'
if chain.exists():
 abc=(chain/'services.jar.com.android.server.display.AutomaticBrightnessController.txt').read_text(encoding='utf8')
 constructor='\n'.join(section for section in abc.split('\nMETHOD ') if section.startswith('<init> '))
 check('->supportDualSensorPolicy()Z' in constructor and '->mSupportDualSensorPolicy Z' in constructor)
 update=abc.split('METHOD updateAmbientLux (J)V')[1].split('\nMETHOD ')[0]
 check(update.index('->mSupportDualSensorPolicy Z')<update.index('->updateDualSensorPolicy(J I)Z'))
 check('->updateSingleSensorPolicy(J)Z' in update)
 impl=(P/'disassembly/miui/com.android.server.display.AutomaticBrightnessControllerImpl.txt').read_text(encoding='utf8')
 supports=impl.split('METHOD supportDualSensorPolicy ')[1].split('\nMETHOD ')[0]
 check('->mUseAssistSensorEnabled Z' in supports and '->getAssistLightSensor()' in supports)
 initialize=text.split('METHOD setAmbientLuxWhenInvalid ')[1].split('\nMETHOD ')[0]
 check('->mUseLightSensorFlag I' in initialize and '->USE_MAIN_LIGHT_SENSOR I' in initialize)
 check('->updateAmbientLux(I F Z Z)V' in initialize)
 ref=(chain/'miui-services.jar.com.android.server.display.RefactorNitController.txt').read_text(encoding='utf8')
 blend=ref.split('METHOD blendedDefaultAtLux ')[1].split('\nMETHOD ')[0]
 check('->defaultAtLux(F)F' in blend and '1056964608' in blend)
 enforce=ref.split('METHOD enforceMonotonicityAndRestore ')[1].split('\nMETHOD ')[0]
 check('->blendedDefaultAtLux(F F F)F' in enforce and '->min(F F)F' in enforce and '->max(F F)F' in enforce)
 handover=ref.split('METHOD shouldUseGoodCurve ')[1].split('\nMETHOD ')[0]
 check('->mGoodAnchorNit [F' in handover and '->mAnchorNit [F' in handover and '->arraycopy(' in handover)
print('Assist compatibility firmware: '+str(cases)+' checks PASS; actual user inventory and OEM consumer, no device execution')
