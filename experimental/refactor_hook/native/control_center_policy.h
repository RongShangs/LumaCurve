#ifndef HYPERLUX_CONTROL_CENTER_POLICY_H
#define HYPERLUX_CONTROL_CENTER_POLICY_H
#include "panel_policy.h"
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
