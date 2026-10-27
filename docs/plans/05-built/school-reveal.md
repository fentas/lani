# The school shows a level a bit at a time

Jan, 2026-10-27, as A2 (14 units) and B1 (16) were outlined: "no need to show it all directly". Before, the school's
screen listed every unit of every level: all 18 of A1, then A2's 14 (each planned one greyed "📝 Kmalu · Coming"), B1's
16 and B2's header, each closed level's units as 🔒 rows. A learner at A2 scrolled past 18 done rows and a column of
units they couldn't open to find what's next.

## What it does now

`School.reveal` (game/School.kt, pure) decides what the screen (ui/school/SchoolScreen.kt) shows of each level:

- **An open level**: its units done and begun, the current one (its first written unit not done: ★ in the level the
  course is at) and the next two written units not done after it (`School.AHEAD`), and any unit with the tutor's
  homework, as the rows they were (✅, ★, ▶️, ○, each with its 🖨️). A learner who jumped ahead keeps every unit done or
  begun in view. The rest folds into one quiet line under the rows, "📝 Še 9 enot v A2 · 9 more units in A2": a tap
  lists them read-only in a sheet (`LaniSheet`), each with its picture, title, where it stands ("○ Pozneje · Later", a
  planned one "📝 Kmalu · Coming", a closed level's "🔒 Zaklenjeno · Locked") and its 🖨️. A planned unit is never a row.
- **A level done** (every written unit done): its rows behind one line, "✅ 18 opravljenih enot · 18 units done", which
  shows them on a tap (to open one again: a unit done stays open for its intro, sheets and homework) and hides them on
  the next; its planned units, if any, folded as above.
- **The first closed level** after the open ones: its header (🔒), what opens it with each way on (the gate card, as
  before), and every unit folded ("📝 14 enot v A2 · 14 units in A2"). The levels after it aren't shown until it opens.
- Each level shown keeps its booklet, "🖨️ Vsi listi · All sheets".

**The opening rules are unchanged** (`School.isOpen`, `School.needs`, `School.opened`): A1 opens with the hamlet (the class
begins); A2 with the school standing and every written A1 unit done (each unit is done when its test is passed, so: A1's
tests passed); B1 and B2 with the school's levels 2 and 3 (whose upgrades wait for A2's and B1's units) and the level
before done; a level once open stays open, also when units are added to the one before; planned units hold nothing back.
They were right for "a new level appears when the previous one's test is passed", so the reveal only changes what is
shown.

**The current one** is the level's first written unit not done, in the course's order. On this branch A2 begins with six
planned units (the cases, written elsewhere), so its current one is the first written one after them (mnozina-skloni);
the planned ones before it are in the folded list, in order.

**TalkBack**: the folded line is a button that reads its count ("📝 Še 10 enot v A2 · 10 more units in A2"), its action
"Pokaži seznam · Show the list"; the done line a button with "Pokaži enote / Skrij enote" and its state ("prikazane /
skrite"); the sheet's title is a heading, its rows read title and status; the marks at the lines' ends are left out.

**Phone and tablet**: the lines are full width with the rows (48 dp high at least); the sheet is Material's bottom sheet
(at most 640 dp wide on a tablet, so on Teo's 2560×1600 landscape it stands centred), its list scrolls.

## How to use it

Nothing to set. The tutor and the course are unchanged: a planned unit in `course.json` shows only in its level's folded
list; writing it (removing `"planned": true`, adding its file) makes it a row once the learner gets to it.

QA: `companion/bin/qa --no-town --steps school-reveal` with `LANI_DEV_DATA` a synthetic learner (`bun
test/fixtures/make-learner.ts <dir>`): QA's hook `school:reveal` (app/QaHooks.kt `readyReveal`: the school standing, every
A1 unit done, A2's first written unit done and its second begun) and the checks in companion/README.md.

## Tests

- `game/SchoolRevealTest` (9): nothing done; A1 done and A2 begun; jumped ahead; planned units only folded; a level
  closed (before the class, A1 done without the school); all done (nothing folded); three units or fewer (nothing to
  fold) and a level with nothing written; the tutor's homework keeping a unit in view; a level kept open as units come to
  the one before.
- `app/QaHooksTest`: the hook's village (the school, A1 done and kept, A2's first done and second begun, or one begun).
- The l10n tables' tests (every key used, sl and en alike, every message parses).

## Screenshots and tap counts

The same synthetic learner (make-learner.ts, then the school standing, every A1 unit done, A2's first written unit,
mnozina-skloni, done and the second, zaimki-skloni, begun), the same course (this branch: A2's six case units still
planned, eight written), a phone of 1080×2400; `companion/bin/qa --apk <before|after>.apk --no-town --steps
school-reveal`, before with main's debug build. In the scratchpad's `ux-school-reveal/before/` and `after/`:

- before: `09-school-reveal-top` (A1's 18 done rows from the top), `10-school-reveal-a2`, `11-school-reveal-a2-rows`
  (A2's six planned rows first, then its eight written ones, B1's planned rows under its gate); the step fails where the
  fold is looked for, as expected.
- after: `09-school-reveal-top` (A1 one line, A2 at the bottom of the first screen), `11-school-reveal-a2-rows` (A2:
  mnozina-skloni ✅, zaimki-skloni ★, čigav, primernik, then "📝 Še 10 enot v A2", then B1's gate), `12-school-reveal-folded`
  (the list: six planned "Kmalu", four written "Pozneje" with 🖨️), `13-school-reveal-b1` (B1's gate and "📝 16 enot v
  B1"; no B2), `14-school-reveal-a1-done` (A1's units shown on a tap).

| To … | before | after |
|---|---|---|
| open the current A2 unit | 1 tap (★ at the top) | 1 tap (★ at the top), or its row 1 swipe down |
| see level A2 | 7 swipes | 1 swipe |
| see a planned unit's title | 7 swipes | 2 swipes and 1 tap (the folded line) |
| see what opens B1 | 8 swipes | 2 swipes |
| open a written A2 unit further on (vezniki) | 1 tap, ~8 swipes | not from the school's list until it comes into view (below) |
| open a done A1 unit again | 1 tap | 2 taps (the done line, its row) |

Teo's tablet wasn't run on the emulator: the screen is the same full-width list as before (the lines are rows like the
units'), and the folded list is Material's bottom sheet (at most 640 dp wide, centred on a wide screen).

## Strings for the native check (machine-written)

`school.foldedMore` "Še 9 enot v A2", `school.foldedAll` "14 enot v A2", `school.foldedNote` "Pokažejo se ena za drugo,
ko prideš do njih.", `school.foldedClosedNote` "Odprejo se z ravnijo A2.", `school.later` "Pozneje", `school.listUnits`
"Pokaži seznam", `school.unitsAllDone` "18 opravljenih enot" (1 opravljena enota, 2 opravljeni enoti, 3 opravljene
enote), `school.showUnits` "Pokaži enote", `school.hideUnits` "Skrij enote", `school.unitsShown` "prikazane",
`school.unitsHidden` "skrite"; and their German and Italian ("Noch 9 Einheiten in A2", "Ancora 9 unità di A2" …).

## What's left, and the risks

- A unit further on can't be opened from the school's screen until it comes into view (the folded list is read-only, as
  asked): a learner who wants to jump far ahead tests out of the units in between ("⏩ Znam že"), each bringing the next
  in. Home's "Danes · Today", the class's lesson and the tutor's links still open any unit.
- A level done costs one tap more to open one of its units again (the done line, then the row).
- The school's levels on "📊 Napredek · Progress" (ui/progress) still count every unit of a level; nothing there lists
  them all.
- A2's header says "še 6 v pripravi · 6 more coming" (planned) while its folded line says "Še 10 enot v A2" (planned and
  written further on): two counts of different things side by side.
- QA's setup failed for every step on main ("l10n() missing 1 required positional argument": the coast step's second
  `def l10n(lang, key)` replaced the table reader setup uses); fixed here with one `l10n(lang, key=None)` (b2450a66).
