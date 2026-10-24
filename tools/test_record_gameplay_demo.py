"""Signal fixtures protect playback timing, gain, and early stops in the README soundtrack."""
from array import array
from pathlib import Path
import tempfile
import unittest
import wave

from record_gameplay_demo import SAMPLE_RATE, load_samples, mix_audio


class DemoAudioTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.assets = Path(self.temporary.name)
        with wave.open(str(self.assets / 'tone.wav'), 'wb') as source:
            source.setparams((1, 2, SAMPLE_RATE, 0, 'NONE', 'not compressed'))
            source.writeframes((array('h', [10000]) * SAMPLE_RATE).tobytes())
        self.output = self.assets / 'mixed.wav'

    def test_onsets_overlaps_and_stop_times_preserve_production_gains(self):
        timeline = {'fps': 10, 'frames': 10, 'voices': [
            {'sound': 'tone.wav', 'volume': 0.5, 'startFrame': 2, 'stopFrame': 6},
            {'sound': 'tone.wav', 'volume': 0.25, 'startFrame': 4, 'stopFrame': 8},
        ]}
        summary = mix_audio(timeline, self.assets, self.output)
        samples = load_samples(self.output)
        self.assertEqual(SAMPLE_RATE, len(samples))
        for seconds, expected in [(0.1, 0), (0.3, 5000), (0.5, 7500), (0.7, 2500), (0.9, 0)]:
            self.assertEqual(expected, samples[round(seconds * SAMPLE_RATE)])
        self.assertAlmostEqual(7500 / 32768, summary['peak'])

    def test_clipping_is_rejected_instead_of_silently_distorting_audio(self):
        voice = {'sound': 'tone.wav', 'volume': 1.0, 'startFrame': 0, 'stopFrame': 10}
        with self.assertRaisesRegex(ValueError, 'clip'):
            mix_audio({'fps': 10, 'frames': 10, 'voices': [voice] * 4}, self.assets, self.output)
