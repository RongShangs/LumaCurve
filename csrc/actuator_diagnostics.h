#pragma once
#include <stdint.h>
extern uint64_t luma_write_attempts, luma_write_successes;
extern int luma_write_errno, luma_write_request;
extern int64_t luma_write_bytes;
extern const char *luma_write_stage;
