# Built: small app UX leftovers (ux-leftovers)

*Built 2026-11-02, branch `ux-leftovers` (wave C, part 1: followups-8.md §1). Jan: "sure continue". Seven leftovers
from the earlier rounds, for Jan (Slovene from English, an adult, a phone) and Teo (Italian from German, the Friuli
village, a tablet of 2560 × 1600 on its side and upright). Each item was checked on main (21aa4e9b) first: one was
still open, the other six were done already.*

## What, item by item

### 1. "★ Next unit" named the next unit twice (open: fixed)

**Before.** After a passed test the button read the next unit's title as a pair in both halves of its paired label, in
every course. From course-de-a1-c's QA (a German course from Slovene): "★ Nächste Einheit: Ich möchte, ich kann · Rad
bi, znam: möchten in können · Naslednja enota: Ich möchte, ich kann · Rad bi, znam: möchten in können". The title went
into `bi()` already paired (`titleShown`), and `bi()` puts each half's sentence around the whole argument.

The unit's own main button did the same: "▶️ Naprej: Tabela: biti · Table: to be · Next: Tabela: biti · Table: to be",
and "Naprej: Uvod · Introduction · Next: Uvod · Introduction" for the intro, "Naprej: Test · Test · Next: Test · Test"
for the test.

**Now** (`ui/school/SchoolScreen.kt`: `nextUnitLabel`, `continueLabel`, `continueKeyLabel`):

- The title is an argument read in the language of the half it fills (`Localized`), as the teacher's lesson button in a
  scene already did: "★ Naslednja enota: Hrana · Next unit: Food", "▶️ Naprej: Uvod · Next: Introduction",
  "★ Prossima unità: Cibo · Nächste Einheit: Essen" for Teo.
- A title without the base's language reads in English in that half (as `titleShown` did).
- The test's finish (`SheetScreen`), the unit's page (`UnitScreen`) and the teacher's lesson in a scene (`SceneScreen`
  `lessonGo`) use the same three functions, so the three can't drift apart.

No new strings: `school.nextUnit` and `school.continueStep` are the tables' as before.

### 2. The tablet on its side: the feedback card over the sheet (done already)

