# My voice: the learner's own lines in a voice they choose (built 2026-10-29)

Jan: "dialogs also say now own lines. but for teo this should be maybe a boy voice -> maybe we offer (in general) like 2,3
voices to choose from?" And for the choices: "make it the best - prod ready".

Since 2026-10-25 the learner's own dialog lines are said aloud ([player-voice.md](player-voice.md)), in the cast's
`player`: one voice per gender. Two things were wrong with that for Teo, a boy of about 7 who learns Italian from German
in the Friuli village on a 2560 × 1600 tablet:

- **An adult man's voice for a child.** `cultures/friuli/voice-cast.json` had no `player`, so the default gave the male
  narrator, Chris Basetta.
- **The same voice as the person he talks to.** Chris Basetta is also the friuli `young-man`, Pastore Davide's voice: in a
  dialog with Davide, Teo's line and Davide's reply sounded like one person. Kärnten had the same gap (no `player`: its
  male narrator Daniel, nobody else's there, but an English voice), Lakeland too (Daniel again).

## What

"🗣️ Moj glas · My voice": every learner hears their own lines in a voice that fits them (a child's for a child),
different from everyone they talk to, chosen from 2–3 voices, or none.

- **The voices to choose from** are each culture cast's `players`: for each gender, a grown-up's 2–3 and a child's 2–3,
  the default first (below, "The shortlist"). Each is a speaker of the cast with an ElevenLabs library id, a gender, an
  `age` (adult, child), a name to show and, in the Slovene cast, its local Gepard voice. The narrators' Slovene voices are
  Primorska's grown-up defaults (`male.sl`, Luka; `female.sl`, Nina). The cast's `player` stays the grown-ups' default per
  gender; a cast without `players` falls back to it as before.
- **Never a voice of the cast's.** No voice offered may be one that anyone the learner talks to speaks with: every speaker
  of the cast but the narrators and the learner's voices, in every language it has (`castClash`, `checkPlayers` in
  `bridge/src/cast.ts`). The bridge leaves such an entry out and logs it; the smoke test checks the four casts shipped
  (none, and 2–3 in each place). A cast without voices to choose whose `player` is a villager's voice too (friuli's male
  narrator) says the learner's lines in nothing: the phone speaks them. No villager may speak with a learner's voice.
- **The default**: the first voice of the learner's gender (the profile's `learner.gender`) and age (the profile registry's
  child flag, `LANI_CHILD`, which `lani-profile` passes to the learner's bridge: Teo's runs with `LANI_CHILD=1`; the app has
  it from pairing). Jan keeps Luka. A profile without a gender is offered both genders' voices of their age, the female
  one's the default, as before.
- **The choice** is per learner, on the bridge: `learner-profile.json` `preferences.my_voice` (`{"voice": "player-boy-b" |
  "male.sl" | "off" | "default", "since": "2026-10-29"}`). The app asks for the learner's lines as `player`, as before; the
  bridge says them in the voice chosen. voice-build voices them in it too, a visit takes it along.
- **"🔇 Brez glasu · No voice"** says no own lines: none is said, their line has no 🔊; the choices' 🔊 before the pick
  stays (in the default voice, as before).
- **The picker**: in "⚙️ Nastavitve › 🔊 Zvok in glas · Sound and voice", first on the page; and offered once at the first
  dialog line of the learner's (a sheet over the dialog, never a wall). Each voice is a row: its face (👦 👧 👨 👩), its name,
  "privzeti · default" under the first, and a ▶ that says its sample ("Ciao! Sono Teo.", "Živjo! Jaz sem Jan."), made by
  the bridge in that voice the first time it is tapped (the normal clip path: a live line of a few characters, the
  reserve's rules), then kept. A tap on a row chooses it; the next line comes in it. Nothing is voiced again in bulk: the
  old clips stay in the store.
- **When ElevenLabs can't** (the quota at its reserve, no key, an error): a Slovene voice's own Gepard voice; else the
  phone's offline voice or TTS in the voice's gender, a child's higher. Never a cast member's voice, never another
  person's clip.
- **A visit** uses the visitor's choice as its kind: the town's voice of the same gender and age at the same place on its
  list, in the town's language.

## How

### The cast files

`companion/voice-cast.json` (Primorska) and `companion/cultures/{friuli,kaernten,lakeland}/voice-cast.json`:

- New speakers `player-man-a`, `player-woman-a`, `player-boy-a`, `player-girl-a`, … with `"age": "adult" | "child"`. The
  letter is the place on the list after the default; a voice swapped later keeps its speaker (the learner's choice stays
  valid) and takes a `since`, so its clips are made again as the app asks for them, as for any speaker.
- `players`: `{"male": {"adult": ["male.sl", "player-man-a", "player-man-b"], "child": [...]}, "female": {...}}`.
- `player` written in every cast as the grown-ups' default (Primorska's unchanged: Luka, Nina).
- The comments say all of this where it applies; the voices' audition notes are in each speaker's `role`.

