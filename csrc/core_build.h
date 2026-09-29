#pragma once
/* Local test build; publishing is a separate, explicitly approved action. */
#ifdef LUMA_FRAMEWORK_BACKEND
#ifdef LUMA_FRAMEWORK_PRODUCTION_BUILD
#define LUMA_CORE_BUILD "20260930-framework-local01"
#else
#define LUMA_CORE_BUILD "20260930-framework-core-test04"
#endif
#else
#define LUMA_CORE_BUILD "20260929-test03"
#endif
