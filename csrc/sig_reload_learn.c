#include "domain_io.h"
#ifndef IOS_PRODUCTION
void ios_sig_reload_learn(IosCpu *caller) {
    (void)caller;
    SET_INT(g_reload_learn, 1);
}
#endif

void ios_request_learn_reload(int signal_number) {
    (void)signal_number;
#ifdef IOS_PRODUCTION
    ios_state.g_reload_learn = 1;
#else
    SET_INT(g_reload_learn, 1);
#endif
}
