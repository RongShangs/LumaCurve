/* Cache Android settings; poll the shell only when the XML changed or the cache expired. */
#include "business_api.h"
static uintptr_t settings_pipe(DomainIo *io, const char *command) {
    return (uintptr_t)CALL(io, popen, ARG(command), ARG("r"));
}
int ios_settings_refresh(DomainIo *io) {
    int64_t stamp[2];
    int result = (int)CALL(io, clock_gettime, 1, ARG(stamp));
    uint64_t now = (uint64_t)stamp[0] * 1000 + (uint64_t)stamp[1] / 1000000;
    if (now - TIME(last_settings_ms) < 2000)
        return result;
    SET_TIME(last_settings_ms, now);
    uint8_t metadata[128] = {0};
    result = (int)CALL(io, stat, ARG("/data/system/users/0/settings_system.xml"), ARG(metadata));
    bool changed = false;
    if (result == 0) {
        uint64_t mtime = ios_load((uintptr_t)(metadata + 88), 8),
                 size = ios_load((uintptr_t)(metadata + 48), 8);
        uint64_t old_mtime = TIME(last_settings_xml_mtime), old_size = TIME(last_settings_xml_size);
        SET_TIME(last_settings_xml_mtime, mtime);
        SET_TIME(last_settings_xml_size, size);
        changed = !old_mtime || mtime != old_mtime || size != old_size;
    }
    if (!changed && INT(cached_auto) >= 0 && now - TIME(last_settings_shell_ms) < 60000)
        return result;
    SET_TIME(last_settings_shell_ms, now);
    bool mode_failed = true;
    char value[32] = {0};
    uintptr_t stream = settings_pipe(io, "settings get system screen_brightness_mode 2>/dev/null");
    if (stream) {
        if (CALL(io, fgets, ARG(value), sizeof(value), stream)) {
            uint32_t mode = (uint32_t)CALL(io, atoi, ARG(value));
            if (mode <= 1) {
                SET_INT(cached_auto, mode);
                mode_failed = false;
            }
        }
        CALL(io, pclose, stream);
    }
    SET_FLAG(cached_auto_adj_valid, 0);
    stream = settings_pipe(io, "settings get system screen_auto_brightness_adj 2>/dev/null");
    if (stream) {
        memset(value, 0, sizeof(value));
        if (CALL(io, fgets, ARG(value), sizeof(value), stream)) {
            char *end = NULL;
            CALL(io, strtof, ARG(value), ARG(&end));
            float adjustment = domain_float_result(io);
            if (end != value && isfinite(adjustment) && adjustment >= -1.01f &&
                adjustment <= 1.01f) {
                if (adjustment > 1)
                    adjustment = 1;
                if (adjustment < -1)
                    adjustment = -1;
                SET_FLAG(cached_auto_adj_valid, 1);
                SET_FLOAT(cached_auto_adj, adjustment);
                memcpy(STRING(cached_slider_source), "auto_adj", 9);
            }
        }
        CALL(io, pclose, stream);
    }
    stream = settings_pipe(io, "settings get system screen_brightness 2>/dev/null");
    result = 0;
    if (stream) {
        memset(value, 0, sizeof(value));
        if (CALL(io, fgets, ARG(value), sizeof(value), stream)) {
            int64_t slider = (int64_t)CALL(io, strtol, ARG(value), 0, 10);
            if (slider >= 0) {
                if (slider > 255) {
                    int maximum = INT(g_max) > 0 ? INT(g_max) : 16383;
                    int32_t normalized =
                        (int32_t)ios_sdiv((uint64_t)slider * 255u, (uint64_t)maximum, 64);
                    if (normalized > 255)
                        normalized = 255;
                    if (normalized < 0)
                        normalized = 0;
                    slider = normalized;
                }
                SET_INT(cached_slider, slider);
                if (!(FLAG(cached_auto_adj_valid) & 1))
                    memcpy(STRING(cached_slider_source), "brightness", 11);
            }
        }
        result = (int)CALL(io, pclose, stream);
    }
    if (mode_failed)
        memcpy(STRING(settings_read_error), "settings_read_failed", 21);
    else {
        memcpy(STRING(settings_read_source), "settings", 9);
        STRING(settings_read_error)[0] = 0;
    }
    return result;
}
#ifndef IOS_PRODUCTION
void ios_refresh_settings(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = (uint32_t)ios_settings_refresh(&io);
}
#endif
