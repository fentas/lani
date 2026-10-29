#!/usr/bin/env python3
"""The talk's take gate (TalkMic + SilenceGate) on the benchmark's takes, as the app feeds it: MediaRecorder's max
amplitude every 100 ms (0..32767). Each teo take at several speech levels (the whole take scaled: speech and room
together), today's gate, the variants tried and the one built (Tracking). Counts the takes that end without being sent ("I didn't catch
that"), those that wait for the 10 s give-up, and how long after the last word the take ends.

  gate.py <audio manifest> <out.md>      (the manifest prep.py wrote)
"""
import json
import subprocess
import sys

import numpy as np

SR = 16000


def decode(path):
    r = subprocess.run(["/usr/bin/ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", path, "-ac", "1", "-ar", "16000", "-f", "f32le", "pipe:1"], capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32)


def ticks(x):
    n = len(x) // 1600
    return (np.abs(x[: n * 1600]).reshape(n, 1600).max(axis=1) * 32767).astype(int)


class Tracking:
    """The gate as built (data/Stt.kt SilenceGate, room-aware): the room is the lowest median of three ticks so far;
    speech is [over] times it (400 at least, [speech] at most) and starts the take at once; quiet is under half the
    speech level, a sixth of the peak, or twice the room once the room is credibly below the voice (a quarter of the
    peak)."""

    def __init__(self, speech=1500, quiet_ms=3000, give_up_ms=10000, over=2.5, least=400):
        self.speech, self.quiet_ms, self.give_up_ms, self.over, self.least = speech, quiet_ms, give_up_ms, over, least
        self.started = False
        self.peak = 0
        self.first = -1
        self.quiet_since = -1
        self.last = []
        self.room = None

    def threshold(self):
        if self.room is None:
            return self.speech
        return int(min(self.speech, max(self.least, self.room * self.over)))

    def feed(self, a, t):
        if self.first < 0:
            self.first = t
        self.last = (self.last + [a])[-3:]
        if len(self.last) == 3:
            m = sorted(self.last)[1]
            self.room = m if self.room is None else min(self.room, m)
        sp = self.threshold()
        self.peak = max(self.peak, a)
        if not self.started:
            if a >= sp:
                self.started = True
            return not self.started and t - self.first >= self.give_up_ms
        room = self.room * 2 if self.room is not None and self.room * 4 <= self.peak else 0
        if a < max(sp // 2, self.peak // 6, room):
            if self.quiet_since < 0:
                self.quiet_since = t
            return t - self.quiet_since >= self.quiet_ms
        self.quiet_since = -1
        return False


class Gate:
    """SilenceGate (data/Stt.kt) with TalkMic's settings as they were (speech 1500, 150 ms to start); the variants tried:
    [start_ms] 0 starts at once, [adaptive] makes speech that many times the room of the first 300 ms ([floor] at
    least, [speech] at most). [Tracking] is the one built."""

    def __init__(self, speech=1500, start_ms=150, quiet_ms=3000, give_up_ms=10000, adaptive=None, floor=500, quiet_peak=6):
        self.speech, self.start_ms, self.quiet_ms, self.give_up_ms, self.adaptive = speech, start_ms, quiet_ms, give_up_ms, adaptive
        self.min, self.quiet_peak = floor, quiet_peak
        self.started = False
        self.peak = 0
        self.first = -1
        self.loud_since = -1
        self.quiet_since = -1
        self.room = []

    def threshold(self):
        if not self.adaptive or len(self.room) < 3:
            return self.speech
        floor = sorted(self.room[:3])[1]
        return int(min(self.speech, max(self.min, floor * self.adaptive)))

    def feed(self, a, t):
        if self.first < 0:
            self.first = t
        if len(self.room) < 3:
            self.room.append(a)
        sp = self.threshold()
        self.peak = max(self.peak, a)
        if not self.started:
            if a >= sp:
                if self.loud_since < 0:
                    self.loud_since = t
                if t - self.loud_since >= self.start_ms:
                    self.started = True
            else:
                self.loud_since = -1
            return not self.started and t - self.first >= self.give_up_ms
        if a < max(sp // 2, self.peak // self.quiet_peak, (sorted(self.room[:3])[1] * 2 if self.adaptive and len(self.room) >= 3 else 0)):
            if self.quiet_since < 0:
                self.quiet_since = t
            return t - self.quiet_since >= self.quiet_ms
        self.quiet_since = -1
        return False


def take(tk, gate, extra_quiet=40, room=None):
    """The take fed tick by tick, then the room goes on (as after the last word: [extra_quiet] more ticks of the take's
    last second). Returns (sent, ended_ms, gave_up)."""
    g = gate()
    tail = room if room is not None else tk[:2]  # the room before the first word (0.25-0.8 s)
    seq = list(tk) + [int(tail[i % len(tail)]) for i in range(extra_quiet + 100)]
    loud = 0
    t = 0
    for a in seq:
        t += 100
        if a >= g.threshold():
            loud += 1
        if g.feed(a, t):
            return loud >= 2, t, not g.started
    return loud >= 2, t, not g.started


GATES = {
    "today (1500, 150 ms)": lambda: Gate(),
    "1500, starts at once": lambda: Gate(start_ms=0),
    "room +8 dB (400..1500), at once": lambda: Gate(start_ms=0, adaptive=2.5, floor=400),
    "room +12 dB (400..1500), at once": lambda: Gate(start_ms=0, adaptive=4.0, floor=400),
    "tracking room +8 dB (built)": lambda: Tracking(),
}


def main():
    m = json.load(open(sys.argv[1]))
    takes = [x for x in m if x["cond"] == "teo"][::2]
    room = [x for x in m if x["cond"] == "noise" and "-room-" in x["id"]]
    audio = [(decode(x["audio"]), x) for x in takes]
    noise = [decode(x["audio"]) for x in room]
    lines = ["| Speech peak | Gate | Takes | Not sent when it ends by itself (\"I didn't catch that\") | Cut before the last word | Ends within 4.5 s of the last word | Waits for the 10 s give-up | Runs on (> 6 s after) |", "|---|---|---|---|---|---|---|---|"]
    for level in (-34, -30, -26, -22, -18, "-22, speech from the first tick"):
        for name, gate in GATES.items():
            lost = gave = ontime = long = cut = 0
            for x, info in audio:
                lv = level if isinstance(level, int) else -22
                s = x / (np.abs(x).max() + 1e-9) * 10 ** (lv / 20)
                room = ticks(s)[:2]
                if not isinstance(level, int):  # the room before the first word left out
                    e = np.abs(s) > 0.3 * np.abs(s).max()
                    s = s[max(0, int(np.argmax(e)) - 800):]
                tk = ticks(s)
                sent, ended, gave_up = take(tk, gate, room=room)
                after = ended - (len(tk) * 100 - info["after_s"] * 1000)
                lost += not sent
                gave += gave_up
                ontime += 0 <= after <= 4500 and not gave_up
                cut += after < 0
                long += after > 6000
            n = len(audio)
            lines.append(f"| {level} dBFS | {name} | {n} | {100 * lost / n:.1f} % | {100 * cut / n:.1f} % | {100 * ontime / n:.1f} % | {100 * gave / n:.1f} % | {100 * long / n:.1f} % |")
    lines += ["", "| Room alone at | Gate | Takes | Sent (Whisper hears words in it) |", "|---|---|---|---|"]
    for level in (-46, -40, -34):
        for name, gate in GATES.items():
            sent_n = sum(take(ticks(x / (np.abs(x).max() + 1e-9) * 10 ** (level / 20)), gate)[0] for x in noise)
            lines.append(f"| {level} dBFS peak | {name} | {len(noise)} | {100 * sent_n / len(noise):.1f} % |")
    md = "\n".join(lines)
    print(md)
    open(sys.argv[2], "w").write(md + "\n")


if __name__ == "__main__":
    main()
