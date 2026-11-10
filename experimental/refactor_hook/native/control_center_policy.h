#ifndef HYPERLUX_CONTROL_CENTER_POLICY_H
#define HYPERLUX_CONTROL_CENTER_POLICY_H
#include "panel_policy.h"
#include <limits.h>
static inline int control_center_number(const char *text,int maximum,int *out){
 if(!*text)return 0;
 int value=0;
 for(;*text;text++){if(*text<'0'||*text>'9'||value>(maximum-(*text-'0'))/10)return 0;value=value*10+(*text-'0');if(value>maximum)return 0;}
 *out=value;return 1;
}
/* Fold identity and framework lease ownership are independent, composable gates. */
static inline int control_center_options(int argc,char **argv,int *pid,int *fold){
 *pid=0;*fold=-1;
 if(argc<4||argc>6||strcmp(argv[1],"--daemon")||strcmp(argv[2],"--node"))return 0;
 for(int i=4;i<argc;i++){
  if(!strncmp(argv[i],"--control-center=",17)){if(*pid||!control_center_number(argv[i]+17,INT_MAX,pid)||*pid<=1)return 0;}
  else if(!strncmp(argv[i],"--active-fold=",14)){if(*fold>=0||!control_center_number(argv[i]+14,7,fold))return 0;}
  else return 0;
 }
 return 1;
}
/* A failed write may not re-arm from its own unchanged renewal. Withdrawal or
 * replacement is required; the Root listener itself remains alive. */
static int control_center_after_failure(const struct panel_lease *lease,int readable,char *failed){
 if(!readable||!lease->active||strcmp(lease->token,failed))failed[0]=0;
 return readable&&!failed[0];
}
/* Root opts in and binds this listener to one existing framework process. */
static int control_center_admit(const struct panel_lease *lease,int framework_pid,int maximum,int64_t now){
 return framework_pid>1&&lease->pid==framework_pid&&lease->active&&!strncmp(lease->token,"cc_",3)&&
        panel_token(lease->token)&&lease->target>=10&&lease->target<=maximum&&
        lease->expires>now&&lease->expires<=now+PANEL_LEASE_MS;
}
#endif
