# Built: the course writers' small things, and the recount (2026-11-03)

Wave D, last branch (Jan, 2026-11-02: "yes complete"). Branch `course-polish`, from German B2's tip, which holds every course. Seven things the writers found and left, each verified first, then a recount of what each language and culture has.

## For the native check

Everything here is machine-written (Claude). Only item 5 adds language.

1. **Benché, sebbene, nonostante with the indicative are almost, the subjunctive shown** (`grading/it.json`, `nonstandard`): is "Molti dicono così; per iscritto vale la forma indicata" fair for them? See "5. Italian typed forms" for what Treccani does and doesn't say: it has them, it doesn't say how common they are in speech. If nobody grown up says *nonostante fa freddo*, take that conjunction out of the rule's `after`.
2. **The sentence that names it**, in `benche-purche`'s first step and on the page `congiunzioni-congiuntivo`: de "Viele sagen auch *benché piove*; geschrieben gilt **benché piova**."; sl "Ljudje rečejo tudi *benché piove*, pisno pa velja **benché piova**."; en "People also say *benché piove*; writing wants **benché piova**."
3. **`dassi`, `stassi`** stay wrong, as the brief says, still on the native list (`course-it-b2-c.md`).

## Voice (no credits spent)

Counted with the bridge's own `courseTexts`, in every base of all four courses, at the branch's base and now (the script compares both trees; nothing is voiced):

- **Items 1, 2:** no line and no explanation changes. A cue is on no list of what is read (`prebuild.ts`, `course-speech.ts`, `voice.ts`); `formeller-brief`'s test isn't voiced; every unit equals its old self with its cues dropped.
- **Item 5:** no teacher's line changes. Six explanations in the learner's language do, `benche-purche`'s first step and its summary (the page's rule) in de, sl and en: **3,336 characters** if they are made again. The app asks for them as an intro opens, and only if one had been made is one lost.
- **Item 6:** nothing cut, so nothing lost.

## 1. "→ (→)" on a transform list: fixed

The app draws a line's arrow and a cue in brackets after it (`SheetExercises.kt`), so a cue of `→` showed twice. German's 203 went in 6d510035. The brief named Italian's 28 and English's 10 and asked to check Slovene: Slovene has none that are only an arrow, but a cue that **opens** with one reads the same way ("→ (→ To je …)"), and there were 19 of those.

| | only an arrow: the cue goes | opens with an arrow: the arrow goes | an arrow inside: stays |
|---|---|---|---|
| Italian | 28 | 2 (`plurale`) | 2 (`molto → -issimo`, `forse → ?`) |
| English | 10 | 2 (`object-pronouns`) | 2 (`am, is → was; are → were`) |
| Slovene | 0 | 14 (`To je …`, `je že …`, `več · more`, `bi …`, `… kot …`, `zaimka · two pronouns`) | 2 (`hoče → gre`) |
| German | 0 | 1 (`plural`, per base) | 0 |

**The validator** (`arrowCues` in `course.ts`, used by `validateUnit` and `validateWorksheet`) refuses a transform's cue that opens with an arrow, in any of its bases, saying where and why. Not in the schema, and not for a homework read back from the tutor's files, so a sheet published before the rule doesn't vanish from a learner's unit. An arrow inside a cue is meaning and stays. The smoke has three new checks. **Seen on the emulator:** `plurale` as `→ (tanti · viele)`, `object-pronouns`, `primernik` as `→ (… kot …)`.

## 2. QA can't type ä, ö, ü, ß or Italian's accents: fixed

`type_letters` in `bin/qa` enters them as a learner does: adb types what it can, the rest is the letter's chip under the field. The keyboard goes down if it hides the chips, and the sheet scrolls to them. A capital with only a small chip is typed small (Italian's È: a capital doesn't count); a letter with no chip (Slovene's ć, đ) is folded, as a keyboard without it would.

**A bug it met.** With the keyboard down adb's keys reach the field within a frame of each other, and right after a chip they came out of order: `Grüßne`, `Büergrmeister`, which graded wrong. So it goes one key at a time then, and it checks what the focused field holds and types again if it differs. This is QA's speed and no learner's: a person's keys are a tenth of a second apart. The first run showed it only as 10/12 on a test that still passed, so **`course-unit` now fails if any of the unit's own answers is graded wrong**, where it used to pass at 80 %. That is stricter for every later run.

