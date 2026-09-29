#include "business_api.h"
uint32_t ios_display_refresh(DomainIo *io) {
    uintptr_t stream =
        (uintptr_t)CALL(io, popen, ARG("dumpsys display 2>/dev/null | head -5"), ARG("r"));
    if (!stream)
        return 0;
    char line[256];
    while (CALL(io, fgets, ARG(line), sizeof(line), stream)) {
        char *state = strstr(line, "state=");
        if (!state)
            continue;
        state += 6;
        char *end = strchr(state, ',');
        if (!end)
            end = strchr(state, '\n');
        if (end)
            *end = 0;
        CALL(io, strncpy, ARG(STRING(g_last_display_state)), ARG(state), 23);
    }
    return (uint32_t)CALL(io, pclose, stream);
}

#ifndef IOS_PRODUCTION
void ios_refresh_display_state_on_edge(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_display_refresh(&io);
}
#endif
