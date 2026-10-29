"""Compile production native policy on the host; no kernel/SELinux/device claims."""
from pathlib import Path
import subprocess,re
R=Path(__file__).resolve().parents[1];O=R/'build/native-panel-tests';O.mkdir(parents=True,exist_ok=True)
p=O/'test.c'
p.write_text(r'''
#include <assert.h>
#include <stdio.h>
#include "panel_policy.h"
static int cases;
#define CHECK(x) do {if(!(x)){fprintf(stderr,"case %d\n",cases);return 1;}cases++;}while(0)
int main(void){
 CHECK(!panel_output_live(-1));CHECK(!panel_output_live(0));CHECK(panel_output_live(1));CHECK(panel_output_live(16383));
 struct panel_policy p;panel_clear(&p);
 CHECK(p.target==-1&&!panel_live(&p,0));
 CHECK(!panel_arm(&p,"",1,0));CHECK(!panel_arm(&p,"bad token",1,0));CHECK(!panel_arm(&p,"valid",0,0));
 CHECK(panel_arm(&p,"A-1_",456,1000));CHECK(p.expires==9000);
 CHECK(panel_live(&p,8999));CHECK(!panel_live(&p,9000));
 CHECK(!panel_set(&p,"A-1_",4000,16383,9000));
 CHECK(panel_arm(&p,"old",456,0));
 int invalid[]={-1,0,9,16384,65536};
 for(unsigned i=0;i<sizeof(invalid)/sizeof(invalid[0]);i++)CHECK(!panel_set(&p,"old",invalid[i],16383,1));
 int allowed[]={10,1000,4000,8192,16383};
 for(unsigned i=0;i<sizeof(allowed)/sizeof(allowed[0]);i++){CHECK(panel_set(&p,"old",allowed[i],16383,1));CHECK(p.target==allowed[i]);}
 CHECK(!panel_set(&p,"stale",10000,16383,1));
 CHECK(panel_arm(&p,"new",456,2000));CHECK(p.target==16383);
 CHECK(!panel_set(&p,"old",10,16383,2001));CHECK(panel_set(&p,"new",10000,16383,2001));
 CHECK(!panel_keep(&p,"old",456,2001));CHECK(!panel_keep(&p,"new",457,2001));
 CHECK(panel_keep(&p,"new",456,9999));CHECK(p.expires==17999);CHECK(!panel_keep(&p,"new",456,17999));
 char message[128];CHECK(panel_health(message,sizeof(message),&p,1,0,9999)>0);CHECK(!strcmp(message,"new 1 0 9999\n"));
 CHECK(panel_health(message,sizeof(message),&p,0,13,10000)>0);CHECK(!strcmp(message,"new 0 13 10000\n"));
 panel_clear(&p);CHECK(!panel_set(&p,"new",500,16383,10000));CHECK(!panel_live(&p,10000));
 CHECK(panel_arm(&p,"driver65535",456,0));CHECK(panel_set(&p,"driver65535",65535,65535,1));
 CHECK(!panel_set(&p,"driver65535",65536,65535,1));
 struct panel_lease l;CHECK(panel_read_lease("session 123 8000 1 16383\n",16383,1,&l));CHECK(l.pid==123&&l.active&&l.target==16383&&l.expires==8000);
 CHECK(panel_read_lease("session 123 8000 0 16383\n",16383,1,&l));CHECK(!l.active&&l.target==16383);
 const char *bad[]={"session 123 8000", "session 123 8000 2 100", "session 0 8000 1 100", "session 123 8000 1 9", "session 123 8000 1 16384", "session 123 9000 1 100", "session 123 1 1 100", "session 123 8000 1 100 extra"};
 for(unsigned i=0;i<sizeof(bad)/sizeof(bad[0]);i++)CHECK(!panel_read_lease(bad[i],16383,1,&l));
 p.paused=1;CHECK(!panel_set(&p,"driver65535",100,65535,1));p.paused=0;CHECK(panel_set(&p,"driver65535",100,65535,1));
 puts("Native panel policy: PASS");
 printf("Native panel: %d cases PASS; host policy and wire format, Android kernel not tested\n",cases);
}
''',encoding='utf-8')
exe=O/'test.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-I',str(R/'experimental/refactor_hook/native'),str(p),'-o',str(exe)],check=True)
baseline=subprocess.run([str(exe)],check=True,capture_output=True,text=True)
print(baseline.stdout.replace('Native panel:','Native panel baseline:'),end='')
base_cases=int(re.search(r'Native panel: (\d+) cases PASS',baseline.stdout).group(1))
# Extract the real production write boundary, not a copy of its cancellation rule.
native=(R/'experimental/refactor_hook/native/main_panel.c').read_text('utf-8')
begin=native.index('static int write_output(int value){');end=native.index('\n}',begin)+2
function=native[begin:end]
harness=r'''
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include "panel_policy.h"
#ifndef ESTALE
#define ESTALE 116
#endif
static struct panel_policy state;
static const char *node="node";
static int control_center_listener,last_error,active_fold_port=-1,output=10,attempts,successes;
static int present,matching_pid,matching_token,live,actual,acquire_failure,write_failure,readback_failure,late_change,reads,writes,releases;
static int read_lease(struct panel_lease *l){reads++;memset(l,0,sizeof(*l));l->pid=matching_pid?7:8;strcpy(l->token,matching_token?"cc_good":"cc_other");l->active=1;l->target=late_change&&reads>1?8000:9000;return present;}
static int same_node(void){return live;}
static int number(const char *p){(void)p;return actual;}
static int fold_panel_live(const char *p,int n){(void)p;(void)n;return 1;}
static int acquire_output(void){if(acquire_failure){errno=EACCES;return -1;}return 0;}
static int release_output(void){releases++;return 0;}
static long fake_lseek(int fd,long offset,int mode){(void)fd;(void)offset;(void)mode;return 0;}
static int fake_write(int fd,const char *b,size_t n){(void)fd;if(write_failure){errno=EIO;return -1;}writes++;if(!readback_failure)actual=atoi(b);return (int)n;}
#define lseek fake_lseek
#define write fake_write
#ifndef SEEK_SET
#define SEEK_SET 0
#endif
'''
harness=harness.replace('#include <errno.h>','#include <errno.h>\n#include <stdlib.h>')+function+r'''
static int cases;
#define CHECK(x) do{if(!(x)){fprintf(stderr,"lease race case %d\n",cases);return 1;}cases++;}while(0)
static void reset(int follow){panel_clear(&state);panel_arm(&state,"cc_good",7,0);control_center_listener=follow;present=matching_pid=matching_token=live=1;actual=1000;last_error=attempts=successes=acquire_failure=write_failure=readback_failure=late_change=reads=writes=releases=0;}
int main(void){
 for(int follow=0;follow<=1;follow++)for(int variant=0;variant<3;variant++){
  reset(follow);if(variant==0)present=0;if(variant==1)matching_pid=0;if(variant==2)matching_token=0;
  CHECK(write_output(9000)==(follow?1:-1));CHECK(writes==0&&attempts==0);CHECK(last_error==(follow?0:ECANCELED));
  if(follow){present=matching_pid=matching_token=1;CHECK(write_output(9000)==0);CHECK(writes==1&&actual==9000&&last_error==0);}
 }
 for(int follow=0;follow<=1;follow++){
  reset(follow);live=0;CHECK(write_output(9000)<0&&last_error==ESTALE&&writes==0);
  reset(follow);acquire_failure=1;CHECK(write_output(9000)<0&&last_error==EACCES&&writes==0);
  reset(follow);write_failure=1;CHECK(write_output(9000)<0&&last_error==EIO&&writes==0);
  reset(follow);readback_failure=1;CHECK(write_output(9000)<0&&last_error==ERANGE);
  reset(follow);actual=0;CHECK(write_output(9000)==1&&writes==0);
  reset(follow);CHECK(write_output(8000)==1&&writes==0);
  reset(follow);late_change=1;CHECK(write_output(9000)==1&&writes==0&&releases==1&&last_error==0);
 }
 printf("Lease revocation race: %d cases PASS; real write_output, syscall doubles\n",cases);return 0;
}
'''
c=O/'lease-race.c';c.write_text(harness,'utf-8');race_exe=O/'lease-race.exe'
command=['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-Wno-unused-function','-I',str(R/'experimental/refactor_hook/native'),str(c),'-o',str(race_exe)]
subprocess.run(command,check=True)
result=subprocess.run([str(race_exe)],check=True,capture_output=True,text=True);print(result.stdout,end='')
race_cases=int(re.search(r'(\d+) cases PASS',result.stdout).group(1))
# Negative control: restoring the old fatal treatment reproduces the stuck listener.
assert 'if(control_center_listener)return 1;' in harness
c.write_text(harness.replace('if(control_center_listener)return 1;',''),'utf-8');subprocess.run(command,check=True)
bad=subprocess.run([str(race_exe)],capture_output=True,text=True);assert bad.returncode!=0 and 'lease race case' in bad.stderr
c.write_text(harness,'utf-8');print('Old fatal lease cancellation reproduced: PASS')
print(f'Native panel: {base_cases+race_cases} cases PASS; host policy and production write boundary, Android kernel not tested')
