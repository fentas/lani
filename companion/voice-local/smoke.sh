#!/usr/bin/env bash
# Smoke test for the local voice worker: wait for /health, synthesize one
# sentence, check the result is MP3.
#
#   smoke.sh [URL] [OUT.mp3]      defaults: http://127.0.0.1:8795  /tmp/lani-voice-smoke.mp3
set -euo pipefail
# shellcheck source=../bin/lani-env.sh
. "$(dirname "${BASH_SOURCE[0]}")/../bin/lani-env.sh"   # LANI_* (or their FLUENT_* names)
URL="${1:-${LANI_VOICE_URL:-http://127.0.0.1:8795}}"
OUT="${2:-${TMPDIR:-/tmp}/lani-voice-smoke.mp3}"
TEXT="${LANI_VOICE_SMOKE_TEXT:-Dober dan, jaz sem Jan. Koliko stane kruh?}"

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
health="$(cat "${TMP}/health" 2>/dev/null || true)"
[[ "${code}" == 200 ]] || { echo "FAIL: /health returned ${code:-no answer}"; exit 1; }
echo "health: ${health}"

body="$(python3 -c 'import json,sys; print(json.dumps({"text": sys.argv[1]}))' "${TEXT}")"
read -r code took < <(curl -s -o "${OUT}" -D "${TMP}/hdr" -w '%{http_code} %{time_total}\n' \
  -H 'Content-Type: application/json' -d "${body}" "${URL}/synth")
hdr="$(tr -d '\r' < "${TMP}/hdr")"
[[ "${code}" == 200 ]] || { echo "FAIL: /synth returned ${code}: $(head -c 300 "${OUT}")"; exit 1; }
grep -qi '^content-type: audio/mpeg' <<<"${hdr}" || { echo "FAIL: not audio/mpeg"; exit 1; }
size=$(stat -c %s "${OUT}")
(( size > 2000 )) || { echo "FAIL: MP3 too small (${size} bytes)"; exit 1; }
audio="$(grep -i '^x-audio-seconds:' <<<"${hdr}" | awk '{print $2}')"
voice="$(grep -i '^x-voice:' <<<"${hdr}" | awk '{print $2}')"
echo "OK: ${size} bytes, ${audio}s of audio (voice ${voice}) in ${took}s -> ${OUT}"
