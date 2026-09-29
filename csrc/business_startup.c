/* Full initialization: signals, configuration, HBM, sensors, owner, warmup. */
#include "backlight_paths.h"
#include "scene_adaptation.h"
#include "business_support.h"
#include "business_state_views.h"
IosStartup ios_business_startup(DomainIo *context, char scratch[256]) {
  DomainIo io = *context;
  bool warmup_timeout;
  uint32_t brightness_or_pid;
  int read_result;
  int close_result;
  FILE *stream;
  char *reason_text;
  DIR *directory;
  dirent *entry;
  uint64_t timestamp_ms;
  int64_t stale_age_ms;
  uint64_t sensor_poll_ms;
  float smooth_lux;
  float raw_lux;
  int screen_on;
  uint32_t target_brightness;
  uint32_t current_brightness;
  current_brightness = 0;
  signal(0xf,sig_h);
  signal(2,sig_h);
  signal(0xd,(__sighandler_t)0x1);
  signal(10,sig_learn_reset);
  signal(0xc,sig_reload);
  signal(1,sig_reload_learn);
  setpriority(PRIO_PROCESS,0,10);
  stream = fopen("/data/local/tmp/luma_curve.pid","w");
  if (stream != (FILE *)0x0) {
    brightness_or_pid = getpid();
    io_print(&io,stream,"%d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)((uint64_t)brightness_or_pid)}},1);
    fclose(stream);
  }
  if (!ios_backlight_initialize(&io)) {
    io_print(&io,(FILE *)domain_stdout(),"[LumaCurve] No unique valid backlight; leaving control to system\n",NULL,0);
    ios_state.g_run = 0;
    return (IosStartup){0};
  }
  load_config();
  memset(((char *)ios_state.g_learn),0,0x420);
  ios_state.g_global_offset = 0.0f;
  if ((ios_state.cfg_learn_enabled != 0) && (read_result = load_learn(), read_result == 0)) {
    save_learn();
  }
  stream = fopen(ios_backlight_maximum(),"r");
  VIEW4((uint8_t *)(scratch) + 0) = 0x3fff;
  read_result = 0x3fff;
  if (stream != (FILE *)0x0) {
    read_result = fscanf(stream,"%d",(int32_t *)scratch);
    if (read_result != 1) {
      VIEW4((uint8_t *)(scratch) + 0) = 0x3fff;
    }
    fclose(stream);
    read_result = VIEW4((uint8_t *)(scratch) + 0);
    if ((int)VIEW4((uint8_t *)(scratch) + 0) < 1) {
      read_result = 0x3fff;
    }
  }
  ios_state.g_max = read_result;
  clock_gettime(1,(timespec *)scratch);
  read_result = stat("/data/local/tmp/luma_curve.conf",(stat_buffer *)scratch);
  if (read_result == 0) {
    ios_state.cfg_last_mtime = VIEW8(scratch + 88);
  }
  io_print(&io,(FILE *)domain_stdout(),"[%s] LumaCurve v%s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)("1.0.0")}},2);
  io_print(&io,(FILE *)domain_stdout(),"[%s] Max: %d | Nits: 0.2~%.1f\n",(IoArgument[]){{.real=538.5,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.g_max)}},3);
  io_print(&io,(FILE *)domain_stdout(),"[%s] Thermal: trigger=%d resume=%d floor=%.2f charging_guard=%d\n",(IoArgument[]){{.real=(double)ios_state.cfg_thermal_hard_floor_pct,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.cfg_thermal_hard_trigger)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.cfg_thermal_hard_resume)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.cfg_charging_heat_guard)}},5);
  if ((ios_state.hl_hbm_discovery_attempted & 1) == 0) {
    hbm_find_node();
  }
  if (ios_state.hl_hbm_path[0] == '\0') {
    ios_state.hl_hbm_active = 0;
  }
  else {
    stream = fopen(ios_state.hl_hbm_path,"w");
    if (stream != (FILE *)0x0) {
      read_result = io_print(&io,stream,"%s",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(ios_state.cfg_hbm_off_value)}},1);
      close_result = fclose(stream);
      if ((-1 < read_result) && (close_result == 0)) {
        ios_state.hl_hbm_active = 0;
        goto initial_hbm_finished;
      }
    }
    io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: HBM write failed path=%s on=%d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(ios_state.hl_hbm_path)},{.word=(uint64_t)(uintptr_t)(0)}},3);
  }
