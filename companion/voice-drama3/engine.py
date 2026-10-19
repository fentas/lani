"""The Drama 3 engine: the model, the character voices, and a take that passes the quality guard.

A voice (voices.json) is a reference take made by Drama 3 itself from a description of the
character ("[a kind, warm grandmother in her seventies, speaking Slovenian]"), kept as its codes
with its transcript: every line of the speaker is generated after that reference, so the
speaker keeps one voice. No real person's voice is used.

Every take goes through checks.judge (the gender by pitch, noise, length and silence, then the
text by Whisper) and is made again with another seed when it fails, up to LANI_DRAMA3_ATTEMPTS
times; a line that never passes is reported, never returned as audio.
"""

from __future__ import annotations

import base64
import json
import logging
import os
import re
import subprocess
import tempfile
import threading
import time
import zlib
from dataclasses import dataclass, field
from pathlib import Path

import checks

HERE = Path(__file__).resolve().parent
SAMPLE_RATE = 44100
HOP = 2048  # samples per frame: 21.5 frames a second
NUM_CODEBOOKS = 10

log = logging.getLogger("lani-drama3")


def env_path(name: str, default: str | Path) -> Path:
    return Path(os.environ.get(name) or default).expanduser()


BASE = env_path("LANI_DRAMA3_HOME", Path.home() / ".local/share/lani-drama3")
WEIGHTS = env_path("LANI_DRAMA3_WEIGHTS", "/mnt/scratch/fish-drama3/weights/drama-3")
WHISPER = env_path("LANI_DRAMA3_WHISPER", "/mnt/scratch/fish-drama3/weights/faster-whisper-large-v3")
ASR_PYTHON = env_path("LANI_DRAMA3_ASR_PYTHON", BASE / "venv-asr/bin/python")
FFMPEG = os.environ.get("LANI_DRAMA3_FFMPEG", "/usr/bin/ffmpeg")
MP3_BITRATE = os.environ.get("LANI_DRAMA3_MP3_BITRATE", "96k")
ATTEMPTS = int(os.environ.get("LANI_DRAMA3_ATTEMPTS", "4"))
# A line whose takes all failed on noise alone: its best take through the bridge's "hiss" filter (the denoise of the
# voices that hiss, companion/bridge/src/voice.ts DENOISE), checked again, Whisper and all; "off": not done.
DENOISE = os.environ.get("LANI_DRAMA3_DENOISE", "hiss")
HISS = ("highpass=f=60,apad=pad_dur=0.025,afftdn=nr=12:nf=-40,atrim=start=0.025,asetpts=PTS-STARTPTS,"
        "agate=threshold=0.0141:ratio=3:attack=5:release=120")
# the process's share of the card: Gepard and STT hold ~7 GB next to it
VRAM_CAP_GIB = float(os.environ.get("LANI_DRAMA3_VRAM_GIB", "7.6"))
MIN_AVAILABLE_GIB = float(os.environ.get("LANI_DRAMA3_MIN_AVAILABLE_GIB", "4"))


# --- voices --------------------------------------------------------------------------------------

def codes_to_b64(codes) -> str:
    """[10, frames] codes as base64 of little-endian uint16 (codebooks of 4,096)."""
    import numpy as np
    return base64.b64encode(np.asarray(codes, dtype="<u2").tobytes()).decode()


def codes_from_b64(s: str, frames: int):
    import numpy as np
    a = np.frombuffer(base64.b64decode(s), dtype="<u2").astype(np.int64)
    return a.reshape(NUM_CODEBOOKS, frames)


@dataclass
class Voice:
    name: str
    gender: str
    age: str = "adult"
    description: str = ""
    seed: int = 0
    ref_text: str | None = None
    ref_codes: object = None  # numpy [10, frames] or None
    ref_f0: float | None = None
    ref_id: str | None = None
    settings: dict = field(default_factory=dict)
    aliases: list = field(default_factory=list)

    @property
    def spec(self) -> dict:
        return {"gender": self.gender, "age": self.age, "ref_f0": self.ref_f0}


