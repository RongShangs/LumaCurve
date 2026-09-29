#pragma once
#ifdef LUMA_FRAMEWORK_BACKEND
#include <stdint.h>
#include <stddef.h>
typedef struct LumaFrameworkSnapshot {
    int mode, slider, on, window, node, active;
    float adjustment, minimum, maximum, base, adjusted, goal, limited, request;
    uint64_t sampled_ms;
} LumaFrameworkSnapshot;
extern LumaFrameworkSnapshot luma_framework_snapshot;
int luma_framework_initialize(void);
int luma_framework_refresh(void);
int luma_framework_acquire(void);
int luma_framework_release(void);
int luma_framework_ready(void);
void luma_framework_pulse(void);
uint64_t luma_framework_apply(uint64_t now, int32_t *current, int32_t target);
const char *luma_framework_path(const char *path, char *buffer, size_t capacity);
#endif
