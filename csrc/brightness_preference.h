/* LumaCurve gradual preference learning; independent of the recovered state ABI. */
#pragma once
#include "domain_io.h"
extern int luma_preference_learning, luma_preference_revision;
extern float luma_preference_base;
void luma_preference_reset(void);
void luma_preference_configure(DomainIo *io);
void luma_preference_settings(DomainIo *io, uint64_t now, int mode, float adj, long slider);
int luma_preference_apply(DomainIo *io, uint64_t now, int target);
/* Pure getters, safe in state serialization. */
float luma_preference_offset(void);
float luma_preference_effective(void);
unsigned luma_preference_samples(void);
int luma_preference_pending(void);
int luma_preference_dirty(void);
const char *luma_preference_evidence(void);
float luma_preference_point(unsigned index, float baseline);
int luma_preference_anchor(void);
