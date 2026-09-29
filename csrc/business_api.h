/* Business operations. Legacy register adapters are kept only for differential tests. */
#pragma once
#include "domain_io.h"

struct IosState;
typedef struct IosStartup { uint32_t current, target; int32_t screen_on; float smooth_lux, raw_lux; } IosStartup;
IosStartup ios_business_startup(DomainIo *io, char scratch[256]);
void ios_business_sensor_init(DomainIo *io, void *scratch);
typedef struct IosSunlightResult { int32_t target; uint8_t previous_active; } IosSunlightResult;
IosSunlightResult ios_business_sunlight(struct IosState *state, DomainIo *io,
    uint64_t now_ms, float raw_lux, int32_t requested, bool sensor_invalid);
int32_t ios_business_thermal(struct IosState *state, DomainIo *io, int32_t requested);
int32_t ios_business_learning(struct IosState *state, DomainIo *io, float lux, int32_t base);
int32_t ios_business_debounce(struct IosState *state, uint64_t now_ms,
    int32_t requested, int32_t current, float lux, bool fast_dark);
uint64_t ios_battery_refresh(DomainIo *io, uint64_t now);
int ios_configuration_reload(DomainIo *io);
int ios_learning_load(DomainIo *io);
int ios_learning_save(DomainIo *io);
int ios_hbm_discover(DomainIo *io);
int ios_hbm_scan(DomainIo *io, const char *parent, int depth, int max_depth,
                 int *directories, int *entries);
unsigned ios_sensor_poll(DomainIo *io);
void ios_light_sensors_set(DomainIo *io, uint32_t enabled);
uint64_t ios_frame_apply(DomainIo *io, uint64_t now, int32_t *current,
                         int32_t target, bool emergency);
int ios_settings_refresh(DomainIo *io);
uint32_t ios_wakefulness_refresh(DomainIo *io);
uint32_t ios_display_refresh(DomainIo *io);
uint64_t ios_sunlight_deactivate(DomainIo *io, const char *reason);
float ios_fallback_lux_read(DomainIo *io, uint64_t *legacy_result);
