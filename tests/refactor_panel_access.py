"""Run production Java/native open checks with syscall doubles, including mode 0444.

The doubles model Root access independently of mode bits. This verifies control
flow and descriptor cleanup; actual Android DAC/SELinux/driver behavior needs a device.
"""
from pathlib import Path
import os
import subprocess

R = Path(__file__).resolve().parents[1]
S = R / 'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O = R / 'build/panel-access-tests'
J = R / 'build/refactor-diagnostics/json-20240303.jar'
files = {
    'android/system/ErrnoException.java': '''package android.system;public class ErrnoException extends Exception{public final int errno;public ErrnoException(String op,int e){super(op+" errno="+e);errno=e;}}''',
    'android/system/StructStat.java': '''package android.system;public class StructStat{public int st_mode;public long st_dev=1,st_ino=2;}''',
    'android/system/OsConstants.java': '''package android.system;public class OsConstants{public static final int O_WRONLY=1,O_CLOEXEC=02000000,O_NOFOLLOW=0400000;public static boolean S_ISREG(int mode){return (mode&0170000)==0100000;}}''',
    'android/system/Os.java': r'''package android.system;import java.io.*;
public class Os{
 public static int mode=0444,denied,opens,closes,chmods,flags;public static boolean changed,nonRegular,statFails;
 public static void reset(){mode=0444;denied=opens=closes=chmods=flags=0;changed=nonRegular=statFails=false;}
 public static StructStat lstat(String p){StructStat s=new StructStat();s.st_mode=0100000|mode;return s;}
 public static FileDescriptor open(String p,int f,int m)throws ErrnoException{opens++;flags=f;if(denied!=0)throw new ErrnoException("open",denied);return new FileDescriptor();}
 public static StructStat fstat(FileDescriptor f)throws ErrnoException{if(statFails)throw new ErrnoException("fstat",5);StructStat s=lstat("");if(changed)s.st_ino++;if(nonRegular)s.st_mode=0040000|mode;return s;}
 public static void close(FileDescriptor f){closes++;}public static void chmod(String p,int m){chmods++;}
}''',
    'top/rongshangs/lumacurve/refactor/RootControl.java': '''package top.rongshangs.lumacurve.refactor;import java.io.*;class RootControl{static final File DATA=new File("build/panel-access-tests");static void process(String... p){}}''',
    'top/rongshangs/lumacurve/refactor/PanelNodeDiscovery.java': '''package top.rongshangs.lumacurve.refactor;import java.io.*;import org.json.*;class PanelNodeDiscovery{static JSONObject selected;static int number(File f){return 951;}static JSONObject discover(File f)throws Exception{return new JSONObject(selected.toString());}}''',
    'top/rongshangs/lumacurve/refactor/NativePanelClient.java': '''package top.rongshangs.lumacurve.refactor;import java.io.*;import org.json.*;class NativePanelClient{static JSONObject guard;static JSONObject call(String s)throws Exception{if(guard==null)throw new IOException("Connection refused");return guard;}static void require(String s){}}''',
    'top/rongshangs/lumacurve/refactor/PanelAccessTest.java': r'''package top.rongshangs.lumacurve.refactor;
import android.system.*;import java.io.*;import java.nio.file.*;import org.json.*;
public class PanelAccessTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static File node;
 static void reset()throws Exception{Os.reset();NativePanelClient.guard=null;PanelNodeDiscovery.selected=new JSONObject().put("supported",true).put("path",node.getAbsolutePath()).put("canonical_path",node.toPath().toRealPath().toString()).put("maximum",16383).put("actual",951);}
 static void unchanged()throws Exception{check(new String(Files.readAllBytes(node.toPath()),"US-ASCII").equals("951\n"));check(Os.chmods==0);}
 public static void main(String[] args)throws Exception{
  node=new File("build/panel-access-tests/brightness");Files.write(node.toPath(),"951\n".getBytes("US-ASCII"));
  for(int mode:new int[]{0444,0644,0664}){reset();Os.mode=mode;JSONObject out=NativePanelRoot.status();check(out.getBoolean("supported"));check(out.getString("write_probe").equals("open_ok"));check(out.getString("node_mode").equals(Integer.toOctalString(mode)));check(Os.opens==1&&Os.closes==1);check(Os.flags==(OsConstants.O_WRONLY|OsConstants.O_CLOEXEC|OsConstants.O_NOFOLLOW));unchanged();}
  for(int error:new int[]{1,13,30}){reset();Os.mode=0664;Os.denied=error;JSONObject out=NativePanelRoot.status();check(!out.getBoolean("supported"));check(out.getString("reason").equals("node_not_writable"));check(out.getInt("write_errno")==error&&out.getString("write_error").contains("open"));check(out.getInt("actual")==951&&out.getInt("maximum")==16383);check(Os.opens==1&&Os.closes==0);unchanged();}
  reset();Os.changed=true;JSONObject out=NativePanelRoot.status();check(!out.getBoolean("supported"));check(out.getString("write_error").contains("发生变化"));check(Os.opens==1&&Os.closes==1);unchanged();
  reset();Os.nonRegular=true;check(!NativePanelRoot.status().getBoolean("supported"));check(Os.closes==1);unchanged();
  reset();Os.statFails=true;out=NativePanelRoot.status();check(!out.getBoolean("supported")&&out.getInt("write_errno")==5);check(Os.closes==1);unchanged();
  reset();PanelNodeDiscovery.selected.put("canonical_path",node.getAbsolutePath()+".other");check(!NativePanelRoot.status().getBoolean("supported"));check(Os.closes==1);unchanged();
  reset();NativePanelClient.guard=new JSONObject(PanelNodeDiscovery.selected.toString()).put("ok",true).put("native_build","raw07-openprobe");out=NativePanelRoot.status();check(out.getBoolean("supported")&&out.getBoolean("guard_running"));check(out.getString("write_probe").equals("guard_owns_descriptor"));check(Os.opens==0);unchanged();
  for(String wrong:new String[]{"raw06-health","different-node"}){reset();NativePanelClient.guard=new JSONObject(PanelNodeDiscovery.selected.toString()).put("ok",true).put("native_build",wrong);out=NativePanelRoot.status();check(!out.getBoolean("supported"));check(out.getString("reason").equals("guard_node_mismatch"));check(Os.opens==0);unchanged();}
  reset();PanelNodeDiscovery.selected=new JSONObject().put("supported",false).put("reason","ambiguous_primary_nodes");check(!NativePanelRoot.status().getBoolean("supported"));check(Os.opens==0);unchanged();
  System.out.println("Panel access Java: "+cases+" cases PASS; production status, syscall model only");
 }
}''',
}
paths = []
for name, source in files.items():
    p = O / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(source, encoding='utf-8')
    paths.append(p)
