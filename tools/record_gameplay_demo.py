#!/usr/bin/env python3
"""Record production desktop gameplay and render its actual sound events into an MP4."""
import argparse
from array import array
from collections import Counter
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
import wave

ROOT = Path(__file__).resolve().parents[1]
SAMPLE_RATE = 44100


def load_samples(path):
    with wave.open(str(path), 'rb') as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (1, 2, SAMPLE_RATE):
            raise ValueError(f'Expected mono 16-bit {SAMPLE_RATE} Hz PCM: {path}')
        samples = array('h', source.readframes(source.getnframes()))
    if sys.byteorder != 'little':
        samples.byteswap()
    return samples


def mix_audio(timeline, assets, destination):
    fps, frames = timeline['fps'], timeline['frames']
    if fps <= 0 or frames <= 0:
        raise ValueError('Frame count and rate must be positive')
    mixed = array('f', [0.0]) * round(frames * SAMPLE_RATE / fps)
    clips = {}
    for voice in timeline['voices']:
        name = voice['sound']
        if name not in clips:
            clips[name] = load_samples(assets / name)
        gain = voice['volume']
        start_frame, stop_frame = voice['startFrame'], voice['stopFrame']
        if not 0 <= gain <= 1 or not 0 <= start_frame <= stop_frame:
            raise ValueError(f'Invalid playback event: {voice}')
        start = round(start_frame * SAMPLE_RATE / fps)
        stop = min(len(mixed), round(stop_frame * SAMPLE_RATE / fps), start + len(clips[name]))
        for offset in range(max(0, stop - start)):
            mixed[start + offset] += clips[name][offset] * gain
    peak = max(abs(value) for value in mixed)
    if peak > 32767:
        raise ValueError(f'Audio would clip (peak {peak}); review production playback gains')
    rms = math.sqrt(sum(value * value for value in mixed) / len(mixed))
    pcm = array('h', (round(value) for value in mixed))
    if sys.byteorder != 'little':
        pcm.byteswap()
    with wave.open(str(destination), 'wb') as output:
        output.setparams((1, 2, SAMPLE_RATE, 0, 'NONE', 'not compressed'))
        output.writeframes(pcm.tobytes())
    return {'peak': peak / 32768, 'rms': rms / 32768,
            'events': dict(Counter(voice['sound'] for voice in timeline['voices']))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reuse-capture', action='store_true', help='Reuse a capture already made from the current checkout')
    args = parser.parse_args()
    for tool in ('ffmpeg', 'ffprobe'):
        if not shutil.which(tool):
            raise SystemExit(f'{tool} is required')
    directory = ROOT / 'build/reports/gameplay-demo'
    if not args.reuse_capture:
        subprocess.run([str(ROOT / 'gradlew'), ':lwjgl3:gameplayDemo', '--console=plain'], cwd=ROOT, check=True)
    timeline = json.loads((directory / 'audio-events.json').read_text())
    if timeline['frames'] != 900 or timeline['fps'] != 30:
        raise SystemExit('Expected exactly 30 seconds at 30 fps')
    audio = directory / 'soundtrack.wav'
    summary = mix_audio(timeline, ROOT / 'assets', audio)
    if summary['peak'] == 0 or len(summary['events']) < 2:
        raise SystemExit('Recording has no meaningful game audio')
    movie = directory / 'gameplay.mp4'
    subprocess.run(['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error',
                    '-i', str(directory / 'silent.mp4'), '-i', str(audio),
                    '-map', '0:v:0', '-map', '1:a:0', '-c:v', 'copy', '-c:a', 'aac',
                    '-b:a', '128k', '-t', '30', '-movflags', '+faststart',
                    '-metadata', 'title=Feltri 8-Ball — 30-second desktop gameplay',
                    '-metadata', 'comment=https://github.com/ffeltrinelli/pool-android',
                    str(movie)], check=True)
    probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-count_frames',
                      '-show_streams', '-show_format', '-of', 'json', str(movie)], text=True))
    video = next(stream for stream in probe['streams'] if stream['codec_type'] == 'video')
    sound = next(stream for stream in probe['streams'] if stream['codec_type'] == 'audio')
    if video['codec_name'] != 'h264' or sound['codec_name'] != 'aac' or int(video['nb_read_frames']) != 900:
        raise SystemExit('Unexpected output encoding or frame count')
    if abs(float(probe['format']['duration']) - 30) > 0.05 or movie.stat().st_size >= 10_000_000:
        raise SystemExit('Movie must be 30 seconds and smaller than 10 MB')
    shutil.copyfile(directory / 'poster.png', ROOT / 'docs/images/gameplay-preview.png')
    summary.update({'duration': float(probe['format']['duration']), 'frames': 900,
                    'bytes': movie.stat().st_size, 'video': video['codec_name'], 'audio': sound['codec_name']})
    (directory / 'verification.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))
    print(f'Video: {movie}\nPreview: {ROOT / "docs/images/gameplay-preview.png"}')


if __name__ == '__main__':
    main()
