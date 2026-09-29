#include "scene_adaptation.h"
#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#else
#define EXPORT
#endif
/* Expose pure production diagnostics to the controlled full-main fixture. */
EXPORT const char *luma_test_scene_class(void) { return ios_scene_class(); }
EXPORT uint64_t luma_test_scene_hold_left(uint64_t now) { return ios_scene_hold_left(now); }
EXPORT int luma_test_scene_holding(void) { return ios_scene_holding(); }
