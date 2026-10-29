#!/usr/bin/env python3
"""The talk benchmark's recognitions (companion/stt-local/README.md, "Talks"): each take heard as the worker hears it
(server.py's Engine and its decode options), plain and with each context prompt.

  run.py <items.json> <model> <out.jsonl> [--conds clean,teo,noise] [--passes plain,ctx,far] [--twice] [--every 2] [--limit N]

--twice: one request per take as a talk sends it (plain=1: the encoder shared, the token budget), "ctx" and "plain".
Resumable (a line per take and pass). Gentle with the GPU a live worker shares: a pause after every 20 takes, and a
wait while less than 2.5 GiB of VRAM is free. Its own state: LANI_TALK_BENCH (default <repo>/.tmp/stt-bench: models/,
the kernel caches); the worker's code: LANI_TALK_SERVER (default this folder's parent, server.py), e.g. an older copy
to measure it. Run with the worker's venv (~/.local/share/lani-stt/venv/bin/python), HIP_VISIBLE_DEVICES as its unit has.
"""
import argparse
import json
import os
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
BENCH = Path(os.environ.get("LANI_TALK_BENCH", HERE.parents[2] / ".tmp/stt-bench"))
os.environ.setdefault("LANI_STT_MODELS", str(BENCH / "models"))
os.environ.setdefault("LANI_STT_STATE", str(BENCH / "state"))
os.environ.setdefault("MIOPEN_USER_DB_PATH", str(BENCH / "miopen"))
os.environ.setdefault("MIOPEN_CUSTOM_CACHE_DIR", str(BENCH / "miopen/cache"))
os.environ.setdefault("TRITON_CACHE_DIR", str(BENCH / "triton"))
sys.path.insert(0, os.environ.get("LANI_TALK_SERVER", str(HERE.parent)))
import server  # noqa: E402


def wait_for_room(model: str) -> None:
    """Until the GPU has the model's fp16 weights and 2 GiB more free: the live worker and others share it."""
    import torch

    f = server.model_file(model)
    need = (f.stat().st_size if f else 3 * 2**30) + 2 * 2**30
    while torch.cuda.is_available() and torch.cuda.mem_get_info()[0] < need:
        print(f"VRAM: {torch.cuda.mem_get_info()[0] / 2**30:.1f} GiB free, {need / 2**30:.1f} needed: waiting", flush=True)
        time.sleep(30)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("items")
    ap.add_argument("model")
    ap.add_argument("out")
    ap.add_argument("--conds", default="clean,teo,noise")
    ap.add_argument("--passes", default="plain,ctx,far")
    ap.add_argument("--twice", action="store_true", help="one request per take with plain=1: ctx and plain")
    ap.add_argument("--every", type=int, default=1, help="every n-th clip only (a sample)")
    ap.add_argument("--offset", type=int, default=0, help="with --every: from this clip (1 with --every 2: the odd ones, the test split)")
    ap.add_argument("--limit", type=int, default=0)
    a = ap.parse_args()
    data = json.loads(Path(a.items).read_text())
    items = {i["id"]: i for i in data["items"]}
    conds = a.conds.split(",")
    passes = ["ctx", "plain"] if a.twice else a.passes.split(",")
    takes = [m for m in data["manifest"] if m["cond"] in conds and (m["cond"] == "noise" or (int(m["id"]) - a.offset) % a.every == 0)]
    if a.limit:
        takes = takes[: a.limit]
    out = Path(a.out)
    done = set()
    if out.exists():
        for line in out.read_text().splitlines():
            r = json.loads(line)
            done.add((r["id"], r["cond"], r["pass"]))
    for p in (BENCH / "miopen/cache", BENCH / "triton", BENCH / "state"):
        p.mkdir(parents=True, exist_ok=True)
    wait_for_room(a.model)
    e = server.Engine(a.model, warm=("it", None))
    e.load()
    if not e.ready:
        sys.exit(f"load failed: {e.error}")
    torch = e.torch
    print(f"{e.name} on {e.device} in {e.load_seconds:.1f}s; {len(takes)} takes x {passes}", flush=True)
    n = 0
    with out.open("a") as f:
        def write(m, p, r, took, dec, prompt, seconds):
            f.write(json.dumps({"id": m["id"], "cond": m["cond"], "pass": p, "model": e.name, "took": round(took, 3),
                                "decode": round(dec, 3), "seconds": seconds, "prompt": prompt, "r": r}, ensure_ascii=False) + "\n")

        for m in takes:
            todo = [p for p in passes if (m["id"], m["cond"], p) not in done]
            if not todo:
                continue
            n += 1
            if n % 20 == 0:
                time.sleep(1.5)
                while torch.cuda.mem_get_info()[0] < 2.5 * 2**30:
                    print("VRAM low: waiting", flush=True)
                    time.sleep(20)
            t0 = time.monotonic()
            audio = server.decode(Path(m["audio"]).read_bytes(), "m4a")
            dec = time.monotonic() - t0
            seconds = round(len(audio) / 16000, 2)
            it = items[m["id"]]
            if a.twice:
                t0 = time.monotonic()
                r = e.transcribe(audio, "it", it["prompt"]["ctx"], plain=True)
                took = time.monotonic() - t0
                ctx = {k: v for k, v in r.items() if k != "plain"}
                write(m, "ctx", ctx, took, dec, it["prompt"]["ctx"], seconds)  # the request's time: both hearings
                write(m, "plain", {**ctx, **r["plain"]}, 0.0, dec, None, seconds)
            else:
                for p in todo:
                    prompt = None if p == "plain" else it["prompt"][p]
                    t0 = time.monotonic()
                    r = e.transcribe(audio, "it", prompt)
                    write(m, p, r, time.monotonic() - t0, dec, prompt, seconds)
            f.flush()
            if n % 100 == 0:
                print(n, m["cond"], m["id"], flush=True)
    print("done", n, flush=True)


if __name__ == "__main__":
    main()
