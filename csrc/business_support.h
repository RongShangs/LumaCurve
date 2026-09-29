/* Typed IO and byte serialization used by the recovered business flow. */
#pragma once
#include "business_api.h"
#include <stdio.h>
#include <math.h>
typedef struct { char padding[19]; char d_name[256]; } dirent;
typedef void DIR;
typedef struct { int64_t tv_sec, tv_nsec; } timespec;
typedef struct { int second, minute, hour, day, month, year, weekday, yearday, dst; } tm;
typedef int64_t time_t;
typedef unsigned char stat_buffer[128];
typedef void (*__sighandler_t)(int);
#define PRIO_PROCESS 0

static inline uint32_t float_bits(float value) {
    uint32_t bits; memcpy(&bits, &value, 4); return bits;
}
static inline uint64_t word_bits(uint64_t value) { return value; }
#define BITS(value) _Generic((value), float: float_bits, default: word_bits)(value)
#define ABS(value) fabsf(value)
#undef NAN
#define NAN(value) isnan(value)
#define numeric_minimum(a,b) fminf((a),(b))
#define signed_subtraction_overflow(a,b) (((int64_t)(int32_t)(a) - (int64_t)(int32_t)(b)) != (int64_t)(int32_t)((uint32_t)(a)-(uint32_t)(b)))
static inline uint64_t io_read_packed(const void *source, unsigned width) {
    uint64_t value = 0; memcpy(&value, source, width); return value;
}
static inline void io_store_packed(void *destination, unsigned width, uint64_t value) {
    memcpy(destination, &value, width);
}
#define VIEW1(p) (*(uint8_t *)(p))
typedef struct __attribute__((packed,may_alias)) { uint16_t value; } ByteWord16;
typedef struct __attribute__((packed,may_alias)) { uint32_t value; } ByteWord32;
typedef struct __attribute__((packed,may_alias)) { uint64_t value; } ByteWord64;
#define VIEW2(p) (((ByteWord16 *)(p))->value)
#define VIEW3(p) io_read_packed((p), 3)
#define VIEW4(p) (((ByteWord32 *)(p))->value)
#define VIEW5(p) io_read_packed((p), 5)
#define VIEW6(p) io_read_packed((p), 6)
#define VIEW7(p) io_read_packed((p), 7)
#define VIEW8(p) (((ByteWord64 *)(p))->value)

typedef struct { uint64_t word; double real; bool floating; } IoArgument;
static inline IoArgument io_word(uint64_t value) { return (IoArgument){.word=value}; }
static inline IoArgument io_real(double value) { return (IoArgument){.real=value,.floating=true}; }
#define CARG(value) _Generic((value), float: io_real, double: io_real, default: io_word)(value)
static inline int io_print(DomainIo *io, FILE *stream, const char *format,
                                  IoArgument *args, size_t count) {
    uint64_t words[16]; double reals[8]; size_t nw=0, nr=0;
    for (size_t i=0;i<count;i++) {
        if (args[i].floating) reals[nr++]=args[i].real;
        else words[nw++]=args[i].word;
    }
    return domain_print(io,(uintptr_t)stream,format,words,nw,reals,nr);
}
static inline int io_format(DomainIo *io, char *buffer, size_t capacity,
                                   const char *format, IoArgument *args, size_t count) {
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
    uint64_t values[16]; size_t nw=0,nr=0;
    for (size_t i=0;i<count;i++) {
      if (args[i].floating) domain_real_argument(io,(unsigned)nr++,args[i].real);
      else values[nw++]=args[i].word;
    }
    return (int32_t)ios_native_format_values(io,buffer,capacity,format,values,nw);
#else
    uint64_t words[19] = {ARG(buffer),capacity,ARG(format)};
    size_t nw=3, nr=0;
    for (size_t i=0;i<count;i++) {
        if (args[i].floating) domain_real_argument(io,(unsigned)nr++,args[i].real);
        else words[nw++]=args[i].word;
    }
    return (int32_t)domain_call(io,IOS_I_snprintf,words,nw);
#endif
}

