"""Character voices for Drama 3: make the reference candidates, pick one, check a voice holds.

    refs.py candidates [--speakers grandma,girl] [--out DIR] [--n 3]
        For each speaker of voices.json: three candidate references, each a take of the speaker's
        line made from their description alone (no reference, no real person's voice), the first
        of up to --tries seeds that passes the checks (the gender by pitch, noise, Whisper; a
        reference is held to a cleaner noise floor, as its hiss would be cloned into every line).
        Writes DIR/<speaker>-<n>.mp3 and DIR/list.txt, keeps every candidate's codes in
        candidates.json, and makes the best-scoring one the speaker's reference in voices.json
        (unless Jan picked one).
    refs.py pick grandma=2 [girl=1 ...]
        Jan's pick: that candidate becomes the speaker's reference in voices.json.
    refs.py rescore [--speakers ...] [--out DIR]
        The stored candidates scored again (no model run), the defaults and DIR/list.txt updated.
    refs.py consistency [--speakers ...] [--out DIR] [--lines 8]
        Each voice says the 8 test lines after its reference, through the full guard; reports per
        speaker the pitch of each line, its spread in semitones, the gender check and the attempts.

Model runs: check `free -g` (keep 20 GB available) and run under the host's heavy lock, e.g.
    flock <heavy.lock> venv/bin/python refs.py candidates
"""

from __future__ import annotations

import argparse
import json
import logging
import math
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import checks  # noqa: E402
import engine as E  # noqa: E402

CANDIDATES = HERE / "candidates.json"
VOICES = HERE / "voices.json"
# a reference's hiss is cloned into every line of its speaker: a cleaner floor than a line's
REF_NOISE_MAX = -62.0
LINES = [
    "Dober dan! Kako si danes?",
    "Babica Micka peče kruh v peči.",
    "Grem delat, pridem zvečer.",
    "Na svidenje, se vidimo jutri!",
    "Midva sva na pašniku, onidve sta v kuhinji.",
    "Oprostite, ali ste vi Anton?",
    "Kamen na kamen. Temelji so trdni!",
    "Čez cesto je ribnik, zraven njega pa stara lipa.",
]
log = logging.getLogger("lani-drama3")


def wait_for_memory(min_gib: float = 20.0, poll: float = 30.0) -> None:
    import drama3_rocm as D
    while D.mem_available() < min_gib * D.GIB:
        log.warning("MemAvailable %.1f GiB < %.0f GiB: waiting", D.mem_available() / D.GIB, min_gib)
        time.sleep(poll)


def load_json(p: Path) -> dict:
    return json.loads(p.read_text()) if p.exists() else {}


def save_json(p: Path, data: dict) -> None:
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    tmp.replace(p)


def set_reference(speaker: str, cand: dict, by: str) -> None:
    """voices.json: [cand] as [speaker]'s reference, chosen [by] ('score' or 'jan')."""
    raw = load_json(VOICES)
    raw[speaker]["ref"] = {"text": cand["text"], "frames": cand["frames"], "codes": cand["codes"],
                           "f0": cand["metrics"].get("f0"), "id": f"{speaker}-{cand['n']}", "seed": cand["seed"],
                           "chosen": by}
    save_json(VOICES, raw)


# a candidate's pitch is scored against what the character should sound like (voices.json "target_f0"
# for a voice of its own: the deep blacksmith, the old man), else its class's
TARGET_F0 = {"male": 120.0, "female": 205.0, "child": 280.0}


