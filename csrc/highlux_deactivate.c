#include "business_api.h"
uint64_t ios_sunlight_deactivate(DomainIo *io, const char *reason) {
    if (!reason)
        reason = "exit";
    bool was_active = (FLAG(hl_active) | FLAG(hl_exiting) | FLAG(hl_hbm_active)) & 1;
    SET_FLAG(hl_active, 0);
    SET_FLAG(hl_exiting, 0);
    SET_INT(hl_confirm_cnt, 0);
    SET_INT(hl_boost_br, 0);
    if (!*STRING(hl_hbm_path))
        SET_FLAG(hl_hbm_active, 0);
    else {
        uintptr_t stream = domain_open(io, STRING(hl_hbm_path), "w");
        bool disabled = false;
        if (stream) {
            int written = PRINT(io, stream, "%s", ARG(STRING(cfg_hbm_off_value)));
            int closed = (int)CALL(io, fclose, stream);
            disabled = written >= 0 && closed == 0;
        }
        if (disabled)
            SET_FLAG(hl_hbm_active, 0);
        else
            PRINT(io, domain_stdout(), "[%s] WARN: HBM write failed path=%s on=%d\n",
                  ARG("LumaCurve"), ARG(STRING(hl_hbm_path)), 0);
    }
    uint64_t result = CALL(io, strncpy, ARG(STRING(hl_last_block_reason)), ARG(reason), 31);
    if (was_active)
        result = (uint32_t)PRINT(io, domain_stdout(), "[%s] sunlight: EXIT reason=%s\n",
                                       ARG("LumaCurve"), ARG(reason));
    return result;
}

#ifndef IOS_PRODUCTION
void ios_highlux_deactivate(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = ios_sunlight_deactivate(&io, (const char *)(uintptr_t)caller->x[0]);
}
#endif
