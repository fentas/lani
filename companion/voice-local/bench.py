#!/usr/bin/env python3
"""Benchmark and voice-sweep tool for the Gepard worker (no HTTP).

Loads the model like server.py, then synthesizes the test sentences and prints
wall time per clip. Writes WAV + MP3 files to --out.

    venv/bin/python bench.py --out /tmp/gepard                 # default voice
    venv/bin/python bench.py --voice seed:7 --voice ref:samples/02_nevtralno.wav
    venv/bin/python bench.py --sweep 0-11 --text "Koliko stane kruh?"
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

SENTENCES = [
    "Dober dan, jaz sem Jan.",
    "Moja partnerka je iz Gorice.",
    "Koliko stane kruh?",
    "Danes piha burja, zato je zelo mraz.",
    "Nedeljsko kosilo pri tašči je bilo odlično.",
    "čebelnjak",
    "kozolec",
    "hvala",
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="/tmp/gepard-bench")
    ap.add_argument("--voice", action="append", default=[],
                    help="voice name from voices.json, seed:N, ref:PATH[@seed] or codes:FILE.pt[@seed]")
    ap.add_argument("--sweep", help="seed range a-b, no reference")
    ap.add_argument("--text", action="append", default=[])
    ap.add_argument("--repeat", type=int, default=1, help="repeat each clip (timing)")
    args = ap.parse_args()

    import server  # noqa: E402  (reads env, voices.json)

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    eng = server.ENGINE
    eng.load()
    if not eng.ready:
        sys.exit(f"load failed: {eng.error}")
    print(f"device={eng.device} load={eng.load_seconds:.1f}s (incl. warm-up)", flush=True)

    voices = list(args.voice)
    if args.sweep:
        a, b = (int(x) for x in args.sweep.split("-"))
        voices += [f"seed:{s}" for s in range(a, b + 1)]
    if not voices:
        voices = [server.DEFAULT_VOICE]

    for spec in voices:
        name = spec
        if spec.startswith("seed:"):
            server.VOICES[name] = {"seed": int(spec[5:])}
        elif spec.startswith("ref:"):
            ref, _, seed = spec[4:].partition("@")
            server.VOICES[name] = {"ref": ref, "seed": int(seed or 0)}
            p = (eng.ckpt / ref) if ref.startswith("samples/") else Path(ref)
            eng.refs[name] = eng.player.encode_reference(str(p))
        elif spec.startswith("codes:"):
            path, _, seed = spec[6:].partition("@")
            server.VOICES[name] = {"seed": int(seed or 0)}
            import torch
            eng.refs[name] = torch.load(path).T.unsqueeze(0).long()
        server.ALIASES[name.lower()] = name
        tag = name.replace(":", "-").replace("/", "_").replace("@", "_s")
        total = 0.0
        for i, text in enumerate(args.text or SENTENCES):
            for _ in range(args.repeat):
                t0 = time.monotonic()
                mp3, dur, _ = eng.synth_mp3(text, name)
                wall = time.monotonic() - t0
            total += wall
            f = out / f"{tag}_{i + 1:02d}.mp3"
            f.write_bytes(mp3)
            print(json.dumps({"voice": name, "i": i + 1, "text": text, "audio_s": round(dur, 2),
                              "wall_s": round(wall, 2), "rtf": round(wall / max(dur, 0.01), 2),
                              "file": str(f)}), flush=True)
        print(f"# {name}: total {total:.1f}s", flush=True)


if __name__ == "__main__":
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    main()
