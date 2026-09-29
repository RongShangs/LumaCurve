/* Explicit machine-level C semantics used during complete source recovery. */
#pragma once
#ifdef IOS_PRODUCTION
#include "native_runtime.h"
#else
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <math.h>
#include <limits.h>
#include <stdlib.h>

typedef union IosVec {
    uint64_t u64[2]; uint32_t u32[4]; uint16_t u16[8]; uint8_t u8[16];
    float f32[4]; double f64[2];
} IosVec;
typedef struct IosCpu {
    uint64_t x[31]; uint64_t sp; IosVec v[32];
    uint32_t n,z,c,vflag;
} IosCpu;
extern const uint8_t ios_ro[14281];
extern uint8_t ios_ram[10888];
void ios_reset_data(void);
void ios_import(IosCpu *r, unsigned kind);
void ios_indirect(IosCpu *r, uint64_t address);
void ios_call(IosCpu *r, uint64_t address);
#ifdef IOS_TRACE
void ios_trace(IosCpu *r,uint64_t address);
#else
#define ios_trace(r,address) ((void)0)
#endif

enum IosCondition { IOS_EQ,IOS_NE,IOS_HS,IOS_LO,IOS_MI,IOS_PL,IOS_VS,IOS_VC,IOS_HI,IOS_LS,IOS_GE,IOS_LT,IOS_GT,IOS_LE,IOS_AL };
static inline int ios_cond(const IosCpu *r,enum IosCondition c) {
    switch(c) {
    case IOS_EQ:return r->z; case IOS_NE:return !r->z;
    case IOS_HS:return r->c; case IOS_LO:return !r->c;
    case IOS_MI:return r->n; case IOS_PL:return !r->n;
    case IOS_VS:return r->vflag; case IOS_VC:return !r->vflag;
    case IOS_HI:return r->c&&!r->z; case IOS_LS:return !r->c||r->z;
    case IOS_GE:return r->n==r->vflag; case IOS_LT:return r->n!=r->vflag;
    case IOS_GT:return !r->z&&r->n==r->vflag; case IOS_LE:return r->z||r->n!=r->vflag;
    default:return 1;
    }
}
static inline void ios_nzcv(IosCpu *r,unsigned flags) { r->n=(flags>>3)&1;r->z=(flags>>2)&1;r->c=(flags>>1)&1;r->vflag=flags&1; }
static inline uint64_t ios_mask(uint64_t v,unsigned w) { return w==32?(uint32_t)v:v; }
static inline uint64_t ios_sub_flags(IosCpu *r,uint64_t a,uint64_t b,unsigned w) {
    a=ios_mask(a,w);b=ios_mask(b,w);uint64_t v=ios_mask(a-b,w);
    r->n=(v>>(w-1))&1;r->z=v==0;r->c=a>=b;r->vflag=(((a^b)&(a^v))>>(w-1))&1;return v;
}
static inline uint64_t ios_add_flags(IosCpu *r,uint64_t a,uint64_t b,unsigned w) {
    a=ios_mask(a,w);b=ios_mask(b,w);uint64_t v=ios_mask(a+b,w);
    r->n=(v>>(w-1))&1;r->z=v==0;r->c=w==32?((a+b)>>32)!=0:v<a;r->vflag=((~(a^b)&(a^v))>>(w-1))&1;return v;
}
static inline void ios_logic_flags(IosCpu *r,uint64_t v,unsigned w) { v=ios_mask(v,w);r->n=(v>>(w-1))&1;r->z=v==0;r->c=r->vflag=0; }
static inline void ios_float_flags(IosCpu *r,double a,double b) {
    if(isnan(a)||isnan(b))ios_nzcv(r,3);else if(a==b)ios_nzcv(r,6);else if(a<b)ios_nzcv(r,8);else ios_nzcv(r,2);
}
static inline uint64_t ios_load(uint64_t address,unsigned size) {
    /* Volatile byte accesses also preserve signal-handler flag visibility. */
    volatile const uint8_t *p=(volatile const uint8_t *)(uintptr_t)address;uint64_t value=0;
    for(unsigned i=0;i<size;i++)value|=(uint64_t)p[i]<<(8*i);return value;
}
static inline void ios_store(uint64_t address,uint64_t value,unsigned size) {
    volatile uint8_t *p=(volatile uint8_t *)(uintptr_t)address;
    for(unsigned i=0;i<size;i++)p[i]=(uint8_t)(value>>(8*i));
}
static inline void ios_bits32(IosCpu *r,unsigned n,uint32_t value) { r->v[n].u64[0]=value;r->v[n].u64[1]=0; }
static inline void ios_bits64(IosCpu *r,unsigned n,uint64_t value) { r->v[n].u64[0]=value;r->v[n].u64[1]=0; }
static inline void ios_f32(IosCpu *r,unsigned n,float value) { uint32_t b;memcpy(&b,&value,4);ios_bits32(r,n,b); }
static inline void ios_f64(IosCpu *r,unsigned n,double value) { uint64_t b;memcpy(&b,&value,8);ios_bits64(r,n,b); }
static inline void ios_loadq(IosCpu *r,unsigned n,uint64_t a) { IosVec t;t.u64[0]=ios_load(a,8);t.u64[1]=ios_load(a+8,8);r->v[n]=t; }
static inline void ios_storeq(IosCpu *r,unsigned n,uint64_t a) { ios_store(a,r->v[n].u64[0],8);ios_store(a+8,r->v[n].u64[1],8); }
static inline uint64_t ios_sdiv(uint64_t a,uint64_t b,unsigned w) {
    if(w==32){int32_t x=(int32_t)a,y=(int32_t)b;if(!y)return 0;if(x==INT32_MIN&&y==-1)return (uint32_t)x;return (uint32_t)(x/y);}
    int64_t x=(int64_t)a,y=(int64_t)b;if(!y)return 0;if(x==INT64_MIN&&y==-1)return (uint64_t)x;return (uint64_t)(x/y);
}
static inline uint64_t ios_sign_extend(uint64_t value,unsigned w) { if(w==64)return value;return (uint64_t)((int64_t)(value<<(64-w))>>(64-w)); }
static inline uint64_t ios_float_int(double value,unsigned w) {
    if(isnan(value))return 0;
    if(w==32){if(value>=2147483648.0)return INT32_MAX;if(value<=-2147483648.0)return (uint32_t)INT32_MIN;return (uint32_t)(int32_t)value;}
    if(value>=9223372036854775808.0)return INT64_MAX;if(value<=-9223372036854775808.0)return (uint64_t)INT64_MIN;return (uint64_t)(int64_t)value;
}
static inline void ios_dup(IosCpu *r,unsigned d,unsigned s,unsigned lane) { uint32_t value=r->v[s].u32[lane];for(unsigned i=0;i<4;i++)r->v[d].u32[i]=value; }
static inline void ios_vec_fgt(IosCpu *r,unsigned d,unsigned a,unsigned b) { IosVec t;for(unsigned i=0;i<4;i++)t.u32[i]=r->v[a].f32[i]>r->v[b].f32[i]?UINT32_MAX:0;r->v[d]=t; }
static inline void ios_uzp(IosCpu *r,unsigned d,unsigned a,unsigned b,unsigned bytes) {
    IosVec t;unsigned lanes=16/bytes;for(unsigned i=0;i<lanes/2;i++){
        memcpy(t.u8+i*bytes,r->v[a].u8+2*i*bytes,bytes);memcpy(t.u8+(i+lanes/2)*bytes,r->v[b].u8+2*i*bytes,bytes);
    }r->v[d]=t;
}
static inline void ios_xtn(IosCpu *r,unsigned d,unsigned s) { IosVec t=r->v[d];for(unsigned i=0;i<4;i++)t.u16[i+4]=(uint16_t)r->v[s].u32[i];r->v[d]=t; }
static inline void ios_tbl(IosCpu *r,unsigned d,unsigned a,unsigned b) { IosVec t={{0}};for(unsigned i=0;i<8;i++){unsigned j=r->v[b].u8[i];t.u8[i]=j<16?r->v[a].u8[j]:0;}r->v[d]=t; }
static inline void ios_vec_shl(IosCpu *r,unsigned d,unsigned a,unsigned shift) { IosVec t={{0}};for(unsigned i=0;i<8;i++)t.u8[i]=(uint8_t)(r->v[a].u8[i]<<shift);r->v[d]=t; }
static inline void ios_vec_negative(IosCpu *r,unsigned d,unsigned a) { IosVec t={{0}};for(unsigned i=0;i<8;i++)t.u8[i]=(int8_t)r->v[a].u8[i]<0?255:0;r->v[d]=t; }
static inline void ios_vec_max(IosCpu *r,unsigned d,unsigned a) { unsigned v=0;for(unsigned i=0;i<8;i++)if(r->v[a].u8[i]>v)v=r->v[a].u8[i];ios_bits32(r,d,v); }
#endif