def ref_score(ok: bool, m: dict, reasons: list, warnings: list, spec: dict, target: float | None = None) -> float:
    """A reference candidate's score (higher is better): passing first; then the text heard, Whisper
    hearing Slovene (not Croatian or Czech), a clean noise floor (its hiss would be in every line),
    the pitch near the character's, few warnings."""
    s = (100.0 if ok else 0.0) - 5.0 * len(reasons) - 2.0 * len(warnings)
    s += 10.0 * float(m.get("text_score") or 0.0)
    if m.get("language") == "sl":
        s += 10.0 * float(m.get("language_p") or 0.0)
    elif m.get("language"):
        s -= 10.0
    if m.get("noise_floor") is not None:
        s -= 0.5 * max(0.0, float(m["noise_floor"]) + 75.0)
    if m.get("f0"):
        t = target or TARGET_F0[checks.pitch_class(spec)]
        s -= abs(12 * math.log2(float(m["f0"]) / t))
    return round(s, 2)


def rescore(speakers: list[str] | None, out: Path) -> None:
    """The stored candidates scored again (no model run): the defaults and list.txt updated."""
    store = load_json(CANDIDATES)
    raw = load_json(VOICES)
    for sp, cands in store.items():
        if speakers and sp not in speakers:
            continue
        spec = {"gender": raw[sp]["gender"], "age": raw[sp].get("age", "adult")}
        for c in cands:
            c["score"] = ref_score(c["ok"], c["metrics"], c["reasons"], c["warnings"], spec, raw[sp].get("target_f0"))
        save_json(CANDIDATES, store)
        if raw[sp].get("ref", {}).get("chosen") != "jan":
            set_reference(sp, max(cands, key=lambda c: (c["ok"], c["score"])), "score")
            raw = load_json(VOICES)
    write_list(out)


def candidates(eng: E.Engine, speakers: list[str], out: Path, n: int, tries: int) -> None:
    out.mkdir(parents=True, exist_ok=True)
    store = load_json(CANDIDATES)
    raw = load_json(VOICES)
    for sp in speakers:
        voice = eng.voices.voices[sp]
        line = raw[sp]["line"]
        made = []
        for k in range(1, n + 1):
            best = None
            for t in range(tries):
                wait_for_memory()
                seed = voice.seed * 1000 + k * 100 + t
                tk = eng.take(line, voice, seed, describe=True)
                # a candidate is a new voice: not checked against the reference it may replace
                v = eng.judge(tk, line, voice, detect=True, spec={**voice.spec, "ref_f0": None})
                if v.ok and v.metrics.get("noise_floor", 0) > REF_NOISE_MAX:
                    v.fail(f"noise: {v.metrics['noise_floor']:.0f} dB, a reference needs under {REF_NOISE_MAX:.0f}")
                tk.verdict = v
                sc = ref_score(v.ok, v.metrics, v.reasons, v.warnings, voice.spec, raw[sp].get("target_f0"))
                log.info("%s-%d try %d (seed %d): %s score %.1f %s", sp, k, t + 1, seed, "ok" if v.ok else "FAIL",
                         sc, "; ".join(v.reasons + v.warnings))
                if best is None or sc > best[1]:
                    best = (tk, sc, t + 1)
                if v.ok:
                    break
            tk, sc, used = best
            m = tk.verdict.metrics
            mp3 = out / f"{sp}-{k}.mp3"
            mp3.write_bytes(E.to_mp3(E.peak_normalise(tk.audio)))
            codes = tk.frames[:, 1:].T.contiguous().numpy()
            made.append({"n": k, "seed": tk.seed, "tries": used, "text": line, "description": voice.description,
                         "frames": int(codes.shape[1]), "codes": E.codes_to_b64(codes), "score": sc,
                         "ok": tk.verdict.ok, "reasons": tk.verdict.reasons, "warnings": tk.verdict.warnings,
                         "metrics": {k2: m.get(k2) for k2 in ("f0", "voiced", "noise_floor", "noise_s", "seconds",
                                                              "heard", "text_score", "language", "language_p")},
                         "file": str(mp3)})
        store[sp] = made
        save_json(CANDIDATES, store)
        top = max(made, key=lambda c: (c["ok"], c["score"]))
        if raw[sp].get("ref", {}).get("chosen") != "jan":
            set_reference(sp, top, "score")
            raw = load_json(VOICES)
        write_list(out)


