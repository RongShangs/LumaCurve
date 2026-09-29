"""Old default retention migrates once; later user choices remain untouched."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'module/migrate_log_retention.sh'
SHELL = 'C:/msys64/usr/bin/sh.exe' if os.name == 'nt' else 'sh'

def shell_path(path):
    text = str(path)
    return '/' + text[0].lower() + text[2:].replace('\\', '/') if os.name == 'nt' else text

def run(config, marker):
    args = [SHELL, '-c', 'export PATH=/usr/bin:/bin:$PATH; sh "$1" "$2" "$3"',
            'sh', str(SCRIPT), shell_path(config), shell_path(marker)] if os.name == 'nt' else \
           [SHELL, str(SCRIPT), str(config), str(marker)]
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', check=True).stdout

with tempfile.TemporaryDirectory(prefix='luma-retention-') as folder:
    config = Path(folder) / 'luma.conf'
    marker = Path(folder) / 'persist/retention-migrated'
    config.write_text('log_retention_days=7\nfuture_option=keep\n', encoding='utf-8')
    assert run(config, marker).strip() == 'migrated'
    assert config.read_text(encoding='utf-8') == 'log_retention_days=3\nfuture_option=keep\n'
    assert marker.exists()
    config.write_text('log_retention_days=7\nfuture_option=keep\n', encoding='utf-8')
    assert run(config, marker) == ''
    assert config.read_text(encoding='utf-8').startswith('log_retention_days=7\n')
    marker.unlink()
    config.write_text('log_retention_days=14\n', encoding='utf-8')
    assert run(config, marker) == ''
    assert config.read_text(encoding='utf-8') == 'log_retention_days=14\n'
print('log retention: old default migrates once, later and custom choices preserved PASS')