Also new, from these runs: `LANI_QA_COURSE_UNIT=a,b,c` plays several units in one run; a quick question's option is looked for below its own heading (`wem-dativ`: "Kathi bringt" stands in the step's bubble too, and the table's `dem Gast` passed for the option); the result's Continue is found below the fold (past 40 answers a day it says why the rewards are halved).

**Restored in `formeller-brief`'s test**, as they were before 6d510035: `Mit freundlichen ___` (Grüßen), and `Kommst du am Freitag? (Würden Sie)`, with both its accepted lines. The file now differs from that commit's parent only in its cues.

## 3. `nonstandard` and `spoken` on a screen: seen, all right

`--steps typed-forms` types a form people say in each of the course's bases, the rest of the exercise answered right, and writes what the screen said to `typed-forms.txt`. **No word and no hint needed changing.** What the learner sees:

| course | typed | verdict | under the field |
|---|---|---|---|
| English, `adverbs` | `Granny Maggie cooks good` | Almost | the right form with `good` struck and `well` marked; "People say it like this; in writing it's the form shown" |
| English, `because-so` | `We walked cos the bus was late` | Correct | "🗣️ Spoken, and right too; the standard form: We walked because the bus was late." |
| German, `obwohl-trotzdem`, a table's cell | `obwohl er ist krank` | Almost | `obwohl er krank ist` with `ist` moved; "So sagen es viele; geschrieben gilt die gezeigte Form" under the row |
| German, `als-waehrend` | `Wie Stefan kam, regnete es` | Right | "🗣️ Umgangssprachlich, auch richtig; schriftsprachlich: Als Stefan kam, regnete es." |
| Italian, `benche-purche` | `Benché piove, Davide esce` | Almost | `piova` marked; "Molti dicono così; per iscritto vale la forma indicata" |
| Italian, `concordanza-tempi` | `Pensavo che Davide veniva` | Right | "🗣️ Parlato, ed è giusto anche così; la forma standard: Pensavo che Davide venisse." |
| Slovene, `govorjeni-jezik` | `Jutri moram delat` | Right | "🗣️ Pogovorno, prav tako prav; knjižno: Jutri moram delati." |

Each hint has its second half in the base: en, sl, de or it as the course has them, each read on the screen. English's spoken form is checked in de here and only its almost in sl and it: in those runs QA's scroll never reached the third line. The strings are the same key.

**Seen and not a fault of these forms:** QA's hook "pair:" changes the app's labels, not the base the dev bridge serves, so a sheet's own `explain` in the panel below stays in the synthetic learner's base during this step. A real learner's are in their base.

The items differ a little from the brief's words, as the units do: `adverbs` has no "She sings good", so it's its line `Granny Maggie cooks good`; `obwohl er ist krank` is the cell in `obwohl-trotzdem`'s first sheet, as the brief has it.

## 4. `--steps village-hud`: fixed

