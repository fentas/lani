"""Tests for companion/voice-drama3/server.py, the Drama 3 worker's HTTP contract (the Gepard worker's):
GET /health while it waits for the host's heavy lock and for memory, then ready; POST /synth's
answers (MP3 and its headers, 400, 422 when no take passed the checks, 503 busy beyond two in
flight). The engine is faked: no model, no GPU, no Whisper, no ffmpeg.

Run: python3 -m unittest discover -s companion/tests
"""
from __future__ import annotations

import fcntl
import json
import os
import sys
import tempfile
import threading
import time
import types
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "voice-drama3"))
import checks  # noqa: E402
import server  # noqa: E402

MP3 = b"ID3\x04\x00\x00fake-mp3"


class FakeVoice(types.SimpleNamespace):
    pass


class FakeVoices:
    default = "female"

    def __init__(self):
        self.voices = {
            "female": FakeVoice(name="female", ref_codes=[[0]], ref_id="female-1"),
            "grandma": FakeVoice(name="grandma", ref_codes=[[0]], ref_id="grandma-2"),
            "boy": FakeVoice(name="boy", ref_codes=None, ref_id=None),
        }

    def pick(self, speaker):
        if not speaker:
            return self.voices[self.default]
        return self.voices.get(speaker.strip().lower())


class FakeEngine:
    """Loads at once (or fails); a text with "FAIL" never passes; "SLOW" takes a while."""

    def __init__(self, fail_load=None):
        self.ready = False
        self.error = None
        self.device = "fake"
        self.load_seconds = 0.1
        self.voices = FakeVoices()
        self.asr = types.SimpleNamespace(available=True, close=lambda: None)
        self.fail_load = fail_load
        self.loads = 0
        self.calls = []

    def load(self):
        self.loads += 1
        if self.fail_load:
            raise RuntimeError(self.fail_load)
        self.ready = True

    def synth(self, text, voice):
        self.calls.append((text, voice.name))
        if "SLOW" in text:
            time.sleep(0.6)
        v = checks.Verdict(metrics={"f0": 212.4, "noise_floor": -66.1, "text_score": 1.0, "heard": text})
        if "FAIL" in text:
            v.fail("gender: 104 Hz is not a female voice")
            attempts = [{"seed": s, "ok": False, **v.to_dict()} for s in (1, 2, 3, 4)]
            return server.E.Result(False, voice.name, text, None, attempts, 1.0)
        v.warnings.append("lj lost: temelji → temeli")
        take = server.E.Take(audio=[0.0] * 44100, seed=4242, frames=[], finish="eos", gen_s=0.1, dec_s=0.1, verdict=v)
        return server.E.Result(True, voice.name, text, take, [{"seed": 4242, "ok": True, **v.to_dict()}], 1.0)


class WorkerTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.meminfo = Path(self.tmp.name) / "meminfo"
        self.memory(64)
        server.MEMINFO = str(self.meminfo)
        server.E.to_mp3 = lambda audio, *a, **k: MP3
        server.E.peak_normalise = lambda audio, *a, **k: audio
        self.engine = FakeEngine()
        server.STATE = server.State(self.engine)
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.httpd.daemon_threads = True
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        if server.STATE.lock_fd is not None:
            os.close(server.STATE.lock_fd)
        self.tmp.cleanup()

    def memory(self, gb):
        self.meminfo.write_text(f"MemTotal: 134217728 kB\nMemAvailable: {int(gb * 2**20)} kB\n")

    def get(self, path="/health"):
        try:
            with urllib.request.urlopen(self.base + path, timeout=5) as r:
                return r.status, json.loads(r.read()), dict(r.headers)
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read()), dict(e.headers)

    def synth(self, body):
        req = urllib.request.Request(self.base + "/synth", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return r.status, r.read(), dict(r.headers)
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read()), dict(e.headers)

    def start(self, lock="", start_gib=20.0):
        t = threading.Thread(target=server.STATE.start, kwargs={"lock": lock, "start_gib": start_gib, "poll": 0.05}, daemon=True)
        t.start()
        return t

    def wait_until(self, cond, secs=5.0):
        end = time.time() + secs
        while not cond() and time.time() < end:
            time.sleep(0.02)
        return cond()

    def test_waits_for_the_heavy_lock_then_holds_it(self):
        lock = Path(self.tmp.name) / "heavy.lock"
        fd = os.open(lock, os.O_RDWR | os.O_CREAT)
        fcntl.flock(fd, fcntl.LOCK_EX)  # another job holds the host's heavy lock
        t = self.start(lock=str(lock))
        self.assertTrue(self.wait_until(lambda: (server.STATE.waiting or "").startswith("the heavy lock")))
        code, body, headers = self.get()
        self.assertEqual(code, 503)
        self.assertTrue(body["loading"])
        self.assertIn("the heavy lock", body["waiting"])
        self.assertEqual(headers.get("Retry-After"), "10")
        self.assertEqual(self.engine.loads, 0)  # the model waits too
        fcntl.flock(fd, fcntl.LOCK_UN)
        os.close(fd)
        t.join(5)
        self.assertEqual(self.engine.loads, 1)
        # the worker holds the lock now: another job can't take it
        probe = os.open(lock, os.O_RDWR)
        with self.assertRaises(BlockingIOError):
            fcntl.flock(probe, fcntl.LOCK_EX | fcntl.LOCK_NB)
        os.close(probe)
        code, body, _ = self.get()
        self.assertEqual(code, 200)
        self.assertEqual((body["ok"], body["engine"], body["built_with"]), (True, "drama3", "Built with Fish Audio"))
        self.assertEqual(body["voices"], ["female", "grandma", "boy"])

    def test_waits_for_memory(self):
        self.memory(12)
        t = self.start(start_gib=20)
        self.assertTrue(self.wait_until(lambda: (server.STATE.waiting or "").startswith("memory")))
        code, body, _ = self.get()
        self.assertEqual((code, body["waiting"]), (503, "memory (12 GB available, 20 needed)"))
        self.assertEqual(self.engine.loads, 0)
        self.memory(40)
        t.join(5)
        self.assertEqual(self.get()[0], 200)

    def test_a_load_that_fails_says_why(self):
        self.engine.fail_load = "no ROCm/CUDA device: Drama 3 needs the GPU"
        self.start(start_gib=0).join(5)
        code, body, _ = self.get()
        self.assertEqual(code, 503)
        self.assertEqual(body["error"], "RuntimeError: no ROCm/CUDA device: Drama 3 needs the GPU")
        self.assertEqual(self.synth({"text": "Dober dan."})[0], 503)

    def test_synth(self):
        self.assertEqual(self.synth({"text": "Dober dan."})[0], 503)  # not loaded yet
        self.start(start_gib=0).join(5)
        code, body, h = self.synth({"text": "sl:  Kamen  na kamen. Temelji so trdni!", "speaker": "Grandma"})
        self.assertEqual(code, 200)
        self.assertEqual(body, MP3)
        self.assertEqual(h["Content-Type"], "audio/mpeg")
        self.assertEqual(h["X-Voice"], "grandma/grandma-2")
        self.assertEqual((h["X-Audio-Seconds"], h["X-Attempts"], h["X-Seed"]), ("1.00", "1", "4242"))
        self.assertGreaterEqual(float(h["X-Synth-Seconds"]), 0.0)
        self.assertEqual(json.loads(h["X-Checks"]), {"f0": 212.4, "noise_floor": -66.1, "text_score": 1.0, "warnings": ["lj lost: temelji → temeli"]})
        self.assertEqual(self.engine.calls[-1], ("Kamen na kamen. Temelji so trdni!", "grandma"))
        # no speaker: the default voice
        self.assertEqual(self.synth({"text": "Dober dan."})[2]["X-Voice"], "female/female-1")

    def test_synth_refusals(self):
        self.start(start_gib=0).join(5)
        self.assertEqual(self.synth({})[0], 400)
        self.assertEqual(self.synth({"text": "  "})[0], 400)
        self.assertEqual(self.synth({"text": "x" * 401})[0], 400)
        self.assertEqual(self.synth({"text": "Dober dan.", "speaker": 3})[0], 400)
        code, body, _ = self.synth({"text": "Dober dan.", "speaker": "soprano"})
        self.assertEqual((code, body["error"], body["voices"]), (400, "unknown speaker 'soprano'", ["female", "grandma", "boy"]))
        code, body, _ = self.synth({"text": "Dober dan.", "speaker": "boy"})
        self.assertEqual(code, 400)
        self.assertIn("no reference voice yet", body["error"])
        self.assertEqual(self.engine.calls, [])

    def test_a_line_no_take_passes_is_422_with_the_reasons(self):
        self.start(start_gib=0).join(5)
        code, body, _ = self.synth({"text": "Midva sva FAIL.", "speaker": "grandma"})
        self.assertEqual(code, 422)
        self.assertEqual((body["ok"], body["error"], body["voice"]), (False, "quality", "grandma"))
        self.assertEqual(len(body["attempts"]), 4)
        self.assertEqual(body["attempts"][0]["reasons"], ["gender: 104 Hz is not a female voice"])

    def test_two_in_flight_a_third_is_busy(self):
        self.start(start_gib=0).join(5)
        results = []
        ts = [threading.Thread(target=lambda: results.append(self.synth({"text": "SLOW line.", "speaker": "grandma"})[0])) for _ in range(2)]
        for t in ts:
            t.start()
        self.assertTrue(self.wait_until(lambda: server.STATE.running == 2))
        code, body, _ = self.synth({"text": "Dober dan.", "speaker": "grandma"})
        self.assertEqual((code, body["error"]), (503, "busy"))
        self.assertTrue(self.get()[1]["busy"])
        for t in ts:
            t.join(5)
        self.assertEqual(sorted(results), [200, 200])
        self.assertEqual(self.synth({"text": "Dober dan."})[0], 200)


