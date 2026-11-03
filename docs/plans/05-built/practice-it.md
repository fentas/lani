# Built: challenges, the car and numbers in Italian (practice-it, 2026-11-02)

Wave D, batch 2, section 10 (followups-11.md). Teo's Home said "Challenges: None yet", a run said "No grammar drills yet",
four of the car's buttons did nothing, and the numbers module was Slovene's. Each of these now works in Italian, and each is
built so that German and English follow by adding data. Audience: Friuli's, German first with "du" for a child, every form
glossed, nothing frightening.

All of the Italian, German, Slovene and English written here is machine-written and not reviewed by a native speaker. The
files say so (`review`), the module's screen says so. The list for a native check is at the end.

## What was wrong (checked on main 45924165)

| Where | What it did | Now |
|---|---|---|
| Home's ✨ (`HomeLogic.challenges`) | "None yet": no module of the language | the modules at the learner's level |
| A run (`Content.pick`) | "No grammar drills yet", "No conversation drills yet" | the modules' exercises; no note (`ItalianPracticeTest`) |
| The road's sheet, the car's menu, the switch buttons | a session with nothing played nothing and said nothing (read in the code: an empty playlist, `RoadService.items`; not played on a car) | it stays away, the sheet names what isn't there and why |
| "🔢 Števila" (`NumbersPractice`) | Slovene's only: `Counting.active`, `sl/stevila` | a `NumberTongue` per language: Slovene as before, Italian new |

## Modules: `companion/modules/<language>/<id>.json`

The bridge already read that directory; there was nothing in it. Two things were added.

