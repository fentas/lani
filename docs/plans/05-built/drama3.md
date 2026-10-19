# Built: Fish Audio Drama 3, a second local voice for batch clips (2026-10-19)

A spike earlier the same day ran Fish Audio's open model Drama 3 on the node's RX 9070 next to Gepard and the STT worker
(docs/plans/03-voice-service.md, "Tried: Fish Audio Drama 3 on the local GPU"). Jan listened and found it more natural
than Gepard, with hiccups: a voice of the wrong gender now and then, white noise in some takes. This round (branch
`drama3`) made it a usable second local voice for the batch clips, with consistent characters and a quality guard.
Not a phase of plan 5. Built with Fish Audio.

## What

- **A worker like Gepard's** (`companion/voice-drama3/`): the spike's runner moved in (safe mode only: the routed FP8
  experts read from the weight files into a VRAM cache), Gepard's HTTP contract on 127.0.0.1:8785 (`GET /health`,
  `POST /synth {text, speaker}` → MP3 with `X-Audio-Seconds`, `X-Synth-Seconds` and the checks' headers; 422 with the
  reasons when no take passed). Two requests in flight (one on the GPU, the other decoded and heard by Whisper on the
  CPU). **On demand, not a service:** it loads the model only once the host's heavy lock is its own (flocked and held
  while it lives) and 20 GB are available; a unit file is there for a run by hand, never installed or enabled.
  `install.sh` (venvs, fish-speech at its commit, the weights where they are or downloaded on request, `run.sh`),
  `smoke.sh`.
- **Consistent character voices.** Each speaker of the voice cast has a reference take Drama 3 made itself from a
  description of the character ("a kind, warm old grandmother in her seventies, speaking Slovenian"): no real person's
  voice. Its codes are kept in `voices.json`, and every line of the speaker is generated after it. Three candidates a
  speaker, checked automatically; the best-scoring is the default until Jan picks (`refs.py pick`).
- **A quality guard on every take** (`checks.py`): the gender by median pitch against the cast gender (children high
  whichever the gender) and the drift from the reference; noise (the floor of the 4–12 kHz band in the quietest frames,
  and the seconds of noise-like frames); the text heard by Whisper large-v3 on the CPU (its own venv, never the live
  STT), word by word with tolerance (lost lj/nj and the schwa of "dober" heard as "dobar" are warnings); the frame cap
  (the silence loop), the speech rate, long silences. A failing take is made again with another seed (4 takes); a line
  whose takes only hissed gets its best take through the bridge's hiss filter and the checks again; a line that never
  passes is reported, never shipped.
- **In the bridge** (`bridge/src/voice.ts`, `bridge/src/drama3.ts`, `bin/voice-build`): the engine `drama3` for batch
  clips only, the order of preference ElevenLabs, then Drama 3, then Gepard (`ENGINE_RANK`: a Gepard take never
  replaces a Drama 3 clip; ElevenLabs replaces either), and `voice-build --drama3 <speaker[,speaker]|all>`: a dry run
  by default (the clips by speaker, the audio, the time at the measured speed, the worker, the lock, the memory), `--run`
  (starts the worker unless one answers, stops it after; pauses below 20 GB available and stops the worker while the
  pause lasts; resumable; Ctrl-C), `--max N`, `--retry-failed`. Never the narrators. Clips stored as every new clip
  (trimmed, levelled to −18 LUFS), marked `drama3` with the worker's voice; a line no take passed keeps its Gepard clip
  and is listed (`drama3_failed` in `voice.db`).