The helper is `village_bar` now, so the step is the step again. A test holds that no function of `bin/qa` is defined twice (`test_qa_hud_labels.py`, over the file's syntax tree), as nothing showed this bug otherwise. The step's labels were fixed in sl · en: they now come from the app's strings in the phone's pair, the resources from the village's culture, and its second base is German where it can be.

**Found by running it**, 2026-11-03 being a feast day in Primorska: today's card's own word "Danes" passed for the ring, so its tap opened that card's sheet, and with the cards hidden the fallback took a deer on the map. `hud_ring` now looks only in the bar, above the cards. `plain_village` uses it too. And a goal with many steps has a sheet up to 86 % of the screen, leaving a strip too thin for "a tap on the map closes it", so the step tries that on the first sheet with room and closes the others by Back. Passes on sl · en in Primorska and it · de in Friuli.

## 5. Italian typed forms: moved

`benché`, `sebbene` and `nonostante` with the indicative were wrong when typed. They are in `grading/it.json`'s `nonstandard` now: almost, the subjunctive shown, no mistake recorded. 26 pairs, each one verb's (`piove` for piova, `e` for sia, `fa` for faccia, `puo` for possa, as `Grading.fold` writes them), each within four words after the conjunction, so `Benché mangia` for piova stays wrong and so does `Penso che il ponte è`. `dassi` and `stassi` stay in `wrong`, as the brief says.

**What Treccani says, opened and read.** The [dictionary](https://www.treccani.it/vocabolario/benche/): "Con l'indicativo è antiquato, ma è comune quando la congiunzione è usata assol., o con valore avversativo". Its [Errata corrige](https://www.treccani.it/magazine/lingua_italiana/articoli/scritto_e_parlato/Errata_Corrige6.html) quotes the press with all three (benché non ha ricevuto, sebbene non può, nonostante ha tenuto) and calls them breaches of the norm, "ancora contenuto per dimensioni". So they are said and printed and not what writing wants, which is what `nonstandard` means. Neither says how common they are in speech. Its [entry on concession](https://www.treccani.it/enciclopedia/espressione-della-concessione_(Enciclopedia-dell'Italiano)/) gives only the subjunctive. **A correction:** `course-it-b2-a.md` linked this claim to Treccani's page on the indefinites, which has chiunque with the indicative and doesn't mention benché; that link is corrected.

`benche-purche`'s first step and the page say both, as COURSE.md asks. `connettivi`'s print no longer has the "✗ benché piove" that `course-it-b2-c.md` worried about: nothing in the content says it now. `ItalianB2aContentTest` holds it on the unit's own items, in lines and in gaps; `GradingNonstandardTest` knows only Slovene has no table now. `school-prints` of `benche-purche` in de, sl and en: fits two pages.

## 6. Older German intro tables over twelve rows: already fine, nothing cut

A step's table grows above its question in one scrolling column, so a question is always there for a thumb. The measure is QA's, the same that stopped `oesterreichisch` at 19 rows: can `course-unit` reach and answer it within its swipes? All six can, on a phone, base en:

| unit | rows | intro reached, test |
|---|---|---|
| `partizip-perfekt` | 16 | ok, 14 of 14 |
| `zahlen` | 15 | ok, 14 of 14, and its print |
| `am-ersten-mai` | 14 | ok, 14 of 14, and its print |
| `werden-futur` | 14 | ok, 13 of 13 |
| `perfekt` | 13 | ok, 13 of 13, and its print |
| `wem-dativ` | 13 | ok, 13 of 13, and its print |

`partizip-perfekt` and `werden-futur` went on past their tests only as far as the result's button, as that fix of QA's came after their run: their prints weren't reached there, and nothing of theirs changed.

## 7. A long typed cell in a narrow column: fixed, small

A table's field was one line that scrolled sideways, so a long answer showed only its end. It now wraps, over up to four lines (`SheetField`'s `wraps`), a point smaller, so `dem Menschen` shows whole in a phone's three columns. An Enter or a pasted line break becomes a space. A wide cell on a tablet stays on one line, as it fits.

**A transform's line had the same fault**, which QA's shots showed once they were looked at for this: beside a long cue the field is squeezed ("→ (me, him, her, it, us, them)" left `s Birkett knows him`), and a sentence longer than the screen lost its start (`ßehr geehrter Herr Diplomingenieur Wieser`). One argument, the same `wraps`, so it's done there too. A gap keeps its one line: it sits in the running text.

`--steps long-cell` (`herr-kollege`'s row *der Mensch*), the before from an APK of the base: on the phone the two cells showed "enschen" and now "dem / Menschen", "des / Menschen", in fields of 187 px against 167. On a tablet upright (1600 × 2560) and on its side (2560 × 1600) both ways show the whole answer on one line, the text a point smaller after. Shots: the scratchpad's `ux-course-polish/lc-before/` and `lc-after2/` (`lc-phone-typed.png`, `lc-phone-checked.png`, `lc-tablet-*.png`); side by side in `m-lc-phone.png` and `m-lc-tablet.png`. German's and others' two-column cells wrap the same way (`obwohl er ist / krank`).

## The recount

`companion/bin/content-count` (`--json` for the numbers; `companion/tests/test_content_count.py`) counts the files and reads no doc. These tables are its output at this branch's tip.

### Each language

|  | de | en | it | sl |
|---|---|---|---|---|
| units written | 72 | 72 | 72 | 66 |
| units A1 | 18 | 18 | 18 | 18 |
| units A2 | 18 | 18 | 18 | 14 |
| units B1 | 18 | 18 | 18 | 16 |
| units B2 | 18 | 18 | 18 | 18 |
| units planned | 0 | 0 | 0 | 0 |
| grammar pages | 68 | 67 | 70 | 77 |
| pages A1 | 15 | 18 | 15 | 28 |
| pages A2 | 20 | 13 | 19 | 13 |
| pages B1 | 15 | 18 | 18 | 18 |
| pages B2 | 18 | 18 | 18 | 18 |
| modules | – | – | 21 (A1 8 · A2 8 · B1 5), 252 exercises | 6 (A1 6), 72 exercises |
| drills: builds | – | – | 80 (A1 53 · A2 17 · B1 10) | 73 (A1 67 · A2 6) |
| drills: transforms | – | – | 251 (A1 111 · A2 105 · B1 35) | 215 (A1 187 · A2 28) |
| drills: rapid | – | – | 131 (A1 131) | 130 (A1 130) |
| drills: riddles | – | – | 58 (A1 47 · A2 11) | 54 (A1 54) |

### Each culture

|  | friuli (it) | kaernten (de) | lakeland (en) | primorska (sl) |
|---|---|---|---|---|
| packs | 80 | 81 | 83 | 82 |
| packs by level | A1 70 · A2 10 | A1 69 · A2 12 | A1 68 · A2 15 | A1 45 · A2 37 |
| pack words | 1115 | 1080 | 1108 | 1311 |
| pack words by level | A1 983 · A2 132 | A1 898 · A2 182 | A1 888 · A2 220 | A1 782 · A2 529 |
| scenes | 33 | 33 | 33 | 33 |
| scenes' happenings | 166 | 180 | 176 | 162 |
| scenes' dialogs | 165 | 180 | 175 | 160 |
| stories | 16 | 16 | 16 | 16 |
| stories in chapters | 0 | 0 | 0 | 3 |
| stories' tellings | A1 16 · A2 16 | A1 16 · A2 16 | A1 16 · A2 16 | A1 16 · A2 16 · B1 7 |
| readings | 9 | 9 | 9 | 9 |
| readings by level | A1 4 · A2 4 · B1 1 | A1 4 · A2 4 · B1 1 | A1 4 · A2 4 · B1 1 | A1 2 · A2 5 · B1 2 |
| arrivals: cast | 13 | 13 | 13 | 14 |
| arrivals by level | A1 13 · A2 13 · B1 13 | A1 13 · A2 13 · B1 13 | A1 13 · A2 13 · B1 13 | A1 14 · A2 14 · B1 14 |
| arrivals: templates | 3 | 3 | 3 | 3 |
| projects with steps | 13 | 13 | 13 | 13 |
| project steps | 78 | 78 | 78 | 78 |
| steps by level | A1 78 · A2 78 · B1 78 | A1 78 · A2 78 | A1 78 · A2 78 | A1 78 · A2 78 |
| parties | 5 | 5 | 5 | 5 |
| parties' tellings | A1 5 · A2 5 · B1 5 · B2 5 | A1 5 · A2 5 · B1 5 · B2 5 | A1 5 · A2 5 · B1 5 · B2 5 | A1 5 · A2 5 |
| keepers | 1 | 1 | 1 | 1 |
| keepers' talks | 1 | 1 | 1 | 44 |
| talks' tellings | A1 1 · A2 1 | A1 1 · A2 1 | A1 1 · A2 1 | A1 44 · A2 44 |
| villagers | 13 | 13 | 13 | 14 |

What it shows beside what was said so far. **Slovene has 66 units, not 72**: 14 at A2 and 16 at B1, and the other three courses 18 at each level. **Primorska's five parties are told at A1 and A2 only**, the other cultures' at all four levels. Only Friuli's project steps are told at B1 (Teo's target). **No pack is above A2** in any culture. Slovene's keeper has 44 talks, the others 1. German and English have no modules and no car drills.

These are what exists, not what is good. A pack or a scene is counted where the bridge reads it, a language's first, then the culture's, the first with an id winning. A talk is everything of a keeper's with its tellings by level.

## What was verified, and how

At the tip, all on the merged branch: `main` was at this branch's base when the checks ran (f9339ab8, German B2's merge), so there was nothing to merge in. The brief's "the branch I name" wasn't named; see the report.

