# Built: the app's and the bridge's loose ends (polish-app)

*Built 2026-11-01, branch `polish-app` (completing the app, wave A, part 9). Jan: "can you make app complete - all what is
missing?"; "for choices - make it the best - prod ready". Eleven loose ends from the rounds before, for Jan (Slovene
from English, an adult, a phone) and Teo (about seven, Italian from German, the Friuli village, a tablet of 2560 × 1600
on its side and upright). Two helpers worked on their own branches, taken in here: the visit voices
(`polish-app-voices`) and the mill, the hint chip and the bold forms (`polish-app-render`).*

## What, item by item

### 1. The phone's stage band under a long card

**Before** (ux-polish.md, "What's left"). On a phone upright an answer's card under the question leaves the question its
first 140 dp (`FeedbackPlace.QUESTION_MIN_DP`), its middle scrolling. The stage band above (the companion's portrait and
line on the place's backdrop, 136 dp and more, about 200 with a two-line bubble) kept its room all the while, so after a
long explanation the sentence just answered showed only its first lines.

**Now** (`ui/PlayerScreen` `CardFold`, `ui/stage/StageBand(fold)`):

- When the card under the question needs all it may have (measured at its greatest height: a long explanation that
  scrolls in the card) and the run has a stage, the band folds to its small form (the 46 dp portrait and the companion's
  line in two lines, 56 dp), as it does while typing.
- The card keeps the height it had. The room the band frees goes to the question: on the emulator's phone, from the run's
  title to the card, 140 dp before, at least 200 now (QA's `feedback-tablet` checks it).
- It stays folded until the next answer's card, so the band doesn't open and fold again as the card measures anew. A
  short card (a right answer and "Naprej") leaves the band as it is, so the screen doesn't jump at every answer.
- A tap on the folded band opens it again for that card (and keeps it large, as any tap that makes it larger). TalkBack's
  "make larger" does the same.
- Beside the question (the tablet on its side, a phone turned) nothing folds: the card has its own column.

### 2. The flaky smoke check: "POST /words … gathered in one line"

**Before.** The check slept a fixed 1.3 s after the first word's POST, for a note gathered 1 s after it. Under load
(the POSTs between, or the note's way over stdio, took longer than the 0.3 s margin) the note hadn't arrived yet, and
the check failed twice at a load average of 53.

**Now** (`test/smoke/lexicon.ts`):

- The check awaits the note (up to 10 s after the gathering time), then waits 0.3 s more, so a second note, which would
  break "one line", has its moment.
- A slow bridge still fails, by a check of its own with its numbers: "the words-added note is sent LANI_WORDS_NOTE_MS
  after the first word, not at once and not more than 2 s late". It is timed by the bridge's own clock: the note's
  `msg_id` is the moment the bridge sent it. It must come no earlier than the gathering time after the first POST began,
  and no more than 2 s after the gathering time from when that POST was answered.

### 3. `set_target_level` for the app's own chat tutor

**Before** (target-next-age.md). The in-app tutor's fixed tool list (phase A, `PhaseA.TOOLS`) had no
`set_target_level`. On a hosted phone the aim was chosen in setup and changed in the settings only. A learner who asked
the tutor in the app to "aim at B1" got nothing done.

**Now** (`tutor/Tools.kt`, `tutor/DryRuns.kt`):

- `set_target_level` is phase A's 18th tool, a write after `set_rhythm`. It is the bridge's own tool, exported to
  `companion/tutor/tools/set_target_level.json`. The live tutor calls it through the node's `/tutor/tools`, with the very
  handler Claude Code's call uses, so the rule is the same: a raise starts with the village's next age (the step the
  village is on keeps its words), and a lower target, or the current one again, applies at once. The lease guard is the
  same too: a `set_` tool is refused while the phone holds the data. A node without the tool says so ("needs an update").
- In shadow mode it is a dry run: the schema checks the level (A1 to C2), and the model reads the rule as the live answer
  would say it.
- The prefix changed (a tool more), so its fingerprint did too. A thread made with the old prefix starts anew, as after
  any app update that changes a tool.

### 4. Tutor scenes and Friuli-only slots

**Before** (culture-source.md). A tutor's scene could name the osteria (`bocce`'s `inn`), the fontanella
(`playground`'s `fontanella`) or the altar candle (`chapel`'s `altarcandle`) in a village of another culture. The app
left it out, since nothing draws it there, and the words panel listed it as waiting for a level no place reaches.

**Now** (`src/scenes.ts` `lookOnlyErrors`, `features/scenes.ts`):

- `publish_scene` and `publish_dialog_variant` refuse a scene or a variant that names such a slot, by the village's
  culture (`LOOK_ONLY`). This covers an object on that slot and a tap turn's choice on it (the learner could never tap
  it). The refusal names the place and says why: "objects[1] (fontanella): the playground's "fontanella" is drawn as a
  thing only in friuli's look, and this village is primorska's: leave it out (the app doesn't draw it here)".
- The smoke checks the refusal on Primorska's bridge, and the rule on objects and taps in each culture. It also checks
  that every culture pack's own scenes (100, the curated Slovene ones among them) name only what their look draws.

### 5. The car's notification channel

**Before** (app-icon.md). The car's "getting ready" (`RoadPrepService`, an ongoing progress notification) posted to
`road-prep`, made with Android's default badge, so its progress could put a dot on the icon. A channel's badge can't be
changed once it is made.

**Now** (`data/NotifyChannels.kt` `RoadChannel`, `data/Notifier.ensureRoadChannel`):

- A new channel, `road-ready`, "🚗 Za pot · For the road" in the learner's own pair: LOW, silent, no dot.
- The old channel's settings carry over, as the "tutor" channel's did: blocked stays blocked, it is never louder than
  LOW, and the lock screen visibility is kept. The old channel is deleted once the new one exists.
- It is made with the other channels (at the start, on a new pair, before every post) and before the road's service
  posts, so its name follows the pair. QA's `road` step reads the channels from `dumpsys notification`: `road-ready`
  with `mShowBadge=false`, and no live `road-prep`.

### 6. A child's phone paired by a typed token

**Before** (app-icon.md). With the app closed, the background check knew a child's phone from the app's last word, the
pairing's flag (a QR code) or the hosted profile's. A phone set up with a typed token has no pairing flag. Until its app
had been opened on this version, nothing had said, so the phone was notified as an adult's: the watchman's alarm and
the evening reminder with its streak.

**Now** (`data/BackgroundCheck.kt` `PhoneChild`, `AppViewModel`):

- When nothing has said, the background check asks the node's own town row (`GET /towns`' `self.child`, what the app
  reads too) once and keeps the answer.
- An older node without towns is an adult's, as the app says too, and that is kept. A failed request keeps nothing, so
  the next check asks again.
- The app keeps its answer only once it knows: paired by a code, hosted, or the town's row read. Before, the `false` of
  a start before the towns came was kept at once, and it would have said "an adult's" to the background check if the app
  was closed then.

### 7. QA's navigation step on a long Today card

**Before** (nav-perf.md). The step's first check, "the learning tiles' first row within three of QA's swipes of Today",
failed on the grown synthetic learner. Its Today card is long (two new modules, a tip, two arrivals to meet, "Še 7 v
vasi"): that is a learner's content, not a fault of Home.

**Now** (`companion/bin/qa` `tiles_under_today`):

- The step checks what the tidy Home promises (navigation.md: the learning tiles right under Today), however long
  Today is. It swipes down until the tiles' heading is in view (at most `TILES_SWIPES`, 10).
- It passes when the heading is the first of Home's later sections (the villagers' strip, which comes after the tiles,
  is never before it or above it) and the tiles' first row is in view on the heading's screen or the next.
- It prints how many swipes Today took. `companion/tests/test_qa_nav.py` covers the decision.

### 8. The visit voices (helper, `polish-app-voices`)

**A review of another language at home.** A review of a visited town's words at home said its exercises with the home
village's voices. Now `ExerciseRun` passes its language to the exercises it shows (`LocalSpeechLang`). Every 🔊, every
listening question and every line an exercise plays by itself goes through the chain a word card's 🔊 uses
(`Speaker.sayIn`): the clips of a linked town of that language through the node, then the phone's offline voice of it,
then Android's voice for it, then the note that offers the voice. No re-record is offered for it. Listening questions in
that language are asked only when the phone itself can speak it, so a question that is only a sound never waits on
another town's node. The stage's companion still greets in the village's language.

**Kept clips checked again, and a visit's first lines got ahead.**

- A line the visitor's node voiced, or a stand-in voice, is checked against the host after a day
  (`LANI_VISIT_VOICE_RECHECK_HOURS`). The host's own take replaces it once there is one.
- The host's own clips are checked after a week (`LANI_VISIT_VOICE_HOST_RECHECK_HOURS`) by their SHA-256
  (`GET /town/voice?…&have=`). An unchanged clip answers `same`, with no audio. A re-voiced line's new take is fetched.
- A clip replaced just after its URL was handed out is still served for 2 minutes (the app's download had failed with a
  404). Orphan files go at the start.
- As a visit begins, the app gets the town's likely first lines onto the phone (`app/VisitAhead.kt`, `Clips.ahead`,
  `POST /towns/:id/voice/prepare`): each person's greeting for the hour and hello, and the first lines of today's guest
  dialogs, at most 40. These are only clips already kept or the host's own. None is voiced for the visit, and all stay
  within the clip cache's cap. In QA 7 of 13 came ahead, and Stari Janez's greeting played from the phone at once.

