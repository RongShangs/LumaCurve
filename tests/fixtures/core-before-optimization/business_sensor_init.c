/* Android NDK symbol discovery and front/back/proximity setup. */
#include "business_support.h"
void ios_business_sensor_init(DomainIo *context, void *scratch) {
  DomainIo io = *context;
  uint32_t sensor_count;
  int sensor_type_or_enable_result;
  FILE *stream;
  void *looper;
  char *reason_text;
  const char *loader_error;
  int64_t sensor_offset;
  ios_state.h_lib = dlopen("libandroid.so",1);
  if (ios_state.h_lib == 0) {
    stream = (FILE *)domain_stdout();
    loader_error = dlerror();
    io_print(&io,stream,"[%s] dlopen: %s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(loader_error)}},2);
  }
  else {
    ios_state.fn_getMgrPkg = (uintptr_t)dlsym(ios_state.h_lib,"ASensorManager_getInstanceForPackage");
    ios_state.fn_getMgr = (uintptr_t)dlsym(ios_state.h_lib,"ASensorManager_getInstance");
    ios_state.fn_getDefSensor = (uintptr_t)dlsym(ios_state.h_lib,"ASensorManager_getDefaultSensor");
    ios_state.fn_createEQ = (uintptr_t)dlsym(ios_state.h_lib,"ASensorManager_createEventQueue");
    ios_state.fn_destroyEQ = (uintptr_t)dlsym(ios_state.h_lib,"ASensorManager_destroyEventQueue");
    ios_state.fn_enable = (uintptr_t)dlsym(ios_state.h_lib,"ASensorEventQueue_enableSensor");
    ios_state.fn_disable = (uintptr_t)dlsym(ios_state.h_lib,"ASensorEventQueue_disableSensor");
    ios_state.fn_setRate = (uintptr_t)dlsym(ios_state.h_lib,"ASensorEventQueue_setEventRate");
    ios_state.fn_getEvents = (uintptr_t)dlsym(ios_state.h_lib,"ASensorEventQueue_getEvents");
    ios_state.fn_minDelay = (uintptr_t)dlsym(ios_state.h_lib,"ASensor_getMinDelay");
    ios_state.fn_looperPrepare = (uintptr_t)dlsym(ios_state.h_lib,"ALooper_prepare");
    ios_state.fn_looperPoll = (uintptr_t)dlsym(ios_state.h_lib,"ALooper_pollOnce");
    ios_state.fn_getSensorList = (uintptr_t)dlsym(ios_state.h_lib,"ASensorManager_getSensorList");
    ios_state.fn_getType = (uintptr_t)dlsym(ios_state.h_lib,"ASensor_getType");
    ios_state.fn_getName = (uintptr_t)dlsym(ios_state.h_lib,"ASensor_getName");
    if ((((ios_state.fn_getDefSensor == 0) ||
         (ios_state.fn_createEQ == 0)) || (ios_state.fn_enable == 0)
        ) || ((ios_state.fn_getEvents == 0 ||
              (ios_state.fn_looperPrepare == 0)))) {
      reason_text = "[%s] Missing NDK symbols\n";
    }
    else {
      if (ios_state.fn_getMgr != 0) {
        ios_state.g_mgr = (void *)(uintptr_t)NDK_instance(&io,ARG(ios_state.fn_getMgr));
      }
      if (((void *)ios_state.g_mgr == (void *)0x0) && (ios_state.fn_getMgrPkg != 0)) {
        ios_state.g_mgr = (void *)(uintptr_t)NDK_package(&io,ARG(ios_state.fn_getMgrPkg),ARG("com.android.system"));
      }
      if ((void *)ios_state.g_mgr == (void *)0x0) {
        reason_text = "[%s] No SensorManager\n";
      }
      else {
        looper = (void *)(uintptr_t)NDK_prepare(&io,ARG(ios_state.fn_looperPrepare),ARG(1));
        if (looper == (void *)0x0) {
          reason_text = "[%s] No looper\n";
        }
        else {
          ios_state.g_eq = (void *)(uintptr_t)NDK_create(&io,ARG(ios_state.fn_createEQ),ARG((void *)ios_state.g_mgr),ARG(looper),ARG(0),ARG((void *)0x0),ARG((void *)0x0));
          if ((void *)ios_state.g_eq != (void *)0x0) {
            ios_state.g_front_sensor = (void *)(uintptr_t)NDK_default(&io,ARG(ios_state.fn_getDefSensor),ARG((void *)ios_state.g_mgr),ARG(5));
            if ((void *)ios_state.g_front_sensor != (void *)0x0) {
              (void)NDK_switch(&io,ARG(ios_state.fn_enable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor));
              if (ios_state.fn_setRate != 0) {
                (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor),ARG(1000000));
              }
              ios_state.g_ndk_ok = 1;
              ios_state.g_light_sensors_enabled = 1;
              ios_state.g_sensor_rate_us = 1000000;
              ios_state.g_sensor_rate_mode = 1;
              stream = (FILE *)domain_stdout();
              if (((ios_state.g_front_sensor == 0) || (ios_state.fn_getName == 0)) ||
                 (reason_text = (char *)(uintptr_t)NDK_name(&io,ARG(ios_state.fn_getName),ARG((void *)ios_state.g_front_sensor)), reason_text == (char *)0x0)) {
                reason_text = "unknown";
              }
              io_print(&io,stream,"[%s] Front ALS ready (%dus, name=%s)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(1000000)},{.word=(uint64_t)(uintptr_t)(reason_text)}},3)
              ;
            }
            if ((ios_state.fn_getSensorList != 0) &&
               (ios_state.fn_getType != 0)) {
              VIEW8(scratch) = 0;
              sensor_count = (int32_t)(uintptr_t)NDK_list(&io,ARG(ios_state.fn_getSensorList),ARG((void *)ios_state.g_mgr),ARG(scratch));
              if (0 < (int)sensor_count) {
                sensor_offset = 0;
                do {
                  sensor_type_or_enable_result = (int32_t)(uintptr_t)NDK_type(&io,ARG(ios_state.fn_getType),ARG(*(void **)((int64_t)VIEW8(scratch) + sensor_offset)));
                  if (sensor_type_or_enable_result == 0x1fa266f) {
                    ios_state.g_back_sensor = *(void **)((uintptr_t)VIEW8(scratch) + sensor_offset);
                    break;
                  }
                  sensor_offset = sensor_offset + 8;
                } while ((uint64_t)sensor_count << 3 != (uint64_t)sensor_offset);
              }
            }
            if ((ios_state.g_back_sensor != 0) &&
               (sensor_type_or_enable_result = (int32_t)(uintptr_t)NDK_switch(&io,ARG(ios_state.fn_enable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_back_sensor)), -1 < sensor_type_or_enable_result)) {
              if (ios_state.fn_setRate != 0) {
                (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_back_sensor),ARG(1000000));
              }
              stream = (FILE *)domain_stdout();
              ios_state.g_back_ok = 1;
              if (((ios_state.g_back_sensor == 0) || (ios_state.fn_getName == 0)) ||
                 (reason_text = (char *)(uintptr_t)NDK_name(&io,ARG(ios_state.fn_getName),ARG((void *)ios_state.g_back_sensor)), reason_text == (char *)0x0)) {
                reason_text = "unknown";
              }
              io_print(&io,stream,"[%s] Back ALS ready (name=%s)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(reason_text)}},2);
            }
            if ((ios_state.fn_getSensorList != 0) &&
               (ios_state.fn_getType != 0)) {
              VIEW8(scratch) = 0;
              sensor_count = (int32_t)(uintptr_t)NDK_list(&io,ARG(ios_state.fn_getSensorList),ARG((void *)ios_state.g_mgr),ARG(scratch));
              if (0 < (int)sensor_count) {
                sensor_offset = 0;
                do {
                  sensor_type_or_enable_result = (int32_t)(uintptr_t)NDK_type(&io,ARG(ios_state.fn_getType),ARG(*(void **)((int64_t)VIEW8(scratch) + sensor_offset)));
                  if (sensor_type_or_enable_result == 8) {
                    ios_state.g_prox_sensor = *(void **)((uintptr_t)VIEW8(scratch) + sensor_offset);
                    break;
                  }
                  sensor_offset = sensor_offset + 8;
                } while ((uint64_t)sensor_count << 3 != (uint64_t)sensor_offset);
              }
            }
            if ((ios_state.g_prox_sensor != 0) &&
               (sensor_type_or_enable_result = (int32_t)(uintptr_t)NDK_switch(&io,ARG(ios_state.fn_enable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_prox_sensor)), -1 < sensor_type_or_enable_result)) {
              ios_state.g_prox_ok = 1;
              io_print(&io,(FILE *)domain_stdout(),"[%s] Proximity ready\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
            }
            fflush((FILE *)domain_stdout());
            goto sensor_probe_finished;
          }
          reason_text = "[%s] No event queue\n";
        }
      }
    }
    io_print(&io,(FILE *)domain_stdout(),reason_text,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
  }
sensor_probe_finished:
  return;
}
