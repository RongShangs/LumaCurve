"""Extract HBM/range/clamp consumers from supplied local DEX; never bundle firmware."""
from pathlib import Path
import argparse, zipfile, struct, hashlib, zlib
from loguru import logger
from androguard.core.dex import DEX

logger.disable('androguard')
def parser_input(raw):
    # Androguard predates Android 17 hidden-API metadata. Remove only that map
    # entry in a temporary parser copy; method bytecode and offsets stay intact.
    data = bytearray(raw)
    offset = struct.unpack_from('<I', data, 52)[0]
    count = struct.unpack_from('<I', data, offset)[0]
    entries = [bytes(data[offset+4+i*12:offset+16+i*12]) for i in range(count)]
    keep = [entry for entry in entries if struct.unpack_from('<H', entry)[0] != 0xf000]
    if len(keep) != count:
        struct.pack_into('<I', data, offset, len(keep))
        data[offset+4:offset+4+count*12] = b''.join(keep) + bytes((count-len(keep))*12)
        data[12:32] = hashlib.sha1(data[32:]).digest()
        struct.pack_into('<I', data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)
parser = argparse.ArgumentParser()
parser.add_argument('firmware', type=Path)
parser.add_argument('--out', type=Path, required=True)
args = parser.parse_args()
args.out.mkdir(parents=True, exist_ok=True)
names = {'HighBrightnessModeController', 'HighBrightnessModeMetadata', 'HbmEvent', 'BrightnessRangeController',
         'DisplayPowerController', 'DisplayPowerControllerImpl', 'AutomaticBrightnessController',
         'AutomaticBrightnessControllerImpl', 'DisplayDeviceConfig', 'HighBrightnessModeData',
         'BrightnessRangeControllerImpl', 'NormalBrightnessModeController', 'BrightnessThrottler', 'BrightnessClamperController', 'BrightnessThermalClamper', 'BrightnessPowerClamper', 'BrightnessReason'}
for jar in sorted(args.firmware.rglob('*.jar')):
    if jar.name not in {'services.jar', 'miui-services.jar'}:
        continue
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if not entry.endswith('.dex'):
                continue
            dex = DEX(parser_input(archive.read(entry)))
            for cls in dex.get_classes():
                if cls.get_name().rsplit('/', 1)[-1].rstrip(';') not in names:
                    continue
                dest = args.out / (cls.get_name()[1:-1].replace('/', '.') + '.txt')
                with dest.open('w', encoding='utf-8') as out:
                    out.write(f'{jar.name}:{entry} {cls.get_name()}\n')
                    for field in cls.get_fields():
                        out.write(f'FIELD {field.get_name()} {field.get_descriptor()}\n')
                    for method in cls.get_methods():
                        out.write(f'\nMETHOD {method.get_name()} {method.get_descriptor()} flags={method.get_access_flags_string()}\n')
                        for offset, ins in method.get_instructions_idx():
                            out.write(f'{offset:06x} {ins.get_name()} {ins.get_output()}\n')
                print(dest, flush=True)
            del dex
