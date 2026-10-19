# Built: Zala's lines in turn, and a voice's own pace (2026-10-19)

Jan reported: "Zala talks a bit slow, and she gets cut off at «Vidim, vidim nekaj, …» by the next sentence. If I replay it,
it is complete." This is not a phase of plan 5; it was built in the same round (branch `ispy-voice`). The full
descriptions are in companion/SCENES.md ("I spy", "Lines in turn") and companion/README.md ("Voice", "A voice that speaks
slowly: its own pace").

## What

### The cut-off: lines in turn

- **The cause.** A game of «Vidim, vidim» adds several of the child's lines at once: the game's line and the first clue
  (`ISpyRun.start`), a guess with the reaction and the next clue, the next round's line and its clue. The scene screen said
  them in a loop: `speaker.say(line)`, then `delay(700 + 70 ms a character)`, then the next line. That delay was a guess at
  the line's length, not the clip's. Measured on Jan's store (read only, a copy): Piku says "Vidim, vidim nekaj, česar ti
  ne vidiš …" in 3.80 s, the guess was 3.43 s. And a line voiced on demand (the lazy re-voice made her I spy lines in Piku
  that day) spends its first seconds on the way from the node, inside the same delay. So the first clue's `say` cut the
  game's line off. A replay plays the clip already on the phone, to its end. 19 of her 400 I spy clips are longer than
  the guess (up to 0.96 s: "To je metulj! Glej, tukaj je."). The typing was out of step too: the clue typed in at once,
  while the voice still said the game's line.
- **The fix.**
  - `Speaker.sayThrough` says a line and returns once it has been heard: the speaker is busy while the clip loads from
    the node and while it plays (`Speaker.heard`), and every route marks that at once (a clip, the family's recording, the
    offline voice; Android's text-to-speech now from the moment the line is handed to it, and only its current
    utterance's callbacks count; one it never starts is let go after 4 s). It returns early when something said or stopped after it cuts it, and gives up after
    45 s (a player that never ends). Cancelled, it stops the line at once (`stopIfLeft`).
  - `Speaker.sayInTurn`: one of several lines in a row. It waits until what is being said has ended (`awaitQuiet`: a 🔊
    tapped meanwhile is heard out), then says the line to its end; with no voice at all it takes the time to read it
    (`Speaker.readingMs`, the old estimate).
  - `ui/scene/LinesInTurn.kt`: the game's lines come out one after another. Each of the child's lines is shown (typed
    in) and said as the one before it has been heard; 0.35 s after a quiet spell, 0.25 s between two in a row. The
    learner's guess comes out in its turn, at once and unsaid. Lines added meanwhile wait; nothing is skipped. The panel
    shows the lines out (`ISpyPanel`'s `shown`), the newest typed in; the child is drawn talking while one is said.
  - A tap on another 🔊 stops the line at once (the next waits for the 🔊). Leaving the game (✕, back, "Nazaj v prizor")
    cancels the loop, which stops the line at once. Before, a closed game's line played on.
- **The other places checked.**
  - A villager's greeting ("Pogovori se": a greeting, then a memory) had the same loop (`900 + 70 ms a character`, at
    most 5.5 s): the memory came after that guess, so it cut a greeting whose clip was longer, or still on its way. It
    now says each line with `sayInTurn`, 0.25 s apart.
  - A scene's dialog, the storyteller's evening story (a dialog run in the fire's scene), a keeper's talk, an arrival,
    a project's step, a guest's dialog on a visit: one line per step. Each waits for the learner's "Naprej" or pick, so no
    line comes over another by itself; the learner's tap cuts the line as it always did. The shared estimate timed only
    the talking pose there: the person is now drawn talking while the line is heard (`sayThrough`, `stopIfLeft = false`:
    the line plays on as before when the dialog moves on), for the reading time when nothing can say it.
  - A gift's reply and the tutor's talk say one line; not changed.

### Zala's pace: a voice's own tempo

- **The cast** (`voice-cast.json`): a speaker (or a voice in a language) can have a `tempo`, a factor from 0.5 to 2: a
  number for every engine, or by engine (`{ "elevenlabs": 1.1 }`). `cast.ts` `tempoOf`, `tempoVoices`.
- **The pace a clip is stored at** (`voice.ts` `clipPace`): its voice's tempo by its engine, times the local voice's pace
  (`LANI_VOICE_GEPARD_TEMPO`, `_PAUSES`) for a local clip. It runs through the existing pace chain (`paceChain`: ffmpeg
  `atempo`, the pitch kept) in the level's encode: after a carrier sentence's cut (the word is cut at the take's own
  timestamps, then paced), after a voice's denoise filter (also a stand-in's), before the edges are measured and trimmed
  and the clip levelled. The row records the factor (`tempo`), the file name includes it.
- **The clips from before:** `voice-build --pace [--run]` (the old `--tempo` is the same mode). `VoiceStore.paceDue`: the
  factor from the tempo a clip was paced at to the one it should be at, from its file as it is. So a second run does
  nothing, a changed tempo paces by the difference, and a tempo taken away slows the clips back down. A local clip that
  the local voice's pace sped up before (none set now) still waits for `--restore-gepard` (the restore leaves a clip at
  its voice's own tempo alone). `voice_status`'s `tempo` lists the voices with a tempo and their clips at it.
- **Zala: `"tempo": { "elevenlabs": 1.1 }`.** Measured on Jan's store (read only: 60 to 80 sentences of 20 to 90
  characters a voice; syllables are the vowels and a syllabic r, over the time from a clip's first sound to its last):
  - the girl (Piku) 3.31 to 3.38 syllables a second (3.93 to 4.0 without the pauses);
  - the cast's ElevenLabs speakers: grandpa 3.13 to 3.18, grandma 3.36 to 3.41, teacher 3.66 to 3.71, young-woman 3.73,
    boy 3.76 to 3.89, female.sl (Nina) 3.80 to 3.95, young-man 4.13, male.sl (Luka) 4.71; their middle 3.73 (4.32
    without the pauses). The villagers' own voices: Janez 2.73, Micka 3.06, France 3.28, Ančka 3.29, Tone 3.46, Anton
    3.64, Vida 3.74, Luka 4.17, Marko 4.40;
  - so the girl is 10 to 13 % under the cast's middle and 11 to 15 % under the other child; ×1.1 takes her to 3.64 to
    3.72 (4.32 to 4.40 without the pauses): the middle;
  - her local clips (nina, 541) are the fastest of all, 5.19 to 5.36: the tempo is ElevenLabs' only.
- **On Jan's store** (the dry run on a scratch copy of the girl's clips, read only): 445 of her ElevenLabs clips wait
  (335 Piku, 110 in earlier voices that the lazy re-voice makes again anyway), 11,373 characters; 20.1 → 18.3 minutes
  of audio, 9.8 → 10.8 characters a second (the median of texts of 15 to 80 characters).

