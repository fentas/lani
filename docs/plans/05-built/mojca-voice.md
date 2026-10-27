# Built: Učiteljica Mojca in her own voice, and the villagers' local clips (2026-10-27)

Jan: "generate all the Mojca substitutions (e.g. Gepard)", and "there are some gepard recordings which should be
replaced". This is not a phase of plan 5; it was built in one round (branch `mojca-voice`). The full descriptions are in
companion/COURSE.md ("Read aloud", "Voiced ahead") and companion/README.md ("Voice": "The school's introductions, read
aloud", "A villager's local clips", "Prebuild").

## What was missing

When Mojca's own clip of a line (the voice `teacher`, ElevenLabs "Milena") isn't there, the app plays a stand-in. The node's
`/voice/say` gives the narrator's clip of the same words when it can't make hers now (over the live rate limit, ElevenLabs
can't: `other()`), often an old local Gepard clip; then the phone's chain plays another of her voices' clip, the offline
Piper voice or Android's text-to-speech. So she was heard in someone else's voice:

- **The school's course was not in the voice corpus.** voice-build voiced the villagers, packs, modules, scenes and the
  rest, never what the app reads of a unit's introduction in her voice. On Jan's store (approximately): 137 of the 179
  examples had no clip of hers (37 had the Gepard narrator's, 36 Nina's, 97 nothing), and 27 of the 91 explanations (in
  English) were voiced.
- **The cast's arrivals were not in it either**: Mojca's arrival (30 lines) was voiced only when it played.
- **Her other lines:** 273 of her 284 villager, scene and project texts had her ElevenLabs clip; 11 did not.
- **18 of the 82 quick questions had their Slovene read by the phone's English voice**: the app read a prompt in her
  voice only when its check for Slovene said so, and "Micka in France … na njivi." (one Slovene word of five in its word
  list), "»smreka«" and the label "Kakšnega spola? · Which gender?" weren't.
- **2,459 villager lines had a local Gepard clip** that nobody had heard yet (77,000 characters, far over a month's
  budget: 193 heard ones were made in ElevenLabs by hand on 2026-10-27).

## What

### The course in the corpus (`companion/bridge/src/course-speech.ts`)

- **A port of the app's reading** (`data/IntroSpeech.kt`): a step's Markdown lines and segments, its runs (a span the
  check says is Slovene in the teacher's voice, the rest in the base), its plain explanation and its key
  (`<base>|<normalized text>`), the quick question (the gap a pause, the trailing brackets out) and its answer's sentence,
  the intro's own words (`vocabulary`, `within`), the summary (the unit's first grammar page's rule, else its goal). The
  Kotlin's regex and whitespace rules are copied (java.util.regex's `.`, `\s`, `$`; Kotlin's `trim()`), and the app's
  composition moved into `IntroSpeech` (`introStep`, `summary`, `summarySay`, `answer`, `gapFilled`), which the screen and
  the day's audio call, so the port mirrors one place.
- **The check for the language learned** (`Speaker.isSlovene`): `Spoken.looks` (the letters and word lists), a clip of the
  text on the node in any voice (`Clips.has`, now also a pure `Clips.has(index, text)`), a family recording (`Voice.has`).
  voice-build reads the store's index and the family's `audio/index.json` for it.
- **Who teaches:** the school's person as the app picks her (`Residents.brings(SCHOOL)`: the first of the cast by order
  whose workplace is the school), in her own voice once it is ready (`@mojca`), else her speaker (`teacher`), else the
  female narrator.
- **What is voiced**, in the corpus's new `course` priority (after the scenarios, before the A1 stories): each step's
  examples, its quick question's lines and answer, the Slovene runs of its explanation and of the summary, for the
  learner's base (the profile's; `--base` names others). The explanations themselves are the last priority,
  `explanations`, each a `CorpusItem` with its `lang`, keyed as the app keys them, ElevenLabs only.
- **The cast's arrivals** (the culture pack's `arrivals.json`, the tutor's for one of them replacing it): every level's
  late line and lines as a scene's dialog, each in its speaker's voice, in the `scenes` priority.
- **The parity fixture** `companion/bridge/test/fixtures/intro-speech.json`: 20 texts for `Spoken.looks` and 7 intros
  (biti in English and German, spol, the Markdown the app draws, quick questions, a clip making a text Slovene, an Italian
  course for a learner of German), each with the voice store's clips and what is read. The app's
  `IntroSpeechParityTest` parses each as a unit's intro and reads it; the smoke's `course-speech` section reads it with
  the port. A change to how a step is read goes into both and the fixture.

### The quick questions in her voice (the app and the port)

A quick question's prompt is in her voice when it is a sentence with a gap to fill and no Markdown (a cloze's, a
choice's), a word named in »quotes«, or what the check says is Slovene; an instruction written as a label ("Kakšnega
spola? · Which gender?": the language learned, then the base, no Markdown) is read in its two voices. A question in the
base still marks its Slovene ("Which form goes with **mi**? ___"). The 18 questions above are read in her voice now.

### voice-build

- `--only voice[,voice]`: the texts of those voices alone (someone's own voice counts as their speaker's; `@mojca` names
  it). `--only teacher` is Mojca everywhere: the course, her villager lines, her arrival, her scene and project lines.
- `--explanations`: the course's explanations in the plan and the run too; without it the plan counts them apart (a
  line under the table: how many, voiced, missing, the characters and credits). They stay on demand: the app asks for
  the current and next unit's as now.
