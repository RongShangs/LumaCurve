#include "business_api.h"
#include <string.h>

void ios_highlux_exit(const char *reason) {
    DomainIo io = {0};
    ios_sunlight_deactivate(&io, reason);
}

void ios_apply_brightness(int32_t *current, int32_t target, uint64_t now, int emergency) {
    DomainIo io = {0};
    ios_frame_apply(&io, now, current, target, emergency != 0);
}

void ios_load_config_typed(void) {
    DomainIo io = {0};
    ios_configuration_reload(&io);
}

int ios_load_learn_typed(void) {
    DomainIo io = {0};
    return ios_learning_load(&io);
}

void ios_save_learn_typed(void) {
    DomainIo io = {0};
    ios_learning_save(&io);
}

void ios_write_state_typed(const char *mode, int screen, int target, int current, int poll_ms,
                           const char *reason, uint64_t now, float lux, float smooth) {
    ios_write_state_frame(mode, screen, target, current, poll_ms, reason, now, lux, smooth);
}
