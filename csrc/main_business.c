#include "business_support.h"
#include "backlight_paths.h"
#include "scene_adaptation.h"
#include "brightness_preference.h"
#include "brightness_curve.h"
#include "business_state_views.h"
#include "state_observer.h"
/* Complete daemon business flow recovered from 2.5.5.
 * The recovery baseline was validated against the original ELF.
 * Hardware reliability and optional scene-adaptive temporal behavior are maintained
 * fork changes, with separate baseline and enabled-feature regressions. */


static void ios_daemon_shutdown(DomainIo *context) {
  DomainIo io = *context;
  highlux_deactivate("stop");
  chmod(ios_backlight_brightness(),0x1a4);
  if (ios_state.cfg_learn_enabled != 0) {
    save_learn();
  }
  if (((ios_state.g_eq != 0) && (ios_state.g_front_sensor != 0)) && (ios_state.fn_disable != 0)) {
    (void)NDK_switch(&io,ARG(ios_state.fn_disable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor));
  }
  if (((ios_state.g_eq != 0) && (ios_state.g_back_sensor != 0)) && (ios_state.fn_disable != 0)) {
    (void)NDK_switch(&io,ARG(ios_state.fn_disable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_back_sensor));
  }
  if (((ios_state.g_eq != 0) && (ios_state.g_prox_sensor != 0)) && (ios_state.fn_disable != 0)) {
    (void)NDK_switch(&io,ARG(ios_state.fn_disable),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_prox_sensor));
  }
  if (((ios_state.g_eq != 0) && (ios_state.g_mgr != 0)) && (ios_state.fn_destroyEQ != 0)) {
    (void)NDK_destroy(&io,ARG(ios_state.fn_destroyEQ),ARG((void *)ios_state.g_mgr),ARG((void *)ios_state.g_eq));
  }
  if (ios_state.h_lib != 0) {
    dlclose(ios_state.h_lib);
  }
  unlink("/data/local/tmp/luma_curve.pid");
  io_print(&io,(FILE *)domain_stdout(),"[%s] Stopped (bright:%d dark:%d sunlight:%d learn:%d/%d)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.bright_cnt)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.dark_cnt)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.hl_trigger_cnt)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.learn_commit_cnt)},{.word=(uint64_t)(uintptr_t)(0)}},6);
}

int ios_business_main(void)

{
  DomainIo io = {0};
  uint64_t now_ms;
  uint8_t eligible_mask;
  const char *screen_label;
  uint64_t prefix_bits;
  bool condition;
  bool invalid_or_reversed;
  bool changed_or_first_frame;
  uint32_t selected_value;
  int result;
  int delta_or_limit;
  FILE *stream;
  char *reason_text;
  tm *local_time;
  time_t wall_time;
  char *text_cursor;
  uint64_t interval_or_age_ms;
  uint32_t reference_brightness;
  uint32_t prefix_word;
  uint32_t proposed_brightness;
  uint64_t deadline_ms;
  uint32_t selected_brightness;
  uint32_t clamped_brightness;
  uint64_t insertion_index;
  LearnCell *learning_cell;
  uint64_t elapsed_or_bucket;
  uint8_t governor_paused;
  int difference_or_step;
  uint64_t reason_word;
  uint32_t secondary_limit;
  int64_t index_or_age;
  uint8_t state_mask;
  uint32_t capped_brightness;
  uint64_t last_priority_ms;
  uint64_t previous_loop_ms;
  float curve_or_upper;
  float smooth_lux;
  float ratio_or_difference;
  uint64_t reason_tail;
  float bounded_ratio;
  float nits_or_prior_lux;
  float neighbor_lux;
  float raw_lux;
  float trend_sample;
  float smoothing_alpha;
  uint32_t events_processed;
  float wake_sample_lux;
  uint64_t last_log_ms;
  int previous_screen_on;
  uint64_t last_screen_check_ms;
  int screen_on;
  uint32_t target_brightness;
  uint64_t last_state_ms;
  uint32_t current_brightness;
  _Alignas(8) char scratch[256] = {0};

  IosStartup startup = ios_business_startup(&io, scratch);
  current_brightness = startup.current;
  target_brightness = startup.target;
  screen_on = startup.screen_on;
  smooth_lux = startup.smooth_lux;
  raw_lux = startup.raw_lux;
  if (ios_state.g_run != 0) {
    last_priority_ms = 0;
    last_screen_check_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
    last_log_ms = 0;
    last_state_ms = 0;
    smoothing_alpha = 0.06f;
    changed_or_first_frame = true;
    previous_screen_on = 1;
    interval_or_age_ms = 0;
    do {
      clock_gettime(1,(timespec *)scratch);
      now_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
      if ((last_priority_ms == 0) || (299999 < now_ms - last_priority_ms)) {
        setpriority(PRIO_PROCESS,0,10);
        last_priority_ms = now_ms;
      }
      refresh_settings();
      result = ios_state.cached_auto;
      selected_value = (uint32_t)(ios_state.cached_auto == 1);
      previous_loop_ms = interval_or_age_ms;
      if ((-1 < ios_state.prev_auto_mode) && (selected_brightness = (uint32_t)(ios_state.cached_auto == 1), (uint32_t)ios_state.prev_auto_mode != selected_brightness)) {
        io_print(&io,(FILE *)domain_stdout(),dat_text_2,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.prev_auto_mode)},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_brightness)}},3);
        if ((ios_state.prev_auto_mode == 1) && (result != 1)) {
          highlux_deactivate("mode");
          ios_state.g_tr_3 = 0;
          ios_state.g_wake_readonly_until = 0;
          ios_state.g_wake_readonly_extends = -1;
          ios_state.g_wake_readonly_last_hw = -1;
          ios_state.g_target_hold_active = 0;
          ios_state.g_gov_hold = 0;
          ios_state.g_gov_last_dir = 0;
          memset(ios_state.g_wake_lux_samples, 0, 20);
          ios_state.g_wake_lux_sample_count = 0;
          ios_state.g_wake_lux_sample_index = 0;
          ios_state.g_wake_last_lux_event_ms = 0;
          ios_state.g_low_lux_rise_confirmed_until = 0;
          ios_state.g_low_lux_rise_confirmed_samples = 0;
          ios_state.g_low_lux_rise_ceiling_br = 0;
          ios_state.g_low_lux_rise_fallback_hold_until = 0;
          ios_state.g_zero_lux_recent_mask = 0;
          stream = fopen(ios_backlight_brightness(),"r");
          VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
          if (stream == (FILE *)0x0) {
            selected_value = 0xffffffff;
          }
          else {
            result = fscanf(stream,"%d",(int32_t *)scratch);
            if (result != 1) {
              VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
            }
            fclose(stream);
            selected_value = VIEW4((uint8_t *)(scratch) + 0);
            if (0 < (int)VIEW4((uint8_t *)(scratch) + 0)) {
              current_brightness = VIEW4((uint8_t *)(scratch) + 0);
              target_brightness = VIEW4((uint8_t *)(scratch) + 0);
            }
          }
          chmod(ios_backlight_brightness(),0x1a4);
          set_light_sensors_enabled(0);
          memcpy(ios_state.g_write_gov_reason, "manual_passthrough\000", 19);
          memcpy(ios_state.g_brightness_owner, "manual\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 32);
          ios_state.current_poll_ms = 8000;
          ios_state.stable_count = 0;
          ios_state.last_stable_lux = -1.0f;
          ios_state.prev_auto_mode = 0;
          io_print(&io,(FILE *)domain_stdout(),dat_text_4,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)}},2);
          last_state_ms = 0;
        }
        else {
          if ((ios_state.prev_auto_mode != 0) || (result != 1)) goto settings_mode_finished;
          stream = fopen(ios_backlight_brightness(),"r");
          VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
          if (stream == (FILE *)0x0) {
            selected_brightness = 0xffffffff;
          }
          else {
            result = fscanf(stream,"%d",(int32_t *)scratch);
            if (result != 1) {
              VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
            }
            fclose(stream);
            selected_brightness = VIEW4((uint8_t *)(scratch) + 0);
            if (0 < (int)VIEW4((uint8_t *)(scratch) + 0)) {
              current_brightness = VIEW4((uint8_t *)(scratch) + 0);
              target_brightness = VIEW4((uint8_t *)(scratch) + 0);
            }
          }
          chmod(ios_backlight_brightness(),0x1a4);
          ios_state.g_wake_readonly_until = now_ms + 10000;
          memcpy(ios_state.g_brightness_owner, "wake_readonly\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 32);
          ios_state.g_wake_readonly_extends = 0;
          ios_state.g_wake_readonly_last_hw = current_brightness;
          ios_state.g_target_debounce_init = 1;
          ios_state.g_target_debounce_br = current_brightness;
          ios_state.g_target_candidate_br = current_brightness;
          ios_state.g_target_candidate_dir = 0;
          ios_state.g_tr_3 = 0;
          ios_state.g_tr_0 = current_brightness;
          ios_state.g_tr_1 = current_brightness;
          ios_state.g_gov_hold = 0;
          ios_state.g_gov_last_dir = 0;
          ios_state.g_target_hold_active = 0;
          ios_state.stable_count = 0;
          ios_state.last_stable_lux = -1.0f;
          memset(ios_state.g_wake_lux_samples, 0, 20);
          ios_state.g_wake_lux_sample_count = 0;
          ios_state.g_wake_lux_sample_index = 0;
          ios_state.g_wake_last_lux_event_ms = 0;
          ios_state.g_target_candidate_since = now_ms;
          set_light_sensors_enabled(1);
          ios_state.current_poll_ms = 1000;
          ios_state.g_wake_guard_until = 0;
          if (ios_state.g_ndk_ok == 1) {
            poll_sensors();
            now_ms = domain_now_ms(&io);
            if (ios_scene_sensor_fresh(&ios_state, true, now_ms) &&
               (((float_bits(ios_state.g_front_lux) < 0x80000000 && (float_bits(ABS(ios_state.g_front_lux)) - 0x800000U) >> 0x18 < 0x7f)
                || float_bits(ios_state.g_front_lux) - 1U < 0x7fffff) || ABS(ios_state.g_front_lux) == 0.0f)) {
              ios_state.g_back_confirm_event_ms = 0;
              ios_state.g_back_fallback_confirm_count = 0;
              builtin_strncpy(ios_state.g_lux_source,"front",6);
              memset(((uint8_t *)ios_state.g_lux_source + 6), 0, 1);
              ios_state.g_last_lux_event_ms = ios_state.g_front_lux_event_ms;
              curve_or_upper = ios_state.g_front_lux;
            }
            else {
              if (((ios_state.g_back_ok != 1) || (ios_state.g_back_lux_event_ms == 0)) ||
                 ((now_ms < ios_state.g_back_lux_event_ms ||
                  ((!ios_scene_sensor_fresh(&ios_state, false, now_ms) ||
                   (((0x7fffffff < float_bits(ios_state.g_back_lux) ||
                     0x7e < (float_bits(ABS(ios_state.g_back_lux)) - 0x800000U) >> 0x18) &&
                    0x7ffffe < float_bits(ios_state.g_back_lux) - 1U) && ABS(ios_state.g_back_lux) != 0.0f))))))
              goto mode_sensor_update_finished;
              if (ios_state.g_back_confirm_event_ms == ios_state.g_back_lux_event_ms) {
mode_back_confirmation:
                if (!ios_scene_back_confirmed(&ios_state, now_ms)) goto mode_sensor_update_finished;
              }
              else {
                ios_state.g_back_confirm_event_ms = ios_state.g_back_lux_event_ms;
                if (ios_state.g_back_fallback_confirm_count < 2) {
                  ios_state.g_back_fallback_confirm_count = ios_state.g_back_fallback_confirm_count + 1;
                  goto mode_back_confirmation;
                }
              }
              builtin_strncpy(ios_state.g_lux_source,"back",5);
              memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 2);
              ios_state.g_last_lux_event_ms = ios_state.g_back_lux_event_ms;
              curve_or_upper = ios_state.g_back_lux;
            }
            memset(((uint8_t *)ios_state.g_lux_source + 7), 0, 1);
            ios_state.g_lux_valid = 1;
            memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 8);
            ios_state.g_sensor_stale = 0;
            ios_state.g_sensor_hold_active = 0;
            ios_state.g_sensor_hold_since_ms = 0;
            ios_state.g_sensor_hold_timeout = 0;
            VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) = VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) & 0xffffffffffffff00;
            if (((0.0f <= curve_or_upper) &&
                (ratio_or_difference = ABS(curve_or_upper),
                (ratio_or_difference != INFINITY &&
                ((uint32_t)ratio_or_difference < 0x7f800000 && (-1 < (int)curve_or_upper || 0x7ffffe < (int)ratio_or_difference - 1U))) &&
                (-1 < (int)curve_or_upper || 0x7e < ((int)ratio_or_difference - 0x800000U) >> 0x18))) &&
               (ios_state.g_wake_last_lux_event_ms != ios_state.g_last_lux_event_ms)) {
              ios_state.g_wake_last_lux_event_ms = ios_state.g_last_lux_event_ms;
              *(float *)(ios_state.g_wake_lux_samples + (uint64_t)(uint32_t)ios_state.g_wake_lux_sample_index * 4) = curve_or_upper;
              ios_state.g_wake_lux_sample_index = (ios_state.g_wake_lux_sample_index + 1U) % 5;
              if (ios_state.g_wake_lux_sample_count < 5) {
                ios_state.g_wake_lux_sample_count = ios_state.g_wake_lux_sample_count + 1;
              }
            }
          }
mode_sensor_update_finished:
          memcpy(ios_state.g_write_gov_reason, "auto_takeover_wake_readonly\000", 28);
          ios_state.prev_auto_mode = selected_value;
          io_print(&io,(FILE *)domain_stdout(),dat_text_3,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_brightness)}},2);
          last_state_ms = 0;
        }
        goto next_iteration;
      }
