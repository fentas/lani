# Built: B2 of the Slovene school, units 13–18 (sl-b2-c)

*Built 2026-11-01, branch `sl-b2-c`. Jan: "can you make app complete - all what is missing?" The outline
(companion/course/sl/course.json) planned B2 as 18 units; three branches write six each (`sl-b2-a`: 1–6, `sl-b2-b`:
7–12). This branch writes the last six. The format and how a unit is written: companion/COURSE.md. Everything here is
machine-written (Claude) in Slovene, English, German and Italian, and waits for a native speaker's check (below).*

## What

Six units of B2, each a guided intro (Mojca's steps with their quick questions, the growing table), three worksheets
and a test, built on A1–B1 and set in the village's adult life: the autumn fair, the feast under the linden, Tone's
horse from the fair, France's letter to the mayor about the bridge, Ančka's letter to Vida, the grape harvest.

| unit | title | its pages | steps | examples | worksheets (items) | test | words | spoken forms |
|---|---|---|---|---|---|---|---|---|
| `stopnjevanje-napredno` | Tako lep kot, prelep: stopnjevanje | stopnjevanje, primernik | 6 | 12 | table + sort (15) · gap text with a bank (8) · 2 transform lists (7) | 12 | 29 | 2 |
| `velelnik-napredno` | Pojdiva, pojdimo, ne zapiraj | ukazi, velelnik, glagolski-vid | 6 | 12 | table + sort (15) · gap text with a bank (8) · 2 transform lists (7) | 12 | 30 | 4 |
| `frazeologija` | Imeti srečo, držati pesti: frazemi | frazemi | 6 | 12 | sort + multi + choice (14) · gap text with a bank (8) · 2 transform lists (8) | 11 | 25 | 2 |
| `pisni-jezik` | Spoštovani: uradno pismo | uradno-pismo, ti-vi | 6 | 12 | sort + table (14) · gap text with a bank (8) · 2 transform lists (8) | 12 | 26 | 12 |
| `govorjeni-jezik` | Bom šu, a si: govorjeni jezik | govorjeni-jezik | 6 | 12 | sort + table (14) · gap text with a bank (8) · transform + choice (7) | 11 | 24 | 32 |
| `besedni-red` | Kaj stoji spredaj: besedni red | besedni-red, naslonke-red | 5 | 10 | 6 reorders + 2 choices (8) · gap text with a bank (7) · transform + choice (7) | 11 | 23 | 0 |

Every test passes at 80. Each unit's first gap text has a word bank, the others and the test none.

- **stopnjevanje-napredno**: tako … kot, ne tako … kot, enako; veliko/precej/še/malo + the comparative; čedalje/vse +
  the comparative; čim prej, kar se da; bolj ko …, bolj … and the written čim …, tem …; naj- + izmed/od; pre- for too
  against zelo; bolje, slabše, več, manj, raje/rajši.
- **velelnik-napredno**: the first person (pojdiva, pojdimo, recimo, dajmo), naj + the present for someone else and a
  wish passed on, a negative command imperfective (ne zapiraj; ne pozabi for a mishap), nikar, kar and le, the
  imperatives to learn (pojdi, pridi, jej, daj, vedi, reci, povej, vzemi), polite requests (Bi mi lahko …?).
- **frazeologija**: about twenty everyday idioms by what they say (luck and joy, annoyance, haste and ease, fooling and
  guilt, failure, talk); an idiom is never taken word by word, but its verb conjugates and its person takes its case.
- **pisni-jezik**: the salutation and its comma with a small letter after it, Vi to one person with the plural, the
  formulae of a letter, the written style's nouns, formal against informal.
- **govorjeni-jezik**: recognising spoken Slovene and writing its standard: a for ali, the short infinitive (and the
  supine, standard after iti), l said u (šu, vidu, reku), the shorter words (tud, zdej, kva, mam), bojo and grejo, ta
  as an article, the words the dictionary marks spoken (fajn, ful, flaša, luštno), a ne?. It marks no spoken form
  wrong: its quick questions ask for meaning or a standard form, never offering a spoken one as a wrong option.
- **besedni-red**: the known first and the news last, the short words second whatever leads, a contrast first with pa,
  tudi and samo before what they are about, a clause last and the question word first. An answer with the other focus
  ("Micka je spekla potico" for "Who baked it?") is `almost`: a real sentence that answers another question.

**New grammar pages** (companion/grammar/sl/, B2, each with its cheat-sheet card): `stopnjevanje`, `ukazi`, `frazemi`,
`uradno-pismo`, `govorjeni-jezik`, `besedni-red`. Each unit names its page first (the summary card); the earlier pages
it builds on follow (primernik, velelnik, glagolski-vid, ti-vi, naslonke-red).

**The outline**: the six entries are written, their pages named, `planned` gone.

**What a unit turns on** (COURSE.md): `stopnjevanje-napredno` the adjective's comparative (as `primernik`),
`velelnik-napredno` the imperative (as `velelnik`); the other four no form of a table.

## Spoken forms on a sheet (the grading B2 needed)

Jan's household: the learner's wife is a native speaker and says what people say. Until now a worksheet could only put
a spoken form in `accept`, where it was right but silent, not told apart from the standard. Now a gap, a table's cell
and a transform's line take `spoken` (COURSE.md "Spoken forms"):

- typed or tapped, a spoken form is right, with a quiet line under it: "🗣️ Pogovorno, prav tako prav; knjižno: bodo ·
  Spoken, and right too; the standard form: bodo" (the case trainer's label, in sl, en, de, it); a spoken form a č
  short is almost, labelled the same;
- the standard (`accept`'s first) stays the answer: the correction, the sentence said right, the prints;
- a spoken form is never a distractor (the bridge refuses one), never a choice's wrong or `almost` option;
- a line with two spoken words lists each mix (A si že jedel?, Ali si že jedu?), so a half-standard answer is right.

Code: the bridge's `course.ts` (the key, strict sheets; a distractor that is a spoken form refused), the app's
`Gap`/`TableCell`/`TransformLine` (Models.kt), `SheetItem.spoken`, `Sheets.check` (`ItemMark.standard`), the label in
`SheetExercises.kt`. An older bridge refuses the key: **the bridge goes out before these units** (the running bridge
reads merged content at once).

Used in the units: bolši and najbolši beside boljši; gremo and greva beside pojdimo and pojdiva; "Držim vam pesti"
beside "Držim pesti za vas"; the half-polite agreement (ste prejel, ste prejela, ste bila, poslala) beside the plural;
govorjeni-jezik's a, bojo, grejo, mam, šu, vidu, tud, kva, zdej, flašo, ful and its spoken lines.

**The forms book** (lexicon/sl.forms.json): šu and reku, the spoken šel and rekel govorjeni-jezik teaches, are
accepted in iti's and reči's past (`also`), never shown; neither is another slot's form.

## Also changed

- **QA**: "school-unit" with `LANI_QA_SPOKEN=1` types a spoken form where an item lists one and checks its label;
  QA's hook "school:<unit>" opens and completes the levels below the unit's (a B2 unit: A1 to B1 done, as Jan's school
  will be), `QaHooks.readyUnit`'s `below`; QaHooksTest.
- Three B2 units leave `sejem` out of their `words`: the dictionary reads it as sejati's "I sow" (the prints'
  "Besede" said so), as course-b1a found.
- Docs: COURSE.md ("Spoken forms", the field lists, the B2 rows), the studio's module-spec.md, README.md (the
  sheet's spoken label, the forms book's spoken l-forms).