initial_hbm_finished:
  fflush((FILE *)domain_stdout());
  ios_business_sensor_init(&io, scratch);
  if ((ios_state.g_ndk_ok & 1) == 0) {
    directory = opendir("/sys/bus/iio/devices");
    if (directory != (DIR *)0x0) {
      entry = readdir(directory);
      while (entry != (dirent *)0x0) {
        if (entry->d_name[0] != '.') {
          io_format(&io,scratch,0x100,"/sys/bus/iio/devices/%s/in_illuminance_input",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(entry->d_name)}},1);
          read_result = access(scratch,4);
          if (read_result == 0) goto fallback_sensor_found;
        }
        entry = readdir(directory);
      }
      closedir(directory);
    }
    directory = opendir("/sys/class/sensors");
    if (directory != (DIR *)0x0) {
      entry = readdir(directory);
      while (entry != (dirent *)0x0) {
        if (entry->d_name[0] != '.') {
          io_format(&io,scratch,0x100,"/sys/class/sensors/%s/in_illuminance_input",(IoArgument[]){{.word=(uint64_t)(uintptr_t)(entry->d_name)}},1);
          read_result = access(scratch,4);
          if (read_result == 0) goto fallback_sensor_found;
        }
        entry = readdir(directory);
      }
      closedir(directory);
    }
    io_print(&io,(FILE *)domain_stdout(),"[%s] No sensor found\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
  }