- `bun build --no-bundle` on the one TypeScript test file changed (`test/smoke/course.ts`): parses. `bunx tsc --noEmit -p .`: clean.
- The smoke (ports 10290, 10293, 10296; Drama 3 on 10308): 3,704 checks, none failed. `tutor-export --check`: 140 files match.
- Android, **the whole unit suite** through lani-slot, twice, the second at the tip with the transform lines wrapping: 4,100 tests in 520 classes, none failed (2 skipped).
- Python: 253 tests (tests), 179 (companion/tests, 11 skipped), all pass. New: `test_content_count.py`, `test_qa_letters.py`, more in `test_qa_hud_labels.py`.
- `school-prints`: `benche-purche` in de, sl, en and `formeller-brief` in en, sl, it, each fits two pages. `bun test/prompt-clashes.ts`: no new clash.
- Every unit of all four courses validated (`validateUnit`, no arrow left at the head of a cue): de 72, en 72, it 72, sl 66.
- **Emulator, `course-unit`, each to its end with the test all right** (`LANI_CULTURE`, `LANI_QA_SETUP_PAIR`, a synthetic learner from `make-learner.ts`):

| course, base | unit | test |
|---|---|---|
| German, en | `formeller-brief` (its two items restored) | 12 of 12, its print |
| | `moechten` | 11 of 11 |
| | `haette-waere` | 12 of 12 |
| | `wenn-ich-haette` | 13 of 13 |
| | `haette-gewusst` | 13 of 13 |
| Italian, de | `futuro` (19 answers with an accent) | 12 of 12 |
| | `plurale` (its cue) | 12 of 12 |
| Slovene, en | `primernik` (35 answers with č, š, ž; its cue) | 12 of 12 |
| English, de | `object-pronouns` (its cue) | 11 of 11 |

  And the six of item 6. No unit needed an answer changed.
