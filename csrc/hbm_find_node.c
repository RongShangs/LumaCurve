#include "business_api.h"
#include "backlight_paths.h"
int ios_hbm_discover(DomainIo *io) {
    STRING(hl_hbm_path)[0] = 0;
    SET_FLAG(hl_hbm_discovery_attempted, 1);
    SET_INT(hl_hbm_found, 0);
    memcpy(STRING(hl_hbm_discovery), "not_found", 10);
    if (*STRING(cfg_hbm_node_path)) {
        domain_string(io, STRING(hl_hbm_path), 256, STRING(cfg_hbm_node_path));
        bool writable = (int)CALL(io, access, ARG(STRING(cfg_hbm_node_path)), 2) == 0;
        SET_INT(hl_hbm_found, writable);
        return (int)CALL(io, snprintf, ARG(STRING(hl_hbm_discovery)), 32, ARG("%s"),
                         ARG(writable ? "configured" : "configured_invalid"));
    }
    char hbm[384], hbm_mode[384];
    const char *brightness = ios_backlight_brightness();
    size_t length = strlen(brightness);
    if (length < 10) return 0;
    memcpy(hbm, brightness, length - 10);
    memcpy(hbm_mode, brightness, length - 10);
    strcpy(hbm + length - 10, "hbm");
    strcpy(hbm_mode + length - 10, "hbm_mode");
    const char *const candidates[] = {hbm, hbm_mode,
        "/sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-DSI-1/hbm"};
    for (size_t i = 0; i < sizeof(candidates) / sizeof(candidates[0]); i++) {
        if ((int)CALL(io, access, ARG(candidates[i]), 2) != 0)
            continue;
        domain_string(io, STRING(hl_hbm_path), 256, candidates[i]);
        SET_INT(hl_hbm_found, 1);
        memcpy(STRING(hl_hbm_discovery), "known_path", 11);
        return PRINT(io, domain_stdout(), "[%s] HBM node: %s\n", ARG("LumaCurve"),
                     ARG(STRING(hl_hbm_path)));
    }
    int directories = 0, entries = 0;
    int found = ios_hbm_scan(io, "/sys/class/backlight", 0, 2, &directories, &entries);
    if (!found)
        found = ios_hbm_scan(io, "/sys/class/drm", 0, 3, &directories, &entries);
    return found;
}
#ifndef IOS_PRODUCTION
void ios_hbm_find_node(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = (uint32_t)ios_hbm_discover(&io);
}
#endif
