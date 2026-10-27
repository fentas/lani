# Built: a worksheet's corrections at its items

Jan, 2026-10-27, on a checked sheet ("Kdo kaj dela?", sedanjik-3): "corrections would make sense at the inputs or nearby
— to have them all at the bottom makes it hard to compare". The rules as built are in companion/COURSE.md ("The school's
exercise types", "transform") and companion/README.md ("The school's course", "Checked"). This note says why it was so,
what changed, how to use it and what is left.

## Why it was so

- **Far from the item.** A checked sheet (`ui/school/SheetExercises.kt`) outlined each field in its verdict's colour and
  listed every item not right under the whole sheet ("✏️ Popravki · Corrections: 7. ~~spive v šotoru~~ → Luka in Tine
  spita v šotoru."). On a phone the list was a screen or two below the line it corrected: to compare, Jan scrolled up to
  the line and down to its correction, item by item.
- **Whole strings, no difference.** The list struck the whole answer and showed the whole right one: "~~spive v šotoru~~
  → Luka in Tine spita v šotoru." says nothing of which letters were wrong (the ending), and adds a subject Jan never
  needed to type.
- **The person in brackets was required.** The transform list asks "Povej za osebo v oklepaju · Say it for the person in
  brackets". A pronoun line accepted both ("Dela doma.", "On dela doma."), a noun line only the sentence with the noun
  ("Luka in Tine spita v šotoru."): "Spita v šotoru." was graded wrong, though it is how Slovene says it (the verb's
  ending says the person) and the line given has no subject either ("Spim v šotoru."). Eight lines of the course were so
  (sedanjik-3: 2, povratni-glagoli-3: 4, its test: 2), five more of povratni-glagoli right only in the clitic-first
  order ("Se zbudimo …" besides the "Zbudimo se …" listed), and three of dvojina's (below).
- **An almost's slip was always "Mind the č / š / ž"**, a typo's too.

## What

- **The diff** (`data/AnswerDiff.kt`, pure): what was typed set against what is right, aligned word by word (a word left
  out, one too many, another in its place: a small edit distance over words whose cost is the letters' distance), and
  within a near word letter by letter (the common start and end kept, the middle struck and replaced): "Spi~~ve~~**ta**
  v ~~s~~**š**otoru.". Compared as `Grading` compares: case, punctuation (`.,!?;:¡¿"„“”«»`) and the kind of apostrophe
  don't count, and the right answer shows as written (its capital, its full stop); a letter the keyboard lacks is a
  small difference (`Grading.fold`: č/c, ä/ae, é/e). Mostly another answer (fewer than half its words typed at least
  nearly): "~~jaz ne vem~~ → **Luka in Tine spita v šotoru.**", no patchwork. A word that may be left out and was (the
  person in brackets) is quiet (`GIVEN`).
- **On screen** (`ui/AnswerDiffText.kt`): typed and wrong struck through in the verdict's colour (red, orange for an
  almost) on a tint of it, right green and bold on a green tint, the rest plain, the person quiet. The tints came from
  QA: a single struck letter read as another ("~~c~~**č**aj" as "ečaj", "Bere~~s~~**m**" as "Beresm"). One `Text` that
  wraps; TalkBack reads it in words (`correctionSaid`: "Prav je · Right: …. Tvoj odgovor · You wrote: …"), not the
  pieces run together.
- **The sheet** (`SheetExercises.kt`): each field once checked has its mark as a small round badge on its corner
  (`VerdictBadge`: ✓, ≈, ✗; inside the field it took room from what was typed and a gap's or a cell's text was cut, seen
  in QA; TalkBack: "prav · right", "skoraj prav · almost right", "narobe · wrong") and, when not right, its correction
  right under it (`ItemCorrection`) with why (`ItemSlip`: an almost's own slip by its hint, `hintText` of the feedback;
  a wrong item's `explain`).
  - gap text: a sentence's corrections on a line of their own under the sentence (full width in the text's flow): the
    gap's number, the correction, the sentence said right (🔊);
  - table: under each cell, smaller; the row's whys under the row (the cells are narrow);
  - transform: under the line's field, aligned with what was typed;
  - sort: on the chip of a word in the wrong column, "→ ženski".
  Under the sheet only the count (`CheckedCount`): "✏️ 3 od 8 narobe, 2 skoraj · 3 of 8 wrong, 2 almost"; a tap brings
  the first item not right into view (`FirstWrong`: the whole line of a list, a gap's correction with its sentence
  above, a cell with its field), as checking does once the feedback slid in (450 ms). The feedback card ("Ne čisto ❌",
  the rule, "V knjigo", Ask, Next) is as it was.
- **Everywhere else a typed answer is corrected**, the same diff: the run's feedback under a typed answer near the right
  one ("Tvoj odgovor · You wrote: Spi~~ve~~**ta** …", where it said "Tvoj odgovor: spive …"; the right answer above
  stays tappable, its 🔊), a forms card's and a counting card's step ("✗ peč**e**"), a typed quick question of a unit's
  introduction ("~~so~~ → **je**", "~~z~~**ž**enska"). The unit's test and homework are sheets: the same screen.
- **Grading: the person in brackets** (`Sheets.withoutPerson`, `Sheets.items`): in a language that leaves its subject
  out (`drop_subject` in its grading table, `app/src/main/resources/grading/<code>.json`: Slovene and Italian; not
  German or English), a transform line with no subject of its own whose answer begins with the person in brackets
  accepts the answer without it ("Spita v šotoru."), and with the line's own order where only the person moved a word
  ("Učim se brati. (Tine)" → "Uči se brati." besides "Se uči brati.", a clitic first never wrong). Not when the line has
  a word the person replaced ("Kruh je svež. (mleko)", "One so kuhale. (onidve)") or the bracket is what the line takes
  in ("Pomagam. (lahko)", "Prideš jutri? (lahko)": without it the answer is the line's own words). A bracket that goes
  in front but isn't the person while the verb changes ("Grem domov. (jutri)" → "Jutri pojdem domov.") can't be told
  from a person: no such line in the course; such a list says `whole`. `"whole": true` on a transform (bridge
  `course.ts`, `Exercise.Transform.whole`) asks for every word, for an instruction that says "write the whole sentence".
  The answer checked without the person is set against the whole sentence, its person quiet (`Sheets.shown`): "Luka in
  Tine spi~~ve~~**ta** v šotoru.".
- **Strings** (sl, en, de, it): `school.rightIs`, `school.itemRight`, `school.itemAlmost`, `school.itemWrong`,
  `school.wrongOf`, `school.toFirstWrong`; `school.corrections` ("Popravki") is gone. Machine-written (Claude): for a
  native speaker's check (each table's `_note_inline_corrections`).

## Tap counts

To compare one wrong line with its correction on a phone, after "Preveri · Check" (1 tap, both):

| | before | after |
|---|---|---|
| a wrong line and its correction on one screen | no: a swipe up to the line, a swipe down to the list, per line | yes: under the field |
| the first wrong line | the list scrolled into view, the line a swipe or two up | brought into view at the check |
| back to the first wrong line from the end | swipes | 1 tap on the count |

## How to use it

- Check a sheet as before; read down the sheet: each line not right says what was typed against what is right under it.
- QA: `companion/bin/qa --steps sheet-corrections` plays Jan's sheet (QA's hook `sheet:sedanjik/sedanjik-3`) with three
  lines wrong, two almost (one without its person) and three right, on the phone, a tablet upright (1600 × 2560 px at
  320 dpi) and on its side (2560 × 1600); `--steps school` checks the gap text's count.
- Tests: `AnswerDiffTest` (Jan's line, the person quiet, Slovene's letters, a word left out or too many, case,
  punctuation and apostrophes as the grading has them, another word whole, nothing typed, ä and é), `SheetsTest` (the
  person in brackets: which lines, the line's order, none for German, `whole`, the correction set against the whole
  sentence), `QaHooksTest` (the hook), the bridge smoke (`whole` validates, a wrong one is refused).

## What's left

- **Content that wants both listed.** A person in brackets that *replaces* the line's subject keeps the grader strict
  (the noun is what such a line tests, "Kruh je svež. (mleko)"). Where it is a pronoun the content should list both, as
  the other lines do (COURSE.md "Several right answers"): dvojina-3 "One so kuhale. (onidve)" (add "Kuhali sta."), the
  test's "One so v kuhinji. (onidve)" (add "Sta v kuhinji." / "V kuhinji sta."). dvojina-3 "Otroci so šli domov. (Zala
  in Tine)" asks for the names (the instruction "Povej za dva · Say it for two" is about the dual): fine as it is, or
  "Šla sta domov." added if the dual alone is what it tests.
- The diff compares words in order: a word moved ("v šotoru spita") shows as one struck and one put in, not as moved.
- A table cell's correction wraps inside the cell on a narrow phone ("~~ste~~ → / **sta**"): readable, not pretty.
