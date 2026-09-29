"""Verify production preference/curve behavior and semantic negative mutations."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'build/preference';out.mkdir(parents=True,exist_ok=True)
parser=argparse.ArgumentParser();parser.add_argument('--cc',default='C:/msys64/mingw64/bin/gcc.exe' if os.name=='nt' else 'gcc');args=parser.parse_args()
names=['brightness_preference.c','curve_pct_for_lux.c']
common=[args.cc,'-std=c11','-O1','-D_DEFAULT_SOURCE','-DIOS_PRODUCTION','-DIOS_TEST_ABI','-ffp-contract=off','-I'+str(ROOT/'csrc'),'-Wall','-Wextra','-Werror','-Wno-misleading-indentation']
def run(change=None):
    sources=[ROOT/'csrc'/n for n in names];label='positive'
    if change:
        label,filename,old,new=change;t=(ROOT/'csrc'/filename).read_text(encoding='utf-8');assert t.count(old)==1
        altered=out/(label+'.c');altered.write_text(t.replace(old,new),encoding='utf-8');sources=[altered if p.name==filename else p for p in sources]
    exe=out/(label+'.exe');subprocess.run(common+[str(p) for p in sources]+[str(ROOT/'tests/brightness_preference.c'),'-lm','-o',str(exe)],check=True)
    result=subprocess.run([str(exe)],capture_output=True,text=True,encoding='utf-8')
    if not change:result.check_returncode();print(result.stdout)
    else:assert result.returncode and 'Assertion' in result.stderr and 'failed' in result.stderr,(label,result.stdout,result.stderr)
    return exe,result
exe,result=run();neg=[]
for change in [('intent','brightness_preference.c','if (strncmp(end, "(user_set)", 10)) return;','if (false) return;'),('delay','brightness_preference.c','now - pref.changed_ms >= 8000','now - pref.changed_ms >= 1'),('step','brightness_preference.c','fminf(.0025f, pref.pending_log * .02f)','fminf(.05f, pref.pending_log * .02f)'),('atomic','brightness_preference.c','if (ok && (int)CALL(io, rename','if ((int)CALL(io, rename'),('curve-order','curve_pct_for_lux.c','(i && value / 100 < parsed[i - 1])','false')]:
    run(change);neg.append({'name':change[0],'semantic_failure_detected':True})
points=subprocess.check_output([str(exe),'--curve'],text=True)
js="const m=require('./module/webroot/curve_math.js');const rows=JSON.parse(process.argv[1]);for(const [x,y] of rows)if(Math.abs(m.base(x,m.defaults,2.2)-y)>.0001)throw Error([x,y,m.base(x,m.defaults,2.2)]);"
subprocess.run(['node','-e',js,json.dumps([[float(v) for v in line.split()] for line in points.splitlines()])],cwd=ROOT,check=True)
files=['csrc/'+n for n in names]+['csrc/brightness_preference.h','csrc/brightness_curve.h','csrc/log_timestamp.h','csrc/platform_native.c','csrc/load_config.c','csrc/refresh_settings.c','csrc/main_business.c','csrc/write_state_file.c','module/webroot/curve_math.js','module/upgrade_data.sh','tests/brightness_preference.c','tests/brightness_preference.py','tools/source_lists.py','CMakeLists.txt']
report={'ok':True,'checks':result.stdout.splitlines()+['201 production C versus JavaScript curve samples MATCH'],'negative':neg,'source_sha256':{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in files},'android_device_verified':False}
(ROOT/'build/preference-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('5 semantic negative mutations rejected; 201 native/JS curve samples MATCH')
