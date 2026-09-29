#include "business_api.h"
#include "framework_wake.h"
#include "temporal_stability.h"
#include <assert.h>
IosState ios_state;
static void reset(void) {
    memset(&ios_state,0,sizeof(ios_state));ios_scene_reset(true);ios_temporal_reset();
    ios_state.g_max=16383;ios_state.cfg_min_step=1;ios_state.g_target_debounce_init=1;
    ios_state.g_target_debounce_br=1442;luma_indoor_stability=0;
}
static void quiet(void) {
    reset();luma_indoor_stability=1;ios_scene_mode(true,1);
    ios_state.g_ndk_ok=1;ios_state.g_light_sensors_enabled=1;ios_state.g_front_sensor=(void *)1;
    ios_state.g_front_sensor_timestamp_ns=1000000000;ios_state.g_front_lux_event_ms=1000;
    ios_state.g_wake_last_lux_event_ms=1000;ios_state.g_wake_lux_sample_count=1;ios_state.g_front_lux=742;
}
int main(void) {
    reset();luma_framework_start_goal(&ios_state,1442);assert(ios_state.g_tr_0==1442&&ios_state.g_tr_1==1442);
    reset();luma_framework_start_goal(&ios_state,-1);assert(ios_state.g_tr_1==0);
    reset();luma_framework_start_goal(&ios_state,20000);assert(ios_state.g_tr_1==0);
    puts("framework startup: seed real goal, reject invalid readback PASS");
    reset();
    assert(ios_business_debounce(&ios_state,1000,15126,1442,500,false)==1442);
    assert(ios_state.g_target_candidate_br==15126);
    assert(ios_business_debounce(&ios_state,2799,15126,1442,500,false)==1442);
    assert(ios_business_debounce(&ios_state,2800,15126,1442,500,false)==15126);
    reset();ios_business_debounce(&ios_state,1000,15126,1442,500,false);
    assert(ios_business_debounce(&ios_state,2500,10000,1442,500,false)==1442);
    assert(ios_business_debounce(&ios_state,4299,10000,1442,500,false)==1442);
    assert(ios_business_debounce(&ios_state,4300,10000,1442,500,false)==10000);
    puts("framework target: full goal after hold, boundary and retarget; no staircase PASS");
    float out=-1;quiet();ios_state.g_sensor_stale=1;ios_state.g_sensor_hold_active=1;
    assert(luma_framework_seed_quiet_wake(&ios_state,17000,&out)&&out==742);
    assert(ios_state.g_front_lux_event_ms==1000&&ios_state.g_wake_lux_sample_count==1);
    assert(ios_state.g_last_processed_lux_event_ms==1000&&!ios_state.g_sensor_stale&&!ios_state.g_sensor_hold_active);
    assert(ios_state.g_actuator_smooth_lux==742&&ios_state.trend_lux[4]==742);
    quiet();ios_scene_mode(true,0);assert(!luma_framework_seed_quiet_wake(&ios_state,1100,&out));
    quiet();ios_scene_mode(true,-1);assert(!luma_framework_seed_quiet_wake(&ios_state,1100,&out));
    quiet();ios_state.g_wake_lux_sample_count=0;assert(!luma_framework_seed_quiet_wake(&ios_state,17000,&out));
    quiet();ios_state.g_light_sensors_enabled=0;assert(!luma_framework_seed_quiet_wake(&ios_state,17000,&out));
    quiet();ios_state.g_wake_last_lux_event_ms=999;assert(!luma_framework_seed_quiet_wake(&ios_state,17000,&out));
    quiet();assert(!luma_framework_seed_quiet_wake(&ios_state,999,&out));
    quiet();ios_state.g_front_lux=NAN;assert(!luma_framework_seed_quiet_wake(&ios_state,17000,&out));
    quiet();ios_state.g_front_sensor_timestamp_ns=0;assert(!luma_framework_seed_quiet_wake(&ios_state,1100,&out));
    quiet();ios_state.g_front_lux=0;assert(luma_framework_seed_quiet_wake(&ios_state,17000,&out)&&out==0);
    quiet();ios_state.g_front_sensor=NULL;ios_state.g_back_ok=1;ios_state.g_back_sensor=(void *)2;
    ios_state.g_back_sensor_timestamp_ns=1000000000;ios_state.g_back_lux_event_ms=1000;ios_state.g_back_lux=12;
    ios_state.g_back_fallback_confirm_count=1;ios_scene_mode(false,1);
    assert(luma_framework_seed_quiet_wake(&ios_state,17000,&out)&&out==12&&!strcmp(ios_state.g_lux_source,"back"));
    puts("framework wake: quiet real sample, no fabricated count/time, invalid/unknown/disabled/zero guards PASS");
    return 0;
}
