# The learner's voice in the dialogs (built 2026-10-25)

Jan asked: "If I select a sentence in a dialog, it should speak it, unless I selected it via the microphone. And does the
player have a voice, meaning male or female?" Until now the learner's own lines in a dialog were shown, never voiced,
and the voice cast had no voice for the learner.

## What

- **A player voice by gender.** `companion/voice-cast.json` has a `player` entry: `{"male": "male.sl", "female":
  "female.sl"}`, Luka and Nina, the narrators' Slovene voices. The bridge says the learner's lines in the one of the
  learner profile's `learner.gender`; a profile that says no gender gets the female one, as `genderOf` counts an unknown
  name (the content still speaks to such a learner as to a man). Its comment notes that Nina and Luka leave the
  ElevenLabs library on about 2027-10-21 and need a replacement before then.
- **The chosen line said, unless it came by the microphone.** A choice tapped (in the list or in the picture), "✓ Naprej"
  at an echo, a turn typed right, and a wrong choice tapped or typed that the person reacts to are said in the
  learner's voice; the reply waits until that line has been heard to its end (and after a 🔊 tapped meanwhile). A line
  said into the 🎤 (a choice said, a turn said whole, an echo said) isn't said again. A slip typed (a form none of the
  choices has) isn't said and has no 🔊.
- **A 🔊 on the learner's line** in the conversation, which plays it in their voice (TalkBack: "Poslušaj · Listen").

## How

- **Bridge** (`src/cast.ts`): `PLAYER = "player"`, the cast's `player` read with the rest of the cast file (a speaker,
  or a speaker's voice in a language, which the store speaks as that speaker: "male.sl" → "male"), a wrong entry logged
  and that gender left to its narrator; `playerVoice(gender)`, `voiceFor(name, gender)`, `playerVoices()`.
  `src/addressee.ts`: the addressee marks a profile that says no gender (`unsaid`), `voicedGender(a)`.
  `src/features/voice.ts`: `/voice/say`, `/voice/prepare` and `/voice/redo` take the voice `player` and say it in
  `voiceFor`'s; `/voice/say` makes a line of the learner's on the normal path with `{carrier: true, alone: true}`
  (`VoiceStore.say`): a word to three inside its carrier sentence as a re-record says it, and no other voice's clip when
  theirs can't be had now. `GET`/`POST /voice/profiles` say `player` (the voice the app looks the clips up by);
  `voice_status` shows the cast's `player`. The tutor's voices guide says the learner has a voice (tutor-export).
