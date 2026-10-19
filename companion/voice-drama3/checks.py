"""Quality checks for a Drama 3 take: the gender by pitch, noise, the text by Whisper, length and silence.

Every take the worker makes goes through these before it is returned (server.py, refs.py); a take
that fails one is made again with another seed. The signal checks need numpy only; text_match is
pure Python. All thresholds are in LIMITS, measured on the spike's clips (README.md, "The quality
guard").
"""

from __future__ import annotations

import math
import re
import unicodedata
from dataclasses import dataclass, field

# The thresholds. Pitch in Hz (median F0 over the voiced frames); noise in dB below the take's
# speech level (the 95th percentile of its frame levels).
LIMITS = {
    # gender by pitch: [fail_below, warn_below, warn_above, fail_above] per class; None: no bound
    "pitch": {
        "male": (None, None, 165.0, 185.0),
        "female": (135.0, 150.0, None, None),
        # a child's voice is high whichever the gender, a girl's and a boy's alike (about 250-300 Hz
        # at eight); under 230 Hz it may be a woman's
        "child": (200.0, 230.0, None, None),
    },
    # the same speaker's pitch against their reference voice's, in semitones
    "pitch_drift_warn": 4.0,
    "pitch_drift_fail": 6.0,
    # less than this share of voiced frames: a whisper or noise, not the voice asked for
    "voiced_min": 0.15,
    # the noise bed: the quietest 10 % of the frames in the 4-12 kHz band, against the speech level
    "noise_floor_warn": -60.0,
    "noise_floor_fail": -57.0,
    # noise-like frames (a flat 4-12 kHz band near the speech level, no voice under it), seconds
    "noise_seconds_fail": 0.5,
    # silence (under the speech level by 40 dB): the longest run inside the take, and at its ends
    "pause_max_s": 1.5,
    "edge_max_s": 2.0,
    # speech rate in letters a second: slower is a loop or a stall, faster a take cut short
    "rate_min": 4.0,
    "rate_max": 25.0,
    # the text: soft errors (a near miss, a lost one-letter word) at most this share of the words
    "soft_max": 0.34,
}


# --- the signal ---------------------------------------------------------------------------------

def _np():
    import numpy as np
    return np


def _frames(x, size: int, hop: int):
    np = _np()
    if len(x) < size:
        x = np.pad(x, (0, size - len(x)))
    n = 1 + (len(x) - size) // hop
    idx = np.arange(size)[None, :] + hop * np.arange(n)[:, None]
    return x[idx]


def levels(audio, sr: int, win_s: float = 0.02) -> "object":
    """Frame levels in dBFS (RMS of 20 ms frames, hop 10 ms)."""
    np = _np()
    fr = _frames(np.asarray(audio, dtype=np.float64), int(win_s * sr), int(0.01 * sr))
    return 10 * np.log10((fr ** 2).mean(axis=1) + 1e-12)


def pitch(audio, sr: int, fmin: float = 60.0, fmax: float = 500.0, threshold: float = 0.15) -> dict:
    """Median F0 of the voiced frames (YIN), and the share of voiced frames.

    A frame is voiced when it is within 35 dB of the take's speech level and its cumulative mean
    normalised difference dips under 0.25.
    """
    np = _np()
    x = np.asarray(audio, dtype=np.float64)
    tau_min, tau_max = int(sr / fmax), int(math.ceil(sr / fmin))
    w, hop = int(0.025 * sr), int(0.01 * sr)
    n = w + tau_max
    if len(x) < n:
        return {"f0": None, "voiced": 0.0, "f0_p10": None, "f0_p90": None}
    fr = _frames(x, n, hop)
    nfft = 1 << (n + w - 1).bit_length()
    head = fr[:, :w]
    r = np.fft.irfft(np.conj(np.fft.rfft(head, nfft)) * np.fft.rfft(fr, nfft), nfft)[:, : tau_max + 1]
    e0 = (head ** 2).sum(axis=1)
    cs = np.concatenate([np.zeros((len(fr), 1)), np.cumsum(fr ** 2, axis=1)], axis=1)
    taus = np.arange(tau_max + 1)
    e_tau = cs[:, w + taus] - cs[:, taus]
    d = np.maximum(e0[:, None] + e_tau - 2 * r, 0.0)
    d[:, 0] = 0.0
    run = np.cumsum(d[:, 1:], axis=1)
    cmnd = np.ones_like(d)
    cmnd[:, 1:] = d[:, 1:] * taus[1:] / np.maximum(run, 1e-12)
    level = 10 * np.log10((head ** 2).mean(axis=1) + 1e-12)
    loud = level > np.percentile(level, 95) - 35
    f0 = np.full(len(fr), np.nan)
    for i in np.nonzero(loud)[0]:
        c = cmnd[i]
        below = np.nonzero(c[tau_min:] < threshold)[0]
        if len(below):
            t = tau_min + below[0]
            while t + 1 <= tau_max and c[t + 1] < c[t]:
                t += 1
        else:
            t = tau_min + int(np.argmin(c[tau_min:]))
        if c[t] >= 0.25:
            continue
        if 0 < t < tau_max:  # parabolic refinement
            a, b, cc = c[t - 1], c[t], c[t + 1]
            den = a - 2 * b + cc
            t = t + (0.5 * (a - cc) / den if den else 0.0)
        f0[i] = sr / t
    v = f0[~np.isnan(f0)]
    if not len(v):
        return {"f0": None, "voiced": 0.0, "f0_p10": None, "f0_p90": None}
    return {"f0": round(float(np.median(v)), 1), "voiced": round(len(v) / len(fr), 3),
            "f0_p10": round(float(np.percentile(v, 10)), 1), "f0_p90": round(float(np.percentile(v, 90)), 1)}


