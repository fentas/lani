# Built: "🔢 Števila · Numbers", a practice module of the numbers (2026-10-19)

Jan asked: "Do we learn numbers in school at some point? Would be nice: a separate test module. 1. listening/text → a
number pad to type it; 2. number → write (the word); 3. ?" This is not a phase of plan 5; it was built in the same round
(branch `numbers`). The full description is in companion/README.md, "Numbers"; the rules in companion/GAME.md (the
school's section) and companion/COURSE.md ("What a unit turns on").

## What

- **The module** opens with the intro of the A1 unit `stevila` (as the counting cards do), from the unit's page and as an
  idea under Home's "Še · Also". Closed, its screen says what opens it, with "📖 Odpri uvod · Open the intro" one tap
  away (before the hamlet: "🏫 V šolo").
- **A run of 10**, the ways of asking mixed: a number heard and typed on a custom number pad; its word read and typed in
  figures; its figures and the word written (č, š, ž forgotten is almost, the words apart too, another number's word
  wrong, the tens first wrong of their own kind, with the rule: sedem-in-dvajset).
- **Mode 3, the choices:**
  - **say it** (🎤, the speak exercise; a recognizer's figures read back as the word, a year right as its figures): from
    a range the learner has, where something can listen. Production after recognition, the app's mastery ladder.
  - **the date's ordinal** ("Danes je ___ maj. (15.)" → petnajsti; sedmi, osmi): the unit stevila's own page `datumi`,
    in the nominative only, as the unit introduces it.
  - **clock times** by ear ("ob pol šestih" → 5:30 on the pad with a ":" key) and in words: gated by the unit
    kdaj-cas's intro, which teaches ob + -ih and half towards the next hour.
  - **Not built:** prices ("tri evre petdeset": the accusative after stati, the unit tožilnik's, and evro's counting
    forms; worth it once the school's pages for it are there), phone numbers digit by digit (less useful than whole
    numbers by ear), sums by ear (a game more than a skill). Years are the last range's numbers, not a mode.
- **Ranges i+1:** 0–10 and 11–20 (the intro), 30–90, 21–99, 100–999, 1000–9999 with years; the next opens at 8 right
  of the last 10 in the newest range, shown with a bar. 60 % of a run's new numbers from the newest range.
- **Repeats in the module's own schedule** (not review cards): the next run, then +1, +3, +7 days after each right
  answer; at most 4 a run. Kept in the village state (`GameState.numbers`); the bridge keeps it from an older app
  (PUT /game) and `get_village` shows it.
- **The tutor:** a `session_end` with `data.numbers` (the ranges, each task, the kinds) and `errors` ready for
  update-db.py; the kinds `numbers_listening`, `numbers_reading`, `numbers_spelling`, `numbers_order`,
  `numbers_speaking`, `numbers_ordinal_date` (lands on datumi), `numbers_clock_time`, `numbers_clock_time_half` (on
  kdaj-cas). The counting paragraph of the bridge's guide (features/counting.ts) says how to persist them; the in-app
  tutor's topic "counting" is regenerated (tutor-export).
- **The words** are rule-built in `SpokenNumbers` (`slovene` 0 … 999 999, `ordinal` 1 … 99) plus the hours' locative;
  machine-written, flagged on the module's screen and in the tutor's data. Speech's digit conversion stays at 0 … 999.

## Files

App: `data/NumbersPractice.kt` (pure: ranges, plan, grading, schedule, wire), `data/SpokenNumbers.kt`,
`data/Models.kt` (`Exercise.Numeral`), `app/NumbersController.kt`, `ui/numbers/NumbersScreen.kt`,
`ui/numbers/NumeralEx.kt` (the pad), Home (`HomeLogic.numbersIdea`, `TodayCard`), the unit's page (`UnitScreen`),
QA's hooks (`numbers…`). Bridge: `src/game.ts` (`numbersOf`), `src/features/game.ts`, `src/features/counting.ts`,
`src/cli/tutor-export.ts`; companion/tutor (regenerated). QA: `companion/bin/qa --steps numbers`.

## What's left

- A native speaker's check of the rule-built words (hand-checked table in `NumbersPracticeTest`), the hours' forms and
  the explanations.
- Each number heard is voiced on demand by the node the first time (the same path as any line); a node with paid voices
  spends a little on each new number heard. A local voice for numbers would cost nothing.
- Prices and other modes once their pages are in the school.
