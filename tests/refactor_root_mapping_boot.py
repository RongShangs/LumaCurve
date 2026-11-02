"""Managed startup file ownership and finite boot program; all commands run in host fixtures."""
from pathlib import Path
import subprocess,os,re,shutil,json
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/root-mapping-boot-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'BootTest.java';p.write_text(r'''package top.rongshangs.lumacurve.refactor;import org.json.*;import java.io.*;import java.nio.file.*;import java.util.*;
class ControlCenterRoot{static final String RECOVERY="recovery";}
class RootSettings{Map<String,String> v=new HashMap<>();String get(String k){return v.get(k);}boolean put(String k,String value){v.put(k,value);return true;}}
public class BootTest{static int cases;static void check(boolean b){if(!b)throw new AssertionError("boot entry "+cases);cases++;}public static void main(String[] a)throws Exception{
 Path home=Paths.get(a[0]);Files.createDirectories(home);File dir=home.resolve("service.d").toFile();RootSettings s=new RootSettings();JSONObject off=new JSONObject().put("enabled",false),on=new JSONObject().put("enabled",true).put("user_serial",0).put("control_center_node_mode",4);check(!RootMappingBoot.configure(s,off.toString(),dir).getBoolean("installed")&&!dir.exists());
 for(int user:new int[]{-1,0,10})for(int mode=-1;mode<=5;mode++)check(RootMappingBoot.wanted(new JSONObject(on.toString()).put("user_serial",user).put("control_center_node_mode",mode))==(user==0&&mode>0&&mode<=4));
 check(RootMappingBoot.configure(s,on.toString(),dir).getBoolean("installed"));Path file=dir.toPath().resolve(RootMappingBoot.NAME);byte[] saved=Files.readAllBytes(file);long modified=Files.getLastModifiedTime(file).toMillis();RootMappingBoot.configure(s,on.toString(),dir);check(modified==Files.getLastModifiedTime(file).toMillis()&&Arrays.equals(saved,Files.readAllBytes(file)));check(RootMappingBoot.inspect(s,dir).getBoolean("installed"));
 Files.write(file,"foreign contents".getBytes("UTF-8"));for(JSONObject c:new JSONObject[]{on,off})try{RootMappingBoot.configure(s,c.toString(),dir);throw new AssertionError();}catch(IOException expected){check(new String(Files.readAllBytes(file),"UTF-8").equals("foreign contents"));}
 Files.write(file,saved);check(!RootMappingBoot.configure(s,off.toString(),dir).getBoolean("installed")&&!Files.exists(file));RootMappingBoot.configure(s,on.toString(),dir);check(Files.exists(file));RootMappingBoot.configure(s,new JSONObject(on.toString()).put("user_serial",10).toString(),dir);check(!Files.exists(file));
 Files.write(home.resolve("script.sh"),RootMappingBoot.SCRIPT.getBytes("UTF-8"));System.out.println("Root mapping startup: "+cases+" cases PASS; owned-file operations in host temporary directory, no device Root changes");}}
''','utf-8');C=O/'classes';C.mkdir(exist_ok=True);subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(p),str(S/'RootMappingBoot.java'),str(S/'AppBuild.java'),str(S/'BrightnessControlOptions.java')],check=True)
# Unique workspace-local fixture per run; never delete or modify existing deliverables.
import tempfile
home=Path(tempfile.mkdtemp(prefix='fixture-',dir=O));result=subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.BootTest',str(home)],check=True,capture_output=True,text=True);print(result.stdout,end='')
bash=shutil.which('bash') or 'C:/Program Files/Git/bin/bash.exe';script=(home/'script.sh').read_text('utf-8');subprocess.run([bash,'-n',str(home/'script.sh')],check=True)
assert 'for delay in 0 3 10 30' in script and '/system/bin/timeout 8' in script and 'i -ge 120' in script and 'boot-resume-mapping' in script
assert 'su ' not in script and 'am start' not in script and 'brightness' not in script and 'pending' in script
root=(S/'RootControl.java').read_text('utf-8');assert 'command.equals("boot-resume-mapping")' in root and '"root_service_directory"' in root and 'out.put("mapping_startup_root",RootMappingBoot.configure(settings,raw))' in root
print('Boot shell syntax, finite waits, fresh package lookup and Root command wiring PASS; real boot entry execution remains device-dependent')

# Execute the real loop and package guards with only command/filesystem roots substituted.
fixture=home/'runner';bin=fixture/'bin';bin.mkdir(parents=True);logs=fixture/'service.d';logs.mkdir();apk=fixture/'data/app/test/base.apk';apk.parent.mkdir(parents=True);apk.write_bytes(b'fixture-not-an-apk')
def executable(name,body):
 p=bin/name;p.write_text('#!/bin/sh\n'+body+'\n','utf-8');return p
executable('getprop','echo "${BOOT_READY:-1}"')
executable('sleep','echo "sleep:$1" >> "$TRACE"')
executable('cmd','echo "${PACKAGE_RESULT:-package:$FAKE_APK}"')
executable('timeout','[ "$1" = 8 ] || [ "$1" = 3 ] || exit 93; shift; exec "$@"')
executable('app_process','echo helper >> "$TRACE"; n=$(grep -c helper "$TRACE"); if [ "$MODE" = pending ] && [ "$n" -lt 4 ]; then state=pending; elif [ "$MODE" = disabled ]; then state=disabled; else state=restored; fi; echo "LUMA_RESULT={\\\"ok\\\":true,\\\"state\\\":\\\"$state\\\"}"')
test_script=script.replace('/data/adb/service.d',logs.as_posix()).replace('/data/app/',(fixture/'data/app').as_posix()+'/').replace('/system/bin/',bin.as_posix()+'/');test_script=test_script.replace((bin/'getprop').as_posix(),'fake_getprop').replace((bin/'sleep').as_posix(),'fake_sleep');test_script='fake_getprop() { echo \"${BOOT_READY:-1}\"; }\nfake_sleep() { echo \"sleep:$1\" >> \"$TRACE\"; }\n'+test_script;runner=fixture/'run.sh';runner.write_text(test_script,'utf-8')
for mode,boot,package,calls in [('pending','1','',4),('disabled','1','',1),('restored','0','',0),('restored','1','package:/untrusted/base.apk',0)]:
 trace=fixture/('trace-'+mode+boot+str(calls));trace.write_text('','utf-8');env=dict(os.environ,TRACE=trace.as_posix(),MODE=mode,BOOT_READY=boot,FAKE_APK=apk.as_posix(),PACKAGE_RESULT=package)
 done=subprocess.run([bash,str(runner)],env=env,capture_output=True,text=True,timeout=10);lines=trace.read_text('utf-8').splitlines();assert lines.count('helper')==calls,(mode,done.stderr,(logs/'hyperlux-mapping-boot.log').read_text('utf-8'))
 if mode=='pending':assert [v for v in lines if v.startswith('sleep:')]==['sleep:0','sleep:3','sleep:10','sleep:30']
 if boot=='0':assert len(lines)==119 and done.returncode!=0
print('Boot runner: 4 scenarios PASS; actual shell flow with fake system commands and owned fixture paths')
