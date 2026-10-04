#!/usr/bin/env bash
# Install (or update) the Lani local speech recognition worker as a systemd user service.
#
#   companion/stt-local/install.sh             # ROCm if /dev/kfd exists, else CPU
#   LANI_STT_TORCH=cpu install.sh               # force CPU torch
#   LANI_STT_NO_SERVICE=1 install.sh            # venv + model only
#   LANI_STT_MODEL=large-v3-turbo install.sh  # another model (see README.md)
#
# Everything goes to ~/.local/share/lani-stt (venv, model, app copy) and
# ~/.config/systemd/user/lani-stt.service. No sudo. Safe to re-run: steps that are
# already done are skipped, and the service restarts only if its files changed.
#
# Installed before the project was renamed from Fluent (docs/migrate-from-fluent.md)? Then
# ~/.local/share/fluent-stt is used as it is (nothing is downloaded again), FLUENT_STT_* and
# ~/.config/fluent-stt.env are still read, and fluent-stt.service is replaced by lani-stt.service.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../bin/lani-env.sh
. "${HERE}/../bin/lani-env.sh"
BASE="$(lani_share_dir lani-stt)"
APP="${BASE}/app"
VENV="${BASE}/venv"
PY="${VENV}/bin/python"
MODELS="${BASE}/models"
UNIT_DIR="${XDG_CONFIG_HOME:-${HOME}/.config}/systemd/user"
UNIT="${UNIT_DIR}/lani-stt.service"
ENV_FILE="${HOME}/.config/lani-stt.env"
LEGACY_ENV_FILE="${HOME}/.config/fluent-stt.env"   # read before ENV_FILE, by this script and by the unit

TORCH_VERSION="2.11.0"
ROCM_INDEX="https://download.pytorch.org/whl/rocm7.2"
CPU_INDEX="https://download.pytorch.org/whl/cpu"
# The voice worker's venv (companion/voice-local) has the same torch build: its files are
# copied with reflinks (btrfs/xfs: no extra space) instead of downloading 14 GB again.
VOICE_SITE="$(lani_share_dir lani-voice)/venv/lib/python3.12/site-packages"

ENV_MODEL=""
ENV_MODEL="$(cat "${LEGACY_ENV_FILE}" "${ENV_FILE}" 2>/dev/null | sed -n 's/^\(LANI\|FLUENT\)_STT_MODEL=//p' | tail -1)"
# Default model: large-v3-turbo (see README.md for the comparison). large-v3-sl is the stricter
# Slovene fine-tune of large-v3, downloaded from Hugging Face and converted here.
# LANI_STT_MODEL=<name> install.sh switches models and records it in ~/.config/lani-stt.env.
MODEL="${LANI_STT_MODEL:-${ENV_MODEL:-large-v3-turbo}}"
MODEL="${MODEL%.pt}"
SL_REPO="yuriyvnv/whisper-large-v3-slovenian"
SL_REV="c989a4ad5123750caab05279dcfe9b13a96b1560"

export UV_CACHE_DIR="${UV_CACHE_DIR:-${BASE}/uv-cache}"
export UV_PYTHON_INSTALL_DIR="${BASE}/python"

say() { printf '\033[1m==> %s\033[0m\n' "$*"; }

# 1. uv --------------------------------------------------------------------
UV="$(command -v uv || true)"
[[ -z "${UV}" ]] && command -v mise >/dev/null && UV="$(mise which uv 2>/dev/null || true)"
[[ -z "${UV}" && -x "${HOME}/.local/bin/uv" ]] && UV="${HOME}/.local/bin/uv"
if [[ -z "${UV}" ]]; then
  say "installing uv into ~/.local/bin"
  curl -LsSf https://astral.sh/uv/install.sh \
    | env UV_INSTALL_DIR="${HOME}/.local/bin" INSTALLER_NO_MODIFY_PATH=1 sh
  UV="${HOME}/.local/bin/uv"
fi
say "uv: ${UV} ($("${UV}" --version))"

