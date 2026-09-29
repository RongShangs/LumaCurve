#include "brightness_preference.h"
#include "brightness_curve.h"
#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include <math.h>
#include <stdlib.h>

#define PREF_PATH "/data/local/tmp/luma_curve_preference"
#define PREF_TEMP PREF_PATH ".tmp-daemon"
int luma_preference_learning, luma_preference_revision;
float luma_preference_base;
static struct {
    bool loaded, configured, dirty, settings_seen, event_seen;
    float offset, effective, stored_base, last_adj, pending_log;
    int stored_revision, last_mode;
    long last_slider;
    unsigned samples;
    float points[14], baseline[14], pending_lux;
    int anchor;
    int pending_dir;
    unsigned gesture_count;
    uint64_t learned_ms;
    uint64_t changed_ms, last_gesture_ms, applied_ms, saved_ms;
    char event[2048];
    const char *evidence;
} pref;

static float bounded(float value) { return fmaxf(-50, fminf(100, value)); }
static bool safe(uint64_t now);
static bool number(const char *text, double *value) {
    char *end; errno = 0; *value = strtod(text, &end);
    if (end == text || errno || !isfinite(*value)) return false;
    while (isspace((unsigned char)*end)) end++;
    return !*end;
}
void luma_preference_reset(void) {
    memset(&pref, 0, sizeof(pref)); pref.evidence = "not_observed"; pref.anchor = -1;
    luma_preference_learning = 0; luma_preference_base = 0; luma_preference_revision = 0;
}
static void load(DomainIo *io) {
    pref.loaded = true;
    uintptr_t file = domain_open(io, PREF_PATH, "r");
    if (!file) return;
    char line[256]; unsigned seen = 0; double values[5] = {0}; bool valid = true;
    float baseline[14], points[14];
    const char *keys[] = {"format", "offset", "samples", "config_offset", "config_revision", "base_points", "learned_points"};
    while (CALL(io, fgets, ARG(line), sizeof(line), file)) {
        char *equal = strchr(line, '=');
        if (!equal || !strchr(line, '\n')) { valid = false; break; }
        *equal++ = 0; unsigned i;
        for (i = 0; i < 7 && strcmp(line, keys[i]); i++) {}
        equal[strcspn(equal, "\r\n")] = 0;
        if (i == 7 || (seen & (1u << i)) || (i < 5 ? !number(equal, &values[i]) :
            !luma_curve_parse(equal, i == 5 ? baseline : points))) { valid = false; break; }
        seen |= 1u << i;
    }
    if ((int)CALL(io, fclose, file) != 0) valid = false;
    if (!valid || !((seen == 31 && values[0] == 1) || (seen == 127 && values[0] == 2)) || values[1] < -50 || values[1] > 100 ||
        values[2] < 0 || values[2] > INT_MAX || floor(values[2]) != values[2] ||
        values[3] < -50 || values[3] > 100 || values[4] < 0 || values[4] > INT_MAX ||
        floor(values[4]) != values[4]) return;
    pref.offset = pref.effective = (float)values[1]; pref.samples = (unsigned)values[2];
    pref.stored_base = (float)values[3]; pref.stored_revision = (int)values[4];
    if (values[0] == 2) {
        for (unsigned i = 0; i < 14; i++) {
            if (fabsf(baseline[i] - luma_curve_base_point(i)) > .0000001f ||
                points[i] < fmaxf(.001f, baseline[i] * .8f) - .0000001f ||
                points[i] > fminf(1, baseline[i] * 1.2f) + .0000001f) {
                pref.samples = 0; return;
            }
        }
        memcpy(pref.baseline, baseline, sizeof(baseline)); memcpy(pref.points, points, sizeof(points));
    } else {
        /* Preserve legacy global preference, but new gestures train local anchors only. */
        for (unsigned i = 0; i < 14; i++) pref.baseline[i] = pref.points[i] = luma_curve_base_point(i);
        pref.samples = 0; pref.dirty = true;
    }
}
static void save(DomainIo *io, uint64_t now) {
    if (!pref.dirty || (pref.saved_ms && now >= pref.saved_ms && now - pref.saved_ms < 5000)) return;
    pref.saved_ms = now;
    char buffer[768], base[256], learned[256];
    if (!luma_curve_format(base, sizeof(base)) || !luma_curve_learned_format(learned, sizeof(learned))) return;
    int length = snprintf(buffer, sizeof(buffer),
        "format=2\noffset=%.6f\nsamples=%u\nconfig_offset=%.9g\nconfig_revision=%d\nbase_points=%s\nlearned_points=%s\n",
        pref.offset, pref.samples, luma_preference_base, luma_preference_revision, base, learned);
    if (length < 0 || (size_t)length >= sizeof(buffer)) return;
    uintptr_t file = domain_open(io, PREF_TEMP, "w");
    if (!file) return;
    bool ok = (int)CALL(io, chmod, ARG(PREF_TEMP), 0600) == 0;
    if (ok) ok = CALL(io, fwrite, ARG(buffer), 1, (unsigned)length, file) == (unsigned)length;
    if ((int)CALL(io, fclose, file) != 0) ok = false;
    if (ok && (int)CALL(io, rename, ARG(PREF_TEMP), ARG(PREF_PATH)) == 0) {
        pref.dirty = false; pref.stored_base = luma_preference_base;
        pref.stored_revision = luma_preference_revision;
    } else CALL(io, unlink, ARG(PREF_TEMP));
}
void luma_preference_configure(DomainIo *io) {
    if (!pref.loaded) load(io);
    bool curve_changed = false;
    for (unsigned i = 0; i < 14; i++) if (fabsf(pref.baseline[i] - luma_curve_base_point(i)) > .0000001f) curve_changed = true;
    if (curve_changed || pref.stored_revision != luma_preference_revision || pref.stored_base != luma_preference_base) {
        bool neutral_init = !pref.configured && !pref.samples && pref.offset == 0 &&
            !luma_preference_learning && luma_preference_base == 0 &&
            luma_preference_revision == 0 && !luma_curve_custom;
        for (unsigned i = 0; i < 14; i++) pref.points[i] = pref.baseline[i] = luma_curve_base_point(i);
        pref.offset = bounded(luma_preference_base); pref.samples = 0;
        pref.dirty = !neutral_init; pref.saved_ms = 0;
        if (!pref.configured) pref.effective = pref.offset;
    }
    pref.configured = true; pref.pending_log = 0; pref.changed_ms = 0;
    pref.pending_dir = 0; pref.gesture_count = 0; pref.last_gesture_ms = 0;
    if (!luma_preference_learning) pref.evidence = "disabled";
    save(io, 0);
}
/* Only the latest primary-display AOSP event, explicitly user_set, is evidence.
 * A changed settings value alone (or changed sysfs backlight) is insufficient.
 * The initial dump establishes a baseline; old events never train after restart. */
