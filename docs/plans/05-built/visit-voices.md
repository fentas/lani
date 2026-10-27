# Voices on a visit (built 2026-10-26)

Jan: "he talks to [a villager] right now but no voice/audio here". Teo, a child who learns Italian from German on a
Samsung Galaxy Tab S6 (Android 12), visited Jan's Slovene village and talked with its people: silence.

Why it was so:

- The app plays a villager's line from the node's clips (`data/Clips.kt`). On a visit the app dropped the node's clips
  (`speaker.clips = null`): they are the learner's own village's, in the learner's own language.
- Then the phone's offline voice (Piper) speaks, but only the language of a voice that is on the phone. Teo's offline
  voice, if any, is Italian; nothing offered the Slovene one.
- Then Android's text-to-speech. A Samsung tablet has no Slovene voice, so nothing spoke, and nothing said so.

The same happens to Jan's phone in Teo's Italian town when its text-to-speech lacks Italian, and to any learner whose
device lacks the language of the town they visit.

Jan also: "he could also not tap stars / moon in my town". The visited town's map drew the night sky, but nothing in it
could be tapped (`VisitScreen` passed no `onSky`). This round makes a visit's "look and learn" taps the home village's
too (below, "Look and learn on a visit").

## What

A visit sounds like the town. Each of its lines (a villager's greeting, a scene's dialog, a talk's reply, the learner's
own line) is heard in the first of these that can say it, each a fallback for the one before:

1. **The host town's own clips.** The host has voiced its villagers' lines in their cast voices (its voice store). The
   visitor's bridge asks the host for the line's clip by its text and voice, as the host's voice index keys them, keeps
   it, and passes it on to its app.
2. **The visitor's node voices it** in the host's cast voice when the host has no clip: the visitor's ElevenLabs in the
   voice the host's cast speaks the town's language with (a live line, as a new line at home), then the local Gepard
   worker for a Slovene town. Kept in the visitor's store under the town.
3. **The phone's offline voice of the town's language** (Piper: Slovene, Italian, German, English). Allowed on a visit
   too. When it isn't on the phone, the visit's first line that nothing could say offers it once: "🗣️ Prenesi slovenski
   glas za obiske (61 MB)? · Download the Slovene voice for visits?" (in the visit's labels: the town's language, then the
   visitor's base).
4. **Android's text-to-speech** when it has the language.
5. **Otherwise, never silent without saying so:** "🔇 Ta naprava nima glasu za slovenščino · No Slovene voice on this
   device", with "⬇️ Prenesi (61 MB)" while the node has the voice, once a visit. The line's text stays on screen.

The learner's own lines (a choice tapped, a turn typed right; not one said into the 🎤) follow the town the same way, in
the voice the host's cast gives the visitor's gender.

## How

### Between the towns (the host's side)

`companion/bridge/src/visit-voices.ts`, `src/features/visit-voices.ts` (a feature of its own, after `questions`):

