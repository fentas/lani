#!/usr/bin/env bash
# Smoke test for the local speech recognition worker: wait for /health, then transcribe
# one Slovene sentence (made with the voice worker on 8795 if it runs, else a tone) and
# check the answer's shape.
#
#   smoke.sh [URL] [AUDIO]      defaults: http://127.0.0.1:8796, a fresh Gepard clip
set -euo pipefail
# shellcheck source=../bin/lani-env.sh
. "$(dirname "${BASH_SOURCE[0]}")/../bin/lani-env.sh"   # LANI_* (or their FLUENT_* names)
URL="${1:-${LANI_STT_URL:-http://127.0.0.1:8796}}"
AUDIO="${2:-}"
TEXT="${LANI_STT_SMOKE_TEXT:-Dober dan, jaz sem Jan. Koliko stane kruh?}"
VOICE="${LANI_VOICE_URL:-http://127.0.0.1:8795}"

TMP="$(mktemp -d)"; trap 'rm -rf "${TMP}"' EXIT
for i in $(seq 1 60); do   # up to ~2 minutes for the model to load
  code="$(curl -s -o "${TMP}/health" -w '%{http_code}' "${URL}/health" || true)"
  if [[ "${code}" == 200 ]]; then break; fi
  if [[ "${code}" == 503 ]] && grep -q '"error"' "${TMP}/health"; then
    echo "FAIL: model load failed: $(cat "${TMP}/health")"; exit 1
  fi
  [[ $i == 1 ]] && echo "waiting for ${URL}/health (HTTP ${code:-none}) ..."
  sleep 2
done
[[ "${code}" == 200 ]] || { echo "FAIL: /health returned ${code:-no answer}"; exit 1; }
echo "health: $(cat "${TMP}/health")"

if [[ -z "${AUDIO}" ]]; then
  body="$(python3 -c 'import json,sys; print(json.dumps({"text": sys.argv[1]}))' "${TEXT}")"
  if curl -sf -o "${TMP}/say.mp3" -H 'Content-Type: application/json' -d "${body}" "${VOICE}/synth" 2>/dev/null; then
    /usr/bin/ffmpeg -hide_banner -loglevel error -i "${TMP}/say.mp3" -ac 1 -ar 16000 -c:a aac -b:a 32k "${TMP}/say.m4a"
    AUDIO="${TMP}/say.m4a"
  else
    echo "note: voice worker not reachable, sending a tone (expect an empty or odd transcript)"
    /usr/bin/ffmpeg -hide_banner -loglevel error -f lavfi -i "sine=frequency=220:duration=2" -ac 1 -ar 16000 -c:a aac "${TMP}/tone.m4a"
    AUDIO="${TMP}/tone.m4a"; TEXT=""
  fi
fi

prompt="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "${TEXT}")"
read -r code took < <(curl -s -o "${TMP}/out.json" -w '%{http_code} %{time_total}\n' \
  --data-binary "@${AUDIO}" -H 'Content-Type: application/octet-stream' \
  "${URL}/transcribe?language=sl&prompt=${prompt}")
[[ "${code}" == 200 ]] || { echo "FAIL: /transcribe returned ${code}: $(head -c 300 "${TMP}/out.json")"; exit 1; }
python3 - "${TMP}/out.json" "${TEXT}" "${took}" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
assert isinstance(r["text"], str) and isinstance(r["segments"], list), r
words = [w for s in r["segments"] for w in s["words"]]
assert all({"word", "prob", "start", "end"} <= set(w) for w in words), words
print(f"OK: {r['text']!r} ({len(words)} words, {r['duration']}s of audio, model {r['model']}) in {sys.argv[3]}s")
print("   words:", ", ".join(f"{w['word']} {w['prob']:.2f}" for w in words))
if sys.argv[2]:
    print("   expected:", repr(sys.argv[2]))
PY
