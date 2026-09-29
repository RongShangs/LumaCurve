/* Apply one bounded brightness step and reconcile external readback conflicts. */
#include "backlight_paths.h"
#include "actuator.h"
#include "business_api.h"
#include "actuator_diagnostics.h"
#include <errno.h>
static int write_errno(void) {
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
    return errno;
#else
    return 0; /* The recovered test ABI does not expose libc TLS errno. */
#endif
}
static void clear_transition(void) {
    SET_INT(g_tr_3, 0);
    SET_FLAG(g_transition_active, 0);
}
static void governor_reason(const char *text) {
    memcpy(STRING(g_write_gov_reason), text, strlen(text) + 1);
}
uint64_t ios_frame_apply(DomainIo *io, uint64_t now, int32_t *current, int32_t target,
                            bool emergency) {
    int32_t old = *current;
    if (old == target) {
        if (!TIME(g_last_write_ms) || now - TIME(g_last_write_ms) > 1199)
            SET_INT(g_gov_last_dir, 0);
        clear_transition();
        SET_FLAG(g_target_hold_active, 0);
        SET_FLAG(g_gov_hold, 0);
        return now;
    }
    int frame = INT(g_actuator_frame_ms);
    if (frame < 1)
        frame = 250;
    uint64_t last_write = TIME(g_last_write_ms);
    uint64_t elapsed = now > last_write - 1 ? now - last_write : (uint64_t)frame;
    uint64_t maximum_elapsed = target > old && !emergency ? 180 : 500;
    if (elapsed > maximum_elapsed)
        elapsed = maximum_elapsed;
    if (elapsed < 2)
        elapsed = 1;
    float units_per_second =
        emergency ? (float)INT(g_max)
                  : (target < old ? darken_rate(io) : brighten_rate()) * (float)INT(g_max);
    int step = (int32_t)ios_float_int((units_per_second * (float)elapsed) / 1000.0f, 32);
    if (step < 2)
        step = 1;
    int direction = old < target ? 1 : -1, last_direction = INT(g_gov_last_dir);
    bool expired_reversal_hold = false;
    if (FLAG(g_gov_hold) == 1) {
        if (direction == last_direction) {
            SET_FLAG(g_gov_hold, 0);
            SET_FLAG(g_target_hold_active, 0);
        } else {
            if (now < TIME(g_gov_hold_until)) {
                governor_reason("hold");
                return now;
            }
            SET_FLAG(g_gov_hold, 0);
            SET_FLAG(g_target_hold_active, 0);
            expired_reversal_hold = true;
        }
    }
    if (!expired_reversal_hold && last_direction && direction != last_direction) {
        governor_reason("hold");
        SET_FLAG(g_target_hold_active, 1);
        SET_FLAG(g_gov_hold, 1);
        SET_TIME(g_gov_hold_until, now + 1200);
        return now;
    }
    int requested = direction > 0 ? (int32_t)((uint32_t)old + (uint32_t)step)
                                  : (int32_t)((uint32_t)old - (uint32_t)step);
    if (direction > 0 && requested >= target)
        requested = target;
    if (direction < 0 && requested <= target)
        requested = target;
    if (direction > 0)
        SET_INT(bright_cnt, (uint32_t)INT(bright_cnt) + 1);
    else
        SET_INT(dark_cnt, (uint32_t)INT(dark_cnt) + 1);
    luma_write_attempts++;
    luma_write_request=requested; luma_write_bytes=-1; luma_write_errno=0;
    int fd = (int)CALL(io, open, ARG(ios_backlight_brightness()), 1);
    if (fd < 0) { luma_write_stage="open_failed"; luma_write_errno=write_errno(); }
    uint64_t result = (uint32_t)fd;
    bool written = false;
    if (fd >= 0) {
        char text[16];
        int length =
            (int)CALL(io, snprintf, ARG(text), sizeof(text), ARG("%d"), (uint32_t)requested);
        int64_t bytes = -1;
        if (length > 0 && (size_t)length < sizeof(text)) {
            bytes = (int64_t)CALL(io, write, (uint32_t)fd, ARG(text), (uint64_t)length);
            luma_write_bytes=bytes;
            luma_write_stage=bytes==length ? "written" : bytes<0 ? "write_failed" : "short_write";
            if (bytes!=length) luma_write_errno=bytes<0 ? write_errno() : EIO;
        } else { luma_write_stage="format_failed"; luma_write_errno=write_errno(); }
        result = (uint32_t)CALL(io, close, (uint32_t)fd);
        written = length > 0 && bytes == length && (int32_t)result == 0;
        if (bytes==length && (int32_t)result != 0) {
            luma_write_stage="close_failed"; luma_write_errno=write_errno();
        }
    }
    if (!written) {
        SET_INT(g_last_write_result, -1);
        SET_INT(g_write_readback_mismatch_streak, 0);
#if defined(IOS_PRODUCTION) && !defined(IOS_TEST_ABI)
        SET_INT(g_last_write_readback, -1);
        /* A failed write relinquishes the lock instead of claiming control. */
        CALL(io, chmod, ARG(ios_backlight_brightness()), 0644);
        memcpy(STRING(g_brightness_owner), "write_failed_passthrough", 25);
        governor_reason("write_failed");
        clear_transition();
#endif
        return result;
    }
    SET_TIME(g_last_write_ms, domain_now_ms(io));
    luma_write_successes++;
    SET_INT(g_last_write_readback, requested);
    SET_INT(g_gov_last_dir, direction);
    int readback = -1;
    uintptr_t stream = domain_open(io, ios_backlight_brightness(), "r");
    result = 0;
    if (stream) {
        if (IOS_SCAN(io, stream, "%d", &readback) != 1)
            readback = -1;
        result = (uint32_t)CALL(io, fclose, stream);
    }
    int actual = readback > 0 ? readback : requested;
    luma_write_stage=readback > 0 ? "readback_ok" : "readback_unavailable";
    *current = actual;
    SET_INT(g_last_write_readback, actual);
    if (FLAG(hl_active) == 1 && INT(cfg_hl_hbm_enable) &&
        actual >= (int32_t)ios_float_int((float)INT(g_max) * .8f, 32) && *STRING(hl_hbm_path)) {
        stream = domain_open(io, STRING(hl_hbm_path), "w");
        bool enabled = false;
        if (stream) {
            int length = PRINT(io, stream, "%s", ARG(STRING(cfg_hbm_on_value)));
            int closed = (int)CALL(io, fclose, stream);
            enabled = length >= 0 && closed == 0;
        }
        if (enabled) {
            SET_FLAG(hl_hbm_active, 1);
            result = 0;
        } else
            result =
                (uint32_t)PRINT(io, domain_stdout(), "[%s] WARN: HBM write failed path=%s on=%d\n",
                                ARG("LumaCurve"), ARG(STRING(hl_hbm_path)), 1);
    }
    if (actual == target)
        clear_transition();
    uint32_t difference = (uint32_t)readback - (uint32_t)requested;
    if ((int32_t)difference < 0)
        difference = 0u - difference;
    if (readback > 0 && difference > 64) {
        luma_write_stage="readback_mismatch";
        uint32_t streak = (uint32_t)INT(g_write_readback_mismatch_streak);
        if (streak < 3 && streak != 2) {
            governor_reason("readback_sync");
            SET_INT(g_write_readback_mismatch_streak, streak + 1);
            SET_INT(g_last_write_result, 1);
            return result;
        }
        SET_INT(g_last_write_result, 1);
        if (!TIME(g_external_write_burst_start_ms) ||
            now - TIME(g_external_write_burst_start_ms) > 10000) {
            SET_INT(g_external_write_burst_count, 1);
            SET_TIME(g_external_write_burst_start_ms, now);
        } else if ((uint32_t)INT(g_external_write_burst_count) < 1000000)
            SET_INT(g_external_write_burst_count, (uint32_t)INT(g_external_write_burst_count) + 1);
        clear_transition();
        SET_FLAG(g_target_hold_active, 0);
        SET_FLAG(g_external_write_hold, 1);
        SET_TIME(g_external_write_hold_until, now + 1200);
        SET_FLAG(g_target_debounce_init, 1);
        SET_FLAG(g_gov_hold, 0);
        SET_INT(g_gov_last_dir, 0);
        SET_INT(g_target_candidate_dir, 0);
        SET_INT(g_write_readback_mismatch_streak, 0);
        governor_reason("external_readback_hold");
        SET_INT(g_tr_0, readback);
        SET_INT(g_tr_1, readback);
        SET_TIME(g_last_write_ms, now);
        SET_INT(g_target_debounce_br, readback);
        SET_INT(g_target_candidate_br, readback);
        SET_TIME(g_target_candidate_since, now);
        return (uint32_t)PRINT(io, domain_stdout(),
                               "[%s] repeated readback conflict: requested=%d actual=%d\n",
                               ARG("LumaCurve"), (uint32_t)requested, (uint32_t)readback);
    }
    SET_INT(g_write_readback_mismatch_streak, 0);
    SET_INT(g_last_write_result, 0);
    return (uint32_t)CALL(io, snprintf, ARG(STRING(g_write_gov_reason)), 32, ARG("%s"),
                          ARG(emergency ? "emergency" : "normal"));
}
#ifndef IOS_PRODUCTION
void ios_apply_brightness_frame(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_frame_apply(&io, caller->x[0], (int32_t *)(uintptr_t)caller->x[1],
                               (int32_t)caller->x[2], (int32_t)caller->x[3] != 0);
}
#endif