## Files

- App: `data/Speaker.kt` (`sayThrough`, `sayInTurn`, `awaitQuiet`, `heard`, `readingMs`; the TTS utterance ids),
  `data/Voice.kt` (QA's clip log), `ui/scene/LinesInTurn.kt` (new), `ui/scene/SceneScreen.kt`, `ui/scene/ISpyPanel.kt`,
  `ui/villagers/VillagerParts.kt`, `ui/scene/KeeperScene.kt`, `ui/villagers/ArrivalScene.kt`,
  `ui/scene/ProjectStepScene.kt`.
- Bridge: `src/cast.ts`, `src/voice.ts` (`clipPace`, `paceDue`, `tempoQueue`, `tempoStats`, `processed`, `processClips`,
  `restorePaced`), `bin/voice-build` (`--pace`), `voice-cast.json`.
- Tests: `LinesInTurnTest` (4), `SpeakerHeardTest` (4); the smoke's voice section (8 new checks, 3 changed).
- QA: `companion/bin/qa --steps ispy` (`speech_log`, `lines_in_turn`).
- Docs: companion/SCENES.md, companion/README.md, the cast's comment.

## How to use

- The app: nothing to do; the lines come in turn.
- The pace, after the merge, on the node (a backup of the voice cache first: `lani-backup`):
  `companion/bin/voice-build --pace` (the dry run), then `companion/bin/voice-build --pace --run`. A running bridge tells
  the app within a minute (`voice_updated`); the phone downloads the new files as it plays them. New clips are paced as
  they are stored once the bridge runs this code.
- Another voice that sounds slow: measure with `voice-build --pace`'s dry run after setting a tempo, compare with the
  other voices.

## Tests

- **The bridge:** `bunx tsc --noEmit -p .` clean; the whole smoke (ports 19600–19604): 2,540 checks, all passed. New in
  its voice section: the cast's tempo (by engine, a language voice's, none for one's own voice), `clipPace`, a tempo out
  of range refused, a new clip of the girl (×1.1: its sound and pause ÷1.1, edges and level after, recorded), the local
  worker's (the girl's own pace, the boy's ×1.2, ×1.26 with the local pace), the carrier cut (cut at 0.91–1.51 s as
  without a tempo, then 0.6 s ÷1.25), `voice-build --pace`'s dry run, its run (`--max 1`, the rest, nothing), a changed
  tempo (by the difference), a tempo taken away (slowed back down); three checks adjusted for the girl's tempo (her
  level clip, `tempoStats`' shape).
- **The app:** the unit tests, 2,853, 0 failures (at 828df007; after the TTS watchdog, 9b1b29eb, the data and scene
  tests again: 544, 0 failures); `LinesInTurnTest` (4: the game's line
  to its end before the clue, the guess in its turn and lines added meanwhile waiting, leaving stops the line, the first
  pause), `SpeakerHeardTest` (4: on its way and playing, cut, the 45 s cap, nothing to say it); `ISpyTest` and
  `DialogMoodTest` after it.
- **Python:** `tests` 249 OK, `companion/tests` 68 OK.
- **The emulator** (the debug build; the dev bridge on 8791 on a synthetic learner from the templates, `LANI_DEV_DATA`,
  `LANI_ENV_FILE=/dev/null`; the fake ElevenLabs with 1.8 s a take plus 15 ms a character, as a line voiced on demand;
  `companion/bin/qa --no-build --no-town --steps ispy`, twice): setup, ispy and the crash check passed. A game with
  Tine by the fire, three rounds: 12 lines said, 12 clips played to their end, none started before the one before it
  had been heard, none stopped. For example the game's line: asked at 18:52:44.99, its clip from 47.91 to 49.81 (1.78 s,
  after 2.9 s on the way from the node), heard at 49.89; the first clue's clip started at 52.51. The old loop would have
  said the clue 3.43 s after the game's line, at 48.42, half a second into its clip: the cut-off Jan heard. A second
  game left (back) while its first line played: "clip stopped" at once. The log with its times is the run's
  `ispy-speech.log`.

## What's left

- A tempo belongs to the voice it was measured on: after a voice swap (a new `since`), measure again.
- The person's own playback pace on the phone (a voice profile's `rate`, 0.96 to 1.04 for a second person of an
  archetype) stays as it was; Zala, the first girl, has 1.
- A learner who taps in the picture while the child still speaks sees the guess when the child's line has ended (lines
  in turn); if that feels slow, the reaction could come first and the pending clue after.