class Voices:
    """voices.json: the speakers of companion/voice-cast.json, each with its reference."""

    def __init__(self, path: Path = HERE / "voices.json") -> None:
        self.path = path
        raw = json.loads(path.read_text())
        self.default = raw.get("_default", "female")
        self.settings = raw.get("_settings", {})
        self.voices: dict[str, Voice] = {}
        for name, v in raw.items():
            if name.startswith("_"):
                continue
            ref = v.get("ref") or {}
            self.voices[name] = Voice(
                name=name, gender=v["gender"], age=v.get("age", "adult"), description=v.get("description", ""),
                seed=int(v.get("seed", 0)), ref_text=ref.get("text"),
                ref_codes=codes_from_b64(ref["codes"], int(ref["frames"])) if ref.get("codes") else None,
                ref_f0=ref.get("f0"), ref_id=ref.get("id"), settings=v.get("settings", {}), aliases=v.get("aliases", []))
        self.alias = {a.lower(): n for n, v in self.voices.items() for a in [n, *v.aliases]}

    def pick(self, speaker: str | None) -> Voice | None:
        if not speaker or not speaker.strip():
            return self.voices.get(self.default)
        return self.voices.get(self.alias.get(speaker.strip().lower(), ""))

    def gen(self, voice: Voice) -> dict:
        """Sampling settings: Fish's defaults, then voices.json's, then the voice's own."""
        g = {"temperature": 0.7, "top_k": 30, "top_p": 0.7}
        g.update(self.settings)
        g.update(voice.settings)
        for k, cast in (("temperature", float), ("top_p", float), ("top_k", int)):
            e = os.environ.get(f"LANI_DRAMA3_{k.upper()}")
            if e:
                g[k] = cast(e)
        return g


# --- Whisper (CPU, its own venv and process) ----------------------------------------------------

class Asr:
    """asr.py in venv-asr, started once and kept; JSON lines over a pipe."""

    def __init__(self, python: Path = ASR_PYTHON, model: Path = WHISPER, threads: int | None = None) -> None:
        self.python, self.model = python, model
        self.threads = threads or int(os.environ.get("LANI_DRAMA3_ASR_THREADS", "8"))
        self.proc: subprocess.Popen | None = None
        self.lock = threading.Lock()
        self.n = 0

    @property
    def available(self) -> bool:
        return self.python.exists() and (self.model / "model.bin").exists()

    def _start(self) -> None:
        self.proc = subprocess.Popen(
            [str(self.python), str(HERE / "asr.py"), "--model", str(self.model), "--threads", str(self.threads)],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, encoding="utf-8", bufsize=1)
        line = self.proc.stdout.readline()
        if not line or not json.loads(line).get("ready"):
            raise RuntimeError("the Whisper helper did not start")

    def hear(self, audio, sr: int, language: str = "sl", detect: bool = False) -> dict:
        import numpy as np
        import soundfile as sf
        from scipy.signal import resample_poly
        with self.lock:
            if self.proc is None or self.proc.poll() is not None:
                self._start()
            pcm = resample_poly(np.asarray(audio, dtype=np.float32), 160, 441) if sr == 44100 else audio
            with tempfile.NamedTemporaryFile(suffix=".wav", prefix="lani-drama3-") as f:
                sf.write(f.name, pcm, 16000 if sr == 44100 else sr, subtype="PCM_16")
                self.n += 1
                self.proc.stdin.write(json.dumps({"id": self.n, "wav": f.name, "language": language, "detect": detect}) + "\n")
                self.proc.stdin.flush()
                line = self.proc.stdout.readline()
            if not line:
                raise RuntimeError("the Whisper helper stopped")
            out = json.loads(line)
            if out.get("error"):
                raise RuntimeError(out["error"])
            return out

    def close(self) -> None:
        if self.proc and self.proc.poll() is None:
            self.proc.stdin.close()
            try:
                self.proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.proc.kill()


# --- the engine ----------------------------------------------------------------------------------

def letters(text: str) -> int:
    return len(re.sub(r"[\W\d_]+", "", re.sub(r"\[[^\]]*\]", "", text)))


def frame_cap(text: str) -> int:
    """Generous: ~2.2 frames a letter is normal speech; 60 + 4 a letter means stuck (a loop)."""
    return 60 + 4 * max(letters(text), 1)


def seed_for(voice: Voice, text: str, attempt: int) -> int:
    """The same text in the same voice gets the same seed: a take can be made again."""
    return (voice.seed * 7919 + zlib.crc32(text.encode()) % 100_000 + 1_000_003 * attempt) % 2**31


@dataclass
class Take:
    audio: object
    seed: int
    frames: object  # the model's frames [n, 1 + 10]
    finish: str
    gen_s: float
    dec_s: float
    verdict: checks.Verdict | None = None


@dataclass
class Result:
    ok: bool
    voice: str
    text: str
    take: Take | None
    attempts: list[dict]
    seconds: float

    def report(self) -> dict:
        return {"ok": self.ok, "voice": self.voice, "text": self.text, "attempts": self.attempts,
                "seconds": round(self.seconds, 1)}


