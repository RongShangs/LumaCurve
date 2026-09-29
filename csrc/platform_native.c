/* Native Android libc/NDK boundary for the complete C recovery. */
#include "business_api.h"
#include <stdio.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/resource.h>
#include <dlfcn.h>
#include <errno.h>
#include <stdarg.h>
#include "log_timestamp.h"
#include "backlight_paths.h"
#include "backlight_write_diagnostics.h"
#include "backlight_handle.h"
#include "core_build.h"
#include "framework_backend.h"

_Static_assert(sizeof(void *) == 8 && sizeof(long) == 8, "Android LP64 is required");
_Static_assert(offsetof(struct stat, st_mtim) == 88, "Unexpected Android stat layout");
_Static_assert(offsetof(struct dirent, d_name) == 19, "Unexpected Android dirent layout");

static uint64_t word(const uint64_t *arguments, unsigned *index) {
    return arguments[(*index)++];
}
static double floating(const DomainIo *io, unsigned *index) {
    unsigned n = (*index)++;
    return n < 8 ? io->reals[n] : 0.0;
}
typedef struct Text {
    char *data;
    size_t size, capacity;
} Text;
static int append(Text *t, const char *p, size_t n) {
    if (n > SIZE_MAX - t->size - 1)
        return -1;
    size_t required = t->size + n + 1;
    if (required > t->capacity) {
        size_t capacity = required * 2;
        char *v = realloc(t->data, capacity);
        if (!v)
            return -1;
        t->data = v;
        t->capacity = capacity;
    }
    memcpy(t->data + t->size, p, n);
    t->size += n;
    t->data[t->size] = 0;
    return 0;
}
/* Variadic floats and GP arguments occupy separate AAPCS64 banks. Format
 * each conversion using native snprintf rather than casting a variadic call.
 */
static char *format_text(const DomainIo *io, const uint64_t *arguments, const char *fmt, unsigned integer, size_t *length) {
    Text out = {0};
    unsigned fp = 0;
    for (const char *p = fmt; *p;) {
        if (*p != '%') {
            const char *start = p;
            while (*p && *p != '%')
                p++;
            if (append(&out, start, (size_t)(p - start)))
                goto fail;
            continue;
        }
        const char *start = p++;
        if (*p == '%') {
            if (append(&out, "%", 1))
                goto fail;
            p++;
            continue;
        }
        char spec[128];
        size_t n = 0;
        spec[n++] = '%';
        while (*p && strchr("-+ #0'", *p)) {
            if (n + 1 >= sizeof(spec))
                goto fail;
            spec[n++] = *p++;
        }
        if (*p == '*') {
            int width = (int32_t)word(arguments, &integer);
            n += (size_t)snprintf(spec + n, sizeof(spec) - n, "%d", width);
            p++;
        } else
            while (*p >= '0' && *p <= '9') {
                if (n + 1 >= sizeof(spec))
                    goto fail;
                spec[n++] = *p++;
            }
        if (*p == '.') {
            spec[n++] = *p++;
            if (*p == '*') {
                int precision = (int32_t)word(arguments, &integer);
                n += (size_t)snprintf(spec + n, sizeof(spec) - n, "%d", precision);
                p++;
            } else
                while (*p >= '0' && *p <= '9') {
                    if (n + 1 >= sizeof(spec))
                        goto fail;
                    spec[n++] = *p++;
                }
        }
        int wide = 0;
        while (*p && strchr("hljztL", *p)) {
            if (*p != 'h')
                wide = 1;
            if (n + 1 >= sizeof(spec))
                goto fail;
            spec[n++] = *p++;
        }
        char kind = *p++;
        if (!kind || n + 2 > sizeof(spec))
            goto fail;
        spec[n++] = kind;
        spec[n] = 0;
        char small[512];
        char *result = small;
        int count = -1;
        uint64_t value = 0;
        double real = 0;
        if (strchr("fFeEgGaA", kind))
            real = floating(io, &fp);
        else
            value = word(arguments, &integer);
#define CONVERT(buffer, size)                                                                      \
    (strchr("fFeEgGaA", kind) ? snprintf(buffer, size, spec, real)                                 \
     : kind == 's'            ? snprintf(buffer, size, spec, (const char *)(uintptr_t)value)       \
     : kind == 'p'            ? snprintf(buffer, size, spec, (void *)(uintptr_t)value)             \
     : kind == 'c'            ? snprintf(buffer, size, spec, (int)value)                           \
     : strchr("di", kind)     ? (wide ? snprintf(buffer, size, spec, (long)value)                  \
                                      : snprintf(buffer, size, spec, (int32_t)value))              \
     : wide                   ? snprintf(buffer, size, spec, (unsigned long)value)                 \
                              : snprintf(buffer, size, spec, (uint32_t)value))
        count = CONVERT(small, sizeof(small));
        if (count < 0)
            goto fail;
        if ((size_t)count >= sizeof(small)) {
            result = malloc((size_t)count + 1);
            if (!result)
                goto fail;
            (void)CONVERT(result, (size_t)count + 1);
        }
        int error = append(&out, result, (size_t)count);
        if (result != small)
            free(result);
        if (error)
            goto fail;
#undef CONVERT
        (void)start;
    }
    if (!out.data) {
        out.data = calloc(1, 1);
        if (!out.data)
            return NULL;
    }
    *length = out.size;
    return out.data;
fail:
    free(out.data);
    errno = ENOMEM;
    return NULL;
}

