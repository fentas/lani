"""Tests for companion/stt-local/server.py, the Whisper worker: a model per language, loaded when first
needed, unloaded when idle or for room, and 503 "not available" without one. Whisper, torch and
ffmpeg are faked: no model is loaded and nothing is downloaded.

Run: python3 -m unittest discover companion/tests
"""
from __future__ import annotations

import atexit
import importlib.util
import json
import os
import shutil
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
_models_dir = tempfile.mkdtemp(prefix="lani-stt-test-")
atexit.register(shutil.rmtree, _models_dir, ignore_errors=True)
# The worker reads its settings at import: a models directory of our own, no GPU.
os.environ.update({"LANI_STT_MODELS": _models_dir, "LANI_STT_DEVICE": "cpu"})
for k in [k for k in os.environ if k.startswith("LANI_STT_MODEL_")]:
    del os.environ[k]

# A fake openai-whisper: its model table (URL basenames are the file names on disk) and languages.
_whisper = types.ModuleType("whisper")
_whisper._MODELS = {
    "large-v3-turbo": "https://example.invalid/aaa/large-v3-turbo.pt",
    "turbo": "https://example.invalid/aaa/large-v3-turbo.pt",
    "medium": "https://example.invalid/bbb/medium.pt",
}
_whisper._ALIGNMENT_HEADS = {"large-v3-turbo": b"", "medium": b"", "large-v3": b""}
_whisper.available_models = lambda: list(_whisper._MODELS)
_tokenizer = types.ModuleType("whisper.tokenizer")
_tokenizer.LANGUAGES = {"sl": "slovenian", "it": "italian", "de": "german", "en": "english"}
_whisper.tokenizer = _tokenizer
sys.modules["whisper"] = _whisper
sys.modules["whisper.tokenizer"] = _tokenizer

_path = Path(__file__).resolve().parents[1] / "stt-local" / "server.py"
_spec = importlib.util.spec_from_file_location("lani_stt_server", _path)
server = importlib.util.module_from_spec(_spec)
sys.modules["lani_stt_server"] = server
_spec.loader.exec_module(server)


class FakeEngine:
    """An Engine that loads in no time and "hears" which model and language it was asked in."""

    loads: list[str] = []
    fail: set[str] = set()

    def __init__(self, model: str, alignment: str = "", warm=("sl", None)) -> None:
        self.model_id = model
        self.name = server.model_name(model)
        self.warm = warm
        self.use_prompt = True
        self.ready = False
        self.error = None
        self.device = "cpu (fake)"
        self.load_seconds = 0.0
        self.last_used = time.monotonic()
        self.unloads = 0
        self.heard: list[tuple[str, str | None]] = []

    def load(self) -> None:
        FakeEngine.loads.append(self.name)
        if self.name in FakeEngine.fail:
            self.error = "RuntimeError: out of memory"
            return
        self.error = None
        self.ready = True
        self.load_seconds = 0.01
        self.last_used = time.monotonic()

    def unload(self) -> None:
        self.ready = False
        self.unloads += 1

    def transcribe(self, audio, language: str, prompt: str | None) -> dict:
        self.last_used = time.monotonic()
        self.heard.append((language, prompt))
        return {"text": f"{self.name}:{language}", "segments": [], "language": language, "duration": len(audio) / 16000, "model": self.name}


def touch(name: str, size: int = 1000) -> Path:
    p = Path(_models_dir) / name
    p.write_bytes(b"\0" * size)
    return p


def models(**kw) -> server.Models:
    kw.setdefault("engine", FakeEngine)
    return server.Models(**kw)


