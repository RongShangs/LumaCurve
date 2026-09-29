/* Execute production C with controlled libc/NDK boundaries. No device IO. */
#include "business_api.h"
#include "state_observer.h"
#include "temporal_stability.h"
#include <assert.h>
#include <stdio.h>

unsigned baseline_sensor_poll(DomainIo *io);
int baseline_write_state_frame(const char *, int, int, int, int, const char *, uint64_t, float, float);
int ios_write_state_frame(const char *, int, int, int, int, const char *, uint64_t, float, float);
static uint64_t now = 100000;
static const char *settings_text, *settings_cursor;
static int pipe_status, pipe_calls, clock_calls, ndk_calls, fwrite_calls;
static bool fail_open, fail_write, fail_close, fail_rename, lease;
static uint64_t lease_wall;
static char pending[32768], published[32768];
static size_t pending_length;
typedef struct Event { int32_t version,sensor,type,reserved; int64_t timestamp; float data[16]; uint8_t tail[16]; } Event;
static Event events[40];
static unsigned event_count, event_index, chunk_limit = 16;
uint64_t ios_battery_refresh(DomainIo *io, uint64_t stamp) { (void)io; return stamp; }
static void append(const char *text, size_t count) {
    assert(pending_length+count<sizeof(pending));
    memcpy(pending+pending_length,text,count);pending_length+=count;pending[pending_length]=0;
}
uint64_t ios_native_call(DomainIo *io, unsigned kind, const uint64_t *a, size_t count) {
    (void)count;
    switch (kind) {
    case IOS_I_clock_gettime: {
        int64_t *t=(int64_t *)(uintptr_t)a[1];t[0]=now/1000;t[1]=(now%1000)*1000000;clock_calls++;return 0;
    }
    case IOS_I_stat:
        if (lease && strstr((const char *)(uintptr_t)a[0],"_ui_watch")) {
            memcpy((char *)(uintptr_t)a[1]+88,&lease_wall,8);return 0;
        }
        return (uint64_t)-1;
    case IOS_I_time:return UINT64_C(1700000000)+now/1000;
    case IOS_I_popen:
        pipe_calls++;settings_cursor=settings_text;return settings_text?100:0;
    case IOS_I_fgets: {
        if (!settings_cursor || !*settings_cursor) return 0;
        char *dst=(char *)(uintptr_t)a[0];unsigned n=0;
        while (n+1<a[1] && *settings_cursor) {
            char c=*settings_cursor++;dst[n++]=c;if(c=='\n')break;
        }
        dst[n]=0;return a[0];
    }
    case IOS_I_pclose:return pipe_status;
    case IOS_I_fopen:
        if(fail_open)return 0;
        pending_length=0;pending[0]=0;return 42;
    case IOS_I_fprintf: {
        const char *fmt=(const char *)(uintptr_t)a[1];char line[32768];int n;
        if(strstr(fmt,"%s"))n=snprintf(line,sizeof(line),fmt,(const char *)(uintptr_t)a[2]);
        else if(strstr(fmt,"%llu"))n=snprintf(line,sizeof(line),fmt,(unsigned long long)a[2]);
        else if(strstr(fmt,"%d"))n=snprintf(line,sizeof(line),fmt,(int32_t)a[2]);
        else n=snprintf(line,sizeof(line),fmt,io->reals[0]);
        assert(n>=0 && (size_t)n<sizeof(line));append(line,n);return n;
    }
    case IOS_I_fwrite: {
        fwrite_calls++;size_t n=a[1]*a[2];if(fail_write)n/=2;append((const char *)(uintptr_t)a[0],n);return n/a[1];
    }
    case IOS_I_fclose:return fail_close?(uint64_t)-1:0;
    case IOS_I_rename:
        if(fail_rename)return (uint64_t)-1;
        memcpy(published,pending,pending_length+1);return 0;
    case IOS_I_unlink:pending_length=0;pending[0]=0;return 0;
    case IOS_I_usleep:now+=a[0]/1000;return 0;
    default:fprintf(stderr,"Unexpected import %u\n",kind);abort();
    }
}
uint64_t ios_native_dynamic(uint64_t fn, const uint64_t *a, size_t count) {
    (void)fn;(void)count;ndk_calls++;
    unsigned n=event_count-event_index;if(n>a[2])n=a[2];if(n>chunk_limit)n=chunk_limit;
    memcpy((void *)(uintptr_t)a[1],events+event_index,n*sizeof(Event));event_index+=n;return n;
}
static void settings_case(const char *text, int status, int expect_mode, float expect_adj, int expect_slider) {
    DomainIo io={0};ios_reset_data();now+=70000;
    ios_state.cached_auto=1;ios_state.cached_auto_adj=.25f;ios_state.cached_auto_adj_valid=1;ios_state.cached_slider=42;
    settings_text=text;pipe_status=status;pipe_calls=0;
    ios_settings_refresh(&io);
    assert(ios_state.cached_auto==expect_mode);
    assert(ios_state.cached_auto_adj==expect_adj);
    assert(ios_state.cached_slider==expect_slider);
    assert(ios_state.cached_auto_adj_valid==1);
    assert(pipe_calls==1);
    ios_settings_refresh(&io);assert(pipe_calls==1);
}
static void test_settings(void) {
    settings_case("0\n0.5\n128\n",0,0,.5f,128);
    settings_case("1\n-1.005\n16383\n",0,1,-1.f,255);
    settings_case("null\nnull\nnull\n",0,1,.25f,42);
    settings_case("1junk\nNaN\n255junk\n",0,1,.25f,42);
    settings_case("2\ninf\n-1\n",0,1,.25f,42);
    settings_case("9999999999999999999999999999999\n2\n9999999999999999999999999999999\n",0,1,.25f,42);
    settings_case("0\n0.5\n128\n",1,1,.25f,42);
    settings_case("0\n0.5\n",0,1,.25f,42);
    settings_case(NULL,0,1,.25f,42);
    settings_case(" \t0 \n -0.5 \n 0 \n",0,0,-.5f,0);
    puts("settings: 10 invalid/valid/cache/exit-status cases PASS");
}
static unsigned random_word=20260929;
static unsigned next_random(void) {random_word=random_word*1664525u+1013904223u;return random_word;}
static void sensor_reset(void) {
    ios_reset_data();ios_state.g_ndk_ok=1;ios_state.g_eq=(void *)(uintptr_t)1;ios_state.fn_getEvents=1;
    event_index=0;clock_calls=0;ndk_calls=0;
}
static void test_sensors(void) {
    DomainIo io={0};
    for(unsigned trial=0;trial<200;trial++) {
        event_count=trial%33;chunk_limit=1+trial%16;
        for(unsigned i=0;i<event_count;i++) {
            memset(&events[i],0,sizeof(Event));
            unsigned r=next_random();events[i].type=(int[]){5,8,0x1fa266f,99}[r%4];
            events[i].timestamp=(int64_t)(r%7)*1000;
            events[i].data[0]=(float)(r%300000);
            if(i%11==0)events[i].data[0]=NAN;
            if(i%13==0)events[i].data[0]=-1;
            if(i%7==0)events[i].data[0]=0;
        }
        sensor_reset();unsigned old=baseline_sensor_poll(&io);IosState baseline=ios_state;int clocks=clock_calls, calls=ndk_calls;
        sensor_reset();unsigned current=ios_sensor_poll(&io);
        assert(current==old && current<=16);assert(!memcmp(&baseline,&ios_state,sizeof(baseline)));
        assert(clock_calls==clocks);assert(ndk_calls<=calls);
    }
    puts("sensors: 200 ordered/partial/burst/invalid/duplicate/proximity baseline comparisons PASS");
}
static void test_state(void) {
    for(unsigned i=0;i<100;i++) {
        ios_reset_data();luma_indoor_stability=0;now+=1000;ios_state.g_max=16383;
        ios_state.cached_auto=i%2;ios_state.g_front_lux=i*.125f;ios_state.g_back_lux=i*2.7f;
        ios_state.g_front_lux_event_ms=now-700;ios_state.g_back_lux_event_ms=now-200;
        ios_state.g_cached_battery_cap=i;ios_state.g_cached_charging=i%2;
        ios_state.hl_active=i%2;ios_state.g_actuator_fast_dark=i%3;
        baseline_write_state_frame(i%2?"auto":"manual",1,154,471,1000,"中文原因",now,i*.1f,i*.11f);
        char old[32768];strcpy(old,published);IosState before=ios_state;fwrite_calls=0;
        assert(ios_write_state_frame(i%2?"auto":"manual",1,154,471,1000,"中文原因",now,i*.1f,i*.11f)==0);
        if(strcmp(old,published)) {
            size_t at=0;while(old[at]==published[at])at++;
            fprintf(stderr,"Frame %u difference at %zu: old %.100s | new %.100s\n",i,at,old+at,published+at);
        }
        assert(!strcmp(old,published));assert(!memcmp(&before,&ios_state,sizeof(before)));assert(fwrite_calls==1);
    }
    char good[32768];strcpy(good,published);
    for(unsigned failure=0;failure<4;failure++) {
        fail_open=failure==0;fail_write=failure==1;fail_close=failure==2;fail_rename=failure==3;
        assert(ios_write_state_frame("auto",1,0,1,1000,"error",now,0,0)!=0);
        assert(!strcmp(good,published));
    }
    fail_open=fail_write=fail_close=fail_rename=false;
    char huge[20000];memset(huge,'x',sizeof(huge)-1);huge[sizeof(huge)-1]=0;
    assert(ios_write_state_frame("auto",1,0,1,1000,huge,now,0,0)!=0);assert(!strcmp(good,published));
    luma_indoor_stability=1;IosState saved=ios_state;
    assert(ios_write_state_frame("auto",1,154,471,1000,"scene",now,200,200)==0);
    assert(strstr(published,"scene_relation=single\n"));
    assert(strstr(published,"scene_hold_left_ms=0\n"));
    assert(strstr(published,"front_reporting_mode=-1\n"));
    assert(!memcmp(&saved,&ios_state,sizeof(saved)));
    puts("state: 100 byte-identical frames, pure snapshots, bounded overflow and 4 IO failure cases PASS");
}
static void test_observer(void) {
    DomainIo io={0};ios_reset_data();ios_state.cached_auto=1;now+=10000;
    lease=true;lease_wall=UINT64_C(1700000000)+now/1000;
    assert(ios_state_observer_active(&io,now));
    ios_state.g_external_write_hold=1;ios_state.g_external_write_hold_until=now-1;
    IosState before=ios_state;
    ios_state_observer_tick(&io,now,1,471,154,1000,7.139f,7.705f);
    assert(strstr(published,"target_br=154\n"));assert(strstr(published,"current_br=471\n"));
    assert(!memcmp(&before,&ios_state,sizeof(before)));
    char saved[32768];strcpy(saved,published);
    ios_state_observer_tick(&io,now+500,1,300,154,1000,7.f,7.f);assert(!strcmp(saved,published));
    now+=1000;ios_state_observer_tick(&io,now,1,300,154,1000,7.f,7.f);
    assert(strstr(published,"current_br=300\n"));
    strcpy(saved,published);now+=1000;ios_state_observer_tick(&io,now,0,100,154,1000,7.f,7.f);
    assert(!strcmp(saved,published));
    now+=5000;assert(!ios_state_observer_active(&io,now));
    ios_state_observer_tick(&io,now,1,100,154,1000,7.f,7.f);assert(!strcmp(saved,published));
    lease=false;
    now+=1000;lease=true;lease_wall=UINT64_C(1700000000)+now/1000;
    uint64_t start=now;
    ios_state_observer_wait(&io,6000,1,300,154,3000,7.f,7.f);
    assert(now-start==6000);assert(!memcmp(&before,&ios_state,sizeof(before)));
    assert(!ios_state_observer_active(&io,now));lease=false;
    puts("observer: refresh, rate limit, lease expiry, screen-off and no control-state mutation PASS");
}
int main(void) {test_settings();test_sensors();test_state();test_observer();puts("ALL PASS");return 0;}
