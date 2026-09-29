"""Only an untouched v11 default curve should receive the brighter v12 profile."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
def shell_path(path):
    value = path.as_posix()
    return '/' + value[0].lower() + value[2:] if len(value)>2 and value[1]==':' else value
default = root / 'module/luma_curve.conf'
old = '0.1,0.2,0.75,1.15,5.6,6.6,8.9,9.9,10.9,12.2,22,25,65,85'
new = '2,2.8,3.5,4.2,6.5,7.5,10,11,12,13.5,22,25,65,85'
source = default.read_text(encoding='utf-8')
assert 'config_version=12' in source and f'curve_points={new}' in source
for custom, curve, expected in [(0, old, new), (1, old, old), (0, '3,3,3,4,7,8,11,12,13,14,22,25,65,85', '3,3,3,4,7,8,11,12,13,14,22,25,65,85')]:
    with tempfile.TemporaryDirectory(dir=root / 'build') as folder:
        path = Path(folder)
        runtime = path / 'luma_curve.conf'
        with runtime.open('w', encoding='utf-8', newline='\n') as file:
            file.write(source.replace('config_version=12', 'config_version=11')
                      .replace('curve_custom=0', f'curve_custom={custom}')
                      .replace(f'curve_points={new}', f'curve_points={curve}'))
        env = dict(os.environ, IOS_PERSIST_DIR=shell_path(path), IOS_UPGRADE_LOG=shell_path(path / 'upgrade.log'))
        env['PATH'] = 'C:/msys64/usr/bin;' + env.get('PATH', '')
        command = f'. "{shell_path(root / "module/upgrade_data.sh")}"; ios_config_migrate "{shell_path(default)}" "{shell_path(runtime)}"'
        done = subprocess.run(['C:/msys64/usr/bin/bash.exe', '-c', command], env=env, capture_output=True, text=True, encoding='utf-8', errors='replace')
        assert done.returncode == 0, done.stderr
        output = runtime.read_text(encoding='utf-8')
        assert 'config_version=12' in output
        assert f'curve_points={expected}' in output
        assert f'curve_custom={custom}' in output
print('curve default migration: 3 cases PASS')
