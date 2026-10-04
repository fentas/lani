#!/usr/bin/env python3
"""Lani local speech recognition: OpenAI Whisper over HTTP.

A model per language (README.md, "Languages"): the primary model (LANI_STT_MODEL,
large-v3-turbo by default, or the Slovene fine-tune) for Slovene, loaded at start; a general
multilingual model (LANI_STT_MODEL_GENERAL, large-v3-turbo) for every other language, or
one of a language's own (LANI_STT_MODEL_IT=...). Another model is loaded on first use,
only when its file is on disk (never downloaded here), and unloaded when idle or when the GPU
needs the room. When the general model is the primary one, one model serves every language.

    GET  /health      200 {"ok": true, "model": "...", "device": "...", "busy": false, "load_seconds": ...,
                           "models": [{"model", "languages", "present", "loaded", "idle_seconds"}]}
                      with ?language=it also "language": {"code", "model", "available", "loaded"},
                      and a model present but not loaded starts loading in the background
                      503 {"ok": false, "loading": true} while the primary model loads
    POST /transcribe  body: audio (m4a/ogg/webm/wav/mp3), at most 2 MB and 30 s
                      query: language=sl (default), prompt=<expected text> (optional)
                      200 {"text", "segments": [{"start", "end", "text", "avg_logprob",
                           "no_speech_prob", "words": [{"word", "start", "end", "prob"}]}],
                           "language", "duration", "took", "model"}
                      400 bad input, 413 too large or too long, 415 not audio,
                      503 busy / not ready / "not available" (no model for the language),
                      500 recognition error

The audio goes to a temporary file (MP4 from MediaRecorder has its index at the end, so
ffmpeg can't read it from a pipe) and is deleted before the answer is sent. Nothing is
stored or logged except lengths and timings.

Only the standard library plus torch and openai-whisper. All settings come from
environment variables (see README.md).
"""

from __future__ import annotations

import json
import logging
import os
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

# MIOpen (the ROCm conv library) searches kernels for every new input shape without this.
os.environ.setdefault("MIOPEN_FIND_MODE", "FAST")


def adopt_legacy_env(env=os.environ):
    """LANI_X from FLUENT_X, its name before the project was renamed from Fluent, when LANI_X isn't set."""
    for k, v in list(env.items()):
        if k.startswith("FLUENT_"):
            env.setdefault("LANI_" + k[len("FLUENT_"):], v)
    return env


def share_dir(name):
    """~/.local/share/<name>, or the same named fluent-… (installed before the rename) while only that one exists."""
    new, old = Path.home() / ".local/share" / name, Path.home() / ".local/share" / name.replace("lani", "fluent")
    return old if not new.exists() and old.exists() else new


adopt_legacy_env()

HOST = os.environ.get("LANI_STT_HOST", "127.0.0.1")
PORT = int(os.environ.get("LANI_STT_PORT", "8796"))
MODEL = os.environ.get("LANI_STT_MODEL", "large-v3-turbo")  # a Whisper name, or a .pt file (see convert_hf.py)
# The language MODEL is for (the Slovene fine-tune's), and the multilingual model for the others.
PRIMARY_LANGUAGE = os.environ.get("LANI_STT_PRIMARY_LANGUAGE", "sl")
GENERAL = os.environ.get("LANI_STT_MODEL_GENERAL", "large-v3-turbo")
# At most this many models loaded at once; a model other than the primary one is unloaded after
# IDLE_SECONDS unused (0: never); loading another keeps VRAM_RESERVE_GB free for the voice worker.
RESIDENT = int(os.environ.get("LANI_STT_RESIDENT", "2"))
IDLE_SECONDS = float(os.environ.get("LANI_STT_IDLE_SECONDS", "900"))
VRAM_RESERVE_GB = float(os.environ.get("LANI_STT_VRAM_RESERVE_GB", "1.0"))
# For a fine-tuned .pt: the base model whose word-timing heads it keeps (e.g. large-v3).
ALIGNMENT = os.environ.get("LANI_STT_ALIGNMENT", "")
DEVICE = os.environ.get("LANI_STT_DEVICE", "auto")  # auto | cuda | cpu
STATE = Path(os.environ.get("LANI_STT_STATE", share_dir("lani-stt"))).expanduser()
MODELS_DIR = Path(os.environ.get("LANI_STT_MODELS", STATE / "models")).expanduser()
FFMPEG = os.environ.get("LANI_STT_FFMPEG", "/usr/bin/ffmpeg")
BEAM = int(os.environ.get("LANI_STT_BEAM", "5"))
# Use the expected text as Whisper's initial prompt: auto = yes for OpenAI's models, no for a
# converted fine-tune (the Slovene one got worse with prompts, see README.md).
PROMPT = os.environ.get("LANI_STT_PROMPT", "auto")
FP16 = os.environ.get("LANI_STT_FP16", "1") != "0"
QUEUE_SECONDS = float(os.environ.get("LANI_STT_QUEUE_SECONDS", "15"))
MAX_BYTES = 2 * 1024 * 1024
MAX_SECONDS = 30.0
MAX_PROMPT = 300
SAMPLE_RATE = 16000

