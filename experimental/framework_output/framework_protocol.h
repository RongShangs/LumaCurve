#pragma once
#include "framework_backend.h"
#include <math.h>
#include <stdio.h>
#include <string.h>
/* Invalid or missing feedback is unknown, never manual or success. */
static inline int luma_framework_parse(const char *text,LumaFrameworkSnapshot *v) {
    char extra;
    if(sscanf(text,"OK %d %f %d %d %d %d %f %f %f %f %f %f %f %d %c",&v->mode,&v->adjustment,&v->slider,&v->on,&v->window,&v->node,
        &v->minimum,&v->maximum,&v->base,&v->adjusted,&v->goal,&v->limited,&v->request,&v->active,&extra)!=14||
       (v->mode!=0&&v->mode!=1)||(v->on!=0&&v->on!=1)||(v->window!=0&&v->window!=1)||(v->active!=0&&v->active!=1)||
       v->slider<0||v->node<0||v->node>16383||!isfinite(v->adjustment)||fabsf(v->adjustment)>1||
       !isfinite(v->minimum)||!isfinite(v->maximum)||v->minimum<0||v->maximum>1||v->minimum>=v->maximum||
       !isfinite(v->base)||!isfinite(v->adjusted)||!isfinite(v->goal)||!isfinite(v->limited)||!isfinite(v->request)||
       v->goal< -1||v->goal>1||v->limited< -1||v->limited>1||v->request<0||v->request>1)return -1;
    return 0;
}
static inline const char *luma_framework_map_path(const char *root,const char *path,char *buffer,size_t capacity) {
    const char *prefix="/data/local/tmp/luma_curve";
    if(strncmp(path,prefix,strlen(prefix)))return path;
    const char *name=strrchr(path,'/')+1;
    if(!root||strstr(path+strlen(prefix),".."))return NULL;
    int n=snprintf(buffer,capacity,"%s/%s",root,name);
    return n<0||(size_t)n>=capacity?NULL:buffer;
}
