/* Named data views. Sizes and offsets remain fixed for compatibility. */
#pragma once
#include "runtime.h"
#ifdef IOS_PRODUCTION
#include "state.h"
#define IOS_ADDR_fini_array_with_sentinels ((uint8_t *)&ios_state.fini_array_with_sentinels)
#define IOS_ADDR_g_max ((uint8_t *)&ios_state.g_max)
#define IOS_ADDR_cfg_thermal_hard_trigger ((uint8_t *)&ios_state.cfg_thermal_hard_trigger)
#define IOS_ADDR_cfg_thermal_hard_resume ((uint8_t *)&ios_state.cfg_thermal_hard_resume)
#define IOS_ADDR_cfg_thermal_hard_floor_pct ((uint8_t *)&ios_state.cfg_thermal_hard_floor_pct)
#define IOS_ADDR_cfg_charging_heat_guard ((uint8_t *)&ios_state.cfg_charging_heat_guard)
#define IOS_ADDR_cached_auto ((uint8_t *)&ios_state.cached_auto)
#define IOS_ADDR_g_power_interactive ((uint8_t *)&ios_state.g_power_interactive)
#define IOS_ADDR_g_last_display_state ((uint8_t *)&ios_state.g_last_display_state)
#define IOS_ADDR_g_brightness_owner ((uint8_t *)&ios_state.g_brightness_owner)
#define IOS_ADDR_g_power_wakefulness ((uint8_t *)&ios_state.g_power_wakefulness)
#define IOS_ADDR_g_run ((uint8_t *)&ios_state.g_run)
#define IOS_ADDR_g_lux_source ((uint8_t *)&ios_state.g_lux_source)
#define IOS_ADDR_last_stable_lux ((uint8_t *)&ios_state.last_stable_lux)
#define IOS_ADDR_prev_auto_mode ((uint8_t *)&ios_state.prev_auto_mode)
#define IOS_ADDR_g_wake_readonly_last_hw ((uint8_t *)&ios_state.g_wake_readonly_last_hw)
#define IOS_ADDR_g_write_gov_reason ((uint8_t *)&ios_state.g_write_gov_reason)
#define IOS_ADDR_current_poll_ms ((uint8_t *)&ios_state.current_poll_ms)
#define IOS_ADDR_g_config_error ((uint8_t *)&ios_state.g_config_error)
#define IOS_ADDR_g_wake_source ((uint8_t *)&ios_state.g_wake_source)
#define IOS_ADDR_lux_prev4 ((uint8_t *)&ios_state.lux_prev4)
#define IOS_ADDR_lux_prev3 ((uint8_t *)&ios_state.lux_prev3)
#define IOS_ADDR_lux_prev2 ((uint8_t *)&ios_state.lux_prev2)
#define IOS_ADDR_lux_prev1 ((uint8_t *)&ios_state.lux_prev1)
#define IOS_ADDR_cached_slider ((uint8_t *)&ios_state.cached_slider)
#define IOS_ADDR_cfg_hl_threshold ((uint8_t *)&ios_state.cfg_hl_threshold)
#define IOS_ADDR_cfg_min_step ((uint8_t *)&ios_state.cfg_min_step)
#define IOS_ADDR_cfg_hbm_off_value ((uint8_t *)&ios_state.cfg_hbm_off_value)
#define IOS_ADDR_cfg_alpha_fast ((uint8_t *)&ios_state.cfg_alpha_fast)
#define IOS_ADDR_cfg_alpha_mid ((uint8_t *)&ios_state.cfg_alpha_mid)
#define IOS_ADDR_cfg_alpha_slow ((uint8_t *)&ios_state.cfg_alpha_slow)
#define IOS_ADDR_cfg_fast_thresh ((uint8_t *)&ios_state.cfg_fast_thresh)
#define IOS_ADDR_cfg_slow_thresh ((uint8_t *)&ios_state.cfg_slow_thresh)
#define IOS_ADDR_cfg_spike_up ((uint8_t *)&ios_state.cfg_spike_up)
#define IOS_ADDR_cfg_spike_down ((uint8_t *)&ios_state.cfg_spike_down)
#define IOS_ADDR_cfg_hyst_low ((uint8_t *)&ios_state.cfg_hyst_low)
#define IOS_ADDR_cfg_hyst_high ((uint8_t *)&ios_state.cfg_hyst_high)
#define IOS_ADDR_cfg_gamma ((uint8_t *)&ios_state.cfg_gamma)
#define IOS_ADDR_cfg_brighten_speed ((uint8_t *)&ios_state.cfg_brighten_speed)
#define IOS_ADDR_cfg_darken_speed ((uint8_t *)&ios_state.cfg_darken_speed)
#define IOS_ADDR_cfg_thermal_cap_enable ((uint8_t *)&ios_state.cfg_thermal_cap_enable)
#define IOS_ADDR_cfg_hl_extreme_lux ((uint8_t *)&ios_state.cfg_hl_extreme_lux)
#define IOS_ADDR_cfg_hl_boost_max ((uint8_t *)&ios_state.cfg_hl_boost_max)
#define IOS_ADDR_cfg_hl_min_pct ((uint8_t *)&ios_state.cfg_hl_min_pct)
#define IOS_ADDR_cfg_hl_cooldown_ms ((uint8_t *)&ios_state.cfg_hl_cooldown_ms)
#define IOS_ADDR_cfg_hl_temp_thresh ((uint8_t *)&ios_state.cfg_hl_temp_thresh)
#define IOS_ADDR_cfg_hl_temp_resume ((uint8_t *)&ios_state.cfg_hl_temp_resume)
#define IOS_ADDR_cfg_hl_bat_thresh ((uint8_t *)&ios_state.cfg_hl_bat_thresh)
#define IOS_ADDR_cfg_hl_hbm_enable ((uint8_t *)&ios_state.cfg_hl_hbm_enable)
#define IOS_ADDR_cfg_hbm_on_value ((uint8_t *)&ios_state.cfg_hbm_on_value)
#define IOS_ADDR_cfg_learn_strength ((uint8_t *)&ios_state.cfg_learn_strength)
#define IOS_ADDR_cfg_learn_max_offset ((uint8_t *)&ios_state.cfg_learn_max_offset)
#define IOS_ADDR_cfg_learn_sample_delay_ms ((uint8_t *)&ios_state.cfg_learn_sample_delay_ms)
#define IOS_ADDR_cfg_learn_decay_days ((uint8_t *)&ios_state.cfg_learn_decay_days)
#define IOS_ADDR_cfg_learn_curve_sigma ((uint8_t *)&ios_state.cfg_learn_curve_sigma)
#define IOS_ADDR_cfg_learn_time_neighbor ((uint8_t *)&ios_state.cfg_learn_time_neighbor)
#define IOS_ADDR_cfg_learn_min_delta ((uint8_t *)&ios_state.cfg_learn_min_delta)
#define IOS_ADDR_hl_hbm_discovery ((uint8_t *)&ios_state.hl_hbm_discovery)
#define IOS_ADDR_g_sensor_rate_mode ((uint8_t *)&ios_state.g_sensor_rate_mode)
#define IOS_ADDR_last_settings_xml_size ((uint8_t *)&ios_state.last_settings_xml_size)
#define IOS_ADDR_cached_slider_source ((uint8_t *)&ios_state.cached_slider_source)
#define IOS_ADDR_settings_read_source ((uint8_t *)&ios_state.settings_read_source)
#define IOS_ADDR_g_front_lux ((uint8_t *)&ios_state.g_front_lux)
#define IOS_ADDR_g_back_lux ((uint8_t *)&ios_state.g_back_lux)
#define IOS_ADDR_hl_last_block_reason ((uint8_t *)&ios_state.hl_last_block_reason)
#define IOS_ADDR_g_last_write_readback ((uint8_t *)&ios_state.g_last_write_readback)
#define IOS_ADDR_g_low_lux_bright_spike_raw ((uint8_t *)&ios_state.g_low_lux_bright_spike_raw)
#define IOS_ADDR_g_thermal_guard_source ((uint8_t *)&ios_state.g_thermal_guard_source)
#define IOS_ADDR_g_reason_primary ((uint8_t *)&ios_state.g_reason_primary)
#define IOS_ADDR_g_cached_battery_cap ((uint8_t *)&ios_state.g_cached_battery_cap)
#define IOS_ADDR_prev_slider_val ((uint8_t *)&ios_state.prev_slider_val)
#define IOS_ADDR_cfg_learn_enabled ((uint8_t *)&ios_state.cfg_learn_enabled)
#define IOS_ADDR_cfg_last_mtime ((uint8_t *)&ios_state.cfg_last_mtime)
#define IOS_ADDR_hl_hbm_discovery_attempted ((uint8_t *)&ios_state.hl_hbm_discovery_attempted)
#define IOS_ADDR_g_ndk_ok ((uint8_t *)&ios_state.g_ndk_ok)
#define IOS_ADDR_trend_lux ((uint8_t *)&ios_state.trend_lux)
#define IOS_ADDR_g_tr_0 ((uint8_t *)&ios_state.g_tr_0)
#define IOS_ADDR_g_tr_1 ((uint8_t *)&ios_state.g_tr_1)
#define IOS_ADDR_g_tr_2 ((uint8_t *)&ios_state.g_tr_2)
#define IOS_ADDR_g_tr_3 ((uint8_t *)&ios_state.g_tr_3)
#define IOS_ADDR_g_wake_readonly_until ((uint8_t *)&ios_state.g_wake_readonly_until)
#define IOS_ADDR_g_wake_readonly_extends ((uint8_t *)&ios_state.g_wake_readonly_extends)
#define IOS_ADDR_g_target_hold_active ((uint8_t *)&ios_state.g_target_hold_active)
#define IOS_ADDR_g_gov_hold ((uint8_t *)&ios_state.g_gov_hold)
#define IOS_ADDR_g_gov_last_dir ((uint8_t *)&ios_state.g_gov_last_dir)
#define IOS_ADDR_g_low_lux_rise_confirmed_until ((uint8_t *)&ios_state.g_low_lux_rise_confirmed_until)
#define IOS_ADDR_g_low_lux_rise_confirmed_samples ((uint8_t *)&ios_state.g_low_lux_rise_confirmed_samples)
#define IOS_ADDR_g_low_lux_rise_ceiling_br ((uint8_t *)&ios_state.g_low_lux_rise_ceiling_br)
#define IOS_ADDR_g_low_lux_rise_fallback_hold_until ((uint8_t *)&ios_state.g_low_lux_rise_fallback_hold_until)
#define IOS_ADDR_g_zero_lux_recent_mask ((uint8_t *)&ios_state.g_zero_lux_recent_mask)
#define IOS_ADDR_stable_count ((uint8_t *)&ios_state.stable_count)
#define IOS_ADDR_g_wake_guard_until ((uint8_t *)&ios_state.g_wake_guard_until)
#define IOS_ADDR_g_last_lux_event_ms ((uint8_t *)&ios_state.g_last_lux_event_ms)
#define IOS_ADDR_g_reset_learn ((uint8_t *)&ios_state.g_reset_learn)
#define IOS_ADDR_g_reload_config ((uint8_t *)&ios_state.g_reload_config)
#define IOS_ADDR_g_reload_learn ((uint8_t *)&ios_state.g_reload_learn)
#define IOS_ADDR_g_learn_reset_settle_until ((uint8_t *)&ios_state.g_learn_reset_settle_until)
#define IOS_ADDR_g_transition_active ((uint8_t *)&ios_state.g_transition_active)
#define IOS_ADDR_g_last_actuator_ms ((uint8_t *)&ios_state.g_last_actuator_ms)
#define IOS_ADDR_g_actuator_frame_ms ((uint8_t *)&ios_state.g_actuator_frame_ms)
#define IOS_ADDR_g_thermal_emergency_bypass ((uint8_t *)&ios_state.g_thermal_emergency_bypass)
#define IOS_ADDR_bright_cnt ((uint8_t *)&ios_state.bright_cnt)
#define IOS_ADDR_dark_cnt ((uint8_t *)&ios_state.dark_cnt)
#define IOS_ADDR_g_actuator_only_count ((uint8_t *)&ios_state.g_actuator_only_count)
#define IOS_ADDR_g_light_sensors_enabled ((uint8_t *)&ios_state.g_light_sensors_enabled)
#define IOS_ADDR_g_last_screen_edge_ms ((uint8_t *)&ios_state.g_last_screen_edge_ms)
#define IOS_ADDR_g_learn_pending_3 ((uint8_t *)&ios_state.learn_pending_since_ms)
#define IOS_ADDR_g_learn_pending_4 ((uint8_t *)&ios_state.learn_pending_sample_count)
#define IOS_ADDR_g_learn_pending_5 ((uint8_t *)&ios_state.learn_pending_lux_sum)
#define IOS_ADDR_g_learn_pending_6 ((uint8_t *)&ios_state.learn_pending_lux_sum_mirror)
#define IOS_ADDR_g_learn_pending_7 ((uint8_t *)&ios_state.learn_pending_committed)
#define IOS_ADDR_g_wake_hw_br ((uint8_t *)&ios_state.g_wake_hw_br)
#define IOS_ADDR_g_wake_sensor_settle_until ((uint8_t *)&ios_state.g_wake_sensor_settle_until)
#define IOS_ADDR_g_external_write_hold ((uint8_t *)&ios_state.g_external_write_hold)
#define IOS_ADDR_g_external_write_hold_until ((uint8_t *)&ios_state.g_external_write_hold_until)
#define IOS_ADDR_g_external_write_burst_count ((uint8_t *)&ios_state.g_external_write_burst_count)
#define IOS_ADDR_g_external_write_burst_start_ms ((uint8_t *)&ios_state.g_external_write_burst_start_ms)
#define IOS_ADDR_fast_dark_confirm_cnt ((uint8_t *)&ios_state.fast_dark_confirm_cnt)
#define IOS_ADDR_fast_dark_streak ((uint8_t *)&ios_state.fast_dark_streak)
#define IOS_ADDR_g_zero_lux_since ((uint8_t *)&ios_state.g_zero_lux_since)
#define IOS_ADDR_g_low_lux_bright_spike_guard ((uint8_t *)&ios_state.g_low_lux_bright_spike_guard)
#define IOS_ADDR_g_low_lux_bright_spike_count ((uint8_t *)&ios_state.g_low_lux_bright_spike_count)
#define IOS_ADDR_g_low_lux_bright_spike_since ((uint8_t *)&ios_state.g_low_lux_bright_spike_since)
#define IOS_ADDR_g_transition_retarget_count ((uint8_t *)&ios_state.g_transition_retarget_count)
#define IOS_ADDR_g_aod_enabled ((uint8_t *)&ios_state.g_aod_enabled)
#define IOS_ADDR_g_learn_dirty ((uint8_t *)&ios_state.g_learn_dirty)
#define IOS_ADDR_g_last_learn_flush_ms ((uint8_t *)&ios_state.g_last_learn_flush_ms)
#define IOS_ADDR_g_wake_lux_sample_count ((uint8_t *)&ios_state.g_wake_lux_sample_count)
#define IOS_ADDR_trend_idx ((uint8_t *)&ios_state.trend_idx)
#define IOS_ADDR_trend_filled ((uint8_t *)&ios_state.trend_filled)
#define IOS_ADDR_g_last_processed_lux_event_ms ((uint8_t *)&ios_state.g_last_processed_lux_event_ms)
#define IOS_ADDR_g_actuator_smooth_lux ((uint8_t *)&ios_state.g_actuator_smooth_lux)
#define IOS_ADDR_g_lux_valid ((uint8_t *)&ios_state.g_lux_valid)
#define IOS_ADDR_g_last_write_ms ((uint8_t *)&ios_state.g_last_write_ms)
#define IOS_ADDR_g_proximity_near ((uint8_t *)&ios_state.g_proximity_near)
#define IOS_ADDR_g_dark_drop_until ((uint8_t *)&ios_state.g_dark_drop_until)
#define IOS_ADDR_fast_dark ((uint8_t *)&ios_state.fast_dark)
#define IOS_ADDR_fast_dark_candidate ((uint8_t *)&ios_state.fast_dark_candidate)
#define IOS_ADDR_g_sensor_reactive_until ((uint8_t *)&ios_state.g_sensor_reactive_until)
#define IOS_ADDR_g_zero_lux_suspect ((uint8_t *)&ios_state.g_zero_lux_suspect)
#define IOS_ADDR_g_dark_drop_confirm_count ((uint8_t *)&ios_state.g_dark_drop_confirm_count)
#define IOS_ADDR_g_dark_settle_until ((uint8_t *)&ios_state.g_dark_settle_until)
#define IOS_ADDR_g_actuator_fast_dark ((uint8_t *)&ios_state.g_actuator_fast_dark)
#define IOS_ADDR_g_sensor_stale ((uint8_t *)&ios_state.g_sensor_stale)
#define IOS_ADDR_g_sensor_hold_active ((uint8_t *)&ios_state.g_sensor_hold_active)
#define IOS_ADDR_hl_active ((uint8_t *)&ios_state.hl_active)
#define IOS_ADDR_hl_hbm_active ((uint8_t *)&ios_state.hl_hbm_active)
#define IOS_ADDR_g_wake_target_raw ((uint8_t *)&ios_state.g_wake_target_raw)
#define IOS_ADDR_g_wake_target_clamped ((uint8_t *)&ios_state.g_wake_target_clamped)
#define IOS_ADDR_g_heat_guard_active ((uint8_t *)&ios_state.g_heat_guard_active)
#define IOS_ADDR_g_thermal_entry ((uint8_t *)&ios_state.g_thermal_entry)
#define IOS_ADDR_cached_auto_adj_valid ((uint8_t *)&ios_state.cached_auto_adj_valid)
#define IOS_ADDR_g_last_stable_on_hits ((uint8_t *)&ios_state.g_last_stable_on_hits)
#define IOS_ADDR_g_last_stable_on_br ((uint8_t *)&ios_state.g_last_stable_on_br)
#define IOS_ADDR_g_last_stable_on_lux ((uint8_t *)&ios_state.g_last_stable_on_lux)
#define IOS_ADDR_g_last_stable_on_ms ((uint8_t *)&ios_state.g_last_stable_on_ms)
#define IOS_ADDR_g_target_candidate_dir ((uint8_t *)&ios_state.g_target_candidate_dir)
#define IOS_ADDR_hl_confirm_cnt ((uint8_t *)&ios_state.hl_confirm_cnt)
#define IOS_ADDR_hl_trigger_cnt ((uint8_t *)&ios_state.hl_trigger_cnt)
#define IOS_ADDR_learn_commit_cnt ((uint8_t *)&ios_state.learn_commit_cnt)
#define IOS_ADDR_hl_hbm_path ((uint8_t *)&ios_state.hl_hbm_path)
#define IOS_ADDR_g_config_valid ((uint8_t *)&ios_state.g_config_valid)
#define IOS_ADDR_cfg_config_version ((uint8_t *)&ios_state.cfg_config_version)
#define IOS_ADDR_cfg_hl_max_active_ms ((uint8_t *)&ios_state.cfg_hl_max_active_ms)
#define IOS_ADDR_cfg_hbm_node_path ((uint8_t *)&ios_state.cfg_hbm_node_path)
#define IOS_ADDR_g_learn ((uint8_t *)&ios_state.g_learn)
#define IOS_ADDR_g_global_offset ((uint8_t *)&ios_state.g_global_offset)
#define IOS_ADDR_hl_hbm_found ((uint8_t *)&ios_state.hl_hbm_found)
#define IOS_ADDR_h_lib ((uint8_t *)&ios_state.h_lib)
#define IOS_ADDR_fn_getMgrPkg ((uint8_t *)&ios_state.fn_getMgrPkg)
#define IOS_ADDR_fn_getMgr ((uint8_t *)&ios_state.fn_getMgr)
#define IOS_ADDR_fn_getDefSensor ((uint8_t *)&ios_state.fn_getDefSensor)
#define IOS_ADDR_fn_createEQ ((uint8_t *)&ios_state.fn_createEQ)
#define IOS_ADDR_fn_destroyEQ ((uint8_t *)&ios_state.fn_destroyEQ)
#define IOS_ADDR_fn_enable ((uint8_t *)&ios_state.fn_enable)
#define IOS_ADDR_fn_disable ((uint8_t *)&ios_state.fn_disable)
#define IOS_ADDR_fn_setRate ((uint8_t *)&ios_state.fn_setRate)
#define IOS_ADDR_fn_getEvents ((uint8_t *)&ios_state.fn_getEvents)
#define IOS_ADDR_fn_minDelay ((uint8_t *)&ios_state.fn_minDelay)
#define IOS_ADDR_fn_looperPrepare ((uint8_t *)&ios_state.fn_looperPrepare)
#define IOS_ADDR_fn_looperPoll ((uint8_t *)&ios_state.fn_looperPoll)
#define IOS_ADDR_fn_getSensorList ((uint8_t *)&ios_state.fn_getSensorList)
#define IOS_ADDR_fn_getType ((uint8_t *)&ios_state.fn_getType)
#define IOS_ADDR_fn_getName ((uint8_t *)&ios_state.fn_getName)
#define IOS_ADDR_g_mgr ((uint8_t *)&ios_state.g_mgr)
#define IOS_ADDR_g_eq ((uint8_t *)&ios_state.g_eq)
#define IOS_ADDR_g_front_sensor ((uint8_t *)&ios_state.g_front_sensor)
#define IOS_ADDR_g_sensor_rate_us ((uint8_t *)&ios_state.g_sensor_rate_us)
#define IOS_ADDR_g_back_sensor ((uint8_t *)&ios_state.g_back_sensor)
#define IOS_ADDR_g_back_ok ((uint8_t *)&ios_state.g_back_ok)
#define IOS_ADDR_g_prox_sensor ((uint8_t *)&ios_state.g_prox_sensor)
#define IOS_ADDR_g_prox_ok ((uint8_t *)&ios_state.g_prox_ok)
#define IOS_ADDR_g_sensor_path ((uint8_t *)&ios_state.g_sensor_path)
#define IOS_ADDR_last_settings_ms ((uint8_t *)&ios_state.last_settings_ms)
#define IOS_ADDR_last_settings_xml_mtime ((uint8_t *)&ios_state.last_settings_xml_mtime)
#define IOS_ADDR_last_settings_shell_ms ((uint8_t *)&ios_state.last_settings_shell_ms)
#define IOS_ADDR_cached_auto_adj ((uint8_t *)&ios_state.cached_auto_adj)
#define IOS_ADDR_settings_read_error ((uint8_t *)&ios_state.settings_read_error)
#define IOS_ADDR_g_last_aod_check_ms ((uint8_t *)&ios_state.g_last_aod_check_ms)
#define IOS_ADDR_g_target_debounce_init ((uint8_t *)&ios_state.g_target_debounce_init)
#define IOS_ADDR_g_target_debounce_br ((uint8_t *)&ios_state.g_target_debounce_br)
#define IOS_ADDR_g_target_candidate_br ((uint8_t *)&ios_state.g_target_candidate_br)
#define IOS_ADDR_g_target_candidate_since ((uint8_t *)&ios_state.g_target_candidate_since)
#define IOS_ADDR_g_front_sensor_timestamp_ns ((uint8_t *)&ios_state.g_front_sensor_timestamp_ns)
#define IOS_ADDR_g_front_lux_event_ms ((uint8_t *)&ios_state.g_front_lux_event_ms)
#define IOS_ADDR_g_sensor_nan_count ((uint8_t *)&ios_state.g_sensor_nan_count)
#define IOS_ADDR_g_back_sensor_timestamp_ns ((uint8_t *)&ios_state.g_back_sensor_timestamp_ns)
#define IOS_ADDR_g_back_lux_event_ms ((uint8_t *)&ios_state.g_back_lux_event_ms)
#define IOS_ADDR_g_back_confirm_event_ms ((uint8_t *)&ios_state.g_back_confirm_event_ms)
#define IOS_ADDR_g_back_fallback_confirm_count ((uint8_t *)&ios_state.g_back_fallback_confirm_count)
#define IOS_ADDR_g_sensor_hold_since_ms ((uint8_t *)&ios_state.g_sensor_hold_since_ms)
#define IOS_ADDR_g_sensor_hold_timeout ((uint8_t *)&ios_state.g_sensor_hold_timeout)
#define IOS_ADDR_g_sensor_stale_source ((uint8_t *)&ios_state.g_sensor_stale_source)
#define IOS_ADDR_hl_exiting ((uint8_t *)&ios_state.hl_exiting)
#define IOS_ADDR_hl_boost_br ((uint8_t *)&ios_state.hl_boost_br)
#define IOS_ADDR_g_wake_lux_samples ((uint8_t *)&ios_state.g_wake_lux_samples)
#define IOS_ADDR_g_wake_lux_sample_index ((uint8_t *)&ios_state.g_wake_lux_sample_index)
#define IOS_ADDR_g_wake_last_lux_event_ms ((uint8_t *)&ios_state.g_wake_last_lux_event_ms)
#define IOS_ADDR_cfg_last_check_ms ((uint8_t *)&ios_state.cfg_last_check_ms)
#define IOS_ADDR_g_gov_hold_until ((uint8_t *)&ios_state.g_gov_hold_until)
#define IOS_ADDR_g_write_readback_mismatch_streak ((uint8_t *)&ios_state.g_write_readback_mismatch_streak)
#define IOS_ADDR_g_last_write_result ((uint8_t *)&ios_state.g_last_write_result)
#define IOS_ADDR_g_thermal_temp ((uint8_t *)&ios_state.g_thermal_temp)
#define IOS_ADDR_g_tz_last_class ((uint8_t *)&ios_state.g_tz_last_class)
#define IOS_ADDR_g_thermal_trusted_temp_mdeg ((uint8_t *)&ios_state.g_thermal_trusted_temp_mdeg)
#define IOS_ADDR_g_thermal_suspect_temp_mdeg ((uint8_t *)&ios_state.g_thermal_suspect_temp_mdeg)
#define IOS_ADDR_g_thermal_deny_source ((uint8_t *)&ios_state.g_thermal_deny_source)
#define IOS_ADDR_g_thermal_exit_settle_until ((uint8_t *)&ios_state.g_thermal_exit_settle_until)
#define IOS_ADDR_g_thermal_floor_latched_br ((uint8_t *)&ios_state.g_thermal_floor_latched_br)
#define IOS_ADDR_g_thermal_source_filtered ((uint8_t *)&ios_state.g_thermal_source_filtered)
#define IOS_ADDR_g_thermal_guard_confirm_cnt ((uint8_t *)&ios_state.g_thermal_guard_confirm_cnt)
#define IOS_ADDR_g_thermal_cap_br ((uint8_t *)&ios_state.g_thermal_cap_br)
#define IOS_ADDR_hl_active_start ((uint8_t *)&ios_state.hl_active_start)
#define IOS_ADDR_hl_cooldown_until ((uint8_t *)&ios_state.hl_cooldown_until)
#define IOS_ADDR_g_reason_chain ((uint8_t *)&ios_state.g_reason_chain)
#define IOS_ADDR_g_learn_block_reason ((uint8_t *)&ios_state.g_learn_block_reason)
#define IOS_ADDR_g_last_battery_read_ms ((uint8_t *)&ios_state.g_last_battery_read_ms)
#define IOS_ADDR_g_cached_charging ((uint8_t *)&ios_state.g_cached_charging)
#define IOS_ADDR_g_tz_last_source ((uint8_t *)&ios_state.g_tz_last_source)
#define IOS_ADDR_g_temp_battery_mdeg ((uint8_t *)&ios_state.g_temp_battery_mdeg)
#define IOS_ADDR_g_thermal_trusted_source ((uint8_t *)&ios_state.g_thermal_trusted_source)
#define IOS_ADDR_g_temp_charger_mdeg ((uint8_t *)&ios_state.g_temp_charger_mdeg)
#define IOS_ADDR_g_temp_display_mdeg ((uint8_t *)&ios_state.g_temp_display_mdeg)
#define IOS_ADDR_g_thermal_suspect_source ((uint8_t *)&ios_state.g_thermal_suspect_source)
#define IOS_ADDR_g_last_thermal_temp_read_ms ((uint8_t *)&ios_state.g_last_thermal_temp_read_ms)
#define IOS_ADDR_g_tz_cached ((uint8_t *)&ios_state.g_tz_cached)
#define IOS_ADDR_g_tz_count ((uint8_t *)&ios_state.g_tz_count)
#define IOS_ADDR_g_tz_indices ((uint8_t *)&ios_state.g_tz_indices)
#define IOS_ADDR_g_tz_names ((uint8_t *)&ios_state.g_tz_names)
#define IOS_ADDR_g_tz_classes ((uint8_t *)&ios_state.g_tz_classes)
#define IOS_ADDR_hl_thermal_blocked ((uint8_t *)&ios_state.hl_thermal_blocked)
#define IOS_ADDR_hl_exit_start ((uint8_t *)&ios_state.hl_exit_start)
#define IOS_ADDR_g_sensor_zero_streak ((uint8_t *)&ios_state.g_sensor_zero_streak)
#define IOS_ADDR_g_sensor_max_streak ((uint8_t *)&ios_state.g_sensor_max_streak)
#define IOS_ADDR_curve_lux_anchors (ios_ro + 13716)
#define IOS_ADDR_curve_pct_anchors (ios_ro + 13772)
#else
#define IOS_ADDR_fini_array_with_sentinels (ios_ram + 6328) /* 0x138b8, 24 bytes */
#define IOS_ADDR_g_learn (ios_ram + 8104) /* 0x13fa8, 1056 bytes */
#define IOS_ADDR_cfg_learn_enabled (ios_ram + 7040) /* 0x13b80, 4 bytes */
#define IOS_ADDR_g_global_offset (ios_ram + 9160) /* 0x143c8, 4 bytes */
#define IOS_ADDR_g_max (ios_ram + 6352) /* 0x138d0, 4 bytes */
#define IOS_ADDR_cfg_last_mtime (ios_ram + 7048) /* 0x13b88, 8 bytes */
#define IOS_ADDR_cfg_thermal_hard_floor_pct (ios_ram + 6364) /* 0x138dc, 4 bytes */
#define IOS_ADDR_cfg_thermal_hard_resume (ios_ram + 6360) /* 0x138d8, 4 bytes */
#define IOS_ADDR_cfg_charging_heat_guard (ios_ram + 6368) /* 0x138e0, 4 bytes */
#define IOS_ADDR_cfg_thermal_hard_trigger (ios_ram + 6356) /* 0x138d4, 4 bytes */
#define IOS_ADDR_hl_hbm_discovery_attempted (ios_ram + 7056) /* 0x13b90, 1 bytes */
#define IOS_ADDR_hl_hbm_path (ios_ram + 7576) /* 0x13d98, 256 bytes */
#define IOS_ADDR_cfg_hbm_off_value (ios_ram + 6660) /* 0x13a04, 32 bytes */
#define IOS_ADDR_hl_hbm_active (ios_ram + 7512) /* 0x13d58, 1 bytes */
#define IOS_ADDR_h_lib (ios_ram + 9168) /* 0x143d0, 8 bytes */
#define IOS_ADDR_fn_setRate (ios_ram + 9232) /* 0x14410, 8 bytes */
#define IOS_ADDR_fn_looperPoll (ios_ram + 9264) /* 0x14430, 8 bytes */
#define IOS_ADDR_fn_disable (ios_ram + 9224) /* 0x14408, 8 bytes */
#define IOS_ADDR_fn_destroyEQ (ios_ram + 9208) /* 0x143f8, 8 bytes */
#define IOS_ADDR_fn_getMgrPkg (ios_ram + 9176) /* 0x143d8, 8 bytes */
#define IOS_ADDR_fn_getMgr (ios_ram + 9184) /* 0x143e0, 8 bytes */
#define IOS_ADDR_fn_getDefSensor (ios_ram + 9192) /* 0x143e8, 8 bytes */
#define IOS_ADDR_fn_createEQ (ios_ram + 9200) /* 0x143f0, 8 bytes */
#define IOS_ADDR_fn_enable (ios_ram + 9216) /* 0x14400, 8 bytes */
#define IOS_ADDR_fn_getEvents (ios_ram + 9240) /* 0x14418, 8 bytes */
#define IOS_ADDR_fn_minDelay (ios_ram + 9248) /* 0x14420, 8 bytes */
#define IOS_ADDR_fn_looperPrepare (ios_ram + 9256) /* 0x14428, 8 bytes */
#define IOS_ADDR_fn_getSensorList (ios_ram + 9272) /* 0x14438, 8 bytes */
#define IOS_ADDR_fn_getType (ios_ram + 9280) /* 0x14440, 8 bytes */
#define IOS_ADDR_fn_getName (ios_ram + 9288) /* 0x14448, 8 bytes */
#define IOS_ADDR_g_mgr (ios_ram + 9296) /* 0x14450, 8 bytes */
#define IOS_ADDR_g_eq (ios_ram + 9304) /* 0x14458, 8 bytes */
#define IOS_ADDR_g_front_sensor (ios_ram + 9312) /* 0x14460, 8 bytes */
#define IOS_ADDR_g_ndk_ok (ios_ram + 7060) /* 0x13b94, 1 bytes */
#define IOS_ADDR_g_light_sensors_enabled (ios_ram + 7260) /* 0x13c5c, 1 bytes */
#define IOS_ADDR_g_sensor_rate_us (ios_ram + 9320) /* 0x14468, 4 bytes */
#define IOS_ADDR_g_sensor_rate_mode (ios_ram + 6868) /* 0x13ad4, 4 bytes */
#define IOS_ADDR_g_sensor_path (ios_ram + 9356) /* 0x1448c, 256 bytes */
#define IOS_ADDR_cached_auto (ios_ram + 6372) /* 0x138e4, 4 bytes */
#define IOS_ADDR_g_last_aod_check_ms (ios_ram + 9712) /* 0x145f0, 8 bytes */
#define IOS_ADDR_g_aod_enabled (ios_ram + 7388) /* 0x13cdc, 4 bytes */
#define IOS_ADDR_g_power_interactive (ios_ram + 6376) /* 0x138e8, 4 bytes */
#define IOS_ADDR_g_last_display_state (ios_ram + 6380) /* 0x138ec, 24 bytes */
#define IOS_ADDR_g_brightness_owner (ios_ram + 6404) /* 0x13904, 32 bytes */
#define IOS_ADDR_g_power_wakefulness (ios_ram + 6436) /* 0x13924, 16 bytes */
#define IOS_ADDR_g_target_debounce_init (ios_ram + 9720) /* 0x145f8, 1 bytes */
#define IOS_ADDR_g_target_debounce_br (ios_ram + 9724) /* 0x145fc, 4 bytes */
#define IOS_ADDR_g_target_candidate_br (ios_ram + 9728) /* 0x14600, 4 bytes */
#define IOS_ADDR_g_target_candidate_since (ios_ram + 9736) /* 0x14608, 8 bytes */
#define IOS_ADDR_g_target_candidate_dir (ios_ram + 7560) /* 0x13d88, 4 bytes */
#define IOS_ADDR_g_run (ios_ram + 6452) /* 0x13934, 4 bytes */
#define IOS_ADDR_g_front_lux_event_ms (ios_ram + 9752) /* 0x14618, 8 bytes */
#define IOS_ADDR_g_front_lux (ios_ram + 6912) /* 0x13b00, 4 bytes */
#define IOS_ADDR_g_back_confirm_event_ms (ios_ram + 9784) /* 0x14638, 8 bytes */
#define IOS_ADDR_g_back_fallback_confirm_count (ios_ram + 9792) /* 0x14640, 4 bytes */
#define IOS_ADDR_g_lux_valid (ios_ram + 7436) /* 0x13d0c, 1 bytes */
#define IOS_ADDR_g_last_lux_event_ms (ios_ram + 7192) /* 0x13c18, 8 bytes */
#define IOS_ADDR_g_lux_source (ios_ram + 6456) /* 0x13938, 16 bytes */
#define IOS_ADDR_g_back_ok (ios_ram + 9336) /* 0x14478, 1 bytes */
#define IOS_ADDR_g_back_lux_event_ms (ios_ram + 9776) /* 0x14630, 8 bytes */
#define IOS_ADDR_g_back_lux (ios_ram + 6916) /* 0x13b04, 4 bytes */
#define IOS_ADDR_g_sensor_stale (ios_ram + 7500) /* 0x13d4c, 1 bytes */
#define IOS_ADDR_g_sensor_hold_active (ios_ram + 7504) /* 0x13d50, 1 bytes */
#define IOS_ADDR_g_sensor_hold_since_ms (ios_ram + 9800) /* 0x14648, 8 bytes */
#define IOS_ADDR_g_sensor_hold_timeout (ios_ram + 9808) /* 0x14650, 1 bytes */
#define IOS_ADDR_g_sensor_stale_source (ios_ram + 9812) /* 0x14654, 24 bytes */
#define IOS_ADDR_trend_lux (ios_ram + 7072) /* 0x13ba0, 20 bytes */
#define IOS_ADDR_last_stable_lux (ios_ram + 6472) /* 0x13948, 4 bytes */
#define IOS_ADDR_prev_auto_mode (ios_ram + 6476) /* 0x1394c, 4 bytes */
#define IOS_ADDR_g_tr_3 (ios_ram + 7120) /* 0x13bd0, 4 bytes */
#define IOS_ADDR_g_wake_readonly_until (ios_ram + 7128) /* 0x13bd8, 8 bytes */
#define IOS_ADDR_g_wake_readonly_extends (ios_ram + 7136) /* 0x13be0, 4 bytes */
#define IOS_ADDR_g_wake_readonly_last_hw (ios_ram + 6480) /* 0x13950, 4 bytes */
#define IOS_ADDR_g_target_hold_active (ios_ram + 7140) /* 0x13be4, 1 bytes */
#define IOS_ADDR_g_gov_hold (ios_ram + 7144) /* 0x13be8, 1 bytes */
#define IOS_ADDR_g_gov_last_dir (ios_ram + 7148) /* 0x13bec, 4 bytes */
#define IOS_ADDR_g_wake_lux_samples (ios_ram + 9844) /* 0x14674, 20 bytes */
#define IOS_ADDR_g_wake_lux_sample_count (ios_ram + 7408) /* 0x13cf0, 4 bytes */
#define IOS_ADDR_g_wake_lux_sample_index (ios_ram + 9864) /* 0x14688, 4 bytes */
#define IOS_ADDR_g_wake_last_lux_event_ms (ios_ram + 9872) /* 0x14690, 8 bytes */
#define IOS_ADDR_g_low_lux_rise_confirmed_until (ios_ram + 7152) /* 0x13bf0, 8 bytes */
#define IOS_ADDR_g_low_lux_rise_confirmed_samples (ios_ram + 7160) /* 0x13bf8, 4 bytes */
#define IOS_ADDR_g_low_lux_rise_ceiling_br (ios_ram + 7164) /* 0x13bfc, 4 bytes */
#define IOS_ADDR_g_low_lux_rise_fallback_hold_until (ios_ram + 7168) /* 0x13c00, 8 bytes */
#define IOS_ADDR_g_zero_lux_recent_mask (ios_ram + 7176) /* 0x13c08, 4 bytes */
#define IOS_ADDR_g_write_gov_reason (ios_ram + 6484) /* 0x13954, 32 bytes */
#define IOS_ADDR_current_poll_ms (ios_ram + 6516) /* 0x13974, 4 bytes */
#define IOS_ADDR_stable_count (ios_ram + 7180) /* 0x13c0c, 4 bytes */
#define IOS_ADDR_g_tr_0 (ios_ram + 7096) /* 0x13bb8, 4 bytes */
#define IOS_ADDR_g_tr_1 (ios_ram + 7104) /* 0x13bc0, 4 bytes */
#define IOS_ADDR_g_wake_guard_until (ios_ram + 7184) /* 0x13c10, 8 bytes */
#define IOS_ADDR_cfg_last_check_ms (ios_ram + 9880) /* 0x14698, 8 bytes */
#define IOS_ADDR_g_config_error (ios_ram + 6520) /* 0x13978, 96 bytes */
#define IOS_ADDR_g_reset_learn (ios_ram + 7200) /* 0x13c20, 4 bytes */
#define IOS_ADDR_g_learn_pending_7 (ios_ram + 7304) /* 0x13c88, 1 bytes */
#define IOS_ADDR_g_learn_reset_settle_until (ios_ram + 7216) /* 0x13c30, 8 bytes */
#define IOS_ADDR_g_learn_pending_4 (ios_ram + 7280) /* 0x13c70, 4 bytes */
#define IOS_ADDR_g_reload_config (ios_ram + 7204) /* 0x13c24, 4 bytes */
#define IOS_ADDR_g_reload_learn (ios_ram + 7208) /* 0x13c28, 4 bytes */
#define IOS_ADDR_g_transition_active (ios_ram + 7224) /* 0x13c38, 1 bytes */
#define IOS_ADDR_g_last_actuator_ms (ios_ram + 7232) /* 0x13c40, 8 bytes */
#define IOS_ADDR_g_actuator_frame_ms (ios_ram + 7240) /* 0x13c48, 4 bytes */
#define IOS_ADDR_g_external_write_hold (ios_ram + 7320) /* 0x13c98, 1 bytes */
#define IOS_ADDR_g_external_write_hold_until (ios_ram + 7328) /* 0x13ca0, 8 bytes */
#define IOS_ADDR_g_last_screen_edge_ms (ios_ram + 7264) /* 0x13c60, 8 bytes */
#define IOS_ADDR_g_learn_dirty (ios_ram + 7392) /* 0x13ce0, 1 bytes */
#define IOS_ADDR_g_last_learn_flush_ms (ios_ram + 7400) /* 0x13ce8, 8 bytes */
#define IOS_ADDR_g_thermal_emergency_bypass (ios_ram + 7244) /* 0x13c4c, 4 bytes */
#define IOS_ADDR_g_actuator_only_count (ios_ram + 7256) /* 0x13c58, 4 bytes */
#define IOS_ADDR_fast_dark_confirm_cnt (ios_ram + 7352) /* 0x13cb8, 4 bytes */
#define IOS_ADDR_fast_dark_streak (ios_ram + 7356) /* 0x13cbc, 4 bytes */
#define IOS_ADDR_g_wake_sensor_settle_until (ios_ram + 7312) /* 0x13c90, 8 bytes */
#define IOS_ADDR_g_zero_lux_since (ios_ram + 7360) /* 0x13cc0, 8 bytes */
#define IOS_ADDR_g_low_lux_bright_spike_guard (ios_ram + 7368) /* 0x13cc8, 1 bytes */
#define IOS_ADDR_g_low_lux_bright_spike_count (ios_ram + 7372) /* 0x13ccc, 4 bytes */
#define IOS_ADDR_g_low_lux_bright_spike_since (ios_ram + 7376) /* 0x13cd0, 8 bytes */
#define IOS_ADDR_g_wake_hw_br (ios_ram + 7308) /* 0x13c8c, 4 bytes */
#define IOS_ADDR_g_transition_retarget_count (ios_ram + 7384) /* 0x13cd8, 4 bytes */
#define IOS_ADDR_g_external_write_burst_count (ios_ram + 7336) /* 0x13ca8, 4 bytes */
#define IOS_ADDR_g_external_write_burst_start_ms (ios_ram + 7344) /* 0x13cb0, 8 bytes */
#define IOS_ADDR_g_wake_source (ios_ram + 6616) /* 0x139d8, 16 bytes */
#define IOS_ADDR_trend_idx (ios_ram + 7412) /* 0x13cf4, 4 bytes */
#define IOS_ADDR_trend_filled (ios_ram + 7416) /* 0x13cf8, 4 bytes */
#define IOS_ADDR_lux_prev4 (ios_ram + 6632) /* 0x139e8, 4 bytes */
#define IOS_ADDR_lux_prev3 (ios_ram + 6636) /* 0x139ec, 4 bytes */
#define IOS_ADDR_lux_prev2 (ios_ram + 6640) /* 0x139f0, 4 bytes */
#define IOS_ADDR_lux_prev1 (ios_ram + 6644) /* 0x139f4, 4 bytes */
#define IOS_ADDR_g_last_processed_lux_event_ms (ios_ram + 7424) /* 0x13d00, 8 bytes */
#define IOS_ADDR_g_actuator_smooth_lux (ios_ram + 7432) /* 0x13d08, 4 bytes */
#define IOS_ADDR_g_last_write_ms (ios_ram + 7440) /* 0x13d10, 8 bytes */
#define IOS_ADDR_g_proximity_near (ios_ram + 7448) /* 0x13d18, 4 bytes */
#define IOS_ADDR_cached_slider (ios_ram + 6648) /* 0x139f8, 4 bytes */
#define IOS_ADDR_g_dark_drop_until (ios_ram + 7456) /* 0x13d20, 8 bytes */
#define IOS_ADDR_fast_dark (ios_ram + 7464) /* 0x13d28, 4 bytes */
#define IOS_ADDR_fast_dark_candidate (ios_ram + 7468) /* 0x13d2c, 1 bytes */
#define IOS_ADDR_cfg_hl_threshold (ios_ram + 6652) /* 0x139fc, 4 bytes */
#define IOS_ADDR_g_sensor_reactive_until (ios_ram + 7472) /* 0x13d30, 8 bytes */
#define IOS_ADDR_g_zero_lux_suspect (ios_ram + 7480) /* 0x13d38, 1 bytes */
#define IOS_ADDR_g_dark_drop_confirm_count (ios_ram + 7484) /* 0x13d3c, 4 bytes */
#define IOS_ADDR_g_dark_settle_until (ios_ram + 7488) /* 0x13d40, 8 bytes */
#define IOS_ADDR_cfg_alpha_slow (ios_ram + 6700) /* 0x13a2c, 4 bytes */
#define IOS_ADDR_cfg_alpha_fast (ios_ram + 6692) /* 0x13a24, 4 bytes */
#define IOS_ADDR_cfg_fast_thresh (ios_ram + 6704) /* 0x13a30, 4 bytes */
#define IOS_ADDR_cfg_slow_thresh (ios_ram + 6708) /* 0x13a34, 4 bytes */
#define IOS_ADDR_cfg_alpha_mid (ios_ram + 6696) /* 0x13a28, 4 bytes */
#define IOS_ADDR_g_actuator_fast_dark (ios_ram + 7496) /* 0x13d48, 4 bytes */
#define IOS_ADDR_hl_active (ios_ram + 7508) /* 0x13d54, 1 bytes */
#define IOS_ADDR_cfg_learn_time_neighbor (ios_ram + 6828) /* 0x13aac, 4 bytes */
#define IOS_ADDR_cfg_learn_curve_sigma (ios_ram + 6824) /* 0x13aa8, 4 bytes */
#define IOS_ADDR_cfg_learn_decay_days (ios_ram + 6820) /* 0x13aa4, 4 bytes */
#define IOS_ADDR_cfg_learn_max_offset (ios_ram + 6812) /* 0x13a9c, 4 bytes */
#define IOS_ADDR_cfg_learn_strength (ios_ram + 6808) /* 0x13a98, 4 bytes */
#define IOS_ADDR_g_cached_battery_cap (ios_ram + 7016) /* 0x13b68, 4 bytes */
#define IOS_ADDR_g_cached_charging (ios_ram + 10256) /* 0x14810, 4 bytes */
#define IOS_ADDR_g_thermal_entry (ios_ram + 7528) /* 0x13d68, 1 bytes */
#define IOS_ADDR_g_thermal_source_filtered (ios_ram + 9964) /* 0x146ec, 1 bytes */
#define IOS_ADDR_cfg_thermal_cap_enable (ios_ram + 6740) /* 0x13a54, 4 bytes */
#define IOS_ADDR_g_thermal_cap_br (ios_ram + 9972) /* 0x146f4, 4 bytes */
#define IOS_ADDR_g_heat_guard_active (ios_ram + 7524) /* 0x13d64, 1 bytes */
#define IOS_ADDR_g_thermal_guard_confirm_cnt (ios_ram + 9968) /* 0x146f0, 4 bytes */
#define IOS_ADDR_g_thermal_guard_source (ios_ram + 6960) /* 0x13b30, 32 bytes */
#define IOS_ADDR_g_thermal_exit_settle_until (ios_ram + 9952) /* 0x146e0, 8 bytes */
#define IOS_ADDR_g_thermal_temp (ios_ram + 9904) /* 0x146b0, 4 bytes */
#define IOS_ADDR_g_last_thermal_temp_read_ms (ios_ram + 10368) /* 0x14880, 8 bytes */
#define IOS_ADDR_g_thermal_trusted_temp_mdeg (ios_ram + 9912) /* 0x146b8, 4 bytes */
#define IOS_ADDR_g_thermal_suspect_temp_mdeg (ios_ram + 9916) /* 0x146bc, 4 bytes */
#define IOS_ADDR_g_thermal_deny_source (ios_ram + 9920) /* 0x146c0, 32 bytes */
#define IOS_ADDR_g_thermal_floor_latched_br (ios_ram + 9960) /* 0x146e8, 4 bytes */
#define IOS_ADDR_g_tz_cached (ios_ram + 10376) /* 0x14888, 1 bytes */
#define IOS_ADDR_g_tz_count (ios_ram + 10380) /* 0x1488c, 4 bytes */
#define IOS_ADDR_g_tz_names (ios_ram + 10432) /* 0x148c0, 384 bytes */
#define IOS_ADDR_g_tz_indices (ios_ram + 10384) /* 0x14890, 48 bytes */
#define IOS_ADDR_g_tz_classes (ios_ram + 10816) /* 0x14a40, 48 bytes */
#define IOS_ADDR_g_temp_battery_mdeg (ios_ram + 10292) /* 0x14834, 4 bytes */
#define IOS_ADDR_g_temp_charger_mdeg (ios_ram + 10328) /* 0x14858, 4 bytes */
#define IOS_ADDR_g_temp_display_mdeg (ios_ram + 10332) /* 0x1485c, 4 bytes */
#define IOS_ADDR_g_tz_last_class (ios_ram + 9908) /* 0x146b4, 4 bytes */
#define IOS_ADDR_g_tz_last_source (ios_ram + 10260) /* 0x14814, 32 bytes */
#define IOS_ADDR_g_thermal_trusted_source (ios_ram + 10296) /* 0x14838, 32 bytes */
#define IOS_ADDR_g_thermal_suspect_source (ios_ram + 10336) /* 0x14860, 32 bytes */
#define IOS_ADDR_cfg_hl_temp_thresh (ios_ram + 6760) /* 0x13a68, 4 bytes */
#define IOS_ADDR_cfg_hl_temp_resume (ios_ram + 6764) /* 0x13a6c, 4 bytes */
#define IOS_ADDR_hl_thermal_blocked (ios_ram + 10864) /* 0x14a70, 1 bytes */
#define IOS_ADDR_cfg_hl_bat_thresh (ios_ram + 6768) /* 0x13a70, 4 bytes */
#define IOS_ADDR_hl_exiting (ios_ram + 9836) /* 0x1466c, 1 bytes */
#define IOS_ADDR_hl_exit_start (ios_ram + 10872) /* 0x14a78, 8 bytes */
#define IOS_ADDR_hl_last_block_reason (ios_ram + 6920) /* 0x13b08, 32 bytes */
#define IOS_ADDR_g_wake_target_raw (ios_ram + 7516) /* 0x13d5c, 4 bytes */
#define IOS_ADDR_g_last_stable_on_br (ios_ram + 7540) /* 0x13d74, 4 bytes */
#define IOS_ADDR_g_wake_target_clamped (ios_ram + 7520) /* 0x13d60, 4 bytes */
#define IOS_ADDR_cfg_min_step (ios_ram + 6656) /* 0x13a00, 4 bytes */
#define IOS_ADDR_cfg_hyst_low (ios_ram + 6720) /* 0x13a40, 4 bytes */
#define IOS_ADDR_cfg_hyst_high (ios_ram + 6724) /* 0x13a44, 4 bytes */
#define IOS_ADDR_g_tr_2 (ios_ram + 7112) /* 0x13bc8, 8 bytes */
#define IOS_ADDR_g_sensor_zero_streak (ios_ram + 10880) /* 0x14a80, 4 bytes */
#define IOS_ADDR_g_sensor_max_streak (ios_ram + 10884) /* 0x14a84, 4 bytes */
#define IOS_ADDR_cached_auto_adj_valid (ios_ram + 7532) /* 0x13d6c, 1 bytes */
#define IOS_ADDR_g_learn_block_reason (ios_ram + 10184) /* 0x147c8, 64 bytes */
#define IOS_ADDR_prev_slider_val (ios_ram + 7020) /* 0x13b6c, 4 bytes */
#define IOS_ADDR_cached_auto_adj (ios_ram + 9640) /* 0x145a8, 4 bytes */
#define IOS_ADDR_g_learn_pending_5 (ios_ram + 7288) /* 0x13c78, 4 bytes */
#define IOS_ADDR_g_learn_pending_6 (ios_ram + 7296) /* 0x13c80, 4 bytes */
#define IOS_ADDR_g_learn_pending_3 (ios_ram + 7272) /* 0x13c68, 8 bytes */
#define IOS_ADDR_cfg_learn_sample_delay_ms (ios_ram + 6816) /* 0x13aa0, 4 bytes */
#define IOS_ADDR_g_last_stable_on_hits (ios_ram + 7536) /* 0x13d70, 4 bytes */
#define IOS_ADDR_hl_confirm_cnt (ios_ram + 7564) /* 0x13d8c, 4 bytes */
#define IOS_ADDR_g_back_sensor (ios_ram + 9328) /* 0x14470, 8 bytes */
#define IOS_ADDR_g_last_stable_on_lux (ios_ram + 7544) /* 0x13d78, 4 bytes */
#define IOS_ADDR_g_last_stable_on_ms (ios_ram + 7552) /* 0x13d80, 8 bytes */
#define IOS_ADDR_cfg_learn_min_delta (ios_ram + 6832) /* 0x13ab0, 4 bytes */
#define IOS_ADDR_g_reason_chain (ios_ram + 9992) /* 0x14708, 192 bytes */
#define IOS_ADDR_g_reason_primary (ios_ram + 6992) /* 0x13b50, 24 bytes */
#define IOS_ADDR_cfg_hl_extreme_lux (ios_ram + 6744) /* 0x13a58, 4 bytes */
#define IOS_ADDR_cfg_hl_boost_max (ios_ram + 6748) /* 0x13a5c, 4 bytes */
#define IOS_ADDR_hl_boost_br (ios_ram + 9840) /* 0x14670, 4 bytes */
#define IOS_ADDR_hl_cooldown_until (ios_ram + 9984) /* 0x14700, 8 bytes */
#define IOS_ADDR_cfg_hl_max_active_ms (ios_ram + 7840) /* 0x13ea0, 4 bytes */
#define IOS_ADDR_hl_active_start (ios_ram + 9976) /* 0x146f8, 8 bytes */
#define IOS_ADDR_cfg_hl_cooldown_ms (ios_ram + 6756) /* 0x13a64, 4 bytes */
#define IOS_ADDR_hl_trigger_cnt (ios_ram + 7568) /* 0x13d90, 4 bytes */
#define IOS_ADDR_cfg_hl_min_pct (ios_ram + 6752) /* 0x13a60, 4 bytes */
#define IOS_ADDR_learn_commit_cnt (ios_ram + 7572) /* 0x13d94, 4 bytes */
#define IOS_ADDR_g_low_lux_bright_spike_raw (ios_ram + 6956) /* 0x13b2c, 4 bytes */
#define IOS_ADDR_cfg_spike_up (ios_ram + 6712) /* 0x13a38, 4 bytes */
#define IOS_ADDR_cfg_spike_down (ios_ram + 6716) /* 0x13a3c, 4 bytes */
#define IOS_ADDR_g_sensor_nan_count (ios_ram + 9760) /* 0x14620, 4 bytes */
#define IOS_ADDR_g_prox_sensor (ios_ram + 9344) /* 0x14480, 8 bytes */
#define IOS_ADDR_bright_cnt (ios_ram + 7248) /* 0x13c50, 4 bytes */
#define IOS_ADDR_dark_cnt (ios_ram + 7252) /* 0x13c54, 4 bytes */
#define IOS_ADDR_g_prox_ok (ios_ram + 9352) /* 0x14488, 1 bytes */
#define IOS_ADDR_cfg_config_version (ios_ram + 7836) /* 0x13e9c, 4 bytes */
#define IOS_ADDR_cfg_hbm_node_path (ios_ram + 7844) /* 0x13ea4, 256 bytes */
#define IOS_ADDR_cfg_gamma (ios_ram + 6728) /* 0x13a48, 4 bytes */
#define IOS_ADDR_cfg_hl_hbm_enable (ios_ram + 6772) /* 0x13a74, 4 bytes */
#define IOS_ADDR_cfg_brighten_speed (ios_ram + 6732) /* 0x13a4c, 4 bytes */
#define IOS_ADDR_cfg_darken_speed (ios_ram + 6736) /* 0x13a50, 4 bytes */
#define IOS_ADDR_cfg_hbm_on_value (ios_ram + 6776) /* 0x13a78, 32 bytes */
#define IOS_ADDR_g_config_valid (ios_ram + 7832) /* 0x13e98, 1 bytes */
#define IOS_ADDR_hl_hbm_discovery (ios_ram + 6836) /* 0x13ab4, 32 bytes */
#define IOS_ADDR_hl_hbm_found (ios_ram + 9164) /* 0x143cc, 4 bytes */
#define IOS_ADDR_last_settings_ms (ios_ram + 9616) /* 0x14590, 8 bytes */
#define IOS_ADDR_last_settings_shell_ms (ios_ram + 9632) /* 0x145a0, 8 bytes */
#define IOS_ADDR_last_settings_xml_mtime (ios_ram + 9624) /* 0x14598, 8 bytes */
#define IOS_ADDR_last_settings_xml_size (ios_ram + 6872) /* 0x13ad8, 8 bytes */
#define IOS_ADDR_cached_slider_source (ios_ram + 6880) /* 0x13ae0, 16 bytes */
#define IOS_ADDR_settings_read_error (ios_ram + 9644) /* 0x145ac, 64 bytes */
#define IOS_ADDR_settings_read_source (ios_ram + 6896) /* 0x13af0, 16 bytes */
#define IOS_ADDR_g_front_sensor_timestamp_ns (ios_ram + 9744) /* 0x14610, 8 bytes */
#define IOS_ADDR_g_back_sensor_timestamp_ns (ios_ram + 9768) /* 0x14628, 8 bytes */
#define IOS_ADDR_g_gov_hold_until (ios_ram + 9888) /* 0x146a0, 8 bytes */
#define IOS_ADDR_g_last_write_readback (ios_ram + 6952) /* 0x13b28, 4 bytes */
#define IOS_ADDR_g_write_readback_mismatch_streak (ios_ram + 9896) /* 0x146a8, 4 bytes */
#define IOS_ADDR_g_last_write_result (ios_ram + 9900) /* 0x146ac, 4 bytes */
#define IOS_ADDR_g_last_battery_read_ms (ios_ram + 10248) /* 0x14808, 8 bytes */
#define IOS_ADDR_curve_lux_anchors (ios_ro + 13716) /* 0x3594, 56 bytes */
#define IOS_ADDR_curve_pct_anchors (ios_ro + 13772) /* 0x35cc, 56 bytes */

#endif
