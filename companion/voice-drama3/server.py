#!/usr/bin/env python3
"""Lani's Drama 3 worker: Fish Audio Drama 3 over HTTP, for the bridge's batch clips.

Built with Fish Audio. The same contract as the Gepard worker (companion/voice-local):

    GET  /health  200 {"ok": true, "engine": "drama3", "device": "...", "busy": false, "voices": [...]}
                  503 {"ok": false, "loading": true, "waiting": "..."} while it waits for the host's
                  heavy lock or for memory, or loads the model; 503 {"ok": false, "error": "..."}
    POST /synth   {"text": "...", "speaker": "grandma"}  ->  200 audio/mpeg (MP3, 44.1 kHz mono)
                  headers X-Voice, X-Audio-Seconds, X-Synth-Seconds, X-Attempts, X-Seed, X-Checks
                  422 {"ok": false, "error": "quality", "attempts": [...]}: no take passed the checks
                  400 bad input or an unknown speaker; 503 busy or not ready; 500 an error

Far too slow for live lines (about 6-12 s of compute a second of audio on the RX 9070): the
bridge's voice-build --drama3 starts it, voices a speaker's clips and stops it. Two requests may
be in flight (one on the GPU while the other is decoded and heard by Whisper); a third gets 503.

The model loads only after the host's heavy lock (LANI_DRAMA3_LOCK, a file to flock) is ours and
MemAvailable is at least LANI_DRAMA3_START_GIB (20 by default); the lock is held while the
process lives. Settings: README.md.
"""

from __future__ import annotations

import fcntl
import json
import logging
import os
import signal
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import engine as E  # noqa: E402  (the standard library at import; numpy and torch only when used)

HOST = os.environ.get("LANI_DRAMA3_HOST", "127.0.0.1")
PORT = int(os.environ.get("LANI_DRAMA3_PORT", "8785"))
LOCK = os.environ.get("LANI_DRAMA3_LOCK", "")
START_GIB = float(os.environ.get("LANI_DRAMA3_START_GIB", "20"))
INFLIGHT = int(os.environ.get("LANI_DRAMA3_INFLIGHT", "2"))
MEMINFO = os.environ.get("LANI_DRAMA3_MEMINFO", "/proc/meminfo")
MAX_CHARS = 400

log = logging.getLogger("lani-drama3")


class State:
    """The engine and what /health tells about it."""

    def __init__(self, engine) -> None:
        self.engine = engine
        self.waiting: str | None = "starting"
        self.slots = threading.BoundedSemaphore(INFLIGHT)
        self.running = 0
        self.lock_fd: int | None = None

    def start(self, lock: str = LOCK, start_gib: float = START_GIB, poll: float = 15.0) -> None:
        """Waits for the lock and for memory, then loads the model (in its own thread)."""
        try:
            if lock:
                self.waiting = f"the heavy lock ({lock})"
                fd = os.open(lock, os.O_RDWR | os.O_CREAT, 0o644)
                fcntl.flock(fd, fcntl.LOCK_EX)  # held until the process ends
                self.lock_fd = fd
                log.info("holding the heavy lock %s", lock)
            while start_gib > 0 and available_gib() < start_gib:
                self.waiting = f"memory ({available_gib():.0f} GB available, {start_gib:.0f} needed)"
                time.sleep(poll)
            self.waiting = "loading the model"
            self.engine.load()
            self.waiting = None
        except Exception as e:  # noqa: BLE001
            log.exception("model load failed")
            self.engine.error = f"{type(e).__name__}: {e}"
            self.waiting = None


def available_gib(meminfo: str | None = None) -> float:
    with open(meminfo or MEMINFO) as f:
        for line in f:
            if line.startswith("MemAvailable:"):
                return int(line.split()[1]) / 2**20
    return 0.0


STATE: State | None = None


