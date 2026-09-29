from pathlib import Path
import os,sys,struct,json,re,math
import argparse,hashlib
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--original',type=Path,required=True);p.add_argument('--case');a=p.parse_args();fix=a.fixtures
os.environ.setdefault('IOS_TEST_DLL',str(root/'build/core-main-replay/current.dll'))
sys.path[:0]=[str(fix/'tests'),str(fix/'tools')]
os.environ['IOS_ANDROID_ELF']=str(root/'build/highlevel/luma_curve_daemon')
sys.argv=[sys.argv[0],'--original',str(a.original)]
from android_c import CompleteAndroid
from full_main_scenarios import Fixture,state
from unicorn.arm64_const import *
class Arm(Fixture,CompleteAndroid):
 def cstr(self,a):return super().cstr(a).replace('/data/local/tmp/luma_curve','/data/local/tmp/ios_brightness')
 def __init__(self,case):
  self.node_mode=0o444;self.node_opens=0;self.node_seeks=0;self.node_offsets={};self.locked_writes=0
  CompleteAndroid.__init__(self)
  self.files['/data/local/tmp/ios_brightness.conf']=(root/'module/luma_curve.conf').read_text(encoding='utf-8').replace('curve_custom=0','curve_custom=1').replace('curve_points=0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,85','curve_points='+','.join(['60']*14)).replace('preference_learning=1','preference_learning=0')
  self.setup(case)
  # Model an already-booted phone, so startup settings are read immediately.
  self.now_us=100_000_000
 def hook(self,uc,address,size,data):
  name=self.elf.plt.get(address)
  node='/sys/class/backlight/panel0-backlight/brightness'
  arg0=uc.reg_read(UC_ARM64_REG_X0) if name else 0
  if name=='chmod' and self.cstr(arg0)==node:
   self.node_mode=uc.reg_read(UC_ARM64_REG_X1);self.chmods.append((node,self.node_mode));self.finish_import(0);return
  if name=='open' and self.cstr(arg0)==node and uc.reg_read(UC_ARM64_REG_X1)&3:
   self.node_opens+=1
   if not self.node_mode&0o222:
    uc.mem_write(0x889800,struct.pack('<i',13));self.finish_import(-1);return
  if name=='lseek':
   assert self.fd.get(arg0)==node and uc.reg_read(UC_ARM64_REG_X1)==0
   self.node_offsets[arg0]=0;self.node_seeks+=1;self.finish_import(0);return
  if name=='write' and self.fd.get(arg0)==node:
   if self.node_mode==0o444:self.locked_writes+=1
   assert self.node_offsets.get(arg0,0)==0,'Persistent sysfs FD was not rewound'
   self.node_offsets[arg0]=uc.reg_read(UC_ARM64_REG_X2)
  if name=='localtime_r':
   dst=uc.reg_read(UC_ARM64_REG_X1);uc.mem_write(dst,struct.pack('<9i',0,0,12,14,10,123,2,317,0)+bytes(20));self.finish_import(dst);return
  if name=='strftime':
   blob=b'2026-09-29 19:10:00';uc.mem_write(uc.reg_read(UC_ARM64_REG_X0),blob+b'\0');self.finish_import(len(blob));return
  if name=='strcspn':
   txt=self.cstr(uc.reg_read(UC_ARM64_REG_X0));reject=self.cstr(uc.reg_read(UC_ARM64_REG_X1));self.finish_import(next((i for i,c in enumerate(txt) if c in reject),len(txt)));return
  if name=='vsnprintf':
   buf=uc.reg_read(UC_ARM64_REG_X0);cap=uc.reg_read(UC_ARM64_REG_X1);fmt=self.cstr(uc.reg_read(UC_ARM64_REG_X2));va=uc.reg_read(UC_ARM64_REG_X3)
   stack,gr,vr,go,vo=struct.unpack('<QQQii',bytes(uc.mem_read(va,32)))
   def convert(m):
    nonlocal stack,go,vo
    spec=m[0];fp=m[1] in 'fFeEgG'
    if fp and vo<0:addr=vr+vo;vo+=16
    elif not fp and go<0:addr=gr+go;go+=8
    else:addr=stack;stack+=8
    val=struct.unpack('<d' if fp else '<Q',bytes(uc.mem_read(addr,8)))[0]
    if m[1]=='s':val=self.cstr(val)
    elif m[1]=='d':val=self.signed(val,64 if 'l' in spec else 32)
    return (spec.replace('ll','').replace('l','').replace('z','').replace('%u','%d'))%val
   text=re.sub(r'%[-+ #0]*[0-9]*(?:\.[0-9]+)?(?:ll|l|z)?([sdiufFeEgG])',convert,fmt)
   blob=text.encode();uc.mem_write(buf,blob[:max(0,cap-1)]+b'\0');self.finish_import(len(blob));return
  if name in ('strtod','strtof','strtol'):
   addr=uc.reg_read(UC_ARM64_REG_X0);out=uc.reg_read(UC_ARM64_REG_X1);txt=self.cstr(addr)
   match=re.match(r'\s*[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?',txt);val=float(match[0]) if match else 0
   if out:uc.mem_write(out,struct.pack('<Q',addr+(len(match[0]) if match else 0)))
   if name=='strtol':self.finish_import(int(val));return
   uc.reg_write(UC_ARM64_REG_D0,struct.unpack('<Q',struct.pack('<d',val))[0] if name=='strtod' else struct.unpack('<I',struct.pack('<f',val))[0]);uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR));return
  if name in ('log1pf','expm1f'):
   val=struct.unpack('<f',struct.pack('<I',uc.reg_read(UC_ARM64_REG_S0)))[0]
   val=math.log1p(val) if name=='log1pf' else math.expm1(val)
   uc.reg_write(UC_ARM64_REG_S0,struct.unpack('<I',struct.pack('<f',val))[0]);uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR));return
  if name=='__errno':self.finish_import(0x889800);return
  if name=='strerror':uc.mem_write(0x889880,b'IO error\0');self.finish_import(0x889880);return
  if name=='popen' and 'screen_brightness_mode' in self.cstr(uc.reg_read(UC_ARM64_REG_X0)):
   command=self.cstr(uc.reg_read(UC_ARM64_REG_X0));h=0x801000+len(self.handles)*0x100
   self.handles[h]=dict(path=command,pos=0,text=f'{self.mode}\n{self.adjustment}\n{self.slider}\n',mode='r');self.finish_import(h);return
  if name=='write' and self.case.get('backlight_failure') and self.sleeps>=5:uc.mem_write(0x889800,struct.pack('<i',30))
  return super().hook(uc,address,size,data)