log = logging.getLogger("lani-stt")


def sniff(b: bytes) -> str | None:
    """Container from the first bytes: only real audio reaches ffmpeg."""
    if len(b) >= 12 and b[4:8] == b"ftyp":
        return "m4a"
    if b[:4] == b"OggS":
        return "ogg"
    if b[:4] == b"\x1a\x45\xdf\xa3":
        return "webm"
    if len(b) >= 12 and b[:4] == b"RIFF" and b[8:12] == b"WAVE":
        return "wav"
    if b[:3] == b"ID3" or (len(b) >= 2 and b[0] == 0xFF and (b[1] & 0xE0) == 0xE0):
        return "mp3"
    return None


def decode(data: bytes, kind: str):
    """Audio bytes -> 16 kHz mono float32 (numpy), at most MAX_SECONDS + a little."""
    import numpy as np

    tmpdir = os.environ.get("XDG_RUNTIME_DIR") or None  # tmpfs when there is one
    with tempfile.NamedTemporaryFile(suffix="." + kind, dir=tmpdir) as f:
        f.write(data)
        f.flush()
        cmd = [FFMPEG, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", f.name,
               "-t", str(MAX_SECONDS + 0.5), "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE),
               "-f", "f32le", "pipe:1"]
        res = subprocess.run(cmd, capture_output=True, check=False, timeout=30)
    if res.returncode != 0:
        raise ValueError("could not decode the audio: " + res.stderr.decode(errors="replace")[-200:].strip())
    return np.frombuffer(res.stdout, dtype=np.float32).copy()  # writable, for torch


def model_source(whisper, model: str) -> str:
    """A Whisper name as is; NAME or NAME.pt in the models directory (a converted fine-tune); else a path."""
    if model in whisper.available_models():
        return model
    for p in (MODELS_DIR / model, MODELS_DIR / f"{model}.pt"):
        if p.is_file():
            return str(p)
    return str(Path(model).expanduser())


def model_name(model: str = MODEL) -> str:
    return Path(model).stem if model.endswith(".pt") else model


def model_file(model: str) -> Path | None:
    """The model's weights on disk: a Whisper name's download in the models directory, NAME or
    NAME.pt there (a converted fine-tune), or a path; None when there is none. Nothing is downloaded."""
    url = None
    try:
        import whisper

        url = whisper._MODELS.get(model)
    except Exception:  # noqa: BLE001  (no whisper: a name is looked for as NAME.pt)
        pass
    candidates = [MODELS_DIR / os.path.basename(url)] if url else []
    candidates += [MODELS_DIR / model, MODELS_DIR / f"{model}.pt"]
    if "/" in model or model.endswith(".pt"):
        candidates.append(Path(model).expanduser())
    return next((p for p in candidates if p.is_file()), None)


def alignment_base(whisper, model: str, alignment: str) -> str | None:
    """Word-timing heads for a fine-tune: LANI_STT_ALIGNMENT, else the Whisper name its file
    name starts with (large-v3-sl -> large-v3)."""
    if alignment:
        return alignment if alignment in whisper._ALIGNMENT_HEADS else None
    name = model_name(model)
    if name in whisper._ALIGNMENT_HEADS:
        return None  # load_model sets them
    known = [k for k in whisper._ALIGNMENT_HEADS if name.startswith(k)]
    return max(known, key=len) if known else None


def rounded(x, n=3):
    return None if x is None else round(float(x), n)