- **"Built with Fish Audio"** in NOTICE.md and LICENSES/ (the licence, its attribution notice and what the runner changes
  in Fish's code), the website's credits and the app's Offline audio screen. The licence's rule that **its output never
  trains another speech model** (Gepard is never fine-tuned on these clips) is in the worker's README and NOTICE.md.

## How to use

```bash
# once, on Jan's node, reusing the spike's venvs and checkout
LANI_DRAMA3_VENV=/mnt/scratch/fish-drama3/venv LANI_DRAMA3_ASR_VENV=/mnt/scratch/fish-drama3/venv-asr \
  LANI_DRAMA3_SRC=/mnt/scratch/fish-drama3/src/fish-speech companion/voice-drama3/install.sh
# Jan picks a reference per speaker from the candidates (else the best-scoring stays), then install.sh again (as above)
python3 companion/voice-drama3/refs.py pick grandma=2 girl=1
# the batch: a dry run first, then overnight, under the host's heavy lock
companion/bin/voice-build --drama3 all
LANI_HEAVY_LOCK=<the shared heavy.lock> companion/bin/voice-build --drama3 all --run
```

## Files

- `companion/voice-drama3/`: `server.py`, `engine.py`, `checks.py`, `asr.py`, `drama3_rocm.py`, `refs.py`,
  `voices.json`, `candidates.json`, `install.sh`, `lani-drama3.service`, `smoke.sh`, `requirements*.lock`, `README.md`
- `companion/bridge/src/voice.ts` (the engine, the rank, the queue and the batch), `companion/bridge/src/drama3.ts` (the
  worker on demand, the memory pause, the estimate), `companion/bin/voice-build` (`--drama3`)
- Tests: `companion/tests/test_drama3_checks.py`, `companion/tests/test_drama3_worker.py`,
  `companion/tests/fixtures/drama3/`, `companion/bridge/test/smoke/drama3.ts`, `companion/bridge/test/fakes/drama3.ts`
- NOTICE.md, `LICENSES/Fish-Audio-Community-License-1.0.md`, `LICENSES/fish-audio-NOTICE.txt`, `site/`, the app's
  `offlineAudio.clipsCredit`

## Measured

- **The checks on the spike's 25 takes** (Whisper's transcripts of the spike): 10 fail, among them 6 of the 16 Jan
  compared with Gepard. Noise: 01, 03, 10, 14, 15, 18 (a hiss bed at −53 to −57 dB under the speech, or over 1 s of
  hiss in the pauses; the clean takes −60 to −72, Gepard's −71 to −80); gender: 03 (a woman at 105 Hz), 10 (a man at
  185 Hz); text: 05 ("mi dva smo" for "midva sva"); the whisper, the sigh-and-laugh and a German take with a 2 s pause.
  Warnings: "dobar", "temeli", "kuhini". The hiss filter takes the hissing takes' floor to −84…−90 dB, the speech level
  and the pitch unchanged.
- **The candidates:** 33 (3 a speaker), all passing; a reference took up to 6 seeds (the girl's hissed). In the
  scratchpad's `drama3-refs/` with `list.txt`, for Jan to pick; the defaults are the best-scoring.
- **Consistency** (the 11 default voices, 8 lines each, the full guard): every first take in its speaker's gender band
  (88/88; the spike without references: 2 of 16 out of it, one at the edge); all 88 lines passed, 83 on the first take,
  98 takes in all; the pitch within 1.4 to 5.5 semitones a voice (grandma 7.3: one low line), RMS 0.6 to 2.8 semitones
  from the reference.
- **Speed:** 10.0 s a second of audio for the batch (273 s of audio in 2,723 s, two in flight, retries included) on a
  busy host; a frame 300 to 400 ms (the spike's quiet host: 199 ms). 4 real clips of a copy of the voice cache, worker
  start and stop included: 3 min 24 s while Gepard was voicing at the same time.
- **The voice cache's batch:** 2,663 local Gepard clips of the villagers (61,370 letters, about 2 h 14 min of audio):
  about **22 hours** at that speed (the dry run says 22 h 17 min), perhaps 14 to 16 on a quiet host.
- **Tests:** the bridge smoke's Drama 3 section (24 checks), the Python tests of the checks (19, the signal part with the
  worker's venv) and of the worker (11); the real worker through `smoke.sh` and `voice-build --drama3 --run --max 4` on
  a copy of the voice cache.

## What's left

- **Jan picks the references** (`refs.py pick`); the defaults are the best-scoring candidates. Then the batch, when Jan
  says: a dry run first, then `--run` overnight with the shared heavy lock (`LANI_HEAVY_LOCK`).
- **A native ear.** The checks hear what Whisper hears: stress, the schwa ("pasal" for "pasel" in all three young-man
  candidates) and lj/nj stay warnings; whether the voices fit the characters is Jan's call.
- **Speed.** Prefetching the next layer's experts, a larger expert cache and HIP graphs for the dense part might make it
  2 to 4 times faster (the spike's estimate, not tried); the GPU codec once Gepard isn't on the card.
- **The licence:** the runner patches Fish's code at run time; NOTICE.md and LICENSES/ treat that as a derivative work
  of the Fish Audio Materials (the notice, what changed). Worth a look if Lani is ever distributed beyond Jan's node.

