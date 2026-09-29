/* Configuration reload with validation and rollback of the previous values. */
#include "business_api.h"
enum ConfigType { CONFIG_INT, CONFIG_FLOAT };
typedef struct ConfigField {
    const char *key;
    uint8_t *address;
    enum ConfigType type;
} ConfigField;
#define INTEGER(key, name)                                                                         \
    { key, ADDRESS(name), CONFIG_INT }
#define REAL(key, name)                                                                            \
    { key, ADDRESS(name), CONFIG_FLOAT }
static const ConfigField fields[] = {
    INTEGER("config_version", cfg_config_version), REAL("alpha_fast", cfg_alpha_fast),
    REAL("alpha_mid", cfg_alpha_mid), REAL("alpha_slow", cfg_alpha_slow),
    REAL("fast_threshold", cfg_fast_thresh), REAL("slow_threshold", cfg_slow_thresh),
    REAL("spike_up", cfg_spike_up), REAL("spike_down", cfg_spike_down),
    REAL("hyst_low", cfg_hyst_low), REAL("hyst_high", cfg_hyst_high),
    REAL("manual_gamma", cfg_gamma), REAL("brighten_speed", cfg_brighten_speed),
    REAL("darken_speed", cfg_darken_speed), INTEGER("min_step", cfg_min_step),
    INTEGER("thermal_cap_enable", cfg_thermal_cap_enable),
    INTEGER("thermal_hard_trigger", cfg_thermal_hard_trigger),
    INTEGER("thermal_hard_resume", cfg_thermal_hard_resume),
    REAL("thermal_hard_floor_pct", cfg_thermal_hard_floor_pct),
    INTEGER("charging_heat_guard", cfg_charging_heat_guard),
    REAL("high_lux_threshold", cfg_hl_threshold), REAL("high_lux_extreme_lux", cfg_hl_extreme_lux),
    REAL("high_lux_boost_max", cfg_hl_boost_max), REAL("high_lux_min_pct", cfg_hl_min_pct),
    INTEGER("high_lux_max_active_ms", cfg_hl_max_active_ms),
    INTEGER("high_lux_cooldown_ms", cfg_hl_cooldown_ms),
    INTEGER("high_lux_temp_thresh", cfg_hl_temp_thresh),
    INTEGER("high_lux_temp_resume", cfg_hl_temp_resume),
    INTEGER("high_lux_bat_thresh", cfg_hl_bat_thresh),
    INTEGER("high_lux_hbm_enable", cfg_hl_hbm_enable),
    /* The original parser does not accept learning keys, but rollback saves these fields. */
    INTEGER(NULL, cfg_learn_enabled), REAL(NULL, cfg_learn_strength),
    REAL(NULL, cfg_learn_max_offset), INTEGER(NULL, cfg_learn_sample_delay_ms),
    REAL(NULL, cfg_learn_decay_days), REAL(NULL, cfg_learn_curve_sigma),
    REAL(NULL, cfg_learn_time_neighbor), INTEGER(NULL, cfg_learn_min_delta)};
