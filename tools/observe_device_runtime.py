"""Read-only ADB observations for HyperLux; raw data stays outside release archives.

Does not refresh the app status, start sensors, change settings, wake the display,
install builds or reboot. USB/ADB and charging affect power measurements.
"""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import json
import os
import re
import subprocess
import time

STATUS = 'lumacurve_refactor_status_v1'


def process_stat(text):
    fields = text[text.rindex(')') + 2:].split()
    return {'cpu_ticks': int(fields[11]) + int(fields[12]),
            'start_ticks': int(fields[19]), 'rss_pages': int(fields[21])}


def summarize(status):
    control = status.get('brightness_control') or {}
    scene = status.get('scene_control') or {}
    system_scene = status.get('system_scene') or {}
    return {**{k: status.get(k) for k in ['build', 'phase', 'elapsed_ms', 'consumed',
                                        'main_fast_lux', 'assist_fast_lux', 'assist_valid',
                                        'assist_sampling_enabled', 'assist_reading_state',
                                        'sensor_reference_name', 'sensor_status']},
            'scene': {k: system_scene.get(k) for k in ['night_driving', 'night_wake',
                                                       'assist_reset_pending', 'proximity_near']},
            'owner': control.get('owner'), 'listening': control.get('listening'),
            'control_error': control.get('error'), 'scene_error': scene.get('error'),
            'main_samples': control.get('watch_main_samples'),
            'assist_samples': control.get('watch_assist_samples'),
            'sensor_evaluations': control.get('watch_evaluations')}


class Observer:
    def __init__(self, adb, serial, output):
        self.adb = [adb] + (['-s', serial] if serial else [])
        self.output = Path(output).resolve()
        self.previous = None

    def shell(self, *args):
        result = subprocess.run(self.adb + ['shell', *args], capture_output=True,
                                text=True, encoding='utf8', errors='replace', timeout=15)
        if result.returncode:
            raise RuntimeError('ADB command failed: ' + result.stderr.strip()[:160])
        return result.stdout.strip()

    def sample(self):
        record = {'utc': datetime.now(timezone.utc).isoformat()}
        try:
            pid = self.shell('pidof', 'system_server')
            if not re.fullmatch(r'[0-9]+', pid):
                raise RuntimeError('system_server PID unavailable')
            record['pid'] = int(pid)
            record['uptime_s'] = float(self.shell('cat', '/proc/uptime').split()[0])
            record.update(process_stat(self.shell('su', '-c', 'cat /proc/' + pid + '/stat')))
            status = json.loads(self.shell('su', '-c', 'settings get global ' + STATUS))
            # Never force a status refresh, which would alter the measured workload.
            record.update(summarize(status))
            if status.get('status_transport'):
                record['status_chunked'] = True
            power = self.shell('dumpsys', 'power')
            for name in ['mWakefulness', 'mIsPowered', 'mStayOn']:
                found = re.search(r'^\s*' + name + r'=([^\r\n]+)', power, re.M)
                if found:
                    record[name] = found.group(1)
            old = self.previous
            if old and (old.get('pid'), old.get('start_ticks')) == (record['pid'], record['start_ticks']):
                elapsed = record['uptime_s'] - old['uptime_s']
                if elapsed > 0:
                    record['cpu_ticks_per_s'] = round((record['cpu_ticks'] - old['cpu_ticks']) / elapsed, 3)
            elif old:
                record['system_server_changed'] = True
            self.previous = record.copy()
        except (OSError, ValueError, KeyError, IndexError, RuntimeError, subprocess.TimeoutExpired) as error:
            record['observation_error'] = str(error)[:240]
        return record

    def write(self, record):
        self.output.mkdir(parents=True, exist_ok=True)
        today = datetime.now(timezone.utc).strftime('%Y-%m-%d')
        with (self.output / ('runtime-' + today + '.jsonl')).open('a', encoding='utf8') as stream:
            stream.write(json.dumps(record, ensure_ascii=False) + '\n')
        # Keep at most seven daily files; only prune this observer's exact filenames.
        for path in sorted(self.output.glob('runtime-????-??-??.jsonl'))[:-7]:
            if re.fullmatch(r'runtime-\d{4}-\d{2}-\d{2}\.jsonl', path.name):
                path.unlink()
        temporary = self.output / 'latest.json.tmp'
        temporary.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf8')
        temporary.replace(self.output / 'latest.json')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial')
    parser.add_argument('--output', default='build/device-observations')
    parser.add_argument('--interval', type=int, default=300)
    parser.add_argument('--hours', type=float, default=168)
    parser.add_argument('--once', action='store_true')
    args = parser.parse_args()
    if args.interval < 60 or not 0 < args.hours <= 720:
        parser.error('interval must be >=60 seconds and hours must be in (0, 720]')
    observer = Observer(args.adb, args.serial, args.output)
    observer.output.mkdir(parents=True, exist_ok=True)
    pidfile = observer.output / 'observer.pid'
    try:
        fd = os.open(pidfile, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        parser.error('observer.pid exists; verify the previous observer has stopped before restarting')
    with os.fdopen(fd, 'w') as stream:
        stream.write(str(os.getpid()))
    try:
        deadline = time.monotonic() + args.hours * 3600
        while time.monotonic() < deadline and not (observer.output / 'STOP').exists():
            record = observer.sample()
            observer.write(record)
            if args.once:
                print(json.dumps(record, ensure_ascii=False))
                break
            # Responsive stop without additional ADB calls between observations.
            until = min(deadline, time.monotonic() + args.interval)
            while time.monotonic() < until and not (observer.output / 'STOP').exists():
                time.sleep(min(1, max(0, until - time.monotonic())))
    finally:
        pidfile.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
