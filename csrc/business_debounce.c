/* Target selection restored from the complete daemon flow, not a guessed helper. */
#include "business_api.h"
#include "state.h"
#include "temporal_stability.h"
#include "scene_adaptation.h"

int luma_indoor_stability = 1;
enum SceneKind { SCENE_SINGLE, SCENE_STABLE, SCENE_COMMON_RISE, SCENE_COMMON_FALL,
                 SCENE_FRONT_ONLY, SCENE_BACK_ONLY, SCENE_DIVERGENT };
typedef struct SceneSide {
    float value, anchor;
    int64_t timestamp, onset_ns;
    uint64_t arrival, onset, sequence;
    int direction, mode;
} SceneSide;
static struct {
    SceneSide sides[2]; /* front=0, back=1; each has its own scale/reference. */
    enum SceneKind kind;
    float accepted_front;
    bool initialized, holding;
    int pending_direction, common_direction, front_confirmed_direction;
    uint64_t since, first_sequence, deadline, common_until, front_confirmed_until;
    uint64_t smooth_event, smooth_ms;
    unsigned samples;
} scene;
void ios_scene_reset(bool reset_modes) {
    int front = scene.sides[0].mode, back = scene.sides[1].mode;
    memset(&scene, 0, sizeof(scene));
    scene.sides[0].mode = reset_modes ? -1 : front;
    scene.sides[1].mode = reset_modes ? -1 : back;
}
void ios_scene_mode(bool front, int mode) {
    scene.sides[front ? 0 : 1].mode = mode >= 0 && mode <= 3 ? mode : -1;
}
int ios_scene_reporting_mode(bool front) { return scene.sides[front ? 0 : 1].mode; }
static int direction_at(float value, float anchor, float threshold) {
    /* Absolute noise floor plus a multiplicative threshold; no raw front/back ratio. */
    if (fabsf(value - anchor) < .5f) return 0;
    float change = log1pf(value) - log1pf(anchor);
    return change > threshold ? 1 : change < -threshold ? -1 : 0;
}
static int scene_direction(float value, float anchor) { return direction_at(value, anchor, .18232156f); }
void ios_scene_observe(bool front, float lux, int64_t sensor_ns, uint64_t arrival_ms) {
    SceneSide *side = &scene.sides[front ? 0 : 1];
    if (!isfinite(lux) || lux < 0 || lux > 200000 || !arrival_ms || sensor_ns <= 0 ||
        (side->timestamp && sensor_ns <= side->timestamp)) return;
    if (side->arrival > arrival_ms) {
        ios_scene_reset(false);
        side = &scene.sides[front ? 0 : 1];
    }
    if (!side->arrival) side->anchor = lux;
    int direction = scene_direction(lux, side->anchor);
    if (direction != side->direction) {
        side->onset = direction ? arrival_ms : 0;
        side->onset_ns = direction ? sensor_ns : 0;
    }
    side->direction = direction;
    side->value = lux;
    side->timestamp = sensor_ns;
    side->arrival = arrival_ms;
    side->sequence++;
}
bool ios_scene_sensor_fresh(const IosState *s, bool front, uint64_t now) {
    uint64_t event = front ? s->g_front_lux_event_ms : s->g_back_lux_event_ms;
    float lux = front ? s->g_front_lux : s->g_back_lux;
    if (!event || event > now || !isfinite(lux) || lux < 0 || lux > 200000) return false;
    /* Only a confirmed NDK reporting mode allows a quiet sample to remain valid.
       Timestamps and sample counters are never refreshed by this check. */
    return now - event <= 8000 || (luma_indoor_stability && s->g_light_sensors_enabled &&
        (front ? s->g_front_sensor != NULL && s->g_front_sensor_timestamp_ns > 0 :
                 s->g_back_ok == 1 && s->g_back_sensor != NULL && s->g_back_sensor_timestamp_ns > 0) &&
        ios_scene_reporting_mode(front) == 1);
}
bool ios_scene_selected_fresh(const IosState *s, uint64_t now) {
    if (!strcmp(s->g_lux_source, "front")) return ios_scene_sensor_fresh(s, true, now);
    if (!strcmp(s->g_lux_source, "back")) return ios_scene_sensor_fresh(s, false, now);
    return s->g_last_lux_event_ms && s->g_last_lux_event_ms <= now &&
           now - s->g_last_lux_event_ms <= 8000;
}
bool ios_scene_back_confirmed(const IosState *s, uint64_t now) {
    return s->g_back_fallback_confirm_count >= 2 || (luma_indoor_stability &&
        ios_scene_reporting_mode(false) == 1 && s->g_back_fallback_confirm_count >= 1 &&
        ios_scene_sensor_fresh(s, false, now) && now - s->g_back_lux_event_ms >= 1000);
}
static enum SceneKind scene_classify(const IosState *s, uint64_t now) {
    SceneSide *front = &scene.sides[0], *back = &scene.sides[1];
    if (!front->arrival || !back->arrival || !ios_scene_sensor_fresh(s, true, now) ||
        !ios_scene_sensor_fresh(s, false, now) || s->g_back_ok != 1) return SCENE_SINGLE;
    if (!front->direction) return back->direction ? SCENE_BACK_ONLY : SCENE_STABLE;
    if (!back->direction) return SCENE_FRONT_ONLY;
    uint64_t onset_gap = front->onset_ns > back->onset_ns ?
        (uint64_t)(front->onset_ns - back->onset_ns) : (uint64_t)(back->onset_ns - front->onset_ns);
    uint64_t sample_gap = front->timestamp > back->timestamp ?
        (uint64_t)(front->timestamp - back->timestamp) : (uint64_t)(back->timestamp - front->timestamp);
    if (onset_gap > UINT64_C(1500000000) || sample_gap > UINT64_C(1500000000)) return SCENE_FRONT_ONLY;
    if (front->direction != back->direction) return SCENE_DIVERGENT;
    float a = fabsf(log1pf(front->value) - log1pf(front->anchor));
    float b = fabsf(log1pf(back->value) - log1pf(back->anchor));
    if (fmaxf(a, b) > 4 * fminf(a, b)) return SCENE_FRONT_ONLY;
    return front->direction > 0 ? SCENE_COMMON_RISE : SCENE_COMMON_FALL;
}
const char *ios_scene_class(void) {
    static const char *const names[] = {"single", "stable", "common_rise", "common_fall",
                                       "front_only", "back_only", "divergent"};
    return names[scene.kind];
}
uint64_t ios_scene_hold_left(uint64_t now) {
    return scene.holding && scene.deadline > now ? scene.deadline - now : 0;
}
unsigned ios_scene_samples(void) { return scene.samples; }
bool ios_scene_holding(void) { return scene.holding; }
bool ios_scene_filter(IosState *s, uint64_t now, float *lux) {
    /* A config reload clears history, but an on-change sensor may remain quiet.
       Seed from real cached events; preserve their original times and counts. */
    if (!scene.sides[0].arrival && ios_scene_sensor_fresh(s, true, now))
        ios_scene_observe(true, s->g_front_lux, s->g_front_sensor_timestamp_ns, s->g_front_lux_event_ms);
    if (!scene.sides[1].arrival && ios_scene_sensor_fresh(s, false, now))
        ios_scene_observe(false, s->g_back_lux, s->g_back_sensor_timestamp_ns, s->g_back_lux_event_ms);
    scene.kind = scene_classify(s, now);
    if (!luma_indoor_stability || strcmp(s->g_lux_source, "front") || !isfinite(*lux) ||
        strcmp(s->g_brightness_owner, "daemon") || s->g_wake_readonly_until > now ||
        !ios_scene_sensor_fresh(s, true, now)) {
        scene.holding = false;
        scene.initialized = false;
        return false;
    }
    if (!scene.initialized) {
        scene.accepted_front = *lux;
        scene.initialized = true;
        scene.sides[0].anchor = *lux;
        scene.sides[0].direction = 0;
        scene.sides[1].anchor = scene.sides[1].value;
        scene.sides[1].direction = 0;
        scene.kind = scene_classify(s, now);
    }
    bool common = scene.kind == SCENE_COMMON_RISE || scene.kind == SCENE_COMMON_FALL;
    int direction = direction_at(*lux, scene.accepted_front, .09531018f);
    if (!direction || scene.kind == SCENE_SINGLE) {
        scene.holding = false;
        scene.pending_direction = 0;
        scene.samples = 0;
        if (!direction) *lux = scene.accepted_front; /* Tiny jitter never enters fast-dark/EMA. */
        else {
            scene.accepted_front = *lux;
            scene.sides[0].anchor = *lux;
            scene.sides[0].direction = 0;
        }
        return false;
    }
    if (common) {
        scene.common_until = now + 4000;
        scene.common_direction = direction;
        scene.samples = 0;
    } else {
        SceneSide *front = &scene.sides[0];
        if (!scene.holding || direction != scene.pending_direction || now < scene.since) {
            scene.since = now;
            scene.first_sequence = front->sequence;
            scene.pending_direction = direction;
            scene.holding = true;
            scene.deadline = now + (direction > 0 ? 2000 : 3000);
        }
        uint64_t samples = front->sequence - scene.first_sequence + 1;
        scene.samples = samples > UINT32_MAX ? UINT32_MAX : (unsigned)samples;
        bool enough_samples = scene.samples >= 2 || front->mode == 1;
        if (now < scene.deadline || !enough_samples) { *lux = scene.accepted_front; return true; }
        scene.front_confirmed_until = now + 4000;
        scene.front_confirmed_direction = direction;
    }
    scene.accepted_front = *lux;
    scene.holding = false;
    scene.pending_direction = 0;
    scene.sides[0].anchor = *lux;
    scene.sides[0].direction = 0;
    scene.sides[1].anchor = scene.sides[1].value;
    scene.sides[1].direction = 0;
    return false;
}
float ios_scene_smooth_tick(IosState *s, uint64_t now, float smooth, float *raw) {
    bool front = !strcmp(s->g_lux_source, "front");
    bool back = !strcmp(s->g_lux_source, "back");
    uint64_t previous = scene.smooth_ms;
    scene.smooth_ms = now;
    if (!luma_indoor_stability || (!front && !back) || ios_scene_reporting_mode(front) != 1 ||
        !ios_scene_selected_fresh(s, now) || !s->g_lux_valid || s->g_sensor_stale ||
        s->g_sensor_hold_active || scene.holding || strcmp(s->g_brightness_owner, "daemon") ||
        s->g_wake_readonly_until > now || !isfinite(smooth) || smooth < 0) return smooth;
    uint64_t event = s->g_last_lux_event_ms;
    if (!previous || previous >= now || event != scene.smooth_event ||
        event != s->g_last_processed_lux_event_ms) {
        scene.smooth_event = event;
        return smooth; /* New events already ran the original filter. */
    }
    if (front && !scene.initialized) return smooth;
    float retained = front ? scene.accepted_front : s->g_back_lux;
    if (s->g_zero_lux_suspect) {
        if (retained > .01f || !s->g_zero_lux_since || now < s->g_zero_lux_since ||
            now - s->g_zero_lux_since < 3500) return smooth;
        s->g_zero_lux_suspect = 0;
        *raw = retained; /* Time confirmation; leave the real zero-sample mask intact. */
    }
    if (s->g_low_lux_bright_spike_guard) {
        if (!s->g_low_lux_bright_spike_since || now < s->g_low_lux_bright_spike_since ||
            now - s->g_low_lux_bright_spike_since < 6000 ||
            fabsf(retained - s->g_low_lux_bright_spike_raw) >
                fmaxf(.5f, .05f * s->g_low_lux_bright_spike_raw)) return smooth;
        int actual_samples = s->g_low_lux_bright_spike_count;
        s->g_low_lux_bright_spike_guard = 0;
        s->g_low_lux_bright_spike_since = 0;
        s->g_low_lux_bright_spike_count = 0;
        s->g_low_lux_rise_confirmed_samples = actual_samples;
        s->g_low_lux_rise_confirmed_until = now + 6000;
        *raw = retained; /* Existing limited-rise cap still applies downstream. */
    }
    if (!isfinite(*raw) || *raw < 0 || *raw > 200000) return smooth;
    float relative = fabsf(*raw - smooth) / fmaxf(1, fminf(*raw, smooth) + 1);
    float alpha = relative > s->cfg_fast_thresh ? s->cfg_alpha_fast :
                  relative > s->cfg_slow_thresh ? s->cfg_alpha_mid : s->cfg_alpha_slow;
    if (s->g_actuator_fast_dark && alpha < .7f) alpha = .7f;
    if (!isfinite(alpha) || alpha <= 0 || alpha > 1) return smooth;
    uint64_t elapsed = now - previous;
    if (elapsed > 10000) elapsed = 10000;
    float weight = alpha == 1 ? 1 : -expm1f(log1pf(-alpha) * ((float)elapsed / 1000));
    float next = fmaf(weight, *raw - smooth, smooth);
    if (fabsf(next - *raw) <= fmaxf(.05f, .005f * *raw)) next = *raw;
    s->g_actuator_smooth_lux = next;
    return next;
}
static struct {
    uint64_t event, since, last_now;
    int32_t anchor, samples[7];
    unsigned count, next;
    int direction;
} temporal;
void ios_temporal_reset(void) {
    memset(&temporal, 0, sizeof(temporal));
}
int32_t ios_temporal_target(IosState *s, uint64_t now, int32_t target,
                            float lux, bool bypass) {
    int32_t held = s->g_target_debounce_br;
    if (!luma_indoor_stability || bypass || !isfinite(lux) || lux < 0 ||
        !s->g_lux_valid || s->g_sensor_stale || s->g_sensor_hold_active ||
        strcmp(s->g_brightness_owner, "daemon") || s->g_wake_readonly_until > now) {
        ios_temporal_reset();
        return target;
    }
    int64_t delta = (int64_t)target - held;
    int64_t difference = delta < 0 ? -delta : delta;
    int floor = s->cfg_min_step > 1 ? s->cfg_min_step : 1;
    int deadband = (int)fmaf((float)held, .05f, .5f);
    if (deadband < floor) deadband = floor;
    if (difference <= deadband) {
        ios_temporal_reset();
        return held;
    }
    /* Obvious moves retain the existing fast-path and protection semantics. */
    bool paired = now < scene.common_until && (delta > 0 ? 1 : -1) == scene.common_direction;
    bool confirmed_front = now < scene.front_confirmed_until &&
        (delta > 0 ? 1 : -1) == scene.front_confirmed_direction;
    bool no_scene_transition = scene.kind == SCENE_SINGLE || scene.kind == SCENE_STABLE || scene.kind == SCENE_BACK_ONLY;
    if ((difference > (int64_t)held / 2 && no_scene_transition) || held < 1) {
        ios_temporal_reset();
        return target;
    }
    int direction = delta > 0 ? 1 : -1;
    uint64_t event = s->g_last_lux_event_ms;
    if (!event || event > now || !ios_scene_selected_fresh(s, now)) {
        ios_temporal_reset();
        return held;
    }
    if (temporal.anchor != held || temporal.direction != direction ||
        now < temporal.last_now || event < temporal.event ||
        (temporal.event && event - temporal.event > 6000)) {
        ios_temporal_reset();
        temporal.anchor = held;
        temporal.direction = direction;
        temporal.since = event;
    }
    temporal.last_now = now;
    if (event != temporal.event) {
        temporal.event = event;
        temporal.samples[temporal.next] = target;
        temporal.next = (temporal.next + 1) % 7;
        if (temporal.count < 7) temporal.count++;
    }
    uint64_t confirm = paired || confirmed_front ? 1000 : direction > 0 ? 3000 : 4000;
    bool on_change = !strcmp(s->g_lux_source, "front") && scene.sides[0].mode == 1;
    unsigned minimum = on_change ? 1 : paired || confirmed_front ? 2 : 3;
    uint64_t elapsed = on_change ? now - temporal.since : event - temporal.since;
    if (temporal.count < minimum || elapsed < confirm) return held;
    int32_t sorted[7];
    memcpy(sorted, temporal.samples, temporal.count * sizeof(*sorted));
    for (unsigned i = 1; i < temporal.count; i++) {
        int32_t value = sorted[i]; unsigned j = i;
        while (j && sorted[j-1] > value) { sorted[j] = sorted[j-1]; j--; }
        sorted[j] = value;
    }
    return sorted[temporal.count / 2];
}

static int32_t step_threshold(float maximum, float fraction, int32_t minimum) {
    int32_t scaled = (int32_t)(fmaf(maximum, fraction, 0.5f));
    return scaled < minimum ? minimum : scaled;
}

int32_t ios_business_debounce(IosState *s, uint64_t now_ms, int32_t requested,
                              int32_t current, float lux, bool fast_dark) {
    int32_t candidate;
    int32_t direction = 0;
    uint64_t since = now_ms;
    bool bypass = fast_dark || !(s->g_target_debounce_init & 1) ||
        now_ms < s->g_dark_drop_until || (s->hl_active & 1) ||
        (s->g_heat_guard_active & 1) ||
        (requested < current && now_ms < s->g_wake_guard_until) ||
        (current < requested && now_ms < s->g_low_lux_rise_confirmed_until);
    requested = ios_temporal_target(s, now_ms, requested, lux, bypass);
    candidate = requested;
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