**Someone's own voice on a visit: built, off by default.** Such a voice is designed in the host's ElevenLabs account, so
only the host can speak with it. The host now makes a guest's line in that voice, but only when its node allows it
(`LANI_TOWN_VOICE_OWN_CHARS`: characters a day for all linked towns together). It never takes from the reserve kept for
the host's own live lines, and it keeps the clip in its own store, so its own learner hears it too. A take not ready
within 2.5 s answers `making`, and the visitor checks again in a minute. It is off by default because it spends the
host's credits for a guest, and a guest must never spend them without the host's say. Without the setting the
archetype's voice speaks as before. **Jan's call:** set it on a node (for example 3000) to allow it.

**The hint's base pieces.** The hint's 🔊 says its base-language pieces by the phone's offline voice of the base when it
is on the phone, else Android's. A piece in the language learned without a clip goes to the offline voice of that
language before Android's. This holds at home and on a visit (`data/HintVoice.kt`).

### 9. The mill at night (helper, `polish-app-render`)

**Before** (mill-mechanics.md). In V mlinu and Nel mulino the water in the buckets, the pour, the drops, the water
falling off the buckets, the race and its waterfall were drawn through `Env.lit`. That function is meant for pixels that
skip the light pass, but these pixels then went through the light pass again, so they were lit twice. The flume's water
and the wheel's wood were lit once. At night the flume looked a bright daytime blue over a black race and black buckets.

