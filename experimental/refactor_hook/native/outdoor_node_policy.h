#ifndef HYPERLUX_OUTDOOR_NODE_POLICY_H
#define HYPERLUX_OUTDOOR_NODE_POLICY_H
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
/* Cooperative output. The Java outdoor policy owns session duration. */
struct outdoor_lease { int pid,active,fraction,raw,target; int64_t until; uint64_t physical; };
struct outdoor_node_state { int pid,base,target,held,inhibited,observed; int64_t settle; };
static inline int outdoor_lease_parse(const char *text,int64_t now,struct outdoor_lease *l){
 int pid,active,value;long long until;char extra,version[3];
 if(strncmp(text,"v1 ",3)&&strncmp(text,"v2 ",3)&&strncmp(text,"v3 ",3))return 0;
 uint64_t physical=0;
 if(!strncmp(text,"v3 ",3)){char digits[20];if(sscanf(text,"%2s %d %lld %d %d %19[0-9] %c",version,&pid,&until,&value,&active,digits,&extra)!=6)return 0;physical=strtoull(digits,NULL,10);if(!physical||physical>INT64_MAX)return 0;}
 else if(sscanf(text,"%2s %d %lld %d %d %c",version,&pid,&until,&value,&active,&extra)!=5)return 0;
 if(pid<=0||until<=now||until-now>1500||(active!=0&&active!=1))return 0;
 int raw=!strcmp(version,"v2")||!strcmp(version,"v3");
 if(raw?(value!=0&&(value<10||value>65535)):(strcmp(version,"v1")||value<0||value>1000))return 0;
 memset(l,0,sizeof(*l));l->pid=pid;l->until=until;l->fraction=raw?1000:value;l->raw=raw;l->target=raw?value:0;l->active=active;l->physical=physical;return 1;
}
struct outdoor_fold_ids{uint64_t values[8];int count;};
static inline int outdoor_fold_ids_parse(const char *text,struct outdoor_fold_ids *ids){
 memset(ids,0,sizeof(*ids));const char *p=text;
 while(*p){if(ids->count==8)return 0;const char *start=p;while(*p>='0'&&*p<='9')p++;size_t n=(size_t)(p-start);if(n==0||n>19||(*p&&*p!=','))return 0;
  char digits[20];memcpy(digits,start,n);digits[n]=0;uint64_t v=strtoull(digits,NULL,10);if(!v||v>INT64_MAX)return 0;for(int i=0;i<ids->count;i++)if(ids->values[i]==v)return 0;ids->values[ids->count++]=v;
  if(*p==','){p++;if(!*p)return 0;}
 }return ids->count>=2;
}
static inline int outdoor_fold_authorized(const struct outdoor_fold_ids *ids,const struct outdoor_lease *lease){
 if(!lease||!lease->physical)return 0;
 for(int i=0;i<ids->count;i++)if(ids->values[i]==lease->physical)return 1;
 return 0;
}
/* 0: wait without touching the previous panel; 1: same binding; 2: new binding. */
static inline int outdoor_fold_decide(int bound_port,uint64_t physical,int port,const struct outdoor_lease *lease,int live){
 if(port<0||port>7)return 0;
 if(live&&lease&&lease->active){
  if(!lease->physical)return 0;
  if(physical&&((port!=bound_port)!=(lease->physical!=physical)))return 0;
  return port!=bound_port||lease->physical!=physical?2:1;
 }
 return port==bound_port?1:0;
}
/* Returns a proposed node write, or -1. No write permissions/mode isolation. */
static inline int outdoor_node_step(struct outdoor_node_state *s,const struct outdoor_lease *l,int current,int maximum,int manual,int64_t now){
 if(!l||!l->active||(!l->raw&&!l->fraction)||manual){
  int restore=s->held&&!manual&&current==s->target?s->base:-1;
  memset(s,0,sizeof(*s));return restore;
 }
 if(s->pid&&s->pid!=l->pid){int restore=s->held&&current==s->target?s->base:-1;memset(s,0,sizeof(*s));return restore;}
 s->pid=l->pid;
 if(s->held&&current!=s->target){s->held=0;s->inhibited=1;return -1;}
 if(s->inhibited)return -1;
 if(!s->held){if(current<10||current>=maximum||maximum>65535||maximum<=10)return -1;
  if(!s->settle||s->observed!=current){s->observed=current;s->settle=now;return -1;}if(now-s->settle<500)return -1;s->base=current;}
 int target=l->raw?(l->target?l->target:maximum):s->base+(maximum-s->base)*l->fraction/1000;
 if(target>maximum)target=maximum;
 if(target<=s->base){if(s->held){s->held=0;s->settle=0;return s->base;}return -1;}
 if(s->held&&target==s->target)return -1;
 s->target=target;s->held=1;return target;
}
#endif
