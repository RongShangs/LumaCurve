"""Exercise production native wrappers with controlled failing kernel IO."""
from pathlib import Path
import hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[1]
out=ROOT/'build/backlight-diagnostics';out.mkdir(parents=True,exist_ok=True)
native=(ROOT/'csrc/platform_native.c').read_text(encoding='utf-8')
wrappers=native[native.index('int ios_native_open('):native.index('int ios_native_clock_gettime(')]
program=r'''
#include <assert.h>
#include <stdint.h>
#include <fcntl.h>
#include <sys/types.h>
#include <unistd.h>
#include "backlight_write_diagnostics.h"
static int mode;
static const char *ios_backlight_brightness(void) { return "/test/brightness"; }
static int fake_open(const char *p,int flags,...) { (void)p;(void)flags;if(mode==1){errno=EACCES;return -1;}return 7; }
static int fake_close(int fd) { (void)fd;if(mode==4){errno=EIO;return -1;}return 0; }
static int64_t fake_write(int fd,const void *p,size_t n) { (void)fd;(void)p;if(mode==2){errno=EROFS;return -1;}return mode==3?1:(int64_t)n; }
static int fake_chmod(const char *p,mode_t v) { (void)p;(void)v;return 0; }
static off_t fake_lseek(int fd,off_t p,int w) { (void)fd;(void)w;return p; }
#define open fake_open
#define close fake_close
#define write fake_write
#define chmod fake_chmod
#define lseek fake_lseek
#include "backlight_handle.h"
''' + wrappers + r'''
int main(void) {
(void)luma_backlight_acquire;(void)luma_backlight_release;
mode=1;assert(ios_native_open("/test/brightness",O_WRONLY,0)==-1 && errno==EACCES);
assert(ios_native_open("/test/brightness",O_WRONLY,0)==-1 && errno==EACCES);
mode=0;assert(ios_native_open("/test/brightness",O_WRONLY,0)==7);
mode=2;assert(ios_native_write(7,"123",3)==-1 && errno==EROFS);
mode=3;assert(ios_native_write(7,"123",3)==1);
mode=4;assert(ios_native_close(7)==-1 && errno==EIO);
mode=1;assert(ios_native_open("/test/other",O_WRONLY,0)==-1);
return 0;
}
'''
source=out/'check.c';source.write_text(program,encoding='utf-8')
exe=out/'check.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-DO_CLOEXEC=0','-O1','-Wall','-Wextra','-Werror','-I'+str(ROOT/'csrc'),str(source),'-o',str(exe)],check=True)
result=subprocess.run([str(exe)],capture_output=True,text=True,encoding='utf-8');result.check_returncode()
for stage in ('open','write','short_write','close'):assert result.stderr.count('阶段='+stage+' ')==1,result.stderr
report={'ok':True,'checks':['open/write/short-write/close failures reported','duplicate open failures throttled','errno and syscall results preserved','unrelated file IO not reported'],
        'android_device_verified':False,'source_sha256':{name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in ('csrc/platform_native.c','csrc/backlight_write_diagnostics.h','tests/backlight_diagnostics.py')}}
(ROOT/'build/backlight-diagnostics-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Native backlight failure diagnostics: 4 checks PASS')
