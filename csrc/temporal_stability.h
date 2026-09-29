#pragma once
#include <stdint.h>
#include <stdbool.h>
extern int luma_indoor_stability;
void ios_temporal_reset(void);
/* Called by the maintained debounce path, after all protection calculations. */
struct IosState;
int32_t ios_temporal_target(struct IosState *s, uint64_t now, int32_t target,
                            float lux, bool bypass);
