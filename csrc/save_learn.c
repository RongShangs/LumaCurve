#include "learning.h"
#include "business_api.h"
int ios_learning_save(DomainIo *io) {
    uintptr_t stream = domain_open(io, "/data/local/tmp/luma_curve_learn", "w");
    if (!stream)
        return 0;
    PRINT(io, stream, "version=%d\n", LEARN_VERSION);
    for (unsigned lux_bucket = 0; lux_bucket < LEARN_LUX_BUCKETS; lux_bucket++) {
        for (unsigned time_bucket = 0; time_bucket < LEARN_TIME_BUCKETS; time_bucket++) {
            LearnCell cell;
            size_t index = lux_bucket * LEARN_TIME_BUCKETS + time_bucket;
            memcpy(&cell, ADDRESS(g_learn) + index * sizeof(cell), sizeof(cell));
            if (!(cell.confidence > 0.001f))
                continue;
            domain_print(io, stream, "%d %d %.1f %d %.4f %ld\n",
                         (uint64_t[]){lux_bucket, time_bucket, (uint32_t)cell.slider,
                                      (uint64_t)cell.timestamp},
                         4, (double[]){cell.lux, cell.confidence}, 2);
        }
    }
    domain_print(io, stream, "global_offset=%.6f\n", NULL, 0, (double[]){FLOAT(g_global_offset)},
                 1);
    int result = (int)CALL(io, fclose, stream);
    SET_FLAG(g_learn_dirty, 0);
    return result;
}
#ifndef IOS_PRODUCTION
void ios_save_learn(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = (uint32_t)ios_learning_save(&io);
}
#endif
