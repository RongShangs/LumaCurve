/* Log-lux interpolation across the complete fourteen original anchors. */
#include "domain_io.h"
#include <math.h>
#include "brightness_curve.h"
#include "brightness_preference.h"
#include <ctype.h>
#include <errno.h>
#include <stdlib.h>

static const float lux_anchors[] = {0,    1,    5,    10,   50,    100,   500,
                                    1000, 2000, 5000, 9000, 10000, 35000, 100000};
static const float pct_anchors[] = {.001f, .002f, .0075f, .0115f, .056f, .066f, .089f,
                                    .099f, .109f, .122f,  .22f,   .25f,  .65f,  .85f};
bool luma_curve_custom;
float luma_curve_points[14];
static float circadian_factor = 1;
void luma_curve_reset(void) {
    luma_curve_custom = false; circadian_factor = 1;
    memcpy(luma_curve_points, pct_anchors, sizeof(pct_anchors));
}
bool luma_curve_parse(const char *text, float output[14]) {
    float parsed[14]; const char *cursor = text;
    for (unsigned i = 0; i < 14; i++) {
        char *end; errno = 0; float value = strtof(cursor, &end);
        if (end == cursor || errno || !isfinite(value) || value < .1f || value > 100 ||
            (i && value / 100 < parsed[i - 1])) return false;
        parsed[i] = value / 100;
        while (isspace((unsigned char)*end)) end++;
        if (i < 13) { if (*end != ',') return false; cursor = end + 1; }
        else if (*end) return false;
    }
    memcpy(output, parsed, sizeof(parsed)); return true;
}
bool luma_curve_format(char *output, size_t capacity) {
    size_t used = 0; const float *points = luma_curve_custom ? luma_curve_points : pct_anchors;
    for (unsigned i = 0; i < 14; i++) {
        int n = snprintf(output + used, capacity - used, "%s%.6f", i ? "," : "", points[i] * 100);
        if (n < 0 || (size_t)n >= capacity - used) return false;
        used += (size_t)n;
    }
    return true;
}
float luma_curve_base_point(unsigned i) { return i < 14 ? (luma_curve_custom ? luma_curve_points[i] : pct_anchors[i]) : 0; }
unsigned luma_curve_nearest(float lux) {
    float at = log1pf(fmaxf(0, lux)), distance = INFINITY; unsigned nearest = 0;
    for (unsigned i = 0; i < 14; i++) {
        float next = fabsf(at - log1pf(lux_anchors[i]));
        if (next < distance) { distance = next; nearest = i; }
    }
    return nearest;
}
bool luma_curve_learned_format(char *output, size_t capacity) {
    size_t used = 0;
    for (unsigned i = 0; i < 14; i++) {
        int n = snprintf(output + used, capacity - used, "%s%.6f", i ? "," : "",
            luma_preference_point(i, luma_curve_base_point(i)) * 100);
        if (n < 0 || (size_t)n >= capacity - used) return false;
        used += (size_t)n;
    }
    return true;
}
void luma_curve_context(float value) { circadian_factor = value; }
float luma_curve_circadian(void) { return circadian_factor; }

float ios_brightness_for_lux(float lux, float gamma) {
    float points[14];
    for (unsigned i = 0; i < 14; i++) points[i] = luma_preference_point(i, luma_curve_base_point(i));
    if (!isfinite(lux) || lux <= 0)
        return points[0];
    if (lux > 100000)
        return points[13];
    unsigned upper = 1;
    while (upper < 13 && lux > lux_anchors[upper])
        upper++;
    unsigned lower = upper - 1;
    float low_log = log10f(lux_anchors[lower] + 1), high_log = log10f(lux_anchors[upper] + 1);
    float position = high_log > low_log ? (log10f(lux + 1) - low_log) / (high_log - low_log) : 0;
    if (position > 1)
        position = 1;
    if (position < 0)
        position = 0;
    float bounded_gamma = gamma > 3.5f ? 3.5f : gamma;
    float exponent = gamma < 1 ? 2.2f : 2.2f / bounded_gamma;
    float weight = powf(position, exponent);
    return fmaf(points[upper] - points[lower], weight, points[lower]);
}

#ifndef IOS_PRODUCTION
void ios_curve_pct_for_lux(IosCpu *caller) {
    ios_f32(caller, 0, ios_brightness_for_lux(caller->v[0].f32[0], FLOAT(cfg_gamma)));
}
#endif