class Engine:
    def __init__(self, voices: Voices | None = None, asr: Asr | None = None) -> None:
        self.voices = voices or Voices()
        self.asr = asr if asr is not None else Asr()
        self.ready = False
        self.error: str | None = None
        self.device = "?"
        self.load_seconds = 0.0
        self.info: dict = {}
        # two requests may be in flight: one generating on the GPU while the other is decoded and
        # heard on the CPU; the model, its RNG and the codec are one at a time
        self.gpu = threading.Lock()
        self.cpu = threading.Lock()

    def load(self) -> None:
        t0 = time.monotonic()
        import torch
        import drama3_rocm as D

        self.torch, self.D = torch, D
        from fish_speech.models.text2semantic.fish_omni import generate as G
        self.G = G
        if not torch.cuda.is_available():
            raise RuntimeError("no ROCm/CUDA device: Drama 3 needs the GPU")
        best = max(range(torch.cuda.device_count()), key=lambda i: torch.cuda.get_device_properties(i).total_memory)
        torch.cuda.set_device(best)
        dev = f"cuda:{best}"
        D.protect_host(MIN_AVAILABLE_GIB, log=log.error)
        total = torch.cuda.get_device_properties(best).total_memory
        torch.cuda.set_per_process_memory_fraction(min(1.0, VRAM_CAP_GIB * 2**30 / total), best)
        self.model, self.info = D.load_model(WEIGHTS, device=dev, cap_gib=VRAM_CAP_GIB)
        self.codec = D.load_codec(WEIGHTS, "cpu")
        props = torch.cuda.get_device_properties(best)
        self.device = f"rocm {props.gcnArchName} ({props.name})" if torch.version.hip else f"cuda ({props.name})"
        self.load_seconds = time.monotonic() - t0
        self.ready = True
        log.info("Drama 3 ready on %s in %.1fs: %s", self.device, self.load_seconds, json.dumps(self.info))

    # ------------------------------------------------------------------ one take
    def prompt(self, text: str, voice: Voice, describe: bool = False):
        """The prompt rows: after the voice's reference (its transcript and codes), the line; with
        [describe] (or no reference yet), the line led by the voice's description instead."""
        torch = self.torch
        if describe or voice.ref_codes is None:
            line = f"{voice.description} {text}".strip() if voice.description else text
            return self.G.build_drama3_prompt(line, self.model.tokenizer, self.model.config)
        codes = torch.as_tensor(voice.ref_codes, dtype=torch.long)
        return self.G.build_drama3_prompt(text, self.model.tokenizer, self.model.config,
                                          prompt_texts=[voice.ref_text], prompt_tokens=[codes])

    def take(self, text: str, voice: Voice, seed: int, describe: bool = False) -> Take:
        torch = self.torch
        gen = self.voices.gen(voice)
        prompt = self.prompt(text, voice, describe)
        with self.gpu:  # the seed and the sampling together: the same seed gives the same take
            torch.manual_seed(seed)
            t = time.perf_counter()
            frames, finish = self.G.generate_drama3(self.model, prompt, max_new_tokens=frame_cap(text),
                                                    temperature=gen["temperature"], top_k=gen["top_k"], top_p=gen["top_p"])
            torch.cuda.synchronize()
            gen_s = time.perf_counter() - t
        if len(frames) == 0:
            import numpy as np
            return Take(np.zeros(0, dtype=np.float32), seed, frames, finish, gen_s, 0.0)
        codes = frames[:, 1:].T.contiguous()[None].long()
        with self.cpu:
            t = time.perf_counter()
            with torch.inference_mode():
                audio = self.codec.from_indices(codes)[0, 0].float().cpu().numpy()
            dec_s = time.perf_counter() - t
        return Take(audio, seed, frames, finish, gen_s, dec_s)

    def judge(self, take: Take, text: str, voice: Voice, hear: bool = True, detect: bool = False,
              spec: dict | None = None) -> checks.Verdict:
        """The checks of a take: the signal first; Whisper only when those pass (it costs seconds).
        [spec]: the speaker as checked (voice.spec: its gender, age and reference pitch)."""
        import numpy as np
        if not len(take.audio) or not np.isfinite(take.audio).all():
            v = checks.Verdict()
            v.fail("audio: empty or not finite")
            return v
        v = checks.judge(take.audio, SAMPLE_RATE, text, spec or voice.spec, None, ended=take.finish == "eos")
        if v.ok and hear and self.asr.available:
            got = self.asr.hear(take.audio, SAMPLE_RATE, "sl", detect=detect)
            t = checks.text_match(text, got.get("text", ""))
            v.metrics.update({"heard": got.get("text", ""), "text_score": t["score"], "asr_s": got.get("seconds")})
            if detect:
                v.metrics.update({"language": got.get("language"), "language_p": got.get("p")})
            if t["hard"]:
                v.fail("text: " + "; ".join(t["hard"]))
            elif not t["ok"]:
                v.fail("text: too many near misses: " + "; ".join(t["soft"]))
            v.warnings.extend(t["warnings"])
        elif v.ok and hear:
            v.warnings.append("text: not checked (no Whisper helper)")
        return v

    def synth(self, text: str, voice: Voice, attempts: int = ATTEMPTS, describe: bool = False) -> Result:
        """The first take of [text] in [voice] that passes the checks, trying other seeds; when every take
        failed on noise alone, the best of those through the hiss filter, if it passes then (DENOISE)."""
        t0 = time.monotonic()
        tried = []
        noisy: Take | None = None
        for n in range(attempts):
            seed = seed_for(voice, text, n)
            tk = self.take(text, voice, seed, describe)
            tk.verdict = self.judge(tk, text, voice)
            m = tk.verdict.metrics
            tried.append({"seed": seed, "frames": len(tk.frames), "finish": tk.finish, "gen_s": round(tk.gen_s, 1),
                          **tk.verdict.to_dict()})
            log.info("take %d of %r (%s, seed %d): %s %s", n + 1, text, voice.name, seed,
                     "ok" if tk.verdict.ok else "FAIL " + "; ".join(tk.verdict.reasons),
                     json.dumps({k: m.get(k) for k in ("f0", "noise_floor", "text_score", "seconds")}))
            if tk.verdict.ok:
                return Result(True, voice.name, text, tk, tried, time.monotonic() - t0)
            if all(r.startswith("noise:") for r in tk.verdict.reasons) and (
                    noisy is None or tk.verdict.metrics.get("noise_floor", 0) < noisy.verdict.metrics.get("noise_floor", 0)):
                noisy = tk
        if noisy is not None and DENOISE != "off":
            clean = Take(denoise(noisy.audio), noisy.seed, noisy.frames, noisy.finish, noisy.gen_s, noisy.dec_s)
            clean.verdict = self.judge(clean, text, voice)
            clean.verdict.metrics["denoised"] = DENOISE
            tried.append({"seed": noisy.seed, "frames": len(noisy.frames), "finish": noisy.finish, "denoised": DENOISE,
                          **clean.verdict.to_dict()})
            log.info("take of %r (%s, seed %d) through the %s filter: %s", text, voice.name, noisy.seed, DENOISE,
                     "ok" if clean.verdict.ok else "FAIL " + "; ".join(clean.verdict.reasons))
            if clean.verdict.ok:
                return Result(True, voice.name, text, clean, tried, time.monotonic() - t0)
        return Result(False, voice.name, text, None, tried, time.monotonic() - t0)


