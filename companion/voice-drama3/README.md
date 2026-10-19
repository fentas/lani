# voice-drama3: Fish Audio Drama 3, a second local voice for batch clips

**Built with Fish Audio.** A worker that runs Fish Audio's open model
[Drama 3](https://huggingface.co/fishaudio/drama-3) (35B MoE, FP8, under the
[Fish Audio Community License](../../LICENSES/Fish-Audio-Community-License-1.0.md)) on the node's AMD RX 9070 (ROCm,
gfx1201), next to the Gepard worker ([voice-local](../voice-local/README.md)) and the Whisper STT worker. Jan finds its
voices more natural than Gepard's. It is far too slow for live lines (about 10 s of compute a second of audio, more on a busy host; see
"Speed"), so the bridge never calls it live: `companion/bin/voice-build --drama3` starts it, remakes a speaker's local
Gepard clips with it, and stops it.

Two rules of its licence apply to everything here:

- **Its output must never be used to train, distill or improve another speech model.** Gepard (or any other voice
  model) is never fine-tuned on these clips, nor on anything made from them.
- **No real person's voice.** Every voice is a character made by Drama 3 itself from a description (below); none is
  cloned from a recording of a person.

"Built with Fish Audio" is shown in [NOTICE.md](../../NOTICE.md), the website's credits and the app's Offline audio
screen; [LICENSES/fish-audio-NOTICE.txt](../../LICENSES/fish-audio-NOTICE.txt) has the attribution notice and what
`drama3_rocm.py` changes in Fish's code at run time.

## HTTP contract (127.0.0.1:8785), as Gepard's

| Request | Response |
|---|---|
| `GET /health` | `200 {"ok": true, "engine": "drama3", "device": "rocm gfx1201 (…)", "busy": false, "running": 0, "voices": [...], "default_voice": "female", "load_seconds": 15.4, "whisper": true, "built_with": "Built with Fish Audio"}` when ready. `503 {"ok": false, "loading": true, "waiting": "the heavy lock (…)"}` while it waits for the host's heavy lock, for memory (`"memory (12 GB available, 20 needed)"`) or loads the model. `503 {"ok": false, "error": "…"}` if loading failed. |
| `POST /synth` `{"text": "...", "speaker": "grandma"}` | `200 audio/mpeg` (MP3, 96 kbit/s, 44.1 kHz mono). Headers `X-Voice` (`grandma/grandma-2`: the speaker and its reference), `X-Audio-Seconds`, `X-Synth-Seconds`, `X-Attempts` (takes made), `X-Seed`, `X-Checks` (the take's checks as JSON: `f0`, `drift`, `noise_floor`, `noise_s`, `text_score`, `rate`, `warnings`), `X-Denoised` (`hiss`, when the take went through the hiss filter). |
| | `422 {"ok": false, "error": "quality", "voice", "text", "attempts": [{"seed", "ok", "reasons", "warnings", "metrics"}, …]}`: no take passed the checks. The line is reported, never shipped. |
| | `400`: no text, more than 400 characters, invalid JSON, a speaker that isn't a voice (the answer lists them), or a voice without a reference yet. |
| | `503 {"error": "busy"}`: two requests are in flight already (one generating on the GPU, the other decoded and heard by Whisper on the CPU); `503` while loading. |
| | `500` if synthesis failed. |

`speaker` is a speaker of [voice-cast.json](../voice-cast.json) (`female`, `male`, `grandma`, `grandpa`, `young-man`,
`gruff`, `woman`, `teacher`, `young-woman`, `girl`, `boy`) or an alias; none: `female`. The bridge sends the cast
speaker of the clip's voice. A leading `sl:` is removed; bracketed directions (`[warmly]`, `[sighs]`) are Drama 3's own
and are kept.

## How the bridge uses it

`companion/bin/voice-build --drama3 <speaker[,speaker]|all>` (all: every speaker but the narrators), a dry run by
default:

```bash
companion/bin/voice-build --drama3 all                 # dry run: the clips, by speaker, the time it would take, the worker
companion/bin/voice-build --drama3 grandma,girl --run --max 20
LANI_HEAVY_LOCK=/path/to/heavy.lock companion/bin/voice-build --drama3 all --run   # the night's batch
companion/bin/voice-build --drama3 all --retry-failed  # the lines no take passed before, again
```

- It remakes a speaker's **local Gepard clips** with Drama 3, most played first. The narrators (`female`, `male`) are
  refused, as `--elevenlabs` does: their local voices stay Gepard's, the fallback of their Slovene ElevenLabs voices.
- **The order of preference: ElevenLabs, then Drama 3, then Gepard** (`ENGINE_RANK` in `bridge/src/voice.ts`). A clip
  is replaced only by an engine ranked as high: a Gepard take never replaces a Drama 3 clip; ElevenLabs replaces either
  (`--upgrade`, `--elevenlabs`, a re-record on request).
- Each clip is stored as every new clip is: its edges trimmed and **levelled to −18 LUFS** (as `voice-build --level`),
  under a new file name, its hits kept, the Gepard file removed, the row's engine `drama3` and its `engine_voice` the
  worker's (`grandma/grandma-2`). A running bridge tells the app within a minute (`voice_updated`). A clip that changed
  meanwhile (the bridge made an ElevenLabs one) is left as it is.
- **A line no take passed** keeps its Gepard clip and is recorded with the checks' reasons (`drama3_failed` in
  `voice.db`); the run lists them, the next run skips them, `--retry-failed` tries them again.
