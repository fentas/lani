#!/usr/bin/env python3
"""Quality and speed check for the Whisper worker, without HTTP.

Takes Slovene TTS clips with known texts from the bridge's voice store (voice.db, opened
read-only), makes "learner-like" variants with ffmpeg and numpy, and reports word error
rate, latency and VRAM for one model:

    bench.py --model large-v3-turbo --db ~/.../data/app/voice/voice.db [--n 30] [--json out.json]

Variants: clean, slow (0.8x tempo), noise (pink noise at 10 dB SNR), phone (8 kHz, AAC 24k).
Each clip runs with no prompt and with the expected text as the prompt.

--errors also runs the prompt-bias test: sentences with a typical learner mistake, voiced by
the local Gepard worker (127.0.0.1:8795), transcribed with the CORRECT sentence as the
prompt. It shows whether the prompt makes Whisper "hear" the correct form.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import statistics
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

# Sentences with a learner mistake (spoken) and the correct form (expected, used as the prompt).
ERRORS = [
    ("Moj partnerka je iz Gorice.", "Moja partnerka je iz Gorice."),
    ("Živim v Ljubljano.", "Živim v Ljubljani."),
    ("Imam en brat in dve sestri.", "Imam enega brata in dve sestri."),
    ("Kozarec voda, prosim.", "Kozarec vode, prosim."),
    ("Nimam čas.", "Nimam časa."),
    ("Grem v trgovina.", "Grem v trgovino."),
    ("Rad pijem kava.", "Rad pijem kavo."),
    ("Včeraj sem bil v kino.", "Včeraj sem bil v kinu."),
    ("Stanovanje je velik.", "Stanovanje je veliko."),
    ("Jutri gremo na morje z avto.", "Jutri gremo na morje z avtom."),
    ("Jaz ima dva otroka.", "Jaz imam dva otroka."),
    ("Midva gremo v kino.", "Midva greva v kino."),
    ("To je moj sestra.", "To je moja sestra."),
    ("Ne maram mleko.", "Ne maram mleka."),
    ("Včeraj sem šla v službo.", "Včeraj sem šel v službo."),
    ("Kupil sem dva kruha.", "Kupil sem dva kruha in mleko."),
    ("Dober jutro, gospa Novak.", "Dobro jutro, gospa Novak."),
    ("Kje je postaja avtobus?", "Kje je avtobusna postaja?"),
    ("Imam lačen.", "Lačen sem."),
    ("Prosim eno kavo z mleko.", "Prosim eno kavo z mlekom."),
]


def norm(s: str) -> list[str]:
    s = s.lower().replace("’", "'")
    s = re.sub(r"[^\w\s']", " ", s)
    return s.split()


def edits(a: list, b: list) -> int:
    d = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(b) + 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (a[i - 1] != b[j - 1]))
            prev, d[j] = d[j], cur
    return d[len(b)]


def pick(db: str, n: int) -> list[tuple[str, str]]:
    """n clips: mostly sentences, some single words, spread by file name (a hash)."""
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    rows = con.execute("select file, text from clips where engine='elevenlabs' order by file").fetchall()
    con.close()
    long = [r for r in rows if len(r[1]) >= 12]
    short = [r for r in rows if len(r[1]) < 12]
    k_short = max(1, n // 6)
    take = lambda xs, k: [xs[i * len(xs) // k] for i in range(k)] if xs else []  # noqa: E731
    return take(long, n - k_short) + take(short, k_short)


def ffmpeg(src: str, dst: str, *args: str) -> None:
    subprocess.run(["/usr/bin/ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", src, *args, dst], check=True)


def variants(files: list[Path], tmp: Path) -> dict[str, list[Path]]:
    out: dict[str, list[Path]] = {"clean": [], "slow": [], "phone": []}
    for f in files:
        base = tmp / f.stem
        out["clean"].append(f)
        ffmpeg(str(f), f"{base}-slow.wav", "-af", "atempo=0.8", "-ar", "16000", "-ac", "1")
        out["slow"].append(Path(f"{base}-slow.wav"))
        ffmpeg(str(f), f"{base}-phone.m4a", "-af", "highpass=f=300,lowpass=f=3400", "-ar", "8000", "-ac", "1",
               "-c:a", "aac", "-b:a", "24k")
        out["phone"].append(Path(f"{base}-phone.m4a"))
    return out


def with_noise(audio, snr_db: float, seed: int):
    import numpy as np
    rng = np.random.default_rng(seed)
    white = rng.standard_normal(len(audio) + 1).astype(np.float32)
    pink = np.cumsum(white)  # brown-ish, then high-pass by differencing half of it: a cheap pink-like noise
    pink = (pink - np.convolve(pink, np.ones(64) / 64, mode="same"))[: len(audio)]
    p_sig = float((audio ** 2).mean()) or 1e-9
    p_noise = float((pink ** 2).mean()) or 1e-9
    return (audio + pink * (p_sig / p_noise / 10 ** (snr_db / 10)) ** 0.5).astype(np.float32)


def gepard(text: str, dst: Path, url: str = "http://127.0.0.1:8795") -> bool:
    req = urllib.request.Request(f"{url}/synth", data=json.dumps({"text": text}).encode(),
                                 headers={"content-type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            dst.write_bytes(r.read())
        return True
    except Exception as e:  # noqa: BLE001
        print(f"gepard failed for {text!r}: {e}", file=sys.stderr)
        return False


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="large-v3-turbo")
    ap.add_argument("--models-dir", default=None)
    ap.add_argument("--db", required=True)
    ap.add_argument("--n", type=int, default=30)
    ap.add_argument("--beam", type=int, default=None)
    ap.add_argument("--errors", action="store_true")
    ap.add_argument("--alignment", default=None, help="base model of a fine-tuned .pt, e.g. large-v3")
    ap.add_argument("--json", default=None)
    a = ap.parse_args()
    os.environ["LANI_STT_MODEL"] = a.model
    os.environ.setdefault("LANI_STT_PROMPT", "1")  # the "+prompt" rows always send it
    if a.models_dir:
        os.environ["LANI_STT_MODELS"] = a.models_dir
    if a.beam:
        os.environ["LANI_STT_BEAM"] = str(a.beam)
    if a.alignment:
        os.environ["LANI_STT_ALIGNMENT"] = a.alignment
    import server  # noqa: E402  (reads the environment at import)

    files_dir = Path(a.db).parent / "files"
    clips = [(files_dir / f, t) for f, t in pick(a.db, a.n)]
    tmp = Path(tempfile.mkdtemp(prefix="lani-stt-bench-"))
    var = variants([c[0] for c in clips], tmp)

    eng = server.Engine()
    eng.load()
    if not eng.ready:
        sys.exit(f"load failed: {eng.error}")
    torch = eng.torch
    gpu = torch.cuda.is_available() and server.DEVICE != "cpu"
    if gpu:
        torch.cuda.reset_peak_memory_stats()

    results: dict = {"model": a.model, "beam": server.BEAM, "device": eng.device, "load_seconds": round(eng.load_seconds, 1),
                     "clips": len(clips), "variants": {}, "samples": []}
    for vname in ("clean", "slow", "noise", "phone"):
        for prompted in (False, True):
            key = f"{vname}{'+prompt' if prompted else ''}"
            errs = words = 0
            lat, audio_s = [], []
            p_right, p_wrong = [], []  # word probabilities of heard words that are / aren't in the reference
            for i, (src, text) in enumerate(clips):
                f = var["clean" if vname == "noise" else vname][i]
                audio = server.decode(f.read_bytes(), server.sniff(f.read_bytes()) or "wav")
                if vname == "noise":
                    audio = with_noise(audio, 10.0, i)
                t0 = time.monotonic()
                out = eng.transcribe(audio, "sl", text if prompted else None)
                lat.append(time.monotonic() - t0)
                audio_s.append(len(audio) / 16000)
                ref, hyp = norm(text), norm(out["text"])
                e = edits(ref, hyp)
                left = list(ref)
                for w in (w for s in out["segments"] for w in s["words"]):
                    k = norm(w["word"])
                    if not k or w["prob"] is None:
                        continue
                    if k[0] in left:
                        left.remove(k[0])
                        p_right.append(w["prob"])
                    else:
                        p_wrong.append(w["prob"])
                errs += e
                words += len(ref)
                if e and len(results["samples"]) < 80:
                    probs = [(w["word"], w["prob"]) for s in out["segments"] for w in s["words"]]
                    results["samples"].append({"variant": key, "ref": text, "hyp": out["text"], "words": probs})
            results["variants"][key] = {
                "wer": round(errs / words, 4), "words": words,
                "latency_mean": round(statistics.mean(lat), 3), "latency_p90": round(sorted(lat)[int(0.9 * len(lat)) - 1], 3),
                "audio_mean": round(statistics.mean(audio_s), 2),
                "right_words_unclear": round(sum(p < 0.5 for p in p_right) / max(1, len(p_right)), 3),
                "wrong_words_unclear": round(sum(p < 0.5 for p in p_wrong) / max(1, len(p_wrong)), 3),
                "wrong_words": len(p_wrong),
            }
            print(f"{a.model:15s} {key:14s} WER {errs / words:6.1%}  ({errs}/{words})  latency {statistics.mean(lat):.2f}s "
                  f"(p90 {sorted(lat)[int(0.9 * len(lat)) - 1]:.2f}s) for {statistics.mean(audio_s):.1f}s audio; "
                  f"p<0.5: {sum(p < 0.5 for p in p_right)}/{len(p_right)} right words, {sum(p < 0.5 for p in p_wrong)}/{len(p_wrong)} wrong",
                  flush=True)

    if a.errors:
        bias = []
        for i, (spoken, expected) in enumerate(ERRORS):
            f = tmp / f"err{i}.mp3"
            if not gepard(spoken, f):
                continue
            audio = server.decode(f.read_bytes(), "mp3")
            plain = eng.transcribe(audio, "sl", None)
            biased = eng.transcribe(audio, "sl", expected)
            ok_plain = edits(norm(spoken), norm(plain["text"])) < edits(norm(expected), norm(plain["text"]))
            ok_biased = edits(norm(spoken), norm(biased["text"])) < edits(norm(expected), norm(biased["text"]))
            low = [(w["word"], w["prob"]) for s in biased["segments"] for w in s["words"] if (w["prob"] or 1) < 0.6]
            # The same sentence said correctly (same voice): does it pass with the prompt?
            right = None
            g = tmp / f"ok{i}.mp3"
            if gepard(expected, g):
                r = eng.transcribe(server.decode(g.read_bytes(), "mp3"), "sl", expected)
                right = {"text": r["text"], "exact": norm(r["text"]) == norm(expected),
                         "low": [(w["word"], w["prob"]) for s in r["segments"] for w in s["words"] if (w["prob"] or 1) < 0.6]}
            bias.append({"spoken": spoken, "expected": expected, "plain": plain["text"], "prompted": biased["text"],
                         "mistake_heard_plain": ok_plain, "mistake_heard_prompted": ok_biased, "low_prob_prompted": low,
                         "correct_said": right})
            print(f"  said {spoken!r:40s} plain {plain['text']!r:40s} prompted {biased['text']!r:40s} low {low}", flush=True)
            if right:
                print(f"  said {expected!r:40s} prompted {right['text']!r:40s} low {right['low']}", flush=True)
        results["bias"] = bias
        hp = sum(b["mistake_heard_plain"] for b in bias)
        hb = sum(b["mistake_heard_prompted"] for b in bias)
        flagged = sum(1 for b in bias if b["mistake_heard_prompted"] or b["low_prob_prompted"])
        ok = [b["correct_said"] for b in bias if b["correct_said"]]
        print(f"{a.model:15s} mistake kept in the transcript: {hp}/{len(bias)} without prompt, {hb}/{len(bias)} with prompt "
              f"({flagged}/{len(bias)} kept or flagged unclear); correct form passes: "
              f"{sum(r['exact'] for r in ok)}/{len(ok)} exact, {sum(1 for r in ok if r['low'])} with an unclear word")

    if gpu:
        results["vram_peak_gib"] = round(torch.cuda.max_memory_reserved() / 2**30, 2)
        print(f"{a.model:15s} load {eng.load_seconds:.1f}s, peak VRAM reserved by torch {results['vram_peak_gib']} GiB")
    if a.json:
        Path(a.json).write_text(json.dumps(results, ensure_ascii=False, indent=1))
    subprocess.run(["rm", "-rf", str(tmp)], check=False)


if __name__ == "__main__":
    main()
