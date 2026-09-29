/* Parse into a temporary table, then commit only a valid learning file. */
#include "learning.h"
#include "business_api.h"
int ios_learning_load(DomainIo *io) {
    uintptr_t stream = domain_open(io, "/data/local/tmp/luma_curve_learn", "r");
    if (!stream)
        return 0;
    LearnCell pending[LEARN_LUX_BUCKETS][LEARN_TIME_BUCKETS] = {0};
    char line[192] = {0};
    int version = 0;
    float global_offset = 0;
    unsigned line_number = 0;
    bool valid = true;
    while (CALL(io, fgets, ARG(line), sizeof(line), stream)) {
        line_number++;
        if (line_number == 1) {
            if (IOS_SCAN_TEXT(io, line, "version=%d", &version) != 1 ||
                version != LEARN_VERSION) {
                valid = false;
                break;
            }
            continue;
        }
        if (line[0] == '\0' || line[0] == '\n' || line[0] == '#')
            continue;
        if (!strncmp(line, "global_offset=", 14)) {
            if (IOS_SCAN_TEXT(io, line + 14, "%f", &global_offset) != 1 ||
                !isfinite(global_offset))
                valid = false;
            continue;
        }
        int lux_bucket = 0, time_bucket = 0;
        LearnCell cell = {0};
        int fields = (int)IOS_SCAN_TEXT(io, line, "%d %d %f %d %f %ld", &lux_bucket, &time_bucket, &cell.lux, &cell.slider, &cell.confidence, &cell.timestamp);
        if (fields != 6 || lux_bucket < 0 || lux_bucket >= LEARN_LUX_BUCKETS || time_bucket < 0 ||
            time_bucket >= LEARN_TIME_BUCKETS || !isfinite(cell.lux) || cell.lux < 0 ||
            cell.slider < 0 || cell.slider > 255 || !isfinite(cell.confidence) ||
            cell.confidence < 0 || cell.confidence > 1 || cell.timestamp < 0) {
            valid = false;
            break;
        }
        pending[lux_bucket][time_bucket] = cell;
    }
    CALL(io, fclose, stream);
    if (!valid) {
        PRINT(io, domain_stdout(), "[%s] Learn reload rejected: version=%d line=%d\n",
              ARG("LumaCurve"), (uint32_t)version, line_number);
        return 0;
    }
    memcpy(ADDRESS(g_learn), pending, sizeof(pending));
    global_offset = global_offset > 0.15f ? 0.15f : global_offset;
    global_offset = global_offset < -0.15f ? -0.15f : global_offset;
    SET_FLOAT(g_global_offset, global_offset);
    SET_FLAG(g_learn_dirty, 0);
    domain_print(io, domain_stdout(), "[%s] Learn v%d: global=%.2f\n",
                 (uint64_t[]){ARG("LumaCurve"), LEARN_VERSION}, 2, (double[]){global_offset}, 1);
    return 1;
}
#ifndef IOS_PRODUCTION
void ios_load_learn(IosCpu *caller) {
    DomainIo io = {0};
    caller->x[0] = (uint32_t)ios_learning_load(&io);
}
#endif