class GuardedTakes(unittest.TestCase):
    """Engine.synth's loop: other seeds until a take passes; a line whose takes all failed on noise alone
    gets its best take through the hiss filter, checked again."""

    class Scripted(server.E.Engine):
        def __init__(self, verdicts):
            super().__init__(voices=types.SimpleNamespace(), asr=types.SimpleNamespace(available=False))
            self.script = list(verdicts)
            self.seeds = []

        def take(self, text, voice, seed, describe=False):
            self.seeds.append(seed)
            return server.E.Take(audio=["take", len(self.seeds)], seed=seed, frames=[], finish="eos", gen_s=0.0, dec_s=0.0)

        def judge(self, take, text, voice, hear=True, detect=False):
            v = checks.Verdict(metrics={"noise_floor": -50.0 - len(self.seeds)})
            for r in self.script.pop(0):
                v.fail(r)
            return v

    def setUp(self):
        self.voice = server.E.Voice(name="grandma", gender="female", seed=103)
        self.real = server.E.denoise
        server.E.denoise = lambda audio, *a, **k: ["denoised", *audio]

    def tearDown(self):
        server.E.denoise = self.real

    def test_retries_with_other_seeds_until_a_take_passes(self):
        eng = self.Scripted([["gender: 104 Hz is not a female voice"], ["text: sva → smo"], []])
        r = eng.synth("Midva sva doma.", self.voice, attempts=4)
        self.assertTrue(r.ok)
        self.assertEqual(len(r.attempts), 3)
        self.assertEqual(len(set(eng.seeds)), 3)
        self.assertEqual(r.take.seed, eng.seeds[-1])

    def test_never_passing_is_reported(self):
        eng = self.Scripted([["gender: x"], ["text: y"], ["noise: z", "text: y"], ["length: no end"]])
        r = eng.synth("Midva sva doma.", self.voice, attempts=4)
        self.assertFalse(r.ok)
        self.assertIsNone(r.take)
        self.assertEqual([a["reasons"] for a in r.attempts], [["gender: x"], ["text: y"], ["noise: z", "text: y"], ["length: no end"]])

    def test_noise_alone_gets_the_hiss_filter(self):
        # every take hisses; the best (the lowest floor: the 2nd, -52 dB) is filtered and passes then
        eng = self.Scripted([["noise: a hiss bed"], ["noise: a hiss bed"], []])
        r = eng.synth("Dober dan.", self.voice, attempts=2)
        self.assertTrue(r.ok)
        self.assertEqual(r.take.audio, ["denoised", "take", 2])
        self.assertEqual(r.take.verdict.metrics["denoised"], "hiss")
        self.assertEqual(r.attempts[-1]["denoised"], "hiss")
        # and when the filtered take fails too, the line is reported
        eng = self.Scripted([["noise: a hiss bed"], ["noise: a hiss bed"], ["text: sva → smo"]])
        self.assertFalse(eng.synth("Dober dan.", self.voice, attempts=2).ok)


class EngineHelpers(unittest.TestCase):
    def test_frame_cap_and_seeds(self):
        E = server.E
        self.assertEqual(E.frame_cap("[warmly] Dober dan!"), 60 + 4 * 8)
        v = E.Voice(name="grandma", gender="female", seed=103)
        self.assertEqual(E.seed_for(v, "Dober dan.", 0), E.seed_for(v, "Dober dan.", 0))
        self.assertNotEqual(E.seed_for(v, "Dober dan.", 0), E.seed_for(v, "Dober dan.", 1))
        self.assertEqual(E.letters("Čez cesto, 2 x!"), 9)


if __name__ == "__main__":
    unittest.main()