class ModelChoice(unittest.TestCase):
    def setUp(self) -> None:
        FakeEngine.loads = []
        FakeEngine.fail = set()
        for f in Path(_models_dir).iterdir():
            f.unlink()

    def test_slovene_gets_the_primary_model_and_the_others_the_general_one(self):
        touch("large-v3-turbo.pt")
        m = models(primary="large-v3-sl", general="large-v3-turbo")
        self.assertEqual(m.model_for("sl"), "large-v3-sl")
        self.assertEqual(m.model_for("it"), "large-v3-turbo")
        self.assertEqual(m.model_for("de"), "large-v3-turbo")
        self.assertIs(m.engine_for("it"), m.engine_for("en"))
        self.assertIs(m.engine_for("sl"), m.primary)

    def test_one_model_serves_every_language_when_the_general_one_is_the_primary(self):
        m = models(primary="large-v3-turbo", general="large-v3-turbo")
        self.assertIs(m.engine_for("it"), m.primary)
        self.assertEqual(len(m.engines), 1)
        self.assertEqual(m.languages_of("large-v3-turbo"), ["sl", "*"])

    def test_a_language_of_its_own_from_the_environment(self):
        m = server.Models.from_env({"LANI_STT_MODEL_IT": "medium", "LANI_STT_MODEL_GENERAL": "x", "LANI_STT_MODELS": "/d"})
        self.assertEqual(m.per_language, {"it": "medium"})
        self.assertEqual(m.model_for("it"), "medium")

    def test_a_model_not_on_disk_is_not_available_and_never_downloaded(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo")
        self.assertFalse(m.available("it"))
        with self.assertRaises(server.NotAvailable):
            m.engine_for("it")
        self.assertTrue(m.available("sl"))  # the primary is loaded at start, as before
        touch("large-v3-turbo.pt")
        self.assertTrue(m.available("it"))

    def test_model_files_by_whisper_name_alias_and_converted_fine_tune(self):
        self.assertIsNone(server.model_file("large-v3-turbo"))
        touch("large-v3-turbo.pt")
        self.assertEqual(server.model_file("turbo").name, "large-v3-turbo.pt")  # the alias's download
        touch("large-v3-sl.pt")
        self.assertEqual(server.model_file("large-v3-sl").name, "large-v3-sl.pt")
        self.assertIsNone(server.model_file("nowhere"))


class Memory(unittest.TestCase):
    def setUp(self) -> None:
        FakeEngine.loads = []
        FakeEngine.fail = set()
        for f in Path(_models_dir).iterdir():
            f.unlink()
        touch("large-v3-turbo.pt", 1_600)
        touch("large-v3-sl.pt", 3_100)

    def test_the_general_model_loads_when_first_needed(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo")
        e = m.engine_for("it")
        self.assertFalse(e.ready)
        m.load(e)
        self.assertTrue(e.ready)
        m.load(e)
        self.assertEqual(FakeEngine.loads, ["large-v3-turbo"])

    def test_two_models_stay_loaded_when_there_is_room(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo", resident=2)
        m.load(m.primary)
        m.load(m.engine_for("it"))
        self.assertTrue(m.primary.ready)
        self.assertEqual({e.name for e in m.loaded()}, {"large-v3-sl", "large-v3-turbo"})

    def test_only_one_resident_model_unloads_the_other_first(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo", resident=1)
        m.load(m.primary)
        it = m.engine_for("it")
        m.load(it)
        self.assertFalse(m.primary.ready)
        self.assertEqual(m.primary.unloads, 1)
        # Slovene again: the primary comes back, the general one goes
        m.load(m.primary)
        self.assertFalse(it.ready)
        self.assertEqual(FakeEngine.loads, ["large-v3-sl", "large-v3-turbo", "large-v3-sl"])

    def test_short_vram_unloads_the_least_recently_used_model_first(self):
        free = {"bytes": 4_000}
        m = models(primary="large-v3-sl", general="large-v3-turbo", per_language={"de": "medium"}, resident=3, reserve=1_000,
                   vram=lambda: (free["bytes"], 16_000))
        touch("medium.pt", 1_000)
        m.load(m.primary)
        m.load(m.engine_for("it"))
        m.primary.last_used = time.monotonic() - 100  # the primary is the least recently used
        free["bytes"] = 1_500  # medium needs 1.5 x 1000 plus the reserve: not enough
        orig = m.primary.unload

        def unload_frees():
            orig()
            free["bytes"] += 5_000

        m.primary.unload = unload_frees
        de = m.engine_for("de")
        m.load(de)
        self.assertTrue(de.ready)
        self.assertFalse(m.primary.ready)
        self.assertTrue(m.engine_for("it").ready)

    def test_a_model_that_fails_to_load_is_not_available(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo")
        FakeEngine.fail = {"large-v3-turbo"}
        with self.assertRaises(server.NotAvailable):
            m.load(m.engine_for("it"))
        self.assertFalse(m.available("it"))

    def test_idle_models_other_than_the_primary_are_unloaded(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo", idle=60)
        m.load(m.primary)
        it = m.engine_for("it")
        m.load(it)
        now = time.monotonic()
        self.assertEqual(m.sweep(now + 30), [])
        self.assertEqual(m.sweep(now + 3600), ["large-v3-turbo"])
        self.assertFalse(it.ready)
        self.assertTrue(m.primary.ready)
        self.assertEqual(models(idle=0).sweep(now + 10**6), [])

    def test_warming_loads_in_the_background_once(self):
        m = models(primary="large-v3-sl", general="large-v3-turbo")
        m.warm("it")
        for _ in range(100):
            if m.engine_for("it").ready:
                break
            time.sleep(0.01)
        self.assertTrue(m.engine_for("it").ready)
        m.warm("it")
        time.sleep(0.05)
        self.assertEqual(FakeEngine.loads.count("large-v3-turbo"), 1)
        missing = models(primary="large-v3-sl", general="large-v3-turbo", per_language={"de": "nowhere"})
        missing.warm("de")  # no model on disk: nothing happens
        time.sleep(0.05)
        self.assertNotIn("nowhere", FakeEngine.loads)


WAV = b"RIFF\x24\x00\x00\x00WAVEfmt " + b"\0" * 32


class Http(unittest.TestCase):
    """The worker's HTTP API over a real socket, with the fake engines and a fake decoder."""

    def setUp(self) -> None:
        FakeEngine.loads = []
        FakeEngine.fail = set()
        for f in Path(_models_dir).iterdir():
            f.unlink()
        self._decode = server.decode
        self._models = server.MODELS
        server.decode = lambda data, kind: [0.0] * 16000  # one second of silence
        server.MODELS = models(primary="large-v3-sl", general="large-v3-turbo")
        server.MODELS.primary.load()
        server.Handler.langs = {"sl", "it", "de", "en"}
        self.srv = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.srv.daemon_threads = True
        threading.Thread(target=self.srv.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.srv.server_address[1]}"

    def tearDown(self) -> None:
        self.srv.shutdown()
        self.srv.server_close()
        server.decode = self._decode
        server.MODELS = self._models

    def call(self, path: str, body: bytes | None = None) -> tuple[int, dict]:
        req = urllib.request.Request(self.base + path, data=body, method="POST" if body is not None else "GET")
        try:
            with urllib.request.urlopen(req, timeout=5) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            with e:
                return e.code, json.loads(e.read())

    def test_slovene_is_recognized_by_the_primary_model(self):
        code, out = self.call("/transcribe?language=sl&prompt=Dober%20dan", WAV)
        self.assertEqual(code, 200)
        self.assertEqual(out["model"], "large-v3-sl")
        self.assertEqual(out["text"], "large-v3-sl:sl")

    def test_italian_without_the_general_model_is_503_not_available(self):
        code, out = self.call("/transcribe?language=it", WAV)
        self.assertEqual(code, 503)
        self.assertEqual(out["error"], "not available")
        self.assertEqual(out["language"], "it")
        code, health = self.call("/health?language=it")
        self.assertEqual(code, 200)
        self.assertTrue(health["ok"])
        self.assertEqual(health["language"], {"code": "it", "model": "large-v3-turbo", "available": False, "loaded": False})

    def test_italian_loads_the_general_model_on_first_use_with_its_prompt(self):
        touch("large-v3-turbo.pt")
        code, out = self.call("/transcribe?language=it&prompt=Dov%27%C3%A8%20l%27acqua%3F", WAV)
        self.assertEqual(code, 200)
        self.assertEqual(out["model"], "large-v3-turbo")
        self.assertEqual(out["language"], "it")
        self.assertEqual(server.MODELS.engine_for("it").heard, [("it", "Dov'è l'acqua?")])
        code, health = self.call("/health?language=it")
        self.assertEqual(health["language"]["loaded"], True)
        self.assertEqual({m["model"]: m["loaded"] for m in health["models"]}, {"large-v3-sl": True, "large-v3-turbo": True})

    def test_the_status_of_a_language_warms_its_model(self):
        touch("large-v3-turbo.pt")
        code, health = self.call("/health?language=it")
        self.assertEqual((code, health["language"]["available"], health["language"]["loaded"]), (200, True, False))
        for _ in range(100):
            if server.MODELS.engine_for("it").ready:
                break
            time.sleep(0.01)
        self.assertTrue(server.MODELS.engine_for("it").ready)

    def test_health_without_a_language_is_as_before(self):
        code, health = self.call("/health")
        self.assertEqual(code, 200)
        self.assertEqual((health["ok"], health["model"], health["busy"]), (True, "large-v3-sl", False))
        self.assertNotIn("language", health)

    def test_an_unknown_language_is_400(self):
        code, out = self.call("/transcribe?language=xx", WAV)
        self.assertEqual(code, 400)

    def test_while_the_primary_loads_the_worker_says_so(self):
        server.MODELS = models(primary="large-v3-sl", general="large-v3-sl")
        code, health = self.call("/health")
        self.assertEqual((code, health.get("loading")), (503, True))
        code, out = self.call("/transcribe?language=it", WAV)
        self.assertEqual((code, out["error"]), (503, "loading"))


if __name__ == "__main__":
    unittest.main()
