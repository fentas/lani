#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = ["numpy>=1.25"]
# ///
"""Background ambience for the village scenes: nine quiet loops and twelve one-shots, synthesized from code (no
recordings, no samples).

  uv run companion/android/tools/ambient_sounds.py [OUT_DIR] [--preview DIR] [--only NAME ...]
      [--shots | --only-shots NAME ...] [--preview-shots DIR]

OUT_DIR defaults to app/src/main/assets/ambient next to this script. It gets one Ogg Vorbis file per loop (mono,
24 kHz): birds, crickets, owl, rain, stream, fire, wind, sea and frogs; and one per one-shot: thunder-1, thunder-2,
thunder-3, bell, bell-far, anvil, cowbell, hoot, whistle, lid, purr and whoosh. ffmpeg with libvorbis must be on the
PATH: it encodes the files and measures their loudness. The WAVs it encodes go to a temporary directory.

A plain run makes the loops and the one-shots. --only makes only the loops given (no one-shots); --shots makes only the
one-shots, --only-shots only the one-shots given (no loops).

Each loop repeats exactly every P seconds (N = P × 24000 samples). The file holds the loop from −PAD to N + PAD: the
last PAD samples of the loop, then the whole loop, then its first PAD samples (PAD = 6000 samples, 0.25 s). The
player cuts one period out of the middle, so the loop point never falls on the edges of the file. To make the loop
exact, everything is built round a circle: noise is shaped in the frequency domain of length N, slow changes have
whole cycles per loop, an event that runs past the end continues at the start, and reverb is a circular convolution.

Each loop is set to −26 LUFS (EBU R128, measured on the decoded loop played again and again for 60 s), with the true
peak at −3 dBTP or lower. The random numbers have fixed seeds, so a new run gives the same sounds.

A one-shot is not a loop: it starts after 8 ms of silence (where the codec's pre-echo of a sharp onset falls) and ends
in silence, with no pads. Filters and reverb work on it with silence round it, so nothing wraps round. Each is set by
its loudest momentary loudness (EBU R128 M, 400 ms): −28 LUFS, the thunders −26 LUFS, so none is louder than a loop at
its full level; the true peak at −3 dBTP or lower.

With --preview DIR, it also writes to DIR a 60-second WAV of each loop (decoded from its Ogg file and repeated), and a
few mixes of loops for scenes, to listen to. With --preview-shots DIR, it writes 60-second MP3s (from the files in
OUT_DIR): the thunder in a storm in the open, in a room and over the village, and the one-shots in their scenes,
mixed at the app's levels relative to each other and set to −23 LUFS, with text files of when each flash and each
one-shot comes.
"""
import argparse
import re
import subprocess
import sys
import tempfile
import wave
from math import ceil
from pathlib import Path

import numpy as np

SR = 24000  # sample rate (Hz)
PAD = 6000  # samples of the loop before and after the period in each file (0.25 s)
LUFS = -26.0  # integrated loudness of every loop
MAX_PEAK = -3.0  # the highest true peak allowed (dBTP)
QUALITY = 1  # libvorbis quality (ffmpeg -q:a)
TAU = 2 * np.pi


# --- building blocks --------------------------------------------------------------------------------------------------

def hz(n):
    """The frequencies (Hz) of the rfft of n samples."""
    return np.fft.rfftfreq(n, 1 / SR)


def db(x):
    return 10 ** (np.asarray(x) / 20)


def rms(x):
    return np.sqrt(np.mean(x * x))


def lowpass(f, fc, order=2):
    return 1 / np.sqrt(1 + (f / fc) ** (2 * order))


def highpass(f, fc, order=2):
    with np.errstate(divide="ignore", over="ignore"):
        return np.where(f > 0, 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-9)) ** (2 * order)), 0.0)


def band(f, lo, hi, order=2):
    return highpass(f, lo, order) * lowpass(f, hi, order)


def resonance(f, fc, q):
    """A 2nd-order band-pass: gain 1 at fc, bandwidth fc / q."""
    g = np.maximum(f, 1e-9)
    return 1 / np.sqrt(1 + q * q * (g / fc - fc / g) ** 2)


def filt(x, resp):
    """x through a zero-phase filter with the magnitude resp(f), round the circle of len(x)."""
    n = len(x)
    return np.fft.irfft(np.fft.rfft(x) * resp(hz(n)), n)


def convolve(x, kernel):
    """Circular convolution of x with a kernel shorter than x."""
    n = len(x)
    return np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(kernel, n), n)


def noise(rng, n, slope=0.0, resp=None):
    """Noise that repeats every n samples, unit RMS. Its amplitude goes as f ** slope: 0 white, -0.5 pink, -1 brown."""
    f = hz(n)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) * np.maximum(f, 20.0) ** slope
    if resp is not None:
        spec *= resp(f)
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / rms(x)


