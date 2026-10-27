#!/usr/bin/env bash
# ffmpeg and ffprobe for the bridge's smoke (its voice checks), on a GitHub runner (ci.yml), without a slow mirror
# costing the job: on 2026-10-27 the runner's Ubuntu mirror sent the 87 packages (63 MB) at about 50 kB/s, and the
# job's 20 minutes ran out in this step.
#
# Usage: .github/scripts/ffmpeg.sh <cache directory>
#        .github/scripts/ffmpeg.sh --cut-off      after the step was stopped at its time limit (ci.yml's backstop)
#
# The packages are Ubuntu's, from the runner's own apt sources (apt checks each one against the signed package lists),
# kept in <cache directory> between runs (ci.yml caches it): a run with the cache downloads nothing. What isn't there
# is downloaded in a few bounded tries, each going on from what the last one fetched. Then the packages are installed
# from that directory alone (no network while dpkg runs: it is never cut off half-way). If ffmpeg still isn't there,
# the job goes on without it: the smoke skips the voice checks that need it, and the job's summary says so. This
# script fails only on a wrong call. It prints (GITHUB_OUTPUT) installed=true|false and fetched=<packages downloaded>.
#
# Environment (for a test of the script): LANI_APT_UPDATE_SECS (each try of apt-get update, 45), LANI_APT_FETCH_SECS
# (each try of the download, 90), LANI_APT_TRIES (2), LANI_APT_OPTS (more apt options, e.g. a download limit).
set -uo pipefail

cache="${1:?usage: ffmpeg.sh <cache directory> | --cut-off}"
update_secs="${LANI_APT_UPDATE_SECS:-45}"
fetch_secs="${LANI_APT_FETCH_SECS:-90}"
tries="${LANI_APT_TRIES:-2}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
output="${GITHUB_OUTPUT:-/dev/null}"

root() { if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi; }

# half there is worse than none: the smoke would see an ffmpeg on the PATH and run the voice checks with it
unbroken() {
  for b in ffmpeg ffprobe; do
    p="$(command -v "$b" || true)"
    if [ -n "$p" ] && ! "$p" -version > /dev/null 2>&1; then root rm -f "$p"; fi
  done
}

# [why] it isn't there (after [secs] s): a warning, and the job's summary
missing() {
  echo "::warning title=ffmpeg not installed::$1. The smoke skips the voice checks that need ffmpeg."
  {
    echo "### ⚠️ ffmpeg not installed"
    echo
    echo "Not installed${2:+ after $2 s}: $1."
    echo
    echo "The smoke ran without it: its voice checks that need ffmpeg (about 30) were skipped, not passed."
  } >> "$summary"
}

if [ "$cache" = --cut-off ]; then
  if ffmpeg -version > /dev/null 2>&1 && ffprobe -version > /dev/null 2>&1; then exit 0; fi
  unbroken
  missing "the step was stopped at its time limit" ""
  exit 0
fi
case "$cache" in /*) ;; *) cache="$PWD/$cache" ;; esac
# the runner's user owns the cache (actions/cache saves it as that user); apt downloads as root into it
# (a lock still held by a try that was cut off is waited for, not failed on)
apt=(apt-get -q -o "Dir::Cache::Archives=$cache/" -o APT::Sandbox::User=root -o Acquire::Retries=3
  -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 -o DPkg::Lock::Timeout=30)
# shellcheck disable=SC2206 # word splitting wanted: a list of options
[ -n "${LANI_APT_OPTS:-}" ] && apt+=(${LANI_APT_OPTS})
debs() { find "$cache" -maxdepth 1 -name '*.deb' | wc -l; }

mkdir -p "$cache/partial"
had="$(debs)"
started="$(date +%s)"

# the package lists: usually a second; lists that don't come leave the ones the runner's image has
updated=false
for ((i = 1; i <= tries; i++)); do
  if root timeout -k 10 "$update_secs" "${apt[@]}" update; then
    updated=true
    break
  fi
  echo "::warning::apt-get update didn't finish within ${update_secs} s (try $i of $tries)"
done

# the packages not in the cache yet; a try cut off keeps what it fetched (and the part of the one it was on)
fetched=false
for ((i = 1; i <= tries; i++)); do
  if root timeout -k 10 "$fetch_secs" "${apt[@]}" install -y --no-install-recommends --download-only ffmpeg; then
    fetched=true
    break
  fi
  echo "::warning::ffmpeg's packages didn't all come within ${fetch_secs} s (try $i of $tries; $(debs) in the cache)"
done

why=""
if ! $fetched; then
  why="the mirror didn't send its packages in $tries tries of $fetch_secs s"
  $updated || why="$why, nor the package lists in $tries tries of $update_secs s"
  why="$why ($(debs) packages in the cache, kept for the next run)"
elif ! root "${apt[@]}" install -y --no-install-recommends --no-download ffmpeg; then
  why="apt-get install failed (see the step's log)"
fi
if [ -z "$why" ] && ! { ffmpeg -version > /dev/null 2>&1 && ffprobe -version > /dev/null 2>&1; }; then
  why="ffmpeg -version or ffprobe -version failed after the install"
fi
[ -z "$why" ] || unbroken

# only the packages the lists still have (an update replaced the others): the cache doesn't grow run by run
root "${apt[@]}" autoclean > /dev/null 2>&1 || true
root chown -R "$(id -u):$(id -g)" "$cache"
now="$(debs)"
secs=$(($(date +%s) - started))

if [ -z "$why" ]; then
  version="$(ffmpeg -version | head -n 1)"
  echo "installed=true" >> "$output"
  echo "fetched=$((now > had ? now - had : 0))" >> "$output"
  echo "$version: $now packages, $had of them from the cache, in $secs s"
  echo "ffmpeg: \`$version\`, $now packages ($had from the cache) in $secs s." >> "$summary"
else
  echo "installed=false" >> "$output"
  echo "fetched=$((now > had ? now - had : 0))" >> "$output"
  missing "$why" "$secs"
fi
exit 0
