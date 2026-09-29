#include "business_api.h"
uint64_t ios_battery_refresh(DomainIo *io, uint64_t now) {
    uint64_t last = TIME(g_last_battery_read_ms);
    if (last && (now - last) < 15000)
        return now;
    SET_TIME(g_last_battery_read_ms, now);
    int capacity = INT(g_cached_battery_cap);
    uintptr_t stream = domain_open(io, "/sys/class/power_supply/battery/capacity", "r");
    if (stream) {
        if (IOS_SCAN(io, stream, "%d", &capacity) != 1)
            capacity = INT(g_cached_battery_cap);
        CALL(io, fclose, stream);
    }
    if (capacity > 100)
        capacity = 100;
    if (capacity < 0)
        capacity = 0;
    SET_INT(g_cached_battery_cap, capacity);
    stream = domain_open(io, "/sys/class/power_supply/battery/status", "r");
    if (!stream)
        return 0;
    char status[32] = {0};
    if (CALL(io, fgets, ARG(status), sizeof(status), stream))
        SET_INT(g_cached_charging,
                strstr(status, "Charging") != NULL || strstr(status, "Full") != NULL);
    return (uint32_t)CALL(io, fclose, stream);
}
#ifndef IOS_PRODUCTION
void ios_refresh_battery_state(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_battery_refresh(&io, caller->x[0]);
}
#endif