- `GET /town/voice?v=1&text=…&voice=…[&voice=…][&gender=female|male]`, signed both ways, linked towns only (`401` with
  the towns' codes otherwise). Up to four voices in the order to try (a speaker of the cast, someone's own `@id`,
  `player` with the visitor's `gender`). The answer is the clip of the first that the host's store has, the one its own
  app gets (`VoiceStore.best`: a Slovene narrator's in Nina's or Luka's voice, else its local one): `{v, town, language,
  voice, engine, bytes, audio}`, the MP3 in base64 inside the signed JSON, so the towns' signatures, `townRequest` and
  the relay carry it as they carry every answer. `404 no_clip` when it has none.
- **Only clips that are there.** The route never asks an engine, never queues a re-voice and never counts a hit
  (`hostClip`: `best` and `path` only). A file is only one the store's row names (`<sha1>.mp3`), read from the store's
  `files/` and handed over when it is an MP3 of at most 768 KB. A text of 1 to 300 characters without control
  characters; a voice that is a path is a `400`.
- **Limits.** 900 asks an hour from one town (`LANI_TOWN_VOICE_PER_HOUR`), `429` with `retry-after` beyond.
- `GET /town/voice/cast`: the cast a visit is voiced in (`castForVisits`): each speaker with its gender, the ElevenLabs
  voice it speaks the town's language with (`languageVoice`: the narrators' Slovene Nina and Luka), whether that voice
  says a lone word in a carrier sentence, its local Gepard voices (a Slovene town only), its filter (`denoise`) and its
  pace (`tempo`); the voice the cast says a learner's lines in by gender (`player`); how its people are heard (the voice
  profiles: `speaker`, `archetype`, `pitch`, `rate`). Library voice ids only: no key, no account, no designed voice's id.

### The visitor's bridge

- `POST /towns/:id/voice/say {text, voice}`: a kept clip in the voice asked, at once; else the town's cast
  (`castOf`: kept for 10 minutes, the last one for a day while the town is away; a town older than this has its culture
  pack's cast as this node's copy of the pack has it: `castFromPack`) and the voices to try (`visitVoices`: the learner's
  own in the town's voice for their gender alone; someone's own voice, then their archetype's; then the narrators); the
  host's clip (`fromHost`, checked by `cleanHostClip`: that town, a voice asked, an MP3 within the size); else voiced here
  (`voiceInTown`); else a voice it falls back to, kept or the host's (not for the learner's own); else `204`. A text with
  another `lang` (a hint's explanation) is a `400 not_a_line`. Two asks of one line at once share one answer.
- `voiceInTown`: ElevenLabs (`ElevenLabs.sayAs`: the voice id and the language given, not the village's; the quota guard,
  the reserve for live lines and the gate at ElevenLabs are the village's own instance's; the live lines' rate window,
  `VoiceStore.liveSlot`, counts a visit's lines too), a lone word of a carrier voice in the language's carrier sentence
  (`carrierIn`), then Gepard (`Gepard.sayAs`, the worker's voice by name) for a Slovene town only. Drama 3 isn't asked: it
  is far too slow for a live line (about 10 s a second of audio) and its references are the visitor's cast's. Each take
  through the host's filter and pace for that voice, its edges and the level (ffmpeg, `ffmpegLeveller`), as the store
  keeps a clip.
- The engines are the node's own (`Voicing.engines`, `Ctx.voiceEngines`: the same ElevenLabs and Gepard instances as
  the village's store), so a village whose cast has no voices (Lakeland) still voices a visit with the node's key. The
  phone's bridge has none: it hands the host's clips on, and nothing else.
- `VisitClips`: `<voice dir>/visits/index.json` and `files/<sha1>.mp3`, per town, voice and line, with the town's
  language, where it came from (`host`, `node`), the engine and its own voice. At most 100 MB (`LANI_VISIT_VOICE_MB`),
  the least recently given out first; a town no longer linked loses its clips when the bridge starts. Written with
  `node:fs` only (no SQLite), so the phone's bridge runs it too. No pass of the village's store (re-voice, level, stale
  clips, `voice-build`) ever sees these clips.
- `GET /towns/:id/voice/index` (the town's kept clips, as `/voice/index` has the village's), `GET
  /towns/:id/voice/profiles` (`{people, player, language}`: the town's people's pitch and pace, the learner's voice
  there) and `GET /towns/voice/file/<file>` (only a file of the index). The lease leaves `/towns/:id/voice/` alone (no
  learner data is written).
- A line the host had no clip of isn't asked again for 10 minutes (`LANI_VISIT_VOICE_MISS_MS`), nor a town whose bridge
  is older than this (its 404 isn't `no_clip`).
- Through the relay as any town's route: `townRequest` asks a town only on the relay there.

### The app

- `data/Clips.kt`: `ClipRoutes` (`OWN`, `visit(town)`), `ClipsApi(config, routes)`, `Clips(…, visit = true)`: the
  town's index in memory for the visit, its files in the clip cache (within the cap, under their own names), the learner's
  own index, earlier clips (`PreviousClips`) and re-records untouched; `enabled` once the node can be asked, so a 🔊 and a
  villager's greeting speak before any clip of the town is known; a long press re-records nothing (`ReRecord.Offline`).
- `AppViewModel.visitTown`: the speaker's clips are the town's (`ClipRoutes.visit(t.id)`), with the learner's gender;
  `endVisit` gives the own ones back. The player's voice on a visit is the one `/towns/:id/voice/profiles` says.
- `data/Speaker.kt`: `onSilent`, told when nothing could say a line (no clip, no offline voice of the language, no
  Android voice). `qaNoTts` (QA's "audio:tts:off"): Android's voice counts as missing, as on Teo's tablet.
- `app/VisitVoiceController.kt`: `VisitVoicePolicy.prompt` (the offer; the note with the download after a "Ne zdaj";
  the note alone without a node; nothing while the voice is on the phone or on its way), once a visit. "Ne zdaj · Not
  now" is remembered per language (`AudioSettings.declineVisitVoice`).
- `app/OfflineAudioController.kt`: `offerFor(language)`, `downloadFor(language)` (its own WorkManager work,
  `OfflineVoiceWorker.nameFor`, so it never waits for, or keeps, the own voice's), `visitProgress`, `visitProblem`,
  `visitVoices` and `removeFor(language)`. Piper speaks a visited town's language once its voice is on the phone (it
  speaks the language the labels show, `L10n.pair.target`).
- `ui/towns/VisitVoiceNote.kt`: the offer as a dialog, the note as a small card under the visit's banner (a polite live
  region for TalkBack), the download's progress, "✅ Slovenski glas je na telefonu" for a moment once it came.
- `ui/home/OfflineAudioSettings.kt`: "🧳 Glasovi za obiske · Voices for visits", each with its size and "🗑️ Odstrani".
- **A town's word at home** (found by the progress-sharing round): a word card's 🔊 (`ui/words/WordSheet.kt`) said the
  word with the home village's clips and voice even when the card was another language's (a word of a town visited,
  reviewed at home: `WordQuery.lang`). `ui/Components.kt` `WordSpeakButton` and `Speaker.sayIn(text, lang)`: in the
  labels' language as before; another language's through the same chain: a linked town of that language's clips through
  the node (`Speaker.foreignClips`: `Clips` at its visit routes, kept by town while the app runs; `AppViewModel`), else
  the offline voice of that language (`OfflineVoice.speaksIn`, `renderIn`: Piper speaks any of its voices on the phone),
  else Android's voice in that language (set for the request, then back to the labels'), else
  `VisitVoiceController.silentIn(lang)`: the offer or the note named in that language ("targetLang"), once for each
  language while the app runs. No re-record for it. A review of another language at home still says its exercises
  with the home voices (below, "What's left").

## Look and learn on a visit

What only looks or teaches works on a visit as in the learner's own village, in the town's language explained in the
visitor's base; what would change the host stays replaced by "🧳 Tukaj si gost · You're a guest here".
`ui/towns/VisitTaps.kt` routes a visit's taps (pure, `VisitTapsTest`).

| Tap | At home (VillageScreen) | On a visit before | On a visit now |
|---|---|---|---|
| The night sky: the moon, a star, a constellation, a planet, the Milky Way, a shooting star | its card (lines, words to tap, 🔊, "📚 Learn them", "📖" the almanac) | nothing: drawn, not tappable | **its card**, the town's culture's sky, its lines and words to tap, 🔊; not "📚" nor "📖" (their runs and answers are kept in the visitor's own village) |
| TalkBack: "🌌 Nebo nocoj · Tonight's sky", "🌙 Luna" | the list, the moon's card | not there | **there** |
| The night sky over an outdoor place of a scene | its card | nothing | **its card** (not during a dialog) |
| A wild animal at the forest's edge (and its TalkBack node) | its word's card (🔊, "Add to my words") | nothing: a tap on where it stands | **its word's card**, the word in the town's language, added to the visitor's words in it |
| A spot its culture tells about (the charcoal pile) | its card with its history at the learner's level, its words, "📚 Learn the words" | its card, without the story | **its history and its words to look up**; not "📚" |
| A building | its card; "🔨" the building's card (upgrade, repair, move) | its card as a guest: level, damaged, its scenes | unchanged |
| The fire | its card; "🪵 Feed the fire", the hearths' sheet | its card: "🔥 a warm fire", its scenes | unchanged |
| The forest | its card; "🎧 Gather wood" | its card | unchanged |
| A spot of the landscape (the woodpile, the rocks, the pond …) | its card: what it is for, its practice, its scenes | its card: what it is, its scenes | unchanged |
| The horizon (the hills, the sea) | its card when a scene opens there, else nothing | the same | unchanged |
| A landmark (the vineyard's terraces) | its card into its scene, else the project's sheet (its steps) | its card | unchanged (a project's step changes the host) |
| Someone of the village | their card: bond, gifts, requests, talk | their card as a guest: who, their greeting, "💬 Talk" | unchanged |
| A bubble (a happening, a request, an event, a festival, a party, an arrival, the treasure map) | opens it | no bubbles: the town's requests are in "📜 Prošnje", its dialogs for guests in its places | unchanged |
| The pedlar, the pilgrim, the charcoal burner by his pile | the chest's stall, the pilgrim's way, his talk | not in a visited town (its keepers' cycles are the host's) | unchanged |
| The plot chooser (build, move) | chooses a plot | never | unchanged |
| Double tap, pinch, the camera | zoom, pan | the same | unchanged |
| A thing in a place (a scene) | its word, 🔊, found | the same | unchanged |
| Someone in a place | greets, their dialog, a lesson, I spy | greets, their dialog (the ones for guests among them) | unchanged (I spy's rounds are kept in the own village) |
| A sleeper in a place | a sleepy mumble | nothing | unchanged: left for later (who sleeps where is the host's day) |

## How to use it

Nothing to set: a visit asks the host first, then voices on the node, then the phone. On the node:

- `LANI_TOWN_VOICE_PER_HOUR` (900): the clips a linked town may ask an hour of this town.
- `LANI_VISIT_VOICE_MB` (100): what this node keeps of visited towns' clips.
- `LANI_VISIT_VOICE_MISS_MS` (600000): how long a line the host had no clip of isn't asked again.

On the phone: the settings' "🔊 Zvok brez povezave · Offline audio" lists the voices got for visits ("🧳 Glasovi za
obiske"), each with "🗑️ Odstrani".

QA's hooks (a debug build): "audio:tts:off" (Android's voice as missing) and "audio:tts:", "audio:visitvoice:reset" (the
offers declined forgotten, the note to come again), "audio:voice:remove:<lang>" (an offline voice off the phone).

## Tests

- **The bridge's smoke** (`test/smoke.ts`, 2,721 checks after the rebase onto main, all passing): `test/smoke/visit-voices.ts`, 31 checks in the
  towns section, with its three bridges (Jan's Slovene town, Mia's Italian one with a fake ElevenLabs and a fake local
  worker of her own, Luka's child's town at two clips an hour):
  - the host's side: the host's clip handed over signed, byte for byte, in the first voice asked that it has (someone
    without a voice of their own: their archetype's); a line it has no clip of: `no_clip`, and neither its ElevenLabs
    nor its local worker asked; an unlinked town, an unsigned request, the app token, a replayed request: `401` (the
    cast too); a voice that is a path, a line over 300 characters, five voices, none, a wrong gender, a control
    character: `400`; a tampered store (a row naming `../../towns.json`, a file that isn't an MP3, one of 900 KB):
    `no_clip`, nothing read; the cast (Nina and Luka for the narrators in Slovene, Babica's Zlata, `nina`, `ana` for
    words, the girl's pace), nothing private in it; 429 with `retry-after` over the limit;
  - the visitor's side: Mia's app gets Babica's line, the host's clip kept on her node byte for byte, in the town's
    index; a line the host has none of voiced on her node in Babica's Zlata with `language_code: sl` (a live line), kept
    apart from her village's clips; with her ElevenLabs down, her local worker in `nina`, a lone word in `ana`; the
    learner's own line in the town's voice for her gender, the host's clip; none to be had: `204`, no other voice for it;
    the app's routes' refusals (an explanation with another `lang` among them);
  - an Italian town's line voiced on a node (in-process): ElevenLabs in Angelina's voice with `language_code: it`, never
    the local worker; ElevenLabs down: nothing; over the live lines' rate: nothing asked; a carrier voice's lone word in
    the town language's carrier sentence;
  - the cleaning of another town's clip and cast, a town older than this (its culture pack's cast: Kärnten's carrier
    narrator, Primorska's default cast), the voices tried, the store's size and a town no longer linked.
- **The relay's smoke** (`test/relay.ts`, 43 checks, all passing; `companion/relay`'s `bun test`: 19): Marta's hosted town visits Ana's, reached only
  through the relay: Ana's clip byte for byte, her town's cast, `204` for a line she has none of.
- `bunx tsc --noEmit -p .`: clean. The phone's bundle builds (`phone/build.ts`: 1,035.8 KB minified).
- **The app's unit tests**: 3,068 after the rebase onto main, all passing (`PhoneBridgeTest` counts the bridge's 36
  features). New: `VisitVoicePolicyTest` (4), `VisitClipsTest` (4), `VisitSpeakerTest` (3, Robolectric: a visit's clips,
  a silent line told, a town's word at home said in its own language), `VisitTapsTest` (6); the string tables' tests
  (`L10nTablesTest`, `L10nParityTest`) take the new labels.
- **Python**: `tests` 251 and `companion/tests` 98 (10 skipped), all passing.
- **On the emulator** (`companion/bin/qa --steps visit-voices`, the x86_64 emulator, Android's text-to-speech as missing:
  QA's "audio:tts:off"). The phone's own node is an Italian learner's from German, of the Friuli village, as Teo's (a
  synthetic learner, the dev bridge with a fake ElevenLabs); the town visited is a synthetic Slovene village of the
  Primorska culture pack (`test/fixtures/dev-town-sl.json`, its own clips of its people's 22 greetings made by a fake
  ElevenLabs as it starts). The labels during the visit read "Slovene · German" ("Na obisku · Zu Besuch"):
  - (a) Babica Micka's greeting the town has a clip of: `said «dober dan si lačen»: node clip (grandma)` (the app),
    `visit voices: Vas za Preizkus: "Dober dan. Si lačen?" (grandma): the town's own clip` (the visitor's bridge);
  - (b) a line the town has none of: `fake ElevenLabs: V5O4WAcdw7w3Ccfvma8Z sl 25 chars "Danes pečem kruh. Vonjaš?"`
    (Babica's Zlata, in Slovene), `… voiced here (elevenlabs)`, and the app played it as the node's clip;
  - (c) the node's ElevenLabs down (503) and no offline voice: `said «krompir je treba olupiti pet kil»: nothing (no tts
    voice)`, `VisitVoice: … OFFER`: the dialog "🗣️ Prenesi slovenski glas za obiske (61 MB)? · Die slowenische Stimme für
    Besuche herunterladen (61 MB)?"; "Ne zdaj"; the next visit's silent line: `… NOTICE_DOWNLOAD`, the card "🔇 Ta naprava
    nima glasu za slovenščino · Keine slowenische Stimme auf diesem Gerät" with "⬇️ Prenesi (61 MB) · Herunterladen (61
    MB)";
  - (d) "⬇️": the voice on the phone after 8 s (62.5 MB), "✅ Slovenski glas je na telefonu" shown; the next line said by
    the offline voice: `said «burja piha zapri vrata»: offline voice`, `Piper sl_SI-artur-medium loaded in 888 ms`, `Piper
    said 24 characters (1.9 s of speech) in 153 ms`.
  - out of the visit by Back: Friends reads "🤝 Amici · Freunde" again, the learner's own pair.
- **The night sky on a visit** (`companion/bin/qa --steps visit-sky`, the town's map at night): a tap on the moon opens
  its card in the town's language explained in the visitor's base ("Luna · Der Mond", "Ščip – polna luna · Vollmond",
  its lore and "Besede neba · Die Wörter des Himmels" to tap), without "📚 Learn them".
- One run with all the steps passing (`--steps visit-voices,visit-voices-silent,visit-sky`: setup, the second town, the
  three steps, the crash check); screenshots and logs in `companion/qa-shots/<run>/` (`visit-voices.log`).

## What's left

- **A node-made clip stays** when the host later makes its own of the line: the visitor keeps the first it got. A
  re-check of the host now and then (a node-made clip older than a week) would bring the host's take.
- **A host's re-voiced line** (a new take) isn't fetched again: the visitor plays the take it kept.
- **The day's audio and the car** don't plan a visit's lines (no prefetch on a visit); a visit's first lines wait for the
  host (up to 4 s) or the node.
- **Someone's own voice** (designed in the host's ElevenLabs account) can't be made on the visitor's node: their
  archetype's voice says what the host has no clip of.
- **A review of another language at home** (a town's words reviewed in their language) says its exercises' lines
  through the home village's speaker: only the word card's 🔊 follows the word's language so far.
- **The hint's 🔊** (a dialog's TL;DR) speaks its town-language pieces from the town's clips, but its base-language
  pieces with Android's voice only (no offline voice for them), as at home.
- **Machine-written labels**: the Slovene of the new labels beyond the two the brief gave ("Prenesi slovenski glas za
  obiske", "Ta naprava nima glasu za slovenščino"), and all of the German and Italian, are machine-written: for a native
  check (sl.json's `_note_visit_voices`).
