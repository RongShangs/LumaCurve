"""Compile production native policy on the host; no kernel/SELinux/device claims."""
from pathlib import Path
import subprocess
from native_host import compile_and_run
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
result=compile_and_run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-I',str(R/'experimental/refactor_hook/native'),str(p),'-o',str(exe)],exe)
print(result.stdout,end='')
