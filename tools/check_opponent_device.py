#!/usr/bin/env python3
"""Explicit OnePlus 1080x2400 opponent smoke check; resets the rack and leaves Vs computer ready."""
import argparse
import json
import subprocess
import time
from pathlib import Path

PACKAGE = 'io.github.ffeltrinelli.pool'
ACTIVITY = PACKAGE + '/.android.AndroidLauncher'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--output', type=Path, default=Path('build/reports/device-computer'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    def adb(*arguments):
        return subprocess.check_output([args.adb, *arguments], timeout=20)

    def shell(command):
        return adb('shell', command).decode()

    def capture(name):
        (args.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))

    def preferences():
        return shell('run-as ' + PACKAGE + ' cat shared_prefs/pool-settings.xml')

    def wait_for(predicate, description):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(.2)
        raise SystemExit('Timed out waiting for ' + description)

    def hud_visible():
        pixels = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', 'pipe:0',
            '-vf', 'crop=280:180:30:140', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
            input=adb('exec-out', 'screencap', '-p'), timeout=10)
        return sum(1 for i in range(0, len(pixels), 3) if min(pixels[i:i+3]) > 180) > 30

    def pool_is_foreground():
        activity = shell('dumpsys activity activities')
        resumed = [line for line in activity.splitlines() if 'topResumedActivity=' in line or 'mResumedActivity:' in line]
        return any(PACKAGE in line for line in resumed)

    if '1080x2400' not in shell('wm size'):
        raise SystemExit('This script requires the OnePlus 1080x2400 safe viewport.')
    checks = {}
    shell('am force-stop ' + PACKAGE)
    shell('am start -W -n ' + ACTIVITY)
    wait_for(hud_visible, 'first game frame')
    capture('reopened')
    shell('input tap 956 288')
    shell('input tap 342 829')  # Solo, starts a fresh rack.
    wait_for(lambda: 'SOLO' in preferences(), 'saved Solo mode')
    checks['solo_saved'] = True
    capture('solo-settings')
    shell('input tap 738 829')
    wait_for(lambda: 'COMPUTER' in preferences(), 'saved computer mode')
    checks['computer_saved'] = True
    capture('versus-settings')
    shell('input keyevent KEYCODE_BACK')
    capture('back-dismissed')
    pid = shell('pidof ' + PACKAGE).strip()
    shell('input keyevent KEYCODE_HOME')
    shell('am start -W -n ' + ACTIVITY)
    wait_for(hud_visible, 'resumed game frame')
    checks['background_resume_same_process'] = pid == shell('pidof ' + PACKAGE).strip()
    for index in range(3):
        shell('input tap 956 288')
        shell('input tap 540 1498')
        time.sleep(.1)
        capture('restart-' + str(index))
    shell('am force-stop ' + PACKAGE)
    shell('am start -W -n ' + ACTIVITY)
    wait_for(hud_visible, 'new process first frame')
    checks['computer_persists_after_process_restart'] = 'COMPUTER' in preferences()
    shell('input tap 956 288')
    shell('input tap 540 1678')
    wait_for(lambda: not pool_is_foreground(), 'Exit finishing the activity')
    checks['exit_finishes_activity'] = True
    shell('am start -W -n ' + ACTIVITY)
    wait_for(hud_visible, 'first game frame')
    capture('ready-to-play')
    (args.output / 'lifecycle.json').write_text(json.dumps(checks, indent=2))
    print(json.dumps(checks, indent=2))
    if not all(checks.values()):
        raise SystemExit('Device smoke failed; inspect lifecycle.json and screenshots.')


if __name__ == '__main__':
    main()
