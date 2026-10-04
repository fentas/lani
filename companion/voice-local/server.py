#!/usr/bin/env python3
"""Lani local voice worker: Gepard Slovenian TTS over HTTP.

Loads texdata/Gepard-Slovenian-TTS and the NVIDIA NanoCodec decoder once, then
serves:

    GET  /health  200 {"ok": true, "engine": "gepard", "device": "..."}
                  503 {"ok": false, "loading": true} while the model loads
    POST /synth   {"text": "...", "speaker": optional}  ->  200 audio/mpeg
                  400 bad input, 503 busy / not ready, 500 synthesis error

One synthesis at a time. A request that arrives while another one runs gets
503 at once, so the bridge can fall back to ElevenLabs.

Only the standard library plus the model libraries. All settings come from
environment variables (see README.md).
"""

from __future__ import annotations

import json
import logging
import os
import re
import subprocess
import sys
import threading
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

# MIOpen (ROCm conv library) runs a slow kernel search for every new input
# shape. FAST mode skips it: the codec decode drops from ~4 s to ~0.1-0.4 s.
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

HOST = os.environ.get("LANI_VOICE_HOST", "127.0.0.1")
PORT = int(os.environ.get("LANI_VOICE_PORT", "8795"))
DEVICE = os.environ.get("LANI_VOICE_DEVICE", "auto")  # auto | cuda | cpu
FFMPEG = os.environ.get("LANI_VOICE_FFMPEG", "/usr/bin/ffmpeg")
MP3_BITRATE = os.environ.get("LANI_VOICE_MP3_BITRATE", "64k")
MAX_CHARS = 400

MODEL_REPO = "texdata/Gepard-Slovenian-TTS"
MODEL_REV = os.environ.get("LANI_VOICE_MODEL_REV", "cd7666910de8bbbb7caf4417763571eb6f3988c5")
CODEC_REPO = "nvidia/nemo-nano-codec-22khz-1.89kbps-21.5fps"
CODEC_REV = os.environ.get("LANI_VOICE_CODEC_REV", "fc00890b604aa2de298d2641ffc6c5f6caf8c4d7")
CODEC_FILE = "nemo-nano-codec-22khz-1.89kbps-21.5fps.nemo"
SAMPLE_RATE = 22050
FRAME_RATE = 21.5

# Generation settings. The model card suggests temperature 0.4 / cfg 4.5; a
# Whisper-scored sweep (see README) found 0.3 / 3.5 slightly clearer and more
# stable with a reference voice.
GEN = dict(
    temperature=float(os.environ.get("LANI_VOICE_TEMPERATURE", "0.3")),
    top_k=0,
    cfg_scale=float(os.environ.get("LANI_VOICE_CFG", "3.5")),
    cfg_frames=int(os.environ.get("LANI_VOICE_CFG_FRAMES", "25")),
    repetition_penalty=1.45,
    repetition_window=32,
)

STATE = Path(os.environ.get("LANI_VOICE_STATE", share_dir("lani-voice"))).expanduser()

# Voices. The model has no speaker id. A voice is a voice-cloning reference plus
# a fixed sampling seed (see voices.json).
_VOICE_CFG = json.loads((HERE / "voices.json").read_text())
VOICES = {k: v for k, v in _VOICE_CFG.items() if not k.startswith("_")}
DEFAULT_VOICE = os.environ.get("LANI_VOICE_DEFAULT", _VOICE_CFG.get("_default", "ana"))
POOL = [p for p in _VOICE_CFG.get("_pool", []) if p in VOICES] or [DEFAULT_VOICE]
ALIASES = {a.lower(): name for name, v in VOICES.items() for a in [name, *v.get("aliases", [])]}


def pick_voice(speaker: str | None) -> str:
    """No speaker -> default. Known name or alias -> that voice. Any other name
    (e.g. a role-play character) -> a fixed voice from the pool, so the same
    character always gets the same voice."""
    if not speaker or not speaker.strip():
        return DEFAULT_VOICE
    key = speaker.strip().lower()
    if key in ALIASES:
        return ALIASES[key]
    return POOL[zlib.crc32(key.encode()) % len(POOL)]

log = logging.getLogger("lani-voice")


