#include "domain_io.h"
#ifndef IOS_PRODUCTION
void ios_sig_reload(IosCpu *caller) {
    (void)caller;
    SET_INT(g_reload_config, 1);
}
#endif

void ios_request_config_reload(int signal_number) {
    (void)signal_number;
#ifdef IOS_PRODUCTION
    ios_state.g_reload_config = 1;
#else
    SET_INT(g_reload_config, 1);
#endif
}
