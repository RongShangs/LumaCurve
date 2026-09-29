"""Current-boot log selection, including size rotation and stale boot markers."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'module/current_boot_log.sh'
SHELL = 'C:/msys64/usr/bin/sh.exe' if os.name == 'nt' else 'sh'

with tempfile.TemporaryDirectory(prefix='luma-log-') as folder:
    log = Path(folder) / 'luma.log'
    marker = Path(str(log) + '.boot_id')

    def read(boot):
        path = str(log)
        if os.name == 'nt':
            path = '/' + path[0].lower() + path[2:].replace('\\', '/')
        args = [SHELL, '-c', 'export PATH=/usr/bin:/bin:$PATH; sh "$1" "$2" "$3"',
                'sh', str(SCRIPT), path, boot] if os.name == 'nt' else [SHELL, str(SCRIPT), path, boot]
        return subprocess.run(args, capture_output=True, text=True,
                              encoding='utf-8', check=True).stdout

    log.write_text('old line\n本次开机日志开始 LUMA_BOOT_ID=boot-a\na line\n'
                   '本次开机日志开始 LUMA_BOOT_ID=boot-b\nb line\n', encoding='utf-8')
    marker.write_text('boot-b\n', encoding='utf-8')
    assert read('boot-b') == '本次开机日志开始 LUMA_BOOT_ID=boot-b\nb line\n'
    assert read('boot-c') == ''

    log.write_text('b rotated tail\n', encoding='utf-8')
    assert read('boot-b') == 'b rotated tail\n'
    assert read('boot-a') == ''
    print('current boot log: previous boot filtered, rotated tail kept, stale marker rejected PASS')