def write_list(out: Path) -> None:
    store = load_json(CANDIDATES)
    raw = load_json(VOICES)
    lines = ["Drama 3 reference candidates: three per speaker, each made by Drama 3 from the description",
             "alone (no real person's voice). The default is the best-scoring one (marked *); Jan picks with",
             "  python3 companion/voice-drama3/refs.py pick <speaker>=<n>", ""]
    for sp, cands in store.items():
        ref = raw.get(sp, {}).get("ref", {})
        lines.append(f"{sp} ({raw.get(sp, {}).get('gender')}{', ' + raw[sp]['age'] if raw.get(sp, {}).get('age') else ''})")
        lines.append(f"  description: {cands[0]['description']}")
        lines.append(f"  line: {cands[0]['text']}")
        for c in cands:
            m = c["metrics"]
            mark = "*" if ref.get("id") == f"{sp}-{c['n']}" else " "
            lang = f"{m.get('language')} {m.get('language_p')}" if m.get("language") else "-"
            lines.append(f"  {mark} {sp}-{c['n']}.mp3  score {c['score']:6.1f}  {'pass' if c['ok'] else 'FAIL'}"
                         f"  f0 {m.get('f0')} Hz  noise {m.get('noise_floor')} dB  {m.get('seconds')} s"
                         f"  language {lang}  seed {c['seed']} (try {c['tries']})")
            lines.append(f"      heard: {m.get('heard')}")
            for r in c["reasons"] + c["warnings"]:
                lines.append(f"      - {r}")
        lines.append("")
    (out / "list.txt").write_text("\n".join(lines))


def pick(choices: list[str]) -> None:
    store = load_json(CANDIDATES)
    for ch in choices:
        sp, _, n = ch.partition("=")
        cand = next((c for c in store.get(sp, []) if str(c["n"]) == n), None)
        if cand is None:
            sys.exit(f"no candidate {sp}-{n} in {CANDIDATES}")
        set_reference(sp, cand, "jan")
        print(f"{sp}: {sp}-{n} is the reference now")


