"""Private single-input transplant evidence; do not distribute the collected ROM."""
from pathlib import Path
import json,re

R=Path(__file__).resolve().parents[1];P=R/'build/beta-4.0.1-beta01'
bundle=P/'redmagic-5b0e41';dex=P/'redmagic-disassembly';cases=0
def check(value,message):
 global cases
 assert value,message
 cases+=1
state=json.loads((bundle/'state.json').read_text(encoding='utf-8-sig'));runtime=state['runtime']
text=(bundle/'sensorservice.txt').read_text(encoding='utf-8',errors='replace')
sensors=re.findall(r'^0x[0-9a-fA-F]+\).*?\| type:\s*([^\s(]+)\((\d+)\)',text,re.M)
check(len(sensors)==45 and 'Total 37 h/w sensors' in text,'37 hardware plus 8 framework virtual entries')
check(sum(kind=='android.sensor.light' and code=='5' for kind,code in sensors)==1,'one exposed standard ALS')
check(not any(kind=='zte.sensor.light_second' or code=='33171055' for kind,code in sensors),'no validated auxiliary candidate')
check(any(kind=='zte.sensor.zte_light_ps' for kind,code in sensors),'composite light/proximity channel exists but is not scalar auxiliary lux')
check(runtime['main_input_compatibility']['profile']=='oem_main','native main source selected')
check(runtime['dual_policy_supported'] is False,'native cached single-input mode')
check(runtime['assist_compatibility']['state']=='no_verified_candidate','no auxiliary slot fallback applied')
check(runtime['assist_valid'] is False and 'assist_fast_lux' not in runtime,'absence was not a zero-lux sample')
control=runtime['brightness_control']
check(control['watch_main_samples']>0 and control['watch_assist_samples']==0,'real main-only watch history')
check(control['watch_binding_active'] and control['dark_lock_supported'],'existing main-only lock is already bound')
check(runtime['refactor_curve_verification']['baseline_fields_match'] and runtime['refactor_curve_verification']['current_basis_verified'],'user curve native readback is healthy')
impl=(dex/'com.android.server.display.AutomaticBrightnessControllerImpl.txt').read_text(encoding='utf-8')
abc=(dex/'com.android.server.display.AutomaticBrightnessController.txt').read_text(encoding='utf-8')
check('METHOD supportDualSensorPolicy ()Z' in impl and '->getAssistLightSensor()Landroid/hardware/Sensor;' in impl,'native support depends on real auxiliary slot')
check('->mSupportDualSensorPolicy Z' in abc and '->updateSingleSensorPolicy(J)Z' in abc,'native single-input consumer retained')
print(f'Single sensor firmware: {cases} checks PASS; actual private inventory and native consumers, no device test')
