"""Whisper on the CPU for the text check: what a take says, heard by faster-whisper large-v3.

Runs in its own venv (venv-asr: faster-whisper, CTranslate2 on the CPU, int8), apart from the
model's, and never asks the node's live STT service. The worker starts it once and keeps it:

    venv-asr/bin/python asr.py --model DIR [--threads 8]

Requests on stdin, one JSON object a line, answers on stdout the same way:

    {"id": 1, "wav": "/tmp/x.wav", "language": "sl", "detect": false}
    -> {"id": 1, "text": "Dober dan.", "seconds": 1.9, "language": "sl", "p": 0.97}

"detect": true adds the language Whisper hears without being told ("language", "p": its
probability); otherwise "language" is the one given. The WAV is 16 kHz mono (any rate is
resampled to 16 kHz).
"""

from __future__ import annotations

import argparse
import json
import sys
import time


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="a faster-whisper model directory (CTranslate2)")
    ap.add_argument("--threads", type=int, default=8)
    ap.add_argument("--beam", type=int, default=5)
    a = ap.parse_args()
    from faster_whisper import WhisperModel

    import numpy as np
    import soundfile as sf
    from scipy.signal import resample_poly

    model = WhisperModel(a.model, device="cpu", compute_type="int8", cpu_threads=a.threads)

    def load(path: str):
        # read here, not by faster-whisper's PyAV decoder (PyAV 19 dropped an argument it passes)
        audio, sr = sf.read(path, dtype="float32", always_2d=True)
        audio = audio.mean(axis=1)
        if sr != 16000:
            g = np.gcd(sr, 16000)
            audio = resample_poly(audio, 16000 // g, sr // g).astype(np.float32)
        return audio
    print(json.dumps({"ready": True}), flush=True)
    for line in sys.stdin:
        if not line.strip():
            continue
        req: dict = {}
        try:
            req = json.loads(line)
            t = time.perf_counter()
            lang = req.get("language") or None
            audio = load(req["wav"])
            out = {"id": req.get("id")}
            if req.get("detect"):
                segs, info = model.transcribe(audio, language=None, beam_size=a.beam,
                                              condition_on_previous_text=False)
                heard_auto = " ".join(s.text.strip() for s in segs)
                out.update({"language": info.language, "p": round(info.language_probability, 3)})
                if info.language == lang:
                    out["text"] = heard_auto
            if "text" not in out:
                segs, _ = model.transcribe(audio, language=lang, beam_size=a.beam,
                                           condition_on_previous_text=False)
                out["text"] = " ".join(s.text.strip() for s in segs)
                out.setdefault("language", lang)
            out["seconds"] = round(time.perf_counter() - t, 2)
        except Exception as e:  # noqa: BLE001
            out = {"id": req.get("id"), "error": f"{type(e).__name__}: {e}"}
        print(json.dumps(out, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
