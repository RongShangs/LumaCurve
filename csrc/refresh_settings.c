/* Cache Android settings. A failed read never becomes a valid zero. */
#include "business_api.h"
#include "brightness_preference.h"
#include <ctype.h>
#include <errno.h>
#include <limits.h>
#include "framework_backend.h"

#ifndef LUMA_FRAMEWORK_BACKEND
static bool trailing_space(const char *end) {
    while (isspace((unsigned char)*end)) end++;
    return *end == 0;
}
static bool setting_integer(const char *text, long *value) {
    char *end;
    errno = 0;
    long parsed = strtol(text, &end, 10);
    if (errno || end == text || !trailing_space(end)) return false;
    *value = parsed;
    return true;
}
static bool setting_adjustment(const char *text, float *value) {
    char *end;
    errno = 0;
    float parsed = strtof(text, &end);
    if (errno || end == text || !trailing_space(end) || !isfinite(parsed) ||
        parsed < -1.01f || parsed > 1.01f) return false;
    *value = fmaxf(-1, fminf(1, parsed));
    return true;
}
#endif
int ios_settings_refresh(DomainIo *io) {
#ifdef LUMA_FRAMEWORK_BACKEND
    uint64_t now=domain_now_ms(io);
    if(now>=TIME(last_settings_ms)&&now-TIME(last_settings_ms)<500&&INT(cached_auto)>=0)return 0;
    SET_TIME(last_settings_ms,now);
    if(luma_framework_refresh()){
        SET_INT(cached_auto,-1);SET_FLAG(cached_auto_adj_valid,0);
        memcpy(STRING(settings_read_error),"framework_read_failed",22);return -1;
    }
    LumaFrameworkSnapshot *s=&luma_framework_snapshot;
    SET_INT(cached_auto,s->mode);SET_FLOAT(cached_auto_adj,s->adjustment);SET_FLAG(cached_auto_adj_valid,1);SET_INT(cached_slider,s->slider);
    memcpy(STRING(settings_read_source),"framework",10);STRING(settings_read_error)[0]=0;
    memcpy(STRING(cached_slider_source),"auto_adj",9);
    luma_preference_settings(io,now,s->mode,s->adjustment,s->slider);return 0;
#else
    int64_t stamp[2] = {0};
    int result = (int)CALL(io, clock_gettime, 1, ARG(stamp));
    if (result != 0) return result;
    uint64_t now = (uint64_t)stamp[0] * 1000 + (uint64_t)stamp[1] / 1000000;
    if (now - TIME(last_settings_ms) < 2000) return 0;
    SET_TIME(last_settings_ms, now);
    uint8_t metadata[128] = {0};
    result = (int)CALL(io, stat, ARG("/data/system/users/0/settings_system.xml"), ARG(metadata));
    bool changed = false;
    if (result == 0) {
        uint64_t mtime = ios_load((uintptr_t)(metadata + 88), 8);
        uint64_t size = ios_load((uintptr_t)(metadata + 48), 8);
        changed = !TIME(last_settings_xml_mtime) || mtime != TIME(last_settings_xml_mtime) ||
                  size != TIME(last_settings_xml_size);
        SET_TIME(last_settings_xml_mtime, mtime);
        SET_TIME(last_settings_xml_size, size);
    }
    if (!changed && INT(cached_auto) >= 0 && now - TIME(last_settings_shell_ms) < 60000)
        return result;
    SET_TIME(last_settings_shell_ms, now);
    /* One pipe/shell, retaining the same three Android queries and user semantics.
     * && and pclose prevent partial command failures from committing. */
    uintptr_t stream = (uintptr_t)CALL(io, popen, ARG(
        "settings get system screen_brightness_mode 2>/dev/null && "
        "settings get system screen_auto_brightness_adj 2>/dev/null && "
        "settings get system screen_brightness 2>/dev/null"), ARG("r"));
    char values[3][64] = {{0}};
    bool complete = stream != 0;
    if (stream) {
        for (unsigned i = 0; i < 3; i++) {
            if (!CALL(io, fgets, ARG(values[i]), sizeof(values[i]), stream) ||
                !strchr(values[i], '\n')) complete = false;
        }
        if ((int)CALL(io, pclose, stream) != 0) complete = false;
    }
    long mode = -1, slider = -1;
    float adjustment = 0;
    bool mode_ok = complete && setting_integer(values[0], &mode) && (mode == 0 || mode == 1);
    bool adj_ok = complete && setting_adjustment(values[1], &adjustment);
    bool slider_ok = complete && setting_integer(values[2], &slider) && slider >= 0 && slider <= INT_MAX;
    if (mode_ok) {
        SET_INT(cached_auto, mode);
        memcpy(STRING(settings_read_source), "settings", 9);
    }
    if (adj_ok) {
        SET_FLOAT(cached_auto_adj, adjustment);
        SET_FLAG(cached_auto_adj_valid, 1);
        memcpy(STRING(cached_slider_source), "auto_adj", 9);
    }
    if (slider_ok) {
        if (slider > 255) {
            int maximum = INT(g_max) > 0 ? INT(g_max) : 16383;
            slider = (long)((int64_t)slider * 255 / maximum);
            if (slider > 255) slider = 255;
        }
        SET_INT(cached_slider, slider);
        if (!FLAG(cached_auto_adj_valid)) memcpy(STRING(cached_slider_source), "brightness", 11);
    }
    if (mode_ok && adj_ok && slider_ok) {
        STRING(settings_read_error)[0] = 0;
        luma_preference_settings(io, now, (int)mode, adjustment, slider);
    }
    else memcpy(STRING(settings_read_error), "settings_read_failed", 21);
    return mode_ok && adj_ok && slider_ok ? 0 : -1;
#endif
}
#ifndef IOS_PRODUCTION
void ios_refresh_settings(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = (uint32_t)ios_settings_refresh(&io);
}
#endif
