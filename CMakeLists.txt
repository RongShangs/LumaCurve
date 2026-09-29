cmake_minimum_required(VERSION 3.22)
project(LumaCurve VERSION 1.0.0 LANGUAGES C)
if(NOT ANDROID OR NOT ANDROID_ABI STREQUAL "arm64-v8a")
  message(FATAL_ERROR "Use the Android NDK toolchain with ANDROID_ABI=arm64-v8a")
endif()
add_executable(luma_curve_daemon
  csrc/brightness_preference.c
  csrc/apply_brightness_frame.c
  csrc/curve_pct_for_lux.c
  csrc/data.c
  csrc/hbm_find_node.c
  csrc/hbm_scan_bounded.c
  csrc/highlux_deactivate.c
  csrc/load_config.c
  csrc/load_learn.c
  csrc/poll_sensors.c
  csrc/read_lux_fallback.c
  csrc/read_screen.c
  csrc/refresh_battery_state.c
  csrc/refresh_display_state_on_edge.c
  csrc/refresh_power_wakefulness.c
  csrc/refresh_settings.c
  csrc/save_learn.c
  csrc/set_light_sensors_enabled.c
  csrc/sig_h.c
  csrc/sig_learn_reset.c
  csrc/sig_reload.c
  csrc/sig_reload_learn.c
  csrc/trans_update.c
  csrc/write_state_file.c
  csrc/domain_api.c
  csrc/domain_api_more.c
  csrc/main_business.c
  csrc/business_startup.c
  csrc/business_debounce.c
  csrc/business_learning.c
  csrc/business_thermal.c
  csrc/business_sunlight.c
  csrc/business_sensor_init.c
  csrc/business_literals.c
  csrc/state.c
  csrc/platform_native.c
)
target_compile_features(luma_curve_daemon PRIVATE c_std_11)
target_compile_definitions(luma_curve_daemon PRIVATE _DEFAULT_SOURCE IOS_PRODUCTION IOS_BUSINESS_MAIN)
target_compile_options(luma_curve_daemon PRIVATE -O2 -g "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=." -fPIE -ffp-contract=off -fno-strict-aliasing -Wall -Wextra -Werror -Wno-unused-label)
target_link_options(luma_curve_daemon PRIVATE -pie -Wl,--no-relax -Wl,-z,max-page-size=16384 -Wl,--build-id=sha1 -Wl,-z,relro -Wl,-z,now)
target_link_libraries(luma_curve_daemon PRIVATE m dl)