classes = O / 'classes'
classes.mkdir(exist_ok=True)
entry = 'top.rongshangs.lumacurve.refactor.PanelAccessTest'
production = S / 'NativePanelRoot.java'
def java_compile(source, target):
    subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '8', '-cp', str(J), '-d', str(target), str(source), str(S / 'NativePanelAsset.java'), *map(str, paths)], check=True)
java_compile(production, classes)
subprocess.run(['java', '-cp', str(classes)+os.pathsep+str(J), entry], check=True, cwd=R)
# Reintroduce mode-bit rejection: the first 0444 fixture must now fail.
fixed = production.read_text(encoding='utf-8')
old = fixed.replace('probe(j);j.put("write_probe","open_ok");', 'if((Os.lstat(node.toString()).st_mode&0222)==0)throw new IOException("mode rejection");probe(j);j.put("write_probe","open_ok");')
assert old != fixed
negative = O / 'negative/NativePanelRoot.java'
negative.parent.mkdir(exist_ok=True)
negative.write_text(old, encoding='utf-8')
negative_classes = O / 'negative-classes'
negative_classes.mkdir(exist_ok=True)
java_compile(negative, negative_classes)
failed = subprocess.run(['java', '-cp', str(negative_classes)+os.pathsep+str(J), entry], cwd=R, capture_output=True, text=True)
assert failed.returncode != 0 and 'AssertionError: case 0' in failed.stderr
print('Previous 0444 Java mode rejection reproduced: PASS')

native = (R / 'experimental/refactor_hook/native/main_panel.c').read_text(encoding='utf-8')
def function(name):
    start = native.index('static int '+name+'(')
    end = native.index('\n}', start) + 2
    return native[start:end]
