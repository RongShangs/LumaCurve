/* Publish the module's state-file protocol through an atomic rename. */
#include "actuator.h"
#include "business_api.h"
#include <stdarg.h>
#include "state_observer.h"
#include "scene_adaptation.h"
#include "brightness_preference.h"
#include "brightness_curve.h"
#include "temporal_stability.h"
#include "actuator_diagnostics.h"
#include "core_build.h"
#include "framework_backend.h"
#include "backlight_paths.h"

typedef struct StateOutput {
    char data[16384];
    size_t length;
    bool failed;
} StateOutput;
static uint64_t last_publish_ms;
static void state_append(uintptr_t output, const char *format, ...) {
    StateOutput *buffer = (StateOutput *)output;
    if (buffer->failed) return;
    va_list args;
    va_start(args, format);
    size_t remaining = sizeof(buffer->data) - buffer->length;
    int count = vsnprintf(buffer->data + buffer->length, remaining, format, args);
    va_end(args);
    if (count < 0 || (size_t)count >= remaining) buffer->failed = true;
    else buffer->length += (size_t)count;
}
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
    (void)io;
    if (strstr(format, "%llu")) state_append(stream, format, (unsigned long long)value);
    else state_append(stream, format, (int32_t)value);
}
static void state_real(DomainIo *io, uintptr_t stream, const char *format, double value) {
    (void)io;
    state_append(stream, format, value);
}
static void state_text(DomainIo *io, uintptr_t stream, const char *format, const char *value) {
    (void)io;
    state_append(stream, format, value);
}
static int publish_state(DomainIo *io, const StateFrame *frame) {
    StateOutput output = {0};
    uintptr_t stream = (uintptr_t)&output;
    int battery = INT(g_cached_battery_cap);
    int charging = INT(g_cached_charging);
    bool allowed = frame->screen != 0 && !strcmp(frame->mode, "auto") &&
                   !strcmp(STRING(g_brightness_owner), "daemon");
    bool can_write = allowed && TIME(g_wake_readonly_until) <= frame->now;
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
    allowed = allowed && ios_native_backlight_ready();
    can_write = can_write && allowed;
#endif
    if (can_write) {
        if (FLAG(g_external_write_hold) == 1 && frame->now < TIME(g_external_write_hold_until))
            can_write = false;
    }
    int32_t sunlight_left_ms = 0;
    if (FLAG(hl_active) == 1 && INT(cfg_hl_max_active_ms) > 0) {
        float remaining =
            (float)INT(cfg_hl_max_active_ms) - (float)(frame->now - TIME(hl_active_start));
        if (remaining <= 0)
            remaining = 0;
        sunlight_left_ms = (int32_t)ios_float_int(remaining, 32);
    }
    state_text(io, stream, "version=%s\n", "1.0.0");
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
    state_append(stream, "curve_stage=base\n");
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
    if (luma_indoor_stability || luma_curve_custom || luma_preference_learning) {
        state_text(io, stream, "core_build=%s\n", LUMA_CORE_BUILD);
        state_text(io, stream, "backlight_path=%s\n", ios_backlight_brightness());
        state_word(io, stream, "actuator_write_attempts=%llu\n", luma_write_attempts);
        state_word(io, stream, "actuator_write_successes=%llu\n", luma_write_successes);
        state_word(io, stream, "actuator_write_errno=%d\n", luma_write_errno);
        state_word(io, stream, "actuator_last_request=%d\n", luma_write_request);
        state_word(io, stream, "actuator_last_bytes=%d\n", (uint32_t)luma_write_bytes);
        state_text(io, stream, "actuator_write_stage=%s\n", luma_write_stage);
#ifdef LUMA_FRAMEWORK_BACKEND
        const LumaFrameworkSnapshot *fw=&luma_framework_snapshot;
        state_text(io, stream, "output_backend=%s\n", getenv("LUMA_FRAMEWORK_PRODUCTION") ?
            "hyperos4_framework" : "framework_temporary_experimental");
        state_text(io, stream, "curve_coordinate=%s\n", getenv("LUMA_FRAMEWORK_PRODUCTION") ?
            "legacy_raw_backlight_code_calibrated" : "legacy_raw_fraction_nominal");
        state_real(io, stream, "framework_algorithm_goal=%.7f\n", fw->goal);
        state_real(io, stream, "framework_limited_goal=%.7f\n", fw->limited);
        state_real(io, stream, "framework_request=%.7f\n", fw->request);
        if (getenv("LUMA_FRAMEWORK_PRODUCTION") && fw->active && isfinite(fw->limited) && fw->limited >= 0)
            state_word(io, stream, "framework_effective_target_br=%d\n", (int)lroundf(fw->limited * 17848.0f));
        if (getenv("LUMA_FRAMEWORK_PRODUCTION"))
            state_real(io, stream, "framework_panel_codes_per_float=%.1f\n", 17848.0f);
        state_real(io, stream, "framework_base=%.7f\n", fw->base);
        state_real(io, stream, "framework_adjusted=%.7f\n", fw->adjusted);
        state_word(io, stream, "framework_actual_node=%d\n", fw->node);
        state_word(io, stream, "framework_owned=%d\n", fw->active);
        state_word(io, stream, "framework_feedback_age_ms=%llu\n", frame->now>=fw->sampled_ms?frame->now-fw->sampled_ms:0);
#endif
        char curve[256];
        if (luma_curve_format(curve, sizeof(curve))) state_text(io, stream, "curve_points=%s\n", curve);
        if (luma_curve_learned_format(curve, sizeof(curve))) state_text(io, stream, "curve_learned_points=%s\n", curve);
        state_word(io, stream, "thermal_enabled=%d\n", INT(cfg_thermal_cap_enable));
        state_word(io, stream, "thermal_zone_count=%d\n", INT(g_tz_count));
        state_word(io, stream, "thermal_scan_done=%d\n", FLAG(g_tz_cached));
        state_word(io, stream, "curve_custom=%d\n", luma_curve_custom);
        state_real(io, stream, "curve_gamma=%.3f\n", FLOAT(cfg_gamma));
        state_real(io, stream, "curve_circadian_factor=%.3f\n", luma_curve_circadian());
        state_real(io, stream, "curve_sunlight_threshold=%.3f\n", FLOAT(cfg_hl_threshold));
        state_real(io, stream, "curve_sunlight_extreme=%.3f\n", FLOAT(cfg_hl_extreme_lux));
        state_real(io, stream, "curve_sunlight_boost=%.3f\n", FLOAT(cfg_hl_boost_max));
        state_real(io, stream, "curve_sunlight_min=%.3f\n", FLOAT(cfg_hl_min_pct));
    }
    if (luma_preference_learning || luma_preference_offset() != 0 || luma_preference_samples()) {
        state_real(io, stream, "preference_offset=%.3f\n", luma_preference_offset());
        state_real(io, stream, "preference_effective_offset=%.3f\n", luma_preference_effective());
        state_word(io, stream, "preference_learning=%d\n", luma_preference_learning);
        state_word(io, stream, "preference_samples=%d\n", luma_preference_samples());
        state_word(io, stream, "preference_anchor=%d\n", luma_preference_anchor());
        state_word(io, stream, "preference_pending=%d\n", luma_preference_pending());
        state_word(io, stream, "preference_dirty=%d\n", luma_preference_dirty());
        state_text(io, stream, "preference_evidence=%s\n", luma_preference_evidence());
    }
    if (luma_indoor_stability) {
        state_text(io, stream, "scene_relation=%s\n", ios_scene_class());
        state_word(io, stream, "scene_hold_active=%d\n", ios_scene_holding());
        state_word(io, stream, "scene_hold_left_ms=%llu\n", ios_scene_hold_left(frame->now));
        state_word(io, stream, "scene_confirm_samples=%d\n", ios_scene_samples());
        state_word(io, stream, "front_reporting_mode=%d\n", ios_scene_reporting_mode(true));
        state_word(io, stream, "back_reporting_mode=%d\n", ios_scene_reporting_mode(false));
    }
    if (output.failed) return -1;
    uintptr_t file = domain_open(io, "/data/local/tmp/luma_curve_state.tmp", "w");
    if (!file) return -1;
    size_t written = (size_t)CALL(io, fwrite, ARG(output.data), 1, output.length, file);
    int closed = (int)CALL(io, fclose, file);
    if (written != output.length || closed != 0) {
        CALL(io, unlink, ARG("/data/local/tmp/luma_curve_state.tmp"));
        return -1;
    }
    int result = (int)CALL(io, rename, ARG("/data/local/tmp/luma_curve_state.tmp"),
                          ARG("/data/local/tmp/luma_curve_state"));
    if (result == 0) last_publish_ms = frame->now;
    else CALL(io, unlink, ARG("/data/local/tmp/luma_curve_state.tmp"));
    return result;
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
    /* Keep legacy control publication's battery refresh; observer frames call
     * the pure serializer directly and never refresh any business state. */
    ios_battery_refresh(&io, domain_now_ms(&io));
    return publish_state(&io, &frame);
}