**Now** (`game/render/scene/MillPainter.kt`): all 18 `Env.lit` calls are gone (they were all water). The mill's water is
drawn in its daylight colours and lit once by the light pass, as the rest of the scene is (the smithy's trough and the
fountain draw their water this way too). At night it is one dark blue, at dusk it is warm, and by day nothing changed.
Measured at night, the buckets kept 0.11 of their red and the race 0.12, while the wood kept 0.41. Now the buckets keep
0.45 and the race 0.37, in line with the wood. The race is brighter at night than before: it had the same double light.
`MillLightTest` (3) checks the night against the wheel's wood and pins the day. `CultureLooksTest` "mill" was taken
again for dusk-winter and night-k2 only, after looking at the frames: only water pixels differ. QA's `mill-turning`
shoots at night with `LANI_QA_MILL_HOUR=22.5`.

### 10. The hint chip reads the trigger table (helper)

**Before** (case-lens-2.md). The 📖 Namig chip at a turn decided with the app's own `Triggers` rules. The case lens, the
word card and the sentence's grammar read `companion/grammar/sl/data/case-triggers.json`.

**Now** (`game/CaseTriggers.kt`, `game/TurnHint.kt`, `app/build.gradle.kts`): the app bundles the table with the
grammar book and reads it at the turn, offline, with no lookup. The old `Triggers` and its tables are gone (TurnHint was
their only user). The chip reads the table's prepositions with their cases and senses, the negations, the quantity
words and measure forms, the numbers, the verbs and words of feeling with their cases, and the pronouns that are only
ever the subject. Its past and future cue words (včeraj, jutri) stay its own, since the table is about cases. A
preposition's question comes from its sense (Kam?, Kje?, Od kod?, O kom? O čem?). The five B1 pages still say their
rule.

Where the table decides differently from the old rules:
- After "z dvema" the preposition decides (the instrumental). Before, the number decided and the chip said the
  nominative.
- "po" no longer asks Kje?/Kam?. "blizu" and "okoli" ask Kje?.
- New triggers from the table: zahvaliti se, vseeno, rezina, pomagajva.

