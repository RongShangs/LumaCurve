"""Regression witnesses for the 2026-09-30 audit repairs."""
from pathlib import Path
import json, subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/code-audit-20260930'
OUT.mkdir(parents=True, exist_ok=True)
java = '''public class AuditLogic {
  public static void main(String[] args) {
    LumaFrameworkSliderOverride hold = new LumaFrameworkSliderOverride();
    hold.observe(1000, 1, 0, .2f);
    hold.settle(3500, .6f, 100);
    float thermalTarget = .15f;
    float actualGoal = hold.apply(thermalTarget,thermalTarget);
    if (actualGoal > thermalTarget) throw new AssertionError("thermal cap overridden");
    System.out.println("thermal_hold_override: core_cap="+thermalTarget+", broker_goal="+actualGoal);
    LumaFrameworkMappingFeedback mapping = new LumaFrameworkMappingFeedback();
    LumaFrameworkOutputFeedback physical = new LumaFrameworkOutputFeedback();
    boolean fault = false;
    LumaFrameworkConvergence convergence=new LumaFrameworkConvergence();
    for (long t=0;t<=60000;t+=500) {
      fault |= convergence.failed(t,.5f,.5f,.1f);
      fault |= mapping.failed(t,.5f,.5f,.1f,false);
      fault |= physical.failed(t,.5f,.5f,.1f,1638);
    }
    if (!fault) throw new AssertionError("nonconvergence ignored");
    System.out.println("nonconverging_feedback: 60s request=.5 adjusted=.1 node=1638, fault detected");
    float legacy = LumaFrameworkDeviceProfile.scale("local:4630946949513469331",16383);
    System.out.println("same_id_OTA: legacy scale selected without config hash/version="+legacy);
  }
}'''
(OUT / 'AuditLogic.java').write_text(java, encoding='utf-8')
subprocess.run(['javac', '--release', '8', '-cp', str(ROOT/'build/framework-probe/classes'), '-d', str(OUT), str(OUT/'AuditLogic.java')], check=True)
result = subprocess.run(['java','-cp', str(OUT)+';'+str(ROOT/'build/framework-probe/classes'),'AuditLogic'], capture_output=True, text=True, check=True)
print(result.stdout)

# Reuse the existing production IO fixture, replacing its entry point in a copy.
fixture = (ROOT/'tests/core_reliability.c').read_text(encoding='utf-8')
fixture = fixture.replace('int main(', 'int original_main(', 1)
fixture += '''
int main(void) {
  reset(); config="alpha_fast=nan\\n";
  int result=ios_configuration_reload(&io);
  printf("nan_config: accepted=%d, alpha_fast_isnan=%d\\n",result,isnan(ios_state.cfg_alpha_fast));
  printf("config_error=%s\\n",ios_state.g_config_error);
  /* Host CRT nan parsing varies; do not claim this proves Android nan acceptance. */
  reset(); config="brighten_speed=1junk\\n";
  result=ios_configuration_reload(&io);
  printf("junk_config: accepted=%d, brighten_speed=%g\\n",result,(double)ios_state.cfg_brighten_speed);
  printf("config_error=%s\\n",ios_state.g_config_error);
  assert(result==0 && ios_state.cfg_brighten_speed==1);
  reset();config="alpha_fast=.42\\n";result=ios_configuration_reload(&io);assert(result==1);
  extern uint32_t luma_config_loaded_hash;uint32_t saved=luma_config_loaded_hash;
  config="alpha_fast=.35\\n";close_fail=true;result=ios_configuration_reload(&io);
  assert(result==0 && luma_config_loaded_hash==saved && ios_state.cfg_alpha_fast==.42f);
  puts("config close failure: previous values and applied hash preserved");

  return 0;
}
'''
(OUT/'config_audit.c').write_text(fixture, encoding='utf-8')
names=['curve_pct_for_lux.c','brightness_preference.c','state.c','read_screen.c','read_lux_fallback.c','business_sensor_init.c','set_light_sensors_enabled.c','business_debounce.c','load_config.c']
command=['C:/msys64/mingw64/bin/gcc.exe','-O1','-std=c11','-DIOS_PRODUCTION','-DIOS_TEST_ABI','-ffp-contract=off','-fno-strict-aliasing','-I'+str(ROOT/'csrc')]
subprocess.run(command+[str(ROOT/'csrc'/name) for name in names]+[str(OUT/'config_audit.c'),'-lm','-o',str(OUT/'config_audit.exe')],check=True)
config = subprocess.run([str(OUT/'config_audit.exe')], capture_output=True, text=True, check=True)
print(config.stdout)
overflow = '''
#include "business_api.h"
#include "framework_backend.h"
#include <assert.h>
IosState ios_state;
LumaFrameworkSnapshot luma_framework_snapshot={.mode=1,.adjustment=0,.slider=100};
int luma_framework_refresh(void){return 0;}
void luma_preference_settings(DomainIo *io,uint64_t now,int mode,float adj,long slider){(void)io;(void)now;(void)mode;(void)adj;(void)slider;}
uint64_t ios_native_call(DomainIo *io,unsigned kind,const uint64_t *a,size_t count){
  (void)io;(void)count;assert(kind==IOS_I_clock_gettime);
  ((int64_t*)(uintptr_t)a[1])[0]=100;((int64_t*)(uintptr_t)a[1])[1]=0;return 0;
}
int main(void){
  DomainIo io={0};ios_state.g_front_lux=482.238f;
  float before=ios_state.g_front_lux;
  assert(ios_settings_refresh(&io)==0);
  printf("framework_settings_overflow: source_capacity=%u copy_bounded, front_lux_before=%g after=%g\\n",
    (unsigned)sizeof(ios_state.settings_read_source),(double)before,(double)ios_state.g_front_lux);
  assert(ios_state.g_front_lux==before);return 0;
}
'''
(OUT/'settings_audit.c').write_text(overflow,encoding='utf-8')
subprocess.run(command+['-DLUMA_FRAMEWORK_BACKEND',str(ROOT/'csrc/refresh_settings.c'),str(OUT/'settings_audit.c'),'-lm','-o',str(OUT/'settings_audit.exe')],check=True)
settings=subprocess.run([str(OUT/'settings_audit.exe')],capture_output=True,text=True,check=True)
print(settings.stdout)
(OUT/'reproductions.json').write_text(json.dumps({'repairs_verified':True,'android_device_verified':False,'java':result.stdout.splitlines(),'config':config.stdout.splitlines(),'framework_settings':settings.stdout.splitlines()},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
