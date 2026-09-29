/* Typed IO helpers. Only this boundary needs architectural call arguments. */
#pragma once
#include <stdbool.h>
#include <stdio.h>
#include "functions.h"
#include "state_layout.h"
#include "log_locale.h"

#define ADDRESS(name) IOS_ADDR_##name
#define INT(name) ((int32_t)ios_load((uintptr_t)ADDRESS(name), 4))
#define TIME(name) ios_load((uintptr_t)ADDRESS(name), 8)
#define FLAG(name) ((uint8_t)ios_load((uintptr_t)ADDRESS(name), 1))
#define POINTER(name) ((uintptr_t)TIME(name))
#define STRING(name) ((char *)ADDRESS(name))
#define SET_INT(name, value) ios_store((uintptr_t)ADDRESS(name), (uint32_t)(value), 4)
#define SET_TIME(name, value) ios_store((uintptr_t)ADDRESS(name), (uint64_t)(value), 8)
#define SET_FLAG(name, value) ios_store((uintptr_t)ADDRESS(name), (uint8_t)(value), 1)
static inline float state_float_value(const uint8_t *p) {
    uint32_t bits = (uint32_t)ios_load((uintptr_t)p, 4);
    float value;
    memcpy(&value, &bits, 4);
    return value;
}
static inline void state_set_float(uint8_t *p, float value) {
    uint32_t bits;
    memcpy(&bits, &value, 4);
    ios_store((uintptr_t)p, bits, 4);
}
#define FLOAT(name) state_float_value(ADDRESS(name))
#define SET_FLOAT(name, value) state_set_float(ADDRESS(name), (value))
#define ARG(pointer) ((uint64_t)(uintptr_t)(pointer))

typedef struct DomainIo {
#ifdef IOS_PRODUCTION
    double reals[8];
    double real_result;
    float float_result;
#else
    IosCpu call;
    uint64_t stack[16];
#endif
} DomainIo;
#if defined(IOS_PRODUCTION) && defined(IOS_TEST_ABI)
uint64_t ios_native_call(DomainIo *io, unsigned import, const uint64_t *args, size_t count);
uint64_t ios_native_dynamic(uint64_t function, const uint64_t *args, size_t count);
#endif
static inline double domain_real_result(DomainIo *io) {
#ifdef IOS_PRODUCTION
    return io->real_result;
#else
    return io->call.v[0].f64[0];
#endif
}
static inline float domain_float_result(DomainIo *io) {
#ifdef IOS_PRODUCTION
    return io->float_result;
#else
    return io->call.v[0].f32[0];
#endif
}
static inline void domain_real_argument(DomainIo *io, unsigned index, double value) {
#ifdef IOS_PRODUCTION
    io->reals[index] = value;
#else
    io->call.v[index].f64[0] = value;
#endif
}
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
#include "native_calls.h"
#else
static inline uint64_t domain_call(DomainIo *io, unsigned import, const uint64_t *args,
                                   size_t count) {
#ifdef IOS_PRODUCTION
    return ios_native_call(io,import,args,count);
#else
    for (size_t i = 0; i < count; i++) {
        if (i < 8)
            io->call.x[i] = args[i];
        else
            io->stack[i - 8] = args[i];
    }
    io->call.sp = (uintptr_t)io->stack;
    ios_import(&io->call, import);
    return io->call.x[0];
#endif
}
#define CALL(io, name, ...)                                                                        \
    domain_call((io), IOS_I_##name, (uint64_t[]){__VA_ARGS__},                                     \
                sizeof((uint64_t[]){__VA_ARGS__}) / sizeof(uint64_t))
static inline uint64_t domain_dynamic(DomainIo *io, uint64_t function, const uint64_t *args,
                                      size_t count) {
#ifdef IOS_PRODUCTION
    (void)io;
    return ios_native_dynamic(function,args,count);
#else
    for (size_t i = 0; i < count; i++)
        io->call.x[i] = args[i];
    ios_indirect(&io->call, function);
    return io->call.x[0];
#endif
}
#define DYNAMIC(io, function, ...)                                                                 \
    domain_dynamic((io), (function), (uint64_t[]){__VA_ARGS__},                                    \
                   sizeof((uint64_t[]){__VA_ARGS__}) / sizeof(uint64_t))
