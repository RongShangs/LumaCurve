"""Reject semantic mutations in real repaired Java/C code (never edit live sources)."""
from pathlib import Path
import subprocess,json,hashlib
ROOT=Path(__file__).resolve().parents[1];out=ROOT/'build/audit-negative';out.mkdir(parents=True,exist_ok=True)
classes=ROOT/'build/framework-probe/classes';checks=[]
for name,file,old,new in [
 ('thermal','LumaFrameworkSliderOverride.java','return Math.min(safetyMaximum,apply(unadjusted));','return apply(unadjusted);'),
 ('convergence','LumaFrameworkConvergence.java','return now-since>=15000;','return false;'),
 ('speed','LumaFrameworkOutputRamp.java','.04f*dt*(to>from?brightenSpeed:darkenSpeed)','.04f*dt')]:
 directory=out/name;directory.mkdir(exist_ok=True);path=directory/file
 text=(ROOT/'experimental/framework_output'/file).read_text();assert old in text
 path.write_text(text.replace(old,new))
 subprocess.run(['javac','--release','8','-cp',str(classes),'-d',str(directory),str(path)],check=True)
 result=subprocess.run(['java','-cp',str(directory)+';'+str(classes),'LumaFrameworkAuditRepairTest'],capture_output=True,text=True)
 assert result.returncode!=0 and 'AssertionError' in result.stderr,(name,result.stdout,result.stderr)
 checks.append(name)
# Framework source overflow witness, using the existing compiled isolated IO fixture.
text=(ROOT/'csrc/refresh_settings.c').read_text();old='memcpy(STRING(settings_read_source),"framework",10);';assert old in text
path=out/'overflow.c';path.write_text(text.replace(old,'memcpy(STRING(settings_read_source),"framework_provider",19);'))
exe=out/'overflow.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-O1','-std=c11','-DIOS_PRODUCTION','-DIOS_TEST_ABI','-DLUMA_FRAMEWORK_BACKEND','-I'+str(ROOT/'csrc'),str(path),str(ROOT/'build/code-audit-20260930/settings_audit.c'),'-lm','-o',str(exe)],check=True)
result=subprocess.run([str(exe)],capture_output=True,text=True)
assert result.returncode!=0 and 'Assertion failed' in result.stderr
checks.append('framework-source-overflow')
(ROOT/'build/audit-negative-verification.json').write_text(json.dumps({'ok':True,'checks':checks,'source_sha256':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in [ROOT/'csrc/refresh_settings.c']+list((ROOT/'experimental/framework_output').glob('LumaFramework*.java'))}},indent=2)+'\n')
print('Audit negative: 4 independently compiled regressions rejected')
