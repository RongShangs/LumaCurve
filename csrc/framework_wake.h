#pragma once
#include "state.h"
#include "scene_adaptation.h"
#include <math.h>
#include <string.h>
/* A real on-change sample may stay quiet. This does not create a second event. */
static inline bool luma_framework_wake_front(const IosState *s,uint64_t now) {
    return s->g_front_sensor&&s->g_front_sensor_timestamp_ns>0&&ios_scene_sensor_fresh(s,true,now);
}
static inline void luma_framework_start_goal(IosState *s,int32_t current) {
    if(current>0&&current<=s->g_max){s->g_tr_0=current;s->g_tr_1=current;}
}
static inline bool luma_framework_quiet_wake_ready(const IosState *s,uint64_t now) {
    if(s->g_wake_lux_sample_count!=1||!s->g_ndk_ok||!s->g_light_sensors_enabled||!s->g_wake_last_lux_event_ms)return false;
    bool front=luma_framework_wake_front(s,now);
    if(!front&&(!s->g_back_ok||!s->g_back_sensor||s->g_back_sensor_timestamp_ns<=0||!ios_scene_back_confirmed(s,now)||!ios_scene_sensor_fresh(s,false,now)))return false;
    return ios_scene_reporting_mode(front)==1&&s->g_wake_last_lux_event_ms==(front?s->g_front_lux_event_ms:s->g_back_lux_event_ms);
}
static inline bool luma_framework_seed_quiet_wake(IosState *s,uint64_t now,float *out) {
    if(!luma_framework_quiet_wake_ready(s,now))return false;
    bool front=luma_framework_wake_front(s,now);
    float lux=front?s->g_front_lux:s->g_back_lux;
    if(!isfinite(lux)||lux<0||lux>200000)return false;
    memcpy(s->g_lux_source,front?"front":"back",front?6:5);
    s->g_last_lux_event_ms=front?s->g_front_lux_event_ms:s->g_back_lux_event_ms;
    s->g_last_processed_lux_event_ms=s->g_last_lux_event_ms;
    s->g_lux_valid=1;s->g_sensor_stale=0;s->g_sensor_hold_active=0;
    s->g_sensor_hold_since_ms=0;s->g_sensor_hold_timeout=0;s->g_sensor_stale_source[0]=0;
    for(unsigned i=0;i<5;i++)s->trend_lux[i]=lux;
    s->trend_idx=0;s->trend_filled=5;
    s->lux_prev1=s->lux_prev2=s->lux_prev3=s->lux_prev4=lux;
    s->g_actuator_smooth_lux=lux;*out=lux;return true;
}
