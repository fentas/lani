#!/usr/bin/env python3
"""The talk benchmark's audio (companion/stt-local/README.md, "Talks"): the corpus clips as a tablet would send them.

  prep.py <corpus dir> <out dir>      (the corpus: corpus.json, [{file, text, voice, words, source}], and its clips)

Writes <out>/<cond>/<n>.m4a (AAC 32 kbit/s, 16 kHz mono: what the app's SpeechRecorder sends) and <out>/manifest.json.

Conditions:
  clean  the clip, 16 kHz mono, a little silence before and after
  teo    like a child's take on a tablet: an adult voice pitched up 3-5 semitones (formants with it), a tablet mic
         (band 150-250 Hz .. 4.5-6 kHz, a small room's echo), a low level, room noise at 8-18 dB SNR, the room before
         the first word and up to 3 s after the last (the talk ends a take after a 3 s pause); a third mumbled
         (low-passed at 2-2.6 kHz), a third with the first 60-140 ms of speech cut off, a quarter with a TV or
         a sibling behind (another clip, reversed, 12-16 dB down)
  noise  takes with no words: room noise, an "ooo" sung, a reversed mumble, breaths and knocks (120)

Deterministic: every random choice is seeded by the clip's file name.
"""
import hashlib
import json
import random
import subprocess
import sys
from pathlib import Path

import numpy as np

SR = 16000
FFMPEG = "/usr/bin/ffmpeg"
CHILD = {"girl", "boy"}


def seed(*parts) -> random.Random:
    return random.Random(int(hashlib.sha256("|".join(map(str, parts)).encode()).hexdigest()[:12], 16))


def decode(path: Path, filters: str | None = None) -> np.ndarray:
    cmd = [FFMPEG, "-hide_banner", "-loglevel", "error", "-nostdin", "-i", str(path)]
    if filters:
        cmd += ["-af", filters]
    cmd += ["-ac", "1", "-ar", str(SR), "-f", "f32le", "pipe:1"]
    r = subprocess.run(cmd, capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32).copy()


def encode(x: np.ndarray, out: Path) -> None:
    """AAC in MP4, 16 kHz mono, 32 kbit/s: SpeechRecorder's format."""
    out.parent.mkdir(parents=True, exist_ok=True)
    x = np.clip(x, -1.0, 1.0).astype(np.float32)
    subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-nostdin", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1",
                    "-i", "pipe:0", "-c:a", "aac", "-b:a", "32k", "-ar", str(SR), "-ac", "1", str(out)],
                   input=x.tobytes(), check=True)


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x.astype(np.float64) ** 2))) if len(x) else 0.0


def active(x: np.ndarray, frame: int = 320) -> np.ndarray:
    """The frames (20 ms) of speech: over 10 % of the loudest frame's RMS."""
    n = len(x) // frame
    if n == 0:
        return x
    f = x[: n * frame].reshape(n, frame)
    e = np.sqrt((f.astype(np.float64) ** 2).mean(axis=1))
    return f[e > 0.1 * e.max()].ravel()


def onset(x: np.ndarray, frame: int = 160) -> int:
    n = len(x) // frame
    if n == 0:
        return 0
    e = np.sqrt((x[: n * frame].reshape(n, frame).astype(np.float64) ** 2).mean(axis=1))
    above = np.nonzero(e > 0.1 * e.max())[0]
    return int(above[0] * frame) if len(above) else 0


def room(n: int, r: random.Random) -> np.ndarray:
    """A room: pink-ish noise, a little rumble and a mains hum."""
    g = np.random.default_rng(r.randrange(2**32))
    white = g.standard_normal(n)
    # pink: white shaped by 1/sqrt(f) in the frequency domain
    spec = np.fft.rfft(white)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spec[1:] /= np.sqrt(freqs[1:] / 50)
    spec[0] = 0
    pink = np.fft.irfft(spec, n)
    pink = pink / (np.std(pink) + 1e-9)
    t = np.arange(n) / SR
    hum = sum(a * np.sin(2 * np.pi * f * t + r.random() * 6.28) for f, a in ((50, 0.3), (100, 0.15), (150, 0.08)))
    return (0.8 * pink + 0.08 * white + hum * r.uniform(0.0, 1.0)).astype(np.float32)


def at_snr(speech: np.ndarray, noise: np.ndarray, snr_db: float) -> np.ndarray:
    s = rms(active(speech))
    k = s / (10 ** (snr_db / 20)) / (rms(noise) + 1e-12)
    return noise * k


