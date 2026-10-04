#!/usr/bin/env bash
# Install (or update) the Lani local voice worker as a systemd user service.
#
#   companion/voice-local/install.sh            # ROCm if /dev/kfd exists, else CPU
#   LANI_VOICE_TORCH=cpu install.sh              # force CPU torch
#   LANI_VOICE_NO_SERVICE=1 install.sh           # venv + models only
#
# Everything goes to ~/.local/share/lani-voice (venv, models, app copy) and
# ~/.config/systemd/user/lani-voice.service. No sudo. Safe to re-run: steps
# that are already done are skipped, and the service restarts only if its
# files changed.
#
# Installed before the project was renamed from Fluent (docs/migrate-from-fluent.md)? Then
# ~/.local/share/fluent-voice is used as it is (nothing is downloaded again), FLUENT_VOICE_* and
# ~/.config/fluent-voice.env are still read, and fluent-voice.service is replaced by lani-voice.service.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../bin/lani-env.sh
. "${HERE}/../bin/lani-env.sh"
BASE="$(lani_share_dir lani-voice)"
APP="${BASE}/app"
VENV="${BASE}/venv"
PY="${VENV}/bin/python"
UNIT_DIR="${XDG_CONFIG_HOME:-${HOME}/.config}/systemd/user"
UNIT="${UNIT_DIR}/lani-voice.service"

TORCH_VERSION="2.11.0"
ROCM_INDEX="https://download.pytorch.org/whl/rocm7.2"
CPU_INDEX="https://download.pytorch.org/whl/cpu"
MODEL_REPO="texdata/Gepard-Slovenian-TTS"
MODEL_REV="cd7666910de8bbbb7caf4417763571eb6f3988c5"
CODEC_REPO="nvidia/nemo-nano-codec-22khz-1.89kbps-21.5fps"
CODEC_REV="fc00890b604aa2de298d2641ffc6c5f6caf8c4d7"
CODEC_FILE="nemo-nano-codec-22khz-1.89kbps-21.5fps.nemo"

export HF_HOME="${BASE}/hf"
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
FLAVOR="${LANI_VOICE_TORCH:-auto}"
if [[ "${FLAVOR}" == auto ]]; then
  if [[ -r /dev/kfd ]]; then FLAVOR=rocm; else FLAVOR=cpu; fi
fi
case "${FLAVOR}" in
  rocm) TORCH_INDEX="${ROCM_INDEX}"; TORCH_TAG="+rocm7.2" ;;
  cpu)  TORCH_INDEX="${CPU_INDEX}";  TORCH_TAG="+cpu" ;;
  *) echo "LANI_VOICE_TORCH must be rocm, cpu or auto" >&2; exit 2 ;;
esac
say "torch ${TORCH_VERSION}${TORCH_TAG}"

# 3. venv + deps -------------------------------------------------------------
mkdir -p "${BASE}" "${APP}" "${BASE}/miopen/cache"
if [[ ! -x "${PY}" ]]; then
  say "creating venv (Python 3.12) in ${VENV}"
  "${UV}" venv --python 3.12 "${VENV}"
fi
# Both steps are no-ops when the pinned versions are already installed.
"${UV}" pip install --python "${PY}" --index-url "${TORCH_INDEX}" \
  "torch==${TORCH_VERSION}${TORCH_TAG}" "torchaudio==${TORCH_VERSION}${TORCH_TAG}"
"${UV}" pip install --python "${PY}" --no-deps -r "${HERE}/requirements.lock"
"${PY}" -c "import torch, transformers, gepard_inference, nemo; print('torch', torch.__version__, '| gpu', torch.cuda.is_available(), '| transformers', transformers.__version__)"

# 4. models (pinned revisions, into ${HF_HOME}) ------------------------------
say "downloading models into ${HF_HOME} (skipped when cached)"
"${PY}" - <<PY
from huggingface_hub import hf_hub_download, snapshot_download
p = snapshot_download("${MODEL_REPO}", revision="${MODEL_REV}",
                      allow_patterns=["*.json", "*.safetensors", "*.jinja", "samples/*"])
c = hf_hub_download("${CODEC_REPO}", "${CODEC_FILE}", revision="${CODEC_REV}")
print("model:", p); print("codec:", c)
PY

# 5. app copy + unit ---------------------------------------------------------
changed=0
for f in server.py fastgen.py voices.json; do
  if ! cmp -s "${HERE}/${f}" "${APP}/${f}"; then
    install -m 0644 "${HERE}/${f}" "${APP}/${f}"; changed=1
  fi
done
if [[ -n "${LANI_VOICE_NO_SERVICE:-}" ]]; then
  say "venv and models ready; service not installed (LANI_VOICE_NO_SERVICE)"
  exit 0
fi
mkdir -p "${UNIT_DIR}"
# The unit from before the rename serves the same port: lani-voice.service replaces it.
if [[ -e "${UNIT_DIR}/fluent-voice.service" ]]; then
  say "replacing fluent-voice.service (from before the rename) with lani-voice.service"
  systemctl --user disable --now fluent-voice.service >/dev/null 2>&1 || true
  rm -f "${UNIT_DIR}/fluent-voice.service"; changed=1
fi
# The unit, with the directory this node uses (BASE) in place of the template's.
unit_text() { sed -e "s#%h/.local/share/lani-voice#${BASE}#g" "${HERE}/lani-voice.service"; }
if ! cmp -s <(unit_text) "${UNIT}"; then
  unit_text > "${UNIT}.tmp" && chmod 0644 "${UNIT}.tmp" && mv "${UNIT}.tmp" "${UNIT}"; changed=1
fi
if [[ "${FLAVOR}" == cpu && ! -e "${HOME}/.config/lani-voice.env" && ! -e "${HOME}/.config/fluent-voice.env" ]]; then
  echo "LANI_VOICE_DEVICE=cpu" > "${HOME}/.config/lani-voice.env"; changed=1
fi
systemctl --user daemon-reload
systemctl --user enable lani-voice.service >/dev/null
if [[ "${changed}" == 1 ]] || ! systemctl --user is-active --quiet lani-voice.service; then
  say "(re)starting lani-voice.service"
  systemctl --user restart lani-voice.service
else
  say "lani-voice.service already running and up to date"
fi

if [[ "$(loginctl show-user "${USER}" -p Linger --value 2>/dev/null)" != yes ]]; then
  echo "note: user lingering is off, so the service runs only while you are logged in."
  echo "      To keep it running after logout: loginctl enable-linger ${USER}"
fi
say "done. Check it with: ${HERE}/smoke.sh   (model load takes ~35 s)"
