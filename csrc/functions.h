/* Complete C recovery declarations. Generated once; edit source directly. */
#pragma once
#include "runtime.h"
#include <stdbool.h>
#ifndef IOS_PRODUCTION
void ios_sig_h(IosCpu *r);
void ios_sig_learn_reset(IosCpu *r);
void ios_sig_reload(IosCpu *r);
void ios_sig_reload_learn(IosCpu *r);
void ios_load_config(IosCpu *r);
void ios_load_learn(IosCpu *r);
void ios_save_learn(IosCpu *r);
void ios_hbm_find_node(IosCpu *r);
void ios_refresh_settings(IosCpu *r);
void ios_read_screen(IosCpu *r);
void ios_refresh_power_wakefulness(IosCpu *r);
void ios_refresh_display_state_on_edge(IosCpu *r);
void ios_read_lux_fallback(IosCpu *r);
void ios_poll_sensors(IosCpu *r);
void ios_highlux_deactivate(IosCpu *r);
void ios_set_light_sensors_enabled(IosCpu *r);
void ios_apply_brightness_frame(IosCpu *r);
void ios_write_state_file(IosCpu *r);
void ios_curve_pct_for_lux(IosCpu *r);
void ios_refresh_battery_state(IosCpu *r);
void ios_trans_update(IosCpu *r);
void ios_hbm_scan_bounded(IosCpu *r);
void ios_main(IosCpu *r);
void ios_main_exit(IosCpu *r);
#endif
void ios_request_stop(int signal_number);
void ios_request_learn_reset(int signal_number);
void ios_request_config_reload(int signal_number);
void ios_request_learn_reload(int signal_number);

#ifndef IOS_BUSINESS_MAIN
int ios_structured_main(void);
#endif
int ios_business_main(void);
typedef struct DomainIo DomainIo;
#ifndef IOS_PRODUCTION
/* Historical region helper interfaces; not production business operations. */
enum OwnershipDecision {
    OWN_KEEP = 0,
    OWN_ENTER_WAKE_READONLY,
    OWN_EXTEND_WAKE_READONLY,
    OWN_EXIT_WAKE_READONLY_TO_DAEMON,
    OWN_RELEASE_TO_MANUAL,
    OWN_RELEASE_TO_PASSTHROUGH
};
enum OwnershipDecision ios_ownership_step(uint64_t now, int auto_on, int screen_on, int hw_br,
                                          int fresh_als_samples, int chmod_lock_ok);
int32_t ios_target_debounce_step(uint64_t now, int32_t curved, int32_t current_hw, int32_t min_step);
/* Region adapter counters (region build target). */
unsigned ios_region_ownership_steps(void);
unsigned ios_region_debounce_steps(void);
unsigned ios_ownership_exec_count(void);
unsigned ios_target_debounce_exec_count(void);
unsigned ios_smooth_exec_count(void);
void ios_smooth_lux_reset(void);
float ios_smooth_lux_step(float raw_lux);
unsigned ios_thermal_exec_count(void);
int32_t ios_thermal_hard_step(int32_t desired_br, int32_t max_br, int32_t temp_mdeg, int charging);
int32_t ios_battery_brightness_cap(int32_t desired_br, int32_t max_br, int battery_pct, int charging);
int ios_thermal_read_zone_mdeg(DomainIo *io, const char *path, int *out_mdeg);
void ios_thermal_record_sample(int mdeg, int trusted);
int ios_thermal_scan_zones(DomainIo *io, uint64_t now);
unsigned ios_learn_apply_count(void);
int ios_learn_lux_bucket(float lux);
int ios_learn_time_bucket(int hour);
float ios_learn_apply(float base_pct, float lux, int hour);
enum HighLuxAction {
    HL_KEEP = 0,
    HL_ENTER,
    HL_HOLD,
    HL_EXIT_LUX,
    HL_EXIT_MAX_ACTIVE,
    HL_EXIT_COOLDOWN,
    HL_BLOCKED
};
enum HighLuxAction ios_highlux_step(uint64_t now, float lux, int32_t max_br, int32_t temp_mdeg,
                                    int battery_pct, int thermal_blocked);
