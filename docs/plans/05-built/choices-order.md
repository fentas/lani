# Built: a choice's options in an order of their own (choices-order, 2026-11-03)

A course's or a module's `choice` showed its options in the file's order, and the writers nearly always put the right one
first. Share with `answer: 0` on main: Slovene B2 125/125, Slovene B1 81/101, Italian B1 164/165, Italian A2 102/139, English
A1 144/145, German A1 111/138. A learner could pass by always tapping the top option. Fixed in the code, so every existing
and future file benefits; no content changed.

## What the learner sees

The same choice, its options in another order. It stays the same all day and changes the next day. The right answer stands
first as often as anywhere else: about one in three on three options (a shuffle that avoided the top would teach "never the
first"). No tap counts change.

## How

`data/ChoiceOrder.kt`: `of(n, question, day)` gives where each row from the top is in the file. It is only a display order:
each row's index stays the file's, so `answer`, `almost`, `answers`, `Grading.picked`, what an `Outcome` says (the text),
what a school result stores, `about_exercise` (its `options`, the file's order) and what goes to the bridge and the tutor are
as they were. Older apps keep their order. Nothing changes in the data or at the bridge's API.

**The seed: the question and the day**, as `FormsCardController`'s forms cards ("shuffled the same way through the day").
- It holds while the learner works with nothing kept anywhere: a screen turned (the activity keeps its composition), going back
  a step in an intro, leaving a sheet and coming back, the process killed. The day itself is held by `rememberSaveable` while
  the question is on screen, so midnight doesn't reshuffle what's being read.
- Tomorrow's order is another, so "the third one" isn't learned for life.
- The question is its prompt and its right answer (`ChoiceOrder.question`), not its wrong ones: the learner's own trap
  (`Adaptive.choice`) may replace a wrong one between two visits, and the rows stay.
- What it doesn't do: a failed test retaken the same day has the same order, as a forms card has. Position is then one more
  thing to remember for a few hours, not for life. A seed per attempt would need the attempt kept through a process's death;
  ask if this shows up.

## Every place a choice's options reach the learner

| Where | Before | Now |
|---|---|---|
| `ui/PlayerScreen.kt` `ChoiceEx`: the school's worksheets and tests, modules, a page's practice, runs, requests, reviews, a scenario with options | the exercise's order | **the day's order** |
| `ui/PlayerScreen.kt` `MultiEx`: same fault (`[0, 1, 2]`, or right/wrong pairs with the right one first) | the file's order | **the day's order**; what's ticked is still told in the file's order |
| `ui/school/IntroScreen.kt` `IntroAsk`: an intro's quick questions | the file's order | **the day's order** |
| `companion/prints/sheet.ts` `chooseCheck`: "Preveri se · Check yourself" (`a / b / c`) | the file's order (right first: en 31/31, de 29/32, it 98/120, sl 23/45) | **the sheet's own order** (en 7/31, de 15/32, it 46/120, sl 14/45) |
| Generated choices that also pass `ChoiceEx`: `ReviewPlanner`, `Content.listen`, `TownActs`, `Calendar`, `Surprises`, `TentMove`, `Treasure`'s | shuffled | their code as it was; one more fixed shuffle changes nothing about them |
| `Treasure`'s letter, true or false ("Drži / Ne drži") | fixed | **kept fixed** (`Exercise.Choice.fixedOrder`, a Kotlin field never read from a file) |
| `DialogRun.choices`: dialogs' chosen turns | shuffled per talk (a random seed in every `DialogRun.start`) | as it was |
| "✋ Izberi raje · Let me choose" under an adaptive choice typed or said | through `ChoiceEx` | the day's order |
| `data/CaseTrainer.kt` + `ui/cases/CaseEx.kt`: "Kje ali kam?" | shuffled per item | as it was |
| `app/FormsCardController.kt` + `ui/FormsCardView.kt`: forms cards | shuffled through the day | as it was |
| `road/RoadQuiz.kt` `order`: the car's quiz | shuffled per day | as it was (the right one is index 0 only inside the question) |
| `ui/game/ReadingLogic.kt` + `ReadingSheet`: a reading's questions | shuffled per reading; true or false fixed | as it was |
| `game/scene/StoryBooks.kt` + `ui/notebook/NotebookQuiz.kt`: a book's questions | shuffled | as it was |
| `data/Counting.kt` + `ui/CountingCard.kt`: counting cards | step 1 en / ena / eno fixed (a paradigm whose right one varies), the others shuffled | as it was |
| `ui/PlayerScreen.kt` `ReorderEx`, `data/Sheets.kt` `sortWords`, `bank` | shuffled | as it was |

The car's dialogs (`RoadPlay`) only play the right answer, so nothing there offers options.

## An order that means something

Read: all 1,099 `choice` and 12 `multi` of `companion/course` and `companion/modules` (589 lists of single words, 426 of
phrases, the 12 multis, each by eye), and their prompts, explanations and instructions searched for a place ("the second one",
"above", "prva možnost", "oben", "sopra" as a reference). **No list has one:** no scale, no "both of the above", no numbered or
sorted set (`7:30 / 6:30 / 8:30` isn't sorted; `moški / ženski / srednji` has answers all through it and reads as well in any
order), no text that names a place. The hits of "above" and "all of them" are words of the exercise ("sotto il tavolo", "druga
beseda" = another word). `multi`s with right/wrong pairs (`Nimam avta. / Nimam avto.`) lose the pair standing together, which
only makes them harder to pass by pattern.
So the format stays as it is. The one fixed order is the app's own: a letter's true or false. A culture's riddle with
"all of them / none of them" (`cultures/lakeland/surprises.json`) was already shuffled and reads alone.

## The print

`printedOrder(options, seed)` in `companion/prints/sheet.ts`: FNV-1a and mulberry32, no `Bun.hash` (the bridge also runs on the
phone), seeded by the unit's id and the question's prompt in the file. No date and no learner: every print of one sheet has one
order, the booklet's the same as the single sheet's, and the bridge's page stays byte for byte the generator's. The answers at
the foot name the right one by its text, so they can't disagree. Also: the options through `fillLearner` as the prompt is, so a
`{m:…|f:…}` option couldn't reach the paper raw (none does today).

## Checks

- `ui/ChoiceOrderTest` (Robolectric, the real composables): the rows from the top are the day's order, not the file's; the right
  answer tapped in a reordered list is right; a near miss stays almost; another option is wrong; a right answer that isn't the
  file's first; a multi by its texts, told in the file's order; true or false keeps its order; the state restored keeps the order;
  no file can ask for the fixed order. Over all four courses: each order a permutation, the right answer in each place about as
  often, none stuck in one place for a fortnight.
- `test/smoke/prints.ts`: in all four languages each printed choice's key among its options, right first well under the files'
  share, the same order on every build.
- `companion/bin/qa-slot --steps choices-order`: a unit's intro, first worksheet and test tapped by the options' texts, each
  choice's rows from the top to `choices-order.txt`, a shot of a right answer that isn't on top before and after its tap; the unit's
  print from the dev bridge against its key. QA's other steps already tap by text; `answer_any`, the forms' first option and the
  culture run's fallback tap whichever is first and don't need it right.
- Run 2026-11-03 on `pogojnik-polno` (B1, a synthetic learner): 8 choices, 6 with the right answer not on top, each tapped there and
  right, the unit passed and kept done by the dev bridge. Run again after merging main, 30 minutes later on a reinstalled app: the same
  rows. `biti`'s PDF by eye: "Vidva ___ prijatelja. (ste / sva / sta)", key "4 sta"; "Mi ___ pri lipi. (smo / so / sva)", key "5 smo".

## What's left

- Nothing to write or say: no strings, no voice clips (0 characters), no native check.
- Writers may stop thinking about order; they don't have to change anything.