#ifdef IOS_PRODUCTION
#define sig_h ios_request_stop
#define sig_learn_reset ios_request_learn_reset
#define sig_reload ios_request_config_reload
#define sig_reload_learn ios_request_learn_reload
#else
#define sig_h ios_sig_h
#define sig_learn_reset ios_sig_learn_reset
#define sig_reload ios_sig_reload
#define sig_reload_learn ios_sig_reload_learn
#endif
#define load_config() ios_configuration_reload(&io)
#define load_learn() ios_learning_load(&io)
#define save_learn() ios_learning_save(&io)
#define hbm_find_node() ios_hbm_discover(&io)
#define refresh_settings() ios_settings_refresh(&io)
#define refresh_power_wakefulness() ios_wakefulness_refresh(&io)
#define refresh_display_state_on_edge() ios_display_refresh(&io)
#define read_screen() ios_read_screen_on(&io)
#define poll_sensors() ios_sensor_poll(&io)
#define read_lux_fallback() api_read_lux_fallback()
#define curve_pct_for_lux(lux) api_curve_pct_for_lux(lux)
#define refresh_battery_state(now) ios_battery_refresh(&io,now)
#define highlux_deactivate(reason) ios_sunlight_deactivate(&io,reason)
#define set_light_sensors_enabled(enabled) ios_light_sensors_set(&io,enabled)
#define trans_update(target,current,now) ios_transition_update(target,current,now)
#define apply_brightness_frame(now,current,target,emergency) ios_frame_apply(&io,now,(int32_t *)current,target,emergency)
#define write_state_file(mode,screen,target,current,poll,reason,now,lux,smooth) ios_write_state_frame(mode,screen,target,current,poll,reason,now,lux,smooth)

#define signal(number,handler) CALL(&io,signal,number,ARG(handler))
#define setpriority(which,who,priority) CALL(&io,setpriority,which,who,priority)
#define fopen(path,mode) ((FILE *)(uintptr_t)CALL(&io,fopen,ARG(path),ARG(mode)))
#define fclose(stream) ((int32_t)CALL(&io,fclose,ARG(stream)))
#define fflush(stream) ((int32_t)CALL(&io,fflush,ARG(stream)))
#define fscanf(stream,format,destination) ((int32_t)IOS_SCAN(&io,stream,format,destination))
#define fgets(buffer,size,stream) ((char *)(uintptr_t)CALL(&io,fgets,ARG(buffer),size,ARG(stream)))
#undef popen
#undef pclose
#define popen(command,mode) ((FILE *)(uintptr_t)CALL(&io,popen,ARG(command),ARG(mode)))
#define pclose(stream) ((int32_t)CALL(&io,pclose,ARG(stream)))
#define chmod(path,mode) ((int32_t)CALL(&io,chmod,ARG(path),mode))
#define access(path,mode) ((int32_t)CALL(&io,access,ARG(path),mode))
#define stat(path,buffer) ((int32_t)CALL(&io,stat,ARG(path),ARG(buffer)))
#define clock_gettime(clock,buffer) ((int32_t)CALL(&io,clock_gettime,clock,ARG(buffer)))
#define getpid() ((int32_t)CALL(&io,getpid))
#define usleep(usec) ((int32_t)CALL(&io,usleep,usec))
#define unlink(path) ((int32_t)CALL(&io,unlink,ARG(path)))
#define time(pointer) ((time_t)CALL(&io,time,ARG(pointer)))
#define localtime(pointer) ((tm *)(uintptr_t)CALL(&io,localtime,ARG(pointer)))
#define opendir(path) ((DIR *)(uintptr_t)CALL(&io,opendir,ARG(path)))
#define readdir(directory) ((dirent *)(uintptr_t)CALL(&io,readdir,ARG(directory)))
#define closedir(directory) ((int32_t)CALL(&io,closedir,ARG(directory)))
#define dlopen(path,flags) ((void *)(uintptr_t)CALL(&io,dlopen,ARG(path),flags))
#define dlsym(handle,name) ((void *)(uintptr_t)CALL(&io,dlsym,ARG(handle),ARG(name)))
#define dlerror() ((char *)(uintptr_t)CALL(&io,dlerror))
#define dlclose(handle) ((int32_t)CALL(&io,dlclose,ARG(handle)))
#define builtin_strncpy strncpy

#define SUB21(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB31(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB41(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB51(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB54(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffffffff))
#define SUB61(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB62(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffff))
#define SUB64(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffffffff))
#define SUB71(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB72(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffff))
#define SUB81(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xff))
#define SUB82(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffff))
#define SUB84(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffffffff))
#define SUB85(value,offset) ((BITS(value) >> (8*(offset))) & UINT64_C(0xffffffffff))
#define CONCAT12(hi,lo) ((BITS(hi) << 16) | BITS(lo))
#define CONCAT13(hi,lo) ((BITS(hi) << 24) | BITS(lo))
#define CONCAT14(hi,lo) ((BITS(hi) << 32) | BITS(lo))
#define CONCAT17(hi,lo) ((BITS(hi) << 56) | BITS(lo))
#define CONCAT25(hi,lo) ((BITS(hi) << 40) | BITS(lo))
#define CONCAT44(hi,lo) ((BITS(hi) << 32) | BITS(lo))
#define CONCAT53(hi,lo) ((BITS(hi) << 24) | BITS(lo))
#define CONCAT62(hi,lo) ((BITS(hi) << 16) | BITS(lo))
