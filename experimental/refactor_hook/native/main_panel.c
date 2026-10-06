#define _GNU_SOURCE
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <sys/types.h>
#include <sys/time.h>
#include <sys/inotify.h>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stddef.h>
#include "panel_policy.h"
#define DIR "/data/adb/luma_curve_refactor_test"
#define NODE "/sys/class/backlight/panel0-backlight/brightness"
#define MAX_NODE "/sys/class/backlight/panel0-backlight/max_brightness"
#define RECORD DIR "/main-panel-permissions"
#define SOCKET_NAME "hyperlux.main.panel.raw04"
static struct panel_policy state;
static int output=-1,maximum,lock_fd=-1,original=-1,attempts,successes,last_error;
static volatile sig_atomic_t stopping;
static int64_t due;
static int corrections;
static int64_t last_correction_log;
static int read_lease(struct panel_lease *lease);
static int64_t clock_ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (int64_t)t.tv_sec*1000+t.tv_nsec/1000000;}
static void signal_stop(int sig){(void)sig;stopping=1;}
static int number(const char *path){
 int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return -1;char b[32];ssize_t n=read(fd,b,sizeof(b)-1);close(fd);if(n<=0)return -1;b[n]=0;
 char *end;errno=0;long v=strtol(b,&end,10);while(*end=='\n'||*end=='\r'||*end==' '||*end=='\t')end++;
 return errno||end==b||*end||v<0||v>65535?-1:(int)v;
}
static int restore_record(void){
 int fd=open(RECORD,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return errno==ENOENT?0:-1;
 struct stat st;if(fstat(fd,&st)||st.st_uid||!S_ISREG(st.st_mode)||(st.st_mode&0077)){close(fd);return -1;}
 char b[24];ssize_t n=read(fd,b,sizeof(b)-1);close(fd);if(n<=0)return -1;b[n]=0;
 char *end;long mode=strtol(b,&end,8);if((*end!='\n'&&*end)||mode<0||mode>0777)return -1;
 if(chmod(NODE,(mode_t)mode))return -1;return unlink(RECORD);
}
static int release_output(void){
 if(output>=0)close(output);output=-1;
 int result=restore_record();if(!result)original=-1;return result;
}
static int acquire_output(void){
 if(output>=0)return 0;
 struct stat st;if(lstat(NODE,&st)||!S_ISREG(st.st_mode)||!(st.st_mode&0222)){errno=EACCES;return -1;}
 original=st.st_mode&0777;
 // Save permissions before locking. The already-open descriptor remains writable.
 int f=open(RECORD,O_WRONLY|O_CREAT|O_EXCL|O_CLOEXEC|O_NOFOLLOW,0600);if(f<0)return -1;
 char b[24];int len=snprintf(b,sizeof(b),"%o\n",original);int ok=write(f,b,(size_t)len)==len&&fsync(f)==0;close(f);
 if(!ok){unlink(RECORD);return -1;}
 output=open(NODE,O_WRONLY|O_CLOEXEC|O_NOFOLLOW);if(output<0){restore_record();return -1;}
 if(chmod(NODE,(mode_t)(original&~0222))){release_output();return -1;}return 0;
}
static int write_output(int value){
 struct panel_lease lease;
 if(!read_lease(&lease)||lease.pid!=state.pid||strcmp(lease.token,state.token)){last_error=ECANCELED;return -1;}
 if(!lease.active||lease.target!=value||!panel_output_live(number(NODE)))return 1;
 attempts++;if(acquire_output()){last_error=errno?errno:EIO;return -1;}
 if(!read_lease(&lease)||!lease.active||lease.target!=value||lease.pid!=state.pid||strcmp(lease.token,state.token)){release_output();return 1;}
 char b[24];int len=snprintf(b,sizeof(b),"%d",value);
 if(lseek(output,0,SEEK_SET)<0||write(output,b,(size_t)len)!=len){last_error=errno?errno:EIO;return -1;}
 if(number(NODE)!=value){last_error=ERANGE;return -1;}
 successes++;last_error=0;return 0;
}
static int private_lock(void){
 struct stat st;if(lstat(DIR,&st)||!S_ISDIR(st.st_mode)||st.st_uid||(st.st_mode&0077))return -1;
 lock_fd=open(DIR "/main-panel.lock",O_RDWR|O_CREAT|O_NOFOLLOW|O_CLOEXEC,0600);
 if(lock_fd<0||fstat(lock_fd,&st)||!S_ISREG(st.st_mode)||st.st_uid||(st.st_mode&0077)||flock(lock_fd,LOCK_EX|LOCK_NB))return -1;
 return 0;
}
static void response(int c,int ok,const char *reason){
 char out[768];int n=snprintf(out,sizeof(out),"{\"ok\":%s,\"reason\":\"%s\",\"backend\":\"raw_main_node\",\"native_build\":\"raw04\",\"target\":%d,\"actual\":%d,\"maximum\":%d,\"armed\":%s,\"paused\":%s,\"session\":\"%s\",\"write_attempts\":%d,\"write_successes\":%d,\"corrections\":%d,\"errno\":%d}\n",ok?"true":"false",reason,state.target,number(NODE),maximum,state.armed?"true":"false",state.paused?"true":"false",state.token,attempts,successes,corrections,last_error);
 if(n>0&&(size_t)n<sizeof(out))send(c,out,(size_t)n,MSG_NOSIGNAL);
}
static int read_lease(struct panel_lease *lease){
 int f=open("/data/system/hyperlux-main-panel-lease",O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(f<0)return 0;
 struct stat st;if(fstat(f,&st)||st.st_uid!=1000||!S_ISREG(st.st_mode)||(st.st_mode&0077)){close(f);return 0;}
 char b[160]={0};ssize_t n=read(f,b,sizeof(b)-1);close(f);
 return n>0&&panel_read_lease(b,maximum,clock_ms(),lease);
}
static int lease_for(const char *token,int pid,struct panel_lease *lease){
 if(!read_lease(lease))return 0;return !strcmp(lease->token,token)&&lease->pid==pid;
}
static int health(int active){
 const char *temp="/data/system/hyperlux-main-panel-health.new",*path="/data/system/hyperlux-main-panel-health";
 int f=open(temp,O_CREAT|O_TRUNC|O_WRONLY|O_CLOEXEC|O_NOFOLLOW,0644);if(f<0)return -1;
 char b[128];int len=panel_health(b,sizeof(b),&state,active,last_error);
 int ok=fchmod(f,0644)==0&&write(f,b,(size_t)len)==len;close(f);
 if(!ok){unlink(temp);return -1;}return rename(temp,path);
}
static int bind_server(void){
 int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0)return -1;
 struct sockaddr_un a={.sun_family=AF_UNIX};memcpy(a.sun_path+1,SOCKET_NAME,sizeof(SOCKET_NAME)-1);
 if(bind(fd,(struct sockaddr*)&a,(socklen_t)(offsetof(struct sockaddr_un,sun_path)+sizeof(SOCKET_NAME)))||listen(fd,8)){close(fd);return -1;}return fd;
}
static void serve(int c){
 struct ucred cred;socklen_t size=sizeof(cred);
 if(getsockopt(c,SOL_SOCKET,SO_PEERCRED,&cred,&size)|| (cred.uid!=0&&cred.uid!=1000)){return;}
 struct timeval timeout={.tv_sec=0,.tv_usec=250000};setsockopt(c,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));setsockopt(c,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
 char b[240]={0};size_t used=0;while(used<sizeof(b)-1){ssize_t n=recv(c,b+used,sizeof(b)-1-used,0);if(n<=0)return;used+=(size_t)n;b[used]=0;if(strchr(b,'\n'))break;}
 char action[16]={0},token[81]={0},tail;int value=0;int parts=sscanf(b,"%15s %80s %d %c",action,token,&value,&tail);int64_t now=clock_ms();
 if(!strcmp(action,"STATUS")&&parts==1){response(c,1,"status");return;}
 struct panel_lease lease;
 if(!strcmp(action,"ARM")&&cred.uid==0&&parts==3&&lease_for(token,value,&lease)&&lease.active&&panel_arm(&state,token,value,now)){state.expires=lease.expires;state.paused=0;last_error=0;if(health(1)){last_error=errno?errno:EIO;response(c,0,"health_state_failed");stopping=1;}else response(c,1,"armed");return;}

 if(!strcmp(action,"STOP")&&((cred.uid==0&&parts==1)|| (parts==2&&!strcmp(token,state.token)))){
  int ok=release_output()==0;panel_clear(&state);response(c,ok,ok?"released":"permission_restore_failed");stopping=1;return;
 }
 if(!strcmp(action,"SET")&&cred.uid==0&&parts==3&&lease_for(token,state.pid,&lease)&&lease.active&&lease.target==value&&panel_set(&state,token,value,maximum,now)){
  int result=write_output(value);if(result<0){release_output();panel_clear(&state);response(c,0,"write_or_readback_failed");stopping=1;return;}
  if(result>0){response(c,0,"screen_not_writable");return;}
  due=now+250;response(c,1,"readback_ok");return;
 }
 response(c,0,"stale_or_invalid_request");
}
static int escape_freezer(void){
 const char *paths[]={"/sys/fs/cgroup/cgroup.procs","/sys/fs/cgroup/uid_0/cgroup.procs","/dev/freezer/cgroup.procs","/sys/fs/cgroup/freezer/cgroup.procs"};
 int exists=0;char b[32];int n=snprintf(b,sizeof(b),"%d",getpid());
 for(size_t i=0;i<sizeof(paths)/sizeof(paths[0]);i++){if(access(paths[i],F_OK))continue;exists=1;int f=open(paths[i],O_WRONLY|O_CLOEXEC);if(f>=0){int ok=write(f,b,(size_t)n)==n;close(f);if(ok)return 0;}}
 return exists?-1:0;
}
static int startup_fail(int code,const char *stage){fprintf(stderr,"NATIVE_START_FAIL stage=%s exit=%d errno=%d (%s)\n",stage,code,errno,strerror(errno));return code;}
int main(int argc,char **argv){
 if(geteuid()!=0||private_lock())return startup_fail(2,"root_private_lock");
 if(restore_record())return startup_fail(3,"restore_permissions");
 if(argc==2&&!strcmp(argv[1],"--recover"))return 0;
 if(argc!=2||strcmp(argv[1],"--daemon"))return 2;
 setsid();if(escape_freezer())return startup_fail(6,"escape_app_freezer");
 maximum=number(MAX_NODE);if(maximum<10||maximum>65535)return startup_fail(4,"main_panel_range");
 int server=bind_server();if(server<0)return startup_fail(5,"control_socket");
 fprintf(stderr,"NATIVE_READY build=raw04 pid=%d maximum=%d\n",getpid(),maximum);
 signal(SIGTERM,signal_stop);signal(SIGINT,signal_stop);signal(SIGHUP,signal_stop);signal(SIGPIPE,SIG_IGN);panel_clear(&state);
 // Lease rename wakes the guard immediately on screen-off/DOZE or automatic mode.
 int events=inotify_init1(IN_NONBLOCK|IN_CLOEXEC);
 if(events>=0&&inotify_add_watch(events,"/data/system",IN_MOVED_TO|IN_DELETE|IN_CLOSE_WRITE)<0){close(events);events=-1;}
 int64_t idle=clock_ms()+10000;
 while(!stopping){
  int64_t now=clock_ms();
  if(state.armed){struct panel_lease lease;
   if(!read_lease(&lease)||lease.pid!=state.pid||kill(state.pid,0))break;
   int changed=strcmp(lease.token,state.token)||state.paused==lease.active;
   strcpy(state.token,lease.token);state.expires=lease.expires;state.target=lease.target;state.paused=!lease.active;
   if(state.paused){if(output>=0&&release_output())break;}
   if(changed){due=now;if(health(1))break;fprintf(stderr,"NATIVE_%s target=%d\n",state.paused?"PAUSED":"RESUMED",state.target);}
  }
  if(!state.armed&&now>=idle)break;
  if(state.armed&&!state.paused&&state.target>=10&&now>=due){
   int current=number(NODE);if(current<0){last_error=EIO;break;}
   if(panel_output_live(current)&&current!=state.target){int result=write_output(state.target);if(result<0)break;if(!result){corrections++;if(corrections<=8||now-last_correction_log>=30000){last_correction_log=now;fprintf(stderr,"NATIVE_CORRECTION actual=%d target=%d count=%d\n",current,state.target,corrections);}}}
   due=now+250;
  }
  int64_t deadline=state.armed?state.expires:idle;if(state.armed&&!state.paused&&state.target>=10&&due<deadline)deadline=due;
  int ms=(int)(deadline-now);if(ms<0)ms=0;if(events<0&&ms>250)ms=250;
  struct pollfd p[2]={{.fd=server,.events=POLLIN},{.fd=events,.events=POLLIN}};int ready=poll(p,events>=0?2:1,ms);
  if(ready>0&&events>=0&&(p[1].revents&POLLIN)){char changes[4096];read(events,changes,sizeof(changes));}
  if(ready>0&&(p[0].revents&POLLIN)){int c=accept4(server,NULL,NULL,SOCK_CLOEXEC);if(c>=0){serve(c);close(c);}}
 }
 release_output();health(0);if(events>=0)close(events);close(server);return 0;
}