# 2. torch flavour -----------------------------------------------------------
FLAVOR="${LANI_STT_TORCH:-auto}"
if [[ "${FLAVOR}" == auto ]]; then
  if [[ -r /dev/kfd ]]; then FLAVOR=rocm; else FLAVOR=cpu; fi
fi
case "${FLAVOR}" in
  rocm) TORCH_INDEX="${ROCM_INDEX}"; TORCH_TAG="+rocm7.2"; TRITON_DIST="triton_rocm" ;;
  cpu)  TORCH_INDEX="${CPU_INDEX}";  TORCH_TAG="+cpu";     TRITON_DIST="" ;;
  *) echo "LANI_STT_TORCH must be rocm, cpu or auto" >&2; exit 2 ;;
esac
say "torch ${TORCH_VERSION}${TORCH_TAG}"

# 3. venv + deps -------------------------------------------------------------
mkdir -p "${BASE}" "${APP}" "${MODELS}" "${BASE}/miopen/cache" "${BASE}/triton"
if [[ ! -x "${PY}" ]]; then
  say "creating venv (Python 3.12) in ${VENV}"
  "${UV}" venv --python 3.12 "${VENV}"
fi
SITE="$("${PY}" -c 'import sysconfig; print(sysconfig.get_paths()["purelib"])')"
TORCH_DIST="torch-${TORCH_VERSION}${TORCH_TAG}.dist-info"
if [[ ! -d "${SITE}/${TORCH_DIST}" && -d "${VOICE_SITE}/${TORCH_DIST}" ]]; then
  say "copying torch from the voice worker's venv (reflinks where the filesystem can)"
  cp -a --reflink=auto "${VOICE_SITE}/torch" "${VOICE_SITE}/torchgen" "${VOICE_SITE}/${TORCH_DIST}" "${SITE}/"
  if [[ -n "${TRITON_DIST}" ]]; then
    for d in "${VOICE_SITE}/triton" "${VOICE_SITE}/${TRITON_DIST}"-*.dist-info; do
      [[ -e "${d}" ]] && cp -a --reflink=auto "${d}" "${SITE}/"
    done
  fi
fi
# No-ops when the pinned versions are already installed. openai-whisper goes in with
# --no-deps: its "triton" requirement would pull the CUDA build over triton-rocm.
"${UV}" pip install --python "${PY}" --index-url "${TORCH_INDEX}" "torch==${TORCH_VERSION}${TORCH_TAG}"
"${UV}" pip install --python "${PY}" --no-deps -r "${HERE}/requirements.lock"
"${PY}" -c "import torch, whisper; print('torch', torch.__version__, '| gpu', torch.cuda.is_available(), '| whisper', whisper.__version__)"

# 4. model -------------------------------------------------------------------
case "${MODEL}" in
  large-v3-sl)
    MODEL_FILE="large-v3-sl.pt"
    if [[ ! -s "${MODELS}/${MODEL_FILE}" ]]; then
      say "downloading ${SL_REPO}@${SL_REV:0:8} (6.2 GB) and converting it to ${MODEL_FILE} (fp16, 3.1 GB)"
      DL="$(mktemp -d "${BASE}/download.XXXXXX")"
      trap 'rm -rf "${DL}"' EXIT
      for f in config.json model.safetensors.index.json model-00001-of-00002.safetensors model-00002-of-00002.safetensors; do
        curl -fL --retry 3 -o "${DL}/${f}" "https://huggingface.co/${SL_REPO}/resolve/${SL_REV}/${f}"
      done
      "${PY}" "${HERE}/convert_hf.py" "${DL}" "${MODELS}/${MODEL_FILE}.part" --fp16
      mv "${MODELS}/${MODEL_FILE}.part" "${MODELS}/${MODEL_FILE}"
      rm -rf "${DL}"
    fi
    ;;
  *)
    say "downloading Whisper ${MODEL} into ${MODELS} (skipped when cached)"
    "${PY}" -c "import sys, whisper; whisper._download(whisper._MODELS[sys.argv[1]], sys.argv[2], False)" "${MODEL}" "${MODELS}"
    ;;
