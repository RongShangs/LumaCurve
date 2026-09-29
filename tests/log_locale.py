"""Validate UTF-8 daemon logs through both production and host IO paths."""
import hashlib, json, re, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
out = ROOT / 'build/log-locale'
out.mkdir(parents=True, exist_ok=True)
header = (ROOT / 'csrc/log_locale.h').read_text(encoding='utf-8')
pairs = [(json.loads(a), json.loads(b)) for a, b in re.findall(r'\{("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")\}', header)]
assert len(pairs) == 56
spec = r'%(?:[-+ #0]*\d*(?:\.\d+)?(?:ll|l|z)?[a-zA-Z%])'
for original, chinese in pairs:
    assert re.findall(spec, original) == re.findall(spec, chinese), original
    assert original != chinese and re.search('[\u4e00-\u9fff]', chinese)
program = r'''
#include "domain_io.h"
#include <assert.h>
static int calls;
static uint64_t capture(DomainIo *io,const char *fmt,const uint64_t *a,size_t n) {
    char output[512];assert(n==1 || n==3);
    if(n==3) {
        int size=snprintf(output,sizeof(output),fmt,(const char *)(uintptr_t)a[0],(int)a[1],(int)a[2]);
        assert(size>0 && (size_t)size<sizeof(output));
        assert(!strcmp(output,"[LumaCurve] 同步外部背光：实际=172 次数=4\n"));
        ++calls;return size;
    }
    assert(io->reals[0]==7.139 && io->reals[1]==3.8);
    int size=snprintf(output,sizeof(output),fmt,(const char *)(uintptr_t)a[0],io->reals[0],io->reals[1]);
    assert(size>0 && (size_t)size<sizeof(output));
    assert(!strcmp(output,"[LumaCurve] 低照度突亮：等待确认，原始照度=7.1 平滑照度=3.8\n"));
    ++calls;return size;
}
#ifdef IOS_TEST_ABI
uint64_t ios_native_call(DomainIo *io,unsigned kind,const uint64_t *a,size_t n) {
    assert(kind==IOS_I_fprintf && (n==3 || n==5));
    return capture(io,(const char *)(uintptr_t)a[1],a+2,n-2);
}
#else
uint64_t ios_native_print_values(DomainIo *io,FILE *stream,const char *fmt,const uint64_t *a,size_t n) {
    assert(stream==stdout);return capture(io,fmt,a,n);
}
#endif
int main(void) {
    const char *old="[%s] AUTO lux=%.1f sm=%.1f a=%.2f nits=%.1f tgt=%d cur=%d poll=%dms ev=%d%s%s\n";
    assert(strstr(luma_log_format(old),"自动调节：照度=")!=NULL);
    assert(!strcmp(luma_log_format("%s"),"%s"));
    assert(!strcmp(luma_log_format("unknown=%d"),"unknown=%d"));
    DomainIo io={0};uint64_t words[]={ARG("LumaCurve")};double real[]={7.139,3.8};
    domain_print(&io,(uintptr_t)stdout,"[%s] Low-lux rise candidate: raw=%.1f sm=%.1f\n",words,1,real,2);
    uint64_t integers[]={ARG("LumaCurve"),172,4};
    domain_print(&io,(uintptr_t)stdout,"[%s] external brightness sync: hw=%d count=%d\n",integers,3,NULL,0);
    assert(calls==2);return 0;
}
'''
source = out / 'check.c'
source.write_text(program, encoding='utf-8', newline='\n')
for mode in ('production', 'test-abi'):
    exe = out / (mode + '.exe')
    command = ['C:/msys64/mingw64/bin/gcc.exe', '-std=c11', '-O1', '-Wall', '-Wextra', '-Werror', '-DIOS_PRODUCTION', '-I' + str(ROOT / 'csrc')]
    if mode == 'test-abi': command.append('-DIOS_TEST_ABI')
    subprocess.run(command + [str(source), '-o', str(exe)], check=True)
    subprocess.run([str(exe)], check=True)
main = (ROOT / 'csrc/main_business.c').read_text(encoding='utf-8')
assert 'Quiet ALS is not a fault' in main
assert not re.search(r'io_print\([^\n]*WARN: sensor stale[^\n]*\("event_age"\)', main)
assert '"no_sensor"' in main and '"wake_no_fresh_als"' in main
report = {'ok': True, 'translated_formats': len(pairs), 'printf_arguments_unchanged': True,
          'io_paths': ['production', 'test-abi'], 'quiet_event_age_warning_removed': True,
          'source_sha256': {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
                            for name in ('csrc/log_locale.h', 'csrc/domain_io.h', 'csrc/main_business.c')}}
(ROOT / 'build/log-locale-verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('56 Chinese formats, unchanged printf arguments, both IO paths and quiet ALS diagnostics PASS')
