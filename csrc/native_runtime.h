/* Numerical and byte serialization utilities used by ordinary C business code. */
#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <math.h>
#include <limits.h>
#include <stdlib.h>
#include "state.h"
#ifdef IOS_TEST_ABI
extern const uint8_t ios_ro[14281];
#define ios_ram ((uint8_t *)&ios_state)
#endif
void ios_reset_data(void);
static inline uint64_t ios_load(uint64_t address, unsigned size) {
    volatile const uint8_t *p = (volatile const uint8_t *)(uintptr_t)address;
    uint64_t value = 0;
    for (unsigned i=0;i<size;i++) value |= (uint64_t)p[i] << (8*i);
    return value;
}
static inline void ios_store(uint64_t address, uint64_t value, unsigned size) {
    volatile uint8_t *p = (volatile uint8_t *)(uintptr_t)address;
    for (unsigned i=0;i<size;i++) p[i] = (uint8_t)(value >> (8*i));
}
static inline uint64_t ios_float_int(double value, unsigned width) {
    if (isnan(value)) return 0;
    if (width==32) {
        if (value >= 2147483648.0) return INT32_MAX;
        if (value <= -2147483648.0) return (uint32_t)INT32_MIN;
        return (uint32_t)(int32_t)value;
    }
    if (value >= 9223372036854775808.0) return INT64_MAX;
    if (value <= -9223372036854775808.0) return (uint64_t)INT64_MIN;
    return (uint64_t)(int64_t)value;
}
static inline uint64_t ios_sdiv(uint64_t a, uint64_t b, unsigned width) {
    if (width==32) return b ? (uint32_t)((int64_t)(int32_t)a / (int32_t)b) : 0;
    if (!b) return 0;
    if (a==UINT64_C(0x8000000000000000) && b==UINT64_MAX) return a;
    return (uint64_t)((int64_t)a/(int64_t)b);
}