class Engine:
    """One Whisper model: loaded, used and unloaded by Models. [warm]: the language and text of the
    warm-up call that compiles the GPU kernels."""

    def __init__(self, model: str = MODEL, alignment: str = ALIGNMENT, warm: tuple[str, str | None] = ("sl", "Dober dan.")) -> None:
        self.model_id = model
        self.name = model_name(model)
        self.alignment = alignment
        self.warm = warm
        self.use_prompt = PROMPT == "1" or (PROMPT == "auto" and not model.endswith(".pt") and "-sl" not in self.name)
        self.ready = False
        self.error: str | None = None
        self.device = "?"
        self.load_seconds = 0.0
        self.model = None
        self.last_used = time.monotonic()

    def unload(self) -> None:
        """Gives the model's memory back (VRAM too); load() brings it again."""
        self.model = None
        self.ready = False
        torch = getattr(self, "torch", None)
        import gc

        gc.collect()
        if torch is not None and torch.cuda.is_available():
            torch.cuda.empty_cache()
        log.info("%s unloaded", self.name)

    def load(self) -> None:
        t0 = time.monotonic()
        self.error = None
        try:
            import numpy as np
            import torch
            import whisper

            dev = DEVICE
            if dev == "auto":
                dev = "cuda" if torch.cuda.is_available() else "cpu"
            if dev == "cuda" and torch.cuda.device_count() > 1:
                # Prefer the discrete GPU (most memory) over an iGPU.
                best = max(range(torch.cuda.device_count()),
                           key=lambda i: torch.cuda.get_device_properties(i).total_memory)
                torch.cuda.set_device(best)
                dev = f"cuda:{best}"
            if dev == "cpu":
                torch.set_num_threads(int(os.environ.get("LANI_STT_THREADS", "8")))
            self.torch = torch
            self.whisper = whisper
            self.fp16 = FP16 and dev != "cpu"

            model = whisper.load_model(model_source(whisper, self.model_id), device=dev, download_root=str(MODELS_DIR))
            base = alignment_base(whisper, self.model_id, self.alignment)
            if base:
                # A fine-tune keeps its base model's cross-attention heads for word timing.
                model.set_alignment_heads(whisper._ALIGNMENT_HEADS[base])
            if self.fp16:
                # openai-whisper keeps fp32 weights and casts them on every call; storing them as
                # fp16 halves the VRAM. LayerNorm stays fp32 (it computes in fp32 anyway).
                for m in model.modules():
                    if isinstance(m, torch.nn.LayerNorm):
                        continue
                    for p in m.parameters(recurse=False):
                        p.data = p.data.half()
            self.model = model

            if dev == "cpu":
                self.device = f"cpu ({torch.get_num_threads()} threads)"
            else:
                props = torch.cuda.get_device_properties(torch.device(dev))
                self.device = f"rocm {props.gcnArchName} ({props.name})" if torch.version.hip \
                    else f"cuda ({props.name})"
            # Warm-up: the first call compiles GPU kernels. A tone, then silence.
            t = np.arange(int(2.0 * SAMPLE_RATE), dtype=np.float32) / SAMPLE_RATE
            self._transcribe(np.concatenate([0.1 * np.sin(2 * np.pi * 220 * t), np.zeros(8000, np.float32)]).astype(np.float32),
                             *self.warm)
            self.load_seconds = time.monotonic() - t0
            self.ready = True
            self.last_used = time.monotonic()
            vram = ""
            if dev != "cpu":
                vram = f", {torch.cuda.memory_reserved() / 2**30:.2f} GiB VRAM reserved"
            log.info("%s ready on %s in %.1fs%s", self.name, self.device, self.load_seconds, vram)
        except Exception as e:  # noqa: BLE001
            log.exception("model load failed")
            self.error = f"{type(e).__name__}: {e}"

    def _transcribe(self, audio, language: str, prompt: str | None) -> dict:
        with self.torch.inference_mode():
            return self.model.transcribe(
                audio,
                language=language,
                task="transcribe",
                initial_prompt=prompt or None,
                word_timestamps=True,
                condition_on_previous_text=False,
                temperature=(0.0, 0.2, 0.4),
                beam_size=BEAM,
                best_of=BEAM,
                fp16=self.fp16,
                verbose=None,
            )

    def transcribe(self, audio, language: str, prompt: str | None) -> dict:
        self.last_used = time.monotonic()
        r = self._transcribe(audio, language, prompt if self.use_prompt else None)
        segments = []
        for s in r.get("segments", []):
            words = [{"word": w["word"].strip(), "start": rounded(w["start"], 2), "end": rounded(w["end"], 2),
                      "prob": rounded(w.get("probability"))}
                     for w in s.get("words", []) if w["word"].strip()]
            segments.append({"start": rounded(s["start"], 2), "end": rounded(s["end"], 2), "text": s["text"].strip(),
                             "avg_logprob": rounded(s.get("avg_logprob")),
                             "no_speech_prob": rounded(s.get("no_speech_prob")), "words": words})
        text = " ".join(s["text"] for s in segments if s["text"]).strip()
        return {"text": text, "segments": segments, "language": r.get("language") or language,
                "duration": rounded(len(audio) / SAMPLE_RATE, 2), "model": self.name}


