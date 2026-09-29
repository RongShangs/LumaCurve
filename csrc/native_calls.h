/* Typed libc and NDK boundary. Reference ABI packing is test-only. */
#pragma once
FILE * ios_native_fopen(const char * a, const char * b);
#define NATIVE_fopen(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_fopen((const char *)(uintptr_t)(a), (const char *)(uintptr_t)(b)))
int ios_native_fclose(FILE * a);
#define NATIVE_fclose(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_fclose((FILE *)(uintptr_t)(a)))
char * ios_native_fgets(char * a, int b, FILE * c);
#define NATIVE_fgets(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_fgets((char *)(uintptr_t)(a), (int)(uintptr_t)(b), (FILE *)(uintptr_t)(c)))
size_t ios_native_fwrite(const void * a, size_t b, size_t c, FILE * d);
#define NATIVE_fwrite(io,a,b,c,d) ((void)(io), (uint64_t)(uintptr_t)ios_native_fwrite((const void *)(uintptr_t)(a), (size_t)(uintptr_t)(b), (size_t)(uintptr_t)(c), (FILE *)(uintptr_t)(d)))
int ios_native_fflush(FILE * a);
#define NATIVE_fflush(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_fflush((FILE *)(uintptr_t)(a)))
int ios_native_open(const char * a, int b, unsigned c);
#define NATIVE_open(io,a,b,...) ((void)(io), (uint64_t)(uintptr_t)ios_native_open((const char *)(uintptr_t)(a),(int)(b),0))
int ios_native_close(int a);
#define NATIVE_close(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_close((int)(uintptr_t)(a)))
int64_t ios_native_write(int a, const void * b, size_t c);
#define NATIVE_write(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_write((int)(uintptr_t)(a), (const void *)(uintptr_t)(b), (size_t)(uintptr_t)(c)))
int ios_native_clock_gettime(int a, void * b);
#define NATIVE_clock_gettime(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_clock_gettime((int)(uintptr_t)(a), (void *)(uintptr_t)(b)))
int ios_native_usleep(unsigned a);
#define NATIVE_usleep(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_usleep((unsigned)(uintptr_t)(a)))
uintptr_t ios_native_signal(int a, void (*b)(int));
#define NATIVE_signal(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_signal((int)(uintptr_t)(a), (void (*)(int))(uintptr_t)(b)))
int ios_native_getpid(void);
#define NATIVE_getpid(io) ((void)(io), (uint64_t)(uintptr_t)ios_native_getpid())
int ios_native_setpriority(int a, unsigned b, int c);
#define NATIVE_setpriority(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_setpriority((int)(uintptr_t)(a), (unsigned)(uintptr_t)(b), (int)(uintptr_t)(c)))
void * ios_native_dlopen(const char * a, int b);
#define NATIVE_dlopen(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_dlopen((const char *)(uintptr_t)(a), (int)(uintptr_t)(b)))
void * ios_native_dlsym(void * a, const char * b);
#define NATIVE_dlsym(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_dlsym((void *)(uintptr_t)(a), (const char *)(uintptr_t)(b)))
char * ios_native_dlerror(void);
#define NATIVE_dlerror(io) ((void)(io), (uint64_t)(uintptr_t)ios_native_dlerror())
int ios_native_dlclose(void * a);
#define NATIVE_dlclose(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_dlclose((void *)(uintptr_t)(a)))
void * ios_native_opendir(const char * a);
#define NATIVE_opendir(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_opendir((const char *)(uintptr_t)(a)))
void * ios_native_readdir(void * a);
#define NATIVE_readdir(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_readdir((void *)(uintptr_t)(a)))
int ios_native_closedir(void * a);
#define NATIVE_closedir(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_closedir((void *)(uintptr_t)(a)))
int ios_native_access(const char * a, int b);
#define NATIVE_access(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_access((const char *)(uintptr_t)(a), (int)(uintptr_t)(b)))
int ios_native_stat(const char * a, void * b);
#define NATIVE_stat(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_stat((const char *)(uintptr_t)(a), (void *)(uintptr_t)(b)))
int ios_native_chmod(const char * a, unsigned b);
#define NATIVE_chmod(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_chmod((const char *)(uintptr_t)(a), (unsigned)(uintptr_t)(b)))
void * ios_native_memset(void * a, int b, size_t c);
#define NATIVE_memset(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_memset((void *)(uintptr_t)(a), (int)(uintptr_t)(b), (size_t)(uintptr_t)(c)))
void * ios_native_memcpy(void * a, const void * b, size_t c);
#define NATIVE_memcpy(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_memcpy((void *)(uintptr_t)(a), (const void *)(uintptr_t)(b), (size_t)(uintptr_t)(c)))
char * ios_native_strncpy(char * a, const char * b, size_t c);
#define NATIVE_strncpy(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_strncpy((char *)(uintptr_t)(a), (const char *)(uintptr_t)(b), (size_t)(uintptr_t)(c)))
int ios_native_strcmp(const char * a, const char * b);
#define NATIVE_strcmp(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_strcmp((const char *)(uintptr_t)(a), (const char *)(uintptr_t)(b)))
int ios_native_strncmp(const char * a, const char * b, size_t c);
#define NATIVE_strncmp(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_strncmp((const char *)(uintptr_t)(a), (const char *)(uintptr_t)(b), (size_t)(uintptr_t)(c)))
char * ios_native_strchr(const char * a, int b);
#define NATIVE_strchr(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_strchr((const char *)(uintptr_t)(a), (int)(uintptr_t)(b)))
char * ios_native_strstr(const char * a, const char * b);
#define NATIVE_strstr(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_strstr((const char *)(uintptr_t)(a), (const char *)(uintptr_t)(b)))
FILE * ios_native_popen(const char * a, const char * b);
#define NATIVE_popen(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_popen((const char *)(uintptr_t)(a), (const char *)(uintptr_t)(b)))
int ios_native_pclose(FILE * a);
#define NATIVE_pclose(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_pclose((FILE *)(uintptr_t)(a)))
int64_t ios_native_time(void * a);
#define NATIVE_time(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_time((void *)(uintptr_t)(a)))
void * ios_native_localtime(const void * a);
#define NATIVE_localtime(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_localtime((const void *)(uintptr_t)(a)))
int ios_native_unlink(const char * a);
#define NATIVE_unlink(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_unlink((const char *)(uintptr_t)(a)))
int ios_native_rename(const char * a, const char * b);
#define NATIVE_rename(io,a,b) ((void)(io), (uint64_t)(uintptr_t)ios_native_rename((const char *)(uintptr_t)(a), (const char *)(uintptr_t)(b)))
int ios_native_atoi(const char * a);
#define NATIVE_atoi(io,a) ((void)(io), (uint64_t)(uintptr_t)ios_native_atoi((const char *)(uintptr_t)(a)))
int64_t ios_native_strtol(const char * a, char ** b, int c);
#define NATIVE_strtol(io,a,b,c) ((void)(io), (uint64_t)(uintptr_t)ios_native_strtol((const char *)(uintptr_t)(a), (char **)(uintptr_t)(b), (int)(uintptr_t)(c)))
uint64_t ios_native_parse_double(DomainIo *io, const char *text);
uint64_t ios_native_parse_float(DomainIo *io, const char *text, char **end);
#define NATIVE_atof(io,a) ios_native_parse_double(io,(const char *)(uintptr_t)(a))
#define NATIVE_strtof(io,a,b) ios_native_parse_float(io,(const char *)(uintptr_t)(a),(char **)(uintptr_t)(b))
uint64_t ios_native_print_values(DomainIo *io, FILE *stream, const char *format, const uint64_t *values, size_t count);
uint64_t ios_native_format_values(DomainIo *io, char *buffer, size_t capacity, const char *format, const uint64_t *values, size_t count);
#define NATIVE_fprintf(io,a,b,...) ios_native_print_values(io,(FILE *)(uintptr_t)(a),(const char *)(uintptr_t)(b),(uint64_t[]){__VA_ARGS__},sizeof((uint64_t[]){__VA_ARGS__})/8)
#define NATIVE_snprintf(io,a,b,c,...) ios_native_format_values(io,(char *)(uintptr_t)(a),(size_t)(b),(const char *)(uintptr_t)(c),(uint64_t[]){__VA_ARGS__},sizeof((uint64_t[]){__VA_ARGS__})/8)
#define CALL(io,name,...) NATIVE_##name(io,##__VA_ARGS__)
#define NDK_instance(io,fn) ((void)(io), (uint64_t)(uintptr_t)((void *(*)(void))(uintptr_t)(fn))())
#define NDK_package(io,fn,a) ((void)(io), (uint64_t)(uintptr_t)((void *(*)(const char *))(uintptr_t)(fn))((const char *)(uintptr_t)(a)))
#define NDK_prepare(io,fn,a) ((void)(io), (uint64_t)(uintptr_t)((void *(*)(int))(uintptr_t)(fn))((int)(uintptr_t)(a)))
#define NDK_list(io,fn,a,b) ((void)(io), (uint64_t)(uintptr_t)((int(*)(void *,const void ***))(uintptr_t)(fn))((void *)(uintptr_t)(a),(const void ***)(uintptr_t)(b)))
#define NDK_default(io,fn,a,b) ((void)(io), (uint64_t)(uintptr_t)((void *(*)(void *,int))(uintptr_t)(fn))((void *)(uintptr_t)(a),(int)(uintptr_t)(b)))
#define NDK_name(io,fn,a) ((void)(io), (uint64_t)(uintptr_t)((const char *(*)(const void *))(uintptr_t)(fn))((const void *)(uintptr_t)(a)))
#define NDK_type(io,fn,a) ((void)(io), (uint64_t)(uintptr_t)((int(*)(const void *))(uintptr_t)(fn))((const void *)(uintptr_t)(a)))
#define NDK_poll(io,fn,a,b,c,d) ((void)(io), (uint64_t)(uintptr_t)((int(*)(int,int *,int *,void **))(uintptr_t)(fn))((int)(uintptr_t)(a),(int *)(uintptr_t)(b),(int *)(uintptr_t)(c),(void **)(uintptr_t)(d)))
#define NDK_create(io,fn,a,b,c,d,e) ((void)(io), (uint64_t)(uintptr_t)((void *(*)(void *,void *,int,void *,void *))(uintptr_t)(fn))((void *)(uintptr_t)(a),(void *)(uintptr_t)(b),(int)(uintptr_t)(c),(void *)(uintptr_t)(d),(void *)(uintptr_t)(e)))
#define NDK_destroy(io,fn,a,b) ((void)(io), (uint64_t)(uintptr_t)((int(*)(void *,void *))(uintptr_t)(fn))((void *)(uintptr_t)(a),(void *)(uintptr_t)(b)))
#define NDK_events(io,fn,a,b,c) ((void)(io), (uint64_t)(uintptr_t)((int64_t(*)(void *,void *,size_t))(uintptr_t)(fn))((void *)(uintptr_t)(a),(void *)(uintptr_t)(b),(size_t)(uintptr_t)(c)))
#define NDK_switch(io,fn,a,b) ((void)(io), (uint64_t)(uintptr_t)((int(*)(void *,const void *))(uintptr_t)(fn))((void *)(uintptr_t)(a),(const void *)(uintptr_t)(b)))
#define NDK_rate(io,fn,a,b,c) ((void)(io), (uint64_t)(uintptr_t)((int(*)(void *,const void *,int))(uintptr_t)(fn))((void *)(uintptr_t)(a),(const void *)(uintptr_t)(b),(int)(uintptr_t)(c)))
