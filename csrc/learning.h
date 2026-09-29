/* Persistent learning cells: eleven lux buckets, four time buckets. */
#pragma once
#include <stdint.h>
#include <stddef.h>
enum { LEARN_LUX_BUCKETS = 11, LEARN_TIME_BUCKETS = 4, LEARN_VERSION = 4 };
typedef struct LearnCell {
    float lux;
    int32_t slider;
    int64_t timestamp;
    float confidence;
    uint32_t reserved;
} LearnCell;
_Static_assert(sizeof(LearnCell) == 24, "Persistent cell layout");
_Static_assert(offsetof(LearnCell, timestamp) == 8, "Persistent timestamp layout");
