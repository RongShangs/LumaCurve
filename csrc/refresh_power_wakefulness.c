#include "business_api.h"
uint32_t ios_wakefulness_refresh(DomainIo *io) {
    uintptr_t stream = (uintptr_t)CALL(
        io, popen, ARG("dumpsys power 2>/dev/null | grep 'mWakefulness='"), ARG("r"));
    if (!stream)
        return 0;
    char line[128];
    if (CALL(io, fgets, ARG(line), sizeof(line), stream)) {
        char *wakefulness = strstr(line, "mWakefulness=");
        if (wakefulness) {
            wakefulness += 13;
            char *newline = strchr(wakefulness, '\n');
            if (newline)
                *newline = 0;
            CALL(io, strncpy, ARG(STRING(g_power_wakefulness)), ARG(wakefulness), 15);
            SET_INT(g_power_interactive, !strcmp(STRING(g_power_wakefulness), "Awake"));
        }
    }
    return (uint32_t)CALL(io, pclose, stream);
}

#ifndef IOS_PRODUCTION
void ios_refresh_power_wakefulness(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_wakefulness_refresh(&io);
}
#endif
