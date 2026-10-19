#!/usr/bin/env bash
# Smoke test for the Drama 3 worker: start it (unless one answers), wait for /health, voice one line, check the MP3
# and the checks' headers, and stop it again if this script started it. Built with Fish Audio.
#
#   smoke.sh [URL] [OUT.mp3]   defaults: http://127.0.0.1:8785  /tmp/lani-drama3-smoke.mp3
#   LANI_DRAMA3_CMD            the worker's command (default ~/.local/share/lani-drama3/run.sh)
#   LANI_HEAVY_LOCK            the host's heavy lock the worker takes (default /tmp/lani-heavy.lock)
#
# The model takes ~15 s to load once the lock is free; a line ~20-60 s (four takes at most). Check `free -g` first:
# keep 20 GB available.
set -euo pipefail
URL="${1:-${LANI_DRAMA3_URL:-http://127.0.0.1:8785}}"
OUT="${2:-${TMPDIR:-/tmp}/lani-drama3-smoke.mp3}"
TEXT="${LANI_DRAMA3_SMOKE_TEXT:-Dober dan, jaz sem babica Micka. Koliko stane kruh?}"
SPEAKER="${LANI_DRAMA3_SMOKE_SPEAKER:-grandma}"
CMD="${LANI_DRAMA3_CMD:-${LANI_DRAMA3_HOME:-${HOME}/.local/share/lani-drama3}/run.sh}"
TMP="$(mktemp -d)"; PID=""
cleanup() { [[ -n "${PID}" ]] && kill "${PID}" 2>/dev/null && wait "${PID}" 2>/dev/null; rm -f "${TMP}"/*; rmdir "${TMP}"; }
trap cleanup EXIT

if ! curl -sf -o /dev/null "${URL}/health"; then
  hostport="${URL#*://}"
  echo "starting the worker: ${CMD} (its output: ${TMP}/worker.log)"
  # shellcheck disable=SC2086
  LANI_DRAMA3_HOST="${hostport%%:*}" LANI_DRAMA3_PORT="${hostport##*:}" LANI_DRAMA3_LOCK="${LANI_HEAVY_LOCK:-/tmp/lani-heavy.lock}" \
    ${CMD} > "${TMP}/worker.log" 2>&1 &
  PID=$!
fi
for i in $(seq 1 720); do   # up to an hour: the heavy lock may be another job's for a while
  code="$(curl -s -o "${TMP}/health" -w '%{http_code}' "${URL}/health" || true)"
  [[ "${code}" == 200 ]] && break
  if [[ "${code}" == 503 ]] && grep -q '"error"' "${TMP}/health"; then echo "FAIL: $(cat "${TMP}/health")"; exit 1; fi
  if [[ -n "${PID}" ]] && ! kill -0 "${PID}" 2>/dev/null; then echo "FAIL: the worker stopped:"; tail -20 "${TMP}/worker.log"; exit 1; fi
  (( i % 15 == 1 )) && echo "waiting for ${URL}/health: $(cat "${TMP}/health" 2>/dev/null || echo "HTTP ${code:-none}")"
  sleep 5
done
[[ "${code}" == 200 ]] || { echo "FAIL: /health returned ${code:-no answer}"; exit 1; }
echo "health: $(cat "${TMP}/health")"

body="$(python3 -c 'import json,sys; print(json.dumps({"text": sys.argv[1], "speaker": sys.argv[2]}))' "${TEXT}" "${SPEAKER}")"
read -r code took < <(curl -s -o "${OUT}" -D "${TMP}/hdr" -w '%{http_code} %{time_total}\n' -H 'Content-Type: application/json' -d "${body}" "${URL}/synth")
hdr="$(tr -d '\r' < "${TMP}/hdr")"
[[ "${code}" == 200 ]] || { echo "FAIL: /synth returned ${code}: $(head -c 600 "${OUT}")"; exit 1; }
grep -qi '^content-type: audio/mpeg' <<<"${hdr}" || { echo "FAIL: not audio/mpeg"; exit 1; }
size=$(stat -c %s "${OUT}")
(( size > 2000 )) || { echo "FAIL: MP3 too small (${size} bytes)"; exit 1; }
h() { grep -i "^$1:" <<<"${hdr}" | cut -d' ' -f2-; }
echo "OK: ${size} bytes, $(h x-audio-seconds) s of audio (voice $(h x-voice), $(h x-attempts) take(s)) in ${took} s -> ${OUT}"
echo "checks: $(h x-checks)"