class NotAvailable(Exception):
    """No model for a language: its file isn't on disk, or it failed to load."""


class Models:
    """Which model recognizes which language, and which of them are loaded.

    [primary] serves [primary_language] (Slovene) and is loaded at start; [general] every other
    language, unless [per_language] names one of its own. A model is one Engine whatever the
    languages it serves (the primary and the general one are the same by default). Another model
    is loaded when first needed, if its file is present, and unloaded [idle] seconds after its
    last use (never the primary). Before a load, the least recently used models go until at most
    [resident] are loaded and, on a GPU, until the new one fits with [reserve] bytes to spare.
    Loading and recognizing hold [gpu]: one at a time.
    """

    def __init__(self, primary: str = MODEL, general: str = GENERAL, per_language: dict[str, str] | None = None,
                 primary_language: str = PRIMARY_LANGUAGE, resident: int = RESIDENT, idle: float = IDLE_SECONDS,
                 reserve: float = VRAM_RESERVE_GB * 2**30, engine=Engine, present=model_file, vram=None) -> None:
        self.primary_id = primary
        self.general_id = general
        self.per_language = dict(per_language or {})
        self.primary_language = primary_language
        self.resident = max(1, resident)
        self.idle = idle
        self.reserve = reserve
        self.make = engine
        self.present = present
        self.vram = vram or self._vram
        self.gpu = threading.Lock()
        self.engines: dict[str, Engine] = {}
        self.warming: set[str] = set()
        self.table = threading.Lock()
        self.primary = self._engine(primary, primary_language)

    @classmethod
    def from_env(cls, env=os.environ) -> "Models":
        """LANI_STT_MODEL_<LANG>=<model>: a language's own model (LANI_STT_MODEL_GENERAL excepted)."""
        own = {k[len("LANI_STT_MODEL_"):].lower(): v for k, v in adopt_legacy_env(dict(env)).items()
               if k.startswith("LANI_STT_MODEL_") and k != "LANI_STT_MODEL_GENERAL" and v.strip()}
        return cls(per_language=own)

    def model_for(self, language: str) -> str:
        if language in self.per_language:
            return self.per_language[language]
        return self.primary_id if language == self.primary_language else self.general_id

    def _engine(self, model: str, language: str) -> Engine:
        with self.table:
            e = self.engines.get(model)
            if e is None:
                e = self.engines[model] = self.make(model, ALIGNMENT if model == self.primary_id else "",
                                                    warm=(language, "Dober dan." if language == "sl" else None))
            return e

    def engine_for(self, language: str) -> Engine:
        """The language's engine, loaded or not; NotAvailable when its model isn't on disk."""
        model = self.model_for(language)
        if model not in self.engines and model != self.primary_id and not self.present(model):
            raise NotAvailable(model)
        return self._engine(model, language)

    def available(self, language: str) -> bool:
        """Whether the language can be recognized: its model is loaded, or on disk and hasn't failed to load."""
        model = self.model_for(language)
        e = self.engines.get(model)
        if e is not None and e.ready:
            return True
        if e is not None and e.error:
            return False
        return model == self.primary_id or self.present(model) is not None

    def languages_of(self, model: str) -> list[str]:
        own = sorted(k for k, v in self.per_language.items() if v == model)
        out = ([self.primary_language] if model == self.primary_id else []) + own
        return out + (["*"] if model == self.general_id else [])

    # --- loading, room, unloading -----------------------------------------------------------------

    @staticmethod
    def _vram() -> tuple[int, int] | None:
        """(free, total) bytes of the GPU in use, or None on the CPU."""
        try:
            import torch

            if torch.cuda.is_available() and DEVICE != "cpu":
                return torch.cuda.mem_get_info()
        except Exception:  # noqa: BLE001
            pass
        return None

    def need(self, e: Engine) -> int:
        """What loading [e] takes on the GPU: about 1.5x its fp16 weights (turbo: 1.6 GB file, 2.4 GiB)."""
        f = self.present(e.model_id)
        return int(f.stat().st_size * 1.5) if f else 3 * 2**30

    def loaded(self) -> list[Engine]:
        return sorted((e for e in self.engines.values() if e.ready), key=lambda e: e.last_used)

    def make_room(self, e: Engine) -> None:
        """Unloads the least recently used other models: to [resident] - 1, then until [e] fits."""
        others = [o for o in self.loaded() if o is not e]
        while others and len(others) >= self.resident:
            others.pop(0).unload()
        v = self.vram()
        while others and v is not None and v[0] - self.need(e) < self.reserve:
            others.pop(0).unload()
            v = self.vram()

    def load(self, e: Engine) -> None:
        """Loads [e] if it isn't; call holding [gpu]. NotAvailable when it fails."""
        if e.ready:
            return
        self.make_room(e)
        e.load()
        if not e.ready:
            raise NotAvailable(e.error or e.model_id)

    def warm(self, language: str) -> None:
        """Loads the language's model in the background, if it is present and not loaded (a status check
        before speaking, so the first recognition doesn't wait for the load)."""
        try:
            e = self.engine_for(language)
        except NotAvailable:
            return
        with self.table:
            if e.ready or e.model_id in self.warming:
                return
            self.warming.add(e.model_id)

        def run() -> None:
            try:
                with self.gpu:
                    self.load(e)
            except NotAvailable:
                pass
            finally:
                self.warming.discard(e.model_id)

        threading.Thread(target=run, name=f"warm-{e.name}", daemon=True).start()

    def sweep(self, now: float | None = None) -> list[str]:
        """Unloads the models other than the primary one unused for [idle] seconds; their names."""
        if self.idle <= 0:
            return []
        now = time.monotonic() if now is None else now
        out = []
        for e in self.loaded():
            if e is self.primary or now - e.last_used < self.idle:
                continue
            if self.gpu.acquire(blocking=False):
                try:
                    e.unload()
                    out.append(e.name)
                finally:
                    self.gpu.release()
        return out

    def status(self, now: float | None = None) -> list[dict]:
        now = time.monotonic() if now is None else now
        return [{"model": e.name, "languages": self.languages_of(e.model_id), "present": e is self.primary or self.present(e.model_id) is not None,
                 "loaded": e.ready, "idle_seconds": round(now - e.last_used)} for e in self.engines.values()]


