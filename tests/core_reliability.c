/* Real production C, controlled system boundaries; never access device nodes. */
#include "business_api.h"
#include "brightness_preference.h"
#include "brightness_curve.h"
#include "backlight_paths.h"
#include "temporal_stability.h"
#include "scene_adaptation.h"
#include <assert.h>
#include <stdio.h>

static DomainIo io;
static uint64_t now = 100000;
static bool clock_fail, close_fail, screen_missing, original_panel = true;
static int power, pipe_status, pipe_calls, front_enable, back_enable;
static bool front_present = true, back_present = true;
static const char *lux_text = "7.139", *power_text, *cursor, *config;
static unsigned directory_index, alternatives;
static char entry[19+256], active_path[384];
static const void *sensor_list[2] = {(void *)11,(void *)12};
enum { GET_MGR=1, GET_DEF, CREATE_EQ, ENABLE, DISABLE, RATE, EVENTS, PREPARE, LIST, TYPE, NAME, REPORT_MODE };
uint64_t ios_native_call(DomainIo *context, unsigned kind, const uint64_t *a, size_t count) {
    (void)count;
    switch (kind) {
    case IOS_I_clock_gettime:
        if (clock_fail) return (uint64_t)-1;
        ((int64_t *)(uintptr_t)a[1])[0]=now/1000;
        ((int64_t *)(uintptr_t)a[1])[1]=(now%1000)*1000000; return 0;
    case IOS_I_fopen: {
        const char *path=(const char *)(uintptr_t)a[0];
        strcpy(active_path,path);
        if (strstr(path,"luma_curve.conf")) {cursor=config;return config?501:0;}
        if (strstr(path,"lux")) return 41;
        if (strstr(path,"bl_power")) return screen_missing?0:42;
        if (strstr(path,"panel0-backlight") && !original_panel) return 0;
        return strstr(path,"max_brightness") || strstr(path,"/brightness")?43:0;
    }
    case IOS_I_fscanf: {
        const char *format=(const char *)(uintptr_t)a[1];
        if(a[0]==41) return sscanf(lux_text,format,(void *)(uintptr_t)a[2]);
        int value=a[0]==42?power:strstr(active_path,"max_brightness")?16383:100;
        *(int *)(uintptr_t)a[2]=value;return 1;
    }
    case IOS_I_fclose:return close_fail?(uint64_t)-1:0;
    case IOS_I_opendir:directory_index=0;return alternatives?71:0;
    case IOS_I_readdir:
        if(directory_index>=alternatives)return 0;
        snprintf(entry+19,sizeof(entry)-19,"panel%u",++directory_index);return ARG(entry);
    case IOS_I_closedir:return 0;
    case IOS_I_popen:pipe_calls++;cursor=power_text;return power_text?1001:0;
    case IOS_I_fgets: {
        if(!cursor || !*cursor)return 0;
        char *out=(char *)(uintptr_t)a[0];size_t n=0;
        while(n+1<a[1] && *cursor) {out[n++]=*cursor++;if(out[n-1]=='\n')break;}
        out[n]=0;return a[0];
    }
    case IOS_I_pclose:return pipe_status;
    case IOS_I_dlopen:return 1;
    case IOS_I_dlsym: {
        const char *name=(const char *)(uintptr_t)a[1];
        if(strstr(name,"getInstanceForPackage"))return 0;
        if(strstr(name,"getInstance"))return GET_MGR;
        if(strstr(name,"getDefaultSensor"))return GET_DEF;
        if(strstr(name,"createEventQueue"))return CREATE_EQ;
        if(strstr(name,"enableSensor"))return ENABLE;
        if(strstr(name,"disableSensor"))return DISABLE;
        if(strstr(name,"setEventRate"))return RATE;
        if(strstr(name,"getEvents"))return EVENTS;
        if(strstr(name,"prepare"))return PREPARE;
        if(strstr(name,"getSensorList"))return LIST;
        if(strstr(name,"getType"))return TYPE;
        if(strstr(name,"getName"))return NAME;
        if(strstr(name,"getReportingMode"))return REPORT_MODE;
        return 0;
    }
    case IOS_I_atof:context->real_result=atof((const char *)(uintptr_t)a[0]);return 0;
    case IOS_I_atoi:return atoi((const char *)(uintptr_t)a[0]);
    case IOS_I_snprintf:
        return snprintf((char *)(uintptr_t)a[0],a[1],(const char *)(uintptr_t)a[2],(const char *)(uintptr_t)a[3]);
    case IOS_I_strncpy: strncpy((char *)(uintptr_t)a[0],(const char *)(uintptr_t)a[1],a[2]);return a[0];
    case IOS_I_fprintf:case IOS_I_fflush:return 0;
    default:fprintf(stderr,"Unexpected import %u\n",kind);abort();
    }
}
uint64_t ios_native_dynamic(uint64_t function, const uint64_t *a, size_t count) {
    (void)count;
    switch(function) {
    case GET_MGR:case PREPARE:case CREATE_EQ:return 1;
    case GET_DEF:return front_present?11:0;
    case ENABLE:return a[1]==11?(uint64_t)(int64_t)front_enable:(uint64_t)(int64_t)back_enable;
    case DISABLE:case RATE:return 0;
    case LIST:*(const void ***)(uintptr_t)a[1]=sensor_list;return back_present?2:1;
    case TYPE:return a[0]==12?0x1fa266f:5;
    case NAME:return ARG("mock-ALS");
    case REPORT_MODE:return a[0]==11?1:0;
    default:assert(0);return 0;
    }
}
int ios_hbm_discover(DomainIo *context) {(void)context;return 0;}
static void reset(void) {
    ios_reset_data();now+=100000;
    clock_fail=close_fail=screen_missing=false;power=0;power_text=NULL;pipe_status=pipe_calls=0;
    original_panel=true;alternatives=0;front_present=back_present=true;front_enable=back_enable=0;
}
static void test_fallback(void) {
    reset();strcpy(ios_state.g_sensor_path,"/mock/lux");
    const char *bad[]={"inf","nan","-1","200001","junk","-inf"};
    uint64_t result=0;
    for(unsigned i=0;i<sizeof(bad)/sizeof(*bad);i++) {
        lux_text=bad[i];IosState saved=ios_state;
        assert(ios_fallback_lux_read(&io,&result)==-1);assert(!memcmp(&saved,&ios_state,sizeof(saved)));
    }
    lux_text="7.139";clock_fail=true;IosState saved=ios_state;
    assert(ios_fallback_lux_read(&io,&result)==-1);assert(!memcmp(&saved,&ios_state,sizeof(saved)));
    clock_fail=false;assert(ios_fallback_lux_read(&io,&result)>7);
    assert(ios_state.g_lux_valid==1 && ios_state.g_last_lux_event_ms==now);
    puts("fallback: finite/range/parse/clock failure preserves state; valid timestamp PASS");
}
static void test_screen(void) {
    reset();assert(ios_read_screen_on(&io));
    screen_missing=true;assert(ios_read_screen_on(&io));assert(pipe_calls==1);
    assert(ios_read_screen_on(&io));assert(pipe_calls==1);
    now+=5000;power_text="  mWakefulness=Asleep\n";assert(!ios_read_screen_on(&io));
    now+=5000;power_text="  mWakefulness=Awake\n";pipe_status=1;assert(!ios_read_screen_on(&io));
    now+=5000;pipe_status=0;assert(ios_read_screen_on(&io));
    reset();power=4;assert(!ios_read_screen_on(&io));power=-1;assert(!ios_read_screen_on(&io));
    reset();screen_missing=true;assert(!ios_read_screen_on(&io));
    reset();screen_missing=true;power_text=" mWakefulness=AwakeGarbage\n";assert(!ios_read_screen_on(&io));
    reset();screen_missing=true;power_text=" mWakefulness=Awake\n";clock_fail=true;assert(!ios_read_screen_on(&io));
    puts("screen: cached on/off, cold unknown, invalid state, failed pipe and retry throttle PASS");
}
static void test_backlight(void) {
    reset();assert(ios_backlight_initialize(&io));assert(strstr(ios_backlight_brightness(),"panel0-backlight"));
    original_panel=false;alternatives=1;assert(ios_backlight_initialize(&io));
    assert(!strcmp(ios_backlight_brightness(),"/sys/class/backlight/panel1/brightness"));
    assert(!strcmp(ios_backlight_power(),"/sys/class/backlight/panel1/bl_power"));
    assert(!strcmp(ios_backlight_maximum(),"/sys/class/backlight/panel1/max_brightness"));
    alternatives=2;assert(!ios_backlight_initialize(&io));assert(!*ios_backlight_brightness());
    alternatives=0;assert(!ios_backlight_initialize(&io));
    alternatives=256;assert(!ios_backlight_initialize(&io));
    reset();assert(strstr(ios_backlight_brightness(),"panel0-backlight"));
    puts("backlight: original preference, unique alternate, coherent paths, ambiguous/missing rejection PASS");
}
static void test_ndk(void) {
    char scratch[256]={0};
    for(unsigned i=0;i<4;i++) {
        reset();front_enable=i&1?-1:0;back_enable=i&2?-1:0;
        ios_business_sensor_init(&io,scratch);
        assert(ios_state.g_ndk_ok==(i!=3));
        assert(ios_state.g_back_ok==!(i&2));
        assert((ios_state.g_front_sensor!=NULL)==!(i&1));
        assert(ios_state.g_lux_valid==0); /* Detection/enabling is not an event. */
        assert(ios_state.g_light_sensors_enabled==(i!=3));
        assert(ios_scene_reporting_mode(true)==(i&1?-1:1));
        assert(ios_scene_reporting_mode(false)==(i&2?-1:0));
    }
    reset();front_present=false;ios_business_sensor_init(&io,scratch);
    assert(ios_state.g_ndk_ok && ios_state.g_back_ok);
    ios_state.fn_disable=DISABLE;ios_light_sensors_set(&io,0);
    assert(!ios_state.g_light_sensors_enabled);back_enable=-1;ios_light_sensors_set(&io,1);
    assert(!ios_state.g_light_sensors_enabled && !ios_state.g_sensor_rate_us);
    back_enable=0;ios_light_sensors_set(&io,1);assert(ios_state.g_light_sensors_enabled);
    puts("NDK: four front/back enable combinations, back-only and failed/successful resume PASS");
}
static void temporal_setup(void) {
    reset();ios_state.g_lux_valid=1;ios_state.g_target_debounce_init=1;ios_state.g_target_debounce_br=1000;
}
static int temporal_sample(int target,uint64_t stamp) {
    now=stamp;ios_state.g_last_lux_event_ms=stamp;
    return ios_business_debounce(&ios_state,stamp,target,1000,20,false);
}
static void test_temporal(void) {
    temporal_setup();uint64_t start=now;
    for(unsigned i=0;i<120;i++) assert(temporal_sample(1000+(i%2?25:-25),start+i*1000)==1000);
    temporal_setup();start=now;int accepted=1000;
    for(unsigned i=0;i<10;i++)accepted=temporal_sample(1200+((int)(i%3)-1)*3,start+i*1000);
    assert(accepted>=1197 && accepted<=1203);
    temporal_setup();start=now;
    assert(temporal_sample(1200,start)==1000);
    assert(temporal_sample(1200,start+1000)==1000);
    assert(temporal_sample(1200,start+2000)==1000);
    for(unsigned i=3;i<7;i++)assert(ios_temporal_target(&ios_state,start+i*1000,1200,20,false)==1000);
    temporal_setup();start=now;
    for(unsigned i=0;i<30;i++) assert(temporal_sample(i%2?1200:800,start+i*1000)==1000);
    temporal_setup();assert(ios_temporal_target(&ios_state,now,2000,20,false)==2000);
    assert(ios_temporal_target(&ios_state,now,1200,20,true)==1200);
    ios_state.g_sensor_stale=1;assert(ios_temporal_target(&ios_state,now,1200,20,false)==1200);
    ios_state.g_sensor_stale=0;luma_indoor_stability=0;
    assert(ios_temporal_target(&ios_state,now,1200,20,false)==1200);
    puts("temporal: 120 jitter samples held, sustained move accepted, no repeated-event confirmation, reversal and bypass PASS");
}
static void test_config(void) {
    reset();config="indoor_stability=0\n";assert(ios_configuration_reload(&io)==1);assert(!luma_indoor_stability);
    config="indoor_stability=garbage\n";assert(ios_configuration_reload(&io)==0);assert(!luma_indoor_stability);
    config="indoor_stability=1\n";assert(ios_configuration_reload(&io)==1);assert(luma_indoor_stability);
    config="indoor_stability=1junk\n";assert(ios_configuration_reload(&io)==0);assert(luma_indoor_stability);
    puts("config: indoor switch parsed strictly and invalid reload rolls back PASS");
    config="preference_learning=0\ncurve_custom=1\ncurve_points=10,10,10,10,10,10,10,10,10,10,10,10,10,10\n";
    ios_state.g_target_debounce_init=1;ios_state.g_target_candidate_dir=1;
    assert(ios_configuration_reload(&io)==1);assert(!luma_preference_learning && luma_curve_custom);
    assert(!ios_state.g_target_debounce_init && !ios_state.g_target_candidate_dir);
    assert(fabsf(ios_brightness_for_lux(100,2.2f)-.1f)<1e-6f);
    ios_state.g_target_debounce_init=1;ios_state.g_target_candidate_dir=-1;
    assert(ios_configuration_reload(&io)==1);
    assert(ios_state.g_target_debounce_init==1 && ios_state.g_target_candidate_dir==-1);
    config="preference_learning=1\ncurve_custom=0\npreference_offset=nan\n";
    assert(ios_configuration_reload(&io)==0 && !luma_preference_learning && luma_curve_custom);
    config="preference_learning=1\ncurve_custom=1\ncurve_points=1,2,3\n";
    assert(ios_configuration_reload(&io)==0 && !luma_preference_learning && luma_curve_custom);
    assert(ios_state.g_target_debounce_init==1 && ios_state.g_target_candidate_dir==-1);
    config="preference_revision=1.5\n";assert(ios_configuration_reload(&io)==0);
    config="preference_learning=2\n";assert(ios_configuration_reload(&io)==0);
    puts("config: preference default/disable, curve parse and transactional invalid reload rollback PASS");
}
static void scene_event(bool front, float lux, uint64_t stamp) {
    if (front) { ios_state.g_front_lux=lux;ios_state.g_front_lux_event_ms=stamp;ios_state.g_front_sensor_timestamp_ns=stamp*1000000; }
    else { ios_state.g_back_lux=lux;ios_state.g_back_lux_event_ms=stamp;ios_state.g_back_sensor_timestamp_ns=stamp*1000000; }
    ios_scene_observe(front,lux,(int64_t)stamp*1000000,stamp);
}
static uint64_t scene_setup(float front, float back) {
    reset();ios_state.g_front_sensor=(void *)11;ios_state.g_back_sensor=(void *)12;
    ios_state.g_ndk_ok=ios_state.g_back_ok=ios_state.g_light_sensors_enabled=ios_state.g_lux_valid=1;
    strcpy(ios_state.g_lux_source,"front");
    scene_event(true,front,now);scene_event(false,back,now);
    float selected=front;assert(!ios_scene_filter(&ios_state,now,&selected));assert(selected==front);
    return now;
}
static void test_scene(void) {
    uint64_t start=scene_setup(200,40);float raw;
    scene_event(true,205,start+1000);scene_event(false,40,start+1000);raw=205;
    assert(!ios_scene_filter(&ios_state,start+1000,&raw) && raw==200);
    scene_event(true,200,start+2000);scene_event(false,10000,start+2000);raw=200;
    assert(!ios_scene_filter(&ios_state,start+2000,&raw) && raw==200);
    assert(!strcmp(ios_scene_class(),"back_only"));
    puts("scene: front priority, tiny jitter and rear flashlight cannot override front PASS");

    start=scene_setup(200,40);
    scene_event(true,400,start+1000);scene_event(false,40,start+1000);raw=400;
    assert(ios_scene_filter(&ios_state,start+1000,&raw) && raw==200);
    assert(ios_scene_hold_left(start+1000)==2000);
    scene_event(true,400,start+2000);scene_event(false,40,start+2000);raw=400;
    assert(ios_scene_filter(&ios_state,start+2000,&raw));
    scene_event(true,400,start+3000);scene_event(false,40,start+3000);raw=400;
    assert(!ios_scene_filter(&ios_state,start+3000,&raw) && raw==400);
    assert(ios_scene_samples()==3);
    puts("scene: unilateral rise waits 2s, sustained front change eventually accepted PASS");

    start=scene_setup(200,40);
    scene_event(true,1,start+1000);scene_event(false,40,start+1000);raw=1;
    assert(ios_scene_filter(&ios_state,start+1000,&raw) && raw==200);
    scene_event(true,200,start+2000);scene_event(false,40,start+2000);raw=200;
    assert(!ios_scene_filter(&ios_state,start+2000,&raw) && raw==200);
    assert(!ios_scene_hold_left(start+2000));
    puts("scene: transient front occlusion is cancelled before original fast-dark PASS");

    for(unsigned fall=0;fall<2;fall++) {
        start=scene_setup(200,40);float f=fall?20:400,b=fall?4:80;
        scene_event(true,f,start+1000);scene_event(false,b,start+1000);raw=f;
        assert(!ios_scene_filter(&ios_state,start+1000,&raw) && raw==f);
        assert(!strcmp(ios_scene_class(),fall?"common_fall":"common_rise"));
    }
    start=scene_setup(4000,80000);
    scene_event(true,8000,start+1000);scene_event(false,160000,start+1000);raw=8000;
    assert(!ios_scene_filter(&ios_state,start+1000,&raw) && raw==8000);
    puts("scene: paired rise/fall immediate, independent sensor scales and all-lux operation PASS");

    start=scene_setup(200,40);
    scene_event(true,20,start+1000);scene_event(false,80,start+1000);raw=20;
    assert(ios_scene_filter(&ios_state,start+1000,&raw) && raw==200);
    assert(!strcmp(ios_scene_class(),"divergent"));
    start=scene_setup(200,40);
    scene_event(true,400,start+1000);raw=400;assert(ios_scene_filter(&ios_state,start+1000,&raw));
    scene_event(true,400,start+4000);scene_event(false,80,start+4000);raw=400;
    ios_scene_filter(&ios_state,start+4000,&raw);
    assert(strcmp(ios_scene_class(),"common_rise"));
    start=scene_setup(200,40);
    scene_event(true,400,start+1000);scene_event(false,80,start+1000);
    ios_scene_observe(false,80,(int64_t)(start+4000)*1000000,start+1000);raw=400;
    assert(ios_scene_filter(&ios_state,start+1000,&raw)); /* Same arrival, different event time. */
    puts("scene: opposite directions, asynchronous onset and queued timestamp skew are unconfirmed PASS");

    start=scene_setup(200,40);ios_scene_mode(true,1);ios_scene_mode(false,1);
    assert(ios_scene_sensor_fresh(&ios_state,true,start+60000));
    assert(ios_state.g_front_lux_event_ms==start); /* No invented fresh sample. */
    ios_scene_mode(true,0);assert(!ios_scene_sensor_fresh(&ios_state,true,start+8001));
    ios_scene_mode(true,-1);assert(!ios_scene_sensor_fresh(&ios_state,true,start+8001));
    ios_scene_mode(true,1);ios_state.g_light_sensors_enabled=0;
    assert(!ios_scene_sensor_fresh(&ios_state,true,start+8001));
    ios_state.g_light_sensors_enabled=1;
    ios_state.g_back_fallback_confirm_count=1;
    assert(!ios_scene_back_confirmed(&ios_state,start+999));
    assert(ios_scene_back_confirmed(&ios_state,start+1000));
    assert(ios_state.g_back_fallback_confirm_count==1);
    scene_event(true,400,start+1000);raw=400;
    assert(ios_scene_filter(&ios_state,start+1000,&raw));raw=400;
    assert(!ios_scene_filter(&ios_state,start+3000,&raw) && raw==400);
    assert(ios_scene_samples()==1); /* On-change persistence, explicitly not two samples. */
    ios_scene_reset(false);assert(ios_scene_reporting_mode(true)==1);
    raw=400;assert(!ios_scene_filter(&ios_state,start+4000,&raw));
    assert(!strcmp(ios_scene_class(),"stable"));
    scene_event(true,800,start+5000);raw=800;
    assert(ios_scene_filter(&ios_state,start+5000,&raw) && raw==400);
    ios_scene_reset(true);assert(ios_scene_reporting_mode(true)==-1);
    puts("scene: confirmed on-change validity, no fabricated timestamp/count, continuous/unknown timeout and reset PASS");

    temporal_setup();ios_state.g_last_lux_event_ms=now;
    assert(ios_temporal_target(&ios_state,now,1200,10000,false)==1000);
    assert(ios_temporal_target(&ios_state,now,1200,10000,true)==1200);
    puts("scene: temporal confirmation extends beyond indoor lux; protection bypass preserved PASS");
}
static void smoothing_setup(void) {
    scene_setup(200,40);ios_scene_mode(true,1);
    ios_state.g_last_lux_event_ms=ios_state.g_last_processed_lux_event_ms=now;
    ios_state.cfg_alpha_fast=ios_state.cfg_alpha_mid=ios_state.cfg_alpha_slow=.18f;
}
static void test_timed_smoothing(void) {
    smoothing_setup();uint64_t start=now;float raw=200,smooth=100;
    assert(ios_scene_smooth_tick(&ios_state,start,smooth,&raw)==100);
    for(unsigned i=1;i<=4;i++)smooth=ios_scene_smooth_tick(&ios_state,start+i*250,smooth,&raw);
    float quarter_steps=smooth;assert(smooth>117.99f && smooth<118.01f);
    assert(ios_state.g_front_lux_event_ms==start && ios_state.g_last_processed_lux_event_ms==start);
    smoothing_setup();start=now;raw=200;smooth=100;
    ios_scene_smooth_tick(&ios_state,start,smooth,&raw);
    smooth=ios_scene_smooth_tick(&ios_state,start+1000,smooth,&raw);
    assert(fabsf(smooth-quarter_steps)<.001f);
    for(unsigned i=2;i<=100;i++) {
        float next=ios_scene_smooth_tick(&ios_state,start+i*1000,smooth,&raw);
        assert(next>=smooth && next<=raw);smooth=next;
    }
    assert(smooth==200);
    assert(ios_state.g_front_lux_event_ms==start && ios_scene_samples()==0);
    assert(ios_scene_smooth_tick(&ios_state,start+99900,smooth,&raw)==smooth);
    ios_state.g_sensor_stale=1;raw=300;assert(ios_scene_smooth_tick(&ios_state,start+101000,smooth,&raw)==smooth);
    puts("on-change smoothing: cadence equivalence, monotone convergence, clock reversal/stale hold, no invented events PASS");
    smoothing_setup();start=now;raw=200;smooth=100;
    ios_scene_smooth_tick(&ios_state,start,smooth,&raw);
    scene_event(true,205,start+1000);raw=205;
    assert(!ios_scene_filter(&ios_state,start+1000,&raw) && raw==200);
    ios_state.g_last_lux_event_ms=ios_state.g_last_processed_lux_event_ms=start+1000;
    ios_scene_smooth_tick(&ios_state,start+1000,smooth,&raw);
    assert(ios_scene_smooth_tick(&ios_state,start+2000,smooth,&raw)>smooth);
    puts("on-change smoothing: accepted input continues converging through a deadband event PASS");

    start=scene_setup(0,0);ios_scene_mode(true,1);
    ios_state.g_last_lux_event_ms=ios_state.g_last_processed_lux_event_ms=start;
    ios_state.g_zero_lux_suspect=1;ios_state.g_zero_lux_since=start;ios_state.g_zero_lux_recent_mask=1;
    raw=92;smooth=100;ios_scene_smooth_tick(&ios_state,start,smooth,&raw);
    assert(ios_scene_smooth_tick(&ios_state,start+3499,smooth,&raw)==100);
    assert(ios_state.g_zero_lux_suspect==1 && raw==92);
    ios_scene_smooth_tick(&ios_state,start+3500,smooth,&raw);
    assert(ios_state.g_zero_lux_suspect==0 && raw==0);
    assert(ios_state.g_zero_lux_recent_mask==1 && !ios_state.fast_dark);

    start=scene_setup(100,20);ios_scene_mode(true,1);
    ios_state.g_last_lux_event_ms=ios_state.g_last_processed_lux_event_ms=start;
    ios_state.g_low_lux_bright_spike_guard=1;ios_state.g_low_lux_bright_spike_since=start;
    ios_state.g_low_lux_bright_spike_raw=100;ios_state.g_low_lux_bright_spike_count=1;
    raw=7.75f;smooth=7;ios_scene_smooth_tick(&ios_state,start,smooth,&raw);
    assert(ios_scene_smooth_tick(&ios_state,start+5999,smooth,&raw)==7);
    assert(ios_state.g_low_lux_bright_spike_guard==1);
    ios_scene_smooth_tick(&ios_state,start+6000,smooth,&raw);
    assert(!ios_state.g_low_lux_bright_spike_guard && raw==100);
    assert(ios_state.g_low_lux_rise_confirmed_samples==1 && ios_state.g_low_lux_rise_confirmed_until==start+12000);
    assert(ios_state.g_last_lux_event_ms==start);
    puts("on-change guards: real-time zero/spike confirmation, actual sample counts and limited-rise window PASS");
}
int main(void) {
    test_fallback();test_screen();test_backlight();test_ndk();test_temporal();test_config();test_scene();test_timed_smoothing();
    puts("ALL PASS");return 0;
}
