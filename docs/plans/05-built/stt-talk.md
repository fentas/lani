# Speech in the free talk: fewer retakes (built 2026-10-29)

Jan asked: "I noticed that Teo is repeating a lot until speech to text is picking it up correctly (in free dialogs). Is
there a way to improve this?" In Teo's session of 2026-10-29 (a child of about 7, Italian on a Samsung SM-T860 tablet)
77 recordings went to the node for about 39 lines sent, about two takes a line; "Al ruscello" after Davide's "andiamo al
ruscello o al bosco?" took 8. The talk gave Whisper no context, the tutor never got another reading, a likely mishearing
("amigo" for amico) was corrected twice ("amigo ist Spanisch"), noise went out as lines ("Ooooooooh"), and the role-play
instructions said "simple Slovene" to Teo's Italian tutor.

## What

- **The conversation as Whisper's context.** Each take of a talk goes to the node with the conversation: the character's
  last two lines, the scenario's useful phrases (its vocabulary hints, a villager's "Come stai?", their likes) and the
  names (the learner's, the character's). The bridge makes Whisper's initial prompt of them, a short transcript of what
  came before: the names, the phrases, then the lines, which Whisper weighs most, being nearest; at most 300 characters
  (the worker's limit), phrases dropped first, then the older line.
- **A second opinion.** The node hears the take twice, with the prompt and plain, in one request (`plain=1`: the worker
  encodes the audio once for both). The bridge weighs the two readings: the same words in both are the prompted reading
  (it spells as the conversation does); otherwise the one with fewer words the language doesn't have ("il pentolino"
  over "Inventorino"), then the one whose words fit the audio better (Whisper's word probabilities are of the text
  without the prompt, so the two compare; the plain one needs 0.1 more). The other reading goes to the tutor as
  `alternatives`.
