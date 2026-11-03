# Built: the placement test (placement)

*Built 2026-11-03, branch `placement` (wave E, followups-14.md section 3). Jan: "I want to have app finished to
release". Before, the school's levels ignored the learner's level (`School.needs`, game/School.kt): a B1 starter began
at A1 and tested out of one unit after another: 32 tests before the first B1 lesson in Slovene, 36 in the other courses.
Now an optional placement test, short, adaptive and made of the course's own test items, finds where the starter is,
marks the units below as done (still open to do again) and suggests the level. It never changes an existing learner's
progress (Jan's, Teo's) unless they take it and say yes to its result, and then it only adds.*

## Native check (first)

Every new string is machine-written by Claude (`_note_placement` in the four tables). For a native speaker:

- **sl** (Jan's wife): `placement.*`, `school.placed`, `setup.placement*`, `outbox.placement`. Most to check:
  - "Poglejva, kaj že znaš! Vprašala te bom nekaj stvari: težje, ko ti gre, lažje, ko ti ne." (the teacher, Mojca)
  - "Igrajva se: pokaži mi, kaj že znaš! Samo tapni. Nič hudega, če česa ne veš." (to a child)
  - `placement.marks`: "{n} enot pred njo šteje za opravljene; še vedno jih lahko odpreš in narediš znova." (the
    plural forms one/two/few: "1 enota pred njo šteje", "2 enoti … štejeta", "3 enote … štejejo")
  - `placement.logged`: "Uvrstitveni test: začetek pri B1 1/3 (32 enot opravljenih)"
  - "Začneva na začetku: tako je čisto prav!", "Ostanem, kjer sem", "Najprej kratek test", "Uvrstitveni test" itself.
- **it** (Teo's Italian; the teacher Maestra Elena speaks to a child): "Facciamo un piccolo gioco: fammi vedere che cosa
  sai già! Basta toccare. Va bene anche non sapere.", "Test di livello" for the placement test, "Comincio da qui",
  "Resto dove sono".
- **de** (Teo's base, the Carinthian teacher Lehrerin Eva): "Einstufungstest", "Spielen wir ein kleines Spiel: Zeig mir,
  was du schon kannst! Tipp einfach an. Es ist nicht schlimm, wenn du etwas nicht weißt.", "Hier fange ich an".
- **en**: British English; "Keep my place", "Know some already?".

Nothing of the course's content changed: the test asks the units' own test items as they are.

## What

"🧭 Uvrstitveni test · Placement test" (`game/LevelPlacement.kt`, pure; `app/PlacementController.kt`;
`ui/school/PlacementScreen.kt`; COURSE.md "The placement test", GAME.md "The school", README "The school's course").

- **Parts.** Each level's written units in the course's order in three parts (A1 1/3 … B2 3/3): twelve parts for each of
  the four courses (sl 18/14/16/18 units, it, de, en 18 a level; every part 4 to 6 units).
- **A probe** of a part: exercises of its units' own tests, each from another unit, the ones that ask the most of what
  the probe still needs in the least time first (a new kind counting a quarter more), until it has 8 items in 2 exercises
  at least, or 3 exercises. A table is cut to its first rows of at most 6 cells, a list (transform) to its first 4 lines,
  a sort to 6 words a column at a time; a gap text of more than 6 gaps and anything heard, said or graded by the tutor
  isn't asked. It passes at **75 %** of its items, an almost counting half, as a unit's
  test counts.
- **Adaptive.** It starts just below the learner's level (the profile's, as the app goes by it; on this device the
  setup's "Your level now"): A1's middle part for an A1 learner, the last part of the level below for anyone above (A2 3/3
  for a B1 learner), and after the parts whose units the learner has done already. Passed, it climbs two parts, then four,
  then eight; once a part fails it halves the gap between the highest part passed and the lowest failed until they meet.
  At most 6 probes; it ends early when the next probe wouldn't fit in 15 minutes at the learner's own pace, or when the
  learner stops. Every part up to the highest passed counts as known.
- **The result.** The teacher says where the learner starts, then: the first unit, how many units before it count as done,
  the level (raised or as it stays), the answers' pay. "✓ Začnem tukaj · Start here" takes it; "Ostanem, kjer sem · Keep my
  place" doesn't; with nothing to take (a beginner) one "✓ V redu · OK".
- **A child's version** (Teo, 7: the app set up for a child): "Igrajva se: pokaži mi, kaj že znaš!", tap exercises only (a
  choice, a multi, a sort of at most 6 words, word chips of at most 6, a gap text of at most 3 gaps with its word bank), A1
  to the end of B1, probes of 4 items in 3 or 4 exercises, at most 4 probes and 10 minutes; its "✓ Začnem tukaj" behind a
  parent's two-second hold (a parent decides where the child starts). A choice reads aloud where the course gives it a
  `say` or `say_options`, as in the unit's test; the teacher's lines on the page and the result have their 🔊.

### Decisions, and why

- **Parts of a level, not single units.** A unit's test is about 12 items; testing each of 66 units is the problem this
  solves. A third of a level (4 to 6 units) is what one probe of 8 items can vouch for, and 12 parts take at most 4 to 6
  probes to search: 10 to 15 minutes.
- **75 % to pass a probe**: between the treasure station's 70 % and a unit test's 80 %. A probe samples 3 of a part's 5
  units, so it is a little more lenient than the unit's own test, but it never places above the parts actually passed:
  every part below the highest passed counts as known only because the search is monotone (a higher part passed means the
  lower ones would be). The unit tests stay as they are: a learner who doubts can do any unit again.
- **Starting just below the learner's level, climbing fast, halving once wrong** (an exponential search, then a
  bisection): a beginner is done in two probes (about 4 minutes, two "Nič hudega"); a B2 learner climbs A1 2/3 → A2 1/3 →
  B1 2/3 → B2 3/3 in four probes; the longest way is six (about 14 minutes; 8 to 11 on average, by course). A
  slow learner's test ends earlier: before a probe that wouldn't fit in 15 minutes at their own pace, placing them at most
  one part lower than where the search would have met. A fixed bisection from the middle would
  open with B1 items for a beginner.
- **No help, nothing counted on the rules.** The run has no 📋 cheat sheet and no 💬 tutor (`ExerciseRun(practice = false,
  help = false)`), and its answers count on no grammar page: a test of where the learner is shouldn't unlock B2 pages for an
  A1 learner, record mistakes on rules they've never met or make a rule "secure" (the adaptive choice is off too, so the
  test is the same for everyone). The feedback after each answer stays (the app's way: immediate, with its why); a 💡 hint
  makes an item almost, as everywhere.
- **Its answers pay as a test's** (`GameController.finishSheet`, the fresh-mind rule), **no unit's bonus**: 36 units' bonus
  at once would break the pacing.
- **Taking it only adds.** A unit not done is done today, tested out and `placed`; a unit begun keeps its sheets; a unit
  done, a level open, a best try: as they were. The level is raised, never lowered: a level the node has above the test's
  (a tutor's placement) stays. The levels up to the one the learner starts in open (`opened`), past the school's building
  and its upgrades, which otherwise hold A2, B1 and B2 back: a B1 starter can't be told to build and upgrade a school to
  reach lessons they already passed. The hamlet still begins the class (the camp lasts a day or two), and the levels
  after the one opened open as before.
- **The intro of a placed unit counts as skipped** ("⏭ Poznam to"), so what a unit's intro turns on is on for a placed
  learner: counting cards (stevila), the numbers module, the forms cards' rows of its pages, the case trainer. Without it a
  B1 starter would have A1's forms greyed. The notes those intros say ("🔢 Kartice za štetje so odslej …") aren't said for
  32 units at once.
- **The level at once in the app** (`LevelPlacement.level`, as the treasure's: `ContentController.levelIn`), until the
  node has it; the treasure map's "time at the level" counts from the placement's day.
- **Offered, never pressed.** The card at the top of the school is for a learner who has neither done nor begun a unit of
  the course and hasn't taken the test; everyone else (Jan's school, Teo's) has a quiet row under the levels. Nothing
  migrates and nothing runs by itself.
- **Prints:** none. The test is the app's only; its items are the units' tests, which print with their units.

## How

- `game/LevelPlacement.kt` (pure): `parts`, `asked` (what a version asks, cut short), `seconds` (each exercise's time,
  about: a choice 15 s, a gap 15, a cell 12, a list's line 25, 10 for its feedback; a child's half as long again),
  `startAt`, `start`, `next`, `probe`, `record`, `stop`, `result`, `apply`, `level`, `confirm`, `offer`.
- `game/School.kt`: `CourseProgress.placements` (by language: `Placed`), `UnitProgress.placed`.
- `app/PlacementController.kt`: the test under way, the pay at its end, `take` (apply, then the outbox), `keep`, `check`
  (the node has the level), QA's `qaAnswer`.
- `data/PlacementReport.kt`: the body of `POST /placement` and an older node's `session_end`; `data/Outbox.kt`
  `Writes.placement` (with its fallback, as the treasure's `levelUp`).
- `ui/school/PlacementScreen.kt` (`Screen.Placement`, route "placement"): the page, the probes (one `ExerciseRun` each,
  the teacher's stage, "1. del", "2. del: malo težje / lažje"), the result. `ui/school/SchoolScreen.kt`: the card, the
  row, a unit's "✅ Opravljeno z uvrstitvenim testom". `ui/PlayerScreen.kt`: `ExerciseRun(practice, help)`.
- Setup: `ui/setup/SetupScreen.kt` `PlacementChoice` on "Še o tebi" and on this device's questions
  (`ui/hosted/HostedScreens.kt`); `AppViewModel.askPlacement`, `found` (the founding opens the test's page once when it was
  chosen; `Prefs.placementAsked` keeps the choice across a restart).
- The bridge: `src/placement.ts` (pure: the body, the report, the tutor's line), `POST /placement` in
  `features/level.ts` (once per client_id; update-db.py; `placement` to the tutor), `get_course` shows `placed` and
  `placement` (`features/course.ts`; the tool's description, so `companion/tutor/tools/get_course.json` is regenerated).

### Older apps and older bridges

- **The bridge serves nothing new** to an app: the test is the app's (its items are the bundled course's), so an old app
  (Teo's tablet on 0.2.698) gets nothing it can't read. The bridge tells apps apart by what they ask (`?arts=`, scenes.ts
  `forApp`), not by version; this needs none of it.
- **An old app on another device of the same learner** reads what the test marked in the old format (units `done`,
  `testedOut`, `introDone`; levels in `opened`): it shows them done and the levels open. It doesn't know `placed` (its label
  reads "Opravljeno s testom") and drops `placements` and `placed` when it saves the village (`PUT /game` keeps the course
  whole only when an app leaves all of it out); the level is on the node by then (`POST /placement`).
- **An older bridge** answers `POST /placement` 404: the outbox sends a `session_end` with the report (its `level` when it
  rises) for the tutor to persist with update-db.py, as the treasure's `/level` does. Until the node has the level the app
  goes by it.

## How to use

- **A starter at setup:** on "✨ Še o tebi" (the family's computer) or on this device's questions, "🧭 Nekaj že znaš? ·
  Know some already?": "Od začetka · From the start" (marked) or "Najprej kratek test · A short test first". With the
  test, its page opens once the village is founded: "▶️ Začniva" or "Pozneje".
- **In the school:** the card "🧭 Nekaj že znaš?" → "🧭 Na test" (a learner who hasn't begun), else the row "🧭 Uvrstitveni
  test: kje začnem?" under the levels.
- **QA:** `companion/bin/qa-slot --steps placement` (only when named): the hooks `placement:fresh` (the dev village's class
  readied with nothing of the course done) and `placement:answer:<part>` (the test on screen answered to its end as a
  learner who knows the course up to that part: 5 is A2's end). `placement` alone opens the page.

## Tap counts

| Journey | Before | After |
|---|---|---|
| A B1 starter to the first B1 lesson, from the school | 32 (sl) to 36 units × (the unit, "⏩ Znam že", its test of ~12 items) ≈ 100 taps and ~400 items | 2 to the test ("🧭 Na test", "▶️ Začniva"), its 4–6 probes (~40 items), 1 to take it ("✓ Začnem tukaj"): the school with B1's first unit ★ |
| The setup, the default kept | Jan's QR way 5 taps, Teo's 8 | the same: "Od začetka" is marked |
| The setup with the test | — | +1 ("Najprej kratek test"); after the founding its page is there, +1 ("▶️ Začniva") |
| An existing learner (Jan, Teo) | — | nothing changes: a row under the levels, never pressed |

## Tests and QA

- `game/LevelPlacementTest` (the four bundled courses): three parts a level, each unit once; an adult's probe and a child's
  for every part they ask; nothing heard or said, a table and a list cut short; a child's by tap only; for every course,
  version, starting level (A1 to C1) and true place (none to B2's end): never placed above it, found where the search
  meets, at most its probes, no exercise asked twice; an adult starting at A1 found anywhere; the time within 15 minutes
  (a child's 8) and a probe's overrun; a beginner done in two probes with nothing to take; where it starts; stopped, the
  probe on screen decides nothing; a learner's parts done count as known; taking it only adds (Jan's units, a unit begun's
  sheets, the levels open as they were, no unit's bonus, the line in the chronicle); a level above it stays and is
  confirmed at once; the level counts until the node has it; past the course's end: every unit, B2; the offer: a card to a
  starter, a row to everyone else (another language's units don't count); the village untouched by the test itself.
- The bridge's smoke: `test/smoke/placement.ts` (taken above the level, replayed, kept, taken below the level and a child's
  version, refused bodies, Italian) and `test/smoke/course.ts` (`get_course`: a unit placed, the test taken).
- QA on the emulator (`companion/bin/qa-slot --steps placement`, a synthetic learner Ana, sl · en, made by
  `test/fixtures/make-learner.ts`; the screenshots in the scratchpad under `ux-placement/before` and `after`):
  - **Before** (an APK from before this branch, 5bd990b7 of course-en-b2-b, in main's history): a starter's school is A1
    0/18 with "Biti" ★, nothing else; "Še o tebi" has the aim and the voice only.
  - **After**: the school's card "🧭 Nekaj že znaš? · Know some already?" above the teacher; its page (Mojca's line, how long,
    no cheat sheet, nothing changes until yes, "▶️ Začniva", "Pozneje"); the first probe on the stage ("1. del · Part 1",
    a table "Imam … → Nimam …", no 📋 or 💬 in the top bar); answered by QA's hook as a learner who knows A2 (39 items,
    24 right): "🧭 Tvoje mesto v šoli", "Začneš na ravni B1!", the first unit (Oče – očeta, dan – dneva), "32 enot pred
    njo šteje za opravljene", "A1 → B1", the pay (+63 🌾 +159 🪨), "✓ Začnem tukaj" and "Ostanem, kjer sem"; after it the
    school with A1 18/18 and A2 14/14 each one line, "★ Naslednja · Next · B1: Oče – očeta", B1 open further down.
  - Found by QA and fixed: an early return inside `key { }` built for the JVM tests but couldn't be dexed (the APK
    failed); the first unit's title came in both halves of the pair.

## Voice

No clip is needed for the test to work: the teacher's lines are labels, read by the 🔊 on tap through the voice the
node or the phone has for the teacher. A build of the five teacher lines (`placement.hello`, `helloChild`, `startAt` for
A1 to B2, `startBeginning`, `startPast`) in each course language: about 1,300 characters (sl 250, it 290, de 350, en 300,
with `startAt`'s four levels). Characters for the teacher's voice: Učiteljica Mojca (sl), Maestra Elena (it), Lehrerin Eva
(de), Mrs Hartley (en). Not built: Jan's credits.

## What's left

- **A native check** of the strings above.
- **"📨 Pošlji znova"** for a level the node doesn't show after days (the treasure has one); the outbox retries meanwhile.
- **A child's spoken version.** The child's test is by tap, each exercise's 🔊 there; a version that reads every prompt
  aloud as it comes up (as the intro's "Beri razlago" does) would suit a child who doesn't read yet.
- **A second probe of a part that passes narrowly** (between 60 and 80 %) would place more surely at the cost of a minute;
  not done: the learner sees the result before taking it, and any unit can be done again.
- **The setup's speech** doesn't read the new "🧭 Nekaj že znaš?" choice aloud (its chips are TalkBack's).
- **The Build sheet's note** under the school's card ("… šola ga preseli pod streho in odpre A2") still says the school
  opens A2 for a placed learner whose A2 the test opened already; the note's wording is native-checked, so left as it is.

## Also on this branch: the curated modules from the learner's level (55ade858)

Found by practice-it-b2, asked by the lead: `ModuleStore.offered` paced the curated modules from A1 up, so a starter at
B2 (or one the placement test puts there) was asked A1's first module first. Now the next is the learner's own level's
first, then the levels below, the nearest first, each by its `order`, and the list comes in that order (Home's ideas take
its first). The pace is the same (one open, one new a day); a module asked (open, done, or led to by a `helps` request)
stays listed whatever its level. Reasoned from the code and the content, not from their data: Slovene's six curated
modules are all A1, so a learner with all six asked (Jan) gets nothing new before or after; an Italian learner at A1 (Teo)
gets the same next module as before (A1 by its `order`), only the asked ones list by `order` instead of by file name; at
A2 or above the learner's level's first comes before the lower ones left. Smoke: modules.ts, 6 checks.