def consistency(eng: E.Engine, speakers: list[str], out: Path, lines: list[str], lanes: int = 2) -> dict:
    """Each voice says [lines] after its reference through the full guard, [lanes] at a time as the
    worker takes them (one generating on the GPU while the other is decoded and heard), so the wall
    time per second of audio is the batch's."""
    from concurrent.futures import ThreadPoolExecutor

    out.mkdir(parents=True, exist_ok=True)
    report = load_json(out / "consistency.json")
    voices = [eng.voices.voices[sp] for sp in speakers]
    for v in voices:
        if v.ref_codes is None:
            log.warning("%s has no reference yet: skipped", v.name)
    jobs = [(v, i, text) for v in voices if v.ref_codes is not None for i, text in enumerate(lines, 1)]
    rows: dict[str, list] = {v.name: [None] * len(lines) for v in voices}

    def one(job):
        voice, i, text = job
        wait_for_memory()
        r = eng.synth(text, voice)
        first = r.attempts[0]
        row = {"text": text, "ok": r.ok, "attempts": len(r.attempts), "seconds": round(r.seconds, 1),
               "first": {"ok": first["ok"], "f0": first["metrics"].get("f0"), "reasons": first["reasons"]},
               "audio_s": r.take.verdict.metrics.get("seconds") if r.ok else None,
               "f0": r.take.verdict.metrics.get("f0") if r.ok else None,
               "noise_floor": r.take.verdict.metrics.get("noise_floor") if r.ok else None,
               "heard": r.take.verdict.metrics.get("heard") if r.ok else None,
               "denoised": r.take.verdict.metrics.get("denoised") if r.ok else None,
               "warnings": r.take.verdict.warnings if r.ok else [],
               "failed": [a["reasons"] for a in r.attempts if not a["ok"]]}
        if r.ok:
            (out / f"{voice.name}-{i}.mp3").write_bytes(E.to_mp3(E.peak_normalise(r.take.audio)))
        rows[voice.name][i - 1] = row
        log.info("%s line %d: %s", voice.name, i, json.dumps(row, ensure_ascii=False))

    t0 = time.monotonic()
    with ThreadPoolExecutor(lanes) as pool:
        list(pool.map(one, jobs))
    wall = time.monotonic() - t0
    semis = lambda v: round(12 * math.log2(max(v) / min(v)), 1) if len(v) > 1 else 0.0  # noqa: E731
    for voice in voices:
        if voice.ref_codes is None:
            continue
        rs = rows[voice.name]
        f0s = [x["f0"] for x in rs if x["f0"]]
        firsts = [x["first"]["f0"] for x in rs if x["first"]["f0"]]
        gender_first = 0
        for x in rs:
            v = checks.Verdict()
            if x["first"]["f0"]:
                checks.check_gender(x["first"]["f0"], {**voice.spec, "ref_f0": None}, v)
            gender_first += v.ok
        report[voice.name] = {
            "class": checks.pitch_class(voice.spec), "ref_f0": voice.ref_f0, "ref": voice.ref_id, "lines": rs,
            "passed": sum(x["ok"] for x in rs), "attempts": sum(x["attempts"] for x in rs),
            "first_take_passed": sum(x["first"]["ok"] for x in rs), "first_take_gender_ok": gender_first,
            "f0": f0s, "f0_spread_semitones": semis(f0s),
            "f0_rms_from_ref_semitones": round(12 * (sum(math.log2(x / voice.ref_f0) ** 2 for x in f0s) / len(f0s)) ** 0.5, 1)
            if f0s and voice.ref_f0 else None,
            "first_take_f0_spread_semitones": semis(firsts),
            "audio_seconds": round(sum(x["audio_s"] or 0 for x in rs), 1),
        }
        log.info("%s: %s", voice.name, json.dumps({k: v for k, v in report[voice.name].items() if k != "lines"}))
    audio = sum(r["audio_seconds"] for r in report.values() if isinstance(r, dict) and "audio_seconds" in r)
    report["_batch"] = {"lanes": lanes, "lines": len(jobs), "wall_seconds": round(wall, 1),
                        "audio_seconds": round(audio, 1), "seconds_per_audio_second": round(wall / audio, 2) if audio else None}
    save_json(out / "consistency.json", report)
    log.info("batch: %s", json.dumps(report["_batch"]))
    return report


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("what", choices=["candidates", "pick", "consistency", "list", "rescore"])
    ap.add_argument("choices", nargs="*", help="pick: speaker=n …")
    ap.add_argument("--speakers", default="")
    ap.add_argument("--out", default=str(Path.home() / "lani-drama3-refs"))
    ap.add_argument("--n", type=int, default=3)
    ap.add_argument("--tries", type=int, default=4)
    ap.add_argument("--lines", type=int, default=len(LINES))
    a = ap.parse_args()
    logging.basicConfig(level=logging.INFO, stream=sys.stdout, format="%(asctime)s %(levelname)s %(message)s")
    if a.what == "pick":
        pick(a.choices)
        return
    if a.what == "list":
        write_list(Path(a.out))
        return
    if a.what == "rescore":
        rescore([s for s in a.speakers.split(",") if s], Path(a.out))
        return
    eng = E.Engine()
    speakers = [s for s in a.speakers.split(",") if s] or list(eng.voices.voices)
    unknown = [s for s in speakers if s not in eng.voices.voices]
    if unknown:
        sys.exit(f"not in voices.json: {', '.join(unknown)}")
    wait_for_memory()
    eng.load()
    try:
        if a.what == "candidates":
            candidates(eng, speakers, Path(a.out), a.n, a.tries)
        else:
            consistency(eng, speakers, Path(a.out), LINES[: a.lines])
    finally:
        eng.asr.close()


if __name__ == "__main__":
    main()