### The bridge

- `src/cast.ts`: `Age`, the speaker's `age`, `Players`, `MAX_OPTIONS` (3), `checkPlayers` (each entry checked: a speaker
  or a speaker's voice in a language, its gender and age, one place only, no ElevenLabs voice twice, `castClash`),
  `castClash` (who of the cast speaks with that ElevenLabs voice: everyone but the narrators and the learner's voices),
  `optionsOf(gender, age)` (the cast's `player` written for a grown-up first, then `players`; a child without voices of
  their own gets a grown-up's), `playerOptions()`, `playerOption(gender, age, pick)`, `playerVoice(gender, age, pick)`
  (now undefined when nothing may say the lines), `isLearnerVoice(name)`; `speakerFor` never gives a villager a learner's
  voice; `castInfo` shows the age.
- `src/my-voice.ts` (new; node:fs and node:path only, so the phone's bridge runs it too): `myVoice({gender, child,
  choice})` (the options offered, the one in effect, off, chosen), `choiceOf(dataDir)` (read again when the profile
  changes), `writeChoice` (the profile read just before, only `preferences.my_voice` changed, written beside it and
  renamed over it), `mineVoice` (for voice-build and the status), `sampleLine(language, addressee)`.
- `src/features/voice.ts`: the voice `player` resolves to the learner's voice (their choice, gender, child flag);
  `/voice/say` of a learner's voice (asked as `player` or by its name: a sample) is said alone, a word to three in its
  carrier sentence; `GET /voice/profiles` adds `mine` (`voice`, `id`, `name`, `gender`, `age`, `off`, `chosen`) and says
  `player: null` when nothing may say the lines; new routes `GET /learner/voice`, `POST /learner/voice {voice}` (not under
  `/voice/`, so the lease guards it: it writes the learner's data) and `POST /voice/sample {voice}`; `voice_status`
  shows `my_voice`; the tutor's voices guide says where the choice is kept and that it keeps the key.
- `src/voice.ts`: a learner's voice never waits for the narrator's clip (`waitsForElevenLabs`), so its Gepard voice speaks
  when ElevenLabs can't; the corpus's `mine` voices the dialogs' choices in the learner's voice too (`my lines`, after the
  scenes and I spy; priority labels gained one); the status shows the cast's `players`.
- `src/visit-voices.ts`, `src/features/visit-voices.ts`: `VisitCast.players`, `visitPlayer` (the town's voice of the
  visitor's kind at their place, else its first; a town older than this: its `player` unless a villager of it speaks
  with that voice too), `GET /town/voice` takes `age` and `pick` beside `gender`, `castFromPack` reads `players`.
- `src/learners.ts`: `childOf(dataDir, projectDir)` for voice-build (`LANI_CHILD`, else the registry entry's flag).
- `bin/voice-build`: the corpus with the learner's voice.
- `src/villagers.ts`: a villager's `speaker` may not be a learner's voice.

### The app

- `data/MyVoice.kt` (new): `MyVoiceOption`, `MyVoiceState`, `MineVoice`, `MyVoices` (parsing, `offerDue`), `PlayerTts`
  (the phone's voice for a learner's line: a voice of their gender where the engine names one, Samsung's `SMTm00`/`SMTf00`,
  Google's `#male_1`, a `male`/`female` feature; a man's line a little lower in a voice not known to be a man's, ×0.85; a
  child's at ×1.3), `MyVoiceApi`.
- `data/Clips.kt`: `mine` from `/voice/profiles`, `mineSaid` (a choice just made: the next line in it), `NONE` when the node
  has no voice for the learner's lines (none of the phone's clips stands in).
