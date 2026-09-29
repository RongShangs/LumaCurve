"""Explicit production source list for LumaCurve."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
CSRC = ROOT / 'csrc'
DEFAULT_TARGET = 'highlevel'
PRODUCTION_SOURCES = ['brightness_preference.c', 'apply_brightness_frame.c', 'curve_pct_for_lux.c', 'data.c', 'hbm_find_node.c', 'hbm_scan_bounded.c', 'highlux_deactivate.c', 'load_config.c', 'load_learn.c', 'poll_sensors.c', 'read_lux_fallback.c', 'read_screen.c', 'refresh_battery_state.c', 'refresh_display_state_on_edge.c', 'refresh_power_wakefulness.c', 'refresh_settings.c', 'save_learn.c', 'set_light_sensors_enabled.c', 'sig_h.c', 'sig_learn_reset.c', 'sig_reload.c', 'sig_reload_learn.c', 'trans_update.c', 'write_state_file.c', 'domain_api.c', 'domain_api_more.c', 'main_business.c', 'business_startup.c', 'business_debounce.c', 'business_learning.c', 'business_thermal.c', 'business_sunlight.c', 'business_sensor_init.c', 'business_literals.c', 'state.c', 'platform_native.c']
def sources_for(target='highlevel'):
    if target != 'highlevel':
        raise ValueError('LumaCurve only builds its maintained highlevel C target')
    paths = [CSRC / name for name in PRODUCTION_SOURCES]
    for path in paths:
        if not path.is_file():
            raise FileNotFoundError(path)
    return paths