def slow(rng, n, kmin, kmax):
    """A smooth random curve in [-1, 1] that repeats every n samples: kmin to kmax whole cycles per loop."""
    spec = np.zeros(n // 2 + 1, complex)
    k = np.arange(kmin, kmax + 1)
    spec[k] = (rng.standard_normal(len(k)) + 1j * rng.standard_normal(len(k))) / np.sqrt(k)
    x = np.fft.irfft(spec, n)
    return x / np.max(np.abs(x))


def ramp(m):
    """m samples that rise from 0 to 1 (raised cosine)."""
    return 0.5 - 0.5 * np.cos(np.pi * (np.arange(m) + 0.5) / max(m, 1))


def envelope(m, attack, release):
    """m samples: a raised-cosine attack and release (seconds), flat between them."""
    env = np.ones(m)
    a = min(int(attack * SR), m // 2)
    r = min(int(release * SR), m - a)
    env[:a] *= ramp(a)
    if r:
        env[m - r:] *= ramp(r)[::-1]
    return env


def decay(m, attack, tau):
    """m samples: a raised-cosine attack, then an exponential decay (time constant tau), faded to 0 at the end."""
    t = np.arange(m) / SR
    env = np.exp(-np.maximum(t - attack, 0) / tau)
    a = min(int(attack * SR), m // 2)
    env[:a] *= ramp(a)
    r = max(m // 8, 1)
    env[m - r:] *= ramp(r)[::-1]
    return env


def curve(points, m, log=False):
    """m samples through (time in seconds, value) points, eased (cosine) from one point to the next."""
    ts = np.array([p[0] for p in points], float) * SR
    vs = np.array([p[1] for p in points], float)
    if log:
        vs = np.log(vs)
    i = np.arange(m)
    k = np.clip(np.searchsorted(ts, i, side="right") - 1, 0, len(ts) - 2)
    w = np.clip((i - ts[k]) / np.maximum(ts[k + 1] - ts[k], 1e-9), 0, 1)
    out = vs[k] + (vs[k + 1] - vs[k]) * (0.5 - 0.5 * np.cos(np.pi * w))
    return np.exp(out) if log else out


def sine(freq, phase=0.0, harmonics=()):
    """A sine that follows freq (Hz, one value per sample), plus harmonics as (number, level). A harmonic fades out
    before it gets to 9 kHz, so nothing folds back from above half the sample rate."""
    ph = TAU * np.cumsum(freq) / SR + phase
    x = np.sin(ph)
    for k, level in harmonics:
        x += level * np.clip((9000 - k * freq) / 1500, 0, 1) * np.sin(k * ph)
    return x


def gap(seconds):
    return np.zeros(int(seconds * SR))


def place(buf, event, start):
    """Adds event into buf from sample start on. What runs past the end continues at the start: the loop is a circle."""
    n = len(buf)
    s = int(start) % n
    e = s + len(event)
    if e <= n:
        buf[s:e] += event
    else:
        buf[s:] += event[:n - s]
        buf[:e - n] += event[n - s:]


def spread(rng, total, draw):
    """Event times round a loop of total seconds. The gaps come from draw() until they fill the loop, then they are
    scaled to fill it exactly, so the gap across the loop point is like the others."""
    gaps = [draw()]
    while sum(gaps) < total:
        gaps.append(draw())
    if len(gaps) > 1 and abs(total / (sum(gaps) - gaps[-1]) - 1) < abs(total / sum(gaps) - 1):
        gaps.pop()
    g = np.array(gaps) * total / sum(gaps)
    return (rng.uniform(0, total) + np.concatenate([[0], np.cumsum(g[:-1])])) % total


def steady(rng, total, every, jitter):
    """Event times round the loop, about every `every` seconds (± jitter, a fraction)."""
    return spread(rng, total, lambda: every * (1 + jitter * rng.uniform(-1, 1)))


def shots(rng, n, rate, tau, spread_db=6.0):
    """Circular shot noise, unit RMS: rate tiny blips a second at random times, with random signs and log-normal levels
    (spread_db), each a 0.5 ms rise and an exponential fall with time constant tau."""
    count = int(rate * n / SR)
    buf = np.zeros(n)
    np.add.at(buf, rng.integers(0, n, count), rng.choice((-1.0, 1.0), count) * db(rng.normal(0, spread_db, count)))
    y = convolve(buf, decay(int((0.0005 + 6 * tau) * SR) + 2, 0.0005, tau))
    return y / rms(y)


def moving(x, centres, pos, resp, norm=True):
    """x through a filter that moves: pos (per sample, 0 … len(centres) − 1) picks the centre, and neighbouring
    filters are crossfaded. With norm, each filter's output has unit RMS."""
    out = np.zeros_like(x)
    for i, c in enumerate(centres):
        w = np.clip(1 - np.abs(pos - i), 0, 1)
        if w.any():
            y = filt(x, lambda f: resp(f, c))
            out += w * (y / rms(y) if norm else y)
    return out


def reverb(rng, x, t60, bright, predelay=0.012):
    """The wet part of a simple reverb: x convolved round the circle with decaying noise of unit energy, low-passed at
    bright Hz. The tail falls by 60 dB in t60 seconds."""
    m = int(t60 * SR)
    tail = rng.standard_normal(m) * np.exp(-6.9 * np.arange(m) / SR / t60)
    tail = np.fft.irfft(np.fft.rfft(tail, 2 * m) * lowpass(hz(2 * m), bright), 2 * m)[:m]
    a = int(0.005 * SR)
    tail[:a] *= ramp(a)
    ir = np.concatenate([np.zeros(int(predelay * SR)), tail])
    return convolve(x, ir / np.sqrt(np.sum(ir * ir)))


def finish(x):
    """The last filter of every loop: nothing below about 90 Hz (phone speakers; no rumble), no DC, and little above
    9 kHz."""
    f = hz(len(x))
    spec = np.fft.rfft(x) * highpass(f, 95, 8) * lowpass(f, 8500, 8)
    spec[0] = 0
    return np.fft.irfft(spec, len(x))


# --- rain, stream, fire, wind, sea: shaped noise and many small events ------------------------------------------------

def rain(rng, n):
    """Steady moderate rain: a pinkish wash with slow swells, a dense layer of tiny droplet ticks and pings, fewer and
    softer drops on leaves close by, and a low wash of rain further off."""
    p = n / SR
    swell = db(1.75 * slow(rng, n, 1, 3))
    bed = noise(rng, n, -0.5, lambda f: band(f, 300, 7000))
    sizzle = filt(shots(rng, n, 1800, 0.0006), lambda f: band(f, 1200, 6500))
    far = noise(rng, n, -1.0, lambda f: band(f, 150, 800)) * db(1.5 * slow(rng, n, 1, 4))

    ticks = np.zeros(n)
    for start in rng.integers(0, n, int(75 * p)):
        if rng.random() < 0.6:  # a tiny burst of noise
            tau = rng.uniform(0.0005, 0.0015)
            m = int((0.002 + 6 * tau) * SR)
            ev = rng.standard_normal(m) * decay(m, 0.002, tau)
        else:  # a small resonant ping, rising a little
            f0 = np.exp(rng.uniform(np.log(2000), np.log(6000)))
            tau = rng.uniform(0.002, 0.007)
            m = int((0.002 + 5 * tau) * SR)
            ev = 0.7 * sine(f0 * (1 + 0.08 * np.arange(m) / m), rng.uniform(0, TAU)) * decay(m, 0.002, tau)
        place(ticks, db(rng.uniform(-22, 0)) * ev, start)
    ticks = filt(ticks, lambda f: band(f, 1500, 7000))

    leaves = np.zeros(n)
    for start in rng.integers(0, n, int(4 * p)):
        level = db(rng.uniform(-12, 0))
        for k in range(2 if rng.random() < 0.3 else 1):  # sometimes the drop runs on to a lower leaf
            f0 = rng.uniform(700, 1800)
            tau = rng.uniform(0.008, 0.02)
            m = int((0.003 + 6 * tau) * SR)
            body = sine(f0 * (1 - 0.12 * np.arange(m) / m), rng.uniform(0, TAU)) * decay(m, 0.003, tau)
            tap = rng.standard_normal(m) * decay(m, 0.002, 0.0015)
            place(leaves, level * (0.4 if k else 1.0) * (body + 0.5 * tap), start + k * rng.uniform(0.06, 0.2) * SR)
    leaves = filt(leaves, lambda f: band(f, 300, 5000))
    leaves += 0.3 * reverb(rng, leaves, 0.3, 4000)

    return (swell * (bed + 0.3 * sizzle / rms(sizzle) + 0.3 * ticks / rms(ticks)) + 0.25 * leaves / rms(leaves)
            + 0.45 * far)


def stream(rng, n):
    """A babbling brook: a few hundred small bubbles a second (short sines that glide up and die away, like Minnaert
    bubbles), some in quick gurgling groups, over a band of rushing water noise."""
    p = n / SR
    busy = 1 + 0.35 * slow(rng, n, 2, 7)  # more bubbles, then fewer
    starts = rng.integers(0, n, int(200 * 1.35 * p))
    starts = starts[rng.random(len(starts)) * 1.35 < busy[starts]]
    bubbles = np.zeros(n)
    for start in starts:
        f0 = np.exp(rng.uniform(np.log(300), np.log(1400)))
        level = db(-28 + 28 * rng.random() ** 3) * (600 / f0) ** 0.3  # most are quiet; a few stand out
        t = start
        for _ in range(1 if rng.random() < 0.75 else rng.integers(2, 5)):
            d = rng.uniform(0.005, 0.025)
            m = int(d * SR)
            fr = f0 * (1 + rng.uniform(0.1, 0.6) * (np.arange(m) / m) ** 1.3)
            place(bubbles, level * sine(fr, rng.uniform(0, TAU)) * decay(m, 0.002, d / rng.uniform(2.5, 4.5)), t)
            t += rng.uniform(0.012, 0.045) * SR
            f0 *= np.exp(rng.normal(0, 0.12))
            level *= db(rng.uniform(-4, 1))
    bed = noise(rng, n, -0.3, lambda f: band(f, 300, 3000))
    bed *= db(2 * slow(rng, n, 1, 5)) * (1 + 0.15 * slow(rng, n, 40, 140))  # slow swells and a quick flutter
    sparkle = filt(shots(rng, n, 2500, 0.0004), lambda f: band(f, 2000, 6000))
    out = bubbles / rms(bubbles) + 0.35 * bed + 0.15 * sparkle / rms(sparkle)
    return out + 0.25 * reverb(rng, out, 0.25, 3500)


def fire(rng, n):
    """A small campfire: a soft low roar that flickers, crackles (groups of tiny clicks), embers ticking, now and then a
    larger, softer pop with a short hiss, and a faint hiss of burning wood."""
    p = n / SR
    roar = noise(rng, n, -0.75, lambda f: highpass(f, 120, 4) * lowpass(f, 400, 1))
    roar *= db(2.5 * slow(rng, n, 4, 30) + 1.2 * slow(rng, n, 30, 120))
    embers = filt(shots(rng, n, 150, 0.0003, 4.0), lambda f: band(f, 2000, 7000))

    crackle = np.zeros(n)

    def cluster(t, clicks, base):
        for _ in range(clicks):
            m = max(int(rng.uniform(0.0002, 0.002) * SR), 4)
            click = rng.standard_normal(m) * np.hanning(m + 2)[1:-1]  # smooth at both ends: no hard edge
            click /= np.max(np.abs(click))  # the level sets the peak
            rm = int(rng.uniform(0.004, 0.012) * SR)
            tone = np.full(rm, rng.uniform(1500, 4500))
            ring = 0.4 * sine(tone, rng.uniform(0, TAU)) * decay(rm, 0.0005, rng.uniform(0.001, 0.003))
            level = base * db(rng.uniform(-6, 0))
            place(crackle, level * click, t)
            place(crackle, level * ring, t)
            t += (0.001 + rng.exponential(0.008)) * SR

    for t in spread(rng, p, lambda: rng.exponential(1 / 6)):
        cluster(t * SR, min(rng.geometric(0.28), 8), db(rng.uniform(-14, 0)))

    pops = np.zeros(n)
    for t in spread(rng, p, lambda: rng.uniform(3, 6)):
        m = int(0.12 * SR)
        body = filt(rng.standard_normal(m), lambda f: band(f, 250, 2500)) * decay(m, 0.003, rng.uniform(0.015, 0.03))
        hl = rng.uniform(0.2, 0.5)
        hm = int(hl * SR)
        hiss = filt(rng.standard_normal(hm), lambda f: band(f, 2500, 7000)) * decay(hm, 0.02, hl / 3)
        level = db(rng.uniform(-4, 0))
        place(pops, level * body, t * SR)
        place(pops, level * 0.12 * hiss, (t + 0.01) * SR)
        cluster(t * SR, rng.integers(2, 4), 0.8)

    crackle = filt(crackle, lambda f: band(f, 1200, 7500))
    hiss = noise(rng, n, 0.0, lambda f: band(f, 2000, 6000)) * db(3 * slow(rng, n, 3, 20))
    return (0.6 * roar + 0.5 * embers + 0.03 * hiss + 5 * crackle / np.max(np.abs(crackle))
            + 4.5 * pops / np.max(np.abs(pops)))


def wind(rng, n):
    """Wind in the mountains: pink-brown noise through a band-pass that drifts slowly (centre 150–700 Hz) and rises
    with the gusts, gusts that swell over 4–10 s, and a faint airy whistle only in the strongest gusts."""
    gust = slow(rng, n, 3, 7)
    g = (gust - gust.min()) / (gust.max() - gust.min())  # 0 … 1
    centres = np.geomspace(150, 700, 7)
    drift = (slow(rng, n, 1, 3) + 1) / 2
    pos = np.clip(0.1 + 0.6 * g + 0.3 * drift, 0, 1) * (len(centres) - 1)
    src = noise(rng, n, -0.75, lambda f: highpass(f, 110, 3))
    body = moving(src, centres, pos, lambda f, c: resonance(f, c, 1.2))
    body *= 0.3 + 0.7 * g ** 1.6

    tones = np.geomspace(700, 1200, 12)
    whistle = moving(noise(rng, n), tones, np.clip((g - 0.55) / 0.45, 0, 1) * (len(tones) - 1),
                     lambda f, c: resonance(f, c, 20))
    whistle *= np.clip((g - 0.72) / 0.28, 0, 1) ** 2
    air = noise(rng, n, -0.5, lambda f: band(f, 1500, 5000)) * g ** 3
    return body + 0.18 * whistle + 0.12 * air


def sea(rng, n):
    """Gentle waves on a sandy lagoon shore, about four a loop: each a swelling wash, a soft break and a long hiss as it
    runs back, darker and darker (low-pass from about 4 kHz down to 800 Hz), with foam fizzing. Under them, a quiet
    surf."""
    p = n / SR
    waves = np.zeros(n)
    foam_level = np.zeros(n)
    cuts = np.geomspace(500, 4000, 7)
    for start in spread(rng, p, lambda: rng.uniform(6.8, 9.2)):
        strength = rng.uniform(0.7, 1.0)
        ts, tb, tr = rng.uniform(1.5, 3.0), rng.uniform(0.3, 0.45), rng.uniform(3.0, 5.0)
        d = ts + tb + tr
        m = int((d + 0.2) * SR)
        level = curve([(0, 0), (ts * 0.5, 0.18), (ts, 0.6), (ts + 0.15, 1.0), (ts + tb, 0.75),
                       (ts + tb + 0.35 * tr, 0.38), (d, 0), (d + 0.2, 0)], m)
        cut = curve([(0, 500), (ts, 1500), (ts + 0.15, 4000), (ts + tb, 3400), (d, 800), (d + 0.2, 800)], m, log=True)
        pos = np.log(cut / cuts[0]) / np.log(cuts[-1] / cuts[0]) * (len(cuts) - 1)
        wash = moving(noise(rng, m, -0.3, lambda f: highpass(f, 150)), cuts, pos, lambda f, c: lowpass(f, c),
                      norm=False)
        place(waves, strength * level * wash, start * SR)
        place(foam_level, strength * curve([(0, 0), (ts, 0), (ts + 0.15, 1), (ts + tb + 0.3 * tr, 0.4), (d, 0),
                                            (d + 0.2, 0)], m), start * SR)
    foam = filt(shots(rng, n, 1200, 0.0004), lambda f: band(f, 2000, 6500))
    surf = noise(rng, n, -0.5, lambda f: band(f, 120, 1200)) * db(2 * slow(rng, n, 1, 4))
    return waves / rms(waves) + 0.6 * foam_level * foam / rms(foam) + 0.25 * surf


# --- birds ------------------------------------------------------------------------------------------------------------

def bird_note(rng, dur, points, attack=0.005, release=0.012, level=1.0, vib=0.006, harmonics=((2, 0.1),)):
    """One note: a sine along the (fraction of the note, Hz) points, slight vibrato, a weak 2nd harmonic (−20 dB), a
    smooth envelope. High notes are made quieter."""
    m = int(dur * SR)
    fr = curve([(u * dur, f) for u, f in points], m, log=True)
    fr = fr * (1 + vib * np.sin(TAU * rng.uniform(25, 40) * np.arange(m) / SR + rng.uniform(0, TAU)))
    tame = np.minimum(1, (2500 / fr) ** 0.7)
    return level * tame * sine(fr, rng.uniform(0, TAU), harmonics) * envelope(m, attack, release)


def great_tit(rng, v):
    """'Tea-cher tea-cher tea-cher': a high note, then a lower one, three to five times."""
    parts = []
    for _ in range(rng.integers(3, 6)):
        hi = v["hi"] * (1 + 0.01 * rng.standard_normal())
        lo = v["lo"] * (1 + 0.01 * rng.standard_normal())
        parts += [bird_note(rng, v["d1"] * rng.uniform(0.95, 1.05), [(0, hi * 1.02), (0.3, hi * 1.04), (1, hi * 0.94)]),
                  gap(rng.uniform(0.025, 0.04)),
                  bird_note(rng, v["d2"] * rng.uniform(0.95, 1.05), [(0, lo * 1.05), (0.5, lo), (1, lo * 0.97)],
                            level=0.85),
                  gap(rng.uniform(0.06, 0.09))]
    return np.concatenate(parts)


def chaffinch(rng, v):
    """A trill of 8–15 quick notes that steps down in three groups and speeds up, then a short flourish."""
    count = rng.integers(8, 16)
    steps = np.geomspace(v["top"], v["bottom"], 3) * (1 + 0.015 * rng.standard_normal(3))
    parts, i = [], 0
    for group, f, every in zip(np.array_split(np.arange(count), 3), steps, (0.095, 0.08, 0.065)):
        for j in range(len(group)):
            d = rng.uniform(0.035, 0.045)
            fj = f * (1 - 0.015 * j)
            parts += [bird_note(rng, d, [(0, fj * 1.12), (1, fj * 0.86)], attack=0.003, release=0.008,
                                level=0.6 + 0.4 * i / count), gap(every - d)]
            i += 1
    a, b, c, e = v["flourish"] * rng.uniform(0.97, 1.03)
    parts += [gap(0.03), bird_note(rng, 0.24, [(0, a), (0.25, b), (0.6, c), (1, e)], release=0.03)]
    return np.concatenate(parts)


def sparrow(rng, v):
    """Two to four sparrow chirps: quick up-and-down notes with a little more harmonic."""
    parts = []
    for _ in range(rng.integers(2, 5)):
        f = v["f"] * rng.uniform(0.93, 1.07)
        parts += [bird_note(rng, rng.uniform(0.04, 0.065), [(0, f * 0.82), (0.4, f * 1.15), (1, f * 0.88)],
                            attack=0.003, release=0.01, level=rng.uniform(0.7, 1.0), vib=0.0,
                            harmonics=((2, 0.2), (3, 0.06))),
                  gap(rng.uniform(0.12, 0.35))]
    return np.concatenate(parts[:-1])


def contact(rng, v):
    """Two to four short contact calls: a thin falling 'tsip' or a 'chip'."""
    parts = []
    for _ in range(rng.integers(2, 5)):
        f = v["f"] * rng.uniform(0.97, 1.03)
        if rng.random() < 0.5:
            parts.append(bird_note(rng, 0.03, [(0, f * 1.08), (1, f * 0.85)], attack=0.003, release=0.01, level=0.6))
        else:
            parts.append(bird_note(rng, 0.035, [(0, f * 0.68), (0.5, f * 0.8), (1, f * 0.64)], attack=0.003,
                                   release=0.01))
        parts.append(gap(rng.uniform(0.2, 0.5)))
    return np.concatenate(parts[:-1])


def blackbird(rng, v):
    """A lower, fluted phrase (1.8–3.2 kHz, 1–2 s): four to seven notes with smooth glides, some with a warble."""
    parts = []
    for _ in range(rng.integers(4, 8)):
        a, b, c = rng.uniform(v["lo"], v["hi"], 3)
        parts += [bird_note(rng, rng.uniform(0.15, 0.3), [(0, a), (rng.uniform(0.25, 0.6), b), (1, c)],
                            attack=0.01, release=0.03, level=rng.uniform(0.7, 1.0),
                            vib=0.012 if rng.random() < 0.4 else 0.004, harmonics=((2, 0.1), (3, 0.03))),
                  gap(rng.uniform(0.03, 0.09))]
    return np.concatenate(parts[:-1])


FAR_BIRDS = 0.14  # the level of the birds far off


def far_call(rng, v):
    """A short call of a bird far off: one or two chirps, a thin 'tsip', a quick trill or a two-note whistle."""
    kind = v["calls"][rng.integers(len(v["calls"]))]
    f = v["f"] * rng.uniform(0.95, 1.05)
    parts = []
    if kind == "chirp":
        for _ in range(rng.integers(1, 3)):
            parts += [bird_note(rng, rng.uniform(0.04, 0.06), [(0, f * 0.85), (0.4, f * 1.12), (1, f * 0.9)],
                                attack=0.003, release=0.01, vib=0.0, harmonics=((2, 0.15),)),
                      gap(rng.uniform(0.1, 0.2))]
    elif kind == "tsip":
        parts.append(bird_note(rng, 0.03, [(0, f * 1.1), (1, f * 0.85)], attack=0.003, release=0.01))
    elif kind == "trill":
        for j in range(rng.integers(5, 10)):
            d = rng.uniform(0.025, 0.035)
            fj = f * (1 - 0.02 * j)
            parts += [bird_note(rng, d, [(0, fj * 1.1), (1, fj * 0.9)], attack=0.003, release=0.008),
                      gap(rng.uniform(0.045, 0.06) - d)]
    else:  # a two-note whistle, high then low
        parts += [bird_note(rng, 0.12, [(0, f), (1, f * 0.97)], release=0.02), gap(0.05),
                  bird_note(rng, 0.12, [(0, f * 0.8), (1, f * 0.78)], release=0.02, level=0.8)]
    return np.concatenate(parts)


def birds(rng, n):
    """A calm morning: a sparrow and a small contact caller near, a great tit, two chaffinches (one far off) and a
    blackbird. A phrase starts every 1.5–3 s, sometimes after a longer pause, and a bird rests a few seconds between
    its own phrases. The farther birds are quieter, duller and have more reverb. Under them, eleven birds far off call
    softly (about 16 dB lower, low-passed at 3–4 kHz), so the loop is never silent."""
    p = n / SR
    singers = [  # song, voice, turns in each round, rest (s), level, low-pass (Hz), reverb send
        (sparrow, {"f": rng.uniform(3000, 3600)}, 3, 3, 0.55, 8000, 0.25),
        (contact, {"f": rng.uniform(4800, 5600)}, 2, 4, 0.3, 7000, 0.4),
        (great_tit, {"hi": rng.uniform(5200, 6200), "lo": rng.uniform(4000, 4500), "d1": rng.uniform(0.08, 0.1),
                     "d2": rng.uniform(0.09, 0.12)}, 3, 5, 0.45, 6500, 0.4),
        (chaffinch, {"top": rng.uniform(5400, 6000), "bottom": rng.uniform(3000, 3500),
                     "flourish": np.array([3500, 4300, 2700, 3000]) * rng.uniform(0.95, 1.05)}, 2, 6, 0.5, 7000,
         0.35),
        (chaffinch, {"top": rng.uniform(5200, 5800), "bottom": rng.uniform(3100, 3600),
                     "flourish": np.array([3700, 4400, 2800, 3200]) * rng.uniform(0.95, 1.05)}, 2, 7, 0.25, 4500,
         0.7),
        (blackbird, {"lo": 1800, "hi": 3200}, 2, 7, 0.9, 5000, 0.55),
    ]
    buses = [np.zeros(n) for _ in singers]
    sung, bag = [], []  # (time, singer); the turns of this round still to come, shuffled
    for t in np.sort(spread(rng, p, lambda: rng.uniform(3.5, 5.0) if rng.random() < 0.15 else rng.uniform(1.5, 3.0))):
        if not bag:  # a new round: every bird gets its share of turns
            bag = list(rng.permutation([i for i, s in enumerate(singers) for _ in range(s[2])]))
        # a bird sings again only after its rest (round the loop): the next turn in the bag whose bird is rested
        rested = [k for k, i in enumerate(bag)
                  if all(min(abs(t - u), p - abs(t - u)) >= singers[i][3] for u, j in sung if j == i)]
        if not rested:
            continue
        i = bag.pop(rested[0])
        sung.append((t, i))
        place(buses[i], singers[i][0](rng, singers[i][1]), t * SR)
    dry, send = np.zeros(n), np.zeros(n)
    for bus, (_, _, _, _, level, lp, wet) in zip(buses, singers):
        bus = level * filt(bus, lambda f: lowpass(f, lp))
        dry += (1 - 0.5 * wet) * bus
        send += wet * bus

    far_rng = rng.spawn(1)[0]  # its own random numbers: the near birds and the reverb stay as they are
    far = np.zeros(n)
    # how many birds, seconds between the calls of each (on average), level, low-pass (Hz): six far off, and five
    # more, farther still
    for count, every, level, lp in ((6, 1.6, 1.0, 4000), (5, 1.2, 0.3, 3000)):
        bus = np.zeros(n)
        for _ in range(count):
            calls = far_rng.permutation(["chirp", "tsip", "trill", "whistle"])[:2]
            v = {"f": far_rng.uniform(2800, 5000), "calls": calls}
            gain = level * db(far_rng.uniform(-6, 0))
            for t in spread(far_rng, p, lambda: 0.2 + far_rng.exponential(every)):
                place(bus, gain * db(far_rng.uniform(-6, 0)) * far_call(far_rng, v), t * SR)
        far += FAR_BIRDS * filt(bus, lambda f: lowpass(f, lp))
    dry += 0.5 * far
    send += 0.9 * far
    return dry + 0.6 * reverb(rng, send, 0.4, 4000)


# --- crickets, owl, frogs: the night ----------------------------------------------------------------------------------

def crickets(rng, n):
    """Four field crickets, each on its own carrier (4.2–5.0 kHz): chirps of 3–4 soft pulses, 2–4 chirps a second, one
    near and three farther off (quieter, duller); and a faint tree-cricket trill far off."""
    p = n / SR
    out = np.zeros(n)
    for level, lp in ((1.0, 7000), (0.55, 5500), (0.4, 5000), (0.28, 4500)):
        f0 = rng.uniform(4200, 5000)
        pulses = rng.integers(3, 5)
        pl, pg = rng.uniform(0.012, 0.018), rng.uniform(0.015, 0.02)
        rests = [(rng.uniform(0, p), rng.uniform(1.5, 4)) for _ in range(rng.integers(0, 3))]
        bus = np.zeros(n)
        for t in steady(rng, p, 1 / rng.uniform(2.0, 4.0), 0.12):
            if any((t - a) % p < b for a, b in rests):
                continue
            chirp = []
            for k in range(pulses):
                m = int(pl * rng.uniform(0.92, 1.08) * SR)
                fr = f0 * (1 + 0.004 * rng.standard_normal()) * (1 - 0.012 * np.arange(m) / m)
                amp = (0.75, 0.9, 1.0, 0.95)[k] * db(rng.uniform(-1, 0))
                chirp += [amp * sine(fr, rng.uniform(0, TAU)) * envelope(m, 0.004, 0.005),
                          gap(pg * rng.uniform(0.9, 1.1))]
            place(bus, level * np.concatenate(chirp), t * SR)
        out += filt(bus, lambda f: lowpass(f, lp))
    fc = round(2900 * p) / p  # whole cycles per loop
    rate = round(45 * p) / p
    t = np.arange(n) / SR
    trill = np.sin(TAU * fc * t) * (0.5 - 0.5 * np.cos(TAU * rate * t)) ** 1.5 * db(3 * slow(rng, n, 1, 4))
    out += 0.06 * filt(trill, lambda f: lowpass(f, 4000))
    return out + 0.35 * reverb(rng, out, 0.7, 5000)


def hoot(rng, dur, points, level=1.0, waver=0.0):
    """One owl note: a sine with weak 2nd and 3rd harmonics and a breathy noise under it (−18 dB), soft onset. waver
    adds the tremble of the long last note."""
    m = int(dur * SR)
    t = np.arange(m) / SR
    fr = curve([(u * dur, f) for u, f in points], m, log=True)
    amp = envelope(m, min(0.06, dur / 4), min(0.15, dur / 3)) * curve([(0, 0.8), (0.3 * dur, 1.0), (dur, 0.75)], m)
    if waver:
        w = np.sin(TAU * rng.uniform(7, 10) * t) * np.clip(t / (0.3 * dur), 0, 1)
        fr = fr * (1 + 0.5 * waver * w)
        amp = amp * (1 + 0.3 * w)
    tone = sine(fr, rng.uniform(0, TAU), ((2, db(-18)), (3, db(-26))))
    breath = filt(rng.standard_normal(m), lambda f: band(f, 300, 1000, 3))
    return level * amp * (tone + db(-18) * breath / rms(breath) * np.sqrt(0.5))


def tawny_male(rng, level):
    """A long 'hoooo', a pause of 3–4 s, then 'hu … hu-hu-hu-hooooo' with a wavering last note."""
    f = rng.uniform(390, 430)
    parts = [hoot(rng, rng.uniform(0.8, 1.0), [(0, f * 0.97), (0.3, f * 1.04), (1, f * 0.93)]),
             gap(rng.uniform(3, 4)),
             hoot(rng, 0.14, [(0, f * 0.98), (1, f * 0.96)], level=0.6),
             gap(rng.uniform(0.25, 0.4))]
    for i in range(rng.integers(3, 5)):
        parts += [hoot(rng, rng.uniform(0.07, 0.09), [(0, f * 0.97), (1, f)], level=0.55 + 0.1 * i),
                  gap(rng.uniform(0.04, 0.06))]
    parts.append(hoot(rng, rng.uniform(1.0, 1.5), [(0, f), (0.2, f * 1.05), (1, f * 0.92)], level=0.95, waver=0.03))
    return level * np.concatenate(parts)


def tawny_female(rng):
    """The female's 'ke-wick': a short 'ke', then a sharp rising 'wick' (1.1–1.6 kHz)."""
    ke = sine(curve([(0, 1300), (0.07, 1200)], int(0.07 * SR), log=True), 0, ((2, 0.35), (3, 0.15)))
    ke *= envelope(len(ke), 0.005, 0.02)
    m = int(0.22 * SR)
    wick = sine(curve([(0, 1100), (0.15, 1600), (0.22, 1450)], m, log=True), 0, ((2, 0.35), (3, 0.18)))
    rasp = filt(rng.standard_normal(m), lambda f: band(f, 800, 3000))
    wick = (wick + db(-14) * rasp / rms(rasp)) * envelope(m, 0.005, 0.06)
    return np.concatenate([ke, gap(0.03), wick])


def owl(rng, n):
    """A tawny owl at night: two call sequences of the male (the second one farther off) and one 'ke-wick' of the
    female between them; the rest is silence, with a night-forest reverb."""
    out = np.zeros(n)
    a = rng.uniform(1.0, 3.0)
    place(out, tawny_male(rng, 1.0), a * SR)
    place(out, 0.45 * filt(np.concatenate([tawny_female(rng), gap(0.1)]), lambda f: lowpass(f, 3000)),
          (a + rng.uniform(11, 14)) * SR)
    place(out, filt(np.concatenate([tawny_male(rng, 0.7), gap(0.1)]), lambda f: lowpass(f, 1800)),
          (a + rng.uniform(19, 22)) * SR)
    return out + 0.5 * reverb(rng, out, 1.3, 2500)


def kre(rng, v):
    """One 'kre' of a tree frog: 30–50 ms of a buzzy tone (a pulse train at 100–150 Hz on a 1–2 kHz carrier)."""
    dur = rng.uniform(0.03, 0.05)
    m = int(dur * SR)
    t = np.arange(m) / SR
    pulses = (0.5 - 0.5 * np.cos(TAU * v["pulse"] * t)) ** 2
    fr = v["f"] * (1 + 0.04 * (1 - t / dur))
    return sine(fr, rng.uniform(0, TAU), ((2, 0.25), (3, 0.08))) * pulses * envelope(m, 0.004, 0.008)


def croak(rng, f0):
    """A pond frog's short rolling 'rrrk': 30–40 low pulses a second (300–600 Hz), 0.25–0.45 s."""
    dur = rng.uniform(0.25, 0.45)
    m = int(dur * SR)
    buf = np.zeros(m)
    rate = rng.uniform(28, 40)
    t = 0.0
    while t < dur - 0.02:
        k = int(t * SR)
        pm = min(int(0.03 * SR), m - k)
        tt = np.arange(pm) / SR
        pulse = (np.sin(TAU * f0 * tt) + 0.5 * np.sin(TAU * 2.4 * f0 * tt) * np.exp(-tt / 0.003)) * np.exp(-tt / 0.007)
        pulse[:24] *= ramp(min(24, pm))
        buf[k:k + pm] += db(rng.uniform(-3, 0)) * pulse
        t += rng.uniform(0.9, 1.1) / rate
    return buf * curve([(0, 0), (0.03, 0.8), (0.35 * dur, 1.0), (dur, 0)], m)


def frogs(rng, n):
    """A small chorus on a spring night: three tree frogs at different distances call series of 'kre-kre-kre' (5–8 a
    second, 1–4 s long), taking turns, with pauses; two pond frogs croak every few seconds."""
    p = n / SR
    tree = [{"f": rng.uniform(1150, 1350), "pulse": rng.uniform(110, 150), "rate": rng.uniform(5.5, 7.5),
             "level": 1.0, "lp": 4500},
            {"f": rng.uniform(1450, 1650), "pulse": rng.uniform(110, 150), "rate": rng.uniform(5.5, 7.5),
             "level": 0.6, "lp": 3500},
            {"f": rng.uniform(1700, 1950), "pulse": rng.uniform(100, 140), "rate": rng.uniform(5.0, 7.0),
             "level": 0.4, "lp": 3000}]
    bouts, t, last = [], 0.0, -1
    while t < p:
        k = int(rng.choice([i for i in range(len(tree)) if i != last]))
        d = rng.uniform(1.0, 4.0)
        bouts.append((t, k, d))
        last = k
        t += d + (rng.uniform(-0.5, 0.2) if rng.random() < 0.55 else rng.uniform(0.6, 2.5))
    scale, shift = p / t, rng.uniform(0, p)  # the bouts fill the loop exactly, the last pause included
    out = np.zeros(n)
    for v in tree:
        v["bus"] = np.zeros(n)
    for start, k, d in bouts:
        v = tree[k]
        every = scale / v["rate"]
        for i in range(max(1, int(d * v["rate"]))):
            at = shift + start * scale + i * every * rng.uniform(0.95, 1.05)
            place(v["bus"], min(1.0, 0.45 + 0.2 * i) * db(rng.uniform(-1.5, 0)) * kre(rng, v), at * SR)
    for v in tree:
        out += v["level"] * filt(v["bus"], lambda f: lowpass(f, v["lp"]))
    pond = np.zeros(n)
    for f0, level, (lo, hi) in ((rng.uniform(380, 480), 0.7, (3, 6)), (rng.uniform(480, 580), 0.45, (4, 8))):
        for t in spread(rng, p, lambda: rng.uniform(lo, hi)):
            place(pond, level * croak(rng, f0), t * SR)
    out += filt(pond, lambda f: band(f, 150, 2500))
    return out + 0.35 * reverb(rng, out, 0.8, 3000)


# name: (period in seconds, seed, builder)
LOOPS = {
    "birds": (36, 1101, birds),
    "crickets": (20, 1102, crickets),
    "owl": (40, 1103, owl),
    "rain": (16, 1104, rain),
    "stream": (18, 1105, stream),
    "fire": (20, 1106, fire),
    "wind": (30, 1107, wind),
    "sea": (32, 1108, sea),
    "frogs": (24, 1109, frogs),
}

# Mixes for the previews only (loop name, gain in dB); the app mixes the loops itself.
MIXES = {
    "mix-forest-day": (("birds", 0),),
    "mix-night-meadow": (("crickets", 0), ("owl", -2), ("frogs", -8)),
    "mix-rainy-village": (("rain", 0), ("wind", -10)),
    "mix-stream-day": (("stream", 0), ("birds", -4)),
    "mix-campfire-night": (("fire", 0), ("crickets", -8)),
    "mix-alps": (("wind", 0), ("stream", -8)),
    "mix-sea": (("sea", 0), ("wind", -10)),
}


# --- one-shots: short sounds that play once ---------------------------------------------------------------------------
#
# Unlike the loops they are not circles: they start at their event (or rise softly into it, the thunder) and end in
# silence. Filters and reverb work on the sound with silence round it, so nothing wraps from the end to the start.

SHOT_M = -28.0  # the loudest momentary loudness (EBU R128 M, 400 ms) of a one-shot: under a loop at its full level
THUNDER_M = -26.0  # the loudest momentary loudness of a thunder
LEAD = 0.008  # s of silence before each one-shot's sound: the codec's pre-echo of a sharp onset lands there


def put(buf, event, start):
    """Adds event into buf from sample start on; what runs past the end of buf is left out."""
    s = int(start)
    if s >= len(buf):
        return
    m = min(len(event), len(buf) - s)
    buf[s:s + m] += event[:m]


def ofilt(x, resp, pad=6000):
    """x through the zero-phase filter resp(f), with silence on both sides (not round a circle)."""
    y = filt(np.concatenate([np.zeros(pad), x, np.zeros(pad)]), resp)
    return y[pad:pad + len(x)]


def oreverb(rng, x, t60, bright, predelay=0.012):
    """The wet part of a reverb for a one-shot: x with silence after it for the tail, so the tail doesn't wrap. The
    result is longer than x by t60 + predelay."""
    return reverb(rng, np.concatenate([x, np.zeros(int((t60 + predelay) * SR) + 1)]), t60, bright, predelay)


def onset(x, seconds):
    """x with its first seconds rising from nothing (raised cosine)."""
    a = max(int(seconds * SR), 1)
    y = x.copy()
    y[:a] *= ramp(a)
    return y


def fade(x, seconds):
    """x with its last seconds falling to nothing (raised cosine), the last sample 0."""
    r = max(int(seconds * SR), 1)
    y = x.copy()
    y[-r:] *= ramp(r)[::-1]
    y[-1] = 0.0
    return y


def fit(x, seconds):
    """x cut or padded with silence to seconds."""
    n = int(seconds * SR)
    return x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])


def struck(rng, seconds, parts, beat=(0.4, 1.5), second=0.35):
    """A struck body ringing: each part (Hz, level, decay time constant in s) a sine dying away exponentially, doubled a
    fraction of a Hz apart (beat: the range in Hz) at the level second, so it beats slowly as a real bell's partials do."""
    t = np.arange(int(seconds * SR)) / SR
    out = np.zeros(len(t))
    for f, level, tau in parts:
        if f >= 9000:
            continue
        out += level * np.sin(TAU * f * t + rng.uniform(0, TAU)) * np.exp(-t / tau)
        if second:
            df = rng.uniform(*beat) * rng.choice((-1.0, 1.0))
            out += second * level * np.sin(TAU * (f + df) * t + rng.uniform(0, TAU)) * np.exp(-t / tau)
    return out


def burst(rng, seconds, lo, hi, tau, attack=0.001):
    """A short burst of band noise (lo … hi Hz) from a raised-cosine attack, dying away with time constant tau."""
    m = int(seconds * SR)
    x = ofilt(rng.standard_normal(m), lambda f: band(f, lo, hi))
    return x / rms(x) * decay(m, attack, tau)


# thunder far off: a rolling rumble

def swells(t, peaks):
    """A level over the times t: each swell (its peak in s, its rise in s, its fall's time constant in s, its level)
    rises smoothly to its peak and dies away."""
    out = np.zeros_like(t)
    for at, rise, tau, level in peaks:
        u = np.clip((t - (at - rise)) / rise, 0, 1)
        out += level * np.where(t < at, 0.5 - 0.5 * np.cos(np.pi * u), np.exp(-(t - at) / tau))
    return out


def rolls(rng, n, rate, depth):
    """A level that rolls round 1 at about rate Hz: low-passed noise, exponentiated, so now and then a roll is much
    louder than the rest."""
    g = filt(rng.standard_normal(n), lambda f: lowpass(f, rate, 2))
    x = np.exp(depth * g / np.std(g))
    return x / np.mean(x)


def thunder(rng, seconds, peaks, bright, grumble, rips, tail, crack=0.0):
    """Thunder 1–2 km off: the sound of the whole bolt arriving over seconds, the highs lost on the way. A low body (50–
    300 Hz) and a grumble (150 Hz up to bright) that roll at random (the rolls louder now and then), a few softer rips of
    band noise where it is loudest, all under the swells of peaks and into a long, dark reverb. crack: a brighter
    first swell. Nothing starts suddenly: every swell rises over a quarter second or more."""
    n = int(seconds * SR)
    t = np.arange(n) / SR
    level = swells(t, peaks)
    body = noise(rng, n, -1.0, lambda f: highpass(f, 50, 4) * lowpass(f, 300, 2)) * rolls(rng, n, 2.5, 0.7)
    grum = noise(rng, n, -0.5, lambda f: band(f, 150, bright, 2)) * rolls(rng, n, 7.0, 0.9)
    # the body half as loud as the grumble: a phone's speaker plays the grumble, and the loudness is set on the whole
    x = level * (0.5 * body / rms(body) + grumble * grum / rms(grum))
    if crack:
        at, rise, tau, _ = peaks[0]
        first = swells(t, [(at, rise, tau * 0.6, 1.0)])
        rip = noise(rng, n, -0.3, lambda f: band(f, 300, 1600, 2)) * rolls(rng, n, 14.0, 0.6)
        x += crack * first * rip / rms(rip)
    rip_buf = np.zeros(n)
    for at in np.sort(rng.uniform(0.3, 0.75 * seconds, int(rips))):
        d = rng.uniform(0.06, 0.2)
        put(rip_buf, db(rng.uniform(-6, 0)) * level[int(at * SR)] * burst(rng, d + 0.05, 250, 1400, d / 3, 0.015),
            at * SR)
    x += 0.8 * rip_buf
    y = np.concatenate([x, np.zeros(int(tail * SR) + 1)])
    y += 0.5 * oreverb(rng, x, tail, 1100)[:len(y)]
    y = ofilt(y, lambda f: highpass(f, 45, 4) * lowpass(f, 2500, 2))
    return fade(fit(y, seconds), 0.8)


def thunder_1(rng):
    """A long roll with three swells."""
    return thunder(rng, 6.5, [(0.55, 0.5, 0.7, 0.8), (1.7, 0.6, 1.0, 1.0), (3.2, 0.7, 1.2, 0.65)], 850, 0.9, 5, 2.0)


def thunder_2(rng):
    """A softer crack, then the roll."""
    return thunder(rng, 5.5, [(0.32, 0.3, 0.45, 1.0), (1.1, 0.5, 1.1, 0.7), (2.3, 0.6, 0.9, 0.4)], 1100, 1.0, 6, 1.8,
                   crack=0.6)


def thunder_3(rng):
    """Deep and short."""
    return thunder(rng, 4.2, [(0.5, 0.45, 0.6, 1.0), (1.25, 0.5, 0.8, 0.55)], 650, 0.7, 2, 1.6)


# bells

BELL_PRIME = 350.0  # Hz: a village church bell
# the partials of a bell tuned as church bells are, by their ratio to the prime: (ratio, level, decay time constant s):
# the hum an octave below the prime, the prime, the minor third (the tierce) that gives a bell its sound, the quint,
# the nominal an octave above, and the upper partials that die away first; last, the strike's short clang
BELL = (
    (0.5, 0.45, 1.8),
    (1.0, 0.55, 1.2),
    (1.2, 0.7, 1.0),
    (1.5, 0.25, 0.7),
    (2.0, 1.0, 0.8),
    (2.5, 0.3, 0.45),
    (2.67, 0.35, 0.4),
    (3.0, 0.4, 0.3),
    (4.0, 0.25, 0.18),
    (5.4, 0.2, 0.06),
    (6.3, 0.15, 0.05),
    (7.6, 0.1, 0.04),
)


def bell_strike(rng, seconds):
    """One strike of the bell, dry: its partials from a 4 ms rise, and the clapper's soft thud under them."""
    parts = [(r * BELL_PRIME, level, tau) for r, level, tau in BELL]
    ring = onset(struck(rng, seconds, parts, beat=(0.3, 1.4)), 0.004)
    thud = burst(rng, 0.12, 120, 900, 0.012, 0.002)
    put(ring, 0.25 * np.max(np.abs(ring)) / np.max(np.abs(thud)) * thud, 0)
    return ring


def bell(rng):
    """A village church bell struck once, near: 2.8 s, the tail faded out."""
    x = bell_strike(rng, 2.8)
    return fade(x + 0.15 * oreverb(rng, x, 0.6, 5000)[:len(x)], 0.9)


def bell_far(rng):
    """The same bell heard from far away over the land: dull (low-passed at 1.8 kHz), a softer onset (20 ms), in a
    long reverb."""
    x = onset(ofilt(bell_strike(np.random.default_rng(SHOTS["bell"][0]), 3.0), lambda f: lowpass(f, 1800, 2)), 0.02)
    y = 0.6 * x + 0.8 * oreverb(rng, x, 1.2, 1500, 0.03)[:len(x)]
    return fade(y, 0.9)


def anvil(rng):
    """A hammer's tap on an anvil: a bright steel ring (inharmonic partials 0.9–4.5 kHz, beating fast), dying away over
    about a second, and the dull tap of the hammer under it."""
    f0 = 1650.0
    parts = [(f0 * r, level, tau) for r, level, tau in (
        (1.0, 1.0, 0.17), (1.37, 0.7, 0.14), (1.83, 0.8, 0.12), (2.21, 0.5, 0.1), (2.71, 0.45, 0.08),
        (0.54, 0.35, 0.05))]
    ring = onset(struck(rng, 1.3, parts, beat=(2.0, 5.0), second=0.3), 0.0015)
    tap = burst(rng, 0.1, 150, 1500, 0.01, 0.0015)
    t = np.arange(int(0.1 * SR)) / SR
    tap = tap + 0.8 * np.sin(TAU * 300 * t) * decay(len(t), 0.0015, 0.02)
    put(ring, 0.5 * np.max(np.abs(ring)) / np.max(np.abs(tap)) * tap, 0)
    return fade(ring, 0.4)


def cowbell(rng):
    """One clank of an alpine cowbell (riveted sheet iron): a hollow, clanky set of partials (300 Hz – 2.5 kHz) dying
    fast, the clapper bouncing back for a softer second touch 35 ms later, and a rattle of the rivets."""
    parts = [(f, level, tau) for f, level, tau in (
        (300, 0.5, 0.05), (480, 1.0, 0.12), (730, 0.8, 0.09), (1170, 0.7, 0.07), (1560, 0.5, 0.06),
        (2080, 0.4, 0.05), (2450, 0.3, 0.04))]
    n = int(0.6 * SR)
    hit = onset(struck(rng, 0.6, parts, beat=(3.0, 9.0), second=0.4), 0.001)
    x = hit.copy()
    put(x, 0.35 * onset(struck(rng, 0.6, parts, beat=(3.0, 9.0), second=0.4), 0.001), 0.035 * SR)
    clack = burst(rng, 0.05, 700, 3500, 0.004)
    put(x, 0.4 * np.max(np.abs(hit)) / np.max(np.abs(clack)) * clack, 0)
    # the rivets: the ring's level buzzing a little
    buzz = filt(rng.standard_normal(n), lambda f: band(f, 60, 400))
    return fade(x * (1 + 0.25 * buzz / np.max(np.abs(buzz))), 0.2)


def owl_hoot(rng):
    """The tawny owl's call, short: a 'hu', a pause, two quick 'hu's and a long wavering 'hooo', with a little night
    reverb; 1.6 s."""
    f = 410.0
    parts = [hoot(rng, 0.13, [(0, f * 0.98), (1, f * 0.96)], level=0.6), gap(0.2)]
    for i in range(2):
        parts += [hoot(rng, 0.075, [(0, f * 0.97), (1, f)], level=0.6 + 0.1 * i), gap(0.045)]
    parts.append(hoot(rng, 0.78, [(0, f), (0.2, f * 1.05), (1, f * 0.92)], level=0.95, waver=0.03))
    dry = np.concatenate(parts)
    wet = oreverb(rng, dry, 0.7, 2500)
    return fade(fit(dry, 1.6) + 0.3 * fit(wet, 1.6), 0.25)


def whistle(rng):
    """An alpine marmot's alarm: one sharp high whistle (2.35 → 2.85 → 2.5 kHz, 0.3 s) from a 5 ms attack, with a little
    breath in it, and two fainter echoes off the slopes (0.35 s and 0.62 s later)."""
    d = 0.3
    m = int(d * SR)
    fr = curve([(0, 2350), (0.04, 2850), (0.12, 2800), (d, 2500)], m, log=True)
    amp = envelope(m, 0.005, 0.04) * curve([(0, 0.9), (0.03, 1.0), (d, 0.7)], m)
    tone = sine(fr, rng.uniform(0, TAU), ((2, 0.08),))
    breath = ofilt(rng.standard_normal(m), lambda f: band(f, 2000, 3200, 3))
    call = amp * (tone + db(-22) * breath / rms(breath) * np.sqrt(0.5))
    x = np.zeros(int(1.1 * SR))
    put(x, call, 0)
    echo = ofilt(np.concatenate([call, np.zeros(2000)]), lambda f: lowpass(f, 2200, 2))
    put(x, 0.22 * echo, 0.35 * SR)
    put(x, 0.09 * echo, 0.62 * SR)
    x += 0.2 * oreverb(rng, x, 0.35, 4000)[:len(x)]
    return fade(x, 0.2)


def lid(rng):
    """A pot's lid rattling on its rim as the water boils: nine small clinks of metal every 0.125 s (from 0 to 1 s),
    each a tiny inharmonic tick with the lid's lower tock under it, and a soft puff of steam."""
    n = int(1.3 * SR)
    x = np.zeros(n)
    m = int(0.03 * SR)
    t = np.arange(m) / SR
    for k in range(9):
        ev = np.zeros(m)
        for f, level, tau in ((2400, 0.6, 0.005), (3100, 1.0, 0.004), (4300, 0.7, 0.003), (5600, 0.5, 0.0025)):
            ev += level * np.sin(TAU * f * rng.uniform(0.95, 1.05) * t + rng.uniform(0, TAU)) * np.exp(-t / tau)
        ev += 0.5 * np.sin(TAU * rng.uniform(500, 750) * t + rng.uniform(0, TAU)) * np.exp(-t / 0.01)
        put(x, db(rng.uniform(-3, 3)) * onset(ev, 0.0006), 0.125 * k * SR)
    steam = ofilt(rng.standard_normal(n), lambda f: band(f, 2000, 6000, 3))
    steam *= curve([(0, 0), (0.1, 0), (0.35, 1), (0.8, 0.6), (1.2, 0), (1.3, 0)], n)
    x += db(-24) * steam / rms(steam)
    return fade(x, 0.08)


def purr(rng):
    """A cat purring, two breaths (in, 1.1 s; out, 1.3 s, a little lower and louder): trains of pulses 26 and 23.5 times
    a second, each a short burst of noise and two rings of the throat (about 160 and 420 Hz), through its band (130 Hz –
    1.1 kHz, so a phone's speaker keeps the pulses), with faint air."""
    n = int(2.6 * SR)
    x = np.zeros(n)
    m = int(0.035 * SR)
    t = np.arange(m) / SR
    for start, d, rate, level in ((0.0, 1.1, 26.0, 0.7), (1.18, 1.32, 23.5, 1.0)):
        u = 0.0
        while u < d:
            a = level * min(1.0, u / 0.15, (d - u) / 0.2) * db(rng.normal(0, 1.5))
            ev = 1.3 * rng.standard_normal(m) * np.exp(-t / 0.006)
            ev += 0.8 * np.sin(TAU * rng.uniform(140, 170) * t) * np.exp(-t / 0.009)
            ev += 1.2 * np.sin(TAU * rng.uniform(380, 440) * t) * np.exp(-t / 0.006)
            put(x, max(a, 0.0) * onset(ev, 0.002), (start + u) * SR)
            u += rng.uniform(0.97, 1.03) / rate
    x = ofilt(x, lambda f: band(f, 130, 1100, 2))
    air = ofilt(rng.standard_normal(n), lambda f: band(f, 300, 2500, 2))
    x += 0.08 * rms(x) / rms(air) * air * np.abs(x) / (np.max(np.abs(x)) + 1e-12) * 4
    return fade(onset(x, 0.02), 0.1)


def crackles(rng, n, times):
    """The fire loop's crackles for a one-shot: a group of tiny clicks with a short ring at each of times (s)."""
    out = np.zeros(n)
    for at in times:
        t = at * SR
        base = db(rng.uniform(-10, 0))
        for _ in range(min(rng.geometric(0.28), 8)):
            m = max(int(rng.uniform(0.0002, 0.002) * SR), 4)
            click = rng.standard_normal(m) * np.hanning(m + 2)[1:-1]
            click /= np.max(np.abs(click))
            rm = int(rng.uniform(0.004, 0.012) * SR)
            ring = 0.4 * sine(np.full(rm, rng.uniform(1500, 4500)), rng.uniform(0, TAU)) * decay(
                rm, 0.0005, rng.uniform(0.001, 0.003))
            level = base * db(rng.uniform(-6, 0))
            put(out, level * click, t)
            put(out, level * ring, t)
            t += (0.001 + rng.exponential(0.008)) * SR
    return ofilt(out, lambda f: band(f, 1200, 7500))


def whoosh(rng):
    """A fire catching and flaring up: a soft 'fwoomp' (a puff of low air, a swell of noise brightening to 2 kHz and
    darkening again), rising over 80 ms, and a few crackles in its second half; 1.2 s."""
    d = 1.2
    n = int(d * SR)
    cuts = np.geomspace(300, 2000, 7)
    cut = curve([(0, 300), (0.3, 2000), (0.55, 1400), (d, 500)], n, log=True)
    pos = np.log(cut / cuts[0]) / np.log(cuts[-1] / cuts[0]) * (len(cuts) - 1)
    flame = moving(noise(rng, n, -0.3, lambda f: highpass(f, 120, 2)), cuts, pos, lambda f, c: lowpass(f, c, 2))
    flame *= curve([(0, 0), (0.08, 0.8), (0.3, 1.0), (0.6, 0.55), (1.0, 0.2), (d, 0)], n)
    puff = noise(rng, n, -1.0, lambda f: highpass(f, 70, 4) * lowpass(f, 220, 2)) * curve(
        [(0, 0), (0.06, 1), (0.25, 0.3), (0.5, 0), (d, 0)], n)
    cr = crackles(rng, n, np.sort(rng.uniform(0.5, 1.05, 5)))
    x = flame / rms(flame) + 0.6 * puff / rms(puff) + 0.8 * cr / np.max(np.abs(cr))
    return fade(x, 0.15)


# name: (seed, builder, the loudest momentary loudness in LUFS)
SHOTS = {
    "thunder-1": (2101, thunder_1, THUNDER_M),
    "thunder-2": (2102, thunder_2, THUNDER_M),
    "thunder-3": (2103, thunder_3, THUNDER_M),
    "bell": (2111, bell, SHOT_M),
    "bell-far": (2112, bell_far, SHOT_M),
    "anvil": (2113, anvil, SHOT_M),
    "cowbell": (2114, cowbell, SHOT_M),
    "hoot": (2115, owl_hoot, SHOT_M),
    "whistle": (2116, whistle, SHOT_M),
    "lid": (2117, lid, SHOT_M),
    "purr": (2118, purr, SHOT_M),
    "whoosh": (2119, whoosh, SHOT_M),
}


# --- files, ffmpeg and loudness ---------------------------------------------------------------------------------------

def write_wav(path, x):
    """A mono 24-bit WAV at SR."""
    pcm = np.clip(np.round(x * 8388607), -8388608, 8388607).astype("<i4")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(3)
        w.setframerate(SR)
        w.writeframes(pcm.view(np.uint8).reshape(-1, 4)[:, :3].tobytes())


def ffmpeg(*args, text=False):
    return subprocess.run(["ffmpeg", "-hide_banner", *args], capture_output=True, text=text, check=True)


def measure(path):
    """Integrated loudness (LUFS) and true peak (dBTP) of a sound file, by ffmpeg's ebur128 filter."""
    err = ffmpeg("-nostats", "-i", str(path), "-af", "ebur128=peak=true", "-f", "null", "-", text=True).stderr
    summary = err[err.rfind("Summary:"):]
    lufs = float(re.search(r"I:\s+(-?[\d.]+) LUFS", summary).group(1))
    peak = float(re.search(r"Peak:\s+(-?[\d.]+|-inf) dBFS", summary).group(1))
    return lufs, peak


def repeated(loop, seconds=60):
    """The loop played again and again for `seconds` (at least twice)."""
    reps = max(2, ceil(seconds * SR / len(loop)))
    return np.tile(loop, reps)


def loudness(loop, tmp):
    path = tmp / "measure.wav"
    write_wav(path, repeated(loop))
    return measure(path)


def decode(ogg):
    out = ffmpeg("-loglevel", "error", "-i", str(ogg), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-").stdout
    return np.frombuffer(out, "<f4").astype(float)


def make(name, out_dir, tmp):
    """Builds one loop and writes its Ogg file. Returns the decoded loop, its LUFS, its true peak and the seam error."""
    period, seed, build = LOOPS[name]
    n = period * SR
    x = finish(build(np.random.default_rng(seed), n))
    x *= 0.5 / np.max(np.abs(x))
    x *= db(LUFS - loudness(x, tmp)[0])
    wav, ogg = tmp / f"{name}.wav", out_dir / f"{name}.ogg"
    for _ in range(3):  # the codec moves the loudness a little: measure the decoded file and correct
        write_wav(wav, np.concatenate([x[-PAD:], x, x[:PAD]]))
        with wave.open(str(wav)) as w:
            frames = w.readframes(w.getnframes())
        head, tail = frames[:3 * 2 * PAD], frames[3 * n:3 * (n + 2 * PAD)]
        if head != tail:
            raise SystemExit(f"{name}: the WAV does not repeat every {n} samples")
        ffmpeg("-loglevel", "error", "-y", "-i", str(wav), "-map_metadata", "-1", "-fflags", "+bitexact",
               "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", str(QUALITY), str(ogg))
        decoded = decode(ogg)
        loop = decoded[PAD:PAD + n]
        lufs, peak = loudness(loop, tmp)
        if abs(lufs - LUFS) <= 0.1:
            break
        x *= db(LUFS - lufs)
    # The seam: the decoded samples round the loop's end (PAD + n) against the same samples round its start (PAD),
    # relative to the loop's RMS. Only the codec's error is left, so it is well below 1.
    w = PAD // 2
    seam = rms(decoded[PAD + n - w:PAD + n + w] - decoded[PAD - w:PAD + w]) / rms(loop)
    return loop, lufs, peak, seam


def momentary(path):
    """The loudest momentary loudness (LUFS, EBU R128 M: 400 ms, every 100 ms) and the true peak (dBTP) of a sound
    file, by ffmpeg's ebur128 filter."""
    err = ffmpeg("-nostats", "-i", str(path), "-af", "ebur128=peak=true", "-f", "null", "-", text=True).stderr
    most = max(float(v) for v in re.findall(r"M:\s*(-?[\d.]+)", err))
    summary = err[err.rfind("Summary:"):]
    peak = float(re.search(r"Peak:\s+(-?[\d.]+|-inf) dBFS", summary).group(1))
    return most, peak


def shot_loudness(x, tmp):
    """momentary() of a one-shot, with half a second of silence round it so every window it touches is measured."""
    path = tmp / "measure-shot.wav"
    pad = np.zeros(SR // 2)
    write_wav(path, np.concatenate([pad, x, pad]))
    return momentary(path)


def make_shot(name, out_dir, tmp):
    """Builds one one-shot and writes its Ogg file. Returns the decoded sound, its loudest momentary loudness and its
    true peak."""
    seed, build, target = SHOTS[name]
    x = build(np.random.default_rng(seed))
    x = x - np.mean(x) * np.hanning(len(x)) / np.mean(np.hanning(len(x)))  # no DC, the ends untouched
    # a little silence first: the codec's pre-echo of a sharp onset lands there, and the file starts at 0
    x = np.concatenate([np.zeros(int(LEAD * SR)), x])
    x *= 0.5 / np.max(np.abs(x))
    x *= db(target - shot_loudness(x, tmp)[0])
    wav, ogg = tmp / f"{name}.wav", out_dir / f"{name}.ogg"
    for _ in range(3):  # the codec moves the loudness a little: measure the decoded file and correct
        write_wav(wav, x)
        ffmpeg("-loglevel", "error", "-y", "-i", str(wav), "-map_metadata", "-1", "-fflags", "+bitexact",
               "-flags:a", "+bitexact", "-c:a", "libvorbis", "-q:a", str(QUALITY), str(ogg))
        decoded = decode(ogg)
        most, peak = shot_loudness(decoded, tmp)
        if abs(most - target) <= 0.1:
            break
        x *= db(target - most)
    return decoded, most, peak


def rate(x, r):
    """x played r times as fast (linear interpolation, as the app does): higher and shorter for r > 1."""
    at = np.arange(0, len(x) - 1, r)
    return np.interp(at, np.arange(len(x)), x)


def loop_of(name, out_dir):
    """A loop decoded from its Ogg file: one period, cut out of the middle as the app does."""
    n = LOOPS[name][0] * SR
    return decode(out_dir / f"{name}.ogg")[PAD:PAD + n]


def bed(out_dir, parts, seconds, rng):
    """seconds of loops (name, level) together, each from a random point of its loop and at its trim in the app."""
    n = int(seconds * SR)
    out = np.zeros(n)
    for name, level in parts:
        loop = loop_of(name, out_dir)
        start = rng.integers(len(loop))
        out += level * TRIM[name] * np.tile(np.roll(loop, -start), n // len(loop) + 1)[:n]
    return out


# the loops' trims in the app (companion/android/…/game/ambient/Soundscape.kt, Layer): for the previews
TRIM = {"birds": 0.8, "crickets": 0.55, "owl": 0.7, "frogs": 0.55, "rain": 0.9, "stream": 0.8, "fire": 1.0, "wind": 0.9,
        "sea": 0.9}


def storm(out_dir, flashes, rng, seconds, gain):
    """The thunder after each flash (s), as the app plays it: 2.5–6 s after it, the farther the later, quieter (1 −
    0.45 × how far), lower and slower (rate 1.06 − 0.16 × how far), one of the three at random; all times gain."""
    n = int(seconds * SR)
    out = np.zeros(n)
    thunders = [decode(out_dir / f"thunder-{k}.ogg") for k in (1, 2, 3)]
    heard = []
    for f in flashes:
        far = rng.uniform()
        at = f + 2.5 + 3.5 * far
        k = rng.integers(3)
        put(out, gain * (1 - 0.45 * far) * rate(thunders[k], 1.06 - 0.16 * far), at * SR)
        heard.append((f, at, k + 1, far))
    return out, heard


def scene_flashes(rng, seconds):
    """A scene's flashes (s): the n-th begins at n × 7.3 + 2.5 × u (u at random 0..1)."""
    out, k = [], 0
    while k * 7.3 < seconds:
        out.append(k * 7.3 + 2.5 * rng.uniform())
        k += 1
    return [f for f in out if f < seconds]


def mp3(path, x, tmp, lufs=-23.0):
    """x as an MP3 (128 kb/s) at lufs integrated, or quieter if its peak would pass −1 dBFS. Returns its loudness."""
    wav = tmp / "preview.wav"
    write_wav(wav, 0.1 * x / np.max(np.abs(x)))
    y = 0.1 * x / np.max(np.abs(x)) * db(lufs - measure(wav)[0])
    y *= min(1.0, db(-1) / np.max(np.abs(y)))
    write_wav(wav, y)
    ffmpeg("-loglevel", "error", "-y", "-i", str(wav), "-c:a", "libmp3lame", "-b:a", "128k", str(path))
    return measure(wav)[0]


def preview_shots(out_dir, preview_dir, tmp):
    """60-second MP3s of the thunder in a storm (in the open, in a room, over the village) and of the one-shots in their
    scenes, mixed at the app's levels relative to each other, each set to −23 LUFS to listen to (the app plays all
    of it about 17 dB quieter by default)."""
    preview_dir.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(3001)
    minute = 60
    flashes = scene_flashes(rng, minute)
    thunder_open, heard = storm(out_dir, flashes, np.random.default_rng(3002), minute, 1.0)
    rain = bed(out_dir, [("rain", 0.95), ("wind", 0.6)], minute, rng)
    lines = ["# a scene's flashes (s) and the thunder after each: when it begins (s), which one, how far (0..1)"]
    lines += [f"flash {f:6.2f}  thunder {a:6.2f}  thunder-{k}  far {d:.2f}" for f, a, k, d in heard]
    (preview_dir / "storm-open-flashes.txt").write_text("\n".join(lines) + "\n")
    made = {"storm-open.mp3": mp3(preview_dir / "storm-open.mp3", rain + thunder_open, tmp)}
    fire = bed(out_dir, [("fire", 0.25)], minute, rng)
    made["storm-kitchen.mp3"] = mp3(preview_dir / "storm-kitchen.mp3", 0.4 * rain + 0.5 * thunder_open + fire, tmp)
    village, vheard = storm(out_dir, [k * 5.3 for k in range(12)], np.random.default_rng(3003), minute, 1.0)
    vrain = bed(out_dir, [("rain", 0.95), ("wind", 0.7)], minute, rng)
    made["village-storm.mp3"] = mp3(preview_dir / "village-storm.mp3", vrain + village, tmp)
    lines = ["# the village's storm: a flash every 5.3 s; when its thunder begins (s), which one, how far (0..1)"]
    lines += [f"flash {f:6.2f}  thunder {a:6.2f}  thunder-{k}  far {d:.2f}" for f, a, k, d in vheard]
    (preview_dir / "village-storm-flashes.txt").write_text("\n".join(lines) + "\n")

    shots = {name: decode(out_dir / f"{name}.ogg") for name in SHOTS}
    n = minute * SR
    mix = np.zeros(n)
    cues = []
    day, alps, kitchen = [("birds", 0.5)], [("stream", 0.6), ("wind", 0.45)], [("fire", 0.25)]
    # (start s, what, its scene's loops, the one-shots: (name, after s, gain, rate))
    plan = [
        (0, "the square: the tower's bell tapped", day,
         [("bell", 0.29 + 0.571 * i, g, 1.0) for i, g in enumerate((1, 1, 0.95, 0.8, 0.5, 0.2))]),
        (5, "the church: the rope pulled", [],
         [("bell", 0.374 + 0.748 * i, g, 1.0) for i, g in enumerate((0.8, 1, 1, 0.6))]),
        (10, "the field: the church bell far off", day, [("bell-far", 0.3 + 0.909 * i, 0.7, 1.0) for i in range(4)]),
        (15, "the market: the hand bell", day, [("bell", 0.26 + 0.524 * i, 0.6, 1.6) for i in range(5)]),
        (20, "the smithy: the anvil tapped once, then twice", [("fire", 0.45)],
         [("anvil", 0.2, 1, 1.0), ("anvil", 2.4, 1, 1.0), ("anvil", 3.05, 1, 1.0)]),
        (25, "the alps: the cow shakes its head, its bell clanging", alps,
         [("cowbell", 0.2 + 0.224 * i, 0.9 - 0.7 * max(0.0, (0.224 * i - 1.2) / 1.0), (0.95, 1.05)[i % 2])
          for i in range(10)]),
        (30, "the alps: the herd's bells (a dialog's cowbells)", alps,
         [("cowbell", 0.2 + t, g, r) for t, g, r in ((0.0, 0.7, 1.0), (0.31, 0.45, 0.92), (0.55, 0.6, 1.08),
                                                       (0.98, 0.4, 0.95), (1.4, 0.55, 1.04))]),
        (35, "the forest at night: the owl tapped, hooting", [("crickets", 0.35)], [("hoot", 0.6, 1, 1.0)]),
        (40, "the alps: the marmot whistles", alps, [("whistle", 0.3, 1, 1.0)]),
        (45, "the kitchen: the pot's lid rattles", kitchen, [("lid", 0.2, 1, 1.0)]),
        (50, "the kitchen: the cat purrs", kitchen, [("purr", 0.2, 1, 1.0)]),
        (55, "the kitchen: the oven's fire catches", kitchen, [("whoosh", 0.3, 1, 1.0)]),
    ]
    for start, what, loops, plays in plan:
        seg = bed(out_dir, loops, 5.3, rng) if loops else np.zeros(int(5.3 * SR))
        seg = fade(onset(seg, 0.3), 0.3)
        put(mix, seg, start * SR)
        for name, after, g, r in plays:
            put(mix, g * rate(shots[name], r), (start + after) * SR)
            cues.append(f"{start + after:6.2f}  {name:<9} gain {g:.2f}  rate {r:.2f}  ({what})")
    (preview_dir / "oneshots-cues.txt").write_text("# when each one-shot begins (s), at what gain and rate\n"
                                                    + "\n".join(cues) + "\n")
    made["oneshots.mp3"] = mp3(preview_dir / "oneshots.mp3", mix, tmp)
    for name, lufs in made.items():
        print(f"preview {name}: {lufs:.1f} LUFS")


def previews(loops, preview_dir, tmp):
    preview_dir.mkdir(parents=True, exist_ok=True)
    minute = 60 * SR
    for name, loop in loops.items():
        write_wav(preview_dir / f"{name}.wav", repeated(loop)[:minute])
    for mix, parts in MIXES.items():
        if all(name in loops for name, _ in parts):
            y = sum(db(gain) * repeated(loops[name])[:minute] for name, gain in parts)
            path = preview_dir / f"{mix}.wav"
            write_wav(path, y)
            write_wav(path, y * db(LUFS - measure(path)[0]))


def main():
    default_out = Path(__file__).resolve().parent.parent / "app/src/main/assets/ambient"
    ap = argparse.ArgumentParser(description="Synthesize the ambient loops (Ogg Vorbis, mono, 24 kHz).")
    ap.add_argument("out_dir", nargs="?", type=Path, default=default_out, help="where the .ogg files go")
    ap.add_argument("--preview", type=Path, metavar="DIR", help="also write 60-second WAVs to listen to")
    ap.add_argument("--only", nargs="+", choices=list(LOOPS), metavar="NAME", help="make only these loops")
    ap.add_argument("--shots", action="store_true", help="make only the one-shots, no loops")
    ap.add_argument("--only-shots", nargs="+", choices=list(SHOTS), metavar="NAME", help="make only these one-shots")
    ap.add_argument("--preview-shots", type=Path, metavar="DIR",
                    help="also write 60-second MP3s of the thunder and the one-shots in their scenes")
    args = ap.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    only_shots = args.shots or args.only_shots
    names = [] if only_shots else args.only or list(LOOPS)
    shot_names = [] if args.only else args.only_shots or list(SHOTS)
    loops, bad = {}, []
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        if names:
            print(f"{'loop':<10}{'period':>7}{'size':>10}{'LUFS':>8}{'peak':>8}{'seam':>7}")
        for name in names:
            loop, lufs, peak, seam = make(name, args.out_dir, tmp)
            loops[name] = loop
            size = (args.out_dir / f"{name}.ogg").stat().st_size
            print(f"{name:<10}{LOOPS[name][0]:>6}s{size / 1024:>8.1f}kB{lufs:>8.1f}{peak:>8.1f}{seam:>7.3f}")
            if abs(lufs - LUFS) > 0.5 or peak > MAX_PEAK:
                bad.append(name)
        if shot_names:
            print(f"{'one-shot':<10}{'length':>7}{'size':>10}{'max M':>8}{'peak':>8}")
        for name in shot_names:
            sound, most, peak = make_shot(name, args.out_dir, tmp)
            size = (args.out_dir / f"{name}.ogg").stat().st_size
            print(f"{name:<10}{len(sound) / SR:>6.2f}s{size / 1024:>8.1f}kB{most:>8.1f}{peak:>8.1f}")
            if abs(most - SHOTS[name][2]) > 0.5 or peak > MAX_PEAK:
                bad.append(name)
        if args.preview:
            previews(loops, args.preview, tmp)
        if args.preview_shots:
            preview_shots(args.out_dir, args.preview_shots, tmp)
    for kind, group in (("loops", LOOPS), ("one-shots", SHOTS)):
        files = [args.out_dir / f"{name}.ogg" for name in group]
        total = sum(f.stat().st_size for f in files if f.exists())
        print(f"all {kind}: {total / 1024:.1f} kB (Vorbis -q:a {QUALITY})")
    if bad:
        sys.exit(f"loudness or true peak out of range: {', '.join(bad)}")


if __name__ == "__main__":
    main()
