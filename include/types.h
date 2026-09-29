/* Typed business state for recovered daemon (phase 3).
 *
 * Production core should use these fields. The fixed-offset layout in
 * state_layout.h remains only for compatibility/tests until all call sites
 * migrate. Do not serialize these structs raw to disk — use explicit
 * config/learn parsers.
 */
#pragma once
#include <stdint.h>
#include <stdbool.h>

typedef struct Config {
    int32_t config_version;
    float alpha_fast, alpha_mid, alpha_slow;
    float fast_threshold, slow_threshold;
    float spike_up, spike_down;
    float hyst_low, hyst_high;
    float manual_gamma;
    float brighten_speed, darken_speed;
    int32_t min_step;
    int32_t thermal_cap_enable;
    int32_t thermal_hard_trigger;   /* mdeg C */
    int32_t thermal_hard_resume;
    float thermal_hard_floor_pct;
    int32_t charging_heat_guard;
    float high_lux_threshold;       /* lux */
    float high_lux_extreme_lux;
    float high_lux_boost_max;
    float high_lux_min_pct;
    int32_t high_lux_max_active_ms;
    int32_t high_lux_cooldown_ms;
    int32_t high_lux_temp_thresh;   /* mdeg C */
    int32_t high_lux_temp_resume;
    int32_t high_lux_bat_thresh;
    int32_t high_lux_hbm_enable;
    char hbm_node_path[256];
    char hbm_on_value[32];
    char hbm_off_value[32];
    /* learning knobs (parsed from same conf) */
    int32_t learn_enabled;
    float learn_strength;
    float learn_max_offset;
    float learn_decay_days;
    float learn_curve_sigma;
    int32_t learn_time_neighbor;
    int32_t learn_sample_delay_ms;
    float learn_min_delta;
    int32_t log_max_bytes;
    int32_t log_max_backups;
    int32_t log_retention_days;
} Config;

/* Learning uses the real persistent cell from csrc/learning.h (v4). */
#include "../csrc/learning.h"

typedef struct SensorState {
    void *libandroid;
    void *manager;
    void *event_queue;
    void *front_sensor;
    void *back_sensor;
    void *prox_sensor;
    void *fn_getMgrPkg, *fn_getMgr, *fn_getDefSensor, *fn_createEQ, *fn_destroyEQ;
    void *fn_enable, *fn_disable, *fn_setRate, *fn_getEvents, *fn_minDelay;
    void *fn_looperPrepare, *fn_looperPoll, *fn_getSensorList, *fn_getType, *fn_getName;
    uint8_t ndk_ok;
    uint8_t light_sensors_enabled;
    uint8_t back_ok;
    uint8_t prox_ok;
    int32_t sensor_rate_us;
    int32_t sensor_rate_mode;
    char sensor_path[256];
    float front_lux;
    float back_lux;
    uint64_t front_lux_event_ms;
    uint64_t back_lux_event_ms;
    uint64_t last_lux_event_ms;
    uint8_t lux_valid;
    uint8_t sensor_stale;
    uint8_t sensor_hold_active;
    uint64_t sensor_hold_since_ms;
    uint8_t sensor_hold_timeout;
} SensorState;

typedef struct BrightnessTransition {
    int32_t from_br, to_br;
    uint64_t start_ms;
    int32_t duration_ms;
    uint8_t active;
    int32_t retarget_count;
} BrightnessTransition;

typedef struct ThermalState {
    int32_t temp_mdeg;
    int32_t trusted_temp_mdeg;
    int32_t suspect_temp_mdeg;
    uint8_t entry;
    uint8_t guard_active;
    int32_t guard_confirm_cnt;
    int32_t cap_br;
    uint8_t cap_enable;
    int32_t hard_trigger, hard_resume;
    float hard_floor_pct;
    int32_t charging_heat_guard;
    int32_t emergency_bypass;
    int32_t floor_latched_br;
    char last_source[32];
} ThermalState;

typedef struct HighLuxState {
    uint8_t active;
    uint8_t exiting;
    uint8_t thermal_blocked;
    int32_t confirm_cnt;
    int32_t trigger_cnt;
    int32_t boost_br;
    uint64_t active_start;
    uint64_t cooldown_until;
    uint8_t hbm_active;
    uint8_t hbm_found;
    uint8_t hbm_discovery_attempted;
    char hbm_path[256];
    char last_block_reason[32];
} HighLuxState;

typedef struct DaemonState {
    int32_t run;
    int32_t max_brightness;
    int32_t cached_auto;
    int32_t cached_slider;
    int32_t power_interactive;
    char power_wakefulness[16];
    char last_display_state[24];
    char brightness_owner[32];
    int32_t current_poll_ms;
    int32_t bright_cnt, dark_cnt;
    int32_t learn_commit_cnt;
    Config config;
    SensorState sensor;
    BrightnessTransition transition;
    ThermalState thermal;
    HighLuxState highlux;
} DaemonState;

/* Load/save without struct-punning fixed offsets. */
int daemon_load_config(DaemonState *st, const char *path);
int daemon_save_learn(const DaemonState *st, const char *path);
int daemon_load_learn(DaemonState *st, const char *path);