MODELS = Models.from_env()
ENGINE = MODELS.primary  # the primary model's engine (bench.py and older callers)


def languages() -> set[str]:
    try:
        from whisper.tokenizer import LANGUAGES
        return set(LANGUAGES)
    except Exception:  # noqa: BLE001
        return {"sl", "en", "de", "it"}


class Handler(BaseHTTPRequestHandler):
    server_version = "lani-stt/1"
    protocol_version = "HTTP/1.1"
    langs: set[str] = set()

    def log_message(self, fmt, *args):
        log.info("%s %s", self.address_string(), fmt % args)

    def log_request(self, code="-", size="-"):
        # The path without the query: the prompt (expected text) stays out of the journal.
        log.info("%s %s %s", self.command, urlsplit(self.path).path, code)

    def _json(self, code: int, obj: dict) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        if code == 503:
            self.send_header("Retry-After", "5")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path != "/health":
            return self._json(404, {"ok": False, "error": "not found"})
        m = MODELS
        p = m.primary
        base = {"engine": "whisper", "model": p.name}
        if p.error and not p.ready:
            return self._json(503, {"ok": False, **base, "error": p.error})
        if not p.ready and p.load_seconds == 0:
            return self._json(503, {"ok": False, **base, "loading": True})
        out = {"ok": True, **base, "device": p.device, "busy": m.gpu.locked(), "prompt": p.use_prompt,
               "load_seconds": round(p.load_seconds, 1), "models": m.status()}
        language = (parse_qs(url.query).get("language") or [""])[0].strip().lower()
        if language:
            model = m.model_for(language)
            e = m.engines.get(model)
            known = language in (self.langs or languages())
            out["language"] = {"code": language, "model": model_name(model), "available": known and m.available(language),
                               "loaded": bool(e and e.ready)}
            if known:
                m.warm(language)
        return self._json(200, out)

    def do_POST(self):
        url = urlsplit(self.path)
        if url.path != "/transcribe":
            return self._json(404, {"ok": False, "error": "not found"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            return self._json(400, {"ok": False, "error": "bad Content-Length"})
        if n > MAX_BYTES:
            self.close_connection = True
            return self._json(413, {"ok": False, "error": f"audio larger than {MAX_BYTES} bytes"})
        if n <= 0:
            return self._json(400, {"ok": False, "error": "no audio"})
        data = self.rfile.read(n)
        q = parse_qs(url.query)
        language = (q.get("language") or ["sl"])[0].strip().lower() or "sl"
        prompt = " ".join((q.get("prompt") or [""])[0].split())[:MAX_PROMPT] or None
        if language not in (self.langs or languages()):
            return self._json(400, {"ok": False, "error": f"unknown language {language!r}"})
        kind = sniff(data)
        if not kind:
            return self._json(415, {"ok": False, "error": "not an audio file (m4a, ogg, webm, wav or mp3)"})
        m = MODELS
        try:
            engine = m.engine_for(language)
        except NotAvailable as e:
            return self._json(503, {"ok": False, "error": "not available", "language": language, "model": model_name(str(e))})
        if engine is m.primary and not engine.ready and engine.load_seconds == 0:
            return self._json(503, {"ok": False, "error": engine.error or "loading"})
        try:
            audio = decode(data, kind)
        except (ValueError, subprocess.TimeoutExpired) as e:
            return self._json(400, {"ok": False, "error": str(e)})
        finally:
            del data
        seconds = len(audio) / SAMPLE_RATE
        if seconds > MAX_SECONDS:
            return self._json(413, {"ok": False, "error": f"audio longer than {MAX_SECONDS:.0f} s"})
        if seconds < 0.1:
            return self._json(400, {"ok": False, "error": "audio too short"})
        # One recognition (or model load) at a time on the GPU; a short wait is fine (each takes
        # well under a second). A model not loaded yet loads now (~10 s, within the bridge's 30 s).
        if not m.gpu.acquire(timeout=QUEUE_SECONDS):
            return self._json(503, {"ok": False, "error": "busy"})
        t0 = time.monotonic()
        try:
            m.load(engine)
            out = engine.transcribe(audio, language, prompt)
        except NotAvailable:
            return self._json(503, {"ok": False, "error": "not available", "language": language, "model": engine.name})
        except Exception as e:  # noqa: BLE001
            log.exception("transcription failed")
            return self._json(500, {"ok": False, "error": f"{type(e).__name__}: {e}"})
        finally:
            m.gpu.release()
        out["took"] = round(time.monotonic() - t0, 3)
        log.info("transcribed %.1fs of %s in %s with %s (%d bytes, prompt %s) in %.2fs", seconds, kind, language,
                 engine.name, n, "yes" if prompt else "no", out["took"])
        return self._json(200, out)


def sweeper(models: Models, every: float = 60.0) -> None:
    """Unloads idle models, once a minute."""
    while True:
        time.sleep(every)
        for name in models.sweep():
            log.info("%s idle for %.0f s: unloaded", name, models.idle)


def main() -> None:
    logging.basicConfig(level=logging.INFO, stream=sys.stdout,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    Handler.langs = languages()
    srv = ThreadingHTTPServer((HOST, PORT), Handler)
    srv.daemon_threads = True

    def load_primary() -> None:
        with MODELS.gpu:
            MODELS.primary.load()

    threading.Thread(target=load_primary, name="load", daemon=True).start()
    threading.Thread(target=sweeper, args=(MODELS,), name="sweep", daemon=True).start()
    others = {lang: model_name(m) for lang, m in MODELS.per_language.items()}
    log.info("listening on http://%s:%d (loading %s for %s; other languages: %s%s)", HOST, PORT, MODELS.primary.name,
             MODELS.primary_language, model_name(MODELS.general_id), f", {others}" if others else "")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
