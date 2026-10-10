"""Verify observer parsing, restart detection, bounded output and read-only calls."""
from pathlib import Path
from tempfile import TemporaryDirectory
import importlib.util
import json
import subprocess

R = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('observer', R / 'tools/observe_device_runtime.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
cases = 0


def check(value):
    global cases
    assert value
    cases += 1


def stat(utime, stime, start):
    fields = ['S'] + ['0'] * 22
    fields[11], fields[12], fields[19], fields[21] = map(str, [utime, stime, start, 123])
    return '42 (system server) ' + ' '.join(fields)


check(module.process_stat(stat(12, 13, 2)) == {'cpu_ticks': 25, 'start_ticks': 2, 'rss_pages': 123})
zero = module.summarize({'assist_fast_lux': 0, 'assist_valid': True,
                         'system_scene': {'night_driving': False, 'private': 'omit'}})
check(zero['assist_fast_lux'] == 0 and zero['assist_valid'] and zero['scene']['night_driving'] is False and 'private' not in zero['scene'])
with TemporaryDirectory() as directory:
    observer = module.Observer('adb', None, directory)
    calls = []
    uptime, cpu, start, pid = 10, 100, 2, 42
    transport = None

    def shell(*args):
        calls.append(args)
        if args == ('pidof', 'system_server'):
            return str(pid)
        if args == ('cat', '/proc/uptime'):
            return str(uptime) + ' 0'
        if args[0:2] == ('su', '-c') and args[2].startswith('cat /proc/'):
            return stat(cpu, 20, start)
        if args == ('su', '-c', 'settings get global ' + module.STATUS):
            return json.dumps({'build': 'test', 'phase': 'active', 'private': 'omit', 'status_transport': transport,
                               'brightness_control': {'owner': 'none', 'listening': False}})
        if args == ('dumpsys', 'power'):
            return '  mWakefulness=Asleep\n  mIsPowered=true\n  mStayOn=false\n'
        raise AssertionError(args)

    observer.shell = shell
    first = observer.sample()
    check(first['cpu_ticks'] == 120 and first['mWakefulness'] == 'Asleep')
    check(first['owner'] == 'none' and first['listening'] is False and 'private' not in first)
    uptime, cpu = 20, 150
    second = observer.sample()
    check(second['cpu_ticks_per_s'] == 5)
    pid, start = 43, 21
    third = observer.sample()
    check(third['system_server_changed'] and 'cpu_ticks_per_s' not in third)
    transport = {'schema': 1, 'snapshot': 'test', 'parts': {}}
    chunked = observer.sample()
    check(chunked['status_chunked'] and chunked['phase'] == 'active' and chunked['owner'] == 'none')
    check(all('settings put' not in ' '.join(c) and 'input' not in c and 'reboot' not in c for c in calls))
    for i in range(1, 10):
        (Path(directory) / ('runtime-2000-01-%02d.jsonl' % i)).write_text('{}\n')
    untouched = Path(directory) / 'other.jsonl'
    untouched.write_text('preserve')
    observer.write(third)
    check(len(list(Path(directory).glob('runtime-????-??-??.jsonl'))) == 7)
    check(untouched.read_text() == 'preserve')
    check(json.loads((Path(directory) / 'latest.json').read_text()) == third)
    observer.shell = lambda *args: (_ for _ in ()).throw(subprocess.TimeoutExpired('adb', 15))
    check('observation_error' in observer.sample())
print('Read-only device observer: %d cases PASS; no device actions' % cases)