**Texts per base.** A learner of Italian may read from German, Slovene or English, so a curated module's learner-language
texts may be `{"de", "sl", "en"}` (English always), as a course unit's are: `explain`, `hint`, `instruction`, `prompt`, and also
`title`, `description`, a quest's `story`, a flashcard's `back` and `note` (`MODULE_BASED_KEYS`, `validateCuratedModule` in
`bridge/src/store.ts`). The bridge resolves them in the learner's base before serving, so `GET /modules` and `GET
/modules/:id` give plain strings and an app of any age reads them. Teo's tablet (0.2.698) needs no update for them. A module
the tutor publishes keeps plain strings.

**Offered by level.** Until now every curated module was A1. The app puts every module's exercises into every run and turns
each quest into a villager's request, so a congiuntivo module would have reached a child at A1. `GET /modules` now leaves out
a curated module above the learner's level (`ModuleStore.offered`, with the pace below), on the bridge, so an older app gets this too. The tutor's
`list_modules` shows all, `GET /modules/:id` serves any, and what the tutor published is always offered. The level-up moves
the `content` area, so the app reads the list again and the next level's modules come as new.

## Drills: `companion/drills/<language>/`

Nothing in code is Italian. The format had `language` already; the bridge's `numberWord` now counts in all four as the app's
`SpokenNumbers` does, so a rapid set's generated numbers work anywhere. The app bundles the directory as it finds it
(`DrillFiles` in `build.gradle.kts`), and the smoke holds any language in `practice.ts`'s table to the same checks.

## The road's buttons

`RoadMix.available(library, today)` says which sessions have something to play. `RoadMenu.children` and `RoadMenu.actions` take
it, so a car's menu lists only those (with nothing in a tab, "get ready in the app first"), and no switch button leads to
nothing. The sheet shows the same buttons and under "Več · More" says "🔇 … posnetkov še ni: Preobrat, Hitri odziv …". A
getting-ready that found no clips at all says there are no recordings in this language (`road.noRecordings`), rather than
"nothing is ready" with no reason. This holds for every language. Taps to learn why a button is missing: 1 ("Več") against
before, where no number of taps said anything.

## Numbers: `data/NumberTongue.kt`

The module's ladder, runs, schedule and screens are shared; what is a language's own moved into a `NumberTongue`: the unit that
opens it, its words and what else is right, a date, a clock time, the mistakes and their whys. Slovene's behaviour didn't
change (`NumbersPracticeTest` is unchanged and passes). A `GameState.numbers` from before stays valid, and a learner whose
`numeri` intro is done has the module at once.

What Italian does, and what a German-speaking child gets wrong in it:

| | Italian | Graded as |
|---|---|---|
| Words | all one word: ventisette, ventitré, ventotto, centotré, duemilaventisei (`SpokenNumbers.italian`, also the fix for centotré in speech) | apart: almost; an accent forgotten: almost; another number's word: wrong |
| Order | the tens first; German says the ones first | 72 for 27, "sette venti": `numbers_order`, its why says so |
| Date | "Oggi è il ___ maggio. (15)": the plain number; il primo; l'otto, l'undici | "uno" for the first: `numbers_ordinal_date` (its id kept) |
| Clock | alle cinque, all'una, alle cinque e mezza; le cinque, cinque, e mezzo, e trenta, a mezzogiorno also right | another hour: never a slip |
| Half past | from the hour that is there; German's "halb sechs" is the other way | 4:30 for alle cinque e mezza, "alle sei e mezza" for 5:30: `numbers_clock_time_half` |

The unit `numeri` opens it, `ore-giorni` brings the dates and the clock (`numbers.timeOn`). `get_village` shows it, and the
tutor's guide on the numbers says what the ids mean in Italian.

## Tap counts (Teo on Home)

| Journey | Before | After |
|---|---|---|
| A grammar drill | none to play: ✨ opened an empty sheet | 3: ✨, the module, "▶ Inizia" (its giver's card between); the list grows one a day at most |
| Numbers | none: no tile, the unit's page had none | 2 once `numeri`'s intro is done: 🔢, "▶ Inizia"; closed, 1 to the intro |
| Why a car's drills aren't there | not said anywhere | 2: 🚗, "Altro · Mehr" |

## What German and English need (the next agents)

**Modules:** files in `companion/modules/de/` and `en/`, with the shape of the Italian ones. Bases: German's `sl`, `en`, `it`;
English's `sl`, `de`, `it` (`en` still required by the format for German's). Add the language to `LANGUAGES` in
`bridge/test/smoke/practice.ts` with its culture and bases. Nothing else changes. `bun test/check-practice.ts de` checks
them while writing; extend `ItalianPracticeTest` if the run's note matters.

**Drills:** files in `companion/drills/de/` and `en/`: `preobrat`, `hitri-odziv`, `gradnja`, `uganke` by those ids, with each
text in the language plus `en` and `de`, the riddles' `teller` someone of the culture's cast (Kärnten's and the Lake
District's storyteller), `word` from its packs. The buttons come by themselves once there are clips.

**Numbers:** one object in `NumberTongue.kt`, its name in `ALL`, its texts in the four string tables under `numbers.de.*` or
`numbers.en.*`, as Italian's are (`tip`, `ask.timeWrite`, `why.*`: each named whole in the object, because `L10nTablesTest` reads keys
and their arguments from the code, and each message's arguments must be in its Slovene or English one), its unit in `NUMBERS_UNITS` (`bridge/src/game.ts`), and a test as `NumbersItalianTest`.

| | German (`zahlen`, `uhrzeit`, Austrian) | English (`numbers`, `time`, British) |
|---|---|---|
| Words | `SpokenNumbers.de` stops at 999: add 1000–9999 (tausend, zweitausendsechsundzwanzig) | `SpokenNumbers.en` stops at 999: add one thousand, two thousand and twenty-six; a year also "twenty twenty-six" |
| Other forms | ein, eine; tausend or eintausend | hyphen or not; "a hundred", "a thousand" |
| Order | the ones first, like Slovene: `swappedWords` "zwanzig sieben" | the tens first, like Italian: "seven twenty" |
| Date | "Heute ist der ___ Mai. (15.)": an ordinal, fünfzehnte; first, third, seventh and eighth are irregular; **Jänner** in Austria | "Today is the ___ of May. (15th)": fifteenth; first, second, third, fifth, eighth, ninth, twelfth |
| Clock | um fünf; **um halb sechs** is 5:30, as Slovene: `halfHeard` h + 1; viertel sechs also Austrian | at five; half past five, half five in British speech, five thirty also right; a learner from German may count towards the next hour |
| Dates' and clock's page | `uhrzeit` (`dateUnit` = `timeUnit` = uhrzeit) | `time-dates` (unit `time`) |

## Voice

Nothing was voiced; no ElevenLabs call was made. A build would take (the corpus's rows, one voice each, the female narrator's;
the riddles in Nonno Bepi's):

| What | Texts | Characters |
|---|---|---|
| Drills A1 | 841 | 15,579 |
| Drills A2 | 302 | 7,271 |
| Drills B1 | 98 | 2,416 |
| **All the drills** (a text said at two levels counts once) | 1,205 | 24,601 |
| The modules (`say`, options read aloud) | about 250 | about 3,300 |
| **Together** | about 1,450 | about 27,900, about 14,000 credits on `eleven_v4_turbo` (`MODEL_CREDITS`) |

An upper bound: a text the store already has isn't made again, and this count didn't read any learner's voice store. The
numbers module's own words aren't in the corpus: they are made when heard. `voice-build`'s priorities are `modules`, `drills
A1`, `drills A2+`, so a budget that runs out has the easiest. Jan's decision; nothing here spends.

Until then the road leaves those sessions out. Inside the app a module's 🔊 and a number heard ask `POST /voice/say` as any
Italian line does (made on demand within the bridge's limits, else the phone's voice), so Teo's use may spend a little of
the live reserve. No new mechanism: say so to Jan if the reserve matters.

## Checks

Run on this branch, merged with main 1e0cdf04:

- Bridge: `tsc`; the smoke, all checks passed (3,545), its new section `practice.ts` and friuli.ts's serving; `tutor-export --check`.
- Android: the full unit suite, 3,802 tests, 0 failures. New: `NumbersItalianTest` (11), `ItalianPracticeTest` (4), two in `RoadMenuTest`.
  `NumbersPracticeTest` is unchanged and passes.
- Python: `tests` and `companion/tests`.
- Tap coverage after: `lexicon-build --coverage --lang it` 99.9 % of tokens, 99.1 % of words; about 50 words and forms the new content
  brought went into `lexicon/it.extra.json`. That count leaves out the drills, so they were scanned separately.
- QA through `qa-slot` on a synthetic Teo (`--steps practice-it`, screenshots pi-*.png): the ✨ tile with modules, the sheet at A1 only, a
  module from Nonna Rosa's card with its why in German, the numbers closed, its intro skipped, the note, open, the ones first's why,
  half past's why, 🔢 on Home, the road's sheet without drills. Slovene regression on a synthetic learner: `--steps numbers,road`,
  with the car's browser listing five sessions under "Več" (the four drills, without clips, left out) and all of them playing.
- Not run: a number heard in Italian on the emulator (the code is shared with Slovene's, which QA hears), a real car, Teo's tablet size.

## For a native check

Everything below is machine-written. Italian (for a native Italian speaker):

- `numbers`: "Oggi è l'otto maggio", "l'undici marzo"; "duemila e ventisei" accepted beside duemilaventisei; `centouno`/`centuno`,
  `centotto`/`centootto`; "alle cinque e trenta" accepted.
- Modules, marked "almost" rather than wrong: "Quando sono grande, avrò …", "Penso che Giulia è …", "Lorenzo può già leggere"
  (for "sa"). Accepted as spoken: "Penso che Giulia è a casa", "Spero che vieni", "Peccato che non vieni". Counted wrong, perhaps
  heard in speech: "C'è tre gatti", "restate" for "restiate" after "è meglio che".
- Modules, is each wrong option firmly wrong: "Sono alla casa", "Vado nella montagna", "Abito in Udine", "Giulia va alla scuola",
  "Da piccolo ha giocato sempre", "Era sera e ha fatto freddo", "Mentre Giulia ha letto, Moro è entrato", "Ho dormito quando
  arrivava Moro"; the gap "Tu ___ (dovere) dormire di più" takes only dovresti (the gloss rules out devi); è andato as the everyday form of andò.
- Word orders accepted in chips: "Quando dormivo hai telefonato", "Fa meno freddo oggi di ieri", "Andrò domani a Udine".
- Drills: "A ottobre" against "Ad aprile", "Ad agosto"; riddles: "La gallina mi fa." (l'uovo), "Al sole divento acqua.", "Ho tanti
  gusti.", "Mi spingi con i piedi." (la bicicletta), "Con il raffreddore lavoro male.", "Scendo dagli occhi quando sei triste.",
  "Sono buono sul pane.", "Mangio la legna."; building: "Oggi piove sulle montagne", "Stasera chiamo la nonna con il telefono di
  papà.", "Il gatto dorme sotto il tavolo in cucina."; transformations: "Avrei una domanda.", "Compreremmo il pane." (mechanical),
  "Il fiume è largo come il ruscello.", "La mattina" beside "Di notte".
- `dove-preposizioni` doesn't teach "in montagna", which `preposizioni-articolate` (the module) treats as a set phrase: the page may want a line.

Slovene in the modules' `sl` and the new strings (Jan's wife, as ever, with fran.si for what is standard): "Videm (Udine)", "Giuliin"
(the book has Giuliin and Giulijin), "Mislim, da ja", "Hočeš sir? Ja, malo ga hočem", "Za omleto" for frittata, the `numbers.it.*` and `road.notRecordedYet`, `road.noRecordings` lines.

German (Standard, "du") in the modules, the drills and those strings: not reviewed either.

## The pace

The app turns every listed module with a quest block into a request at once (`GameEngine.withTutorQuests`), and every module's
exercises into every run. Eight listed are eight requests. What that did, on a synthetic Teo at Vas (it-de, child, Friuli,
13 people met, a tablet's window of 4; QA's `practice-pace`, `before/`, `after/` of its screenshots):

| | Eight at once (first version of this branch) | One at a time |
|---|---|---|
| Home's ✨ | "8 new" | "1 new" |
| Home's Today | one module under "Also" | the same one, the level's first |
| Requests open | 11: 8 modules, 3 a villager's own | 4: 1 module, 3 a villager's own |
| The map, the first day | 3 own and 1 module (the window keeps what it showed), "📜 +7" | 3 own and 1 module, "+0" |
| The map, the next day on | 4 modules, no villager's own (`CuratedPaceTest`: the tutor's rank above a villager's, a giver each: Rosa, Bepi, Davide, Giulia) | 1 module and 3 of their own |
| Which comes first | Nonno Bepi's "Ho mangiato": the last unit of A1 | "Sono o ho?": `order` follows the course |
| The chronicle | 8 lines at once | 1 |
| A gathering run | any of 56 grammar items, from units not reached | that module's and those before |

So it wasn't a wall the first day: the window and its pins held. It was a slow takeover: for weeks every bubble would have been a
12-question grammar drill and his own villagers' requests would wait in "Later". Tutors have a rule against that (publish_module warns at
four open), and curated modules had none.

**The rule** (`ModuleStore.offered`, on the bridge, so 0.2.698 gets it): the list holds the modules already asked (dropped, the app would
forget them as played) and, when none is open and none was asked today, the next by level and `order` whose giver lives here. So
one request is open at a time, at most one new one a day, in the course's order. A module not played holds the next one: that's the
pace, and nothing in the game waits for modules. A level-up adds its modules after the level before. Nobody here for any: the first, without a request, for the runs. A
module without a quest block and what a tutor publishes are never held. `/modules` now also depends on the `village` area, so the next
comes without a restart.

**Teo's 0.2.698:** read at its commit a37c1765 (the commit count is the version; no tag). `ContentController.reload` reads `/modules` every time
Home comes back, so the next arrives there with no area check; `GameController.tick` calls `withTutorQuests` with the list; `Quests.fromModule`
reads `quest` and `since`; a module gone from the list loses its request, which is why asked ones stay. `QuestQueue.of`, `RequestWindow.order`
and `Quests.kt` are the same as now (the diff adds only `retry` and `retried`), so `CuratedPaceTest` holds for it. It was not run on that build.

**Crowding out, checked:** a villager's bubbles and the arrivals' stay up (screenshot); the tutor's bar and messages aren't touched by a module's
arrival (no event, no channel message; only `publish_module` tells the tutor); a run has one module's items, so a Stone run of 8 is that
module's 7 and a card's, which repeats until the next comes. That is the cost of this pace: said here, not hidden.

## Deploying

Curated modules are read once, when a bridge starts (`ModuleStore`): a learner's bridge restarted through the gate has them. Drills are read per request. Once
restarted, Teo's village gets one module, `essere-avere`, with Nonno Bepi's request, and the next at the pace above. An older app reads them as it reads any module.

## What's left

- `about_exercise.source` for a numbers task doesn't say its language; the tutor knows the learner's. `lani-fix` reads it as a note for the developer in any language.
- The base `sl` for the car's drills: the texts have `it`, `en`, `de`, as Slovene's have `sl`, `en`, `de`, so a Slovene-speaking learner of Italian hears English cues.
- German and English say "None yet" and "No grammar drills yet" until their modules are written: it's true, and it says so.
- Modules above B1 and drills above B1: none in any language.
- A module's exercises come into a run at the module's level, not by the page's being introduced: as Slovene's did. A child at the start of A1 may meet an A1 page the school hasn't reached (the module's why explains it).
