#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <fcntl.h>
#include <dirent.h>
#include <assert.h>
#define RTLD_NOW 2
#define RTLD_LOCAL 0
static int ndk=1,sysfs=0,missing=0,denied=0,maximum=16383,current=100,dir_index,opens;
static int write_failure,short_write,close_failure,readback_mismatch,writes;
static int original_missing, alternate_panels, backlight_directory;
static FILE *probe_file(const char *path,const char *mode) {
    assert(!strcmp(mode,"r"));
    if(missing)return NULL;
    if(original_missing && strstr(path,"panel0-backlight"))return NULL;
    int value;
    if(strstr(path,"max_brightness"))value=maximum;
    else if(strstr(path,"/brightness"))value=current;
    else if(strstr(path,"bl_power"))value=0;
    else if(strstr(path,"illuminance") && sysfs)value=42;
    else return NULL;
    FILE *file=tmpfile();assert(file);fprintf(file,"%d\n",value);rewind(file);return file;
}
static int probe_open(const char *path,int flags) {assert(strstr(path,"/brightness"));assert(flags==O_WRONLY);opens++;return denied?-1:42;}
static int probe_close(int fd) {assert(fd==42);return close_failure?-1:0;}
static int64_t probe_write(int fd,const void *text,size_t length) {
    assert(fd==42);assert(atoi(text)==current);writes++;
    if(write_failure)return -1;
    if(short_write)return (int64_t)length-1;
    if(readback_mismatch)current++;
    return (int64_t)length;
}
static DIR *probe_dir(const char *path) {dir_index=0;backlight_directory=!strcmp(path,"/sys/class/backlight");return (backlight_directory?alternate_panels:sysfs)?(DIR *)(uintptr_t)1:NULL;}
static struct dirent *probe_entry(DIR *dir) {
    (void)dir;static struct dirent entry;
    if(backlight_directory) {
        if(dir_index>=alternate_panels)return NULL;
        snprintf(entry.d_name,sizeof(entry.d_name),"panel%d",++dir_index);
    } else {if(dir_index++)return NULL;strcpy(entry.d_name,"iio:device0");}
    return &entry;
}
static int probe_closedir(DIR *dir) {(void)dir;return 0;}
static void *manager(void) {return (void *)(uintptr_t)1;}
static const void *sensor(void *mgr,int type) {assert(mgr && type==5);return (void *)(uintptr_t)2;}
static void *probe_dlopen(const char *path,int flags) {(void)flags;assert(!strcmp(path,"libandroid.so"));return ndk?(void *)(uintptr_t)1:NULL;}
static void *probe_dlsym(void *lib,const char *name) {(void)lib;if(!strcmp(name,"ASensorManager_getInstance"))return (void *)manager;if(!strcmp(name,"ASensorManager_getDefaultSensor"))return (void *)sensor;return (void *)(uintptr_t)1;}
static int probe_dlclose(void *lib) {(void)lib;return 0;}
#define fopen probe_file
#define open probe_open
#define close probe_close
#define write probe_write
#define opendir probe_dir
#define readdir probe_entry
#define closedir probe_closedir
#define dlopen probe_dlopen
#define dlsym probe_dlsym
#define dlclose probe_dlclose
#include "install_probe.h"
int main(void) {
    assert(luma_install_probe()==0);
    ndk=0;sysfs=1;assert(luma_install_probe()==0);
    sysfs=0;assert(luma_install_probe()==2);
    ndk=1;denied=1;assert(luma_install_probe()==2);denied=0;
    maximum=0;assert(luma_install_probe()==2);maximum=16383;
    current=20000;assert(luma_install_probe()==2);current=100;
    missing=1;assert(luma_install_probe()==2);
    missing=0;original_missing=1;alternate_panels=1;assert(luma_install_probe()==0);
    alternate_panels=2;assert(luma_install_probe()==2);
    original_missing=0;assert(luma_install_probe()==0);
    assert(opens==6);
    write_failure=1;assert(luma_install_probe()==2);write_failure=0;
    short_write=1;assert(luma_install_probe()==2);short_write=0;
    close_failure=1;assert(luma_install_probe()==2);close_failure=0;
    readback_mismatch=1;assert(luma_install_probe()==2);readback_mismatch=0;
    assert(writes==9);puts("probe: 14 cases; current-value write and readback, rejected failures PASS");
    return 0;
}