settings_mode_finished:
      if ((result == 1) && (screen_on != 0)) {
        prefix_word = VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 0);
        if (prefix_word == 0x756e616d && VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 3) == 0x6c6175) {
          stream = fopen(ios_backlight_brightness(),"r");
          VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
          if (stream != (FILE *)0x0) {
            delta_or_limit = fscanf(stream,"%d",(int32_t *)scratch);
            if (delta_or_limit != 1) {
              VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
            }
            fclose(stream);
            if (0 < (int)VIEW4((uint8_t *)(scratch) + 0)) {
              current_brightness = VIEW4((uint8_t *)(scratch) + 0);
              target_brightness = VIEW4((uint8_t *)(scratch) + 0);
            }
          }
          chmod(ios_backlight_brightness(),0x1a4);
          ios_state.g_wake_readonly_until = now_ms + 10000;
          memcpy(ios_state.g_brightness_owner, "wake_readonly\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 32);
          ios_state.g_wake_readonly_extends = 0;
          ios_state.g_wake_readonly_last_hw = current_brightness;
          ios_state.g_target_debounce_init = 1;
          ios_state.g_target_debounce_br = current_brightness;
          ios_state.g_target_candidate_br = current_brightness;
          ios_state.g_target_candidate_dir = 0;
          memset(ios_state.g_wake_lux_samples, 0, 20);
          ios_state.g_wake_lux_sample_count = 0;
          ios_state.g_wake_lux_sample_index = 0;
          ios_state.g_wake_last_lux_event_ms = 0;
          ios_state.g_target_candidate_since = now_ms;
          set_light_sensors_enabled(1);
          ios_state.current_poll_ms = 1000;
          ios_state.g_wake_guard_until = 0;
          io_print(&io,(FILE *)domain_stdout(),dat_text_5,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
        }
      }
      ios_state.prev_auto_mode = selected_value;
      if (((0x752 < (now_ms - ios_state.cfg_last_check_ms) >> 4) &&
          (ios_state.cfg_last_check_ms = now_ms,
          delta_or_limit = stat("/data/local/tmp/luma_curve.conf",(stat_buffer *)scratch), delta_or_limit == 0)) &&
         (VIEW8(scratch + 88) != ios_state.cfg_last_mtime)) {
        ios_state.cfg_last_mtime = VIEW8(scratch + 88);
        delta_or_limit = load_config();
        reason_text = ios_state.g_config_error;
        if (delta_or_limit != 0) {
          reason_text = "OK";
        }
        io_print(&io,(FILE *)domain_stdout(),"[%s] Config reload by mtime: %s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(reason_text)}},2);
      }
      if (ios_state.g_reset_learn != 0) {
        ios_state.g_reset_learn = 0;
        memset(((char *)ios_state.g_learn),0,0x420);
        ios_state.g_global_offset = 0.0f;
        save_learn();
        stream = fopen("/data/local/tmp/luma_curve_learn_ledger","w");
        if (stream != (FILE *)0x0) {
          selected_value = 0;
          selected_brightness = 0;
          index_or_age = -0x420;
          do {
            if (0.001f < *(float *)((int64_t)&ios_state.fn_getMgrPkg + index_or_age)) {
              io_print(&io,stream,"%d %d %.1f %d %.4f %ld\n",(IoArgument[]){{.real=(double)*(float *)((int64_t)&ios_state.g_global_offset + index_or_age),.floating=true},{.real=(double)*(float *)((int64_t)&ios_state.fn_getMgrPkg + index_or_age),.floating=true},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)(0)},{.word=(uint64_t)(uintptr_t)((uint64_t)*(uint32_t *)((int64_t)&ios_state.hl_hbm_found + index_or_age))},{.word=(uint64_t)(uintptr_t)(*(uint64_t *)((int64_t)&ios_state.h_lib + index_or_age))}},6);
              selected_brightness = selected_brightness + 1;
            }
            if (0.001f < *(float *)((int64_t)&ios_state.fn_createEQ + index_or_age)) {
              io_print(&io,stream,"%d %d %.1f %d %.4f %ld\n",(IoArgument[]){{.real=(double)*(float *)((int64_t)&ios_state.fn_getMgr + index_or_age),.floating=true},{.real=(double)*(float *)((int64_t)&ios_state.fn_createEQ + index_or_age),.floating=true},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)(1)},{.word=(uint64_t)(uintptr_t)((uint64_t)*(uint32_t *)((int64_t)&ios_state.fn_getMgr + index_or_age + 4))},{.word=(uint64_t)(uintptr_t)(*(uint64_t *)((int64_t)&ios_state.fn_getDefSensor + index_or_age))}},6);
              selected_brightness = selected_brightness + 1;
            }
            if (0.001f < *(float *)((int64_t)&ios_state.fn_disable + index_or_age)) {
              io_print(&io,stream,"%d %d %.1f %d %.4f %ld\n",(IoArgument[]){{.real=(double)*(float *)((int64_t)&ios_state.fn_destroyEQ + index_or_age),.floating=true},{.real=(double)*(float *)((int64_t)&ios_state.fn_disable + index_or_age),.floating=true},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)(2)},{.word=(uint64_t)(uintptr_t)((uint64_t)*(uint32_t *)((int64_t)&ios_state.fn_destroyEQ + index_or_age + 4))},{.word=(uint64_t)(uintptr_t)(*(uint64_t *)((int64_t)&ios_state.fn_enable + index_or_age))}},6);
              selected_brightness = selected_brightness + 1;
            }
            if (0.001f < *(float *)((int64_t)&ios_state.fn_minDelay + index_or_age)) {
              io_print(&io,stream,"%d %d %.1f %d %.4f %ld\n",(IoArgument[]){{.real=(double)*(float *)((int64_t)&ios_state.fn_setRate + index_or_age),.floating=true},{.real=(double)*(float *)((int64_t)&ios_state.fn_minDelay + index_or_age),.floating=true},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)(3)},{.word=(uint64_t)(uintptr_t)((uint64_t)*(uint32_t *)((int64_t)&ios_state.fn_setRate + index_or_age + 4))},{.word=(uint64_t)(uintptr_t)(*(uint64_t *)((int64_t)&ios_state.fn_getEvents + index_or_age))}},6);
              selected_brightness = selected_brightness + 1;
            }
            index_or_age = index_or_age + 0x60;
            selected_value = selected_value + 1;
          } while (index_or_age != 0);
          fclose(stream);
          io_print(&io,(FILE *)domain_stdout(),"[%s] Learn ledger: %d cells\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_brightness)}},2);
        }
        clock_gettime(1,(timespec *)scratch);
        ios_state.learn_pending_committed = 0;
        ios_state.g_learn_reset_settle_until = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000 + 15000;
        ios_state.learn_pending_sample_count = 0;
        io_print(&io,(FILE *)domain_stdout(),"[%s] Learn: reset (settle until %llu)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(ios_state.g_learn_reset_settle_until)}},2);
      }
      if (ios_state.g_reload_config != 0) {
        ios_state.g_reload_config = 0;
        delta_or_limit = load_config();
        reason_text = ios_state.g_config_error;
        if (delta_or_limit != 0) {
          reason_text = "OK";
        }
        io_print(&io,(FILE *)domain_stdout(),"[%s] Config reload via SIGUSR2: %s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(reason_text)}},2);
        last_state_ms = 0;
      }
      if (ios_state.g_reload_learn != 0) {
        ios_state.g_reload_learn = 0;
        if (ios_state.cfg_learn_enabled == 0) {
          memset(((char *)ios_state.g_learn),0,0x420);
          reason_text = "[%s] Learn reload ignored: persistent learning retired\n";
          ios_state.g_global_offset = 0.0f;
        }
        else {
          delta_or_limit = load_learn();
          if (delta_or_limit == 0) {
            reason_text = "[%s] Learn reload via SIGHUP: REJECTED\n";
          }
          else {
            reason_text = "[%s] Learn reload via SIGHUP: OK\n";
            ios_state.g_learn_reset_settle_until = now_ms + 15000;
            ios_state.learn_pending_committed = 0;
            ios_state.learn_pending_sample_count = 0;
          }
        }
        io_print(&io,(FILE *)domain_stdout(),reason_text,(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
        last_state_ms = 0;
      }
      selected_value = current_brightness;
      elapsed_or_bucket = now_ms - interval_or_age_ms;
      state_mask = 0;
      if (current_brightness != target_brightness) {
        state_mask = ios_state.g_transition_active;
      }
      if ((state_mask != 1) || (interval_or_age_ms == 0 || (uint32_t)ios_state.current_poll_ms <= elapsed_or_bucket)) {
screen_poll_finished:
        if (interval_or_age_ms != 0 && (uint32_t)ios_state.current_poll_ms > elapsed_or_bucket) {
          result = (int)elapsed_or_bucket;
          if (now_ms <= interval_or_age_ms - 1) {
            result = 0;
          }
          selected_value = ios_state.current_poll_ms - result;
          governor_paused = 0;
          if (0 < ios_state.g_actuator_frame_ms) {
            governor_paused = ios_state.g_transition_active;
          }
          selected_brightness = selected_value;
          if (governor_paused == 1) {
            selected_brightness = ios_state.g_actuator_frame_ms - (int)(now_ms - ios_state.g_last_actuator_ms);
            if ((uint64_t)(uint32_t)ios_state.g_actuator_frame_ms <= now_ms - ios_state.g_last_actuator_ms) {
              selected_brightness = 0;
            }
            if (now_ms <= ios_state.g_last_actuator_ms - 1) {
              selected_brightness = ios_state.g_actuator_frame_ms;
            }
            if ((int)selected_value <= (int)selected_brightness && 0 < (int)selected_value) {
              selected_brightness = selected_value;
            }
          }
          if ((int)selected_brightness < 0x15) {
            selected_brightness = 0x14;
          }
          if (1999 < selected_brightness) {
            selected_brightness = 2000;
          }
          if ((((state_mask == 0) && (ios_state.g_ndk_ok == 1)) && (ios_state.g_light_sensors_enabled != 0)) &&
             (ios_state.fn_looperPoll != 0)) {
            (void)NDK_poll(&io,ARG(ios_state.fn_looperPoll),ARG(selected_brightness),ARG((void *)0x0),ARG((void *)0x0),ARG((void *)0x0));
            result = poll_sensors();
            previous_loop_ms = 0;
            if (result < 1) {
              previous_loop_ms = interval_or_age_ms;
            }
            goto next_iteration;
          }
          goto wait_delay;
        }
        events_processed = 0;
        if ((ios_state.g_ndk_ok == 1) && (ios_state.g_light_sensors_enabled != 0)) {
          events_processed = poll_sensors();
          /* Polling records arrival times after the loop's original clock read.
             Compare freshness against the clock after that IO, never before it. */
          now_ms = domain_now_ms(&io);
        }
        previous_loop_ms = now_ms;
        if ((2999 < now_ms - last_screen_check_ms) &&
           (delta_or_limit = read_screen(), last_screen_check_ms = now_ms, delta_or_limit != previous_screen_on)) {
          screen_label = "ON";
          if (delta_or_limit == 0) {
            screen_label = "OFF";
          }
          ios_state.g_last_screen_edge_ms = now_ms;
          io_print(&io,(FILE *)domain_stdout(),"[%s] Screen: %s\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(screen_label)}},2);
          if ((0 < ios_state.learn_pending_sample_count) && ((ios_state.learn_pending_committed & 1) == 0)) {
            io_print(&io,(FILE *)domain_stdout(),"[%s] Learn: cancelled pending on screen edge\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
          }
          ios_state.learn_pending_committed = 0;
          ios_state.learn_pending_sample_count = 0;
          if ((delta_or_limit != 0) && (result == 1)) {
            chmod(ios_backlight_brightness(),0x1a4);
            if ((299999 < now_ms - ios_state.g_last_aod_check_ms) &&
               (ios_state.g_last_aod_check_ms = now_ms,
               stream = popen("settings get secure doze_always_on 2>/dev/null","r"),
               stream != (FILE *)0x0)) {
              VIEW8(scratch) = 0;
              VIEW8(scratch + 8) = 0;
              reason_text = fgets(scratch,0x10,stream);
              if (reason_text != (char *)0x0) {
                result = atoi(scratch);
                ios_state.g_aod_enabled = (int)(result == 1);
              }
              pclose(stream);
            }
            refresh_display_state_on_edge();
            refresh_power_wakefulness();
            stream = fopen(ios_backlight_brightness(),"r");
            VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
            if (stream != (FILE *)0x0) {
              result = fscanf(stream,"%d",(int32_t *)scratch);
              if (result != 1) {
                VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
              }
              fclose(stream);
              if (0 < (int)VIEW4((uint8_t *)(scratch) + 0)) {
                current_brightness = VIEW4((uint8_t *)(scratch) + 0);
                selected_value = VIEW4((uint8_t *)(scratch) + 0);
              }
            }
            ios_state.g_wake_readonly_until = now_ms + 10000;
            ios_state.g_wake_guard_until = 0;
            ios_state.g_wake_sensor_settle_until = 0;
            ios_state.g_external_write_hold = 0;
            ios_state.g_external_write_hold_until = 0;
            ios_state.g_external_write_burst_count = 0;
            ios_state.g_external_write_burst_start_ms = 0;
            ios_state.fast_dark_confirm_cnt = 0;
            ios_state.fast_dark_streak = 0;
            ios_state.g_zero_lux_since = 0;
            ios_state.g_zero_lux_recent_mask = 0;
            ios_state.g_low_lux_bright_spike_guard = 0;
            ios_state.g_low_lux_bright_spike_count = 0;
            ios_state.g_low_lux_bright_spike_since = 0;
            ios_state.g_low_lux_rise_confirmed_until = 0;
            ios_state.g_low_lux_rise_confirmed_samples = 0;
            ios_state.g_low_lux_rise_ceiling_br = 0;
            ios_state.g_low_lux_rise_fallback_hold_until = 0;
            ios_state.g_target_debounce_init = 1;
            ios_state.g_target_candidate_dir = 0;
            ios_state.g_gov_hold = 0;
            ios_state.g_gov_last_dir = 0;
            ios_state.g_transition_retarget_count = 0;
            ios_state.g_target_hold_active = 0;
            ios_state.g_wake_readonly_extends = 0;
            ios_state.stable_count = 0;
            ios_state.last_stable_lux = -1.0f;
            if ((CONCAT13(ios_state.g_last_display_state[3],
                          CONCAT12(ios_state.g_last_display_state[2],VIEW2((uint8_t *)(ios_state.g_last_display_state) + 0))) ==
                 0x455a4f44 && ios_state.g_last_display_state[4] == '\0') ||
               (CONCAT44(VIEW4((uint8_t *)(ios_state.g_last_display_state) + 4),
                         CONCAT13(ios_state.g_last_display_state[3],
                                  CONCAT12(ios_state.g_last_display_state[2],VIEW2((uint8_t *)(ios_state.g_last_display_state) + 0)))) ==
                0x5355535f455a4f44 &&
                CONCAT53(VIEW5((uint8_t *)(ios_state.g_last_display_state) + 8),VIEW3((uint8_t *)(ios_state.g_last_display_state) + 5)) == 0x444e4550535553)
               ) {
              memcpy(ios_state.g_wake_source, "doze\000", 5);
            }
            else if (ios_state.g_aod_enabled == 0) {
              memcpy(ios_state.g_wake_source, "normal\000", 7);
            }
            else {
              builtin_strncpy(ios_state.g_wake_source,"aod",4);
            }
            memset(ios_state.g_wake_lux_samples, 0, 20);
            ios_state.g_wake_lux_sample_count = 0;
            ios_state.g_wake_lux_sample_index = 0;
            ios_state.g_wake_last_lux_event_ms = 0;
            ios_state.g_wake_readonly_last_hw = selected_value;
            ios_state.g_last_screen_edge_ms = now_ms;
            ios_state.g_wake_hw_br = selected_value;
            ios_state.g_target_debounce_br = selected_value;
            ios_state.g_target_candidate_br = selected_value;
            ios_state.g_target_candidate_since = now_ms;
            set_light_sensors_enabled(1);
            ios_state.current_poll_ms = 1000;
            if (ios_state.g_ndk_ok == 1) {
              poll_sensors();
              now_ms = domain_now_ms(&io);
              if (ios_scene_sensor_fresh(&ios_state, true, now_ms) &&
                 (((float_bits(ios_state.g_front_lux) < 0x80000000 &&
                   (float_bits(ABS(ios_state.g_front_lux)) - 0x800000U) >> 0x18 < 0x7f) ||
                  float_bits(ios_state.g_front_lux) - 1U < 0x7fffff) || ABS(ios_state.g_front_lux) == 0.0f)) {
                ios_state.g_back_confirm_event_ms = 0;
                ios_state.g_back_fallback_confirm_count = 0;
                builtin_strncpy(ios_state.g_lux_source,"front",6);
                memset(((uint8_t *)ios_state.g_lux_source + 6), 0, 1);
                ios_state.g_last_lux_event_ms = ios_state.g_front_lux_event_ms;
                curve_or_upper = ios_state.g_front_lux;
              }
              else {
                if (((ios_state.g_back_ok != 1) || (ios_state.g_back_lux_event_ms == 0)) ||
                   ((now_ms < ios_state.g_back_lux_event_ms ||
                    ((!ios_scene_sensor_fresh(&ios_state, false, now_ms) ||
                     (((0x7fffffff < float_bits(ios_state.g_back_lux) ||
                       0x7e < (float_bits(ABS(ios_state.g_back_lux)) - 0x800000U) >> 0x18) &&
                      0x7ffffe < float_bits(ios_state.g_back_lux) - 1U) && ABS(ios_state.g_back_lux) != 0.0f))))))
                goto wake_observation_enter;
                if (ios_state.g_back_confirm_event_ms == ios_state.g_back_lux_event_ms) {
wake_back_confirmation:
                  if (!ios_scene_back_confirmed(&ios_state, now_ms)) goto wake_observation_enter;
                }
                else {
                  ios_state.g_back_confirm_event_ms = ios_state.g_back_lux_event_ms;
                  if (ios_state.g_back_fallback_confirm_count < 2) {
                    ios_state.g_back_fallback_confirm_count = ios_state.g_back_fallback_confirm_count + 1;
                    goto wake_back_confirmation;
                  }
                }
                builtin_strncpy(ios_state.g_lux_source,"back",5);
                memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 2);
                ios_state.g_last_lux_event_ms = ios_state.g_back_lux_event_ms;
                curve_or_upper = ios_state.g_back_lux;
              }
              memset(((uint8_t *)ios_state.g_lux_source + 7), 0, 1);
              ios_state.g_lux_valid = 1;
              memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 8);
              ios_state.g_sensor_stale = 0;
              ios_state.g_sensor_hold_active = 0;
              ios_state.g_sensor_hold_since_ms = 0;
              ios_state.g_sensor_hold_timeout = 0;
              VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) = VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) & 0xffffffffffffff00;
              if (((0.0f <= curve_or_upper) &&
                  (ratio_or_difference = ABS(curve_or_upper),
                  (ratio_or_difference != INFINITY &&
                  ((uint32_t)ratio_or_difference < 0x7f800000 && (-1 < (int)curve_or_upper || 0x7ffffe < (int)ratio_or_difference - 1U)))
                  && (-1 < (int)curve_or_upper || 0x7e < ((int)ratio_or_difference - 0x800000U) >> 0x18))) &&
                 (ios_state.g_wake_last_lux_event_ms != ios_state.g_last_lux_event_ms)) {
                ios_state.g_wake_last_lux_event_ms = ios_state.g_last_lux_event_ms;
                *(float *)(ios_state.g_wake_lux_samples + (uint64_t)(uint32_t)ios_state.g_wake_lux_sample_index * 4) = curve_or_upper;
                ios_state.g_wake_lux_sample_index = (ios_state.g_wake_lux_sample_index + 1U) % 5;
                if (ios_state.g_wake_lux_sample_count < 5) {
                  ios_state.g_wake_lux_sample_count = ios_state.g_wake_lux_sample_count + 1;
                }
              }
            }
wake_observation_enter:
            memcpy(ios_state.g_brightness_owner, "wake_readonly\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 32);
            io_print(&io,(FILE *)domain_stdout(),"[%s] wake_readonly: ENTER hw=%d source=%s observe=%dms\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)(ios_state.g_wake_source)},{.word=(uint64_t)(uintptr_t)(10000)}},4);
            ios_state.g_tr_3 = 0;
            memcpy(ios_state.g_write_gov_reason, "wake_readonly\000", 14);
            previous_screen_on = 1;
            ios_state.g_tr_0 = selected_value;
            ios_state.g_tr_1 = selected_value;
            write_state_file("auto",1,selected_value,selected_value,ios_state.current_poll_ms,
                             "wake readonly: system brightness owns node",now_ms,raw_lux,smooth_lux);
            screen_on = 1;
            target_brightness = selected_value;
            last_state_ms = now_ms;
            goto next_iteration;
          }
          if (delta_or_limit == 0) {
            highlux_deactivate("screen");
            chmod(ios_backlight_brightness(),0x1a4);
            set_light_sensors_enabled(0);
            memset(ios_state.g_wake_lux_samples, 0, 20);
            ios_state.g_wake_lux_sample_count = 0;
            ios_state.g_wake_lux_sample_index = 0;
            ios_state.g_wake_last_lux_event_ms = 0;
            ios_state.current_poll_ms = 8000;
            ios_state.fast_dark_confirm_cnt = 0;
            ios_state.fast_dark_streak = 0;
            ios_state.g_wake_guard_until = 0;
            ios_state.g_wake_sensor_settle_until = 0;
            ios_state.g_wake_readonly_until = 0;
            ios_state.g_wake_readonly_extends = 0;
            ios_state.g_wake_readonly_last_hw = -1;
            ios_state.g_zero_lux_since = 0;
            ios_state.g_zero_lux_recent_mask = 0;
            ios_state.g_low_lux_bright_spike_guard = 0;
            ios_state.g_low_lux_bright_spike_count = 0;
            ios_state.g_low_lux_bright_spike_since = 0;
            ios_state.g_low_lux_rise_confirmed_until = 0;
            ios_state.g_low_lux_rise_confirmed_samples = 0;
            ios_state.g_low_lux_rise_ceiling_br = 0;
            ios_state.g_low_lux_rise_fallback_hold_until = 0;
            ios_state.g_tr_3 = 0;
            ios_state.g_transition_active = 0;
            ios_state.g_tr_0 = selected_value;
            ios_state.g_tr_1 = selected_value;
            ios_state.stable_count = 0;
            ios_state.last_stable_lux = -1.0f;
            memcpy(ios_state.g_brightness_owner, "screen_off_passthrough\000\000\000\000\000\000\000\000\000\000", 32);
            if (0x7c < (now_ms - last_state_ms) >> 6) {
              reason_text = "auto";
              if (result != 1) {
                reason_text = "manual";
              }
              write_state_file(reason_text,0,selected_value,selected_value,8000,"screen off: brightness paused",now_ms,
                               raw_lux,smooth_lux);
              last_state_ms = now_ms;
            }
            screen_on = 0;
            previous_screen_on = 0;
            goto next_iteration;
          }
          previous_screen_on = 1;
          screen_on = 1;
        }
        if ((ios_state.g_learn_dirty == 1) && (0x752 < (now_ms - ios_state.g_last_learn_flush_ms) >> 4)) {
          save_learn();
          ios_state.g_learn_dirty = 0;
          ios_state.g_last_learn_flush_ms = now_ms;
        }
        if (now_ms < ios_state.g_wake_readonly_until) {
          if (ios_state.g_ndk_ok == 1) {
            if (ios_scene_sensor_fresh(&ios_state, true, now_ms) &&
               (((float_bits(ios_state.g_front_lux) < 0x80000000 && (float_bits(ABS(ios_state.g_front_lux)) - 0x800000U) >> 0x18 < 0x7f)
                || float_bits(ios_state.g_front_lux) - 1U < 0x7fffff) || ABS(ios_state.g_front_lux) == 0.0f)) {
              ios_state.g_back_confirm_event_ms = 0;
              ios_state.g_back_fallback_confirm_count = 0;
              ios_state.g_last_lux_event_ms = ios_state.g_front_lux_event_ms;
              builtin_strncpy(ios_state.g_lux_source,"front",6);
              memset(((uint8_t *)ios_state.g_lux_source + 6), 0, 1);
              curve_or_upper = ios_state.g_front_lux;
ownership_sensor_retry:
              memset(((uint8_t *)ios_state.g_lux_source + 7), 0, 1);
              ios_state.g_lux_valid = 1;
              memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 8);
              ios_state.g_sensor_stale = 0;
              ios_state.g_sensor_hold_active = 0;
              ios_state.g_sensor_hold_since_ms = 0;
              ios_state.g_sensor_hold_timeout = 0;
              VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) = VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) & 0xffffffffffffff00;
              goto ownership_front_valid;
            }
            if ((((ios_state.g_back_ok == 1) && (ios_state.g_back_lux_event_ms != 0)) && (ios_state.g_back_lux_event_ms <= now_ms))
               && ((ios_scene_sensor_fresh(&ios_state, false, now_ms) &&
                   (((float_bits(ios_state.g_back_lux) < 0x80000000 &&
                     (float_bits(ABS(ios_state.g_back_lux)) - 0x800000U) >> 0x18 < 0x7f) ||
                    float_bits(ios_state.g_back_lux) - 1U < 0x7fffff) || ABS(ios_state.g_back_lux) == 0.0f)))) {
              if (ios_state.g_back_confirm_event_ms == ios_state.g_back_lux_event_ms) {
ownership_back_confirmation:
                if (!ios_scene_back_confirmed(&ios_state, now_ms)) goto ownership_sensor_ready;
              }
              else {
                ios_state.g_back_confirm_event_ms = ios_state.g_back_lux_event_ms;
                if (ios_state.g_back_fallback_confirm_count < 2) {
                  ios_state.g_back_fallback_confirm_count = ios_state.g_back_fallback_confirm_count + 1;
                  goto ownership_back_confirmation;
                }
              }
              ios_state.g_last_lux_event_ms = ios_state.g_back_lux_event_ms;
              builtin_strncpy(ios_state.g_lux_source,"back",5);
              memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 2);
              curve_or_upper = ios_state.g_back_lux;
              goto ownership_sensor_retry;
            }
          }
          else {
            curve_or_upper = read_lux_fallback();
ownership_front_valid:
            if (((0.0f <= curve_or_upper) &&
                (ratio_or_difference = ABS(curve_or_upper),
                (ratio_or_difference != INFINITY &&
                ((uint32_t)ratio_or_difference < 0x7f800000 && (-1 < (int)curve_or_upper || 0x7ffffe < (int)ratio_or_difference - 1U))) &&
                (-1 < (int)curve_or_upper || 0x7e < ((int)ratio_or_difference - 0x800000U) >> 0x18))) &&
               ((ios_state.g_last_lux_event_ms != 0 && (ios_state.g_wake_last_lux_event_ms != ios_state.g_last_lux_event_ms)))) {
              ios_state.g_wake_last_lux_event_ms = ios_state.g_last_lux_event_ms;
              *(float *)(ios_state.g_wake_lux_samples + (uint64_t)(uint32_t)ios_state.g_wake_lux_sample_index * 4) = curve_or_upper;
              ios_state.g_wake_lux_sample_index = (ios_state.g_wake_lux_sample_index + 1U) % 5;
              if (ios_state.g_wake_lux_sample_count < 5) {
                ios_state.g_wake_lux_sample_count = ios_state.g_wake_lux_sample_count + 1;
              }
            }
          }
ownership_sensor_ready:
          selected_brightness = ios_state.g_wake_readonly_last_hw;
          if (ios_state.g_wake_readonly_last_hw < 1) {
            selected_brightness = selected_value;
          }
          stream = fopen(ios_backlight_brightness(),"r");
          interval_or_age_ms = 0xffffffff;
          VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
          if (stream != (FILE *)0x0) {
            result = fscanf(stream,"%d",(int32_t *)scratch);
            if (result != 1) {
              VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
            }
            fclose(stream);
            interval_or_age_ms = (uint64_t)VIEW8(scratch) & 0xffffffff;
          }
          reference_brightness = (uint32_t)interval_or_age_ms;
          difference_or_step = reference_brightness - selected_brightness;
          delta_or_limit = ios_state.g_max / 100;
          result = -difference_or_step;
          if (-1 < difference_or_step) {
            result = difference_or_step;
          }
          if (ios_state.g_max < 0x3264) {
            delta_or_limit = 0x80;
          }
          if (0 < (int)reference_brightness) {
            selected_value = reference_brightness;
            ios_state.g_wake_readonly_last_hw = reference_brightness;
            target_brightness = reference_brightness;
            current_brightness = reference_brightness;
          }
          ios_state.g_tr_3 = 0;
          memcpy(ios_state.g_write_gov_reason, "wake_readonly\000", 14);
          ios_state.g_tr_0 = selected_value;
          ios_state.g_tr_1 = selected_value;
          if (((0 < (int)reference_brightness && 0 < (int)selected_brightness) && (delta_or_limit < result)) &&
             (ios_state.g_wake_readonly_extends < 3)) {
            ios_state.g_wake_readonly_extends = ios_state.g_wake_readonly_extends + 1;
            ios_state.g_wake_readonly_until = now_ms + 5000;
            io_print(&io,(FILE *)domain_stdout(),"[%s] wake_readonly: EXTEND hw=%d expected=%d count=%d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(interval_or_age_ms)},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_brightness)},{.word=(uint64_t)(uintptr_t)(ios_state.g_wake_readonly_extends)}},4);
          }
          interval_or_age_ms = 0;
          if (now_ms <= ios_state.g_wake_readonly_until) {
            interval_or_age_ms = ios_state.g_wake_readonly_until - now_ms;
          }
          if (((ios_state.g_wake_lux_sample_count < 2) && (interval_or_age_ms < 0xbb9)) && (ios_state.g_wake_readonly_extends < 3))
          {
            ios_state.g_wake_readonly_extends = ios_state.g_wake_readonly_extends + 1;
            ios_state.g_wake_readonly_until = now_ms + 5000;
            io_print(&io,(FILE *)domain_stdout(),"[%s] wake_readonly: EXTEND waiting fresh ALS samples=%d count=%d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(ios_state.g_wake_lux_sample_count)},{.word=(uint64_t)(uintptr_t)(ios_state.g_wake_readonly_extends)}},3);
          }
          if (2999 < now_ms - last_state_ms) {
            write_state_file("auto",1,selected_value,selected_value,ios_state.current_poll_ms,
                             "wake readonly: system brightness owns node",now_ms,raw_lux,smooth_lux);
            last_state_ms = now_ms;
          }
          ios_state_observer_wait(&io,1000,screen_on,current_brightness,target_brightness,ios_state.current_poll_ms,raw_lux,smooth_lux);
        }
        else {
          if ((ios_state.g_wake_readonly_until != 0) && (-1 < ios_state.g_wake_readonly_extends)) {
            ios_state.g_wake_readonly_until = 0;
            ios_state.g_wake_readonly_extends = -1;
            ios_state.g_wake_readonly_last_hw = -1;
            selected_brightness = ios_state.g_wake_lux_sample_count;
            if (ios_state.g_ndk_ok == 1) {
              delta_or_limit = poll_sensors();
              now_ms = domain_now_ms(&io);
              selected_brightness = ios_state.g_wake_lux_sample_count;
              events_processed = delta_or_limit + events_processed;
              if (ios_scene_sensor_fresh(&ios_state, true, now_ms) &&
                 (((float_bits(ios_state.g_front_lux) < 0x80000000 &&
                   (float_bits(ABS(ios_state.g_front_lux)) - 0x800000U) >> 0x18 < 0x7f) ||
                  float_bits(ios_state.g_front_lux) - 1U < 0x7fffff) || ABS(ios_state.g_front_lux) == 0.0f)) {
                ios_state.g_back_confirm_event_ms = 0;
                ios_state.g_back_fallback_confirm_count = 0;
                builtin_strncpy(ios_state.g_lux_source,"front",6);
                memset(((uint8_t *)ios_state.g_lux_source + 6), 0, 1);
                ios_state.g_last_lux_event_ms = ios_state.g_front_lux_event_ms;
                curve_or_upper = ios_state.g_front_lux;
              }
              else {
                if (((ios_state.g_back_ok != 1) || (ios_state.g_back_lux_event_ms == 0)) ||
                   ((now_ms < ios_state.g_back_lux_event_ms ||
                    ((!ios_scene_sensor_fresh(&ios_state, false, now_ms) ||
                     (((0x7fffffff < float_bits(ios_state.g_back_lux) ||
                       0x7e < (float_bits(ABS(ios_state.g_back_lux)) - 0x800000U) >> 0x18) &&
                      0x7ffffe < float_bits(ios_state.g_back_lux) - 1U) && ABS(ios_state.g_back_lux) != 0.0f))))))
                goto wake_collect_finished;
                if (ios_state.g_back_confirm_event_ms == ios_state.g_back_lux_event_ms) {
wake_collect_back_confirmation:
                  if (!ios_scene_back_confirmed(&ios_state, now_ms)) goto wake_collect_finished;
                }
                else {
                  ios_state.g_back_confirm_event_ms = ios_state.g_back_lux_event_ms;
                  if (ios_state.g_back_fallback_confirm_count < 2) {
                    ios_state.g_back_fallback_confirm_count = ios_state.g_back_fallback_confirm_count + 1;
                    goto wake_collect_back_confirmation;
                  }
                }
                builtin_strncpy(ios_state.g_lux_source,"back",5);
                memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 2);
                ios_state.g_last_lux_event_ms = ios_state.g_back_lux_event_ms;
                curve_or_upper = ios_state.g_back_lux;
              }
              memset(((uint8_t *)ios_state.g_lux_source + 7), 0, 1);
              ios_state.g_lux_valid = 1;
              memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 8);
              ios_state.g_sensor_stale = 0;
              ios_state.g_sensor_hold_active = 0;
              ios_state.g_sensor_hold_since_ms = 0;
              ios_state.g_sensor_hold_timeout = 0;
              VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) = VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) & 0xffffffffffffff00;
              if (((curve_or_upper < 0.0f) ||
                  (ratio_or_difference = ABS(curve_or_upper),
                  (ratio_or_difference == INFINITY ||
                  (0x7f7fffff < (uint32_t)ratio_or_difference || ((int)curve_or_upper < 0 && (int)ratio_or_difference - 1U < 0x7fffff))) ||
                  ((int)curve_or_upper < 0 && ((int)ratio_or_difference - 0x800000U) >> 0x18 < 0x7f))) ||
                 (ios_state.g_wake_last_lux_event_ms == ios_state.g_last_lux_event_ms)) goto wake_collect_finished;
              interval_or_age_ms = (uint64_t)(uint32_t)ios_state.g_wake_lux_sample_count;
              ios_state.g_wake_last_lux_event_ms = ios_state.g_last_lux_event_ms;
              *(float *)(ios_state.g_wake_lux_samples + (uint64_t)(uint32_t)ios_state.g_wake_lux_sample_index * 4) = curve_or_upper;
              ios_state.g_wake_lux_sample_index = (ios_state.g_wake_lux_sample_index + 1U) % 5;
              if ((int)selected_brightness < 5) {
                selected_brightness = selected_brightness + 1;
                goto wake_collect_finished;
              }
              memcpy(scratch,ios_state.g_wake_lux_samples,interval_or_age_ms << 2);
wake_sample_received:
              elapsed_or_bucket = 1;
              do {
                curve_or_upper = *(float *)(scratch + elapsed_or_bucket * 4);
                insertion_index = elapsed_or_bucket;
                do {
                  if (*(float *)(scratch + (insertion_index - 1 & 0xffffffff) * 4) <= curve_or_upper)
                  goto wake_sample_eligible;
                  *(float *)(scratch + insertion_index * 4) =
                       *(float *)(scratch + (insertion_index - 1 & 0xffffffff) * 4);
                  changed_or_first_frame = 1 < (int64_t)insertion_index;
                  insertion_index = insertion_index - 1;
                } while (changed_or_first_frame);
                insertion_index = 0;
wake_sample_eligible:
                elapsed_or_bucket = elapsed_or_bucket + 1;
                *(float *)(scratch + (int64_t)(int)insertion_index * 4) = curve_or_upper;
              } while (elapsed_or_bucket != interval_or_age_ms);
              if (0 < (int)selected_brightness) {
                selected_brightness = selected_brightness - 1;
              }
              curve_or_upper = *(float *)(scratch + (uint64_t)(uint32_t)((int)selected_brightness >> 1) * 4);
              wake_sample_lux = curve_or_upper;
              if (curve_or_upper < 0.0f) goto wake_lux_filter;
              ios_state.trend_lux[1] = curve_or_upper;
              ios_state.trend_lux[0] = curve_or_upper;
              ios_state.trend_lux[3] = curve_or_upper;
              ios_state.trend_lux[2] = curve_or_upper;
              ios_state.trend_idx = 0;
              ios_state.trend_filled = 5;
              ios_state.g_last_processed_lux_event_ms = ios_state.g_last_lux_event_ms;
              ios_state.lux_prev4 = curve_or_upper;
              ios_state.lux_prev3 = curve_or_upper;
              ios_state.lux_prev2 = curve_or_upper;
              ios_state.lux_prev1 = curve_or_upper;
              ios_state.trend_lux[4] = curve_or_upper;
              ios_state.g_actuator_smooth_lux = curve_or_upper;
              smooth_lux = curve_or_upper;
            }
            else {
wake_collect_finished:
              ios_state.g_wake_lux_sample_count = selected_brightness;
              if ((int)selected_brightness < 1) {
                wake_sample_lux = -1.0f;
              }
              else {
                interval_or_age_ms = (uint64_t)selected_brightness;
                memcpy(scratch,ios_state.g_wake_lux_samples,interval_or_age_ms << 2);
                if (selected_brightness != 1) goto wake_sample_received;
                memcpy(&wake_sample_lux, scratch, sizeof(wake_sample_lux));
              }
wake_lux_filter:
              ios_state.g_lux_valid = 0;
              curve_or_upper = raw_lux;
              if ((ios_state.g_sensor_stale & 1) == 0) {
                ios_state.g_sensor_hold_active = 1;
                ios_state.g_sensor_hold_timeout = 0;
                ios_state.g_sensor_stale = 1;
                memcpy(ios_state.g_sensor_stale_source, "wake_no_fresh_als\000\000\000\000\000\000\000", 24);
                index_or_age = 0;
                if (ios_state.g_last_lux_event_ms != 0) {
                  index_or_age = now_ms - ios_state.g_last_lux_event_ms;
                }
                ios_state.g_sensor_hold_since_ms = now_ms;
                io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor stale (%s, %llums)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)("wake_no_fresh_als")},{.word=(uint64_t)(uintptr_t)(index_or_age)}},3);
              }
              else {
                ios_state.g_sensor_hold_active = 1;
              }
            }
            delta_or_limit = chmod(ios_backlight_brightness(),0x124);
            if (delta_or_limit == 0) {
              memcpy(ios_state.g_brightness_owner, "daemon\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 32);
            }
            else {
              chmod(ios_backlight_brightness(),0x1a4);
              memcpy(ios_state.g_brightness_owner, "lock_failed_passthrough\000\000\000\000\000\000\000\000\000", 32);
              io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: wake takeover lock failed, passthrough\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
            }
            ios_state.current_poll_ms = 1000;
            ios_state.g_gov_hold = 0;
            ios_state.g_gov_last_dir = 0;
            ios_state.g_target_hold_active = 0;
            ios_state.g_wake_guard_until = now_ms + 6000;
            ios_state.g_wake_sensor_settle_until = now_ms + 0x4b0;
            ios_state.g_target_debounce_init = 1;
            ios_state.g_target_debounce_br = selected_value;
            ios_state.g_target_candidate_br = selected_value;
            changed_or_first_frame = true;
            ios_state.g_target_candidate_dir = 0;
            ios_state.g_last_actuator_ms = now_ms;
            ios_state.g_last_write_ms = now_ms;
            ios_state.g_target_candidate_since = now_ms;
            io_print(&io,(FILE *)domain_stdout(),"[%s] wake_readonly: EXIT -> daemon takeover hw=%d samples=%d lux=%.1f\n",(IoArgument[]){{.real=(double)wake_sample_lux,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.g_wake_lux_sample_count)}},4);
            raw_lux = curve_or_upper;
          }
          if (result == 1) {
            if (ios_state.g_proximity_near == 0) {
              prefix_word = VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 0);
              if (prefix_word == 0x6d656164 && VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 3) == 0x6e6f6d) {
                stream = fopen(ios_backlight_brightness(),"r");
                VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
                if (stream != (FILE *)0x0) {
                  result = fscanf(stream,"%d",(int32_t *)scratch);
                  if (result != 1) {
                    VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
                  }
                  fclose(stream);
                  if ((0 < (int)VIEW4((uint8_t *)(scratch) + 0)) && (0 < (int)selected_value)) {
                    selected_value = VIEW4((uint8_t *)(scratch) + 0) - selected_value;
                    selected_brightness = -selected_value;
                    if (-1 < (int)selected_value) {
                      selected_brightness = selected_value;
                    }
                    if (0x40 < selected_brightness) {
                      if ((ios_state.g_external_write_burst_start_ms == 0) ||
                         (10000 < now_ms - ios_state.g_external_write_burst_start_ms)) {
                        ios_state.g_external_write_burst_count = 1;
                        ios_state.g_external_write_burst_start_ms = now_ms;
                      }
                      else if (ios_state.g_external_write_burst_count < 1000000) {
                        ios_state.g_external_write_burst_count = ios_state.g_external_write_burst_count + 1;
                      }
                      ios_state.g_transition_active = 0;
                      ios_state.g_tr_3 = 0;
                      ios_state.g_gov_hold = 0;
                      ios_state.g_tr_0 = VIEW4((uint8_t *)(scratch) + 0);
                      ios_state.g_gov_last_dir = 0;
                      ios_state.g_tr_1 = VIEW4((uint8_t *)(scratch) + 0);
                      ios_state.g_target_hold_active = 0;
                      ios_state.g_external_write_hold = 1;
                      ios_state.g_external_write_hold_until = now_ms + 0x4b0;
                      ios_state.g_target_debounce_init = 1;
                      ios_state.g_target_debounce_br = VIEW4((uint8_t *)(scratch) + 0);
                      ios_state.g_target_candidate_br = VIEW4((uint8_t *)(scratch) + 0);
                      ios_state.g_target_candidate_dir = 0;
                      current_brightness = VIEW4((uint8_t *)(scratch) + 0);
                      memcpy(ios_state.g_write_gov_reason, "external_sync\000", 14);
                      ios_state.g_last_write_ms = now_ms;
                      ios_state.g_target_candidate_since = now_ms;
                      io_print(&io,(FILE *)domain_stdout(),"[%s] external brightness sync: hw=%d count=%d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(current_brightness)},{.word=(uint64_t)(uintptr_t)(ios_state.g_external_write_burst_count)}},3);
                    }
                  }
                }
              }
              if (ios_state.g_ndk_ok == 1) {
                if (ios_scene_sensor_fresh(&ios_state, true, now_ms)
                   && (((float_bits(ios_state.g_front_lux) < 0x80000000 &&
                        (float_bits(ABS(ios_state.g_front_lux)) - 0x800000U) >> 0x18 < 0x7f) ||
                       float_bits(ios_state.g_front_lux) - 1U < 0x7fffff) || ABS(ios_state.g_front_lux) == 0.0f)) {
                  ios_state.g_back_confirm_event_ms = 0;
                  ios_state.g_back_fallback_confirm_count = 0;
                  builtin_strncpy(ios_state.g_lux_source,"front",6);
                  memset(((uint8_t *)ios_state.g_lux_source + 6), 0, 1);
                  ios_state.g_last_lux_event_ms = ios_state.g_front_lux_event_ms;
                  curve_or_upper = ios_state.g_front_lux;
                }
                else {
                  if (((ios_state.g_back_ok != 1) || (ios_state.g_back_lux_event_ms == 0)) ||
                     ((now_ms < ios_state.g_back_lux_event_ms ||
                      ((!ios_scene_sensor_fresh(&ios_state, false, now_ms) ||
                       (((0x7fffffff < float_bits(ios_state.g_back_lux) ||
                         0x7e < (float_bits(ABS(ios_state.g_back_lux)) - 0x800000U) >> 0x18) &&
                        0x7ffffe < float_bits(ios_state.g_back_lux) - 1U) && ABS(ios_state.g_back_lux) != 0.0f))))))
                  goto night_profile_ready;
                  if (ios_state.g_back_confirm_event_ms == ios_state.g_back_lux_event_ms) {
sensor_back_confirmation:
                    if (!ios_scene_back_confirmed(&ios_state, now_ms)) goto night_profile_ready;
                  }
                  else {
                    ios_state.g_back_confirm_event_ms = ios_state.g_back_lux_event_ms;
                    if (ios_state.g_back_fallback_confirm_count < 2) {
                      ios_state.g_back_fallback_confirm_count = ios_state.g_back_fallback_confirm_count + 1;
                      goto sensor_back_confirmation;
                    }
                  }
                  builtin_strncpy(ios_state.g_lux_source,"back",5);
                  memset(((uint8_t *)ios_state.g_lux_source + 5), 0, 2);
                  ios_state.g_last_lux_event_ms = ios_state.g_back_lux_event_ms;
                  curve_or_upper = ios_state.g_back_lux;
                }
                memset(((uint8_t *)ios_state.g_lux_source + 7), 0, 1);
                ios_state.g_lux_valid = 1;
                memset(((uint8_t *)ios_state.g_lux_source + 8), 0, 8);
                ios_state.g_sensor_stale = 0;
                ios_state.g_sensor_hold_active = 0;
                ios_state.g_sensor_hold_since_ms = 0;
                ios_state.g_sensor_hold_timeout = 0;
                VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) = VIEW8((uint8_t *)(ios_state.g_sensor_stale_source) + 0) & 0xffffffffffffff00;
                if (curve_or_upper < 0.0f) goto night_profile_ready;
sensor_acquisition_finished:
                if (ios_scene_filter(&ios_state, now_ms, &curve_or_upper)) {
                  invalid_or_reversed = true;
                  goto trend_ready;
                }
                deadline_ms = ios_state.g_low_lux_rise_confirmed_until;
                nits_or_prior_lux = ios_state.lux_prev2;
                ratio_or_difference = ios_state.lux_prev3;
                if (ios_state.g_last_lux_event_ms == 0) goto night_profile_ready;
                ios_state.fast_dark = (int)(now_ms < ios_state.g_dark_drop_until);
                ios_state.fast_dark_candidate = 0;
                if (ios_state.g_last_lux_event_ms != ios_state.g_last_processed_lux_event_ms) {
                  if (2.0f <= ABS(curve_or_upper - smooth_lux)) {
                    raw_lux = smooth_lux + 1.0f;
                    if (smooth_lux + 1.0f <= 1.0f) {
                      raw_lux = 1.0f;
                    }
                    if (((0.18f <= ABS(curve_or_upper - smooth_lux) / raw_lux) ||
                        (ios_state.cfg_hl_threshold * 0.75f <= curve_or_upper)) || ((20.0f < smooth_lux && (curve_or_upper <= 8.0f)))
                       ) {
                      ios_state.g_sensor_reactive_until = now_ms + 6000;
                    }
                  }
                  bounded_ratio = ABS(curve_or_upper);
                  ios_state.lux_prev3 = ios_state.lux_prev2;
                  ios_state.lux_prev4 = ratio_or_difference;
                  ios_state.lux_prev2 = ios_state.lux_prev1;
                  raw_lux = smooth_lux;
                  neighbor_lux = curve_or_upper;
                  if ((bounded_ratio != INFINITY &&
                      ((uint32_t)bounded_ratio < 0x7f800000 &&
                      (-1 < (int)curve_or_upper || 0x7ffffe < (int)bounded_ratio - 1U))) &&
                      (-1 < (int)curve_or_upper || 0x7e < ((int)bounded_ratio - 0x800000U) >> 0x18)) {
                    ios_state.g_zero_lux_recent_mask =
                         (uint32_t)(curve_or_upper <= 0.01f) | (ios_state.g_zero_lux_recent_mask & 3U) << 1;
                    if (0.01f < curve_or_upper && 5.0f < curve_or_upper) {
                      ios_state.g_zero_lux_recent_mask = 0;
                    }
                    if (0.01f < curve_or_upper) {
                      ios_state.g_zero_lux_suspect = 0;
                      if (1.0f <= curve_or_upper) {
                        ios_state.g_zero_lux_since = 0;
                      }
                      if (ios_state.g_low_lux_rise_confirmed_until - 1 < now_ms) {
                        if (ios_state.g_low_lux_rise_confirmed_samples - 1U < 2) {
                          ios_state.g_low_lux_rise_fallback_hold_until = now_ms + 4000;
                        }
                        ios_state.g_low_lux_rise_confirmed_until = 0;
                        ios_state.g_low_lux_rise_confirmed_samples = 0;
                      }
                      if (now_ms < deadline_ms) {
                        if (curve_or_upper <= fmaf(smooth_lux, 1.2f, 2.0f)) {
                          if (ios_state.g_low_lux_rise_confirmed_samples - 1U < 2) {
                            ios_state.g_low_lux_rise_fallback_hold_until = now_ms + 4000;
                          }
                          ios_state.g_low_lux_rise_confirmed_until = 0;
                          ios_state.g_low_lux_rise_confirmed_samples = 0;
                          goto sensor_stale_reason;
                        }
                        invalid_or_reversed = ios_state.g_low_lux_rise_confirmed_samples == 2;
                        if ((2 < ios_state.g_low_lux_rise_confirmed_samples) ||
                           (ios_state.g_low_lux_rise_confirmed_samples = ios_state.g_low_lux_rise_confirmed_samples + 1,
                           invalid_or_reversed)) {
                          ios_state.g_low_lux_rise_ceiling_br = 0;
                          ios_state.g_low_lux_rise_fallback_hold_until = 0;
                        }
                        ios_state.g_low_lux_rise_confirmed_until = now_ms + 6000;
invalid_lux_hold:
                        ios_state.lux_prev1 = curve_or_upper;
                        if (ios_state.g_low_lux_bright_spike_guard == 1) {
                          ios_state.g_low_lux_bright_spike_guard = 0;
                          ios_state.g_low_lux_bright_spike_since = 0;
                          ios_state.g_low_lux_bright_spike_count = 0;
                        }
                      }
                      else {
sensor_stale_reason:
                        if (20.0f < smooth_lux) goto invalid_lux_hold;
                        raw_lux = fmaf(smooth_lux, 3.0f, 6.0f);
                        if (raw_lux <= 12.0f) {
                          raw_lux = 12.0f;
                        }
                        if (curve_or_upper < raw_lux) goto invalid_lux_hold;
                        if ((ios_state.g_low_lux_bright_spike_guard == 1) &&
                           ((ios_state.g_low_lux_bright_spike_since == 0 ||
                            (now_ms - ios_state.g_low_lux_bright_spike_since < 0x1771)))) {
                          raw_lux = curve_or_upper;
                          if (ios_state.g_low_lux_bright_spike_count < 2) {
                            ios_state.g_low_lux_bright_spike_count = ios_state.g_low_lux_bright_spike_count + 1;
                            ios_state.lux_prev3 = nits_or_prior_lux;
                            ios_state.lux_prev1 = curve_or_upper;
                            ios_state.g_low_lux_bright_spike_raw = curve_or_upper;
                            goto stale_reason_finished;
                          }
                        }
                        else {
                          ios_state.g_low_lux_bright_spike_guard = 1;
                          ios_state.g_low_lux_bright_spike_count = 1;
                          ios_state.lux_prev1 = curve_or_upper;
                          ios_state.g_low_lux_bright_spike_raw = curve_or_upper;
                          ios_state.g_low_lux_bright_spike_since = now_ms;
                          io_print(&io,(FILE *)domain_stdout(),"[%s] Low-lux rise candidate: raw=%.1f sm=%.1f\n",(IoArgument[]){{.real=(double)curve_or_upper,.floating=true},{.real=(double)smooth_lux,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")}},3);
stale_reason_finished:
                          raw_lux = ios_state.lux_prev1;
                          if (ios_state.g_low_lux_bright_spike_count < 2) {
                            raw_lux = (float)numeric_minimum(curve_or_upper,smooth_lux + 0.75f);
                            neighbor_lux = ios_state.lux_prev1;
                            goto smooth_spike_filter;
                          }
                        }
                        ios_state.lux_prev1 = raw_lux;
                        ios_state.g_low_lux_bright_spike_guard = 0;
                        ios_state.g_low_lux_bright_spike_since = 0;
                        ios_state.g_low_lux_bright_spike_count = 0;
                        ios_state.g_low_lux_rise_confirmed_until = now_ms + 6000;
                        ios_state.g_low_lux_rise_confirmed_samples = 2;
                        io_print(&io,(FILE *)domain_stdout(),"[%s] Low-lux rise confirmed-limited: raw=%.1f\n",(IoArgument[]){{.real=(double)curve_or_upper,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")}},2);
                      }
                      raw_lux = curve_or_upper;
                      neighbor_lux = ios_state.lux_prev1;
                      if ((0.001f < smooth_lux) &&
                         ((ratio_or_difference = ios_state.cfg_spike_up, ios_state.cfg_spike_up < curve_or_upper / smooth_lux ||
                          (ratio_or_difference = ios_state.cfg_spike_down, curve_or_upper / smooth_lux < ios_state.cfg_spike_down)))) {
                        raw_lux = smooth_lux * ratio_or_difference;
                      }
                    }
                    else {
                      if (ios_state.g_zero_lux_since == 0) {
                        ios_state.g_zero_lux_since = now_ms;
                      }
                      if (((ios_state.g_zero_lux_recent_mask & 1U) + ((uint32_t)ios_state.g_zero_lux_recent_mask >> 2) +
                           ((uint32_t)ios_state.g_zero_lux_recent_mask >> 1 & 1) < 2) &&
                         (now_ms - ios_state.g_zero_lux_since < 0xdac)) {
                        ios_state.g_zero_lux_suspect = 1;
                        raw_lux = smooth_lux * 0.92f;
                      }
                      else {
                        ios_state.g_zero_lux_suspect = 0;
                        raw_lux = 0.0f;
                      }
                    }
                  }
smooth_spike_filter:
                  ios_state.lux_prev1 = neighbor_lux;
                  if ((((((((ios_state.g_zero_lux_suspect & 1) != 0) || (ios_state.lux_prev4 <= 0.0f)) ||
                         (ios_state.lux_prev3 < 0.0f)) || ((ios_state.lux_prev2 < 0.0f || (ios_state.lux_prev1 < 0.0f)))) ||
                       (ios_state.lux_prev4 < ios_state.lux_prev3)) ||
                      ((ios_state.lux_prev3 < ios_state.lux_prev2 || (ios_state.lux_prev2 < ios_state.lux_prev1)))) ||
                     ((ios_state.lux_prev4 - ios_state.lux_prev1) / (ios_state.lux_prev4 + 0.01f) < 0.32f)) {
                    ios_state.fast_dark_confirm_cnt = 0;
                    ios_state.fast_dark_streak = 0;
                  }
                  else {
                    ios_state.fast_dark_candidate = 1;
                    result = ios_state.fast_dark_confirm_cnt + 1;
                    invalid_or_reversed = 0 < ios_state.fast_dark_confirm_cnt;
                    ios_state.fast_dark_confirm_cnt = result;
                    if (invalid_or_reversed) {
                      ios_state.fast_dark_streak = ios_state.fast_dark_streak + 1;
                      ios_state.fast_dark = 1;
                    }
                  }
                  state_mask = 0;
                  if (20.0f < smooth_lux) {
                    state_mask = ios_state.g_zero_lux_suspect ^ 1;
                  }
                  if ((5.0f < curve_or_upper) || (state_mask == 0)) {
                    if ((8.0f < curve_or_upper) || (smooth_lux <= 20.0f)) {
                      ios_state.g_dark_drop_confirm_count = 0;
                    }
                  }
                  else {
                    if (curve_or_upper <= 0.01f) {
                      ios_state.g_dark_drop_confirm_count = 2;
                    }
                    else if (ios_state.g_dark_drop_confirm_count < 2) {
                      ios_state.g_dark_drop_confirm_count = ios_state.g_dark_drop_confirm_count + 1;
                    }
                    if ((1 < ios_state.g_dark_drop_confirm_count) && (ios_state.g_dark_drop_until <= now_ms)) {
                      ios_state.g_dark_drop_until = now_ms + 8000;
                      ios_state.g_dark_settle_until = now_ms + 12000;
                      ios_state.fast_dark = 1;
                      io_print(&io,(FILE *)domain_stdout(),"[%s] Dark drop confirmed from fresh ALS samples\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")}},1);
                    }
                  }
                  ios_state.g_actuator_fast_dark = ios_state.fast_dark;
                  if ((((ios_state.fast_dark == 0) && (ios_state.g_zero_lux_suspect == 0)) && (5.0f < smooth_lux)) &&
                     ((0.0f <= raw_lux && (raw_lux < smooth_lux * 0.72f)))) {
                    ios_state.g_dark_settle_until = now_ms + 6000;
                  }
                  ios_state.trend_lux[(uint32_t)ios_state.trend_idx] = raw_lux;
                  ios_state.trend_idx = (ios_state.trend_idx + 1U) % 5;
                  if (ios_state.trend_filled < 5) {
                    curve_or_upper = 0.0f;
                    result = ios_state.trend_filled + 1;
                    invalid_or_reversed = ios_state.trend_filled == 4;
                    ios_state.trend_filled = result;
                    if (invalid_or_reversed) goto low_lux_zero_filter;
                  }
                  else {
low_lux_zero_filter:
                    result = ios_state.trend_idx - 4;
                    if ((uint32_t)ios_state.trend_idx < 4) {
                      result = ios_state.trend_idx + 1;
                    }
                    delta_or_limit = -3;
                    if ((uint32_t)ios_state.trend_idx < 3) {
                      delta_or_limit = 2;
                    }
                    ratio_or_difference = ios_state.trend_lux[result];
                    bounded_ratio = ios_state.trend_lux[(uint32_t)ios_state.trend_idx];
                    nits_or_prior_lux = ios_state.trend_lux[delta_or_limit + ios_state.trend_idx];
                    curve_or_upper = 0.0f;
                    result = 3;
                    if (1 < (uint32_t)ios_state.trend_idx) {
                      result = -2;
                    }
                    selected_value = 4;
                    if (ios_state.trend_idx != 0) {
                      selected_value = ios_state.trend_idx - 1;
                    }
                    neighbor_lux = ios_state.trend_lux[result + ios_state.trend_idx];
                    trend_sample = ios_state.trend_lux[selected_value];
                    invalid_or_reversed = false;
                    condition = false;
                    if (neighbor_lux < nits_or_prior_lux) {
                      invalid_or_reversed = false;
                      condition = true;
                      if (!NAN(trend_sample) && !NAN(neighbor_lux)) {
                        invalid_or_reversed = trend_sample < neighbor_lux;
                        condition = false;
                      }
                    }
                    if (50.0f < ABS(trend_sample - bounded_ratio)) {
                      if (((raw_lux <= smooth_lux) || (trend_sample <= neighbor_lux)) ||
                         ((neighbor_lux <= nits_or_prior_lux || ((nits_or_prior_lux <= ratio_or_difference || (ratio_or_difference <= bounded_ratio)))))) {
                        curve_or_upper = 0.3f;
                        if (smooth_lux <= raw_lux ||
                            (bounded_ratio <= ratio_or_difference || (invalid_or_reversed == condition || ratio_or_difference <= nits_or_prior_lux))) {
                          curve_or_upper = 0.0f;
                        }
                      }
                      else {
                        curve_or_upper = 0.3f;
                        if (smooth_lux <= 20.0f) {
                          curve_or_upper = 0.0f;
                        }
                      }
                    }
                  }
                  if (0.0f <= raw_lux) {
                    if (ios_state.g_actuator_fast_dark == 0) {
                      nits_or_prior_lux = (float)numeric_minimum(smooth_lux,raw_lux);
                      ratio_or_difference = smooth_lux;
                      if (smooth_lux <= raw_lux) {
                        ratio_or_difference = raw_lux;
                      }
                      bounded_ratio = nits_or_prior_lux + 1.0f;
                      if (nits_or_prior_lux + 1.0f <= 0.001f) {
                        bounded_ratio = 0.001f;
                      }
                      ratio_or_difference = (ratio_or_difference + 1.0f) / bounded_ratio + -1.0f;
                      smoothing_alpha = ios_state.cfg_alpha_fast;
                      if ((ratio_or_difference <= ios_state.cfg_fast_thresh) &&
                         (smoothing_alpha = ios_state.cfg_alpha_mid, ratio_or_difference <= ios_state.cfg_slow_thresh)) goto smooth_ready;
                    }
                    else {
                      smoothing_alpha = ios_state.cfg_alpha_fast;
                      if (ios_state.cfg_alpha_fast <= 0.7f) {
                        smoothing_alpha = 0.7f;
                      }
                    }
                  }
                  else {
smooth_ready:
                    smoothing_alpha = ios_state.cfg_alpha_slow;
                  }
                  if (smoothing_alpha <= curve_or_upper) {
                    smoothing_alpha = curve_or_upper;
                  }
                  result = 0;
                  smooth_lux = fmaf(smoothing_alpha, raw_lux, smooth_lux * (1.0f - smoothing_alpha));
                  ios_state.g_actuator_smooth_lux = 0.0f;
                  if (0.0f <= smooth_lux) {
                    ios_state.g_actuator_smooth_lux = smooth_lux;
                  }
                  if (ios_state.last_stable_lux < 0.0f) {
                    ios_state.last_stable_lux = raw_lux;
                  }
                  if (ios_state.g_actuator_fast_dark == 0) {
                    smooth_lux = ios_state.last_stable_lux;
                    if (ios_state.last_stable_lux <= raw_lux) {
                      smooth_lux = raw_lux;
                    }
                    curve_or_upper = smooth_lux * 0.12f;
                    if (smooth_lux * 0.12f <= 0.35f) {
                      curve_or_upper = 0.35f;
                    }
                    if (((ABS(raw_lux - ios_state.last_stable_lux) <= curve_or_upper) &&
                        ((ios_state.g_low_lux_bright_spike_guard & 1) == 0)) &&
                       (result = ios_state.stable_count, ios_state.stable_count < 1000)) {
                      result = ios_state.stable_count + 1;
                    }
                  }
                  ios_state.stable_count = result;
                  condition = false;
                  invalid_or_reversed = false;
                  ios_state.g_last_processed_lux_event_ms = ios_state.g_last_lux_event_ms;
                  ios_state.last_stable_lux = raw_lux;
                  smooth_lux = ios_state.g_actuator_smooth_lux;
                  goto lux_reason_ready;
                }
                invalid_or_reversed = true;
trend_ready:
                if (!ios_scene_selected_fresh(&ios_state, now_ms)) {
                  if ((ios_state.g_sensor_stale & 1) == 0) {
                    ios_state.g_sensor_hold_active = 1;
                    ios_state.g_sensor_hold_timeout = 0;
                    ios_state.g_sensor_stale = 1;
                    memcpy(ios_state.g_sensor_stale_source, "event_age\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 24);
                    ios_state.g_sensor_hold_since_ms = now_ms;
                    /* Quiet ALS is not a fault; keep state diagnostics without repeated warnings. */
                    condition = invalid_or_reversed;
                    goto lux_valid_filter;
                  }
screen_passthrough:
                  ios_state.g_sensor_hold_active = 1;
                  goto curve_ready;
                }
                if (ios_state.g_lux_valid != 1) goto lux_filtered;
                ios_state.g_sensor_stale = 0;
                ios_state.g_sensor_hold_active = 0;
              }
              else {
                curve_or_upper = read_lux_fallback();
                if (0.0f <= curve_or_upper) goto sensor_acquisition_finished;
night_profile_ready:
                ios_state.fast_dark = (int)(now_ms < ios_state.g_dark_drop_until);
                condition = true;
                invalid_or_reversed = true;
                ios_state.fast_dark_candidate = 0;
lux_reason_ready:
                if (ios_state.g_last_lux_event_ms != 0) goto trend_ready;
lux_valid_filter:
                invalid_or_reversed = condition;
                if (((ios_state.g_lux_valid & 1) == 0) && (ios_state.g_last_lux_event_ms == 0)) {
                  if ((ios_state.g_sensor_stale & 1) != 0) goto screen_passthrough;
                  ios_state.g_sensor_hold_active = 1;
                  ios_state.g_sensor_hold_timeout = 0;
                  ios_state.g_sensor_stale = 1;
                  memcpy(ios_state.g_sensor_stale_source, "no_sensor\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 24);
                  ios_state.g_sensor_hold_since_ms = now_ms;
                  io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor stale (%s, %llums)\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)("no_sensor")},{.word=(uint64_t)(uintptr_t)(0)}},3);
                }
lux_filtered:
                if ((ios_state.g_sensor_stale & 1) != 0) {
curve_ready:
                  if (((ios_state.hl_active & 1) != 0) || (ios_state.hl_hbm_active != 0)) {
                    highlux_deactivate("sensor_stale");
                  }
                }
              }
              if ((((ios_state.g_sensor_hold_active == 1) && (ios_state.g_sensor_hold_since_ms != 0)) &&
                  (119999 < now_ms - ios_state.g_sensor_hold_since_ms)) && ((ios_state.g_sensor_hold_timeout & 1) == 0))
              {
                ios_state.g_sensor_hold_timeout = 1;
                io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor hold timeout (%llums), allowing conservative rise\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(now_ms - ios_state.g_sensor_hold_since_ms)}},2);
              }
              smooth_lux = ios_scene_smooth_tick(&ios_state, now_ms, smooth_lux, &raw_lux);
              curve_or_upper = curve_pct_for_lux(smooth_lux);
              selected_brightness = (uint32_t)(fmaf((float)ios_state.g_max, curve_or_upper, 0.5f));
              curve_or_upper = curve_or_upper * 538.5f;
              selected_value = selected_brightness;
              if (ios_state.g_max <= (int)selected_brightness) {
                selected_value = ios_state.g_max;
              }
              if ((int)selected_brightness < 1) {
                selected_value = 1;
              }
              ratio_or_difference = 538.5f;
              if (curve_or_upper <= 538.5f) {
                ratio_or_difference = curve_or_upper;
              }
              nits_or_prior_lux = 0.2f;
              if (0.2f <= curve_or_upper) {
                nits_or_prior_lux = ratio_or_difference;
              }
              VIEW8(scratch) = time((time_t *)0x0);
              local_time = localtime((time_t *)scratch);
              condition = 0xfffffff3 < local_time->hour - 0x12U;
              curve_or_upper = 0.95f;
              if (condition) {
                curve_or_upper = 1.0f;
              }
              selected_brightness = (int)(curve_or_upper * (float)(int)selected_value);
              if (condition) {
                selected_brightness = selected_value;
              }
              luma_curve_context(curve_or_upper);
              selected_value = luma_preference_apply(&io, now_ms, selected_brightness);
              if (ios_state.cfg_learn_enabled != 0) {
                selected_value = (uint32_t)ios_business_learning(&ios_state, &io, smooth_lux, (int32_t)selected_brightness);
              }
              clock_gettime(1,(timespec *)scratch);
              refresh_battery_state((int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000);
              result = ios_state.g_cached_battery_cap;
              clock_gettime(1,(timespec *)scratch);
              refresh_battery_state((int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000);
              reference_brightness = selected_value;
              if (((result < 0xf) && (ios_state.g_cached_charging == 0)) &&
                 (reference_brightness = (int)((float)ios_state.g_max * 0.7f), (int)selected_value <= (int)((float)ios_state.g_max * 0.7f))) {
                reference_brightness = selected_value;
              }
              selected_value = (uint32_t)ios_business_thermal(&ios_state, &io, (int32_t)reference_brightness);
              IosSunlightResult sunlight = ios_business_sunlight(&ios_state, &io,
                  now_ms, raw_lux, (int32_t)selected_value, invalid_or_reversed);
              selected_value = (uint32_t)sunlight.target;
              state_mask = sunlight.previous_active;
              curve_or_upper = 1.0f;
              reference_brightness = current_brightness;
              if (ios_state.g_low_lux_bright_spike_guard == 1) {
                if ((int)current_brightness <= (int)selected_value) {
                  selected_value = current_brightness;
                }
                builtin_strncpy(ios_state.g_write_gov_reason + 0x10,"amp",4);
                memcpy(ios_state.g_write_gov_reason, "low_lux_spike_cl", 16);
              }
              proposed_brightness = selected_value;
              if (now_ms < ios_state.g_wake_guard_until) {
                ratio_or_difference = 0.05f;
                invalid_or_reversed = true;
                bounded_ratio = 0.06f;
                if ((ios_state.g_aod_enabled == 0) && (VIEW4((uint8_t *)(ios_state.g_wake_source) + 0) != 0x646f61)) {
                  if (VIEW4((uint8_t *)(ios_state.g_wake_source) + 0) == 0x657a6f64 && ios_state.g_wake_source[4] == '\0') {
                    invalid_or_reversed = true;
                    bounded_ratio = 0.06f;
                    ratio_or_difference = 0.05f;
                  }
                  else {
                    prefix_bits = CONCAT25(VIEW2((uint8_t *)(ios_state.g_wake_source) + 5),
                                     CONCAT14(ios_state.g_wake_source[4],VIEW4((uint8_t *)(ios_state.g_wake_source) + 0)));
                    interval_or_age_ms = (CONCAT17(ios_state.g_wake_source[7],prefix_bits) & 0xff00ff00ff00ff00) >> 8 |
                             ((uint64_t)prefix_bits & 0xff00ff00ff00ff) << 8;
                    interval_or_age_ms = (interval_or_age_ms & 0xffff0000ffff0000) >> 0x10 |
                             (interval_or_age_ms & 0xffff0000ffff) << 0x10;
                    interval_or_age_ms = interval_or_age_ms >> 0x20 | interval_or_age_ms << 0x20;
                    elapsed_or_bucket = 0x616f645f73746172;
                    if (interval_or_age_ms == 0x616f645f73746172) {
                      proposed_brightness = (uint32_t)((uint16_t)VIEW2((uint8_t *)(ios_state.g_wake_source) + 8) >> 8) |
                               ((uint16_t)VIEW2((uint8_t *)(ios_state.g_wake_source) + 8) & 0xff00ff) << 8;
                      interval_or_age_ms = (uint64_t)proposed_brightness;
                      if (proposed_brightness != 0x7400) {
                        elapsed_or_bucket = 0x7400;
                        goto target_step_ready;
                      }
                      result = 0;
                    }
                    else {
target_step_ready:
                      result = 1;
                      if (interval_or_age_ms < elapsed_or_bucket) {
                        result = -1;
                      }
                    }
                    invalid_or_reversed = result == 0;
                    ratio_or_difference = 0.05f;
                    bounded_ratio = 0.06f;
                    if (!invalid_or_reversed) {
                      ratio_or_difference = 0.08f;
                      bounded_ratio = 0.12f;
                    }
                  }
                }
                capped_brightness = current_brightness + (int)(fmaf(bounded_ratio, (float)ios_state.g_max, 0.5f));
                proposed_brightness = current_brightness - (int)(fmaf(ratio_or_difference, (float)ios_state.g_max, 0.5f));
                if ((!invalid_or_reversed) && (0 < ios_state.g_last_stable_on_br)) {
                  clamped_brightness = (uint32_t)(fmaf((float)ios_state.g_last_stable_on_br, 1.2f, 0.5f));
                  secondary_limit = (uint32_t)(fmaf((float)ios_state.g_last_stable_on_br, 0.75f, 0.5f));
                  if ((int)capped_brightness <= (int)clamped_brightness) {
                    capped_brightness = clamped_brightness;
                  }
                  if ((int)secondary_limit <= (int)proposed_brightness) {
                    proposed_brightness = secondary_limit;
                  }
                }
                if (ios_state.g_max <= (int)capped_brightness) {
                  capped_brightness = ios_state.g_max;
                }
                if ((int)proposed_brightness < 2) {
                  proposed_brightness = 1;
                }
                clamped_brightness = selected_value;
                if ((int)capped_brightness <= (int)selected_value) {
                  clamped_brightness = capped_brightness;
                }
                ios_state.g_wake_target_raw = selected_value;
                ios_state.g_wake_target_clamped = proposed_brightness;
                if ((int)proposed_brightness <= (int)selected_value) {
                  proposed_brightness = clamped_brightness;
                  ios_state.g_wake_target_clamped = clamped_brightness;
                }
              }
              if ((((ios_state.g_low_lux_rise_confirmed_until != 0) &&
                   (ios_state.g_low_lux_rise_confirmed_until <= now_ms)) &&
                  (0 < ios_state.g_low_lux_rise_confirmed_samples)) && (ios_state.g_low_lux_rise_confirmed_samples < 3))
              {
                ios_state.g_low_lux_rise_confirmed_until = 0;
                ios_state.g_low_lux_rise_confirmed_samples = 0;
                ios_state.g_low_lux_rise_fallback_hold_until = now_ms + 4000;
              }
              invalid_or_reversed = ios_state.g_low_lux_rise_confirmed_until <= now_ms;
              condition = now_ms < ios_state.g_low_lux_rise_fallback_hold_until;
              if (((now_ms < ios_state.g_low_lux_rise_fallback_hold_until) && (0 < ios_state.g_low_lux_rise_ceiling_br))
                 && ((int)proposed_brightness <= ios_state.g_low_lux_rise_ceiling_br)) {
                condition = false;
                ios_state.g_low_lux_rise_ceiling_br = 0;
                ios_state.g_low_lux_rise_fallback_hold_until = 0;
              }
              if ((bool)(((((!invalid_or_reversed && ios_state.g_low_lux_rise_confirmed_samples != 0) &&
                          (invalid_or_reversed || -1 < ios_state.g_low_lux_rise_confirmed_samples)) &&
                         ios_state.g_low_lux_rise_confirmed_samples + -3 < 0) !=
                         (((!invalid_or_reversed && ios_state.g_low_lux_rise_confirmed_samples != 0) &&
                          (invalid_or_reversed || -1 < ios_state.g_low_lux_rise_confirmed_samples)) &&
                         signed_subtraction_overflow(ios_state.g_low_lux_rise_confirmed_samples,3))) | condition)) {
                if ((int)current_brightness < (int)proposed_brightness) {
                  if ((ios_state.g_low_lux_rise_ceiling_br < 1) &&
                     (ios_state.g_low_lux_rise_ceiling_br = current_brightness + (int)(fmaf((float)ios_state.g_max, 0.01f, 0.5f)),
                     ios_state.g_max <= ios_state.g_low_lux_rise_ceiling_br)) {
                    ios_state.g_low_lux_rise_ceiling_br = ios_state.g_max;
                  }
                  if (ios_state.g_low_lux_rise_ceiling_br <= (int)proposed_brightness) {
                    proposed_brightness = ios_state.g_low_lux_rise_ceiling_br;
                  }
                  memcpy(ios_state.g_write_gov_reason, "rise_confirm_limited\000", 21);
                }
              }
              else {
                ios_state.g_low_lux_rise_ceiling_br = 0;
              }
              target_brightness = (uint32_t)ios_business_debounce(&ios_state, now_ms,
                  (int32_t)proposed_brightness, (int32_t)current_brightness, smooth_lux, ios_state.fast_dark != 0);
              if (now_ms < ios_state.g_learn_reset_settle_until) {
                memcpy(((uint8_t *)ios_state.g_write_gov_reason + 4), "le\000", 3);
                if ((int)current_brightness <= ios_state.g_target_debounce_br) {
                  target_brightness = current_brightness;
                }
                builtin_strncpy(ios_state.g_write_gov_reason,"sett",4);
              }
              else if ((ios_state.g_sensor_hold_active == 1) && ((int)current_brightness < ios_state.g_target_debounce_br)) {
                if ((ios_state.g_sensor_hold_since_ms == 0) ||
                   (interval_or_age_ms = now_ms - ios_state.g_sensor_hold_since_ms, interval_or_age_ms < 120000)) {
                  builtin_strncpy(ios_state.g_write_gov_reason + 8,"old",4);
                  memcpy(ios_state.g_write_gov_reason, "sensor_h", 8);
                  target_brightness = current_brightness;
                }
                else {
                  if ((ios_state.g_sensor_hold_timeout & 1) == 0) {
                    ios_state.g_sensor_hold_timeout = 1;
                    io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor hold timeout (%llums), allowing conservative rise\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(now_ms - ios_state.g_sensor_hold_since_ms)}},2);
                    interval_or_age_ms = now_ms - ios_state.g_sensor_hold_since_ms;
                    deadline_ms = ios_state.g_sensor_hold_since_ms;
                    governor_paused = ios_state.g_sensor_hold_active;
                  }
                  else {
                    deadline_ms = 1;
                    governor_paused = 1;
                  }
                  if (((governor_paused != 0) && (deadline_ms != 0)) && (119999 < interval_or_age_ms)) {
                    if ((ios_state.g_sensor_hold_timeout & 1) == 0) {
                      ios_state.g_sensor_hold_timeout = 1;
                      io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor hold timeout (%llums), allowing conservative rise\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(now_ms - ios_state.g_sensor_hold_since_ms)}},2);
                    }
                    if (ios_state.g_learn_reset_settle_until <= now_ms) {
                      ratio_or_difference = (float)ios_state.g_max * 0.015f;
                      if (ratio_or_difference <= 32.0f) {
                        ratio_or_difference = 32.0f;
                      }
                      proposed_brightness = reference_brightness + (int)ratio_or_difference;
                      if (ios_state.g_max <= (int)(reference_brightness + (int)ratio_or_difference)) {
                        proposed_brightness = ios_state.g_max;
                      }
                      if ((int)proposed_brightness <= (int)target_brightness) {
                        target_brightness = proposed_brightness;
                      }
                      memcpy(ios_state.g_write_gov_reason, "sensor_hold_to\000", 15);
                    }
                  }
                }
              }
              proposed_brightness = ios_state.g_tr_1;
              if (ios_state.g_tr_1 < 1) {
                proposed_brightness = reference_brightness;
              }
              if (((ios_state.fast_dark == 0) && (0 < (int)proposed_brightness)) &&
                 (ABS((float)(int)(target_brightness - proposed_brightness)) / (float)(int)proposed_brightness <=
                  fmaf(((float)(int)proposed_brightness / (float)ios_state.g_max), (ios_state.cfg_hyst_high - ios_state.cfg_hyst_low), ios_state.cfg_hyst_low))) {
                target_brightness = ios_state.g_tr_1;
              }
              if (changed_or_first_frame) {
                stream = fopen(ios_backlight_brightness(),"r");
                VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
                if (stream != (FILE *)0x0) {
                  result = fscanf(stream,"%d",(int32_t *)scratch);
                  if (result != 1) {
                    VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
                  }
                  fclose(stream);
                  if (0 < (int)VIEW4((uint8_t *)(scratch) + 0)) {
                    current_brightness = VIEW4((uint8_t *)(scratch) + 0);
                    reference_brightness = VIEW4((uint8_t *)(scratch) + 0);
                  }
                }
                result = target_brightness - reference_brightness;
                if (result == 0) {
                  ios_state.g_tr_3 = 0;
                  reference_brightness = target_brightness;
                  ios_state.g_tr_1 = target_brightness;
                }
                else {
                  delta_or_limit = -result;
                  if (-1 < result) {
                    delta_or_limit = result;
                  }
                  ratio_or_difference = (float)delta_or_limit / (float)ios_state.g_max;
                  if (ratio_or_difference <= 0.08f) {
                    if (ratio_or_difference <= 0.03f) {
                      ios_state.g_tr_3 = 900;
                    }
                    else {
                      ios_state.g_tr_3 = (int)(((ratio_or_difference + -0.03f) * 3600.0f) / 0.05f + 900.0f);
                    }
                  }
                  else {
                    ios_state.g_tr_3 = 0x1194;
                  }
                  if (ios_state.g_tr_3 < 0x7d1) {
                    ios_state.g_tr_3 = 2000;
                  }
                  if (8999 < (uint32_t)ios_state.g_tr_3) {
                    ios_state.g_tr_3 = 9000;
                  }
                  ios_state.g_transition_active = 1;
                  ios_state.g_tr_0 = reference_brightness;
                  ios_state.g_tr_1 = target_brightness;
                  ios_state.g_tr_2 = now_ms;
                }
              }
              if ((ios_state.g_thermal_entry == 1) && ((int)target_brightness < (int)reference_brightness)) {
                delta_or_limit = reference_brightness - target_brightness;
                ios_state.g_tr_3 = 0x4b0;
transition_prepare:
                ratio_or_difference = (float)delta_or_limit / (float)ios_state.g_max;
                ios_state.g_transition_active = 1;
                ios_state.g_tr_0 = reference_brightness;
                ios_state.g_tr_1 = target_brightness;
                ios_state.g_tr_2 = now_ms;
              }
              else {
                if ((ios_state.hl_active == 1) && (result = target_brightness - reference_brightness, result != 0)) {
                  delta_or_limit = -result;
                  if (-1 < result) {
                    delta_or_limit = result;
                  }
                  ios_state.g_tr_3 = 1000;
                  goto transition_prepare;
                }
                if ((ios_state.fast_dark == 0) || ((int)reference_brightness <= (int)target_brightness)) {
                  capped_brightness = target_brightness - reference_brightness;
                  proposed_brightness = -capped_brightness;
                  if (-1 < (int)capped_brightness) {
                    proposed_brightness = capped_brightness;
                  }
                  if ((500 < proposed_brightness) || (ios_state.g_max / 0x14 < (int)proposed_brightness)) {
                    ratio_or_difference = (float)(int)proposed_brightness / (float)ios_state.g_max;
                    result = 0;
                    if (ios_state.g_max != 0) {
                      result = (int)(proposed_brightness * 2000) / ios_state.g_max;
                    }
                    if (result < -4999) {
                      if (ratio_or_difference <= 0.08f) {
                        if (ratio_or_difference <= 0.03f) {
                          ios_state.g_tr_3 = 900;
                        }
                        else {
                          ios_state.g_tr_3 = (int)(((ratio_or_difference + -0.03f) * 3600.0f) / 0.05f + 900.0f);
                        }
                      }
                      else {
                        ios_state.g_tr_3 = 0x1194;
                      }
                    }
                    else {
                      if (1999 < result) {
                        result = 2000;
                      }
                      ios_state.g_tr_3 = result + 5000;
                    }
                    if (ios_state.g_tr_3 < 0x385) {
                      ios_state.g_tr_3 = 900;
                    }
                    if (8999 < (uint32_t)ios_state.g_tr_3) {
                      ios_state.g_tr_3 = 9000;
                    }
                    goto transition_commit;
                  }
                  if ((ios_state.g_tr_3 < 1) || (target_brightness == (uint32_t)ios_state.g_tr_1)) {
                    trans_update(target_brightness,reference_brightness,now_ms);
                    ratio_or_difference = (float)(int)proposed_brightness / (float)ios_state.g_max;
                  }
                  else {
                    if ((int)target_brightness < (int)reference_brightness == ios_state.g_tr_0 <= ios_state.g_tr_1) {
                      ios_state.g_transition_retarget_count = ios_state.g_transition_retarget_count + 1;
                    }
                    trans_update(target_brightness,reference_brightness,now_ms);
                    ratio_or_difference = (float)(int)proposed_brightness / (float)ios_state.g_max;
                  }
                }
                else {
                  proposed_brightness = 0;
                  if (ios_state.g_max != 0) {
                    proposed_brightness = (int)((reference_brightness - target_brightness) * 0x9c4) / ios_state.g_max;
                  }
                  ratio_or_difference = (float)(int)(reference_brightness - target_brightness) / (float)ios_state.g_max;
                  proposed_brightness = proposed_brightness & ((int)proposed_brightness >> 0x1f ^ 0xffffffffU);
                  if (2999 < proposed_brightness) {
                    proposed_brightness = 3000;
                  }
                  ios_state.g_tr_3 = proposed_brightness + 0xdac;
transition_commit:
                  ios_state.g_transition_active = 1;
                  ios_state.g_tr_0 = reference_brightness;
                  ios_state.g_tr_1 = target_brightness;
                  ios_state.g_tr_2 = now_ms;
                }
              }
              result = 0xa0;
              if (ratio_or_difference <= 0.03f) {
                result = 0xdc;
              }
              ios_state.g_actuator_frame_ms = 0x78;
              if (ratio_or_difference <= 0.08f) {
                ios_state.g_actuator_frame_ms = result;
              }
              if (screen_on == 0) {
governor_prepare:
                memcpy(ios_state.g_write_gov_reason, "owner_passthrough\000", 18);
              }
              else {
                prefix_word = VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 0);
                if (((prefix_word != 0x6d656164 || VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 3) != 0x6e6f6d) ||
                    (now_ms < ios_state.g_wake_readonly_until)) ||
                   ((ios_state.g_external_write_hold == 1 && (now_ms < ios_state.g_external_write_hold_until))))
                goto governor_prepare;
                if ((ios_state.g_external_write_hold != 0) && (ios_state.g_external_write_hold_until <= now_ms)) {
                  ios_state.g_external_write_hold = 0;
                }
                ios_state.g_last_actuator_ms = now_ms;
                apply_brightness_frame
                          (now_ms,(int *)&current_brightness,target_brightness,(uint32_t)(ios_state.g_thermal_emergency_bypass != 0)
                          );
              }
              if (raw_lux <= 0.01f) {
                result = ios_state.g_sensor_zero_streak + 1;
                changed_or_first_frame = 0x1d < ios_state.g_sensor_zero_streak;
                ios_state.g_last_actuator_ms = now_ms;
                ios_state.g_sensor_zero_streak = result;
                if (changed_or_first_frame) {
                  io_print(&io,(FILE *)domain_stdout(),"[%s] WARN: sensor zero streak %d\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(ios_state.g_sensor_zero_streak)}},2);
                }
              }
              else {
                ios_state.g_sensor_zero_streak = 0;
                ios_state.g_last_actuator_ms = now_ms;
              }
              result = 0;
              if ((float)ios_state.g_max * 0.95f <= raw_lux) {
                result = ios_state.g_sensor_max_streak + 1;
              }
              if (NAN(raw_lux)) {
                ios_state.g_sensor_nan_count = ios_state.g_sensor_nan_count + 1;
              }
              if (ios_state.cfg_learn_enabled == 0) {
                state_mask = 1;
                memcpy(ios_state.g_learn_block_reason, "cfg_dis", 7);
                VIEW4((uint8_t *)(ios_state.g_learn_block_reason) + 7) =
                     (uint32_t)
                     (CONCAT53(VIEW5((uint8_t *)(s_cfg_disabled) + 8),VIEW3((uint8_t *)(s_cfg_disabled) + 5)) >> 0x10)
                ;
                memcpy(((uint8_t *)ios_state.g_learn_block_reason + 11), "d\000", 2);
                governor_paused = ios_state.cached_auto_adj_valid;
              }
              else if ((ios_state.cached_auto_adj_valid & 1) == 0) {
                builtin_strncpy(ios_state.g_learn_block_reason + 8,"adj",4);
                ios_state.learn_pending_committed = 0;
                state_mask = 1;
                memcpy(ios_state.g_learn_block_reason, "no_auto_", 8);
                ios_state.learn_pending_sample_count = 0;
                ios_state.prev_slider_val = -1;
                governor_paused = 0;
              }
              else {
                if (screen_on == 0) {
                  memcpy(ios_state.g_learn_block_reason, "screen_off\000", 11);
                }
                else {
                  prefix_word = VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 0);
                  if (prefix_word == 0x6d656164 && VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 3) == 0x6e6f6d) {
                    if (now_ms < ios_state.g_wake_readonly_until) {
                      memcpy(ios_state.g_learn_block_reason, "wake_re", 7);
                      VIEW4((uint8_t *)(ios_state.g_learn_block_reason) + 7) =
                           (uint32_t)
                           (CONCAT62(VIEW6((uint8_t *)(s_wake_readonly) + 8),VIEW2((uint8_t *)(s_wake_readonly) + 6))
                           >> 8);
                      memcpy(((uint8_t *)ios_state.g_learn_block_reason + 11), "ly\000", 3);
                      goto governor_ready;
                    }
                    if (ios_state.g_heat_guard_active == 1) {
                      memcpy(((uint8_t *)ios_state.g_learn_block_reason + 16), "e\000", 2);
                      reason_word = VIEW8((uint8_t *)(s_thermal_or_rescue) + 0);
                      reason_tail = VIEW8((uint8_t *)(s_thermal_or_rescue) + 8);
                    }
                    else {
                      reason_word = VIEW8((uint8_t *)(s_sunlight_active) + 0);
                      reason_tail = VIEW8((uint8_t *)(s_sunlight_active) + 8);
                      if (ios_state.hl_active != 1) {
                        if (ios_state.g_transition_active != 1) {
                          if ((ios_state.g_lux_valid & 1) == 0) {
                            reason_text = "sensor_not_valid";
                            goto governor_direction_ready;
                          }
                          if (ios_state.g_sensor_stale == 1) {
                            reason_text = "sensor_stale";
                          }
                          else {
                            if ((ios_state.g_last_lux_event_ms == 0) || (now_ms - ios_state.g_last_lux_event_ms < 0x1f41)
                               ) {
                              if ((ios_state.g_last_screen_edge_ms == 0) ||
                                 (0x270 < (now_ms - ios_state.g_last_screen_edge_ms) >> 4)) {
                                memset(ios_state.g_learn_block_reason, 0, 1);
                                governor_paused = 1;
                                goto state_output_ready;
                              }
                              memcpy(((uint8_t *)ios_state.g_learn_block_reason + 16), "ge\000", 3);
                              reason_word = VIEW8((uint8_t *)(s_recent_screen_edge) + 0);
                              reason_tail = VIEW8((uint8_t *)(s_recent_screen_edge) + 8);
                              goto governor_speed_ready;
                            }
                            reason_text = "lux_age_high";
                          }
                          reason_word = *(uint64_t *)(reason_text + 5);
                          ios_state.g_learn_block_reason[0] = (char)*(uint64_t *)reason_text;
                          VIEW4((uint8_t *)(ios_state.g_learn_block_reason) + 1) =
                               (uint32_t)((uint64_t)*(uint64_t *)reason_text >> 8);
                          ios_state.g_learn_block_reason[5] = (char)reason_word;
                          ios_state.g_learn_block_reason[6] = (char)((uint64_t)reason_word >> 8);
                          VIEW4((uint8_t *)(ios_state.g_learn_block_reason) + 7) = (uint32_t)((uint64_t)reason_word >> 0x10);
                          ios_state.g_learn_block_reason[0xb] = (char)((uint64_t)reason_word >> 0x30);
                          ios_state.g_learn_block_reason[0xc] = (char)((uint64_t)reason_word >> 0x38);
                          goto governor_ready;
                        }
                        builtin_strncpy(ios_state.g_learn_block_reason + 0x10,"ion",4);
                        reason_word = VIEW8((uint8_t *)(s_unstable_transition) + 0);
                        reason_tail = VIEW8((uint8_t *)(s_unstable_transition) + 8);
                      }
                    }
                  }
                  else {
                    reason_text = "owner_not_daemon";
governor_direction_ready:
                    memset(((uint8_t *)ios_state.g_learn_block_reason + 16), 0, 1);
                    reason_word = *(uint64_t *)reason_text;
                    reason_tail = *(uint64_t *)(reason_text + 8);
                  }
governor_speed_ready:
                  io_store_packed((uint8_t *)(ios_state.g_learn_block_reason) + 8, 3, (uint32_t)reason_tail);
                  ios_state.g_learn_block_reason[0xb] = SUB81(reason_tail,3);
                  ios_state.g_learn_block_reason[0xc] = SUB81(reason_tail,4);
                  ios_state.g_learn_block_reason[0xd] = SUB81(reason_tail,5);
                  ios_state.g_learn_block_reason[0xe] = SUB81(reason_tail,6);
                  ios_state.g_learn_block_reason[0xf] = SUB81(reason_tail,7);
                  ios_state.g_learn_block_reason[0] = (char)reason_word;
                  VIEW4((uint8_t *)(ios_state.g_learn_block_reason) + 1) = SUB84(reason_word,1);
                  ios_state.g_learn_block_reason[5] = SUB81(reason_word,5);
                  ios_state.g_learn_block_reason[6] = SUB81(reason_word,6);
                  ios_state.g_learn_block_reason[7] = SUB81(reason_word,7);
                }
