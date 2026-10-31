# Built: the Italian school's first six A2 units (2026-10-31)

Teo (a child of about seven, learning Italian from German, in the Friuli village; a tablet) has A1's 18 units, and A2
was outlined on 2026-10-31 with 18 units, every one planned: once A1 was done, Maestra Elena only said she was preparing
the next lessons. Three branches write A2 side by side, in the outline's order: this one (`course-it-a2a`) units 1–6,
`course-it-a2b` units 7–12, `course-it-a2c` units 13–18. The format and how a unit is written are companion/COURSE.md
("Writing a unit", "The Italian course", and "The Italian course: A2, units 1–6" for these).

## The units

Each has a guided intro of five or six steps (Maestra Elena's lines in German first with du, then Slovene and English;
every Italian form in bold for her voice, its meaning in brackets after it; two examples a step, the table growing row
by row, a quick question each), three worksheets from easy to harder (the forms in a table or a sort, then a gap text
with a word bank, then a transform list), and a test at pass 80 with new sentences. They assume A1 (all 18 units) and
the A2 units before them in the outline, nothing after: no pronoun lo, la or gli, no ne, no mai or niente, no vorrei, no
relative che and no imperfetto or future around a gap.

| # | unit | intro | examples | worksheets (items) | test |
|---|---|---|---|---|---|
| 1 | `riflessivi` 🧼 Mi lavo, mi vesto | 5 steps + its own summary | 10 | table + sort (15) · gap text with a bank + choice (7) · 2 transforms (8): 30 | 12 items |
| 2 | `modali` 🙋 Posso, voglio, devo | 5 | 10 | table + 2 choices (14) · gap text with a bank + choice (8) · transform (7): 29 | 12 |
| 3 | `stare-gerundio` 🏃 Sto giocando! | 5 | 10 | table + sort (16) · gap text with a bank + choice (7) · transform (7): 30 | 12 |
| 4 | `partitivo` 🧺 Del pane, delle mele | 6 | 12 | table + sort (14) · gap text with a bank + choice (7) · 2 transforms (9): 30 | 12 |
| 5 | `comparativi` ⚖️ Più grande di te | 5 | 10 | table + sort (13) · gap text with a bank + choice (7) · transform (7): 27 | 11 |
| 6 | `superlativi` 🏆 Il più alto, altissimo | 6 | 12 | table + 2 choices (12) · gap text with a bank + choice (7) · 2 transforms (8): 27 | 12 |

- **riflessivi**: mi lavo, ti lavi, si lava (Mi chiamo Giulia: the one the child knows), ci laviamo … si lavano with si
  twice; the -si of the dictionary and the verbs reflexive only in Italian (alzarsi, svegliarsi, addormentarsi); non
  before the little word; mi lavo le mani with the article. The gap text: Lorenzo's morning at the mill; the test's: the
  evening at the fire. Its intro ends on a summary of its own (`intro.summary`): the page goes on to the modals.
- **modali**: potere for may and can (Posso andare in bagno?), dovere, volere with the infinitive right after it (not at
  the end as in German), sapere for a can that is learned (Sai nuotare? Sì, ho imparato al mare!), non before the modal,
  devo lavarmi and mi devo lavare. The gap text: Giulia's gubana day with Tommaso; the test's: the bonfire.
- **stare-gerundio**: stare + -ando, -endo, the gerund that never changes, facendo, dicendo, bevendo and "Che cosa stai
  facendo?", right now against every day (Ogni sera Nonno Bepi racconta), mi sto lavando i denti. The gap text: noon in
  the village (Bruno at his iron, Davide's sheep coming home); the test's: a rainy afternoon at home.
- **partitivo**: del and della as di + the article the child knows from preposizioni (la casa del nonno), dello, dell',
  dei, degli, delle, un po' di (un po' d'acqua), nothing after non (non c'è pane), qualche with the singular. The gap
  text: Nonna Rosa and Giulia at the market; the test's: Lorenzo's snack.
