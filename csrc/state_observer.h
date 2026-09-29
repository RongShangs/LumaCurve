/* UI observation never changes sensor rates, brightness targets or protection. */
#pragma once
#include "domain_io.h"
bool ios_state_observer_active(DomainIo *io, uint64_t now);
void ios_state_observer_tick(DomainIo *io, uint64_t now, int screen, int current,
                             int target, int poll_ms, float lux, float smooth);
void ios_state_observer_wait(DomainIo *io, unsigned delay_ms, int screen, int current,
                             int target, int poll_ms, float lux, float smooth);
