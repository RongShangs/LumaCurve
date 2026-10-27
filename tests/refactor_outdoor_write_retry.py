"""Production native write loop with deterministic competing writers and lease revocation."""
from pathlib import Path
import subprocess,re,ast,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/outdoor-write-retry-tests';O.mkdir(exist_ok=True)
native=(R/'experimental/refactor_hook/native/outdoor_node.h').read_text(encoding='utf-8')
production=native.split('/* OUTDOOR_WRITE_BEGIN: exercised with host I/O doubles. */')[1].split('/* OUTDOOR_WRITE_END */')[0]
prefix=r'''
#include <errno.h>
#include <time.h>
#include <stdint.h>
#include <unistd.h>
#include "outdoor_node_policy.h"
static int current,maximum,failures,mode,calls,waits,stopping,revoked,locked,folded,live,same;
static int outdoor_attempt_errno,outdoor_report_errno,outdoor_write_attempts;
static const char *outdoor_attempt_stage="none",*outdoor_report_stage="none";
static const char *node="node",*max_node="max";static int active_fold_port;
static struct outdoor_lease lease;
static int number(const char *p){return p==max_node?maximum:current;}
static int outdoor_lock(void){return locked?-1:10;}
static int outdoor_folded(void){return folded;}
static int fold_panel_live(const char *p,int port){(void)p;(void)port;return live;}
static int same_node(void){return same;}
static int open_output(void){if(mode==3){errno=EACCES;return -1;}return 11;}
static int fake_close(int fd){(void)fd;return 0;}
static int outdoor_read(struct outdoor_lease *out){*out=lease;return !revoked;}
static int outdoor_process(int pid){return pid==123;}
static ssize_t fake_write(int fd,const void *b,size_t n){(void)fd;calls++;
 if(calls<=failures){errno=EBUSY;return -1;}
 if(mode==1){current=777;return (ssize_t)n;}
 if(mode==2||mode==16)return (ssize_t)n; // Accepted, asynchronously pending.
 current=atoi(b);return (ssize_t)n;
}
static int fake_sleep(const struct timespec *delay,struct timespec *remaining){(void)remaining;waits++;
 if(delay->tv_nsec!=80000000L&&delay->tv_nsec!=160000000L)return -1;
 if(mode==4)revoked=1;
 if(mode==5)current=777;
 if(mode==6)locked=1;
 if(mode==7)live=0;
 if(mode==8)lease.target=2000;
 if(mode==9)lease.pid=456;
 if(mode==10)lease.physical++;
 if(mode==11)stopping=1;
 if(mode==12)lease.active=0;
 if(mode==13)same=0;
 if(mode==14||mode==16)current=4095;
 if(mode==15)maximum=2000;
 return 0;
}
#define write fake_write
#define close fake_close
#define nanosleep fake_sleep
'''
driver=r'''
static int cases;
#define CHECK(x) do{if(!(x)){fprintf(stderr,"case %d at %d\n",cases,__LINE__);return 1;}cases++;}while(0)
static void reset(void){current=2047;maximum=4095;failures=mode=calls=waits=stopping=revoked=locked=folded=0;live=same=1;lease=(struct outdoor_lease){.pid=123,.active=1,.raw=1,.target=0,.fraction=1000,.until=2500};}
int main(void){
 for(int count=0;count<6;count++){
  reset();failures=count;int result=outdoor_write(2047,4095,&lease);
  CHECK(result==(count<3?0:-1));CHECK(calls==(count<3?count+1:3));CHECK(waits==(count<3?count:2));CHECK(outdoor_write_attempts==calls);
  CHECK(current==(count<3?4095:2047));if(count>=3)CHECK(outdoor_report_errno==EBUSY&&!strcmp(outdoor_report_stage,"write"));
 }
 for(int m=4;m<=15;m++){
  reset();mode=m;failures=1;if(m==14)continue;if(m==7)folded=1;
  CHECK(outdoor_write(2047,4095,&lease)==(m==5?2:1));CHECK(calls==1&&waits==1);CHECK(current==(m==5?777:2047));
 }
 reset();mode=3;CHECK(outdoor_write(2047,4095,&lease)==-1);CHECK(calls==0&&waits==0&&outdoor_write_attempts==1&&outdoor_report_errno==EACCES);
 reset();mode=1;CHECK(outdoor_write(2047,4095,&lease)==2);CHECK(calls==1&&waits==0&&current==777); // Never fight a competing writer.
 reset();mode=2;CHECK(outdoor_write(2047,4095,&lease)==-1);CHECK(calls==3&&waits==2&&outdoor_report_errno==EAGAIN);
 reset();mode=14;failures=1;CHECK(outdoor_write(2047,4095,&lease)==2);CHECK(calls==1&&waits==1&&current==4095);
 reset();CHECK(outdoor_write(2047,4095,NULL)==1&&calls==0);
 reset();revoked=1;CHECK(outdoor_write(2047,4095,&lease)==1&&calls==0);
 reset();locked=1;CHECK(outdoor_write(2047,4095,&lease)==1&&calls==0&&waits==0);
 reset();mode=16;CHECK(outdoor_write(2047,4095,&lease)==0&&calls==1&&waits==1&&outdoor_report_errno==0);
 reset();current=0;CHECK(outdoor_write(2047,4095,&lease)==2&&calls==0);
 reset();folded=1;live=0;CHECK(outdoor_write(2047,4095,&lease)==1&&calls==0);
 reset();maximum=3000;CHECK(outdoor_write(2047,4095,&lease)==1&&calls==0);
 reset();current=4095;stopping=1;CHECK(outdoor_write(4095,2047,&lease)==0&&current==2047&&calls==1); // Shutdown restores owned output.
 reset();current=4095;failures=3;CHECK(outdoor_write(4095,2047,&lease)==-1&&calls==1&&waits==0); // Release never retries.
 reset();current=1000;CHECK(outdoor_write(4095,2047,&lease)==2&&calls==0); // Release must not overwrite a new owner.
 printf("Outdoor bounded write retry: %d cases PASS\n",cases);return 0;
}
'''
p=O/'retry.c';p.write_text(prefix+production+driver,encoding='utf-8');exe=O/'retry.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-I',str(R/'experimental/refactor_hook/native'),str(p),'-o',str(exe)],check=True)
result=subprocess.check_output([str(exe)],text=True,encoding='utf-8');print(result,end='');total=int(re.search(r'(\d+) cases PASS',result).group(1))
# Reuse existing actual-root fixtures, replacing only its Java test driver. The
# explicit retry must reach validation/start while ordinary status never clears it.
fixture=R/'tests/refactor_outdoor_fold.py';setup=fixture.read_text(encoding='utf-8').split('paths=[]',1)[0]
context={'__file__':str(fixture)};exec(compile(setup,str(fixture),'exec'),context);sources=dict(context['sources'])
base='top/rongshangs/lumacurve/refactor/'
sources[base+'OutdoorFoldTest.java']=r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.nio.file.*;
public class OutdoorFoldTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args)throws Exception{
  Files.createDirectories(RootControl.DATA.toPath());
  JSONObject config=new JSONObject().put("enabled",true).put("outdoor_node_boost",true).put("revision","new");
  JSONObject state=new JSONObject().put("phase","active").put("revision","new").put("outdoor",new JSONObject().put("options",new JSONObject().put("outdoor_node_boost",true)));
  for(String reason:new String[]{"write_failed","node_changed","start_failed"}){
   JSONObject health=new JSONObject().put("reason",reason).put("failure_revision","new");Files.write(OutdoorNodeRoot.HEALTH.toPath(),health.toString().getBytes("UTF-8"));
   OutdoorNodeRoot.reconcile(state,config.toString());check(OutdoorNodeRoot.health().getString("reason").equals(reason));
   // New revision, old error: ordinary reconcile remains latched too.
   health.put("failure_revision",reason.equals("start_failed")?"new":"old");Files.write(OutdoorNodeRoot.HEALTH.toPath(),health.toString().getBytes("UTF-8"));
   OutdoorNodeRoot.reconcile(state,config.toString());check(OutdoorNodeRoot.health().getString("failure_revision").equals("new"));
   // No compatible runtime: explicit recovery reaches full support validation,
   // fails closed and records it. It cannot launch a writer on this host fixture.
   try{OutdoorNodeRoot.reconcileAfterApply(state,config.toString());throw new AssertionError("invalid runtime accepted");}catch(java.io.IOException expected){cases++;}
   check(OutdoorNodeRoot.health().getString("reason").equals("start_failed"));check(OutdoorNodeRoot.running()<0);
  }
  JSONObject before=OutdoorNodeRoot.health();state.put("revision","not-confirmed");OutdoorNodeRoot.reconcileAfterApply(state,config.toString());check(OutdoorNodeRoot.health().toString().equals(before.toString()));
  System.out.println("Outdoor explicit recovery: "+cases+" cases PASS");
 }
}'''
paths=[]
for name,body in sources.items():
 p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding='utf-8');paths.append(p)
C=O/'classes';C.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
production=['OutdoorOutputAccess','OutdoorNodeBridge','OutdoorNodeRoot','OutdoorNodeProfile','OutdoorNodeTarget','OutdoorOptions','OutdoorPolicy','AdvancedPolicy']
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),*map(str,paths),*[str(S/(n+'.java')) for n in production]],check=True)
result=subprocess.check_output(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.OutdoorFoldTest'],text=True,encoding='utf-8');print(result,end='');total+=int(re.search(r'(\d+) cases PASS',result).group(1))
root=(S/'RootControl.java').read_text(encoding='utf-8');assert root.index('if(!waitFor(revision,true))')<root.index('OutdoorNodeRoot.reconcileAfterApply(')
assert root.count('OutdoorNodeRoot.reconcileAfterApply(')==1
print(f'Outdoor retry and recovery total: {total} cases PASS; host I/O doubles, target device not tested')