initialize_settings:
  refresh_settings();
  read_result = ios_state.cached_auto;
  screen_on = read_screen();
  if ((read_result == 1) && (screen_on != 0)) {
    clock_gettime(1,(timespec *)scratch);
    timestamp_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
    if ((299999 < timestamp_ms - ios_state.g_last_aod_check_ms) &&
       (ios_state.g_last_aod_check_ms = timestamp_ms,
       stream = popen("settings get secure doze_always_on 2>/dev/null","r"), stream != (FILE *)0x0
       )) {
      VIEW8(scratch) = 0;
      VIEW8(scratch + 8) = 0;
      reason_text = fgets(scratch,0x10,stream);
      if (reason_text != (char *)0x0) {
        read_result = atoi(scratch);
        ios_state.g_aod_enabled = (int)(read_result == 1);
      }
      pclose(stream);
    }
    refresh_power_wakefulness();
    refresh_display_state_on_edge();
    if ((ios_state.g_power_interactive == 0) ||
       (VIEW2((uint8_t *)(ios_state.g_last_display_state) + 0) != 0x4e4f || ios_state.g_last_display_state[2] != '\0')) {
      chmod(ios_backlight_brightness(),0x1a4);
      memcpy(ios_state.g_brightness_owner, "screen_off_passthrough\000\000\000\000\000\000\000\000\000\000", 32);
      io_print(&io,(FILE *)domain_stdout(),"[%s] Auto mode: passthrough (doze/AOD, wake=%s disp=%s)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(ios_state.g_power_wakefulness)},{.word=(uint64_t)(uintptr_t)(ios_state.g_last_display_state)}},3);
      goto read_warm_brightness;
    }
    read_result = chmod(ios_backlight_brightness(),0x124);
    if (read_result == 0) {
      stream = (FILE *)domain_stdout();
      reason_text = "[%s] Auto mode: locked backlight\n";
      memset(ios_state.g_brightness_owner, 0, sizeof(ios_state.g_brightness_owner));
      memcpy(ios_state.g_brightness_owner, "daemon", 6);
    }
    else {
      chmod(ios_backlight_brightness(),0x1a4);
      stream = (FILE *)domain_stdout();
      reason_text = "[%s] WARN: backlight lock failed, passthrough\n";
      memset(ios_state.g_brightness_owner, 0, sizeof(ios_state.g_brightness_owner));
      memcpy(ios_state.g_brightness_owner, "lock_failed_passthrough", 23);
    }
  }
  else {
    chmod(ios_backlight_brightness(),0x1a4);
    if (read_result == 1) {
      stream = (FILE *)domain_stdout();
      reason_text = "[%s] Auto mode: passthrough (screen off)\n";
      memset(ios_state.g_brightness_owner, 0, sizeof(ios_state.g_brightness_owner));
      memcpy(ios_state.g_brightness_owner, "screen_off_passthrough", 22);
    }
    else {
      stream = (FILE *)domain_stdout();
      reason_text = "[%s] Manual mode: passthrough\n";
      memset(ios_state.g_brightness_owner, 0, sizeof(ios_state.g_brightness_owner));
      memcpy(ios_state.g_brightness_owner, "manual", 6);
    }
  }
  io_print(&io,stream,reason_text,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
read_warm_brightness:
  stream = fopen(ios_backlight_brightness(),"r");
  brightness_or_pid = 0xffffffff;
  VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
  if (stream == (FILE *)0x0) {
    target_brightness = 0;
    current_brightness = 0xffffffff;
  }
  else {
    read_result = fscanf(stream,"%d",(int32_t *)scratch);
    if (read_result != 1) {
      VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
    }
    fclose(stream);
    brightness_or_pid = VIEW4((uint8_t *)(scratch) + 0);
    current_brightness = VIEW4((uint8_t *)(scratch) + 0);
    if ((int)VIEW4((uint8_t *)(scratch) + 0) < 1) {
      target_brightness = 0;
    }
    else {
      clock_gettime(1,(timespec *)scratch);
      target_brightness = brightness_or_pid;
      ios_state.g_target_debounce_init = 1;
      ios_state.g_target_debounce_br = brightness_or_pid;
      ios_state.g_target_candidate_since = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
      ios_state.g_target_candidate_br = brightness_or_pid;
      ios_state.g_target_candidate_dir = 0;
    }
  }
  io_print(&io,(FILE *)domain_stdout(),"[%s] Warm start: %d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)brightness_or_pid)}},2);
  io_print(&io,(FILE *)domain_stdout(),"[%s] Waiting for sensor...\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
  fflush((FILE *)domain_stdout());
  raw_lux = 0.0f;
  read_result = 0x28;
  reason_text = "Sensor timeout";
  do {
    if (ios_state.g_run == 0) break;
    if (ios_state.g_ndk_ok == 1) {
      poll_sensors();
      clock_gettime(1,(timespec *)scratch);
      sensor_poll_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
      if (ios_scene_sensor_fresh(&ios_state, true, sensor_poll_ms) &&
         (((float_bits(ios_state.g_front_lux) < 0x80000000 && (float_bits(ABS(ios_state.g_front_lux)) - 0x800000U) >> 0x18 < 0x7f) ||
          float_bits(ios_state.g_front_lux) - 1U < 0x7fffff) || ABS(ios_state.g_front_lux) == 0.0f)) {
        ios_state.g_back_confirm_event_ms = 0;
        ios_state.g_back_fallback_confirm_count = 0;
        ios_state.g_last_lux_event_ms = ios_state.g_front_lux_event_ms;
        builtin_strncpy(ios_state.g_lux_source,"front",6);
        memset(((uint8_t *)ios_state.g_lux_source + 6), 0, 1);
        smooth_lux = ios_state.g_front_lux;
warmup_retry:
        memset(((uint8_t *)ios_state.g_lux_source + 7), 0, 1);
        ios_state.g_lux_valid = 1;
        memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 8);
        ios_state.g_sensor_stale = 0;
        ios_state.g_sensor_hold_active = 0;
        ios_state.g_sensor_hold_since_ms = 0;
        ios_state.g_sensor_hold_timeout = 0;
        VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) = VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) & 0xffffffffffffff00;
        goto warmup_front_valid;
      }
      if (((ios_state.g_back_ok == 1) && (ios_state.g_back_lux_event_ms != 0)) &&
         ((ios_state.g_back_lux_event_ms <= sensor_poll_ms &&
          ((ios_scene_sensor_fresh(&ios_state, false, sensor_poll_ms) &&
           (((float_bits(ios_state.g_back_lux) < 0x80000000 && (float_bits(ABS(ios_state.g_back_lux)) - 0x800000U) >> 0x18 < 0x7f) ||
            float_bits(ios_state.g_back_lux) - 1U < 0x7fffff) || ABS(ios_state.g_back_lux) == 0.0f)))))) {
        if (ios_state.g_back_confirm_event_ms == ios_state.g_back_lux_event_ms) {
warmup_back_confirmation:
          if (!ios_scene_back_confirmed(&ios_state, sensor_poll_ms)) goto warmup_sample_fallback;
        }
        else {
          ios_state.g_back_confirm_event_ms = ios_state.g_back_lux_event_ms;
          if (ios_state.g_back_fallback_confirm_count < 2) {
            ios_state.g_back_fallback_confirm_count = ios_state.g_back_fallback_confirm_count + 1;
            goto warmup_back_confirmation;
          }
        }
        ios_state.g_last_lux_event_ms = ios_state.g_back_lux_event_ms;
        builtin_strncpy(ios_state.g_lux_source,"back",5);
        memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 2);
        smooth_lux = ios_state.g_back_lux;
        goto warmup_retry;
      }
    }
    else {
      smooth_lux = read_lux_fallback();
warmup_front_valid:
      if (0.0f <= smooth_lux) {
        warmup_timeout = false;
        reason_text = "Sensor ready";
        raw_lux = smooth_lux;
        goto warmup_finished;
      }
    }
warmup_sample_fallback:
    usleep(250000);
    read_result = read_result + -1;
  } while (read_result != 0);
  warmup_timeout = true;
  smooth_lux = 100.0f;
