# stt-local: Whisper speech recognition worker

Local speech recognition for Lani: Slovene, and Italian and the other languages a learner speaks
(see "Languages"). The app records a spoken answer, the bridge sends it
here (`POST /stt`, see `companion/bridge/src/features/stt.ts`), and the answer is graded like any
recognizer result. The app uses it when the phone's recognizer can't take the language, and when the
learner picks "🎯 Natančneje · More accurate".

It runs [openai-whisper](https://github.com/openai/whisper) `large-v3-turbo` (MIT) on the AMD
Radeon RX 9070 (gfx1201) with the PyTorch ROCm 7.2 wheels, next to the Gepard voice worker
(`companion/voice-local`). CTranslate2 / faster-whisper has no ROCm build, so it is plain PyTorch.

## HTTP contract (127.0.0.1:8796)

| Request | Response |
|---|---|
| `GET /health` | `200 {"ok": true, "engine": "whisper", "model": "large-v3-turbo", "device": "rocm gfx1201 (...)", "busy": false, "prompt": true, "load_seconds": 8.3, "models": [{"model", "languages", "present", "loaded", "idle_seconds"}]}` when ready (`model`: the primary one). `503 {"ok": false, "loading": true}` while the primary model loads, `503 {"ok": false, "error": "..."}` if loading failed. |
| `GET /health?language=it` | The same, and `"language": {"code": "it", "model": "large-v3-turbo", "available": true, "loaded": false}`. A model that is present but not loaded starts loading in the background, so the recognition that follows doesn't wait. |
| `POST /transcribe?language=sl&prompt=<expected text>` with the audio as the body | `200 {"text", "segments": [{"start", "end", "text", "avg_logprob", "no_speech_prob", "words": [{"word", "start", "end", "prob"}]}], "language", "duration", "took", "model"}` |
| | `400` no audio, audio under 0.1 s, undecodable audio or an unknown language. `413` more than 2 MB or longer than 30 s. `415` not m4a, ogg, webm, wav or mp3 (checked from the first bytes). |
| | `503 {"error": "busy"}` when another recognition (or a model load) held the GPU for 15 s (`LANI_STT_QUEUE_SECONDS`); one runs at a time, and each takes well under a second. `503` while loading. `503 {"error": "not available", "language", "model"}` when the language's model isn't on disk or failed to load. `500` if recognition failed. |

`language` defaults to `sl`. `prompt` (at most 300 characters) is the sentence the learner is
expected to say; it goes to Whisper as the initial prompt (see "Why the prompt" below). `prob` is
Whisper's probability of the word (0..1); the app shows words under 0.3 as a pronunciation hint.

The audio is written to a temporary file in `$XDG_RUNTIME_DIR` (tmpfs; MediaRecorder's MP4 has
its index at the end, so ffmpeg can't read it from a pipe), decoded to 16 kHz mono and deleted
before the answer is sent. The journal gets lengths and timings only, not the prompt or the text.

## Install, stop, remove

```bash
companion/stt-local/install.sh     # venv + pinned deps + model + user service (idempotent)
companion/stt-local/smoke.sh       # waits for /health, transcribes one sentence (voiced by the voice worker)
```

| Path | What | Size |
|---|---|---|
| `~/.local/share/lani-stt/venv` | Python 3.12 venv: torch 2.11.0+rocm7.2, openai-whisper 20250625 | ~15 GB, but see below |
| `~/.local/share/lani-stt/models` | `large-v3-turbo.pt` | 1.6 GB |
| `~/.local/share/lani-stt/{app,python,miopen,triton,uv-cache}` | server copy, uv-managed Python, kernel caches | < 200 MB |
| `~/.config/systemd/user/lani-stt.service` | the unit (copy of `lani-stt.service`) | |
| `~/.config/lani-stt.env` | optional overrides (see below) | |

The torch wheel is 14 GB of GPU libraries. When the voice worker's venv has the same torch build,
`install.sh` copies it from there with `cp --reflink=auto`: on btrfs (this node) the copy shares
the blocks and takes no space. The venvs stay separate, so updating one can't break the other.
`openai-whisper` is installed with `--no-deps` from `requirements.lock`: its `triton` requirement
would install the CUDA triton over the `triton-rocm` that comes with the ROCm torch.

```bash
systemctl --user status lani-stt
journalctl --user -u lani-stt -f
systemctl --user disable --now lani-stt         # stop and do not start at login
```

Remove completely: `systemctl --user disable --now lani-stt`, then delete
`~/.config/systemd/user/lani-stt.service`, `~/.config/lani-stt.env` and `~/.local/share/lani-stt`.
Without `loginctl enable-linger $USER` the service runs only while the user has a session.

## Resources (measured on this node)

- Model load: 8-12 s (fp16 weights, then a warm-up that compiles the GPU kernels).
- VRAM: 2.4 GiB reserved by torch, 2.9 GiB for the process in `rocm-smi`. With the Gepard worker
  (2.7 GiB) both use 6.2-6.3 GiB of the 16 GiB, also while both are busy at the same time.
- RAM: ~1.8 GB RSS (3.5 GB peak while loading). The unit sets `MemoryHigh=6G`, `MemoryMax=10G`.
- Speed: 0.33-0.47 s per 3-5 s sentence through the bridge (m4a in, decode included), 0.25 s for
  a 1-2 s clip in the worker; 0.5-0.66 s while Gepard synthesizes at the same time.

## Model choice

Test set (`bench.py`): 30 clips from the bridge's voice store (ElevenLabs `eleven_v3`, Slovene,
99 words, 1.6 s on average, picked evenly by file name; `voice.db` opened read-only), each in four
variants: clean, slowed to 0.8x, pink noise at 10 dB SNR, and phone band (300-3400 Hz, 8 kHz,
AAC 24 kbit/s). Plus 20 sentences with a typical learner mistake ("Moj partnerka je iz Gorice.",
"Grem v trgovina.", "Midva gremo v kino.") and their correct forms, voiced by the Gepard worker.
Word error rate (WER) after lowercasing and removing punctuation; beam 5, RX 9070, fp16.

| Model | WER, no prompt (clean / slow / noise / phone) | WER, expected text as prompt | Learner mistakes still in the transcript, with prompt | Latency (1.6 s clip) | VRAM | Load |
|---|---|---|---|---|---|---|
| **large-v3-turbo** | 31 / 34 / 38 / 35 % | **1 / 3 / 3 / 1 %** | 14 of 20 (and 5 of the other 6 have a word under 0.6) | **0.24 s** | **2.4 GiB** | 9 s |
| large-v3 | 19 / 19 / 19 / 16 % | 11 / 11 / 12 / 10 % | 15 of 20 | 0.55 s | 4.3 GiB | 14 s |
| medium | 29 / 26 / 26 / 30 % | 0 / 0 / 0 / 0 % | 1 of 10: it copies the prompt | 0.41 s | 2.1 GiB | 10 s |
| large-v3-sl ([yuriyvnv/whisper-large-v3-slovenian](https://huggingface.co/yuriyvnv/whisper-large-v3-slovenian), Apache-2.0) | **9 / 10 / 12 / 13 %** | 8 / 16 / 13 / 11 % (adds stray words) | **20 of 20** (no prompt) | 0.60 s | 4.4 GiB | 11 s |

Beam 1 instead of 5 on turbo: the same WER, 0.20 s instead of 0.24 s.

**Why the prompt.** Zero-shot Whisper is weak on short Slovene sentences: without context, turbo
writes Croatian or Czech spellings ("ustájem o přijastih" for "Vstanem ob šestih", "Kuhindži").
With the expected sentence as the initial prompt it spells like the sentence, and correct speech
passes (118 of 120 clip variants CORRECT or ALMOST in the app's grading, 20 of 20 Gepard
sentences). The cost: the prompt pulls some mistakes toward the expected form. 6 of the 20
learner mistakes came back corrected ("Moj partnerka" → "Moja partnerka", "Grem v trgovina" →
"Grem v trgovino"). Most of those words had a lower probability (0.3-0.6), but correct words
often do too, so the probability can't tell them apart reliably.

**Why not the Slovene fine-tune.** `large-v3-sl` is the honest one: 9-13 % WER without any prompt
and every learner mistake kept. But correct speech then fails more often (104 of 120 clip variants
CORRECT or ALMOST, 17 of 20 Gepard sentences), and real accented learner speech will fail more
still. Its word probabilities are almost never under 0.5, so there would be no pronunciation
hints. It is 2.5x slower and needs 2x the VRAM. It is a switch away for a stricter grader:

```bash
LANI_STT_MODEL=large-v3-sl companion/stt-local/install.sh     # downloads 6.2 GB, converts to a 3.1 GB fp16 .pt
```

(prompts are then ignored, `LANI_STT_PROMPT=auto`: they made this model add stray words.)
Using both (the fine-tune, overruled when turbo with the prompt heard exactly the expected sentence
with every word over 0.6) made 4 more of the 120 clip variants fully correct and kept all 20
mistakes, but needs both models; not built.

**Pronunciation hints.** A word under 0.3 (one-letter words aside) is shown as "🎯 izgovorjava ·
pronunciation: 'čebelnjak' was unclear". 27 of the 140 correctly spoken test items (19 %) got one,
mostly on words the TTS voice itself slurred ("Slabo", "Vstanem", "postelja", "dež": the same words
that zero-shot Whisper misheard). Real learner audio is still to be checked; the threshold is
`SttSpeech.UNCLEAR` in the app.

**Not measured:** real phone microphone recordings of a learner. All test audio is synthetic.

## Reading aloud

The app's "🎤 Beri na glas · Read aloud" (`companion/GAME.md`, "Reading aloud") grades a text read aloud word by
word (right, misread with what was heard, skipped). The prompt hears correct reading as correct, but may mend a slip;
without it, turbo misspells correct reading. So the app sends each take twice, with the text as the prompt and without,
grades on the prompted transcript and takes the plain one as a second opinion only where the prompt may have pulled a
word to the text: a word the prompted transcript has right is misread when the plain one heard it with another ending
(the same stem), or when its probability is under 0.05 and the plain transcript doesn't have it either
(`data/ReadAloud.kt`). A final consonant said voiceless ("Zlatorok" for "Zlatorog") isn't another ending, and words run
together or split elsewhere ("Gebel", "speto živi") count as read.

Decided on 13 transcriptions (2026-09-27, large-v3-turbo on this node) of three clips from the voice store (a Gepard
voice and two ElevenLabs ones, 7-9 s, and the first two joined, 16 s), each as the phone sends it (AAC, 16 kHz,
32 kbit/s); to stand for a learner's slip, the prompt was a text that differs from what the clip says:

| Clip says | Prompt (the page) | Prompted transcript | Plain transcript |
|---|---|---|---|
| Zlatorog je bel gams z zlatimi rogovi. Živi visoko v gorah … | the same | right, 13 of 13 words | "Zlatero Gebel Gams z Zlatimi Rogavi …": 4 of 13 wrong |
| Lovec ga ustreli. … Zlatorog jih poje in spet oživi. | the same | right, 14 of 14 | "… Zlatorok … speto živi": 3 wrong |
| Jan, veš, tu sem hodil v šolo. … Ta klop je bila moja. | the same | right, 15 of 15 | right, 15 of 15 |
| the first two, joined | the same | right, 27 of 27 | "Zlatero gebel gams, zlatimi …": the same, and "z" left out |
| … v gorah … | … v **hribih** … (a word misread) | "gorah": kept | |
| Lovec ga ustreli. … | Lovec ga ustreli **s puško**. (words skipped) | not put in | |
| … hodil … | … **hodila** … (an ending) | "hodil": kept | |
| … je bila moja. | … je **bil moj**. (two endings) | "bila moja": kept | |
| … je bel gams z zlatimi … (the Gepard clip) | … je **bela** gams z **zlatim** … | "**bela** (0.0) … **zlatim** (0.49)": both mended | "Gebel … Zlatimi": both kept |

Graded on the plain transcript alone, 7 of the 42 words of the three clips read correctly would have been misread (4 of
the 13 of the first); graded on the prompted one alone, a mended slip passes (as the "Model choice" test found for 6 of 20 learner mistakes). Together, the
13 transcriptions grade as the clips were read: every word of correct reading right, and each slip caught (the two
mended ones by the second opinion: "zlatim", heard "Zlatimi"; "bela", not heard). The transcripts are the unit test's
fixtures (`android/app/src/test/java/si/lanisce/lani/data/ReadAloudTest.kt`). Still to see: a learner's own voice,
which is less clear than these, so Whisper is less sure and the prompt pulls more.

## Languages

The app asks in the learner's target language (`language=it` for Italian). Each language has a model:

| Language | Model | Loaded |
|---|---|---|
| Slovene (`LANI_STT_PRIMARY_LANGUAGE`) | the primary model, `LANI_STT_MODEL` (`large-v3-turbo`, or the fine-tune `large-v3-sl`) | at start, as before |
| one with a model of its own | `LANI_STT_MODEL_<LANG>`, e.g. `LANI_STT_MODEL_DE=large-v3` | on first use |
| every other | the general multilingual model, `LANI_STT_MODEL_GENERAL` (`large-v3-turbo`) | on first use |

- **One model for all, by default.** When the general model is the primary one (both `large-v3-turbo`, as on this node),
  that one loaded model serves every language: Italian (`it`), German (`de`) and English (`en`), the app's other
  target languages, need no second model and no more memory. The prompt stays on for them (OpenAI's model;
  `LANI_STT_PROMPT`), so they are spelled toward the expected sentence as Slovene is.
- **Never downloaded by the worker.** A model other than the primary one is used only when its file is in the models
  directory (a Whisper name's download, or a converted `.pt`); otherwise its languages answer `503 "not available"`
  and the app keeps the phone's recognizer. `install.sh` downloads the general model too when the primary is another
  one (`LANI_STT_MODEL=large-v3-sl install.sh` fetches `large-v3-turbo` as well).
- **Memory.** Loading and recognizing take one lock, so one model loads at a time and never next to a recognition.
  Before a model loads, the least recently used others are unloaded until at most `LANI_STT_RESIDENT` (2) stay
  loaded and, on a GPU, until the new one fits (about 1.5× its file: turbo 2.4 GiB) with
  `LANI_STT_VRAM_RESERVE_GB` (1.0) to spare for the voice worker. A model other than the primary one is unloaded after
  `LANI_STT_IDLE_SECONDS` (900) unused. On the RX 9070 (16 GiB) the Slovene fine-tune (4.4 GiB), turbo for Italian
  (2.4 GiB) and Gepard (2.7 GiB) fit together; on a smaller GPU the primary model makes room and comes back at the
  next Slovene request (~10 s). `LANI_STT_RESIDENT=1` keeps only one model at a time.
- The first recognition in a language whose model isn't loaded waits for the load (~10 s, within the bridge's 30 s);
  the app's status check before speaking (`/health?language=it`) starts that load early.

## Configuration (`~/.config/lani-stt.env`)

| Variable | Default | |
|---|---|---|
| `LANI_STT_MODEL` | `large-v3-turbo` | the primary model (Slovene): a Whisper name, or a `.pt` in the models directory (`large-v3-sl`, see `convert_hf.py`) |
| `LANI_STT_MODEL_GENERAL` | `large-v3-turbo` | the multilingual model for the other languages (see "Languages") |
| `LANI_STT_MODEL_<LANG>` | | a language's own model, e.g. `LANI_STT_MODEL_IT` |
| `LANI_STT_PRIMARY_LANGUAGE` | `sl` | the language of `LANI_STT_MODEL` |
| `LANI_STT_RESIDENT` | `2` | at most this many models loaded at once |
| `LANI_STT_IDLE_SECONDS` | `900` | a model other than the primary one is unloaded after this long unused (`0`: never) |
| `LANI_STT_VRAM_RESERVE_GB` | `1.0` | GPU memory to keep free when another model loads |
| `LANI_STT_PROMPT` | `auto` | `1`/`0`: use the expected text as the prompt. `auto`: yes for OpenAI's models, no for `-sl`/`.pt` fine-tunes |
| `LANI_STT_ALIGNMENT` | from the file name | word-timing heads of a fine-tune's base model, e.g. `large-v3` |
| `LANI_STT_BEAM` | `5` | beam size (and best-of for the temperature fallback) |
| `LANI_STT_DEVICE` | `auto` | `cuda` (ROCm is `cuda` in torch) or `cpu` |
| `LANI_STT_PORT` / `LANI_STT_HOST` | `8796` / `127.0.0.1` | do not use 8790, 8791 or 8795 |
| `LANI_STT_QUEUE_SECONDS` | `15` | how long a request waits for the GPU before 503 busy |
| `LANI_STT_FP16` | `1` | `0`: fp32 weights (double the VRAM) |

The bridge reads `LANI_STT_URL` (default `http://127.0.0.1:8796`) and `LANI_STT_TIMEOUT_MS`
(default 30000).

## Files

- `server.py`: HTTP worker (standard library `http.server` + torch + openai-whisper); its tests, with the models
  faked: `companion/tests/test_stt_worker.py` (`python3 -m unittest discover companion/tests`)
- `bench.py`: the model comparison above, without HTTP (`--model`, `--models-dir`, `--errors`, `--json`)
- `convert_hf.py`: Hugging Face Whisper checkpoint → openai-whisper `.pt`
- `install.sh`, `lani-stt.service`, `smoke.sh`, `requirements.lock`