def teo(clip: dict, corpus: Path, others: list[dict]) -> tuple[np.ndarray, dict]:
    r = seed(clip["file"], "teo")
    f = []
    info: dict = {}
    if clip["voice"] not in CHILD:
        st = r.uniform(3.0, 5.0)
        f.append(f"rubberband=pitch={2 ** (st / 12):.4f}")
        info["pitch"] = round(st, 1)
    if r.random() < 1 / 3:
        lp = r.uniform(2000, 2600)
        f.append(f"lowpass=f={lp:.0f}")
        info["mumble"] = round(lp)
    f.append("aecho=0.8:0.6:19|37|61:0.25|0.15|0.08")
    hp, lp = r.uniform(150, 250), r.uniform(4500, 6000)
    f += [f"highpass=f={hp:.0f}", f"lowpass=f={lp:.0f}"]
    x = decode(corpus / clip["file"], ",".join(f))
    if r.random() < 1 / 3:
        cut = int(r.uniform(0.06, 0.14) * SR)
        x = x[onset(x) + cut:]
        info["clipped_ms"] = round(cut / SR * 1000)
    peak = 10 ** (r.uniform(-26, -14) / 20)
    x = x / (np.abs(x).max() + 1e-9) * peak
    pre, post = int(r.uniform(0.25, 0.8) * SR), int(r.uniform(0.4, 3.0) * SR)
    take = np.concatenate([np.zeros(pre, np.float32), x, np.zeros(post, np.float32)])
    snr = r.uniform(8, 18)
    noise = at_snr(x, room(len(take), r), snr)
    out = take + noise
    if r.random() < 0.25:
        o = others[r.randrange(len(others))]
        b = decode(corpus / o["file"], "highpass=f=200,lowpass=f=4000")[::-1]
        b = np.resize(b, len(take)) if len(b) < len(take) else b[: len(take)]
        db = r.uniform(12, 16)
        out = out + b * (rms(active(x)) / (rms(active(b)) + 1e-9)) / 10 ** (db / 20)
        info["babble_db"] = round(db, 1)
    info.update(snr=round(snr, 1), level_db=round(20 * np.log10(peak), 1), after_s=round(post / SR, 1))
    return out.astype(np.float32), info


def clean(clip: dict, corpus: Path) -> tuple[np.ndarray, dict]:
    x = decode(corpus / clip["file"])
    r = seed(clip["file"], "clean")
    pad = lambda s: (np.random.default_rng(r.randrange(2**32)).standard_normal(int(s * SR)) * 1e-4).astype(np.float32)
    return np.concatenate([pad(0.3), x, pad(0.6)]), {}


def noise_takes(corpus: Path, clips: list[dict]) -> list[tuple[str, np.ndarray, dict]]:
    out = []
    for i in range(120):
        kind = ("room", "ooo", "mumble", "breath")[i % 4]
        r = seed("noise", i)
        n = int(r.uniform(1.5, 4.0) * SR)
        base = room(n, r) * 10 ** (r.uniform(-40, -30) / 20)
        t = np.arange(n) / SR
        if kind == "room":
            x = room(n, r) * 10 ** (r.uniform(-24, -16) / 20)
        elif kind == "ooo":
            f0 = r.uniform(250, 400)
            dur = r.uniform(0.6, 2.0)
            m = (t > 0.3) & (t < 0.3 + dur)
            vib = 1 + 0.02 * np.sin(2 * np.pi * 5.5 * t)
            phase = 2 * np.pi * np.cumsum(f0 * vib) / SR
            # an "o": the first harmonics strong, the higher ones weak
            v = sum(np.sin(k * phase) / k ** 1.6 for k in range(1, 12))
            env = np.where(m, np.minimum(1, np.minimum((t - 0.3) / 0.08, (0.3 + dur - t) / 0.15)), 0)
            x = (v * env).astype(np.float32)
            x = x / (np.abs(x).max() + 1e-9) * 10 ** (r.uniform(-24, -12) / 20)
        elif kind == "mumble":
            c = clips[r.randrange(len(clips))]
            y = decode(corpus / c["file"], "lowpass=f=1200")[::-1].copy()
            y = y[: n - int(0.3 * SR)]
            x = np.concatenate([np.zeros(int(0.3 * SR), np.float32), y, np.zeros(max(0, n - int(0.3 * SR) - len(y)), np.float32)])
            x = x / (np.abs(x).max() + 1e-9) * 10 ** (r.uniform(-28, -16) / 20)
        else:
            g = np.random.default_rng(r.randrange(2**32))
            x = np.zeros(n, np.float32)
            for _ in range(r.randint(1, 3)):  # breaths: band-passed noise bursts
                s, d = int(r.uniform(0.2, max(0.3, n / SR - 0.8)) * SR), int(r.uniform(0.25, 0.6) * SR)
                burst = g.standard_normal(d) * np.hanning(d)
                x[s:s + d] += burst[: len(x[s:s + d])] * 0.3
            for _ in range(r.randint(0, 3)):  # knocks
                s = int(r.uniform(0.1, n / SR - 0.1) * SR)
                k = np.exp(-np.arange(400) / 60) * g.standard_normal(400)
                x[s:s + 400] += k[: len(x[s:s + 400])]
            x = x / (np.abs(x).max() + 1e-9) * 10 ** (r.uniform(-22, -12) / 20)
        out.append((f"noise-{kind}-{i:03d}", (x + base).astype(np.float32), {"kind": kind}))
    return out


def main() -> None:
    corpus, out = Path(sys.argv[1]), Path(sys.argv[2])
    clips = json.loads((corpus / "corpus.json").read_text())
    manifest = []
    for n, c in enumerate(clips):
        others = [o for o in clips if o["text"] != c["text"]]
        for cond, make in (("clean", lambda c: clean(c, corpus)), ("teo", lambda c: teo(c, corpus, others))):
            path = out / cond / f"{n:04d}.m4a"
            x, info = make(c)
            if not path.exists():
                encode(x, path)
            manifest.append({"id": f"{n:04d}", "cond": cond, "audio": str(path), "seconds": round(len(x) / SR, 2), **info})
        if n % 100 == 0:
            print(n, flush=True)
    for name, x, info in noise_takes(corpus, clips):
        path = out / "noise" / f"{name}.m4a"
        if not path.exists():
            encode(x, path)
        manifest.append({"id": name, "cond": "noise", "audio": str(path), "seconds": round(len(x) / SR, 2), **info})
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print("done", len(manifest))


if __name__ == "__main__":
    main()
