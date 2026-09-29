"""Read device DEX files locally; do not redistribute firmware or generated disassembly."""
from pathlib import Path
import argparse, zipfile, struct, hashlib, zlib
from loguru import logger
from androguard.core.dex import DEX

logger.disable('androguard')
def parser_input(raw):
 # This parser's hidden-API enum predates Android 17. Omit only that metadata
 # map entry in an in-memory copy; all bytecode and its offsets stay intact.
 data=bytearray(raw); offset=struct.unpack_from('<I',data,52)[0]
 count=struct.unpack_from('<I',data,offset)[0]
 entries=[bytes(data[offset+4+i*12:offset+16+i*12]) for i in range(count)]
 keep=[e for e in entries if struct.unpack_from('<H',e)[0]!=0xf000]
 if len(keep)!=count:
  struct.pack_into('<I',data,offset,len(keep))
  data[offset+4:offset+4+count*12]=b''.join(keep)+bytes((count-len(keep))*12)
  data[12:32]=hashlib.sha1(data[32:]).digest()
  struct.pack_into('<I',data,8,zlib.adler32(data[12:])&0xffffffff)
 return bytes(data)
p=argparse.ArgumentParser();p.add_argument('firmware',type=Path);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar');a=p.parse_args()
a.out.mkdir(parents=True,exist_ok=True)
classes={
 'Landroid/hardware/display/DisplayManagerGlobal;',
 'Lcom/android/server/display/DisplayManagerService;',
 'Lcom/android/server/display/DisplayManagerService$BinderService;',
 'Lcom/android/server/display/DisplayPowerController;',
 'Lcom/android/server/display/DisplayPowerControllerImpl;',
 'Lcom/android/server/display/MiuiRampAnimator;',
 'Lcom/android/server/display/DualSensorPolicy;',
 'Lcom/android/server/display/MiuiBrightnessUtilsImpl;',
 'Lcom/android/server/display/MiuiPhysicalBrightnessMappingStrategy;',
 'Lcom/android/server/display/AutomaticBrightnessControllerImpl;',
 'Lcom/android/server/display/brightness/strategy/AutomaticBrightnessStrategy;',
 'Lcom/android/server/display/brightness/strategy/TemporaryBrightnessStrategy;',
 'Lcom/android/server/display/brightness/DisplayBrightnessStrategySelector;',
 'Lcom/android/server/display/brightness/DisplayBrightnessController;',
 'Lcom/android/server/display/DisplayPowerController$DisplayControllerHandler;',
 'Lcom/android/server/display/RampAnimator;',
 'Lcom/android/server/display/RampAnimator$DualRampAnimator;',
 'Lcom/android/server/display/RampAnimator$1;',
}
for jar in sorted(a.firmware.glob('*.jar')):
 if a.jar and jar.name!=a.jar:continue
 with zipfile.ZipFile(jar) as z:
  for entry in z.namelist():
   if not entry.endswith('.dex'):continue
   d=DEX(parser_input(z.read(entry)))
   for c in d.get_classes():
    if c.get_name() not in classes:continue
    path=a.out/(c.get_name()[1:-1].replace('/','.')+'.txt')
    with path.open('w',encoding='utf-8') as out:
     out.write(f'{jar.name}:{entry} {c.get_name()}\n')
     for f in c.get_fields():out.write(f'FIELD {f.get_name()} {f.get_descriptor()} {f.get_init_value()}\n')
     for m in c.get_methods():
      out.write(f'\nMETHOD {m.get_name()} {m.get_descriptor()} flags={m.get_access_flags_string()}\n')
      code=m.get_code()
      if not code:continue
      out.write(f'registers={code.get_registers_size()} ins={code.get_ins_size()}\n')
      for offset,i in m.get_instructions_idx():out.write(f'{offset:06x} {i.get_name()} {i.get_output()}\n')
    print(path.name,flush=True)
   del d
