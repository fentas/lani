#!/usr/bin/env python3
"""A talk's latency in the worker (companion/stt-local/README.md, "Talks"): today's talk (one
plain hearing), the two hearings as two requests (today's worker: two transcribes, the encoder four times or more,
Whisper's 224-token limit) and as one (plain=1: the encoder once, the token budget). The same takes in the same order, alternating, so the GPU's load
is shared fairly; and whether the texts stay the same.

  timing.py <items.json> <model> <out.json> [n]
"""
import json
import os
import statistics as st
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
BENCH = Path(os.environ.get("LANI_TALK_BENCH", HERE.parents[2] / ".tmp/stt-bench"))
for k, v in dict(LANI_STT_MODELS=BENCH / "models", LANI_STT_STATE=BENCH / "state", MIOPEN_USER_DB_PATH=BENCH / "miopen",
                 MIOPEN_CUSTOM_CACHE_DIR=BENCH / "miopen/cache", TRITON_CACHE_DIR=BENCH / "triton").items():
    os.environ.setdefault(k, str(v))
sys.path.insert(0, str(HERE.parent))
import server  # noqa: E402


def main():
    items_path, model, out = sys.argv[1], sys.argv[2], sys.argv[3]
    n = int(sys.argv[4]) if len(sys.argv) > 4 else 80
    data = json.loads(Path(items_path).read_text())
    items = {i["id"]: i for i in data["items"]}
    takes = [m for m in data["manifest"] if m["cond"] == "teo" and int(m["id"]) % 15 == 0][:n]
    e = server.Engine(model, warm=("it", None))
    e.load()
    if not e.ready:
        sys.exit(e.error)
    torch = e.torch
    rows = []
    for k, m in enumerate(takes):
        audio = server.decode(Path(m["audio"]).read_bytes(), "m4a")
        prompt = items[m["id"]]["prompt"]["ctx"]
        if k % 10 == 9:
            time.sleep(1.0)
            while torch.cuda.mem_get_info()[0] < 2.5 * 2**30:
                time.sleep(20)
        order = ("one", "old", "new") if k % 2 == 0 else ("new", "old", "one")
        r = {}
        for way in order:
            torch.cuda.synchronize()
            t0 = time.monotonic()
            if way == "one":  # today's talk: one hearing, no prompt, no budget
                server.BUDGET = 0
                texts = (e.transcribe(audio, "it", None)["text"],)
            elif way == "old":
                server.BUDGET = 0
                a = e.transcribe(audio, "it", prompt)
                b = e.transcribe(audio, "it", None)
                texts = (a["text"], b["text"])
            else:
                server.BUDGET = 12
                hits0 = e.cache.hits
                a = e.transcribe(audio, "it", prompt, plain=True)
                texts = (a["text"], a["plain"]["text"])
                r["hits"] = e.cache.hits - hits0
            torch.cuda.synchronize()
            r[way] = time.monotonic() - t0
            if way != "one":
                r[way + "_texts"] = texts
        r["seconds"] = len(audio) / 16000
        rows.append(r)
    same = sum(1 for r in rows if r["old_texts"] == r["new_texts"])
    res = {
        "model": e.name, "takes": len(rows),
        "one plain hearing (today's talk)": {"median": st.median(r["one"] for r in rows), "p90": sorted(r["one"] for r in rows)[int(0.9 * len(rows))]},
        "two requests": {"median": st.median(r["old"] for r in rows), "p90": sorted(r["old"] for r in rows)[int(0.9 * len(rows))]},
        "one request, plain=1": {"median": st.median(r["new"] for r in rows), "p90": sorted(r["new"] for r in rows)[int(0.9 * len(rows))]},
        "encoder reuses a take (median)": st.median(r["hits"] for r in rows),
        "same texts": same,
        "differ": [(r["old_texts"], r["new_texts"]) for r in rows if r["old_texts"] != r["new_texts"]][:12],
    }
    Path(out).write_text(json.dumps(res, indent=1, ensure_ascii=False))
    print(json.dumps(res, indent=1, ensure_ascii=False))


if __name__ == "__main__":
    main()
