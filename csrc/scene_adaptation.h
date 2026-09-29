#pragma once
#include <stdbool.h>
#include <stdint.h>
struct IosState;
/* Independent of the recovered state layout. No allocation or extra polling. */
void ios_scene_reset(bool reset_modes);
void ios_scene_mode(bool front, int mode);
int ios_scene_reporting_mode(bool front);
void ios_scene_observe(bool front, float lux, int64_t sensor_ns, uint64_t arrival_ms);
bool ios_scene_sensor_fresh(const struct IosState *, bool front, uint64_t now);
bool ios_scene_selected_fresh(const struct IosState *, uint64_t now);
bool ios_scene_back_confirmed(const struct IosState *, uint64_t now);
/* true: retain accepted front light and skip updating the original lux history. */
bool ios_scene_filter(struct IosState *, uint64_t now, float *lux);
const char *ios_scene_class(void);
uint64_t ios_scene_hold_left(uint64_t now);
unsigned ios_scene_samples(void);
bool ios_scene_holding(void);
/* Advance a retained on-change sample with elapsed time, never new event counts. */
float ios_scene_smooth_tick(struct IosState *, uint64_t now, float smooth, float *raw);
