#pragma once
#include <stdbool.h>
#include <stddef.h>
extern bool luma_curve_custom;
extern float luma_curve_points[14];
void luma_curve_reset(void);
bool luma_curve_parse(const char *text, float output[14]);
bool luma_curve_format(char *output, size_t capacity);
void luma_curve_context(float circadian);
float luma_curve_circadian(void);
float luma_curve_base_point(unsigned index);
unsigned luma_curve_nearest(float lux);
bool luma_curve_learned_format(char *output, size_t capacity);
