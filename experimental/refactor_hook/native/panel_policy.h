#ifndef HYPERLUX_PANEL_POLICY_H
#define HYPERLUX_PANEL_POLICY_H
#include <stdint.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>
/* Never revive an extinguished panel (screen off or early AOD transition). */
static inline int panel_output_live(int actual){return actual>0;}
#define PANEL_LEASE_MS 8000
struct panel_policy { char token[81]; int pid, target, armed, paused; int64_t expires; };
struct panel_lease { char token[81];int pid,active,target;int64_t expires; };
static int panel_token(const char *s) {
 size_t n=strlen(s); if(!n||n>80)return 0;
 for(size_t i=0;i<n;i++)if(!isalnum((unsigned char)s[i])&&s[i]!='-'&&s[i]!='_')return 0;
 return 1;
}
static int panel_live(struct panel_policy *p,int64_t now){return p->armed&&now<p->expires;}
static inline int panel_read_lease(const char *text,int maximum,int64_t now,struct panel_lease *l){
 char extra;long long expiry;int active,pid,target;char token[81];
 if(sscanf(text,"%80s %d %lld %d %d %c",token,&pid,&expiry,&active,&target,&extra)!=5||!panel_token(token)||pid<=0||(active!=0&&active!=1)||target<10||target>maximum||expiry<=now||expiry-now>PANEL_LEASE_MS)return 0;
 strcpy(l->token,token);l->pid=pid;l->active=active;l->target=target;l->expires=expiry;return 1;
}
static int panel_arm(struct panel_policy *p,const char *s,int pid,int64_t now){
 if(!panel_token(s)||pid<=0)return 0;
 strcpy(p->token,s);p->pid=pid;p->armed=1;p->paused=0;p->expires=now+PANEL_LEASE_MS;return 1;
}
static inline int panel_keep(struct panel_policy *p,const char *s,int pid,int64_t now){
 if(!panel_live(p,now)||strcmp(p->token,s)||pid!=p->pid)return 0;
 p->expires=now+PANEL_LEASE_MS;return 1;
}
static int panel_set(struct panel_policy *p,const char *s,int value,int max,int64_t now){
 if(!panel_live(p,now)||p->paused||strcmp(p->token,s)||value<10||value>max)return 0;
 p->target=value;return 1;
}
static void panel_clear(struct panel_policy *p){memset(p,0,sizeof(*p));p->target=-1;}
static inline int panel_health(char *out,size_t size,const struct panel_policy *p,int active,int error,int64_t now){return snprintf(out,size,"%s %d %d %lld\n",p->token,active,error,(long long)now);}
#endif
