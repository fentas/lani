#!/usr/bin/env bash
# Install (or update) Lani's Drama 3 worker: Fish Audio Drama 3 for the bridge's batch clips (voice-build --drama3).
# Built with Fish Audio.
#
#   companion/voice-drama3/install.sh                       # venvs, fish-speech, the app copy and run.sh
#   LANI_DRAMA3_WEIGHTS=/path/to/drama-3 install.sh          # the model's directory (default /mnt/scratch/fish-drama3/weights/drama-3)
#   LANI_DRAMA3_DOWNLOAD=1 install.sh                        # download what is missing: the model (39 GB), Whisper (3 GB)
#   LANI_DRAMA3_VENV=/path/to/venv install.sh                # an existing venv for the model (made if missing)
#   LANI_DRAMA3_ASR_VENV=/path/to/venv-asr install.sh        # an existing venv for the Whisper check (made if missing)
#   LANI_DRAMA3_SRC=/path/to/fish-speech install.sh          # an existing fish-speech checkout (cloned if missing)
#   LANI_DRAMA3_SERVICE=1 install.sh                         # also copy the user unit (never enabled, never started)
#
# Everything goes to ~/.local/share/lani-drama3 (LANI_DRAMA3_HOME): the venvs, fish-speech, the app copy and run.sh,
# which voice-build --drama3 --run starts and stops (the worker is no service: it holds ~7 GB of VRAM next to Gepard
# and the STT worker). No sudo. Safe to re-run: what is there is kept; run it again after refs.py pick (it copies
# voices.json). The model and fish-speech are under the Fish Audio Community License (LICENSES/).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE="${LANI_DRAMA3_HOME:-${HOME}/.local/share/lani-drama3}"
APP="${BASE}/app"
VENV="${LANI_DRAMA3_VENV:-${BASE}/venv}"
ASR_VENV="${LANI_DRAMA3_ASR_VENV:-${BASE}/venv-asr}"
SRC="${LANI_DRAMA3_SRC:-${BASE}/fish-speech}"
WEIGHTS="${LANI_DRAMA3_WEIGHTS:-/mnt/scratch/fish-drama3/weights/drama-3}"
# Whisper large-v3 for the text check: the spike's copy on this node when it is there
SPIKE_WHISPER=/mnt/scratch/fish-drama3/weights/faster-whisper-large-v3
[[ -e "${SPIKE_WHISPER}/model.bin" ]] && DEFAULT_WHISPER="${SPIKE_WHISPER}" || DEFAULT_WHISPER="${BASE}/faster-whisper-large-v3"
WHISPER="${LANI_DRAMA3_WHISPER:-${DEFAULT_WHISPER}}"
UNIT_DIR="${XDG_CONFIG_HOME:-${HOME}/.config}/systemd/user"

TORCH_VERSION="2.11.0"
ROCM_INDEX="https://download.pytorch.org/whl/rocm7.2"
FISH_REPO="https://github.com/fishaudio/fish-speech.git"
FISH_REV="541566932abbaebcb0a40e562a1f846738383f38"
MODEL_REPO="fishaudio/drama-3"
MODEL_REV="b2b5ee998034b2975720906c815e7fb0e9f007bd"
WHISPER_REPO="Systran/faster-whisper-large-v3"
WHISPER_REV="edaa852ec7e145841d8ffdb056a99866b5f0a478"

export UV_CACHE_DIR="${UV_CACHE_DIR:-${BASE}/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${BASE}/python"

