/* Installer-only probe: write the current value, never change mode or take ownership. */
#pragma once
#include "backlight_paths.h"
static int probe_integer(const char *path, int *value) {
    FILE *f = fopen(path, "r");
    if (!f) return 0;
    int ok = fscanf(f, "%d", value) == 1;
    int closed = fclose(f);
    return ok && closed == 0;
}
static int probe_backlight_integer(void *context, const char *path, int *value) {
    (void)context;
    return probe_integer(path, value);
}
static void *probe_backlight_directory(void *context, const char *path) {
    (void)context;
    return opendir(path);
}
static const char *probe_backlight_entry(void *context, void *directory) {
    (void)context;
    struct dirent *entry = readdir(directory);
    return entry ? entry->d_name : NULL;
}
static void probe_backlight_close(void *context, void *directory) {
    (void)context;
    closedir(directory);
}
static int probe_sysfs_als(void) {
    const char *parents[] = {"/sys/bus/iio/devices", "/sys/class/sensors"};
    for (unsigned i=0;i<2;i++) {
        DIR *dir=opendir(parents[i]);if(!dir)continue;
        struct dirent *entry;unsigned count=0;int found=0;
        while((entry=readdir(dir)) && count++<256) {
            if(entry->d_name[0]=='.')continue;
            char path[512];
            int n=snprintf(path,sizeof(path),"%s/%s/in_illuminance_input",parents[i],entry->d_name);
            if(n<0 || (size_t)n>=sizeof(path))continue;
            FILE *f=fopen(path,"r");if(!f)continue;
            float lux=-1;int ok=fscanf(f,"%f",&lux)==1;fclose(f);
            if(ok && isfinite(lux) && lux>=0 && lux<=200000) {found=1;break;}
        }
        closedir(dir);if(found)return 1;
    }
    return 0;
}
static int probe_ndk_als(void) {
    void *lib=dlopen("libandroid.so",RTLD_NOW|RTLD_LOCAL);if(!lib)return 0;
    void *(*get_mgr)(void)=(void *(*)(void))dlsym(lib,"ASensorManager_getInstance");
    void *(*get_pkg)(const char *)=(void *(*)(const char *))dlsym(lib,"ASensorManager_getInstanceForPackage");
    const void *(*get_sensor)(void *,int)=(const void *(*)(void *,int))dlsym(lib,"ASensorManager_getDefaultSensor");
    int ok=get_sensor && dlsym(lib,"ASensorManager_createEventQueue") &&
        dlsym(lib,"ASensorEventQueue_enableSensor") && dlsym(lib,"ASensorEventQueue_getEvents") &&
        dlsym(lib,"ALooper_prepare");
    void *mgr=ok && get_mgr?get_mgr():NULL;
    if(ok && !mgr && get_pkg)mgr=get_pkg("com.android.system");
    ok=ok && mgr && get_sensor(mgr,5);
    if(!ok && mgr) {
        int (*list)(void *,const void ***)=(int (*)(void *,const void ***))dlsym(lib,"ASensorManager_getSensorList");
        int (*type)(const void *)=(int (*)(const void *))dlsym(lib,"ASensor_getType");
        const void **sensors=NULL;int n=list && type?list(mgr,&sensors):0;
        for(int i=0;sensors && i<n && i<512;i++)if(type(sensors[i])==0x1fa266f) {ok=1;break;}
    }
    dlclose(lib);return ok;
}
static int luma_install_probe(void) {
    puts("probe_protocol=1");puts("engine_load=ok");
    LumaBacklight backlight;
    LumaBacklightOps ops = {NULL, probe_backlight_integer, probe_backlight_directory,
                           probe_backlight_entry, probe_backlight_close};
    if(!luma_backlight_select(&backlight, &ops)) {
        puts("result=fail");puts("reason=backlight_node_or_range");return 2;
    }
    int fd=open(backlight.brightness,O_WRONLY);
    if(fd<0) {puts("result=fail");puts("reason=backlight_permission");return 2;}
    int current=-1, readback=-1;
    char text[32];
    int length=probe_integer(backlight.brightness,&current) ?
        snprintf(text,sizeof(text),"%d\n",current) : -1;
    int verified=length>0 && (size_t)length<sizeof(text) &&
        write(fd,text,(size_t)length)==length;
    if(close(fd)<0) verified=0;
    if(!verified || !probe_integer(backlight.brightness,&readback) || readback!=current) {
        puts("actual_brightness_write=failed");
        puts("result=fail");puts("reason=backlight_write_or_readback");return 2;
    }
    puts("backlight=read_and_write_open_ok");
    puts("actual_brightness_write=verified_current_value");
    printf("backlight_path=%s\n", backlight.brightness);
    int ndk=probe_ndk_als(),fallback=probe_sysfs_als();
    printf("ndk_als=%d\nsysfs_als=%d\n",ndk,fallback);
    if(!ndk && !fallback) {puts("result=fail");puts("reason=no_supported_light_sensor");return 2;}
    int power=-1;
    printf("screen_node=%s\n",probe_integer(backlight.power,&power)?"readable":"unverified");
    puts("sensor_events=unverified");
    puts("result=pass");return 0;
}