esac
# The languages other than Slovene use the general multilingual model (LANI_STT_MODEL_GENERAL,
# large-v3-turbo; README.md, "Languages"). The worker never downloads one, so a primary model of
# another kind (the Slovene fine-tune) needs it downloaded here too.
ENV_GENERAL=""
ENV_GENERAL="$(cat "${LEGACY_ENV_FILE}" "${ENV_FILE}" 2>/dev/null | sed -n 's/^\(LANI\|FLUENT\)_STT_MODEL_GENERAL=//p' | tail -1)"
GENERAL="${LANI_STT_MODEL_GENERAL:-${ENV_GENERAL:-large-v3-turbo}}"
if [[ "${GENERAL}" != "${MODEL}" ]]; then
  say "downloading Whisper ${GENERAL} for the other languages into ${MODELS} (skipped when cached)"
  "${PY}" -c "import sys, whisper; whisper._download(whisper._MODELS[sys.argv[1]], sys.argv[2], False)" "${GENERAL}" "${MODELS}"
fi

# 5. app copy + unit + env ---------------------------------------------------
changed=0
for f in server.py; do
  if ! cmp -s "${HERE}/${f}" "${APP}/${f}"; then
    install -m 0644 "${HERE}/${f}" "${APP}/${f}"; changed=1
  fi
done
if [[ -n "${LANI_STT_NO_SERVICE:-}" ]]; then
  say "venv and model ready; service not installed (LANI_STT_NO_SERVICE)"
  exit 0
fi
mkdir -p "${UNIT_DIR}"
# The unit from before the rename serves the same port: lani-stt.service replaces it.
if [[ -e "${UNIT_DIR}/fluent-stt.service" ]]; then
  say "replacing fluent-stt.service (from before the rename) with lani-stt.service"
  systemctl --user disable --now fluent-stt.service >/dev/null 2>&1 || true
  rm -f "${UNIT_DIR}/fluent-stt.service"; changed=1
fi
# The unit, with the directory this node uses (BASE) in place of the template's.
unit_text() { sed -e "s#%h/.local/share/lani-stt#${BASE}#g" "${HERE}/lani-stt.service"; }
if ! cmp -s <(unit_text) "${UNIT}"; then
  unit_text > "${UNIT}.tmp" && chmod 0644 "${UNIT}.tmp" && mv "${UNIT}.tmp" "${UNIT}"; changed=1
fi
# ~/.config/lani-stt.env: local overrides. The model line is written only when it changes.
if [[ "${MODEL}" != "${ENV_MODEL%.pt}" && ( -n "${LANI_STT_MODEL:-}" || -n "${ENV_MODEL}" ) ]]; then
  { grep -v '^LANI_STT_MODEL=' "${ENV_FILE}" 2>/dev/null || true; echo "LANI_STT_MODEL=${MODEL}"; } > "${ENV_FILE}.tmp"
  mv "${ENV_FILE}.tmp" "${ENV_FILE}"; changed=1
fi
if [[ "${FLAVOR}" == cpu ]] && ! cat "${LEGACY_ENV_FILE}" "${ENV_FILE}" 2>/dev/null | grep -q '^\(LANI\|FLUENT\)_STT_DEVICE='; then
  echo "LANI_STT_DEVICE=cpu" >> "${ENV_FILE}"; changed=1
fi
systemctl --user daemon-reload
systemctl --user enable lani-stt.service >/dev/null
if [[ "${changed}" == 1 ]] || ! systemctl --user is-active --quiet lani-stt.service; then
  say "(re)starting lani-stt.service"
  systemctl --user restart lani-stt.service
else
  say "lani-stt.service already running and up to date"
fi

if [[ "$(loginctl show-user "${USER}" -p Linger --value 2>/dev/null)" != yes ]]; then
  echo "note: user lingering is off, so the service runs only while you are logged in."
  echo "      To keep it running after logout: loginctl enable-linger ${USER}"
fi
say "done. Check it with: ${HERE}/smoke.sh   (model load takes ~10-20 s)"
