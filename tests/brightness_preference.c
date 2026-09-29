/* Production preference/curve logic with bounded, failure-capable mocked IO. */
#include "brightness_preference.h"
#include "brightness_curve.h"
#include "business_api.h"
#include "log_timestamp.h"
#include <assert.h>
#include <stdlib.h>
#include <math.h>
IosState ios_state;
static char record[2048], temporary[2048], event[2048];
static const char *cursor;
static int failure, probes, renames;
uint64_t ios_native_call(DomainIo *io, unsigned kind, const uint64_t *a, size_t n) {
    (void)io; (void)n;
    switch (kind) {
    case IOS_I_fopen:
        assert(strstr((char *)(uintptr_t)a[0], "luma_curve_preference"));
        if (!strcmp((char *)(uintptr_t)a[1],"r")) { cursor=record; return *record ? 1 : 0; }
        temporary[0]=0; return failure==1 ? 0 : 2;
    case IOS_I_fgets: {
        if (!cursor || !*cursor) return 0;
        char *out=(char *)(uintptr_t)a[0]; size_t count=0;
        while (*cursor && count+1<a[1]) { out[count++]=*cursor++; if(out[count-1]=='\n')break; }
        out[count]=0; return a[0]; }
    case IOS_I_fclose:return failure==3 ? (uint64_t)-1 : 0;
    case IOS_I_chmod:return failure==4 ? (uint64_t)-1 : 0;
    case IOS_I_fwrite: {
        size_t count=(size_t)a[2]; assert(count<sizeof(temporary));
        if (failure==2) count--;
        memcpy(temporary,(void *)(uintptr_t)a[0],count); temporary[count]=0; return count; }
    case IOS_I_rename:renames++;if(failure==5)return (uint64_t)-1;strcpy(record,temporary);return 0;
    case IOS_I_unlink:temporary[0]=0;return 0;
    case IOS_I_popen:probes++;cursor=event;return *event?3:0;
    case IOS_I_pclose:return failure==6?(uint64_t)-1:0;
    default:fprintf(stderr,"unexpected import %u\n",kind);abort();
    }
}
uint64_t ios_native_dynamic(uint64_t f,const uint64_t*a,size_t n){(void)f;(void)a;(void)n;abort();}
static DomainIo io;
static void ready(void) {
    memset(&ios_state,0,sizeof(ios_state));ios_state.g_max=10000;
    ios_state.g_actuator_smooth_lux=100;ios_state.cached_auto=1;ios_state.g_lux_valid=1;strcpy(ios_state.g_brightness_owner,"daemon");
    luma_preference_reset();luma_curve_reset();failure=0;probes=0;renames=0;
    record[0]=event[0]=0;luma_preference_learning=1;luma_preference_configure(&io);
}
static void observation(unsigned id,float from,float to,bool user,int mode,uint64_t now) {
    snprintf(event,sizeof(event),"09-29 12:00:%02u.000 - BrightnessEvent: brt=%.4f%s (20%%), nits=20, lux=100, reason=automatic, state=ON, initBrt=%.4f, autoBrightness=true (default), logicalId=0\n",id,to,user?"(user_set)":"",from);
    luma_preference_settings(&io,now,mode,(float)id/100,(long)id);
}
static void train(unsigned id,float from,float to,uint64_t now) {
    observation(id*2,from,to,true,1,now);luma_preference_apply(&io,now,1000);
    observation(id*2+1,from,to,true,1,now+6000);luma_preference_apply(&io,now+6000,1000);
    luma_preference_apply(&io,now+14000,1000);
}
int main(int argc,char **argv) {
    if (argc>1 && !strcmp(argv[1],"--curve")) {
        luma_curve_reset();for(unsigned i=0;i<=200;i++){float lux=powf(100001,(float)i/200)-1;printf("%.6f %.6f\n",lux,ios_brightness_for_lux(lux,2.2f)*100);}return 0;
    }
    ready();observation(0,.3f,.4f,true,1,10000);assert(!luma_preference_pending());
    observation(1,.3f,.6f,false,1,20000);
    observation(2,.3f,.6f,false,1,26000);luma_preference_apply(&io,34000,1000);
    assert(luma_preference_samples()==0 && luma_preference_offset()==0);
    observation(2,.3f,.6f,true,0,30000);assert(!luma_preference_pending());
    event[0]=0;luma_preference_settings(&io,40000,1,.04f,4);assert(!luma_preference_pending());
    puts("intent: startup history, automatic writes, manual-mode and missing evidence do not learn PASS");
    observation(5,.3f,.45f,true,1,50000);luma_preference_apply(&io,50000,1000);
    observation(6,.45f,.6f,true,1,52000);luma_preference_apply(&io,59999,1000);assert(luma_preference_samples()==0);
    observation(7,.45f,.6f,true,1,57000);luma_preference_apply(&io,64999,1000);assert(luma_preference_samples()==0);
    int applied=luma_preference_apply(&io,65000,1000);
    assert(applied==1000 && luma_preference_samples()==1 && luma_preference_offset()==0);
    float base5=luma_curve_base_point(5);
    assert(fabsf(luma_preference_point(5,base5)-base5*1.0025f)<1e-7f);
    for(unsigned i=0;i<14;i++) if(i!=5) assert(luma_preference_point(i,luma_curve_base_point(i))==luma_curve_base_point(i));
    assert(*record && !luma_preference_dirty());
    train(8,.6f,.3f,70000);assert(luma_preference_samples()==1);
    train(9,.6f,.3f,140000);assert(luma_preference_samples()==2);
    assert(luma_preference_point(5,base5)<base5*1.0025f);
    puts("learning: repeated same-direction gestures, 8-second quiet period, nearest anchor, 0.25% cap and cooldown PASS");
    float learned=luma_preference_point(5,base5);
    luma_preference_configure(&io);assert(luma_preference_point(5,base5)==learned);
    luma_preference_reset();luma_preference_learning=1;luma_preference_configure(&io);
    assert(fabsf(luma_preference_point(5,base5)-learned)<1e-7f && luma_preference_samples()==2);
    luma_preference_learning=0;luma_preference_configure(&io);train(9,.3f,.6f,200000);assert(luma_preference_samples()==2);
    luma_preference_revision=1;luma_preference_configure(&io);
    assert(luma_preference_point(5,base5)==base5 && luma_preference_samples()==0);
    puts("persistence: unrelated reload and restart retain local curve, disabled learning retains shape, explicit curve reset clears learning PASS");
    for (int bad=1;bad<=5;bad++) {
        ready();strcpy(record,"old-safe-record\n");failure=bad;luma_preference_revision=1;luma_preference_configure(&io);
        assert(luma_preference_dirty() && !strcmp(record,"old-safe-record\n"));
        failure=0;luma_preference_apply(&io,10000,1000);assert(!luma_preference_dirty() && strstr(record,"format=2\n"));
    }
    const char *invalid[]={"format=1\noffset=nan\nsamples=1\nconfig_offset=0\nconfig_revision=0\n","format=1\noffset=101\nsamples=1\nconfig_offset=0\nconfig_revision=0\n","format=1\noffset=20\noffset=20\nsamples=1\nconfig_offset=0\nconfig_revision=0\n","format=1\noffset=20\nsamples=1\nconfig_offset=0\nconfig_revision=0"};
    for(unsigned i=0;i<sizeof(invalid)/sizeof(*invalid);i++){ready();strcpy(record,invalid[i]);luma_preference_reset();luma_preference_configure(&io);assert(luma_preference_offset()==0);}
    ready();strcpy(record,"format=1\noffset=3\nsamples=2\nconfig_offset=0\nconfig_revision=0\n");
    luma_preference_reset();luma_preference_learning=1;luma_preference_configure(&io);
    assert(luma_preference_offset()==3 && luma_preference_samples()==0 && strstr(record,"format=2\n"));
    puts("IO/migration: five write failures preserve old record, retry works, corruption rejected, legacy bias preserved without global training PASS");
    for(unsigned guard=0;guard<10;guard++) {
        ready();observation(0,.3f,.4f,false,1,10000);observation(1,.3f,.6f,true,1,20000);
        if(guard==0)ios_state.g_sensor_hold_active=1;if(guard==1)ios_state.g_heat_guard_active=1;
        if(guard==2)ios_state.g_wake_guard_until=40000;if(guard==3)strcpy(ios_state.g_brightness_owner,"system_owned");if(guard==4)ios_state.g_lux_valid=0;
        if(guard==5)ios_state.g_actuator_smooth_lux=500;
        if(guard==6)ios_state.hl_active=1;if(guard==7)ios_state.hl_hbm_active=1;
        if(guard==8)ios_state.g_zero_lux_suspect=1;if(guard==9)ios_state.g_low_lux_bright_spike_guard=1;
        luma_preference_apply(&io,28000,1000);assert(!luma_preference_samples());
    }
    ready();observation(0,.3f,.4f,false,1,10000);
    observation(1,.3f,.6f,true,1,20000);
    observation(2,.6f,.3f,true,1,26000);
    luma_preference_apply(&io,34000,1000);assert(luma_preference_samples()==0);
    observation(3,.3f,.6f,true,1,40000);
    observation(4,.3f,.6f,true,1,46000);
    ios_state.g_actuator_smooth_lux=500;
    luma_preference_apply(&io,54000,1000);assert(luma_preference_samples()==0);
    ready();observation(0,.3f,.4f,false,1,10000);
    for(unsigned i=1;i<=100;i++)train(i,.1f,.9f,20000+(uint64_t)i*80000);
    assert(fabsf(luma_preference_point(5,base5)-base5*1.2f)<1e-7f);
    for(unsigned i=1;i<=160;i++)train(i,.9f,.1f,9000000+(uint64_t)i*80000);
    assert(fabsf(luma_preference_point(5,base5)-luma_curve_base_point(4))<1e-7f); /* neighboring anchor wins over the -20% bound */
    assert(luma_curve_nearest(90)==5 && luma_curve_nearest(7)==2);
    puts("bounds/safety: reversal, scene change, sensor/thermal/wake/owner guards and +/-20% long-term limits PASS");
    float points[14];assert(luma_curve_parse("0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,85",points));
    assert(!luma_curve_parse("0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,nan",points));
    assert(!luma_curve_parse("0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,20",points));
    assert(!luma_curve_parse("0.1,0.2",points));assert(!luma_curve_parse("0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,85,90",points));
    assert(luma_curve_parse("10,10,10,10,10,10,10,10,10,10,10,10,10,10",points));
    memcpy(luma_curve_points,points,sizeof(points));luma_curve_custom=true;
    assert(fabsf(ios_brightness_for_lux(500,2.2f)-.1f)<1e-6f && ios_brightness_for_lux(200000,2.2f)==.1f);
    char text[192];assert(luma_curve_format(text,sizeof(text)));assert(!luma_curve_format(text,4));
    puts("curve: complete ordered anchors, strict finite/range/count validation, equal plateaus, custom interpolation and output bounds PASS");
    FILE *file=tmpfile();assert(file);assert(luma_log_timestamp(file)==0);assert(ftell(file)==0);fclose(file);
    int stamp=luma_log_timestamp(stdout);assert(stamp==22);puts("timestamp: console prefix and unchanged data streams PASS");
    return 0;
}