- **Emulator, `typed-forms`:** English in sl, de, it (see item 3), German in en, sl, it, Italian in de, sl, en, Slovene in en, de, it.
- **Emulator, `village-hud`:** sl · en in Primorska on a feast day (the bar, the cards, the ring's sheet, its long press hiding the cards, still hidden when the village is opened again, each card's section, a tap on the map, the settlement in sl · de, the plot chooser and back); it · de in Friuli, the same, its second base sl.
- **Emulator, `long-cell`:** see item 7. Again at the tip with the lines wrapping, beside `formeller-brief` (12 of 12) and `moechten` (11 of 11) once more and `object-pronouns` (11 of 11): `Mrs Birkett knows him` and `Sehr geehrter Herr Diplomingenieur Wieser` whole on the phone (`ux-course-polish/m-wrap.png`, before and after).
- The voice comparison, see "Voice".

**Not done:** the rest of the course's units on an emulator; the other two bases of the units above (their words are the same, their explanations aren't typed); `typed-forms` on a tablet; a release.

## What's left

- **The letter row on a phone clips German's seventh chip** (Ü) inside an intro's card: seven chips in a `Row`. It can still be tapped. Left, because a wrapping row takes a line from a dialog's typed turn, where room is tight. Slovene's and Italian's six fit.
- **Intro tables elsewhere are longer than German's six:** English `past-irregular` 25 rows, Italian `alterati` and `infinito-passato` 18, Slovene `sklanjatev-posebnosti` 17. They weren't in the brief and I didn't play them. A learner can scroll; only QA has a limit.
- `forse → ?` on `futuro-anteriore` reads "→ (forse → ?)": allowed, odd.
- **The native check**, first in this doc.