enum { CONFIG_FIELD_COUNT = sizeof(fields) / sizeof(fields[0]) };
typedef struct ConfigBackup {
    uint32_t numeric[CONFIG_FIELD_COUNT];
    char hbm_path[256], hbm_on[32], hbm_off[32];
    bool hbm_was_active;
} ConfigBackup;
static void snapshot_config(DomainIo *io, ConfigBackup *backup) {
    for (size_t i = 0; i < CONFIG_FIELD_COUNT; i++)
        backup->numeric[i] = (uint32_t)ios_load((uintptr_t)fields[i].address, 4);
    domain_string(io, backup->hbm_path, sizeof(backup->hbm_path), STRING(cfg_hbm_node_path));
    domain_string(io, backup->hbm_on, sizeof(backup->hbm_on), STRING(cfg_hbm_on_value));
    domain_string(io, backup->hbm_off, sizeof(backup->hbm_off), STRING(cfg_hbm_off_value));
    backup->hbm_was_active = FLAG(hl_hbm_active) & 1;
}
static void restore_config(DomainIo *io, const ConfigBackup *backup) {
    for (size_t i = 0; i < CONFIG_FIELD_COUNT; i++)
        ios_store((uintptr_t)fields[i].address, backup->numeric[i], 4);
    domain_string(io, STRING(cfg_hbm_node_path), 256, backup->hbm_path);
    domain_string(io, STRING(cfg_hbm_on_value), 32, backup->hbm_on);
    domain_string(io, STRING(cfg_hbm_off_value), 32, backup->hbm_off);
}
static bool write_hbm(DomainIo *io, bool on, bool warn) {
    const char *path = STRING(hl_hbm_path);
    if (!*path)
        return false;
    uintptr_t stream = domain_open(io, path, "w");
    bool success = false;
    if (stream) {
        int written =
            PRINT(io, stream, "%s", ARG(on ? STRING(cfg_hbm_on_value) : STRING(cfg_hbm_off_value)));
        int closed = (int)CALL(io, fclose, stream);
        success = written >= 0 && closed == 0;
        if (success)
            SET_FLAG(hl_hbm_active, on);
    }
    if (!success && warn)
        PRINT(io, domain_stdout(), "[%s] WARN: HBM write failed path=%s on=%d\n", ARG("LumaCurve"),
              ARG(path), on);
    return success;
}
/* Keep the original ordered comparisons: NaN policy is a separate behavior change. */
static bool outside(float value, float low, float high) {
    return value < low || value > high;
}
static const char *configuration_error(void) {
    if (outside(FLOAT(cfg_alpha_fast), .01f, .95f) || outside(FLOAT(cfg_alpha_mid), .01f, .95f) ||
        outside(FLOAT(cfg_alpha_slow), .01f, .95f))
        return "alpha_out_of_range";
    if (outside(FLOAT(cfg_fast_thresh), .1f, 20) || outside(FLOAT(cfg_slow_thresh), .001f, 1))
        return "threshold_out_of_range";
    if (outside(FLOAT(cfg_spike_up), 1, 50) || outside(FLOAT(cfg_spike_down), .001f, 1))
        return "spike_out_of_range";
    if (outside(FLOAT(cfg_hyst_low), .001f, .5f) ||
        outside(FLOAT(cfg_hyst_high), FLOAT(cfg_hyst_low), .5f))
        return "hysteresis_invalid";
    if (outside(FLOAT(cfg_gamma), 1, 3.5f))
        return "gamma_out_of_range";
    if (outside(FLOAT(cfg_brighten_speed), .5f, 2.5f) ||
        outside(FLOAT(cfg_darken_speed), .5f, 2.5f))
        return "speed_out_of_range";
    if (INT(cfg_min_step) < 1 || INT(cfg_min_step) > 2048)
        return "min_step_out_of_range";
    if (outside(FLOAT(cfg_learn_strength), .1f, 5) ||
        outside(FLOAT(cfg_learn_max_offset), .01f, .95f) ||
        outside(FLOAT(cfg_learn_decay_days), 1, 365) ||
        outside(FLOAT(cfg_learn_curve_sigma), .05f, 5) ||
        outside(FLOAT(cfg_learn_time_neighbor), .05f, 1) || INT(cfg_learn_sample_delay_ms) < 1000 ||
        INT(cfg_learn_sample_delay_ms) > 60000 || INT(cfg_learn_min_delta) < 1 ||
        INT(cfg_learn_min_delta) > 64)
        return "learning_invalid";
    if (INT(cfg_thermal_hard_trigger) < 45000 || INT(cfg_thermal_hard_trigger) > 90000 ||
        INT(cfg_thermal_hard_resume) < 35000 ||
        (uint32_t)INT(cfg_thermal_hard_resume) >= (uint32_t)INT(cfg_thermal_hard_trigger) ||
        outside(FLOAT(cfg_thermal_hard_floor_pct), .05f, .8f))
        return "thermal_invalid";
    int active_ms = INT(cfg_hl_max_active_ms);
    if (outside(FLOAT(cfg_hl_threshold), 500, 100000) ||
        outside(FLOAT(cfg_hl_extreme_lux), FLOAT(cfg_hl_threshold) + 100, 200000) ||
        outside(FLOAT(cfg_hl_boost_max), 0, .6f) || outside(FLOAT(cfg_hl_min_pct), .2f, 1) ||
        (active_ms != 0 && (active_ms < 3000 || active_ms > 600000)) ||
        INT(cfg_hl_cooldown_ms) < 0 || INT(cfg_hl_cooldown_ms) > 600000 ||
        INT(cfg_hl_temp_thresh) < 40000 || INT(cfg_hl_temp_thresh) > 80000 ||
        INT(cfg_hl_temp_resume) < 35000 ||
        (uint32_t)INT(cfg_hl_temp_resume) >= (uint32_t)INT(cfg_hl_temp_thresh) ||
        INT(cfg_hl_bat_thresh) < 5 || INT(cfg_hl_bat_thresh) > 50)
        return "sunlight_invalid";
    return NULL;
}
static void parse_config_line(DomainIo *io, char *line) {
    if (line[0] == '#')
        return;
    char *separator = strchr(line, '=');
    if (!separator)
        return;
    *separator = 0;
    char *key = line, *value = separator + 1;
    char *newline = strchr(value, '\n');
    if (newline)
        *newline = 0;
    while (*key == ' ' || *key == '\t')
        key++;
    while (*value == ' ' || *value == '\t')
        value++;
    CALL(io, atof, ARG(value));
    float real = (float)domain_real_result(io);
    uint32_t integer = (uint32_t)CALL(io, atoi, ARG(value));
    for (size_t i = 0; i < CONFIG_FIELD_COUNT; i++) {
        if (!fields[i].key || strcmp(key, fields[i].key))
            continue;
        if (fields[i].type == CONFIG_FLOAT)
            state_set_float(fields[i].address, real);
        else
            ios_store((uintptr_t)fields[i].address, integer, 4);
        return;
    }
    if (!strcmp(key, "hbm_node_path"))
        CALL(io, strncpy, ARG(STRING(cfg_hbm_node_path)), ARG(value), 255);
    else if (!strcmp(key, "hbm_on_value"))
        CALL(io, strncpy, ARG(STRING(cfg_hbm_on_value)), ARG(value), 31);
    else if (!strcmp(key, "hbm_off_value"))
        CALL(io, strncpy, ARG(STRING(cfg_hbm_off_value)), ARG(value), 31);
}
int ios_configuration_reload(DomainIo *io) {
    ConfigBackup backup = {0};
    snapshot_config(io, &backup);
    if (backup.hbm_was_active && !write_hbm(io, false, false)) {
        SET_FLAG(g_config_valid, 1);
        memcpy(STRING(g_config_error), "hbm_off_failed", 15);
        PRINT(io, domain_stdout(), "[%s] Config reload rejected: cannot disable active HBM\n",
              ARG("LumaCurve"));
        return 0;
    }
    uintptr_t stream = domain_open(io, "/data/local/tmp/luma_curve.conf", "r");
    if (!stream) {
        if (backup.hbm_was_active)
            write_hbm(io, true, true);
        memcpy(STRING(g_config_error), "config_missing", 15);
        SET_FLAG(g_config_valid, 1);
        return 0;
    }
    char line[256];
    while (CALL(io, fgets, ARG(line), sizeof(line), stream))
        parse_config_line(io, line);
    CALL(io, fclose, stream);
    SET_INT(cfg_learn_enabled, 0); /* Forced off in the original 2.5.5 reload path. */
    const char *error = configuration_error();
    if (error) {
        restore_config(io, &backup);
        if (backup.hbm_was_active)
            write_hbm(io, true, true);
        SET_FLAG(g_config_valid, 1);
        domain_string(io, STRING(g_config_error), 96, error);
        PRINT(io, domain_stdout(), "[%s] Config rejected: %s\n", ARG("LumaCurve"), ARG(error));
        return 0;
    }
    ios_hbm_discover(io);
    if (backup.hbm_was_active && INT(cfg_hl_hbm_enable))
        write_hbm(io, true, true);
    SET_FLAG(g_config_valid, 0);
    memcpy(STRING(g_config_error), "-", 2);
    return 1;
}
#ifndef IOS_PRODUCTION
void ios_load_config(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = (uint32_t)ios_configuration_reload(&io);
}
#endif