governor_ready:
                state_mask = 1;
                governor_paused = 1;
              }
state_output_ready:
              eligible_mask = 0;
              if (ios_state.fast_dark == 0) {
                eligible_mask = (ios_state.g_proximity_near == 0) & ((state_mask | ios_state.hl_active) ^ 0xff);
              }
              state_mask = 0;
              if (0.0f <= raw_lux) {
                state_mask = eligible_mask;
              }
              if (governor_paused == 0) {
                delta_or_limit = ios_state.g_max;
                if (ios_state.g_max < 1) {
                  delta_or_limit = 0x3fff;
                }
                reference_brightness = 0;
                if ((int64_t)delta_or_limit != 0) {
                  reference_brightness = (uint32_t)((int64_t)(((-(uint64_t)(selected_brightness >> 0x1f) & 0xffffff0000000000) |
                                         (uint64_t)selected_brightness << 8) - (int64_t)(int)selected_brightness) / (int64_t)delta_or_limit);
                }
                if (0xfe < (int)reference_brightness) {
                  reference_brightness = 0xff;
                }
                reference_brightness = reference_brightness & ((int)reference_brightness >> 0x1f ^ 0xffffffffU);
                selected_brightness = ios_state.cached_slider;
              }
              else {
                reference_brightness = 0x80;
                selected_brightness = (uint32_t)(fmaf(ios_state.cached_auto_adj, 127.0f, 128.0f) + 0.5f);
                if (0xfe < (int)selected_brightness) {
                  selected_brightness = 0xff;
                }
                selected_brightness = selected_brightness & ((int)selected_brightness >> 0x1f ^ 0xffffffffU);
              }
              state_mask = state_mask ^ 1;
              if (ios_state.cfg_learn_enabled == 0) {
                state_mask = 1;
              }
              ios_state.g_sensor_max_streak = result;
              if ((state_mask == 0) && (-1 < ios_state.prev_slider_val)) {
                result = selected_brightness - ios_state.prev_slider_val;
                if (result == 0) {
                  if (0 < ios_state.learn_pending_sample_count) {
                    result = ios_state.learn_pending_sample_count + 1;
                    changed_or_first_frame = ios_state.learn_pending_sample_count != 1;
                    ios_state.learn_pending_lux_sum = smooth_lux + ios_state.learn_pending_lux_sum;
                    ios_state.learn_pending_lux_sum_mirror = smooth_lux + ios_state.learn_pending_lux_sum_mirror;
                    ios_state.learn_pending_sample_count = result;
                    if (((changed_or_first_frame) && ((ios_state.learn_pending_committed & 1) == 0)) &&
                       ((uint64_t)(int64_t)ios_state.cfg_learn_sample_delay_ms <= now_ms - ios_state.learn_pending_since_ms)) {
                      ratio_or_difference = (float)ios_state.learn_pending_lux_sum / (float)result;
                      if (ratio_or_difference <= 0.0f) {
                        proposed_brightness = 0;
                      }
                      else {
                        bounded_ratio = log10f(ratio_or_difference);
                        proposed_brightness = (uint32_t)(bounded_ratio + 1.0f + bounded_ratio + 1.0f);
                        if (9 < (int)proposed_brightness) {
                          proposed_brightness = 10;
                        }
                        proposed_brightness = proposed_brightness & ((int)proposed_brightness >> 0x1f ^ 0xffffffffU);
                      }
                      VIEW8(scratch) = time((time_t *)0x0);
                      local_time = localtime((time_t *)scratch);
                      result = selected_brightness - reference_brightness;
                      interval_or_age_ms = (uint64_t)proposed_brightness;
                      capped_brightness = local_time->hour / 6;
                      if (2 < (int)capped_brightness) {
                        capped_brightness = 3;
                      }
                      elapsed_or_bucket = (uint64_t)(capped_brightness & ((int)capped_brightness >> 0x1f ^ 0xffffffffU));
                      if (0x7e < result) {
                        result = 0x7f;
                      }
                      learning_cell = (LearnCell *)((char *)ios_state.g_learn) + (uint64_t)proposed_brightness * LEARN_TIME_BUCKETS + elapsed_or_bucket;
                      if (result < -0x7f) {
                        result = -0x80;
                      }
                      result = result + 0x80;
                      bounded_ratio = learning_cell->confidence;
                      if (0.01f <= bounded_ratio) {
                        delta_or_limit = learning_cell->slider;
                        bounded_ratio = fmaf(bounded_ratio, 0.85f, 0.15f);
                        learning_cell->lux = fmaf(learning_cell->lux, 0.8f, ratio_or_difference * 0.2f);
                        learning_cell->slider =
                             (int)(fmaf((float)delta_or_limit, 0.84000003f, (float)result * 0.16f) + 0.5f);
                      }
                      else {
                        learning_cell->lux = ratio_or_difference;
                        bounded_ratio = 0.1f;
                        learning_cell->slider = result;
                      }
                      if (bounded_ratio <= 1.0f) {
                        curve_or_upper = bounded_ratio;
                      }
                      learning_cell->confidence = curve_or_upper;
                      wall_time = time((time_t *)0x0);
                                          ios_state.learn_commit_cnt = ios_state.learn_commit_cnt + 1;
                      learning_cell->timestamp = wall_time;
                      ios_state.g_learn_dirty = 1;
                      io_print(&io,(FILE *)domain_stdout(),"[%s] Learn: b=%d s=%d slider=%d ref=%d pref=%d lux=%.1f conf=%.3f\n",(IoArgument[]){{.real=(double)ratio_or_difference,.floating=true},{.real=(double)curve_or_upper,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)(interval_or_age_ms)},{.word=(uint64_t)(uintptr_t)(elapsed_or_bucket)},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_brightness)},{.word=(uint64_t)(uintptr_t)((uint64_t)reference_brightness)},{.word=(uint64_t)(uintptr_t)(result)}},8);
                      ios_state.learn_pending_committed = 1;
                    }
                  }
                }
                else {
                  delta_or_limit = -result;
                  if (-1 < result) {
                    delta_or_limit = result;
                  }
                  if (delta_or_limit < ios_state.cfg_learn_min_delta) {
                    ios_state.learn_pending_committed = 0;
                    ios_state.learn_pending_sample_count = 0;
                  }
                  else {
                    ios_state.learn_pending_sample_count = 1;
                    ios_state.learn_pending_committed = 0;
                    ios_state.learn_pending_since_ms = now_ms;
                    ios_state.learn_pending_lux_sum = smooth_lux;
                    ios_state.learn_pending_lux_sum_mirror = smooth_lux;
                  }
                }
              }
              reference_brightness = current_brightness;
              if (((screen_on == 0) || (now_ms < ios_state.g_wake_guard_until)) ||
                 (((ios_state.g_heat_guard_active & 1) != 0 || (((ios_state.hl_active & 1) != 0 || (0 < ios_state.g_tr_3)))))) {
output_ready:
                ios_state.g_last_stable_on_hits = 0;
              }
              else {
                delta_or_limit = target_brightness - current_brightness;
                result = -delta_or_limit;
                if (-1 < delta_or_limit) {
                  result = delta_or_limit;
                }
                if ((int)((float)ios_state.g_max * 0.03f) < result) goto output_ready;
                result = ios_state.g_last_stable_on_hits + 1;
                changed_or_first_frame = 0 < ios_state.g_last_stable_on_hits;
                ios_state.g_last_stable_on_hits = result;
                if (changed_or_first_frame) {
                  ios_state.g_last_stable_on_br = current_brightness;
                  ios_state.g_last_stable_on_lux = smooth_lux;
                  ios_state.g_last_stable_on_ms = now_ms;
                }
              }
              state_mask = 0;
              if (((7 < ios_state.stable_count) && ((ios_state.g_transition_active & 1) == 0)) &&
                 (ios_state.g_dark_settle_until <= now_ms)) {
                delta_or_limit = selected_value - current_brightness;
                result = -delta_or_limit;
                if (-1 < delta_or_limit) {
                  result = delta_or_limit;
                }
                difference_or_step = (int)(fmaf((float)ios_state.g_max, 0.01f, 0.5f));
                delta_or_limit = ios_state.cfg_min_step;
                if (ios_state.cfg_min_step <= difference_or_step) {
                  delta_or_limit = difference_or_step;
                }
                state_mask = (((now_ms < ios_state.g_dark_drop_until || delta_or_limit < result) ||
                          ios_state.g_target_candidate_dir != 0) |
                         ios_state.g_sensor_hold_active | ios_state.g_low_lux_bright_spike_guard | ios_state.g_zero_lux_suspect |
                         ios_state.hl_active) ^ 1;
              }
              result = ios_state.g_sensor_rate_mode;
              ios_state.prev_slider_val = selected_brightness;
              delta_or_limit = ios_state.g_sensor_rate_us;
              if ((now_ms < ios_state.g_sensor_reactive_until) || (now_ms < ios_state.g_dark_drop_until)) {
governor_end:
                ios_state.current_poll_ms = 500;
                if (now_ms < ios_state.g_dark_drop_until || ios_state.g_dark_settle_until <= now_ms) {
                  ios_state.current_poll_ms = 0xfa;
                }
                if ((((ios_state.g_ndk_ok == 1) && (ios_state.g_eq != 0)) && (ios_state.fn_setRate != 0)
                    ) && ((ios_state.g_light_sensors_enabled != 0 && (ios_state.g_sensor_rate_us != 250000)))) {
                  if (ios_state.g_front_sensor != 0) {
                    (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor),ARG(250000));
                  }
                  result = 2;
                  if (ios_state.g_back_ok == 1) {
                    delta_or_limit = 250000;
                    if (ios_state.g_back_sensor != 0) {
                      (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_back_sensor),ARG(250000));
                      result = 2;
                    }
                  }
                  else {
                    delta_or_limit = 250000;
                  }
                }
              }
              else {
                governor_paused = ios_state.hl_active;
                if (now_ms < ios_state.g_dark_settle_until) {
                  governor_paused = 1;
                }
                if ((((governor_paused & 1) != 0) || (0 < ios_state.hl_confirm_cnt)) ||
                   (((ios_state.g_low_lux_bright_spike_guard & 1) != 0 || (ios_state.g_zero_lux_suspect != 0))))
                goto governor_end;
                if ((state_mask & 1) == 0) {
                  ios_state.current_poll_ms = 1000;
                  if (((ios_state.g_ndk_ok == 1) && (ios_state.g_eq != 0)) &&
                     ((ios_state.fn_setRate != 0 &&
                      ((ios_state.g_light_sensors_enabled != 0 && (ios_state.g_sensor_rate_us != 1000000)))))) {
                    if (ios_state.g_front_sensor != 0) {
                      (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor),ARG(1000000));
                    }
                    result = 1;
                    if (ios_state.g_back_ok == 1) {
                      delta_or_limit = 1000000;
                      if (ios_state.g_back_sensor != 0) {
                        (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_back_sensor),ARG(1000000));
                        result = 1;
                      }
                    }
                    else {
                      delta_or_limit = 1000000;
                    }
                  }
                }
                else {
                  ios_state.current_poll_ms = 3000;
                  if ((((ios_state.g_ndk_ok == 1) && (ios_state.g_eq != 0)) &&
                      (ios_state.fn_setRate != 0)) &&
                     ((ios_state.g_light_sensors_enabled != 0 &&
                      (difference_or_step = 3000000, ios_state.g_sensor_rate_us != 3000000)))) {
                    if (ios_state.g_front_sensor != 0) {
                      (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_front_sensor),ARG(3000000));
                    }
                    result = 0;
                    delta_or_limit = difference_or_step;
                    if ((ios_state.g_back_ok == 1) && (ios_state.g_back_sensor != 0)) {
                      (void)NDK_rate(&io,ARG(ios_state.fn_setRate),ARG((void *)ios_state.g_eq),ARG((void *)ios_state.g_back_sensor),ARG(3000000));
                      result = 0;
                    }
                  }
                }
              }
              ios_state.g_sensor_rate_us = delta_or_limit;
              ios_state.g_sensor_rate_mode = result;
              if (0x752 < (now_ms - last_state_ms) >> 3) {
                memcpy(ios_state.g_reason_chain, "base>alpha>circ>learn>batt>thermal>apply\000", 41);
                builtin_strncpy(ios_state.g_reason_primary,"auto",5);
                io_format(&io,scratch,0xc0,"%s tgt=%d cur=%d lux=%.1f sm=%.1f",(IoArgument[]){{.real=(double)raw_lux,.floating=true},{.real=(double)smooth_lux,.floating=true},{.word=(uint64_t)(uintptr_t)(ios_state.g_reason_chain)},{.word=(uint64_t)(uintptr_t)((uint64_t)target_brightness)},{.word=(uint64_t)(uintptr_t)((uint64_t)reference_brightness)}},5);
                write_state_file("auto",1,target_brightness,reference_brightness,ios_state.current_poll_ms,scratch,now_ms,raw_lux,
                                 smooth_lux);
                last_state_ms = now_ms;
              }
              if (0x752 < (now_ms - last_log_ms) >> 3) {
                reason_text = " [SUN]";
                if (ios_state.hl_active == 0) {
                  reason_text = "";
                }
                text_cursor = "";
                if (ios_state.fast_dark != 0) {
                  text_cursor = " [FAST]";
                }
                io_print(&io,(FILE *)domain_stdout(),"[%s] AUTO lux=%.1f sm=%.1f a=%.2f nits=%.1f tgt=%d cur=%d poll=%dms ev=%d%s%s\n",(IoArgument[]){{.real=(double)raw_lux,.floating=true},{.real=(double)smooth_lux,.floating=true},{.real=(double)smoothing_alpha,.floating=true},{.real=(double)nits_or_prior_lux,.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)target_brightness)},{.word=(uint64_t)(uintptr_t)((uint64_t)reference_brightness)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.current_poll_ms)},{.word=(uint64_t)(uintptr_t)((uint64_t)events_processed)},{.word=(uint64_t)(uintptr_t)(reason_text)},{.word=(uint64_t)(uintptr_t)(text_cursor)}},11);
                fflush((FILE *)domain_stdout());
                last_log_ms = now_ms;
              }
              clock_gettime(1,(timespec *)scratch);
              interval_or_age_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
              selected_value = ios_state.current_poll_ms - (int)(interval_or_age_ms - now_ms);
              if ((uint64_t)(uint32_t)ios_state.current_poll_ms <= interval_or_age_ms - now_ms) {
                selected_value = 0;
              }
              if (interval_or_age_ms <= now_ms - 1) {
                selected_value = ios_state.current_poll_ms;
              }
              selected_brightness = selected_value;
              if ((ios_state.g_transition_active == 1) && (0 < ios_state.g_actuator_frame_ms)) {
                selected_brightness = ios_state.g_actuator_frame_ms - (int)(interval_or_age_ms - ios_state.g_last_actuator_ms);
                if ((uint64_t)(uint32_t)ios_state.g_actuator_frame_ms <= interval_or_age_ms - ios_state.g_last_actuator_ms) {
                  selected_brightness = 0;
                }
                if (interval_or_age_ms <= ios_state.g_last_actuator_ms - 1) {
                  selected_brightness = ios_state.g_actuator_frame_ms;
                }
                if ((int)selected_value <= (int)selected_brightness && 0 < (int)selected_value) {
                  selected_brightness = selected_value;
                }
              }
              if ((int)selected_brightness < 0x15) {
                selected_brightness = 0x14;
              }
              if (1999 < selected_brightness) {
                selected_brightness = 2000;
              }
              state_mask = 0;
              if (target_brightness != reference_brightness) {
                state_mask = ios_state.g_transition_active;
              }
              if ((((state_mask == 1) || (ios_state.g_ndk_ok != 1)) || (ios_state.g_light_sensors_enabled == 0)) ||
                 (ios_state.fn_looperPoll == 0)) {
                ios_state_observer_wait(&io,selected_brightness,screen_on,current_brightness,target_brightness,ios_state.current_poll_ms,raw_lux,smooth_lux);
                changed_or_first_frame = false;
              }
              else {
                (void)NDK_poll(&io,ARG(ios_state.fn_looperPoll),ARG(selected_brightness),ARG((void *)0x0),ARG((void *)0x0),ARG((void *)0x0));
                result = poll_sensors();
                changed_or_first_frame = false;
                previous_loop_ms = 0;
                if (result < 1) {
                  previous_loop_ms = now_ms;
                }
              }
            }
            else {
              highlux_deactivate("prox");
              ios_state.current_poll_ms = 8000;
              if (0x752 < (now_ms - last_state_ms) >> 3) {
                curve_or_upper = curve_pct_for_lux(smooth_lux);
                delta_or_limit = (int)(fmaf((float)ios_state.g_max, curve_or_upper, 0.5f));
                result = delta_or_limit;
                if (ios_state.g_max <= delta_or_limit) {
                  result = ios_state.g_max;
                }
                if (delta_or_limit < 1) {
                  result = 1;
                }
                write_state_file("auto",1,result,selected_value,8000,"proximity blocked: adjustment paused",
                                 now_ms,raw_lux,smooth_lux);
                last_state_ms = now_ms;
              }
              ios_state_observer_wait(&io,ios_state.current_poll_ms,screen_on,current_brightness,target_brightness,ios_state.current_poll_ms,raw_lux,smooth_lux);
            }
          }
          else {
            stream = fopen(ios_backlight_brightness(),"r");
            VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
            if (stream != (FILE *)0x0) {
              result = fscanf(stream,"%d",(int32_t *)scratch);
              if (result != 1) {
                VIEW4((uint8_t *)(scratch) + 0) = 0xffffffff;
              }
              fclose(stream);
              if (0 < (int)VIEW4((uint8_t *)(scratch) + 0)) {
                current_brightness = VIEW4((uint8_t *)(scratch) + 0);
                selected_value = VIEW4((uint8_t *)(scratch) + 0);
              }
            }
            if (ios_state.current_poll_ms < 8000) {
              ios_state.current_poll_ms = 8000;
            }
            result = ios_state.current_poll_ms;
            memcpy(ios_state.g_brightness_owner, "manual\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000", 32);
            if (0x752 < (now_ms - last_state_ms) >> 3) {
              curve_or_upper = curve_pct_for_lux(smooth_lux);
              difference_or_step = (int)(fmaf((float)ios_state.g_max, curve_or_upper, 0.5f));
              delta_or_limit = difference_or_step;
              if (ios_state.g_max <= difference_or_step) {
                delta_or_limit = ios_state.g_max;
              }
              if (difference_or_step < 1) {
                delta_or_limit = 1;
              }
              write_state_file("manual",screen_on,delta_or_limit,selected_value,result,
                               "manual passthrough: system brightness owns the slider",now_ms,raw_lux,
                               smooth_lux);
              last_state_ms = now_ms;
            }
            if (0x752 < (now_ms - last_log_ms) >> 3) {
              io_print(&io,(FILE *)domain_stdout(),"[%s] MANUAL passthrough slider=%d cur=%d poll=%dms\n",(IoArgument[]){{.word=(uint64_t)(uintptr_t)("LumaCurve")},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.cached_slider)},{.word=(uint64_t)(uintptr_t)((uint64_t)selected_value)},{.word=(uint64_t)(uintptr_t)((uint64_t)(uint32_t)ios_state.current_poll_ms)}},4);
              fflush((FILE *)domain_stdout());
              last_log_ms = now_ms;
            }
            ios_state_observer_wait(&io,ios_state.current_poll_ms,screen_on,current_brightness,target_brightness,ios_state.current_poll_ms,raw_lux,smooth_lux);
          }
        }
      }
      else {
        if (ios_state.g_last_actuator_ms != 0) {
          selected_brightness = ios_state.g_actuator_frame_ms;
          if (ios_state.g_actuator_frame_ms < 1) {
            selected_brightness = 0xfa;
          }
          if (now_ms - ios_state.g_last_actuator_ms < (uint64_t)selected_brightness) goto screen_poll_finished;
        }
        delta_or_limit = read_screen();
        if (delta_or_limit == 0) {
          highlux_deactivate("screen");
          ios_state.g_tr_3 = 0;
          ios_state.g_transition_active = 0;
          chmod(ios_backlight_brightness(),0x1a4);
          set_light_sensors_enabled(0);
          memcpy(ios_state.g_brightness_owner, "screen_off_passthrough\000\000\000\000\000\000\000\000\000", 31);
          last_state_ms = 0;
          previous_screen_on = 0;
          screen_on = 0;
          memcpy(ios_state.g_write_gov_reason, "screen_off_passthrough\000", 23);
          now_ms = ios_state.g_last_actuator_ms;
        }
        else if ((screen_on != 0) && (result == 1)) {
          prefix_word = VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 0);
          if (((prefix_word == 0x6d656164 && VIEW4((uint8_t *)(ios_state.g_brightness_owner) + 3) == 0x6e6f6d) &&
              (ios_state.g_wake_readonly_until <= now_ms)) &&
             ((ios_state.g_external_write_hold != 1 || (ios_state.g_external_write_hold_until <= now_ms)))) {
            if ((ios_state.g_external_write_hold != 0) && (ios_state.g_external_write_hold_until <= now_ms)) {
              ios_state.g_external_write_hold = 0;
            }
            apply_brightness_frame
                      (now_ms,(int *)&current_brightness,target_brightness,(uint32_t)(ios_state.g_thermal_emergency_bypass != 0));
            ios_state.g_actuator_only_count = ios_state.g_actuator_only_count + 1;
          }
        }
        ios_state.g_last_actuator_ms = now_ms;
        clock_gettime(1,(timespec *)scratch);
        now_ms = (int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000;
        selected_value = ios_state.current_poll_ms - (int)(now_ms - interval_or_age_ms);
        if ((uint64_t)(uint32_t)ios_state.current_poll_ms <= now_ms - interval_or_age_ms) {
          selected_value = 0;
        }
        if (now_ms <= interval_or_age_ms - 1) {
          selected_value = ios_state.current_poll_ms;
        }
        selected_brightness = selected_value;
        if ((ios_state.g_transition_active == 1) && (0 < ios_state.g_actuator_frame_ms)) {
          selected_brightness = ios_state.g_actuator_frame_ms - (int)(now_ms - ios_state.g_last_actuator_ms);
          if ((uint64_t)(uint32_t)ios_state.g_actuator_frame_ms <= now_ms - ios_state.g_last_actuator_ms) {
            selected_brightness = 0;
          }
          if (now_ms <= ios_state.g_last_actuator_ms - 1) {
            selected_brightness = ios_state.g_actuator_frame_ms;
          }
          if ((int)selected_value <= (int)selected_brightness && 0 < (int)selected_value) {
            selected_brightness = selected_value;
          }
        }
        if ((int)selected_brightness < 0x15) {
          selected_brightness = 0x14;
        }
        if (1999 < selected_brightness) {
          selected_brightness = 2000;
        }
wait_delay:
        ios_state_observer_wait(&io,selected_brightness,screen_on,current_brightness,target_brightness,ios_state.current_poll_ms,raw_lux,smooth_lux);
      }
next_iteration:
      ios_state_observer_tick(&io,domain_now_ms(&io),screen_on,current_brightness,target_brightness,ios_state.current_poll_ms,raw_lux,smooth_lux);
      interval_or_age_ms = previous_loop_ms;
    } while (ios_state.g_run != 0);
  }
  ios_daemon_shutdown(&io);
  return 0;
}
