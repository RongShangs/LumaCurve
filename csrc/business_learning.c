/* The original ledger weighting, including its 0.25 minimum denominator. */
#include "business_api.h"
#include "state.h"

int32_t ios_business_learning(IosState *s, DomainIo *io, float lux, int32_t base) {
    int64_t stamp = (int64_t)CALL(io, time, 0);
    const int32_t *calendar = (const int32_t *)(uintptr_t)CALL(io, localtime, ARG(&stamp));
    int32_t period = calendar[2] / 6;
    if (period > 2) period = 3;
    if (period < 0) period = 0;
    int64_t now = (int64_t)CALL(io, time, 0);
    float log_lux = log10f(lux);
    if (lux <= 0.0f) log_lux = 0.0f;
    float time_sigma_squared = s->cfg_learn_time_neighbor * s->cfg_learn_time_neighbor;
    float curve_sigma_squared = s->cfg_learn_curve_sigma * s->cfg_learn_curve_sigma;
    float total_weight = 0.0f, weighted_offset = 0.0f;
    for (int bucket = 0; bucket < LEARN_LUX_BUCKETS; bucket++) {
        float anchor = powf(10.0f, (float)bucket / 2.0f - 1.0f);
        float log_anchor = log10f(anchor);
        if (anchor <= 0.0f) log_anchor = 0.0f;
        float distance = fabsf(log_lux - log_anchor);
        float lux_weight = expf((distance * distance * -0.5f) / curve_sigma_squared);
        for (int neighbor = 0; neighbor < LEARN_TIME_BUCKETS; neighbor++) {
            const LearnCell *cell = &s->g_learn[bucket][neighbor];
            if (cell->confidence < 0.025f) continue;
            int delta = neighbor - period;
            if (delta < 0) delta = -delta;
            if (delta >= 3) delta = 4 - delta;
            float time_weight = expf(((float)(delta * delta) * -0.5f) /
                                     (time_sigma_squared * 4.0f * 4.0f));
            float age_weight = cell->timestamp < 1 ? 0.0f :
                expf(((float)(now - cell->timestamp) / -86400.0f) / s->cfg_learn_decay_days);
            float weight = lux_weight * cell->confidence * time_weight * age_weight;
            total_weight = total_weight + weight;
            weighted_offset = fmaf(weight, ((float)(cell->slider - 128) / 255.0f), weighted_offset);
        }
    }
    float offset = fmaf(s->g_global_offset, s->cfg_learn_strength,
        (weighted_offset / fmaxf(total_weight, 0.25f)) * s->cfg_learn_max_offset * s->cfg_learn_strength);
    float upper = s->cfg_learn_max_offset;
    if (offset <= upper) upper = offset;
    float bounded = -s->cfg_learn_max_offset;
    if (bounded <= offset) bounded = upper;
    float multiplier = bounded + 1.0f;
    upper = 1.7f;
    if (multiplier <= upper) upper = multiplier;
    bounded = 0.3f;
    if (bounded <= multiplier) bounded = upper;
    int32_t adjusted = (int32_t)(fmaf(bounded, (float)base, 0.5f));
    if (s->g_max <= adjusted) return s->g_max;
    if (adjusted < 1) return 1;
    return adjusted;
}