## How to use

The units are in the school's B2 level ("Raven B2"), which opens with the school's third level and B1 done (GAME.md,
"The school"). Prints: `companion/bin/school-prints --level B2 --units stopnjevanje-napredno,velelnik-napredno,frazeologija,pisni-jezik,govorjeni-jezik,besedni-red`.
QA, on a synthetic learner:

```
LANI_DEV_DATA=$(cd companion/bridge && bun test/fixtures/make-learner.ts /tmp/qa-b2/data) LANI_QA_UNIT=govorjeni-jezik \
  LANI_QA_SPOKEN=1 companion/bin/qa-slot --no-town --steps school-unit
```

## Tests

- **Bridge**: `tsc` clean; the smoke on this branch's ports (`LANI_SMOKE_PORT=9510 LANI_SMOKE_DRAMA3_PORT=9528`): all
  3,255 checks pass, among them the course section (every unit validates; 471 answers are the dictionary's forms;
  COURSE.md's turns-on table, 42 rows), the two new ones (spoken forms validate on a gap, a cell and a line; a
  distractor that is a spoken form is refused), course-speech (never an English word in Mojca's voice) and the forms
  book. `tutor-export --check`: 89 files match.
- **Android**: `CourseContentTest` (every right answer and every spoken one graded right, the spoken one labelled with
  the standard; no distractor right), `SheetsTest` (15, the spoken label new), `SchoolBoardTest` (every B2 title and
  table form in chalk), `GrammarTest`, `IntroSpeechParityTest`, `CheatSheetTest`, `GrammarBookTest`, `CourseTest`,
  `PrintsTest`, `FormsCardTest`, `SchoolTest`, `QaHooksTest` (24, the levels below a B2 unit new): pass; the full unit
  suite through lani-slot.sh: 3,629 tests in 462 classes, none failed (2 skipped).
- **Python**: `tests` 251 and `companion/tests` 138 (11 skipped), green.
- **Prints** (`school-prints --level B2 --units …`, English, Chromium): every unit on two pages (stopnjevanje-napredno,
  velelnik-napredno and pisni-jezik a bit tighter), the booklet of the six 14 pages; previews in the scratchpad's
  `ux-sl-b2-c/prints-all/`.
- **Emulator QA** (`qa-slot --no-town --steps school-unit`, a synthetic learner Ana, `LANI_QA_UNIT=govorjeni-jezik
  LANI_QA_SPOKEN=1 LANI_QA_UNIT_PRINT=1`): the school with A1 to B1 done, B2 open; govorjeni-jezik from its page through
  its six intro steps, the summary card, the first worksheet (the sort; the table typed in spoken forms: bojo, grejo,
  mam … each ✓ with "🗣️ Pogovorno, prav tako prav; knjižno: bodo"), the test straight away with spoken answers (passed
  10/11: adb types no š, so "su" for šu is almost, labelled too), Mojca's cheer, the unit kept done by the dev bridge,
  then its sheet in Android's print dialog (two pages, A4). 28 screenshots and the cheer's video in the scratchpad's
  `ux-sl-b2-c/qa-govorjeni-jezik/`.

**Tap counts.** Before: B2's six units were planned, folded as "📝 Kmalu · Coming", not to open. After: from the
school's levels, a unit is one tap (its row under "Raven B2"), its intro one more ("Naprej: Uvod"); a spoken answer's
label costs no tap (it shows under the item once the sheet is checked).

## Voice

Mojca's Slovene of the six units (the course-speech port, English base): 345 texts, 7,048 characters; the English
explanations, apart and on demand, 41 texts and 10,451 characters. Nothing was voiced.

## Sources

Settled through a polite cached fetcher (one request at a time, 4 s apart; about 85 requests in all): fran.si's SP2001
(`FilteredDictionaryIds=134`), SSKJ² (133), Keber's Slovar slovenskih frazemov (192, 216), and the Jezikovna
svetovalnica. What each settled:

- SSKJ² **a** 4, člen., pog.: "uvaja vprašanje; ali: a si ga videl? si lačen, a ne?" (https://fran.si/iskanje?FilteredDictionaryIds=133&View=1&Query=a)
- SSKJ² **fajn**, **ful**, **flaša**, **lušten**: all pog. (https://fran.si/iskanje?FilteredDictionaryIds=133&View=1&Query=fajn, …&Query=ful, …&Query=fla%C5%A1a, …&Query=lu%C5%A1ten); glih and cajt are in neither SP2001 nor SSKJ², so the unit doesn't teach them.
- SP2001 **kajne** (https://fran.si/iskanje?FilteredDictionaryIds=134&View=1&Query=kajne); **usesti se** standard (…&Query=usesti).
- SP2001 **čim** with the comparative, written apart, and čim … tem (https://fran.si/iskanje?FilteredDictionaryIds=134&View=1&Query=%C4%8Dim); **rajši** standard (…&Query=raj%C5%A1i); **bolši** in neither dictionary (spoken).
- SSKJ² **bolj**: bolj ko …, bolj …; bolj in bolj marked ●, sent to čedalje bolj (named as heard, never graded wrong) (https://fran.si/iskanje?FilteredDictionaryIds=133&View=1&Query=bolj); **prelep** (…&Query=prelep); **nikar** with a negated imperative (…&Query=nikar); **dati**: pog. dajmo + infinitive (…&Query=dati).
- Keber's phrasebook for every idiom (https://fran.si/iskanje?FilteredDictionaryIds=192&FilteredDictionaryIds=216&View=1&Query=<headword>): živec, pest, vrat, maslo, migniti, nos, maček, nebesa (devetih and sedmih both), voda, muha, zvon, puška, rak, mula, trn, rokav, polh, jezik; SSKJ² pest ("držati, tiščati pesti za koga") and smola.
- Jezikovna svetovalnica 1209 (Dobrovoljc 2016): the salutation's comma, a small letter after it (https://svetovalnica.zrc-sazu.si/topic/1209); 1261 (Snoj 2016): vikanje takes the masculine plural, polvikanje is spoken (https://svetovalnica.zrc-sazu.si/topic/1261).

## For the native check

Every unit's `review` names its doubtful spots. What most needs Jan's wife's eye:

- **govorjeni-jezik**: whether the spoken forms are hers (central Slovene: šu, vidu, reku, zdej, kva, tud, mam, mate);
  *Daj mi ta veliko flašo*, *Bom šu domov* (and *Šu bom domov*), Ančka's letter. Regions differ: add hers as `spoken`.
- **besedni-red**: every order the reorders accept (*Ključ je Luka dal Micki*, *Luka je ključ dal Micki*, *Na ples je
  Zala šla s Tinetom*), *Tone pa med*, *Kdaj nam prideš na obisk?*, the other focus as `almost`.
- **stopnjevanje-napredno**: "Na jesenskem sejmu" (*konja prodati čim dražje*, *Ta konj je prestar za delo*, *pozneje
  kot kdajkoli*), *Več ko vadita* beside *Bolj ko vadita*, bolši/najbolši, how bolj in bolj is named.
- **velelnik-napredno**: "Pred praznikom pod lipo" (*Dajmo, pospravimo trg*, *Kar sedi in počivaj*), *Jejva zunaj*,
  *Naj počaka Nejc pri vratih*, naj grejo, *Nikar ne hiti*, *Kar vzemi svetilko*.
- **frazeologija**: "Konj s sejma" (*spi kot polh*, *ti pa me vlečeš za nos*), *Držim vam pesti* (spoken, accepted),
  *v devetih* against *v sedmih nebesih*, *Ves trud je šel rakom žvižgat*, *stresa zgodbe iz rokava*; the German and
  Italian equivalents.
- **pisni-jezik**: the polite capital Vi (no source found: taught as "often"), *Pozdravljeni, gospa Novak,*, *vozovi ne
  morejo čezenj*, *Prosim Vas za hitro popravilo*, *Pišem zaradi prijave na tečaj*, whether *Dragi župan!* and *Lp,*
  are fair as `almost`.
- The six pages' rules and tables, in all three bases.

## What's left

- **The dictionary lacks some of the units' words** (the prints leave them out of "Besede"): precej, čedalje, izmed,
  preveč, prevelik, predrag, raje, šopek; povedati, počakati, skrbeti, nikar, svetilka; živec, trn, žakelj, žvižgati,
  migniti; zadeva, nagovor, spoštovan, občina, prijava, prijavnica, popravilo, obvestilo, priloga, sestanek, obrazec,
  račun, prejeti, obveščati, zahvaljevati se, na voljo. And `sejem` reads as "I sow" (B1's units keep it in `words`).
- **The print's rule line** takes a step's first sentences: govorjeni-jezik's steps 5 and 6 lose their second point
  there (ta as an article, a ne?); polish-content adds a check for this.
- **The polite capital** in a letter (Vi, Vas, Vam) wants a source (SP2001's rule on the courtesy capital).
- **The tutor's homework** may use `spoken` once the bridge is deployed; publish_worksheet validates it as the units.