bool ios_state_observer_active(DomainIo *io, uint64_t now) {
    static uint64_t checked_ms;
    static bool active;
    if (checked_ms && now >= checked_ms && now - checked_ms < 1000) return active;
    checked_ms = now;
    uint8_t metadata[128] = {0};
    active = false;
    if ((int)CALL(io, stat, ARG("/data/local/tmp/luma_curve_ui_watch"), ARG(metadata)) == 0) {
        int64_t modified = (int64_t)ios_load((uintptr_t)(metadata + 88), 8);
        int64_t wall = (int64_t)CALL(io, time, 0);
        active = modified > 0 && wall >= modified && wall - modified < 4;
    }
    return active;
}
void ios_state_observer_tick(DomainIo *io, uint64_t now, int screen, int current,
                             int target, int poll_ms, float lux, float smooth) {
    if (!screen || !ios_state_observer_active(io, now) ||
        (now >= last_publish_ms && now - last_publish_ms < 1000)) return;
    const char *mode = INT(cached_auto) == 1 ? "auto" : "manual";
    char reason[256];
    if (!strcmp(mode, "manual")) {
        target = current;
        snprintf(reason, sizeof(reason), "manual passthrough: system brightness owns the slider");
    } else if (!strcmp(STRING(g_brightness_owner), "wake_readonly")) {
        snprintf(reason, sizeof(reason), "wake readonly: observing system brightness");
    } else if (INT(g_proximity_near)) {
        snprintf(reason, sizeof(reason), "proximity blocked: adjustment paused");
    } else {
        snprintf(reason, sizeof(reason), "%s tgt=%d cur=%d lux=%.1f sm=%.1f",
                 STRING(g_reason_chain), target, current, (double)lux, (double)smooth);
    }
    StateFrame frame = {.mode=mode, .reason=reason, .screen=screen, .target=target,
        .current=current, .poll_ms=poll_ms, .lux=lux, .smooth=smooth, .now=now};
    (void)publish_state(io, &frame);
}
void ios_state_observer_wait(DomainIo *io, unsigned delay_ms, int screen, int current,
                             int target, int poll_ms, float lux, float smooth) {
    /* Split only an observed, screen-on sleep. The control loop still waits the
     * full delay; no sensor refresh or actuator operation occurs in this loop. */
    for (;;) {
        uint64_t now = domain_now_ms(io);
        ios_state_observer_tick(io, now, screen, current, target, poll_ms, lux, smooth);
        unsigned part = screen && ios_state_observer_active(io, now) && delay_ms > 1000
                            ? 1000 : delay_ms;
        CALL(io, usleep, part * 1000u);
        if (delay_ms <= part || !INT(g_run)) return;
        delay_ms -= part;
    }
}
