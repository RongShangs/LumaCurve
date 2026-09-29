/* One bounded, read-only discovery policy for installation and runtime. */
#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

typedef struct LumaBacklight {
    char brightness[384], maximum[384], power[384];
} LumaBacklight;
typedef struct LumaBacklightOps {
    void *context;
    int (*integer)(void *, const char *, int *);
    void *(*directory)(void *, const char *);
    const char *(*entry)(void *, void *);
    void (*close_directory)(void *, void *);
} LumaBacklightOps;
static inline bool luma_backlight_candidate(LumaBacklight *out, const char *name,
                                           const LumaBacklightOps *ops) {
    if (!*name || strchr(name, '/') || name[0] == '.') return false;
    LumaBacklight candidate = {0};
    int n = snprintf(candidate.brightness, sizeof(candidate.brightness),
                     "/sys/class/backlight/%s/brightness", name);
    if (n < 0 || (size_t)n >= sizeof(candidate.brightness)) return false;
    n = snprintf(candidate.maximum, sizeof(candidate.maximum),
             "/sys/class/backlight/%s/max_brightness", name);
    if (n < 0 || (size_t)n >= sizeof(candidate.maximum)) return false;
    n = snprintf(candidate.power, sizeof(candidate.power),
             "/sys/class/backlight/%s/bl_power", name);
    if (n < 0 || (size_t)n >= sizeof(candidate.power)) return false;
    int maximum = 0, current = -1;
    if (!ops->integer(ops->context, candidate.maximum, &maximum) || maximum < 1 ||
        !ops->integer(ops->context, candidate.brightness, &current) ||
        current < 0 || current > maximum) return false;
    *out = candidate;
    return true;
}
static inline bool luma_backlight_select(LumaBacklight *out, const LumaBacklightOps *ops) {
    memset(out, 0, sizeof(*out));
    if (luma_backlight_candidate(out, "panel0-backlight", ops)) return true;
    void *directory = ops->directory(ops->context, "/sys/class/backlight");
    if (!directory) return false;
    unsigned count = 0, found = 0;
    bool exhausted = false;
    const char *name;
    LumaBacklight candidate;
    while (count++ < 256) {
        name = ops->entry(ops->context, directory);
        if (!name) { exhausted = true; break; }
        if (luma_backlight_candidate(&candidate, name, ops)) {
            *out = candidate;
            if (++found > 1) break; /* Never guess between two usable panels. */
        }
    }
    ops->close_directory(ops->context, directory);
    if (found == 1 && exhausted) return true;
    memset(out, 0, sizeof(*out));
    return false;
}

/* Runtime state is separate from the recovered ABI and reset with the daemon. */
struct DomainIo;
void ios_runtime_io_reset(void);
bool ios_backlight_initialize(struct DomainIo *io);
const char *ios_backlight_brightness(void);
const char *ios_backlight_maximum(void);
const char *ios_backlight_power(void);
