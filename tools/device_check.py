#!/usr/bin/env python3
"""Opt-in foreground OnePlus smoke/soak check. Requires an unlocked adb device and installed APK."""
import argparse
import json
import shlex
import statistics
import subprocess
import time
from pathlib import Path

PACKAGE = 'io.github.ffeltrinelli.pool'
ACTIVITY = PACKAGE + '/.android.AndroidLauncher'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--seconds', type=int, default=600)
    parser.add_argument('--view', choices=('top', 'player'), default='top', help='Selects and saves this in-game view for the run.')
    parser.add_argument('--output', type=Path, default=Path('build/reports/device'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*arguments):
        return subprocess.check_output([args.adb, *arguments], timeout=20)

    def shell(command):
        return adb('shell', command).decode()

    model = shell('getprop ro.product.model').strip()
    api = shell('getprop ro.build.version.sdk').strip()
    size = shell('wm size').strip()
    if '1080x2400' not in size:
        raise SystemExit('This gesture smoke check uses the OnePlus 1080x2400 viewport.')
    shell('am start -W -n ' + ACTIVITY)
    pid = shell('pidof ' + PACKAGE).strip()
    if not pid:
        raise SystemExit('Game did not launch.')
    # Validate background/resume without changing system settings.
    shell('input keyevent KEYCODE_HOME')
    shell('am start -W -n ' + ACTIVITY)
    if shell('pidof ' + PACKAGE).strip() != pid:
        raise SystemExit('Process changed during background/resume.')
    (args.output / 'oneplus-resumed.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    start = time.monotonic()
    next_shot = start
    next_sample = start
    samples = []
    errors = []
    shot_count = 0
    print(f'Started {args.seconds}s soak on {model}, API {api}', flush=True)
    while time.monotonic() - start < args.seconds:
        now = time.monotonic()
        if now >= next_shot:
            foreground = shell('dumpsys activity activities')
            resumed = [line for line in foreground.splitlines() if 'topResumedActivity=' in line or 'mResumedActivity:' in line]
            if not any(PACKAGE in line for line in resumed):
                errors.append('Game left the foreground; stopped before sending touch input.')
                break
            # OnePlus API 34 safe viewport starts at y=103; coordinates include that cutout inset.
            # Back dismisses any open settings before opening a fresh modal.
            shell('input keyevent KEYCODE_BACK')
            shell('input tap 956 288')
            shell('input tap ' + ('342' if args.view == 'top' else '738') + ' 1048')
            shell('input tap 540 1498')
            time.sleep(0.3)
            shell('input touchscreen swipe 540 1850 540 1400 220')
            shot_count += 1
            if shot_count == 1:
                (args.output / 'oneplus-shot.png').write_bytes(adb('exec-out', 'screencap', '-p'))
            next_shot = now + 30
        if now >= next_sample:
            if shell('pidof ' + PACKAGE).strip() != pid:
                errors.append('Game process stopped or restarted.')
                break
            layers = shell('dumpsys SurfaceFlinger --list').splitlines()
            layer = next((x for x in layers if f'SurfaceView[{PACKAGE}/' in x and '(BLAST)' in x), None)
            if layer:
                raw = shell('dumpsys SurfaceFlinger --latency ' + shlex.quote(layer))
                times = []
                for line in raw.splitlines():
                    fields = line.split()
                    if len(fields) == 3 and all(v.isdigit() for v in fields):
                        timestamp = int(fields[1])
                        if 0 < timestamp < 2**63 - 1:
                            times.append(timestamp)
                times = sorted(set(times))
                if len(times) > 2:
                    intervals = [(b-a)/1e6 for a,b in zip(times,times[1:])]
                    samples.append({'elapsed_seconds': round(now-start, 1), 'frames': len(times),
                                    'fps': (len(times)-1)*1e9/(times[-1]-times[0]),
                                    'p95_frame_ms': sorted(intervals)[int((len(intervals)-1)*0.95)]})
            next_sample = now + 10
        time.sleep(1)
    (args.output / 'oneplus-final.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    result = {'model': model, 'view': args.view, 'api': int(api), 'elapsed_seconds': round(time.monotonic()-start, 1),
              'scripted_breaks': shot_count, 'background_resume_same_process': True,
              'sampling': 'SurfaceFlinger presented timestamps: short windows sampled every 10s, not a continuous frame trace',
              'median_sample_fps': statistics.median(s['fps'] for s in samples) if samples else None,
              'minimum_sample_fps': min(s['fps'] for s in samples) if samples else None,
              'errors': errors, 'samples': samples}
    (args.output / 'soak.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k != 'samples'}, indent=2), flush=True)
    if errors or not samples:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