- **App** (`data/Clips.kt`): `Clips.PLAYER`; `player` (the node's, from the voice profiles), `playerGender` (the
  learner profile's, set by `AppViewModel`), `playerVoice` (the node's, else `playerOf(gender)`: male → "male", else
  "female"); the clips on the phone are looked up by `playerVoice` (`knownAs`), while the node is asked for "player".
  `data/Speaker.kt`: `voices(PLAYER)` is the player alone, no narrator of the other gender stands in.
  `ui/scene/DialogRun.kt`: `Said.by` (`PICK`, `TYPE`, `MIC`, `SLIP`), `Said.voiced`, `Said.replayable`;
  `choose(k, by)`, typed and said answers marked. `ui/scene/DialogVoice.kt`: `DialogVoice.toSay` (the newest step's
  lines to say), `pauses`, `say` (the learner's line, then the reply after `quiet`), and `rememberDialogVoice`, which
  every dialog's screen uses now instead of its own effect: `SceneScreen` (scene dialogs, the storyteller's story, a
  project's step and an age's party in a scene), `KeeperScene`, `ProjectStepScene`, `ArrivalScene`,
  `VisitSceneScreen`. `ui/scene/SceneDialog.kt`: `onChoose(k, by)` (the 🎤's take as `MIC`), `Mine`'s 🔊.
- **Every place checked:** scene dialogs, the storyteller's story (its turns, through `SceneScreen`), keepers' talks,
  arrivals, project steps and parties, guests' dialogs on a visit: the learner's line voiced and the 🔊; on a visit the
  phone's voice in the town's language says it (the node's clips are the learner's own language there). «Vidim, vidim»:
  the learner's guesses are words, kept silent, no 🔊.
- **Settings:** the app has no mute or auto-play setting for dialogs (the others' lines are always said): the learner's
  line follows them, at the phone's volume, with TalkBack too.

## How to use it

Nothing to turn on. To give the learner a voice of their own: add it as a speaker of the cast (`"player-male":
{gender, elevenlabs, gepard}`) and name it in `player` (one line); its clips are made as the app asks for them.

QA: `LANI_DEV_DATA=<a synthetic learner> LANI_ENV_FILE=/dev/null LANI_DEV_FAKE_ELEVENLABS=1 companion/bin/qa --no-town
--steps player-voice`; QA's new hook `mic:right` has a dialog's 🎤 hear the turn's right choice without listening.

## What's left

- The choices' 🔊 still read them in the female narrator's voice (the corpus prebuilds the choices in it); only the line
  once chosen is in the learner's voice. Voicing the choices in it too would make a male learner's choices again in
  Luka's voice (credits).
- The corpus doesn't prebuild the learner's lines in their voice: a male learner's line is made the first time it is
  said (a live line, about 20–40 characters).
- Nina and Luka leave the ElevenLabs library on about 2027-10-21.

## Tests

- **Bridge:** `bunx tsc --noEmit -p .` clean; the whole smoke (`LANI_SMOKE_PORT=19500`, the `phone` section's bundle
  included): 2,582 checks, all passed. New in `test/smoke/voice.ts`: the cast's player by gender and the female fallback,
  the addressee's `unsaid`, a swapped player (one line) and a wrong one (logged, the narrator kept), `alone`; on the
  smoke's bridge (Jan, a man): `/voice/say` for "player" made by the fake ElevenLabs in Luka's voice on the normal path,
  cached, the male narrator's clip; a short line in its carrier sentence ("Beseda je: Hvala lepa.") where the narrator's
  is plain; `/voice/prepare`; `/voice/profiles` saying `player`; the profile switched to a woman (Nina) and to no gender
  (Nina), then back. `tutor-export --check`: 80 files match.
- **App:** the unit tests, 2,916 in 367 classes, 0 failures. New: `DialogVoiceTest` (8: a tapped line said then the reply,
  a wrong pick with a reaction said, a typed right line said, a slip not, a line by the 🎤 not and only the reply, an echo
  both ways, a dialog shown again saying only its newest line of someone's, the pauses, the reply waiting for the
  learner's line and for a 🔊), `PlayerVoiceTest` (4: the voice by gender and the female fallback, the node's voice and
  the lookup, the phone's resolution, no other gender standing in), `QaHooksTest` (`mic:right`); `DialogAdaptTest`
  updated for `Said.by`.
- **Python:** `tests` 249 OK, `companion/tests` 98 OK (10 skipped).
- **The emulator** (the debug build; the dev bridge on 8791 on a synthetic learner, Tine, a man, made by the bridge's
  `initLearnerData`, `LANI_DEV_DATA`, `LANI_ENV_FILE=/dev/null`, `LANI_DEV_FAKE_ELEVENLABS=1`; `companion/bin/qa
  --no-build --no-town --steps player-voice`): setup, player-voice and the crash check passed. Babica's salt: "Dober
  večer! Lahko pomagam?" tapped, said as "node clip (player)" (the dev bridge's fake asked for Luka's voice,
  SmCUS1ekZnVjC5lqnRQG), heard after 3,022 ms, then her reply said in Zlata's; "Skoraj. Še malo soli, prosim." by the 🎤
  (QA's hook): never asked of the node nor said, her reply said; the turn typed ("pomagam"): the line said from the clip
  on the phone, heard after 1,621 ms, then her reply. The learner's line shows its 🔊 (player-voice-line.png).
