#include "business_api.h"
float ios_fallback_lux_read(DomainIo *io, uint64_t *legacy_result) {
    float lux = -1;
    if (*STRING(g_sensor_path)) {
        uintptr_t stream = domain_open(io, STRING(g_sensor_path), "r");
        *legacy_result = stream;
        if (stream) {
            if (IOS_SCAN(io, stream, "%f", &lux) != 1)
                lux = -1;
            *legacy_result = CALL(io, fclose, stream);
            if (lux >= 0) {
                int64_t stamp[2];
                *legacy_result = CALL(io, clock_gettime, 1, ARG(stamp));
                SET_FLAG(g_lux_valid, 1);
                memset(STRING(g_lux_source), 0, 16);
                memcpy(STRING(g_lux_source), "sysfs", 5);
                SET_FLAG(g_sensor_hold_active, 0);
                SET_FLAG(g_sensor_stale, 0);
                SET_FLAG(g_sensor_hold_timeout, 0);
                SET_TIME(g_sensor_hold_since_ms, 0);
                STRING(g_sensor_stale_source)[0] = 0;
                SET_TIME(g_last_lux_event_ms,
                         (uint64_t)stamp[0] * 1000 + (uint64_t)stamp[1] / 1000000);
            }
        }
    }
    return lux;
}

#ifndef IOS_PRODUCTION
void ios_read_lux_fallback(IosCpu *caller) {
    DomainIo io = {0};
    float lux = ios_fallback_lux_read(&io, &caller->x[0]);
    ios_f32(caller, 0, lux);
}
#endif
