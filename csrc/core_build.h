#pragma once
/* Local test build; publishing is a separate, explicitly approved action. */
#ifdef LUMA_FRAMEWORK_BACKEND
#define LUMA_CORE_BUILD "20260930-framework-core-test03"
#else
#define LUMA_CORE_BUILD "20260929-test03"
#endif