say() { printf '\033[1m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

[[ -r /dev/kfd ]] || die "no /dev/kfd: Drama 3 needs the AMD GPU through ROCm (the CPU is far too slow for it)"

# 1. uv --------------------------------------------------------------------------------------------------------
UV="$(command -v uv || true)"
[[ -z "${UV}" ]] && command -v mise >/dev/null && UV="$(mise which uv 2>/dev/null || true)"
[[ -z "${UV}" && -x "${HOME}/.local/bin/uv" ]] && UV="${HOME}/.local/bin/uv"
[[ -n "${UV}" ]] || die "uv is needed (https://docs.astral.sh/uv/; companion/voice-local/install.sh installs it too)"
say "uv: ${UV} ($("${UV}" --version))"
mkdir -p "${BASE}" "${APP}"

# 2. the model's venv: torch for ROCm, then the frozen rest -------------------------------------------------------
if [[ ! -x "${VENV}/bin/python" ]]; then
  say "creating the model's venv (Python 3.12) in ${VENV}"
  "${UV}" venv --python 3.12 "${VENV}"
fi
"${UV}" pip install --python "${VENV}/bin/python" --index-url "${ROCM_INDEX}" \
  "torch==${TORCH_VERSION}+rocm7.2" "torchaudio==${TORCH_VERSION}+rocm7.2"
"${UV}" pip install --python "${VENV}/bin/python" --no-deps -r "${HERE}/requirements.lock"

# 3. the Whisper check's venv (CPU) --------------------------------------------------------------------------------
if [[ ! -x "${ASR_VENV}/bin/python" ]]; then
  say "creating the Whisper venv (Python 3.12) in ${ASR_VENV}"
  "${UV}" venv --python 3.12 "${ASR_VENV}"
fi
"${UV}" pip install --python "${ASR_VENV}/bin/python" --no-deps -r "${HERE}/requirements-asr.lock"

# 4. fish-speech at the pinned commit (not changed: drama3_rocm.py patches it at run time) ------------------------
if [[ ! -d "${SRC}/fish_speech" ]]; then
  say "cloning fish-speech into ${SRC}"
  git clone --filter=blob:none "${FISH_REPO}" "${SRC}"
fi
if [[ "$(git -C "${SRC}" rev-parse HEAD)" != "${FISH_REV}" ]]; then
  say "fish-speech at ${FISH_REV:0:7}"
  git -C "${SRC}" fetch --quiet origin "${FISH_REV}" 2>/dev/null || git -C "${SRC}" fetch --quiet origin
  git -C "${SRC}" -c advice.detachedHead=false checkout --quiet "${FISH_REV}"
fi

# 5. the weights: Drama 3 and Whisper large-v3 (CTranslate2) ------------------------------------------------------
fetch() { # repo rev dir python
  [[ -n "${LANI_DRAMA3_DOWNLOAD:-}" ]] || die "$1 is not in $3: set LANI_DRAMA3_DOWNLOAD=1 to download it, or point the variable at a copy"
  say "downloading $1 into $3"
  "$4" - "$1" "$2" "$3" <<'PY'
import sys
from huggingface_hub import snapshot_download
print(snapshot_download(sys.argv[1], revision=sys.argv[2], local_dir=sys.argv[3]))
PY
}
for f in config.json codec.pth tokenizer.json model.safetensors.index.json; do
  [[ -e "${WEIGHTS}/${f}" ]] || { fetch "${MODEL_REPO}" "${MODEL_REV}" "${WEIGHTS}" "${VENV}/bin/python"; break; }
done
[[ -e "${WHISPER}/model.bin" ]] || fetch "${WHISPER_REPO}" "${WHISPER_REV}" "${WHISPER}" "${ASR_VENV}/bin/python"
"${VENV}/bin/python" -c "import torch, safetensors, tokenizers; print('torch', torch.__version__, '| gpu', torch.cuda.is_available())"

# 6. the app copy and run.sh ----------------------------------------------------------------------------------------
for f in server.py engine.py checks.py asr.py drama3_rocm.py refs.py voices.json; do
  cmp -s "${HERE}/${f}" "${APP}/${f}" || install -m 0644 "${HERE}/${f}" "${APP}/${f}"
done
cat > "${BASE}/run.sh.tmp" <<RUN
#!/usr/bin/env bash
# Lani's Drama 3 worker (written by companion/voice-drama3/install.sh). voice-build --drama3 --run starts it with
# LANI_DRAMA3_PORT and LANI_DRAMA3_LOCK set, and stops it after. Overrides: ~/.config/lani-drama3.env.
set -a
LANI_DRAMA3_HOME="${BASE}"
LANI_DRAMA3_WEIGHTS="${WEIGHTS}"
LANI_DRAMA3_SRC="${SRC}"
LANI_DRAMA3_WHISPER="${WHISPER}"
LANI_DRAMA3_ASR_PYTHON="${ASR_VENV}/bin/python"
# ROCm: device 0 is the RX 9070 (device 1 is the Ryzen iGPU); kernel caches next to the install
HIP_VISIBLE_DEVICES=0
TRITON_CACHE_DIR="${BASE}/triton"
MIOPEN_USER_DB_PATH="${BASE}/miopen"
MIOPEN_CUSTOM_CACHE_DIR="${BASE}/miopen/cache"
HF_HUB_OFFLINE=1
PYTHONUNBUFFERED=1
[[ -r "\${XDG_CONFIG_HOME:-\${HOME}/.config}/lani-drama3.env" ]] && . "\${XDG_CONFIG_HOME:-\${HOME}/.config}/lani-drama3.env"
set +a
exec "${VENV}/bin/python" "${APP}/server.py" "\$@"
RUN
chmod 0755 "${BASE}/run.sh.tmp" && mv "${BASE}/run.sh.tmp" "${BASE}/run.sh"
mkdir -p "${BASE}/triton" "${BASE}/miopen/cache"

# 7. the unit, only when asked: copied, never enabled or started ------------------------------------------------------
if [[ -n "${LANI_DRAMA3_SERVICE:-}" ]]; then
  mkdir -p "${UNIT_DIR}"
  sed -e "s#%h/.local/share/lani-drama3#${BASE}#g" "${HERE}/lani-drama3.service" > "${UNIT_DIR}/lani-drama3.service"
  systemctl --user daemon-reload
  say "copied lani-drama3.service (not enabled: start it by hand only when no batch runs, and stop it after)"
fi
say "done: ${BASE}/run.sh. Check it with ${HERE}/smoke.sh (it starts the worker, says one line, stops it)."
