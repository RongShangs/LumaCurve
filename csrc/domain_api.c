/* Typed production-facing APIs (W7). IosCpu adapters in existing files call these. */
#include "business_api.h"
#include <math.h>

int32_t api_read_max_brightness(void) {
    return INT(g_max);
}

int api_read_screen_on(void) {
    DomainIo io = {0};
    return ios_read_screen_on(&io) ? 1 : 0;
}

float api_curve_pct_for_lux(float lux) {
    return ios_brightness_for_lux(lux, FLOAT(cfg_gamma));
}

void api_trans_update(int32_t target, int32_t current, uint64_t now) {
    ios_transition_update(target, current, now);
}

void api_refresh_battery(uint64_t now) {
    DomainIo io = {0};
    ios_battery_refresh(&io, now);
}

void api_refresh_settings(void) {
    DomainIo io = {0};
    ios_settings_refresh(&io);
}

float api_read_lux_fallback(void) {
    DomainIo io = {0};
    uint64_t legacy_result = 0;
    return ios_fallback_lux_read(&io, &legacy_result);
}

void api_highlux_deactivate(const char *reason) {
    ios_highlux_exit(reason);
}
