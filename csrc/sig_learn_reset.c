/* Request a learning-state reset; the main loop performs it. */
#include "domain_io.h"
#ifndef IOS_PRODUCTION
void ios_sig_learn_reset(IosCpu *caller) {
    (void)caller;
    SET_INT(g_reset_learn, 1);
}
#endif

void ios_request_learn_reset(int signal_number) {
    (void)signal_number;
#ifdef IOS_PRODUCTION
    ios_state.g_reset_learn = 1;
#else
    SET_INT(g_reset_learn, 1);
#endif
}
