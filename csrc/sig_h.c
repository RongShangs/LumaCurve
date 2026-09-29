/* Stop request from the Android signal callback. */
#include "domain_io.h"
#ifndef IOS_PRODUCTION
void ios_sig_h(IosCpu *caller) {
    (void)caller;
    SET_INT(g_run, 0);
}
#endif

void ios_request_stop(int signal_number) {
    (void)signal_number;
#ifdef IOS_PRODUCTION
    ios_state.g_run = 0;
#else
    SET_INT(g_run, 0);
#endif
}