- `--base xx[,yy]`: the learner's own languages the explanations and their runs are read in (default: the profile's).
- The plan's table has the `course` row; `local_clips` counts the villagers' local clips waiting for ElevenLabs.
- On a synthetic store (a learner made by `test/fixtures/make-learner.ts`, no clips, no key), `voice-build --only
  teacher` plans 1,111 texts, 25,362 characters: the course 797 (12,556), her villager lines 33 (1,115), the scenes'
  row 281 (11,691: scene dialogs 89 / 3,763, projects' steps and parties 162 / 6,235, her arrival 30 / 1,693). The
  explanations apart: 109 (28,743 characters, about 14,400 credits with `eleven_v4_turbo`). On Jan's store the clips
  there are counted as cached, and its index makes a few more spans Slovene.

### The live path

- **`say()`'s stand-in for the teacher, `other()`, stays as it is**: the narrator's clip of the same words (Nina's, else
  the local one), then the other narrator's. Once her clips exist it is never reached for the course: `best(norm,
  'teacher')` answers first. Making a fresh Gepard clip in her key when the node can't make hers now would contradict the
  README's rule that a villager line whose narrator clip exists waits for ElevenLabs (no local clip in a character's key:
  "a local clip in a character key would be no better"), and Jan is replacing the local character clips (below). Where
  no narrator clip exists, `voice()` already makes her local clip (Gepard `ana`) as before.
- **A villager's local clips** (`VoiceStore.wantsElevenLabsVoice`, `localUpgradeQueue`, `upgradeLocal`): a character's
  Gepard clip (never a narrator's, nor Drama 3's, nor an explanation) is made in the speaker's ElevenLabs voice when the
  app asks for it. A download of its file (the day's clips, got ready a day ahead) is served as it is and queued for the
  lazy re-voice; `/voice/say` (within the live lines' rate limit) and `/voice/prepare` make it at once when they can and
  hand over the new clip, the local one only when ElevenLabs can't. The cap is the lazy re-voice's
  (`LANI_VOICE_REVOICE_DAILY`, 40 a day by default, shared with the carrier words, the narrators' Slovene voices and the
  stale clips), never into the reserve. The new clip has its filter, edges and level, a new file name, the hits kept;
  the local file goes; the app is told (`voice_updated`). `voice_status` shows `local_clips`.

## How to run it for Jan

Dry runs first; the commands use the default learner's data and voice cache (lani.env), so `--data` is not needed for
Jan.

```bash
companion/bin/voice-build --only teacher                  # her texts: cached, missing, what --run would make in ElevenLabs
companion/bin/voice-build --only teacher --run            # make them in her voice, within the budget (never the reserve)
companion/bin/voice-build --only teacher --explanations   # with the English explanations too (dry run): ~29,000 characters
```

What doesn't fit the budget goes to the local worker in her voice only where no narrator clip says the same words, else
it waits for the next period. A bridge restart picks up the lazy upgrade of the villagers' local clips; the app is told of
the new clips within a minute.

## Tests

- Android: `IntroSpeechParityTest` (the shared fixture), `IntroSpeechTest` (the existing cases, a question in the base
  with a gap).
- Bridge smoke `course-speech`: the fixture read by the port, `Clips.has`/`Voice.has`, the school's person and her voices,
  the course and the arrivals in the corpus, voice-build `--only`/`--explanations`/`--base` (the plan, the refusals, a
  run within a 60-character budget), a character's Gepard clip asked for (`/voice/say`, a download, the day's cap, the
  reserve), and on the bridge `/voice/prepare` and a download.

## Left

- The corpus voices a villager's scene and project lines in their speaker, not in their own voice once it is ready
  (`@micka`); the app asks for the own voice first and gets the speaker's clip as its archetype's stand-in. Stories and
  the course use the own voice.
- COURSE.md says a homework's introduction is read the same way; the app reads only a unit's introduction
  (`IntroScreen`), so the corpus voices only those.
- An answer's sentence and a quick question whose prompt has Markdown keep the marks in the text the app asks for
  ("**Micka** je doma."): voice-build makes those clips from the text without the marks (the same key), but a line the
  app asks for live is sent as it is.
