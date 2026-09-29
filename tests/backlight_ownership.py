"""Model kernfs open permission checks against the actual native wrappers.

Also restore the old per-frame close as a semantic negative: frame two fails.
"""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
out=ROOT/'build/backlight-ownership';out.mkdir(parents=True,exist_ok=True)
native=(ROOT/'csrc/platform_native.c').read_text(encoding='utf-8')
wrappers=native[native.index('int ios_native_open('):native.index('int ios_native_clock_gettime(')]
wrappers+=native[native.index('int ios_native_chmod('):native.index('void * ios_native_memset(')]
program=r'''
#include <assert.h>
#include <stdint.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unistd.h>
#include <sys/stat.h>
#include "backlight_write_diagnostics.h"
static const char *ios_backlight_brightness(void) { return "/test/brightness"; }
static unsigned permissions=0644;
static int live,opens,closes,position,value=100,fault;
static int fake_open(const char *path,int flags,...) {
    assert(!strcmp(path,"/test/brightness"));assert((flags&O_ACCMODE)==O_WRONLY);
    opens++;
    if(!(permissions&0222) || fault==1){errno=EACCES;return -1;}
    assert(!live);live=1;position=0;return 7;
}
static int fake_close(int fd) {
    assert(fd==7 && live);live=0;closes++;
    if(fault==5){errno=EIO;return -1;}return 0;
}
static int fake_chmod(const char *path,mode_t mode) {
    assert(!strcmp(path,"/test/brightness"));
    if(fault==2 && mode==0444){errno=EPERM;return -1;}
    permissions=mode;return 0;
}
static off_t fake_lseek(int fd,off_t offset,int whence) {
    assert(fd==7 && live && whence==SEEK_SET && offset==0);
    if(fault==3){errno=ESPIPE;return -1;}position=0;return 0;
}
static ssize_t fake_write(int fd,const void *text,size_t n) {
    assert(fd==7 && live);
    if(fault==4){errno=EROFS;return -1;}
    /* kernfs validates the descriptor at open; writes still work after chmod. */
    if(position){errno=EINVAL;return -1;}
    char buffer[32];assert(n<sizeof(buffer));memcpy(buffer,text,n);buffer[n]=0;
    value=atoi(buffer);position+=(int)n;return (ssize_t)n;
}
#define open fake_open
#define close fake_close
#define chmod fake_chmod
#define lseek fake_lseek
#define write fake_write
#include "backlight_handle.h"
''' + wrappers + r'''
int main(void) {
    /* Recover an old daemon's stale 0444; open precedes the new lock. */
    permissions=0444;
    assert(ios_native_chmod("/test/brightness",0444)==0);
    assert(live && permissions==0444 && ios_native_backlight_ready());
    int count=opens;
    for(int i=0;i<1000;i++) {
        int fd=ios_native_open("/test/brightness",O_WRONLY,0);
        assert(fd==7 && ios_native_write(fd,"200",3)==3);
        assert(ios_native_close(fd)==0);
    }
    assert(value==200 && opens==count && closes==0);
    assert(ios_native_chmod("/test/brightness",0444)==0 && opens==count);
    assert(ios_native_chmod("/test/brightness",0644)==0);
    assert(!live && !ios_native_backlight_ready() && permissions==0644 && closes==1);
    /* Reacquire on wake/mode changes and close once on each release. */
    for(int i=0;i<50;i++) {
        assert(ios_native_chmod("/test/brightness",0444)==0);
        assert(ios_native_chmod("/test/brightness",0644)==0);
    }
    assert(!live && closes==51);
    fault=1;assert(ios_native_chmod("/test/brightness",0444)==-1);
    assert(!live && permissions==0644 && !ios_native_backlight_ready());
    fault=2;assert(ios_native_chmod("/test/brightness",0444)==-1);
    assert(!live && permissions==0644 && !ios_native_backlight_ready());
    fault=0;assert(ios_native_chmod("/test/brightness",0444)==0);
    fault=3;assert(ios_native_write(7,"201",3)==-1 && errno==ESPIPE);
    fault=4;assert(ios_native_write(7,"201",3)==-1 && errno==EROFS);
    fault=5;assert(ios_native_chmod("/test/brightness",0644)==-1);
    assert(!live && permissions==0644 && !ios_native_backlight_ready());
    return 0;
}
'''
# stdlib is required only by the model, not injected into the daemon.
program='#include <stdlib.h>\n'+program
def run(name,text):
    source=out/(name+'.c');source.write_text(text,encoding='utf-8')
    exe=out/(name+'.exe')
    subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-DO_CLOEXEC=0','-O1','-Wall','-Wextra','-Werror','-I'+str(ROOT/'csrc'),str(source),'-o',str(exe)],check=True)
    return subprocess.run([str(exe)],capture_output=True,text=True,encoding='utf-8')
result=run('fixed',program);assert result.returncode==0,(result.stdout,result.stderr)
negative=program.replace('if (a == luma_owned_backlight_fd && a >= 0) return 0;','if (a == luma_owned_backlight_fd) luma_owned_backlight_fd = -1;')
assert negative!=program
result=run('old-close',negative);assert result.returncode!=0,'Old close-after-frame must fail with 0444'
sources=['csrc/platform_native.c','csrc/backlight_handle.h','csrc/backlight_write_diagnostics.h','tests/backlight_ownership.py']
report={'ok':True,'checks':['stale 0444 recovery','1000 writes with one held FD and offset reset','idempotent acquire','50 release/reacquire cycles without leaks','open and chmod rollback','seek/write/close errors','restored old close detected by negative'],
        'negative_detected':True,'android_device_verified':False,'source_sha256':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in sources}}
(ROOT/'build/backlight-ownership-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Backlight ownership: 7 checks and old-close semantic negative PASS')