FILE * ios_native_fopen(const char * a, const char * b) {
#ifdef LUMA_FRAMEWORK_BACKEND
    if(!strncmp(a,"/sys/",5)&&strcmp(b,"r")){errno=EPERM;return NULL;}
    char path[512];a=luma_framework_path(a,path,sizeof(path));if(!a)return NULL;
#endif
    return fopen(a,b);
}
int ios_native_fclose(FILE * a) { return fclose(a); }
char * ios_native_fgets(char * a, int b, FILE * c) { return fgets(a,b,c); }
size_t ios_native_fwrite(const void * a, size_t b, size_t c, FILE * d) { return fwrite(a,b,c,d); }
int ios_native_fflush(FILE * a) { return fflush(a); }
int ios_native_open(const char * a, int b, unsigned c) {
#ifdef LUMA_FRAMEWORK_BACKEND
    if(!strncmp(a,"/sys/",5)&&(b&O_ACCMODE)!=O_RDONLY){errno=EPERM;return -1;}
    char path[512];a=luma_framework_path(a,path,sizeof(path));if(!a)return -1;
#endif
    if ((b & O_ACCMODE) == O_WRONLY && !strcmp(a,ios_backlight_brightness()) &&
        luma_owned_backlight_fd >= 0)
        return luma_owned_backlight_fd;
    int fd=open(a,b,(mode_t)c), error=errno;
    if ((b & O_ACCMODE) != O_RDONLY && !strcmp(a,ios_backlight_brightness())) {
        if (fd < 0) luma_backlight_write_error("open",error);
        else luma_backlight_fd=fd;
    }
    errno=error; return fd;
}
int ios_native_close(int a) {
    /* Per-frame close ends the logical operation, not the ownership lease. */
    if (a == luma_owned_backlight_fd && a >= 0) return 0;
    int result=close(a), error=errno;
    if (a==luma_backlight_fd) {
        luma_backlight_fd=-1;
        if (result < 0) luma_backlight_write_error("close",error);
    }
    errno=error; return result;
}
int64_t ios_native_write(int a, const void * b, size_t c) {
    if (a == luma_owned_backlight_fd && a >= 0 && lseek(a,0,SEEK_SET) < 0) {
        int error=errno;
        luma_backlight_write_error("seek",error);
        return -1;
    }
    int64_t result=write(a,b,c); int error=errno;
    if (a==luma_backlight_fd && (result < 0 || (size_t)result != c))
        luma_backlight_write_error(result < 0 ? "write" : "short_write",result < 0 ? error : EIO);
    errno=error; return result;
}
int ios_native_clock_gettime(int a, void * b) { return clock_gettime((clockid_t)a,(struct timespec *)b); }
int ios_native_usleep(unsigned a) {
#ifdef LUMA_FRAMEWORK_BACKEND
    while(a>200000){if(usleep(200000))return -1;a-=200000;luma_framework_pulse();}
    int result=usleep(a);luma_framework_pulse();return result;
#else
    return usleep(a);
#endif
}
uintptr_t ios_native_signal(int a, void (*b)(int)) { return (uintptr_t)signal(a,b); }
int ios_native_getpid(void) { return getpid(); }
int ios_native_setpriority(int a, unsigned b, int c) { return setpriority(a,b,c); }
void * ios_native_dlopen(const char * a, int b) { return dlopen(a,b); }
void * ios_native_dlsym(void * a, const char * b) { return dlsym(a,b); }
char * ios_native_dlerror(void) { return dlerror(); }
int ios_native_dlclose(void * a) { return dlclose(a); }
void * ios_native_opendir(const char * a) { return opendir(a); }
void * ios_native_readdir(void * a) { return readdir(a); }
int ios_native_closedir(void * a) { return closedir(a); }
int ios_native_access(const char * a, int b) {
#ifdef LUMA_FRAMEWORK_BACKEND
    char path[512];a=luma_framework_path(a,path,sizeof(path));if(!a)return -1;
#endif
    return access(a,b);
}
int ios_native_stat(const char * a, void * b) {
#ifdef LUMA_FRAMEWORK_BACKEND
    char path[512];a=luma_framework_path(a,path,sizeof(path));if(!a)return -1;
#endif
    return stat(a,(struct stat *)b);
}
int ios_native_chmod(const char * a, unsigned b) {
#ifdef LUMA_FRAMEWORK_BACKEND
    if(!strcmp(a,ios_backlight_brightness()))return (b&0222)?luma_framework_release():luma_framework_acquire();
    if(!strncmp(a,"/sys/",5)){errno=EPERM;return -1;}
#endif
    if (strcmp(a,ios_backlight_brightness())) return chmod(a,b);
    return (b & 0222) ? luma_backlight_release(a,b) : luma_backlight_acquire(a,b);
}
int ios_native_backlight_ready(void) {
#ifdef LUMA_FRAMEWORK_BACKEND
    return luma_framework_ready();
#else
    return luma_owned_backlight_fd >= 0;
#endif
}
void * ios_native_memset(void * a, int b, size_t c) { return memset(a,b,c); }
void * ios_native_memcpy(void * a, const void * b, size_t c) { return memcpy(a,b,c); }
char * ios_native_strncpy(char * a, const char * b, size_t c) { return strncpy(a,b,c); }
int ios_native_strcmp(const char * a, const char * b) { return strcmp(a,b); }
int ios_native_strncmp(const char * a, const char * b, size_t c) { return strncmp(a,b,c); }
char * ios_native_strchr(const char * a, int b) { return strchr(a,b); }
char * ios_native_strstr(const char * a, const char * b) { return strstr(a,b); }
FILE * ios_native_popen(const char * a, const char * b) { return popen(a,b); }
int ios_native_pclose(FILE * a) { return pclose(a); }
int64_t ios_native_time(void * a) { return time(a); }
void * ios_native_localtime(const void * a) { return localtime(a); }
int ios_native_unlink(const char * a) {
#ifdef LUMA_FRAMEWORK_BACKEND
    char path[512];a=luma_framework_path(a,path,sizeof(path));if(!a)return -1;
#endif
    return unlink(a);
}
int ios_native_rename(const char * a, const char * b) {
#ifdef LUMA_FRAMEWORK_BACKEND
    char first[512],second[512];a=luma_framework_path(a,first,sizeof(first));b=luma_framework_path(b,second,sizeof(second));if(!a||!b)return -1;
#endif
    return rename(a,b);
}
int ios_native_atoi(const char * a) { return atoi(a); }
int64_t ios_native_strtol(const char * a, char ** b, int c) { return strtol(a,b,c); }
uint64_t ios_native_parse_double(DomainIo *io, const char *text) { io->real_result = atof(text); return 0; }
uint64_t ios_native_parse_float(DomainIo *io, const char *text, char **end) { io->float_result = strtof(text,end); return 0; }
uint64_t ios_native_print_values(DomainIo *io, FILE *stream, const char *format, const uint64_t *values, size_t count) {
 (void)count; size_t length=0; char *text=format_text(io,values,format,0,&length);
 if (!text) return (uint64_t)-1;
 if (luma_log_timestamp(stream) < 0) { free(text); return (uint64_t)-1; }
 size_t n=fwrite(text,1,length,stream); free(text); return n==length ? length : (uint64_t)-1;
}
uint64_t ios_native_format_values(DomainIo *io, char *buffer, size_t capacity, const char *format, const uint64_t *values, size_t count) {
 (void)count; size_t length=0; char *text=format_text(io,values,format,0,&length);
 if (!text) return (uint64_t)-1;
 if (capacity) { size_t n=length < capacity-1 ? length : capacity-1; memcpy(buffer,text,n); buffer[n]=0; }
 free(text); return length;
}
#ifndef LUMA_FRAMEWORK_BACKEND
#include "install_probe.h"
#endif
int main(int argc, char **argv) {
#ifdef LUMA_FRAMEWORK_BACKEND
    (void)argv;
    if(argc!=1||luma_framework_initialize()){fprintf(stderr,"Isolated framework launcher and runtime directory required\n");return 2;}
#else
    if (argc == 2 && !strcmp(argv[1], "--build-info")) { puts(LUMA_CORE_BUILD); return 0; }
    if (argc == 2 && !strcmp(argv[1], "--check-install")) return luma_install_probe();
    if (argc != 1) { fprintf(stderr,"Usage: luma_curve_daemon [--check-install]\n"); return 2; }
#endif
    fprintf(stdout,"[LumaCurve] 核心构建：%s\n",LUMA_CORE_BUILD); fflush(stdout);
    ios_reset_data();
#ifdef IOS_BUSINESS_MAIN
    return ios_business_main();
#else
    return ios_structured_main();
#endif
}
