"""Production private-lock checks and installer rollback with host syscall/service doubles."""
from pathlib import Path
import subprocess,re,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';N=R/'experimental/refactor_hook/native';O=R/'build/native-storage-tests';O.mkdir(exist_ok=True)
native=(N/'main_panel.c').read_text('utf-8');storage=native.split('/* PRIVATE_STORAGE_BEGIN:',1)[1].split('*/',1)[1].split('/* PRIVATE_STORAGE_END */',1)[0]
assert 'unlink(' not in storage and 'write(' not in storage and 'chown(' not in storage
source=r'''
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <limits.h>
#define DIR "/data/adb/luma_curve_refactor_test"
#define O_RDONLY 0
#define O_RDWR 2
#define O_CREAT 64
#define O_DIRECTORY 65536
#define O_NOFOLLOW 131072
#define O_CLOEXEC 524288
#define AT_SYMLINK_NOFOLLOW 256
#define LOCK_EX 2
#define LOCK_NB 4
#ifndef ESTALE
#define ESTALE 116
#endif
#ifndef ELOOP
#define ELOOP 40
#endif
#ifndef EWOULDBLOCK
#define EWOULDBLOCK EAGAIN
#endif
static int cases,uid,parent_uid,dir_uid,file_uid,parent_mode,dir_mode,file_mode,links,busy,alias,wrong_file,wrong_dir,changed_dir,changed_file,fail_dir_mode,fail_file_mode,lie_mode,open_failure;
static int dir_chmods,file_chmods,file_opens,file_closes,dir_closes,dir_stats,lock_calls;
static void fill(struct stat *s,int mode,int owner,int ino,int nlink){memset(s,0,sizeof(*s));s->st_mode=mode;s->st_uid=owner;s->st_dev=1;s->st_ino=ino;s->st_nlink=nlink;}
static int fake_geteuid(void){return uid;}
static int fake_lstat(const char *p,struct stat *s){
 if(!strcmp(p,"/data/adb")){fill(s,S_IFDIR|parent_mode,parent_uid,1,2);return 0;}
 if(strcmp(p,DIR))abort();dir_stats++;fill(s,(wrong_dir?S_IFREG:S_IFDIR)|dir_mode,dir_uid,changed_dir&&dir_stats>1?99:2,2);return 0;
}
static char *fake_realpath(const char *p,char *out){if(strcmp(p,DIR))abort();strcpy(out,alias?"/different":DIR);return out;}
static int fake_open(const char *p,int flags){if(strcmp(p,DIR)||(flags&(O_NOFOLLOW|O_DIRECTORY|O_CLOEXEC))!=(O_NOFOLLOW|O_DIRECTORY|O_CLOEXEC))abort();return 10;}
static int fake_openat(int dir,const char *name,int flags,int mode){if(dir!=10||(strcmp(name,"main-panel.lock")&&strcmp(name,"outdoor-node.lock"))||mode!=0600||(flags&(O_NOFOLLOW|O_CLOEXEC|O_CREAT|O_RDWR))!=(O_NOFOLLOW|O_CLOEXEC|O_CREAT|O_RDWR))abort();file_opens++;if(open_failure){errno=EACCES;return -1;}return 20;}
static int fake_fstat(int fd,struct stat *s){if(fd==10)fill(s,S_IFDIR|dir_mode,dir_uid,2,2);else if(fd==20)fill(s,(wrong_file?S_IFDIR:S_IFREG)|file_mode,file_uid,3,links);else abort();return 0;}
static int fake_fstatat(int fd,const char *name,struct stat *s,int flags){if(fd!=10||flags!=AT_SYMLINK_NOFOLLOW||!name)abort();fill(s,S_IFREG|file_mode,file_uid,changed_file?99:3,links);return 0;}
static int fake_fchmod(int fd,int mode){if(fd==10){dir_chmods++;if(fail_dir_mode){errno=EACCES;return -1;}if(mode!=0700)abort();if(!lie_mode)dir_mode=mode;}else if(fd==20){file_chmods++;if(fail_file_mode){errno=EPERM;return -1;}if(mode!=0600)abort();if(!lie_mode)file_mode=mode;}else abort();return 0;}
static int fake_flock(int fd,int op){if(fd!=20||op!=(LOCK_EX|LOCK_NB))abort();lock_calls++;if(busy){errno=EWOULDBLOCK;return -1;}return 0;}
static int fake_close(int fd){if(fd==10)dir_closes++;else if(fd==20)file_closes++;else abort();errno=0;return 0;}
#define geteuid fake_geteuid
#define lstat fake_lstat
#define realpath fake_realpath
#define open fake_open
#define openat fake_openat
#define fstat fake_fstat
#define fstatat fake_fstatat
#define fchmod fake_fchmod
#define flock fake_flock
#define close fake_close
'''+storage+r'''
#define CHECK(x) do{if(!(x)){fprintf(stderr,"storage case %d line=%d stage=%s errno=%d\n",cases,__LINE__,private_stage,errno);return 1;}cases++;}while(0)
static void reset(void){uid=parent_uid=dir_uid=file_uid=0;parent_mode=dir_mode=0700;file_mode=0600;links=1;busy=alias=wrong_file=wrong_dir=changed_dir=changed_file=fail_dir_mode=fail_file_mode=lie_mode=open_failure=0;dir_chmods=file_chmods=file_opens=file_closes=dir_closes=dir_stats=lock_calls=0;errno=0;private_stage="none";}
int main(void){
 // This reproduces the old errno=0 metadata rejection; it does not prove the user's file mode.
 reset();file_mode=0644;CHECK((file_mode&0077)!=0&&errno==0);CHECK(private_open_lock("main-panel.lock")==20);CHECK(file_mode==0600&&file_chmods==1&&lock_calls==1&&file_closes==0&&dir_closes==1);close(20);
 int modes[]={0000,0400,0600,0644,0666,0700,0755,0777};
 for(int i=0;i<8;i++)for(int j=0;j<8;j++){
  reset();dir_mode=modes[i];file_mode=modes[j];CHECK(private_open_lock("main-panel.lock")==20);CHECK(dir_mode==0700&&file_mode==0600);CHECK(file_opens==1&&file_closes==0&&dir_closes==1);CHECK(!strcmp(private_stage,"ready"));close(20);
 }
 reset();CHECK(private_open_lock("outdoor-node.lock")==20);CHECK(!dir_chmods&&!file_chmods);close(20);
 reset();uid=1000;CHECK(private_open_lock("main-panel.lock")<0&&errno==EPERM&&!strcmp(private_stage,"root_identity"));CHECK(!file_opens&&!dir_chmods);
 reset();parent_uid=1000;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!file_opens);
 reset();parent_mode=0777;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!file_opens);
 reset();dir_uid=1000;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!dir_chmods&&!file_opens);
 reset();alias=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==ELOOP&&!file_opens);
 reset();wrong_dir=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!file_opens);
 reset();file_uid=1000;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!file_chmods&&file_closes==1&&dir_closes==1);
 reset();wrong_file=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!file_chmods);
 reset();links=2;CHECK(private_open_lock("main-panel.lock")<0&&errno==EMLINK&&!file_chmods&&!lock_calls);
 reset();file_mode=0644;busy=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==EWOULDBLOCK&&!file_chmods&&file_closes==1);CHECK(file_mode==0644&&!strcmp(private_stage,"lock_acquire"));
 reset();dir_mode=0755;fail_dir_mode=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&!file_opens&&dir_closes==1);
 reset();file_mode=0644;fail_file_mode=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==EPERM&&file_closes==1&&dir_closes==1);
 reset();file_mode=0644;lie_mode=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==ESTALE&&file_closes==1);
 reset();changed_file=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==ESTALE&&file_closes==1);
 reset();changed_dir=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==ESTALE&&!file_opens);
 reset();open_failure=1;CHECK(private_open_lock("main-panel.lock")<0&&errno==EACCES&&dir_closes==1);
 reset();CHECK(private_open_lock("../other")<0&&errno==EINVAL&&!file_opens);
 printf("Native storage locks: %d cases PASS; same-inode mode repair, contention and rejected identities; host only\n",cases);
 return 0;
}
'''
p=O/'locks.c';p.write_text(source,'utf-8');exe=O/'locks.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-Wno-misleading-indentation',str(p),'-o',str(exe)],check=True)
run=subprocess.run([str(exe)],check=True,capture_output=True,text=True);print(run.stdout,end='');lock_cases=int(re.search(r'(\d+) cases PASS',run.stdout).group(1))