class Engine:
    def __init__(self) -> None:
        self.ready = False
        self.error: str | None = None
        self.device = "?"
        self.load_seconds = 0.0
        self.busy = threading.Lock()
        self.refs: dict = {}

    # ------------------------------------------------------------------ load
    def load(self) -> None:
        t0 = time.monotonic()
        try:
            import torch
            from huggingface_hub import snapshot_download, hf_hub_download
            from gepard_inference.runner import GepardRunner
            from gepard_inference.codec_wrapper import Player, UnfoldedCodecModel

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
                torch.set_num_threads(int(os.environ.get("LANI_VOICE_THREADS", "12")))
            self.torch = torch

            ckpt = snapshot_download(MODEL_REPO, revision=MODEL_REV,
                                     allow_patterns=["*.json", "*.safetensors", "*.jinja", "samples/*"])
            codec_path = hf_hub_download(CODEC_REPO, CODEC_FILE, revision=CODEC_REV)

            runner = GepardRunner.from_checkpoint(ckpt, device=dev)
            dtype = os.environ.get("LANI_VOICE_DTYPE", "")
            if dtype == "float32" or (not dtype and dev == "cpu"):
                runner.model.to(torch.float32)
            if os.environ.get("LANI_VOICE_FAST", "1") != "0":
                from fastgen import make_fast
                runner = make_fast(runner)
            codec = UnfoldedCodecModel.restore_from(codec_path, map_location=torch.device(dev))
            codec = codec.eval().to(dev)
            self.player = Player(codec=codec, fsq_levels=[8, 7, 6, 6],
                                 sample_rate=SAMPLE_RATE, device=dev, max_ref_seconds=15.0)
            self.runner = runner
            self.ckpt = Path(ckpt)

            for name, v in VOICES.items():
                if v.get("ref"):
                    ref = v["ref"]
                    p = (self.ckpt / ref) if ref.startswith("samples/") else (HERE / ref)
                    self.refs[name] = self.player.encode_reference(str(p))
                elif v.get("bootstrap"):
                    self.refs[name] = self._bootstrap_ref(name, v["bootstrap"])

            if dev == "cpu":
                self.device = f"cpu ({torch.get_num_threads()} threads)"
            else:
                props = torch.cuda.get_device_properties(torch.device(dev))
                self.device = f"rocm {props.gcnArchName} ({props.name})" if torch.version.hip \
                    else f"cuda ({props.name})"
            # Warm-up: first call compiles kernels on the GPU.
            self._synth_wav("Dober dan.", DEFAULT_VOICE)
            self.load_seconds = time.monotonic() - t0
            self.ready = True
            log.info("ready on %s in %.1fs", self.device, self.load_seconds)
        except Exception as e:  # noqa: BLE001
            log.exception("model load failed")
            self.error = f"{type(e).__name__}: {e}"

    def _bootstrap_ref(self, name: str, spec: dict):
        """Reference codes made by the model itself (no reference, fixed seed).
        Cached on disk: the codes, not the seed, define the voice, and the
        same seed gives a different voice on another device or torch build."""
        torch = self.torch
        cache = STATE / "voices" / f"{name}-s{spec['seed']}.pt"
        if cache.exists():
            codes = torch.load(cache)
        else:
            torch.manual_seed(int(spec["seed"]))
            codes = self.runner.generate("sl: " + spec["text"], max_frames=400, **GEN).cpu()
            cache.parent.mkdir(parents=True, exist_ok=True)
            torch.save(codes, cache)
            log.info("bootstrapped voice %s -> %s", name, cache)
        return codes.T.unsqueeze(0).long()  # (heads, T) -> [1, T, heads]

    # ------------------------------------------------------------ synthesis

    def _synth_wav(self, text: str, voice: str):
        import numpy as np

        torch = self.torch
        v = VOICES[voice]
        gen = dict(GEN)
        gen.update({k: v[k] for k in ("temperature", "cfg_scale", "cfg_frames") if k in v})
        ref = self.refs.get(voice)
        parts = []
        gap = np.zeros(int(0.18 * SAMPLE_RATE), dtype=np.float32)
        for i, chunk in enumerate(split_text(text)):
            max_frames = min(1400, int(60 + 2.6 * len(chunk)))
            for attempt in range(2):
                torch.manual_seed(int(v.get("seed", 0)) + i + 1000 * attempt)
                tokens = self.runner.generate("sl: " + chunk, ref_codes=ref,
                                              max_frames=max_frames, **gen)
                if tokens.shape[-1] < max_frames:
                    break
                log.warning("no stop after %d frames (runaway), retrying: %r", max_frames, chunk)
            wave = self._decode(tokens)
            if parts:
                parts.append(gap)
            parts.append(wave.astype(np.float32))
        return np.concatenate(parts)

    def _decode(self, tokens):
        """Codes -> waveform. Pads to a multiple of 32 frames so the conv
        decoder sees few distinct shapes (MIOpen caches per shape); the real
        length goes in codes_len, so the padding is masked and trimmed."""
        torch = self.torch
        n = tokens.shape[-1]
        padded = -(-n // 32) * 32
        if padded > n:
            tokens = torch.cat([tokens, tokens[:, -1:].expand(-1, padded - n)], dim=1)
        dev = self.player.device
        with torch.inference_mode():
            audio, alen = self.player.codec.decode_from_codes(
                tokens.unsqueeze(0).to(dev), torch.tensor([n], device=dev))
        return audio[0, : int(alen[0])].float().cpu().numpy()

    def synth_mp3(self, text: str, speaker: str | None) -> tuple[bytes, float, str]:
        voice = pick_voice(speaker)
        wave = self._synth_wav(text, voice)
        # Peak-normalise to -1 dBFS so clips match in loudness.
        peak = float(abs(wave).max()) or 1.0
        wave = (wave * (0.89 / peak)).clip(-1, 1)
        mp3 = to_mp3(wave.tobytes())
        return mp3, len(wave) / SAMPLE_RATE, voice


def split_text(text: str, limit: int = 180) -> list[str]:
    """Split into sentence-sized chunks. The model is best on short sentences."""
    sentences = re.split(r"(?<=[.!?…])\s+", text.strip())
    chunks: list[str] = []
    for s in sentences:
        if not s:
            continue
        if chunks and len(chunks[-1]) + 1 + len(s) <= 60:
            chunks[-1] += " " + s  # glue very short sentences together
        elif len(s) <= limit:
            chunks.append(s)
        else:  # over-long sentence: cut at commas, then at spaces
            cur = ""
            for piece in re.split(r"(?<=[,;:])\s+|\s+", s):
                if cur and len(cur) + 1 + len(piece) > limit:
                    chunks.append(cur)
                    cur = piece
                else:
                    cur = f"{cur} {piece}".strip()
            if cur:
                chunks.append(cur)
    return chunks or [text.strip()]


def to_mp3(pcm_f32: bytes) -> bytes:
    cmd = [FFMPEG, "-hide_banner", "-loglevel", "error", "-f", "f32le", "-ar", str(SAMPLE_RATE),
           "-ac", "1", "-i", "pipe:0", "-codec:a", "libmp3lame", "-b:a", MP3_BITRATE,
           "-ar", str(SAMPLE_RATE), "-f", "mp3", "pipe:1"]
    res = subprocess.run(cmd, input=pcm_f32, capture_output=True, check=False, timeout=60)
    if res.returncode != 0:
        raise RuntimeError("ffmpeg failed: " + res.stderr.decode(errors="replace")[-300:])
    return res.stdout


ENGINE = Engine()


class Handler(BaseHTTPRequestHandler):
    server_version = "lani-voice/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # route to logging, not stderr spam
        log.info("%s %s", self.address_string(), fmt % args)

    def _json(self, code: int, obj: dict) -> None:
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if code == 503:
            self.send_header("Retry-After", "5")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.split("?")[0] != "/health":
            return self._json(404, {"ok": False, "error": "not found"})
        if ENGINE.ready:
            return self._json(200, {"ok": True, "engine": "gepard", "device": ENGINE.device,
                                    "busy": ENGINE.busy.locked(),
                                    "voices": list(VOICES), "default_voice": DEFAULT_VOICE,
                                    "load_seconds": round(ENGINE.load_seconds, 1)})
        if ENGINE.error:
            return self._json(503, {"ok": False, "engine": "gepard", "error": ENGINE.error})
        return self._json(503, {"ok": False, "engine": "gepard", "loading": True})

    def do_POST(self):
        if self.path.split("?")[0] != "/synth":
            return self._json(404, {"ok": False, "error": "not found"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
            if n > 64 * 1024:
                return self._json(413, {"ok": False, "error": "body too large"})
            req = json.loads(self.rfile.read(n) or b"{}")
            text = req.get("text")
            speaker = req.get("speaker")
        except (ValueError, AttributeError):
            return self._json(400, {"ok": False, "error": "invalid JSON"})
        if not isinstance(text, str) or not text.strip():
            return self._json(400, {"ok": False, "error": "text is required"})
        text = " ".join(text.split())
        if text.lower().startswith("sl:"):
            text = text[3:].strip()
        if len(text) > MAX_CHARS:
            return self._json(400, {"ok": False, "error": f"text longer than {MAX_CHARS} characters"})
        if speaker is not None and not isinstance(speaker, str):
            return self._json(400, {"ok": False, "error": "speaker must be a string"})
        if not ENGINE.ready:
            return self._json(503, {"ok": False, "error": ENGINE.error or "loading"})
        if not ENGINE.busy.acquire(blocking=False):
            return self._json(503, {"ok": False, "error": "busy"})
        t0 = time.monotonic()
        try:
            mp3, dur, voice = ENGINE.synth_mp3(text, speaker)
        except Exception as e:  # noqa: BLE001
            log.exception("synthesis failed")
            return self._json(500, {"ok": False, "error": f"{type(e).__name__}: {e}"})
        finally:
            ENGINE.busy.release()
        wall = time.monotonic() - t0
        log.info("synth %d chars -> %.1fs audio in %.1fs (voice=%s)", len(text), dur, wall, voice)
        self.send_response(200)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("Content-Length", str(len(mp3)))
        self.send_header("X-Voice", voice)
        self.send_header("X-Audio-Seconds", f"{dur:.2f}")
        self.send_header("X-Synth-Seconds", f"{wall:.2f}")
        self.end_headers()
        self.wfile.write(mp3)


def main() -> None:
    logging.basicConfig(level=logging.INFO, stream=sys.stdout,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    for noisy in ("nemo_logger", "nemo", "transformers", "huggingface_hub"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
    srv = ThreadingHTTPServer((HOST, PORT), Handler)
    srv.daemon_threads = True
    threading.Thread(target=ENGINE.load, name="load", daemon=True).start()
    log.info("listening on http://%s:%d (loading model)", HOST, PORT)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
