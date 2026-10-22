#include "outdoor_node_policy.h"
#define OUTDOOR_LEASE "/data/system/hyperlux-outdoor-node-lease"
#define OUTDOOR_HEALTH DIR "/outdoor-node-health.json"
static struct outdoor_fold_ids outdoor_ids;
static uint64_t outdoor_physical;
static char outdoor_ids_text[168];
static char outdoor_controller[PANEL_REAL_PATH_SIZE];
static int outdoor_folded(void){return outdoor_ids.count>0;}
static int outdoor_fold_select(int port){
 char path[128],controller[PANEL_REAL_PATH_SIZE],previous_node[PANEL_NODE_PATH_SIZE],previous_max[PANEL_NODE_PATH_SIZE],previous_real[PANEL_REAL_PATH_SIZE];if(port<0||port>7)return -1;
 int previous_port=active_fold_port;dev_t previous_device=node_device;ino_t previous_inode=node_inode;strcpy(previous_node,node);strcpy(previous_max,max_node);strcpy(previous_real,real_node);
 snprintf(path,sizeof(path),"/sys/class/backlight/panel%d-backlight/brightness",port);active_fold_port=port;
 if(resolve_node(path))goto failed;strcpy(controller,real_node);char *end=strrchr(controller,'/');if(!end)goto failed;*end=0;end=strrchr(controller,'/');if(!end)goto failed;*end=0;
 if(outdoor_controller[0]&&strcmp(outdoor_controller,controller))goto failed;
 int max=number(max_node);if(max<=10||max>65535)goto failed;strcpy(outdoor_controller,controller);return max;
failed:
 active_fold_port=previous_port;node_device=previous_device;node_inode=previous_inode;strcpy(node,previous_node);strcpy(max_node,previous_max);strcpy(real_node,previous_real);return -1;
}
static int outdoor_fold_initialize(const char *path){
 int selected=-1;for(int i=0;i<8;i++)if(panel_fold_path(path,i))selected=i;if(selected<0)return -1;
 panel_directory *dir=opendir("/sys/class/backlight");if(!dir)return -1;int count=0,entries=0,valid=1;struct dirent *e;
 while((e=readdir(dir))){if(++entries>66){valid=0;break;}int port;char tail;if(sscanf(e->d_name,"panel%d-backlight%c",&port,&tail)!=1)continue;char expected[64];snprintf(expected,sizeof(expected),"panel%d-backlight",port);if(strcmp(e->d_name,expected))continue;
  if(port<0||port>7||outdoor_fold_select(port)<0){valid=0;break;}count++;
 }closedir(dir);if(!valid||count!=outdoor_ids.count)return -1;
 return outdoor_fold_select(selected);
}
static int outdoor_read(struct outdoor_lease *lease){
 int fd=open(OUTDOOR_LEASE,O_RDONLY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return 0;
 struct stat st;char text[160]={0};
 if(fstat(fd,&st)||!S_ISREG(st.st_mode)||st.st_uid!=1000||(st.st_mode&0077)){close(fd);return 0;}
 ssize_t n=read(fd,text,sizeof(text)-1);close(fd);return n>0&&n<(ssize_t)sizeof(text)-1&&outdoor_lease_parse(text,clock_ms(),lease);
}
static int outdoor_process(int pid){
 char path[64],name[64]={0};snprintf(path,sizeof(path),"/proc/%d/cmdline",pid);
 int f=open(path,O_RDONLY|O_CLOEXEC);if(f<0)return 0;ssize_t n=read(f,name,sizeof(name)-1);close(f);
 return n>0&&!strcmp(name,"system_server");
}
static void outdoor_health(const struct outdoor_node_state *s,const char *reason){
 char temp[]=OUTDOOR_HEALTH ".new";int f=open(temp,O_WRONLY|O_CREAT|O_TRUNC|O_NOFOLLOW|O_CLOEXEC,0644);if(f<0)return;
 char b[768];int n=snprintf(b,sizeof(b),"{\"pid\":%d,\"held\":%s,\"inhibited\":%s,\"base\":%d,\"target\":%d,\"reason\":\"%s\",\"fold_route\":%s,\"fold_ids\":\"%s\",\"fold_port\":%d,\"physical_id\":%llu,\"uptime_ms\":%lld}\n",getpid(),s->held?"true":"false",s->inhibited?"true":"false",s->base,s->target,reason,outdoor_folded()?"true":"false",outdoor_ids_text,active_fold_port,(unsigned long long)outdoor_physical,(long long)clock_ms());
 int ok=n>0&&n<(int)sizeof(b)&&write(f,b,(size_t)n)==n;close(f);if(ok)rename(temp,OUTDOOR_HEALTH);else unlink(temp);
}
static int outdoor_lock(void){
 int f=open(DIR "/main-panel.lock",O_RDWR|O_CREAT|O_NOFOLLOW|O_CLOEXEC,0600);if(f<0)return -1;
 struct stat st;if(fstat(f,&st)||!S_ISREG(st.st_mode)||st.st_uid||(st.st_mode&0077)||flock(f,LOCK_EX|LOCK_NB)){close(f);return -1;}return f;
}
static int outdoor_write(int expected,int target){
 /* Shared manual guard lock; do not chmod the node or stop framework output. */
 int lock=outdoor_lock();if(lock<0)return 1;
 if(outdoor_folded()&&!fold_panel_live("/sys",active_fold_port)){close(lock);return 1;}
 if(target<0||target>number(max_node)||number(node)!=expected||!same_node()){close(lock);return 1;}
 int fd=open_output();if(fd<0){close(lock);return -1;}
 char b[24];int n=snprintf(b,sizeof(b),"%d",target);
 int ok=number(node)==expected&&(!outdoor_folded()||fold_panel_live("/sys",active_fold_port))&&write(fd,b,(size_t)n)==n&&number(node)==target;
 int still_live=!outdoor_folded()||fold_panel_live("/sys",active_fold_port);close(fd);close(lock);return !still_live?1:ok?0:-1;
}
static int outdoor_run(const char *path){
 struct stat dir;if(lstat(DIR,&dir)||!S_ISDIR(dir.st_mode)||dir.st_uid||(dir.st_mode&0077))return 2;
 int singleton=open(DIR "/outdoor-node.lock",O_RDWR|O_CREAT|O_NOFOLLOW|O_CLOEXEC,0600);
 struct stat st;if(singleton<0||fstat(singleton,&st)||!S_ISREG(st.st_mode)||st.st_uid||(st.st_mode&0077)||flock(singleton,LOCK_EX|LOCK_NB))return 2;
 int maximum;if(outdoor_folded()){maximum=outdoor_fold_initialize(path);if(maximum<0)return 4;}else{if(resolve_node(path))return 4;maximum=number(max_node);if(maximum<=10||maximum>65535)return 4;}
 if(setsid()<0||escape_freezer())return 6;
 signal(SIGTERM,signal_stop);signal(SIGINT,signal_stop);signal(SIGHUP,signal_stop);
 const char *exit_reason="stopped";struct outdoor_node_state state={0};outdoor_health(&state,"waiting");
 int events=inotify_init1(IN_NONBLOCK|IN_CLOEXEC);
 if(events<0||inotify_add_watch(events,"/data/system",IN_MOVED_TO|IN_DELETE)<0)return 7;
 while(!stopping){
  struct outdoor_lease lease;int live=outdoor_read(&lease);
  if(live&&!outdoor_process(lease.pid))live=0;
  if(outdoor_folded()){
   if(live&&!outdoor_fold_authorized(&outdoor_ids,&lease))live=0;
   int port=fold_panel_active_port("/sys");
   int decision=outdoor_fold_decide(active_fold_port,outdoor_physical,port,live?&lease:NULL,live);
   if(!decision){
    memset(&state,0,sizeof(state));outdoor_health(&state,"fold_waiting");goto wait_events;
   }
   if(decision==2){
    /* Commit the coherent identity even if the new panel is still at zero.
       The shared policy waits for a positive settled value before any raise. */
    memset(&state,0,sizeof(state));maximum=outdoor_fold_select(port);if(maximum<0){outdoor_health(&state,"fold_waiting");goto wait_events;}outdoor_physical=lease.physical;outdoor_health(&state,"fold_bound");
   }
  }
  int manualLock=outdoor_lock(),manual=manualLock<0;if(manualLock>=0)close(manualLock);
  int current=number(node);if(current<0||number(max_node)!=maximum||!same_node()){exit_reason="node_changed";break;}
  struct outdoor_node_state before=state;
  int value=outdoor_node_step(&state,live?&lease:NULL,current,maximum,manual,clock_ms());
  if(value>=0){
   /* Refresh authorization immediately before a raise. Release is compare-only. */
   struct outdoor_lease latest;
   if(value>current&&(!outdoor_read(&latest)||!latest.active||latest.pid!=lease.pid||latest.until!=lease.until||latest.fraction!=lease.fraction||latest.raw!=lease.raw||latest.target!=lease.target||latest.physical!=lease.physical)){state=before;continue;}
   int result=outdoor_write(current,value);
   if(result==1&&outdoor_folded()){memset(&state,0,sizeof(state));outdoor_health(&state,"fold_waiting");goto wait_events;}
   if(result){state.held=before.held;state.base=before.base;state.target=before.target;state.inhibited=1;exit_reason="write_failed";outdoor_health(&state,exit_reason);break;}
  }
  if(memcmp(&before,&state,sizeof(state)))outdoor_health(&state,state.inhibited?"system_override":state.held?"boosted":"released");
wait_events: ;
  int ms=-1;if(live&&lease.active)ms=(int)(lease.until-clock_ms());
  if(ms<0&&live&&lease.active)ms=0;
  struct pollfd p={.fd=events,.events=POLLIN};if(poll(&p,1,ms)>0){char b[4096];read(events,b,sizeof(b));}
 }
 if(state.held)outdoor_write(state.target,state.base);
 memset(&state,0,sizeof(state));outdoor_health(&state,exit_reason);close(events);close(singleton);return 0;
}