- **Noise is nothing heard.** The worker now measures how much of a take is a voice (`voice.voiced`: seconds loud over
  the room and periodic like a pitch: the autocorrelation peaks at a voice's period after a valley); under 0.1 s is no
  voice. large-v3-turbo's `no_speech_prob` is 0 for a room's noise that it hears as "Grazie.", so this is what tells
  them apart. A reading Whisper made up (a word or a phrase over and over, a letter held, more words than the audio can
  hold, a web address, its stock lines like "Grazie a tutti.", "Sottotitoli creati dalla comunità Amara.org"), a sound
  ("Ooooh", "mmm") or the prompt's own words that don't fit the audio is no line either. Nothing goes to the draft; the
  app says "🤔 I didn't catch that".
- **Near-misses go to the tutor.** A heard word the language doesn't have, one edit from a word of the conversation
  ("Amigo" with "amico" in Davide's line), is matched to it in an alternative reading for the tutor ("Amico, come
  stai?"), with `near: [{from, to}]`. The draft keeps what was heard: on the test set every match changed a word wrongly
  ("Near-misses" below). Only with a dictionary of 50,000 lemmas or more (Italian's has 201,408; Slovene's in the
  repository 5,774, which lacks "kam").
- **A tutor who knows it's a recognizer.** The role-play paragraph says that `heard` is speech recognition of a learner,
  maybe a child, which mishears: a word it may have misheard (one the alternatives read otherwise, one in `unclear`, a
  word a letter or two from the conversation's, an ending lost in a mumble: piccoli/piccola) is recast in the reply,
  never corrected; only what every reading agrees on and is clearly the learner's is corrected; a line that is only a
  sound gets a friendly "Come?" in character; `takes` over 2 means the learner struggled, so the next line is easy. It
  is in the course's language: "simple Italian", `{"sl": "<your line, in Italian: the key is sl in every language>",
  "en": "<its translation, in German>"}` for Teo, as before for Jan (the app reads the keys `sl` and `en`).
- **Fewer taps when it's right: "Pošlji sam · Send by itself".** A line the node heard surely goes by itself after 1.8
  s, with "➤ Pošiljam … · Sending …" and "✋ Obdrži · Keep" (keeps it as a draft). Sure: both readings the same words,
  every word at 0.85 or more and a word of the language or the talk, no near-miss, and a voice measured (an older worker
  measures none: then nothing is sure, since a room's "Grazie." agrees in both readings). On for a child's profile (the
  existing child flag, `PairedInfo.child` from the profile registry), off for others; a switch in "⚙️ Nastavitve ·
  Settings", "🔊 Zvok in glas · Sound and voice": "🎤 Pogovor: pošlji sam · Talk: send by itself". Why the default: a
  child of 7 reads the draft slowly or not at all, and every line costs a tap on ➤; an adult checks the line before it
  goes, and the draft is where Jan corrects it. Unsure takes stay a draft with the unclear words marked, as before.
- **The take.** The talk's gate listens to the room: speech is 8 dB over the quietest the room has been (the lowest
  median of three 100 ms ticks; 400 to 1500 of 32767) and starts the take at once; quiet is also under twice the room
  once the room is well below the voice. A child's soft voice on a tablet starts and ends a take as a loud one does
  ("The take" below).
- **Measured in the field.** A line's data has `takes` (how many times it was said, when more than once), `unclear` and
  `typed`, and the transcript at the talk's end has each line's `takes`. Each take is a line of the app log under the
  tag `LaniStt` (the dev upload sends it to `<data>/app/dev/app-log-<day>.txt`): the engine, both readings with their
  mean probability, the one chosen and why, the voice the node measured, the take's level (peak, the speech level, the
  room, the loud ticks), how it ended (tap, pause, next, cut, limit, nothing) and the round trip in ms. No audio is
  kept.
- **The model.** large-v3-turbo stays ("The model" below): large-v3 needs a retake for 2.3 points fewer child-like
  takes, but takes 2.3 times as long for the two hearings (3 times at the p90) with 1.8 times the VRAM, and its word
  probabilities need a threshold of their own. Measuring it found the worker's load reaching for 3 times a model's fp16
  size on the GPU: a model now loads in fp16 on the CPU and moves (`load_half`).
- **Where else.** The scene dialogs' spoken turns (`ui/scene/DialogMic.kt`, `GiftScene`) already send their choices as
  the prompt (`ChoiceMatch.prompt`); unchanged. The speak exercises keep their expected text as the prompt and their
  gate: a room-aware gate there would start more takes on noise, which the expected sentence as the prompt can turn into
  the right answer, and the exercise has no second hearing to catch it ("What's left").

## How

- **Worker** (`companion/stt-local/server.py`): `POST /transcribe?…&plain=1` (`Engine.transcribe(…, plain=True)`, the
  second hearing as `"plain"`), `EncoderCache` (the encoder's last output by its input, on for that request: the two
  hearings, their temperature retries and their word timings encode the same window), `voice_activity` (numpy, 14 ms a
  take) in every response, `token_budget` (12 text tokens a second and 10 more, `LANI_STT_BUDGET`; 0: Whisper's 224),
  `load_half` (the model made in fp16 on the CPU, then moved: equal to the old load's in all 587 tensors). Tests:
  `companion/tests/test_stt_worker.py` (the plain hearing asked for, the budget, the cache, the voice).
- **Bridge**: `src/stt-talk.ts` (`talkPrompt`, `weigh` with `WEIGHING` {margin 0.1, sureWord 0.85, minVoiced 0.1,
  copyProb 0.5, words: true}, `madeUp`, `copied`, `snap`, `MIN_LEMMAS`), `src/features/stt.ts`
  (`?talk=1&said=…&words=…&names=…`, `talkContextOf`, `Stt.talk`: one request with `plain=1`, else two; the response's
  `alternatives`, `near`, `sure`, `noise`, `by`, `readings`), `src/lexicon.ts` (`size`), `src/features/scenarios.ts`
  (`scenariosInstructions`: the recognizer's rules, per language). The tutor guide regenerated
  (`companion/tutor/guide/role-play.md`). Smoke: 17 checks in `test/smoke/stt.ts` (a fake worker with both hearings),
  one in `test/smoke/friuli.ts` (the Italian paragraph).
- **App**: `data/Stt.kt` (`SttContext`, the request's talk parameters, `SttResult`'s `alternatives`, `sure`, `noise`,
  `by`, `readings`, `voice`; `SilenceGate(room = true)`; `Stt.sendsBySelf`, `chooseBySelf`), `data/Talk.kt`
  (`sttContext`, `turnData`'s `takes`, `unclear`, `typed`, `TalkLine.takes` and the transcript's `takes`),
  `ui/talk/TalkMic.kt` (the context, noise, sure, the takes, the log line), `TalkDraft.kt` (`sure`, `hold`, `took`,
  `takes`; `Said`'s `takes` and `unclear`), `TalkVoice.kt`, `TalkInput.kt` (`SendingBar`, `BySelfSetting`,
  `SEND_BY_SELF_MS`), `TalkModel.say(Said)`, `ui/settings/SettingsScreen.kt`, `app/DevUpload.kt` (`LaniStt` among
  `AppLog.TAGS`), QA's hook `talk:<villager>` (`app/QaHooks.kt`). Strings `talkInput.sending`, `keep`, `bySelf`,
  `bySelfHint` in sl, en, de and it (machine-written: for the native check).
- **Benchmark** (`companion/stt-local/talk-bench/`): `prep.py` (the takes), `items.ts` (their contexts, by the bridge's
  `talkPrompt`), `run.py` (the hearings, `--twice` as a talk sends them), `voice.py`, `score.ts` and `sweep.ts` (the
  bridge's own `weigh`), `timing.py`, `gate.py` (the gate on the takes' levels). How to run them:
  companion/stt-local/README.md, "Talks".

## Measurements

The test set: 1,239 Italian lines of the app's own (ElevenLabs, their exact text; 187 in the child voices, 1,179 of at
most 6 words; 4 lines in another language left out), each as three takes, 16 kHz mono AAC at 32 kbit/s as the app
records them (`talk-bench/prep.py`):

- **clean**: the line, a little silence around it.
- **teo**, like a child's take on a tablet: an adult voice pitched up 3 to 5 semitones (its formants with it), a tablet
  microphone (150-250 Hz to 4.5-6 kHz, a small room's echo), a low level (peak -26 to -14 dBFS), room noise at 8 to 18
  dB SNR, the room before the first word and up to 3 s after the last; a third mumbled (low-passed at 2-2.6 kHz), a
  third with the first 60-140 ms of speech cut off, a quarter with a TV or a sibling behind (another line, reversed,
  12-16 dB down). Today's plain hearing needs a retake for 37 % of these, about Teo's two takes a line.
- **noise**: 120 takes without words, as loud as speech: a room, a sung "ooo", a reversed mumble, breaths and knocks.

Each take's context is what a talk would send (`talk-bench/items.ts`, the bridge's `talkPrompt`): the names (Teo and a
Friuli villager), the villager's talk phrases and likes, and the 1-2 lines before it in its source (a scene's or a
road's lines, in order, never its own text): "related" are the takes that share a word of four letters or more with
those lines (Teo's "Al ruscello" after "andiamo al ruscello o al bosco?"). "Unrelated context": two lines of another
source instead (the learner says what the character didn't). Needs a retake: the words heard aren't the line's (case,
accents, punctuation and spacing aside, numbers as words), or nothing was heard. Sure: what would go by itself; wrong
while sure: of all takes, sent by itself with other words than said. The weighing's settings were chosen on the even
lines (dev) and are reported on the odd ones (test); "all" is both. large-v3-turbo, beam 5, the RX 9070 shared with the
node's own worker and other jobs.

**In short** (the odd lines, large-v3-turbo; today's one plain hearing → everything as built):

| | Today | As built |
|---|---|---|
| Child-like takes needing a retake | 36.0 % | 34.4 % |
| … of them reusing a word of the character's lines | 33.1 % | 28.1 % |
| Clean takes needing a retake | 6.0 % | 5.5 % |
| WER: child-like / reusing / clean | 17.3 / 10.8 / 2.4 % | 16.1 / 9.1 / 2.2 % |
| Noise takes with text in the draft | 99 % | 30 % (none sent by itself) |
| Sent by itself (child-like / clean) | – | 22 % / 47 % of the takes; with other words than said: 1.8 % / 0.5 % of the takes |
| A soft voice (-26 dBFS) not sent when its take ends | 61.5 % | 0.2 % |
| Latency a take (median / p90) | 0.39 / 0.58 s (one hearing) | 0.43 / 0.81 s (both, one request) |

The prompt alone takes about as many retakes away, but its loops ("non, non, non, …", copies of the prompt) make some
takes very long (27 % WER on the child-like takes of the first run); the second hearing and the made-up check take them
out. The rest of a retake is the take itself: through a mumble, a clipped start and a room, both hearings agree on the
same wrong word ("Lo taglio è pieno di rame" for "Lo stagno è pieno di rane").

Today's worker (two requests), the test split (the odd lines):

*Clean takes (617)*

| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |
|---|---|---|---|---|---|---|---|
| plain (today) | 617 | 2.4 % | 6.0 % | 0.0 % | 0.0 % | 0.0 % | 0.51 / 0.80 s |
| context prompt | 617 | 2.4 % | 5.7 % | 0.0 % | 0.0 % | 0.0 % | 0.52 / 0.79 s |
| + second opinion | 617 | 2.3 % | 5.8 % | 0.2 % | 0.0 % | 0.0 % | 0.97 / 1.47 s |
| + dictionary (near-misses to the tutor) | 617 | 2.2 % | 5.5 % | 0.0 % | 0.0 % | 0.0 % | 0.97 / 1.47 s |
| + voice check (all) | 617 | 2.2 % | 5.5 % | 0.0 % | 47.2 % | 0.5 % | 0.97 / 1.47 s |

*Child-like takes (617)*

| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |
|---|---|---|---|---|---|---|---|
| plain (today) | 617 | 17.2 % | 35.8 % | 0.0 % | 0.0 % | 0.0 % | 0.54 / 1.70 s |
| context prompt | 617 | 27.3 % | 35.0 % | 0.0 % | 0.0 % | 0.0 % | 0.55 / 1.55 s |
| context prompt, unrelated context | 617 | 39.1 % | 36.6 % | 0.0 % | 0.0 % | 0.0 % | 0.55 / 1.65 s |
| + second opinion | 617 | 16.2 % | 35.0 % | 0.8 % | 0.0 % | 0.0 % | 1.01 / 2.50 s |
| + dictionary (near-misses to the tutor) | 617 | 15.8 % | 34.0 % | 0.6 % | 0.0 % | 0.0 % | 1.01 / 2.50 s |
| + voice check (all) | 617 | 15.8 % | 34.0 % | 1.3 % | 22.0 % | 1.8 % | 1.01 / 2.50 s |
| all, unrelated context | 617 | 16.6 % | 35.7 % | 1.6 % | 22.5 % | 1.6 % | 1.01 / 2.52 s |

*Child-like takes that reuse a word of the character's lines (139)*

| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |
|---|---|---|---|---|---|---|---|
| plain (today) | 139 | 10.7 % | 33.1 % | 0.0 % | 0.0 % | 0.0 % | 0.64 / 2.21 s |
| context prompt | 139 | 9.0 % | 27.3 % | 0.0 % | 0.0 % | 0.0 % | 0.65 / 2.14 s |
| context prompt, unrelated context | 139 | 31.6 % | 32.4 % | 0.0 % | 0.0 % | 0.0 % | 0.58 / 2.21 s |
| + second opinion | 139 | 8.8 % | 28.1 % | 0.7 % | 0.0 % | 0.0 % | 1.17 / 3.14 s |
| + dictionary (near-misses to the tutor) | 139 | 8.7 % | 27.3 % | 0.7 % | 0.0 % | 0.0 % | 1.17 / 3.14 s |
| + voice check (all) | 139 | 8.7 % | 27.3 % | 0.7 % | 34.5 % | 2.2 % | 1.17 / 3.14 s |
| all, unrelated context | 139 | 10.0 % | 31.7 % | 0.7 % | 34.5 % | 1.4 % | 1.13 / 3.30 s |

*Noise (120 takes without words)*

| Variant | Takes | Text in the draft | Sent by itself |
|---|---|---|---|
| plain (today) | 120 | 99.2 % | 0.0 % |
| context prompt | 120 | 100.0 % | 0.0 % |
| context prompt, unrelated context | 120 | 99.2 % | 0.0 % |
| + second opinion | 120 | 68.3 % | 0.0 % |
| + dictionary (near-misses to the tutor) | 120 | 68.3 % | 0.0 % |
| + voice check (all) | 120 | 27.5 % | 0.0 % |
| all, unrelated context | 120 | 26.7 % | 0.0 % |

The worker as built (one request, `plain=1`: the encoder shared, the token budget), the same takes heard again (the
temperature retries sample, so a reading can differ); the single hearings are parts of one request here, their latency
is in the table above and under "Latency":

*Clean*

| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |
|---|---|---|---|---|---|---|---|
| plain (today) | 617 | 2.4 % | 6.0 % | 0.0 % | 0.0 % | 0.0 % | – |
| context prompt | 617 | 2.4 % | 5.7 % | 0.0 % | 0.0 % | 0.0 % | – |
| + second opinion | 617 | 2.3 % | 5.8 % | 0.2 % | 0.0 % | 0.0 % | 0.42 / 1.15 s |
| + dictionary (near-misses to the tutor) | 617 | 2.2 % | 5.5 % | 0.0 % | 0.0 % | 0.0 % | 0.42 / 1.15 s |
| + voice check (all) | 617 | 2.2 % | 5.5 % | 0.0 % | 47.0 % | 0.5 % | 0.42 / 1.15 s |

*Child-like*

| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |
|---|---|---|---|---|---|---|---|
| plain (today) | 617 | 17.3 % | 36.0 % | 0.0 % | 0.0 % | 0.0 % | – |
| context prompt | 617 | 17.2 % | 34.7 % | 0.0 % | 0.0 % | 0.0 % | – |
| + second opinion | 617 | 16.5 % | 35.3 % | 0.8 % | 0.0 % | 0.0 % | 0.52 / 1.03 s |
| + dictionary (near-misses to the tutor) | 617 | 16.1 % | 34.4 % | 0.6 % | 0.0 % | 0.0 % | 0.52 / 1.03 s |
| + voice check (all) | 617 | 16.1 % | 34.4 % | 1.3 % | 22.0 % | 1.8 % | 0.52 / 1.03 s |

*Child-like, reusing the character's words*

| Variant | Takes | WER | Needs a retake | Nothing heard | Sure (sent by itself) | Wrong while sure | Latency median / p90 |
|---|---|---|---|---|---|---|---|
| plain (today) | 139 | 10.8 % | 33.1 % | 0.0 % | 0.0 % | 0.0 % | – |
| context prompt | 139 | 9.0 % | 27.3 % | 0.0 % | 0.0 % | 0.0 % | – |
| + second opinion | 139 | 9.0 % | 28.8 % | 0.7 % | 0.0 % | 0.0 % | 0.56 / 0.96 s |
| + dictionary (near-misses to the tutor) | 139 | 9.1 % | 28.1 % | 0.7 % | 0.0 % | 0.0 % | 0.56 / 0.96 s |
| + voice check (all) | 139 | 9.1 % | 28.1 % | 0.7 % | 34.5 % | 2.2 % | 0.56 / 0.96 s |

*Noise*

| Variant | Takes | Text in the draft | Sent by itself |
|---|---|---|---|
| plain (today) | 120 | 99.2 % | 0.0 % |
| context prompt | 120 | 100.0 % | 0.0 % |
| + second opinion | 120 | 70.8 % | 0.0 % |
| + dictionary (near-misses to the tutor) | 120 | 70.8 % | 0.0 % |
| + voice check (all) | 120 | 30.0 % | 0.0 % |


**Latency** (`talk-bench/timing.py`, 80 child-like takes, the ways in turn on the same takes):

| Model | One plain hearing (today's talk) | Both hearings, two requests | Both hearings, one request (`plain=1`) | Same texts as two requests |
|---|---|---|---|---|
| large-v3-turbo | 0.39 / 0.58 s | 0.80 / 1.11 s | 0.43 / 0.81 s | 75 of 80 |
| large-v3 | 0.57 / 0.80 s | 1.14 / 1.60 s | 0.93 / 1.32 s | 78 of 80 |

Median / p90 a take, on the RX 9070 shared with the node's own worker and other jobs. The second hearing costs 0.04 s at
the median with the encoder shared (0.43 against 0.39 s), and the token budget keeps a loop's retries short (p90 0.81
against 1.11 s for the two requests). The texts that differ are noise heard at a higher temperature, which samples.

**Near-misses.** As a change of the draft's text, the matching made 7 changes on the 2,478 takes, none right: "grandio"
→ "radio" for "il granchio" (a villager's like), "bebe" → "Bene" for "Adesso beve!", "Tego" → "Teo" for "Prego",
"cuscolo" → "cuscino" for "piccolo", "Uovi?" → "Uova" (a line about the learner-style mistake "uovi"). So the matched
reading goes to the tutor as an alternative, and the draft keeps what was heard.

**Noise.** Whisper writes "Grazie." or "Grazie a tutti." over a room's noise (99 % of the noise takes got text with the
plain hearing), with word probabilities of 0.6 to 0.9, as high as a real "Grazie" heard through noise, and
large-v3-turbo's `no_speech_prob` is 0 there. The two hearings agree on it about a third of the time. What tells them
apart is the voice in the take: every room and every breath-and-knock take (60) has at most 0.01 s voiced, under the 0.1
s a voice needs, and 0.6 % of the child-like takes (none of the clean ones) have less, most of them single words that
are misheard anyway. On the emulator, a burst of pink noise over a silent room measured 0.2 s voiced at first: a low
rumble's autocorrelation stays high at a pitch's lag; a voice's period is now a peak after a valley (0.3 over it), and
the burst measures 0. What still reaches the draft (30 % of the noise takes) is voiced: a sung "ooo" or a reversed
mumble heard as "Sì" or "Grazie."; none of it goes by itself.

**The take** (`talk-bench/gate.py`: the gate on the teo takes' 100 ms levels, 620 of them, each scaled to a peak; no
tap, the take ends by itself):

| Speech peak | Gate | Not sent when it ends by itself ("I didn't catch that") | Ends within 4.5 s of the last word | Waits for the 10 s give-up |
|---|---|---|---|---|
| -30 dBFS | today (1500, 150 ms to start) | 100 % | 0 % | 100 % |
| -30 dBFS | the room's, as built | 1.0 % | 93.5 % | 0.2 % |
| -26 dBFS | today | 61.5 % | 1.8 % | 98.2 % |
| -26 dBFS | the room's | 0.2 % | 92.9 % | 0 % |
| -22 dBFS | today | 4.4 % | 53.7 % | 41.9 % |
| -22 dBFS | the room's | 0 % | 92.6 % | 0 % |
| -18 dBFS | today | 0.2 % | 54.5 % | 10.5 % |
| -18 dBFS | the room's | 0 % | 92.4 % | 0 % |
| -22 dBFS, the first word at the first tick | today | 4.0 % | 54.0 % | 41.6 % |
| -22 dBFS, the first word at the first tick | the room's | 4.0 % | 92.4 % | 0 % |

Today's gate needs 3 ticks in a row over 1500 to start: a short word ("Sì", "Ciao") rarely has them, so the take runs to
the 10 s give-up; and its quiet is under a sixth of the peak, which a room at 8-18 dB under the voice never is, so at
-18 dBFS 36 % ran on more than 6 s after the last word. No take was cut before its last word by either gate. The room's
gate starts at once and is quiet under twice the room only once the room is well below the voice (a quarter of the
peak): when the voice comes at the first tick, the room isn't known yet and nothing is cut (the variant with the room of
the first 300 ms cut 3.5 % of those). A quiet room alone (-46 to -34 dBFS peak) starts no take in either. Which level
Teo's voice has on the tablet isn't known: the LaniStt lines will say (peak, speech, room).

**The model.**

large-v3 against large-v3-turbo, both as built (one request, `plain=1`, the same weighing), on the odd lines (617 clean
and 617 child-like takes) and the 120 noise takes:

| | large-v3-turbo | large-v3 |
|---|---|---|
| clean: needs a retake, plain → all | 6.0 % → 5.5 % | 5.7 % → 5.0 % |
| clean: WER, plain → all | 2.4 % → 2.2 % | 2.7 % → 2.2 % |
| clean: sure / wrong while sure | 47.0 % / 0.5 % | 56.7 % / 1.1 % |
| clean: both hearings, median / p90 | 0.42 / 1.15 s | 1.09 / 1.82 s |
| child-like: needs a retake, plain → all | 36.0 % → 34.4 % | 34.8 % → 32.1 % |
| child-like: WER, plain → all | 17.3 % → 16.1 % | 15.7 % → 13.4 % |
| child-like: sure / wrong while sure | 22.0 % / 1.8 % | 26.6 % / 2.4 % |
| child-like: both hearings, median / p90 | 0.52 / 1.03 s | 1.17 / 3.10 s |
| child-like, reusing the character's words: needs a retake, plain → all | 33.1 % → 28.1 % | 32.4 % → 26.6 % |
| child-like, reusing the character's words: WER, plain → all | 10.8 % → 9.1 % | 10.1 % → 8.4 % |
| child-like, reusing the character's words: sure / wrong while sure | 34.5 % / 2.2 % | 44.6 % / 5.0 % |
| child-like, reusing the character's words: both hearings, median / p90 | 0.56 / 0.96 s | 1.29 / 3.07 s |
| no_speech_prob of the plain hearing, noise takes (median / max) | 0.0 / 0.0 | 0.815 / 0.923 |
| no_speech_prob, child-like takes (median / max) | 0.0 / 0.0 | 0.097 / 0.878 |
| noise takes with text in the draft (all) | 30.0 % | 40.0 % |
| VRAM (stt-local/README.md) / load | 2.4 GiB / 9 s | 4.3 GiB / 14 s |


large-v3 needs a retake for 2.3 points fewer child-like takes (32.1 % against 34.4 %) and has a working `no_speech_prob`
(0.8 on noise, 0.1 on speech; turbo's is 0 everywhere), but takes 2.3 times as long for the two hearings (1.17 s against
0.52 s, median) and 3 times at the p90 (3.1 s against 1.0 s), on a GPU shared with the voice worker, with 4.3 GiB
instead of 2.4. Its word probabilities run higher: at the same 0.85 more of its lines would go by themselves, and more
of them wrongly (2.4 % of the child-like takes, 5.0 % of those that reuse the character's words, against 1.8 % and 2.2
%), so it would need its own threshold; and more of the noise takes reach the draft (40 % against 30 %). Not clearly
better at an acceptable latency: large-v3-turbo stays. Should real takes show turbo struggling where large-v3 doesn't,
the switch is `FLUENT_STT_MODEL_IT=large-v3` in `~/.config/fluent-stt.env` (the model downloaded into the service's
models directory first: the worker never downloads one; `sureWord` measured again for it).



## Tap counts

| A line of the talk | Before | After |
|---|---|---|
| heard right, a child (send by itself on) | 🎤, ➤: 2 taps | 🎤: 1 tap (it goes after 1.8 s) |
| heard right, an adult (off by default) | 🎤, ➤: 2 | 🎤, ➤: 2 |
| a take of noise | 🎤, then the draft cleared (✕) or sent as a line: 2-3 | 🎤: "I didn't catch that", nothing to clear |
| a soft voice that never reached the fixed level | 🎤, 10 s waiting, "I didn't catch that", 🎤 again | 🎤: the take ends 3 s after the last word |

## How to use it

Nothing to turn on in the app; "Send by itself" is in Settings, "Sound and voice". The node's worker needs the new
`server.py` for one request per take and for "sure" (see "Deploy"); the bridge works with the old worker too (two
requests, nothing sure).

QA: `LANI_QA_MIC=1 LANI_QA_TALK_CLIP=<an Italian clip> LANI_QA_TALK_TEXT="Ciao! Giochi a calcio?"
LANI_STT_URL=http://127.0.0.1:<a worker of your own> LANI_DEV_DATA=<make-learner.ts --target it --base de --name Teo>
LANI_CULTURE=friuli LANI_CHILD=1 LANI_QA_SETUP_PAIR=it-de LANI_QA_PAIR=it-de LANI_QA_LEARNER=Teo companion/bin/qa
--no-build --no-town --steps talk-mic`. **The emulator's microphone hears a file**: the emulator's own WAV audio driver
(`QEMU_AUDIO_DRV=wav`, `QEMU_WAV_IN_PATH=<file>`, with `-allow-host-audio`) reads its input from a WAV file, and the
console's `avd rewindaudio` starts it over; QA writes each recording into that file in place (15 s, 44.1 kHz stereo, its
quiet after it) and rewinds it as the app starts recording. Nothing of the host's audio is touched (the PulseAudio
backend, `-audio pa`, didn't start in the headless emulator: "Could not init `pa' audio driver").

## QA on the emulator

`--steps talk-mic` on a synthetic Teo (Italian from German, Friuli, a child), Pastore Davide's talk ("Ehi, ciao! Sei
nuovo?"), the emulator's microphone fed the boy's voice saying "Ciao! Giochi a calcio?" from the test set, the dev
bridge's recognizer a worker of the run's own on the CPU (8 threads), screenshots in `scratchpad/ux-stt/qa-final/`:

1. "Send by itself" on (`09-talk-mic-setting-on`): the take ended 3 s after the line (`ended=pause`, room 0, speech
   level 400, 17 loud ticks); both hearings "Ciao! Giochi a calcio?" (0.97 each, every word 0.89 or more, 1.33 s
   voiced): sure. "➤ Invio … · Wird gesendet …" with "✋ Tieni · Behalten" for 1.8 s (`11-talk-mic-said`), then the
   line went by itself (🎤 Ciao! Giochi a calcio?) and Davide answered (`12-talk-mic-sent`): one tap for the line.
2. A room's noise (pink, 2.5 s): "Grazie." with the conversation, "Grazie a tutti." plain, 0.01 s voiced: noise.
   Nothing in the draft, "🤔 Non ti ho sentito · Ich habe dich nicht verstanden …" (`13-talk-mic-room`).
3. "Send by itself" off (`14-talk-mic-setting-off`): the same line, heard surely again, waits in the draft for ➤
   (`16-talk-mic-draft`), as every line did before.

The node took 6 to 7 s for the two hearings on the CPU (0.4 s on the node's GPU). Getting there showed two things now
fixed: the headless emulator's PulseAudio backend doesn't start (the WAV driver feeds the microphone instead), and a
burst of pink noise over a silent room measured 0.2 s voiced, so its "Grazie." went to the draft (the voice measure's
valley, "Noise" above).

## Deploy

The bridge and the app ship as always (the bridge's code with its restart, then the app). The Whisper worker is outside
the repository's deploy and needs its new `server.py` for one request per take (two otherwise: 0.80 s instead of 0.43 s
a take, median) and for the voice measure (without it no line is sure, so nothing goes by itself, and a room's noise
that both hearings write as "Grazie." lands in the draft as it does today):

```bash
cp companion/stt-local/server.py ~/.local/share/fluent-stt/app/server.py   # the installed copy (fluent-stt, before the rename)
systemctl --user restart fluent-stt                                        # ~25 s: the model loads again
curl -s http://127.0.0.1:8796/health | head -c 200; echo                    # "ok": true once loaded
```

The new `server.py` reads the old unit's `FLUENT_STT_*` and `~/.local/share/fluent-stt` as before (`adopt_legacy_env`,
`share_dir`). `companion/stt-local/install.sh` would do it too, but also replaces `fluent-stt.service` with
`lani-stt.service`; that rename is a step of its own. No model or environment change: large-v3-turbo stays for Italian
(`LANI_STT_MODEL_IT`/`FLUENT_STT_MODEL_IT` unset); `LANI_STT_BUDGET` (12) needs no setting.

## What's left

- **Real takes.** Every number here is of synthetic speech (the app's own ElevenLabs lines, made child-like); Teo's and
  Jan's own takes are the test that counts. The app logs each take (`LaniStt`): after a week of talks, the share of
  takes per line, the readings that disagreed, the levels of Teo's voice (the gate's room and speech level) and how the
  takes ended show what to tune next.
- **Slovene.** Not measured: the test set is Italian. Zero-shot turbo is weak on short Slovene sentences (31 % WER on
  clean clips, stt-local/README.md "Model choice") and good with a prompt of the expected sentence; the conversation as
  the prompt should help Jan more than Teo, the noise guards apply as they are. Slovene has no near-miss matching (its
  dictionary is too small, 5,774 lemmas).
- **Near-misses in the draft.** On this test set no match into the draft was right; with real takes logged, it can be
  looked at again (a match where the character just said the word, as "Al ruscello" after Davide's line).
- **The speak exercises and the scene dialogs' 🎤** keep the fixed gate (1500): a soft voice waits for its 8 s give-up
  there and isn't sent unless tapped. The room-aware gate would help, but there the prompt is the expected answer: a
  take of noise it starts could be heard as the right answer. It needs the voice measure in their path first (the worker
  sends it in every response now; the app would check it before grading).
- **Visits.** A talk on a visit in another language gets no near-miss matching (only the village language's dictionary
  is the bridge's at hand); everything else works.
- **The phone's recognizer** (the node unreachable) is unchanged: no context, no second reading, nothing sure.
- **A child's app set up by hand.** "Send by itself" follows the app's child flag, which comes with pairing by QR code
  (`PairedInfo.child`); an app connected with a typed token doesn't know it's a child's (as for the parent-gated
  settings), and the switch has to be turned on there. QA's setup is such a one: its step turns it on.
- **The strings** `talkInput.sending`, `keep`, `bySelf`, `bySelfHint` are machine-written in sl, de and it: for the
  native check.
