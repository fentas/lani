#!/usr/bin/env python3
"""The worker's measure of each benchmark take's voice (server.py voice_activity): <out.json>, {"<cond>/<id>": {voiced, loud}}.

  voice.py <audio manifest> <out.json>      (numpy: the worker's venv)
"""
import json
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("LANI_STT_MODELS", "/nonexistent")  # nothing is loaded
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import server  # noqa: E402

m = json.loads(Path(sys.argv[1]).read_text())
out, t = {}, 0.0
for x in m:
    a = server.decode(Path(x["audio"]).read_bytes(), "m4a")
    t0 = time.monotonic()
    out[f"{x['cond']}/{x['id']}"] = server.voice_activity(a)
    t += time.monotonic() - t0
Path(sys.argv[2]).write_text(json.dumps(out))
print(len(out), "takes,", round(t / len(out) * 1000, 1), "ms each")