def noise(audio, sr: int) -> dict:
    """The noise bed and the noise-like stretches of a take.

    Drama 3's codec sometimes lays a flat hiss up to a sharp edge (12-16 kHz) under a take, or
    fills its pauses with it. floor: the quietest 10 % of the frames' 4-12 kHz band, in dB under
    the speech level (clean takes -60 to -72, hissing ones -53 to -57; Gepard's -71 to -80).
    seconds: frames whose 4-12 kHz band is flat (spectral flatness over 0.25 from 1 kHz up), near
    the speech level (within 55 dB) and without voice under 4 kHz (25 dB down).
    """
    np = _np()
    x = np.asarray(audio, dtype=np.float64)
    n = 1024 if sr > 30000 else 512
    hop = n // 2
    fr = _frames(x, n, hop) * np.hanning(n)
    p = np.abs(np.fft.rfft(fr, axis=1)) ** 2 + 1e-20
    f = np.fft.rfftfreq(n, 1 / sr)
    full = 10 * np.log10(p.sum(axis=1))
    ref = np.percentile(full, 95)
    top = min(12000.0, sr / 2 * 0.9)
    hf = 10 * np.log10(p[:, (f >= 4000) & (f < top)].sum(axis=1))
    lo = 10 * np.log10(p[:, (f >= 100) & (f < 4000)].sum(axis=1))
    band = p[:, (f >= 1000) & (f < top)]
    flat = np.exp(np.log(band).mean(axis=1)) / band.mean(axis=1)
    noisy = (hf > ref - 55) & (flat > 0.25) & (lo < ref - 25)
    return {"floor": round(float(np.percentile(hf, 10) - ref), 1), "seconds": round(float(noisy.sum() * hop / sr), 2)}


def silence(audio, sr: int) -> dict:
    """Silent runs (40 dB under the speech level): the longest inside the take, and its ends (s)."""
    np = _np()
    lv = levels(audio, sr)
    if not len(lv):
        return {"pause": 0.0, "lead": 0.0, "trail": 0.0, "sound": 0.0}
    sound = lv > np.percentile(lv, 95) - 40
    idx = np.nonzero(sound)[0]
    if not len(idx):
        return {"pause": 0.0, "lead": round(len(lv) * 0.01, 2), "trail": 0.0, "sound": 0.0}
    inner = sound[idx[0]: idx[-1] + 1]
    longest = run = 0
    for s in inner:
        run = 0 if s else run + 1
        longest = max(longest, run)
    return {"pause": round(longest * 0.01, 2), "lead": round(float(idx[0]) * 0.01, 2),
            "trail": round(float(len(lv) - 1 - idx[-1]) * 0.01, 2), "sound": round(float(idx[-1] - idx[0] + 1) * 0.01, 2)}


# --- the text ------------------------------------------------------------------------------------

def words(text: str) -> list[str]:
    """Lower-case words, without bracketed directions ("[warmly]") and punctuation."""
    t = unicodedata.normalize("NFC", re.sub(r"\[[^\]]*\]", " ", text)).lower()
    return [w for w in re.split(r"[^\w]+", t) if w and not w.isspace()]


