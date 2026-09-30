#pragma once
/* Local test build; publishing is a separate, explicitly approved action. */
#ifdef LUMA_FRAMEWORK_BACKEND
#ifdef LUMA_FRAMEWORK_PRODUCTION_BUILD
#ifdef LUMA_FRAMEWORK_OFFICIAL_BUILD
#define LUMA_CORE_BUILD "20260930-release-1.0.0"
#else
#define LUMA_CORE_BUILD "20260930-audit-repair01"
#endif
#else
#define LUMA_CORE_BUILD "20260930-framework-core-test04"
#endif
#else
#define LUMA_CORE_BUILD "20260929-test03"
#endif
