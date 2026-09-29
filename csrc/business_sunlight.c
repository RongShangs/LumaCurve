/* Sustained sunlight state, thermal/battery blocks and boost cap. */
#include "business_support.h"
IosSunlightResult ios_business_sunlight(IosState *state, DomainIo *context,
    uint64_t now_ms, float raw_lux, int32_t requested, bool sensor_invalid) {
  DomainIo io = *context;
  uint32_t selected_br = (uint32_t)requested;
  bool invalid = sensor_invalid;
  bool below_threshold;
  int confirm_count;
  char *reason_text;
  uint32_t boosted_br;
  uint8_t previous_active;
  float unit;
  float boost_ratio;
  float upper_ratio;
  float bounded_ratio;
  _Alignas(8) char scratch[256] = {0};
  previous_active = state->hl_active;
  unit = 1.0f;
  if (state->g_low_lux_bright_spike_guard == 1) {
    if (((state->hl_active | state->hl_hbm_active) & 1) != 0) {
      highlux_deactivate("low_lux_spike_guard");
    }
    reason_text = "low_lux_spike_guard";
sunlight_curve_ready:
    strncpy(state->hl_last_block_reason, reason_text, 31);
  }
  else {
    if (state->cfg_hl_temp_thresh <= state->g_thermal_trusted_temp_mdeg) {
      state->hl_thermal_blocked = 1;
      if (state->hl_active != 0) {
        highlux_deactivate("thermal");
      }
      reason_text = "thermal";
      goto sunlight_curve_ready;
    }
    if ((0 < state->g_thermal_trusted_temp_mdeg) &&
       (state->g_thermal_trusted_temp_mdeg <= state->cfg_hl_temp_resume)) {
      state->hl_thermal_blocked = 0;
    }
    clock_gettime(1,(timespec *)scratch);
    refresh_battery_state((int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000);
    if (state->g_cached_battery_cap < state->cfg_hl_bat_thresh) {
      clock_gettime(1,(timespec *)scratch);
      refresh_battery_state((int64_t)VIEW8(scratch) * 1000 + VIEW8(scratch + 8) / 1000000);
      if (state->g_cached_charging == 0) {
        if (state->hl_active == 1) {
          highlux_deactivate("power");
        }
        reason_text = "power";
        goto sunlight_curve_ready;
      }
    }
    if (state->hl_active == 1) {
      if (!invalid) {
        if (state->cfg_hl_threshold * 0.65f <= raw_lux) {
          state->hl_exiting = 0;
          boost_ratio = unit;
          if (1.0f <= state->cfg_hl_extreme_lux - state->cfg_hl_threshold) {
            boost_ratio = state->cfg_hl_extreme_lux - state->cfg_hl_threshold;
          }
          boost_ratio = (raw_lux - state->cfg_hl_threshold) / boost_ratio;
          upper_ratio = unit;
          if (boost_ratio <= 1.0f) {
            upper_ratio = boost_ratio;
          }
          bounded_ratio = 0.0f;
          if (0.0f <= boost_ratio) {
            bounded_ratio = upper_ratio;
          }
          state->hl_boost_br = (int)(fmaf((float)state->g_max, state->cfg_hl_boost_max * bounded_ratio, 0.5f));
        }
        else {
          if (state->hl_exiting != 1) {
            state->hl_exiting = 1;
            state->hl_exit_start = now_ms;
          }
          if (0x5dc < now_ms - state->hl_exit_start) {
            highlux_deactivate("lux_drop");
            goto sunlight_state_ready;
          }
        }
      }
      if ((state->cfg_hl_max_active_ms < 1) ||
         (now_ms - state->hl_active_start <= (uint64_t)(uint32_t)state->cfg_hl_max_active_ms)) {
sunlight_boost_clamp:
        boosted_br = (uint32_t)(fmaf(state->cfg_hl_min_pct, (float)state->g_max, 0.5f));
        if ((int)boosted_br <= (int)(state->hl_boost_br + selected_br)) {
          boosted_br = state->hl_boost_br + selected_br;
        }
        selected_br = boosted_br;
        if (state->g_max <= (int)boosted_br) {
          selected_br = state->g_max;
        }
        if ((int)boosted_br < 1) {
          selected_br = 1;
        }
      }
      else {
        highlux_deactivate("max_active");
        if (0 < state->cfg_hl_cooldown_ms) {
          state->hl_cooldown_until = now_ms + (uint32_t)state->cfg_hl_cooldown_ms;
        }
      }
    }
    else {
      below_threshold = invalid;
      if (raw_lux < state->cfg_hl_threshold) {
        below_threshold = true;
      }
      if (below_threshold) {
        if (invalid) goto sunlight_state_ready;
      }
      else {
        if (state->hl_thermal_blocked == 1) {
          reason_text = "thermal";
        }
        else {
          if (state->hl_cooldown_until <= now_ms) {
            confirm_count = state->hl_confirm_cnt + 1;
            invalid = 0 < state->hl_confirm_cnt;
            state->hl_confirm_cnt = confirm_count;
            if (invalid) {
              boost_ratio = unit;
              if (1.0f <= state->cfg_hl_extreme_lux - state->cfg_hl_threshold) {
                boost_ratio = state->cfg_hl_extreme_lux - state->cfg_hl_threshold;
              }
              boost_ratio = (raw_lux - state->cfg_hl_threshold) / boost_ratio;
              upper_ratio = unit;
              if (boost_ratio <= 1.0f) {
                upper_ratio = boost_ratio;
              }
              bounded_ratio = 0.0f;
              if (0.0f <= boost_ratio) {
                bounded_ratio = upper_ratio;
              }
              state->hl_trigger_cnt = state->hl_trigger_cnt + 1;
              state->hl_active = 1;
              state->hl_exiting = 0;
              memcpy(state->hl_last_block_reason, "active\000\000\000\000\000\000\000\000\000", 15);
              state->hl_boost_br = (int)(fmaf((float)state->g_max, state->cfg_hl_boost_max * bounded_ratio, 0.5f));
              memset(((uint8_t *)state->hl_last_block_reason + 15), 0, 16);
              state->hl_active_start = now_ms;
              io_print(&io,(FILE *)domain_stdout(),"[%s] sunlight: ENTER lux=%.0f boost=%.2f\n",(IoArgument[]){{.real=(double)raw_lux,.floating=true},{.real=(double)(state->cfg_hl_boost_max * bounded_ratio),.floating=true},{.word=(uint64_t)(uintptr_t)("LumaCurve")}},3);
              goto sunlight_boost_clamp;
            }
            goto sunlight_state_ready;
          }
          reason_text = "cooldown";
        }
        strncpy(state->hl_last_block_reason, reason_text, 31);
      }
      state->hl_confirm_cnt = 0;
    }
  }
sunlight_state_ready:
  return (IosSunlightResult){(int32_t)selected_br, previous_active};
}
