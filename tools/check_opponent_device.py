#!/usr/bin/env python3
"""Explicit OnePlus 1080x2400 opponent smoke check; resets the rack and leaves Vs computer ready."""
import argparse
import json
import subprocess
import time
import xml.etree.ElementTree as ET
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
        if command.startswith('input ') and not pool_is_foreground():
            raise SystemExit('Game left the foreground; stopped before sending touch input.')
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
            '-vf', 'crop=110:110:900:245', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'],
            input=adb('exec-out', 'screencap', '-p'), timeout=10)
        # Require Pool's green gear button and cream icon, not arbitrary bright launcher pixels.
        green = sum(1 for i in range(0, len(pixels), 3)
                    if all(abs(pixels[i + c] - value) < 5 for c, value in enumerate((50, 80, 79))))
        cream = sum(1 for i in range(0, len(pixels), 3)
                    if all(abs(pixels[i + c] - value) < 5 for c, value in enumerate((242, 229, 200))))
        return green > 1000 and cream > 50

    def pool_is_foreground():
        activity = shell('dumpsys activity activities')
        resumed = [line for line in activity.splitlines() if 'topResumedActivity=' in line or 'mResumedActivity:' in line]
        focused = shell('dumpsys window').splitlines()
        return (any(PACKAGE in line for line in resumed)
                and any('mCurrentFocus=' in line and PACKAGE in line for line in focused))

    if '1080x2400' not in shell('wm size'):
        raise SystemExit('This script requires the OnePlus 1080x2400 safe viewport.')
    def preference(name, default=None):
        node = ET.fromstring(preferences()).find(f"./string[@name='{name}']")
        return default if node is None else node.text

    def select_difficulty(level, x):
        shell(f'input tap {x} 1402')
        wait_for(lambda: preference('opponent-difficulty', 'MEDIUM') == level, f'saved {level} difficulty')

    checks = {}
    shell('am force-stop ' + PACKAGE)
    shell('am start -W -n ' + ACTIVITY)
    wait_for(lambda: pool_is_foreground() and hud_visible(), 'first foreground game frame')
    capture('reopened')
    shell('input tap 956 288')
    capture('settings')
    for level, x in [('EASY', 283), ('MEDIUM', 540), ('HARD', 797)]:
        select_difficulty(level, x)
        capture('difficulty-' + level.lower())
    checks['difficulty_taps_save_all_levels'] = True
    shell('input touchscreen swipe 797 1402 100 1402 600')
    wait_for(lambda: preference('opponent-difficulty') == 'EASY', 'left drag clamps to Easy')
    shell('input touchscreen swipe 283 1402 1000 1402 600')
    wait_for(lambda: preference('opponent-difficulty') == 'HARD', 'right drag clamps to Hard')
    checks['difficulty_drag_snaps_and_clamps'] = True
    shell('input tap 20 1402')
    checks['outside_tap_preserves_difficulty'] = preference('opponent-difficulty') == 'HARD'
    shell('input tap 926 718')  # X dismisses the modal.
    capture('x-dismissed')
    shell('input tap 956 288')
    select_difficulty('MEDIUM', 540)
    capture('difficulty-medium-restored')
    shell('input keyevent KEYCODE_BACK')
    capture('back-dismissed')
    pid = shell('pidof ' + PACKAGE).strip()
    shell('input tap 956 288')  # Suspend with Settings still open through Home/resume.
    capture('before-background')
    shell('input keyevent KEYCODE_HOME')
    shell('am start -W -n ' + ACTIVITY)
    wait_for(pool_is_foreground, 'resumed foreground game')
    checks['background_resume_same_process'] = pid == shell('pidof ' + PACKAGE).strip()
    capture('after-background')
    select_difficulty('HARD', 797)  # This tap succeeds only if the modal stayed open.
    checks['background_preserves_open_settings'] = preference('opponent-difficulty') == 'HARD'
    shell('input keyevent KEYCODE_BACK')
    for index in range(3):
        shell('input tap 956 288')
        shell('input tap 342 1672')
        time.sleep(.1)
        capture('restart-' + str(index))
    shell('am force-stop ' + PACKAGE)
    shell('am start -W -n ' + ACTIVITY)
    wait_for(lambda: pool_is_foreground() and hud_visible(), 'new process foreground frame')
    checks['difficulty_persists_after_process_restart'] = preference('opponent-difficulty') == 'HARD'
    shell('input tap 956 288')
    select_difficulty('MEDIUM', 540)
    shell('input tap 738 1672')
    wait_for(lambda: not pool_is_foreground(), 'Exit finishing the activity')
    checks['exit_finishes_activity'] = True
    checks['medium_saved_before_exit'] = preference('opponent-difficulty') == 'MEDIUM'
    shell('am start -W -n ' + ACTIVITY)
    wait_for(lambda: pool_is_foreground() and hud_visible(), 'first foreground game frame')
    capture('ready-to-play')
    (args.output / 'lifecycle.json').write_text(json.dumps(checks, indent=2))
    print(json.dumps(checks, indent=2))
    if not all(checks.values()):
        raise SystemExit('Device smoke failed; inspect lifecycle.json and screenshots.')


if __name__ == '__main__':
    main()
