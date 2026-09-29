/* Prefix human-readable console lines only. Hardware/state writes stay byte-identical. */
#pragma once
#include <stdio.h>
#include <time.h>
static inline int luma_log_timestamp(FILE *stream) {
    if (stream != stdout && stream != stderr) return 0;
    time_t stamp = time(NULL); struct tm broken; char text[32];
#ifdef _WIN32
    if (localtime_s(&broken, &stamp)) return -1;
#else
    if (!localtime_r(&stamp, &broken)) return -1;
#endif
    size_t n = strftime(text, sizeof(text), "[%Y-%m-%d %H:%M:%S] ", &broken);
    if (!n || fwrite(text, 1, n, stream) != n) return -1;
    return (int)n;
}