def _lev(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def similarity(a: str, b: str) -> float:
    return 1.0 - _lev(a, b) / max(len(a), len(b), 1)


# Slovene devoices a voiced consonant at the end of a word and before a voiceless one ("gozd" is
# said [gost], "odpeljem" [otpeljem]): Whisper may write what it hears, a near miss, not an error.
_DEVOICE = str.maketrans({"b": "p", "d": "t", "g": "k", "z": "s", "ž": "š", "v": "f"})


def _sub_cost(e: str, h: str) -> tuple[float, str]:
    """The cost and the kind of hearing h for e: ('', 0) the same; 'soft' a near miss; 'hard'."""
    if e == h:
        return 0.0, ""
    if h.isdigit() or len(e) <= 2 or similarity(e, h) >= 0.6 or e.translate(_DEVOICE) == h.translate(_DEVOICE):
        return 0.5, "soft"
    return 1.0, "hard"


def _warning(e: str, h: str) -> str | None:
    """The Slovene sounds Drama 3 is known to miss, heard in a near miss (warnings, not errors)."""
    for pair in ("lj", "nj"):
        if pair in e and e.replace(pair, pair[0]) == h:
            return f"{pair} lost: {e} → {h}"
    if "lj" in e and "nj" in e and e.replace("lj", "l").replace("nj", "n") == h:
        return f"lj/nj lost: {e} → {h}"
    # the schwa of "dober", "veter": heard as a (Croatian "dobar") or dropped ("dobr")
    for m in re.finditer("e", e):
        for alt in ("a", ""):
            if e[: m.start()] + alt + e[m.end():] == h and re.search(r"[bcčdfghjklmnprsštvzž]e[rl]", e):
                return f"schwa: {e} → {h}"
    return None


def text_match(expected: str, heard: str) -> dict:
    """How the words Whisper heard match the text.

    Words are aligned with merges and splits allowed ("midva" heard as "mi dva"). A hard error: a
    word of three or more letters missed, added, or heard as a different word (under 60 % alike);
    soft: a near miss ("kruhu" for "kruh"; "gost" for "gozd", said so), a word of one or two
    letters, a number in digits. A
    take passes with no hard error and soft errors on at most a third of the words. Lost lj/nj and
    the schwa of "dober" heard as "dobar" are warnings.
    """
    e, h = words(expected), words(heard)
    inf = float("inf")
    n, m = len(e), len(h)
    cost = [[inf] * (m + 1) for _ in range(n + 1)]
    back: list[list[tuple | None]] = [[None] * (m + 1) for _ in range(n + 1)]
    cost[0][0] = 0.0

    def relax(i2, j2, c, step):
        if c < cost[i2][j2]:
            cost[i2][j2] = c
            back[i2][j2] = step

    for i in range(n + 1):
        for j in range(m + 1):
            c = cost[i][j]
            if c == inf:
                continue
            if i < n:  # a word missed
                relax(i + 1, j, c + (1.0 if len(e[i]) >= 3 else 0.5), ("del", i, j))
            if j < m:  # a word added
                relax(i, j + 1, c + (1.0 if len(h[j]) >= 3 else 0.5), ("ins", i, j))
            if i < n and j < m:
                relax(i + 1, j + 1, c + _sub_cost(e[i], h[j])[0], ("sub", i, j))
            if i < n and j + 1 < m and e[i] == h[j] + h[j + 1]:  # heard split
                relax(i + 1, j + 2, c, ("split", i, j))
            if i + 1 < n and j < m and e[i] + e[i + 1] == h[j]:  # heard joined
                relax(i + 2, j + 1, c, ("join", i, j))
    hard, soft, warnings = [], [], []
    i, j = n, m
    while (i, j) != (0, 0):
        step = back[i][j]
        kind, pi, pj = step
        if kind == "del":
            (hard if len(e[pi]) >= 3 else soft).append(f"missed: {e[pi]}")
        elif kind == "ins":
            (hard if len(h[pj]) >= 3 else soft).append(f"added: {h[pj]}")
        elif kind == "sub":
            _, how = _sub_cost(e[pi], h[pj])
            if how:
                (hard if how == "hard" else soft).append(f"{e[pi]} → {h[pj]}")
                w = _warning(e[pi], h[pj])
                if w:
                    warnings.append(w)
        i, j = pi, pj
    hard.reverse(), soft.reverse(), warnings.reverse()
    ok = not hard and len(soft) <= LIMITS["soft_max"] * max(n, 1)
    return {"ok": ok, "words": n, "hard": hard, "soft": soft, "warnings": warnings,
            "score": round(1.0 - min(1.0, cost[n][m] / max(n, 1)), 3)}


# --- the verdict ---------------------------------------------------------------------------------

@dataclass
class Verdict:
    ok: bool = True
    reasons: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    metrics: dict = field(default_factory=dict)

    def fail(self, why: str) -> None:
        self.ok = False
        self.reasons.append(why)

    def to_dict(self) -> dict:
        return {"ok": self.ok, "reasons": self.reasons, "warnings": self.warnings, "metrics": self.metrics}


def pitch_class(spec: dict) -> str:
    """'child', 'male' or 'female': a speaker's band for the gender check."""
    return "child" if spec.get("age") == "child" else ("male" if spec.get("gender") == "male" else "female")


def check_gender(f0: float | None, spec: dict, v: Verdict) -> None:
    if f0 is None:
        return
    cls = pitch_class(spec)
    fail_lo, warn_lo, warn_hi, fail_hi = LIMITS["pitch"][cls]
    if (fail_lo is not None and f0 < fail_lo) or (fail_hi is not None and f0 > fail_hi):
        v.fail(f"gender: {f0:.0f} Hz is not a {cls} voice")
    elif (warn_lo is not None and f0 < warn_lo) or (warn_hi is not None and f0 > warn_hi):
        v.warnings.append(f"gender: {f0:.0f} Hz is low/high for a {cls} voice")
    ref = spec.get("ref_f0")
    if ref:
        drift = 12 * math.log2(f0 / ref)
        v.metrics["drift"] = round(drift, 1)
        if abs(drift) > LIMITS["pitch_drift_fail"]:
            v.fail(f"pitch: {drift:+.1f} semitones from the voice's reference ({ref:.0f} Hz)")
        elif abs(drift) > LIMITS["pitch_drift_warn"]:
            v.warnings.append(f"pitch: {drift:+.1f} semitones from the voice's reference")


def judge(audio, sr: int, text: str, spec: dict, heard: str | None = None, ended: bool = True) -> Verdict:
    """All checks of one take. [spec]: the speaker (gender, age, ref_f0); [heard]: Whisper's
    transcript (None: the text check is skipped); [ended]: the model stopped by itself (no cap)."""
    v = Verdict()
    dur = len(audio) / sr
    v.metrics["seconds"] = round(dur, 2)
    if not ended:
        v.fail("length: no end (the frame cap was reached: a loop)")
    p = pitch(audio, sr)
    v.metrics.update({"f0": p["f0"], "voiced": p["voiced"]})
    if p["voiced"] < LIMITS["voiced_min"]:
        v.fail(f"voice: only {p['voiced']:.0%} voiced (a whisper or noise)")
    check_gender(p["f0"], spec, v)
    nz = noise(audio, sr)
    v.metrics.update({"noise_floor": nz["floor"], "noise_s": nz["seconds"]})
    if nz["floor"] > LIMITS["noise_floor_fail"]:
        v.fail(f"noise: a hiss bed at {nz['floor']:.0f} dB under the speech")
    elif nz["seconds"] > LIMITS["noise_seconds_fail"]:
        v.fail(f"noise: {nz['seconds']:.1f} s of hiss")
    elif nz["floor"] > LIMITS["noise_floor_warn"]:
        v.warnings.append(f"noise: the floor at {nz['floor']:.0f} dB under the speech")
    s = silence(audio, sr)
    v.metrics.update({"pause": s["pause"], "lead": s["lead"], "trail": s["trail"]})
    if s["pause"] > LIMITS["pause_max_s"]:
        v.fail(f"silence: a pause of {s['pause']:.1f} s")
    if max(s["lead"], s["trail"]) > LIMITS["edge_max_s"]:
        v.fail(f"silence: {max(s['lead'], s['trail']):.1f} s at an end")
    letters = len(re.sub(r"[\W\d_]+", "", re.sub(r"\[[^\]]*\]", "", text)))
    if s["sound"] > 0 and letters >= 8:
        rate = letters / s["sound"]
        v.metrics["rate"] = round(rate, 1)
        if rate < LIMITS["rate_min"]:
            v.fail(f"length: {rate:.1f} letters a second (too slow: a stall)")
        elif rate > LIMITS["rate_max"]:
            v.fail(f"length: {rate:.1f} letters a second (too fast: cut short)")
    if heard is not None:
        t = text_match(text, heard)
        v.metrics.update({"heard": heard, "text_score": t["score"]})
        if t["hard"]:
            v.fail("text: " + "; ".join(t["hard"]))
        elif not t["ok"]:
            v.fail("text: too many near misses: " + "; ".join(t["soft"]))
        v.warnings.extend(t["warnings"])
    return v


def score(v: Verdict) -> float:
    """A take's rank among others (higher is better): passing first, then fewer warnings, the
    text, the noise floor and the pitch drift."""
    m = v.metrics
    s = (100.0 if v.ok else 0.0) - 5.0 * len(v.reasons) - 2.0 * len(v.warnings)
    s += 10.0 * float(m.get("text_score", 1.0))
    s -= 0.2 * max(0.0, float(m.get("noise_floor", -70.0)) + 70.0)
    s -= 0.5 * abs(float(m.get("drift", 0.0)))
    return round(s, 2)