- `data/Speaker.kt`: `mine`, `onOwnLine`, the learner's line without a clip in the phone's voice styled as theirs,
  `sample(text, like, fetch)`.
- `app/MyVoiceController.kt` (new): the state, `load`, `choose`, `sample`, `offerAtLine` (once a run, at home, while not
  chosen), `closeOffer` (a tap beside the sheet or "✓ V redu" keeps the default as chosen), QA's `qaOfferAgain`.
- `ui/settings/MyVoiceSettings.kt` (new): `MyVoiceSettings` (the settings' block) and `MyVoiceOffer` (the sheet), both
  over `MyVoiceChoices`: the rows (64 dp, the whole row a radio button for TalkBack, its ▶ labelled "Poslušaj: Teddy ·
  Listen: Teddy"), "🔇 Brez glasu", and what to say while loading, offline or from an older node.
- `ui/scene/DialogVoice.kt`: off says none of the learner's lines (the reply comes as before they had a voice); the
  first line of theirs tells the app (the offer). `ui/scene/SceneDialog.kt`: no 🔊 on their line while off (one line).
- `MainActivity.kt`: the offer's sheet over any dialog; `ui/settings/SettingsScreen.kt`: the block first on the Sound page.
- Labels (`myVoice.*`, the changed `settings.soundAbout`) in sl, en, de and it, written by Claude: for a native speaker's
  check (each table's `_note_my_voice`).

## The shortlist

How: free calls only. `GET /v1/shared-voices` (by language, gender, age, accent, use case and search words: child, kid,
boy, girl, little, years old, Austrian, northern …; about 2,800 voices seen), and the voices' free `preview_url` samples
(the per-language preview in `verified_languages` where there is one: the voice speaking the slot's language). The key
was read from `~/.config/lani/keys.env` into the process only. No text-to-speech, no voice added, no credits spent.
Each serious candidate's preview was measured: its pitch (F0 median and 10–90 % range over voiced frames), its
loudness (EBU R128) and the level of its pauses (the quietest 10 % of 20 ms windows; the cast's own hissing voices are
at −38 to −55 dB). The local Whisper (CPU, `large-v3-turbo`, at nice 10) heard all 82 previews: every voice offered
speaks the slot's language at 0.97–1.00 (Croatian 0.97–0.997; Italian, German, English 0.997–1.00); Milly Maple's
"German" preview was English (1.00), so she is out. The audition's data stays outside the repository.
Then ranked: a child's voice that is a child's (F0 about 250–400 Hz, a library kid voice, not an adult performing or a
teenager), the language's native accent (else a voice ElevenLabs verified in it), clear and calm for a learner, clean
audio, `free_users_allowed`, `rate` 1, a long notice period (730 days for all but Nina's owner's 4,015).

What the library has (October 29, 2026):

- **No Slovenian voice at all** (Nina and Luka were pulled on October 21 and stay usable on the account until about
  October 21, 2027): the other Slovene options are Croatian natives, as the villagers' voices are South Slavic.
- **No native child voice in Italian, Slovene or Croatian, one in German** (Bibi Blume, a neutral cartoon voice at
  F0 401–575 Hz: too much of a caricature; Milly Maple, a cartoon squirrel, speaks English in her "German" preview). The child options are the library's kid voices that ElevenLabs verified in
  the language (Croatian for Slovene). The only Italian-native "kid", Oceano, is 14 with a broken voice (F0 127 Hz).
- **No British boy.** Lakeland's boys are American and Indian-English kid voices.

The voices offered, the default first (F0: the preview's median; "filtered": its pauses above −58 dB, so the `hiss`
filter is on):

**Primorska (Slovene)**

| | Speaker | Voice | ElevenLabs id | F0 | Why |
|---|---|---|---|---|---|
| Man | `male.sl` | Luka | `SmCUS1ekZnVjC5lqnRQG` | | the narrator's native Slovene voice: Jan keeps it (the brief); leaves the library about 2027-10-21 |
|  | `player-man-a` | Tomislav | `O9XgmQnaZveORWl6mTHm` | 106 | Croatian native (no Slovene one left), calm and clear |
|  | `player-man-b` | Marko | `peXmQaCErbfrWCM5FqjH` | 114 | Croatian native, a young warm baritone, natural, the cleanest of the Croatian men |
| Woman | `female.sl` | Nina | `0IwLFuWc6siAnpd77hRG` | | the narrator's native Slovene voice: Jan keeps it (the brief); leaves the library about 2027-10-21 |
|  | `player-woman-a` | Ana | `rmUck1qkzTjuEjdC6Cav` | 209 | Croatian native from Nina's and Luka's owner, gentle and friendly |
|  | `player-woman-b` | Elena | `iahPPG34ls0Mm3W0DBhh` | 190 | Croatian native from Nina's and Luka's owner, confident and articulate |
| Boy | `player-boy-a` | Valf | `loY1uopAz31XyhAEhNSa` | 318 | a real kid's voice, playful; verified in Croatian (none in Slovene); pauses near −56 dB, so filtered |
|  | `player-boy-b` | Pintu | `oT6YGCPblY04TqTskA2d` | 368 | a young child's, playful, very clean; verified in Croatian (none in Slovene) |
| Girl | `player-girl-a` | Mithi | `MIl0Flub6Sc9KSkX2A42` | 272 | an actual young girl's, sincere and curious; verified in Croatian (none in Slovene) |
|  | `player-girl-b` | Munni | `VOGEEZj2Kly5dP9LrQy8` | 394 | a lively real kid's, very clean; verified in Croatian (none in Slovene) |
|  | `player-girl-c` | Cherry | `XJ2fW4ybq7HouelYYGcL` | 375 | bright and sweet, the library's most used kid voice, clean |

**Friuli (Italian)**

| | Speaker | Voice | ElevenLabs id | F0 | Why |
|---|---|---|---|---|---|
| Man | `player-man-a` | Ricasco | `G1QO6RfZl0zS1DpKDReq` | 140 | Italian native, clear, warm and well-paced, made for teaching |
|  | `player-man-b` | Vittorio | `nH7uLS5UdEnvKEOAXtlQ` | 109 | Italian native, a friendly northern (Milanese) colour; pauses near −56 dB, so filtered |
|  | `player-man-c` | Matteo | `MTgv1KRJpUnc34UMGTHK` | 149 | Italian native, a soft Veneto accent (the nearest to Friuli), warm; hisses (−48 dB), so filtered |
| Woman | `player-woman-a` | Rossana | `MuTiG4dbrEGYEy3XP4iP` | 175 | Italian native, clear standard Italian at an easy pace, made for conversation, very clean |
|  | `player-woman-b` | Nora | `zFA34HbdHBvF8WhlSusK` | 233 | Italian native, bright and easy to understand, very clean |
|  | `player-woman-c` | Sara | `uV2Bhcm1HwmAqPqkbjfl` | 209 | Italian native, warm, clear, mid-range; pauses near −58 dB, so filtered |
| Boy | `player-boy-a` | Bhola | `d7G4zsIoYxHBAwkKbqs5` | 309 | soft, innocent, cheerful young boy, verified in Italian, very clean |
|  | `player-boy-b` | Piku | `EeQEodFZVtBkjtgK3HBc` | 299 | an expressive real kid ('his' delivery; the library says female; Zala's in Primorska); verified in Italian |
|  | `player-boy-c` | Omar | `srEfhy4AF67Mw9SpTVFd` | 359 | a bright, lively young boy, verified in Italian and German, very clean |
| Girl | `player-girl-a` | Gudiya | `e3FEEanj39p8QCSMe8iV` | 321 | a curious child's, sweet and natural, verified in Italian, very clean |
|  | `player-girl-b` | Chutki | `Jr72SE8p9OcJmr8hyX0D` | 277 | a cheerful kid, verified in Italian, very clean (giggly at times) |
|  | `player-girl-c` | Cherry | `XJ2fW4ybq7HouelYYGcL` | 378 | bright and sweet, the library's most used kid voice, verified in Italian, clean |

**Kärnten (German)**

| | Speaker | Voice | ElevenLabs id | F0 | Why |
|---|---|---|---|---|---|
| Man | `player-man-a` | Juri | `06UBgCCFLiCyxbrvrgpN` | 137 | Austrian (verified de-AT), gentle and young, very clean |
|  | `player-man-b` | Peter | `FosrFSJcgFTol7FFvQXU` | 102 | Austrian (Upper Austria, de-AT), calm, clean (a quiet preview: levelled) |
|  | `player-man-c` | Tom | `sTeT0bXBeK6hi9pnfwhh` | 101 | clear, calm, an Austrian-German colour |
| Woman | `player-woman-a` | Anna | `Ah5UjbC5d1A2iCl9Lbe7` | 158 | Austrian (verified de-AT), calm and pleasant, steady; pauses near −56 dB, so filtered |
|  | `player-woman-b` | Johanna | `coPfQIqaxowKv5u2s2bV` | 175 | warm, clear standard German, very clean |
|  | `player-woman-c` | Seda | `mekn71YL8kSYrEunOj4N` | 206 | warm Austrian German (verified de-AT), a storyteller's; pauses near −56 dB, so filtered |
| Boy | `player-boy-a` | Bhola | `d7G4zsIoYxHBAwkKbqs5` | 294 | soft, innocent young boy, verified in German (not native: none is in the library), very clean |
|  | `player-boy-b` | Omar | `srEfhy4AF67Mw9SpTVFd` | 325 | a bright, lively young boy, verified in Italian and German, very clean |
| Girl | `player-girl-a` | Mini | `hO2yZ8lxM3axUxL8OeKX` | 255 | bright and cheerful, verified in German, very clean (may sound a young woman more than a girl) |
|  | `player-girl-b` | Suhana | `9vP6R7VVxNwGIGLnpl17` | 375 | very young and joyful, verified in German (its accent marked "other"), clean |

**Lakeland (English)**

| | Speaker | Voice | ElevenLabs id | F0 | Why |
|---|---|---|---|---|---|
| Man | `player-man-a` | Liam | `wUwsnXivqGrDWuz1Fc89` | 150 | a soft northern British accent, warm and relaxed |
|  | `player-man-b` | Woodstock | `tsZIuoP32cHvWfs5aIPK` | 128 | northern English (East Lancashire, the nearest to the Lakes), conversational |
|  | `player-man-c` | Mark | `0CoxwbC90B8N0Fa3LDCH` | 105 | northern English (Durham), warm and clear; pauses near −57 dB, so filtered |
| Woman | `player-woman-a` | Carrie | `GETflDbIxMMHeQDeVCEW` | 168 | British, a softly assured northern tone, clear and friendly |
|  | `player-woman-b` | Ida | `xbOj8ppmiYz8dqX2I2ri` | 171 | northern English, warm and down-to-earth, clean |
|  | `player-woman-c` | Maria | `RU8huomG1ebFQW6nNko3` | 185 | Manchester, clear and articulate, soft northern vowels, clean |
| Boy | `player-boy-a` | Teddy | `XjGYkUkzth8BPs29fmcV` | 319 | a sweet, cheerful little boy (American: the library has no British boy), very clean |
|  | `player-boy-b` | Bhola | `d7G4zsIoYxHBAwkKbqs5` | 289 | soft, innocent young boy, verified in English (an Indian accent), clean |
|  | `player-boy-c` | Valf | `loY1uopAz31XyhAEhNSa` | 359 | a very young, playful boy (American); pauses near −64 dB |
| Girl | `player-girl-a` | Emmaline | `nDJIICjR9zfJExIFeSCN` | 258 | the library's young British girl, clear and bright (rather 10 than 7) |
|  | `player-girl-b` | Gudiya | `e3FEEanj39p8QCSMe8iV` | 334 | a curious child's, sweet and natural (an Indian accent), very clean |
|  | `player-girl-c` | Cherry | `XJ2fW4ybq7HouelYYGcL` | 452 | bright and sweet, the library's most used kid voice, clean |

Also heard and left out (the notable ones): Karlo (Croatian, `pTD8166KADi6ZFJ5FEXi`: the best young Croatian man but
pauses at −53 dB), two more Croatian "Luka"s (a third Luka beside the narrator's and Pastir Luka), Maja (a 90-day notice),
Krish and Vardan (adult men in kid characters, F0 119–154 Hz), Chintu and Leo (adults performing a child, F0 186–229 Hz),
Gungun (a many-character performer), Lulu Lolipop (a high-pitched caricature), Oceano (a teenager), Mimmi and Kina (Italian
women doing a girl's voice, both hissing), Tamara, Alina, Bettina and Alexandra (Austrian but noisy, pauses −41 to −51
dB), Bibi Blume (the German cartoon voice above), Fruna (neutral gender, quiet), Ben and Emma (a faint northern edge, very
quiet or hissing previews).

**The first ▶ of each voice** makes its sample on the node: about 20 characters, 10 credits with `eleven_v4_turbo`
(about 30 for Jan's three voices, 25 for Teo's), once; the first request with a library voice's id adds it to the account
without a voice slot. The voices offered were not heard by Jan or Teo: each is one line to swap (its `elevenlabs`, `name`,
`since`).

## Decisions, and why

- **The choice is kept on the bridge, in the learner profile** (as the brief asks): it holds on every device of the
  learner's, voice-build reads it, and a visit takes it along. `writeChoice` changes only `preferences.my_voice`, reads the
  file just before and renames a new file over it; the tutor's `update-db.py` does the same with the whole profile, so the
  two could only lose each other's write within the same second. The tutor's guide says to keep the key.
- **"default" is a choice too.** The offer comes while the learner hasn't chosen; a tap beside the sheet or "✓ V redu ·
  OK" keeps the default and is remembered as `"default"`, so the offer never comes again on another device, and the default
  still follows the learner's gender, age and the cast (a better first voice later, a child grown up).
- **Off keeps a voice for strays.** With the lines off, the app asks for none; an app that asks anyway (an older one on
  another device) still gets the default voice, never nothing in a wrong voice.
- **Only the learner's gender and age are offered** (both genders while the profile says none): the content speaks to
  them in their gender ("Si lačen?"), and a choice of voices of the other gender would sound wrong in those lines. A choice
  of a voice not offered (the profile's gender changed) falls back to the default.
- **The narrators may be a learner's voice, the villagers never.** Jan keeps Luka (the brief): the narrators read words and
  exercises, the app's own voice, and nobody the learner talks to in a dialog speaks with it. So the crossCheck compares
  against every other speaker in every language, which is exactly what catches friuli's narrator: Chris Basetta is the young
  man's voice too. A villager's `speaker` naming a learner's voice is refused, so the rule holds from both sides.
- **A learner's voice is declared by its `age`.** A speaker with an `age` is a learner's, whatever lists it: the bridge never
  gives it to a villager, and a `player` naming one is a voice of the learner's own in one line, as before.
- **A learner's voice never waits for the narrator's clip.** A villager's line waits for ElevenLabs while the narrator has a
  clip of it (the app plays that meanwhile); a learner's line has no stand-in, so its local Gepard voice speaks when
  ElevenLabs can't, as the brief asks.
- **The phone's voice imitates the voice when nothing else can.** Android has no gender on a voice; Samsung's voice names
  carry it (Teo's tablet: `it-IT-SMTm00`), Google's older ones too, some engines a feature. With a man's voice the line is
  said in it; else a man's line a little lower (×0.85) in the default voice (most defaults are a woman's), a child's at
  ×1.3: a 7-year-old's F0 is about 250–300 Hz against a woman's 200 and a man's 120.
- **A visit carries the kind, not the voice.** On a visit the learner's line is in the town's language: the town's voice of
  the same gender and age at the same place on its list is native there and checked against that town's cast; the
  visitor's own voice speaking another language would sound foreign and could be someone's of that town. Off stays off;
  the offer is never shown on a visit (its samples are the home village's language).
- **voice-build voices the learner's lines** (`my lines`, after the scenes and I spy) so a line isn't made live at its first
  tap: the choices are said in the learner's voice as they're picked, in the default voice by their 🔊 before. Low in the
  order, so a budget that runs out spends it on the villagers' lines first.
- **The sample** is a line with the learner's name, short and in the village's language, so the ▶ costs about 10 credits
  once per voice and says what their lines will sound like: "Ciao! Sono Teo.", "Živjo! Jaz sem Jan.", "Hallo! Ich bin …",
  "Hello! I'm …"; without a name, a greeting and a question. It is said in that voice alone (never a narrator's clip of
  it) and kept, so a second ▶ costs nothing.
- **The picker is a list with a ▶ per row**, not a carousel: two or three voices fit a phone's screen and Teo's tablet at
  once; the whole row chooses (a big target for a child), the ▶ only listens, so listening never changes the choice by
  mistake. The faces (👦 👧 👨 👩) say the kind without reading.
- **Named speakers by place** (`player-boy-a`, `-b`): the learner's choice names the place, so a voice swapped later (a
  better one, a voice that leaves the library) keeps every learner's choice; its `since` makes its clips again as they're
  asked for.

## How to use it

- The learner: "⚙️ Nastavitve › 🔊 Zvok in glas · Sound and voice" → "🗣️ Moj glas · My voice": ▶ to listen, a tap to
  choose, "🔇 Brez glasu" to turn the lines off. Or the sheet at their first line in a dialog.
- Swap a voice: put the new library id in the speaker's `elevenlabs` (`player-boy-a`), its name, and `since` (the day);
  the bridge reads the cast again, and the learner's next lines come in it. Add a voice: a speaker with its `age`, its
  id in `players` (at most 3 a place). The smoke test says whether the cast is right (`bun test/smoke.ts`, the "my voice"
  checks).
- The tutor: `voice_status` shows `my_voice` (the choice, the voice in effect, what is offered) and the cast's `players`.

## Tap counts

- Before: none (no choice; the voice followed the profile's gender only, Teo got Pastore Davide's).
- Choosing a voice in the settings: Home ⚙️ → "🔊 Zvok in glas" → a row: 3 taps (▶ to listen: one more each).
- At the first line: the sheet is there: 1 tap on a voice (or "✓ V redu", or beside the sheet: 1 tap).

## What's left

- **Jan and Teo haven't heard the voices yet.** They were picked by the library's labels and the free previews (pitch,
  loudness, noise; below). The first ▶ of each makes its sample on the node (about 10 credits a voice). A voice either of
  them dislikes is one line to swap (its `elevenlabs`, `name`, `since`).
- **The child voices are verified on ElevenLabs' v2.5 and multilingual v2 models, not on `eleven_v4_turbo`**, which
  the casts use: Bhola's ▶ is the first v4 take of Teo's default; if it doesn't sound like a boy speaking Italian,
  `player-boy-b` (Piku) or `-c` (Omar) is one tap away, and the cast's first is a one-line swap.
- **Slovene has no native voice beyond Nina and Luka**, who leave the library on about 2027-10-21; the others offered are
  South Slavic voices speaking Slovene, as the villagers' are. A designed voice (Voice Design, from Slovene) would be
  native; it costs a voice slot and credits, so it is the lead's call.
- **The stories' turns** (the storyteller's evening story) say the learner's line in their voice as every dialog does,
  but voice-build doesn't prebuild their right choices in it yet (`storyTexts` keeps them in the default voice): they are
  made live the first time.
- **The phone's voice by gender** relies on the engine naming a voice's gender (Samsung, Google's older names). Google's
  current voices name none: the default voice then, a man's line a little lower.
- **The learner's gender**: a profile without `learner.gender` hears the female voice by default and is offered both
  genders'; Teo's profile should say `"gender": "male"` (not checked here: the learners' data is off limits).
- **A role-play (the talk screen)** doesn't say the learner's lines; if it ever does, its characters by a scenario's
  `voice` speak in the narrators' voices, which may be the learner's (Luka for Jan).
- **The tutor writing the whole profile** keeps `preferences.my_voice` only because its guide says so; `update-db.py`
  keeps it (it changes the profile in place).

## Tests

- **Bridge:** `bunx tsc --noEmit -p .` clean. The whole smoke (`LANI_SMOKE_PORT=8980`, `LANI_SMOKE_FRIULI_PORT=8983`,
  `LANI_SMOKE_TOWN_PORT=8986`): 2,888 checks, all passed. New, `test/smoke/my-voice.ts` (runs before the voice section,
  which spends the bridge's live lines): each of the four casts offers 2–3 voices in each place, every entry valid, none a
  voice its cast speaks with nor twice (the crossCheck), each with its gender, age and name (a Slovene one its Gepard
  voice), `player` the grown-ups' first; the crossCheck refuses friuli's male narrator (Chris Basetta is the young man's)
  and logs a wrong entry of each kind; Teo (friuli, a boy, a child) gets a boy's voice, never Chris Basetta's or Kavya's,
  his choice, off, a voice not offered him; a profile without a gender offered both; Jan keeps Luka, a woman Nina; a cast
  without `players` falls back to its `player` but never to a cast member's voice, a child without children's voices gets
  a grown-up's; a visit's place on the town's list; voice-build's `my lines`; the sample line in four languages; on the
  smoke's bridge GET `/learner/voice`, `/voice/profiles`' `mine`, a sample made once in that voice and kept, a voice not
  offered refused, the choice kept in the profile (the rest of it as it was) and the next line in it, ElevenLabs down: the
  voice's Gepard voice though the narrator has a clip of the line, off, a woman's voice and a malformed one refused,
  `default`, `voice_status`. Elsewhere: the friuli bridge with Teo's case end to end (a boy and a child: the boys' voices,
  his line in Bhola's, in Italian), each culture's learner line in its first voice with the pack's `language_code`, a
  visitor's second voice mapped to the town's second, the old `player` checks rewritten (a villager's voice refused).
  `tutor-export --check`: 82 files match.
- **App:** the full unit suite, 3,306 tests in 424 classes, 0 failures (2 skipped). New: `MyVoiceTest` (4: the node's answers and the state after a choice, the offer's rule, the phone's lookups by the node's voice, none and an older node, the phone's voice by gender and a child's pitch), `DialogVoiceTest` (off: no line of the learner's, the reply at its own pause).
  The labels' tables (`L10nTablesTest`, `L10nParityTest`) with `myVoice.*`.
- **Python** (`companion/bin/qa`, `companion/tests/test_drama3_checks.py`): `tests` 251 OK; `companion/tests` 98 OK (10
  skipped).
- **The emulator** (the debug build; the dev bridge on 8791 inside the emulator lock, on a synthetic learner, Jan, a man,
  made by `test/fixtures/make-learner.ts`, `LANI_DEV_DATA`, `LANI_ENV_FILE=/dev/null`, `LANI_DEV_FAKE_ELEVENLABS=1`, no
  Gepard or Whisper worker; `companion/bin/qa --no-build --apk … --no-town --steps my-voice`, about 6 minutes): setup,
  my-voice and the crash check passed. Babica's soup: "Dober večer! Lahko pomagam?" tapped, said as "node clip (player)"
  in Luka's voice, the offer over the dialog ("Kateri glas je tvoj?": Luka, default, Tomislav, Marko, Brez glasu) while
  Babica's reply went on; Tomislav's ▶: "Živjo! Jaz sem Jan." made by the fake in his voice (`O9XgmQnaZveORWl6mTHm`, 19
  characters) and played ("sample clip"); a tap on Tomislav closed the sheet, the node kept it (`/learner/voice`,
  `/voice/profiles`); the next line, "Skoraj. Še malo soli, prosim.", made in Tomislav's voice and heard before her reply;
  the settings' page with Tomislav marked, "Brez glasu" kept off, Luka again. Screenshots (`scratchpad/ux-voice/`):
  my-voice-offer, -sample, -next-line, -settings, -off. Not on a tablet: the sheet and the block are at most 640 and 720 dp
  wide, centred, as the app's other sheets and pages.