On the curated scenes, where the lens decides a case by a trigger (323 turns), the chip names the same word in 319. The
rest are where-to against where, which only the meaning tells, and one turn filed under the wrong page by its wrong
choice. A shared fixture of 24 turns (`companion/bridge/test/fixtures/hint-lens.json`, `bun test/hint-lens.ts --write`)
keeps the chip and the lens in step: `TurnHintLensTest` checks the chip against it, and the smoke checks the lens.
Tests: `TurnHintTest` (14, moved to the table, one more for what the table decides differently), `CaseTriggersTest` (3),
`TurnHintLensTest` (1).

### 11. Bold forms the intro makes Italian, tappable (helper)

**Before** (course-it-a2a.md). In Maestra Elena's bubble a bold form was underlined for a tap only when the app's own
check knew it (mi lavo). The read-aloud also took a form made only of the intro's own words (si lava), so she read it,
but it couldn't be tapped.

**Now** (`data/IntroSpeech.learned`, `ui/Markdown.kt`, `ui/school/IntroScreen.kt`): one check decides both the
read-aloud and what is underlined, in the bubble, on the board, in a quick question's explanation and in the summary. A
tap on "si lava" does what the others do: her voice says it. German in bold stays plain. `IntroBoldTapTest` (2)
covers riflessivi's step 1, and checks over every step that the underlined spans are exactly the ones she reads.

## How to use it

- **Jan, the stage band.** Nothing to do. After a long explanation the companion's band shrinks to its small form and
  the question shows more. Tap the band to open it again.
- **Jan, the tutor in the app.** Ask it, for example "aim at C1". It answers with what the village's step now needs and
  what the next one will need, as the settings' "🎯 Cilj · Aim" does.
- **Someone's own voice for visitors.** It is off. To let linked towns hear your villagers in their own voices, set
  `LANI_TOWN_VOICE_OWN_CHARS` (characters a day, for example 3000) in the node's lani.env and restart the bridge.
- **Teo's school.** In an Italian intro, each underlined bold form says itself with Maestra Elena's voice on a tap, "si
  lava" too.

## Tap counts

| Journey | Before | After |
|---|---|---|
| A long card on the phone: see the question just answered | 0 taps, 95 px of its line shown, 140 dp of room from the run's title to the card | 0 taps, the band folded: 237 dp of room (the sentence, the instruction and the tip whole) |
| The folded band back to full size | (it never folded) | 1 tap on the band |
| The aim through the app's tutor | not possible in the chat (in the settings: ⚙️, 🎯 Cilj · Aim, a level: 3 taps) | 1 message to the tutor (the settings' 3 taps as before) |
| "si lava" in Elena's voice | not tappable | 1 tap |
| Into the mill, the hint chip | 2, 1 | 2, 1 (unchanged) |

## On the emulator

`companion/bin/qa-slot`, slot 2, the synthetic learner Ana (make-learner.ts, Slovene from English; a copy for each run).
The "before" is the debug build of main at ba5bada7 with main's own QA script, and the "after" is this branch's build
before the helpers' commits were taken in. Screenshots are in
`/tmp/claude-1000/-mnt-scratch-github-fentas-ai-slo/721724ea-4389-48ad-b641-b6d1d5157803/scratchpad/ux-polish-app/`:
`before/` and `after/` (mine), `voices/after/` and `render/` (the helpers').

| What | Before | After |
|---|---|---|
| The phone's card under a long explanation (`15-feedback-phone.png`, `25-feedback-phone.png`) | the tall band (Babica Micka's portrait and a two-line bubble), the question's line at y 862 px, the tip cut off | the small band with her line, the question's line at y 641 px, the tip whole: 237 dp from the title to the card (feedback-tablet.txt) |
| The tablet on its side and upright | the card beside it, under it | the same (nothing folds beside it; upright the card fits) |
| QA's navigation on Ana | failed at the first check: "the learning tiles' first row within three of QA's swipes of Today" | passes end to end: "Today took 3 of QA's swipes, the tiles' first row in view after 4"; the settings, Friends, the grammar book, My words, a run's result, the village's 🤝, a scene's link and the process's death |
| The road's channel (dumpsys notification) | `road-prep`, a dot allowed (not checked by main's QA) | `road-ready`, "🚗 Za pot · For the road", importance 2, `mShowBadge=false` |
| A visit's first lines (voices helper) | Stari Janez's greeting fetched from the node at the tap | "a visit's first lines got ahead: 7 of 13", his greeting played from the phone |
| The mill at 22.5 (render helper) | the flume a bright blue over a black race and black buckets | all of it one night blue |
| Teo's riflessivi intro, tablet (render helper) | "si lava" plain | underlined, with 🔈 |

