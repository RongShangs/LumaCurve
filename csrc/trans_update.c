#include "domain_io.h"

void ios_transition_update(int32_t target, int32_t current, uint64_t now) {
    int32_t duration = INT(g_tr_3);
    if (duration > 0) {
        uint32_t remaining = (uint32_t)duration + (uint32_t)TIME(g_tr_2) - (uint32_t)now;
        SET_TIME(g_tr_2, now);
        SET_INT(g_tr_1, target);
        SET_INT(g_tr_3, (int32_t)remaining > 200 ? remaining : 200);
        SET_FLAG(g_transition_active, 1);
        SET_INT(g_transition_retarget_count, (uint32_t)INT(g_transition_retarget_count) + 1);
        return;
    }
    uint32_t delta = (uint32_t)target - (uint32_t)current;
    int32_t distance = (int32_t)delta;
    if (distance < 0)
        distance = (int32_t)(0u - delta);
    float fraction = (float)distance / (float)INT(g_max);
    if (!(fraction <= .08f))
        duration = 4500;
    else if (fraction <= .03f)
        duration = 900;
    else
        duration = (int32_t)(((fraction - .03f) * 3600.0f) / .05f + 900.0f);
    if (duration < 900)
        duration = 900;
    if ((uint32_t)duration >= 9000)
        duration = 9000;
    SET_INT(g_tr_0, current);
    SET_INT(g_tr_1, target);
    SET_TIME(g_tr_2, now);
    SET_INT(g_tr_3, duration);
    SET_FLAG(g_transition_active, 1);
}

#ifndef IOS_PRODUCTION
void ios_trans_update(IosCpu *caller) {
    ios_transition_update((int32_t)caller->x[0], (int32_t)caller->x[1], caller->x[2]);
}
#endif