def denoise(audio, sr: int = SAMPLE_RATE, chain: str = HISS):
    """[audio] through the hiss filter (ffmpeg's afftdn and agate): the noise bed down ~30 dB, the voice kept."""
    import numpy as np
    cmd = [FFMPEG, "-hide_banner", "-loglevel", "error", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", "pipe:0",
           "-af", chain, "-f", "f32le", "pipe:1"]
    res = subprocess.run(cmd, input=np.asarray(audio, dtype=np.float32).tobytes(), capture_output=True, check=False, timeout=60)
    if res.returncode != 0:
        raise RuntimeError("ffmpeg failed: " + res.stderr.decode(errors="replace")[-300:])
    return np.frombuffer(res.stdout, dtype=np.float32).copy()


def peak_normalise(audio, peak: float = 0.89):
    import numpy as np
    p = float(np.abs(audio).max()) or 1.0
    return (audio * (peak / p)).clip(-1, 1).astype(np.float32)


def to_mp3(audio, sr: int = SAMPLE_RATE, bitrate: str = MP3_BITRATE) -> bytes:
    import numpy as np
    cmd = [FFMPEG, "-hide_banner", "-loglevel", "error", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", "pipe:0",
           "-codec:a", "libmp3lame", "-b:a", bitrate, "-ar", str(sr), "-f", "mp3", "pipe:1"]
    res = subprocess.run(cmd, input=np.asarray(audio, dtype=np.float32).tobytes(), capture_output=True, check=False, timeout=60)
    if res.returncode != 0:
        raise RuntimeError("ffmpeg failed: " + res.stderr.decode(errors="replace")[-300:])
    return res.stdout
