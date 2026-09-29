/* Original 2.5.5 defaults expressed as normal typed C initializers. */
#include "state.h"
#include "backlight_paths.h"
#include "temporal_stability.h"
#include "scene_adaptation.h"
#include "brightness_preference.h"
#include "brightness_curve.h"
#include "actuator_diagnostics.h"
uint64_t luma_write_attempts, luma_write_successes;
int luma_write_errno, luma_write_request;
int64_t luma_write_bytes = -1;
const char *luma_write_stage = "not_attempted";
IosState ios_state;
const IosState ios_default_state = {
    .fini_array_with_sentinels = {255,255,255,255,255,255,255,255,76,79,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_max = 16383,
    .cfg_thermal_hard_trigger = 58000,
    .cfg_thermal_hard_resume = 55000,
    .cfg_thermal_hard_floor_pct = 0x1.99999a0000000p-3f,
    .cfg_charging_heat_guard = 1,
    .cached_auto = -1,
    .g_power_interactive = 1,
    .g_last_display_state = {79,78,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_brightness_owner = {100,97,101,109,111,110,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_power_wakefulness = {117,110,107,110,111,119,110,0,0,0,0,0,0,0,0,0},
    .g_run = 1,
    .g_lux_source = {110,111,110,101,0,0,0,0,0,0,0,0,0,0,0,0},
    .last_stable_lux = -0x1.0000000000000p+0f,
    .prev_auto_mode = -1,
    .g_wake_readonly_last_hw = -1,
    .g_write_gov_reason = {110,111,110,101,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .current_poll_ms = 1000,
    .g_config_error = {45,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_wake_source = {110,111,114,109,97,108,0,0,0,0,0,0,0,0,0,0},
    .lux_prev4 = -0x1.0000000000000p+0f,
    .lux_prev3 = -0x1.0000000000000p+0f,
    .lux_prev2 = -0x1.0000000000000p+0f,
    .lux_prev1 = -0x1.0000000000000p+0f,
    .cached_slider = 128,
    .cfg_hl_threshold = 0x1.3880000000000p+12f,
    .cfg_min_step = 4,
    .cfg_hbm_off_value = {48,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .cfg_alpha_fast = 0x1.ae147a0000000p-2f,
    .cfg_alpha_mid = 0x1.70a3d80000000p-3f,
    .cfg_alpha_slow = 0x1.99999a0000000p-5f,
    .cfg_fast_thresh = 0x1.19999a0000000p+0f,
    .cfg_slow_thresh = 0x1.eb851e0000000p-5f,
    .cfg_spike_up = 0x1.8000000000000p+3f,
    .cfg_spike_down = 0x1.eb851e0000000p-5f,
    .cfg_hyst_low = 0x1.eb851e0000000p-6f,
    .cfg_hyst_high = 0x1.eb851e0000000p-5f,
    .cfg_gamma = 0x1.19999a0000000p+1f,
    .cfg_brighten_speed = 0x1.0000000000000p+0f,
    .cfg_darken_speed = 0x1.0000000000000p+0f,
    .cfg_thermal_cap_enable = 1,
    .cfg_hl_extreme_lux = 0x1.d4c0000000000p+14f,
    .cfg_hl_boost_max = 0x1.0000000000000p-1f,
    .cfg_hl_min_pct = 0x1.cccccc0000000p-1f,
    .cfg_hl_cooldown_ms = 10000,
    .cfg_hl_temp_thresh = 52000,
    .cfg_hl_temp_resume = 48000,
    .cfg_hl_bat_thresh = 10,
    .cfg_hl_hbm_enable = 1,
    .cfg_hbm_on_value = {49,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .cfg_learn_strength = 0x1.0000000000000p+0f,
    .cfg_learn_max_offset = 0x1.ae147a0000000p-2f,
    .cfg_learn_sample_delay_ms = 10000,
    .cfg_learn_decay_days = 0x1.c000000000000p+4f,
    .cfg_learn_curve_sigma = 0x1.6666660000000p-1f,
    .cfg_learn_time_neighbor = 0x1.6666660000000p-2f,
    .cfg_learn_min_delta = 8,
    .hl_hbm_discovery = {110,111,116,95,102,111,117,110,100,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_sensor_rate_mode = 1,
    .last_settings_xml_size = UINT64_C(0xffffffffffffffff),
    .cached_slider_source = {98,114,105,103,104,116,110,101,115,115,0,0,0,0,0,0},
    .settings_read_source = {117,110,107,110,111,119,110,0,0,0,0,0,0,0,0,0},
    .g_front_lux = -0x1.0000000000000p+0f,
    .g_back_lux = -0x1.0000000000000p+0f,
    .hl_last_block_reason = {105,100,108,101,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_last_write_readback = -1,
    .g_low_lux_bright_spike_raw = -0x1.0000000000000p+0f,
    .g_thermal_guard_source = {45,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_reason_primary = {97,117,116,111,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},
    .g_cached_battery_cap = 100,
    .prev_slider_val = -1,
};
void ios_reset_data(void) {
    luma_write_attempts=luma_write_successes=0;
    luma_write_errno=luma_write_request=0; luma_write_bytes=-1;
    luma_write_stage="not_attempted";
    ios_state = ios_default_state;
    ios_runtime_io_reset();
    luma_indoor_stability = 1;
    ios_temporal_reset();
    ios_scene_reset(true);
    luma_preference_reset();
    luma_curve_reset();
}
