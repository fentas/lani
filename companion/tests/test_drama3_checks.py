"""Tests for companion/voice-drama3/checks.py, the Drama 3 worker's quality guard.

The text check is pure Python and always runs. The signal checks (the gender by pitch, noise,
silence) need numpy and ffmpeg: without them they are skipped; the worker's venv has numpy:

    /path/to/lani-drama3/venv/bin/python -m unittest companion/tests/test_drama3_checks.py

Fixtures (companion/tests/fixtures/drama3/, cut to 3 s from the spike's Drama 3 takes of
2026-10-19; Drama 3's outputs are ours under its licence): clean-male.mp3 ("Na svidenje, se
vidimo jutri!", a young man, no hiss), noisy-male.mp3 ("Kamen na kamen. Temelji so trdni!", a
hiss bed in the pause) and male-for-a-woman.mp3 ("Grem delat, pridem zvečer.", asked for as an
older woman, came out as a man at 105 Hz, with hiss too). Synthetic tones and noise for the rest.
"""
from __future__ import annotations

import json
import shutil
import subprocess
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
FIXTURES = Path(__file__).resolve().parent / "fixtures" / "drama3"
sys.path.insert(0, str(ROOT / "voice-drama3"))
import checks  # noqa: E402

try:
    import numpy as np
except ImportError:  # the system Python: the text checks only
    np = None
FFMPEG = shutil.which("ffmpeg")
SR = 44100


def load(name: str):
    raw = subprocess.run([FFMPEG, "-v", "error", "-i", str(FIXTURES / name), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32)


def voice(f0: float, secs: float = 2.0, gaps: tuple = ((0.9, 1.1),), noise_db: float | None = None, seed: int = 1):
    """A voice-like signal: a harmonic tone at [f0] with a little vibrato and a syllable rhythm, silent
    in [gaps]; with [noise_db], white noise at that level (dBFS) under all of it."""
    rng = np.random.default_rng(seed)
    t = np.arange(int(secs * SR)) / SR
    phase = 2 * np.pi * np.cumsum(f0 * (1 + 0.02 * np.sin(2 * np.pi * 5 * t))) / SR
    x = sum((0.5 / k) * np.sin(k * phase) for k in range(1, 12))
    x *= 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 3 * t))
    for a, b in gaps:
        x[int(a * SR):int(b * SR)] = 0.0
    x = 0.3 * x / np.abs(x).max()
    if noise_db is not None:
        x = x + 10 ** (noise_db / 20) * rng.standard_normal(len(x))
    return x.astype(np.float32)


class TextMatch(unittest.TestCase):
    def test_exact_and_punctuation(self):
        r = checks.text_match("Oprostite, ali ste vi Anton?", "Oprostite ali ste vi Anton.")
        self.assertTrue(r["ok"])
        self.assertEqual((r["hard"], r["soft"], r["score"]), ([], [], 1.0))

    def test_dual_changed_to_plural_fails(self):
        # the spike's voice A: "Mi dva smo" for "Midva sva": the split is fine, sva → smo is not
        r = checks.text_match("Midva sva na pašniku, onidve sta v kuhinji.", "Mi dva smo na pašniku, oni dve sta v kuhinji.")
        self.assertFalse(r["ok"])
        self.assertEqual(r["hard"], ["sva → smo"])

    def test_known_slovene_misses_are_warnings(self):
        r = checks.text_match("Dober dan! Kako si danes?", "Dobar dan, kako si danes?")
        self.assertTrue(r["ok"])
        self.assertEqual(r["warnings"], ["schwa: dober → dobar"])
        r = checks.text_match("Dober dan!", "Dobr dan.")
        self.assertEqual(r["warnings"], ["schwa: dober → dobr"])
        r = checks.text_match("Kamen na kamen. Temelji so trdni!", "Kamen na kamen. Temeli so trdni.")
        self.assertTrue(r["ok"])
        self.assertEqual(r["warnings"], ["lj lost: temelji → temeli"])
        r = checks.text_match("Midva sva na pašniku, onidve sta v kuhinji.", "Mi dva sva na pašniku, oni dve sta v kuhini.")
        self.assertTrue(r["ok"])
        self.assertEqual(r["warnings"], ["nj lost: kuhinji → kuhini"])

    def test_near_misses_and_short_words_are_soft(self):
        # "kruh v peči" heard as "kruhu peči": a near miss and a lost one-letter word
        r = checks.text_match("Babica Micka peče kruh v peči.", "Babica Micka peče kruhu peči.")
        self.assertTrue(r["ok"])
        self.assertEqual(r["hard"], [])
        self.assertEqual(sorted(r["soft"]), ["kruh → kruhu", "missed: v"])

    def test_final_devoicing_is_a_near_miss(self):
        # "gozd" is said [gost]: Whisper writes "gost", which isn't an error of the take
        r = checks.text_match("Ko sem bil mlad, sem vsak dan hodil v gozd.", "Ko sem bil mlad, sem vsak dan hodil v gost.")
        self.assertTrue(r["ok"])
        self.assertEqual(r["soft"], ["gozd → gost"])
        self.assertEqual(checks.text_match("hodil v gozd", "hodil v ghost")["hard"], ["gozd → ghost"])

    def test_missing_and_added_words_are_hard(self):
        r = checks.text_match("Grem delat, pridem zvečer.", "Grem delat.")
        self.assertFalse(r["ok"])
        self.assertEqual(r["hard"], ["missed: pridem", "missed: zvečer"])
        r = checks.text_match("Na svidenje!", "Na svidenje, se vidimo jutri.")
        self.assertEqual(r["hard"], ["added: vidimo", "added: jutri"])

    def test_too_many_near_misses_fail(self):
        r = checks.text_match("Kruh mleko sir", "Kruha mleka sira")
        self.assertFalse(r["ok"])
        self.assertEqual(r["hard"], [])

    def test_directions_are_not_words(self):
        self.assertEqual(checks.words("[sighs] Grem delat. [laughs]"), ["grem", "delat"])


