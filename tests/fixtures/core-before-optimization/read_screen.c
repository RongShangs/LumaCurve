/* Screen power read — typed core + IosCpu adapter. */
#include "domain_io.h"

static bool read_screen_power(DomainIo *io) {
    for (unsigned attempt = 0; attempt < 2; attempt++) {
        uintptr_t stream = domain_open(io, "/sys/class/backlight/panel0-backlight/bl_power", "r");
        if (!stream)
            continue;
        int power = -1;
        int parsed = (int)IOS_SCAN(io, stream, "%d", &power);
        CALL(io, fclose, stream);
        if (parsed == 1)
            return power == 0;
    }
    return true;
}

bool ios_read_screen_on(DomainIo *io) {
    return read_screen_power(io);
}

#ifndef IOS_PRODUCTION
void ios_read_screen(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = read_screen_power(&io);
}
#endif