c = O / 'access-test.c'
c.write_text(r'''
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include "panel_node.h"
#ifndef ESTALE
#define ESTALE 116
#endif
#ifndef O_CLOEXEC
#define O_CLOEXEC 02000000
#endif
#ifndef O_NOFOLLOW
#define O_NOFOLLOW 0400000
#endif
#define RECORD "record"
static char node[PANEL_NODE_PATH_SIZE]="/sys/class/backlight/panel0-backlight/brightness",real_node[PANEL_REAL_PATH_SIZE]="/sys/devices/panel0/brightness",boot_id[40]="5a30f9e3-021b-4ec1-8131-47f151ebb7aa";
static dev_t node_device=1;static ino_t node_inode=2;static int output=-1,original=-1;
static int mode,deny,changed,live,opens,closes,chmods,restores,record_writes,record_exists,chmod_mode,fail_chmod,fail_sync;
static int same_node(void){return live;}
static int fake_open(const char *path,int flags,...){
 if(!strcmp(path,RECORD)){if(record_exists){errno=EEXIST;return -1;}record_exists=1;return 20;}
 opens++;if(flags!=(O_WRONLY|O_CLOEXEC|O_NOFOLLOW)){errno=EINVAL;return -1;}if(deny){errno=deny;return -1;}return 10;
}
static int fake_close(int fd){if(fd==10)closes++;return 0;}
static int fake_lstat(const char *path,struct stat *st){(void)path;memset(st,0,sizeof(*st));st->st_mode=S_IFREG|mode;st->st_dev=node_device;st->st_ino=node_inode;return 0;}
static int fake_fstat(int fd,struct stat *st){(void)fd;fake_lstat(node,st);if(changed)st->st_ino++;return 0;}
static int fake_fchmod(int fd,int m){if(fd!=10)return -1;chmods++;if(fail_chmod){errno=EPERM;return -1;}chmod_mode=m;mode=m;return 0;}
static int fake_write(int fd,const char *b,size_t n){if(fd!=20){fprintf(stderr,"unexpected brightness write\n");return -1;}struct panel_permissions saved;if(!panel_permissions_parse(b,&saved)||saved.mode!=(unsigned)original||saved.device!=1||saved.inode!=2)return -1;record_writes++;return (int)n;}
static int fake_fsync(int fd){(void)fd;if(fail_sync){errno=EIO;return -1;}return 0;}
static int fake_unlink(const char *path){(void)path;record_exists=0;return 0;}
static int restore_record(void){restores++;mode=original;record_exists=0;return 0;}
#define open fake_open
#define close fake_close
#define lstat fake_lstat
#define fstat fake_fstat
#define fchmod fake_fchmod
#define write fake_write
#define fsync fake_fsync
#define unlink fake_unlink
''' + function('open_output') + '\n' + function('release_output') + '\n' + function('acquire_output') + r'''
static int cases;
#define CHECK(x) do{if(!(x)){fprintf(stderr,"case %d\n",cases);return 1;}cases++;}while(0)
static void reset(int m){mode=m;deny=changed=opens=closes=chmods=restores=record_writes=record_exists=fail_chmod=fail_sync=0;live=1;output=original=-1;chmod_mode=-1;}
int main(void){
 int modes[]={0444,0644,0664};
 for(unsigned i=0;i<3;i++){reset(modes[i]);int fd=open_output();CHECK(fd==10);CHECK(opens==1&&closes==0&&chmods==0&&record_writes==0);CHECK(mode==modes[i]);fake_close(fd);CHECK(closes==1);CHECK(acquire_output()==0);CHECK(output==10&&original==modes[i]);CHECK(mode==(modes[i]&~0222)&&record_writes==1);CHECK(release_output()==0);CHECK(mode==modes[i]&&closes==2&&restores==1&&!record_exists);}
 int errors[]={EPERM,EACCES,EROFS};
 for(unsigned i=0;i<3;i++){reset(0664);deny=errors[i];CHECK(open_output()==-1&&errno==errors[i]);CHECK(closes==0&&chmods==0&&record_writes==0);CHECK(acquire_output()==-1&&errno==errors[i]);CHECK(mode==0664&&restores==1&&!record_exists&&output==-1);}
 reset(0444);changed=1;CHECK(open_output()==-1&&errno==ESTALE);CHECK(opens==1&&closes==1&&chmods==0);CHECK(acquire_output()==-1&&errno==ESTALE);CHECK(mode==0444&&!record_exists&&output==-1);
 reset(0444);live=0;CHECK(open_output()==-1&&errno==ESTALE&&opens==0);CHECK(acquire_output()==-1&&record_writes==0);
 reset(0664);fail_chmod=1;CHECK(acquire_output()==-1);CHECK(output==-1&&mode==0664&&closes==1&&restores==1&&!record_exists);
 reset(0664);fail_sync=1;CHECK(acquire_output()==-1);CHECK(opens==0&&chmods==0&&!record_exists&&mode==0664);
 reset(0444);CHECK(acquire_output()==0);int prior=opens;CHECK(acquire_output()==0&&opens==prior);CHECK(release_output()==0&&mode==0444);
 printf("Panel access native: %d cases PASS; production open/acquire/release, syscall model only\n",cases);
}
''', encoding='utf-8')
exe = O / 'access-test.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe', '-std=c11', '-static', '-Wall', '-Wextra', '-Werror', '-Wno-misleading-indentation', '-I', str(R / 'experimental/refactor_hook/native'), str(c), '-o', str(exe)], check=True)
subprocess.run([str(exe)], check=True)
print('Panel access total: 130 cases PASS; Java/native syscall models, Android not tested')