records=[]
for case in ({'name':'arm_sysfs_write','sysfs':True,'lux':400,'waits':160},
             {'name':'arm_ndk_write','ndk':True,'lux':400,'waits':160},
             {'name':'arm_mode_reacquire','sysfs':True,'lux':400,'waits':400,'mode_changes':{140:0,190:1}},
             {'name':'arm_screen_reacquire','ndk':True,'lux':400,'waits':400,'screen_changes':{140:False,190:True}},
             {'name':'arm_write_failure','sysfs':True,'lux':400,'waits':300,'backlight_failure':'write','failure_until':1000}):
 if a.case and a.case!=case['name']:continue
 m=Arm(case);assert m.call('main',(1,))==0
 v=dict(line.split('=',1) for line in m.files['/data/local/tmp/ios_brightness_state'].splitlines() if '=' in line)
 assert v['core_build']=='20260929-test03'
 assert int(v['actuator_write_attempts'])>0,(case,v,m.coverage,m.output[-5:])
 if case.get('backlight_failure'):
  assert v['actuator_write_stage']=='write_failed' and v['actuator_write_errno']=='30',v
  assert any('errno=30' in row for row in m.output),m.output[-10:]
  assert v['brightness_owner']=='write_failed_passthrough' and v['daemon_can_write']=='0',v
 else:
  assert int(v['actuator_write_successes'])>0 and int(v['last_write_result'])==0,v
  assert int(v['current_br'])>.5*int(v['max_br']),v
  assert v['actuator_write_stage']=='readback_ok',v
  assert (m.node_opens>=2 if 'reacquire' in case['name'] else m.node_opens==1) and m.locked_writes>1 and m.node_seeks>1,(m.node_opens,m.locked_writes,m.node_seeks,m.output,m.chmods)
 assert m.node_mode==0o644,'Exit must release the backlight lock'
 records.append({'name':case['name'],'ok':True,'attempts':int(v['actuator_write_attempts']),'successes':int(v['actuator_write_successes']),'stage':v['actuator_write_stage'],'errno':int(v['actuator_write_errno']),'node_opens':m.node_opens,'locked_writes':m.locked_writes,'fd_rewinds':m.node_seeks})
 print(records[-1])
report={'ok':True,'cases':records,'actual_android_elf':True,'android_device_verified':False,'elf_sha256':hashlib.sha256((root/'build/highlevel/luma_curve_daemon').read_bytes()).hexdigest(),
 'source_sha256':{str(p.relative_to(root)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in [root/'csrc/backlight_handle.h',root/'csrc/apply_brightness_frame.c',root/'csrc/state.c',root/'csrc/write_state_file.c',root/'csrc/platform_native.c',root/'csrc/core_build.h',root/'csrc/actuator_diagnostics.h',Path(__file__)]}}
(root/'build/android-actuator-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