The road step fails later in both runs, the same way on main: "nothing in 🚗 Za pot on the phone" (`road:mix` empty on
this learner with `--no-town`). That is not this round's change: main's build fails it the same way. The channel check
passes before that point.

## Tests and checks

- **Android unit suite:** 3,660 tests in 471 classes, 0 failures, 2 skipped (the whole branch, the helpers' commits in). Gradle ran with `-Pkotlin.compiler.execution.strategy=in-process
  -Dorg.gradle.jvmargs=-Xmx5g`: the shared Kotlin daemon ran out of memory on the busy machine. New or extended in this
  round:
  - `FeedbackPlaceTest` (+2: the fold, the height kept, the reset, no stage)
  - `StageBandFoldTest` (1, Robolectric: tall, folded, opened by a tap, folded again)
  - `ShadowToolsTest` (the eighteen tools; set_target_level's dry run and a level the schema refuses)
  - `LiveTutorTest` (+1: set_target_level through the node)
  - `NotifyChannelsTest` (+1: the road's channel)
  - `PhoneChildTest` (5)
  - `ExerciseSpeechTest` (6), `VisitAheadTest` (2), `VisitClipsTest` (+1), `HintVoiceOfflineTest` (2)
  - `MillLightTest` (3), `CultureLooksTest` "mill" (two digests retaken)
  - `TurnHintTest` (14), `CaseTriggersTest` (3), `TurnHintLensTest` (1), `IntroBoldTapTest` (2)
- **Bridge:** `bunx tsc --noEmit -p .` clean. The smoke passed with 3,269 checks (`LANI_SMOKE_PORT=9630`, `…FRIULI…=9633`,
  `…TOWN…=9636`, `LANI_SMOKE_DRAMA3_PORT=9648`). New checks: the words-added note's timing, the look-only refusal and
  rule, every culture pack's scenes, the visit voices' re-checks, prepare and own voice (43, from 31), and the hint
  fixture against the lens. `tutor-export --check`: 89 files match (54 tools, 32 guide topics). The voices helper ran the
  relay smoke too: 45 checks, all passing.
- **Python:** `companion/tests` 142 (11 skipped), `test_qa_nav` 4 of them; `tests` 251.

## What's left

- **The road step on Ana fails after the channel check**, on main too: "nothing in 🚗 Za pot on the phone" (`road:mix`
  empty, with `--no-town`). It is not this round's change, but it is worth a look.
- **A visit's lines the host is still making** are heard in the archetype's voice the first time, and in the person's
  own voice after the re-check. The phone keeps the take it downloaded for the rest of the visit.
- **The day's audio and the car don't plan for a visit:** the app has no planned visits. A line the host has no clip of
  still waits for the node to voice it when it is said.
- **Someone's own voice for guests** stays off until a node sets `LANI_TOWN_VOICE_OWN_CHARS` (Jan's call).
- **The hint chip reads the table bundled in the APK:** an edit to case-triggers.json reaches the lens at once, and the
  chip only with a new APK. Run `bun test/hint-lens.ts --write` after a change to the lens or the table (the smoke
  flags the drift). Without a dictionary the chip can take an adjective that is also a table verb form ("pravi") for a
  verb, and it can't tell where-to from where by meaning.
- **The intro's check** is used by the intro only: other Markdown (worksheets, the cheat sheet) underlines with the
  speaker's check alone.
- **Seen in the voices helper's QA, not this round's:** the dev bridge of an Italian learner (Friuli) voiced about 200
  Slovene texts of its own villagers with `language_code it` before and during a visit ("Mandi. Jutri bo nova
  zgodba."). It looks like a fault in the dev bridge's prebuild or prefetch. If it happens on Teo's node, it wastes his
  credits. The evidence is in the helper's `companion/qa-shots/20261101-130941/bridge.log`.

## For the native check

This round adds no labels: the road's channel uses `road.title` as it is. The only machine-written language is the 24
Slovene sentences of the hint fixture (`companion/bridge/test/fixtures/hint-lens.json`, test data, shown nowhere in the
app).
