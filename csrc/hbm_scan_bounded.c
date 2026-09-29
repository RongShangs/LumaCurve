/* Bounded discovery: at most 64 directories, 1024 entries and caller-set depth. */
#include "business_api.h"
static bool allowed_hbm_path(const char *path) {
    return *path && !strstr(path, "local_hbm") && !strstr(path, "fingerprint") &&
           !strstr(path, "fod_hbm") && !strstr(path, "udfps");
}
int ios_hbm_scan(DomainIo *io, const char *parent, int depth, int max_depth, int *directories,
                    int *entries) {
    if (!parent || depth > max_depth || *directories > 63 || *entries >= 1024)
        return 0;
    uintptr_t directory = (uintptr_t)CALL(io, opendir, ARG(parent));
    if (!directory)
        return 0;
    (*directories)++;
    bool found = false;
    uintptr_t entry;
    while ((entry = (uintptr_t)CALL(io, readdir, directory)) && *entries < 1024) {
        const char *name = (const char *)(entry + 19); /* Android ARM64 dirent.d_name. */
        if (!strcmp(name, ".") || !strcmp(name, ".."))
            continue;
        (*entries)++;
        char path[512];
        int length =
            (int)CALL(io, snprintf, ARG(path), sizeof(path), ARG("%s/%s"), ARG(parent), ARG(name));
        if (length >= 512)
            continue;
        bool candidate = !strcmp(name, "hbm") || !strcmp(name, "hbm_mode") ||
                         !strcmp(name, "high_brightness_mode");
        if (candidate && allowed_hbm_path(path) && (int)CALL(io, access, ARG(path), 2) == 0) {
            domain_string(io, STRING(hl_hbm_path), 256, path);
            SET_INT(hl_hbm_found, 1);
            memcpy(STRING(hl_hbm_discovery), "bounded_scan", 13);
            PRINT(io, domain_stdout(), "[%s] HBM node discovered: %s\n", ARG("LumaCurve"),
                  ARG(STRING(hl_hbm_path)));
            found = true;
            break;
        }
        if (depth >= max_depth || *directories >= 64)
            continue;
        uint8_t metadata[128] = {0};
        if ((int)CALL(io, stat, ARG(path), ARG(metadata)) != 0)
            continue;
        uint32_t mode = (uint32_t)ios_load((uintptr_t)(metadata + 16), 4);
        if ((mode & 0xf000) == 0x4000 &&
            ios_hbm_scan(io, path, depth + 1, max_depth, directories, entries)) {
            found = true;
            break;
        }
    }
    CALL(io, closedir, directory);
    return found;
}
#ifndef IOS_PRODUCTION
void ios_hbm_scan_bounded(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_hbm_scan(&io, (const char *)(uintptr_t)caller->x[0], (int32_t)caller->x[1],
                            (int32_t)caller->x[2], (int *)(uintptr_t)caller->x[3],
                            (int *)(uintptr_t)caller->x[4]);
}
#endif
