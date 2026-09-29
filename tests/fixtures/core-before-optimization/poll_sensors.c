/* ASensorEvent layout from the ARM64 Android ABI. */
#include "business_api.h"
typedef struct SensorEvent {
    int32_t version, sensor, type, reserved;
    int64_t timestamp_ns;
    float data[16];
    uint8_t tail[16];
} SensorEvent;
_Static_assert(sizeof(SensorEvent) == 104, "Android sensor-event size");
_Static_assert(offsetof(SensorEvent, data) == 24, "Android sensor-event data offset");
enum {
    SENSOR_LIGHT = 5,
    SENSOR_PROXIMITY = 8,
    SENSOR_BACK_LIGHT = 0x1fa266f,
    MAX_EVENTS_PER_POLL = 16
};
static bool valid_light_event(float lux, int64_t timestamp, int64_t last_timestamp) {
    return isfinite(lux) && lux >= 0 && lux <= 200000.0f &&
           !(timestamp > 0 && timestamp <= last_timestamp);
}
unsigned ios_sensor_poll(DomainIo *io) {
    uintptr_t queue = POINTER(g_eq), get_events = POINTER(fn_getEvents);
    if (FLAG(g_ndk_ok) != 1 || !queue || !get_events)
        return 0;
    unsigned processed = 0;
    for (; processed < MAX_EVENTS_PER_POLL; processed++) {
        SensorEvent event = {0};
        if ((int32_t)NDK_events(io, get_events, queue, ARG(&event), 1) < 1)
            break;
        float value = event.data[0];
        if (event.type == SENSOR_PROXIMITY) {
            SET_INT(g_proximity_near, value == 0);
            continue;
        }
        if (event.type != SENSOR_LIGHT && event.type != SENSOR_BACK_LIGHT)
            continue;
        bool front = event.type == SENSOR_LIGHT;
        int64_t last =
            (int64_t)(front ? TIME(g_front_sensor_timestamp_ns) : TIME(g_back_sensor_timestamp_ns));
        if (!valid_light_event(value, event.timestamp_ns, last)) {
            SET_INT(g_sensor_nan_count, (uint32_t)INT(g_sensor_nan_count) + 1);
            continue;
        }
        if (front) {
            SET_FLOAT(g_front_lux, value);
            SET_TIME(g_front_sensor_timestamp_ns, event.timestamp_ns);
            SET_TIME(g_front_lux_event_ms, domain_now_ms(io));
        } else {
            SET_FLOAT(g_back_lux, value);
            SET_TIME(g_back_sensor_timestamp_ns, event.timestamp_ns);
            SET_TIME(g_back_lux_event_ms, domain_now_ms(io));
        }
    }
    return processed;
}
#ifndef IOS_PRODUCTION
void ios_poll_sensors(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_sensor_poll(&io);
}
#endif