def method(text,signature):
 start=text.index(signature);i=text.index('{',start)+1;depth=1
 while depth:
  depth+=(text[i]=='{')-(text[i]=='}');i+=1
 return text[start:i]
java=(S/'NativePanelRoot.java').read_text('utf-8');install=method(java,' static void install()')
assert 'RootControl.process(BIN.toString(),"--recover")' not in install
sources={
'android/system/Os.java':'''package android.system;public class Os{public static int chmods;public static void chmod(String p,int mode){if(mode!=0700)throw new AssertionError();chmods++;}}''',
'top/rongshangs/lumacurve/refactor/NativePanelRoot.java':'''package top.rongshangs.lumacurve.refactor;import java.io.*;import java.nio.file.*;import java.security.MessageDigest;import android.system.Os;class NativePanelRoot{static final File BIN=new File(RootControl.DATA,"hyperlux-main-panel");'''+install+'}',
'top/rongshangs/lumacurve/refactor/StorageInstallTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.nio.file.*;import java.io.*;import java.util.*;
class RootWorkspace{static boolean fail;static void requireNative()throws IOException{if(fail)throw new IOException("native unavailable");}}
class NativePanelClient{static int stops,probes;static void require(String s){stops++;}static void call(String s)throws IOException{probes++;throw new IOException("stopped");}}
class RootControl{static final File DATA=new File(System.getProperty("fixture"));static int calls;static boolean fail;static File staged;static byte[] expected;static void process(String path,String action)throws Exception{calls++;staged=new File(path);if(staged.equals(NativePanelRoot.BIN)||!action.equals("--recover")||!staged.getCanonicalFile().getParentFile().equals(DATA.getCanonicalFile())||!Arrays.equals(Files.readAllBytes(staged.toPath()),expected))throw new AssertionError("wrong recovery payload");if(fail)throw new IOException("recovery rejected");}}
public class StorageInstallTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("install "+cases);cases++;}interface W{void run()throws Exception;}static void bad(W w)throws Exception{try{w.run();throw new AssertionError();}catch(IOException expected){cases++;}}
public static void main(String[] args)throws Exception{
 RootControl.DATA.mkdirs();RootControl.expected=NativePanelAsset.read(new File(System.getenv("CLASSPATH")));byte[] old={1,2,3};Files.write(NativePanelRoot.BIN.toPath(),old);
 RootControl.fail=true;bad(()->NativePanelRoot.install());check(Arrays.equals(Files.readAllBytes(NativePanelRoot.BIN.toPath()),old));check(RootControl.calls==1&&!RootControl.staged.exists());check(NativePanelClient.stops==1);
 RootControl.fail=false;NativePanelRoot.install();check(Arrays.equals(Files.readAllBytes(NativePanelRoot.BIN.toPath()),RootControl.expected));check(RootControl.calls==2&&!RootControl.staged.exists());
 int stops=NativePanelClient.stops;NativePanelRoot.install();check(RootControl.calls==2&&NativePanelClient.stops==stops);
 RootWorkspace.fail=true;bad(()->NativePanelRoot.install());check(RootControl.calls==2&&Arrays.equals(Files.readAllBytes(NativePanelRoot.BIN.toPath()),RootControl.expected));
 System.out.println("Native storage installer: "+cases+" cases PASS; verified staged recovery, rollback and cached payload; host only");
}}
'''
}
import zipfile
payload=bytearray(64);payload[:6]=bytes([127,69,76,70,2,1]);payload[18]=183
apk=O/'input.apk'
with zipfile.ZipFile(apk,'w') as z:z.writestr('assets/hyperlux-main-panel',payload)
files=[]
for n,s in sources.items():
 p=O/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,'utf-8');files.append(str(p))
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(C),*files,str(S/'NativePanelAsset.java')],check=True)
env=dict(os.environ,CLASSPATH=str(apk.resolve()))
run=subprocess.run(['java','-Dfixture='+str((O/'workspace').resolve()),'-cp',str(C),'top.rongshangs.lumacurve.refactor.StorageInstallTest'],env=env,check=True,capture_output=True,text=True);print(run.stdout,end='')
installer_cases=int(re.search(r'(\d+) cases PASS',run.stdout).group(1))
print(f'Native private storage: {lock_cases+installer_cases} cases PASS; production native checks and Java installer, no Android execution')