warmup_finished:
  io_print(&io,(FILE *)domain_stdout(),"[%s] %s: %.1f lux\n",(IoArgument[]){{.real=(double)smooth_lux,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(reason_text)}},3);
  fflush((FILE *)domain_stdout());
  if (warmup_timeout) {
    clock_gettime(1,(timespec *)scratch);
    if ((ios_state.g_sensor_stale & 1) == 0) {
      ios_state.g_sensor_stale = 1;
      ios_state.g_sensor_hold_active = 1;
      ios_state.g_sensor_hold_timeout = 0;
      memcpy(ios_state.g_sensor_stale_source, "warmup_timeout\000\000\000\000\000\000\000\000\000", 23);
      ios_state.g_sensor_hold_since_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
      memset(((uint8_t *)ios_state.g_sensor_stale_source + 23), 0, 1);
      stale_age_ms = 0;
      if (ios_state.g_last_lux_event_ms != 0) {
        stale_age_ms = ios_state.g_sensor_hold_since_ms - ios_state.g_last_lux_event_ms;
      }
      io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor stale (%s, %llums)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)("warmup_timeout")},{.word=(uint64_t)(uintptr_t)(stale_age_ms)}},3);
    }
    else {
      ios_state.g_sensor_hold_active = 1;
    }
    memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 7);
    builtin_strncpy(ios_state.g_lux_source,"none",5);
    memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 3);
  }
  ios_state.trend_lux[1] = smooth_lux;
  ios_state.trend_lux[0] = smooth_lux;
  ios_state.trend_lux[3] = smooth_lux;
  ios_state.trend_lux[2] = smooth_lux;
  ios_state.last_stable_lux = smooth_lux;
  ios_state.trend_lux[4] = smooth_lux;
  clock_gettime(1,(timespec *)scratch);
  if (!warmup_timeout) {
    float seed = raw_lux;
    (void)ios_scene_filter(&ios_state,
        (uint64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000, &seed);
  }
  return (IosStartup){current_brightness, target_brightness, screen_on, smooth_lux, raw_lux};
fallback_sensor_found:
  strncpy(ios_state.g_sensor_path,scratch,0xff);
  closedir(directory);
  io_print(&io,(FILE *)domain_stdout(),"[%s] Fallback sensor: %s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(ios_state.g_sensor_path)}},2);
  goto initialize_settings;
}
