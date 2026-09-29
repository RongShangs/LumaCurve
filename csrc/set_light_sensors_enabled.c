#include "business_api.h"
#include "scene_adaptation.h"
static void reset_light_history(void) {
    ios_scene_reset(false);
    SET_FLOAT(g_front_lux, -1);
    SET_FLOAT(g_back_lux, -1);
    SET_TIME(g_front_sensor_timestamp_ns, 0);
    SET_TIME(g_back_sensor_timestamp_ns, 0);
    SET_TIME(g_front_lux_event_ms, 0);
    SET_TIME(g_back_lux_event_ms, 0);
    SET_TIME(g_last_processed_lux_event_ms, 0);
    SET_TIME(g_back_confirm_event_ms, 0);
    SET_INT(g_back_fallback_confirm_count, 0);
    SET_FLAG(g_lux_valid, 0);
    SET_TIME(g_last_lux_event_ms, 0);
    SET_FLAG(g_sensor_stale, 0);
    SET_FLAG(g_sensor_hold_active, 0);
    SET_TIME(g_sensor_hold_since_ms, 0);
    SET_FLAG(g_sensor_hold_timeout, 0);
    memcpy(STRING(g_lux_source), "none", 5);
}
static bool switch_sensor(DomainIo *io, uintptr_t function, uintptr_t sensor) {
    return sensor && (int32_t)NDK_switch(io, function, POINTER(g_eq), sensor) == 0;
}
void ios_light_sensors_set(DomainIo *io, uint32_t enabled) {
    if (FLAG(g_ndk_ok) != 1 || !POINTER(g_eq) || !POINTER(fn_enable) || !POINTER(fn_disable) ||
        FLAG(g_light_sensors_enabled) == enabled)
        return;
    uintptr_t operation = enabled ? POINTER(fn_enable) : POINTER(fn_disable);
    bool front = switch_sensor(io, operation, POINTER(g_front_sensor));
    bool back = FLAG(g_back_ok) == 1 && switch_sensor(io, operation, POINTER(g_back_sensor));
    if (FLAG(g_prox_ok) == 1)
        switch_sensor(io, operation, POINTER(g_prox_sensor));
    bool active = enabled && (front || back);
    SET_FLAG(g_light_sensors_enabled, active);
    SET_INT(g_sensor_rate_mode, 1);
    if (!active) {
        SET_INT(g_sensor_rate_us, 0);
        SET_INT(g_proximity_near, 0);
        reset_light_history();
        return;
    }
    reset_light_history();
    if (POINTER(fn_setRate)) {
        if (front)
            NDK_rate(io, POINTER(fn_setRate), POINTER(g_eq), POINTER(g_front_sensor), 1000000);
        if (back)
            NDK_rate(io, POINTER(fn_setRate), POINTER(g_eq), POINTER(g_back_sensor), 1000000);
    }
    SET_INT(g_sensor_rate_us, 1000000);
}
#ifndef IOS_PRODUCTION
void ios_set_light_sensors_enabled(IosCpu *caller) {
    DomainIo io = {0};
    ios_light_sensors_set(&io, (uint32_t)caller->x[0]);
}
#endif
