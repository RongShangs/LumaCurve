/* Screen power read — typed core + IosCpu adapter. */
#include "domain_io.h"
#include "backlight_paths.h"

static LumaBacklight backlight = {
    .brightness = "/sys/class/backlight/panel0-backlight/brightness",
    .maximum = "/sys/class/backlight/panel0-backlight/max_brightness",
    .power = "/sys/class/backlight/panel0-backlight/bl_power"
};
static int last_screen = -1;
static uint64_t last_power_probe;
static bool power_probe_attempted;

void ios_runtime_io_reset(void) {
    strcpy(backlight.brightness, "/sys/class/backlight/panel0-backlight/brightness");
    strcpy(backlight.maximum, "/sys/class/backlight/panel0-backlight/max_brightness");
    strcpy(backlight.power, "/sys/class/backlight/panel0-backlight/bl_power");
    last_screen = -1;
    last_power_probe = 0;
    power_probe_attempted = false;
}
static int read_integer(void *context, const char *path, int *value) {
    DomainIo *io = context;
    uintptr_t stream = domain_open(io, path, "r");
    if (!stream) return 0;
    int result = (int)IOS_SCAN(io, stream, "%d", value);
    int closed = (int)CALL(io, fclose, stream);
    return result == 1 && closed == 0;
}
static void *read_directory(void *context, const char *path) {
    return (void *)(uintptr_t)CALL((DomainIo *)context, opendir, ARG(path));
}
static const char *read_entry(void *context, void *directory) {
    uintptr_t entry = CALL((DomainIo *)context, readdir, ARG(directory));
    return entry ? (const char *)(entry + 19) : NULL;
}
static void close_directory(void *context, void *directory) {
    CALL((DomainIo *)context, closedir, ARG(directory));
}
bool ios_backlight_initialize(DomainIo *io) {
    LumaBacklightOps ops = {io, read_integer, read_directory, read_entry, close_directory};
    return luma_backlight_select(&backlight, &ops);
}
const char *ios_backlight_brightness(void) { return backlight.brightness; }
const char *ios_backlight_maximum(void) { return backlight.maximum; }
const char *ios_backlight_power(void) { return backlight.power; }

/* Query only when the cheap node fails, with a bounded command and retry interval.
 * Default state strings are not evidence: only a successfully closed fresh read
 * may update this cache. A cold unknown leaves brightness with the system. */
static void read_power_fallback(DomainIo *io) {
    int64_t stamp[2] = {0};
    if ((int64_t)CALL(io, clock_gettime, 1, ARG(stamp)) != 0 ||
        stamp[0] < 0 || stamp[1] < 0 || stamp[1] >= 1000000000 ||
        (uint64_t)stamp[0] > UINT64_MAX / 1000 - 1000) return;
    uint64_t now = (uint64_t)stamp[0] * 1000 + (uint64_t)stamp[1] / 1000000;
    if (power_probe_attempted && now >= last_power_probe && now - last_power_probe < 5000) return;
    power_probe_attempted = true;
    last_power_probe = now;
    uintptr_t stream = CALL(io, popen,
        ARG("timeout 2 dumpsys power 2>/dev/null | grep -m1 'mWakefulness='"), ARG("r"));
    if (!stream) return;
    char line[192] = {0};
    int screen = -1;
    const char *state = NULL;
    if (CALL(io, fgets, ARG(line), sizeof(line), stream)) {
        char *start = strstr(line, "mWakefulness=");
        if (start) {
            start += 13;
            size_t length = strcspn(start, " \t\r\n");
            if (length == 5 && !strncmp(start, "Awake", length)) { screen = 1; state = "Awake"; }
            else if (length == 6 && !strncmp(start, "Asleep", length)) { screen = 0; state = "Asleep"; }
            else if (length == 6 && !strncmp(start, "Dozing", length)) { screen = 0; state = "Dozing"; }
        }
    }
    int result = (int)CALL(io, pclose, stream);
    if (!result && screen >= 0) {
        last_screen = screen;
        SET_INT(g_power_interactive, screen);
        strcpy(STRING(g_power_wakefulness), state);
    }
}

static bool read_screen_power(DomainIo *io) {
    for (unsigned attempt = 0; attempt < 2; attempt++) {
        uintptr_t stream = domain_open(io, ios_backlight_power(), "r");
        if (!stream)
            continue;
        int power = -1;
        int parsed = (int)IOS_SCAN(io, stream, "%d", &power);
        int closed = (int)CALL(io, fclose, stream);
        if (parsed == 1 && closed == 0 && power >= 0 && power <= 4) {
            last_screen = power == 0;
            return last_screen != 0;
        }
    }
    read_power_fallback(io);
    return last_screen == 1;
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