void luma_preference_settings(DomainIo *io, uint64_t now, int mode, float adj, long slider) {
    bool changed = !pref.settings_seen || mode != pref.last_mode || adj != pref.last_adj || slider != pref.last_slider;
    pref.settings_seen = true; pref.last_mode = mode; pref.last_adj = adj; pref.last_slider = slider;
    if (mode != 1) { pref.changed_ms = 0; pref.pending_log = 0; pref.pending_dir = 0; pref.gesture_count = 0; }
    if (!luma_preference_learning || !changed) return;
    uintptr_t pipe = (uintptr_t)CALL(io, popen, ARG(
        "set -o pipefail 2>/dev/null || exit 1; timeout 2 dumpsys display 2>/dev/null | grep 'BrightnessEvent:.*logicalId=0' | tail -n 1"), ARG("r"));
    if (!pipe) { pref.evidence = "unavailable"; return; }
    char line[2048] = {0};
    bool complete = CALL(io, fgets, ARG(line), sizeof(line), pipe) && strchr(line, '\n');
    int closed = (int)CALL(io, pclose, pipe);
    if (!complete || closed != 0) { pref.evidence = "unavailable"; return; }
    bool fresh = pref.event_seen && strcmp(pref.event, line);
    pref.event_seen = true; memcpy(pref.event, line, sizeof(line));
    pref.evidence = "observed";
    char *brightness = strstr(line, "BrightnessEvent: brt="), *initial = strstr(line, ", initBrt=");
    if (!fresh || mode != 1 || !safe(now) || !brightness || !initial || !strstr(line, ", autoBrightness=true") ||
        !strstr(line, ", state=ON,") || !strstr(line, ", logicalId=0")) return;
    char *end; float to = strtof(brightness + strlen("BrightnessEvent: brt="), &end);
    if (strncmp(end, "(user_set)", 10)) return;
    float from = strtof(initial + strlen(", initBrt="), &end);
    if (*end != ',' || !isfinite(to) || !isfinite(from) || to <= 0 || to > 1 || from <= 0 || from > 1) return;
    float lux = FLOAT(g_actuator_smooth_lux);
    if (!isfinite(lux) || lux < 0 || (pref.learned_ms && now >= pref.learned_ms && now - pref.learned_ms < 60000)) return;
    float delta = logf(to / from);
    if (!isfinite(delta) || fabsf(delta) < .03f) return;
    int direction = delta > 0 ? 1 : -1;
    unsigned anchor = luma_curve_nearest(lux);
    bool same_scene = pref.changed_ms && now >= pref.changed_ms && now - pref.changed_ms <= 600000 &&
        pref.anchor == (int)anchor && pref.pending_dir == direction &&
        fabsf(log1pf(lux) - log1pf(pref.pending_lux)) <= logf(1.5f);
    if (!same_scene) {
        pref.pending_log = 0; pref.gesture_count = 0; pref.pending_lux = lux;
        pref.anchor = (int)anchor; pref.pending_dir = direction; pref.last_gesture_ms = 0;
    }
    if (!pref.gesture_count || (now >= pref.last_gesture_ms && now - pref.last_gesture_ms >= 5000)) {
        if (pref.gesture_count < UINT_MAX) pref.gesture_count++;
        pref.last_gesture_ms = now;
    }
    pref.pending_log += delta; pref.changed_ms = now; pref.evidence = "user_set";
}
static bool safe(uint64_t now) {
    return INT(cached_auto) == 1 && FLAG(g_lux_valid) && !FLAG(g_sensor_hold_active) &&
        !FLAG(g_heat_guard_active) && !FLAG(hl_active) && !FLAG(hl_hbm_active) &&
        !FLAG(g_zero_lux_suspect) && !FLAG(g_low_lux_bright_spike_guard) && now >= TIME(g_wake_readonly_until) &&
        now >= TIME(g_wake_guard_until) && !strcmp(STRING(g_brightness_owner), "daemon");
}
int luma_preference_apply(DomainIo *io, uint64_t now, int target) {
    if (!pref.configured) return target;
    if (pref.changed_ms && (now < pref.changed_ms || now - pref.changed_ms > 600000 || !safe(now) ||
        !isfinite(FLOAT(g_actuator_smooth_lux)) || FLOAT(g_actuator_smooth_lux) < 0 ||
        luma_curve_nearest(FLOAT(g_actuator_smooth_lux)) != (unsigned)pref.anchor ||
        fabsf(log1pf(FLOAT(g_actuator_smooth_lux)) - log1pf(pref.pending_lux)) > logf(1.5f))) {
        pref.changed_ms = 0; pref.pending_log = 0; pref.pending_dir = 0; pref.gesture_count = 0;
    }
    if (luma_preference_learning && pref.changed_ms && pref.gesture_count >= 2 && now - pref.changed_ms >= 8000) {
        float lux = FLOAT(g_actuator_smooth_lux);
        float step = fmaxf(-.0025f, fminf(.0025f, pref.pending_log * .02f));
        if (isfinite(lux) && lux >= 0 && luma_curve_nearest(lux) == (unsigned)pref.anchor &&
            fabsf(log1pf(lux) - log1pf(pref.pending_lux)) <= logf(1.5f) && fabsf(step) >= .0005f) {
            unsigned i = (unsigned)pref.anchor;
            float lower = fmaxf(.001f, pref.baseline[i] * .8f), upper = fminf(1, pref.baseline[i] * 1.2f);
            if (i) lower = fmaxf(lower, pref.points[i - 1]);
            if (i < 13) upper = fminf(upper, pref.points[i + 1]);
            float next = fmaxf(lower, fminf(upper, pref.points[i] * (1 + step)));
            if (next != pref.points[i]) {
                pref.points[i] = next; if (pref.samples < INT_MAX) pref.samples++;
                pref.dirty = true; pref.learned_ms = now;
            }
        }
        pref.changed_ms = 0; pref.pending_log = 0; pref.pending_dir = 0; pref.gesture_count = 0;
    }
    save(io, now);
    if (pref.applied_ms && now >= pref.applied_ms) {
        float dt = fminf(1000, (float)(now - pref.applied_ms));
        pref.effective += (pref.offset - pref.effective) * -expm1f(-dt / 4000);
        if (fabsf(pref.effective - pref.offset) < .001f) pref.effective = pref.offset;
    }
    pref.applied_ms = now;
    if (pref.effective == 0 || target <= 0) return target;
    double adjusted = (double)target * (1 + pref.effective / 100.0);
    return (int)fmin(INT(g_max) > 0 ? INT(g_max) : INT_MAX, floor(adjusted + .5));
}
float luma_preference_offset(void) { return pref.offset; }
float luma_preference_effective(void) { return pref.effective; }
unsigned luma_preference_samples(void) { return pref.samples; }
int luma_preference_pending(void) { return pref.changed_ms != 0; }
int luma_preference_dirty(void) { return pref.dirty; }
const char *luma_preference_evidence(void) { return pref.evidence ? pref.evidence : "not_observed"; }
float luma_preference_point(unsigned i, float baseline) {
    return pref.configured && i < 14 && fabsf(pref.baseline[i] - baseline) < .0000001f ? pref.points[i] : baseline;
}
int luma_preference_anchor(void) { return pref.samples || pref.changed_ms ? pref.anchor : -1; }
