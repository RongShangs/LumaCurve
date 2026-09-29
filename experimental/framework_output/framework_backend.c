/* Isolated C-to-framework broker adapter. No sysfs mutation in this backend. */
#define _GNU_SOURCE 1
#include "business_api.h"
#include "framework_backend.h"
#include "brightness_preference.h"
#include "actuator_diagnostics.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include <errno.h>
#include <stdlib.h>
#include <ctype.h>
#include <math.h>
#include "framework_protocol.h"

LumaFrameworkSnapshot luma_framework_snapshot = {.mode=-1,.node=-1,.goal=-1,.limited=-1};
static const char *socket_name, *run_directory;
static int requested_ownership;
static uint64_t last_pulse_ms;
static uint64_t sequence;
static float scene_lux(void) {
    float lux=FLOAT(g_actuator_smooth_lux);
    return FLAG(g_lux_valid)&&isfinite(lux)&&lux>=0?lux:-1;
}
static uint64_t clock_ms(void) {
    struct timespec t; if(clock_gettime(CLOCK_MONOTONIC,&t))return 0;
    return (uint64_t)t.tv_sec*1000+(uint64_t)t.tv_nsec/1000000;
}
int luma_framework_initialize(void) {
    socket_name=getenv("LUMA_FRAMEWORK_SOCKET");run_directory=getenv("LUMA_FRAMEWORK_RUN");
    const char *prefix="/data/local/tmp/luma-framework-core.";
    if(!socket_name||!run_directory||strncmp(run_directory,prefix,strlen(prefix))||strlen(run_directory)<=strlen(prefix)||
       strlen(socket_name)>90||!strlen(socket_name)||strchr(run_directory,'\n')||strstr(run_directory,"..")) {errno=EINVAL;return -1;}
    for(const char *p=socket_name;*p;p++)if(!isalnum((unsigned char)*p)&&!strchr("._-",*p)){errno=EINVAL;return -1;}
    return 0;
}
const char *luma_framework_path(const char *path,char *buffer,size_t capacity) {
    if(getenv("LUMA_FRAMEWORK_PRODUCTION") && !strcmp(getenv("LUMA_FRAMEWORK_PRODUCTION"),"1"))return path;
    const char *mapped=luma_framework_map_path(run_directory,path,buffer,capacity);
    if(!mapped)errno=EINVAL;
    return mapped;
}
static int rpc(const char *command) {
    if(!socket_name){errno=ENOTCONN;return -1;}
    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0)return -1;
    struct timeval timeout={.tv_sec=1,.tv_usec=0};
    setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
    struct sockaddr_un address={.sun_family=AF_UNIX};
    memcpy(address.sun_path+1,socket_name,strlen(socket_name));
    int error=0,result=-1;
    if(connect(fd,(struct sockaddr *)&address,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+1+strlen(socket_name)))){error=errno;goto end;}
    struct ucred peer;socklen_t peer_size=sizeof(peer);
    if(getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&peer,&peer_size)||peer.uid!=0){error=EPERM;goto end;}
    size_t n=strlen(command),sent=0;
    while(sent<n){ssize_t k=send(fd,command+sent,n-sent,MSG_NOSIGNAL);if(k<=0){error=errno;goto end;}sent+=(size_t)k;}
    char text[512];size_t used=0;
    while(used<sizeof(text)-1){ssize_t k=recv(fd,text+used,1,0);if(k!=1){error=errno?errno:EIO;goto end;}if(text[used++]=='\n')break;}
    text[used]=0;
    LumaFrameworkSnapshot v={0};
    if(luma_framework_parse(text,&v)) {error=EPROTO;goto end;}
    v.sampled_ms=clock_ms();luma_framework_snapshot=v;result=0;
end:
    close(fd);if(result<0)errno=error?error:EIO;return result;
}
int luma_framework_refresh(void){return rpc("Q\n");}
int luma_framework_ready(void){return requested_ownership&&luma_framework_snapshot.active&&luma_framework_snapshot.mode==1&&luma_framework_snapshot.on&&!luma_framework_snapshot.window;}
int luma_framework_acquire(void){if(rpc("A\n"))return -1;requested_ownership=luma_framework_snapshot.active;return requested_ownership?0:-1;}
int luma_framework_release(void){requested_ownership=0;last_pulse_ms=0;return rpc("X\n");}
void luma_framework_pulse(void) {
    if(!requested_ownership)return;
    uint64_t now=clock_ms();
    if(INT(cached_auto)!=1||INT(g_proximity_near)||strcmp(STRING(g_brightness_owner),"daemon")||now<TIME(g_wake_readonly_until)){
        (void)luma_framework_release();return;
    }
    if(last_pulse_ms&&now>=last_pulse_ms&&now-last_pulse_ms<1000)return;
    char command[64];
    snprintf(command,sizeof(command),"P %.3f\n",scene_lux());
    if(rpc(command)){requested_ownership=0;SET_INT(cached_auto,-1);return;}
    last_pulse_ms=now;
    SET_INT(cached_auto,luma_framework_snapshot.mode);
}
uint64_t luma_framework_apply(uint64_t now,int32_t *current,int32_t target) {
    char command[128];int maximum=INT(g_max);
    if(maximum<1||target<0||target>maximum){errno=EINVAL;return now;}
    if(!luma_framework_ready()&&INT(cached_auto)==1&&!INT(g_proximity_near)&&
       !strcmp(STRING(g_brightness_owner),"daemon")&&now>=TIME(g_wake_readonly_until))
        (void)luma_framework_acquire();
    snprintf(command,sizeof(command),"T %llu %d %d %.3f\n",(unsigned long long)++sequence,target,maximum,scene_lux());
    luma_write_attempts++;
    int result=rpc(command);
    if(result<0||!luma_framework_ready()){
        SET_INT(g_last_write_result,-1);luma_write_errno=result<0?errno:EACCES;luma_write_stage="framework_refused";
        /* A/T can be refused during a user-slider handoff. Soft release keeps
         * the broker's pending capture while dropping a stale C ownership. */
        if(requested_ownership){requested_ownership=0;last_pulse_ms=0;(void)rpc("R\n");}
    } else {
        *current=luma_framework_snapshot.node;SET_INT(g_last_write_readback,*current);SET_INT(g_last_write_result,0);
        luma_write_successes++;luma_write_errno=0;luma_write_stage="framework_rpc_accepted";
        memcpy(STRING(g_write_gov_reason),"framework_request",18);
    }
    luma_write_request=target;luma_write_bytes=-1;
    SET_INT(g_tr_3,0);SET_INT(g_tr_1,target);
    SET_FLAG(g_transition_active,luma_framework_snapshot.request!=luma_framework_snapshot.limited);
    SET_TIME(g_last_write_ms,now);return now;
}