class Voices(unittest.TestCase):
    def test_every_cast_speaker_has_a_voice_of_its_gender(self):
        # the learner's own voices (a speaker with its "age": "🗣️ Moj glas") have none: Drama 3 remakes the villagers' clips
        cast = {k: v for k, v in json.loads((ROOT / "voice-cast.json").read_text())["speakers"].items() if "age" not in v}
        voices = {k: v for k, v in json.loads((ROOT / "voice-drama3" / "voices.json").read_text()).items() if not k.startswith("_")}
        self.assertEqual(set(voices), set(cast))
        for name, v in voices.items():
            self.assertEqual(v["gender"], cast[name]["gender"], name)
            self.assertTrue(v["description"].startswith("[") and "Slovenian" in v["description"], name)
            ref = v.get("ref")
            if ref:  # a reference made by Drama 3 itself: its codes, frames and transcript
                self.assertEqual(ref["text"], v["line"], name)
                self.assertIn(ref["chosen"], ("score", "jan"), name)
                self.assertEqual(len(ref["codes"]) * 3 // 4 - ref["codes"].count("=") , 10 * ref["frames"] * 2, name)
        self.assertEqual(voices["girl"].get("age"), "child")
        self.assertEqual(voices["boy"].get("age"), "child")


@unittest.skipIf(np is None or FFMPEG is None, "numpy or ffmpeg missing: run with the worker's venv")
class Signal(unittest.TestCase):
    def test_clean_fixture_passes(self):
        v = checks.judge(load("clean-male.mp3"), SR, "", {"gender": "male"})
        self.assertTrue(v.ok, v.reasons)
        self.assertLess(v.metrics["noise_floor"], -62)
        self.assertLess(v.metrics["f0"], 165)

    def test_noisy_fixture_fails_on_noise(self):
        v = checks.judge(load("noisy-male.mp3"), SR, "", {"gender": "male"})
        self.assertFalse(v.ok)
        self.assertTrue(any(r.startswith("noise:") for r in v.reasons), v.reasons)
        self.assertGreater(v.metrics["noise_floor"], -57)
        self.assertFalse(any(r.startswith("gender:") for r in v.reasons), v.reasons)

    def test_wrong_gender_fixture_fails_on_gender(self):
        v = checks.judge(load("male-for-a-woman.mp3"), SR, "", {"gender": "female"})
        self.assertFalse(v.ok)
        self.assertIn("gender: 105 Hz is not a female voice", v.reasons)
        # the same take asked for as a man passes the gender check
        m = checks.judge(load("male-for-a-woman.mp3"), SR, "", {"gender": "male"})
        self.assertFalse(any(r.startswith("gender:") for r in m.reasons), m.reasons)

    def test_pitch_of_a_tone(self):
        for f0 in (100.0, 140.0, 220.0, 280.0):
            p = checks.pitch(voice(f0), SR)
            self.assertAlmostEqual(p["f0"], f0, delta=f0 * 0.03)
            self.assertGreater(p["voiced"], 0.5)

    def test_gender_bands(self):
        def reasons(f0, spec):
            v = checks.Verdict()
            checks.check_gender(f0, spec, v)
            return v.ok, v.warnings
        self.assertEqual(reasons(120, {"gender": "male"}), (True, []))
        self.assertFalse(reasons(200, {"gender": "male"})[0])
        self.assertTrue(reasons(175, {"gender": "male"})[0] and reasons(175, {"gender": "male"})[1])
        self.assertEqual(reasons(210, {"gender": "female"}), (True, []))
        self.assertFalse(reasons(110, {"gender": "female"})[0])
        # a child, girl or boy: high whichever the gender; a woman's 210 Hz fails, a man's surely
        self.assertEqual(reasons(280, {"gender": "male", "age": "child"}), (True, []))
        self.assertFalse(reasons(190, {"gender": "female", "age": "child"})[0])
        self.assertFalse(reasons(120, {"gender": "male", "age": "child"})[0])
        # against the speaker's reference: 6 semitones off fails, 5 warns
        self.assertFalse(reasons(220 * 2 ** (6.5 / 12), {"gender": "female", "ref_f0": 220})[0])
        ok, warn = reasons(220 * 2 ** (5 / 12), {"gender": "female", "ref_f0": 220})
        self.assertTrue(ok and warn)

    def test_noise_bed_is_caught(self):
        clean = checks.noise(voice(140, noise_db=-90), SR)
        hiss = checks.noise(voice(140, noise_db=-45), SR)
        self.assertLess(clean["floor"], checks.LIMITS["noise_floor_warn"])
        self.assertGreater(hiss["floor"], checks.LIMITS["noise_floor_fail"])
        self.assertGreater(hiss["seconds"], 0.1)

    def test_silence_and_rate(self):
        loop = voice(140, secs=4.0, gaps=((1.0, 3.0),))
        v = checks.judge(loop, SR, "Dober dan, kako si?", {"gender": "male"})
        self.assertIn("silence: a pause of 2.0 s", v.reasons)
        v = checks.judge(voice(140, secs=6.0, gaps=()), SR, "Dober dan, kako si danes?", {"gender": "male"})
        self.assertTrue(any(r.startswith("length:") for r in v.reasons), v.reasons)
        v = checks.judge(voice(140, secs=2.0), SR, "Dober dan, kako si?", {"gender": "male"}, ended=False)
        self.assertIn("length: no end (the frame cap was reached: a loop)", v.reasons)

    def test_a_gap_filled_with_hiss_is_caught(self):
        # the smoke's take: 3.5 s between two sentences filled with a low hiss, 37 dB under the speech
        x = voice(140, secs=6.0, gaps=((1.5, 5.0),))
        rng = np.random.default_rng(7)
        x[int(1.5 * SR):int(5.0 * SR)] += (0.0025 * rng.standard_normal(int(3.5 * SR))).astype(np.float32)
        v = checks.judge(x, SR, "Dober dan, jaz sem babica Micka. Koliko stane kruh?", {"gender": "male"})
        self.assertTrue(any(r.startswith("silence: a pause of 3.5") for r in v.reasons), v.reasons)
        self.assertTrue(any(r.startswith("noise:") for r in v.reasons), v.reasons)

    def test_whisper_is_not_a_voice(self):
        rng = np.random.default_rng(3)
        whisper = (0.05 * rng.standard_normal(2 * SR) * np.abs(np.sin(2 * np.pi * 3 * np.arange(2 * SR) / SR))).astype(np.float32)
        v = checks.judge(whisper, SR, "", {"gender": "female"})
        self.assertTrue(any(r.startswith("voice:") for r in v.reasons), v.reasons)

    def test_verdict_is_json(self):
        v = checks.judge(load("clean-male.mp3"), SR, "Na svidenje, se vidimo jutri!", {"gender": "male", "ref_f0": 130.0},
                         heard="Na svidenje. Se vidimo jutri.")
        json.dumps(v.to_dict())
        self.assertTrue(v.ok, v.reasons)
        self.assertEqual(v.metrics["text_score"], 1.0)


if __name__ == "__main__":
    unittest.main()
