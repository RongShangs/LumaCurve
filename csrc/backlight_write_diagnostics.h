/* Rate-limited native IO errors; preserve errno for the caller. */
#pragma once
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "log_timestamp.h"
static int luma_backlight_fd = -1;
static time_t luma_backlight_error_last;
static int luma_backlight_error_code;
static const char *luma_backlight_error_stage;
static void luma_backlight_write_error(const char *stage, int error) {
    time_t now = time(NULL);
    if (!luma_backlight_error_stage || strcmp(stage,luma_backlight_error_stage) ||
        error != luma_backlight_error_code || now < luma_backlight_error_last ||
        now - luma_backlight_error_last >= 10) {
        luma_backlight_error_last=now; luma_backlight_error_code=error;
        luma_backlight_error_stage=stage;
        luma_log_timestamp(stderr);
        fprintf(stderr,"[LumaCurve] 背光写入失败：阶段=%s errno=%d 原因=%s\n",stage,error,strerror(error));
        fflush(stderr);
    }
    errno=error;
}