def checks_header(verdict) -> str:
    """The checks of the take as one ASCII line of JSON (an HTTP header)."""
    m = verdict.metrics
    keep = {k: m[k] for k in ("f0", "drift", "noise_floor", "noise_s", "text_score", "rate") if m.get(k) is not None}
    return json.dumps({**keep, "warnings": verdict.warnings}, ensure_ascii=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "lani-drama3/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        log.info("%s %s", self.address_string(), fmt % args)

    def _json(self, code: int, obj: dict) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if code == 503:
            self.send_header("Retry-After", "10")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.split("?")[0] != "/health":
            return self._json(404, {"ok": False, "error": "not found"})
        st, eng = STATE, STATE.engine
        if eng.ready:
            return self._json(200, {"ok": True, "engine": "drama3", "device": eng.device, "busy": st.running >= INFLIGHT,
                                    "running": st.running, "voices": list(eng.voices.voices),
                                    "default_voice": eng.voices.default, "load_seconds": round(eng.load_seconds, 1),
                                    "whisper": bool(getattr(eng.asr, "available", False)),
                                    "built_with": "Built with Fish Audio"})
        if eng.error:
            return self._json(503, {"ok": False, "engine": "drama3", "error": eng.error})
        return self._json(503, {"ok": False, "engine": "drama3", "loading": True, "waiting": st.waiting})

    def do_POST(self):
        if self.path.split("?")[0] != "/synth":
            return self._json(404, {"ok": False, "error": "not found"})
        try:
            n = int(self.headers.get("Content-Length") or 0)
            if n > 64 * 1024:
                return self._json(413, {"ok": False, "error": "body too large"})
            req = json.loads(self.rfile.read(n) or b"{}")
            text, speaker = req.get("text"), req.get("speaker")
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
        st, eng = STATE, STATE.engine
        if not eng.ready:
            return self._json(503, {"ok": False, "error": eng.error or "loading", "waiting": st.waiting})
        voice = eng.voices.pick(speaker)
        if voice is None:
            return self._json(400, {"ok": False, "error": f"unknown speaker {speaker!r}", "voices": list(eng.voices.voices)})
        if voice.ref_codes is None:
            return self._json(400, {"ok": False, "error": f"{voice.name} has no reference voice yet (refs.py candidates)"})
        if not st.slots.acquire(blocking=False):
            return self._json(503, {"ok": False, "error": "busy"})
        st.running += 1
        t0 = time.monotonic()
        try:
            r = eng.synth(text, voice)
            if not r.ok:
                log.warning("no take of %r (%s) passed: %s", text, voice.name,
                            " | ".join("; ".join(a["reasons"]) for a in r.attempts))
                return self._json(422, {"ok": False, "error": "quality", **r.report()})
            mp3 = E.to_mp3(E.peak_normalise(r.take.audio))
        except Exception as e:  # noqa: BLE001
            log.exception("synthesis failed")
            return self._json(500, {"ok": False, "error": f"{type(e).__name__}: {e}"})
        finally:
            st.running -= 1
            st.slots.release()
        wall = time.monotonic() - t0
        dur = len(r.take.audio) / E.SAMPLE_RATE
        log.info("synth %d chars -> %.1fs audio in %.1fs, %d attempt(s) (voice=%s)", len(text), dur, wall, len(r.attempts), voice.name)
        self.send_response(200)
        self.send_header("Content-Type", "audio/mpeg")
        self.send_header("Content-Length", str(len(mp3)))
        self.send_header("X-Voice", f"{voice.name}/{voice.ref_id or 'description'}")
        self.send_header("X-Audio-Seconds", f"{dur:.2f}")
        self.send_header("X-Synth-Seconds", f"{wall:.2f}")
        self.send_header("X-Attempts", str(len(r.attempts)))
        self.send_header("X-Seed", str(r.take.seed))
        self.send_header("X-Checks", checks_header(r.take.verdict))
        if r.take.verdict.metrics.get("denoised"):
            self.send_header("X-Denoised", str(r.take.verdict.metrics["denoised"]))
        self.end_headers()
        self.wfile.write(mp3)


def main() -> None:
    global STATE
    logging.basicConfig(level=logging.INFO, stream=sys.stdout, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    STATE = State(E.Engine())
    srv = ThreadingHTTPServer((HOST, PORT), Handler)
    srv.daemon_threads = True
    threading.Thread(target=STATE.start, name="load", daemon=True).start()
    # SIGTERM (voice-build stopping it, systemd): out of serve_forever, the Whisper helper closed
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(0))
    log.info("listening on http://%s:%d (Built with Fish Audio)", HOST, PORT)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        STATE.engine.asr.close()


if __name__ == "__main__":
    main()