- **comparativi**: più … di (Tommaso, nine, è più grande di Giulia, eight), di with the article and di me, di te, meno …
  di, come and quanto (La pecora Bianca è bianca come la neve), migliore with a thing against meglio with a verb (Giulia
  legge meglio di Tommaso: the cast's own fact), peggiore and peggio named. The gap text: who is bigger among the
  children.
- **superlativi**: il più piccolo, the group with di (il più vecchio del paese: Nonno Bepi), the noun inside (l'albero
  più vecchio della piazza), -issimo with its agreement and its h (stanchissimi), il migliore (la gubana di Nonna Rosa;
  l'Udinese for Tommaso). The gap text: the village festa and its greasy pole.

## The grammar pages

New A2 pages of the Italian book, each its unit's summary card and first cheat-sheet card, in it, sl, en and de:
`riflessivi`, `modali`, `stare-gerundio`, `comparativi`, `superlativi` (a table and seven examples each). `partitivo`
(A2 already) has qualche + the singular and alcuni, alcune now, with an example: the unit's last step. The outline's
entries name the pages and no longer say planned. The book has 23 Italian pages (the smoke and `GrammarTest` ask for
18 or more).

## Texts in the child's language, the read-aloud, the grading

- Every text the child reads beside the Italian is per base, German first (`explain`, `hint`, `instruction`,
  `translation`, `prompt`, a table's `columns`); a hint of forms only ("tu → ti …", "-ere → -endo") stays a string.
- **Read aloud**: every bold Italian form of an explanation is read in Elena's voice when the explanation's one clip
  can't be had: each is a word the intro itself shows (its examples, rows, questions, the unit's `words`) or reads as
  Italian; only endings (-issimo) and the letter h are the phone's German voice. Her lines for a learner of German: 313,
  none of them German (the smoke's course-speech check, run on the six).
- **The grading** needed no change for these units (`grading/it.json`): every right answer is graded right, an accent
  or an apostrophe forgotten stays almost (può, dell'acqua, un po'), no word-bank distractor is right for any gap.
  Several right answers are all listed: devo lavarmi and mi devo lavare, mi sto lavando and sto lavandomi, come and
  quanto, migliore and, for food, the spoken più buono, più buona; a transform's person in brackets may be left out
  (`drop_subject`).

## The prints

`companion/bin/school-prints --language it --level A2 --base de` (and `sl`, `en`): every sheet fits, five on one page
and superlativi on two; the booklet has 9 pages. A step's explanation prints as its first sentence and the next ones
with forms, about 200 characters at most (`condense`), so four steps were rewritten to put their point first (riflessivi
3, modali 5, comparativi 5, superlativi 5). Words without a pack's meaning (stare, grande, lo zucchero …) are left out of
a sheet's words, as A1's are.

## A second read

A reviewer agent (read only, the files and COURSE.md, not this branch's reasoning) read every item fresh. Fixed from its
report before the merge: right answers that were marked wrong (the gerund lists' cue "adesso", which a child puts at the
end too; the partitivo and superlativi tests' gap texts saying only "Completa", so lo zucchero or molto caldo were right;
"Lorenzo è ___ piccolo di tutti", where più fits too); choices where another option was Italian too (Lorenzo mi veste,
Maestra, può …?, il miglior giocatore; the partitivo cloze that took lo); the cast (Moro is Nonno Bepi's old dog, not the
fastest; Lorenzo goes to the field with Franco; a miller without flour was a riddle); hints that gave the form away;
one gap a sentence; la domenica for every Sunday; Slovene enako visoka, najboljša v razredu; meanings for bold forms; the
worksheets at 30 items. Its notes on the cast held: ages, Rosa older than Nives, Bepi the oldest, Giulia reading better
than Tommaso.

## Checks

- **The bridge**: `bunx tsc --noEmit -p .` clean; the smoke (`LANI_SMOKE_PORT=9210 LANI_SMOKE_DRAMA3_PORT=9228`):
  3154 checks, all passed. Its course section validates the outline and every unit of `course/it` (the six in their
  bases), its grammar section every Italian page in sl, en and de, its course-speech section Elena's lines (none German).
  Its Friuli check of `GET /course` asked for A1's written units only and for the planned ones after them: now every
  level's written units in the outline's order, each served (A2 is written in parts side by side, so its written units
  needn't be its first).
- `tutor-export --check`: 88 files match.
- **The app**: `ItalianCourseContentTest` 5/5 (every unit parsed and played at A2, every right answer graded right by the
  Italian grading, no distractor right, every text per base in de, sl, en), `SchoolBoardTest` 5/5 (the board writes
  every letter of the six titles and forms), `GrammarTest` 16, `CourseTest` 8, `CourseContentTest` 4,
  `SchoolRevealTest` 10. The full suite (`testDebugUnitTest` through lani-slot.sh): 3519 tests in 445 classes, none failed
  (2 skipped).
- **Python**: `tests` 251 OK, `companion/tests` 135 OK (11 skipped).
- **The prints**: in de, sl and en, every sheet fits (above); the app's print of riflessivi in QA fitted as Chromium
  does.

## QA (emulator, `companion/bin/qa-slot`)

A synthetic Teo (`make-learner.ts --name Teo --target it --base de`, `LANI_CULTURE=friuli`, `LANI_CHILD=1`,
`LANI_QA_SETUP_PAIR=it-de`), `LANI_QA_IT_UNIT=riflessivi companion/bin/qa-slot --no-town --steps friuli-school-unit`:
the step played only ciao before and knew no table and no print; now it plays any Italian unit (`$LANI_QA_IT_UNIT`), an
A2 one after the school with A1 done, fills a table's cells, picks a choice the app asks typed (once a rule is secure,
`game/Adaptive`: "✋ Fammi scegliere"), and prints the unit's sheet from the unit.

- Run 1 stopped at the test's fourth item, «non si alza», asked typed: the step fixed (above).
- Run 2, green: the school with A1 done (18/18, one line) and A2 shown (riflessivi done, modali begun, 15 more folded);
  riflessivi from A2 as never begun; its intro's five steps (three choices, a gap typed), on the tablet both ways; its
  own summary card; worksheet 1 (the table of alzarsi and svegliarsi, the sort) 15/15; the test straight away, 12/12,
  "Unità completata!"; the dev bridge keeping it/riflessivi done; "🖨️ Stampa la scheda": fit.js in the WebView as in
  Chromium (one page, the table beside the rule), the dialog, the PDF "Lani – A2 – 01 riflessivi" saved, A4, one page.
- Shots: scratchpad `ux-course-it-a2a/fs/` (fs-*.png), `qa-run2/` (the run and its PDF), `prints/` (the sheets and
  booklets in de, sl, en with previews).

Tap counts: no screen changed; a unit is reached as any (Home's school tile, the unit's row: two taps), and a sheet
prints with one tap on the unit as before.

## For the native check

Every unit and page says it is machine-written (`review`). What most needs an Italian speaker's eye (and a German one's
for the child's German):

- riflessivi: le mani with the article (le tue mani a wrong option); si addormenta; ci divertiamo al mulino.
- modali: sapere against potere for a child (Sai andare in bicicletta? Sì, ho imparato quest'estate!); devo alzarmi and
  mi devo alzare; Frau Lehrerin in the German for Maestra.
- stare-gerundio: sto lavandomi beside mi sto lavando; fando as a made-up wrong option; Bruno sta battendo il ferro.
- partitivo: Oggi Franco non ha farina; qualche + the singular for a child; delle uova.
- comparativi: più grande for older; meno grande; più buona beside migliore; Il Monte Sabotino.
- superlativi: la più buona beside la migliore; il più buono as wrong for a player; l'albero della cuccagna; Udinese.
- The German, Slovene and English of every `say`, explanation and translation are machine-written too.

## What's left

- A2's units 7–18 are course-it-a2b's and course-it-a2c's; the outline's entries of each part flip on their own branch.
- The print's rule line takes a step's first sentence; a unit writer who puts the point later loses it on the sheet. A
  check in the smoke (the point's forms present in the printed line) would catch it; not added.
- superlativi prints on two pages (its table of 13 rows); the others on one.
- The read-aloud's fallback (when the explanation's one clip can't be had) reads the endings -ando, -endo, -issimo and
  the letter h with the phone's German voice; every bold Italian form is Elena's.
- In the explanation bubble, a bold form the intro's words make Italian (si lava) isn't underlined for a tap as the
  forms the app's own check knows are (mi lavo): seen in QA, the app's, not this content's.