unsigned ios_highlux_exec_count(void);
unsigned ios_ndk_select_count(void);
int ios_sensor_type_is_front_light(int type);
int ios_sensor_type_is_back_light(int type);
int ios_sensor_type_is_prox(int type);
int ios_sensor_select(const int *types, int count, int want);
int ios_sensor_event_valid(float lux, int64_t ts_ns, int64_t last_ts_ns);
void ios_sensor_record_front(float lux, int64_t ts_ns, uint64_t now_ms);
void ios_sensor_record_back(float lux, int64_t ts_ns, uint64_t now_ms);
unsigned ios_schedule_exec_count(void);
int32_t ios_choose_poll_ms(int32_t base_poll_ms, int transition_active, int32_t actuator_frame_ms,
                           uint64_t now, uint64_t last_actuator_ms);
int ios_sensor_stale_step(uint64_t now, uint64_t last_lux_event_ms, int hold_timeout_ms);
#endif
/* Typed production APIs. */
int32_t api_read_max_brightness(void);
int api_read_screen_on(void);
float api_curve_pct_for_lux(float lux);
void api_trans_update(int32_t target, int32_t current, uint64_t now);
void api_refresh_battery(uint64_t now);
void api_refresh_settings(void);
float api_read_lux_fallback(void);
void api_highlux_deactivate(const char *reason);
bool ios_read_screen_on(DomainIo *io);
float ios_brightness_for_lux(float lux, float gamma);
void ios_transition_update(int32_t target, int32_t current, uint64_t now);
void ios_highlux_exit(const char *reason);
void ios_apply_brightness(int32_t *current, int32_t target, uint64_t now, int emergency);
void ios_load_config_typed(void);
int ios_load_learn_typed(void);
void ios_save_learn_typed(void);
void ios_write_state_typed(const char *mode, int screen, int target, int current, int poll_ms,
                           const char *reason, uint64_t now, float lux, float smooth);
int ios_write_state_frame(const char *mode, int screen, int target, int current, int poll_ms,
                          const char *reason, uint64_t now, float lux, float smooth);
void ios_region_reset_counters(void);
enum OwnershipDecision ios_region_ownership_step(uint64_t now, int auto_on, int screen_on,
                                                 int hw_br, int fresh_als, int chmod_ok);
int32_t ios_region_target_debounce_step(uint64_t now, int32_t curved, int32_t hw, int32_t min_step);
enum IosImport {
  IOS_I___cxa_atexit,
  IOS_I___libc_init,
  IOS_I___register_atfork,
  IOS_I_access,
  IOS_I_atof,
  IOS_I_atoi,
  IOS_I_chmod,
  IOS_I_clock_gettime,
  IOS_I_close,
  IOS_I_closedir,
  IOS_I_dlclose,
  IOS_I_dlerror,
  IOS_I_dlopen,
  IOS_I_dlsym,
  IOS_I_expf,
  IOS_I_fclose,
  IOS_I_fflush,
  IOS_I_fgets,
  IOS_I_fopen,
  IOS_I_fprintf,
  IOS_I_fscanf,
  IOS_I_fwrite,
  IOS_I_getpid,
  IOS_I_localtime,
  IOS_I_log10f,
  IOS_I_memcpy,
  IOS_I_memset,
  IOS_I_open,
  IOS_I_opendir,
  IOS_I_pclose,
  IOS_I_popen,
  IOS_I_powf,
  IOS_I_readdir,
  IOS_I_rename,
  IOS_I_setpriority,
  IOS_I_signal,
  IOS_I_snprintf,
  IOS_I_sscanf,
  IOS_I_stat,
  IOS_I_strchr,
  IOS_I_strcmp,
  IOS_I_strncmp,
  IOS_I_strncpy,
  IOS_I_strstr,
  IOS_I_strtof,
  IOS_I_strtol,
  IOS_I_time,
  IOS_I_unlink,
  IOS_I_usleep,
  IOS_I_write,
  IOS_IMPORT_COUNT
};
extern const char *const ios_import_names[IOS_IMPORT_COUNT];
