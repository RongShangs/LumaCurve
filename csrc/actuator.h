#pragma once
#include "domain_io.h"
static inline float bounded_speed(float value) {
    if (value > 2.5f)
        value = 2.5f;
    if (value < .5f)
        value = .5f;
    return value;
}
static inline float brighten_rate(void) {
    bool sunlight = FLAG(hl_active) != 0;
    return fminf((sunlight ? .18f : .06f) * bounded_speed(FLOAT(cfg_brighten_speed)),
                 sunlight ? .24f : .15f);
}
static inline float darken_rate(DomainIo *io) {
    float rate;
    if (FLAG(g_zero_lux_suspect) & 1)
        rate = .028f;
    else if (INT(g_actuator_fast_dark))
        rate = .07f;
    else
        rate = FLOAT(g_actuator_smooth_lux) <= 5 ? .028f : .05f;
    float boosted = rate * 2.4f;
    if (boosted <= .14f)
        boosted = .14f;
    if (boosted > .16f)
        boosted = .16f;
    if (domain_now_ms(io) < TIME(g_dark_drop_until))
        rate = boosted;
    return fminf(rate * bounded_speed(FLOAT(cfg_darken_speed)), .18f);
}