#endif
#if !defined(IOS_PRODUCTION) || defined(IOS_TEST_ABI)
#define NDK_instance(io,fn) DYNAMIC(io,fn)
#define NDK_package(io,fn,a) DYNAMIC(io,fn,a)
#define NDK_prepare(io,fn,a) DYNAMIC(io,fn,a)
#define NDK_list(io,fn,a,b) DYNAMIC(io,fn,a,b)
#define NDK_default(io,fn,a,b) DYNAMIC(io,fn,a,b)
#define NDK_name(io,fn,a) DYNAMIC(io,fn,a)
#define NDK_type(io,fn,a) DYNAMIC(io,fn,a)
#define NDK_poll(io,fn,a,b,c,d) DYNAMIC(io,fn,a,b,c,d)
#define NDK_create(io,fn,a,b,c,d,e) DYNAMIC(io,fn,a,b,c,d,e)
#define NDK_destroy(io,fn,a,b) DYNAMIC(io,fn,a,b)
#define NDK_events(io,fn,a,b,c) DYNAMIC(io,fn,a,b,c)
#define NDK_switch(io,fn,a,b) DYNAMIC(io,fn,a,b)
#define NDK_rate(io,fn,a,b,c) DYNAMIC(io,fn,a,b,c)
#endif
static inline uintptr_t domain_stdout(void) {
#ifdef IOS_PRODUCTION
    return (uintptr_t)stdout;
#else
    return (uintptr_t)ios_load(ios_load((uintptr_t)(ios_ram + 0x708), 8), 8);
#endif
}
static inline int domain_print(DomainIo *io, uintptr_t stream, const char *format,
                               const uint64_t *words, size_t word_count, const double *reals,
                               size_t real_count) {
    format = luma_log_format(format);
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
    for (size_t i = 0; i < real_count; i++) domain_real_argument(io,(unsigned)i,reals[i]);
    return (int32_t)ios_native_print_values(io,(FILE *)stream,format,words,word_count);
#else
    uint64_t arguments[16] = {stream, ARG(format)};
    for (size_t i = 0; i < word_count; i++)
        arguments[i + 2] = words[i];
    for (size_t i = 0; i < real_count; i++)
        domain_real_argument(io, (unsigned)i, reals[i]);
    return (int32_t)domain_call(io, IOS_I_fprintf, arguments, word_count + 2);
#endif
}
#define PRINT(io, stream, format, ...)                                                             \
    domain_print((io), (stream), (format), (uint64_t[]){__VA_ARGS__},                              \
                 sizeof((uint64_t[]){__VA_ARGS__}) / sizeof(uint64_t), NULL, 0)
static inline uint64_t domain_now_ms(DomainIo *io) {
    int64_t stamp[2] = {0};
    CALL(io, clock_gettime, 1, ARG(stamp));
    return (uint64_t)stamp[0] * 1000 + (uint64_t)stamp[1] / 1000000;
}
static inline uintptr_t domain_open(DomainIo *io, const char *path, const char *mode) {
    return (uintptr_t)CALL(io, fopen, ARG(path), ARG(mode));
}
static inline void domain_string(DomainIo *io, char *destination, size_t capacity,
                                 const char *source) {
    CALL(io, snprintf, ARG(destination), capacity, ARG("%s"), ARG(source));
}

#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
#define IOS_SCAN(io,stream,format,...) ((void)(io), (uint64_t)(intptr_t)(fscanf)((FILE *)(uintptr_t)(stream),format,__VA_ARGS__))
#define IOS_SCAN_TEXT(io,text,format,...) ((void)(io), (uint64_t)(intptr_t)(sscanf)(text,format,__VA_ARGS__))
#else
#define SCAN_COUNT_(_1,_2,_3,_4,_5,_6,_7,_8,N,...) N
#define SCAN_COUNT(...) SCAN_COUNT_(__VA_ARGS__,8,7,6,5,4,3,2,1)
#define SCAN_CAT_(a,b) a##b
#define SCAN_CAT(a,b) SCAN_CAT_(a,b)
#define SCAN_ARGS_1(a) ARG(a)
#define SCAN_ARGS_2(a,b) ARG(a),ARG(b)
#define SCAN_ARGS_3(a,b,c) ARG(a),ARG(b),ARG(c)
#define SCAN_ARGS_4(a,b,c,d) ARG(a),ARG(b),ARG(c),ARG(d)
#define SCAN_ARGS_5(a,b,c,d,e) ARG(a),ARG(b),ARG(c),ARG(d),ARG(e)
#define SCAN_ARGS_6(a,b,c,d,e,f) ARG(a),ARG(b),ARG(c),ARG(d),ARG(e),ARG(f)
#define SCAN_ARGS_7(a,b,c,d,e,f,g) ARG(a),ARG(b),ARG(c),ARG(d),ARG(e),ARG(f),ARG(g)
#define SCAN_ARGS_8(a,b,c,d,e,f,g,h) ARG(a),ARG(b),ARG(c),ARG(d),ARG(e),ARG(f),ARG(g),ARG(h)
#define SCAN_ARGS(...) SCAN_CAT(SCAN_ARGS_,SCAN_COUNT(__VA_ARGS__))(__VA_ARGS__)
#define IOS_SCAN(io,stream,format,...) CALL(io,fscanf,ARG(stream),ARG(format),SCAN_ARGS(__VA_ARGS__))
#define IOS_SCAN_TEXT(io,text,format,...) CALL(io,sscanf,ARG(text),ARG(format),SCAN_ARGS(__VA_ARGS__))
#endif
