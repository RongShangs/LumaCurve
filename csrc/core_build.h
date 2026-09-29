#pragma once
/* Local test build; publishing is a separate, explicitly approved action. */
#ifdef LUMA_FRAMEWORK_BACKEND
#define LUMA_CORE_BUILD "20260929-framework-core-test01"
#else
#define LUMA_CORE_BUILD "20260929-test03"
#endif