- **On demand:** `--run` starts the worker (`LANI_DRAMA3_CMD`, else `~/.local/share/lani-drama3/run.sh`) unless one
  answers at `LANI_DRAMA3_URL` already (then it uses it and leaves it running), waits for it, and stops it after (also
  on Ctrl-C: once finishes the clips in flight, twice stops at once).
- **The host's heavy lock:** the worker loads the model only once it holds `LANI_HEAVY_LOCK` (`/tmp/lani-heavy.lock` by
  default; on Jan's node the shared one other sessions use, so pass its path), and keeps it while it runs.
- **Memory:** the batch pauses below 20 GB available (`free -g`'s "available"; `LANI_DRAMA3_MIN_FREE_GB`), checks every
  minute, stops the worker when the pause lasts over 5 minutes (its VRAM, RAM and the lock freed), and starts it again
  when memory is back. The worker itself waits for 20 GB before it loads, and exits if `MemAvailable` falls under 4 GiB.
- **Resumable:** what is left is what is still Gepard's: a stopped batch goes on where it stopped with the next `--run`.
- **Don't run it on the learner's voice cache without Jan:** it remakes the clips the app plays. Jan decides when
  (overnight; see "Speed").

## Install

```bash
companion/voice-drama3/install.sh          # venvs, fish-speech at the pinned commit, the app copy and run.sh
```

| Variable | Default | |
|---|---|---|
| `LANI_DRAMA3_HOME` | `~/.local/share/lani-drama3` | where it all goes: `venv/`, `venv-asr/`, `fish-speech/`, `app/`, `run.sh`, kernel caches |
| `LANI_DRAMA3_WEIGHTS` | `/mnt/scratch/fish-drama3/weights/drama-3` | the model (39 GB, `fishaudio/drama-3` at `b2b5ee99`) |
| `LANI_DRAMA3_WHISPER` | the spike's copy when there, else `<home>/faster-whisper-large-v3` | Whisper large-v3 for CTranslate2 (3 GB, `Systran/faster-whisper-large-v3`) |
| `LANI_DRAMA3_DOWNLOAD=1` | off | download a missing model from Hugging Face |
| `LANI_DRAMA3_VENV`, `LANI_DRAMA3_ASR_VENV`, `LANI_DRAMA3_SRC` | in the home | an existing venv (model, Whisper) or fish-speech checkout |
| `LANI_DRAMA3_SERVICE=1` | off | also copy `lani-drama3.service` (never enabled or started) |

On Jan's node the spike's venvs and checkout can be reused (no 17 GB of torch again):

```bash
LANI_DRAMA3_VENV=/mnt/scratch/fish-drama3/venv LANI_DRAMA3_ASR_VENV=/mnt/scratch/fish-drama3/venv-asr \
  LANI_DRAMA3_SRC=/mnt/scratch/fish-drama3/src/fish-speech companion/voice-drama3/install.sh
companion/voice-drama3/smoke.sh            # starts the worker, says one line as grandma, stops it
```

The model's venv is Python 3.12 with torch 2.11.0+rocm7.2 and the rest frozen in `requirements.lock`; Whisper's
(`venv-asr`, CPU only, faster-whisper 1.2.1) in `requirements-asr.lock`. Run `install.sh` again after `refs.py pick`: it
copies `voices.json` to the app.

**Not a service.** `lani-drama3.service` is there for a run by hand (`systemctl --user start lani-drama3`, then stop
it); it is never enabled. The worker holds ~7 GB of VRAM; Gepard (3.6 GB) and the STT worker (3.1 GB) keep theirs.

## Character voices

Without a reference, Drama 3's timbre drifts from line to line, even with a fixed seed (the spike). A voice here is
therefore a **reference take made by Drama 3 itself**: the speaker's description and line in, its codes kept
(`voices.json`, `ref`: the transcript, the codes as base64 of `[10, frames]` uint16, its median pitch). Every line of
the speaker is generated after that reference ("convert the provided text to speech reference to the following"), so
the speaker keeps one voice. No recording of a real person is involved.

| Speaker | Description (Drama 3's own words) | Line |
|---|---|---|
| female | a warm, clear woman in her thirties, a calm storyteller | Danes je lep dan. Na vrtu cvetijo rože in sonce sije. |
| male | a warm, clear man in his thirties, a calm storyteller | Zjutraj grem v mesto, popoldne pa se vrnem domov. |
| grandma | a kind, warm old grandmother in her seventies | Pridi, otrok, sedi k peči, skuhala sem ti čaj. |
| grandpa | a calm old man in his seventies with a deep, slightly rough voice | Ko sem bil mlad, sem vsak dan hodil v gozd. |
| young-man | a friendly young man in his twenties with a bright, clear voice | Danes popoldne bom na travniku pasel ovce. |
| gruff | a deep, gruff, strong man in his fifties, a village blacksmith | Železo je treba kovati, dokler je vroče. |
| woman | a warm, cheerful woman in her forties, an innkeeper | Dobrodošli v gostilni! Kaj vam lahko prinesem? |
| teacher | a clear, patient female teacher in her thirties, speaking slowly and clearly | Danes se bomo učili nove besede. Poslušajte pozorno. |
| young-woman | a friendly young woman in her twenties with a bright, lively voice | V soboto gremo na izlet, pridi tudi ti! |
| girl | a cheerful little girl, about eight years old | Mami, poglej, našla sem lepo rožo! |
| boy | a lively little boy, about eight years old | Jaz sem najhitrejši, ujemi me, če me moreš! |

Each description ends with "speaking Slovenian" and is written in brackets before the line, as Drama 3 takes it.

```bash
flock <heavy.lock> venv/bin/python refs.py candidates --out DIR       # 3 candidates a speaker: DIR/<speaker>-<n>.mp3, DIR/list.txt
python3 refs.py pick grandma=2 girl=1                                  # Jan's picks: the reference in voices.json (no model run)
python3 refs.py rescore --out DIR                                      # score the stored candidates again (no model run)
flock <heavy.lock> venv/bin/python refs.py consistency --out DIR       # each voice's 8 test lines, the pitch spread
```

`candidates` makes three per speaker, each the first of up to four seeds that passes the checks (a reference is held
to a cleaner noise floor, under −62 dB, as its hiss would be cloned into every line), and keeps every candidate's
codes in `candidates.json`, so a pick needs no model run. **Until Jan picks, the best-scoring candidate is the
reference:** passing first, then the text Whisper heard, Whisper hearing Slovene (not Croatian or Czech), a clean
noise floor, the pitch near the character's (`target_f0`: the blacksmith deep, the children high), few warnings.
`list.txt` names each candidate, its description, its scores and what Whisper heard.

**The candidates of 2026-10-19** (33, in the scratchpad's `drama3-refs/` for Jan; the defaults marked): all 33 pass
the checks (a reference took up to 6 seeds: the girl's second hissed five times); Whisper hears Slovene in 32 (the
boy's second: Croatian, 0.62).

| Speaker | Default | Its pitch | The other two | Note |
|---|---|---|---|---|
| female | female-3 | 190 Hz | 175, 172 Hz | |
| male | male-1 | 97 Hz | 96, 99 Hz | "popovdne": the l of "popoldne" said as Slovene says it |
| grandma | grandma-1 | 195 Hz | 158, 216 Hz | |
| grandpa | grandpa-3 | 129 Hz | 78, 89 Hz | the other two deeper (rougher?) |
| young-man | young-man-2 | 116 Hz | 126, 154 Hz | all three: the schwa of "pasel" heard as "pasal" |
| gruff | gruff-1 | 86 Hz | 80, 93 Hz | |
| woman | woman-2 | 201 Hz | 229, 213 Hz | |
| teacher | teacher-2 | 175 Hz | 191, 171 Hz | |
| young-woman | young-woman-1 | 212 Hz | 228, 247 Hz | |
| girl | girl-3 | 348 Hz | 333, 408 Hz | |
| boy | boy-3 | 305 Hz | 384, 375 Hz | |

**Consistency, measured** (`refs.py consistency`: each default voice's 8 test lines, the full guard, two in flight):

| Speaker | Pitch of the 8 lines (Hz) | Spread | RMS from the reference | First take passed | Takes |
|---|---|---|---|---|---|
| female | 184 182 194 208 192 177 173 175 | 3.2 st | 1.1 st | 8/8 | 8 |
| male | 86 99 97 98 97 114 99 96 | 4.7 st | 1.2 st | 7/8 | 9 |
| grandma | 186 180 178 200 166 214 176 140 | 7.3 st | 2.5 st | 8/8 | 8 |
| grandpa | 100 128 136 122 116 105 110 108 | 5.3 st | 2.6 st | 8/8 | 8 |
| young-man | 121 125 121 118 116 116 115 119 | 1.4 st | 0.6 st | 8/8 | 8 |
| gruff | 121 88 99 90 94 94 98 91 | 5.5 st | 2.6 st | 7/8 | 11 |
| woman | 184 200 215 212 206 196 202 204 | 2.7 st | 0.8 st | 7/8 | 10 |
| teacher | 161 188 171 178 181 194 190 167 | 3.3 st | 1.1 st | 8/8 | 8 |
| young-woman | 192 202 206 195 193 191 198 184 | 2.0 st | 1.6 st | 7/8 | 11 |
| girl | 288 266 315 358 297 290 318 302 | 5.2 st | 2.8 st | 8/8 | 8 |
| boy | 268 337 299 332 277 310 316 276 | 3.9 st | 1.4 st | 7/8 | 9 |

- **One gender per voice:** every one of the 88 first takes was in its speaker's band (88/88), where 2 of the spike's 16
  takes without a reference were out of it (a woman at 105 Hz, a man at 185 Hz) and a third at its edge (a man at 180 Hz). All 88 lines passed in
  the end, 83 on the first take, in 98 takes.
- **One timbre:** the pitch stays within 1.4 to 5.5 semitones a voice (RMS 0.6 to 2.8 from the reference); the widest,
  grandma's 7.3, is one low line ("Čez cesto …" at 140 Hz, warned about, with a noise floor at −57 dB: it would be
  taken again now, below). A sentence's own melody moves the median by 2 to 3 semitones in a natural voice too.
- **What the retries were:** two silence loops and a stall (gruff's "Dober dan!", young-woman's "Kamen na kamen."),
  long pauses (1.7 and 1.8 s), Whisper's misses ("Micka" as "Mička" with "kruhu", "Oprostite" as "os prostite"
  twice, "Čez" as "s"). The warnings were the known ones: lj and nj lost (9 voices on "temelji", 7 on "kuhinji"), the
  schwa of "dober" once.
- **After the run** the pause and the noise checks were tightened, on a take of smoke.sh (grandma's line) that slipped through:
  3.5 s between two sentences filled with a low hiss (37 dB under the speech, so not silent, and 56 dB under in the
  4–12 kHz band, so just under the noise threshold of then). A pause now counts frames 35 dB under the speech, and the
  noise-like frames those within 60 dB; it fails now on 6.2 s of hiss and a pause of 3.5 s. Of the 88 lines, two would
  now be taken again (grandma's 140 Hz line: 1.4 s of noise; male's "Na svidenje": a 1.8 s pause).

## The quality guard

Every take, in the batch and in `/synth`, goes through `checks.py` (`judge`); a take that fails is made again with
another seed (`LANI_DRAMA3_ATTEMPTS`, 4), and a line that never passes is reported (422), never shipped. The signal
checks run first; Whisper only on a take that passed them.

| Check | What | Fails |
|---|---|---|
| Gender | the median F0 of the voiced frames (YIN) against the speaker's cast gender: a man's band, a woman's, a child's (a girl's and a boy's alike: high whichever the gender) | a man over 185 Hz (warns over 165), a woman under 135 Hz (warns under 150), a child under 200 Hz (warns under 230) |
| Drift | the take's pitch against the speaker's reference | more than 6 semitones (warns over 4) |
| Voice | the share of voiced frames | under 15 % (a whisper, or noise) |
| Noise | the noise bed: the quietest 10 % of the frames' 4–12 kHz band, against the speech level; and the seconds of noise-like frames (a flat 4–12 kHz band within 60 dB of the speech level, no voice under it) | a floor above −57 dB under the speech (warns above −60), or more than 1 s of noise |
| Text | Whisper large-v3 on the CPU (its own venv and process, never the node's STT service), word by word against the text, splits and joins allowed ("mi dva" for "midva") | a word of three or more letters missed, added or heard as another word; near misses on more than a third of the words. Near misses ("kruhu" for "kruh", final devoicing "gost" for "gozd", a word of one or two letters) are soft. **Warnings, not failures:** lost lj/nj ("temeli" for "temelji", "kuhini" for "kuhinji") and the schwa of "dober" heard as "dobar" or "dobr" |
| Length | the model stopped by itself before the frame cap (60 + 4 frames a letter: a loop of silence otherwise); 4 to 25 letters a second of sound | no end; slower or faster |
| Silence | a pause: frames 35 dB under the speech level (a gap filled with a low hiss counts); the ends: 40 dB under | a pause over 1.5 s inside, over 2 s at an end |

**The hiss filter, as a last resort.** When every take of a line failed on noise alone, the best of them goes through
the bridge's `hiss` filter (ffmpeg's `afftdn` and `agate`, the chain of `DENOISE` in `bridge/src/voice.ts`) and through
all the checks again, Whisper included; it ships only if it passes (`X-Denoised: hiss`). On the spike's four hissing
takes it took the floor from −53…−57 dB to −84…−90 dB, the speech level and the pitch unchanged (within 4 Hz).
`LANI_DRAMA3_DENOISE=off` turns it off. This is ordinary editing of the output, not the removal of a provenance signal.

### What the checks catch on the spike's clips

The spike's 25 takes (no reference: voice A "a warm, calm older woman", voice B "a friendly, clear young man"; Jan
heard white noise in some of 01 to 16, and a voice of the wrong gender), with Whisper's transcripts of the spike:

| Take | Fails on | Numbers |
|---|---|---|
| 01 dober-dan, A | noise | 1.2 s of noise-like frames in the pause (the floor at −59 dB) |
| 03 grem-delat, A (a woman) | gender, noise | 105 Hz (a man's); the hiss bed at −55 dB under the speech, 0.8 s of noise |
| 10 babica-micka, B (a man) | gender, noise | 185 Hz (a woman's); −57 dB |
| 14 oprostite-anton, B | noise | −53 dB (a bed up to 10 kHz in the tail) |
| 15 kamen-na-kamen, B | noise | −53 dB, 2.0 s of hiss filling the pause |
| 05 midva-onidve, A | text | "Mi dva **smo**" for "Midva **sva**" (the dual said as a plural) |
| 17 whisper, A | voice, noise | 0 % voiced; −44 dB |
| 18 na-svidenje "warmly", A | noise | 1.2 s of noise-like frames (the floor at −60 dB) |
| 21 sigh and laugh, A | noise, length | −44 dB, 3.3 s; 2.9 letters a second |
| 24 Bäckerei (German), A | silence | a pause of 2.0 s |

The other 15 pass (10 of 25 fail: 6 of the 16 Jan compared with Gepard, 01 to 16). Warnings on them: the noise floor
at −58 to −60 dB (04, 06, 16), a man at 180 Hz (16), the schwa "dobar" (09), lj lost "temeli" (07, 20), nj lost
"kuhini" (13). The spectrograms show the hiss
as a flat bed up to a sharp edge (13 kHz in 03, 15.5 kHz in 10 and 15, 16.5 kHz in 21) that fills the pauses; the clean
takes' pauses are dark (−65 to −90 dB). Gepard's clips measure −71 to −80 dB.

## Speed

Measured on 2026-10-19 on a busy host (load average 28 to 44; a VM at 600 % CPU; Gepard voicing 200 lines in five
minutes of one run), with Gepard (3.6 GB) and the STT worker (3.1 GB) on the card:

| | |
|---|---|
| Load | 15 to 24 s (the dense weights 5 to 14 s, the codec 3 s), once the lock is free |
| A frame (46 ms of audio) | 300 to 400 ms (the spike's quieter host: 199 ms); up to 1.9 s while Gepard was busy |
| Whisper, a take | 5.5 s on 8 CPU threads (large-v3 int8; the encoder's 30 s window dominates, beam 1 is no faster), 10 s with the language too (the candidates) |
| **The batch** | **10.0 s a second of audio**: the consistency run, 88 lines (273 s of audio) in 2,723 s, two in flight (Whisper hidden behind the other take's generation), retries included: 31 s a line |
| 4 real clips of the voice cache (a copy) | 3 min 24 s with the worker's start and stop (17 s a second of audio while Gepard was busy) |
| Memory | 2.4 GiB resident plus Whisper's helper (~2 GB); 7.1 GiB of VRAM |

**The voice cache's batch** (October 19): 2,663 local Gepard clips of the villagers (boy 795, girl 546, grandpa 465,
young-man 421, gruff 195, grandma 134, woman 107; 61,370 letters, about 2 h 14 min of audio): **about 22 hours** at the
measured 10 s a second (`voice-build --drama3 all` estimates 22 h 17 min); on a quiet host perhaps 14 to 16. Two nights,
or a speaker a night, most played first (`--max`). `LANI_DRAMA3_RTF` sets the estimate's speed.

## Configuration

| Variable | Default | |
|---|---|---|
| `LANI_DRAMA3_HOST` / `LANI_DRAMA3_PORT` | `127.0.0.1` / `8785` | the bridge's `LANI_DRAMA3_URL` (voice-build sets both when it starts the worker) |
| `LANI_DRAMA3_LOCK` | none | a file to `flock` before loading (voice-build passes `LANI_HEAVY_LOCK`) |
| `LANI_DRAMA3_START_GIB` | `20` | GB available before it loads the model |
| `LANI_DRAMA3_MIN_AVAILABLE_GIB` | `4` | the watchdog: under this the worker exits to protect the host |
| `LANI_DRAMA3_VRAM_GIB` | `7.6` | the process's share of the card |
| `LANI_DRAMA3_ATTEMPTS` | `4` | takes a line gets |
| `LANI_DRAMA3_DENOISE` | `hiss` | the last-resort filter for a line that only hissed; `off` |
| `LANI_DRAMA3_INFLIGHT` | `2` | requests at once (one on the GPU, one checked) |
| `LANI_DRAMA3_TEMPERATURE`, `_TOP_P`, `_TOP_K` | `0.7`, `0.7`, `30` | sampling (Fish's defaults; `voices.json` `_settings`) |
| `LANI_DRAMA3_ASR_PYTHON`, `LANI_DRAMA3_WHISPER`, `LANI_DRAMA3_ASR_THREADS` | `<home>/venv-asr/bin/python`, see Install, `8` | the Whisper check |
| `LANI_DRAMA3_MP3_BITRATE` | `96k` | |
| `LANI_DRAMA3_TRITON_FP8`, `LANI_DRAMA3_FAST_SOLVE` | `1`, `1` | the ROCm patches (below); `0` falls back |

voice-build's side: `LANI_DRAMA3_URL`, `LANI_DRAMA3_CMD`, `LANI_HEAVY_LOCK`, `LANI_DRAMA3_MIN_FREE_GB` (20),
`LANI_DRAMA3_PAUSE_POLL_S` (60), `LANI_DRAMA3_PAUSE_STOP_S` (300), `LANI_DRAMA3_START_TIMEOUT_S` (4 h: the lock may
be another job's for a while), `LANI_DRAMA3_LOG` (`~/.cache/lani/drama3-worker.log`), `LANI_DRAMA3_RTF` (the estimate's
seconds a second of audio).

## How it runs on ROCm

`drama3_rocm.py` loads the model in "safe mode" and changes how upstream fish-speech (commit `5415669`, not edited)
runs, at run time: the 30 GiB of routed FP8 experts are read from the weight files into a per-layer cache in VRAM
(nothing of them held in RAM: 2.4 GiB resident), Fish's Triton FP8 MoE kernel is on for RDNA4 (upstream turns it off
for every HIP device), the slow ROCm triangular solve is replaced by an exact forward substitution, the token
embedding stays on the CPU and `lm_head` keeps only the rows the sampler can draw, the codec loads without two unused
masks, and there are no HIP graphs. The spike checked every path against a CPU reference. Fish doesn't support ROCm:
these patches are ours to keep.

## Files

- `server.py`: the HTTP worker (standard library), the heavy lock and the memory wait
- `engine.py`: the model, the voices, the guarded take (retries, the hiss filter), MP3
- `checks.py`: the quality checks (numpy; the text check pure Python)
- `asr.py`: the Whisper helper, run in `venv-asr`
- `drama3_rocm.py`: Drama 3 on ROCm (the runner from the spike)
- `refs.py`: the reference candidates, Jan's pick, the consistency check
- `voices.json`: the voices and their references; `candidates.json`: every candidate's codes and scores
- `install.sh`, `run.sh` (written by it), `lani-drama3.service`, `smoke.sh`, `requirements.lock`, `requirements-asr.lock`
- Tests: `companion/tests/test_drama3_checks.py` (the checks, on three fixtures cut from the spike's takes and on
  synthetic tones; the signal part needs numpy: run it with the worker's venv), `companion/tests/test_drama3_worker.py`
  (the HTTP contract with a fake engine), and the bridge smoke's `test/smoke/drama3.ts` (voice-build --drama3 on a fake
  worker)