Done in 669c579a (polish-app, 2026-10-30): on a screen at least 720 dp wide and wider than tall (`FeedbackPlace.beside`:
Teo's tablet on its side, a phone turned) the card goes beside the question, 40 % of the width, and the question keeps
the rest, whole. Every run of exercises has it (`ExerciseRun`: the school's sheets and tests, reviews, packs, modules).
Checked again on the emulator (QA's `feedback-tablet` and `sheet-corrections` on the tablet, below).

### 3. Back from the mill gone into at the stream (done already)

Done in 003d23b4 and 4789bafa (2026-10-27): the stream's mill's "🚪 Vstopi" opens the mill with `from = "ob-potoku"`
(`Screen.Scene.from`), which lies on the stream in the back stack, so Back (the top bar's "‹ Ob potoku" and the
system's) returns to the stream, then the village. `NavStackTest` ("a scene gone into from another's picture lies on
it…", "the mill gone into from the cart bridge's scene…") and QA's `mill` step check it.

### 4. gameIntro.arrive named Triglav in every culture (done already)

Done in f22013c8 (2026-10-30): culture.json's `intro.arrive` per culture (Friuli's Collio near Gorizia, Kärnten's lower
Gailtal under the Dobratsch, the Lake District's dale among the fells, Primorska's valley below Triglav), read by
`GameIntroSpeech.arrive`; the app's own `gameIntro.arrive` is only the fallback for a culture without one.

### 5. The founding screen and the news sheet silent (done already)

Done in dfbd0c7a (2026-10-30): both have the intro's 🔊 (`game/ReadAloud`, `ui/game/ReadAloudButton`), the language
learned in the culture's narrator's voice and the base by the phone's voice. The founding reads itself as it opens (as
the intro does, when "🔊 Beri razlago" is on and no screen reader is); the news sheet, which comes at every return, reads
on a tap only.

### 6. The Friends card's flag from the town's language (done already)

Done in 4a4b16bb (2026-10-30): `Towns.flag(culture, language)` takes the culture's flag (`MapLook.flagEmoji`: 🇦🇹 for
a Carinthian town), the language's only for a culture the app doesn't know. Every town line on Friends, a visit's bar and
a town's progress use it.

### 7. Flaky and colliding tests (done already)

- MyWordsSearchTest: done in 2ac889cf (2026-10-30). The cause was timing, not Compose's snapshot: the test now runs on
  `runTest`'s virtual time. The gate logs kept in the scratchpad show no failure of it since, and it passed in this
  branch's full suite.
- PhoneBridgeNodeTest's fixed port 19110: done in c6788a3a (2026-10-30). Every test server takes a port the system picks.

## How to use it

Nothing to do: pass a unit's test and the gold button names the next unit once in each half; a unit's page names its next
step the same way.

## Tests

- `SchoolStepLabelsTest` (new, 3 tests): the next unit's label in sl · en, it · de, de · en and en · sl; a title missing
  the base's language; the next worksheet, the intro and the test.
- QA: `next_unit_once(course, pair)` in `companion/bin/qa` checks the "★" button after a passed test is one of the
  course's units named once per half. `school-unit` (Slovene), `course-unit` (any course) and `friuli-school`'s unit
  part (Teo's Italian) call it.

## Emulator QA

Through `companion/bin/qa-slot`, synthetic learners only (make-learner.ts: Ana, Slovene from English; Lena, German from
Slovene in the Carinthian village). Screenshots in the scratchpad's `ux-ux-leftovers/`.

- **Next unit, German from Slovene** (`--steps course-unit`, `LANI_CULTURE=kaernten`, `LANI_QA_SETUP_PAIR=de-sl`,
  `LANI_QA_COURSE_UNIT=uhrzeit`, slot 2): ok. After the test: "★ Nächste Einheit: Ich möchte, ich kann · Naslednja
  enota: Rad bi, znam: möchten in können" (`after-next-unit-de-sl.png`; before, from course-de-a1-c's run:
  `before-next-unit-de-sl.png`, four lines of a button for two). The unit's page: "▶️ Weiter: Einführung · Naprej: Uvod"
  on one line (`after-unit-continue-de-sl.png`; before "Weiter: Einführung · Uvod · Naprej: Einführung · Uvod",
  `before-unit-continue-de-sl.png`).
- **Next unit, Slovene from English** (`--steps school-unit`, pogojnik-polno): ok, "★ Naslednja enota: Nihče ni prišel:
  nekdo, nič, nikjer · Next unit: Nobody came: someone, nothing, nowhere".
- **The tablet's feedback card** (`--steps feedback-tablet`): ok. On its side (2560 × 1600 at 320 dpi) the card is
  beside the question, which shows whole; upright and on the phone under it, the question's room 506 dp and 236 dp
  (`run-a/feedback-*.png`, `run-a/feedback-tablet.txt`). A checked worksheet on its side (`--steps sheet-corrections`,
  `LANI_QA_SHEET_TABLET=only`): the sheet keeps the full height left of the card, every correction at its line
  (`run-b/31…33-sheet-tablet-side*.png`).
- **The mill** (`--steps mill`): ok. Into the mill from the stream's mill, "‹ Ob potoku · By the stream" back to the
  stream, the system's Back the same, then the village (`run-b/mill-*.png`).

Three QA steps were broken on main and are fixed here (all in `companion/bin/qa`, nothing of the app):

- `mill` failed at the stream with "name 'hour' is not defined": fafddd62 gave mill-turning an hour of its own and
  changed the mill step's "hour:11" too. The mill step opens the stream at 11 again.
- `sheet-corrections` failed at its end with "name 'lab' is not defined": 50cee4d5 put a copy of the guidance step's tip
  check there. Removed; guidance keeps its own. The step passes again on the tablet.
- `school-unit` with `LANI_QA_UNIT=sedanjik` stopped at the intro's first quick question: its right option, «dela», is
  also a cell of the table above it, and `option_at` took the table's. It now looks below the prompt (`prompt_piece`), as
  friuli-school and course-unit do. Passes: "★ Naslednja enota: Množina: konji, ovce, okna · Next unit: The plural: konji,
  ovce, okna".

## Checks

Main hadn't moved from 21aa4e9b at the end, so there was nothing to merge. On the branch: tsc clean; the smoke on ports
9650–9668 all passed (3,467 ✅); `tutor-export --check` 89 files match; the Android suite through lani-slot 3,760 tests
in 483 classes, none failed (2 skipped); Python 251 and 142 tests OK (11 skipped), `companion/tests` run again after the
last QA edits.

## Tap counts

No change: the same buttons in the same places, only their text.

## What's left

- The run's own title on a school test reads "🎓 Test · Test: Um sieben, am Montag · Ura in dnevi: um sieben, am Montag"
  (`SheetScreen`: the paired word "Test" and then the paired title). Not doubled, but it could read as the others now
  do ("Test: Um sieben, am Montag · Test: Ura in dnevi: …"). QA steps look runs up by their titles, so it wants its own
  look at them.
- Machine-written text: none new (no strings changed), so nothing here needs a native check.
