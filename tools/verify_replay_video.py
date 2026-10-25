#!/usr/bin/env python3
"""Verify an Android replay export with ffprobe/ffmpeg; no third-party Python packages."""
import argparse
from array import array
import json
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('video', type=Path)
    parser.add_argument('--replay-seconds', type=float, help='Expected replay duration including its sound tail')
    args = parser.parse_args()
    probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-count_frames',
        '-show_streams', '-show_format', '-of', 'json', str(args.video)], text=True))
    video = next(s for s in probe['streams'] if s['codec_type'] == 'video')
    audio = next(s for s in probe['streams'] if s['codec_type'] == 'audio')
    assert (video['codec_name'], video['width'], video['height'], video['r_frame_rate']) == ('h264', 720, 1280, '30/1')
    assert audio['codec_name'] == 'aac' and audio['sample_rate'] == '44100'
    if args.replay_seconds is not None:
        import math
        expected = 60 + math.ceil(args.replay_seconds * 30)
        assert int(video['nb_read_frames']) == expected, (video['nb_read_frames'], expected)
        assert abs(float(probe['format']['duration']) - expected / 30) < 0.12
    assert 'freesman' in probe['format']['tags']['comment']
    frames = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(args.video),
        '-frames:v', '61', '-vf', 'scale=72:128', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'])
    size = 72 * 128 * 3
    assert len(frames) == size * 61
    first = frames[:size]
    for index in range(1, 60):
        difference = sum(abs(a - b) for a, b in zip(first, frames[index*size:(index+1)*size])) / size
        assert difference < 2, f'Splash changed before two seconds: frame {index}'
    difference = sum(abs(a - b) for a, b in zip(first, frames[60*size:])) / size
    assert difference > 3, 'Replay did not start after the 60-frame splash'
    pcm = array('f', subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(args.video),
        '-vn', '-ac', '1', '-ar', '44100', '-f', 'f32le', 'pipe:1']))
    if sys.byteorder != 'little':
        pcm.byteswap()
    assert max(abs(v) for v in pcm[:88200]) < 0.001, 'Splash intro is not silent'
    peak = max(abs(v) for v in pcm[88200:])
    assert 0.001 < peak < 1, 'Replay audio missing or clipping'
    report = {'frames': int(video['nb_read_frames']), 'duration': float(probe['format']['duration']),
              'video': 'h264', 'audio': 'aac', 'intro_frames': 60, 'audio_peak': peak,
              'credit': probe['format']['tags']['comment']}
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
