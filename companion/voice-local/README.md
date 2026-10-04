# voice-local: Gepard Slovenian TTS worker

A local Slovene text-to-speech engine for Lani. The bridge uses it when the
ElevenLabs monthly quota runs low. It runs
[texdata/Gepard-Slovenian-TTS](https://huggingface.co/texdata/Gepard-Slovenian-TTS)
(Apache-2.0, a LoRA fine-tune of Gepard-1.0, ~555M parameters, Qwen3.5 backbone)
and decodes with NVIDIA NanoCodec
(`nvidia/nemo-nano-codec-22khz-1.89kbps-21.5fps`, NVIDIA Open Model License),
22,050 Hz mono.

It runs on the AMD Radeon RX 9070 (gfx1201) through the PyTorch ROCm 7.2
wheels. No system ROCm or HIP packages are needed: the wheels bring their own
HIP runtime and only need the kernel driver (`/dev/kfd`, `render` group).

## HTTP contract (127.0.0.1:8795)

| Request | Response |
|---|---|
| `GET /health` | `200 {"ok": true, "engine": "gepard", "device": "rocm gfx1201 (...)", "busy": false, "voices": [...], "default_voice": "ana", "load_seconds": 38.6}` when ready. `503 {"ok": false, "loading": true}` while the model loads. `503 {"ok": false, "error": "..."}` if loading failed. |
| `POST /synth` `{"text": "...", "speaker": "..."}` | `200 audio/mpeg` (MP3, 64 kbit/s, 22.05 kHz mono). Headers `X-Voice`, `X-Audio-Seconds`, `X-Synth-Seconds`. |
| | `400` if the text is empty or longer than 400 characters, or the JSON is invalid. |
| | `503 {"error": "busy"}` if another synthesis is running (one at a time, no queue). `503` while loading. The client should fall back to ElevenLabs. |
| | `500` if synthesis failed. |

The server adds the `sl: ` language tag (do not send it; a leading `sl:` is
removed). Text is split into sentences, and each sentence is synthesized
separately and joined with a 0.18 s pause. The model is best on short
sentences.

`speaker` is optional:

- none or empty: the default voice `ana`
- a voice name or alias (`ana`/`female`/`tutor`, `marko`/`male`, `nina`): that voice
- any other name (for example a role-play character such as `Baker`): a fixed
  voice from the pool, picked by a crc32 of the name. The same name always gets
  the same voice.

## Install, stop, remove

```bash
companion/voice-local/install.sh     # venv + pinned deps + models + user service (idempotent)
companion/voice-local/smoke.sh       # waits for /health, synthesizes one sentence
```

`install.sh` puts everything outside the repo:

| Path | What | Size |
|---|---|---|
| `~/.local/share/lani-voice/venv` | Python 3.12 venv (torch 2.11.0+rocm7.2, NeMo 2.4.0, transformers 5.3.0) | ~17 GB (the ROCm torch wheel is 13 GB of GPU libraries) |
| `~/.local/share/lani-voice/hf` | Hugging Face cache: Gepard model (1.1 GB) + NanoCodec (0.45 GB) | ~1.6 GB |
| `~/.local/share/lani-voice/uv-cache` | uv download cache (on btrfs it shares blocks with the venv) | can be removed with `uv cache clean` |
| `~/.local/share/lani-voice/{app,voices,miopen,python}` | server copy, cached voice codes, MIOpen kernel DB, uv-managed Python | < 150 MB |
| `~/.config/systemd/user/lani-voice.service` | the unit (copy of `lani-voice.service`) | |
| `~/.config/lani-voice.env` | optional overrides (see below) | |

With the CPU wheels (`LANI_VOICE_TORCH=cpu ./install.sh`) the venv is ~3 GB.

Service control:

```bash
systemctl --user status lani-voice
journalctl --user -u lani-voice -f
systemctl --user stop lani-voice                # stop now
systemctl --user disable --now lani-voice       # stop and do not start at login
systemctl --user restart lani-voice             # after editing ~/.config/lani-voice.env
```

Remove completely: `systemctl --user disable --now lani-voice`, then delete
`~/.config/systemd/user/lani-voice.service` and `~/.local/share/lani-voice`.

The service is a user service. Without `loginctl enable-linger $USER` it runs
only while the user has a session.

## Resources (measured on this node)

- Model load: ~35-39 s (NeMo import + codec restore + warm-up). `/health` gives 503 during this time.
- Memory: ~2.8 GB RSS in RAM (peak 3.2 GB during load); ~2.5 GB VRAM.
  The unit sets `MemoryHigh=6G`, `MemoryMax=10G`.
- Speed on the RX 9070, after load: 0.5-1.3 s per test sentence (1-4 s of audio),
  about 0.3x real time. A 321-character paragraph (28 s of audio) takes 9 s.
  The first time a new audio length is decoded, MIOpen compiles a kernel
  (~0.3 s extra, cached on disk).
- Speed on the CPU (12 threads, float32), for reference: 3-15 s per sentence,
  about 2-3x real time. Usable as a slow fallback only.

## What was needed to make it work

1. **Python 3.12** (uv-managed). `gepard-inference` requires `>=3.12`; the
   system Python 3.14 is too new for the ML wheels.
2. **torch 2.11.0+rocm7.2** from `download.pytorch.org/whl/rocm7.2`. It
   includes gfx1201 kernels; no `HSA_OVERRIDE_GFX_VERSION` is needed. torch
   lists two devices: 0 = RX 9070 (gfx1201), 1 = the Ryzen iGPU (gfx1036).
   The unit sets `HIP_VISIBLE_DEVICES=0`; the server also picks the device with
   the most memory.
3. **`nemo-toolkit[tts]==2.4.0`** installed without problems on Python 3.12
   (all wheels, no compiler needed), then **`gepard[inference]`** from
   GitHub `nineninesix-ai/gepard-inference@eb87bc4` (it is not on PyPI), which
   re-pins `transformers==5.3.0` over NeMo's older one. The full set is frozen
   in `requirements.lock` and installed with `--no-deps`.
4. **`MIOPEN_FIND_MODE=FAST`.** Without it, MIOpen runs a kernel search for
   every new input length in the NanoCodec convolutions: 4-10 s per clip. The
   server also pads codes to a multiple of 32 frames (the padding is masked out)
   so the decoder sees few shapes.
5. **Vectorised sampler (`fastgen.py`).** Upstream `GepardRunner` samples the
   32 codebook heads in a Python loop with ~1000 `.item()` calls per audio
   frame. On the GPU that made synthesis slower than on the CPU (RTF 2.7). The
   subclass does the same maths (CFG, repetition penalty, temperature,
   multinomial) on one padded tensor: RTF 0.3. Set `LANI_VOICE_FAST=0` to
   compare with the upstream sampler.

## Voices and settings

The model has no speaker id. Without a reference, the voice depends on the
seed and on the text, so a fixed seed does **not** give the same voice across
sentences (pitch varied by 50-120 Hz between clips). Gepard has a
voice-cloning input, and it still works after the Slovene LoRA. A voice is
therefore a reference plus a fixed seed (`voices.json`):

| Voice | Reference | Seed | Pitch (median F0 over 8 clips) |
|---|---|---|---|
| `ana` (default) | `samples/02_nevtralno.wav` from the model repo (neutral female) | 1 | 190-215 Hz |
| `marko` | made by the model itself: seed 12, no reference, "Danes je lep sončen dan in ptice pojejo na drevesih.", cached in `~/.local/share/lani-voice/voices/` | 2 | 125-140 Hz (male) |
| `nina` | `samples/07_presenecenje.wav` (brighter female) | 0 | 213-243 Hz |

Generation: `temperature=0.3`, `cfg_scale=3.5`, `cfg_frames=25`,
`repetition_penalty=1.45`, `repetition_window=32`, `top_k=0`. `max_frames` is
`60 + 2.6 x characters` per sentence; a sentence that does not stop is retried
once with another seed. Output is peak-normalised to -1 dBFS. The same text and
voice give the same audio every time.

How this was chosen (`bench.py` plus a Whisper-large-v3-turbo check, not in
the repo): 10 seeds without a reference, 8 reference clips, and two settings
(model card 0.4/4.5 and 0.3/3.5), each on the 8 test items. Mean character
error rate of Whisper transcripts: 9-29% without a reference, 5-14% with one.
0.3/3.5 was slightly better (6-9%) than 0.4/4.5 (6-11%) with the same
reference. The final voices: ana 5.0%, marko 6.2%, nina 5.4%.

Known weak points (heard in the transcripts on all voices):

- Palatal `lj`/`nj`: "Nedeljsko" often comes out as "Nedelesko"; "čebelnjak"
  as "čebelniak" or "če bel njak". This is the worst word in the test set.
- Single words are less stable than sentences (ending sometimes sounds like a
  question; "hvala" was once heard as "vala").
- Grapheme model, no text normalisation: write numbers, dates and
  abbreviations out as words.

## Configuration (`~/.config/lani-voice.env`)

| Variable | Default | |
|---|---|---|
| `LANI_VOICE_DEVICE` | `auto` | `cuda` (ROCm is also `cuda` in torch) or `cpu` |
| `LANI_VOICE_PORT` / `LANI_VOICE_HOST` | `8795` / `127.0.0.1` | do not use 8790 or 8791 |
| `LANI_VOICE_DEFAULT` | `ana` | default voice |
| `LANI_VOICE_TEMPERATURE` / `LANI_VOICE_CFG` / `LANI_VOICE_CFG_FRAMES` | `0.3` / `3.5` / `25` | generation |
| `LANI_VOICE_MP3_BITRATE` | `64k` | |
| `LANI_VOICE_THREADS` | `12` | CPU threads (CPU mode) |
| `LANI_VOICE_DTYPE` | bf16 on GPU, float32 on CPU | |
| `LANI_VOICE_FAST` | `1` | `0` = upstream sampler |

## Files

- `server.py`: HTTP worker (standard library `http.server` + model libraries)
- `fastgen.py`: vectorised frame sampler for `GepardRunner`
- `voices.json`: voices, aliases, speaker pool
- `bench.py`: timing and voice sweeps without HTTP (`--sweep 0-9`, `--voice ref:samples/X.wav@seed`, `--voice codes:FILE.pt@seed`)
- `install.sh`, `lani-voice.service`, `smoke.sh`, `requirements.lock`
