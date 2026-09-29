#pragma once
#include <stdint.h>
extern uint64_t luma_write_attempts, luma_write_successes;
extern int luma_write_errno, luma_write_request;
extern int64_t luma_write_bytes;
extern const char *luma_write_stage;
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
int ios_native_backlight_ready(void);
#endif
