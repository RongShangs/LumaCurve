/* Target selection restored from the complete daemon flow, not a guessed helper. */
#include "business_api.h"
#include "state.h"

static int32_t step_threshold(float maximum, float fraction, int32_t minimum) {
    int32_t scaled = (int32_t)(fmaf(maximum, fraction, 0.5f));
    return scaled < minimum ? minimum : scaled;
}

int32_t ios_business_debounce(IosState *s, uint64_t now_ms, int32_t requested,
                              int32_t current, float lux, bool fast_dark) {
    int32_t candidate = requested;
    int32_t direction = 0;
    uint64_t since = now_ms;
    bool bypass = fast_dark || !(s->g_target_debounce_init & 1) ||
        now_ms < s->g_dark_drop_until || (s->hl_active & 1) ||
        (s->g_heat_guard_active & 1) ||
        (requested < current && now_ms < s->g_wake_guard_until) ||
        (current < requested && now_ms < s->g_low_lux_rise_confirmed_until);
    if (bypass) {
        s->g_target_debounce_init = 1;
        s->g_target_debounce_br = requested;
    } else {
        float maximum = (float)s->g_max;
        int32_t difference = requested - s->g_target_debounce_br;
        if (difference < 0) difference = -difference;
        int32_t denominator = s->g_target_debounce_br < 2 ? 1 : s->g_target_debounce_br;
        bool significant = step_threshold(maximum, 0.005f, s->cfg_min_step) < difference ||
            0.015f < (float)difference / (float)denominator;
        bool tiny_dark = lux < 3.0f &&
            difference <= step_threshold(maximum, 0.00075f, s->cfg_min_step);
        if (!significant || tiny_dark) {
            candidate = s->g_target_debounce_br;
        } else {
            direction = requested <= s->g_target_debounce_br ? -1 : 1;
            float fraction = direction < 0 ? 0.04f : lux < 5.0f ? 0.012f : 0.045f;
            int32_t maximum_step = step_threshold(maximum, fraction, s->cfg_min_step);
            if (maximum_step < difference)
                candidate = s->g_target_debounce_br + direction * maximum_step;
            if (s->g_target_candidate_dir == direction) {
                int32_t movement = candidate - s->g_target_candidate_br;
                if (movement < 0) movement = -movement;
                if (movement <= step_threshold(maximum, 0.003f, s->cfg_min_step)) {
                    uint64_t hold_ms = lux >= 5.0f ? 1800 : direction > 0 ? 3000 : 1200;
                    direction = s->g_target_candidate_dir;
                    since = s->g_target_candidate_since;
                    if (hold_ms <= now_ms - since) {
                        direction = 0;
                        s->g_target_debounce_br = candidate;
                        since = now_ms;
                    }
                }
            }
        }
    }
    s->g_target_candidate_since = since;
    s->g_target_candidate_br = candidate;
    s->g_target_candidate_dir = direction;
    return s->g_target_debounce_br;
}
