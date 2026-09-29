/* Publish the module's state-file protocol through an atomic rename. */
#include "actuator.h"
#include "business_api.h"
typedef struct StateFrame {
    const char *mode, *reason;
    int32_t screen, target, current, poll_ms;
    float lux, smooth;
    uint64_t now;
} StateFrame;
static uint64_t time_left(uint64_t now, uint64_t until) {
    return now <= until ? until - now : 0;
}
static uint64_t sensor_age(uint64_t now, uint64_t timestamp) {
    return now <= timestamp - 1 ? 0 : now - timestamp;
}
static const char *sunlight_reason_text(const char *reason) {
    static const struct {
        const char *code, *text;
    } descriptions[] = {{"idle", "Idle"},
                        {"thermal", "Blocked: thermal"},
                        {"power", "Blocked: low battery"},
                        {"cooldown", "Blocked: cooldown"},
                        {"active", "Active"},
                        {"lux_drop", "Exit: lux dropped"},
                        {"max_active", "Exit: max active time"},
                        {"exit", "Exit"},
                        {"mode", "Exit: mode change"},
                        {"screen", "Exit: screen off"},
                        {"prox", "Exit: proximity"},
                        {"sensor_stale", "Exit: sensor stale"},
                        {"low_lux_spike_guard", "Blocked: unconfirmed low-lux rise"},
                        {"stop", "Exit: daemon stop"}};
    for (size_t i = 0; i < sizeof(descriptions) / sizeof(descriptions[0]); i++)
        if (!strcmp(reason, descriptions[i].code))
            return descriptions[i].text;
    return reason;
}
static void state_word(DomainIo *io, uintptr_t stream, const char *format, uint64_t value) {
    PRINT(io, stream, format, value);
}
static void state_real(DomainIo *io, uintptr_t stream, const char *format, double value) {
    domain_print(io, stream, format, NULL, 0, &value, 1);
}
static void state_text(DomainIo *io, uintptr_t stream, const char *format, const char *value) {
    PRINT(io, stream, format, ARG(value));
}
static int publish_state(DomainIo *io, const StateFrame *frame) {
    uintptr_t stream = domain_open(io, "/data/local/tmp/luma_curve_state.tmp", "w");
    if (!stream)
        return 0;
    ios_battery_refresh(io, domain_now_ms(io));
    int battery = INT(g_cached_battery_cap);
    ios_battery_refresh(io, domain_now_ms(io));
    int charging = INT(g_cached_charging);
    bool allowed = frame->screen != 0 && !strcmp(frame->mode, "auto") &&
                   !strcmp(STRING(g_brightness_owner), "daemon");
    bool can_write = allowed && TIME(g_wake_readonly_until) <= frame->now;
    if (can_write) {
        if (FLAG(g_external_write_hold) == 1 && frame->now < TIME(g_external_write_hold_until))
            can_write = false;
        else if (FLAG(g_external_write_hold) && TIME(g_external_write_hold_until) <= frame->now)
            SET_FLAG(g_external_write_hold, 0);
    }
    int32_t sunlight_left_ms = 0;
    if (FLAG(hl_active) == 1 && INT(cfg_hl_max_active_ms) > 0) {
        float remaining =
            (float)INT(cfg_hl_max_active_ms) - (float)(frame->now - TIME(hl_active_start));
        if (remaining <= 0)
            remaining = 0;
        sunlight_left_ms = (int32_t)ios_float_int(remaining, 32);
    }
    state_text(io, stream, "version=%s\n", "0.0.1");
    state_text(io, stream, "mode=%s\n", frame->mode);
    state_word(io, stream, "screen=%d\n", frame->screen);
    state_real(io, stream, "lux=%.3f\n", frame->lux);
    state_real(io, stream, "smooth=%.3f\n", frame->smooth);
    state_word(io, stream, "target_br=%d\n", frame->target);
    state_word(io, stream, "current_br=%d\n", frame->current);
    state_word(io, stream, "max_br=%d\n", INT(g_max));
    state_word(io, stream, "br_delta=%d\n", (uint32_t)frame->target - (uint32_t)frame->current);
    state_word(io, stream, "poll_ms=%d\n", frame->poll_ms);
    state_word(io, stream, "sensor_rate_us=%d\n", INT(g_sensor_rate_us));
    state_text(io, stream, "sensor_rate_mode=%s\n",
               INT(g_sensor_rate_mode) >= 2   ? "reactive"
               : INT(g_sensor_rate_mode) == 1 ? "active"
                                              : "stable");
    state_word(io, stream, "sensor_reactive_left_ms=%llu\n",
               time_left(frame->now, TIME(g_sensor_reactive_until)));
    state_word(io, stream, "sunlight_active=%d\n", FLAG(hl_active));
    state_word(io, stream, "hbm_active=%d\n", FLAG(hl_hbm_active));
    state_word(io, stream, "fast_dark=%d\n", INT(fast_dark));
    state_word(io, stream, "fast_dark_candidate=%d\n", FLAG(fast_dark_candidate));
    state_word(io, stream, "zero_lux_suspect=%d\n", FLAG(g_zero_lux_suspect));
    state_word(io, stream, "low_lux_bright_spike_guard=%d\n", FLAG(g_low_lux_bright_spike_guard));
    state_word(io, stream, "low_lux_bright_spike_count=%d\n", INT(g_low_lux_bright_spike_count));
    state_real(io, stream, "low_lux_bright_spike_raw=%.3f\n", FLOAT(g_low_lux_bright_spike_raw));
    state_word(io, stream, "low_lux_rise_confirmed_samples=%d\n",
               INT(g_low_lux_rise_confirmed_samples));
    state_word(io, stream, "low_lux_rise_confirmed_left_ms=%llu\n",
               time_left(frame->now, TIME(g_low_lux_rise_confirmed_until)));
    state_word(io, stream, "low_lux_rise_ceiling_br=%d\n", INT(g_low_lux_rise_ceiling_br));
    state_word(io, stream, "low_lux_rise_fallback_left_ms=%llu\n",
               time_left(frame->now, TIME(g_low_lux_rise_fallback_hold_until)));
    state_word(io, stream, "dark_settle_left_ms=%llu\n",
               time_left(frame->now, TIME(g_dark_settle_until)));
    state_word(io, stream, "dark_drop_left_ms=%llu\n",
               time_left(frame->now, TIME(g_dark_drop_until)));
    state_real(io, stream, "darken_rate_pct_per_sec=%.3f\n",
               frame->current < 1 ? brighten_rate() : darken_rate(io));
    state_real(io, stream, "brighten_rate_pct_per_sec=%.3f\n", brighten_rate());
    state_real(io, stream, "config_darken_speed=%.2f\n", FLOAT(cfg_darken_speed));
    state_real(io, stream, "config_brighten_speed=%.2f\n", FLOAT(cfg_brighten_speed));
    state_text(io, stream, "darken_context=%s\n",
               FLAG(g_zero_lux_suspect) & 1        ? "zero_lux_suspect"
               : INT(g_actuator_fast_dark)         ? "fast_dark"
               : FLOAT(g_actuator_smooth_lux) < 3  ? "low_lux"
               : FLOAT(g_actuator_smooth_lux) <= 5 ? "very_dark"
                                                   : "normal");
    state_word(io, stream, "wake_settle_until=%llu\n", 0);
    state_word(io, stream, "aod_enabled=%d\n", INT(g_aod_enabled));
    state_word(io, stream, "display_interactive=%d\n", 1);
    state_text(io, stream, "display_state=%s\n", STRING(g_last_display_state));
    state_text(io, stream, "power_wakefulness=%s\n", STRING(g_power_wakefulness));
    state_word(io, stream, "power_interactive=%d\n", INT(g_power_interactive));
    state_word(io, stream, "learn_dirty=%d\n", FLAG(g_learn_dirty));
    state_text(io, stream, "brightness_owner=%s\n", STRING(g_brightness_owner));
    state_word(io, stream, "daemon_write_allowed=%d\n", allowed);
    state_word(io, stream, "write_allowed=%d\n", allowed);
    state_word(io, stream, "daemon_can_write=%d\n", can_write);
    state_word(io, stream, "auto_mode_raw=%d\n", INT(cached_auto));
    state_word(io, stream, "prev_auto_mode=%d\n", INT(prev_auto_mode));
    state_word(io, stream, "wake_guard_left_ms=%llu\n",
               time_left(frame->now, TIME(g_wake_guard_until)));
    state_word(io, stream, "wake_sensor_settle_left_ms=%llu\n",
               time_left(frame->now, TIME(g_wake_sensor_settle_until)));
    state_word(io, stream, "wake_readonly_left_ms=%llu\n",
               time_left(frame->now, TIME(g_wake_readonly_until)));
    state_word(io, stream, "wake_readonly_extends=%d\n", INT(g_wake_readonly_extends));
    state_word(io, stream, "wake_lux_sample_count=%d\n", INT(g_wake_lux_sample_count));
    state_text(io, stream, "wake_source=%s\n", STRING(g_wake_source));
    state_word(io, stream, "wake_hw_br=%d\n", INT(g_wake_hw_br));
    state_word(io, stream, "wake_seed_br=%d\n", 0);
    state_word(io, stream, "wake_target_raw=%d\n", INT(g_wake_target_raw));
    state_word(io, stream, "wake_target_clamped=%d\n", INT(g_wake_target_clamped));
    state_word(io, stream, "last_stable_on_br=%d\n", INT(g_last_stable_on_br));
    state_real(io, stream, "last_stable_on_lux=%.3f\n", FLOAT(g_last_stable_on_lux));
    state_word(io, stream, "last_stable_on_age_ms=%llu\n",
               TIME(g_last_stable_on_ms) ? frame->now - TIME(g_last_stable_on_ms) : 0);
    state_text(io, stream, "settings_read_source=%s\n",
               *STRING(settings_read_source) ? STRING(settings_read_source) : "unknown");
    state_text(io, stream, "settings_read_error=%s\n",
               *STRING(settings_read_error) ? STRING(settings_read_error) : "-");
    state_text(io, stream, "slider_source=%s\n", STRING(cached_slider_source));
    state_real(io, stream, "auto_brightness_adj=%.4f\n", FLOAT(cached_auto_adj));
    state_word(io, stream, "auto_brightness_adj_valid=%d\n", FLAG(cached_auto_adj_valid));
    state_word(io, stream, "config_valid=%d\n", (FLAG(g_config_valid) ^ 1) & 1);
    state_text(io, stream, "config_error=%s\n", STRING(g_config_error));
    state_text(io, stream, "ownership=%s\n",
               INT(cached_auto) >= 1   ? "auto"
               : INT(cached_auto) == 0 ? "manual"
                                       : "unknown");
    state_word(io, stream, "transition_active=%d\n", FLAG(g_transition_active));
    state_word(io, stream, "transition_poll_ms=%d\n", 1000);
    state_word(io, stream, "transition_retarget_count=%d\n", INT(g_transition_retarget_count));
    state_word(io, stream, "transition_frame_ms=%d\n", INT(g_actuator_frame_ms));
    state_word(io, stream, "actuator_tail_active=%d\n", 0);
    state_text(io, stream, "write_governor_reason=%s\n", STRING(g_write_gov_reason));
    state_word(io, stream, "last_write_result=%d\n", INT(g_last_write_result));
    state_word(io, stream, "last_write_readback=%d\n", INT(g_last_write_readback));
    state_word(io, stream, "write_readback_mismatch_streak=%d\n",
               INT(g_write_readback_mismatch_streak));
    state_word(io, stream, "target_hold_active=%d\n", FLAG(g_target_hold_active));
    state_word(io, stream, "target_debounce_br=%d\n", INT(g_target_debounce_br));
    state_word(io, stream, "target_candidate_br=%d\n", INT(g_target_candidate_br));
    state_word(io, stream, "target_candidate_dir=%d\n", INT(g_target_candidate_dir));
    state_word(io, stream, "actuator_only_frame_count=%d\n", INT(g_actuator_only_count));
    state_word(io, stream, "target_poll_ms=%d\n", frame->poll_ms);
    state_text(io, stream, "why=%s\n", frame->reason ? frame->reason : "pending");
    state_word(io, stream, "temp_mdeg=%d\n", INT(g_thermal_temp));
    state_word(io, stream, "thermal_source_class=%d\n", INT(g_tz_last_class));
    state_word(io, stream, "thermal_trusted_temp=%d\n", INT(g_thermal_trusted_temp_mdeg));
    state_word(io, stream, "thermal_suspect_temp=%d\n", INT(g_thermal_suspect_temp_mdeg));
    state_text(io, stream, "thermal_deny_source=%s\n", STRING(g_thermal_deny_source));
    state_word(io, stream, "thermal_emergency_bypass=%d\n", INT(g_thermal_emergency_bypass));
    state_word(io, stream, "thermal_exit_settle_left_ms=%llu\n",
               time_left(frame->now, TIME(g_thermal_exit_settle_until)));
    state_word(io, stream, "thermal_floor_latched_br=%d\n", INT(g_thermal_floor_latched_br));
    state_word(io, stream, "thermal_source_filtered=%d\n", FLAG(g_thermal_source_filtered));
    state_word(io, stream, "thermal_guard_confirm_cnt=%d\n", INT(g_thermal_guard_confirm_cnt));
    state_text(io, stream, "thermal_guard_source=%s\n", STRING(g_thermal_guard_source));
    state_word(io, stream, "thermal_hard_trigger=%d\n", INT(cfg_thermal_hard_trigger));
    state_word(io, stream, "thermal_hard_resume=%d\n", INT(cfg_thermal_hard_resume));
    state_real(io, stream, "thermal_hard_floor_pct=%.3f\n", FLOAT(cfg_thermal_hard_floor_pct));
    state_word(io, stream, "heat_guard_active=%d\n", FLAG(g_heat_guard_active));
    state_word(io, stream, "thermal_cap_br=%d\n", INT(g_thermal_cap_br));
    state_word(io, stream, "thermal_temp=%d\n", INT(g_thermal_temp));
    state_word(io, stream, "hbm_enabled=%d\n", INT(cfg_hl_hbm_enable));
    state_word(io, stream, "hbm_found=%d\n", INT(hl_hbm_found));
    state_word(io, stream, "hbm_active=%d\n", FLAG(hl_hbm_active));
    state_text(io, stream, "hbm_path=%s\n", *STRING(hl_hbm_path) ? STRING(hl_hbm_path) : "-");
    state_text(io, stream, "hbm_discovery=%s\n", STRING(hl_hbm_discovery));
    state_real(io, stream, "sunlight_extreme=%.3f\n", FLOAT(cfg_hl_extreme_lux));
    state_word(io, stream, "sunlight_time_limit_ms=%d\n", INT(cfg_hl_max_active_ms));
    state_word(io, stream, "sunlight_active_left_ms=%d\n", sunlight_left_ms);
    state_word(io, stream, "sunlight_cooldown_left_ms=%d\n",
               time_left(frame->now, TIME(hl_cooldown_until)));
    state_text(io, stream, "sunlight_block_reason=%s\n", STRING(hl_last_block_reason));
    state_text(io, stream, "sunlight_block_text=%s\n",
               sunlight_reason_text(STRING(hl_last_block_reason)));
    state_text(io, stream, "reason_primary=%s\n", STRING(g_reason_primary));
    state_text(io, stream, "reason_chain=%s\n", STRING(g_reason_chain));
    CALL(io, fwrite, ARG("curve_stage=base\n"), 17, 1, stream);
    state_word(io, stream, "learn_dirty=%d\n", FLAG(g_learn_dirty));
    state_word(io, stream, "battery_pct=%d\n", battery);
    state_word(io, stream, "charging=%d\n", charging);
    state_word(io, stream, "lux_valid=%d\n", FLAG(g_lux_valid));
    state_text(io, stream, "lux_source=%s\n", STRING(g_lux_source));
    state_word(io, stream, "lux_age_ms=%llu\n",
               TIME(g_last_lux_event_ms) ? frame->now - TIME(g_last_lux_event_ms) : 0);
    state_real(io, stream, "front_lux=%.3f\n", FLOAT(g_front_lux));
    state_word(io, stream, "front_lux_age_ms=%llu\n",
               sensor_age(frame->now, TIME(g_front_lux_event_ms)));
    state_real(io, stream, "back_lux=%.3f\n", FLOAT(g_back_lux));
    state_word(io, stream, "back_lux_age_ms=%llu\n",
               sensor_age(frame->now, TIME(g_back_lux_event_ms)));
    state_word(io, stream, "back_fallback_confirm_count=%d\n", INT(g_back_fallback_confirm_count));
    state_word(io, stream, "sensor_stale=%d\n", FLAG(g_sensor_stale));
    state_word(io, stream, "sensor_hold_active=%d\n", FLAG(g_sensor_hold_active));
    state_word(io, stream, "sensor_hold_since_ms=%llu\n", TIME(g_sensor_hold_since_ms));
    state_word(io, stream, "sensor_hold_left_ms=%llu\n",
               FLAG(g_sensor_hold_active) & 1 && TIME(g_sensor_hold_since_ms)
                   ? time_left(frame->now, TIME(g_sensor_hold_since_ms) + 120000)
                   : 0);
    state_word(io, stream, "sensor_hold_timeout=%d\n", FLAG(g_sensor_hold_timeout));
    state_text(io, stream, "sensor_stale_source=%s\n",
               *STRING(g_sensor_stale_source) ? STRING(g_sensor_stale_source) : "-");
    state_text(io, stream, "learn_block_reason=%s\n",
               *STRING(g_learn_block_reason) ? STRING(g_learn_block_reason) : "-");
    state_word(io, stream, "external_write_burst_count=%d\n", INT(g_external_write_burst_count));
    state_word(io, stream, "external_write_hold_left_ms=%llu\n",
               time_left(frame->now, TIME(g_external_write_hold_until)));
    state_word(io, stream, "updated_unix=%llu\n", CALL(io, time, 0));
    CALL(io, fclose, stream);
    return (int)CALL(io, rename, ARG("/data/local/tmp/luma_curve_state.tmp"),
                     ARG("/data/local/tmp/luma_curve_state"));
}
#ifndef IOS_PRODUCTION
void ios_write_state_file(IosCpu *caller) {
    StateFrame frame = {.mode = (const char *)(uintptr_t)caller->x[0],
                        .screen = (int32_t)caller->x[1],
                        .target = (int32_t)caller->x[2],
                        .current = (int32_t)caller->x[3],
                        .poll_ms = (int32_t)caller->x[4],
                        .reason = (const char *)(uintptr_t)caller->x[5],
                        .now = caller->x[6],
                        .lux = caller->v[0].f32[0],
                        .smooth = caller->v[1].f32[0]};
    DomainIo io = {0};
    caller->x[0] = (uint32_t)publish_state(&io, &frame);
}
#endif

int ios_write_state_frame(const char *mode, int screen, int target, int current, int poll_ms,
                          const char *reason, uint64_t now, float lux, float smooth) {
    StateFrame frame = {.mode = mode,
                        .screen = screen,
                        .target = target,
                        .current = current,
                        .poll_ms = poll_ms,
                        .reason = reason,
                        .now = now,
                        .lux = lux,
                        .smooth = smooth};
    DomainIo io = {0};
    return publish_state(&io, &frame);
}
